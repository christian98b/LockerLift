package com.lockerlift.wear.presentation

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.lockerlift.wear.R
import com.lockerlift.wear.logic.WearWorkoutLogic
import kotlinx.coroutines.delay

@Composable
fun RestTimerScreen(
    initialSeconds: Int = 90,
    machineName: String? = null,
    completedSetNumber: Int? = null,
    isWorkoutPaused: Boolean = false,
    hasNextExercise: Boolean = false,
    onNextSet: () -> Unit,
    onNextExercise: (() -> Unit)? = null,
    onSkipRest: () -> Unit,
    onDefaultDurationChanged: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    var remainingSeconds by remember { mutableIntStateOf(initialSeconds) }
    var hasVibrated by remember { mutableStateOf(false) }
    var defaultSavedNotice by remember { mutableStateOf<String?>(null) }
    val listState = rememberScalingLazyListState()

    LaunchedEffect(isWorkoutPaused) {
        while (remainingSeconds > 0) {
            delay(1000L)
            if (!isWorkoutPaused) {
                remainingSeconds -= 1
                if (remainingSeconds == 0 && !hasVibrated) {
                    hasVibrated = true
                    triggerVibration(context)
                }
            }
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Labeled step / header (AK 3.4)
        item {
            if (machineName != null && completedSetNumber != null) {
                Text(
                    text = stringResource(R.string.rest_timer_set_logged_format, machineName, completedSetNumber),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }

        item {
            Text(
                text = if (remainingSeconds > 0) {
                    stringResource(R.string.rest_timer_resting)
                } else {
                    stringResource(R.string.rest_timer_finished)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Countdown display
        item {
            Text(
                text = formattedTime,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (remainingSeconds > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
        }

        // Quick adjustment (+/- 15s) while running without restart (AK 3.9)
        item {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                CompactButton(
                    onClick = {
                        remainingSeconds = WearWorkoutLogic.adjustRestSeconds(
                            remainingSeconds,
                            -WearWorkoutLogic.REST_ADJUSTMENT_STEP_SECONDS
                        )
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(stringResource(R.string.btn_minus_15), fontSize = 12.sp)
                }
                CompactButton(
                    onClick = {
                        remainingSeconds = WearWorkoutLogic.adjustRestSeconds(
                            remainingSeconds,
                            WearWorkoutLogic.REST_ADJUSTMENT_STEP_SECONDS
                        )
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(stringResource(R.string.btn_plus_15), fontSize = 12.sp)
                }
            }
        }

        // Primary Action: Next Set (AK 3.4, AK 3.5, AK 3.10)
        item {
            Button(
                onClick = onNextSet,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 2.dp)
            ) {
                Text(stringResource(R.string.btn_next_set))
            }
        }

        // Optional Action: Next Exercise if available (AK 3.4)
        if (hasNextExercise && onNextExercise != null) {
            item {
                Button(
                    onClick = onNextExercise,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(vertical = 2.dp)
                ) {
                    Text(stringResource(R.string.btn_next_exercise))
                }
            }
        }

        // Action: Skip Rest / Overview (AK 3.10)
        item {
            CompactButton(
                onClick = onSkipRest,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(stringResource(R.string.btn_skip_rest), fontSize = 11.sp)
            }
        }

        // Optional: Save adjusted duration as default (AK 3.8)
        if (onDefaultDurationChanged != null) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                CompactButton(
                    onClick = {
                        val durationToSave = if (remainingSeconds > 0) remainingSeconds else initialSeconds
                        onDefaultDurationChanged(durationToSave)
                        defaultSavedNotice = context.getString(R.string.rest_default_updated, durationToSave)
                    },
                    modifier = Modifier.fillMaxWidth(0.85f).padding(top = 2.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.rest_default_set_format,
                            if (remainingSeconds > 0) remainingSeconds else initialSeconds
                        ),
                        fontSize = 10.sp
                    )
                }
            }
            if (defaultSavedNotice != null) {
                item {
                    Text(
                        text = defaultSavedNotice!!,
                        style = MaterialTheme.typography.bodyExtraSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun triggerVibration(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1)
        )
    } else {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        @Suppress("DEPRECATION")
        vibrator.vibrate(500)
    }
}
