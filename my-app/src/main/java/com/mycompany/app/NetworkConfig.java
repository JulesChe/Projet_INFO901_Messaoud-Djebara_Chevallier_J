package com.mycompany.app;

/**
 * Configuration réseau pour le système distribué.
 */
public class NetworkConfig {
    private final String bindAddress;
    private final int basePort;
    private final String multicastGroup;
    private final int multicastPort;
    private final int processId;

    public NetworkConfig(int processId) {
        this(processId, "0.0.0.0", 5000, "230.0.0.1", 5001);
    }

    public NetworkConfig(int processId, String bindAddress, int basePort, String multicastGroup, int multicastPort) {
        this.processId = processId;
        this.bindAddress = bindAddress;
        this.basePort = basePort;
        this.multicastGroup = multicastGroup;
        this.multicastPort = multicastPort;
    }

    public int getProcessId() {
        return processId;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public int getProcessPort() {
        return basePort + processId;
    }

    public String getMulticastGroup() {
        return multicastGroup;
    }

    public int getMulticastPort() {
        return multicastPort;
    }

    @Override
    public String toString() {
        return String.format("NetworkConfig{processId=%d, bindAddress='%s', port=%d, multicast=%s:%d}",
                           processId, bindAddress, getProcessPort(), multicastGroup, multicastPort);
    }
}