package com.mycompany.app;

/**
 * Message de heartbeat pour la détection de processus vivants.
 * Envoyé périodiquement par chaque processus pour signaler qu'il est actif.
 * Messages système qui n'affectent pas l'horloge de Lamport utilisateur.
 *
 * @author Middleware Team
 */
public class HeartbeatMessage extends Message {

    public enum Type {
        HEARTBEAT,           // Message de vie périodique
        PROCESS_DEAD_NOTIFY, // Notification qu'un processus est mort
        RENUMBER_REQUEST     // Demande de renumération
    }

    private final Type heartbeatType;
    private final int deadProcessId;

    /**
     * Constructeur pour message de heartbeat.
     *
     * @param timestamp L'estampille temporelle
     * @param sender L'identifiant du processus expéditeur
     * @param heartbeatType Le type de message de heartbeat
     * @param deadProcessId L'ID du processus mort (pour PROCESS_DEAD_NOTIFY)
     */
    public HeartbeatMessage(int timestamp, int sender, Type heartbeatType, int deadProcessId) {
        super(null, timestamp, sender, true); // Messages système n'affectent pas l'horloge utilisateur
        this.heartbeatType = heartbeatType;
        this.deadProcessId = deadProcessId;
    }

    /**
     * Constructeur simplifié pour HEARTBEAT.
     */
    public HeartbeatMessage(int timestamp, int sender, Type heartbeatType) {
        this(timestamp, sender, heartbeatType, -1);
    }

    /**
     * @return Le type de message de heartbeat
     */
    public Type getHeartbeatType() {
        return heartbeatType;
    }

    /**
     * @return L'ID du processus mort
     */
    public int getDeadProcessId() {
        return deadProcessId;
    }

    @Override
    public String toString() {
        return String.format("HeartbeatMessage{type=%s, sender=%d, timestamp=%d, deadProcessId=%d}",
                           heartbeatType, sender, timestamp, deadProcessId);
    }
}