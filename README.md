# RASTA Universal Audio IR Remote — v11.1.1 GitHub Build Repair

Universal IR remote project with protocol-aware transmission, discovery, memory verification, and hardened WebView integration.

## GitHub Actions

Every push/PR to `main`/`master`, and every manual workflow dispatch, runs:

- JDK 17
- Android SDK 35
- Gradle 8.9
- Debug APK build
- Release APK build
- SHA-256 generation
- Artifact upload

### Downloading the APK

Open:

`GitHub → Actions → Android Build → successful run → Artifacts`

Then download either:

- `RASTA-Universal-Audio-IR-Remote-debug`
- `RASTA-Universal-Audio-IR-Remote-release-unsigned`

## Important

The repository must not contain signing keys or passwords. The Release APK produced by this workflow is unsigned unless a signing configuration is added through GitHub Secrets.

## Validation

See `VALIDATION_REPORT_v11.1.0.txt` for the source-level validation status and known build-environment limitation.


## Re-audit notes

- Only one GitHub Actions workflow is included.
- The repository intentionally uses the GitHub Gradle setup action rather than a fake `gradlew`.
- Pioneer Auto Discovery defaults to scanning the documented Pioneer device range 160–175.
- Pioneer has no independent subdevice byte.
- A same-frame Pioneer double transmission is not represented as a generic compound 64-bit command. True compound Pioneer commands require two learned frames and are not fabricated.

## Discovery policy

Pioneer discovery has two separate modes:

- **Command discovery:** fixed Device (default 160), scan Command/Data 0–255. This is the mode opened by individual Pioneer key candidates.
- **Device discovery:** scan Device 160–175 with Command fixed. Use this only when the target device/address itself is unknown.

A Pioneer compound command is never fabricated from a duplicated frame. Two-frame compound mappings require two independently learned frames.


## GitHub build troubleshooting (v11.1.1)

- The workflow file is `.github/workflows/build.yml`.
- It runs on pushes to any branch, pull requests, and manual dispatch.
- To start manually: open **Actions → Android Build → Run workflow**.
- To get the APK: open the completed run and download the `RASTA-Universal-Audio-IR-Remote-debug` artifact.
- The release artifact is unsigned. It is not suitable for normal public distribution until it is signed.
- This project intentionally invokes the Gradle 8.9 installation provided by the workflow; it does not include a Gradle wrapper script.
- If the workflow fails, open the failed job and inspect the first red step. The exact error log is needed to identify any remaining environment- or source-specific failure.
