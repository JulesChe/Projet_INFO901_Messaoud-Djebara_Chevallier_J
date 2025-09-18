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
            case "dice":
                DiceGame.main(args);
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
        System.out.println("\n3. Test de synchronisation:");
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
     * Test de synchronisation.
     */
    private static void testSynchronization() {
        Com com1 = new Com();
        Com com2 = new Com();
        Com com3 = new Com();

        Thread t1 = new Thread(() -> {
            try {
                System.out.println("Processus " + com1.getProcessId() + " arrive à la barrière");
                com1.synchronize();
                System.out.println("Processus " + com1.getProcessId() + " passe la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(1000);
                System.out.println("Processus " + com2.getProcessId() + " arrive à la barrière");
                com2.synchronize();
                System.out.println("Processus " + com2.getProcessId() + " passe la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread t3 = new Thread(() -> {
            try {
                Thread.sleep(2000);
                System.out.println("Processus " + com3.getProcessId() + " arrive à la barrière");
                com3.synchronize();
                System.out.println("Processus " + com3.getProcessId() + " passe la barrière");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

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
    }
}
