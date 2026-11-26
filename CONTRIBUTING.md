# Contributing

Thanks for helping. A few things keep the project easy to trust and maintain.

## Ground rules

- **The privacy promise comes first.** Only wallet messages may leave the phone, only to the address the owner set. Changes that add analytics, third-party services, or read other messages will not be merged.
- **Never paste a real message.** When reporting a new wallet format, replace phone numbers, amounts, balances and TrxIDs with made-up values.
- Keep changes small and focused. One pull request, one purpose.

## Development

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

- Kotlin, Jetpack Compose, Room, WorkManager, OkHttp. No other runtime dependencies without a good reason.
- `WalletFilter` decides what leaves the phone. Any change to it needs tests in `WalletFilterTest`.
- User-facing text goes in `res/values/strings.xml` with a Bangla translation in `res/values-bn/strings.xml`.
- Try your change on an emulator with `adb emu sms send <sender> "<text>"`.

## Pull requests

1. Fork, then branch from `main`.
2. Make sure the build, tests and lint pass. CI runs them on every pull request and attaches a debug APK to the run, so reviewers can install your change.
3. Describe what changed and how you tested it. Screenshots help for UI changes.
