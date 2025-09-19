package com.mycompany.app;

/**
 * Configuration réseau pour le bus distribué.
 * Contient tous les paramètres nécessaires à la communication inter-processus.
 *
 * @author Middleware Team
 */
public class NetworkConfig {
    private int processId;
    private String bindAddress;
    private int basePort;
    private String multicastGroup;
    private int multicastPort;

    /**
     * Constructeur avec configuration par défaut.
     */
    public NetworkConfig() {
        this.bindAddress = "localhost";
        this.basePort = 5000;
        this.multicastGroup = "230.0.0.1";
        this.multicastPort = 4446;
    }

    /**
     * Constructeur avec ID de processus.
     *
     * @param processId L'identifiant unique du processus
     */
    public NetworkConfig(int processId) {
        this();
        this.processId = processId;
        this.basePort = 5000 + processId; // Port unique par processus
    }

    /**
     * Constructeur complet.
     *
     * @param processId L'identifiant du processus
     * @param bindAddress L'adresse de liaison
     * @param basePort Le port de base
     * @param multicastGroup Le groupe multicast pour la découverte
     * @param multicastPort Le port multicast
     */
    public NetworkConfig(int processId, String bindAddress, int basePort,
                        String multicastGroup, int multicastPort) {
        this.processId = processId;
        this.bindAddress = bindAddress;
        this.basePort = basePort;
        this.multicastGroup = multicastGroup;
        this.multicastPort = multicastPort;
    }

    // Getters et setters

    public int getProcessId() {
        return processId;
    }

    public void setProcessId(int processId) {
        this.processId = processId;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
    }

    public int getBasePort() {
        return basePort;
    }

    public void setBasePort(int basePort) {
        this.basePort = basePort;
    }

    public String getMulticastGroup() {
        return multicastGroup;
    }

    public void setMulticastGroup(String multicastGroup) {
        this.multicastGroup = multicastGroup;
    }

    public int getMulticastPort() {
        return multicastPort;
    }

    public void setMulticastPort(int multicastPort) {
        this.multicastPort = multicastPort;
    }

    @Override
    public String toString() {
        return String.format("NetworkConfig{processId=%d, bind=%s:%d, multicast=%s:%d}",
                           processId, bindAddress, basePort, multicastGroup, multicastPort);
    }
}