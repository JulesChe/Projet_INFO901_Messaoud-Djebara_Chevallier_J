package com.mycompany.app;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

/**
 * Boîte aux lettres pour stocker les messages asynchrones.
 * Thread-safe pour permettre l'accès concurrent.
 *
 * @author Middleware Team
 */
public class Mailbox {
    private final ConcurrentLinkedQueue<Message> messages;
    private final Semaphore messageAvailable;

    /**
     * Constructeur de la boîte aux lettres.
     */
    public Mailbox() {
        this.messages = new ConcurrentLinkedQueue<>();
        this.messageAvailable = new Semaphore(0);
    }

    /**
     * Ajoute un message dans la boîte aux lettres.
     *
     * @param message Le message à ajouter
     */
    public void putMessage(Message message) {
        messages.offer(message);
        messageAvailable.release();
    }

    /**
     * Récupère un message de la boîte aux lettres de manière bloquante.
     * Bloque jusqu'à ce qu'un message soit disponible.
     *
     * @return Le message récupéré
     * @throws InterruptedException Si l'attente est interrompue
     */
    public Message getMessage() throws InterruptedException {
        messageAvailable.acquire();
        return messages.poll();
    }

    /**
     * Récupère un message de la boîte aux lettres de manière non-bloquante.
     *
     * @return Le message récupéré ou null si aucun message n'est disponible
     */
    public Message getMessageNonBlocking() {
        if (messageAvailable.tryAcquire()) {
            return messages.poll();
        }
        return null;
    }

    /**
     * Vérifie si la boîte aux lettres est vide.
     *
     * @return true si vide, false sinon
     */
    public boolean isEmpty() {
        return messages.isEmpty();
    }

    /**
     * Retourne le nombre de messages dans la boîte aux lettres.
     *
     * @return Le nombre de messages
     */
    public int size() {
        return messages.size();
    }
}