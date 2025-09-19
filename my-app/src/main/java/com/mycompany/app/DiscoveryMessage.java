package com.mycompany.app;

/**
 * Message pour la découverte distribuée des processus.
 * Basé sur les concepts de diffusion du cours LaDiffusion.pdf.
 * Permet de maintenir une vue cohérente des processus sans variable de classe.
 *
 * @author Middleware Team
 */
public class DiscoveryMessage extends Message {

    public enum Type {
        DISCOVERY_REQUEST,   // Demande de découverte envoyée par nouveau processus
        DISCOVERY_RESPONSE,  // Réponse contenant les informations du processus
        DISCOVERY_ANNOUNCE,  // Annonce d'un nouveau processus
        DISCOVERY_LEAVE      // Annonce de départ d'un processus
    }

    private final Type discoveryType;
    private final int[] knownProcesses;
    private final int successor; // Pour l'anneau virtuel

    /**
     * Constructeur pour message de découverte.
     *
     * @param timestamp L'estampille temporelle
     * @param sender L'identifiant du processus expéditeur
     * @param discoveryType Le type de message de découverte
     * @param knownProcesses Les processus connus par l'expéditeur
     * @param successor Le successeur dans l'anneau virtuel
     */
    public DiscoveryMessage(int timestamp, int sender, Type discoveryType, int[] knownProcesses, int successor) {
        super(null, timestamp, sender, true); // Messages système n'affectent pas l'horloge utilisateur
        this.discoveryType = discoveryType;
        this.knownProcesses = knownProcesses;
        this.successor = successor;
    }

    /**
     * Constructeur simplifié pour REQUEST et LEAVE.
     */
    public DiscoveryMessage(int timestamp, int sender, Type discoveryType) {
        this(timestamp, sender, discoveryType, null, -1);
    }

    /**
     * @return Le type de message de découverte
     */
    public Type getDiscoveryType() {
        return discoveryType;
    }

    /**
     * @return Les processus connus
     */
    public int[] getKnownProcesses() {
        return knownProcesses;
    }

    /**
     * @return Le successeur dans l'anneau
     */
    public int getSuccessor() {
        return successor;
    }

    @Override
    public String toString() {
        return String.format("DiscoveryMessage{type=%s, sender=%d, timestamp=%d, successor=%d}",
                           discoveryType, sender, timestamp, successor);
    }
}