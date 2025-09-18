package com.mycompany.app;

/**
 * Message utilisateur standard dans le middleware.
 * Ces messages affectent l'horloge de Lamport.
 *
 * @author Middleware Team
 */
public class UserMessage extends Message {

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
}