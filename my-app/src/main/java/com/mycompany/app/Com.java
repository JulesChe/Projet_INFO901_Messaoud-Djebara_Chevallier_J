package com.mycompany.app;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Classe Com (Communicateur) - Middleware pour la communication distribuée.
 *
 * Cette classe implémente un middleware simple avec :
 * - Horloge de Lamport synchronisée
 * - Boîte aux lettres pour messages asynchrones
 * - Communication entre processus
 * - Système de numérotation automatique
 *
 * @author Middleware Team
 */
public class Com {

    // Registre local des processus connus (remplace la variable statique)
    private final Map<Integer, Com> knownProcesses = new ConcurrentHashMap<>();

    // Registre temporaire global pour la simulation locale (sera supprimé en production réelle)
    // En production, les processus communiqueraient via réseau
    private static final Map<Integer, Com> localSimulation = new ConcurrentHashMap<>();

    private final int processId;
    private final Semaphore clockSemaphore;
    private volatile int lamportClock;

    public final Mailbox mailbox;

    // Section critique - gestion du jeton
    private volatile boolean hasToken;
    private volatile boolean wantsToEnterCS;
    private final Object tokenLock = new Object();
    private final Semaphore csAccess = new Semaphore(0);

    // Barrière de synchronisation distribuée
    private volatile int localBarrierGeneration = 0;
    private volatile boolean atBarrier = false;
    private volatile int barrierCoordinatorId = -1;
    private final Set<Integer> processesAtBarrier = new HashSet<>();
    private volatile CountDownLatch barrierLatch = new CountDownLatch(1);
    private final Object barrierLock = new Object();

    // Communication synchrone
    private final Map<String, CountDownLatch> pendingSyncOperations = new ConcurrentHashMap<>();
    private final Map<String, Set<Integer>> broadcastAcksPending = new ConcurrentHashMap<>();
    private final Map<String, SyncMessage> pendingSyncMessages = new ConcurrentHashMap<>();
    private final AtomicInteger syncIdCounter = new AtomicInteger(0);

    // Numérotation automatique distribuée
    private volatile boolean numberingInProgress = false;
    private final Map<Integer, Integer> participantRandomNumbers = new ConcurrentHashMap<>();
    private final CountDownLatch numberingLatch = new CountDownLatch(1);
    private final Object numberingLock = new Object();

    // Système de heartbeat et détection de pannes
    private final Map<Integer, Long> lastHeartbeatTime = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(2);
    private static final long HEARTBEAT_INTERVAL = 2000; // 2 secondes
    private static final long HEARTBEAT_TIMEOUT = 6000;  // 6 secondes
    private volatile boolean heartbeatActive = true;

    // Protocole de découverte distribuée
    private final ScheduledExecutorService discoveryExecutor = Executors.newScheduledThreadPool(1);
    private static final long DISCOVERY_INTERVAL = 5000; // 5 secondes


    /**
     * Constructeur du communicateur.
     * Initialise l'horloge de Lamport et la boîte aux lettres.
     * Utilise l'algorithme de numérotation automatique distribuée.
     */
    public Com() {
        this.processId = getDistributedProcessId();
        this.clockSemaphore = new Semaphore(1);
        this.lamportClock = 0;
        this.mailbox = new Mailbox();
        this.hasToken = false;
        this.wantsToEnterCS = false;

        // Enregistrement local pour simulation (en production, ce serait via réseau)
        localSimulation.put(processId, this);
        knownProcesses.put(processId, this);

        // Enregistrer ce processus auprès du gestionnaire de jeton
        TokenManager.getInstance().registerProcess(processId, this);

        // Démarrer le système de heartbeat
        startHeartbeat();

        // Démarrer le protocole de découverte
        startDiscovery();

        // Annoncer notre présence
        announcePresence();
    }

    /**
     * Obtient l'identifiant du processus.
     *
     * @return L'identifiant du processus
     */
    public int getProcessId() {
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
     * Obtient l'horloge de Lamport actuelle.
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

    /**
     * Diffuse un objet à tous les autres processus de manière asynchrone.
     *
     * @param o L'objet à diffuser
     */
    public void broadcast(Object o) {
        inc_clock();
        int timestamp = getCurrentClock();
        UserMessage message = new UserMessage(o, timestamp, processId);

        // Utiliser la vue locale des processus
        for (Com process : knownProcesses.values()) {
            if (process.processId != this.processId) {
                process.receiveMessage(message);
            }
        }

        // Pour la simulation locale
        for (Com process : localSimulation.values()) {
            if (process.processId != this.processId && !knownProcesses.containsKey(process.processId)) {
                process.receiveMessage(message);
            }
        }
    }

    /**
     * Envoie un objet à un processus spécifique de manière asynchrone.
     *
     * @param o L'objet à envoyer
     * @param dest L'identifiant du processus destinataire
     */
    public void sendTo(Object o, int dest) {
        inc_clock();
        int timestamp = getCurrentClock();
        UserMessage message = new UserMessage(o, timestamp, processId);

        Com destProcess = knownProcesses.get(dest);
        if (destProcess == null) {
            destProcess = localSimulation.get(dest);
        }
        if (destProcess != null) {
            destProcess.receiveMessage(message);
        }
    }

    /**
     * Reçoit un message et le place dans la boîte aux lettres.
     * Ne met à jour l'horloge que pour les messages non-système.
     * Gère spécialement les messages système.
     *
     * @param message Le message reçu
     */
    private void receiveMessage(Message message) {
        if (!message.isSystemMessage()) {
            updateClock(message.getTimestamp());
        }

        // Traitement spécial pour les messages système
        if (message instanceof HeartbeatMessage) {
            handleHeartbeatMessage((HeartbeatMessage) message);
        } else if (message instanceof DiscoveryMessage) {
            handleDiscoveryMessage((DiscoveryMessage) message);
        } else if (message instanceof BarrierMessage) {
            handleBarrierMessage((BarrierMessage) message, null);
        } else {
            mailbox.putMessage(message);
        }
    }

    /**
     * Obtient le nombre total de processus enregistrés.
     *
     * @return Le nombre de processus
     */
    public int getProcessCount() {
        return knownProcesses.size();
    }

    /**
     * Obtient la liste de tous les processus.
     *
     * @return Map des processus (id -> Com)
     */
    public Map<Integer, Com> getAllProcesses() {
        return new HashMap<>(knownProcesses);
    }

    /**
     * Demande l'accès à la section critique.
     * Bloque jusqu'à obtention du jeton.
     */
    public void requestSC() {
        synchronized (tokenLock) {
            wantsToEnterCS = true;
            System.out.println("Processus " + processId + " demande l'accès à la section critique");

            // Si le processus a déjà le jeton, il peut entrer directement
            if (hasToken) {
                System.out.println("Processus " + processId + " a déjà le jeton, accès immédiat à la SC");
                return;
            }
        }

        // Attendre le jeton
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
     */
    public void releaseSC() {
        synchronized (tokenLock) {
            wantsToEnterCS = false;

            if (hasToken) {
                System.out.println("Processus " + processId + " libère la section critique et passe le jeton");
                hasToken = false;
                TokenManager.getInstance().passToken();
            }
        }
    }

    /**
     * Reçoit un message de jeton (message système).
     *
     * @param tokenMessage Le message contenant le jeton
     */
    public void receiveTokenMessage(TokenMessage tokenMessage) {
        synchronized (tokenLock) {
            hasToken = true;
            System.out.println("Processus " + processId + " a reçu le jeton de " + tokenMessage.getSender());

            // Si le processus veut entrer en section critique, lui donner accès
            if (wantsToEnterCS) {
                csAccess.release();
            }
        }
    }

    /**
     * Synchronise tous les processus (barrière de synchronisation distribuée).
     * Utilise un coordinateur élu pour gérer la barrière.
     *
     * Algorithme basé sur les concepts de @CM/LaBarriereDeSynchro.pdf :
     * 1) Élection d'un coordinateur (plus petit ID)
     * 2) Chaque processus notifie son arrivée au coordinateur
     * 3) Le coordinateur attend tous les processus puis broadcast la libération
     */
    public void synchronize() {
        System.out.println("Processus " + processId + " arrive à la barrière de synchronisation");

        // Élire le coordinateur (processus avec le plus petit ID)
        electBarrierCoordinator();

        synchronized (barrierLock) {
            atBarrier = true;
            localBarrierGeneration++;
            int currentGeneration = localBarrierGeneration;

            // Réinitialiser le latch pour cette barrière
            barrierLatch = new CountDownLatch(1);

            if (processId == barrierCoordinatorId) {
                // Je suis le coordinateur
                handleBarrierAsCoordinator(currentGeneration);
            } else {
                // Je suis un participant
                participateInBarrier(currentGeneration);
            }

            atBarrier = false;
        }

        System.out.println("Processus " + processId + " repart de la barrière de synchronisation");
    }

    /**
     * Vérifie si ce processus est actuellement à la barrière.
     *
     * @return true si le processus est à la barrière, false sinon
     */
    public boolean isAtBarrier() {
        return atBarrier;
    }

    /**
     * Obtient le nombre de processus actuellement à la barrière.
     *
     * @return Le nombre de processus à la barrière
     */
    public int getProcessesAtBarrier() {
        synchronized (barrierLock) {
            return processesAtBarrier.size();
        }
    }

    // === COMMUNICATION SYNCHRONE ===

    /**
     * Diffusion synchrone - bloque jusqu'à ce que tous les processus aient reçu le message.
     *
     * @param o L'objet à diffuser
     * @param from L'identifiant du processus expéditeur
     */
    public void broadcastSync(Object o, int from) {
        if (processId == from) {
            // Je suis l'expéditeur - envoyer et attendre les accusés de réception
            inc_clock();
            int timestamp = getCurrentClock();
            String syncId = processId + "-" + syncIdCounter.getAndIncrement();

            Set<Integer> expectedAcks = new HashSet<>();
            for (Integer pid : knownProcesses.keySet()) {
                if (pid != processId) {
                    expectedAcks.add(pid);
                }
            }

            if (expectedAcks.isEmpty()) {
                return; // Pas d'autres processus
            }

            broadcastAcksPending.put(syncId, expectedAcks);
            CountDownLatch latch = new CountDownLatch(expectedAcks.size());
            pendingSyncOperations.put(syncId, latch);

            // Envoyer le message de broadcast
            SyncMessage syncMsg = new SyncMessage(o, timestamp, processId,
                                                SyncMessage.Type.BROADCAST_SYNC, processId, syncId);

            for (Com process : knownProcesses.values()) {
                if (process.processId != processId) {
                    process.receiveSyncMessage(syncMsg);
                }
            }

            // Attendre tous les accusés de réception
            try {
                latch.await();
                System.out.println("Processus " + processId + " : broadcastSync terminé, tous les ACK reçus");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                pendingSyncOperations.remove(syncId);
                broadcastAcksPending.remove(syncId);
            }

        } else {
            // Je ne suis pas l'expéditeur - attendre le message de 'from'
            try {
                String syncKey = "broadcast-from-" + from;
                CountDownLatch latch = new CountDownLatch(1);
                pendingSyncOperations.put(syncKey, latch);

                System.out.println("Processus " + processId + " attend broadcastSync de " + from);
                latch.await();

                SyncMessage receivedMsg = pendingSyncMessages.remove(syncKey);
                if (receivedMsg != null) {
                    System.out.println("Processus " + processId + " a reçu broadcastSync: " + receivedMsg.getPayload());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Envoi synchrone - bloque jusqu'à ce que le destinataire ait reçu le message.
     *
     * @param o L'objet à envoyer
     * @param dest L'identifiant du processus destinataire
     */
    public void sendToSync(Object o, int dest) {
        inc_clock();
        int timestamp = getCurrentClock();
        String syncId = processId + "-" + syncIdCounter.getAndIncrement();

        CountDownLatch latch = new CountDownLatch(1);
        pendingSyncOperations.put(syncId, latch);

        // Envoyer le message
        SyncMessage syncMsg = new SyncMessage(o, timestamp, processId,
                                            SyncMessage.Type.SEND_SYNC, processId, syncId);

        Com destProcess = knownProcesses.get(dest);
        if (destProcess == null) {
            destProcess = localSimulation.get(dest);
        }
        if (destProcess != null) {
            destProcess.receiveSyncMessage(syncMsg);

            // Attendre l'accusé de réception
            try {
                System.out.println("Processus " + processId + " attend ACK de sendToSync vers " + dest);
                latch.await();
                System.out.println("Processus " + processId + " : sendToSync vers " + dest + " terminé");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                pendingSyncOperations.remove(syncId);
            }
        }
    }

    /**
     * Réception synchrone - bloque jusqu'à recevoir un message de l'expéditeur spécifié.
     *
     * @param from L'identifiant du processus expéditeur attendu
     * @return Le message reçu
     */
    public SyncMessage recevFromSync(int from) {
        try {
            String syncKey = "receive-from-" + from;
            CountDownLatch latch = new CountDownLatch(1);
            pendingSyncOperations.put(syncKey, latch);

            System.out.println("Processus " + processId + " attend message synchrone de " + from);
            latch.await();

            SyncMessage receivedMsg = pendingSyncMessages.remove(syncKey);
            if (receivedMsg != null) {
                System.out.println("Processus " + processId + " a reçu message synchrone: " + receivedMsg.getPayload());
            }
            return receivedMsg;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Traite la réception d'un message de synchronisation.
     *
     * @param syncMessage Le message de synchronisation reçu
     */
    private void receiveSyncMessage(SyncMessage syncMessage) {
        // Mettre à jour l'horloge de Lamport
        if (!syncMessage.isSystemMessage()) {
            updateClock(syncMessage.getTimestamp());
        }

        switch (syncMessage.getMessageType()) {
            case BROADCAST_SYNC:
                handleBroadcastSync(syncMessage);
                break;
            case BROADCAST_ACK:
                handleBroadcastAck(syncMessage);
                break;
            case SEND_SYNC:
                handleSendSync(syncMessage);
                break;
            case SEND_ACK:
                handleSendAck(syncMessage);
                break;
        }
    }

    private void handleBroadcastSync(SyncMessage syncMessage) {
        // Stocker le message pour broadcastSync en attente
        String syncKey = "broadcast-from-" + syncMessage.getOriginalSender();
        pendingSyncMessages.put(syncKey, syncMessage);

        // Débloquer le processus en attente
        CountDownLatch latch = pendingSyncOperations.get(syncKey);
        if (latch != null) {
            latch.countDown();
        }

        // Envoyer l'accusé de réception
        inc_clock();
        int timestamp = getCurrentClock();
        SyncMessage ackMsg = new SyncMessage("ACK", timestamp, processId,
                                           SyncMessage.Type.BROADCAST_ACK,
                                           syncMessage.getOriginalSender(), syncMessage.getSyncId());

        Com senderProcess = knownProcesses.get(syncMessage.getOriginalSender());
        if (senderProcess == null) {
            senderProcess = localSimulation.get(syncMessage.getOriginalSender());
        }
        if (senderProcess != null) {
            senderProcess.receiveSyncMessage(ackMsg);
        }
    }

    private void handleBroadcastAck(SyncMessage syncMessage) {
        String syncId = syncMessage.getSyncId();
        Set<Integer> pendingAcks = broadcastAcksPending.get(syncId);

        if (pendingAcks != null) {
            pendingAcks.remove(syncMessage.getSender());

            if (pendingAcks.isEmpty()) {
                // Tous les ACK reçus
                CountDownLatch latch = pendingSyncOperations.get(syncId);
                if (latch != null) {
                    while (latch.getCount() > 0) {
                        latch.countDown();
                    }
                }
            }
        }
    }

    private void handleSendSync(SyncMessage syncMessage) {
        // Stocker le message pour recevFromSync en attente
        String syncKey = "receive-from-" + syncMessage.getOriginalSender();
        pendingSyncMessages.put(syncKey, syncMessage);

        // Débloquer le processus en attente
        CountDownLatch latch = pendingSyncOperations.get(syncKey);
        if (latch != null) {
            latch.countDown();
        }

        // Envoyer l'accusé de réception
        inc_clock();
        int timestamp = getCurrentClock();
        SyncMessage ackMsg = new SyncMessage("ACK", timestamp, processId,
                                           SyncMessage.Type.SEND_ACK,
                                           syncMessage.getOriginalSender(), syncMessage.getSyncId());

        Com senderProcess = knownProcesses.get(syncMessage.getOriginalSender());
        if (senderProcess == null) {
            senderProcess = localSimulation.get(syncMessage.getOriginalSender());
        }
        if (senderProcess != null) {
            senderProcess.receiveSyncMessage(ackMsg);
        }
    }

    private void handleSendAck(SyncMessage syncMessage) {
        String syncId = syncMessage.getSyncId();
        CountDownLatch latch = pendingSyncOperations.get(syncId);

        if (latch != null) {
            latch.countDown();
        }
    }

    /**
     * Obtient un ID de processus unique via l'algorithme de numérotation distribuée.
     * Basé sur les concepts du cours NumérotationAutomatique.pdf :
     * 1. Génération d'un nombre aléatoire
     * 2. Échange avec autres processus
     * 3. Résolution des conflits
     * 4. Attribution de l'ID selon la position triée
     *
     * @return L'ID unique assigné au processus
     */
    private int getDistributedProcessId() {
        synchronized (knownProcesses) {
            // Si c'est le premier processus, il obtient l'ID 0
            if (localSimulation.isEmpty()) {
                return 0;
            }

            // Algorithme de numérotation distribuée
            Random random = new Random();
            int myRandomNumber = random.nextInt(100000) + (int)(System.nanoTime() % 1000);

            // Demander les nombres aléatoires des autres processus
            Map<Integer, Integer> allRandomNumbers = new ConcurrentHashMap<>();
            allRandomNumbers.put(-1, myRandomNumber); // -1 pour moi temporairement

            CountDownLatch responseLatch = new CountDownLatch(localSimulation.size());

            // Envoyer requests aux processus existants
            for (Com otherProcess : localSimulation.values()) {
                NumberingMessage request = new NumberingMessage(0, -1, NumberingMessage.Type.NUMBERING_REQUEST, myRandomNumber);
                otherProcess.handleNumberingMessage(request, allRandomNumbers, responseLatch);
            }

            try {
                // Attendre les réponses avec timeout
                responseLatch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Trier les nombres pour déterminer la position
            List<Map.Entry<Integer, Integer>> sortedEntries = new ArrayList<>(allRandomNumbers.entrySet());
            sortedEntries.sort((a, b) -> {
                int cmp = Integer.compare(a.getValue(), b.getValue());
                if (cmp == 0) {
                    // En cas d'égalité, utiliser l'ID temporaire (-1 pour nous)
                    return Integer.compare(a.getKey(), b.getKey());
                }
                return cmp;
            });

            // Trouver ma position dans le tri
            int myPosition = 0;
            for (Map.Entry<Integer, Integer> entry : sortedEntries) {
                if (entry.getKey() == -1) {
                    break;
                }
                myPosition++;
            }

            // Les IDs existants occupent déjà des positions, trouver le prochain disponible
            Set<Integer> usedIds = new HashSet<>();
            for (Com process : knownProcesses.values()) {
                usedIds.add(process.processId);
            }

            int finalId = 0;
            while (usedIds.contains(finalId)) {
                finalId++;
            }

            return finalId;
        }
    }

    /**
     * Gère les messages de numérotation distribuée.
     */
    private void handleNumberingMessage(NumberingMessage msg, Map<Integer, Integer> allNumbers, CountDownLatch latch) {
        switch (msg.getMessageType()) {
            case NUMBERING_REQUEST:
                // Répondre avec mon nombre aléatoire
                allNumbers.put(this.processId, getCurrentClock());
                NumberingMessage response = new NumberingMessage(getCurrentClock(), this.processId,
                                                                NumberingMessage.Type.NUMBERING_RESPONSE, getCurrentClock());
                latch.countDown();
                break;
            case NUMBERING_RESPONSE:
                allNumbers.put(msg.getSender(), msg.getRandomNumber());
                latch.countDown();
                break;
        }
    }

    /**
     * Démarre le système de heartbeat périodique.
     * Envoie des messages de vie toutes les HEARTBEAT_INTERVAL millisecondes.
     * Surveille également les autres processus pour détecter les pannes.
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

        for (Com process : knownProcesses.values()) {
            if (process.processId != this.processId) {
                process.receiveMessage(heartbeat);
            }
        }
    }

    /**
     * Gère la réception d'un message de heartbeat.
     */
    private void handleHeartbeatMessage(HeartbeatMessage heartbeat) {
        switch (heartbeat.getHeartbeatType()) {
            case HEARTBEAT:
                // Mettre à jour le timestamp du dernier heartbeat reçu
                lastHeartbeatTime.put(heartbeat.getSender(), System.currentTimeMillis());
                break;

            case PROCESS_DEAD_NOTIFY:
                // Un autre processus signale qu'un processus est mort
                int deadId = heartbeat.getDeadProcessId();
                removeDeadProcess(deadId);
                break;

            case RENUMBER_REQUEST:
                // Demande de renumération suite à une panne
                handleRenumberRequest();
                break;
        }
    }

    /**
     * Vérifie si des processus n'ont pas envoyé de heartbeat récemment.
     */
    private void checkForDeadProcesses() {
        long currentTime = System.currentTimeMillis();
        Set<Integer> deadProcesses = new HashSet<>();

        for (Map.Entry<Integer, Long> entry : lastHeartbeatTime.entrySet()) {
            int otherProcessId = entry.getKey();
            long lastSeen = entry.getValue();

            if (currentTime - lastSeen > HEARTBEAT_TIMEOUT && knownProcesses.containsKey(otherProcessId)) {
                deadProcesses.add(otherProcessId);
            }
        }

        // Signaler les processus morts et déclencher la renumération
        for (int deadId : deadProcesses) {
            System.out.println("Processus " + processId + " détecte que le processus " + deadId + " est mort");
            notifyProcessDead(deadId);
            removeDeadProcess(deadId);
        }

        if (!deadProcesses.isEmpty()) {
            // Déclencher la renumération
            triggerRenumbering();
        }
    }

    /**
     * Notifie les autres processus qu'un processus est mort.
     */
    private void notifyProcessDead(int deadProcessId) {
        HeartbeatMessage deathNotification = new HeartbeatMessage(getCurrentClock(), processId,
                                                                 HeartbeatMessage.Type.PROCESS_DEAD_NOTIFY, deadProcessId);

        for (Com process : knownProcesses.values()) {
            if (process.processId != this.processId && process.processId != deadProcessId) {
                process.receiveMessage(deathNotification);
            }
        }
    }

    /**
     * Supprime un processus mort de toutes les structures.
     */
    private void removeDeadProcess(int deadProcessId) {
        knownProcesses.remove(deadProcessId);
        localSimulation.remove(deadProcessId);
        lastHeartbeatTime.remove(deadProcessId);
        TokenManager.getInstance().unregisterProcess(deadProcessId);
    }

    /**
     * Déclenche la renumération des processus survivants.
     */
    private void triggerRenumbering() {
        System.out.println("Processus " + processId + " déclenche la renumération");

        // Envoyer demande de renumération à tous les processus survivants
        HeartbeatMessage renumberRequest = new HeartbeatMessage(getCurrentClock(), processId,
                                                               HeartbeatMessage.Type.RENUMBER_REQUEST);

        for (Com process : knownProcesses.values()) {
            if (process.processId != this.processId) {
                process.receiveMessage(renumberRequest);
            }
        }

        // Effectuer la renumération
        performRenumbering();
    }

    /**
     * Effectue la renumération des processus survivants.
     * Les IDs sont réassignés de manière consécutive en commençant par 0.
     */
    private void performRenumbering() {
        synchronized (knownProcesses) {
            // Récupérer tous les processus survivants triés par leur ID actuel
            List<Com> survivingProcesses = new ArrayList<>(knownProcesses.values());
            survivingProcesses.sort((a, b) -> Integer.compare(a.processId, b.processId));

            // Réassigner les IDs de manière consécutive
            Map<Integer, Integer> oldToNewIdMapping = new HashMap<>();
            int newId = 0;

            for (Com process : survivingProcesses) {
                int oldId = process.processId;
                oldToNewIdMapping.put(oldId, newId);
                newId++;
            }

            // Appliquer la renumération
            Map<Integer, Com> newProcessesMap = new ConcurrentHashMap<>();
            for (Com process : survivingProcesses) {
                int oldId = process.processId;
                int assignedNewId = oldToNewIdMapping.get(oldId);

                // Mettre à jour l'ID du processus
                try {
                    java.lang.reflect.Field processIdField = Com.class.getDeclaredField("processId");
                    processIdField.setAccessible(true);
                    processIdField.set(process, assignedNewId);
                } catch (Exception e) {
                    System.err.println("Erreur lors de la renumération: " + e.getMessage());
                }

                newProcessesMap.put(assignedNewId, process);

                // Réenregistrer auprès du TokenManager
                TokenManager.getInstance().unregisterProcess(oldId);
                TokenManager.getInstance().registerProcess(assignedNewId, process);
            }

            // Remplacer la map des processus
            knownProcesses.clear();
            knownProcesses.putAll(newProcessesMap);
            // Mise à jour de la simulation locale aussi
            localSimulation.clear();
            localSimulation.putAll(newProcessesMap);

            System.out.println("Renumération terminée. Processus " + processId + " a maintenant l'ID: " + this.processId);
        }
    }

    /**
     * Gère une demande de renumération reçue d'un autre processus.
     */
    private void handleRenumberRequest() {
        // Participer à la renumération
        performRenumbering();
    }

    /**
     * Arrête le communicateur et nettoie les ressources.
     */
    public void shutdown() {
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

        TokenManager.getInstance().unregisterProcess(processId);
        knownProcesses.remove(processId);
        localSimulation.remove(processId);

        // Nettoyer le protocole de découverte
        cleanupDiscovery();

        // Nettoyer les opérations de synchronisation en attente
        for (CountDownLatch latch : pendingSyncOperations.values()) {
            while (latch.getCount() > 0) {
                latch.countDown();
            }
        }
        pendingSyncOperations.clear();
        broadcastAcksPending.clear();
        pendingSyncMessages.clear();
    }

    // ================ NOUVEAUX PROTOCOLES DISTRIBUÉS ================

    /**
     * Démarre le protocole de découverte distribuée.
     * Envoie périodiquement la liste des processus connus.
     */
    private void startDiscovery() {
        discoveryExecutor.scheduleAtFixedRate(() -> {
            if (heartbeatActive) {
                shareKnownProcesses();
            }
        }, DISCOVERY_INTERVAL, DISCOVERY_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * Annonce la présence de ce processus à tous les autres.
     */
    private void announcePresence() {
        DiscoveryMessage announce = new DiscoveryMessage(getCurrentClock(), processId,
                                                        DiscoveryMessage.Type.ANNOUNCE);

        // Broadcast à tous les processus connus
        for (Com process : localSimulation.values()) {
            if (process.processId != this.processId) {
                process.handleDiscoveryMessage(announce);
            }
        }
    }

    /**
     * Partage la liste des processus connus avec les autres.
     */
    private void shareKnownProcesses() {
        Set<Integer> knownIds = new HashSet<>(knownProcesses.keySet());
        DiscoveryMessage listMsg = new DiscoveryMessage(getCurrentClock(), processId,
                                                       DiscoveryMessage.Type.PROCESS_LIST, knownIds);

        for (Com process : knownProcesses.values()) {
            if (process.processId != this.processId) {
                process.handleDiscoveryMessage(listMsg);
            }
        }
    }

    /**
     * Gère la réception d'un message de découverte.
     */
    private void handleDiscoveryMessage(DiscoveryMessage msg) {
        switch (msg.getDiscoveryType()) {
            case ANNOUNCE:
                // Un nouveau processus s'annonce
                Com announcer = localSimulation.get(msg.getSender());
                if (announcer != null && !knownProcesses.containsKey(msg.getSender())) {
                    knownProcesses.put(msg.getSender(), announcer);
                    System.out.println("Processus " + processId + " découvre le processus " + msg.getSender());
                }
                break;

            case PROCESS_LIST:
                // Mise à jour de notre vue avec les processus connus de l'expéditeur
                if (msg.getKnownProcesses() != null) {
                    for (Integer pid : msg.getKnownProcesses()) {
                        if (!knownProcesses.containsKey(pid)) {
                            Com process = localSimulation.get(pid);
                            if (process != null) {
                                knownProcesses.put(pid, process);
                            }
                        }
                    }
                }
                break;

            case PROCESS_LEAVING:
                // Un processus quitte le système
                knownProcesses.remove(msg.getSender());
                break;
        }
    }

    /**
     * Élit un coordinateur pour la barrière (processus avec le plus petit ID).
     */
    private void electBarrierCoordinator() {
        int minId = processId;
        for (Integer pid : knownProcesses.keySet()) {
            if (pid < minId) {
                minId = pid;
            }
        }
        barrierCoordinatorId = minId;
        System.out.println("Processus " + processId + " élit " + barrierCoordinatorId + " comme coordinateur de barrière");
    }

    /**
     * Gère la barrière en tant que coordinateur.
     */
    private void handleBarrierAsCoordinator(int generation) {
        System.out.println("Processus " + processId + " (coordinateur) gère la barrière génération " + generation);

        // Ajouter soi-même à la barrière
        processesAtBarrier.add(processId);

        // Attendre que tous les processus arrivent
        CountDownLatch coordinatorLatch = new CountDownLatch(knownProcesses.size() - 1);

        // Envoyer une demande de statut à tous les autres processus
        for (Com process : knownProcesses.values()) {
            if (process.processId != this.processId) {
                BarrierMessage statusRequest = new BarrierMessage(getCurrentClock(), processId,
                    BarrierMessage.Type.BARRIER_STATUS_REQUEST, generation, barrierCoordinatorId);
                process.handleBarrierMessage(statusRequest, coordinatorLatch);
            }
        }

        try {
            // Attendre les réponses avec timeout
            boolean allArrived = coordinatorLatch.await(10, TimeUnit.SECONDS);

            if (allArrived || processesAtBarrier.size() == knownProcesses.size()) {
                System.out.println(">>> BARRIÈRE ATTEINTE : Tous les processus sont arrivés !");

                // Broadcast la libération
                Set<Integer> arrivedProcesses = new HashSet<>(processesAtBarrier);
                BarrierMessage release = new BarrierMessage(getCurrentClock(), processId,
                    BarrierMessage.Type.BARRIER_RELEASE, generation, arrivedProcesses, barrierCoordinatorId);

                for (Com process : knownProcesses.values()) {
                    if (process.processId != this.processId) {
                        process.handleBarrierMessage(release, null);
                    }
                }

                // Réinitialiser pour la prochaine barrière
                processesAtBarrier.clear();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Participe à la barrière en tant que non-coordinateur.
     */
    private void participateInBarrier(int generation) {
        System.out.println("Processus " + processId + " participe à la barrière génération " + generation);

        // Notifier le coordinateur de notre arrivée
        Com coordinator = knownProcesses.get(barrierCoordinatorId);
        if (coordinator != null && coordinator.processId != this.processId) {
            BarrierMessage arrive = new BarrierMessage(getCurrentClock(), processId,
                BarrierMessage.Type.ARRIVE_AT_BARRIER, generation, barrierCoordinatorId);
            coordinator.handleBarrierMessage(arrive, null);
        }

        // Attendre la libération
        try {
            CountDownLatch participantLatch = new CountDownLatch(1);
            barrierLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Gère la réception d'un message de barrière.
     */
    private void handleBarrierMessage(BarrierMessage msg, CountDownLatch latch) {
        switch (msg.getBarrierType()) {
            case ARRIVE_AT_BARRIER:
                // Un processus arrive à la barrière
                if (processId == barrierCoordinatorId) {
                    processesAtBarrier.add(msg.getSender());
                    if (latch != null) {
                        latch.countDown();
                    }
                }
                break;

            case BARRIER_RELEASE:
                // Le coordinateur libère la barrière
                if (msg.getBarrierGeneration() == localBarrierGeneration) {
                    barrierLatch.countDown();
                }
                break;

            case BARRIER_STATUS_REQUEST:
                // Le coordinateur demande notre statut
                if (atBarrier) {
                    BarrierMessage response = new BarrierMessage(getCurrentClock(), processId,
                        BarrierMessage.Type.ARRIVE_AT_BARRIER, msg.getBarrierGeneration(), msg.getCoordinatorId());
                    Com coordinator = knownProcesses.get(msg.getCoordinatorId());
                    if (coordinator != null) {
                        coordinator.handleBarrierMessage(response, latch);
                    }
                }
                break;
        }
    }

    /**
     * Nettoie les ressources du protocole de découverte.
     */
    private void cleanupDiscovery() {
        discoveryExecutor.shutdown();
        try {
            if (!discoveryExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                discoveryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            discoveryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Annoncer le départ
        DiscoveryMessage leaving = new DiscoveryMessage(getCurrentClock(), processId,
                                                       DiscoveryMessage.Type.PROCESS_LEAVING);
        for (Com process : knownProcesses.values()) {
            if (process.processId != this.processId) {
                process.handleDiscoveryMessage(leaving);
            }
        }
    }
}