package com.mycompany.app;

import java.util.*;
import java.util.concurrent.*;

/**
 * Gestionnaire de jeton pour l'exclusion mutuelle distribuée.
 * Fonctionne avec un jeton circulaire qui passe de processus en processus.
 * Le jeton part du dernier processus initialisé et circule dans l'ordre inverse des IDs.
 *
 * @author Middleware Team
 */
public class TokenManager implements Runnable {
    private static TokenManager instance;
    private static final Object instanceLock = new Object();

    private Thread tokenThread;
    private volatile boolean running;
    private volatile int currentTokenHolder;
    private final Map<Integer, Com> processes;
    private List<Integer> processRing;
    private final Object ringLock = new Object();

    /**
     * Constructeur privé pour le singleton.
     */
    private TokenManager() {
        this.processes = new ConcurrentHashMap<>();
        this.processRing = new ArrayList<>();
        this.running = false;
        this.currentTokenHolder = -1;
    }

    /**
     * Obtient l'instance unique du gestionnaire de jeton.
     *
     * @return L'instance du TokenManager
     */
    public static TokenManager getInstance() {
        if (instance == null) {
            synchronized (instanceLock) {
                if (instance == null) {
                    instance = new TokenManager();
                }
            }
        }
        return instance;
    }

    /**
     * Enregistre un processus dans l'anneau.
     *
     * @param processId L'ID du processus
     * @param com Le communicateur du processus
     */
    public void registerProcess(int processId, Com com) {
        synchronized (ringLock) {
            processes.put(processId, com);
            processRing.add(processId);

            // Trier par ordre décroissant pour que le jeton parte du dernier processus initialisé
            processRing.sort(Collections.reverseOrder());

            System.out.println("Processus " + processId + " enregistré dans l'anneau. Anneau actuel: " + processRing);

            // Si c'est le premier processus ou si l'anneau n'est pas encore démarré
            if (processRing.size() == 1 || !running) {
                currentTokenHolder = processRing.get(0); // Le plus grand ID (dernier initialisé)
                System.out.println("Jeton initialisé avec le processus " + currentTokenHolder);
            }
        }
    }

    /**
     * Démarre le gestionnaire de jeton dans un thread séparé.
     */
    public void start() {
        if (!running && processRing.size() > 0) {
            running = true;
            tokenThread = new Thread(this, "TokenManager");
            tokenThread.start();
            System.out.println("Gestionnaire de jeton démarré");
        }
    }

    /**
     * Arrête le gestionnaire de jeton.
     */
    public void stop() {
        running = false;
        if (tokenThread != null) {
            tokenThread.interrupt();
        }
    }

    /**
     * Obtient le prochain processus dans l'anneau.
     *
     * @param currentId L'ID du processus actuel
     * @return L'ID du prochain processus
     */
    private int getNextProcess(int currentId) {
        synchronized (ringLock) {
            if (processRing.isEmpty()) return -1;

            int currentIndex = processRing.indexOf(currentId);
            if (currentIndex == -1) return processRing.get(0);

            return processRing.get((currentIndex + 1) % processRing.size());
        }
    }

    /**
     * Passe le jeton au processus suivant dans l'anneau.
     */
    public void passToken() {
        synchronized (ringLock) {
            if (processRing.isEmpty()) return;

            int nextHolder = getNextProcess(currentTokenHolder);
            Com nextProcess = processes.get(nextHolder);

            if (nextProcess != null) {
                TokenMessage tokenMsg = new TokenMessage(currentTokenHolder);
                nextProcess.receiveTokenMessage(tokenMsg);

                System.out.println("Jeton passé de " + currentTokenHolder + " à " + nextHolder);
                currentTokenHolder = nextHolder;
            }
        }
    }

    /**
     * Obtient l'ID du processus qui détient actuellement le jeton.
     *
     * @return L'ID du détenteur du jeton
     */
    public int getCurrentTokenHolder() {
        return currentTokenHolder;
    }

    /**
     * Boucle principale du gestionnaire de jeton.
     */
    @Override
    public void run() {
        System.out.println("Thread gestionnaire de jeton démarré");

        while (running) {
            try {
                // Attendre un peu avant de faire circuler le jeton automatiquement
                Thread.sleep(100);

                // Le jeton circule automatiquement, mais les processus peuvent le demander
                // La circulation est gérée par les appels à passToken() depuis les processus

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("Thread gestionnaire de jeton arrêté");
    }

    /**
     * Retire un processus de l'anneau.
     *
     * @param processId L'ID du processus à retirer
     */
    public void unregisterProcess(int processId) {
        synchronized (ringLock) {
            processes.remove(processId);
            processRing.remove(Integer.valueOf(processId));

            // Si le processus qui avait le jeton est retiré, passer le jeton
            if (currentTokenHolder == processId && !processRing.isEmpty()) {
                currentTokenHolder = processRing.get(0);
                passToken();
            }
        }
    }
}