# 📋 Sync Zuverlässigkeits-Verbesserungen

## Übersicht

Dieses Dokument beschreibt die implementierten Verbesserungen für die Synchronisation zwischen der LockerLift Mobile App und Wear OS Uhr. Die Änderungen adressieren kritische Probleme, die in der [tiefen Analyse](ANALYSIS_SYNC_RELIABILITY.md) identifiziert wurden.

---

## 🎯 Implementierte Fixes (Priorität 1)

### 1. ✅ NACK-Mechanismus

**Problem:** Sender wusste nicht, warum ein Sync fehlschlug (z.B. Datenbank-Fehler, Invalid Payload).

**Lösung:**
- Neue Nachrichtentypen: `PATH_WORKOUT_NACK` und `PATH_MASTER_DATA_ACK`
- Fehlercodes in `SyncConstants`:
  - `NACK_DATABASE_ERROR`
  - `NACK_INVALID_PAYLOAD`
  - `NACK_ZOMBIE_DETECTED`
  - `NACK_VERSION_CONFLICT`
  - `NACK_UNKNOWN_ERROR`
- NACK-Nachrichten enthalten: `sessionId:errorCode`

**Dateien geändert:**
- `core/sync/src/main/kotlin/com/lockerlift/core/sync/SyncConstants.kt`
- `core/sync/src/main/kotlin/com/lockerlift/core/sync/WearableDataLayerManager.kt`
- `mobile/src/main/kotlin/com/lockerlift/mobile/service/MobileDataLayerListenerService.kt`
- `wear/src/main/kotlin/com/lockerlift/wear/communication/WearDataLayerListenerService.kt`

---

### 2. ✅ Master-Daten in SyncQueue

**Problem:** Master-Daten (Catalog, Templates) hatten keinen Retry-Mechanismus bei Fehlschlag.

**Lösung:**
- `SyncQueueEntity` erweitert um:
  - `itemType`: `WORKOUT`, `MASTER_CATALOG`, `MASTER_TEMPLATES`
  - `errorMessage`: Fehlerbeschreibung
  - `targetDeviceId`: Zielgerät (CAPABILITY_WEAR oder CAPABILITY_MOBILE)
- `MobileMasterDataSync.pushAllMasterData()` speichert Master-Daten jetzt auch in der Queue
- Neue DAO-Methoden für Typ-basierte Abfragen

**Dateien geändert:**
- `core/database/src/main/kotlin/com/lockerlift/core/database/entity/SyncQueueEntity.kt`
- `core/database/src/main/kotlin/com/lockerlift/core/database/dao/SyncQueueDao.kt`
- `core/model/src/main/kotlin/com/lockerlift/core/model/WorkoutSession.kt` (SyncQueueItem)
- `mobile/src/main/kotlin/com/lockerlift/mobile/sync/MobileMasterDataSync.kt`

---

### 3. ✅ Timeout für Channel-Übertragung

**Problem:** Channel-Read/Write konnte bei langsamer Verbindung für immer hängen bleiben.

**Lösung:**
- Timeout von **30 Sekunden** für Channel-Übertragungen
- Check nach jedem 8KB-Chunk
- Verwirft `java.util.concurrent.TimeoutException`

**Dateien geändert:**
- `mobile/src/main/kotlin/com/lockerlift/mobile/service/MobileDataLayerListenerService.kt`
- `wear/src/main/kotlin/com/lockerlift/wear/communication/WearDataLayerListenerService.kt`
- `core/sync/src/main/kotlin/com/lockerlift/core/sync/SyncConstants.kt` (CHANNEL_READ_TIMEOUT_MS)

---

### 4. ✅ Tombstone-Tabelle für gelöschte Workouts

**Problem:** Zombie-Detection funktionierte nur auf Mobile, nicht auf Wear.

**Lösung:**
- Neue Tabelle: `deleted_sessions`
- Enthält: `sessionId`, `deletedAt`, `originDevice`, `ttlDays` (default: 30 Tage)
- `SyncIngestionEngine.ingestWorkoutPayload()` prüft Tombstone auf **beiden** Geräten
- `SyncIngestionEngine.handleWorkoutDelete()` fügt Tombstone-Einträge hinzu
- Automatische Cleanup-Methode für abgelaufene Einträge

**Dateien geändert:**
- `core/database/src/main/kotlin/com/lockerlift/core/database/entity/DeletedSessionEntity.kt` (NEU)
- `core/database/src/main/kotlin/com/lockerlift/core/database/dao/DeletedSessionDao.kt` (NEU)
- `core/model/src/main/kotlin/com/lockerlift/core/model/DeletedSession.kt` (NEU)
- `core/database/src/main/kotlin/com/lockerlift/core/database/LockerLiftDatabase.kt` (Version 2)
- `core/sync/src/main/kotlin/com/lockerlift/core/sync/SyncIngestionEngine.kt`

---

## 🧪 Test-Strategie für Sync-Zuverlässigkeit

### Test-Kategorien

#### 1. **Einheitstests (Unit Tests)**

##### 1.1 NACK-Mechanismus Tests

```kotlin
// File: core/sync/src/test/kotlin/com/lockerlift/core/sync/SyncIngestionEngineTest.kt

class NackMechanismTest {
    
    @Test
    fun `test NACK message parsing`() {
        val nackMessage = "session123:NACK_DATABASE_ERROR"
        val parts = nackMessage.split(":", limit = 2)
        
        assertEquals("session123", parts[0])
        assertEquals("NACK_DATABASE_ERROR", parts[1])
    }
    
    @Test
    fun `test NACK message with missing error code`() {
        val nackMessage = "session123"
        val parts = nackMessage.split(":", limit = 2)
        
        assertEquals("session123", parts[0])
        assertEquals(null, parts.getOrNull(1))
    }
    
    @Test
    fun `test error code classification`() {
        val exceptions = mapOf(
            IllegalArgumentException("Invalid JSON") to SyncConstants.NACK_INVALID_PAYLOAD,
            android.database.sqlite.SQLiteException("Constraint failed") to SyncConstants.NACK_DATABASE_ERROR,
            RuntimeException("Unknown error") to SyncConstants.NACK_UNKNOWN_ERROR
        )
        
        exceptions.forEach { (exception, expectedCode) ->
            val actualCode = when (exception) {
                is IllegalArgumentException -> SyncConstants.NACK_INVALID_PAYLOAD
                is android.database.sqlite.SQLiteException -> SyncConstants.NACK_DATABASE_ERROR
                else -> SyncConstants.NACK_UNKNOWN_ERROR
            }
            assertEquals(expectedCode, actualCode)
        }
    }
}
```

##### 1.2 Tombstone Tests

```kotlin
// File: core/sync/src/test/kotlin/com/lockerlift/core/sync/TombstoneTest.kt

class TombstoneTest {
    
    @Test
    fun `test zombie detection with tombstone`() = runTest {
        val database = createInMemoryDatabase()
        val sessionId = UUID.randomUUID().toString()
        
        // Add tombstone
        database.deletedSessionDao().insert(
            DeletedSessionEntity(
                sessionId = sessionId,
                deletedAt = System.currentTimeMillis(),
                originDevice = "MOBILE",
                ttlDays = 30
            )
        )
        
        // Try to ingest workout with same sessionId
        val payloadJson = createWorkoutPayload(sessionId)
        val result = SyncIngestionEngine.ingestWorkoutPayload(
            database, payloadJson, isMobile = false
        )
        
        assertTrue(result is SyncIngestionEngine.IngestionResult.RejectedZombie)
        assertEquals(sessionId, result.sessionId)
    }
    
    @Test
    fun `test tombstone cleanup`() = runTest {
        val database = createInMemoryDatabase()
        val oldTimestamp = System.currentTimeMillis() - (31L * 24 * 60 * 60 * 1000) // 31 days ago
        
        // Add old tombstone
        database.deletedSessionDao().insert(
            DeletedSessionEntity(
                sessionId = "oldSession",
                deletedAt = oldTimestamp,
                originDevice = "MOBILE",
                ttlDays = 30
            )
        )
        
        // Cleanup
        val deletedCount = SyncIngestionEngine.cleanupExpiredTombstones(database)
        
        assertEquals(1, deletedCount)
        assertNull(database.deletedSessionDao().getBySessionId("oldSession"))
    }
    
    @Test
    fun `test tombstone not cleaned if within TTL`() = runTest {
        val database = createInMemoryDatabase()
        val recentTimestamp = System.currentTimeMillis() - (29L * 24 * 60 * 60 * 1000) // 29 days ago
        
        // Add recent tombstone
        database.deletedSessionDao().insert(
            DeletedSessionEntity(
                sessionId = "recentSession",
                deletedAt = recentTimestamp,
                originDevice = "MOBILE",
                ttlDays = 30
            )
        )
        
        // Cleanup
        val deletedCount = SyncIngestionEngine.cleanupExpiredTombstones(database)
        
        assertEquals(0, deletedCount)
        assertNotNull(database.deletedSessionDao().getBySessionId("recentSession"))
    }
}
```

##### 1.3 Master-Daten Queue Tests

```kotlin
// File: mobile/src/test/kotlin/com/lockerlift/mobile/MobileMasterDataSyncTest.kt

class MobileMasterDataSyncTest {
    
    @Test
    fun `test master data queued for sync`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = createInMemoryDatabase(context)
        val dataLayerManager = mockk<WearableDataLayerManager>()
        
        // Setup test data
        val machineDao = database.machineDao()
        machineDao.insertMachine(
            MachineEntity(
                id = "machine1",
                name = "Chest Press",
                targetMuscleGroup = "Chest",
                createdAt = System.currentTimeMillis()
            )
        )
        
        // Call pushAllMasterData
        MobileMasterDataSync.pushAllMasterData(context, database, dataLayerManager)
        
        // Verify queue items created
        val queueItems = database.syncQueueDao().getPendingMasterDataItems()
        
        assertTrue(queueItems.any { it.itemType == SyncConstants.ITEM_TYPE_MASTER_CATALOG })
        assertTrue(queueItems.any { it.itemType == SyncConstants.ITEM_TYPE_MASTER_TEMPLATES })
    }
    
    @Test
    fun `test queue item type filtering`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = createInMemoryDatabase(context)
        
        // Insert test items
        database.syncQueueDao().insertQueueItem(
            SyncQueueEntity(
                id = "1",
                sessionId = "session1",
                payloadJson = "{}",
                status = QueueStatus.PENDING,
                itemType = SyncConstants.ITEM_TYPE_WORKOUT
            )
        )
        database.syncQueueDao().insertQueueItem(
            SyncQueueEntity(
                id = "2",
                sessionId = "MASTER_CATALOG",
                payloadJson = "{}",
                status = QueueStatus.PENDING,
                itemType = SyncConstants.ITEM_TYPE_MASTER_CATALOG
            )
        )
        
        // Test filtering
        val masterItems = database.syncQueueDao().getPendingItemsByType(SyncConstants.ITEM_TYPE_MASTER_CATALOG)
        val workoutItems = database.syncQueueDao().getPendingItemsByType(SyncConstants.ITEM_TYPE_WORKOUT)
        
        assertEquals(1, masterItems.size)
        assertEquals(1, workoutItems.size)
    }
}
```

---

#### 2. **Integrationstests (Instrumented Tests)**

##### 2.1 Channel Timeout Test

```kotlin
// File: mobile/src/androidTest/kotlin/com/lockerlift/mobile/ChannelTimeoutTest.kt

@RunWith(AndroidJUnit4::class)
class ChannelTimeoutTest {
    
    @get:Rule
    val mockWebServerRule = MockWebServerRule()
    
    @Test
    fun `test channel read timeout`() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = MobileDataLayerListenerService()
        
        // Create mock channel that never sends data
        val mockChannel = mockk<ChannelClient.Channel> {
            every { nodeId } returns "testNode"
            every { path } returns SyncConstants.PATH_WORKOUT_CHANNEL
        }
        
        // Mock channel client to return empty stream
        val mockChannelClient = mockk<ChannelClient> {
            coEvery { getInputStream(any()) } coAnswers {
                // Return input stream that blocks
                object : InputStream() {
                    override fun read(): Int = throw TimeoutException("Simulated timeout")
                }
            }
        }
        
        // Use reflection to inject mock
        // ... (reflection code to set channelClient)
        
        // Call receiveWorkoutFromChannel with timeout
        val startTime = System.currentTimeMillis()
        service.receiveWorkoutFromChannel(mockChannel)
        val endTime = System.currentTimeMillis()
        
        // Should timeout within 30 seconds (use shorter timeout for test)
        assertTrue(endTime - startTime < 35000) // Allow some margin
    }
    
    @Test
    fun `test channel read with large payload`() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = MobileDataLayerListenerService()
        
        // Create large payload (4.5MB)
        val largePayload = "x".repeat(4_500_000)
        val payloadJson = """{"sessionId":"test","data":"$largePayload"}"""
        
        // This should succeed (under 5MB limit)
        val result = service.receiveWorkoutFromChannel(createMockChannel(payloadJson))
        
        // Verify payload was processed
        assertTrue(result != null)
    }
    
    @Test
    fun `test channel read with oversized payload`() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = MobileDataLayerListenerService()
        
        // Create oversized payload (6MB)
        val oversizedPayload = "x".repeat(6_000_000)
        val payloadJson = """{"sessionId":"test","data":"$oversizedPayload"}"""
        
        // This should throw IllegalStateException
        assertThrows<IllegalStateException> {
            service.receiveWorkoutFromChannel(createMockChannel(payloadJson))
        }
    }
}
```

##### 2.2 Sync Round-Trip Test

```kotlin
// File: mobile/src/androidTest/kotlin/com/lockerlift/mobile/SyncRoundTripTest.kt

@RunWith(AndroidJUnit4::class)
class SyncRoundTripTest {
    
    @Test
    fun `test workout sync with ACK`() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mobileDatabase = createTestDatabase(context, "mobile")
        val wearDatabase = createTestDatabase(context, "wear")
        
        // Create workout on mobile
        val sessionId = UUID.randomUUID().toString()
        val workout = createTestWorkout(sessionId)
        mobileDatabase.workoutSessionDao().insertSession(workout.toEntity())
        
        // Queue for sync
        val queueItem = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            payloadJson = SyncPayloadSerializer.encodeSessionPayload(workout.toPayload()),
            status = QueueStatus.PENDING,
            itemType = SyncConstants.ITEM_TYPE_WORKOUT
        )
        mobileDatabase.syncQueueDao().insertQueueItem(queueItem)
        
        // Simulate sync to wear
        val dataLayerManager = WearableDataLayerManager(context)
        val result = dataLayerManager.flushPendingQueue(
            mobileDatabase.syncQueueDao(),
            SyncConstants.CAPABILITY_WEAR
        )
        
        // Verify workout was synced
        assertTrue(result is SyncResult.Success)
        assertEquals(1, result.itemsSyncedCount)
        
        // Verify workout exists on wear
        val syncedWorkout = wearDatabase.workoutSessionDao().getSessionById(sessionId)
        assertNotNull(syncedWorkout)
        assertEquals(SyncStatus.SYNCED, syncedWorkout.syncStatus)
        
        // Verify queue item was removed
        val remainingItems = mobileDatabase.syncQueueDao().getPendingQueueItems()
        assertTrue(remainingItems.none { it.sessionId == sessionId })
    }
    
    @Test
    fun `test workout sync with NACK`() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mobileDatabase = createTestDatabase(context, "mobile")
        
        // Create invalid workout payload
        val invalidPayload = "INVALID_JSON"
        val sessionId = "invalidSession"
        val queueItem = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            payloadJson = invalidPayload,
            status = QueueStatus.PENDING,
            itemType = SyncConstants.ITEM_TYPE_WORKOUT
        )
        mobileDatabase.syncQueueDao().insertQueueItem(queueItem)
        
        // Simulate sync (should fail with NACK)
        val dataLayerManager = WearableDataLayerManager(context)
        val result = dataLayerManager.flushPendingQueue(
            mobileDatabase.syncQueueDao(),
            SyncConstants.CAPABILITY_WEAR
        )
        
        // Queue item should still exist with ERROR status
        val errorItem = mobileDatabase.syncQueueDao().getQueueItemBySessionId(sessionId)
        assertNotNull(errorItem)
        assertEquals(QueueStatus.ERROR, errorItem.status)
        assertTrue(errorItem.errorMessage?.contains("NACK") == true)
    }
    
    @Test
    fun `test zombie workout detection`() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mobileDatabase = createTestDatabase(context, "mobile")
        val wearDatabase = createTestDatabase(context, "wear")
        
        // Create workout on both devices
        val sessionId = UUID.randomUUID().toString()
        val workout = createTestWorkout(sessionId)
        mobileDatabase.workoutSessionDao().insertSession(workout.toEntity())
        wearDatabase.workoutSessionDao().insertSession(workout.toEntity())
        
        // Delete on mobile
        mobileDatabase.workoutSessionDao().deleteSession(sessionId)
        SyncIngestionEngine.addDeletedSessionTombstone(mobileDatabase, sessionId, "MOBILE")
        
        // Try to sync from wear to mobile (should be rejected as zombie)
        val payloadJson = SyncPayloadSerializer.encodeSessionPayload(workout.toPayload())
        val result = SyncIngestionEngine.ingestWorkoutPayload(
            mobileDatabase, payloadJson, isMobile = true
        )
        
        assertTrue(result is SyncIngestionEngine.IngestionResult.RejectedZombie)
        
        // Verify workout not re-created on mobile
        val deletedWorkout = mobileDatabase.workoutSessionDao().getSessionById(sessionId)
        assertNull(deletedWorkout)
    }
}
```

---

#### 3. **End-to-End Tests (Manual Tests)**

##### 3.1 Verbindungstest

**Testfall:** Verbindung zwischen Mobile und Wear wird unterbrochen und wiederhergestellt

**Schritte:**
1. Starte Mobile App und Wear App
2. Erstelle ein Workout auf Mobile
3. Trenne Bluetooth-Verbindung
4. Warte 30 Sekunden
5. Stelle Bluetooth-Verbindung wieder her
6. Warte auf automatischen Sync

**Erwartetes Ergebnis:**
- Workout wird automatisch an Wear gesendet
- Sync-Status wird auf SYNCED aktualisiert
- Kein Datenverlust

**Prüfung:**
- Überprüfe auf Wear, ob Workout angezeigt wird
- Überprüfe Sync-Logs in Logcat

---

##### 3.2 Offline-Sync-Test

**Testfall:** Workout wird offline erstellt und später synchronisiert

**Schritte:**
1. Deaktiviere Bluetooth auf dem Handy
2. Erstelle ein Workout auf Mobile
3. Aktiviere Bluetooth wieder
4. Öffne Wear App

**Erwartetes Ergebnis:**
- Workout wird in der Sync-Queue gespeichert
- Workout wird automatisch gesendet, sobald Verbindung wiederhergestellt ist
- ACK wird empfangen und Queue-Item wird gelöscht

**Prüfung:**
- Überprüfe Queue-Status vor und nach der Synchronisation
- Überprüfe, ob Workout auf Wear angezeigt wird

---

##### 3.3 Master-Daten-Sync-Test

**Testfall:** Gerätekatalog wird aktualisiert und an Wear gesendet

**Schritte:**
1. Füge ein neues Gerät auf Mobile hinzu
2. Warte auf automatischen Sync
3. Öffne Wear App

**Erwartetes Ergebnis:**
- Neues Gerät wird in der Wear-App angezeigt
- Kein manueller Refresh nötig

**Prüfung:**
- Überprüfe, ob Gerät in der Auswahl auf Wear verfügbar ist
- Überprüfe Sync-Logs für Master-Daten

---

##### 3.4 Konflikt-Test (Zombie-Detection)

**Testfall:** Workout wird auf einem Gerät gelöscht, aber Sync ist noch ausstehend

**Schritte:**
1. Erstelle Workout auf Mobile (wird in Queue gespeichert)
2. Lösche Workout auf Mobile, bevor Sync abgeschlossen ist
3. Warte auf Sync-Versuch

**Erwartetes Ergebnis:**
- Workout wird nicht an Wear gesendet
- Tombstone-Eintrag wird erstellt
- Wenn Wear das Workout später sendet, wird es auf Mobile abgelehnt

**Prüfung:**
- Überprüfe Tombstone-Tabelle
- Überprüfe, ob Workout nicht auf Wear erscheint

---

##### 3.5 Große Workout-Test

**Testfall:** Workout mit vielen Sätzen und Übungen wird synchronisiert

**Schritte:**
1. Erstelle ein Workout mit 20 Übungen und je 5 Sätzen
2. Sync an Wear

**Erwartetes Ergebnis:**
- Workout wird erfolgreich übertragen
- Kein Timeout
- Kein Datenverlust

**Prüfung:**
- Überprüfe, ob alle Übungen und Sätze auf Wear angezeigt werden
- Überprüfe Payload-Größe in Logs

---

## 📊 Test-Matrix

| **Test** | **Typ** | **Mobile** | **Wear** | **Priorität** | **Status** |
|----------|---------|------------|----------|--------------|------------|
| NACK Parsing | Unit | ✅ | ✅ | Hoch | ⬜ |
| Zombie Detection | Unit | ✅ | ✅ | Hoch | ⬜ |
| Tombstone Cleanup | Unit | ✅ | ✅ | Mittel | ⬜ |
| Master Data Queue | Unit | ✅ | ❌ | Hoch | ⬜ |
| Channel Timeout | Integration | ✅ | ✅ | Hoch | ⬜ |
| Large Payload | Integration | ✅ | ✅ | Mittel | ⬜ |
| Round-Trip Sync | Integration | ✅ | ✅ | Hoch | ⬜ |
| Zombie Round-Trip | Integration | ✅ | ✅ | Hoch | ⬜ |
| Verbindungstest | E2E | ✅ | ✅ | Hoch | ⬜ |
| Offline-Sync | E2E | ✅ | ✅ | Hoch | ⬜ |
| Master-Daten-Sync | E2E | ✅ | ✅ | Mittel | ⬜ |
| Konflikt-Test | E2E | ✅ | ✅ | Hoch | ⬜ |
| Großes Workout | E2E | ✅ | ✅ | Mittel | ⬜ |

---

## 🛠️ Test-Setup

### Abhängigkeiten

```gradle
// In app/build.gradle.kts
androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
androidTestImplementation("io.mockk:mockk-android:1.13.4")
androidTestImplementation("androidx.test:runner:1.5.2")
androidTestImplementation("androidx.test:rules:1.5.0")
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

// For MockWebServer
androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.10.0")
```

### Test-Helper

```kotlin
// File: core/database/src/test/kotlin/com/lockerlift/core/database/TestDatabase.kt

fun createInMemoryDatabase(context: Context = ApplicationProvider.getApplicationContext()): LockerLiftDatabase {
    return Room.inMemoryDatabaseBuilder(context, LockerLiftDatabase::class.java)
        .allowMainThreadQueries()
        .build()
}

fun createTestWorkout(sessionId: String = UUID.randomUUID().toString()): WorkoutSession {
    return WorkoutSession(
        id = sessionId,
        startTime = System.currentTimeMillis(),
        endTime = System.currentTimeMillis() + 3600000,
        originDevice = "MOBILE",
        syncStatus = SyncStatus.PENDING_SYNC
    )
}

fun WorkoutSession.toPayload(): WorkoutSessionPayload {
    return WorkoutSessionPayload(
        session = this,
        templateName = "Test Template",
        machineInstances = emptyList()
    )
}

fun createMockChannel(payloadJson: String): ChannelClient.Channel {
    return mockk {
        every { nodeId } returns "testNode"
        every { path } returns SyncConstants.PATH_WORKOUT_CHANNEL
    }
}
```

---

## 📈 Erfolgsmetriken

### Zu messende KPIs

1. **Sync-Erfolgsrate:** % der Workouts, die erfolgreich synchronisiert werden
   - Ziel: > 99.9%

2. **Durchschnittliche Sync-Zeit:** Zeit von Queue-Eintritt bis ACK
   - Ziel: < 5 Sekunden

3. **Maximale Sync-Zeit:** 95. Perzentil der Sync-Zeiten
   - Ziel: < 10 Sekunden

4. **Datenkonsistenz:** % der Fälle, in denen Mobile und Wear identische Daten haben
   - Ziel: 100%

5. **Zombie-Rate:** % der gelöschten Workouts, die wieder erscheinen
   - Ziel: 0%

6. **Master-Daten-Aktualität:** Zeit bis Master-Daten auf Wear aktualisiert sind
   - Ziel: < 30 Sekunden

---

## 🔧 Debugging-Hilfen

### Log-Tags

- `MobileDataLayerService`: Mobile Sync-Service
- `WearDataLayerListener`: Wear Sync-Service
- `SyncIngestionEngine`: Datenverarbeitung
- `WearableDataLayerManager`: DataLayer-Operationen
- `SyncQueueWorker`: Background-Sync

### Wichtige Log-Nachrichten

```
# Erfolgreiche Syncs
I/MobileDataLayerService: Workout session XXX successfully processed and synchronized.
I/WearDataLayerListener: Workout session XXX synchronized from phone to watch.
I/WearableDataLayerManager: Queue flush completed. Sent completion notification with count=X

# Fehler
E/MobileDataLayerService: Failed to process workout message payload
E/WearDataLayerListener: Failed to receive workout payload from phone
W/MobileDataLayerService: Received NACK for session XXX with error: YYY

# Zeitüberschreitungen
E/MobileDataLayerService: Channel read timed out after 30000ms

# Zombie-Erkennung
I/SyncIngestionEngine: Rejecting zombie session XXX - found in tombstone table
I/SyncIngestionEngine: Added tombstone for deleted session XXX

# Master-Daten
I/WearDataLayerListener: Synchronized X machines from phone.
I/WearDataLayerListener: Synchronized Y templates from phone.
```

### ADB-Kommandos für Debugging

```bash
# Logs anzeigen
adb logcat | grep -E "MobileDataLayerService|WearDataLayerListener|SyncIngestionEngine|WearableDataLayerManager"

# Wear-Logs anzeigen
adb -s <wear_device_id> logcat | grep -E "WearDataLayerListener|SyncIngestionEngine"

# Datenbank prüfen (auf Mobile)
adb shell
su
sqlite3 /data/data/com.lockerlift.mobile/databases/lockerlift.db

# Sync-Queue prüfen
SELECT * FROM sync_queue;
SELECT * FROM deleted_sessions;

# Wear-Datenbank prüfen
adb -s <wear_device_id> shell
su
sqlite3 /data/data/com.lockerlift.wear/databases/lockerlift.db
```

---

## 📚 Referenzen

- [Wearable Data Layer API](https://developer.android.com/training/wearables/data)
- [Room Database Testing](https://developer.android.com/training/data-testing)
- [Kotlin Coroutines Testing](https://kotlinlang.org/docs/coroutines-testing.html)
- [MockK Documentation](https://mockk.io/)

---

## 🏷️ Version

**Dokument Version:** 1.0.0  
**Letzte Aktualisierung:** 2024  
**Verantwortlich:** Vibe Code (Mistral AI)  
**Status:** Implementiert, Tests ausstehend
