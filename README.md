# Cleargram

Cleargram is an unofficial Android client based on the Telegram Android source
tree. It keeps Telegram protocol compatibility while adding local, on-device
message-filtering tools. Cleargram does not operate a separate server and is
not affiliated with Telegram.

## Source base and license

- Base source: Telegram Android 12.8.1.
- Cleargram version: 1.0.0 (versionCode 6916).
- This repository is distributed under the GNU General Public License Version 2;
  see
  [LICENSE](LICENSE).
- Telegram is a trademark of Telegram FZ-LLC. Cleargram is an unofficial
  client and must not be represented as the official Telegram application.

The Telegram API and MTProto documentation are available at
[core.telegram.org](https://core.telegram.org/). Developers using this source
must follow Telegram's applicable API, branding, security, and distribution
requirements.

## Build prerequisites

Use Android SDK platform/build-tools 35, Android NDK 27.2.12479018, JDK 21,
CMake 3.10.2, and the checked-in Gradle 8.13 wrapper for the checked-out
Telegram Android 12.8.1 source base. The public `afat` profile intentionally
omits Google-services integration when built with `-PnoisegramTestNoGoogleServices`.

### Local Telegram API credentials

Telegram API credentials are deliberately not stored in this repository. Obtain
your own credentials through Telegram's documented API process and add these
two values only to `%USERPROFILE%\.gradle\gradle.properties` (or
`~/.gradle/gradle.properties` on Unix-like systems):

```properties
cleargramTelegramApiId=YOUR_API_ID
cleargramTelegramApiHash=YOUR_API_HASH
```

Do not commit this local file, credentials, signing keys, passwords, or any
local keystore path. Gradle configuration and sync work without these values;
an `afatDebug` or `afatRelease` build stops with a clear validation error until
valid values are supplied.

## Reproducible public build commands

From the repository root, use the public no-Google profile:

```bash
./gradlew :TMessagesProj_App:assembleAfatDebug -PnoisegramTestNoGoogleServices --console=plain
./gradlew :TMessagesProj_App:assembleAfatRelease -PnoisegramTestNoGoogleServices --console=plain
./gradlew :TMessagesProj_App:bundleAfatRelease -PnoisegramTestNoGoogleServices --console=plain
```

Release signing material is local and external to this repository. A build
artifact can be compared to its source revision, command line, local toolchain,
and the generated artifact hash; reproducibility also depends on matching the
documented upstream toolchain and local credentials/signing configuration.

## Development status

Cleargram 1.0.0 is preparing for its first public source publication.
Contributions and distribution processes are intentionally limited until that
publication is complete.
