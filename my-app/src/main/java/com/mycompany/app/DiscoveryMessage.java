package com.mycompany.app;

import java.util.Set;

/**
 * Message pour le protocole de découverte distribuée.
 * Permet aux processus de s'annoncer et de partager leur vue du système.
 * Messages système qui n'affectent pas l'horloge de Lamport utilisateur.
 *
 * @author Middleware Team
 */
public class DiscoveryMessage extends Message {
    private static final long serialVersionUID = 1L;

    public enum Type {
        ANNOUNCE,           // Annonce de présence d'un nouveau processus
        REQUEST_LIST,       // Demande de la liste des processus connus
        PROCESS_LIST,       // Réponse avec la liste des processus
        PROCESS_LEAVING     // Annonce qu'un processus quitte le système
    }

    private final Type discoveryType;
    private final Set<Integer> knownProcesses;

    /**
     * Constructeur pour message de découverte.
     *
     * @param timestamp L'estampille temporelle
     * @param sender L'identifiant du processus expéditeur
     * @param discoveryType Le type de message de découverte
     * @param knownProcesses L'ensemble des processus connus (pour PROCESS_LIST)
     */
    public DiscoveryMessage(int timestamp, int sender, Type discoveryType, Set<Integer> knownProcesses) {
        super(null, timestamp, sender, true); // Messages système n'affectent pas l'horloge utilisateur
        this.discoveryType = discoveryType;
        this.knownProcesses = knownProcesses;
    }

    /**
     * Constructeur simplifié pour ANNOUNCE et REQUEST_LIST.
     */
    public DiscoveryMessage(int timestamp, int sender, Type discoveryType) {
        this(timestamp, sender, discoveryType, null);
    }

    /**
     * @return Le type de message de découverte
     */
    public Type getDiscoveryType() {
        return discoveryType;
    }

    /**
     * @return L'ensemble des processus connus
     */
    public Set<Integer> getKnownProcesses() {
        return knownProcesses;
    }

    @Override
    public String toString() {
        return String.format("DiscoveryMessage{type=%s, sender=%d, timestamp=%d, knownProcesses=%s}",
                           discoveryType, sender, timestamp, knownProcesses);
    }
}