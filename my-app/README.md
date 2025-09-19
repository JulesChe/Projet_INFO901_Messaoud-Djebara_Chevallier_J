# Middleware de Communication Distribuée

## Description

Ce projet implémente un middleware **totalement distribué** pour la communication en Java, **sans aucune variable de classe** conformément aux exigences du sujet. Il fournit les fonctionnalités suivantes :

- **Horloge de Lamport** : Synchronisation temporelle logique entre processus
- **Communication asynchrone** : Envoi de messages sans blocage
- **Communication synchrone** : Envoi et réception bloquants avec accusés
- **Section critique distribuée** : Jeton circulant sur anneau virtuel (sans gestionnaire central)
- **Synchronisation de barrière** : Algorithme totalement distribué
- **Numérotation automatique distribuée** : Algorithme par nombres aléatoires et consensus
- **Heartbeat** : Détection de pannes et renumérotation automatique
- **Boîte aux lettres thread-safe** : Stockage des messages asynchrones

## Architecture

### Classes principales (Version Distribuée)

- **`ComDistributed`** : Communicateur totalement distribué (sans variables de classe)
- **`ProcessDistributed`** : Processus utilisant le middleware distribué
- **`AppDistributed`** : Application de démonstration complète
- **`Message`** : Classe abstraite pour tous les messages
- **`Mailbox`** : Boîte aux lettres thread-safe

### Classes de l'ancienne version (conservées pour compatibilité)

- **`Com`** : Ancien communicateur (avec variables de classe)
- **`Process`** : Ancien processus
- **`App`** : Ancienne application de démonstration

### Hiérarchie des messages

- `Message` (abstraite)
  - `UserMessage` : Messages utilisateur (affectent l'horloge de Lamport)
  - `TokenMessage` : Messages de jeton (système, n'affectent pas l'horloge)
  - `SyncMessage` : Messages de synchronisation pour communication synchrone
  - `HeartbeatMessage` : Messages de heartbeat et détection de pannes (système)
  - `NumberingMessage` : Messages pour la numérotation distribuée (système)
  - `DiscoveryMessage` : Messages pour la découverte de processus (système)
  - `BarrierMessage` : Messages pour la barrière de synchronisation

## Utilisation

### Compilation

```bash
cd my-app
mvn compile
```

### Exécution

#### Version Distribuée (RECOMMANDÉE - Sans variables de classe)

```bash
# Démonstration complète du middleware distribué
mvn exec:java -Dexec.mainClass="com.mycompany.app.AppDistributed"

# Test de processus distribués
mvn exec:java -Dexec.mainClass="com.mycompany.app.AppDistributed" -Dexec.args="process"

# Test de communication synchrone
mvn exec:java -Dexec.mainClass="com.mycompany.app.AppDistributed" -Dexec.args="sync"

# Test de barrière de synchronisation
mvn exec:java -Dexec.mainClass="com.mycompany.app.AppDistributed" -Dexec.args="barrier"

# Test de section critique avec jeton
mvn exec:java -Dexec.mainClass="com.mycompany.app.AppDistributed" -Dexec.args="critical"

# Test de heartbeat et détection de pannes
mvn exec:java -Dexec.mainClass="com.mycompany.app.AppDistributed" -Dexec.args="heartbeat"
```

#### Version Ancienne (avec variables de classe)

```bash
# Ancienne démonstration
mvn exec:java -Dexec.mainClass="com.mycompany.app.App"

# Test de communication synchrone
mvn exec:java -Dexec.mainClass="com.mycompany.app.SyncCommunicationTest"
```

## API du Middleware

### Création d'un communicateur

#### Version Distribuée (RECOMMANDÉE)

```java
ComDistributed com = new ComDistributed();
int processId = com.getProcessId(); // ID obtenu par algorithme distribué
```

#### Version Ancienne

```java
Com com = new Com();
int processId = com.getProcessId(); // ID via variable de classe
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
// Envoi synchrone (bloque jusqu'à accusé de réception)
com.sendToSync("Hello", destinationId);

// Réception synchrone (bloque jusqu'à recevoir de senderId)
SyncMessage msg = com.recevFromSync(senderId);

// Diffusion synchrone (bloque jusqu'à ce que tous reçoivent)
// Si je suis l'expéditeur (processId == myId)
com.broadcastSync("Hello everyone", myId);

// Si je veux recevoir un broadcast de processId 5
com.broadcastSync(null, 5);
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

### Version Distribuée (ComDistributed)

- **AUCUNE variable de classe** : Conformité totale au sujet
- **Découverte distribuée** : Les processus se découvrent par échange de messages
- **Anneau virtuel** : Chaque processus maintient sa vue locale de l'anneau
- **Jeton autonome** : Circulation sans gestionnaire central
- **Barrière distribuée** : Algorithme par messages ARRIVAL/RELEASE
- **Numérotation distribuée** : Basée sur nombres aléatoires et consensus

### Caractéristiques communes

- **Thread-safe** : Utilisation de structures concurrentes
- **Détection de pannes** : Système de heartbeat intégré
- **Évitement des interblocages** : Gestion appropriée des verrous
- **Messages système séparés** : N'affectent pas l'horloge de Lamport
- **Renumérotation automatique** : Après détection de panne

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