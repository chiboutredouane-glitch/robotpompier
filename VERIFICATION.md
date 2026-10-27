# Vérification — Télécommande universelle 1.0.3

Vérifications effectuées le 27 octobre 2026 dans l’environnement de développement.

- Compilation avec le wrapper fourni : **BUILD SUCCESSFUL** pour `assembleDebug testDebugUnitTest lintDebug`.
- **59 tests réussis**, aucun échec, aucune erreur, aucun test ignoré.
- Android Lint : **No issues found.**
- Signature APK vérifiée par `apksigner` : schémas v1 et v2 valides.
- Alignement APK vérifié par `zipalign -c 4`.
- Manifeste compilé inspecté : `fr.telecommande.universelle`, version 1.0.3/code 4, minimum API 23, cible API 35, permissions Bluetooth et BLE attendues.
- Aperçus graphiques générés à partir des vues Android, dont une vue connectée explicitement marquée « simulation ».

## Changement de cette version

La connexion Classic n’ouvre plus de réception permanente, conformément à l’usage en envoi seul du projet MIT fourni. Détection des déconnexions par événements système ACL filtrés sur l’adresse sélectionnée et maintien des erreurs/délais d’envoi. Les commandes, l’arrêt initial S, les noms personnalisés et la suppression de « Réservé 1…4 » sont conservés. La stabilité physique après cette adaptation reste à vérifier sur le téléphone Samsung et le module de l’utilisateur.

## Couverture des tests

- 15 tests de commandes et validation : octets exacts, casse, champs vides, interrupteurs, indépendance mouvement/accessoire, arrêt de sécurité, invalidité des espaces/accents et normalisation des UUID.
- 4 tests de file d’écriture : sérialisation, commande en cours, suppression des mouvements en attente au profit de l’arrêt, reset et commandes identiques successives.
- 6 tests de stockage : conservation des profils et noms, dernier appareil, sélection, suppression, corruption et refus de sauvegarde invalide.
- 10 tests Classic (5 scénarios sur API 28 et API 35) avec exécuteurs et socket simulés : vingt commandes ASCII et avance de 61 secondes de l’horloge Android sans accès au flux RX, déconnexion ACL de l’appareil choisi sans nouvel appui, événement d’un autre appareil ignoré, demande de déconnexion ACL, erreur Android détaillée à l’envoi. L’avance d’horloge n’est pas un essai radio ni une attente réelle de 61 secondes.
- 4 tests de résolution de services BLE : FFE0/FFE1, WRITE, WRITE_NO_RESPONSE et refus d’une caractéristique non inscriptible.
- 5 tests du transport BLE avec callbacks Android simulés : arrêt initial, écritures ASCII, mode sans réponse cadencé, fermeture après arrêt et annulation avec callback tardif.
- 12 tests d’interface : boutons désactivés au départ, gestes, interrupteurs, réglages, renommage sans changement de lettres, fermeture avec désactivation des fonctions actives et **deux doigts sur la fenêtre complète** (direction maintenue pendant le clic d’un réservé).
- 3 tests supplémentaires d’interface vérifient la conservation du motif et des TX après recréation, le motif de perte du focus et l’absence de dialogue pendant une liaison active (inclus dans les 12 ci-dessus).
- 3 tests de rendu Android et de dimensions des zones tactiles en paysage.

Les tests Android utilisent Robolectric, API 28, et les scénarios Classic sont aussi exécutés sur API 35 (enregistrement exporté du récepteur et lecture typée du périphérique) ; les tests de rendu emploient son moteur graphique natif. Java 21.0.8, Gradle 8.13, Android Gradle Plugin 8.9.2, Kotlin 2.1.20, SDK Android 35 et Build Tools 35.0.0 ont été utilisés.

## Limites connues de cette validation

Aucun téléphone physique, HC-05, HC-06 ni MLT-BT05 n’est relié à cet environnement. Les callbacks GATT et les événements tactiles ont été simulés ; la portée radio, le comportement du firmware exact du module, la réception UART et l’arrêt physique des moteurs doivent être vérifiés avec les essais de `docs/RECETTE.md`.

Le profil FFE0/FFE1 est prérempli d’après les informations de l’utilisateur, puis contrôlé sur les services découverts à la connexion. L’affichage TX n’est pas un accusé de réception du robot. La réception de notifications BLE n’est pas implémentée dans cette version.

L’APK est signé pour les essais. Aucune clé privée ni aucun jeton ne sont inclus dans les sources ou l’artefact. Le workflow ne publie que l’APK, son empreinte, les rapports et les aperçus simulés.

La branche GitHub dédiée est **telecommande-universelle**. La branche **main** et l’application Robot Pompier sont conservées. L’application possède un identifiant Android distinct et s’installe à côté de Robot Pompier.
