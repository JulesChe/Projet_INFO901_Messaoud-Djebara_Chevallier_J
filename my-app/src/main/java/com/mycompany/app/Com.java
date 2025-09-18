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
    private static final AtomicInteger nextId = new AtomicInteger(0);

    private final int processId;
    private final Semaphore clockSemaphore;
    private volatile int lamportClock;

    public final Mailbox mailbox;

    /**
     * Constructeur du communicateur.
     * Initialise l'horloge de Lamport et la boîte aux lettres.
     */
    public Com() {
        this.processId = nextId.getAndIncrement();
        this.clockSemaphore = new Semaphore(1);
        this.lamportClock = 0;
        this.mailbox = new Mailbox();
        processes.put(processId, this);
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
        Message message = new Message(o, timestamp, processId);

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
        Message message = new Message(o, timestamp, processId);

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
        updateClock(message.getTimestamp());
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
}