package com.mycompany.app;

/**
 * Application de test pour démontrer l'utilisation du middleware Com.
 *
 * Équivalent au jeu de dés demandé dans le sujet, cette application
 * démontre toutes les fonctionnalités du communicateur :
 * - Communication asynchrone (broadcast, sendTo)
 * - Section critique distribuée (requestSC, releaseSC)
 * - Synchronisation globale (synchronize)
 * - Communication synchrone (broadcastSync, sendToSync, recvFromSync)
 * - Numérotation automatique et heartbeat
 *
 * @author Middleware Team
 */
public class MiddlewareTestApp {

    public static void main(String[] args) {
        System.out.println("=== DÉMONSTRATION DU MIDDLEWARE COM ===\n");

        // Créer le registre partagé (remplace les variables de classe interdites)
        ProcessRegistry registry = new ProcessRegistry();

        // Test 1: Numérotation automatique et communication asynchrone
        testAsyncCommunication(registry);

        // Pause entre les tests
        try { Thread.sleep(3000); } catch (InterruptedException e) {}

        // Test 2: Section critique distribuée avec jeton circulaire
        testCriticalSection(registry);

        // Pause entre les tests
        try { Thread.sleep(3000); } catch (InterruptedException e) {}

        // Test 3: Synchronisation globale
        testSynchronization(registry);

        // Pause entre les tests
        try { Thread.sleep(3000); } catch (InterruptedException e) {}

        // Test 4: Communication synchrone
        testSyncCommunication(registry);

        // Pause entre les tests
        try { Thread.sleep(3000); } catch (InterruptedException e) {}

        // Test 5: Heartbeat et renumération
        testHeartbeatAndRenumbering(registry);

        System.out.println("\n=== DÉMONSTRATION TERMINÉE ===");
    }

    /**
     * Test 1: Numérotation automatique et communication asynchrone.
     */
    private static void testAsyncCommunication(ProcessRegistry registry) {
        System.out.println("TEST 1: Numérotation automatique et communication asynchrone");
        System.out.println("==========================================================");

        // Créer 3 communicateurs - ils reçoivent automatiquement les IDs 0, 1, 2
        Com com0 = new Com(registry);
        Com com1 = new Com(registry);
        Com com2 = new Com(registry);

        System.out.println("Communicateurs créés avec IDs: " + com0.getId() + ", " +
                          com1.getId() + ", " + com2.getId());

        // Test des communications asynchrones
        try {
            Thread.sleep(1000); // Laisser le temps au système de s'initialiser

            // Test sendTo
            System.out.println("\n--- Test sendTo ---");
            com0.sendTo("Message privé de P0 vers P1", 1);
            com0.sendTo("Autre message de P0 vers P2", 2);

            // Test broadcast
            System.out.println("\n--- Test broadcast ---");
            com0.broadcast("Broadcast de P0 à tous");

            // Test inc_clock() par les processus
            System.out.println("\n--- Test inc_clock() ---");
            com1.inc_clock();
            com2.inc_clock();

            Thread.sleep(2000); // Laisser le temps aux messages d'arriver

            // Vérifier les boîtes aux lettres
            System.out.println("\n--- Contenu des boîtes aux lettres ---");
            checkMailbox(com1, "P1");
            checkMailbox(com2, "P2");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Nettoyer
        com0.freeBus();
        com1.freeBus();
        com2.freeBus();

        System.out.println("Test 1 terminé.\n");
    }

    /**
     * Test 2: Section critique distribuée avec jeton circulaire.
     */
    private static void testCriticalSection(ProcessRegistry registry) {
        System.out.println("TEST 2: Section critique distribuée");
        System.out.println("===================================");

        Com com0 = new Com(registry);
        Com com1 = new Com(registry);
        Com com2 = new Com(registry);

        // Threads pour tester l'accès concurrent à la section critique
        Thread t0 = new Thread(() -> {
            try {
                Thread.sleep(500);
                System.out.println("[T0] P" + com0.getId() + " demande la section critique");
                com0.requestSC();
                System.out.println("[T0] P" + com0.getId() + " DANS la section critique - travail...");
                Thread.sleep(2000); // Simule du travail
                com0.releaseSC();
                System.out.println("[T0] P" + com0.getId() + " libère la section critique");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Thread-P0");

        Thread t1 = new Thread(() -> {
            try {
                Thread.sleep(1000);
                System.out.println("[T1] P" + com1.getId() + " demande la section critique");
                com1.requestSC();
                System.out.println("[T1] P" + com1.getId() + " DANS la section critique - travail...");
                Thread.sleep(1500); // Simule du travail
                com1.releaseSC();
                System.out.println("[T1] P" + com1.getId() + " libère la section critique");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Thread-P1");

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(1500);
                System.out.println("[T2] P" + com2.getId() + " demande la section critique");
                com2.requestSC();
                System.out.println("[T2] P" + com2.getId() + " DANS la section critique - travail...");
                Thread.sleep(1000); // Simule du travail
                com2.releaseSC();
                System.out.println("[T2] P" + com2.getId() + " libère la section critique");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Thread-P2");

        // Démarrer tous les threads
        t0.start();
        t1.start();
        t2.start();

        try {
            t0.join();
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Nettoyer
        com0.freeBus();
        com1.freeBus();
        com2.freeBus();

        System.out.println("Test 2 terminé.\n");
    }

    /**
     * Test 3: Synchronisation globale (barrière).
     */
    private static void testSynchronization(ProcessRegistry registry) {
        System.out.println("TEST 3: Synchronisation globale (barrière)");
        System.out.println("==========================================");

        Com com0 = new Com(registry);
        Com com1 = new Com(registry);
        Com com2 = new Com(registry);

        Thread t0 = new Thread(() -> {
            try {
                System.out.println("[" + System.currentTimeMillis() + "] P" + com0.getId() + " travaille avant la barrière...");
                Thread.sleep(1000);  // Simule du travail
                System.out.println("[" + System.currentTimeMillis() + "] P" + com0.getId() + " arrive à la barrière");
                com0.synchronize();  // BARRIÈRE
                System.out.println("[" + System.currentTimeMillis() + "] P" + com0.getId() + " continue après la barrière");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Sync-P0");

        Thread t1 = new Thread(() -> {
            try {
                System.out.println("[" + System.currentTimeMillis() + "] P" + com1.getId() + " travaille avant la barrière...");
                Thread.sleep(2000);  // Simule plus de travail
                System.out.println("[" + System.currentTimeMillis() + "] P" + com1.getId() + " arrive à la barrière");
                com1.synchronize();  // BARRIÈRE
                System.out.println("[" + System.currentTimeMillis() + "] P" + com1.getId() + " continue après la barrière");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Sync-P1");

        Thread t2 = new Thread(() -> {
            try {
                System.out.println("[" + System.currentTimeMillis() + "] P" + com2.getId() + " travaille avant la barrière...");
                Thread.sleep(3000);  // Simule encore plus de travail
                System.out.println("[" + System.currentTimeMillis() + "] P" + com2.getId() + " arrive à la barrière");
                com2.synchronize();  // BARRIÈRE
                System.out.println("[" + System.currentTimeMillis() + "] P" + com2.getId() + " continue après la barrière");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Sync-P2");

        // Démarrer tous les threads
        t0.start();
        t1.start();
        t2.start();

        try {
            t0.join();
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Tous les processus ont passé la barrière ensemble !");

        // Nettoyer
        com0.freeBus();
        com1.freeBus();
        com2.freeBus();

        System.out.println("Test 3 terminé.\n");
    }

    /**
     * Test 4: Communication synchrone.
     */
    private static void testSyncCommunication(ProcessRegistry registry) {
        System.out.println("TEST 4: Communication synchrone");
        System.out.println("===============================");

        Com com0 = new Com(registry);
        Com com1 = new Com(registry);

        // Test sendToSync / recvFromSync
        Thread sender = new Thread(() -> {
            try {
                Thread.sleep(1000);
                System.out.println("P" + com0.getId() + " envoie message synchrone à P" + com1.getId());
                com0.sendToSync("Message synchrone de P0", 1);
                System.out.println("P" + com0.getId() + " a terminé l'envoi synchrone");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "SyncSender");

        Thread receiver = new Thread(() -> {
            try {
                Thread.sleep(2000);
                System.out.println("P" + com1.getId() + " attend message synchrone de P" + com0.getId());
                com1.recvFromSync(null, 0);
                System.out.println("P" + com1.getId() + " a reçu le message synchrone");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "SyncReceiver");

        sender.start();
        receiver.start();

        try {
            sender.join();
            receiver.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Test broadcastSync
        System.out.println("\n--- Test broadcastSync ---");
        try {
            Thread.sleep(1000);
            System.out.println("P" + com0.getId() + " fait un broadcastSync");
            com0.broadcastSync("Broadcast synchrone de P0", 0);
            System.out.println("BroadcastSync terminé");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Nettoyer
        com0.freeBus();
        com1.freeBus();

        System.out.println("Test 4 terminé.\n");
    }

    /**
     * Test 5: Heartbeat et renumération automatique.
     */
    private static void testHeartbeatAndRenumbering(ProcessRegistry registry) {
        System.out.println("TEST 5: Heartbeat et renumération");
        System.out.println("=================================");

        Com com0 = new Com(registry);
        Com com1 = new Com(registry);
        Com com2 = new Com(registry);
        Com com3 = new Com(registry);

        System.out.println("Processus créés avec IDs: " + com0.getId() + ", " +
                          com1.getId() + ", " + com2.getId() + ", " + com3.getId());

        try {
            // Phase 1: Laisser les heartbeats fonctionner normalement
            System.out.println("Phase 1: Heartbeats normaux pendant 5 secondes...");
            Thread.sleep(5000);

            // Phase 2: Simuler la panne du processus 2
            System.out.println("\nPhase 2: Simulation de la panne du processus " + com2.getId());
            com2.freeBus(); // Arrêter le processus 2

            // Phase 3: Attendre la détection de panne et la renumération
            System.out.println("Attente de la détection de panne et renumération...");
            Thread.sleep(8000);

            // Phase 4: Créer un nouveau processus pour vérifier la numérotation
            System.out.println("\nPhase 4: Création d'un nouveau processus...");
            Com com4 = new Com(registry);
            System.out.println("Nouveau processus créé avec ID: " + com4.getId());

            Thread.sleep(2000);

            // Afficher l'état final
            registry.printStatus();

            com4.freeBus();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Nettoyer
        com0.freeBus();
        com1.freeBus();
        com3.freeBus();

        System.out.println("Test 5 terminé.\n");
    }

    /**
     * Vérifie et affiche le contenu d'une boîte aux lettres.
     */
    private static void checkMailbox(Com communicator, String processName) {
        System.out.println("Boîte aux lettres de " + processName + ":");
        int messageCount = 0;
        while (!communicator.mailbox.isEmpty()) {
            try {
                Message msg = communicator.mailbox.getMessageNonBlocking();
                if (msg != null) {
                    messageCount++;
                    System.out.println("  " + messageCount + ". " + msg.getPayload() +
                                     " (de P" + msg.getSender() + ", horloge=" + msg.getTimestamp() + ")");
                }
            } catch (Exception e) {
                break;
            }
        }
        if (messageCount == 0) {
            System.out.println("  (aucun message)");
        }
    }
}