# Le robot continue après le relâchement

La version 1.1 renforce le relâchement : la fin d’un appui tactile actif demande explicitement la minuscule d’arrêt, même si la liste locale des directions maintenues a été effacée. Elle affiche aussi **TX** dans la ligne de connexion. Ce champ change seulement après le retour réussi de l’écriture et du `flush` de la socket Bluetooth. Il confirme une écriture côté téléphone, **pas** la réception par le microcontrôleur ni l’arrêt des moteurs.

L’ancienne application MIT App Inventor ayant été signalée comme fonctionnelle avec le même robot, conserver le programme embarqué pendant la comparaison et effectuer exactement le même appui/relâchement dans les deux applications. Aucun changement du firmware n’est requis pour essayer cette version.

Faire ce test avec les roues levées. Maintenir une seule direction, puis relâcher, sans toucher un autre bouton :

| Bouton | Pendant l’appui | Après relâchement |
| --- | --- | --- |
| Avancer | `TX : A · 0x41` | `TX : a · 0x61` |
| Reculer | `TX : R · 0x52` | `TX : r · 0x72` |
| Droite | `TX : D · 0x44` | `TX : d · 0x64` |
| Gauche | `TX : G · 0x47` | `TX : g · 0x67` |

- Si TX reste en majuscule après avoir levé le doigt, relever le bouton, le modèle du téléphone et ce qu’affiche le voyant. Cela indique que l’écriture de la minuscule n’a pas été constatée côté téléphone.
- Si TX devient la bonne minuscule et que le robot continue, vérifier les octets reçus et leur traitement dans le firmware. Une écriture réussie côté téléphone ne garantit pas leur réception et leur exécution côté robot.
- Si le voyant devient rouge, la connexion a été interrompue ; vérifier aussi l’interverrouillage embarqué décrit dans `SECURITE.md`.

## Vérifications du programme embarqué

Le protocole est sensible à la casse. Une conversion systématique en majuscules (`toupper`, `toUpperCase`, etc.) transformerait l’ordre d’arrêt en nouvel ordre de mouvement. Les minuscules `a`, `r`, `d`, `g` doivent être traitées explicitement. Les octets doivent être lus individuellement, sans attendre de saut de ligne : l’application n’en ajoute aucun.

Vérifier que la boucle de réception ne reste pas bloquée dans un mouvement ou dans un long `delay()`. Vérifier également qu’une commande de mouvement mémorisée n’est pas réappliquée immédiatement après la minuscule d’arrêt.

Le principe de traitement est le suivant, à adapter au câblage et au pilote de moteurs existants :

```text
lire un caractère c
si c vaut A, R, D ou G : appliquer le mouvement demandé
si c vaut a, r, d ou g : arrêter le mouvement concerné
si c vaut E : activer la pompe
si c vaut F : désactiver la pompe
```

L’arrêt des moteurs ne doit pas appeler l’arrêt de la pompe : elle reste commandée par `E` et `F`. Le programme embarqué et les broches du pilote moteur n’ayant pas été fournis, aucun correctif matériel précis n’est supposé dans cette livraison. Fournir la boucle de lecture Bluetooth et les fonctions qui commandent les moteurs pour diagnostiquer cette partie.

## Vérification côté Android

En plus des tests du protocole, `DirectionTouchTest` exécute les véritables listeners tactiles de `MainActivity` avec Robolectric : appui puis relâchement des quatre boutons, annulation tactile, sortie du doigt et activation de la pompe pendant un appui. Ces tests contrôlent les caractères transmis au transport, sans simuler une réception radio ni un robot réel.
