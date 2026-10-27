# Robot Pompier – Commande par Bluetooth

Application **Android native en Kotlin**, Android 6.0 (API 23) et versions ultérieures, conçue pour un HC-05 ou HC-06 en **Bluetooth Classic SPP**. Elle ne fonctionne pas avec un module BLE seul.

## Télécharger l’APK sans Android Studio

1. Se connecter à son compte GitHub, puis ouvrir [les exécutions Android APK](https://github.com/chiboutredouane-glitch/robotpompier/actions/workflows/android.yml).
2. Ouvrir l’exécution la plus récente terminée avec une coche verte. Le workflow démarre automatiquement après chaque envoi sur `main` ; il est aussi disponible dans **Actions > Android APK > Run workflow > main > Run workflow**.
3. Dans la page de cette exécution, descendre à **Artifacts** et cliquer sur **RobotPompier-debug**. Le lien est aussi affiché dans le résumé de l’exécution.
4. Enregistrer `RobotPompier-debug.zip` sur l’ordinateur puis l’extraire.
5. Récupérer **`RobotPompier-debug.apk`** à l’intérieur, le transférer sur le téléphone et l’ouvrir pour l’installer. Aucun Android Studio, Gradle ou JDK n’est nécessaire sur l’ordinateur pour cette récupération.

L’artefact reste disponible **30 jours**. S’il a expiré, relancer **Run workflow**. Pour les sources, utiliser **Code > Download ZIP** sur [le dépôt](https://github.com/chiboutredouane-glitch/robotpompier), ou cloner le dépôt.

Le workflow `.github/workflows/android.yml` installe Java 21 et le SDK Android 35, vérifie les huit fichiers Gradle nécessaires, compile l’APK, exécute les tests et Android Lint, puis vérifie signature et alignement. L’artefact ne contient que l’APK et son empreinte SHA-256. Il ne contient aucune clé privée et aucun secret GitHub personnel n’est nécessaire.

Une nouvelle clé debug est générée sur le runner. Si Android refuse une mise à jour parce que la signature diffère d’une installation antérieure, désinstaller l’ancienne version avant d’installer le nouvel APK. Aucune clé de production n’est incluse dans le dépôt ou l’artefact.

## Fichiers Gradle inclus

- `gradlew` exécutable et `gradlew.bat`.
- `gradle/wrapper/gradle-wrapper.jar` et `gradle/wrapper/gradle-wrapper.properties`, avec l’empreinte SHA-256 de la distribution Gradle.
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` et `app/build.gradle.kts`.

`local.properties` est propre à chaque ordinateur et reste ignoré. GitHub Actions utilise le SDK configuré sur son runner.

## Interface

Le projet propose une interface paysage avec pavé directionnel à gauche, pompe à droite, voyant rouge/vert, sélection Bluetooth et bouton Fermer. Les six commandes sont inactives hors connexion. Le mode paysage suit les deux orientations du téléphone et les zones tactiles prennent en charge plusieurs doigts.

**La capture MIT App Inventor n’a pas été fournie dans la conversation.** La disposition et les couleurs proposées sont donc une interface de remplacement. Une reproduction fidèle nécessitera cette capture ; le fichier à adapter est `app/src/main/res/layout/activity_main.xml` et les styles sont dans `res/values/styles.xml` et `res/drawable/`.

## Ouvrir et compiler dans Android Studio

1. Extraire le ZIP puis ouvrir le dossier `RobotPompier` dans Android Studio.
2. Choisir le JDK intégré compatible Java 17 ou 21. Laisser Android Studio installer le SDK Android 35 et Build Tools 35.0.0.
3. Synchroniser Gradle. Le projet utilise Gradle 8.13, Android Gradle Plugin 8.9.2 et Kotlin 2.1.20.
4. Lancer **Build > Build App Bundle(s) / APK(s) > Build APK(s)** ou exécuter :

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Sous Windows : `gradlew.bat assembleDebug testDebugUnitTest lintDebug`.

L’APK de test signé est créé dans `app/build/outputs/apk/debug/app-debug.apk`. Pour une publication, utiliser **Generate Signed App Bundle / APK**, créer sa propre clé et conserver cette clé. L’APK debug fourni sert à l’installation et aux essais, pas à une publication Play Store. Le SDK, les caches et les chemins spécifiques à cet environnement ne sont pas inclus dans l’archive.

## Installation et utilisation

1. Installer l’APK sur un téléphone Android (autoriser temporairement l’installation depuis l’application qui ouvre le fichier), ou utiliser `adb install -r RobotPompier-debug.apk`.
2. Appairer le HC-05/HC-06 dans les paramètres Bluetooth du téléphone. Le code est souvent `1234` ou `0000`, selon le module.
3. Ouvrir l’application, toucher **Bluetooth** et autoriser **Appareils à proximité** sur Android 12+.
4. Sélectionner le module déjà appairé. Le voyant passe au vert après la connexion SPP et l’envoi initial des ordres d’arrêt.
5. Maintenir une direction pour déplacer le robot ; relâcher ou glisser le doigt hors du bouton pour envoyer son arrêt. Avec un autre doigt, toucher Activer/Désactiver la pompe. La pompe reste commandée indépendamment du déplacement.
6. Toucher **Déconnecter** ou **Fermer** pour envoyer les arrêts puis fermer le lien. Une mise en arrière-plan ou la perte du focus de la fenêtre ferme également la connexion par sécurité. Il faut ensuite se reconnecter manuellement.

Le libellé de la pompe décrit une **commande demandée**, car le protocole ne fournit aucun accusé de réception ni capteur d’état.

## Protocole exact

Le protocole confirmé dans l’application MIT App Inventor utilise **`S` majuscule pour arrêter le déplacement au relâchement de chacune des quatre directions**. La version 1.2 applique ce protocole. Le dernier caractère écrit sur la liaison Bluetooth est affiché avec son code hexadécimal : au relâchement, **`TX : S · 0x53`**. Cet affichage n’est pas un accusé de réception du robot. Voir [le diagnostic d’arrêt](docs/DIAGNOSTIC_ARRET.md).

| Commande | À l’appui | Au relâchement |
| --- | --- | --- |
| Avancer | `A` (65) | `S` (83) |
| Reculer | `R` (82) | `S` (83) |
| Droite | `D` (68) | `S` (83) |
| Gauche | `G` (71) | `S` (83) |
| Activer la pompe | `E` (69), au clic | aucun |
| Désactiver la pompe | `F` (70), au clic | aucun |

Chaque caractère est envoyé en ASCII, sans espace, sans `CR`, sans `LF`. Le clic de pompe est validé au relâchement, comme un bouton Android standard. Les commandes de déplacement sont émises dès le contact tactile. Le flux est sérialisé pour préserver l’ordre de plusieurs doigts. L’UUID SPP est `00001101-0000-1000-8000-00805F9B34FB`.

La séquence supplémentaire **`SF`** est envoyée une fois à la connexion et avant une déconnexion volontaire : arrêt global du déplacement puis arrêt de la pompe. Le firmware doit traiter ces caractères individuellement et de manière idempotente. Un relâchement de direction envoie seulement `S`, afin de laisser la pompe indépendante. Si plusieurs directions sont maintenues, le relâchement de l’une envoie cet arrêt global ; aucun mouvement n’est repris automatiquement par l’autre doigt.

## Autorisations et connexion

- Android 6 à 11 : permission Bluetooth classique déclarée au manifeste.
- Android 12+ : seule la permission d’exécution `BLUETOOTH_CONNECT` est requise. L’application liste les appareils appairés et ne lance aucune recherche, donc aucune permission de localisation ou `BLUETOOTH_SCAN` n’est demandée.
- Refus d’autorisation, Bluetooth absent/désactivé, liste vide, délai de connexion, erreur d’écriture et perte de socket sont gérés sans activer les commandes.
- Connexion RFCOMM sécurisée standard avec le service SPP. Pas de contournement par réflexion vers un canal arbitraire.
- Connexion sur un thread de travail, écritures en file sur un thread dédié, lecture bloquante séparée pour détecter EOF/erreurs même à l’arrêt. Délai maximal de connexion de 12 s, délai de fermeture de sécurité de 750 ms sur une écriture bloquée.
- Aucun démarrage automatique d’un mouvement à la reconnexion. Aucune reconnexion automatique.

## Arrêt de sécurité : deux côtés nécessaires

Consulter `docs/SECURITE.md`. Le téléphone peut couper les commandes locales et tenter les arrêts tant que le lien fonctionne. **Après une coupure radio, il ne peut pas garantir que le robot reçoive l’arrêt.** Le programme embarqué doit couper les moteurs et la pompe sur perte du signal de connexion du module ou sur un watchdog partagé par le protocole.

Aucun heartbeat n’est ajouté ici afin de respecter strictement les caractères demandés. Ajouter simplement un timeout « aucun caractère reçu depuis 500 ms » au firmware arrêterait un mouvement maintenu et la pompe, puisque ce protocole n’envoie pas de répétitions. Utiliser de préférence la broche d’état réellement disponible sur le module, ou définir ensemble un protocole de heartbeat et adapter les deux logiciels.

## Vérifications

Les tests unitaires vérifient les octets exacts, l’indépendance pompe/déplacement, deux directions tactiles, l’absence de doublons, le blocage hors connexion, la remise à zéro après coupure et la séquence d’arrêt. `docs/RECETTE.md` décrit les essais Android et les essais matériels nécessaires. Le rapport `VERIFICATION.md` de la livraison précise ce qui a réellement été exécuté.
