package com.mycompany.app;

import java.util.Set;

/**
 * Message pour la barrière de synchronisation distribuée.
 * Implémente l'algorithme de barrière avec coordinateur selon LaBarriereDeSynchro.pdf.
 * Messages système qui n'affectent pas l'horloge de Lamport utilisateur.
 *
 * @author Middleware Team
 */
public class BarrierMessage extends Message {

    public enum Type {
        ARRIVE_AT_BARRIER,      // Un processus arrive à la barrière
        BARRIER_RELEASE,        // Le coordinateur libère tous les processus
        COORDINATOR_ELECTION,   // Élection du coordinateur de barrière
        COORDINATOR_ACK,        // Accusé de réception du coordinateur
        BARRIER_STATUS_REQUEST, // Demande du statut de la barrière
        BARRIER_STATUS_RESPONSE // Réponse avec le statut
    }

    private final Type barrierType;
    private final int barrierGeneration;
    private final Set<Integer> processesAtBarrier;
    private final int coordinatorId;

    /**
     * Constructeur complet pour message de barrière.
     *
     * @param timestamp L'estampille temporelle
     * @param sender L'identifiant du processus expéditeur
     * @param barrierType Le type de message de barrière
     * @param barrierGeneration La génération de la barrière
     * @param processesAtBarrier L'ensemble des processus à la barrière
     * @param coordinatorId L'ID du coordinateur élu
     */
    public BarrierMessage(int timestamp, int sender, Type barrierType, int barrierGeneration,
                         Set<Integer> processesAtBarrier, int coordinatorId) {
        super(null, timestamp, sender, true); // Messages système n'affectent pas l'horloge utilisateur
        this.barrierType = barrierType;
        this.barrierGeneration = barrierGeneration;
        this.processesAtBarrier = processesAtBarrier;
        this.coordinatorId = coordinatorId;
    }

    /**
     * Constructeur simplifié pour messages sans liste de processus.
     */
    public BarrierMessage(int timestamp, int sender, Type barrierType, int barrierGeneration, int coordinatorId) {
        this(timestamp, sender, barrierType, barrierGeneration, null, coordinatorId);
    }

    /**
     * @return Le type de message de barrière
     */
    public Type getBarrierType() {
        return barrierType;
    }

    /**
     * @return La génération de la barrière
     */
    public int getBarrierGeneration() {
        return barrierGeneration;
    }

    /**
     * @return L'ensemble des processus à la barrière
     */
    public Set<Integer> getProcessesAtBarrier() {
        return processesAtBarrier;
    }

    /**
     * @return L'ID du coordinateur
     */
    public int getCoordinatorId() {
        return coordinatorId;
    }

    @Override
    public String toString() {
        return String.format("BarrierMessage{type=%s, sender=%d, generation=%d, coordinator=%d, processes=%s}",
                           barrierType, sender, barrierGeneration, coordinatorId, processesAtBarrier);
    }
}