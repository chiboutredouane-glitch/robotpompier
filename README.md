# Télécommande universelle — iPhone 1.0.3

Portage natif Swift/UIKit de la télécommande Android 1.0.3. Paysage, quatre directions et quatre fonctions renommables, événements appui/relâchement ou interrupteur, profils mémorisés, commandes ASCII sans saut de ligne, TX et diagnostic local. iOS/iPadOS **16 minimum**.

## Bluetooth compatible

**BLE uniquement**, via CoreBluetooth. Les HC-05 et HC-06 usuels utilisent Bluetooth Classic SPP et **ne fonctionnent pas avec cette application iPhone**. Leur fonctionnement avec l’application Android ne change pas cette restriction iOS. Un appareil Classic compatible MFi nécessiterait un protocole et un développement distincts ; aucun n’est supposé ici.

Le profil BLE est prérempli pour le **MLT-BT05** : service `FFE0`, caractéristique `FFE1`. Les UUID complets ou courts sont modifiables par profil. L’application découvre les services réels et vérifie que la caractéristique permet WRITE ou WRITE_WITHOUT_RESPONSE. Les modules vendus sous des noms proches peuvent utiliser d’autres UUID.

La recherche dure 12 secondes, sans filtre de service pour retrouver les modules qui n’annoncent pas FFE0. L’iPhone demande l’autorisation Bluetooth. Aucune permission de localisation, compte en ligne ni connexion Internet n’est nécessaire au pilotage. Aucun appareil n’est reconnecté automatiquement.

## Commandes par défaut

| Bouton | Mode | Appui / ON | Relâchement / OFF |
| --- | --- | --- | --- |
| Avancer | Maintenir | `A` | `S` |
| Reculer | Maintenir | `R` | `S` |
| Gauche | Maintenir | `G` | `S` |
| Droite | Maintenir | `D` | `S` |
| Phares | Interrupteur | `F` | `f` |
| Feux stop | Interrupteur | `T` | `t` |
| Klaxon | Interrupteur | `K` | `k` |
| Détresse | Interrupteur | `X` | `x` |

Les quatre fonctions affichent leur nom et leur état demandé, sans sous-titre automatique « Réservé 1…4 ». Leur nom est indépendant des lettres. Le fichier SB2 d’un autre projet n’est pas utilisé.

Dans **Réglages**, choisir le bouton, modifier son nom, son mode et les deux événements, puis **Enregistrer**. Une commande est un caractère ASCII visible (33–126) ou un champ vide pour aucun envoi. Espaces, accents, sauts de ligne et commandes de plusieurs caractères sont refusés. Les noms acceptent les accents (24 caractères par bouton, 40 par profil). Maximum 30 profils ; création, copie, sélection et suppression sont disponibles. Annuler abandonne le brouillon ; supprimer un profil enregistré demande confirmation.

## Sécurité et états

Le caractère de sécurité du profil (`S` par défaut) est envoyé une fois à la connexion avant activation des boutons. Il est normal de voir **TX : S** sans avoir touché une commande. Les directions envoient leur arrêt au relâchement, à la sortie du doigt ou à l’annulation du geste. Les contrôles peuvent être utilisés indépendamment avec deux doigts.

Le départ vers les réglages, le changement de profil, la fin de session, un appel ou le passage en arrière-plan désactivent les boutons, abandonnent les commandes en attente et tentent l’arrêt du profil suivi des seules désactivations effectivement demandées ON. Exemple Hum-Bot : **`Sf`** si les phares étaient activés. La commande en cours est conservée, puis la séquence d’arrêt est prioritaire ; fermeture forcée après deux secondes. Une courte tâche d’arrière-plan est demandée à iOS pour terminer cette tentative, sans mode de pilotage permanent en arrière-plan.

Une coupure radio ou un arrêt brutal de l’iPhone peut empêcher l’envoi. L’arrêt physique sur perte de liaison doit aussi être assuré dans le programme du robot. Aucun heartbeat ni caractère supplémentaire n’est ajouté.

**TX** décrit une écriture côté iPhone, sans garantir l’exécution physique. En WRITE_WITHOUT_RESPONSE, aucune confirmation ATT du module n’existe. Les écritures sont sérialisées ; ce mode respecte le contrôle de flux CoreBluetooth et une cadence de 60 ms. WRITE avec réponse est préféré lorsqu’il est proposé, avec délai de réponse limité. ON/OFF désigne une commande demandée, pas un capteur. La réception RX n’est pas implémentée.

L’état rouge ouvre le dernier diagnostic, conservé localement et copiable. Le bouton **Fin** termine la session ; iOS ne permet pas à l’application de se fermer elle-même comme avec le bouton Quitter d’Android.

## Compiler et installer

Voir [INSTALLATION.md](INSTALLATION.md). Le dépôt contient le projet Xcode complet et un paquet Swift indépendant pour tester les commandes.

```sh
swift test
xcodebuild -project TelecommandeUniverselle.xcodeproj -scheme TelecommandeUniverselle -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Dans Xcode, sélectionner sa Team et un identifiant d’application disponible pour signer une installation sur son propre iPhone. Aucune Team, clé, certificat, profil de provisioning ni mot de passe n’est fourni dans le dépôt.

Le workflow GitHub compile sur macOS, teste les commandes et les événements UIKit dans un simulateur, puis archive une application arm64 et emballe un **IPA NON SIGNÉ**. Ce fichier **ne s’installe pas directement** : il doit être signé localement ou publié via un compte Apple Developer autorisé. Une archive simulateur n’est pas un programme installable sur téléphone.

Les sources sont sur la branche **telecommande-universelle-ios** du dépôt robotpompier. `main` conserve Robot Pompier et `telecommande-universelle` conserve l’application Android. Le fichier de workflow utilise le chemin existant `android.yml` pour permettre son lancement manuel depuis le workflow déjà enregistré : choisir explicitement la branche iOS.

Les tests automatiques ne remplacent pas les essais radio avec un véritable iPhone et un module BLE. Voir [VERIFICATION.md](VERIFICATION.md).
