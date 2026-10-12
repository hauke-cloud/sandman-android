<!-- llm-readme-management spec=1 commit=f4a83bbe0a39eb8e488d4e332a47d08a7c613665 template=default model=qwen3.8-27b-q4 digest=598d66067ca0 generated=2026-10-12T11:49:09Z -->
<a href="https://hauke.cloud" target="_blank"><img src="https://img.shields.io/badge/home-hauke.cloud-brightgreen" alt="hauke.cloud" style="display: block;" /></a>
<a href="https://github.com/hauke-cloud" target="_blank"><img src="https://img.shields.io/badge/github-hauke.cloud-blue" alt="hauke.cloud Github Organisation" style="display: block;" /></a>
<a href="https://github.com/hauke-cloud/llm-readme-management" target="_blank"><img src="https://img.shields.io/badge/template-default-orange" alt="Repository type - default" style="display: block;" /></a>


# Sandman Android


<img src="https://raw.githubusercontent.com/hauke-cloud/.github/main/resources/img/organisation-logo-small.png" alt="hauke.cloud logo" width="109" height="123" align="right">


<llm header>

An Android app in Kotlin and Jetpack Compose that gives you a phone-based control panel for a sandman instance. You list the machines behind your Kubernetes nodes and send start, stop, or release commands — PXE/WOL wake, cordon, drain, shutdown — without a terminal. It is for operators of a lab or homelab fleet who already run sandman.

</llm>


## :book: Description

<llm description>

Sandman is a controller that manages the power state of physical machines backing Kubernetes nodes: PXE/WOL wake, cordon, drain, and shutdown. This repository is the Android companion app — a phone client that lets you see the fleet and send power requests without a terminal. You point it at your sandman instance, and it polls the device list, shows each machine's current phase, and lets you start, stop, or release a node with a tap. Every request carries your name and an optional reason, which sandman records as annotations.

- Lists all `SandmanDevice` entries with a per-phase count summary, polled on a configurable interval (default 15 s; shortens to 3 s while any device is in flight)
- Sends `start`, `stop` (confirm dialog by default), and `release` requests through sandman's REST API
- Settings screen for the sandman address, bearer token, display name, refresh interval, and a "Test connection" round-trip
- Tolerates newer sandman servers: unrecognised phases decode as `Unknown` rather than failing

It is the mobile front-end for the [sandman](https://github.com/hauke-cloud/sandman) controller in the same organisation.

</llm>


## 🚀 Getting started

<llm getting_started hint="Assume nothing about the ecosystem beyond what the analysis names. If the repository has no build step, say what a reader does with it instead.">

1. Clone the repository.

```bash
git clone https://github.com/hauke-cloud/sandman-android.git
cd sandman-android
```

2. With `local.properties` pointing at your Android SDK (Android Studio writes this file for you), build the debug APK.

```bash
./gradlew assembleDebug
```

3. If your sandman instance is an in-cluster service with no external exposure, forward it onto your lab network.

```bash
kubectl -n sandman port-forward svc/sandman 8080:8080 --address 0.0.0.0
```

Install `app/build/outputs/apk/debug/app-debug.apk` on a phone running Android 8.0 or later, open the app, and enter your sandman address (for example `http://sandman.lab:8080`) in Settings. Tap **Test connection** to confirm the app can reach the API; the device list appears once the address is correct.

</llm>


## :airplane: Usage

<llm usage>

Once the app is installed, you work with it through three screens: the device list, a per-device detail view, and Settings.

**Point the app at your sandman instance.** Open Settings and enter the base URL (for example `http://sandman.lab:8080`). If sandman was started with `--token`, paste the bearer token into the token field. Optionally set a display name that is recorded as `requestedBy` on every power request; without one the app falls back to `sandman-android (<phone model>)`. Tap **Test connection** to confirm an authenticated `GET /api/v1/devices` round-trip succeeds. If sandman is an in-cluster service with no external exposure, open a port-forward from your workstation:

```bash
kubectl -n sandman port-forward svc/sandman 8080:8080 --address 0.0.0.0
```

Then use your workstation's LAN IP as the base URL.

**Watch the fleet.** The device list polls on the interval you set in Settings (default 15 s; choices 5, 10, 15, 30, 60). While any device is in flight the interval shortens to 3 s. Polling pauses when the screen is not visible. Each row shows the device name and its current phase; a summary bar counts devices per phase.

**Act on a machine.** Tap a device to open its detail view (2 s poll while in flight). From there you can:

- **Start** — one tap, no confirmation.
- **Stop** — a confirm dialog appears by default because stopping drains the node and moves workloads. Disable the dialog in Settings (`confirmStop`).
- **Release** — returns the machine to unmanaged.

Every request carries your `requestedBy` name and an optional reason, which sandman records as annotations on the node.

</llm>


## 📄 License

This Project is licensed under the GNU General Public License v3.0

- see the [LICENSE](LICENSE) file for details.


## :coffee: Contributing

To become a contributor, please check out the [CONTRIBUTING](CONTRIBUTING.md) file.


## :email: Contact

For any inquiries or support requests, please open an issue in this
repository or contact us at [contact@hauke.cloud](mailto:contact@hauke.cloud).
