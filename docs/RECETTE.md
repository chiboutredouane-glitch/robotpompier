# Recette sur téléphone et robot

Faire les premiers essais avec roues levées et pompe débranchée ou alimentée de manière adaptée à l’essai.

| Essai | Résultat attendu |
| --- | --- |
| Démarrage sans connexion | Voyant rouge, six commandes inactives |
| Téléphone sans Bluetooth Classic | Message explicite, pas de plantage |
| Bluetooth désactivé | Demande système d’activation ; annulation gérée |
| Refus « Appareils à proximité » Android 12+ | Commandes inactives, explication affichée |
| Aucun appareil appairé | Proposition d’ouvrir les paramètres Bluetooth |
| Sélection HC-05 puis HC-06 | Connexion SPP ; réception initiale de `ardgF`, voyant vert |
| Module éteint pendant connexion | Échec ou délai de 12 s ; bouton Bluetooth utilisable ensuite |
| Annuler une connexion | Retour au rouge ; aucune reconnexion tardive |
| Quatre directions | Réception exacte `Aa`, `Rr`, `Dd`, `Gg`, sans octet 10/13/espace |
| Maintenir Avancer, puis activer la pompe d’un autre doigt | `A`, puis `E` ; pas de `a` avant relâchement du premier doigt |
| Relâcher Avancer pendant pompe active | `a`, sans `F` ; la pompe reste commandée active |
| Maintenir une direction puis désactiver la pompe | `F` pendant maintien, minuscule seulement au relâchement |
| Glisser le doigt hors d’une direction | Minuscule d’arrêt ; aucun redémarrage sans nouvel appui |
| Notification, bouton Accueil, verrouillage | Tentative `ardgF`, connexion fermée et commandes désactivées |
| Bouton Déconnecter / Fermer / Retour | Tentative `ardgF` puis fermeture, aucun mouvement mémorisé |
| Coupure d’alimentation du module en mouvement et avec pompe active | La pile Bluetooth finit par signaler la perte ; l’interverrouillage embarqué arrête physiquement les actionneurs |
| Éloignement hors portée en mouvement | Même arrêt embarqué, sans attendre la détection Android |
| Réactivation après coupure | Reconnexion manuelle, `ardgF`, aucun mouvement repris |
| Rotation paysage dans les deux sens | Commandes lisibles ; changement de configuration ferme la session par sécurité |
| Petit écran et texte système agrandi | Six commandes accessibles, pas de masquage par les barres système |
| TalkBack / clavier | Direction activée brièvement et relâchée par action d’accessibilité ; touches Entrée/Espace maintenues suivies du relâchement |

Capturer le flux série côté microcontrôleur ou avec un second adaptateur série pour vérifier les octets réels, sans se fier uniquement au voyant de l’application. Le voyant vert indique une socket connectée, pas une télémétrie du robot ni un accusé de réception des commandes.
