# Vérification iOS 1.0.3

Le projet est compilé et testé par le workflow macOS de la branche `telecommande-universelle-ios`. Les résultats effectifs sont ceux de l’exécution GitHub liée à la livraison ; une exécution non terminée ou en échec ne constitue pas une validation.

Vérifications prévues et exécutées par ce workflow :

- Projet Xcode et plist valides.
- 13 tests Swift du protocole et de la file d’écriture.
- 5 tests UIKit sur simulateur : contrôles désactivés et dimensions paysage, relâchement/annulation, deux contrôles indépendants, arrêt des fonctions actives, persistance et récupération de profils corrompus.
- Compilation et tests iOS sur un simulateur iPhone disponible.
- Archive Release pour appareil iOS physique, avec vérification du binaire arm64.
- Emballage en IPA **non signé**, accompagné de son empreinte SHA-256.

Les tests de gestes déclenchent les événements natifs des contrôles UIKit. Ils ne reproduisent pas la radio ni les gestes d’un véritable utilisateur sur écran physique. Les UUID, commandes, cas de coupure et la réception UART doivent encore être vérifiés avec un iPhone et un MLT-BT05 réels.

Aucun Mac/Xcode n’est installé dans l’environnement Linux de préparation. La compilation iOS se déroule sur le runner macOS GitHub. Aucun compte Apple, certificat de développement, clé privée ou profil de provisioning n’est disponible pour signer ici une installation sur iPhone. L’IPA livré nécessite une signature externe ; il ne constitue pas une application installable directement depuis le navigateur.
