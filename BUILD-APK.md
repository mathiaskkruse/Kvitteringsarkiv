# Automatisk APK-build

Projektet indeholder `.github/workflows/build-apk.yml`.

Workflowet bygger en debug-APK på GitHub Actions med:
- JDK 17
- Android SDK 36
- Build Tools 36.0.0
- Gradle 9.6.0
- `testDebugUnitTest`
- `assembleDebug`

Output: `app/build/outputs/apk/debug/app-debug.apk`

APK'en er en debug-build til intern test og kan installeres direkte på Android, hvis installation fra den anvendte browser/filhåndtering er tilladt.
