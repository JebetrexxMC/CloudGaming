# Android builds

`.github/workflows/build-apk.yml` builds two APKs:

| APK | Source | Purpose |
| --- | --- | --- |
| `moonlight-cloudgaming-debug.apk` | [moonlight-android](https://github.com/moonlight-stream/moonlight-android) | Streaming client, labelled **CloudGaming**. Installs next to the official Moonlight (`com.limelight.cloudgaming`) |
| `tailscale-debug.apk` | [JebetrexxMC/tailscale-android](https://github.com/JebetrexxMC/tailscale-android) | Private network to your gaming VM |

ZeroTier users can install the official ZeroTier One app from the Play Store instead.

## Get the APK

Go to **Actions → Build Android APKs → latest run → Artifacts**. To publish one, push a tag: `git tag v1.0.0 && git push --tags`. The APKs are then attached to a GitHub Release.

## Custom icon / strings

Put files under `android/moonlight-overlay/` using the same layout as `moonlight-android/app/`, for example `src/main/res/mipmap-xxxhdpi/ic_launcher.png`. The workflow copies them in before building.

## Signed release (optional)

Add these repository secrets: `ANDROID_KEYSTORE_BASE64` (`base64 -w0 release.jks`), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. With them set, the workflow also produces `moonlight-cloudgaming-release.apk`.
