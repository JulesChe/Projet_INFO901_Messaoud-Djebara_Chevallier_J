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

    private static final Map<Integer, Com> processes = new ConcurrentHashMap<>();

    private final int processId;
    private final Semaphore clockSemaphore;
    private volatile int lamportClock;

    public final Mailbox mailbox;

    // Section critique - gestion du jeton
    private volatile boolean hasToken;
    private volatile boolean wantsToEnterCS;
    private final Object tokenLock = new Object();
    private final Semaphore csAccess = new Semaphore(0);

    // Barrière de synchronisation
    private static volatile int processesAtBarrier = 0;
    private static volatile int barrierGeneration = 0;
    private static final Object barrierLock = new Object();
    private volatile boolean atBarrier = false;

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

        processes.put(processId, this);

        // Enregistrer ce processus auprès du gestionnaire de jeton
        TokenManager.getInstance().registerProcess(processId, this);
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

        for (Com process : processes.values()) {
            if (process.processId != this.processId) {
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

        Com destProcess = processes.get(dest);
        if (destProcess != null) {
            destProcess.receiveMessage(message);
        }
    }

    /**
     * Reçoit un message et le place dans la boîte aux lettres.
     * Ne met à jour l'horloge que pour les messages non-système.
     *
     * @param message Le message reçu
     */
    private void receiveMessage(Message message) {
        if (!message.isSystemMessage()) {
            updateClock(message.getTimestamp());
        }
        mailbox.putMessage(message);
    }

    /**
     * Obtient le nombre total de processus enregistrés.
     *
     * @return Le nombre de processus
     */
    public static int getProcessCount() {
        return processes.size();
    }

    /**
     * Obtient la liste de tous les processus.
     *
     * @return Map des processus (id -> Com)
     */
    public static Map<Integer, Com> getAllProcesses() {
        return new HashMap<>(processes);
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
     * Synchronise tous les processus (barrière de synchronisation).
     * Attend que tous les processus aient invoqué cette méthode pour tous les débloquer.
     *
     * Algorithme basé sur les concepts de @CM/LaBarriereDeSynchro.pdf :
     * 1) Le processus s'arrête à la barrière
     * 2) Attend que tous les autres processus arrivent
     * 3) Tous repartent ensemble
     */
    public void synchronize() {
        System.out.println("Processus " + processId + " arrive à la barrière de synchronisation");

        synchronized (barrierLock) {
            // Marquer ce processus comme étant à la barrière
            atBarrier = true;
            processesAtBarrier++;
            int currentGeneration = barrierGeneration;
            int totalProcesses = processes.size();

            System.out.println("Processus " + processId + " attend (" + processesAtBarrier + "/" + totalProcesses + " processus à la barrière)");

            if (processesAtBarrier == totalProcesses) {
                // Tous les processus sont arrivés - débloquer tout le monde
                System.out.println(">>> BARRIÈRE ATTEINTE : Tous les processus (" + totalProcesses + ") sont arrivés, déblocage général !");

                // Préparer la prochaine génération de barrière
                processesAtBarrier = 0;
                barrierGeneration++;

                // Réveiller tous les processus en attente
                barrierLock.notifyAll();
            } else {
                // Attendre que tous les autres arrivent
                while (processesAtBarrier > 0 && currentGeneration == barrierGeneration) {
                    try {
                        barrierLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        atBarrier = false;
                        return;
                    }
                }
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
    public static int getProcessesAtBarrier() {
        synchronized (barrierLock) {
            return processesAtBarrier;
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
            for (Integer pid : processes.keySet()) {
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

            for (Com process : processes.values()) {
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

        Com destProcess = processes.get(dest);
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

        Com senderProcess = processes.get(syncMessage.getOriginalSender());
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

        Com senderProcess = processes.get(syncMessage.getOriginalSender());
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
        synchronized (processes) {
            // Si c'est le premier processus, il obtient l'ID 0
            if (processes.isEmpty()) {
                return 0;
            }

            // Algorithme de numérotation distribuée
            Random random = new Random();
            int myRandomNumber = random.nextInt(100000) + (int)(System.nanoTime() % 1000);

            // Demander les nombres aléatoires des autres processus
            Map<Integer, Integer> allRandomNumbers = new ConcurrentHashMap<>();
            allRandomNumbers.put(-1, myRandomNumber); // -1 pour moi temporairement

            CountDownLatch responseLatch = new CountDownLatch(processes.size());

            // Envoyer requests aux processus existants
            for (Com otherProcess : processes.values()) {
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
            for (Com process : processes.values()) {
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
     * Arrête le communicateur et nettoie les ressources.
     */
    public void shutdown() {
        TokenManager.getInstance().unregisterProcess(processId);
        processes.remove(processId);

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
}