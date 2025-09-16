# Altrii Filter

This repository contains an Android VPN-based DNS filter that routes DNS lookups through the [CleanBrowsing](https://cleanbrowsing.org/) family filter resolvers while applying additional in-app blocks for social media, YouTube and gambling domains.

## Features

- Foreground VPN service that proxies DNS queries to CleanBrowsing's family-friendly resolvers for adult content filtering.
- Optional category switches (enabled by default) to block popular social media, YouTube and gambling domains before they reach the upstream resolver.
- Local blocked-site screen and toast/snackbar notifications when domains are intercepted.
- Lightweight implementation that only tunnels DNS traffic to keep browsing performance fast.

## Building

The project is configured for Android Studio / Gradle builds. In offline environments the provided `gradlew` wrapper script delegates to a locally installed `gradle` binary.

```
./gradlew tasks
```

Because this project relies on the Android Gradle plugin, ensure the Android SDK and plugin artifacts are available in your build environment before attempting a full build.
