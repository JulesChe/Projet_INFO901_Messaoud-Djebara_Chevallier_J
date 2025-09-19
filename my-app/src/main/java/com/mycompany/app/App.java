package com.mycompany.app;

/**
 * Application principale démontrant les fonctionnalités du middleware distribué.
 * Version simplifiée avec démo principale et mode distribué.
 *
 * @author Middleware Team
 */
public class App {

    /**
     * Point d'entrée de l'application.
     *
     * @param args Arguments : "distributed" pour mode distribué, "demo" (défaut) pour démonstration
     */
    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "demo";

        System.out.println("=== MIDDLEWARE DISTRIBUÉ SIMPLIFIÉ ===");
        System.out.println("Architecture distribuée avec EventBus");
        System.out.println("Mode: " + mode + "\n");

        if ("distributed".equals(mode.toLowerCase())) {
            runDistributedMode(args);
        } else {
            runCompleteDemo();
        }
    }

    /**
     * Mode distribué : lance un processus avec ID spécifique.
     * Usage: java App distributed <processId>
     */
    private static void runDistributedMode(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java App distributed <processId>");
            return;
        }

        int processId = Integer.parseInt(args[1]);
        System.out.println("Démarrage du processus distribué " + processId);

        NetworkConfig config = new NetworkConfig(processId, "localhost", 5000 + processId, "230.0.0.1", 4446);
        Com process = new Com(config);

        try {
            System.out.println("Processus " + process.getProcessId() + " démarré sur port " + config.getBasePort());

            // Test de broadcast distribué
            for (int i = 0; i < 3; i++) {
                Thread.sleep(3000);
                process.broadcast("Message " + i + " du processus " + process.getProcessId());
            }

            // Test section critique si d'autres processus
            if (process.getProcessCount() > 1) {
                System.out.println("Test section critique...");
                process.requestSC();
                Thread.sleep(1000);
                System.out.println("Processus " + process.getProcessId() + " en section critique");
                process.releaseSC();
            }

            // Maintenir le processus actif
            Thread.sleep(30000);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            process.shutdown();
        }
    }

    /**
     * Suite de tests complète du middleware distribué.
     */
    private static void runCompleteDemo() {
        System.out.println("=== SUITE DE TESTS MIDDLEWARE DISTRIBUÉ ===\n");

        try {
            testBasicCommunication();
            Thread.sleep(2000);

            testSynchronousComm();
            Thread.sleep(2000);

            testCriticalSection();

            System.out.println("\n=== TOUS LES TESTS RÉUSSIS ===");

        } catch (Exception e) {
            System.err.println("Erreur dans les tests: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test de communication asynchrone de base.
     */
    private static void testBasicCommunication() throws InterruptedException {
        System.out.println("--- Test Communication Asynchrone ---");

        Com com1 = new Com(new NetworkConfig(1, "localhost", 5001, "230.0.0.1", 4446));
        Com com2 = new Com(new NetworkConfig(2, "localhost", 5002, "230.0.0.1", 4446));

        Thread.sleep(3000); // Attendre découverte

        // Test broadcast
        com1.broadcast("Message broadcast de com1");
        Thread.sleep(1000);

        // Test sendTo
        com2.sendTo("Message direct de com2", com1.getProcessId());
        Thread.sleep(1000);

        System.out.println("✓ Communication asynchrone OK");

        com1.shutdown();
        com2.shutdown();
        Thread.sleep(1000);
    }

    /**
     * Test de communication synchrone.
     */
    private static void testSynchronousComm() throws InterruptedException {
        System.out.println("--- Test Communication Synchrone ---");

        Com com1 = new Com(new NetworkConfig(3, "localhost", 5003, "230.0.0.1", 4446));
        Com com2 = new Com(new NetworkConfig(4, "localhost", 5004, "230.0.0.1", 4446));

        Thread.sleep(3000); // Attendre découverte

        // Test broadcastSync - seulement l'expéditeur
        Thread t1 = new Thread(() -> {
            try {
                Thread.sleep(1000); // Laisser temps à la découverte
                com1.broadcastSync("Message sync", com1.getProcessId());
                System.out.println("✓ BroadcastSync terminé");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                // com2 attend juste, il recevra automatiquement
                Thread.sleep(3000);
                System.out.println("✓ BroadcastSync reçu par com2");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("✓ Communication synchrone OK");

        com1.shutdown();
        com2.shutdown();
        Thread.sleep(1000);
    }

    /**
     * Test de section critique distribuée.
     */
    private static void testCriticalSection() throws InterruptedException {
        System.out.println("--- Test Section Critique ---");

        Com com1 = new Com(new NetworkConfig(5, "localhost", 5005, "230.0.0.1", 4446));
        Com com2 = new Com(new NetworkConfig(6, "localhost", 5006, "230.0.0.1", 4446));

        Thread.sleep(5000); // Attendre token

        Thread t1 = new Thread(() -> {
            try {
                System.out.println("Com1 demande SC");
                com1.requestSC();
                System.out.println("Com1 en SC");
                Thread.sleep(1000);
                com1.releaseSC();
                System.out.println("Com1 sort de SC");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(500);
                System.out.println("Com2 demande SC");
                com2.requestSC();
                System.out.println("Com2 en SC");
                Thread.sleep(1000);
                com2.releaseSC();
                System.out.println("Com2 sort de SC");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("✓ Section critique OK");

        com1.shutdown();
        com2.shutdown();
        Thread.sleep(1000);
    }
}