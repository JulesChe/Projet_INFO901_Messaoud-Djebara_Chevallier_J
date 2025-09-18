package com.mycompany.app;

/**
 * Classe pour les messages de synchronisation.
 * Ces messages utilisent l'horloge de Lamport.
 *
 * @author Middleware Team
 */
public class SyncMessage extends Message {

    /**
     * Constructeur pour un message de synchronisation.
     *
     * @param payload Le contenu du message
     * @param timestamp L'estampille temporelle (horloge de Lamport)
     * @param sender L'identifiant du processus expéditeur
     */
    public SyncMessage(Object payload, int timestamp, int sender) {
        super(payload, timestamp, sender, false);
    }

    @Override
    public String toString() {
        return String.format("SyncMessage{payload=%s, timestamp=%d, sender=%d}",
                           payload, timestamp, sender);
    }
}