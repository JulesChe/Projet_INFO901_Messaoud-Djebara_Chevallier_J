package com.mycompany.app;

import java.io.Serializable;

/**
 * Classe représentant un token pour la section critique.
 * Sérialisable pour permettre l'envoi sur le réseau.
 */
public class Token implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;

    /**
     * Constructeur pour un token.
     *
     * @param name Le nom du token
     */
    public Token(String name) {
        this.name = name;
    }

    /**
     * Obtient le nom du token.
     *
     * @return Le nom du token
     */
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Token{name='" + name + "'}";
    }
}