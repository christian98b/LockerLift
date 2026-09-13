# LockerLift – User Stories & Akzeptanzkriterien

## Epic 1: Globaler Maschinenkatalog (Equipment Catalog)

### US 1.1: Maschinen im globalen Katalog erfassen
* **Als Trainierender** möchte ich Maschinen und Übungen als eigenständige Datensätze anlegen, **damit** ich diese geräteübergreifend in beliebigen Workouts wiederverwenden kann.
* **Akzeptanzkriterien:**
  * **AK 1.1.1:** Eine Maschine kann mit den Attributen `Name` (Pflicht), `Zielmuskelgruppe` (Pflicht), `Standard-Gewichtsschritte` (z. B. 2,5 kg / 5,0 kg) und optionalen `Geräteeinstellungen` (z. B. Sitzhöhe Stufe 3) angelegt werden.
  * **AK 1.1.2:** Das Anlegen ist sowohl in der Smartphone-App als auch direkt auf der Wear OS Smartwatch möglich.
  * **AK 1.1.3:** Lokale Duplikate werden anhand des Namens erkannt und verhindert.

### US 1.2: Geräteeinstellungen einsehen und aktualisieren
* **Als Trainierender** möchte ich an der Maschine meine individuellen Setup-Parameter (z. B. Bolzenposition, Sitzrastung) ablesen und anpassen können, **damit** ich das Gerät ohne langes Ausprobieren korrekt einstelle.
* **Akzeptanzkriterien:**
  * **AK 1.2.1:** Bei Anwahl einer Maschine im Workout werden hinterlegte Notizen zu den Einstellungen prominent im UI dargestellt.
  * **AK 1.2.2:** Werden die Einstellungen während einer Session geändert, kann der Nutzer wählen, ob die Notiz im globalen Katalog dauerhaft aktualisiert wird.

---

## Epic 2: Vorlagenverwaltung ($n$-Workouts)

### US 2.1: Beliebige Anzahl von Workout-Vorlagen verwalten
* **Als Trainierender** möchte ich beliebig viele ($n$) Workout-Templates (z. B. Push, Pull, Beine, Ganzkörper) erstellen, umbenennen und organisieren, **damit** ich meine Trainingsroutine flexibel strukturieren kann.
* **Akzeptanzkriterien:**
  * **AK 2.1.1:** Der Nutzer kann unbegrenzt viele Templates anlegen.
  * **AK 2.1.2:** Ein Template kann initial komplett ohne zugewiesene Maschinen abgespeichert werden (Cold-Start Template).
  * **AK 2.1.3:** Vorlagen können auf dem Smartphone per Drag-and-Drop in ihrer Übungsreihenfolge umsortiert werden.

### US 2.2: Maschinen zu Templates zuordnen
* **Als Trainierender** möchte ich bestehende Maschinen aus dem globalen Katalog einem Template hinzufügen oder entfernen, **damit** meine Standardabläufe vorab definiert sind.
* **Akzeptanzkriterien:**
  * **AK 2.2.1:** Eine Maschine kann mehreren Templates zugeordnet werden (n:m-Beziehung via CrossRef).
  * **AK 2.2.2:** Das Löschen einer Maschine aus einem Template belässt die Maschine im globalen Katalog und verändert keine historischen Trainingsdaten.

---

## Epic 3: Standalone Wear OS Tracking (Smartwatch im Spind-Szenario)

### US 3.1: Autarkes Workout ohne Smartphone-Verbindung starten
* **Als Trainierender** möchte ich ein Workout direkt auf meiner Smartwatch starten können, während mein Smartphone im Spind außer Funkreichweite liegt, **damit** ich ohne Ballast trainieren kann.
* **Akzeptanzkriterien:**
  * **AK 3.1.1:** Die Wear OS App benötigt beim Start weder Bluetooth-, WLAN- noch Mobilfunkverbindung zum Smartphone.
  * **AK 3.1.2:** Zur Auswahl stehen alle synchronisierten Templates sowie die Option „Freies Training“.
  * **AK 3.1.3:** Das Workout läuft in einem `Ongoing Activity Foreground Service`, um Terminierung durch das Betriebssystem bei Displayabschaltung zu verhindern.

### US 3.2: Cold-Start-Erfassung (Dynamisches Befüllen beim ersten Mal)
* **Als Trainierender** möchte ich ein leeres Workout starten und von Station zu Station gehend Maschinen ad-hoc hinzufügen, **damit** ich mein Training im Studio ohne Vorbereitung direkt aufbauen kann.
* **Akzeptanzkriterien:**
  * **AK 3.2.1:** In der aktiven Trainingsansicht der Uhr ist jederzeit die Aktion „+ Maschine hinzufügen“ verfügbar.
  * **AK 3.2.2:** Der Nutzer kann eine Maschine aus dem Katalog wählen oder ad-hoc per Spracheingabe / Tastatur neu anlegen.
  * **AK 3.2.3:** Neu hinzugefügte Maschinen reihen sich am Ende der aktuellen Session ein.

### US 3.3: Flexibles Überspringen oder Austauschen belegter Maschinen
* **Als Trainierender** möchte ich während des Workouts Maschinen überspringen, entfernen oder austauschen können, **damit** ich flexibel auf belegte Geräte reagieren kann.
* **Akzeptanzkriterien:**
  * **AK 3.3.1:** Eine geplante Maschine kann per Wischgeste oder Menüpunkt übersprungen werden (Status: `is_skipped = true`).
  * **AK 3.3.2:** Eine geplante Maschine kann durch eine Alternativübung aus dem Katalog ersetzt werden.
  * **AK 3.3.3:** Diese Änderungen betreffen zunächst ausschließlich die laufende Trainingsinstanz (`WorkoutSession`).

### US 3.4: Erfassung von Gewicht, Wiederholungen und Kadenz
* **Als Trainierender** möchte ich nach jedem Satz das bewegte Gewicht, die Wiederholungszahl und optional das Ausführungstempo erfassen, **damit** meine Trainingsdaten vollständig erfasst werden.
* **Akzeptanzkriterien:**
  * **AK 3.4.1:** Schnelleingabe via Touch-Buttons oder drehbarer Lünette (Rotary Input).
  * **AK 3.4.2:** Werte des vorherigen Satzes bzw. des letzten Trainings werden als Startwerte vorbelegt.
  * **AK 3.4.3:** Es kann eine Kadenz im Format `Exzentrik-Halten-Konzentrik-Pause` (z. B. `3-1-1-0`) hinterlegt oder bestätigt werden.
  * **AK 3.4.4:** Nach Bestätigung eines Satzes startet automatisch ein konfigurierbarer Pausentimer mit haptischem Vibrationssignal bei Ablauf.

### US 3.5: Template-Konsolidierung beim Workout-Abschluss
* **Als Trainierender** möchte ich beim Beenden des Workouts entscheiden können, ob vorgenommene Änderungen an den Maschinen in die Vorlage übernommen werden, **damit** zukünftige Einheiten automatisch angepasst sind.
* **Akzeptanzkriterien:**
  * **AK 3.5.1:** Weicht die Session-Maschinenliste von der ursprünglichen Vorlage ab, erscheint der Dialog: *„Änderungen als Standard für '[Template-Name]' speichern?“*.
  * **AK 3.5.2:** Bei Bestätigung („Ja“) wird die Vorlagenkonfiguration überschrieben.
  * **AK 3.5.3:** Bei Ablehnung („Nein“) bleibt die Vorlage unangetastet; die Trainingshistorie speichert exakt die tatsächlich ausgeführte Session.

---

## Epic 4: Progression & Trainingshistorie

### US 4.1: Historische Referenzdaten an der Maschine anzeigen
* **Als Trainierender** möchte ich an der Maschine direkt sehen, welche Leistung ich im letzten Training erbracht habe, **damit** ich das Startgewicht sofort wählen kann.
* **Akzeptanzkriterien:**
  * **AK 4.1.1:** Bei Auswahl einer Maschine werden die Werte der letzten absolvierten Einheit eingeblendet (z. B. *„Letztes Mal: 80 kg × 10, 80 kg × 9, 80 kg × 8“*).
  * **AK 4.1.2:** Die Daten werden aus der lokalen Datenbank der Uhr bezogen (kein Netzwerk-Call notwendig).

### US 4.2: Progressionsvorschläge (Next Steps)
* **Als Trainierender** möchte ich dynamische Vorschläge für den nächsten Satz erhalten, **damit** ich Progressive Overload systematisch umsetzen kann.
* **Akzeptanzkriterien:**
  * **AK 4.2.1:** Basierend auf konfigurierter Logik (z. B. Double Progression: Wiederholungskorridor 8–12) schlägt das System bei Erreichen der Maximalwiederholungen eine Gewichtserhöhung um das kleinstmögliche Inkrement vor.
  * **AK 4.2.2:** Der Vorschlag wird im Eingabedialog visuell hervorgehoben und kann mit einem Tap übernommen werden.

---

## Epic 5: Offline-First Synchronisation (Wearable Data Layer)

### US 5.1: Automatische Synchronisation bei Wiederverbindung
* **Als Nutzer** möchte ich, dass meine Uhr die Daten automatisch an mein Smartphone sendet, sobald ich nach dem Training wieder am Spind bin, **damit** ich keine manuellen Exporte durchführen muss.
* **Akzeptanzkriterien:**
  * **AK 5.1.1:** Bei Abschluss des Workouts auf der Uhr wird die Einheit in einer lokalen Sync-Queue persistiert.
  * **AK 5.1.2:** Sobald die Bluetooth- oder WLAN-Verbindung zum Smartphone wiederhergestellt ist, initiiert der Hintergrunddienst die Übertragung via `Wearable.getChannelClient()`.
  * **AK 5.1.3:** Die Übertragung gilt erst als abgeschlossen, wenn das Smartphone den Empfang quittiert hat (Zwei-Wege-Handshake).

### US 5.2: Konfliktfreie Datenreplikation von Stammdaten
* **Als Nutzer** möchte ich Maschinen und Vorlagen auf dem Smartphone pflegen und automatisch auf der Uhr verfügbar haben, **damit** beide Geräte denselben Datenstand haben.
* **Akzeptanzkriterien:**
  * **AK 5.2.1:** Änderungen an Templates auf dem Smartphone werden über den `DataClient` synchronisiert.
  * **AK 5.2.2:** Für den Fall gleichzeitiger Änderungen gilt: Das Smartphone ist Master für Template- und Maschinendefinitionen; die Smartwatch ist Master für aktive Session- und Satzdaten.

---

## Epic 6: Health Connect Integration

### US 6.1: Workout an Health Connect übergeben
* **Als gesundheitsbewusster Nutzer** möchte ich meine Krafttrainingseinheiten in Android Health Connect sehen, **damit** sie in Samsung Health oder Google Fit aggregiert werden.
* **Akzeptanzkriterien:**
  * **AK 6.1.1:** Nach erfolgreichem Empfang und Persistieren einer Session auf dem Smartphone schreibt die App einen `ExerciseSessionRecord` mit `EXERCISE_TYPE_STRENGTH_TRAINING`.
  * **AK 6.1.2:** Der Datensatz beinhaltet Start- und Endzeitpunkt sowie Metadaten (Workout-Titel).
  * **AK 6.1.3:** Sofern durch die Smartwatch erfasst, werden verbrannte Kalorien (`TotalCaloriesBurnedRecord`) und Herzfrequenzdaten (`HeartRateRecord`) verknüpft angehängt.

### US 6.2: Granulare Berechtigungsverwaltung
* **Als Nutzer** möchte ich steuern können, welche Daten an Health Connect weitergegeben werden, **damit** meine Privatsphäre gewahrt bleibt.
* **Akzeptanzkriterien:**
  * **AK 6.2.1:** Die App prüft vor dem Schreibvorgang standardkonform die Berechtigungen (`HealthConnectClient.permissionController.getGrantedPermissions()`).
  * **AK 6.2.2:** Werden Berechtigungen verweigert, schlägt der Export lautlos fehl bzw. loggt einen Statuscode; die Kernfunktionalität der App bleibt vollständig uneingeschränkt nutzbar.
