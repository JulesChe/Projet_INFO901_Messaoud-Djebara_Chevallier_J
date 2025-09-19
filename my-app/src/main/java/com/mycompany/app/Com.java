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

    // Endpoints des processus connus - PLUS de références directes !
    private final Map<Integer, ProcessEndpoint> knownEndpoints = new ConcurrentHashMap<>();

    // Bus de messages distribué - remplace toutes les communications directes
    private final DistributedEventBus eventBus;

    // Service de token distribué - remplace TokenManager
    private final DistributedTokenService tokenService;

    private volatile int processId;
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
     * Constructeur du communicateur avec configuration réseau.
     * Architecture 100% distribuée - AUCUNE variable statique partagée.
     */
    public Com(NetworkConfig config) {
        this.processId = config.getProcessId();
        this.clockSemaphore = new Semaphore(1);
        this.lamportClock = 0;
        this.mailbox = new Mailbox();
        this.hasToken = false;
        this.wantsToEnterCS = false;

        // Initialiser le bus de messages distribué
        this.eventBus = new DistributedEventBus(config);

        // Initialiser le service de token distribué
        this.tokenService = new DistributedTokenService(processId, eventBus);

        // S'enregistrer pour recevoir les messages via @Subscribe
        this.eventBus.registerSubscriber(this);

        // Ajouter son propre endpoint
        ProcessEndpoint localEndpoint = new ProcessEndpoint(
            processId, config.getBindAddress(), config.getBasePort());
        knownEndpoints.put(processId, localEndpoint);

        // Démarrer les services distribués
        startHeartbeat();
        startDiscovery();
        tokenService.start();

        System.out.println("Processus " + processId + " démarré avec architecture distribuée");
    }

    /**
     * Constructeur avec numérotation automatique distribuée.
     */
    public Com() {
        this(new NetworkConfig(generateDistributedProcessId()));
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
     * Utilise le bus de messages distribué.
     *
     * @param o L'objet à diffuser
     */
    public void broadcast(Object o) {
        inc_clock();
        int timestamp = getCurrentClock();
        UserMessage message = new UserMessage(o, timestamp, processId);

        // Publier sur le bus distribué - plus de communication directe !
        eventBus.post(message);
    }

    /**
     * Envoie un objet à un processus spécifique de manière asynchrone.
     * Utilise le bus de messages distribué.
     *
     * @param o L'objet à envoyer
     * @param dest L'identifiant du processus destinataire
     */
    public void sendTo(Object o, int dest) {
        inc_clock();
        int timestamp = getCurrentClock();
        UserMessage message = new UserMessage(o, timestamp, processId);

        // Envoyer via le bus distribué à l'endpoint spécifique
        eventBus.sendTo(dest, message);
    }

    // ================ RÉCEPTION DE MESSAGES VIA EventBus ================

    /**
     * Reçoit les messages utilisateur via EventBus (@Subscribe).
     */
    @Subscribe
    public void onUserMessage(UserMessage message) {
        if (message.getSender() != this.processId) { // Éviter de recevoir ses propres messages
            updateClock(message.getTimestamp());
            mailbox.putMessage(message);
            System.out.println("Processus " + processId + " reçoit: " + message.getPayload());
        }
    }

    /**
     * Reçoit les messages de synchronisation via EventBus (@Subscribe).
     */
    @Subscribe
    public void onSyncMessage(SyncMessage message) {
        if (message.getSender() != this.processId) {
            if (!message.isSystemMessage()) {
                updateClock(message.getTimestamp());
            }
            handleSyncMessage(message);
        }
    }

    /**
     * Reçoit les messages de heartbeat via EventBus (@Subscribe).
     */
    @Subscribe
    public void onHeartbeatMessage(HeartbeatMessage message) {
        if (message.getSender() != this.processId) {
            handleHeartbeatMessage(message);
        }
    }

    /**
     * Reçoit les messages de découverte via EventBus (@Subscribe).
     */
    @Subscribe
    public void onDiscoveryMessage(DiscoveryMessage message) {
        if (message.getSender() != this.processId) {
            handleDiscoveryMessage(message);
        }
    }

    /**
     * Reçoit les messages de barrière via EventBus (@Subscribe).
     */
    @Subscribe
    public void onBarrierMessage(BarrierMessage message) {
        if (message.getSender() != this.processId) {
            handleBarrierMessage(message, null);
        }
    }

    /**
     * Reçoit les messages de token via EventBus (@Subscribe).
     */
    @Subscribe
    public void onTokenMessage(TokenMessage message) {
        if (message.getSender() != this.processId) {
            receiveTokenMessage(message);
        }
    }

    /**
     * Reçoit les notifications de découverte d'endpoints via EventBus (@Subscribe).
     */
    @Subscribe
    public void onEndpointDiscovered(EndpointDiscoveredMessage message) {
        ProcessEndpoint endpoint = message.getEndpoint();
        if (endpoint.getProcessId() != this.processId) {
            knownEndpoints.put(endpoint.getProcessId(), endpoint);
            System.out.println("Processus " + processId + " découvre l'endpoint: " + endpoint);
        }
    }

    /**
     * Obtient le nombre total de processus connus.
     *
     * @return Le nombre de processus
     */
    public int getProcessCount() {
        return knownEndpoints.size();
    }

    /**
     * Obtient la liste de tous les endpoints de processus.
     *
     * @return Map des endpoints (id -> ProcessEndpoint)
     */
    public Map<Integer, ProcessEndpoint> getAllEndpoints() {
        return new HashMap<>(knownEndpoints);
    }

    /**
     * Demande l'accès à la section critique.
     * Version distribuée avec tokenService.
     */
    public void requestSC() {
        synchronized (tokenLock) {
            wantsToEnterCS = true;
            System.out.println("Processus " + processId + " demande l'accès à la section critique");

            // Vérifier avec le service de token distribué
            if (tokenService.hasToken()) {
                hasToken = true;
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
     * Version distribuée avec tokenService.
     */
    public void releaseSC() {
        synchronized (tokenLock) {
            wantsToEnterCS = false;

            if (tokenService.hasToken()) {
                System.out.println("Processus " + processId + " libère la section critique et passe le jeton");
                hasToken = false;
                tokenService.passTokenToNext();
            }
        }
    }

    /**
     * Reçoit un message de jeton (message système).
     * Délègue au tokenService distribué.
     *
     * @param tokenMessage Le message contenant le jeton
     */
    public void receiveTokenMessage(TokenMessage tokenMessage) {
        synchronized (tokenLock) {
            // Le tokenService gère déjà la réception via @Subscribe
            // Mettre à jour l'état local
            if (tokenService.hasToken()) {
                hasToken = true;
                System.out.println("Processus " + processId + " confirme réception du jeton de " + tokenMessage.getSender());

                // Si le processus veut entrer en section critique, lui donner accès
                if (wantsToEnterCS) {
                    csAccess.release();
                }
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
            for (Integer pid : knownEndpoints.keySet()) {
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

            // Envoyer le message de broadcast via EventBus
            SyncMessage syncMsg = new SyncMessage(o, timestamp, processId,
                                                SyncMessage.Type.BROADCAST_SYNC, processId, syncId);

            eventBus.post(syncMsg);

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

        // Envoyer le message via EventBus
        SyncMessage syncMsg = new SyncMessage(o, timestamp, processId,
                                            SyncMessage.Type.SEND_SYNC, processId, syncId);

        eventBus.sendTo(dest, syncMsg);

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
     * Appelée depuis onSyncMessage (@Subscribe).
     *
     * @param syncMessage Le message de synchronisation reçu
     */
    private void handleSyncMessage(SyncMessage syncMessage) {
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

        // Envoyer l'accusé de réception via EventBus
        inc_clock();
        int timestamp = getCurrentClock();
        SyncMessage ackMsg = new SyncMessage("ACK", timestamp, processId,
                                           SyncMessage.Type.BROADCAST_ACK,
                                           syncMessage.getOriginalSender(), syncMessage.getSyncId());

        eventBus.sendTo(syncMessage.getOriginalSender(), ackMsg);
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

        // Envoyer l'accusé de réception via EventBus
        inc_clock();
        int timestamp = getCurrentClock();
        SyncMessage ackMsg = new SyncMessage("ACK", timestamp, processId,
                                           SyncMessage.Type.SEND_ACK,
                                           syncMessage.getOriginalSender(), syncMessage.getSyncId());

        eventBus.sendTo(syncMessage.getOriginalSender(), ackMsg);
    }

    private void handleSendAck(SyncMessage syncMessage) {
        String syncId = syncMessage.getSyncId();
        CountDownLatch latch = pendingSyncOperations.get(syncId);

        if (latch != null) {
            latch.countDown();
        }
    }

    /**
     * Génère un ID de processus distribué basé sur l'heure et un numéro aléatoire.
     * Plus de dépendance aux variables statiques !
     *
     * @return L'ID unique assigné au processus
     */
    private static int generateDistributedProcessId() {
        // Utilise l'heure système et un nombre aléatoire pour éviter les collisions
        return (int) (System.currentTimeMillis() % 1000) + new Random().nextInt(100);
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
     * Utilise le bus distribué.
     */
    private void sendHeartbeat() {
        HeartbeatMessage heartbeat = new HeartbeatMessage(getCurrentClock(), processId, HeartbeatMessage.Type.HEARTBEAT);

        // Publier sur le bus distribué
        eventBus.post(heartbeat);
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

            if (currentTime - lastSeen > HEARTBEAT_TIMEOUT && knownEndpoints.containsKey(otherProcessId)) {
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

        // Publier sur le bus distribué
        eventBus.post(deathNotification);
    }

    /**
     * Supprime un processus mort de toutes les structures.
     */
    private void removeDeadProcess(int deadProcessId) {
        knownEndpoints.remove(deadProcessId);
        lastHeartbeatTime.remove(deadProcessId);
        System.out.println("Processus " + deadProcessId + " supprimé des endpoints connus");
    }

    /**
     * Déclenche la renumération des processus survivants.
     */
    private void triggerRenumbering() {
        System.out.println("Processus " + processId + " déclenche la renumération");

        // Envoyer demande de renumération à tous les processus survivants
        HeartbeatMessage renumberRequest = new HeartbeatMessage(getCurrentClock(), processId,
                                                               HeartbeatMessage.Type.RENUMBER_REQUEST);

        // Publier sur le bus distribué
        eventBus.post(renumberRequest);

        // Effectuer la renumération
        performRenumbering();
    }

    /**
     * Effectue la renumération des processus survivants.
     * Version distribuée - chaque processus renumérote son propre ID.
     */
    private void performRenumbering() {
        synchronized (knownEndpoints) {
            // Dans une vraie architecture distribuée, la renumération serait plus complexe
            // Pour simplifier, on garde les IDs existants et on supprime juste les morts
            List<Integer> survivingIds = new ArrayList<>(knownEndpoints.keySet());
            Collections.sort(survivingIds);

            System.out.println("Renumération terminée. Processus survivants: " + survivingIds);
            System.out.println("Processus " + processId + " conserve son ID");
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
     * Version distribuée.
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

        // Supprimer de nos structures locales
        knownEndpoints.remove(processId);

        // Nettoyer le protocole de découverte
        cleanupDiscovery();

        // Arrêter le service de token distribué
        if (tokenService != null) {
            tokenService.stop();
        }

        // Arrêter le bus distribué
        if (eventBus != null) {
            eventBus.shutdown();
        }

        // Nettoyer les opérations de synchronisation en attente
        for (CountDownLatch latch : pendingSyncOperations.values()) {
            while (latch.getCount() > 0) {
                latch.countDown();
            }
        }
        pendingSyncOperations.clear();
        broadcastAcksPending.clear();
        pendingSyncMessages.clear();

        System.out.println("Processus " + processId + " arrêté");
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
     * Annonce la présence de ce processus.
     * Utilise le bus distribué pour la découverte.
     */
    private void announcePresence() {
        // La découverte se fait maintenant automatiquement via le multicast
        // dans DistributedEventBus, plus besoin de logique explicite ici
        System.out.println("Processus " + processId + " s'annonce via le bus distribué");
    }

    /**
     * Partage la liste des processus connus avec les autres.
     */
    private void shareKnownProcesses() {
        Set<Integer> knownIds = new HashSet<>(knownEndpoints.keySet());
        DiscoveryMessage listMsg = new DiscoveryMessage(getCurrentClock(), processId,
                                                       DiscoveryMessage.Type.PROCESS_LIST, knownIds);

        // Publier via le bus distribué
        eventBus.post(listMsg);
    }

    /**
     * Gère la réception d'un message de découverte.
     */
    private void handleDiscoveryMessage(DiscoveryMessage msg) {
        switch (msg.getDiscoveryType()) {
            case ANNOUNCE:
                // Un nouveau processus s'annonce - l'endpoint sera découvert via EventBus
                System.out.println("Processus " + processId + " reçoit annonce de " + msg.getSender());
                break;

            case PROCESS_LIST:
                // Mise à jour de notre vue avec les processus connus de l'expéditeur
                if (msg.getKnownProcesses() != null) {
                    for (Integer pid : msg.getKnownProcesses()) {
                        if (!knownEndpoints.containsKey(pid) && pid != processId) {
                            // Les endpoints seront mis à jour par le bus distribué
                            System.out.println("Processus " + processId + " apprend l'existence de " + pid);
                        }
                    }
                }
                break;

            case PROCESS_LEAVING:
                // Un processus quitte le système
                knownEndpoints.remove(msg.getSender());
                System.out.println("Processus " + msg.getSender() + " quitte le système");
                break;
        }
    }

    /**
     * Élit un coordinateur pour la barrière (processus avec le plus petit ID).
     */
    private void electBarrierCoordinator() {
        int minId = processId;
        for (Integer pid : knownEndpoints.keySet()) {
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
        CountDownLatch coordinatorLatch = new CountDownLatch(knownEndpoints.size() - 1);

        // Envoyer une demande de statut à tous les autres processus via EventBus
        BarrierMessage statusRequest = new BarrierMessage(getCurrentClock(), processId,
            BarrierMessage.Type.BARRIER_STATUS_REQUEST, generation, barrierCoordinatorId);
        eventBus.post(statusRequest);

        try {
            // Attendre les réponses avec timeout
            boolean allArrived = coordinatorLatch.await(10, TimeUnit.SECONDS);

            if (allArrived || processesAtBarrier.size() == knownEndpoints.size()) {
                System.out.println(">>> BARRIÈRE ATTEINTE : Tous les processus sont arrivés !");

                // Broadcast la libération via EventBus
                Set<Integer> arrivedProcesses = new HashSet<>(processesAtBarrier);
                BarrierMessage release = new BarrierMessage(getCurrentClock(), processId,
                    BarrierMessage.Type.BARRIER_RELEASE, generation, arrivedProcesses, barrierCoordinatorId);

                eventBus.post(release);

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

        // Notifier le coordinateur de notre arrivée via EventBus
        if (barrierCoordinatorId != this.processId) {
            BarrierMessage arrive = new BarrierMessage(getCurrentClock(), processId,
                BarrierMessage.Type.ARRIVE_AT_BARRIER, generation, barrierCoordinatorId);
            eventBus.sendTo(barrierCoordinatorId, arrive);
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
                    System.out.println("Processus " + processId + " libéré de la barrière par le coordinateur " + msg.getCoordinatorId());
                }
                break;

            case BARRIER_STATUS_REQUEST:
                // Le coordinateur demande notre statut
                if (atBarrier) {
                    BarrierMessage response = new BarrierMessage(getCurrentClock(), processId,
                        BarrierMessage.Type.ARRIVE_AT_BARRIER, msg.getBarrierGeneration(), msg.getCoordinatorId());
                    eventBus.sendTo(msg.getCoordinatorId(), response);
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

        // Annoncer le départ via EventBus
        DiscoveryMessage leaving = new DiscoveryMessage(getCurrentClock(), processId,
                                                       DiscoveryMessage.Type.PROCESS_LEAVING);
        eventBus.post(leaving);
    }
}