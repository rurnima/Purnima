# Purnima Self-update (GitHub Releases)

This feature checks the public latest release at
`https://api.github.com/repos/rurnima/Purnima/releases/latest`, finds the first
APK asset, downloads it over HTTPS, and hands the APK to Android's package installer.

## Release publishing requirements

- Publish a GitHub Release with a SemVer tag, e.g. `v0.2.0`.
- Attach the installable APK as a release asset.
- APK must use the same Android application ID and signing certificate as the
  installed Purnima app; Android will reject updates signed with a different key.
- Increase `versionName` and `versionCode` in `app/build.gradle.kts` for each release.
- Android requires the user to approve installs from Purnima and confirm the package
  installer. This implementation does not silently install APKs.

## Important

The project currently uses the default/debug signing setup. A production release
needs a persistent release signing key configured securely in CI; do not commit
the keystore or passwords. The app's release asset endpoint should only publish
trusted, signed APKs.
