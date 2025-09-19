package com.mycompany.app;

import java.io.Serializable;

/**
 * Message de diffusion synchrone.
 * Utilisé pour les opérations broadcastSync.
 * Sérialisable pour permettre l'envoi sur le réseau.
 */
public class SyncBroadcastMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final int from;

    /**
     * Constructeur pour un message de diffusion synchrone.
     *
     * @param from L'ID du processus expéditeur
     * @param payload Le contenu du message (doit être sérialisable)
     * @param timestamp L'estampille temporelle
     */
    public SyncBroadcastMessage(int from, Serializable payload, int timestamp) {
        super(payload, timestamp, from, false);
        this.from = from;
    }

    /**
     * Obtient l'ID du processus expéditeur.
     *
     * @return L'ID de l'expéditeur
     */
    public int getFrom() {
        return from;
    }

    @Override
    public String toString() {
        return String.format("SyncBroadcastMessage{from=%d, payload=%s, timestamp=%d}",
                           from, getPayload(), getTimestamp());
    }
}