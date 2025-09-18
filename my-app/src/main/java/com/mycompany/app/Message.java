package com.mycompany.app;

/**
 * Classe abstraite représentant un message générique dans le middleware.
 * Tous les messages échangés dans le système héritent de cette classe.
 *
 * @author Middleware Team
 */
public abstract class Message {
    protected Object payload;
    protected int timestamp;
    protected int sender;
    protected boolean isSystemMessage;

    /**
     * Constructeur pour un message utilisateur.
     *
     * @param payload Le contenu du message
     * @param timestamp L'estampille temporelle (horloge de Lamport)
     * @param sender L'identifiant du processus expéditeur
     */
    public Message(Object payload, int timestamp, int sender) {
        this.payload = payload;
        this.timestamp = timestamp;
        this.sender = sender;
        this.isSystemMessage = false;
    }

    /**
     * Constructeur pour un message système ou utilisateur.
     *
     * @param payload Le contenu du message
     * @param timestamp L'estampille temporelle (horloge de Lamport)
     * @param sender L'identifiant du processus expéditeur
     * @param isSystemMessage true si c'est un message système, false sinon
     */
    public Message(Object payload, int timestamp, int sender, boolean isSystemMessage) {
        this.payload = payload;
        this.timestamp = timestamp;
        this.sender = sender;
        this.isSystemMessage = isSystemMessage;
    }

    /**
     * @return Le contenu du message
     */
    public Object getPayload() {
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