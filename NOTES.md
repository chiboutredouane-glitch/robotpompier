# Maison connectée 1.1

Réalisé par Redouane CHIBOUT.

Interface compacte en trois onglets : Maison, Accès et Musique. Toucher le nom d’une lampe pour la renommer. L’écran Maison tient sans défilement à 360 × 640 dp ; un défilement de secours est conservé sur petit écran, en paysage ou avec une grande police.

DFPlayer Mini :
- R : lire/reprendre ; r : pause.
- C : piste précédente ; N : piste suivante.
- H : augmenter le volume ; h : diminuer le volume.

Ces commandes sont des lettres ASCII seules, sans retour à la ligne. Le programme Arduino doit être adapté : le HC-05 reçoit les lettres et le programme transmet les ordres au DFPlayer. R/r commandent désormais lecture/pause, en conservant l’alimentation du lecteur. L’application ne reçoit pas le volume ou la piste réels.

Les autres commandes sont conservées : L/l, A/a, B/b pour les lampes, V/v ventilateur, S/s sirène, P/p arrosage, O/F et o/f pour porte, M/D et m/d pour store. Les commandes de mouvement sont répétées toutes les 150 ms pendant l’appui ; prévoir un arrêt local Arduino après 500 ms sans commande. Changer d’onglet arrête le mouvement maintenu.

Installer Maison-connectee.apk sur l’ancienne version sans désinstallation pour conserver les noms. Android 6 minimum. APK de développement : huit tests logiciels réussis, signature vérifiée ; montage réel à tester.

Cette publication utilise une branche indépendante et laisse la branche principale du robot pompier inchangée.
