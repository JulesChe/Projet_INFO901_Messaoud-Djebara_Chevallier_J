package com.mycompany.app;

import java.io.Serializable;

/**
 * Message de synchronisation pour les communications sendToSync et recvFromSync.
 * Sérialisable pour permettre l'envoi sur le réseau.
 */
public class SynchronizeMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final int from;
    private final int dest;
    private final SynchronizeMessageType type;

    public enum SynchronizeMessageType {
        SendTo, Response, Recv
    }

    /**
     * Constructeur pour un message de synchronisation.
     *
     * @param from L'ID du processus expéditeur
     * @param dest L'ID du processus destinataire
     * @param type Le type de message de synchronisation
     * @param payload Le contenu du message (doit être sérialisable)
     */
    public SynchronizeMessage(int from, int dest, SynchronizeMessageType type, Serializable payload) {
        super(payload, 0, from, false);
        this.from = from;
        this.dest = dest;
        this.type = type;
    }

    /**
     * Obtient l'ID du processus expéditeur.
     *
     * @return L'ID de l'expéditeur
     */
    public int getFrom() {
        return from;
    }

    /**
     * Obtient l'ID du processus destinataire.
     *
     * @return L'ID du destinataire
     */
    public int getDest() {
        return dest;
    }

    /**
     * Obtient le type de message de synchronisation.
     *
     * @return Le type de message
     */
    public SynchronizeMessageType getType() {
        return type;
    }

    /**
     * Définit l'estampille temporelle.
     *
     * @param timestamp L'estampille temporelle
     */
    public void setEstampillage(int timestamp) {
        // Cette méthode permet de mettre à jour le timestamp après création
        // On utilise la réflexion pour modifier le champ final
        try {
            java.lang.reflect.Field field = Message.class.getDeclaredField("timestamp");
            field.setAccessible(true);
            field.setInt(this, timestamp);
        } catch (Exception e) {
            // En cas d'échec de la réflexion, on ignore silencieusement
        }
    }

    @Override
    public String toString() {
        return String.format("SynchronizeMessage{from=%d, dest=%d, type=%s, payload=%s}",
                           from, dest, type, getPayload());
    }
}