# Middleware de Communication Distribuée

## Description

Ce projet implémente un middleware complet pour la communication distribuée en Java. Il fournit les fonctionnalités suivantes :

- **Horloge de Lamport** : Synchronisation temporelle logique entre processus
- **Communication asynchrone** : Envoi de messages sans blocage
- **Communication synchrone** : Envoi et réception bloquants
- **Section critique distribuée** : Utilisation d'un jeton circulant sur anneau
- **Synchronisation de barrière** : Synchronisation de tous les processus
- **Système de numérotation automatique** : Attribution d'IDs uniques
- **Heartbeat** : Détection automatique de processus inactifs
- **Boîte aux lettres thread-safe** : Stockage des messages asynchrones

## Architecture

### Classes principales

- **`Message`** : Classe abstraite pour tous les messages
- **`Com`** : Communicateur principal (middleware)
- **`Process`** : Processus utilisant le middleware
- **`Mailbox`** : Boîte aux lettres pour messages asynchrones

### Hiérarchie des messages

- `Message` (abstraite)
  - `UserMessage` : Messages utilisateur (affectent l'horloge de Lamport)
  - `TokenMessage` : Messages de jeton (système, n'affectent pas l'horloge)
  - `HeartbeatMessage` : Messages de heartbeat (système)
  - `SyncMessage` : Messages de synchronisation (système)

## Utilisation

### Compilation

```bash
cd my-app
mvn compile
```

### Exécution

```bash
# Démonstration complète (par défaut)
mvn exec:java -Dexec.mainClass="com.mycompany.app.App"

# Exemple de processus original
mvn exec:java -Dexec.mainClass="com.mycompany.app.App" -Dexec.args="process"

# Jeu de dés distribué
mvn exec:java -Dexec.mainClass="com.mycompany.app.App" -Dexec.args="dice"

# Jeu de dés directement
mvn exec:java -Dexec.mainClass="com.mycompany.app.DiceGame"
```

## API du Middleware

### Création d'un communicateur

```java
Com com = new Com();
int processId = com.getProcessId(); // ID automatiquement assigné
```

### Communication asynchrone

```java
// Envoi à un processus spécifique
com.sendTo("Hello", destinationId);

// Diffusion à tous les processus
com.broadcast("Hello everyone");

// Réception (non-bloquante)
Message msg = com.mailbox.getMessageNonBlocking();

// Réception (bloquante)
Message msg = com.mailbox.getMessage();
```

### Communication synchrone

```java
// Envoi synchrone
com.sendToSync("Hello", destinationId);

// Réception synchrone
Message msg = com.recevFromSync(senderId);

// Diffusion synchrone
com.broadcastSync("Hello everyone", myId);
```

### Section critique distribuée

```java
// Demander l'accès (bloquant)
com.requestSC();
try {
    // Code en section critique
    System.out.println("Dans la section critique");
} finally {
    // Libérer la section critique
    com.releaseSC();
}
```

### Synchronisation

```java
// Attendre que tous les processus atteignent ce point
com.synchronize();
```

### Gestion de l'horloge de Lamport

```java
// Incrémenter manuellement l'horloge
com.inc_clock();

// L'horloge est automatiquement mise à jour lors des envois/réceptions
```

### Nettoyage

```java
// Arrêter proprement le communicateur
com.shutdown();
```

## Exemples d'utilisation

### Exemple simple

```java
public class SimpleExample {
    public static void main(String[] args) {
        Com com1 = new Com();
        Com com2 = new Com();

        // Envoi asynchrone
        com1.sendTo("Hello from " + com1.getProcessId(), com2.getProcessId());

        // Réception
        try {
            Message msg = com2.mailbox.getMessage();
            System.out.println("Reçu: " + msg.getPayload());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Nettoyage
        com1.shutdown();
        com2.shutdown();
    }
}
```

### Jeu de dés distribué

Le projet inclut un exemple complet d'un jeu de dés distribué où :
1. Chaque joueur lance un dé
2. Les résultats sont partagés via broadcast
3. Le gagnant est déterminé en section critique
4. Tous les joueurs sont synchronisés

## Caractéristiques techniques

- **Thread-safe** : Utilisation de structures concurrentes
- **Détection de pannes** : Système de heartbeat intégré
- **Évitement des interblocages** : Gestion appropriée des verrous
- **Passage de jeton efficient** : Algorithme d'anneau circulaire
- **Messages système séparés** : N'affectent pas l'horloge de Lamport

## Tests

Le projet inclut plusieurs tests automatiques :
- Communication asynchrone
- Section critique distribuée
- Synchronisation de barrière
- Jeu de dés complet

## Limitations

- Simulation locale (pas de communication réseau réelle)
- Pas de persistance des messages
- Détection de pannes basée sur timeout local
- Pas de cryptage des messages

## Auteurs

Middleware Team - Projet INFO901