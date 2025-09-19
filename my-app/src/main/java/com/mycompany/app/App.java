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
     * Démonstration complète avec plusieurs processus dans la même JVM.
     */
    private static void runCompleteDemo() {
        System.out.println("Démarrage de la démonstration complète...\n");

        Com process1 = new Com(new NetworkConfig(10, "localhost", 5010, "230.0.0.1", 4446));
        Com process2 = new Com(new NetworkConfig(11, "localhost", 5011, "230.0.0.1", 4446));
        Com process3 = new Com(new NetworkConfig(12, "localhost", 5012, "230.0.0.1", 4446));

        try {
            System.out.println("Processus créés : " + process1.getProcessId() +
                             ", " + process2.getProcessId() + ", " + process3.getProcessId());

            // Phase 1 : Découverte automatique
            System.out.println("\n--- Phase 1 : Découverte automatique ---");
            Thread.sleep(5000);
            System.out.println("Processus découverts par chaque instance");

            // Phase 2 : Communication distribuée
            System.out.println("\n--- Phase 2 : Communication distribuée ---");
            process1.broadcast("Broadcast distribué de " + process1.getProcessId());
            process2.sendTo("Message point-à-point", process3.getProcessId());
            Thread.sleep(2000);

            // Phase 3 : Section critique distribuée
            System.out.println("\n--- Phase 3 : Section critique distribuée ---");
            testCriticalSection(process1, process2);

            // Phase 4 : Barrière distribuée
            System.out.println("\n--- Phase 4 : Barrière distribuée ---");
            testBarrier(process1, process2, process3);

            System.out.println("\n--- Démonstration terminée avec succès ! ---");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            System.out.println("Arrêt des processus...");
            process1.shutdown();
            process2.shutdown();
            process3.shutdown();
        }
    }

    private static void testCriticalSection(Com p1, Com p2) throws InterruptedException {
        Thread t1 = new Thread(() -> {
            try {
                p1.requestSC();
                System.out.println("Process " + p1.getProcessId() + " en SC");
                Thread.sleep(1000);
                p1.releaseSC();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(500);
                p2.requestSC();
                System.out.println("Process " + p2.getProcessId() + " en SC");
                Thread.sleep(1000);
                p2.releaseSC();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

    private static void testBarrier(Com p1, Com p2, Com p3) throws InterruptedException {
        Thread t1 = new Thread(() -> {
            try {
                System.out.println("Process " + p1.getProcessId() + " arrive à la barrière");
                p1.synchronize();
                System.out.println("Process " + p1.getProcessId() + " repart de la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(1000);
                System.out.println("Process " + p2.getProcessId() + " arrive à la barrière");
                p2.synchronize();
                System.out.println("Process " + p2.getProcessId() + " repart de la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread t3 = new Thread(() -> {
            try {
                Thread.sleep(2000);
                System.out.println("Process " + p3.getProcessId() + " arrive à la barrière");
                p3.synchronize();
                System.out.println("Process " + p3.getProcessId() + " repart de la barrière");
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
    }
}