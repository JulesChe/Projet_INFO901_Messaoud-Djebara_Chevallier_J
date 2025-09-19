package com.mycompany.app;

import java.io.Serializable;
import java.util.Objects;

/**
 * Représente un endpoint de processus dans l'architecture distribuée.
 * Remplace les références directes aux objets Com par des informations réseau.
 *
 * Cette classe contient uniquement les informations nécessaires pour
 * communiquer avec un processus distant via le réseau.
 *
 * @author Middleware Team
 */
public class ProcessEndpoint implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int processId;
    private final String hostname;
    private final int port;

    /**
     * Constructeur d'un endpoint de processus.
     *
     * @param processId L'identifiant unique du processus
     * @param hostname L'adresse IP ou nom d'hôte
     * @param port Le port TCP d'écoute
     */
    public ProcessEndpoint(int processId, String hostname, int port) {
        this.processId = processId;
        this.hostname = hostname;
        this.port = port;
    }

    /**
     * @return L'identifiant du processus
     */
    public int getProcessId() {
        return processId;
    }

    /**
     * @return L'adresse IP ou nom d'hôte
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * @return Le port TCP d'écoute
     */
    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessEndpoint that = (ProcessEndpoint) o;
        return processId == that.processId &&
               port == that.port &&
               Objects.equals(hostname, that.hostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(processId, hostname, port);
    }

    @Override
    public String toString() {
        return String.format("ProcessEndpoint{id=%d, %s:%d}", processId, hostname, port);
    }
}