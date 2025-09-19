package com.mycompany.app;

/**
 * Message pour la barrière de synchronisation distribuée.
 * Basé sur les concepts du cours LaBarriereDeSynchro.pdf.
 * Implémente un algorithme totalement distribué sans variable de classe.
 *
 * @author Middleware Team
 */
public class BarrierMessage extends Message {

    public enum Type {
        BARRIER_ARRIVAL,    // Un processus arrive à la barrière
        BARRIER_ACK,        // Accusé de réception d'arrivée
        BARRIER_RELEASE,    // Libération de la barrière
        BARRIER_QUERY       // Demande du nombre de processus actifs
    }

    private final Type barrierType;
    private final int barrierGeneration;
    private final int totalProcesses;

    /**
     * Constructeur pour message de barrière.
     *
     * @param timestamp L'estampille temporelle
     * @param sender L'identifiant du processus expéditeur
     * @param barrierType Le type de message de barrière
     * @param barrierGeneration Le numéro de génération de la barrière
     * @param totalProcesses Le nombre total de processus pour cette barrière
     */
    public BarrierMessage(int timestamp, int sender, Type barrierType, int barrierGeneration, int totalProcesses) {
        super(null, timestamp, sender, false); // Messages de barrière affectent l'horloge (coordination)
        this.barrierType = barrierType;
        this.barrierGeneration = barrierGeneration;
        this.totalProcesses = totalProcesses;
    }

    /**
     * Constructeur simplifié.
     */
    public BarrierMessage(int timestamp, int sender, Type barrierType, int barrierGeneration) {
        this(timestamp, sender, barrierType, barrierGeneration, 0);
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
     * @return Le nombre total de processus
     */
    public int getTotalProcesses() {
        return totalProcesses;
    }

    @Override
    public String toString() {
        return String.format("BarrierMessage{type=%s, generation=%d, sender=%d, timestamp=%d, total=%d}",
                           barrierType, barrierGeneration, sender, timestamp, totalProcesses);
    }
}