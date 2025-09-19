package com.mycompany.app;

/**
 * Classe pour les messages système contenant le jeton de section critique.
 * Ces messages ne doivent pas impacter l'horloge de Lamport.
 * Sérialisable pour permettre l'envoi sur le réseau.
 *
 * @author Middleware Team
 */
public class TokenMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final Token token;
    private final int destination;

    /**
     * Constructeur pour un message de jeton.
     *
     * @param token Le jeton à transmettre
     * @param destination L'identifiant du processus destinataire
     */
    public TokenMessage(Token token, int destination) {
        super("TOKEN", 0, -1, true);
        this.token = token;
        this.destination = destination;
    }

    /**
     * Obtient le jeton.
     *
     * @return Le jeton
     */
    public Token getToken() {
        return token;
    }

    /**
     * Obtient l'identifiant du processus destinataire.
     *
     * @return L'ID du destinataire
     */
    public int getDestination() {
        return destination;
    }

    /**
     * @return true car c'est toujours un message système
     */
    @Override
    public boolean isSystemMessage() {
        return true;
    }

    @Override
    public String toString() {
        return String.format("TokenMessage{destination=%d, token=%s}", destination, token);
    }
}