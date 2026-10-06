# Universal Web Control

Universal Web Control is an independent Android control center for managing supported web and cloud services from a mobile-first interface.

## Current foundation

- Native Kotlin + Jetpack Compose
- U-branded dashboard
- Service catalogue for Workers, Pages, DNS, Domains, Analytics, AI, Security, and Account
- Explicit disconnected/authentication state
- No credentials or API tokens in source control
- GitHub Actions debug APK build

## Service boundary

The UI talks to `ControlService`, not directly to provider APIs. Provider integrations can therefore be added without coupling network credentials or provider-specific response handling to Compose screens.

Cloudflare is referenced only to describe supported services. Universal Web Control is an independent third-party project and does not imply endorsement.

## Build

Use JDK 17 and run:

```
./gradlew assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Security

Never commit API tokens, OAuth client secrets, private keys, or account credentials. Production authentication will be introduced behind the service boundary.


API route integration work is tracked in the main branch.
