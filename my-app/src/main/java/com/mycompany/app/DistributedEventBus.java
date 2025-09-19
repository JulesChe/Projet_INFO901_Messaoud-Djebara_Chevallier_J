package com.mycompany.app;

import com.google.common.eventbus.EventBus;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bus d'événements distribué pour la communication inter-processus réseau.
 *
 * Architecture:
 * - EventBus local (Guava) pour les abonnés locaux
 * - Serveur TCP pour recevoir des messages distants
 * - Client TCP pour envoyer vers des processus spécifiques
 * - Multicast UDP pour la découverte et les broadcasts
 */
public class DistributedEventBus {
    private final NetworkConfig config;
    private final EventBus localEventBus;

    // Découverte et table de routage
    private final Map<Integer, ProcessEndpoint> knownProcesses = new ConcurrentHashMap<>();
    private final ExecutorService networkExecutor = Executors.newCachedThreadPool();

    // Serveurs réseau
    private ServerSocket tcpServer;
    private MulticastSocket multicastSocket;
    private InetAddress multicastGroup;

    // État
    private volatile boolean running = false;
    private static final long DISCOVERY_INTERVAL = 5000; // 5 secondes
    private static final long PROCESS_TIMEOUT = 15000;   // 15 secondes

    public DistributedEventBus(NetworkConfig config) {
        this.config = config;
        this.localEventBus = new EventBus("DistributedEventBus-" + config.getProcessId());

        // S'ajouter à la table de routage locale
        ProcessEndpoint localEndpoint = new ProcessEndpoint(
            config.getProcessId(),
            getLocalHostname(),
            config.getProcessPort()
        );
        knownProcesses.put(config.getProcessId(), localEndpoint);
    }

    /**
     * Démarre le bus distribué.
     */
    public synchronized void start() {
        if (running) return;

        try {
            // Démarrer serveur TCP
            startTcpServer();

            // Démarrer multicast pour découverte
            startMulticastDiscovery();

            // Démarrer envoi périodique de découverte
            startPeriodicDiscovery();

            running = true;
            System.out.println("DistributedEventBus démarré: " + config);

        } catch (IOException e) {
            throw new RuntimeException("Impossible de démarrer DistributedEventBus", e);
        }
    }

    /**
     * Arrête le bus distribué.
     */
    public synchronized void stop() {
        if (!running) return;

        running = false;

        try {
            if (tcpServer != null && !tcpServer.isClosed()) {
                tcpServer.close();
            }
            if (multicastSocket != null && !multicastSocket.isClosed()) {
                multicastSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de l'arrêt: " + e.getMessage());
        }

        networkExecutor.shutdown();
        try {
            if (!networkExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                networkExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            networkExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("DistributedEventBus arrêté");
    }

    /**
     * Enregistre un abonné local.
     */
    public void register(Object subscriber) {
        localEventBus.register(subscriber);
    }

    /**
     * Désenregistre un abonné local.
     */
    public void unregister(Object subscriber) {
        localEventBus.unregister(subscriber);
    }

    /**
     * Poste un événement sur le bus (local et distribué).
     */
    public void post(Object event) {
        // Événement local
        localEventBus.post(event);

        // Diffusion réseau si c'est un message sérialisable
        if (event instanceof Serializable && event instanceof Message) {
            Message message = (Message) event;
            distributeMessage(message);
        }
    }

    /**
     * Obtient la liste des processus connus.
     */
    public Set<ProcessEndpoint> getKnownProcesses() {
        // Nettoyer les processus morts
        cleanupDeadProcesses();
        return Set.copyOf(knownProcesses.values());
    }

    /**
     * Démarre le serveur TCP pour recevoir les messages.
     */
    private void startTcpServer() throws IOException {
        tcpServer = new ServerSocket(config.getProcessPort(), 50, InetAddress.getByName(config.getBindAddress()));

        networkExecutor.submit(() -> {
            while (running && !tcpServer.isClosed()) {
                try {
                    Socket clientSocket = tcpServer.accept();
                    networkExecutor.submit(() -> handleTcpConnection(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Erreur serveur TCP: " + e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Gère une connexion TCP entrante.
     */
    private void handleTcpConnection(Socket socket) {
        try (ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
            Object received = ois.readObject();
            if (received instanceof Message) {
                // Traiter le message reçu localement
                localEventBus.post(received);
            }
        } catch (Exception e) {
            System.err.println("Erreur traitement TCP: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignorer
            }
        }
    }

    /**
     * Démarre la découverte multicast.
     */
    private void startMulticastDiscovery() throws IOException {
        multicastGroup = InetAddress.getByName(config.getMulticastGroup());
        multicastSocket = new MulticastSocket(config.getMulticastPort());
        multicastSocket.joinGroup(multicastGroup);

        // Écouter les messages de découverte
        networkExecutor.submit(() -> {
            byte[] buffer = new byte[1024];
            while (running && !multicastSocket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    multicastSocket.receive(packet);
                    handleDiscoveryPacket(packet);
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Erreur multicast: " + e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Démarre l'envoi périodique de messages de découverte.
     */
    private void startPeriodicDiscovery() {
        networkExecutor.submit(() -> {
            while (running) {
                try {
                    sendDiscoveryMessage();
                    Thread.sleep(DISCOVERY_INTERVAL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Erreur envoi découverte: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Envoie un message de découverte en multicast.
     */
    private void sendDiscoveryMessage() throws IOException {
        String discoveryMsg = String.format("DISCOVER:%d:%s:%d",
            config.getProcessId(), getLocalHostname(), config.getProcessPort());

        byte[] data = discoveryMsg.getBytes();
        DatagramPacket packet = new DatagramPacket(
            data, data.length,
            multicastGroup, config.getMulticastPort()
        );

        DatagramSocket socket = new DatagramSocket();
        socket.send(packet);
        socket.close();
    }

    /**
     * Traite un paquet de découverte reçu.
     */
    private void handleDiscoveryPacket(DatagramPacket packet) {
        try {
            String message = new String(packet.getData(), 0, packet.getLength());
            if (message.startsWith("DISCOVER:")) {
                String[] parts = message.split(":");
                if (parts.length == 4) {
                    int processId = Integer.parseInt(parts[1]);
                    String hostname = parts[2];
                    int port = Integer.parseInt(parts[3]);

                    // Ne pas s'ajouter soi-même
                    if (processId != config.getProcessId()) {
                        ProcessEndpoint endpoint = new ProcessEndpoint(processId, hostname, port);
                        knownProcesses.put(processId, endpoint);
                        System.out.println("Processus découvert: " + endpoint);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur traitement découverte: " + e.getMessage());
        }
    }

    /**
     * Distribue un message vers les processus distants.
     */
    private void distributeMessage(Message message) {
        if (message instanceof UserMessage) {
            UserMessage userMsg = (UserMessage) message;
            if (userMsg.isBroadcast()) {
                // Broadcast vers tous les processus connus
                for (ProcessEndpoint endpoint : knownProcesses.values()) {
                    if (endpoint.getProcessId() != config.getProcessId()) {
                        sendToProcess(endpoint, message);
                    }
                }
            } else {
                // Envoi direct vers le destinataire
                ProcessEndpoint target = knownProcesses.get(userMsg.getDestination());
                if (target != null && target.getProcessId() != config.getProcessId()) {
                    sendToProcess(target, message);
                }
            }
        } else {
            // Autres types de messages - broadcast par défaut
            for (ProcessEndpoint endpoint : knownProcesses.values()) {
                if (endpoint.getProcessId() != config.getProcessId()) {
                    sendToProcess(endpoint, message);
                }
            }
        }
    }

    /**
     * Envoie un message à un processus spécifique via TCP.
     */
    private void sendToProcess(ProcessEndpoint endpoint, Message message) {
        networkExecutor.submit(() -> {
            try (Socket socket = new Socket(endpoint.getHostname(), endpoint.getPort());
                 ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {

                oos.writeObject(message);
                oos.flush();

            } catch (IOException e) {
                System.err.println("Impossible d'envoyer à " + endpoint + ": " + e.getMessage());
                // Marquer le processus comme potentiellement mort
                knownProcesses.remove(endpoint.getProcessId());
            }
        });
    }

    /**
     * Nettoie les processus morts de la table de routage.
     */
    private void cleanupDeadProcesses() {
        knownProcesses.entrySet().removeIf(entry -> {
            ProcessEndpoint endpoint = entry.getValue();
            boolean isAlive = endpoint.isAlive(PROCESS_TIMEOUT);
            if (!isAlive && endpoint.getProcessId() != config.getProcessId()) {
                System.out.println("Processus mort détecté: " + endpoint);
                return true;
            }
            return false;
        });
    }

    /**
     * Obtient le nom d'hôte local.
     */
    private String getLocalHostname() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }
}