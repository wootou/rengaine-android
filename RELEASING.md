# Releasing

How a maintainer publishes a version. The signing key never leaves the maintainer's machine and is
never stored in this repository or in GitHub.

## Version numbers

- `versionName` follows SemVer: `1.0.0-rc.1`, `1.0.0-rc.2`, … then `1.0.0`.
- `versionCode` goes up by one for **every** APK published, release candidates included. Android
  refuses to install an APK whose `versionCode` is lower than the installed one, so a number is
  never reused.

Both live in `app/build.gradle.kts`.

## Steps

1. Update `versionName` and `versionCode`, and add a section for the version to `CHANGELOG.md`.
2. If dependencies changed, refresh the licences list and commit it:
   `./gradlew :app:exportLibraryDefinitions` (CI fails when it is out of date).
3. Commit, and check that CI is green.
4. Build the signed APK:

   ```sh
   RENGAINE_KEYSTORE=/path/to/keystore.properties scripts/build-release.sh
   ```

   The script refuses uncommitted changes and a missing changelog entry, runs the tests and lint,
   builds the release, checks that the APK is signed with the release key (fingerprint in
   [SECURITY.md](SECURITY.md)), and writes `dist/rengaine-<version>.apk` and its `.sha256` file.
5. Try the APK on a real device (install over the previous version: settings and sign-in kept).
6. Tag and publish. Release candidates are **pre-releases**:

   ```sh
   git tag -a v1.0.0-rc.1 -m "Rengaine 1.0.0-rc.1"
   git push origin v1.0.0-rc.1
   gh release create v1.0.0-rc.1 --prerelease --title "Rengaine 1.0.0-rc.1" \
     --notes-file notes.md dist/rengaine-1.0.0-rc.1.apk dist/rengaine-1.0.0-rc.1.apk.sha256
   ```

   The notes repeat the changelog section, and give the APK's SHA-256 and the certificate
   fingerprint.

During a release candidate, only fixes go in: new features wait for the next version.

---

# Publier une version

Le numéro `versionName` suit SemVer (`1.0.0-rc.1`… puis `1.0.0`) ; `versionCode` augmente de 1 à
chaque APK publié, RC comprises, et n'est jamais réutilisé. Étapes : mettre à jour les deux numéros et
`CHANGELOG.md`, rafraîchir la liste des licences si les dépendances ont changé, commiter (CI verte),
lancer `scripts/build-release.sh` (tests, lint, APK signé et vérifié, empreinte SHA-256 dans `dist/`),
essayer l'APK sur un vrai téléphone, puis créer le tag et la release GitHub (pré-version pour une RC)
avec l'APK et son fichier `.sha256`. La clé de signature reste hors du dépôt et hors de GitHub.
Pendant une RC, seulement des corrections.
