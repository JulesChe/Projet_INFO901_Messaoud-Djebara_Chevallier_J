package com.mycompany.app;

/**
 * Message système représentant un jeton pour la section critique distribuée.
 * Ces messages n'affectent pas l'horloge de Lamport.
 *
 * @author Middleware Team
 */
public class TokenMessage extends Message {

    /**
     * Constructeur pour un message de jeton.
     *
     * @param sender L'identifiant du processus expéditeur
     */
    public TokenMessage(int sender) {
        super("TOKEN", 0, sender, true);
    }
}