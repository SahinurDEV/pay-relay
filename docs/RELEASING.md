# Releasing (maintainers)

Releases are built by GitHub Actions (`.github/workflows/release.yml`), so every APK on the Releases page comes from the tagged source.

## One-time setup: signing secrets

Android only installs an update when it is signed with the same key as the installed app. Keep `release.jks` and its passwords safe and private (a password manager or a private, encrypted backup). Never commit them.

In the repository: **Settings → Secrets and variables → Actions → New repository secret**, add:

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | the keystore as base64: `base64 -w0 release.jks` (Linux), `base64 -i release.jks` (macOS), or in PowerShell `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))` |
| `SIGNING_STORE_PASSWORD` | `storePassword` from `keystore.properties` |
| `SIGNING_KEY_ALIAS` | `keyAlias` from `keystore.properties` |
| `SIGNING_KEY_PASSWORD` | `keyPassword` from `keystore.properties` |

## Making a release

1. Update `CHANGELOG.md` and the default `versionName` in `app/build.gradle.kts`, and merge to `main`.
2. Tag and push:

   ```bash
   git tag v1.2.0
   git push origin v1.2.0
   ```

   Or on GitHub: **Releases → Draft a new release → Choose a tag → v1.2.0 → Publish**.
3. The **Release** workflow tests, builds `pay-relay-1.2.0.apk` with version code `10200`, and attaches it with a `.sha256` file to the release (creating the release with generated notes if you only pushed a tag).

To rebuild an existing tag, run the workflow by hand: **Actions → Release → Run workflow** and enter the tag.

## Pull requests

The **Build** workflow runs on every push and pull request: unit tests, lint and a debug build. The debug APK is attached to the run (**Actions → the run → Artifacts**) so reviewers can install and try the change. Debug builds install next to the release app (`org.threelx.payrelay.debug`).
