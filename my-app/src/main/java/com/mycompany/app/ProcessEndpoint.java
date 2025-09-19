package com.mycompany.app;

import java.io.Serializable;
import java.util.Objects;

/**
 * Point de terminaison d'un processus dans le système distribué.
 * Remplace les références directes Com pour permettre la distribution réseau.
 */
public class ProcessEndpoint implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int processId;
    private final String hostname;
    private final int port;
    private final long lastSeen;

    public ProcessEndpoint(int processId, String hostname, int port) {
        this.processId = processId;
        this.hostname = hostname;
        this.port = port;
        this.lastSeen = System.currentTimeMillis();
    }

    public int getProcessId() {
        return processId;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    /**
     * Vérifie si ce processus est considéré comme vivant.
     *
     * @param timeoutMs Timeout en millisecondes
     * @return true si le processus est vivant
     */
    public boolean isAlive(long timeoutMs) {
        return System.currentTimeMillis() - lastSeen < timeoutMs;
    }

    /**
     * Crée un nouvel endpoint avec un timestamp mis à jour.
     *
     * @return Nouvel endpoint avec timestamp actuel
     */
    public ProcessEndpoint updateLastSeen() {
        return new ProcessEndpoint(processId, hostname, port);
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
        return String.format("ProcessEndpoint{id=%d, %s:%d, lastSeen=%d}",
                           processId, hostname, port, lastSeen);
    }
}