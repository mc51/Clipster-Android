# Contributing

Thanks for helping! Bug reports and feature requests go to the [issues](https://github.com/mc51/Clipster-Android/issues), code changes to pull requests against `master`.

## Development setup

- JDK 17 or newer and the Android SDK (Android Studio brings both)
- `./gradlew assembleDebug` builds `app/build/outputs/apk/debug/app-debug.apk`

## Before opening a pull request

Run what CI runs:

```sh
./gradlew spotlessCheck lint testDebugUnitTest assembleRelease
```

- **Formatting:** `./gradlew spotlessApply` formats Java with [palantir-java-format](https://github.com/palantir/palantir-java-format).
- **Lint:** warnings fail the build. Fix them, or suppress a false positive with `@SuppressLint` / `tools:ignore` and a comment explaining why.
- **Tests:** JVM unit tests live in `app/src/test`. `CryptoTest` checks compatibility with the other Clipster clients: if it fails, Android can no longer read clips from them.

Dependencies are managed in `gradle/libs.versions.toml` and updated by Dependabot.

## Releases

The git tag is the only place the version is kept. `versionName` and `versionCode` are derived from it (`v0.7.2` becomes `0.7.2` / `702`):

```sh
git tag v0.7.0
git push origin v0.7.0
```

The [Release workflow](.github/workflows/build.yml) then builds, signs and publishes `clipster.apk`. Signing needs these repository secrets: `SIGNING_KEY` (base64 of the keystore), `ALIAS`, `KEY_STORE_PASSWORD` and `KEY_PASSWORD`.

To sign release builds locally, create a file named `secret` in the project root (it's git-ignored):

```properties
storeFile=/path/to/clipster.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without it, `assembleRelease` builds an unsigned APK.
