package com.mycompany.app;

import java.io.Serializable;

/**
 * Message système pour le heartbeat et la détection de pannes.
 * Ces messages ne doivent PAS impacter l'horloge de Lamport.
 *
 * @author Middleware Team
 */
public class HeartbeatMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final Type heartbeatType;
    private final int deadProcessId; // Utilisé pour PROCESS_DEAD_NOTIFY

    /**
     * Types de messages de heartbeat.
     */
    public enum Type {
        HEARTBEAT,          // Message de vie périodique
        PROCESS_DEAD_NOTIFY // Notification qu'un processus est mort
    }

    /**
     * Constructeur pour un message de heartbeat simple.
     *
     * @param timestamp L'estampille temporelle
     * @param sender L'identifiant du processus expéditeur
     * @param type Le type de message de heartbeat
     */
    public HeartbeatMessage(int timestamp, int sender, Type type) {
        super("HEARTBEAT", timestamp, sender, true); // true = message système
        this.heartbeatType = type;
        this.deadProcessId = -1;
    }

    /**
     * Constructeur pour une notification de processus mort.
     *
     * @param timestamp L'estampille temporelle
     * @param sender L'identifiant du processus expéditeur
     * @param type Le type de message (doit être PROCESS_DEAD_NOTIFY)
     * @param deadProcessId L'ID du processus déclaré mort
     */
    public HeartbeatMessage(int timestamp, int sender, Type type, int deadProcessId) {
        super("PROCESS_DEAD", timestamp, sender, true); // true = message système
        this.heartbeatType = type;
        this.deadProcessId = deadProcessId;
    }

    /**
     * Obtient le type de message de heartbeat.
     *
     * @return Le type de message
     */
    public Type getHeartbeatType() {
        return heartbeatType;
    }

    /**
     * Obtient l'ID du processus déclaré mort.
     * Valide uniquement pour le type PROCESS_DEAD_NOTIFY.
     *
     * @return L'ID du processus mort ou -1 si non applicable
     */
    public int getDeadProcessId() {
        return deadProcessId;
    }

    @Override
    public String toString() {
        if (heartbeatType == Type.PROCESS_DEAD_NOTIFY) {
            return String.format("HeartbeatMessage{type=%s, sender=%d, deadProcess=%d}",
                               heartbeatType, getSender(), deadProcessId);
        } else {
            return String.format("HeartbeatMessage{type=%s, sender=%d}",
                               heartbeatType, getSender());
        }
    }
}