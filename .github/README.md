# GitHub Actions Workflows für LockerLift

Dieses Verzeichnis enthält GitHub Actions Workflows für automatisiertes Builden, Testen und Veröffentlichen der LockerLift App.

---

## 📋 Verfügbare Workflows

| Workflow | Auslöser | Beschreibung | Output |
|----------|----------|--------------|--------|
| [`release.yml`](./workflows/release.yml) | `git tag v*` push | Baut **Release-APKs** und erstellt ein GitHub Release mit den APKs | GitHub Release mit mobile-release.apk & wear-release.apk |
| [`build-debug.yml`](./workflows/build-debug.yml) | Manuell (Workflow Dispatch) | Baut **Debug-APKs** für Testing | Artifact mit mobile-debug.apk & wear-debug.apk |
| [`build-pr.yml`](./workflows/build-pr.yml) | Pull Request | Führt **Tests** aus und baut Debug-APKs | Test-Ergebnisse + APK-Artifacts |

---

## 🚀 Release Workflow (release.yml)

**Automatisch ausgelöst durch:** `git tag v1.7.0 && git push origin v1.7.0`

### Was passiert?
1. ✅ Checkout des Repositorys
2. ✅ JDK 21 einrichten
3. ✅ Android SDK 35 einrichten
4. ✅ Abhängigkeiten herunterladen
5. ✅ **Mobile APK (Release) bauen**
6. ✅ **Wear APK (Release) bauen**
7. ✅ **GitHub Release erstellen** mit:
   - Mobile APK (`mobile-release.apk`)
   - Wear APK (`wear-release.apk`)
   - Automatische Release-Notizen aus CHANGELOG.md

### Voraussetzungen für SIGNED APKs

Falls du **signierte APKs** für den Play Store brauchst, musst du diese **Secrets** in deinem GitHub Repository einrichten:

| Secret Name | Beschreibung | Beispiel |
|-------------|--------------|---------|
| `ANDROID_KEYSTORE_FILE` | Base64-kodierte Keystore-Datei | `-----BEGIN CERTIFICATE-----...` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore-Passwort | `meinpasswort123` |
| `ANDROID_KEY_ALIAS` | Alias des Keys | `upload` |
| `ANDROID_KEY_PASSWORD` | Key-Passwort | `meinpasswort123` |

#### Keystore-Datei vorbereiten:
```bash
# Keystore-Datei in Base64 kodieren
base64 -i my-release-key.keystore > my-release-key.keystore.base64

# In GitHub Secrets einfügen:
# 1. Inhalt von my-release-key.keystore.base64 kopieren
# 2. Als Secret ANDROID_KEYSTORE_FILE speichern
```

#### Signing-Konfiguration in build.gradle.kts:
```kotlin
// In mobile/build.gradle.kts und wear/build.gradle.kts
android {
    signingConfigs {
        release {
            storeFile = file(System.getenv("ANDROID_KEYSTORE_FILE")?.let { 
                File(".keystore").apply { 
                    writeBytes(Base64.getDecoder().decode(it))
                }
            })
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

> **⚠️ WICHTIG:** Ohne diese Secrets werden **Debug-APKs** gebaut (für Testing geeignet, aber nicht für den Play Store).

---

## 🛠️ Debug Build Workflow (build-debug.yml)

**Manuell auslösbar** über die GitHub Actions UI.

### Wie auslösen?
1. Gehe zu: [GitHub Actions](https://github.com/christian98b/LockerLift/actions)
2. Wähle den Workflow **"Build Debug APKs"**
3. Klicke auf **"Run workflow"**
4. Optional: Grund für den Build angeben
5. Klicke auf **"Run workflow"**

### Output
- **Artifact:** `debug-apks` (verfügbar für 7 Tage)
  - `mobile-debug.apk`
  - `wear-debug.apk`

---

## 🧪 Pull Request Workflow (build-pr.yml)

**Automatisch ausgelöst** bei Pull Requests auf `main` oder `develop`.

### Was passiert?
1. ✅ Tests ausführen
2. ✅ Debug-APKs bauen
3. ✅ Artifacts hochladen

### Output
- **Test-Ergebnisse** in der PR
- **Artifact:** `pr-apks` (verfügbar für 7 Tage)

---

## 📥 Wie man APKs herunterlädt

### Von einem Release
1. Gehe zu: [Releases](https://github.com/christian98b/LockerLift/releases)
2. Wähle das gewünschte Release (z.B. v1.7.0)
3. Lade die APKs herunter:
   - `mobile-release.apk` (für Smartphone)
   - `wear-release.apk` (für Wear OS Uhr)

### Von einem Workflow Run
1. Gehe zu: [Actions](https://github.com/christian98b/LockerLift/actions)
2. Wähle den gewünschten Workflow-Run
3. Scroll zu **"Artifacts"**
4. Klicke auf das Artifact (z.B. `debug-apks`)
5. Lade die APKs herunter

---

## 🔧 Anpassungen

### Workflow-Dateien anpassen
Die Workflows können an deine Bedürfnisse angepasst werden:

- **Java Version ändern:** Ändere `java-version: '21'` in `java-version: '17'` (falls nötig)
- **SDK Version ändern:** Ändere `sdk-version: '35'` in die gewünschte Version
- **Build-Typ ändern:** Ersetze `assembleRelease` mit `assembleDebug` für Debug-Builds

### Neue Workflows hinzufügen
Erstelle eine neue `.yml`-Datei im `.github/workflows/`-Verzeichnis.

---

## ❓ Häufige Fragen

### Warum wird der Release-Workflow nicht ausgelöst?
- **Lösung:** Stelle sicher, dass das Tag mit `v` beginnt (z.B. `v1.7.0`)
- **Test:** `git tag v1.7.1 && git push origin v1.7.1`

### Warum scheitert der Build?
- **Häufige Ursachen:**
  - Fehlende Abhängigkeiten in `libs.versions.toml`
  - Falsche Java-Version
  - Falsche SDK-Version
- **Lösung:** Prüfe die Build-Logs in GitHub Actions

### Wie kann ich die APKs installieren?
```bash
# Auf dem Smartphone (ADB)
adb install mobile-release.apk

# Auf der Wear OS Uhr (ADB über Bluetooth)
adb -s <wear_device_id> install wear-release.apk
```

### Wie finde ich die Wear Device ID?
```bash
# Verbundene Geräte anzeigen
adb devices

# Wear OS Geräte anzeigen ( Bluetooth muss aktiviert sein)
adb devices | grep -v "List of devices"
```

---

## 📚 Nützliche Links

- [GitHub Actions Dokumentation](https://docs.github.com/en/actions)
- [Android Actions](https://github.com/android-actions)
- [Setup Android SDK](https://github.com/android-actions/setup-android)
- [Gradle Build Action](https://github.com/gradle/gradle-build-action)
- [Softprops Release Action](https://github.com/softprops/action-gh-release)

---

## 💡 Tipps

1. **Cache nutzen:** Die Workflows nutzen Gradle-Cache für schnellere Builds
2. **Artifacts speichern:** APKs werden für 7 Tage gespeichert
3. **Manuelle Trigger:** Debug-Builds können manuell ausgelöst werden
4. **Automatische Releases:** Tags mit `v*` lösen automatisch Release-Builds aus

---

**Letzte Aktualisierung:** 2024-10-08  
**Verantwortlich:** Vibe Code (Mistral AI)
