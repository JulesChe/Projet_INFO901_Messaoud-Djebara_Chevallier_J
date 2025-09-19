package com.mycompany.app;

import com.google.common.eventbus.Subscribe;
import java.util.concurrent.Semaphore;
import java.util.Set;

/**
 * Classe Com distribuée - Middleware pour la communication inter-processus réseau.
 *
 * Cette version utilise :
 * - DistributedEventBus pour la communication réseau
 * - AUCUNE variable statique partagée
 * - Découverte automatique des processus
 * - Communication 100% via messages sérialisables
 *
 * @author Middleware Team
 */
public class DistributedCom {
    private final NetworkConfig config;
    private final DistributedEventBus eventBus;
    private final Semaphore clockSemaphore = new Semaphore(1);
    private volatile int lamportClock = 0;

    public final Mailbox mailbox;

    // Section critique - gestion du jeton
    private volatile boolean hasToken = false;
    private final Object tokenLock = new Object();

    // Communication synchrone
    private volatile boolean syncLock = true;

    // Gestion du token
    private TokenState tokenState = TokenState.Null;

    private enum TokenState {
        Null, Request, SC, Release
    }

    /**
     * Constructeur du communicateur distribué.
     */
    public DistributedCom(NetworkConfig config) {
        this.config = config;
        this.eventBus = new DistributedEventBus(config);
        this.mailbox = new Mailbox();

        // S'enregistrer sur le bus d'événements
        this.eventBus.register(this);

        // Démarrer le bus réseau
        this.eventBus.start();

        System.out.println("DistributedCom démarré: " + config);
    }

    /**
     * Obtient l'identifiant du processus.
     */
    public int getId() {
        return config.getProcessId();
    }

    /**
     * Obtient la liste des processus connus (découverts via réseau).
     */
    public Set<ProcessEndpoint> getKnownProcesses() {
        return eventBus.getKnownProcesses();
    }

    /**
     * Incrémente l'horloge de Lamport de manière thread-safe.
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
     * Diffuse un objet à tous les processus via le réseau.
     */
    public void broadcast(String message) {
        inc_clock();
        int timestamp = getCurrentClock();
        UserMessage userMsg = new UserMessage(message, timestamp, config.getProcessId());
        eventBus.post(userMsg);
    }

    /**
     * Envoie un objet à un processus spécifique via le réseau.
     */
    public void sendTo(String message, int destination) {
        inc_clock();
        int timestamp = getCurrentClock();
        UserMessage userMsg = new UserMessage(message, timestamp, config.getProcessId(), destination);
        eventBus.post(userMsg);
    }

    /**
     * Reçoit un message utilisateur via l'EventBus distribué.
     */
    @Subscribe
    public void onReceiveUserMessage(UserMessage message) {
        if (message.isForProcess(config.getProcessId())) {
            // Mettre à jour l'horloge de Lamport
            if (message.getTimestamp() > this.lamportClock) {
                updateClock(message.getTimestamp());
            }
            inc_clock();

            mailbox.putMessage(message);

            String msgType = message.isBroadcast() ? "broadcast" : "direct";
            System.out.println("Processus " + config.getProcessId() + " reçoit message " + msgType + ": " + message.getPayload());
            System.out.println("Horloge après réception: " + lamportClock);
        }
    }

    /**
     * Reçoit un message de token via l'EventBus distribué.
     */
    @Subscribe
    public void onToken(TokenMessage tokenMessage) {
        if (tokenMessage.getDestination() == config.getProcessId()) {
            System.out.println("Processus " + config.getProcessId() + " reçoit le token " +
                             (tokenState == TokenState.Request ? "BESOIN" : "PAS BESOIN"));

            if (tokenState == TokenState.Request) {
                tokenState = TokenState.SC;
                synchronized (tokenLock) {
                    hasToken = true;
                }

                while (tokenState != TokenState.Release) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            // Passer le token au processus suivant via le réseau
            passTokenToNext(tokenMessage.getToken());
        }
    }

    /**
     * Gère le passage de token entre processus via réseau.
     */
    @Subscribe
    public void onTokenPass(TokenPassMessage passMessage) {
        // Logique de passage circulaire basée sur la découverte réseau
        Set<ProcessEndpoint> knownProcesses = eventBus.getKnownProcesses();
        int currentHolder = passMessage.getCurrentHolder();

        // Trouver le prochain processus dans l'ordre des IDs
        int nextProcessId = findNextProcess(currentHolder, knownProcesses);

        if (nextProcessId == config.getProcessId()) {
            // Je suis le prochain à recevoir le token
            TokenMessage tokenMessage = new TokenMessage(passMessage.getToken(), config.getProcessId());
            eventBus.post(tokenMessage);
        }
    }

    /**
     * Trouve le prochain processus dans l'ordre circulaire.
     */
    private int findNextProcess(int currentHolder, Set<ProcessEndpoint> processes) {
        int minId = Integer.MAX_VALUE;
        int nextId = Integer.MAX_VALUE;

        for (ProcessEndpoint endpoint : processes) {
            int id = endpoint.getProcessId();
            minId = Math.min(minId, id);

            if (id > currentHolder && id < nextId) {
                nextId = id;
            }
        }

        // Si aucun processus trouvé après currentHolder, reprendre au plus petit ID
        return nextId == Integer.MAX_VALUE ? minId : nextId;
    }

    /**
     * Passe le token au processus suivant via réseau.
     */
    private void passTokenToNext(Token token) {
        TokenPassMessage passMessage = new TokenPassMessage(token, config.getProcessId());
        eventBus.post(passMessage);
    }

    /**
     * Demande l'accès à la section critique.
     */
    public void requestSC() {
        if (tokenState == TokenState.Null) {
            tokenState = TokenState.Request;
        }

        while (tokenState != TokenState.SC) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("Processus " + config.getProcessId() + " entre en section critique");
    }

    /**
     * Libère la section critique.
     */
    public void releaseSC() {
        tokenState = TokenState.Release;
        System.out.println("Processus " + config.getProcessId() + " libère la section critique");
    }

    /**
     * Envoie un message synchrone et attend la réponse.
     */
    public void sendToSync(String message, int destination) {
        inc_clock();
        SynchronizeMessage syncMsg = new SynchronizeMessage(
            config.getProcessId(), destination,
            SynchronizeMessage.SynchronizeMessageType.SendTo, message);
        syncMsg.setEstampillage(lamportClock);
        eventBus.post(syncMsg);

        // Attendre la réponse
        syncLock = true;
        while (syncLock) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Processus " + config.getProcessId() + " synchronisé en sendTo avec " + destination);
    }

    /**
     * Attend un message synchrone d'un processus spécifique.
     */
    public void recvFromSync(String message, int from) {
        // Attendre le message de 'from'
        syncLock = true;
        while (syncLock) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Processus " + config.getProcessId() + " synchronisé en Recv avec " + from);
    }

    /**
     * Diffusion synchrone via réseau.
     */
    public void broadcastSync(String message, int from) {
        if (from == config.getProcessId()) {
            inc_clock();
            SyncBroadcastMessage syncMsg = new SyncBroadcastMessage(config.getProcessId(), message, lamportClock);
            eventBus.post(syncMsg);
            syncLock = true;
        } else {
            // Attendre le message de 'from'
            while (syncLock) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("Synchronisé en broadcast");
        }
    }

    /**
     * Reçoit un message de synchronisation via réseau.
     */
    @Subscribe
    public void receiveSync(SynchronizeMessage message) {
        if (config.getProcessId() == message.getDest()) {
            if (message.getType() == SynchronizeMessage.SynchronizeMessageType.SendTo) {
                // Recevoir et renvoyer une confirmation
                mailbox.putMessage(message);
                if (message.getTimestamp() > lamportClock) {
                    updateClock(message.getTimestamp());
                }
                inc_clock();

                SynchronizeMessage response = new SynchronizeMessage(
                    config.getProcessId(), message.getFrom(),
                    SynchronizeMessage.SynchronizeMessageType.Response, message.getPayload());
                response.setEstampillage(lamportClock);
                eventBus.post(response);
            }
            if (message.getType() == SynchronizeMessage.SynchronizeMessageType.Response) {
                // Recevoir confirmation
                mailbox.putMessage(message);
                if (message.getTimestamp() > lamportClock) {
                    updateClock(message.getTimestamp());
                }
                inc_clock();
                syncLock = false;
            }
        }
    }

    /**
     * Reçoit un message de diffusion synchrone via réseau.
     */
    @Subscribe
    public void onSyncBroadcastReceive(SyncBroadcastMessage message) {
        if (message.getFrom() != config.getProcessId()) {
            mailbox.putMessage(message);
            if (message.getTimestamp() > lamportClock) {
                updateClock(message.getTimestamp());
            }
            inc_clock();
        }
    }

    /**
     * Synchronise tous les processus.
     */
    public void synchronize() {
        System.out.println("Processus " + config.getProcessId() + " se synchronise");
    }

    /**
     * Initialise le token pour ce processus.
     */
    public void initToken() {
        TokenMessage message = new TokenMessage(new Token("Section critique"), config.getProcessId());
        eventBus.post(message);
    }

    /**
     * Ajoute du temps à l'horloge de Lamport.
     */
    public void addTimetoClock(int time) {
        try {
            clockSemaphore.acquire();
            lamportClock += time;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            clockSemaphore.release();
        }
    }

    /**
     * Met à jour l'horloge de Lamport.
     */
    public void setClock(int time) {
        try {
            clockSemaphore.acquire();
            lamportClock = time;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            clockSemaphore.release();
        }
    }

    /**
     * Arrête le communicateur et nettoie les ressources réseau.
     */
    public void shutdown() {
        eventBus.unregister(this);
        eventBus.stop();
        System.out.println("DistributedCom arrêté pour processus " + config.getProcessId());
    }
}