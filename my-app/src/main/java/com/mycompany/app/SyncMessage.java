package com.mycompany.app;

/**
 * Message système pour les opérations de synchronisation.
 * Ces messages n'affectent pas l'horloge de Lamport.
 *
 * @author Middleware Team
 */
public class SyncMessage extends Message {

    /**
     * Constructeur pour un message de synchronisation.
     *
     * @param syncType Le type de synchronisation (BARRIER_WAIT, BARRIER_RELEASE, etc.)
     * @param sender L'identifiant du processus expéditeur
     */
    public SyncMessage(String syncType, int sender) {
        super(syncType, 0, sender, true);
    }
}