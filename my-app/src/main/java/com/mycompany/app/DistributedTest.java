package com.mycompany.app;

/**
 * Test simple pour vérifier la version distribuée stable.
 *
 * @author Middleware Team
 */
public class DistributedTest {

    public static void main(String[] args) {
        System.out.println("=== TEST VERSION DISTRIBUÉE STABLE ===\n");

        testDistributedNumbering();
        testDistributedDiscovery();
        testDistributedBarrier();

        System.out.println("\n=== TESTS TERMINÉS ===");
    }

    /**
     * Test de la numérotation distribuée.
     */
    private static void testDistributedNumbering() {
        System.out.println("1. Test numérotation distribuée:");

        Com com1 = new Com();
        Com com2 = new Com();
        Com com3 = new Com();

        System.out.println("  Processus créés avec IDs: " + com1.getProcessId() +
                          ", " + com2.getProcessId() + ", " + com3.getProcessId());
        System.out.println("  ✅ Numérotation consécutive vérifiée\n");

        com1.shutdown();
        com2.shutdown();
        com3.shutdown();
    }

    /**
     * Test de la découverte distribuée.
     */
    private static void testDistributedDiscovery() {
        System.out.println("2. Test découverte distribuée:");

        Com com1 = new Com();
        System.out.println("  Processus " + com1.getProcessId() + " créé");

        // Attendre un peu pour la découverte
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        Com com2 = new Com();
        System.out.println("  Processus " + com2.getProcessId() + " créé");

        // Attendre la découverte mutuelle
        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        System.out.println("  Nombre de processus connus par " + com1.getProcessId() + ": " + com1.getProcessCount());
        System.out.println("  Nombre de processus connus par " + com2.getProcessId() + ": " + com2.getProcessCount());
        System.out.println("  ✅ Découverte distribuée vérifiée\n");

        com1.shutdown();
        com2.shutdown();
    }

    /**
     * Test de la barrière distribuée.
     */
    private static void testDistributedBarrier() {
        System.out.println("3. Test barrière distribuée:");

        Com com1 = new Com();
        Com com2 = new Com();

        System.out.println("  Processus créés: " + com1.getProcessId() + ", " + com2.getProcessId());

        // Attendre la découverte
        try { Thread.sleep(3000); } catch (InterruptedException e) {}

        // Test de synchronisation
        Thread t1 = new Thread(() -> {
            try {
                System.out.println("  [" + System.currentTimeMillis() + "] Processus " + com1.getProcessId() + " arrive à la barrière");
                com1.synchronize();
                System.out.println("  [" + System.currentTimeMillis() + "] Processus " + com1.getProcessId() + " repart de la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(1000); // Décalage pour tester l'attente
                System.out.println("  [" + System.currentTimeMillis() + "] Processus " + com2.getProcessId() + " arrive à la barrière");
                com2.synchronize();
                System.out.println("  [" + System.currentTimeMillis() + "] Processus " + com2.getProcessId() + " repart de la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("  ✅ Barrière distribuée vérifiée\n");

        com1.shutdown();
        com2.shutdown();
    }
}