package com.mycompany.app;

import java.io.Serializable;

/**
 * Classe concrète pour les messages utilisateur.
 * Ces messages utilisent l'horloge de Lamport.
 * Sérialisable pour permettre l'envoi sur le réseau.
 *
 * @author Middleware Team
 */
public class UserMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final Integer destination;

    /**
     * Constructeur pour un message utilisateur (broadcast).
     *
     * @param payload Le contenu du message (doit être sérialisable)
     * @param timestamp L'estampille temporelle (horloge de Lamport)
     * @param sender L'identifiant du processus expéditeur
     */
    public UserMessage(Serializable payload, int timestamp, int sender) {
        super(payload, timestamp, sender, false);
        this.destination = null; // null = broadcast
    }

    /**
     * Constructeur pour un message utilisateur avec destination spécifique.
     *
     * @param payload Le contenu du message (doit être sérialisable)
     * @param timestamp L'estampille temporelle (horloge de Lamport)
     * @param sender L'identifiant du processus expéditeur
     * @param destination L'identifiant du processus destinataire
     */
    public UserMessage(Serializable payload, int timestamp, int sender, int destination) {
        super(payload, timestamp, sender, false);
        this.destination = destination;
    }

    /**
     * Obtient le destinataire du message.
     *
     * @return L'ID du destinataire ou null pour un broadcast
     */
    public Integer getDestination() {
        return destination;
    }

    /**
     * Vérifie si ce message est destiné à un processus spécifique.
     *
     * @param processId L'ID du processus à vérifier
     * @return true si le message est pour ce processus
     */
    public boolean isForProcess(int processId) {
        return destination == null || destination.equals(processId);
    }

    /**
     * Vérifie si ce message est un broadcast.
     *
     * @return true si c'est un broadcast
     */
    public boolean isBroadcast() {
        return destination == null;
    }
}