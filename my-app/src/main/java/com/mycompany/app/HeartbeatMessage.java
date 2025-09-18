package com.mycompany.app;

/**
 * Message système de heartbeat pour détecter les processus inactifs.
 * Ces messages n'affectent pas l'horloge de Lamport.
 *
 * @author Middleware Team
 */
public class HeartbeatMessage extends Message {

    /**
     * Constructeur pour un message de heartbeat.
     *
     * @param sender L'identifiant du processus expéditeur
     */
    public HeartbeatMessage(int sender) {
        super("HEARTBEAT", 0, sender, true);
    }
}