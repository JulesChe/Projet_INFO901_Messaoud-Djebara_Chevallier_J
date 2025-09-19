package com.mycompany.app;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Communicateur avec token intégré.
 * Version allégée maintenant 100% distribuée.
 *
 * @author Middleware Team
 */
public class Com {

    private final DistributedEventBus eventBus;
    private final int processId;
    private final Semaphore clockSemaphore = new Semaphore(1);
    private volatile int lamportClock = 0;
    public final Mailbox mailbox = new Mailbox();

    // Numérotation automatique et consécutive (sujet.pdf)
    private volatile int logicalId = -1; // ID logique consécutif (0, 1, 2...)
    private final Map<Integer, Long> processLastSeen = new ConcurrentHashMap<>();
    private static final long PROCESS_TIMEOUT = 10000; // 10 secondes

    // Token intégré avec thread dédié (sujet.pdf)
    private volatile boolean hasToken = false;
    private volatile boolean wantsToEnterCS = false;
    private final Object tokenLock = new Object();
    private final Semaphore csAccess = new Semaphore(0);
    private volatile boolean tokenThreadRunning = false;
    private Thread tokenThread;

    // Barrière simplifiée
    private volatile boolean atBarrier = false;
    private volatile CountDownLatch barrierLatch = new CountDownLatch(1);
    private final Set<Integer> processesAtBarrier = ConcurrentHashMap.newKeySet();

    // Communication synchrone
    private final Map<String, CountDownLatch> pendingSyncOps = new ConcurrentHashMap<>();
    private final Map<String, UserMessage> pendingSyncMsgs = new ConcurrentHashMap<>();
    private final AtomicInteger syncIdCounter = new AtomicInteger(0);

    // Heartbeat
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1);
    private volatile boolean running = true;

    public Com(NetworkConfig config) {
        this.processId = config.getProcessId();
        this.eventBus = new DistributedEventBus(config);
        this.eventBus.registerSubscriber(this);

        startServices();
    }

    private void startServices() {
        // Démarrer heartbeat - messages système n'affectent PAS l'horloge
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (running) {
                SystemMessage heartbeat = new SystemMessage(-1, processId,
                    SystemMessage.Type.HEARTBEAT, "alive");
                eventBus.post(heartbeat);
            }
        }, 2000, 2000, TimeUnit.MILLISECONDS);

        // Vérifier et corriger numérotation périodiquement
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (running) {
                checkDeadProcessesAndRenumber();
            }
        }, 5000, 3000, TimeUnit.MILLISECONDS);

        // Initier token et démarrer thread dédié si plus petit ID
        heartbeatExecutor.schedule(() -> {
            Map<Integer, ProcessEndpoint> endpoints = eventBus.getKnownEndpoints();
            int smallestId = processId;
            for (Integer id : endpoints.keySet()) {
                if (id < smallestId) smallestId = id;
            }
            if (smallestId == processId) {
                initiateTokenWithThread(); // Thread dédié conforme au sujet
            }
            assignLogicalId(); // Calculer ID logique initial
        }, 3000, TimeUnit.MILLISECONDS);
    }

    // ================ HORLOGE DE LAMPORT ================

    public void inc_clock() {
        try {
            clockSemaphore.acquire();
            lamportClock++;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            clockSemaphore.release();
        }
    }

    private void updateClock(int receivedClock) {
        try {
            clockSemaphore.acquire();
            lamportClock = Math.max(lamportClock, receivedClock) + 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            clockSemaphore.release();
        }
    }

    public int getCurrentClock() {
        return lamportClock;
    }

    // ================ COMMUNICATION ASYNCHRONE ================

    public void broadcast(Object o) {
        inc_clock();
        UserMessage message = new UserMessage(o, getCurrentClock(), processId);
        eventBus.post(message);
    }

    public void sendTo(Object o, int dest) {
        inc_clock();
        UserMessage message = new UserMessage(o, getCurrentClock(), processId);
        eventBus.sendTo(dest, message);
    }

    // ================ RÉCEPTION DE MESSAGES ================

    @DistributedEventBus.Subscribe
    public void onUserMessage(UserMessage message) {
        if (message.getSender() != processId) {
            updateClock(message.getTimestamp());
            mailbox.putMessage(message);

            // Envoyer ACK si c'est un message synchrone
            if (message.getSyncId() != null) {
                SystemMessage ack = new SystemMessage(-1, processId,
                    SystemMessage.Type.SYNC_ACK, message.getSyncId(), message.getSender());
                eventBus.sendTo(message.getSender(), ack);
            }

            // Débloquer broadcastSync si en attente
            String syncKey = "broadcast-from-" + message.getSender();
            CountDownLatch latch = pendingSyncOps.get(syncKey);
            if (latch != null) {
                latch.countDown();
            }

            // Débloquer recevFromSync si en attente
            String recvKey = "recv-from-" + message.getSender();
            CountDownLatch recvLatch = pendingSyncOps.get(recvKey);
            if (recvLatch != null) {
                pendingSyncMsgs.put(recvKey, message);
                recvLatch.countDown();
            }
        }
    }

    @DistributedEventBus.Subscribe
    public void onSystemMessage(SystemMessage message) {
        if (message.getSender() == processId) return;

        // Messages système n'affectent PAS l'horloge de Lamport (sujet.pdf)

        switch (message.getType()) {
            case TOKEN:
                onTokenReceived();
                break;
            case HEARTBEAT:
                // Heartbeat reçu, processus vivant - mettre à jour timestamp
                processLastSeen.put(message.getSender(), System.currentTimeMillis());
                break;
            case BARRIER_ARRIVE:
                synchronized (processesAtBarrier) {
                    processesAtBarrier.add(message.getSender());
                    checkBarrierComplete();
                }
                break;
            case BARRIER_RELEASE:
                if (barrierLatch != null) {
                    barrierLatch.countDown();
                }
                break;
            case SYNC_ACK:
                handleSyncAck(message);
                break;
        }
    }

    // ================ SECTION CRITIQUE AVEC TOKEN INTÉGRÉ ================

    public void requestSC() {
        synchronized (tokenLock) {
            wantsToEnterCS = true;
            if (hasToken) {
                return; // Accès immédiat
            }
        }

        try {
            csAccess.acquire(); // Attendre le token
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            wantsToEnterCS = false;
        }
    }

    public void releaseSC() {
        synchronized (tokenLock) {
            wantsToEnterCS = false;
            if (hasToken) {
                hasToken = false;
                passTokenToNext();
            }
        }
    }

    /**
     * Initie le token avec un thread dédié (conforme au sujet.pdf).
     * "la gestion du jeton doit être gérée par un thread tiers"
     */
    private void initiateTokenWithThread() {
        hasToken = true;
        tokenThreadRunning = true;

        tokenThread = new Thread(this::manageTokenCirculation, "TokenManager-" + processId);
        tokenThread.setDaemon(true);
        tokenThread.start();

        System.out.println("Processus " + processId + " initie le token avec thread dédié");
    }

    /**
     * Thread dédié pour la gestion du token (conforme au sujet.pdf).
     */
    private void manageTokenCirculation() {
        while (tokenThreadRunning && running) {
            try {
                Thread.sleep(5000); // Circulation toutes les 5 secondes

                synchronized (tokenLock) {
                    if (hasToken && !wantsToEnterCS) {
                        // Passer le token seulement si pas en section critique
                        passTokenToNext();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void passTokenToNext() {
        Map<Integer, ProcessEndpoint> endpoints = eventBus.getKnownEndpoints();
        List<Integer> sortedIds = new ArrayList<>(endpoints.keySet());
        Collections.sort(sortedIds);

        int currentIndex = sortedIds.indexOf(processId);
        if (currentIndex != -1 && sortedIds.size() > 1) {
            int nextIndex = (currentIndex + 1) % sortedIds.size();
            int nextProcessId = sortedIds.get(nextIndex);

            if (nextProcessId != processId) {
                // Token = message système, timestamp = -1 (n'affecte pas l'horloge)
                SystemMessage tokenMsg = new SystemMessage(-1, processId, SystemMessage.Type.TOKEN);
                eventBus.sendTo(nextProcessId, tokenMsg);
            }
        }
    }

    private void onTokenReceived() {
        synchronized (tokenLock) {
            hasToken = true;
            if (wantsToEnterCS) {
                csAccess.release(); // Débloquer requestSC()
            } else {
                // Le thread de circulation continuera automatiquement
                if (!tokenThreadRunning) {
                    // Redémarrer le thread si nécessaire
                    tokenThreadRunning = true;
                    tokenThread = new Thread(this::manageTokenCirculation, "TokenManager-" + processId);
                    tokenThread.setDaemon(true);
                    tokenThread.start();
                }
            }
        }
    }

    // ================ BARRIÈRE SIMPLIFIÉE ================

    public void synchronize() {
        atBarrier = true;
        barrierLatch = new CountDownLatch(1);

        // Annoncer arrivée - message système n'affecte pas l'horloge
        SystemMessage arriveMsg = new SystemMessage(-1, processId,
            SystemMessage.Type.BARRIER_ARRIVE);
        eventBus.post(arriveMsg);

        // Ajouter soi-même
        processesAtBarrier.add(processId);
        checkBarrierComplete();

        try {
            barrierLatch.await(); // Attendre libération
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            atBarrier = false;
        }
    }

    private void checkBarrierComplete() {
        Map<Integer, ProcessEndpoint> endpoints = eventBus.getKnownEndpoints();
        if (processesAtBarrier.size() >= endpoints.size()) {
            // Tous arrivés, libérer - message système n'affecte pas l'horloge
            SystemMessage releaseMsg = new SystemMessage(-1, processId,
                SystemMessage.Type.BARRIER_RELEASE);
            eventBus.post(releaseMsg);
            processesAtBarrier.clear();
        }
    }

    // ================ COMMUNICATION SYNCHRONE ================

    public void broadcastSync(Object o, int from) {
        if (processId == from) {
            // Expéditeur - envoie et attend ACKs de tous
            inc_clock();
            String syncId = processId + "-" + syncIdCounter.getAndIncrement();

            Map<Integer, ProcessEndpoint> endpoints = eventBus.getKnownEndpoints();
            int expectedAcks = endpoints.size() - 1; // Exclure soi-même
            if (expectedAcks <= 0) return;

            CountDownLatch latch = new CountDownLatch(expectedAcks);
            pendingSyncOps.put(syncId, latch);

            // Envoyer le message utilisateur avec syncId
            UserMessage userMsg = new UserMessage(o, getCurrentClock(), processId);
            userMsg.setSyncId(syncId); // Marquer pour sync
            eventBus.post(userMsg);

            try {
                latch.await(); // Attendre tous les ACKs
                System.out.println("BroadcastSync terminé, tous les ACKs reçus");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                pendingSyncOps.remove(syncId);
            }
        } else {
            // Récepteur - attendre message de 'from'
            String syncKey = "broadcast-from-" + from;
            CountDownLatch latch = new CountDownLatch(1);
            pendingSyncOps.put(syncKey, latch);

            try {
                latch.await();
                System.out.println("BroadcastSync reçu de " + from);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                pendingSyncOps.remove(syncKey);
            }
        }
    }

    public void sendToSync(Object o, int dest) {
        inc_clock();
        String syncId = processId + "-" + syncIdCounter.getAndIncrement();
        CountDownLatch latch = new CountDownLatch(1);
        pendingSyncOps.put(syncId, latch);

        // Envoyer avec syncId pour avoir un ACK
        UserMessage userMsg = new UserMessage(o, getCurrentClock(), processId);
        userMsg.setSyncId(syncId);
        eventBus.sendTo(dest, userMsg);

        try {
            latch.await(); // Attendre ACK du destinataire
            System.out.println("SendToSync terminé, ACK reçu de " + dest);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pendingSyncOps.remove(syncId);
        }
    }

    public Object recevFromSync(int from) {
        String syncKey = "recv-from-" + from;
        CountDownLatch latch = new CountDownLatch(1);
        pendingSyncOps.put(syncKey, latch);

        try {
            latch.await(); // Attendre message de 'from'
            UserMessage msg = pendingSyncMsgs.remove(syncKey);
            System.out.println("RecevFromSync terminé, message reçu de " + from);
            return msg != null ? msg.getPayload() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            pendingSyncOps.remove(syncKey);
        }
    }

    private void handleSyncAck(SystemMessage message) {
        String syncId = message.getSyncId();
        CountDownLatch latch = pendingSyncOps.get(syncId);
        if (latch != null) {
            latch.countDown();
        }
    }

    // ================ ACCESSEURS ================

    public int getProcessId() { return processId; }

    public int getProcessCount() {
        return eventBus.getKnownEndpoints().size();
    }

    /**
     * Obtient l'ID logique consécutif de ce processus (0, 1, 2...).
     * Conforme au sujet : numérotation automatique commençant à 0.
     */
    public int getLogicalId() {
        return logicalId;
    }

    // ================ NUMÉROTATION AUTOMATIQUE ================

    /**
     * Assigne un ID logique consécutif basé sur les processus actifs.
     * Les IDs sont attribués par ordre croissant des processId physiques.
     */
    private void assignLogicalId() {
        Map<Integer, ProcessEndpoint> endpoints = eventBus.getKnownEndpoints();
        List<Integer> activeProcesses = new ArrayList<>();

        // Collecter les processus actifs (y compris soi-même)
        activeProcesses.addAll(endpoints.keySet());
        for (Integer pid : processLastSeen.keySet()) {
            if (!activeProcesses.contains(pid)) {
                activeProcesses.add(pid);
            }
        }

        Collections.sort(activeProcesses);
        int oldLogicalId = logicalId;
        logicalId = activeProcesses.indexOf(processId);

        if (oldLogicalId != logicalId) {
            System.out.println("Processus " + processId + " : nouvel ID logique = " + logicalId);
        }
    }

    /**
     * Vérifie les processus morts et recalcule la numérotation.
     * Conforme au sujet : correction de numérotation si processus morts.
     */
    private void checkDeadProcessesAndRenumber() {
        long now = System.currentTimeMillis();
        boolean changed = false;

        // Supprimer les processus morts
        Iterator<Map.Entry<Integer, Long>> it = processLastSeen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            if (now - entry.getValue() > PROCESS_TIMEOUT) {
                System.out.println("Processus " + entry.getKey() + " détecté comme mort");
                it.remove();
                changed = true;
            }
        }

        // Recalculer la numérotation si nécessaire
        if (changed) {
            assignLogicalId();
        }
    }

    public void shutdown() {
        running = false;
        tokenThreadRunning = false;

        // Arrêter le thread de token
        if (tokenThread != null) {
            tokenThread.interrupt();
            try {
                tokenThread.join(1000); // Attendre 1 seconde
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        heartbeatExecutor.shutdown();
        eventBus.shutdown();
    }
}