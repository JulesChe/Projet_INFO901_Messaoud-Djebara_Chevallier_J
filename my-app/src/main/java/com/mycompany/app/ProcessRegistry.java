package com.mycompany.app;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registre des processus pour la numérotation automatique et consécutive.
 *
 * Cette classe remplace les variables de classe interdites par le sujet.
 * Elle gère :
 * - L'attribution d'IDs uniques et consécutifs (commençant à 0)
 * - Le suivi des processus actifs
 * - La renumération automatique en cas de panne
 *
 * @author Middleware Team
 */
public class ProcessRegistry {
    private final Map<Integer, Com> activeProcesses = new ConcurrentHashMap<>();
    private final AtomicInteger nextAvailableId = new AtomicInteger(0);
    private final Object registryLock = new Object();

    /**
     * Enregistre un nouveau processus et lui attribue un ID unique et consécutif.
     *
     * @param communicator Le communicateur du processus à enregistrer
     * @return L'ID unique attribué au processus (commençant à 0)
     */
    public int registerNewProcess(Com communicator) {
        synchronized (registryLock) {
            // Trouver le prochain ID disponible consécutif
            int assignedId = findNextConsecutiveId();
            activeProcesses.put(assignedId, communicator);

            System.out.println("Nouveau processus enregistré avec ID: " + assignedId);
            System.out.println("Processus actifs: " + activeProcesses.keySet());

            return assignedId;
        }
    }

    /**
     * Supprime un processus du registre.
     *
     * @param processId L'ID du processus à supprimer
     */
    public void removeProcess(int processId) {
        synchronized (registryLock) {
            Com removed = activeProcesses.remove(processId);
            if (removed != null) {
                System.out.println("Processus " + processId + " supprimé du registre");
                System.out.println("Processus actifs restants: " + activeProcesses.keySet());
            }
        }
    }

    /**
     * Obtient un processus par son ID.
     *
     * @param processId L'ID du processus recherché
     * @return Le communicateur du processus ou null si non trouvé
     */
    public Com getProcess(int processId) {
        return activeProcesses.get(processId);
    }

    /**
     * Obtient l'ensemble des IDs des processus actifs.
     *
     * @return Set des IDs des processus actifs
     */
    public Set<Integer> getActiveProcesses() {
        return Set.copyOf(activeProcesses.keySet());
    }

    /**
     * Obtient le nombre de processus actifs.
     *
     * @return Le nombre de processus actifs
     */
    public int getProcessCount() {
        return activeProcesses.size();
    }

    /**
     * Trouve le prochain ID consécutif disponible.
     * Assure une numérotation consécutive commençant à 0.
     *
     * @return Le prochain ID disponible
     */
    private int findNextConsecutiveId() {
        // Vérifier chaque ID à partir de 0 pour trouver le premier disponible
        int candidateId = 0;
        while (activeProcesses.containsKey(candidateId)) {
            candidateId++;
        }
        return candidateId;
    }

    /**
     * Effectue la renumération des processus survivants.
     * Les IDs sont réassignés de manière consécutive en commençant par 0.
     */
    public void performRenumbering() {
        synchronized (registryLock) {
            if (activeProcesses.isEmpty()) {
                return;
            }

            System.out.println("Début de la renumération des processus survivants");
            System.out.println("Processus avant renumération: " + activeProcesses.keySet());

            // Créer un nouveau mapping avec des IDs consécutifs
            Map<Integer, Com> newMapping = new ConcurrentHashMap<>();
            int newId = 0;

            // Réattribuer les IDs de manière consécutive
            for (Map.Entry<Integer, Com> entry : activeProcesses.entrySet()) {
                int oldId = entry.getKey();
                Com communicator = entry.getValue();

                newMapping.put(newId, communicator);

                // Mettre à jour l'ID dans le communicateur via réflexion
                // (nécessaire car processId est final)
                try {
                    java.lang.reflect.Field field = Com.class.getDeclaredField("processId");
                    field.setAccessible(true);
                    field.setInt(communicator, newId);
                } catch (Exception e) {
                    System.err.println("Erreur lors de la mise à jour de l'ID: " + e.getMessage());
                }

                System.out.println("Processus " + oldId + " renumé en " + newId);
                newId++;
            }

            // Remplacer l'ancien mapping
            activeProcesses.clear();
            activeProcesses.putAll(newMapping);

            System.out.println("Renumération terminée. Nouveaux IDs: " + activeProcesses.keySet());
        }
    }

    /**
     * Affiche l'état actuel du registre pour débogage.
     */
    public void printStatus() {
        synchronized (registryLock) {
            System.out.println("=== État du ProcessRegistry ===");
            System.out.println("Processus actifs: " + activeProcesses.size());
            for (Map.Entry<Integer, Com> entry : activeProcesses.entrySet()) {
                System.out.println("  - ID " + entry.getKey() + ": " + entry.getValue().getClass().getSimpleName());
            }
            System.out.println("Prochain ID disponible: " + findNextConsecutiveId());
            System.out.println("==============================");
        }
    }
}