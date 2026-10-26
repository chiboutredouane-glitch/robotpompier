# Arrêt de sécurité

## Dans l’application

- Un relâchement, une annulation tactile ou une sortie du doigt du bouton envoie la minuscule correspondant à la direction maintenue.
- Une fermeture, une déconnexion, une perte du focus ou une mise en arrière-plan désactive immédiatement les commandes, abandonne les mouvements encore en attente dans la file, tente `ardgF`, puis ferme la socket. La fermeture forcée survient au plus tard 750 ms après la demande si un envoi bloque, sous réserve de l’ordonnancement Android et des délais système.
- Une nouvelle connexion commence par `ardgF` avant d’activer les commandes.
- Une fin de flux ou une exception de la socket met le voyant au rouge et désactive les commandes. La détection radio suit les délais de la pile Bluetooth : elle n’est pas instantanée.
- Une écriture bloquée pendant plus de 750 ms déclenche la fermeture de la socket.
- Une coupure électrique du téléphone ou une suppression forcée du processus peut empêcher toute commande finale. Aucun arrêt logiciel côté téléphone ne couvre ce cas seul.

## À prévoir obligatoirement sur le robot

Le HC-05 expose souvent une broche **STATE** ; vérifier sa polarité et son comportement avec le modèle exact. Tous les HC-06 ne l’exposent pas. Une LED clignotante n’est pas automatiquement une sortie logique compatible ; consulter la fiche du module et respecter les niveaux électriques.

La boucle du microcontrôleur doit avoir priorité sur les ordres de déplacement :

```text
au démarrage : moteurs à l’arrêt, relais de pompe à l’arrêt
à chaque tour de boucle :
    si le signal de connexion du module indique « déconnecté » :
        couper les deux moteurs et la pompe
        vider les commandes série en attente
        effacer tout mouvement mémorisé
        ne pas traiter les commandes d’action
    sinon :
        lire les caractères ASCII et appliquer les commandes connues
        pour a, r, d, g : arrêter la direction correspondante, même si déjà arrêtée
        pour F : couper la pompe, même si déjà coupée
```

Ne pas utiliser de longs `delay()` ou de routine bloquante qui empêcherait de vérifier le signal de connexion. Maintenir le relais de pompe et les sorties moteurs à l’état inactif pendant le démarrage et les réinitialisations.

Si le module ne fournit pas de signal de connexion exploitable, définir une extension de protocole avec heartbeat, fréquence d’émission et délai d’expiration, puis modifier à la fois l’application et le firmware. Cette livraison n’ajoute pas de caractère de heartbeat non demandé. Un watchdog basé seulement sur l’absence des commandes actuelles ne permet pas de distinguer une pompe maintenue d’une perte de liaison.

L’arrêt physique sur perte de liaison doit être testé sur le robot réel. Les caractères de relâchement doivent être associés aux sorties effectives du programme embarqué : le téléphone ne peut pas déduire le câblage ni garantir la réaction des moteurs ou du relais.
