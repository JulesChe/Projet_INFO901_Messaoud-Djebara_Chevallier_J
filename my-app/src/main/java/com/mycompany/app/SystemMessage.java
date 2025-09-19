package com.mycompany.app;

import java.util.Set;

/**
 * Message système unifié remplaçant tous les types de messages système.
 * Utilise un enum Type pour différencier les fonctionnalités.
 *
 * @author Middleware Team
 */
public class SystemMessage extends Message {
    private static final long serialVersionUID = 1L;

    public enum Type {
        TOKEN,
        HEARTBEAT,
        DISCOVERY_ANNOUNCE,
        DISCOVERY_LIST,
        BARRIER_ARRIVE,
        BARRIER_STATUS_REQUEST,
        BARRIER_RELEASE,
        SYNC_ACK,
        NUMBERING_REQUEST,
        NUMBERING_RESPONSE
    }

    private final Type type;
    private final Object payload;
    private final String syncId;
    private final int targetId;
    private final int coordinatorId;
    private final int generation;
    private final Set<Integer> processSet;

    // Constructeur pour Token
    public SystemMessage(int timestamp, int sender, Type type) {
        super(null, timestamp, sender);
        this.type = type;
        this.payload = null;
        this.syncId = null;
        this.targetId = -1;
        this.coordinatorId = -1;
        this.generation = -1;
        this.processSet = null;
    }

    // Constructeur pour Heartbeat
    public SystemMessage(int timestamp, int sender, Type type, Object payload) {
        super(null, timestamp, sender);
        this.type = type;
        this.payload = payload;
        this.syncId = null;
        this.targetId = -1;
        this.coordinatorId = -1;
        this.generation = -1;
        this.processSet = null;
    }

    // Constructeur pour Discovery
    public SystemMessage(int timestamp, int sender, Type type, Set<Integer> processSet) {
        super(null, timestamp, sender);
        this.type = type;
        this.payload = null;
        this.syncId = null;
        this.targetId = -1;
        this.coordinatorId = -1;
        this.generation = -1;
        this.processSet = processSet;
    }

    // Constructeur pour Barrier
    public SystemMessage(int timestamp, int sender, Type type, int generation, int coordinatorId) {
        super(null, timestamp, sender);
        this.type = type;
        this.payload = null;
        this.syncId = null;
        this.targetId = -1;
        this.coordinatorId = coordinatorId;
        this.generation = generation;
        this.processSet = null;
    }

    // Constructeur pour Sync ACK
    public SystemMessage(int timestamp, int sender, Type type, String syncId, int targetId) {
        super(null, timestamp, sender);
        this.type = type;
        this.payload = null;
        this.syncId = syncId;
        this.targetId = targetId;
        this.coordinatorId = -1;
        this.generation = -1;
        this.processSet = null;
    }

    // Getters
    public Type getType() { return type; }
    public Object getPayload() { return payload; }
    public String getSyncId() { return syncId; }
    public int getTargetId() { return targetId; }
    public int getCoordinatorId() { return coordinatorId; }
    public int getGeneration() { return generation; }
    public Set<Integer> getProcessSet() { return processSet; }

    @Override
    public String toString() {
        return "SystemMessage{type=" + type + ", sender=" + getSender() + ", timestamp=" + getTimestamp() + "}";
    }
}