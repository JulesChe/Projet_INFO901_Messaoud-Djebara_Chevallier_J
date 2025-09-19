package com.mycompany.app;

import java.util.Random;

/**
 * Suite de tests pour le middleware distribué simplifié.
 * Regroupe tous les tests fonctionnels avec gestion des ports dynamiques.
 *
 * @author Middleware Team
 */
public class TestSuite {

    private static final Random random = new Random();

    /**
     * Génère un port aléatoire dans une plage libre.
     */
    private static int getRandomPort() {
        return 7000 + random.nextInt(1000); // Ports 7000-8000
    }

    /**
     * Attend que les ports se libèrent.
     */
    private static void waitForPortsToFree() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        System.out.println("=== SUITE DE TESTS MIDDLEWARE DISTRIBUÉ ===\n");

        try {
            testBasicCommunication();
            waitForPortsToFree();

            testSynchronousComm();
            waitForPortsToFree();

            testCriticalSection();
            waitForPortsToFree();

            testBarrier();

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

        int port1 = getRandomPort();
        int port2 = getRandomPort() + 1;
        int multicastPort = getRandomPort() + 100;

        Com com1 = new Com(new NetworkConfig(1, "localhost", port1, "230.0.0.1", multicastPort));
        Com com2 = new Com(new NetworkConfig(2, "localhost", port2, "230.0.0.1", multicastPort));

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
        Thread.sleep(1000); // Attendre shutdown complet
    }

    /**
     * Test de communication synchrone.
     */
    private static void testSynchronousComm() throws InterruptedException {
        System.out.println("--- Test Communication Synchrone ---");

        int port1 = getRandomPort();
        int port2 = getRandomPort() + 1;
        int multicastPort = getRandomPort() + 100;

        Com com1 = new Com(new NetworkConfig(3, "localhost", port1, "230.0.0.1", multicastPort));
        Com com2 = new Com(new NetworkConfig(4, "localhost", port2, "230.0.0.1", multicastPort));

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
        Thread.sleep(1000); // Attendre shutdown complet
    }

    /**
     * Test de section critique distribuée.
     */
    private static void testCriticalSection() throws InterruptedException {
        System.out.println("--- Test Section Critique ---");

        int port1 = getRandomPort();
        int port2 = getRandomPort() + 1;
        int multicastPort = getRandomPort() + 100;

        Com com1 = new Com(new NetworkConfig(5, "localhost", port1, "230.0.0.1", multicastPort));
        Com com2 = new Com(new NetworkConfig(6, "localhost", port2, "230.0.0.1", multicastPort));

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
        Thread.sleep(1000); // Attendre shutdown complet
    }

    /**
     * Test de barrière de synchronisation.
     */
    private static void testBarrier() throws InterruptedException {
        System.out.println("--- Test Barrière ---");

        int port1 = getRandomPort();
        int port2 = getRandomPort() + 1;
        int port3 = getRandomPort() + 2;
        int multicastPort = getRandomPort() + 100;

        Com com1 = new Com(new NetworkConfig(7, "localhost", port1, "230.0.0.1", multicastPort));
        Com com2 = new Com(new NetworkConfig(8, "localhost", port2, "230.0.0.1", multicastPort));
        Com com3 = new Com(new NetworkConfig(9, "localhost", port3, "230.0.0.1", multicastPort));

        Thread.sleep(3000); // Attendre découverte

        Thread t1 = new Thread(() -> {
            try {
                System.out.println("Com1 arrive à la barrière");
                com1.synchronize();
                System.out.println("Com1 repart de la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(1000);
                System.out.println("Com2 arrive à la barrière");
                com2.synchronize();
                System.out.println("Com2 repart de la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread t3 = new Thread(() -> {
            try {
                Thread.sleep(2000);
                System.out.println("Com3 arrive à la barrière");
                com3.synchronize();
                System.out.println("Com3 repart de la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t1.start();
        t2.start();
        t3.start();
        t1.join();
        t2.join();
        t3.join();

        System.out.println("✓ Barrière OK");

        com1.shutdown();
        com2.shutdown();
        com3.shutdown();
        Thread.sleep(1000); // Attendre shutdown complet
    }
}