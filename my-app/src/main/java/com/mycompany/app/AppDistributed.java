package com.mycompany.app;

/**
 * Application principale utilisant le middleware totalement distribué.
 * Version sans variables de classe, conforme aux concepts du cours.
 *
 * Cette classe démontre toutes les fonctionnalités du middleware distribué :
 * - Communication asynchrone et synchrone
 * - Section critique avec jeton circulant
 * - Barrière de synchronisation distribuée
 * - Heartbeat et détection de pannes
 * - Numérotation automatique distribuée
 *
 * @author Middleware Team
 */
public class AppDistributed {

    /**
     * Point d'entrée de l'application distribuée.
     *
     * @param args Arguments de ligne de commande :
     *             - "process" : Lance l'exemple avec des processus distribués
     *             - "sync" : Lance les tests de communication synchrone
     *             - "heartbeat" : Lance le test de heartbeat et renumération
     *             - "barrier" : Lance le test de barrière de synchronisation
     *             - "critical" : Lance le test de section critique
     *             - "demo" : Lance une démonstration complète (par défaut)
     */
    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "demo";

        System.out.println("=== MIDDLEWARE DISTRIBUÉ (SANS VARIABLES DE CLASSE) ===");
        System.out.println("Mode: " + mode + "\n");

        switch (mode.toLowerCase()) {
            case "process":
                runProcessExample();
                break;
            case "sync":
                runSyncCommunicationTest();
                break;
            case "heartbeat":
                runHeartbeatTest();
                break;
            case "barrier":
                runBarrierTest();
                break;
            case "critical":
                runCriticalSectionTest();
                break;
            case "demo":
            default:
                runCompleteDemo();
                break;
        }
    }

    /**
     * Lance l'exemple avec des processus distribués P0, P1, P2.
     */
    private static void runProcessExample() {
        System.out.println("Lancement de l'exemple de processus distribués...\n");

        ProcessDistributed p0 = new ProcessDistributed("P0");
        ProcessDistributed p1 = new ProcessDistributed("P1");
        ProcessDistributed p2 = new ProcessDistributed("P2");

        try {
            Thread.sleep(10000);

            p0.stop();
            p1.stop();
            p2.stop();

            p0.waitStopped();
            p1.waitStopped();
            p2.waitStopped();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Exemple de processus terminé.");
    }

    /**
     * Test de la barrière de synchronisation distribuée.
     * Basé sur LaBarriereDeSynchro.pdf
     */
    private static void runBarrierTest() {
        System.out.println("=== TEST BARRIÈRE DE SYNCHRONISATION DISTRIBUÉE ===\n");
        System.out.println("Basé sur les concepts de LaBarriereDeSynchro.pdf");
        System.out.println("Les processus arrivent à des moments différents");
        System.out.println("et attendent tous avant de repartir ensemble.\n");

        ComDistributed com1 = new ComDistributed();
        ComDistributed com2 = new ComDistributed();
        ComDistributed com3 = new ComDistributed();

        Thread t1 = new Thread(() -> {
            try {
                System.out.println("[" + System.currentTimeMillis() + "] P" + com1.getProcessId() +
                                 " travaille...");
                Thread.sleep(100);

                System.out.println("[" + System.currentTimeMillis() + "] P" + com1.getProcessId() +
                                 " arrive à la barrière");
                com1.synchronize();

                System.out.println("[" + System.currentTimeMillis() + "] P" + com1.getProcessId() +
                                 " repart après la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Barrier-P1");

        Thread t2 = new Thread(() -> {
            try {
                System.out.println("[" + System.currentTimeMillis() + "] P" + com2.getProcessId() +
                                 " travaille...");
                Thread.sleep(500);

                System.out.println("[" + System.currentTimeMillis() + "] P" + com2.getProcessId() +
                                 " arrive à la barrière");
                com2.synchronize();

                System.out.println("[" + System.currentTimeMillis() + "] P" + com2.getProcessId() +
                                 " repart après la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Barrier-P2");

        Thread t3 = new Thread(() -> {
            try {
                System.out.println("[" + System.currentTimeMillis() + "] P" + com3.getProcessId() +
                                 " travaille...");
                Thread.sleep(1000);

                System.out.println("[" + System.currentTimeMillis() + "] P" + com3.getProcessId() +
                                 " arrive à la barrière");
                com3.synchronize();

                System.out.println("[" + System.currentTimeMillis() + "] P" + com3.getProcessId() +
                                 " repart après la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Barrier-P3");

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

        System.out.println("\n=== TEST BARRIÈRE TERMINÉ ===");
    }

    /**
     * Test de section critique avec jeton circulant.
     * Basé sur ExclusionMutuelle.pdf
     */
    private static void runCriticalSectionTest() {
        System.out.println("=== TEST SECTION CRITIQUE DISTRIBUÉE ===\n");
        System.out.println("Basé sur ExclusionMutuelle.pdf - Jeton sur anneau");
        System.out.println("Un seul processus peut être en section critique à la fois.\n");

        ComDistributed com1 = new ComDistributed();
        ComDistributed com2 = new ComDistributed();
        ComDistributed com3 = new ComDistributed();

        Thread t1 = new Thread(() -> {
            try {
                System.out.println("P" + com1.getProcessId() + " demande la section critique");
                com1.requestSC();
                System.out.println(">>> P" + com1.getProcessId() + " ENTRE en section critique");
                Thread.sleep(1000);
                System.out.println("<<< P" + com1.getProcessId() + " SORT de section critique");
                com1.releaseSC();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "CS-P1");

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(200);
                System.out.println("P" + com2.getProcessId() + " demande la section critique");
                com2.requestSC();
                System.out.println(">>> P" + com2.getProcessId() + " ENTRE en section critique");
                Thread.sleep(1000);
                System.out.println("<<< P" + com2.getProcessId() + " SORT de section critique");
                com2.releaseSC();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "CS-P2");

        Thread t3 = new Thread(() -> {
            try {
                Thread.sleep(400);
                System.out.println("P" + com3.getProcessId() + " demande la section critique");
                com3.requestSC();
                System.out.println(">>> P" + com3.getProcessId() + " ENTRE en section critique");
                Thread.sleep(1000);
                System.out.println("<<< P" + com3.getProcessId() + " SORT de section critique");
                com3.releaseSC();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "CS-P3");

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

        System.out.println("\n=== TEST SECTION CRITIQUE TERMINÉ ===");
    }

    /**
     * Test de communication synchrone.
     * Basé sur LaDiffusion.pdf
     */
    private static void runSyncCommunicationTest() {
        System.out.println("=== TEST COMMUNICATION SYNCHRONE ===\n");

        // Test 1: broadcastSync
        testBroadcastSync();

        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        // Test 2: sendToSync / recevFromSync
        testSendToSyncAndRecevFromSync();

        System.out.println("\n=== TEST COMMUNICATION SYNCHRONE TERMINÉ ===");
    }

    private static void testBroadcastSync() {
        System.out.println("1. TEST BROADCAST SYNCHRONE:");
        System.out.println("   Le processus émetteur est bloqué jusqu'à réception par tous\n");

        ComDistributed com0 = new ComDistributed();
        ComDistributed com1 = new ComDistributed();
        ComDistributed com2 = new ComDistributed();

        Thread receiver1 = new Thread(() -> {
            try {
                Thread.sleep(500);
                System.out.println("P" + com1.getProcessId() + " se prépare à recevoir");
                com1.broadcastSync(null, com0.getProcessId());
                System.out.println("P" + com1.getProcessId() + " a reçu le broadcast");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread receiver2 = new Thread(() -> {
            try {
                Thread.sleep(800);
                System.out.println("P" + com2.getProcessId() + " se prépare à recevoir");
                com2.broadcastSync(null, com0.getProcessId());
                System.out.println("P" + com2.getProcessId() + " a reçu le broadcast");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread sender = new Thread(() -> {
            try {
                Thread.sleep(200);
                System.out.println("P" + com0.getProcessId() + " commence broadcastSync");
                long start = System.currentTimeMillis();

                com0.broadcastSync("Message synchrone", com0.getProcessId());

                long end = System.currentTimeMillis();
                System.out.println("P" + com0.getProcessId() + " broadcastSync terminé en " +
                                 (end - start) + "ms");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        receiver1.start();
        receiver2.start();
        sender.start();

        try {
            sender.join();
            receiver1.join();
            receiver2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        com0.shutdown();
        com1.shutdown();
        com2.shutdown();

        System.out.println("Test broadcastSync terminé.\n");
    }

    private static void testSendToSyncAndRecevFromSync() {
        System.out.println("2. TEST SEND/RECEIVE SYNCHRONE:");
        System.out.println("   Communication point à point bloquante\n");

        ComDistributed com0 = new ComDistributed();
        ComDistributed com1 = new ComDistributed();

        Thread receiver = new Thread(() -> {
            try {
                Thread.sleep(1000);
                System.out.println("P" + com1.getProcessId() + " commence recevFromSync");

                SyncMessage msg = com1.recevFromSync(com0.getProcessId());
                if (msg != null) {
                    System.out.println("P" + com1.getProcessId() + " a reçu: " + msg.getPayload());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread sender = new Thread(() -> {
            try {
                Thread.sleep(200);
                System.out.println("P" + com0.getProcessId() + " commence sendToSync");
                long start = System.currentTimeMillis();

                com0.sendToSync("Message direct", com1.getProcessId());

                long end = System.currentTimeMillis();
                System.out.println("P" + com0.getProcessId() + " sendToSync terminé en " +
                                 (end - start) + "ms");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        receiver.start();
        sender.start();

        try {
            sender.join();
            receiver.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        com0.shutdown();
        com1.shutdown();

        System.out.println("Test sendToSync/recevFromSync terminé.");
    }

    /**
     * Test du heartbeat et de la détection de pannes.
     */
    private static void runHeartbeatTest() {
        System.out.println("=== TEST HEARTBEAT ET DÉTECTION DE PANNES ===\n");
        System.out.println("Création de 4 processus avec heartbeat\n");

        ComDistributed com0 = new ComDistributed();
        ComDistributed com1 = new ComDistributed();
        ComDistributed com2 = new ComDistributed();
        ComDistributed com3 = new ComDistributed();

        System.out.println("Processus créés: P" + com0.getProcessId() + ", P" + com1.getProcessId() +
                         ", P" + com2.getProcessId() + ", P" + com3.getProcessId());
        System.out.println("Nombre total: " + com0.getProcessCount() + "\n");

        try {
            System.out.println("Phase 1: Heartbeats normaux pendant 5 secondes...");
            Thread.sleep(5000);

            System.out.println("\nPhase 2: Arrêt de P" + com2.getProcessId());
            com2.shutdown();

            System.out.println("Phase 3: Détection de panne (attente 8 secondes)...");
            Thread.sleep(8000);

            System.out.println("\nProcessus survivants: " + com0.getProcessCount());

            System.out.println("\nPhase 4: Création d'un nouveau processus");
            ComDistributed com4 = new ComDistributed();
            System.out.println("Nouveau processus P" + com4.getProcessId() + " créé");

            Thread.sleep(3000);

            com4.shutdown();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            com0.shutdown();
            com1.shutdown();
            com3.shutdown();
        }

        System.out.println("\n=== TEST HEARTBEAT TERMINÉ ===");
    }

    /**
     * Lance une démonstration complète du middleware.
     */
    private static void runCompleteDemo() {
        System.out.println("=== DÉMONSTRATION COMPLÈTE DU MIDDLEWARE DISTRIBUÉ ===\n");

        System.out.println("Ce middleware est totalement distribué :");
        System.out.println("- PAS de variables de classe (conformité au sujet)");
        System.out.println("- Découverte distribuée par messages");
        System.out.println("- Jeton circulant sur anneau virtuel");
        System.out.println("- Barrière totalement distribuée");
        System.out.println("- Basé sur les concepts purs du cours\n");

        System.out.println("1. Test de communication asynchrone:");
        testAsyncCommunication();

        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        System.out.println("\n2. Test de section critique (jeton sur anneau):");
        testCriticalSectionSimple();

        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        System.out.println("\n3. Test de barrière de synchronisation:");
        testBarrierSimple();

        System.out.println("\n=== DÉMONSTRATION TERMINÉE ===");
    }

    private static void testAsyncCommunication() {
        ComDistributed com1 = new ComDistributed();
        ComDistributed com2 = new ComDistributed();
        ComDistributed com3 = new ComDistributed();

        System.out.println("Processus créés: P" + com1.getProcessId() + ", P" +
                         com2.getProcessId() + ", P" + com3.getProcessId());

        com1.sendTo("Message point à point vers P" + com2.getProcessId(), com2.getProcessId());
        com1.broadcast("Broadcast de P" + com1.getProcessId());

        try {
            Thread.sleep(1000);

            System.out.println("Messages reçus par P" + com2.getProcessId() + ":");
            while (!com2.mailbox.isEmpty()) {
                Message msg = com2.mailbox.getMessageNonBlocking();
                if (msg != null) {
                    System.out.println("  - " + msg.getPayload());
                }
            }

            System.out.println("Messages reçus par P" + com3.getProcessId() + ":");
            while (!com3.mailbox.isEmpty()) {
                Message msg = com3.mailbox.getMessageNonBlocking();
                if (msg != null) {
                    System.out.println("  - " + msg.getPayload());
                }
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            com1.shutdown();
            com2.shutdown();
            com3.shutdown();
        }
    }

    private static void testCriticalSectionSimple() {
        ComDistributed com1 = new ComDistributed();
        ComDistributed com2 = new ComDistributed();

        Thread t1 = new Thread(() -> {
            try {
                System.out.println("P" + com1.getProcessId() + " demande SC");
                com1.requestSC();
                System.out.println("P" + com1.getProcessId() + " DANS SC");
                Thread.sleep(1000);
                System.out.println("P" + com1.getProcessId() + " libère SC");
                com1.releaseSC();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(200);
                System.out.println("P" + com2.getProcessId() + " demande SC");
                com2.requestSC();
                System.out.println("P" + com2.getProcessId() + " DANS SC");
                Thread.sleep(1000);
                System.out.println("P" + com2.getProcessId() + " libère SC");
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
        }
    }

    private static void testBarrierSimple() {
        ComDistributed com1 = new ComDistributed();
        ComDistributed com2 = new ComDistributed();
        ComDistributed com3 = new ComDistributed();

        System.out.println("3 processus vont se synchroniser à une barrière:");

        Thread[] threads = new Thread[3];
        ComDistributed[] coms = {com1, com2, com3};
        int[] delays = {100, 500, 1000};

        for (int i = 0; i < 3; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    System.out.println("P" + coms[index].getProcessId() + " travaille...");
                    Thread.sleep(delays[index]);

                    System.out.println("P" + coms[index].getProcessId() + " arrive à la barrière");
                    coms[index].synchronize();

                    System.out.println("P" + coms[index].getProcessId() + " repart !");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            threads[i].start();
        }

        try {
            for (Thread t : threads) {
                t.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            for (ComDistributed com : coms) {
                com.shutdown();
            }
        }

        System.out.println("Tous les processus ont passé la barrière ensemble !");
    }
}