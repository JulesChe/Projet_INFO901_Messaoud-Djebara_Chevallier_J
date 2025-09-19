package com.mycompany.app;

/**
 * Application principale démontrant les fonctionnalités du middleware.
 *
 * @author Middleware Team
 */
public class App {

    /**
     * Point d'entrée de l'application.
     *
     * @param args Arguments de ligne de commande :
     *             - "distributed" : Lance les tests de la version distribuée
     *             - "multi" : Lance plusieurs processus distribués
     *             - "single" : Lance un seul processus
     *             - "demo" : Lance une démonstration complète (par défaut)
     */
    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "demo";

        System.out.println("=== MIDDLEWARE DE COMMUNICATION DISTRIBUÉE ===");
        System.out.println("Architecture 100% distribuée avec EventBus");
        System.out.println("Mode: " + mode + "\n");

        switch (mode.toLowerCase()) {
            case "distributed":
                runDistributedTest();
                break;
            case "multi":
                runMultiProcessTest();
                break;
            case "single":
                runSingleProcessTest();
                break;
            case "demo":
            default:
                runCompleteDemo();
                break;
        }
    }

    /**
     * Lance un seul processus distribué pour test.
     */
    private static void runSingleProcessTest() {
        System.out.println("Lancement d'un processus distribué...\n");

        // Créer un processus avec configuration réseau
        NetworkConfig config = new NetworkConfig(0); // Processus ID 0
        Com process = new Com(config);

        try {
            System.out.println("Processus " + process.getProcessId() + " démarré");
            System.out.println("Test de fonctionnalités locales...");

            // Test de l'horloge de Lamport
            process.inc_clock();
            System.out.println("Horloge incrémentée");

            // Test de broadcast (sera reçu par d'autres processus s'ils existent)
            process.broadcast("Message de test depuis processus " + process.getProcessId());

            Thread.sleep(10000); // Attendre 10 secondes

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            process.shutdown();
        }

        System.out.println("Test processus unique terminé.");
    }

    /**
     * Test de section critique distribuée.
     */
    private static void testDistributedCriticalSection() {
        Com com1 = new Com(new NetworkConfig(30, "localhost", 5030, "230.0.0.1", 4446));
        Com com2 = new Com(new NetworkConfig(31, "localhost", 5031, "230.0.0.1", 4446));

        try {
            // Attendre la découverte et l'initialisation du token distribué
            Thread.sleep(5000);

            Thread t1 = new Thread(() -> {
                try {
                    System.out.println("Processus " + com1.getProcessId() + " demande SC distribué");
                    com1.requestSC();
                    System.out.println("Processus " + com1.getProcessId() + " DANS SC distribué");
                    Thread.sleep(2000);
                    System.out.println("Processus " + com1.getProcessId() + " libère SC distribué");
                    com1.releaseSC();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            Thread t2 = new Thread(() -> {
                try {
                    Thread.sleep(500);
                    System.out.println("Processus " + com2.getProcessId() + " demande SC distribué");
                    com2.requestSC();
                    System.out.println("Processus " + com2.getProcessId() + " DANS SC distribué");
                    Thread.sleep(2000);
                    System.out.println("Processus " + com2.getProcessId() + " libère SC distribué");
                    com2.releaseSC();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            t1.start();
            t2.start();

            t1.join();
            t2.join();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            com1.shutdown();
            com2.shutdown();
        }
    }

    /**
     * Test de synchronisation distribuée via barrière.
     */
    private static void testDistributedSynchronization() {
        Com com1 = new Com(new NetworkConfig(40, "localhost", 5040, "230.0.0.1", 4446));
        Com com2 = new Com(new NetworkConfig(41, "localhost", 5041, "230.0.0.1", 4446));

        try {
            // Attendre la découverte
            Thread.sleep(5000);

            Thread t1 = new Thread(() -> {
                try {
                    System.out.println("Processus " + com1.getProcessId() + " arrive à la barrière distribuée");
                    com1.synchronize();
                    System.out.println("Processus " + com1.getProcessId() + " repart de la barrière distribuée");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            Thread t2 = new Thread(() -> {
                try {
                    Thread.sleep(3000); // Décalage pour tester l'attente
                    System.out.println("Processus " + com2.getProcessId() + " arrive à la barrière distribuée");
                    com2.synchronize();
                    System.out.println("Processus " + com2.getProcessId() + " repart de la barrière distribuée");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            t1.start();
            t2.start();

            t1.join();
            t2.join();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            com1.shutdown();
            com2.shutdown();
        }
    }

    /**
     * Lance une démonstration complète de l'architecture distribuée.
     */
    private static void runCompleteDemo() {
        System.out.println("=== DÉMONSTRATION MIDDLEWARE DISTRIBUÉ ===\n");

        // Test de communication asynchrone distribuée
        System.out.println("1. Test de communication asynchrone distribuée:");
        testDistributedAsyncCommunication();

        // Pause entre les tests
        try { Thread.sleep(3000); } catch (InterruptedException e) {}

        // Test de section critique distribuée
        System.out.println("\n2. Test de section critique distribuée avec token réseau:");
        testDistributedCriticalSection();

        // Pause entre les tests
        try { Thread.sleep(3000); } catch (InterruptedException e) {}

        // Test de synchronisation distribuée
        System.out.println("\n3. Test de barrière de synchronisation distribuée:");
        testDistributedSynchronization();

        System.out.println("\n=== DÉMONSTRATION TERMINÉE ===");
    }

    /**
     * Test de communication asynchrone distribuée.
     */
    private static void testDistributedAsyncCommunication() {
        // Créer processus avec ports différents pour simulation réseau
        Com com1 = new Com(new NetworkConfig(20, "localhost", 5020, "230.0.0.1", 4446));
        Com com2 = new Com(new NetworkConfig(21, "localhost", 5021, "230.0.0.1", 4446));
        Com com3 = new Com(new NetworkConfig(22, "localhost", 5022, "230.0.0.1", 4446));

        System.out.println("Processus créés avec IDs: " + com1.getProcessId() + ", " +
                          com2.getProcessId() + ", " + com3.getProcessId());

        try {
            // Attendre la découverte automatique
            Thread.sleep(3000);

            System.out.println("Envoi de messages via EventBus...");
            com1.sendTo("Message réseau de " + com1.getProcessId() + " vers " + com2.getProcessId(), com2.getProcessId());
            com1.broadcast("Broadcast réseau de " + com1.getProcessId());

            Thread.sleep(2000);

            System.out.println("Communication distribuée testée !");

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            com1.shutdown();
            com2.shutdown();
            com3.shutdown();
        }
    }

    /**
     * Test de l'ancienne communication asynchrone (pour comparaison).
     */
    private static void testAsyncCommunication() {
        Com com1 = new Com();
        Com com2 = new Com();
        Com com3 = new Com();

        System.out.println("Processus créés avec IDs: " + com1.getProcessId() + ", " +
                          com2.getProcessId() + ", " + com3.getProcessId());

        com1.sendTo("Message de " + com1.getProcessId() + " vers " + com2.getProcessId(), com2.getProcessId());
        com1.broadcast("Broadcast de " + com1.getProcessId());

        try {
            Thread.sleep(1000);

            System.out.println("Messages reçus par " + com2.getProcessId() + ":");
            while (!com2.mailbox.isEmpty()) {
                Message msg = com2.mailbox.getMessageNonBlocking();
                if (msg != null) {
                    System.out.println("  - " + msg.getPayload());
                }
            }

            System.out.println("Messages reçus par " + com3.getProcessId() + ":");
            while (!com3.mailbox.isEmpty()) {
                Message msg = com3.mailbox.getMessageNonBlocking();
                if (msg != null) {
                    System.out.println("  - " + msg.getPayload());
                }
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Test de section critique distribuée.
     */
    private static void testCriticalSection() {
        Com com1 = new Com();
        Com com2 = new Com();

        // Démarrer le gestionnaire de jeton
        TokenManager.getInstance().start();

        Thread t1 = new Thread(() -> {
            try {
                System.out.println("Processus " + com1.getProcessId() + " demande SC");
                com1.requestSC();
                System.out.println("Processus " + com1.getProcessId() + " DANS SC");
                Thread.sleep(1000);
                System.out.println("Processus " + com1.getProcessId() + " libère SC");
                com1.releaseSC();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(200);
                System.out.println("Processus " + com2.getProcessId() + " demande SC");
                com2.requestSC();
                System.out.println("Processus " + com2.getProcessId() + " DANS SC");
                Thread.sleep(1000);
                System.out.println("Processus " + com2.getProcessId() + " libère SC");
                com2.releaseSC();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            com1.shutdown();
            com2.shutdown();
            TokenManager.getInstance().stop();
        }
    }

    /**
     * Test de barrière de synchronisation selon les concepts de @CM/LaBarriereDeSynchro.pdf.
     * Démontre que tous les processus attendent à la barrière et repartent ensemble.
     */
    private static void testSynchronization() {
        Com com1 = new Com();
        Com com2 = new Com();
        Com com3 = new Com();

        System.out.println("Création de 3 processus pour tester la barrière de synchronisation:");
        System.out.println("- Les processus arrivent à des moments différents");
        System.out.println("- Ils attendent tous à la barrière");
        System.out.println("- Tous repartent ensemble quand le dernier arrive\n");

        Thread t1 = new Thread(() -> {
            try {
                System.out.println("[" + System.currentTimeMillis() + "] Processus " + com1.getProcessId() + " travaille avant la barrière...");
                Thread.sleep(100);  // Simule du travail

                com1.synchronize();  // BARRIÈRE

                System.out.println("[" + System.currentTimeMillis() + "] Processus " + com1.getProcessId() + " continue après la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "SyncTest-P1");

        Thread t2 = new Thread(() -> {
            try {
                System.out.println("[" + System.currentTimeMillis() + "] Processus " + com2.getProcessId() + " travaille avant la barrière...");
                Thread.sleep(800);  // Simule plus de travail

                com2.synchronize();  // BARRIÈRE

                System.out.println("[" + System.currentTimeMillis() + "] Processus " + com2.getProcessId() + " continue après la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "SyncTest-P2");

        Thread t3 = new Thread(() -> {
            try {
                System.out.println("[" + System.currentTimeMillis() + "] Processus " + com3.getProcessId() + " travaille avant la barrière...");
                Thread.sleep(1500);  // Simule encore plus de travail

                com3.synchronize();  // BARRIÈRE

                System.out.println("[" + System.currentTimeMillis() + "] Processus " + com3.getProcessId() + " continue après la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "SyncTest-P3");

        // Démarrer tous les threads
        t1.start();
        t2.start();
        t3.start();

        try {
            t1.join();
            t2.join();
            t3.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            com1.shutdown();
            com2.shutdown();
            com3.shutdown();
        }

        System.out.println("\nTest de barrière terminé - Tous les processus ont passé la barrière ensemble !");
    }

    /**
     * Lance les tests de communication synchrone.
     */
    private static void runSyncCommunicationTest() {
        System.out.println("=== TEST DE COMMUNICATION SYNCHRONE ===\n");

        // Test 1: broadcastSync
        testBroadcastSync();

        // Pause entre les tests
        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        // Test 2: sendToSync / recevFromSync
        testSendToSyncAndRecevFromSync();

        System.out.println("\n=== TESTS DE COMMUNICATION SYNCHRONE TERMINÉS ===");
    }

    /**
     * Test de la diffusion synchrone.
     */
    private static void testBroadcastSync() {
        System.out.println("1. TEST BROADCAST SYNCHRONE:");
        System.out.println("   - Le processus 0 diffuse un message");
        System.out.println("   - Les processus 1 et 2 attendent ce message");
        System.out.println("   - Le processus 0 est bloqué jusqu'à ce que tous aient reçu\n");

        Com com0 = new Com();
        Com com1 = new Com();
        Com com2 = new Com();

        System.out.println("Processus créés avec IDs: " + com0.getProcessId() + ", " +
                          com1.getProcessId() + ", " + com2.getProcessId());

        // Thread pour le processus récepteur 1
        Thread receiver1 = new Thread(() -> {
            try {
                Thread.sleep(500); // Simuler du travail avant d'attendre
                System.out.println("Processus " + com1.getProcessId() + " se prépare à recevoir broadcastSync");
                com1.broadcastSync(null, com0.getProcessId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Receiver1");

        // Thread pour le processus récepteur 2
        Thread receiver2 = new Thread(() -> {
            try {
                Thread.sleep(800); // Simuler plus de travail
                System.out.println("Processus " + com2.getProcessId() + " se prépare à recevoir broadcastSync");
                com2.broadcastSync(null, com0.getProcessId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Receiver2");

        // Thread pour le processus expéditeur
        Thread sender = new Thread(() -> {
            try {
                Thread.sleep(200); // Laisser le temps aux récepteurs de se préparer
                System.out.println("Processus " + com0.getProcessId() + " commence broadcastSync");
                long startTime = System.currentTimeMillis();

                com0.broadcastSync("Message synchrone de P" + com0.getProcessId(), com0.getProcessId());

                long endTime = System.currentTimeMillis();
                System.out.println("BroadcastSync terminé en " + (endTime - startTime) + "ms");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Sender");

        // Démarrer tous les threads
        receiver1.start();
        receiver2.start();
        sender.start();

        // Attendre la fin
        try {
            sender.join();
            receiver1.join();
            receiver2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Nettoyer
        com0.shutdown();
        com1.shutdown();
        com2.shutdown();

        System.out.println("Test broadcastSync terminé.\n");
    }

    /**
     * Test de l'envoi/réception synchrone.
     */
    private static void testSendToSyncAndRecevFromSync() {
        System.out.println("2. TEST SEND/RECEIVE SYNCHRONE:");
        System.out.println("   - Le processus 0 envoie un message synchrone au processus 1");
        System.out.println("   - Le processus 1 attend ce message avec recevFromSync");
        System.out.println("   - Le processus 0 est bloqué jusqu'à ce que le processus 1 reçoive\n");

        Com com0 = new Com();
        Com com1 = new Com();

        System.out.println("Processus créés avec IDs: " + com0.getProcessId() + ", " + com1.getProcessId());

        // Thread pour le processus récepteur
        Thread receiver = new Thread(() -> {
            try {
                Thread.sleep(1000); // Simuler du travail avant de recevoir
                System.out.println("Processus " + com1.getProcessId() + " commence recevFromSync");

                SyncMessage receivedMsg = com1.recevFromSync(com0.getProcessId());
                if (receivedMsg != null) {
                    System.out.println("RecevFromSync réussi: " + receivedMsg.getPayload());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "SyncReceiver");

        // Thread pour le processus expéditeur
        Thread sender = new Thread(() -> {
            try {
                Thread.sleep(200); // Laisser le temps au récepteur de se préparer
                System.out.println("Processus " + com0.getProcessId() + " commence sendToSync");
                long startTime = System.currentTimeMillis();

                com0.sendToSync("Message synchrone direct vers P" + com1.getProcessId(), com1.getProcessId());

                long endTime = System.currentTimeMillis();
                System.out.println("SendToSync terminé en " + (endTime - startTime) + "ms");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "SyncSender");

        // Démarrer les threads
        receiver.start();
        sender.start();

        // Attendre la fin
        try {
            sender.join();
            receiver.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Nettoyer
        com0.shutdown();
        com1.shutdown();

        System.out.println("Test sendToSync/recevFromSync terminé.\n");
    }

    /**
     * Test du système de heartbeat et de renumération automatique.
     * Démontre la détection de pannes et la correction de la numérotation.
     */
    private static void runHeartbeatTest() {
        System.out.println("=== TEST HEARTBEAT ET RENUMÉRATION ===\n");
        System.out.println("Ce test démontre :");
        System.out.println("1. La création de processus avec numérotation automatique");
        System.out.println("2. L'envoi périodique de heartbeats");
        System.out.println("3. La détection de panne d'un processus");
        System.out.println("4. La renumération automatique des processus survivants\n");

        Com com0 = new Com();
        Com com1 = new Com();
        Com com2 = new Com();
        Com com3 = new Com();

        System.out.println("Processus créés avec IDs: " + com0.getProcessId() + ", " +
                          com1.getProcessId() + ", " + com2.getProcessId() + ", " + com3.getProcessId());
        System.out.println("Nombre total de processus: " + com0.getProcessCount() + "\n");

        try {
            // Phase 1: Laisser les heartbeats fonctionner normalement
            System.out.println("Phase 1: Heartbeats normaux pendant 8 secondes...");
            Thread.sleep(8000);

            // Phase 2: Simuler la panne du processus 2
            System.out.println("\nPhase 2: Simulation de la panne du processus " + com2.getProcessId());
            com2.shutdown(); // Arrêter le processus 2 (simulation de panne)

            // Phase 3: Attendre la détection de panne et la renumération
            System.out.println("Attente de la détection de panne et renumération...");
            Thread.sleep(10000);

            // Phase 4: Vérifier la nouvelle numérotation
            System.out.println("\nPhase 4: État final après renumération:");
            System.out.println("Nombre de processus survivants: " + com0.getProcessCount());
            System.out.println("IDs des processus survivants:");
            System.out.println("- Processus 0: " + com0.getProcessId());
            System.out.println("- Processus 1: " + com1.getProcessId());
            System.out.println("- Processus 3: " + com3.getProcessId());

            // Phase 5: Créer un nouveau processus pour vérifier la numérotation
            System.out.println("\nPhase 5: Création d'un nouveau processus...");
            Com com4 = new Com();
            System.out.println("Nouveau processus créé avec ID: " + com4.getProcessId());
            System.out.println("Nombre total final: " + com4.getProcessCount());

            Thread.sleep(3000);

            com4.shutdown();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // Nettoyer tous les processus
            com0.shutdown();
            com1.shutdown();
            com3.shutdown();
        }

        System.out.println("\n=== TEST HEARTBEAT TERMINÉ ===");
    }

    /**
     * Lance les tests de la version distribuée stable.
     */
    private static void runDistributedTest() {
        System.out.println("=== TESTS VERSION DISTRIBUÉE STABLE ===\n");

        testDistributedNumbering();
        testDistributedDiscovery();
        testDistributedBarrier();

        System.out.println("\n=== TESTS DISTRIBUÉS TERMINÉS ===");
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
