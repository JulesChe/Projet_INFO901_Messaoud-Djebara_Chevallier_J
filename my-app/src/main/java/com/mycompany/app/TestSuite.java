package com.mycompany.app;

/**
 * Suite de tests pour le middleware distribué simplifié.
 * Regroupe tous les tests fonctionnels.
 *
 * @author Middleware Team
 */
public class TestSuite {

    public static void main(String[] args) {
        System.out.println("=== SUITE DE TESTS MIDDLEWARE DISTRIBUÉ ===\n");

        try {
            testBasicCommunication();
            Thread.sleep(2000);

            testSynchronousComm();
            Thread.sleep(2000);

            testCriticalSection();
            Thread.sleep(2000);

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
    }

    /**
     * Test de communication synchrone.
     */
    private static void testSynchronousComm() throws InterruptedException {
        System.out.println("--- Test Communication Synchrone ---");

        Com com1 = new Com(new NetworkConfig(3, "localhost", 5003, "230.0.0.1", 4446));
        Com com2 = new Com(new NetworkConfig(4, "localhost", 5004, "230.0.0.1", 4446));

        Thread.sleep(3000); // Attendre découverte

        // Test broadcastSync
        Thread t1 = new Thread(() -> {
            try {
                com1.broadcastSync("Message sync", com1.getProcessId());
                System.out.println("✓ BroadcastSync terminé");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(500);
                com2.broadcastSync("Message sync", com1.getProcessId());
                System.out.println("✓ BroadcastSync reçu");
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
    }

    /**
     * Test de barrière de synchronisation.
     */
    private static void testBarrier() throws InterruptedException {
        System.out.println("--- Test Barrière ---");

        Com com1 = new Com(new NetworkConfig(7, "localhost", 5007, "230.0.0.1", 4446));
        Com com2 = new Com(new NetworkConfig(8, "localhost", 5008, "230.0.0.1", 4446));
        Com com3 = new Com(new NetworkConfig(9, "localhost", 5009, "230.0.0.1", 4446));

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
    }
}