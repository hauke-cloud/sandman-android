# sandman-android

An Android client for [sandman](https://github.com/hauke-cloud/sandman): start
and stop the machines behind your Kubernetes nodes from a phone.

sandman does the work — a magic packet to wake a machine, a cordon, a drain and
a shutdown pod to put one to sleep. This app is the button. It reads the fleet
from sandman's REST API and records a desired power state; the controller
notices and acts, which means a request survives the app being closed, the
phone leaving the network, or sandman itself restarting.

## What it does

- **The fleet at a glance.** Every `SandmanDevice`, its phase, and a count per
  phase across the top.
- **Start and stop.** One tap to wake a machine. Stopping asks first, because it
  drains a node and moves its workloads.
- **Release.** Hand a machine back to nobody: sandman keeps reporting its state
  and stops acting on it. It is the way out of a stop that is not a start.
- **A reason on the record.** Every power request carries who asked and why.
  sandman keeps both as annotations on the device, so the next person to look
  can tell a maintenance window from a mistake.
- **Polling that knows when to hurry.** A machine that is starting or stopping
  is polled every few seconds; an idle fleet at whatever interval you set. Both
  stop when the screen is not in front of you.

## Setting it up

Open **Settings** and give it:

| Field | |
|---|---|
| Address | The root of the sandman API, e.g. `http://sandman.lab:8080`. A bare host gets `http://`; a sub-path is kept, so an instance behind a reverse proxy works. |
| Bearer token | Only if sandman was started with `--token`. Leave empty otherwise. |
| Your name | Recorded on every start and stop. Left empty, the phone's model is used. |

**Test connection** does a real authenticated round trip and tells you how many
devices came back.

If sandman is inside the cluster and not exposed, a port-forward is the usual
way in:

```sh
kubectl -n sandman port-forward svc/sandman 8080:8080 --address 0.0.0.0
```

Cleartext HTTP and user-installed CA certificates are both permitted — see
[`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml).
A lab network is the expected home for this app.

## Building

Requires JDK 17+ and an Android SDK with platform 37.

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # minified and shrunk, ~1.8 MB
./gradlew testDebugUnitTest lintDebug
```

`local.properties` must point at your SDK (`sdk.dir=...`); Android Studio
writes it for you.

## Signing

Both build types are signed with the **same** key, and neither uses an
application-id suffix. A debug build therefore installs straight over a release
build and vice versa — no uninstall, no lost settings.

Generate the key once:

```sh
hack/create-keystore.sh
```

It writes a 4096-bit RSA key to
`~/.config/hauke-cloud/sandman-android/release.jks` and a git-ignored
`keystore.properties` pointing at it, with a random password.

**Back both up.** The signing key is the only thing that decides whether a new
build can replace an installed one. Lose it and every device has to uninstall
before it can take another update.

The build reads the environment instead of the properties file when one is
set, which is how CI supplies the key:

| Property | Environment variable |
|---|---|
| `storeFile` | `SANDMAN_KEYSTORE_FILE` |
| `storePassword` | `SANDMAN_KEYSTORE_PASSWORD` |
| `keyAlias` | `SANDMAN_KEY_ALIAS` |
| `keyPassword` | `SANDMAN_KEY_PASSWORD` |

Without either, the build falls back to the stock debug key so a fresh clone
still compiles — it just cannot upgrade an existing install.

## Continuous integration

[`ci.yml`](.github/workflows/ci.yml) runs the unit tests, lint and a debug
build on every push and pull request. That job needs no secrets, so a pull
request from a fork still builds.

A tag matching `v*` additionally produces a signed APK and attaches it to a
GitHub release. Push the signing material once:

```sh
hack/ci-secrets.sh          # --dry-run first, if you like
```

It reads `keystore.properties` and sets, via `gh`:

| | |
|---|---|
| `SANDMAN_KEYSTORE_BASE64` | secret — the keystore itself |
| `SANDMAN_KEYSTORE_PASSWORD` | secret |
| `SANDMAN_KEY_ALIAS` | secret |
| `SANDMAN_KEY_PASSWORD` | secret |
| `SANDMAN_SIGNING_SHA256` | **variable** — the certificate fingerprint |

The last one is a repository variable rather than a secret because a
certificate fingerprint is public, and because CI prints it. After signing, the
release job compares the APK's certificate against it and fails on a mismatch.
A key that quietly changed would otherwise only be discovered by everyone who
already has the app installed and cannot update it. If the variable is unset
the job passes with a warning naming the fingerprint to set.

Cutting a release:

```sh
git tag -a v0.2.0 -m 'v0.2.0'
git push origin v0.2.0
```

The tag is the only place a version is written down. `v0.2.0` becomes
`versionName 0.2.0` and `versionCode 200` — `major * 10000 + minor * 100 +
patch`, so the number rises with the tag and Android will accept the upgrade.
A local build with no tag in the environment is `0.1.0`.

## The API it speaks

| | |
|---|---|
| `GET /api/v1/devices` | every device, with a count per phase |
| `GET /api/v1/devices/{name}` | one device |
| `POST /api/v1/devices/{name}/start` | wake it |
| `POST /api/v1/devices/{name}/stop` | drain and power it off |
| `POST /api/v1/devices/{name}/release` | stop managing it |

Power requests carry `{"requestedBy": ..., "reason": ...}`. sandman decodes
that body with `DisallowUnknownFields`, so the app sends those two fields and
nothing else.

A phase the app does not recognise — from a sandman newer than it — decodes as
`Unknown` rather than failing the whole response.

## Layout

```
app/src/main/java/cloud/hauke/sandman/
├── data/
│   ├── model/Device.kt         the API's types, mirroring internal/server/types.go
│   ├── remote/SandmanClient.kt OkHttp + kotlinx.serialization; base URL is per-call
│   ├── remote/SandmanException.kt  every failure, in the words the UI shows
│   ├── SettingsRepository.kt   DataStore; excluded from backups (it holds the token)
│   └── DeviceRepository.kt     attaches "who asked" to every power request
└── ui/
    ├── devices/                the fleet
    ├── detail/                 one machine, polled harder while it moves
    ├── settings/               address, token, name, refresh
    └── components/             phase badges, the confirm dialog, time formatting
```

Kotlin, Jetpack Compose and Material 3, `minSdk 26`. No dependency-injection
framework: `AppContainer` builds the three objects the app needs by hand.
