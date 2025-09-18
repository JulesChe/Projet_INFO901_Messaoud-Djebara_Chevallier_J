package com.mycompany.app;

/**
 * Classe Process représentant un processus dans le système distribué.
 * Chaque processus utilise un communicateur (Com) pour les interactions.
 *
 * @author Middleware Team
 */
public class Process implements Runnable {
    private Thread thread;
    private boolean alive;
    private boolean dead;
    private Com com;
    private String name;

    /**
     * Constructeur du processus.
     *
     * @param name Le nom du processus
     */
    public Process(String name) {
        this.com = new Com();
        this.name = name;

        this.thread = new Thread(this);
        this.thread.setName(name);
        this.alive = true;
        this.dead = false;
        this.thread.start();
    }

    /**
     * Méthode principale d'exécution du processus.
     */
    public void run() {
        int loop = 0;

        System.out.println(Thread.currentThread().getName() + " id: " + this.com.getProcessId());

        while (this.alive) {
            System.out.println(Thread.currentThread().getName() + " Loop: " + loop);
            try {
                Thread.sleep(500);

                if ("P0".equals(this.getName())) {
                    this.com.sendTo("j'appelle 2 et je te recontacte après", 1);

                    this.com.sendToSync("J'ai laissé un message à 2, je le rappellerai après, on se synchronise tous et on attaque la partie ?", 2);
                    Message msg = this.com.recevFromSync(2);

                    this.com.sendToSync("2 est OK pour jouer, on se synchronise et c'est parti!", 1);

                    this.com.synchronize();

                    this.com.requestSC();
                    if (this.com.mailbox.isEmpty()) {
                        System.out.println("Catched !");
                        this.com.broadcast("J'ai gagné !!!");
                    } else {
                        msg = this.com.mailbox.getMessageNonBlocking();
                        if (msg != null) {
                            System.out.println(msg.getSender() + " a eu le jeton en premier");
                        }
                    }
                    this.com.releaseSC();

                } else if ("P1".equals(this.getName())) {
                    if (!this.com.mailbox.isEmpty()) {
                        this.com.mailbox.getMessage();
                        Message msg = this.com.recevFromSync(0);

                        this.com.synchronize();

                        this.com.requestSC();
                        if (this.com.mailbox.isEmpty()) {
                            System.out.println("Catched !");
                            this.com.broadcast("J'ai gagné !!!");
                        } else {
                            msg = this.com.mailbox.getMessageNonBlocking();
                            if (msg != null) {
                                System.out.println(msg.getSender() + " a eu le jeton en premier");
                            }
                        }
                        this.com.releaseSC();
                    }
                } else if ("P2".equals(this.getName())) {
                    Message msg = this.com.recevFromSync(0);
                    this.com.sendToSync("OK", 0);

                    this.com.synchronize();

                    this.com.requestSC();
                    if (this.com.mailbox.isEmpty()) {
                        System.out.println("Catched !");
                        this.com.broadcast("J'ai gagné !!!");
                    } else {
                        msg = this.com.mailbox.getMessageNonBlocking();
                        if (msg != null) {
                            System.out.println(msg.getSender() + " a eu le jeton en premier");
                        }
                    }
                    this.com.releaseSC();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            loop++;
        }

        System.out.println(Thread.currentThread().getName() + " stopped");
        this.com.shutdown();
        this.dead = true;
    }

    /**
     * Attend que le processus se soit arrêté.
     */
    public void waitStopped() {
        while (!this.dead) {
            try {
                Thread.sleep(500);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Arrête le processus.
     */
    public void stop() {
        this.alive = false;
    }

    /**
     * Obtient le nom du processus.
     *
     * @return Le nom du processus
     */
    public String getName() {
        return this.name;
    }

    /**
     * Obtient le communicateur du processus.
     *
     * @return Le communicateur
     */
    public Com getCom() {
        return this.com;
    }
}