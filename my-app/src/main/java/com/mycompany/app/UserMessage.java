package com.mycompany.app;

/**
 * Classe concrète pour les messages utilisateur.
 * Ces messages utilisent l'horloge de Lamport.
 *
 * @author Middleware Team
 */
public class UserMessage extends Message {
    private static final long serialVersionUID = 1L;

    private String syncId; // Pour communication synchrone

    /**
     * Constructeur pour un message utilisateur.
     *
     * @param payload Le contenu du message
     * @param timestamp L'estampille temporelle (horloge de Lamport)
     * @param sender L'identifiant du processus expéditeur
     */
    public UserMessage(Object payload, int timestamp, int sender) {
        super(payload, timestamp, sender, false);
    }

    public String getSyncId() { return syncId; }
    public void setSyncId(String syncId) { this.syncId = syncId; }
}