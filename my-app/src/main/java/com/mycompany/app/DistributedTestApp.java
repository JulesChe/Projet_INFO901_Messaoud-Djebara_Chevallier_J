package com.mycompany.app;

/**
 * Application de test pour le système distribué avec vraie communication réseau.
 *
 * Pour tester :
 * 1. Lancer plusieurs instances avec des IDs différents :
 *    java -cp target/classes com.mycompany.app.DistributedTestApp 0
 *    java -cp target/classes com.mycompany.app.DistributedTestApp 1
 *    java -cp target/classes com.mycompany.app.DistributedTestApp 2
 *
 * 2. Les processus se découvrent automatiquement via multicast
 * 3. Communication via TCP entre processus
 */
public class DistributedTestApp {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java DistributedTestApp <processId>");
            System.err.println("Exemple: java DistributedTestApp 0");
            System.exit(1);
        }

        int processId = Integer.parseInt(args[0]);
        String testMode = args.length > 1 ? args[1] : "communication";

        System.out.println("=== SYSTÈME DISTRIBUÉ - PROCESSUS " + processId + " ===");
        System.out.println("Mode de test: " + testMode);

        NetworkConfig config = new NetworkConfig(processId);
        DistributedCom com = new DistributedCom(config);

        try {
            switch (testMode.toLowerCase()) {
                case "communication":
                    testDistributedCommunication(com, processId);
                    break;
                case "token":
                    testDistributedToken(com, processId);
                    break;
                case "sync":
                    testDistributedSync(com, processId);
                    break;
                default:
                    runInteractiveMode(com, processId);
                    break;
            }
        } finally {
            // Arrêter proprement
            System.out.println("Arrêt du processus " + processId);
            com.shutdown();
        }
    }

    /**
     * Test de communication distribuée asynchrone.
     */
    private static void testDistributedCommunication(DistributedCom com, int processId) {
        System.out.println("Test de communication distribuée...");

        try {
            // Attendre un peu pour la découverte
            Thread.sleep(3000);

            System.out.println("Processus découverts: " + com.getKnownProcesses());

            if (processId == 0) {
                // Le processus 0 envoie des messages
                Thread.sleep(2000);
                System.out.println("P0 envoie un message direct à P1");
                com.sendTo("Message de P0 vers P1", 1);

                Thread.sleep(1000);
                System.out.println("P0 fait un broadcast");
                com.broadcast("Broadcast de P0");
            }

            // Tous les processus écoutent pendant 10 secondes
            for (int i = 0; i < 10; i++) {
                Thread.sleep(1000);

                // Afficher les messages reçus
                while (!com.mailbox.isEmpty()) {
                    Message msg = com.mailbox.getMessageNonBlocking();
                    if (msg != null) {
                        System.out.println("P" + processId + " a reçu: " + msg.getPayload());
                    }
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Test de système de token distribué.
     */
    private static void testDistributedToken(DistributedCom com, int processId) {
        System.out.println("Test de token distribué...");

        try {
            // Attendre la découverte
            Thread.sleep(3000);

            if (processId == 0) {
                // Le processus 0 initialise le token
                System.out.println("P0 initialise le token");
                com.initToken();
            }

            // Tous les processus tentent d'accéder à la section critique
            Thread.sleep(1000 + processId * 500); // Décalage pour éviter la concurrence

            System.out.println("P" + processId + " demande la section critique");
            com.requestSC();

            // Simule du travail en section critique
            System.out.println("P" + processId + " travaille en section critique...");
            Thread.sleep(2000);

            com.releaseSC();
            System.out.println("P" + processId + " a libéré la section critique");

            // Attendre un peu pour voir les autres processus
            Thread.sleep(10000);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Test de communication synchrone distribuée.
     */
    private static void testDistributedSync(DistributedCom com, int processId) {
        System.out.println("Test de communication synchrone distribuée...");

        try {
            // Attendre la découverte
            Thread.sleep(3000);

            if (processId == 0) {
                // P0 envoie un message synchrone à P1
                Thread.sleep(1000);
                System.out.println("P0 envoie message synchrone à P1");
                com.sendToSync("Message sync de P0", 1);
                System.out.println("P0 a terminé l'envoi synchrone");
            } else if (processId == 1) {
                // P1 attend le message synchrone de P0
                Thread.sleep(2000);
                System.out.println("P1 attend message synchrone de P0");
                com.recvFromSync("", 0);
                System.out.println("P1 a reçu le message synchrone");
            }

            Thread.sleep(5000);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Mode interactif pour tests manuels.
     */
    private static void runInteractiveMode(DistributedCom com, int processId) {
        System.out.println("Mode interactif - Processus " + processId + " en attente...");
        System.out.println("Ce processus écoute les messages et affiche les informations de découverte.");

        try {
            for (int i = 0; i < 60; i++) { // 1 minute
                Thread.sleep(1000);

                // Afficher périodiquement les processus découverts
                if (i % 10 == 0) {
                    System.out.println("Processus découverts: " + com.getKnownProcesses().size());
                    for (ProcessEndpoint endpoint : com.getKnownProcesses()) {
                        System.out.println("  - " + endpoint);
                    }
                }

                // Afficher les messages reçus
                while (!com.mailbox.isEmpty()) {
                    Message msg = com.mailbox.getMessageNonBlocking();
                    if (msg != null) {
                        System.out.println("P" + processId + " reçu: " + msg.getPayload() +
                                         " de P" + msg.getSender());
                    }
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}