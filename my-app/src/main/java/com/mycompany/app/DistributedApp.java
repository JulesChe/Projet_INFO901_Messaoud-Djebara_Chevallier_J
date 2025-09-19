package com.mycompany.app;

/**
 * Application distribuée pour tester l'architecture 100% distribuée.
 * Cette classe remplace les anciennes méthodes qui utilisaient des variables statiques.
 *
 * @author Middleware Team
 */
public class DistributedApp {

    public static void main(String[] args) {
        System.out.println("=== APPLICATION DISTRIBUÉE ===");
        System.out.println("Architecture 100% distribuée avec EventBus\n");

        if (args.length > 0) {
            int processId = Integer.parseInt(args[0]);
            runDistributedProcess(processId);
        } else {
            runDistributedDemo();
        }
    }

    /**
     * Lance un processus distribué spécifique.
     *
     * @param processId L'ID du processus à lancer
     */
    private static void runDistributedProcess(int processId) {
        System.out.println("Démarrage du processus distribué " + processId);

        NetworkConfig config = new NetworkConfig(processId, "localhost", 5000 + processId, "230.0.0.1", 4446);
        Com process = new Com(config);

        try {
            System.out.println("Processus " + process.getProcessId() + " démarré sur port " + config.getBasePort());

            // Simulation d'activité
            for (int i = 0; i < 5; i++) {
                Thread.sleep(3000);

                // Test de broadcast distribué
                process.broadcast("Message " + i + " du processus " + process.getProcessId());

                // Test de communication synchrone
                if (process.getProcessCount() > 1) {
                    System.out.println("Processus " + process.getProcessId() + " connaît " +
                                     process.getProcessCount() + " processus");
                }
            }

            // Test de barrière de synchronisation
            if (process.getProcessCount() > 1) {
                System.out.println("Processus " + process.getProcessId() + " teste la barrière");
                process.synchronize();
                System.out.println("Processus " + process.getProcessId() + " a passé la barrière");
            }

            // Laisser tourner pour observer la découverte
            Thread.sleep(10000);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            process.shutdown();
        }
    }

    /**
     * Lance une démonstration complète avec plusieurs processus.
     */
    private static void runDistributedDemo() {
        System.out.println("Démarrage de la démonstration distribuée complète...\n");

        // Créer plusieurs processus distribués
        Com process1 = new Com(new NetworkConfig(100, "localhost", 5100, "230.0.0.1", 4446));
        Com process2 = new Com(new NetworkConfig(101, "localhost", 5101, "230.0.0.1", 4446));
        Com process3 = new Com(new NetworkConfig(102, "localhost", 5102, "230.0.0.1", 4446));

        try {
            System.out.println("Processus créés : " + process1.getProcessId() +
                             ", " + process2.getProcessId() + ", " + process3.getProcessId());

            // Phase 1 : Découverte automatique
            System.out.println("\n--- Phase 1 : Découverte automatique ---");
            Thread.sleep(5000);

            System.out.println("Process1 connaît " + process1.getProcessCount() + " processus");
            System.out.println("Process2 connaît " + process2.getProcessCount() + " processus");
            System.out.println("Process3 connaît " + process3.getProcessCount() + " processus");

            // Phase 2 : Communication distribuée
            System.out.println("\n--- Phase 2 : Communication distribuée ---");
            process1.broadcast("Broadcast distribué de " + process1.getProcessId());
            process2.sendTo("Message point-à-point réseau", process3.getProcessId());

            Thread.sleep(3000);

            // Phase 3 : Token distribué
            System.out.println("\n--- Phase 3 : Token distribué ---");
            process1.requestSC();
            Thread.sleep(1000);
            System.out.println("Process1 en section critique");
            process1.releaseSC();

            Thread.sleep(2000);

            process2.requestSC();
            Thread.sleep(1000);
            System.out.println("Process2 en section critique");
            process2.releaseSC();

            // Phase 4 : Barrière distribuée
            System.out.println("\n--- Phase 4 : Barrière distribuée ---");

            Thread t1 = new Thread(() -> {
                try {
                    System.out.println("Process1 arrive à la barrière");
                    process1.synchronize();
                    System.out.println("Process1 repart de la barrière");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            Thread t2 = new Thread(() -> {
                try {
                    Thread.sleep(2000); // Décalage
                    System.out.println("Process2 arrive à la barrière");
                    process2.synchronize();
                    System.out.println("Process2 repart de la barrière");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            Thread t3 = new Thread(() -> {
                try {
                    Thread.sleep(4000); // Décalage plus important
                    System.out.println("Process3 arrive à la barrière");
                    process3.synchronize();
                    System.out.println("Process3 repart de la barrière");
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

            System.out.println("\n--- Test terminé avec succès ! ---");

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            System.out.println("Arrêt des processus...");
            process1.shutdown();
            process2.shutdown();
            process3.shutdown();
        }
    }
}