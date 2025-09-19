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
     *             - "process" : Lance l'exemple avec des processus originaux
     *             - "dice" : Lance le jeu de dés
     *             - "demo" : Lance une démonstration complète (par défaut)
     */
    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "demo";

        System.out.println("=== MIDDLEWARE DE COMMUNICATION DISTRIBUÉE ===");
        System.out.println("Mode: " + mode + "\n");

        switch (mode.toLowerCase()) {
            case "process":
                runProcessExample();
                break;
            case "sync":
                runSyncCommunicationTest();
                break;
            case "demo":
            default:
                runCompleteDemo();
                break;
        }
    }

    /**
     * Lance l'exemple original avec les processus P0, P1, P2.
     */
    private static void runProcessExample() {
        System.out.println("Lancement de l'exemple de processus original...\n");

        Process p0 = new Process("P0");
        Process p1 = new Process("P1");
        Process p2 = new Process("P2");

        // Démarrer le TokenManager
        TokenManager.getInstance().start();

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
        } finally {
            TokenManager.getInstance().stop();
        }

        System.out.println("Exemple de processus terminé.");
    }

    /**
     * Lance une démonstration de la communication asynchrone.
     */
    private static void runCompleteDemo() {
        System.out.println("=== DÉMONSTRATION DU MIDDLEWARE ===\n");

        // Test de communication asynchrone
        System.out.println("1. Test de communication asynchrone:");
        testAsyncCommunication();

        // Pause entre les tests
        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        // Test de section critique distribuée
        System.out.println("\n2. Test de section critique distribuée avec jeton circulaire:");
        testCriticalSection();

        // Pause entre les tests
        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        // Test de synchronisation
        System.out.println("\n3. Test de barrière de synchronisation:");
        testSynchronization();

        System.out.println("\n=== DÉMONSTRATION TERMINÉE ===");
    }

    /**
     * Test de communication asynchrone.
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

}
