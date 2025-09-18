package com.mycompany.app;

/**
 * Test rapide des fonctionnalités du middleware.
 *
 * @author Middleware Team
 */
public class QuickTest {

    public static void main(String[] args) {
        System.out.println("=== TEST RAPIDE DU MIDDLEWARE ===\n");

        // Test 1: Création de communicateurs
        System.out.println("1. Test de création de communicateurs:");
        Com com1 = new Com();
        Com com2 = new Com();
        Com com3 = new Com();

        System.out.println("Communicateurs créés avec IDs: " +
                          com1.getProcessId() + ", " +
                          com2.getProcessId() + ", " +
                          com3.getProcessId());

        // Test 2: Communication asynchrone
        System.out.println("\n2. Test de communication asynchrone:");
        com1.sendTo("Hello from " + com1.getProcessId(), com2.getProcessId());
        com1.broadcast("Broadcast message from " + com1.getProcessId());

        try {
            Thread.sleep(500);

            // Vérification des messages reçus
            int messageCount2 = 0;
            int messageCount3 = 0;

            while (!com2.mailbox.isEmpty()) {
                Message msg = com2.mailbox.getMessageNonBlocking();
                if (msg != null && !msg.isSystemMessage()) {
                    System.out.println("Com2 reçu: " + msg.getPayload());
                    messageCount2++;
                }
            }

            while (!com3.mailbox.isEmpty()) {
                Message msg = com3.mailbox.getMessageNonBlocking();
                if (msg != null && !msg.isSystemMessage()) {
                    System.out.println("Com3 reçu: " + msg.getPayload());
                    messageCount3++;
                }
            }

            System.out.println("Messages utilisateur reçus - Com2: " + messageCount2 + ", Com3: " + messageCount3);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Test 3: Horloge de Lamport
        System.out.println("\n3. Test de l'horloge de Lamport:");
        com1.inc_clock();
        com2.inc_clock();
        com2.inc_clock();

        System.out.println("Horloges incrémentées manuellement.");

        // Test 4: Système de numérotation
        System.out.println("\n4. Test du système de numérotation:");
        Com com4 = new Com();
        Com com5 = new Com();

        System.out.println("Nouveaux communicateurs créés avec IDs: " +
                          com4.getProcessId() + ", " +
                          com5.getProcessId());

        System.out.println("Total de processus: " + Com.getProcessCount());

        // Nettoyage
        System.out.println("\n5. Nettoyage des ressources:");
        com1.shutdown();
        com2.shutdown();
        com3.shutdown();
        com4.shutdown();
        com5.shutdown();

        System.out.println("Processus arrêtés. Total restant: " + Com.getProcessCount());

        System.out.println("\n=== TEST TERMINÉ AVEC SUCCÈS ===");
    }
}