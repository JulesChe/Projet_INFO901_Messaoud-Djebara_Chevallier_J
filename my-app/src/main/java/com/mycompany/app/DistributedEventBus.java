package com.mycompany.app;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Bus de messages distribué simplifié.
 * Version allégée avec TCP et multicast intégrés.
 *
 * @author Middleware Team
 */
public class DistributedEventBus {

    private final NetworkConfig config;
    private final ExecutorService executor;
    private final Map<Class<?>, List<Method>> subscribers = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> subscriberInstances = new ConcurrentHashMap<>();
    private final Map<Integer, ProcessEndpoint> knownEndpoints = new ConcurrentHashMap<>();

    private ServerSocket serverSocket;
    private MulticastSocket multicastSocket;
    private InetAddress multicastGroup;
    private volatile boolean running = false;

    public DistributedEventBus(NetworkConfig config) {
        this.config = config;
        this.executor = Executors.newCachedThreadPool();
        startServices();
    }

    private void startServices() {
        try {
            // Démarrer serveur TCP
            serverSocket = new ServerSocket(config.getBasePort());
            running = true;

            // Démarrer multicast pour découverte
            multicastSocket = new MulticastSocket(config.getMulticastPort());
            multicastGroup = InetAddress.getByName(config.getMulticastGroup());
            multicastSocket.joinGroup(multicastGroup);

            // Écouter TCP et multicast
            executor.submit(this::listenTCP);
            executor.submit(this::listenMulticast);
            executor.submit(this::announcePresence);

        } catch (IOException e) {
            throw new RuntimeException("Erreur démarrage EventBus", e);
        }
    }

    public void registerSubscriber(Object subscriber) {
        Class<?> clazz = subscriber.getClass();
        List<Method> methods = new ArrayList<>();

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Subscribe.class) && method.getParameterCount() == 1) {
                method.setAccessible(true);
                methods.add(method);
            }
        }

        if (!methods.isEmpty()) {
            subscribers.put(clazz, methods);
            subscriberInstances.put(clazz, subscriber); // Stocker l'instance
        }
    }

    public void post(Object message) {
        // Livrer localement
        deliverLocally(message);

        // Envoyer à tous les endpoints connus
        for (ProcessEndpoint endpoint : knownEndpoints.values()) {
            sendToEndpoint(endpoint, message);
        }
    }

    public void sendTo(int targetId, Object message) {
        ProcessEndpoint target = knownEndpoints.get(targetId);
        if (target != null) {
            sendToEndpoint(target, message);
        }
    }

    private void deliverLocally(Object message) {
        for (Map.Entry<Class<?>, List<Method>> entry : subscribers.entrySet()) {
            Object subscriber = getSubscriberInstance(entry.getKey());
            if (subscriber != null) {
                for (Method method : entry.getValue()) {
                    if (method.getParameterTypes()[0].isAssignableFrom(message.getClass())) {
                        executor.submit(() -> {
                            try {
                                method.invoke(subscriber, message);
                            } catch (Exception e) {
                                System.err.println("Erreur invocation: " + e.getMessage());
                            }
                        });
                    }
                }
            }
        }
    }

    private void sendToEndpoint(ProcessEndpoint endpoint, Object message) {
        executor.submit(() -> {
            try (Socket socket = new Socket(endpoint.getHostname(), endpoint.getPort());
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

                out.writeObject(message);
                out.flush();

            } catch (IOException e) {
                // Endpoint indisponible, le supprimer
                knownEndpoints.remove(endpoint.getProcessId());
            }
        });
    }

    private void listenTCP() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleIncomingMessage(clientSocket));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Erreur TCP: " + e.getMessage());
                }
            }
        }
    }

    private void handleIncomingMessage(Socket socket) {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            Object message = in.readObject();
            deliverLocally(message);
        } catch (Exception e) {
            System.err.println("Erreur lecture message: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    private void listenMulticast() {
        byte[] buffer = new byte[1024];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                multicastSocket.receive(packet);

                String data = new String(packet.getData(), 0, packet.getLength());
                String[] parts = data.split(":");

                if (parts.length == 3 && "DISCOVERY".equals(parts[0])) {
                    int processId = Integer.parseInt(parts[1]);
                    int port = Integer.parseInt(parts[2]);

                    if (processId != config.getProcessId()) {
                        ProcessEndpoint endpoint = new ProcessEndpoint(processId, packet.getAddress().getHostAddress(), port);
                        knownEndpoints.put(processId, endpoint);
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("Erreur multicast: " + e.getMessage());
                }
            }
        }
    }

    private void announcePresence() {
        while (running) {
            try {
                String announcement = "DISCOVERY:" + config.getProcessId() + ":" + config.getBasePort();
                byte[] data = announcement.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, multicastGroup, config.getMulticastPort());
                multicastSocket.send(packet);

                Thread.sleep(5000); // Annoncer toutes les 5 secondes
            } catch (Exception e) {
                if (running) {
                    System.err.println("Erreur annonce: " + e.getMessage());
                }
            }
        }
    }

    private Object getSubscriberInstance(Class<?> clazz) {
        // Retourner l'instance stockée lors de l'enregistrement
        return subscriberInstances.get(clazz);
    }

    public Map<Integer, ProcessEndpoint> getKnownEndpoints() {
        return new HashMap<>(knownEndpoints);
    }

    public ProcessEndpoint getLocalEndpoint() {
        return new ProcessEndpoint(config.getProcessId(), config.getBindAddress(), config.getBasePort());
    }

    public void shutdown() {
        running = false;

        try {
            if (multicastSocket != null) {
                multicastSocket.leaveGroup(multicastGroup);
                multicastSocket.close();
            }
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Erreur fermeture: " + e.getMessage());
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Annotation Subscribe simplifiée
    public @interface Subscribe {}
}