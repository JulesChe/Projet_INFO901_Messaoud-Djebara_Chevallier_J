package com.mycompany.app;

import java.util.Random;

/**
 * Exemple d'utilisation du middleware avec un jeu de dés distribué.
 * Chaque processus lance un dé et celui qui obtient le plus grand score gagne.
 *
 * @author Middleware Team
 */
public class DiceGame {

    /**
     * Processus de jeu de dés qui utilise le middleware pour la communication.
     */
    public static class DicePlayer implements Runnable {
        private final Com com;
        private final String playerName;
        private final Random random;
        private volatile boolean gameFinished = false;

        /**
         * Constructeur du joueur de dés.
         *
         * @param playerName Le nom du joueur
         */
        public DicePlayer(String playerName) {
            this.playerName = playerName;
            this.com = new Com();
            this.random = new Random();
        }

        @Override
        public void run() {
            try {
                System.out.println("Joueur " + playerName + " (ID: " + com.getProcessId() + ") rejoint la partie");

                Thread.sleep(1000);

                System.out.println("Synchronisation des joueurs...");
                com.synchronize();

                int diceRoll = random.nextInt(6) + 1;
                System.out.println("Joueur " + playerName + " lance le dé: " + diceRoll);

                com.broadcast("DICE_ROLL:" + diceRoll + ":" + playerName);

                Thread.sleep(500);

                com.requestSC();
                try {
                    System.out.println("Joueur " + playerName + " calcule les résultats...");

                    int maxScore = diceRoll;
                    String winner = playerName;

                    while (!com.mailbox.isEmpty()) {
                        Message msg = com.mailbox.getMessageNonBlocking();
                        if (msg != null && msg.getPayload().toString().startsWith("DICE_ROLL:")) {
                            String[] parts = msg.getPayload().toString().split(":");
                            int score = Integer.parseInt(parts[1]);
                            String playerName = parts[2];

                            if (score > maxScore) {
                                maxScore = score;
                                winner = playerName;
                            }
                        }
                    }

                    System.out.println("RÉSULTAT: " + winner + " gagne avec " + maxScore + " !");
                    com.broadcast("WINNER:" + winner + ":" + maxScore);

                } finally {
                    com.releaseSC();
                }

                Thread.sleep(2000);
                gameFinished = true;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                com.shutdown();
            }
        }

        /**
         * Vérifie si le jeu est terminé.
         *
         * @return true si le jeu est terminé
         */
        public boolean isGameFinished() {
            return gameFinished;
        }

        /**
         * Obtient le communicateur du joueur.
         *
         * @return Le communicateur
         */
        public Com getCom() {
            return com;
        }
    }

    /**
     * Lance une partie de dés avec plusieurs joueurs.
     *
     * @param args Arguments de ligne de commande (non utilisés)
     */
    public static void main(String[] args) {
        System.out.println("=== JEU DE DÉS DISTRIBUÉ ===");
        System.out.println("Démarrage d'une partie avec 3 joueurs...\n");

        DicePlayer player1 = new DicePlayer("Alice");
        DicePlayer player2 = new DicePlayer("Bob");
        DicePlayer player3 = new DicePlayer("Charlie");

        Thread thread1 = new Thread(player1);
        Thread thread2 = new Thread(player2);
        Thread thread3 = new Thread(player3);

        thread1.start();
        thread2.start();
        thread3.start();

        try {
            thread1.join();
            thread2.join();
            thread3.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("\n=== PARTIE TERMINÉE ===");
    }
}