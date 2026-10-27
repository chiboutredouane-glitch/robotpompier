# Télécommande universelle — Android Kotlin

Télécommande native pour **Bluetooth Classic SPP (HC-05/HC-06)** et **Bluetooth BLE série (profil MLT-BT05 FFE0/FFE1, modifiable)**. Huit boutons configurables, profils de robots mémorisés, mode paysage et affichage TX. Android 6.0/API 23 minimum, cible Android 15/API 35.

Application distincte de Robot Pompier : identifiant `fr.telecommande.universelle`, nom **Télécommande universelle**, version **1.0.2**. Elle peut être installée en même temps que Robot Pompier. Les sources de cette application sont sur la branche **telecommande-universelle** ; la branche **main** conserve Robot Pompier.

## Télécharger sans Android Studio

1. Se connecter à GitHub puis ouvrir [Actions](https://github.com/chiboutredouane-glitch/robotpompier/actions/workflows/android.yml?query=branch%3Atelecommande-universelle).
2. Ouvrir une exécution **Télécommande universelle · telecommande-universelle** réussie, puis la section **Artifacts**.
3. Télécharger **TelecommandeUniverselle-debug**, extraire le ZIP et récupérer **TelecommandeUniverselle-debug.apk**.
4. Transférer l’APK sur le téléphone, l’ouvrir et autoriser l’installation depuis l’application utilisée pour ouvrir le fichier.

Les artefacts restent disponibles 30 jours. Pour reconstruire : **Actions > Android APK > Run workflow**, choisir **telecommande-universelle** puis **Run workflow**. Le même fichier de workflow existe sur `main` pour Robot Pompier : sélectionner la bonne branche est nécessaire.

Pour télécharger les sources : sélectionner la branche **telecommande-universelle** puis **Code > Download ZIP**, ou utiliser [ce ZIP source](https://github.com/chiboutredouane-glitch/robotpompier/archive/refs/heads/telecommande-universelle.zip). Le ZIP de l’artefact APK contient l’application, pas le projet source.

L’artefact **TelecommandeUniverselle-verifications** contient les résultats des tests, Android Lint et des aperçus rendus par Robolectric. Les vues marquées « simulation » ne constituent pas des essais radio ou une connexion réelle au module.

## Premier lancement

Le profil **Hum-Bot** reprend les commandes confirmées dans les blocs MIT fournis. Le fichier `.sb2` appartenait à un autre projet et n’est pas utilisé.

| Bouton | Mode | Appui / activation | Relâchement / désactivation |
| --- | --- | --- | --- |
| Avancer | Appui / relâchement | `A` | `S` |
| Reculer | Appui / relâchement | `R` | `S` |
| Gauche | Appui / relâchement | `G` | `S` |
| Droite | Appui / relâchement | `D` | `S` |
| Réservé 1 : Phares | Interrupteur | `F` | `f` |
| Réservé 2 : Feux stop | Interrupteur | `T` | `t` |
| Réservé 3 : Klaxon | Interrupteur | `K` | `k` |
| Réservé 4 : Détresse | Interrupteur | `X` | `x` |

Un interrupteur agit au clic validé : premier clic = activation, deuxième clic = désactivation. Un appui annulé n’active rien. Un bouton en mode appui/relâchement envoie son premier caractère dès le contact, et son second lorsque le doigt est levé, sort du bouton ou que le geste est annulé. La pompe ou d’autres fonctions d’un ancien projet ne sont pas ajoutées automatiquement.

`S` est ici un arrêt global du déplacement. Si plusieurs directions sont maintenues, le relâchement de l’une envoie `S` et ne relance pas automatiquement l’autre. Les quatre boutons supplémentaires peuvent être utilisés pendant un déplacement. Leur nom n’impose aucune lettre au robot.

## Modifier les boutons et profils

1. Sélectionner un profil dans la liste en haut de l’écran, puis ouvrir **Réglages**. La connexion est fermée avec tentative d’arrêt avant l’ouverture des réglages.
2. Choisir le bouton à modifier dans la liste des huit boutons.
3. Modifier son **nom**, son **mode**, ses commandes **À l’appui / Au relâchement**, ou **Activation / Arrêt** pour un interrupteur.
4. Appuyer sur **Enregistrer**. Reconnecter le robot ensuite.

Chaque commande est **un seul caractère ASCII visible**. Majuscules, minuscules, chiffres et symboles sont conservés exactement. Un champ vide signifie **aucun envoi** ; les espaces, retours chariot, sauts de ligne, caractères accentués et commandes de plusieurs caractères sont refusés. Les noms, eux, peuvent contenir des accents. Renommer un bouton ne modifie pas ses commandes.

Les réglages restent sur le téléphone après fermeture. **Nouveau profil** propose la base Hum-Bot ou quatre réservés sans commande. **Copier ce profil** conserve ses réglages mais efface l’appareil mémorisé. **Supprimer** demande confirmation ; la suppression du dernier profil rétablit Hum-Bot. La sélection du profil est mémorisée. Sortir des réglages sans enregistrer abandonne les modifications.

Les noms de profils sont limités à 40 caractères et ceux des boutons à 24. Maximum : 30 profils. Aucun compte, serveur ni stockage cloud n’est nécessaire au fonctionnement de la télécommande.

## Bluetooth Classic

Choisir **Classic**, puis **Connecter**. Sélectionner un appareil déjà appairé. Le HC-05/HC-06 doit d’abord être appairé dans les paramètres Android. La connexion utilise RFCOMM avec l’UUID SPP `00001101-0000-1000-8000-00805f9b34fb`.

Le bouton devient **Annuler** pendant la connexion, puis **Déconnecter** une fois prêt. Le voyant vert n’apparaît qu’après la connexion et l’écriture de la commande d’arrêt initiale du profil.

## Bluetooth BLE

Choisir **BLE**, puis **Connecter**. La recherche dure 12 secondes et les appareils sont affichés au fur et à mesure. Aucune obligation d’appairage préalable. Le dernier appareil choisi peut être repris avec **Dernier appareil**.

Le profil MLT-BT05 est prérempli avec :

- Service : `0000ffe0-0000-1000-8000-00805f9b34fb`.
- Caractéristique d’écriture : `0000ffe1-0000-1000-8000-00805f9b34fb`.

Ces identifiants sont issus des informations fournies par l’utilisateur. L’application les **vérifie sur les services réellement découverts**. Ils ne sont pas présumés corrects pour tous les modules vendus sous un même nom. Les UUID restent modifiables dans les réglages ; `FFE0`, `0xFFE0`, une forme sur huit chiffres hexadécimaux ou un UUID complet sont acceptés.

Une caractéristique sans propriété **WRITE** ou **WRITE_NO_RESPONSE** est refusée et les boutons restent désactivés. WRITE avec réponse est préféré quand il est disponible. Les écritures sont sérialisées ; le mode sans réponse est cadencé et les callbacks tardifs ne peuvent pas consommer la commande suivante. Les notifications RX ne sont pas nécessaires et ne sont pas exploitées dans cette version.

## Autorisations Android

- Android 12+ : **Appareils à proximité**, avec `BLUETOOTH_CONNECT` et `BLUETOOTH_SCAN`, déclaré sans usage de localisation. SCAN sert à la recherche BLE et à annuler une recherche concurrente avant une connexion Classic.
- Android 6 à 11 : la recherche BLE exige l’autorisation de localisation et l’activation du service de localisation du téléphone. L’application ne lit ni ne collecte la position ; ce contrôle est imposé par Android pour la recherche BLE.
- Le Bluetooth désactivé provoque une demande système d’activation. L’application ne modifie pas silencieusement l’état du Bluetooth.

## Version 1.0.2

Les quatre boutons de fonctions affichent leur nom personnalisé et leur état demandé, sans la ligne automatique « Réservé 1…4 ». Les noms enregistrés, modes et lettres restent inchangés.

Avant une connexion Classic, l’application annule la découverte Bluetooth Android pour éviter une recherche concurrente à la liaison RFCOMM. Sur Android 12+, l’autorisation SCAN est aussi demandée en Classic : elle est nécessaire pour cette annulation et fait partie des Appareils à proximité. Aucune recherche Classic n’est lancée par l’application.

Les erreurs Classic précisent désormais l’opération concernée (connexion, arrêt initial, lecture ou envoi), la durée depuis la tentative et le détail IOException fourni par Android. La fin du flux SPP est distinguée d’une erreur. Aucun acquittement du robot ni caractère supplémentaire n’est exigé. La cause de la coupure observée sur le téléphone reste à confirmer ; l’erreur générale de la version précédente ne permettait pas de l’identifier.

## Identifier une déconnexion

Depuis la version **1.0.1**, un diagnostic local est accessible en touchant **l’état rouge** après la coupure. Il conserve le motif exact, le mode Classic/BLE, la version Android et jusqu’à vingt dernières valeurs TX affichées de la session (valeurs successives identiques regroupées). Le diagnostic reste disponible après fermeture et réouverture ; la mise en arrière-plan ne remplace plus le message initial de panne. Aucun diagnostic n’est envoyé sur Internet. Depuis la version 1.0.2, le dialogue défile si nécessaire et propose **Copier** pour relever le texte exact. Il est accessible uniquement à l’arrêt pour ne pas interrompre une liaison active.

Le diagnostic aide à distinguer une perte radio, une erreur/délai GATT et une interruption de sécurité par perte du focus. Cette version ne prétend pas corriger une panne radio non reproduite. Les commandes, les délais et les règles de sécurité sont conservés. Après une coupure, relever le texte du diagnostic et le modèle du module pour cibler la correction.

## TX, états et arrêt de sécurité

**TX** affiche le dernier caractère écrit côté téléphone avec son code hexadécimal, par exemple `TX : S · 0x53`. Il ne prouve ni la réception UART par le microcontrôleur ni l’exécution d’une action physique. En BLE sans réponse, il correspond à une écriture acceptée par la pile Android, sans acquittement ATT.

Les mentions **ON/OFF demandé** représentent les commandes de l’utilisateur, pas des capteurs. L’état initial des fonctions est **—** : aucune position physique n’est présumée et aucune activation passée n’est reprise automatiquement.

Chaque profil définit un **arrêt de sécurité** obligatoire, `S` par défaut. Il est envoyé à la connexion. Lors d’une déconnexion volontaire, fermeture, ouverture des réglages, changement de profil/mode, perte du focus, verrouillage ou mise en arrière-plan, l’application désactive les commandes puis tente :

1. L’arrêt de sécurité configuré.
2. Les commandes de relâchement/désactivation des boutons effectivement maintenus ou demandés ON, sans doublons.

Exemple Hum-Bot : si les phares ont été activés, la fermeture tente **`Sf`**. Elle n’envoie pas `F`, qui activerait les phares. Aucune séquence d’un autre robot n’est imposée. La fermeture de la liaison est forcée après un délai de 2 secondes si les écritures ne se terminent pas. Les mouvements en attente sont abandonnés avant la séquence d’arrêt.

Une coupure radio ou l’arrêt brutal du téléphone peut empêcher toute livraison de l’arrêt. Le robot doit assurer son interverrouillage physique sur perte de connexion, indépendamment du téléphone. Aucun caractère de heartbeat non prévu par le protocole n’est ajouté. La détection de perte de liaison dépend des délais de la pile Bluetooth ; elle n’est pas instantanée.

## Compiler et vérifier

Ouvrir ce dossier dans Android Studio, installer le SDK Android 35 et Build Tools 35.0.0, puis utiliser Java 21 et :

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Sous Windows, utiliser `gradlew.bat`. Le wrapper Gradle complet est inclus, avec validation SHA-256 de la distribution Gradle 8.13. Android Gradle Plugin 8.9.2, Kotlin 2.1.20, bytecode Java 17. L’APK est créé dans `app/build/outputs/apk/debug/app-debug.apk`.

Les tests couvrent les octets ASCII, les événements tactiles, les interrupteurs, les profils, les réglages, l’arrêt, les UUID, la découverte GATT et les écritures avec et sans réponse. Ils utilisent Robolectric et un framework Android simulé : ils ne remplacent pas les essais sur téléphone et modules réels. Voir `docs/RECETTE.md` et `VERIFICATION.md`.

L’APK debug est signé automatiquement pour les essais. Aucune clé privée n’est fournie, aucun secret GitHub personnel n’est nécessaire. Une reconstruction peut avoir une autre clé debug : si Android refuse une mise à jour pour incompatibilité de signature, désinstaller cette application avant d’installer le nouvel APK. Pour une distribution durable, utiliser une clé de production appartenant au propriétaire du projet.
