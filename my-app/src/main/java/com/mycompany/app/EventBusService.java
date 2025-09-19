package com.mycompany.app;

import com.google.common.eventbus.EventBus;

/**
 * Service singleton pour le bus d'événements centralisé.
 * Utilise Google Guava EventBus pour la communication entre processus.
 */
public class EventBusService {
    private static volatile EventBusService instance;
    private static final Object lock = new Object();

    private final EventBus eventBus;

    private EventBusService() {
        this.eventBus = new EventBus("DistributedSystemEventBus");
    }

    /**
     * Obtient l'instance unique du service de bus d'événements.
     */
    public static EventBusService getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new EventBusService();
                }
            }
        }
        return instance;
    }

    /**
     * Enregistre un abonné sur le bus d'événements.
     */
    public void registerSubscriber(Object subscriber) {
        eventBus.register(subscriber);
    }

    /**
     * Désenregistre un abonné du bus d'événements.
     */
    public void unRegisterSubscriber(Object subscriber) {
        eventBus.unregister(subscriber);
    }

    /**
     * Poste un événement sur le bus.
     */
    public void postEvent(Object event) {
        eventBus.post(event);
    }

    /**
     * Nettoie le service (pour les tests).
     */
    public static void reset() {
        synchronized (lock) {
            instance = null;
        }
    }
}