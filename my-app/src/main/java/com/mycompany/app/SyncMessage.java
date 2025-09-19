package com.mycompany.app;

/**
 * Classe pour les messages de synchronisation dans les communications synchrones.
 * Ces messages sont utilisés pour les accusés de réception et la coordination.
 *
 * @author Middleware Team
 */
public class SyncMessage extends Message {
    private static final long serialVersionUID = 1L;

    public enum Type {
        BROADCAST_SYNC,
        BROADCAST_ACK,
        SEND_SYNC,
        SEND_ACK
    }

    private final Type messageType;
    private final int originalSender;
    private final String syncId;

    /**
     * Constructeur pour un message de synchronisation.
     *
     * @param payload Le contenu du message
     * @param timestamp L'estampille temporelle (horloge de Lamport)
     * @param sender L'identifiant du processus expéditeur
     * @param messageType Le type de message de synchronisation
     * @param originalSender L'expéditeur original (pour les ACK)
     * @param syncId Identifiant unique pour la synchronisation
     */
    public SyncMessage(Object payload, int timestamp, int sender, Type messageType, int originalSender, String syncId) {
        super(payload, timestamp, sender, false); // Messages de sync affectent l'horloge
        this.messageType = messageType;
        this.originalSender = originalSender;
        this.syncId = syncId;
    }

    /**
     * @return Le type de message de synchronisation
     */
    public Type getMessageType() {
        return messageType;
    }

    /**
     * @return L'expéditeur original du message
     */
    public int getOriginalSender() {
        return originalSender;
    }

    /**
     * @return L'identifiant de synchronisation
     */
    public String getSyncId() {
        return syncId;
    }

    @Override
    public String toString() {
        return String.format("SyncMessage{type=%s, payload=%s, timestamp=%d, sender=%d, originalSender=%d, syncId=%s}",
                           messageType, payload, timestamp, sender, originalSender, syncId);
    }
}