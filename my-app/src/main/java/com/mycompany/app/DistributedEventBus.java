package com.mycompany.app;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Bus de messages distribué inspiré de Google Guava EventBus.
 * Permet la communication entre processus sur le réseau avec le pattern @Subscribe.
 *
 * Architecture distribuée basée sur les concepts du cours :
 * - Communication par messages sérialisés
 * - Découverte automatique par multicast
 * - Horloge de Lamport distribuée
 *
 * @author Middleware Team
 */
public class DistributedEventBus {

    private final NetworkConfig config;
    private final ExecutorService networkExecutor;
    private final ExecutorService messageProcessor;

    // Abonnés locaux pour le pattern @Subscribe
    private final Map<Class<?>, List<SubscriberMethod>> subscribers = new ConcurrentHashMap<>();

    // Endpoints des processus connus
    private final Map<Integer, ProcessEndpoint> knownEndpoints = new ConcurrentHashMap<>();

    // Socket serveur pour écouter les messages entrants
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    // Service de découverte multicast
    private MulticastSocket multicastSocket;
    private InetAddress multicastGroup;

    /**
     * Constructeur du bus distribué.
     *
     * @param config Configuration réseau
     */
    public DistributedEventBus(NetworkConfig config) {
        this.config = config;
        this.networkExecutor = Executors.newCachedThreadPool();
        this.messageProcessor = Executors.newFixedThreadPool(4);

        try {
            initializeNetworking();
            startDiscoveryService();
        } catch (IOException e) {
            throw new RuntimeException("Impossible d'initialiser le bus distribué", e);
        }
    }

    /**
     * Initialise la couche réseau TCP pour la communication.
     */
    private void initializeNetworking() throws IOException {
        // Démarrer le serveur TCP pour recevoir les messages
        serverSocket = new ServerSocket(config.getBasePort());
        running = true;

        // Thread pour accepter les connexions entrantes
        networkExecutor.submit(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    // Traiter chaque connexion dans un thread séparé
                    messageProcessor.submit(() -> handleIncomingConnection(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Erreur lors de l'acceptation de connexion: " + e.getMessage());
                    }
                }
            }
        });

        System.out.println("DistributedEventBus en écoute sur port " + config.getBasePort());
    }

    /**
     * Démarre le service de découverte multicast.
     */
    private void startDiscoveryService() throws IOException {
        multicastGroup = InetAddress.getByName(config.getMulticastGroup());
        multicastSocket = new MulticastSocket(config.getMulticastPort());
        multicastSocket.joinGroup(multicastGroup);

        // Thread pour écouter les annonces multicast
        networkExecutor.submit(() -> {
            byte[] buffer = new byte[1024];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    multicastSocket.receive(packet);
                    handleDiscoveryMessage(packet);
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Erreur de découverte multicast: " + e.getMessage());
                    }
                }
            }
        });

        // Annoncer périodiquement notre présence
        networkExecutor.submit(() -> {
            while (running) {
                try {
                    announcePresence();
                    Thread.sleep(5000); // Annonce toutes les 5 secondes
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    System.err.println("Erreur lors de l'annonce: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Enregistre un objet pour recevoir des messages via @Subscribe.
     *
     * @param subscriber L'objet à enregistrer
     */
    public void registerSubscriber(Object subscriber) {
        Class<?> clazz = subscriber.getClass();

        // Rechercher toutes les méthodes annotées @Subscribe
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Subscribe.class)) {
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length == 1) {
                    Class<?> messageType = paramTypes[0];

                    subscribers.computeIfAbsent(messageType, k -> new ArrayList<>())
                             .add(new SubscriberMethod(subscriber, method));
                }
            }
        }
    }

    /**
     * Publie un message sur le bus distribué.
     * Le message sera envoyé à tous les processus connus.
     *
     * @param message Le message à publier
     */
    public void post(Object message) {
        if (!(message instanceof Serializable)) {
            throw new IllegalArgumentException("Le message doit être sérialisable: " + message.getClass());
        }

        // D'abord, livrer localement
        deliverLocally(message);

        // Ensuite, envoyer aux autres processus
        for (ProcessEndpoint endpoint : knownEndpoints.values()) {
            if (!endpoint.equals(getLocalEndpoint())) {
                sendToEndpoint(endpoint, message);
            }
        }
    }

    /**
     * Envoie un message à un processus spécifique.
     *
     * @param targetProcessId ID du processus destinataire
     * @param message Le message à envoyer
     */
    public void sendTo(int targetProcessId, Object message) {
        ProcessEndpoint target = knownEndpoints.get(targetProcessId);
        if (target != null) {
            sendToEndpoint(target, message);
        } else {
            System.err.println("Processus " + targetProcessId + " inconnu");
        }
    }

    /**
     * Livre un message localement aux abonnés.
     */
    private void deliverLocally(Object message) {
        Class<?> messageType = message.getClass();
        List<SubscriberMethod> methods = subscribers.get(messageType);

        if (methods != null) {
            for (SubscriberMethod subscriber : methods) {
                messageProcessor.submit(() -> {
                    try {
                        subscriber.method.invoke(subscriber.instance, message);
                    } catch (Exception e) {
                        System.err.println("Erreur lors de la livraison du message: " + e.getMessage());
                    }
                });
            }
        }
    }

    /**
     * Envoie un message à un endpoint spécifique.
     */
    private void sendToEndpoint(ProcessEndpoint endpoint, Object message) {
        networkExecutor.submit(() -> {
            try (Socket socket = new Socket(endpoint.getHostname(), endpoint.getPort());
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

                out.writeObject(message);
                out.flush();

            } catch (IOException e) {
                System.err.println("Impossible d'envoyer message à " + endpoint + ": " + e.getMessage());
                // Marquer l'endpoint comme potentiellement mort
                handleDeadEndpoint(endpoint);
            }
        });
    }

    /**
     * Gère une connexion entrante et traite les messages reçus.
     */
    private void handleIncomingConnection(Socket socket) {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            Object message = in.readObject();

            // Livrer le message localement
            deliverLocally(message);

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Erreur lors du traitement du message entrant: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignorer les erreurs de fermeture
            }
        }
    }

    /**
     * Annonce notre présence en multicast.
     */
    private void announcePresence() throws IOException {
        ProcessEndpoint localEndpoint = getLocalEndpoint();
        String announcement = "PROCESS_ANNOUNCE:" + localEndpoint.getProcessId() +
                             ":" + localEndpoint.getHostname() + ":" + localEndpoint.getPort();

        byte[] data = announcement.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length,
                                                  multicastGroup, config.getMulticastPort());
        multicastSocket.send(packet);
    }

    /**
     * Traite un message de découverte reçu.
     */
    private void handleDiscoveryMessage(DatagramPacket packet) {
        String message = new String(packet.getData(), 0, packet.getLength());

        if (message.startsWith("PROCESS_ANNOUNCE:")) {
            String[] parts = message.split(":");
            if (parts.length >= 4) {
                try {
                    int processId = Integer.parseInt(parts[1]);
                    String hostname = parts[2];
                    int port = Integer.parseInt(parts[3]);

                    ProcessEndpoint endpoint = new ProcessEndpoint(processId, hostname, port);

                    // Éviter de s'ajouter soi-même
                    if (!endpoint.equals(getLocalEndpoint())) {
                        knownEndpoints.put(processId, endpoint);
                        System.out.println("Processus découvert: " + endpoint);

                        // Notifier les abonnés de la découverte
                        deliverLocally(new EndpointDiscoveredMessage(endpoint));
                    }

                } catch (NumberFormatException e) {
                    System.err.println("Message de découverte mal formé: " + message);
                }
            }
        }
    }

    /**
     * Obtient l'endpoint local de ce processus.
     */
    private ProcessEndpoint getLocalEndpoint() {
        return new ProcessEndpoint(config.getProcessId(),
                                 config.getBindAddress(),
                                 config.getBasePort());
    }

    /**
     * Gère un endpoint qui semble être mort.
     */
    private void handleDeadEndpoint(ProcessEndpoint endpoint) {
        knownEndpoints.remove(endpoint.getProcessId());
        System.out.println("Processus supprimé (semble mort): " + endpoint);
    }

    /**
     * Obtient la liste des processus connus.
     */
    public Map<Integer, ProcessEndpoint> getKnownEndpoints() {
        return new HashMap<>(knownEndpoints);
    }

    /**
     * Arrête le bus distribué et libère les ressources.
     */
    public void shutdown() {
        running = false;

        try {
            if (serverSocket != null) serverSocket.close();
            if (multicastSocket != null) multicastSocket.close();
        } catch (IOException e) {
            System.err.println("Erreur lors de l'arrêt: " + e.getMessage());
        }

        networkExecutor.shutdown();
        messageProcessor.shutdown();
    }

    /**
     * Classe interne pour stocker un abonné et sa méthode.
     */
    private static class SubscriberMethod {
        final Object instance;
        final Method method;

        SubscriberMethod(Object instance, Method method) {
            this.instance = instance;
            this.method = method;
            this.method.setAccessible(true);
        }
    }
}