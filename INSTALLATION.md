# Installer la version iPhone

Un **APK Android ne fonctionne pas sur iPhone**. L’artefact fourni ici est un **IPA non signé**, à signer avant installation. Le HC-05/HC-06 habituel n’est pas compatible avec ce portage iOS ; utiliser un module BLE tel que MLT-BT05 avec les bons UUID.

## Télécharger

1. Se connecter à GitHub et ouvrir les **Actions** du dépôt `chiboutredouane-glitch/robotpompier`.
2. Choisir une exécution réussie **iPhone 1.0.3 · telecommande-universelle-ios**.
3. Dans **Artifacts**, télécharger **TelecommandeUniverselle-iOS-NON-SIGNEE** et extraire le ZIP.
4. Récupérer `TelecommandeUniverselle-1.0.3-non-signee.ipa`.

Les artefacts sont conservés 30 jours. Pour reconstruire, ouvrir le workflow déjà enregistré **Android APK** (fichier `android.yml`), cliquer **Run workflow**, choisir **telecommande-universelle-ios**, puis lancer. Cette branche exécute la compilation iOS, malgré le nom du workflow sur la branche principale. Les exécutions iOS ont un titre iPhone explicite.

## Sans Mac et sans abonnement Apple Developer

Une installation personnelle peut passer par un outil de signature sur **PC Windows**, par exemple **Sideloadly**, avec un **compte Apple gratuit**. Il s’agit d’un outil tiers, distinct d’Apple et de cette application. Vérifier sa prise en charge de la version iOS de l’iPhone et le télécharger depuis son site officiel : <https://sideloadly.io/>. La disponibilité et les règles de signature dépendent d’Apple et de l’outil installé.

Procédure générale :

1. Installer l’outil officiel et les composants Apple qu’il demande pour reconnaître l’iPhone sous Windows.
2. Brancher l’iPhone par USB, le déverrouiller et accepter **Faire confiance à cet ordinateur**.
3. Sélectionner l’iPhone et l’IPA non signé dans l’outil.
4. Signer avec son propre compte Apple, uniquement dans l’outil local choisi. **Ne jamais envoyer son mot de passe ni son code de validation dans cette conversation ou sur GitHub.**
5. Sur l’iPhone, autoriser le profil de développement si iOS le demande dans **Réglages > Général > VPN et gestion de l’appareil**. Activer le **Mode développeur** si demandé par cette méthode d’installation ; iOS peut demander un redémarrage.
6. Ouvrir l’application et autoriser le Bluetooth.

Avec un compte gratuit, la signature de développement dure généralement **sept jours** : il faut la renouveler avec le même compte et le même identifiant d’application. Apple impose aussi des quotas d’applications et d’identifiants. Ce n’est pas une installation permanente ni une publication App Store/TestFlight. Éviter de désinstaller pour renouveler : la désinstallation efface les profils locaux.

Sans ordinateur compatible, sans compte Apple utilisable pour signer et sans distribution Apple déjà préparée, le fichier non signé ne suffit pas pour installer l’application. Un simple lien de téléchargement sur l’iPhone ne remplace pas la signature.

## Avec un Mac emprunté

Télécharger les sources de la branche iOS, ouvrir `TelecommandeUniverselle.xcodeproj` avec Xcode, ajouter son compte Apple dans Xcode puis sélectionner sa **Personal Team** dans **Signing & Capabilities**. Choisir un Bundle Identifier disponible, brancher l’iPhone et utiliser **Run**. Un abonnement payant n’est généralement pas requis pour un essai personnel ; sa durée et ses quotas restent limités.

## Avec Apple Developer / TestFlight

Le propriétaire d’un compte Apple Developer peut choisir son identifiant d’application et sa Team, signer l’archive, puis préparer une distribution App Store Connect/TestFlight. Cette livraison ne crée pas de compte, n’ajoute pas de certificat et ne publie pas l’application automatiquement. Elle n’inclut aucun secret de signature.

## Premier essai

Utiliser des LED : sélectionner le profil, **Connecter**, choisir le module BLE et vérifier le passage au vert après `S`. Attendre une minute puis commander une LED. Tester chaque appui et relâchement, un déplacement et une fonction avec deux doigts, puis le passage à l’accueil de l’iPhone. Éteindre le module et vérifier le retour au rouge. Vérifier l’arrêt réel côté programme embarqué avant de passer aux moteurs.
