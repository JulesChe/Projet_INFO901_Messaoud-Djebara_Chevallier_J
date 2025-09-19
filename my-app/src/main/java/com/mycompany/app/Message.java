package com.mycompany.app;

import java.io.Serializable;

/**
 * Classe abstraite représentant un message générique dans le middleware.
 * Tous les messages échangés dans le système héritent de cette classe.
 * Sérialisable pour permettre l'envoi sur le réseau.
 *
 * @author Middleware Team
 */
public abstract class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    protected Serializable payload;
    protected int timestamp;
    protected int sender;
    protected boolean isSystemMessage;

    /**
     * Constructeur pour un message utilisateur.
     *
     * @param payload Le contenu du message (doit être sérialisable)
     * @param timestamp L'estampille temporelle (horloge de Lamport)
     * @param sender L'identifiant du processus expéditeur
     */
    public Message(Serializable payload, int timestamp, int sender) {
        this.payload = payload;
        this.timestamp = timestamp;
        this.sender = sender;
        this.isSystemMessage = false;
    }

    /**
     * Constructeur pour un message système ou utilisateur.
     *
     * @param payload Le contenu du message (doit être sérialisable)
     * @param timestamp L'estampille temporelle (horloge de Lamport)
     * @param sender L'identifiant du processus expéditeur
     * @param isSystemMessage true si c'est un message système, false sinon
     */
    public Message(Serializable payload, int timestamp, int sender, boolean isSystemMessage) {
        this.payload = payload;
        this.timestamp = timestamp;
        this.sender = sender;
        this.isSystemMessage = isSystemMessage;
    }

    /**
     * @return Le contenu du message
     */
    public Serializable getPayload() {
        return payload;
    }

    /**
     * @return L'estampille temporelle du message
     */
    public int getTimestamp() {
        return timestamp;
    }

    /**
     * @return L'identifiant du processus expéditeur
     */
    public int getSender() {
        return sender;
    }

    /**
     * @return true si c'est un message système, false sinon
     */
    public boolean isSystemMessage() {
        return isSystemMessage;
    }

    /**
     * Définit si le message est un message système.
     *
     * @param isSystemMessage true pour un message système, false pour un message utilisateur
     */
    public void setSystemMessage(boolean isSystemMessage) {
        this.isSystemMessage = isSystemMessage;
    }

    @Override
    public String toString() {
        return String.format("Message{payload=%s, timestamp=%d, sender=%d, system=%b}",
                           payload, timestamp, sender, isSystemMessage);
    }
}