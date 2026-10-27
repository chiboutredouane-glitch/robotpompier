# Vérification de la version 1.2 — 27 octobre 2026

## Résultat

- Compilation **réussie** avec le wrapper fourni : `./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon --console=plain`.
- **16 tests**, 0 échec, 0 erreur, 0 test ignoré : 10 tests du protocole et 6 tests des listeners tactiles Android exécutés avec Robolectric (API 28).
- Android Lint : **No issues found.** Les choix explicites de paysage et de disposition native par poids sont documentés dans le XML et exclus de leurs avertissements de recommandation respectifs.
- APK signé avec une clé de développement Android : vérification `apksigner verify --verbose` réussie, signatures v1 et v2 valides.
- Alignement APK : `zipalign -c 4` réussi.
- Manifeste compilé inspecté avec `aapt dump badging` : application `fr.robotpompier.commande`, version `1.2` (code 3), minimum API 23, cible API 35.
- Tous les fichiers XML source ont été analysés sans erreur de syntaxe.

Outils utilisés : Eclipse Temurin JDK 21.0.8, Gradle 8.13, Android Gradle Plugin 8.9.2, Kotlin 2.1.20, SDK Android 35 et Build Tools 35.0.0. Compilation réalisée par Gradle en ligne de commande ; le projet est prévu pour être ouvert dans Android Studio.

## Contenu

- `RobotPompier-debug.apk` : APK debug installable, indépendant du ZIP source.
- `RobotPompier-projet.zip` : sources Kotlin, ressources natives, tests, wrapper Gradle complet (JAR inclus), documentation et rapports de vérification ; ouvrir le dossier `RobotPompier` après extraction.
- `SHA256SUMS.txt` : empreintes de l’APK et de l’archive.

L’archive ne contient ni SDK/JDK, ni caches Gradle, ni chemins locaux de SDK, ni clé privée de signature. Android Studio/Gradle générera sa propre clé debug lors d’une nouvelle compilation. Pour remplacer une installation existante signée avec une autre clé debug, désinstaller d’abord cette installation ou conserver sa clé de signature.

Le répertoire d’export absolu annoncé par la session (`/codex/browser/projectless/0001-cr-e-une-application-android-native-en-kotlin-in/output`) n’existe pas dans l’environnement attaché et son parent n’est pas accessible en écriture. Les livrables ont donc été conservés dans **`/workspace/robotpompier/output/`**. Aucun échec de compilation n’est lié à cette limitation d’export.

## Limites de validation

Le protocole a été précisé par l’utilisateur après vérification de ses blocs MIT App Inventor : chaque relâchement de direction envoie **S majuscule**, et non une minuscule. La version 1.2 corrige les quatre correspondances, le filtre des caractères autorisés et la séquence de sécurité (`SF`). Les commandes de pompe restent `E` et `F`. Aucun saut de ligne ni espace n’est ajouté.

Les tests contrôlent les flux `AS`, `RS`, `DS`, `GS`, l’indépendance de la pompe et l’arrêt sur annulation/sortie du doigt. Le test `realReleaseStillWritesStopIfLocalHeldStateWasCleared` conserve également le contrôle de l’arrêt explicite après remise à zéro de l’état local.

L’affichage attendu au relâchement est `TX : S · 0x53`. Il confirme le retour réussi de l’écriture sur la socket, sans constituer un accusé de réception du robot. L’arrêt physique avec cette version reste à vérifier sur le robot réel. Voir `docs/DIAGNOSTIC_ARRET.md`.

- Aucun téléphone, émulateur Android système ni module HC-05/HC-06 connecté à cet environnement : les tests Robolectric simulent le framework Android, sans transmission radio ni arrêt physique des moteurs. Les essais réels restent à valider avec `docs/RECETTE.md`.
- Aucun firmware du robot n’a été fourni. L’arrêt physique sur perte de liaison exige l’interverrouillage côté microcontrôleur décrit dans `docs/SECURITE.md` ; il ne peut pas être garanti par un téléphone après coupure du lien.
- Aucune capture MIT App Inventor jointe : l’interface fournie est une proposition paysage, et non une reproduction vérifiée de cette capture.
- L’APK est signé pour les essais. Une clé de production appartenant au propriétaire du projet est nécessaire pour publier et maintenir une version distribuée.

## Rapports inclus

`docs/rapports/tests.xml`, `docs/rapports/lint.txt` et `docs/rapports/signature.txt` conservent les résultats des vérifications réellement exécutées. L’outil de signature signale un avertissement sur une entrée de métadonnées dans `META-INF` pour le schéma v1 ; la signature v2, qui couvre l’APK complet, est bien valide.
