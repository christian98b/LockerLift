package com.lockerlift.mobile.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockerlift.core.model.InsightsEngine
import com.lockerlift.mobile.LockerLiftMobileApp
import com.lockerlift.mobile.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(app: LockerLiftMobileApp) {
    val context = LocalContext.current
    val configStore = remember { DashboardConfigStore(context) }
    val zone = remember { ZoneId.systemDefault() }

    var widgetOrder by remember { mutableStateOf(configStore.loadOrder()) }
    var hiddenWidgets by remember { mutableStateOf(configStore.loadHidden()) }
    var editMode by remember { mutableStateOf(false) }
    var selectedMachineId by remember { mutableStateOf<String?>(null) }
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }

    val sessions by app.database.workoutSessionDao()
        .getAllSessionsWithDetailsFlow()
        .collectAsState(initial = emptyList())
    val snapshots = remember(sessions) { InsightsMapper.toSnapshots(sessions) }
    val today = remember { LocalDate.now() }

    fun persistOrder(newOrder: List<DashboardWidgetType>, newHidden: Set<DashboardWidgetType>) {
        widgetOrder = newOrder
        hiddenWidgets = newHidden
        configStore.saveOrder(newOrder)
        configStore.saveHidden(newHidden)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.insights_title)) },
                actions = {
                    IconButton(onClick = { editMode = !editMode }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.insights_edit_dashboard)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val visible = widgetOrder.filter { it !in hiddenWidgets }
            if (editMode) {
                item {
                    Text(
                        text = stringResource(R.string.insights_edit_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (!editMode && visible.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.insights_empty_dashboard),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (editMode) {
                items(widgetOrder.size) { index ->
                    EditableWidgetRow(
                        widget = widgetOrder[index],
                        isHidden = widgetOrder[index] in hiddenWidgets,
                        canMoveUp = index > 0,
                        canMoveDown = index < widgetOrder.size - 1,
                        onMoveUp = {
                            if (index > 0) {
                                val mutable = widgetOrder.toMutableList()
                                val tmp = mutable[index - 1]
                                mutable[index - 1] = mutable[index]
                                mutable[index] = tmp
                                persistOrder(mutable.toList(), hiddenWidgets)
                            }
                        },
                        onMoveDown = {
                            if (index < widgetOrder.size - 1) {
                                val mutable = widgetOrder.toMutableList()
                                val tmp = mutable[index + 1]
                                mutable[index + 1] = mutable[index]
                                mutable[index] = tmp
                                persistOrder(mutable.toList(), hiddenWidgets)
                            }
                        },
                        onToggleVisibility = {
                            val widget = widgetOrder[index]
                            val newHidden = if (widget in hiddenWidgets) {
                                hiddenWidgets - widget
                            } else {
                                hiddenWidgets + widget
                            }
                            persistOrder(widgetOrder, newHidden)
                        }
                    )
                }
            } else {
                visible.forEach { widget ->
                    item(key = widget.name) {
                        when (widget) {
                            DashboardWidgetType.VERDICT -> VerdictWidget(snapshots, today, zone)
                            DashboardWidgetType.REGULARITY -> RegularityWidget(snapshots, today, zone)
                            DashboardWidgetType.CALENDAR -> CalendarWidget(snapshots, displayedMonth, zone) { delta ->
                                displayedMonth = displayedMonth.plusMonths(delta)
                            }
                            DashboardWidgetType.VOLUME_CHART -> VolumeChartWidget(snapshots, zone)
                            DashboardWidgetType.PROGRESSION -> ProgressionWidget(snapshots, zone, selectedMachineId) { id ->
                                selectedMachineId = id
                            }
                            DashboardWidgetType.RECORDS -> RecordsWidget(snapshots)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun VerdictWidget(
    sessions: List<InsightsEngine.SessionSnapshot>,
    today: LocalDate,
    zone: ZoneId
) {
    val regularity = remember(sessions, today) {
        InsightsEngine.rateRegularity(sessions, today, zone)
    }
    DashboardCard(title = stringResource(R.string.insights_widget_verdict)) {
        val verdictColor = when (regularity.verdict) {
            InsightsEngine.TrainingVerdict.TOO_LITTLE -> MaterialTheme.colorScheme.error
            InsightsEngine.TrainingVerdict.ON_TRACK -> MaterialTheme.colorScheme.tertiary
            InsightsEngine.TrainingVerdict.DO_MORE -> MaterialTheme.colorScheme.primary
        }
        val verdictLabel = when (regularity.verdict) {
            InsightsEngine.TrainingVerdict.TOO_LITTLE -> stringResource(R.string.insights_verdict_too_little)
            InsightsEngine.TrainingVerdict.ON_TRACK -> stringResource(R.string.insights_verdict_on_track)
            InsightsEngine.TrainingVerdict.DO_MORE -> stringResource(R.string.insights_verdict_do_more)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(verdictColor.copy(alpha = 0.2f), CircleShape)
                    .border(2.dp, verdictColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%.1f", regularity.averageSessionsPerWeek),
                    color = verdictColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Column {
                Text(verdictLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = verdictColor)
                Text(
                    text = stringResource(
                        R.string.insights_sessions_per_week,
                        String.format(Locale.getDefault(), "%.1f", regularity.averageSessionsPerWeek)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun RegularityWidget(
    sessions: List<InsightsEngine.SessionSnapshot>,
    today: LocalDate,
    zone: ZoneId
) {
    val weeks = remember(sessions, today) {
        (0 until 8).map { weekOffset ->
            val weekStart = today.minusWeeks(weekOffset.toLong()).with(java.time.DayOfWeek.MONDAY)
            val days = (0 until 7).map { dayOffset ->
                val date = weekStart.plusDays(dayOffset.toLong())
                !date.isAfter(today) && sessions.any { s ->
                    s.isCompleted &&
                        java.time.Instant.ofEpochMilli(s.startTime).atZone(zone).toLocalDate() == date
                }
            }
            weekStart to days.count { it }
        }
    }
    DashboardCard(title = stringResource(R.string.insights_widget_regularity)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            weeks.reversed().forEach { (weekStart, count) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .background(
                                if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(4.dp)
                            )
                    )
                    Text(
                        text = "${weekStart.dayOfMonth}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        val average = weeks.map { it.second }.average()
        Text(
            text = stringResource(
                R.string.insights_average_per_week,
                String.format(Locale.getDefault(), "%.1f", average)
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CalendarWidget(
    sessions: List<InsightsEngine.SessionSnapshot>,
    month: YearMonth,
    zone: ZoneId,
    onMonthChange: (Int) -> Unit
) {
    val calendar = remember(sessions, month) {
        InsightsEngine.buildCalendarMonth(sessions, month, zone)
    }
    val dayLabels = remember {
        java.time.DayOfWeek.entries.map { day ->
            day.getDisplayName(TextStyle.NARROW, Locale.getDefault())
        }
    }
    DashboardCard(title = stringResource(R.string.insights_widget_calendar)) {
        Row(
            verticalArrangement = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = { onMonthChange(-1) }) { Text("<") }
            Text(
                text = month.toString(),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            TextButton(onClick = { onMonthChange(1) }) { Text(">") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                dayLabels.forEach { label ->
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            calendar.weeks.forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day != null) {
                                val todayMarker = day.date == LocalDate.now()
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            when {
                                                day.hasTrained -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            RoundedCornerShape(6.dp)
                                        )
                                        .border(
                                            width = if (todayMarker) 2.dp else 0.dp,
                                            color = if (todayMarker) MaterialTheme.colorScheme.tertiary else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                )
                                Text(
                                    text = "${day.date.dayOfMonth}",
                                    fontSize = 11.sp,
                                    color = if (day.hasTrained) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeChartWidget(
    sessions: List<InsightsEngine.SessionSnapshot>,
    zone: ZoneId
) {
    val points = remember(sessions) {
        InsightsEngine.overallVolumeProgression(sessions, zone)
    }
    DashboardCard(title = stringResource(R.string.insights_widget_volume_chart)) {
        if (points.size < 2) {
            Text(
                text = stringResource(R.string.insights_not_enough_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LineChart(points = points, modifier = Modifier.fillMaxWidth().height(160.dp))
        }
    }
}

@Composable
private fun ProgressionWidget(
    sessions: List<InsightsEngine.SessionSnapshot>,
    zone: ZoneId,
    selectedMachineId: String?,
    onMachineSelected: (String?) -> Unit
) {
    val machines = remember(sessions) { InsightsEngine.trainedMachines(sessions) }
    val machineId = selectedMachineId ?: machines.firstOrNull()?.first
    val progression = remember(sessions, machineId) {
        machineId?.let { InsightsEngine.exerciseProgression(sessions, it, zone) }
    }
    DashboardCard(title = stringResource(R.string.insights_widget_progression)) {
        if (machines.isEmpty()) {
            Text(
                text = stringResource(R.string.insights_not_enough_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val gridRows = (machines.size + 2) / 3
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((gridRows * 48).dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                userScrollEnabled = false
            ) {
                items(machines.size) { index ->
                    val (id, name) = machines[index]
                    FilterChip(
                        selected = id == machineId,
                        onClick = { onMachineSelected(id) },
                        label = {
                            Text(name, fontSize = 11.sp, maxLines = 1)
                        },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            if (progression == null || progression.points.size < 2) {
                Text(
                    text = stringResource(R.string.insights_not_enough_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LineChart(points = progression.points, modifier = Modifier.fillMaxWidth().height(160.dp))
                val trend = progression.trend!!
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = when {
                            trend.hasImproved -> Icons.Default.TrendingUp
                            trend.changePercent < 0f -> Icons.Default.TrendingDown
                            else -> Icons.Default.TrendingFlat
                        },
                        contentDescription = null,
                        tint = if (trend.hasImproved) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(
                            R.string.insights_trend_change,
                            String.format(Locale.getDefault(), "%+.0f%%", trend.changePercent)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordsWidget(sessions: List<InsightsEngine.SessionSnapshot>) {
    val records = remember(sessions) { InsightsEngine.personalRecords(sessions) }
    DashboardCard(title = stringResource(R.string.insights_widget_records)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RecordStat(
                value = records.totalSessions.toString(),
                label = stringResource(R.string.insights_stat_sessions),
                modifier = Modifier.weight(1f)
            )
            RecordStat(
                value = stringResource(R.string.insights_volume_kg, records.totalVolumeKg.toInt().toString()),
                label = stringResource(R.string.insights_stat_volume),
                modifier = Modifier.weight(1f)
            )
            RecordStat(
                value = records.totalSets.toString(),
                label = stringResource(R.string.insights_stat_sets),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RecordStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditableWidgetRow(
    widget: DashboardWidgetType,
    isHidden: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleVisibility: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isHidden) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isHidden) 0.dp else 1.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleVisibility() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.insights_move_up))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.insights_move_down))
            }
            Text(
                text = stringResource(widget.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(R.string.insights_toggle_visibility)
                )
            }
        }
    }
}

@Composable
internal fun LineChart(points: List<InsightsEngine.VolumePoint>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.15f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxVolume = remember(points) { points.maxOf { it.volumeKg }.coerceAtLeast(1f) }

    Canvas(modifier = modifier) {
        val left = 44f
        val bottom = 24f
        val top = 8f
        val chartWidth = size.width - left - 8f
        val chartHeight = size.height - top - bottom

        (0..2).forEach { i ->
            val y = top + chartHeight * i / 2f
            drawLine(
                color = gridColor,
                start = Offset(left, y),
                end = Offset(left + chartWidth, y),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
            )
        }
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = labelColor.toArgb()
                textSize = 24f
            }
            drawText("${maxVolume.toInt()}", 4f, top + 8f, paint)
            drawText("0", 4f, top + chartHeight, paint)
        }

        if (points.size >= 2) {
            val stepX = chartWidth / (points.size - 1)
            val coords = points.mapIndexed { i, p ->
                val x = left + i * stepX
                val y = top + chartHeight * (1f - (p.volumeKg / maxVolume))
                Offset(x, y)
            }
            val path = Path().apply {
                moveTo(coords.first().x, coords.first().y)
                coords.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val fillPath = Path().apply {
                addPath(path)
                lineTo(coords.last().x, top + chartHeight)
                lineTo(coords.first().x, top + chartHeight)
                close()
            }
            drawPath(fillPath, fillColor)
            drawPath(path, lineColor, style = Stroke(width = 4f))
            coords.forEach { c ->
                drawCircle(color = lineColor, radius = 6f, center = c)
            }
            val lastPoint = points.last()
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                drawText(
                    "${lastPoint.date.dayOfMonth}.${lastPoint.date.monthValue}.",
                    size.width - 8f,
                    size.height - 4f,
                    paint
                )
            }
        }
    }
}


