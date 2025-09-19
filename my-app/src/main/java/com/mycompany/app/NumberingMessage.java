package com.mycompany.app;

/**
 * Message pour le système de numérotation automatique distribuée.
 * Basé sur les concepts du cours NumérotationAutomatique.pdf.
 *
 * @author Middleware Team
 */
public class NumberingMessage extends Message {

    public enum Type {
        NUMBERING_REQUEST,    // Demande de numérotation avec nombre aléatoire
        NUMBERING_RESPONSE,   // Réponse avec nombre aléatoire
        NUMBERING_FINAL       // Attribution finale des IDs
    }

    private final Type messageType;
    private final int randomNumber;
    private final int[] finalAssignments;

    /**
     * Constructeur pour message de numérotation.
     *
     * @param timestamp L'estampille temporelle
     * @param sender L'identifiant du processus expéditeur
     * @param messageType Le type de message de numérotation
     * @param randomNumber Le nombre aléatoire généré
     * @param finalAssignments Les attributions finales (pour NUMBERING_FINAL)
     */
    public NumberingMessage(int timestamp, int sender, Type messageType, int randomNumber, int[] finalAssignments) {
        super(null, timestamp, sender, true); // Messages système n'affectent pas l'horloge utilisateur
        this.messageType = messageType;
        this.randomNumber = randomNumber;
        this.finalAssignments = finalAssignments;
    }

    /**
     * Constructeur simplifié pour REQUEST et RESPONSE.
     */
    public NumberingMessage(int timestamp, int sender, Type messageType, int randomNumber) {
        this(timestamp, sender, messageType, randomNumber, null);
    }

    /**
     * @return Le type de message de numérotation
     */
    public Type getMessageType() {
        return messageType;
    }

    /**
     * @return Le nombre aléatoire
     */
    public int getRandomNumber() {
        return randomNumber;
    }

    /**
     * @return Les attributions finales
     */
    public int[] getFinalAssignments() {
        return finalAssignments;
    }

    @Override
    public String toString() {
        return String.format("NumberingMessage{type=%s, randomNumber=%d, sender=%d, timestamp=%d}",
                           messageType, randomNumber, sender, timestamp);
    }
}