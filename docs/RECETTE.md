# Essais sur téléphone et robot

Commencer avec les roues levées ou des LED de test. Utiliser le profil correspondant réellement au programme embarqué. Les tests radio n’ont pas été effectués dans l’environnement de compilation.

| Essai | Résultat attendu |
| --- | --- |
| Premier lancement | Hum-Bot, Classic, huit boutons désactivés, TX vide |
| Connexion HC-05/HC-06 appairé | Arrêt initial `S`, voyant vert, commandes actives |
| Appui/relâchement des directions | `AS`, `RS`, `GS`, `DS`, sans espace ni saut de ligne |
| Annulation tactile ou sortie du doigt | `S`, sans redémarrage automatique |
| Deux clics Phares | `F`, puis `f` ; indicateur de demande ON puis OFF |
| Deux clics sur les autres réservés | `T/t`, `K/k`, `X/x` |
| Annuler un appui sur un interrupteur | Aucun caractère envoyé |
| Maintenir Avancer puis cliquer Phares d’un autre doigt | `A`, `F`, puis `S` seulement au relâchement de la direction ; phares indépendants |
| Fermer avec Phares ON | Tentative `S`, puis `f`, puis fermeture de la liaison |
| Ouvrir Réglages pendant un mouvement | Tentative d’arrêt et déconnexion ; aucun envoi depuis les champs de réglage |
| Renommer les quatre réservés | Les noms persistent et les lettres restent inchangées |
| Affecter `q/Q` à un bouton | Casse exacte, sans conversion automatique |
| Champ de commande vide | Aucun octet pour cet événement |
| Saisir espace, accent ou saut de ligne comme commande | Réglage refusé ; ancien profil conservé |
| Nouveau profil / copie / sélection | Configurations indépendantes et sélection mémorisée après relance |
| Retour sans Enregistrer | Modifications abandonnées |
| Suppression du dernier profil | Profil Hum-Bot valide rétabli |
| Passage Classic → BLE | Arrêt et déconnexion ; choix explicite d’un nouvel appareil |
| Scan BLE sur Android 12+ | Autorisations Appareils à proximité, liste dédoublonnée, arrêt après 12 s ou fermeture du dialogue |
| Scan BLE sur Android 6–11 | Permission de localisation et service activé exigés, sans lecture de position |
| MLT-BT05 FFE0/FFE1 inscriptible | Vérification des services, écriture initiale `S`, puis boutons actifs |
| Mauvais service ou caractéristique READ/NOTIFY uniquement | Erreur explicite, commandes désactivées |
| Module WRITE_NO_RESPONSE uniquement | Envois dans l’ordre, sans chevauchement des opérations GATT |
| UUID personnalisés | Découverte de ceux réellement enregistrés dans le profil |
| Annuler pendant la connexion | Aucune activation tardive des boutons |
| Éteindre le module ou le Bluetooth | Retour au rouge après détection, aucune reconnexion ni reprise automatique |
| Coupure puis appui sur l’état rouge | Motif initial, mode, version Android et derniers TX consultables ; conservés après réouverture |
| Bouton Accueil, verrouillage, changement d’application | Tentative d’arrêt puis déconnexion ; reconnexion manuelle nécessaire |
| Rotation paysage dans les deux sens | Interface accessible ; recréation de l’activité ferme le lien par sécurité |

Vérifier les octets côté UART avec un port de diagnostic distinct. TX et ON/OFF ne sont pas des acquittements physiques. La perte radio doit aussi arrêter le robot via son programme embarqué et son signal de connexion ou un protocole de watchdog convenu.
