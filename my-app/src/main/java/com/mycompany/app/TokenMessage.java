package com.mycompany.app;

/**
 * Classe pour les messages système contenant le jeton de section critique.
 * Ces messages ne doivent pas impacter l'horloge de Lamport.
 *
 * @author Middleware Team
 */
public class TokenMessage extends Message {
    private static final long serialVersionUID = 1L;

    /**
     * Constructeur pour un message de jeton.
     * Le timestamp n'a pas d'importance car c'est un message système
     * qui n'affecte pas l'horloge de Lamport.
     *
     * @param sender L'identifiant du processus expéditeur
     */
    public TokenMessage(int sender) {
        super("TOKEN", 0, sender, true);
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
        return String.format("TokenMessage{sender=%d}", sender);
    }
}