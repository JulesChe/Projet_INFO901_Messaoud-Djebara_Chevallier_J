# Middleware de Communication Distribuée

## Description

Middleware distribué en Java avec horloge de Lamport, communication asynchrone/synchrone, section critique avec token circulant.

## Exécution

```bash
# Suite de tests complète (recommandé)
mvn exec:java -Dexec.mainClass="com.mycompany.app.App"

# Mode distribué - processus indépendant
mvn exec:java -Dexec.mainClass="com.mycompany.app.App" -Dexec.args="distributed <processId>"
```

## Structure du projet

**Classes principales :**
- **`App.java`** : Point d'entrée avec suite de tests (communication async/sync, section critique)
- **`Com.java`** : Communicateur principal avec horloge de Lamport, token circulant, toutes les fonctionnalités
- **`DistributedEventBus.java`** : Bus de communication réseau multicast UDP
- **`Message.java`** : Classe abstraite pour tous les messages
- **`UserMessage.java`** : Messages utilisateur (affectent l'horloge de Lamport)
- **`SystemMessage.java`** : Messages système (token, heartbeat, barrière)
- **`Mailbox.java`** : Boîte aux lettres thread-safe pour messages asynchrones
- **`NetworkConfig.java`** : Configuration réseau des processus
- **`ProcessEndpoint.java`** : Point de terminaison réseau d'un processus


❌ **Barrière** : Ne fonctionne pas - chaque processus ne voit que lui-même dans sa liste `processesAtBarrier` ! Les messages `BARRIER_ARRIVE` ne sont pas reçus par les autres processus.