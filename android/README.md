# CombatFit Android

Projet Android natif qui embarque la version HTML de CombatFit dans une WebView et ajoute un service Android de minuteur en premier plan. Le dépôt GitHub Pages existant peut continuer à servir le `index.html` à sa racine.

## Construction de l'APK

Le workflow GitHub Actions `.github/workflows/build-android.yml` construit l'APK debug à chaque push concernant `android/`. Dans GitHub : **Actions → Build CombatFit Android → dernier lancement → Artifacts → combatfit-android-debug**.

## Installation

Télécharger `app-debug.apk` depuis l'artefact, l'ouvrir sur le téléphone Android et autoriser l'installation de l'application. Au premier lancement, autoriser les notifications pour voir le minuteur dans la barre de notifications.

## Minuteur

Le bouton flottant et les commandes du minuteur synchronisent le compte à rebours avec un service Android au premier plan. La notification affiche le compte à rebours, propose **Arrêter**, et une notification d'alerte apparaît à la fin. Le service utilise un verrou CPU temporaire pour tenir le compte à rebours lorsque l'écran est verrouillé.

Application ID : `fr.combatfit.app`
