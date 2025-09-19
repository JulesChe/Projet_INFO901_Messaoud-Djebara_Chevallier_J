package com.mycompany.app;

import java.io.Serializable;

/**
 * Message notifiant qu'un nouvel endpoint de processus a été découvert.
 * Ce message est interne au bus distribué.
 *
 * @author Middleware Team
 */
public class EndpointDiscoveredMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ProcessEndpoint endpoint;

    /**
     * Constructeur du message de découverte d'endpoint.
     *
     * @param endpoint L'endpoint découvert
     */
    public EndpointDiscoveredMessage(ProcessEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * @return L'endpoint découvert
     */
    public ProcessEndpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public String toString() {
        return String.format("EndpointDiscoveredMessage{endpoint=%s}", endpoint);
    }
}