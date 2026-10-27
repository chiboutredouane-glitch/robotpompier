# Vérification — Télécommande universelle 1.0.2

Vérifications effectuées le 27 octobre 2026 dans l’environnement de développement.

- Compilation avec le wrapper fourni : **BUILD SUCCESSFUL** pour `assembleDebug testDebugUnitTest lintDebug`.
- **52 tests réussis**, aucun échec, aucune erreur, aucun test ignoré.
- Android Lint : **No issues found.**
- Signature APK vérifiée par `apksigner` : schémas v1 et v2 valides.
- Alignement APK vérifié par `zipalign -c 4`.
- Manifeste compilé inspecté : `fr.telecommande.universelle`, version 1.0.2/code 3, minimum API 23, cible API 35, permissions Bluetooth et BLE attendues.
- Aperçus graphiques générés à partir des vues Android, dont une vue connectée explicitement marquée « simulation ».

## Changement de cette version

Suppression du sous-titre automatique « Réservé 1…4 » dans les quatre boutons de fonctions. Les noms personnalisés et commandes des profils sont conservés. Annulation de la découverte Bluetooth avant une connexion Classic et détail des erreurs IOException (opération et durée). La correction de la coupure physique signalée n’est pas établie : le module n’est pas disponible ici.

## Couverture des tests

- 15 tests de commandes et validation : octets exacts, casse, champs vides, interrupteurs, indépendance mouvement/accessoire, arrêt de sécurité, invalidité des espaces/accents et normalisation des UUID.
- 4 tests de file d’écriture : sérialisation, commande en cours, suppression des mouvements en attente au profit de l’arrêt, reset et commandes identiques successives.
- 6 tests de stockage : conservation des profils et noms, dernier appareil, sélection, suppression, corruption et refus de sauvegarde invalide.
- 3 tests Classic avec exécuteurs et flux RFCOMM simulés : vingt commandes sans aucune réponse du module, détection de fin du flux, erreur Android détaillée à l’envoi.
- 4 tests de résolution de services BLE : FFE0/FFE1, WRITE, WRITE_NO_RESPONSE et refus d’une caractéristique non inscriptible.
- 5 tests du transport BLE avec callbacks Android simulés : arrêt initial, écritures ASCII, mode sans réponse cadencé, fermeture après arrêt et annulation avec callback tardif.
- 12 tests d’interface : boutons désactivés au départ, gestes, interrupteurs, réglages, renommage sans changement de lettres, fermeture avec désactivation des fonctions actives et **deux doigts sur la fenêtre complète** (direction maintenue pendant le clic d’un réservé).
- 3 tests supplémentaires d’interface vérifient la conservation du motif et des TX après recréation, le motif de perte du focus et l’absence de dialogue pendant une liaison active (inclus dans les 12 ci-dessus).
- 3 tests de rendu Android et de dimensions des zones tactiles en paysage.

Les tests Android utilisent Robolectric, API 28 ; les tests de rendu emploient son moteur graphique natif. Java 21.0.8, Gradle 8.13, Android Gradle Plugin 8.9.2, Kotlin 2.1.20, SDK Android 35 et Build Tools 35.0.0 ont été utilisés.

## Limites connues de cette validation

Aucun téléphone physique, HC-05, HC-06 ni MLT-BT05 n’est relié à cet environnement. Les callbacks GATT et les événements tactiles ont été simulés ; la portée radio, le comportement du firmware exact du module, la réception UART et l’arrêt physique des moteurs doivent être vérifiés avec les essais de `docs/RECETTE.md`.

Le profil FFE0/FFE1 est prérempli d’après les informations de l’utilisateur, puis contrôlé sur les services découverts à la connexion. L’affichage TX n’est pas un accusé de réception du robot. La réception de notifications BLE n’est pas implémentée dans cette version.

L’APK est signé pour les essais. Aucune clé privée ni aucun jeton ne sont inclus dans les sources ou l’artefact. Le workflow ne publie que l’APK, son empreinte, les rapports et les aperçus simulés.

La branche GitHub dédiée est **telecommande-universelle**. La branche **main** et l’application Robot Pompier sont conservées. L’application possède un identifiant Android distinct et s’installe à côté de Robot Pompier.
