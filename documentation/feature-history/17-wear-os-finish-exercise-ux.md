# Wear OS Tracking UX Enhancement: "Übung beenden" ("Finish Exercise") (`:wear`)

  - `RepsWeightInputScreen` (`:wear`): Replaced unintuitive "Cancel" / "Abbrechen" button with prominent "Finish Exercise" / "Übung beenden" button (`btn_finish_exercise`) when sets have already been completed (`setNumber > 1` and `!isEditing`). First set retains "Cancel" ("Abbrechen") for explicit discarding.
  - `RestTimerScreen` (`:wear`): Added dedicated `onFinishExercise` callback and `btn_finish_exercise` action alongside "Next Set" and "Next Exercise".
  - `ActiveWorkoutScreen` (`:wear`): Wired `onFinishExercise` to cleanly conclude the active station and return to workout overview with all completed sets safely recorded.
  - `WearWorkoutLogic` (`:wear`): Added `shouldShowFinishExerciseAction(isEditing, setNumber)` pure domain method.
  - Full EN/DE localization in `values/strings.xml` and `values-de/strings.xml`.
