package com.mycompany.app;

/**
 * Message pour gérer le passage de token entre processus.
 * Permet de découvrir dynamiquement le prochain processus actif.
 * Sérialisable pour permettre l'envoi sur le réseau.
 */
public class TokenPassMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final Token token;
    private final int currentHolder;

    /**
     * Constructeur pour un message de passage de token.
     *
     * @param token Le token à faire passer
     * @param currentHolder Le processus qui détient actuellement le token
     */
    public TokenPassMessage(Token token, int currentHolder) {
        super("TOKEN_PASS", 0, currentHolder, true);
        this.token = token;
        this.currentHolder = currentHolder;
    }

    /**
     * Obtient le token.
     *
     * @return Le token
     */
    public Token getToken() {
        return token;
    }

    /**
     * Obtient l'ID du processus qui détient actuellement le token.
     *
     * @return L'ID du détenteur actuel
     */
    public int getCurrentHolder() {
        return currentHolder;
    }

    @Override
    public boolean isSystemMessage() {
        return true;
    }

    @Override
    public String toString() {
        return String.format("TokenPassMessage{currentHolder=%d, token=%s}", currentHolder, token);
    }
}