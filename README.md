# Altrii Filter

This repository contains an Android VPN-based DNS filter that routes DNS lookups through the [CleanBrowsing](https://cleanbrowsing.org/) family filter resolvers while applying additional in-app blocks for social media, YouTube and gambling domains.

## Features

- Foreground VPN service that proxies DNS queries to CleanBrowsing's family-friendly resolvers for adult content filtering.
- Optional category switches (enabled by default) to block popular social media, YouTube and gambling domains before they reach the upstream resolver.
- Local blocked-site screen and toast/snackbar notifications when domains are intercepted.
- Lightweight implementation that only tunnels DNS traffic to keep browsing performance fast.

## Building

The repository ships text-only Gradle bootstrap scripts pinned to Gradle 8.2 so Android Studio and standalone terminals resolve a consistent toolchain without committing binary wrapper artifacts. The scripts fetch the official Gradle distribution on first run and reuse it from your Gradle user home.

### Prerequisites

- macOS/Linux: `curl` or `wget` and `unzip` available on the PATH.
- Windows: PowerShell 5.1+ with the `Invoke-WebRequest` cmdlet enabled.

Once the prerequisites are met you can invoke Gradle normally, for example:

```
./gradlew tasks
```

Because this project relies on the Android Gradle plugin, ensure the Android SDK and plugin artifacts are available in your build environment before attempting a full build. In restricted environments without access to Google's Maven repository the command above will fail while resolving the Android Gradle plugin.
