# Security policy

Please **do not open a public issue** for a security problem. Use GitHub's private vulnerability
reporting instead (the **Security** tab of the repository, then **Report a vulnerability**).

Describe the issue, how to reproduce it, and its impact. We will acknowledge the report, keep you
informed, and credit you in the release notes if you wish.

Only the latest release receives security fixes.

When you install the app:

- Install the APK only from this repository's [releases](https://github.com/wootou/rengaine-android/releases).
- Check that it is signed with the Rengaine release key. Its SHA-256 certificate fingerprint is:

  ```
  C4:DD:73:38:7F:C0:CD:E5:3F:1B:17:BE:5E:41:C5:3D:58:A2:6C:A5:55:50:5D:39:91:7C:99:91:93:4A:CF:E2
  ```

  You can check it with `apksigner verify --print-certs rengaine.apk`.
- Connect to a server over **HTTPS**. The app accepts `http://` addresses but warns you: your password
  and your music then travel unencrypted over the network.

---

# Politique de sécurité

Merci de **ne pas ouvrir de ticket public** pour un problème de sécurité : passez par le signalement
privé de GitHub (onglet **Security** du dépôt, puis **Report a vulnerability**). Décrivez le problème,
comment le reproduire et ses conséquences. Nous accuserons réception, vous tiendrons informé et vous
citerons dans les notes de version si vous le souhaitez. Seule la dernière version reçoit les correctifs
de sécurité.

Installez l'APK uniquement depuis les [releases](https://github.com/wootou/rengaine-android/releases) de
ce dépôt et vérifiez l'empreinte de son certificat (ci-dessus). Connectez-vous à un serveur en
**HTTPS** : l'app accepte `http://` mais vous avertit, car votre mot de passe et votre musique passent
alors en clair sur le réseau.
