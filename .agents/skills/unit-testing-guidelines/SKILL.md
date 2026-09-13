---
name: unit-testing-guidelines
description: >-
  Step-by-step instructions and standards for writing unit tests in the LockerLift project.
  Use whenever writing, updating, or reviewing unit tests for domain models, Room database entities,
  data mappers, sync serializers, ViewModels, or business logic.
---

# LockerLift Unit Testing Guidelines Skill

This skill defines the testing philosophy, conventions, structure, and execution patterns for writing unit tests in LockerLift.

---

## 1. Core Principles & Mandate

1. **Strict Test Mandate:** Every new feature, logic calculation (e.g., Double Progression, cadence parsing), data model, mapper, Room TypeConverter, and serialization workflow **MUST** have 100% unit test coverage.
2. **Regression-Free Guarantee:** All existing unit tests must remain green across future changes.
3. **No Deletion Rule:** Existing tests may **only** be modified or deleted if the corresponding feature has been explicitly removed or superseded by a new specification.
4. **Fast & Deterministic:** Unit tests must be fast, isolated, and require no physical hardware or external network access.

---

## 2. Test Placement & Directory Conventions

Tests are located in `src/test/kotlin/` within the corresponding module:

```text
<module>/
└── src/
    ├── main/kotlin/com/lockerlift/...
    └── test/kotlin/com/lockerlift/...
```

### Naming Conventions
* **Test Class Name:** `<ClassUnderTest>Test.kt` (e.g., `DomainModelTest.kt`, `SyncPayloadSerializerTest.kt`, `EntityMappingTest.kt`).
* **Test Method Name:** Descriptive, expressing the scenario and expected outcome:
  * `test<Method>_<Condition>_<ExpectedResult>()` or `when<Action>_then<Expected>()`
  * Examples:
    * `testDoubleProgression_whenMaxRepsReached_suggestsWeightIncrease()`
    * `testSyncSerializer_whenEncodingFullSession_roundTripsLosslessly()`
    * `testEntityMapper_whenConvertingMachine_preservesAllFields()`

---

## 3. Test Structure: The AAA Pattern

Every unit test must follow the **Arrange-Act-Assert (AAA)** pattern:

```kotlin
@Test
fun testMachineDefaults_whenCreated_hasDefaultIncrement() {
    // 1. Arrange
    val name = "Beinpresse"
    val muscleGroup = "Beine"

    // 2. Act
    val machine = Machine(name = name, targetMuscleGroup = muscleGroup)

    // 3. Assert
    assertEquals("Beinpresse", machine.name)
    assertEquals("Beine", machine.targetMuscleGroup)
    assertEquals(2.5f, machine.defaultIncrementKg, 0.001f)
    assertNotNull(machine.id)
}
```

---

## 4. Testing Patterns by Module

### 4.1 Domain Models (`:core:model`)
* Test default parameters (e.g., default `incrementKg = 2.5f`, timestamps).
* Test UUID generation (verify uniqueness across multiple instances).
* Test JSON serialization and deserialization via `kotlinx.serialization.json.Json`.

```kotlin
@Test
fun testSerialization() {
    val model = Machine(name = "Kabelzug", targetMuscleGroup = "Rücken")
    val json = Json.encodeToString(model)
    val decoded = Json.decodeFromString<Machine>(json)
    assertEquals(model, decoded)
}
```

### 4.2 Room Database & Mappers (`:core:database`)
* **Entity Mappers:** Test bidirectionally (`toEntity()` and `toDomainModel()`) to ensure no fields are lost or silently set to null.
* **TypeConverters:** Test both serialization and deserialization for each enum value (e.g., `SetType`, `SyncStatus`, `QueueStatus`), including fallback behavior on unknown input.
* **DAO Unit Testing (In-Memory Room):**
  When testing Room DAOs with Robolectric or in-memory DB:
  ```kotlin
  val db = Room.inMemoryDatabaseBuilder(context, LockerLiftDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  ```

### 4.3 Sync & Data Layer (`:core:sync`)
* Test serialization round-trip for `WorkoutSessionPayload`.
* Test deep nested structures: verify that machines, sessions, machine instances, and sets maintain identical relationships and sort orders after serialization.
* Test error handling for malformed or missing JSON fields.

### 4.4 Coroutines & Asynchronous Logic
* Use `kotlinx.coroutines.test.runTest` for testing `suspend` functions and Flows.
* Test StateFlow emissions using `first()` or Turbine if available.

---

## 5. Verification Checklist Before Committing

Before creating a git commit for any change:
1. Run all unit tests for the affected modules.
2. Confirm 0 test failures.
3. If new logic was added, ensure corresponding new test methods were written.
4. Verify tests are committed together with the implementation (`feat` + `test` atomic commit).
