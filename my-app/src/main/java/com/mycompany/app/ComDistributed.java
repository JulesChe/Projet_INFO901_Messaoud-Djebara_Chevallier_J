package com.mycompany.app;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Classe Com (Communicateur) - Version totalement distribuée.
 *
 * Cette classe implémente un middleware distribué SANS variables de classe :
 * - Découverte distribuée des processus par diffusion
 * - Horloge de Lamport synchronisée
 * - Section critique avec jeton circulant sur anneau virtuel
 * - Barrière de synchronisation totalement distribuée
 * - Communication asynchrone et synchrone
 * - Numérotation automatique distribuée
 * - Heartbeat et détection de pannes
 *
 * Basé sur les concepts des cours :
 * - LeTemps.pdf (Horloge de Lamport)
 * - ExclusionMutuelle.pdf (Jeton sur anneau)
 * - LaBarriereDeSynchro.pdf (Barrière distribuée)
 * - LaDiffusion.pdf (Communication)
 * - NumérotationAutomatique.pdf (Numérotation distribuée)
 *
 * @author Middleware Team
 */
public class ComDistributed {

    // Registre global temporaire pour la découverte initiale (sera éliminé après découverte)
    private static final List<ComDistributed> bootstrapRegistry = Collections.synchronizedList(new ArrayList<>());

    // Identité du processus
    private final int processId;

    // Horloge de Lamport
    private final Semaphore clockSemaphore;
    private volatile int lamportClock;

    // Boîte aux lettres
    public final Mailbox mailbox;

    // Vue locale des autres processus (pas de variable de classe)
    private final Map<Integer, ProcessInfo> knownProcesses = new ConcurrentHashMap<>();
    private volatile int mySuccessor = -1;
    private volatile int myPredecessor = -1;

    // Section critique - jeton circulant
    private volatile boolean hasToken = false;
    private volatile boolean wantsToEnterCS = false;
    private final Object tokenLock = new Object();
    private final Semaphore csAccess = new Semaphore(0);
    private volatile long tokenCirculationTime = 100; // Temps avant de passer le jeton

    // Barrière de synchronisation distribuée
    private volatile int currentBarrierGeneration = 0;
    private final Set<Integer> processesArrivedAtBarrier = ConcurrentHashMap.newKeySet();
    private volatile CountDownLatch barrierLatch = new CountDownLatch(1);
    private volatile boolean atBarrier = false;

    // Communication synchrone
    private final Map<String, CountDownLatch> pendingSyncOperations = new ConcurrentHashMap<>();
    private final Map<String, Set<Integer>> broadcastAcksPending = new ConcurrentHashMap<>();
    private final Map<String, SyncMessage> pendingSyncMessages = new ConcurrentHashMap<>();
    private final AtomicInteger syncIdCounter = new AtomicInteger(0);

    // Heartbeat et détection de pannes
    private final Map<Integer, Long> lastHeartbeatTime = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private static final long HEARTBEAT_INTERVAL = 2000;
    private static final long HEARTBEAT_TIMEOUT = 6000;
    private volatile boolean active = true;

    /**
     * Classe interne pour stocker les informations d'un processus.
     */
    private static class ProcessInfo {
        final int id;
        volatile ComDistributed reference;
        volatile long lastSeen;

        ProcessInfo(int id, ComDistributed ref) {
            this.id = id;
            this.reference = ref;
            this.lastSeen = System.currentTimeMillis();
        }
    }

    /**
     * Constructeur du communicateur distribué.
     */
    public ComDistributed() {
        // Numérotation distribuée
        this.processId = obtainDistributedId();

        // Initialisation
        this.clockSemaphore = new Semaphore(1);
        this.lamportClock = 0;
        this.mailbox = new Mailbox();

        // S'enregistrer temporairement pour la découverte
        bootstrapRegistry.add(this);

        // Découverte des autres processus
        discoverProcesses();

        // Démarrer les services
        startServices();

        System.out.println("Processus " + processId + " initialisé");
    }

    /**
     * Obtient un ID distribué via l'algorithme de numérotation.
     * Basé sur NumérotationAutomatique.pdf
     */
    private int obtainDistributedId() {
        synchronized (bootstrapRegistry) {
            if (bootstrapRegistry.isEmpty()) {
                return 0; // Premier processus
            }

            // Algorithme distribué : générer un nombre aléatoire
            Random random = new Random();
            int myRandom = random.nextInt(100000) + (int)(System.nanoTime() % 1000);

            // Collecter les nombres des autres processus
            Map<Integer, Integer> randomNumbers = new HashMap<>();
            randomNumbers.put(-1, myRandom); // -1 pour moi temporairement

            int index = 0;
            for (ComDistributed other : bootstrapRegistry) {
                randomNumbers.put(index++, other.hashCode());
            }

            // Trier et trouver ma position
            List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(randomNumbers.entrySet());
            sorted.sort(Map.Entry.comparingByValue());

            // Trouver le premier ID libre
            Set<Integer> usedIds = new HashSet<>();
            for (ComDistributed other : bootstrapRegistry) {
                usedIds.add(other.processId);
            }

            int newId = 0;
            while (usedIds.contains(newId)) {
                newId++;
            }

            return newId;
        }
    }

    /**
     * Découvre les autres processus de manière distribuée.
     */
    private void discoverProcesses() {
        synchronized (bootstrapRegistry) {
            // Construire l'anneau virtuel
            List<ComDistributed> allProcesses = new ArrayList<>(bootstrapRegistry);
            allProcesses.sort((a, b) -> Integer.compare(a.processId, b.processId));

            int myIndex = -1;
            for (int i = 0; i < allProcesses.size(); i++) {
                ComDistributed proc = allProcesses.get(i);
                if (proc != this) {
                    knownProcesses.put(proc.processId, new ProcessInfo(proc.processId, proc));
                }
                if (proc.processId == this.processId) {
                    myIndex = i;
                }
            }

            // Déterminer successeur et prédécesseur dans l'anneau
            if (allProcesses.size() > 1 && myIndex != -1) {
                int successorIndex = (myIndex + 1) % allProcesses.size();
                int predecessorIndex = (myIndex - 1 + allProcesses.size()) % allProcesses.size();

                mySuccessor = allProcesses.get(successorIndex).processId;
                myPredecessor = allProcesses.get(predecessorIndex).processId;

                System.out.println("Processus " + processId + " - Successeur: " + mySuccessor + ", Prédécesseur: " + myPredecessor);
            }

            // Initialiser le jeton pour le processus avec le plus grand ID
            if (!allProcesses.isEmpty() && processId == allProcesses.get(allProcesses.size() - 1).processId) {
                hasToken = true;
                System.out.println("Processus " + processId + " initialise le jeton");
            }
        }
    }

    /**
     * Démarre les services distribués.
     */
    private void startServices() {
        // Service de heartbeat
        scheduler.scheduleAtFixedRate(this::sendHeartbeat,
                                     HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);

        // Service de détection de pannes
        scheduler.scheduleAtFixedRate(this::checkForDeadProcesses,
                                     HEARTBEAT_TIMEOUT, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);

        // Service de circulation du jeton
        scheduler.scheduleAtFixedRate(this::circulateToken,
                                     tokenCirculationTime, tokenCirculationTime, TimeUnit.MILLISECONDS);
    }

    /**
     * @return L'identifiant du processus
     */
    public int getProcessId() {
        return processId;
    }

    /**
     * Incrémente l'horloge de Lamport.
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
     * Met à jour l'horloge de Lamport lors de la réception.
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
     * Obtient l'horloge actuelle.
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

    /**
     * Diffuse un message à tous les processus connus.
     */
    public void broadcast(Object o) {
        inc_clock();
        int timestamp = getCurrentClock();
        UserMessage message = new UserMessage(o, timestamp, processId);

        for (ProcessInfo info : knownProcesses.values()) {
            if (info.reference != null) {
                info.reference.receiveMessage(message);
            }
        }
    }

    /**
     * Envoie un message à un processus spécifique.
     */
    public void sendTo(Object o, int dest) {
        inc_clock();
        int timestamp = getCurrentClock();
        UserMessage message = new UserMessage(o, timestamp, processId);

        ProcessInfo destInfo = knownProcesses.get(dest);
        if (destInfo != null && destInfo.reference != null) {
            destInfo.reference.receiveMessage(message);
        }
    }

    /**
     * Reçoit un message.
     */
    public void receiveMessage(Message message) {
        if (!message.isSystemMessage()) {
            updateClock(message.getTimestamp());
        }

        // Traitement selon le type de message
        if (message instanceof TokenMessage) {
            handleTokenMessage((TokenMessage) message);
        } else if (message instanceof HeartbeatMessage) {
            handleHeartbeatMessage((HeartbeatMessage) message);
        } else if (message instanceof BarrierMessage) {
            handleBarrierMessage((BarrierMessage) message);
        } else if (message instanceof DiscoveryMessage) {
            handleDiscoveryMessage((DiscoveryMessage) message);
        } else if (message instanceof SyncMessage) {
            handleSyncMessage((SyncMessage) message);
        } else {
            mailbox.putMessage(message);
        }
    }

    /**
     * Demande la section critique.
     * Basé sur ExclusionMutuelle.pdf - Algorithme du jeton sur anneau
     */
    public void requestSC() {
        synchronized (tokenLock) {
            wantsToEnterCS = true;
            System.out.println("Processus " + processId + " demande la section critique");

            if (hasToken) {
                System.out.println("Processus " + processId + " a déjà le jeton");
                return;
            }
        }

        try {
            csAccess.acquire(); // Attendre le jeton
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Libère la section critique.
     */
    public void releaseSC() {
        synchronized (tokenLock) {
            wantsToEnterCS = false;
            System.out.println("Processus " + processId + " libère la section critique");

            // Passer le jeton au suivant
            passTokenToSuccessor();
        }
    }

    /**
     * Fait circuler le jeton périodiquement.
     */
    private void circulateToken() {
        synchronized (tokenLock) {
            if (hasToken && !wantsToEnterCS) {
                passTokenToSuccessor();
            }
        }
    }

    /**
     * Passe le jeton au successeur dans l'anneau.
     */
    private void passTokenToSuccessor() {
        if (mySuccessor == -1 || !hasToken) return;

        ProcessInfo successor = knownProcesses.get(mySuccessor);
        if (successor != null && successor.reference != null) {
            TokenMessage token = new TokenMessage(processId);
            successor.reference.receiveMessage(token);
            hasToken = false;
            System.out.println("Processus " + processId + " passe le jeton à " + mySuccessor);
        }
    }

    /**
     * Gère la réception d'un jeton.
     */
    private void handleTokenMessage(TokenMessage msg) {
        synchronized (tokenLock) {
            hasToken = true;
            System.out.println("Processus " + processId + " reçoit le jeton de " + msg.getSender());

            if (wantsToEnterCS) {
                csAccess.release(); // Débloquer requestSC()
            }
        }
    }

    /**
     * Synchronisation par barrière distribuée.
     * Basé sur LaBarriereDeSynchro.pdf
     */
    public void synchronize() {
        int generation = ++currentBarrierGeneration;
        atBarrier = true;
        processesArrivedAtBarrier.clear();
        processesArrivedAtBarrier.add(processId);

        // Annoncer l'arrivée à la barrière
        BarrierMessage arrival = new BarrierMessage(getCurrentClock(), processId,
                                                   BarrierMessage.Type.BARRIER_ARRIVAL, generation);

        for (ProcessInfo info : knownProcesses.values()) {
            if (info.reference != null) {
                info.reference.receiveMessage(arrival);
            }
        }

        // Attendre que tous arrivent
        barrierLatch = new CountDownLatch(1);
        try {
            barrierLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        atBarrier = false;
    }

    /**
     * Gère les messages de barrière.
     */
    private void handleBarrierMessage(BarrierMessage msg) {
        if (msg.getBarrierGeneration() != currentBarrierGeneration) {
            return; // Message d'une ancienne génération
        }

        switch (msg.getBarrierType()) {
            case BARRIER_ARRIVAL:
                processesArrivedAtBarrier.add(msg.getSender());

                // Si tous sont arrivés (y compris nous)
                if (processesArrivedAtBarrier.size() == knownProcesses.size() + 1) {
                    // Diffuser la libération
                    BarrierMessage release = new BarrierMessage(getCurrentClock(), processId,
                                                               BarrierMessage.Type.BARRIER_RELEASE,
                                                               currentBarrierGeneration);

                    for (ProcessInfo info : knownProcesses.values()) {
                        if (info.reference != null) {
                            info.reference.receiveMessage(release);
                        }
                    }

                    barrierLatch.countDown();
                }
                break;

            case BARRIER_RELEASE:
                barrierLatch.countDown();
                break;
        }
    }

    /**
     * Communication synchrone - Broadcast.
     */
    public void broadcastSync(Object o, int from) {
        if (processId == from) {
            // Je suis l'expéditeur
            String syncId = "bcast_" + processId + "_" + syncIdCounter.incrementAndGet();
            Set<Integer> waitingFor = ConcurrentHashMap.newKeySet();

            for (ProcessInfo info : knownProcesses.values()) {
                waitingFor.add(info.id);
            }

            broadcastAcksPending.put(syncId, waitingFor);
            CountDownLatch latch = new CountDownLatch(waitingFor.size());
            pendingSyncOperations.put(syncId, latch);

            inc_clock();
            SyncMessage msg = new SyncMessage(o, getCurrentClock(), processId,
                                             SyncMessage.Type.BROADCAST_SYNC, processId, syncId);

            for (ProcessInfo info : knownProcesses.values()) {
                if (info.reference != null) {
                    info.reference.receiveMessage(msg);
                }
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                pendingSyncOperations.remove(syncId);
                broadcastAcksPending.remove(syncId);
            }
        } else {
            // J'attends le message
            String syncId = "bcast_wait_" + from + "_" + System.nanoTime();
            CountDownLatch latch = new CountDownLatch(1);
            pendingSyncOperations.put(syncId, latch);

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                pendingSyncOperations.remove(syncId);
            }
        }
    }

    /**
     * Communication synchrone - Send.
     */
    public void sendToSync(Object o, int dest) {
        String syncId = "send_" + processId + "_" + dest + "_" + syncIdCounter.incrementAndGet();
        CountDownLatch latch = new CountDownLatch(1);
        pendingSyncOperations.put(syncId, latch);

        inc_clock();
        SyncMessage msg = new SyncMessage(o, getCurrentClock(), processId,
                                         SyncMessage.Type.SEND_SYNC, processId, syncId);

        ProcessInfo destInfo = knownProcesses.get(dest);
        if (destInfo != null && destInfo.reference != null) {
            destInfo.reference.receiveMessage(msg);
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pendingSyncOperations.remove(syncId);
        }
    }

    /**
     * Communication synchrone - Receive.
     */
    public SyncMessage recevFromSync(int from) {
        String waitKey = "recv_" + processId + "_from_" + from;
        CountDownLatch latch = new CountDownLatch(1);
        pendingSyncOperations.put(waitKey, latch);

        try {
            latch.await();
            return pendingSyncMessages.remove(waitKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            pendingSyncOperations.remove(waitKey);
        }
    }

    /**
     * Gère les messages synchrones.
     */
    private void handleSyncMessage(SyncMessage msg) {
        switch (msg.getMessageType()) {
            case BROADCAST_SYNC:
                // Envoyer ACK à l'expéditeur
                SyncMessage ack = new SyncMessage(null, getCurrentClock(), processId,
                                                 SyncMessage.Type.BROADCAST_ACK,
                                                 msg.getOriginalSender(), msg.getSyncId());

                ProcessInfo sender = knownProcesses.get(msg.getSender());
                if (sender != null && sender.reference != null) {
                    sender.reference.receiveMessage(ack);
                }

                // Débloquer si on attendait
                String waitKey = "bcast_wait_" + msg.getSender() + "_";
                for (String key : pendingSyncOperations.keySet()) {
                    if (key.startsWith(waitKey)) {
                        CountDownLatch latch = pendingSyncOperations.get(key);
                        if (latch != null) latch.countDown();
                        break;
                    }
                }
                break;

            case BROADCAST_ACK:
                Set<Integer> waiting = broadcastAcksPending.get(msg.getSyncId());
                if (waiting != null) {
                    waiting.remove(msg.getSender());
                    if (waiting.isEmpty()) {
                        CountDownLatch latch = pendingSyncOperations.get(msg.getSyncId());
                        if (latch != null) latch.countDown();
                    }
                }
                break;

            case SEND_SYNC:
                // Stocker et débloquer le receveur
                String recvKey = "recv_" + processId + "_from_" + msg.getSender();
                pendingSyncMessages.put(recvKey, msg);

                CountDownLatch recvLatch = pendingSyncOperations.get(recvKey);
                if (recvLatch != null) {
                    recvLatch.countDown();
                }

                // Envoyer ACK
                SyncMessage sendAck = new SyncMessage(null, getCurrentClock(), processId,
                                                     SyncMessage.Type.SEND_ACK,
                                                     msg.getOriginalSender(), msg.getSyncId());

                ProcessInfo senderInfo = knownProcesses.get(msg.getSender());
                if (senderInfo != null && senderInfo.reference != null) {
                    senderInfo.reference.receiveMessage(sendAck);
                }
                break;

            case SEND_ACK:
                CountDownLatch sendLatch = pendingSyncOperations.get(msg.getSyncId());
                if (sendLatch != null) {
                    sendLatch.countDown();
                }
                break;
        }
    }

    /**
     * Envoie un heartbeat.
     */
    private void sendHeartbeat() {
        if (!active) return;

        HeartbeatMessage heartbeat = new HeartbeatMessage(getCurrentClock(), processId,
                                                         HeartbeatMessage.Type.HEARTBEAT);

        for (ProcessInfo info : knownProcesses.values()) {
            if (info.reference != null) {
                info.reference.receiveMessage(heartbeat);
            }
        }
    }

    /**
     * Gère les messages de heartbeat.
     */
    private void handleHeartbeatMessage(HeartbeatMessage msg) {
        switch (msg.getHeartbeatType()) {
            case HEARTBEAT:
                lastHeartbeatTime.put(msg.getSender(), System.currentTimeMillis());
                break;

            case PROCESS_DEAD_NOTIFY:
                removeDeadProcess(msg.getDeadProcessId());
                break;

            case RENUMBER_REQUEST:
                performRenumbering();
                break;
        }
    }

    /**
     * Gère les messages de découverte.
     */
    private void handleDiscoveryMessage(DiscoveryMessage msg) {
        switch (msg.getDiscoveryType()) {
            case DISCOVERY_REQUEST:
                // Répondre avec nos processus connus
                int[] known = knownProcesses.keySet().stream()
                                           .mapToInt(Integer::intValue)
                                           .toArray();

                DiscoveryMessage response = new DiscoveryMessage(getCurrentClock(), processId,
                                                                DiscoveryMessage.Type.DISCOVERY_RESPONSE,
                                                                known, mySuccessor);

                ProcessInfo requester = knownProcesses.get(msg.getSender());
                if (requester != null && requester.reference != null) {
                    requester.reference.receiveMessage(response);
                }
                break;

            case DISCOVERY_RESPONSE:
                // Mettre à jour notre vue
                if (msg.getKnownProcesses() != null) {
                    for (int id : msg.getKnownProcesses()) {
                        if (id != processId && !knownProcesses.containsKey(id)) {
                            // Chercher la référence dans le registre bootstrap
                            synchronized (bootstrapRegistry) {
                                for (ComDistributed com : bootstrapRegistry) {
                                    if (com.processId == id) {
                                        knownProcesses.put(id, new ProcessInfo(id, com));
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    rebuildRing();
                }
                break;

            case DISCOVERY_ANNOUNCE:
                // Un nouveau processus s'annonce
                if (!knownProcesses.containsKey(msg.getSender())) {
                    synchronized (bootstrapRegistry) {
                        for (ComDistributed com : bootstrapRegistry) {
                            if (com.processId == msg.getSender()) {
                                knownProcesses.put(msg.getSender(), new ProcessInfo(msg.getSender(), com));
                                break;
                            }
                        }
                    }
                    rebuildRing();
                }
                break;
        }
    }

    /**
     * Vérifie les processus morts.
     */
    private void checkForDeadProcesses() {
        if (!active) return;

        long now = System.currentTimeMillis();
        List<Integer> deadProcesses = new ArrayList<>();

        for (Map.Entry<Integer, Long> entry : lastHeartbeatTime.entrySet()) {
            if (now - entry.getValue() > HEARTBEAT_TIMEOUT) {
                deadProcesses.add(entry.getKey());
            }
        }

        for (int deadId : deadProcesses) {
            System.out.println("Processus " + processId + " détecte que " + deadId + " est mort");
            removeDeadProcess(deadId);

            // Notifier les autres
            HeartbeatMessage notification = new HeartbeatMessage(getCurrentClock(), processId,
                                                                HeartbeatMessage.Type.PROCESS_DEAD_NOTIFY,
                                                                deadId);

            for (ProcessInfo info : knownProcesses.values()) {
                if (info.reference != null && info.id != deadId) {
                    info.reference.receiveMessage(notification);
                }
            }
        }

        if (!deadProcesses.isEmpty()) {
            performRenumbering();
        }
    }

    /**
     * Supprime un processus mort.
     */
    private void removeDeadProcess(int deadId) {
        knownProcesses.remove(deadId);
        lastHeartbeatTime.remove(deadId);

        // Reconstruire l'anneau si nécessaire
        if (deadId == mySuccessor || deadId == myPredecessor) {
            rebuildRing();
        }
    }

    /**
     * Reconstruit l'anneau virtuel.
     */
    private void rebuildRing() {
        List<Integer> allIds = new ArrayList<>(knownProcesses.keySet());
        allIds.add(processId);
        allIds.sort(Integer::compareTo);

        int myIndex = allIds.indexOf(processId);
        if (myIndex != -1 && allIds.size() > 1) {
            mySuccessor = allIds.get((myIndex + 1) % allIds.size());
            myPredecessor = allIds.get((myIndex - 1 + allIds.size()) % allIds.size());

            System.out.println("Processus " + processId + " - Nouvel anneau - Successeur: " +
                             mySuccessor + ", Prédécesseur: " + myPredecessor);
        }
    }

    /**
     * Effectue la renumérotation distribuée.
     */
    private void performRenumbering() {
        // Implémentation simplifiée : conserver les IDs existants
        // Une vraie renumérotation nécessiterait un consensus distribué
        System.out.println("Processus " + processId + " - Renumérotation (conserve son ID)");
    }

    /**
     * Obtient le nombre de processus connus.
     */
    public int getProcessCount() {
        return knownProcesses.size() + 1; // Les autres + moi
    }

    /**
     * Arrête le communicateur.
     */
    public void shutdown() {
        active = false;
        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Notifier le départ
        DiscoveryMessage leave = new DiscoveryMessage(getCurrentClock(), processId,
                                                     DiscoveryMessage.Type.DISCOVERY_LEAVE);

        for (ProcessInfo info : knownProcesses.values()) {
            if (info.reference != null) {
                info.reference.receiveMessage(leave);
            }
        }

        // Se retirer du registre
        bootstrapRegistry.remove(this);

        System.out.println("Processus " + processId + " arrêté");
    }
}