package com.mycompany.app;

/**
 * Classe de test pour valider les fonctionnalités de communication synchrone.
 * Démontre l'utilisation de broadcastSync, sendToSync et recevFromSync.
 *
 * @author Middleware Team
 */
public class SyncCommunicationTest {

    public static void main(String[] args) {
        System.out.println("=== TEST DE COMMUNICATION SYNCHRONE ===\n");

        // Test 1: broadcastSync
        testBroadcastSync();

        // Pause entre les tests
        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        // Test 2: sendToSync / recevFromSync
        testSendToSyncAndRecevFromSync();

        System.out.println("\n=== TESTS TERMINÉS ===");
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