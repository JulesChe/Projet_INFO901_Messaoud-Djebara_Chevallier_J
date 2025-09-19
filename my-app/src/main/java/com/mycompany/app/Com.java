package com.mycompany.app;

import com.google.common.eventbus.Subscribe;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Classe Com (Communicateur) - Middleware pour la communication distribuée.
 *
 * Cette classe implémente un middleware complet avec :
 * - Horloge de Lamport protégée par sémaphore
 * - Boîte aux lettres pour messages asynchrones
 * - Communication via EventBus (Guava)
 * - Système de numérotation automatique SANS variables de classe
 * - Section critique distribuée avec jeton circulaire
 * - Synchronisation de tous les processus
 * - Communication synchrone (broadcastSync, sendToSync, recvFromSync)
 * - Système de heartbeat et renumération automatique
 *
 * @author Middleware Team
 */
public class Com {
    private EventBusService bus;
    private final Semaphore clockSemaphore = new Semaphore(1);
    private volatile int lamportClock = 0;
    private volatile int processId;

    // Boîte aux lettres pour messages asynchrones
    public final Mailbox mailbox;

    // Section critique - gestion du jeton circulaire
    private volatile boolean hasToken = false;
    private volatile boolean wantsToEnterCS = false;
    private final Object tokenLock = new Object();
    private final Semaphore csAccess = new Semaphore(0);

    // Communication synchrone
    private final Map<String, CountDownLatch> pendingSyncOperations = new ConcurrentHashMap<>();
    private final Map<String, SynchronizeMessage> pendingSyncMessages = new ConcurrentHashMap<>();
    private final AtomicInteger syncIdCounter = new AtomicInteger(0);
    private volatile boolean syncLock = true;

    // Synchronisation globale (barrière)
    private volatile boolean atBarrier = false;
    private volatile CountDownLatch barrierLatch = new CountDownLatch(1);
    private final Object barrierLock = new Object();

    // Système de heartbeat et détection de pannes
    private final Map<Integer, Long> lastHeartbeatTime = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(2);
    private static final long HEARTBEAT_INTERVAL = 2000; // 2 secondes
    private static final long HEARTBEAT_TIMEOUT = 6000;  // 6 secondes
    private volatile boolean heartbeatActive = true;

    // Numérotation automatique distribuée (SANS variables de classe)
    private final ProcessRegistry processRegistry;

    /**
     * Constructeur du communicateur.
     * Initialise l'horloge de Lamport, la boîte aux lettres et obtient un ID unique.
     *
     * @param registry Le registre des processus pour la numérotation automatique
     */
    public Com(ProcessRegistry registry) {
        this.processRegistry = registry;
        this.processId = registry.registerNewProcess(this);
        this.bus = EventBusService.getInstance();
        this.bus.registerSubscriber(this);
        this.mailbox = new Mailbox();

        // Démarrer le système de heartbeat
        startHeartbeat();

        // Initialiser le token pour le premier processus (ID = 0)
        if (processId == 0) {
            initToken();
        }

        System.out.println("Communicateur créé - Processus ID: " + processId);
    }

    /**
     * Obtient l'identifiant unique du processus.
     *
     * @return L'identifiant du processus (commençant à 0)
     */
    public int getId() {
        return processId;
    }

    /**
     * Incrémente l'horloge de Lamport de manière thread-safe.
     * Cette méthode peut être appelée par le processus utilisateur.
     */
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

    /**
     * Met à jour l'horloge de Lamport lors de la réception d'un message.
     *
     * @param receivedTimestamp L'estampille du message reçu
     */
    private void updateClock(int receivedTimestamp) {
        try {
            clockSemaphore.acquire();
            lamportClock = Math.max(lamportClock, receivedTimestamp) + 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            clockSemaphore.release();
        }
    }

    /**
     * Obtient l'horloge de Lamport actuelle de manière thread-safe.
     *
     * @return La valeur actuelle de l'horloge
     */
    private int getCurrentClock() {
        try {
            clockSemaphore.acquire();
            return lamportClock;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return lamportClock;
        } finally {
            clockSemaphore.release();
        }
    }

    // ============= COMMUNICATION ASYNCHRONE =============

    /**
     * Diffuse un objet à tous les autres processus de manière asynchrone.
     * L'objet est placé dans la B.a.L. de tous les autres processus.
     *
     * @param o L'objet à diffuser
     */
    public void broadcast(Object o) {
        inc_clock();
        int timestamp = getCurrentClock();
        // Convertir Object en String pour la sérialisation
        String payload = (o instanceof String) ? (String) o : String.valueOf(o);
        UserMessage message = new UserMessage(payload, timestamp, processId);
        bus.postEvent(message);
    }

    /**
     * Envoie un objet à un processus spécifique de manière asynchrone.
     * L'objet est placé dans la B.a.L. du processus destinataire.
     *
     * @param o L'objet à envoyer
     * @param dest L'identifiant du processus destinataire
     */
    public void sendTo(Object o, int dest) {
        inc_clock();
        int timestamp = getCurrentClock();
        // Convertir Object en String pour la sérialisation
        String payload = (o instanceof String) ? (String) o : String.valueOf(o);
        UserMessage message = new UserMessage(payload, timestamp, processId, dest);
        bus.postEvent(message);
    }

    /**
     * Reçoit un message utilisateur via l'EventBus.
     * Met à jour l'horloge de Lamport et place le message dans la B.a.L.
     *
     * @param message Le message reçu
     */
    @Subscribe
    public void onReceiveUserMessage(UserMessage message) {
        if (message.isForProcess(processId)) {
            // Mettre à jour l'horloge de Lamport (uniquement pour messages utilisateur)
            updateClock(message.getTimestamp());

            mailbox.putMessage(message);

            String msgType = message.isBroadcast() ? "broadcast" : "direct";
            System.out.println("Processus " + processId + " reçoit message " + msgType + ": " + message.getPayload());
            System.out.println("Horloge après réception: " + getCurrentClock());
        }
    }

    // ============= SECTION CRITIQUE DISTRIBUÉE =============

    /**
     * Demande l'accès à la section critique.
     * Bloque le processus jusqu'à obtention du jeton.
     * Utilise un jeton circulaire géré par un thread tiers.
     */
    public void requestSC() {
        synchronized (tokenLock) {
            wantsToEnterCS = true;
            System.out.println("Processus " + processId + " demande l'accès à la section critique");

            // Si le processus a déjà le jeton, accès immédiat
            if (hasToken) {
                System.out.println("Processus " + processId + " a déjà le jeton, accès immédiat à la SC");
                return;
            }
        }

        // Attendre le jeton (bloquant)
        try {
            System.out.println("Processus " + processId + " attend le jeton...");
            csAccess.acquire();
            System.out.println("Processus " + processId + " a obtenu l'accès à la section critique");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            wantsToEnterCS = false;
        }
    }

    /**
     * Libère la section critique et passe le jeton au processus suivant.
     * Le jeton circule automatiquement via le thread de gestion.
     */
    public void releaseSC() {
        synchronized (tokenLock) {
            wantsToEnterCS = false;
            hasToken = false;
            System.out.println("Processus " + processId + " libère la section critique");

            // Passer le jeton au processus suivant
            passTokenToNext();
        }
    }

    /**
     * Reçoit un message de jeton (message système).
     * Les messages de jeton n'impactent PAS l'horloge de Lamport.
     *
     * @param tokenMessage Le message contenant le jeton
     */
    @Subscribe
    public void onToken(TokenMessage tokenMessage) {
        if (tokenMessage.getDestination() == processId) {
            synchronized (tokenLock) {
                hasToken = true;
                System.out.println("Processus " + processId + " reçoit le jeton " +
                                 (wantsToEnterCS ? "(BESOIN)" : "(PAS BESOIN)"));

                // Si le processus veut entrer en section critique
                if (wantsToEnterCS) {
                    csAccess.release();
                } else {
                    // Passer immédiatement le jeton au suivant
                    hasToken = false;
                    passTokenToNext();
                }
            }
        }
    }

    /**
     * Initialise le jeton pour le premier processus (ID = 0).
     */
    private void initToken() {
        TokenMessage message = new TokenMessage(new Token("Section critique"), processId);
        bus.postEvent(message);
    }

    /**
     * Passe le jeton au processus suivant dans l'anneau.
     */
    private void passTokenToNext() {
        Set<Integer> activeProcesses = processRegistry.getActiveProcesses();
        int nextProcessId = findNextProcessInRing(processId, activeProcesses);

        if (nextProcessId != -1) {
            TokenMessage tokenMsg = new TokenMessage(new Token("Section critique"), nextProcessId);
            bus.postEvent(tokenMsg);
        }
    }

    /**
     * Trouve le prochain processus dans l'anneau circulaire.
     */
    private int findNextProcessInRing(int currentId, Set<Integer> activeProcesses) {
        int minId = Integer.MAX_VALUE;
        int nextId = Integer.MAX_VALUE;

        for (Integer id : activeProcesses) {
            minId = Math.min(minId, id);
            if (id > currentId && id < nextId) {
                nextId = id;
            }
        }

        // Si aucun processus trouvé après currentId, reprendre au début de l'anneau
        return nextId == Integer.MAX_VALUE ? minId : nextId;
    }

    // ============= SYNCHRONISATION GLOBALE =============

    /**
     * Synchronise tous les processus.
     * Attend que tous les processus aient invoqué cette méthode pour tous les débloquer.
     * Implémente une barrière de synchronisation distribuée.
     */
    public void synchronize() {
        System.out.println("Processus " + processId + " arrive à la barrière de synchronisation");

        synchronized (barrierLock) {
            atBarrier = true;
            Set<Integer> activeProcesses = processRegistry.getActiveProcesses();
            int totalProcesses = activeProcesses.size();

            // Vérifier si tous les processus sont à la barrière
            long processesAtBarrier = activeProcesses.stream()
                .mapToLong(id -> processRegistry.getProcess(id).isAtBarrier() ? 1 : 0)
                .sum();

            if (processesAtBarrier == totalProcesses) {
                // Tous les processus sont arrivés, les débloquer tous
                System.out.println(">>> BARRIÈRE ATTEINTE : Tous les processus sont arrivés !");
                releaseAllFromBarrier(activeProcesses);
            } else {
                // Attendre que les autres arrivent
                try {
                    barrierLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            atBarrier = false;
        }

        System.out.println("Processus " + processId + " repart de la barrière de synchronisation");
    }

    /**
     * Vérifie si ce processus est actuellement à la barrière.
     *
     * @return true si le processus est à la barrière
     */
    public boolean isAtBarrier() {
        return atBarrier;
    }

    /**
     * Libère tous les processus de la barrière.
     */
    private void releaseAllFromBarrier(Set<Integer> activeProcesses) {
        for (Integer id : activeProcesses) {
            Com process = processRegistry.getProcess(id);
            if (process != null && process.atBarrier) {
                process.barrierLatch.countDown();
            }
        }
    }

    // ============= COMMUNICATION SYNCHRONE =============

    /**
     * Diffusion synchrone bloquante.
     *
     * @param o L'objet à diffuser
     * @param from L'identifiant du processus expéditeur
     */
    public void broadcastSync(Object o, int from) {
        if (from == processId) {
            // Je suis l'expéditeur - envoyer et attendre que tous reçoivent
            inc_clock();
            String payload = (o instanceof String) ? (String) o : String.valueOf(o);
            SyncBroadcastMessage message = new SyncBroadcastMessage(processId, payload, getCurrentClock());
            bus.postEvent(message);

            // Attendre que tous les autres processus aient reçu
            syncLock = true;
            while (syncLock) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            // Je ne suis pas l'expéditeur - attendre le message de 'from'
            syncLock = true;
            while (syncLock) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("Processus " + processId + " synchronisé en broadcast avec " + from);
        }
    }

    /**
     * Envoi synchrone bloquant.
     *
     * @param o L'objet à envoyer
     * @param dest L'identifiant du processus destinataire
     */
    public void sendToSync(Object o, int dest) {
        inc_clock();
        String payload = (o instanceof String) ? (String) o : String.valueOf(o);
        SynchronizeMessage message = new SynchronizeMessage(
            processId, dest, SynchronizeMessage.SynchronizeMessageType.SendTo, payload);
        message.setEstampillage(getCurrentClock());
        bus.postEvent(message);

        // Attendre la confirmation de réception
        syncLock = true;
        while (syncLock) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Processus " + processId + " synchronisé en sendTo avec " + dest);
    }

    /**
     * Réception synchrone bloquante.
     *
     * @param o L'objet à recevoir (paramètre non utilisé)
     * @param from L'identifiant du processus expéditeur attendu
     */
    public void recvFromSync(Object o, int from) {
        // Attendre le message de 'from'
        syncLock = true;
        while (syncLock) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Processus " + processId + " synchronisé en recv avec " + from);
    }

    /**
     * Gère la réception de messages de synchronisation.
     */
    @Subscribe
    public void receiveSync(SynchronizeMessage message) {
        if (processId == message.getDest()) {
            if (message.getType() == SynchronizeMessage.SynchronizeMessageType.SendTo) {
                // Recevoir et renvoyer une confirmation
                mailbox.putMessage(message);
                updateClock(message.getTimestamp());

                SynchronizeMessage response = new SynchronizeMessage(
                    processId, message.getFrom(),
                    SynchronizeMessage.SynchronizeMessageType.Response, message.getPayload());
                response.setEstampillage(getCurrentClock());
                bus.postEvent(response);
            }
            if (message.getType() == SynchronizeMessage.SynchronizeMessageType.Response) {
                // Recevoir confirmation
                mailbox.putMessage(message);
                updateClock(message.getTimestamp());
                syncLock = false;
            }
        }
    }

    /**
     * Gère la réception de messages de diffusion synchrone.
     */
    @Subscribe
    public void onSyncBroadcastReceive(SyncBroadcastMessage message) {
        if (message.getFrom() != processId) {
            mailbox.putMessage(message);
            updateClock(message.getTimestamp());
            // TODO: Implémenter la logique de confirmation pour broadcastSync
        }
    }

    // ============= SYSTÈME DE HEARTBEAT =============

    /**
     * Démarre le système de heartbeat périodique.
     */
    private void startHeartbeat() {
        // Envoyer des heartbeats périodiques
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (heartbeatActive) {
                sendHeartbeat();
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);

        // Surveiller les autres processus
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (heartbeatActive) {
                checkForDeadProcesses();
            }
        }, HEARTBEAT_TIMEOUT, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * Envoie un message de heartbeat à tous les autres processus.
     */
    private void sendHeartbeat() {
        HeartbeatMessage heartbeat = new HeartbeatMessage(getCurrentClock(), processId, HeartbeatMessage.Type.HEARTBEAT);
        bus.postEvent(heartbeat);
    }

    /**
     * Gère la réception d'un message de heartbeat.
     */
    @Subscribe
    public void handleHeartbeatMessage(HeartbeatMessage heartbeat) {
        switch (heartbeat.getHeartbeatType()) {
            case HEARTBEAT:
                // Mettre à jour le timestamp du dernier heartbeat reçu
                lastHeartbeatTime.put(heartbeat.getSender(), System.currentTimeMillis());
                break;

            case PROCESS_DEAD_NOTIFY:
                // Un autre processus signale qu'un processus est mort
                int deadId = heartbeat.getDeadProcessId();
                processRegistry.removeProcess(deadId);
                break;
        }
    }

    /**
     * Vérifie si des processus n'ont pas envoyé de heartbeat récemment.
     */
    private void checkForDeadProcesses() {
        long currentTime = System.currentTimeMillis();
        Set<Integer> deadProcesses = ConcurrentHashMap.newKeySet();

        for (Map.Entry<Integer, Long> entry : lastHeartbeatTime.entrySet()) {
            int otherProcessId = entry.getKey();
            long lastSeen = entry.getValue();

            if (currentTime - lastSeen > HEARTBEAT_TIMEOUT &&
                processRegistry.getActiveProcesses().contains(otherProcessId)) {
                deadProcesses.add(otherProcessId);
            }
        }

        // Signaler les processus morts et déclencher la renumération
        for (int deadId : deadProcesses) {
            System.out.println("Processus " + processId + " détecte que le processus " + deadId + " est mort");
            notifyProcessDead(deadId);
            processRegistry.removeProcess(deadId);
        }

        if (!deadProcesses.isEmpty()) {
            triggerRenumbering();
        }
    }

    /**
     * Notifie les autres processus qu'un processus est mort.
     */
    private void notifyProcessDead(int deadProcessId) {
        HeartbeatMessage deathNotification = new HeartbeatMessage(
            getCurrentClock(), processId, HeartbeatMessage.Type.PROCESS_DEAD_NOTIFY, deadProcessId);
        bus.postEvent(deathNotification);
    }

    /**
     * Déclenche la renumération des processus survivants.
     */
    private void triggerRenumbering() {
        System.out.println("Processus " + processId + " déclenche la renumération");
        processRegistry.performRenumbering();
    }

    /**
     * Libère le bus d'événements et arrête les services.
     */
    public void freeBus() {
        heartbeatActive = false;
        heartbeatExecutor.shutdown();

        try {
            if (!heartbeatExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                heartbeatExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        this.bus.unRegisterSubscriber(this);
        processRegistry.removeProcess(processId);
    }
}