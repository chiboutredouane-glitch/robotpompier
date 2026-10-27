# Arrêt au relâchement : commande S

Le protocole réel a été confirmé par l’utilisateur dans ses blocs MIT App Inventor : **la lettre S majuscule arrête le déplacement au relâchement de chacun des quatre boutons**. Les anciennes versions natives envoyaient des minuscules conformément à la spécification initiale ; elles ne correspondaient donc pas à ce protocole.

La version **1.2** envoie désormais `S` pour les quatre directions. Elle applique également `S` sur annulation tactile ou sortie du doigt. La pompe reste indépendante, commandée par `E` et `F`. Aucun changement du programme embarqué n’est nécessaire pour essayer ce protocole corrigé.

## Test avec les roues levées

Maintenir une seule direction, puis relâcher sans toucher un autre bouton :

| Bouton | Pendant l’appui | Après relâchement |
| --- | --- | --- |
| Avancer | `TX : A · 0x41` | `TX : S · 0x53` |
| Reculer | `TX : R · 0x52` | `TX : S · 0x53` |
| Droite | `TX : D · 0x44` | `TX : S · 0x53` |
| Gauche | `TX : G · 0x47` | `TX : S · 0x53` |

Activer ensuite la pompe pendant un appui sur Avancer, puis relâcher Avancer : le flux doit être `A`, `E`, `S` ; la pompe reste active jusqu’à la commande `F`. `S` est un arrêt global du déplacement : si deux directions sont maintenues, le relâchement de l’une arrête le mouvement sans reprendre automatiquement celui de l’autre.

La connexion et la déconnexion volontaire utilisent `SF` : arrêt global du déplacement puis arrêt de la pompe. Les caractères sont envoyés individuellement en ASCII, sans espace ni saut de ligne.

## Interpréter TX

TX est mis à jour seulement après le retour réussi de l’écriture et du `flush` de la socket Bluetooth. Il confirme une écriture côté téléphone, pas la réception par le microcontrôleur ni l’arrêt effectif des moteurs.

- Si TX reste sur la lettre de déplacement après avoir levé le doigt, relever le bouton, le modèle du téléphone et l’état du voyant.
- Si TX affiche `S · 0x53` mais que le robot continue, comparer le même appui/relâchement avec l’ancienne application et relever les octets réellement reçus, sans modifier d’abord le programme embarqué.
- Si le voyant devient rouge, la connexion a été interrompue ; voir aussi `SECURITE.md` pour l’interverrouillage sur perte de liaison.

## Tests côté Android

`CommandStateTest` vérifie les octets du protocole. `DirectionTouchTest` exécute les véritables listeners tactiles de `MainActivity` avec Robolectric : appui/relâchement des quatre directions, annulation, sortie du doigt, relâchement après remise à zéro de l’état local, second doigt et pompe pendant un appui. Ces tests n’utilisent aucune liaison radio et ne remplacent pas l’essai sur le robot.
