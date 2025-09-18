package com.mycompany.app;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Classe Com (Communicateur) - Middleware pour la communication distribuée.
 *
 * Cette classe implémente un middleware complet avec :
 * - Horloge de Lamport synchronisée
 * - Boîte aux lettres pour messages asynchrones
 * - Section critique distribuée avec jeton sur anneau
 * - Synchronisation de barrière
 * - Communication synchrone et asynchrone
 * - Système de numérotation automatique
 * - Heartbeat pour détection de pannes
 *
 * @author Middleware Team
 */
public class Com {

    private static final Map<Integer, Com> processes = new ConcurrentHashMap<>();
    private static final AtomicInteger nextId = new AtomicInteger(0);
    private static final Object idLock = new Object();

    private final int processId;
    private final Semaphore clockSemaphore;
    private volatile int lamportClock;

    public final Mailbox mailbox;

    private volatile boolean hasToken;
    private final Object tokenLock = new Object();
    private final Thread tokenManager;

    private final Map<String, CountDownLatch> syncBarriers = new ConcurrentHashMap<>();
    private final Map<String, Object> syncResults = new ConcurrentHashMap<>();

    private final Thread heartbeatThread;
    private volatile boolean active = true;
    private final Map<Integer, Long> lastHeartbeat = new ConcurrentHashMap<>();
    private static final long HEARTBEAT_INTERVAL = 2000;
    private static final long HEARTBEAT_TIMEOUT = 5000;

    /**
     * Constructeur du communicateur.
     * Initialise l'horloge de Lamport, la boîte aux lettres, et démarre les threads système.
     */
    public Com() {
        this.processId = assignProcessId();
        this.clockSemaphore = new Semaphore(1);
        this.lamportClock = 0;
        this.mailbox = new Mailbox();
        this.hasToken = (processId == 0);

        processes.put(processId, this);

        this.tokenManager = new Thread(this::manageToken);
        this.tokenManager.setDaemon(true);
        this.tokenManager.start();

        this.heartbeatThread = new Thread(this::heartbeatLoop);
        this.heartbeatThread.setDaemon(true);
        this.heartbeatThread.start();
    }

    /**
     * Assigne un identifiant unique au processus.
     *
     * @return L'identifiant assigné
     */
    private int assignProcessId() {
        synchronized (idLock) {
            return nextId.getAndIncrement();
        }
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
     * Demande l'accès à la section critique distribuée.
     * Bloque jusqu'à l'obtention du jeton.
     */
    public void requestSC() {
        synchronized (tokenLock) {
            while (!hasToken) {
                try {
                    tokenLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Libère la section critique distribuée.
     * Passe le jeton au processus suivant.
     */
    public void releaseSC() {
        synchronized (tokenLock) {
            if (hasToken) {
                hasToken = false;
                passToken();
            }
        }
    }

    /**
     * Passe le jeton au processus suivant dans l'anneau.
     */
    private void passToken() {
        List<Integer> activeProcesses = getActiveProcesses();
        if (activeProcesses.size() <= 1) return;

        int currentIndex = activeProcesses.indexOf(processId);
        int nextIndex = (currentIndex + 1) % activeProcesses.size();
        int nextProcess = activeProcesses.get(nextIndex);

        Com nextCom = processes.get(nextProcess);
        if (nextCom != null) {
            TokenMessage tokenMsg = new TokenMessage(processId);
            nextCom.receiveMessage(tokenMsg);
        }
    }

    /**
     * Gère la réception du jeton dans un thread séparé.
     */
    private void manageToken() {
        while (active) {
            try {
                Thread.sleep(100);
                Message message = mailbox.getMessageNonBlocking();
                if (message instanceof TokenMessage) {
                    synchronized (tokenLock) {
                        hasToken = true;
                        tokenLock.notifyAll();
                    }
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Synchronise tous les processus actifs.
     * Bloque jusqu'à ce que tous les processus aient appelé cette méthode.
     */
    public void synchronize() {
        String barrierId = "sync_" + System.currentTimeMillis();
        List<Integer> activeProcesses = getActiveProcesses();
        int expectedCount = activeProcesses.size();

        CountDownLatch barrier = new CountDownLatch(expectedCount);

        for (int pid : activeProcesses) {
            Com process = processes.get(pid);
            if (process != null) {
                process.syncBarriers.put(barrierId, barrier);
            }
        }

        SyncMessage syncMsg = new SyncMessage("BARRIER_READY_" + barrierId, processId);
        for (int pid : activeProcesses) {
            Com process = processes.get(pid);
            if (process != null && process.processId != this.processId) {
                process.receiveMessage(syncMsg);
            }
        }

        barrier.countDown();

        try {
            barrier.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (int pid : activeProcesses) {
            Com process = processes.get(pid);
            if (process != null) {
                process.syncBarriers.remove(barrierId);
            }
        }
    }

    /**
     * Diffusion synchrone : l'expéditeur attend que tous reçoivent.
     *
     * @param o L'objet à diffuser
     * @param from L'identifiant de l'expéditeur
     */
    public void broadcastSync(Object o, int from) {
        if (processId == from) {
            String syncId = "broadcast_" + System.currentTimeMillis();
            List<Integer> activeProcesses = getActiveProcesses();
            CountDownLatch ackLatch = new CountDownLatch(activeProcesses.size() - 1);

            inc_clock();
            int timestamp = getCurrentClock();
            UserMessage message = new UserMessage(o, timestamp, processId);

            for (int pid : activeProcesses) {
                if (pid != processId) {
                    Com process = processes.get(pid);
                    if (process != null) {
                        process.receiveMessage(message);
                        process.receiveMessage(new SyncMessage("SYNC_ACK_" + syncId, pid));
                    }
                }
            }

            try {
                ackLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            try {
                Message message;
                do {
                    message = mailbox.getMessage();
                } while (message.getSender() != from || message.isSystemMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Envoi synchrone à un destinataire spécifique.
     *
     * @param o L'objet à envoyer
     * @param dest L'identifiant du destinataire
     */
    public void sendToSync(Object o, int dest) {
        inc_clock();
        int timestamp = getCurrentClock();
        UserMessage message = new UserMessage(o, timestamp, processId);

        Com destProcess = processes.get(dest);
        if (destProcess != null) {
            destProcess.receiveMessage(message);
            String syncId = "sync_" + processId + "_" + dest + "_" + System.currentTimeMillis();
            destProcess.receiveMessage(new SyncMessage("SYNC_SEND_ACK_" + syncId, dest));

            try {
                Message ackMsg;
                do {
                    ackMsg = mailbox.getMessage();
                } while (!(ackMsg instanceof SyncMessage) ||
                         !ackMsg.getPayload().toString().startsWith("SYNC_SEND_ACK_"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Réception synchrone depuis un expéditeur spécifique.
     *
     * @param from L'identifiant de l'expéditeur
     * @return Le message reçu
     */
    public Message recevFromSync(int from) {
        try {
            Message message;
            do {
                message = mailbox.getMessage();
            } while (message.getSender() != from || message.isSystemMessage());

            String syncId = "sync_" + from + "_" + processId + "_" + System.currentTimeMillis();
            Com fromProcess = processes.get(from);
            if (fromProcess != null) {
                fromProcess.receiveMessage(new SyncMessage("SYNC_RECV_ACK_" + syncId, processId));
            }

            return message;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Obtient la liste des processus actifs.
     *
     * @return Liste des identifiants des processus actifs
     */
    private List<Integer> getActiveProcesses() {
        List<Integer> activeProcesses = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<Integer, Com> entry : processes.entrySet()) {
            int pid = entry.getKey();
            Com process = entry.getValue();

            if (process.active) {
                Long lastHb = lastHeartbeat.get(pid);
                if (lastHb == null || (currentTime - lastHb) < HEARTBEAT_TIMEOUT) {
                    activeProcesses.add(pid);
                }
            }
        }

        Collections.sort(activeProcesses);
        return activeProcesses;
    }

    /**
     * Boucle de heartbeat pour maintenir la détection de processus actifs.
     */
    private void heartbeatLoop() {
        while (active) {
            try {
                lastHeartbeat.put(processId, System.currentTimeMillis());

                HeartbeatMessage hbMsg = new HeartbeatMessage(processId);
                for (Com process : processes.values()) {
                    if (process.processId != this.processId) {
                        process.receiveMessage(hbMsg);
                    }
                }

                Thread.sleep(HEARTBEAT_INTERVAL);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Arrête le communicateur et nettoie les ressources.
     */
    public void shutdown() {
        active = false;
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
        if (tokenManager != null) {
            tokenManager.interrupt();
        }
        processes.remove(processId);
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
}