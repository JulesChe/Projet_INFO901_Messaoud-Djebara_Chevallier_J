package com.mycompany.app;

import java.util.*;
import java.util.concurrent.*;

/**
 * Service de token distribué remplaçant TokenManager.
 * Chaque processus a sa propre instance et communique via EventBus.
 *
 * Architecture distribuée basée sur les concepts du cours :
 * - Pas de singleton statique
 * - Token circulaire distribué
 * - Communication via messages sérialisés
 *
 * @author Middleware Team
 */
public class DistributedTokenService {

    private final int processId;
    private final DistributedEventBus eventBus;

    // État local du token
    private volatile boolean hasToken;
    private volatile boolean isInitiator;

    // Processus connus pour l'anneau du token
    private final Map<Integer, ProcessEndpoint> tokenRing = new ConcurrentHashMap<>();

    // Exécuteur pour les tâches asynchrones
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private volatile boolean running = false;

    /**
     * Constructeur du service de token distribué.
     *
     * @param processId L'ID de ce processus
     * @param eventBus Le bus de messages distribué
     */
    public DistributedTokenService(int processId, DistributedEventBus eventBus) {
        this.processId = processId;
        this.eventBus = eventBus;
        this.hasToken = false;
        this.isInitiator = false;
    }

    /**
     * Démarre le service de token.
     * Le processus avec le plus petit ID initie le token.
     */
    public void start() {
        running = true;

        // S'enregistrer pour recevoir les messages de token
        eventBus.registerSubscriber(this);

        // Attendre un peu pour la découverte, puis décider qui initie le token
        executor.schedule(() -> {
            synchronized (tokenRing) {
                // Mettre à jour l'anneau avec les endpoints connus
                tokenRing.putAll(eventBus.getKnownEndpoints());

                // Le processus avec le plus petit ID initie le token
                int smallestId = processId;
                for (Integer id : tokenRing.keySet()) {
                    if (id < smallestId) {
                        smallestId = id;
                    }
                }

                if (smallestId == processId) {
                    isInitiator = true;
                    initiateToken();
                }
            }
        }, 3000, TimeUnit.MILLISECONDS); // Attendre 3 secondes pour la découverte
    }

    /**
     * Initie le token dans le système.
     */
    private void initiateToken() {
        hasToken = true;
        System.out.println("Processus " + processId + " initie le token distribué");

        // Démarrer la circulation automatique
        scheduleTokenCirculation();
    }

    /**
     * Programme la circulation automatique du token.
     */
    private void scheduleTokenCirculation() {
        if (!running) return;

        executor.schedule(() -> {
            if (hasToken && running) {
                // Passer le token après un délai
                passTokenToNext();
                scheduleTokenCirculation(); // Reprogram pour le prochain passage
            }
        }, 5000, TimeUnit.MILLISECONDS); // Circulation toutes les 5 secondes
    }

    /**
     * Passe le token au processus suivant dans l'anneau.
     */
    public void passTokenToNext() {
        synchronized (tokenRing) {
            if (!hasToken) return;

            List<Integer> sortedIds = new ArrayList<>(tokenRing.keySet());
            Collections.sort(sortedIds);

            int currentIndex = sortedIds.indexOf(processId);
            if (currentIndex != -1 && sortedIds.size() > 1) {
                int nextIndex = (currentIndex + 1) % sortedIds.size();
                int nextProcessId = sortedIds.get(nextIndex);

                if (nextProcessId != processId) {
                    hasToken = false;
                    TokenMessage tokenMsg = new TokenMessage(processId);
                    eventBus.sendTo(nextProcessId, tokenMsg);
                    System.out.println("Token passé de " + processId + " vers " + nextProcessId);
                }
            }
        }
    }

    /**
     * Reçoit un message de token via EventBus.
     *
     * @param tokenMessage Le message de token reçu
     */
    @Subscribe
    public void onTokenReceived(TokenMessage tokenMessage) {
        if (tokenMessage.getSender() != processId) {
            hasToken = true;
            System.out.println("Processus " + processId + " reçoit le token de " + tokenMessage.getSender());

            // Reprogram la circulation
            scheduleTokenCirculation();
        }
    }

    /**
     * Met à jour l'anneau de token avec un nouvel endpoint.
     *
     * @param endpoint Le nouvel endpoint découvert
     */
    @Subscribe
    public void onEndpointDiscovered(EndpointDiscoveredMessage message) {
        ProcessEndpoint endpoint = message.getEndpoint();
        synchronized (tokenRing) {
            tokenRing.put(endpoint.getProcessId(), endpoint);
            System.out.println("TokenService: Endpoint ajouté à l'anneau: " + endpoint);
        }
    }

    /**
     * Vérifie si ce processus détient actuellement le token.
     *
     * @return true si le processus a le token, false sinon
     */
    public boolean hasToken() {
        return hasToken;
    }

    /**
     * Obtient l'ID du processus qui détient actuellement le token.
     * Dans un système distribué, cette information n'est connue que localement.
     *
     * @return L'ID du processus local s'il a le token, -1 sinon
     */
    public int getCurrentTokenHolder() {
        return hasToken ? processId : -1;
    }

    /**
     * Enregistre un processus dans l'anneau de token.
     * Dans la version distribuée, cela se fait automatiquement via la découverte.
     *
     * @param processId L'ID du processus
     * @param endpoint L'endpoint du processus
     */
    public void registerProcess(int processId, ProcessEndpoint endpoint) {
        synchronized (tokenRing) {
            tokenRing.put(processId, endpoint);
        }
    }

    /**
     * Désenregistre un processus de l'anneau de token.
     *
     * @param processId L'ID du processus à supprimer
     */
    public void unregisterProcess(int processId) {
        synchronized (tokenRing) {
            tokenRing.remove(processId);
        }
    }

    /**
     * Arrête le service de token et libère les ressources.
     */
    public void stop() {
        running = false;
        executor.shutdown();

        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("DistributedTokenService arrêté pour processus " + processId);
    }
}