package com.datadragon.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.Calendar
import com.datadragon.app.data.CalendarCalculator
import com.datadragon.app.data.CalendarConfig
import com.datadragon.app.data.CalendarConfigCodec
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.LogEntry
import com.datadragon.app.ui.CalendarViewModel
import com.datadragon.app.ui.components.AppDropdownRow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** The shortened weekday headings, Sunday first — the exact owner-facing strings. */
private val WEEKDAYS = listOf("Sun", "Mon", "Tues", "Wed", "Thur", "Fri", "Sat")

/** A configured calendar paired with its decoded config and per-day results. */
private data class CalendarDays(
    val calendar: Calendar,
    val config: CalendarConfig,
    val values: Map<LocalDate, Double>,
)

/**
 * The calendar viewing screen: "[Form Name] Calendar", a View Calendar dropdown
 * when the form has more than one calendar, the selected calendar's label,
 * description and legend, then the month grid with each day colored by its
 * calculated result. A short press on a day shows every calendar's result for
 * that day.
 *
 * A long press on a day lists that day's logs beneath the calendar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarViewScreen(
    logId: String?,
    onBack: () -> Unit,
    viewModel: CalendarViewModel = viewModel(),
) {
    val templateId = logId?.toLongOrNull()
    LaunchedEffect(templateId) { templateId?.let { viewModel.load(it) } }

    val formName by viewModel.formName.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    // Each calendar's decoded config and per-day results, recomputed only when the
    // inputs change (so tapping days doesn't recompute).
    val calendarDays = remember(calendars, fields, entries) {
        calendars.map { cal ->
            val config = CalendarConfigCodec.decode(cal.configJson)
            CalendarDays(cal, config, CalendarCalculator.dailyValues(config, fields, entries))
        }
    }

    var selectedIndex by remember { mutableStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, (calendarDays.size - 1).coerceAtLeast(0))
    val selected: CalendarDays? = calendarDays.getOrNull(safeIndex)

    var month by remember { mutableStateOf(YearMonth.now()) }
    var popoverDay by remember { mutableStateOf<LocalDate?>(null) }
    // The day whose logs are listed beneath the calendar (set by a long press).
    var logDay by remember { mutableStateOf<LocalDate?>(null) }

    // Every calendar's line for a given day (short press shows all of them).
    fun linesForDay(date: LocalDate): List<String> = calendarDays.mapNotNull { cd ->
        cd.values[date]?.let { CalendarCalculator.shortPressLine(cd.config, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${formName.orEmpty()} Calendar".trim()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (calendarDays.size > 1 && selected != null) {
                AppDropdownRow(
                    label = "View Calendar",
                    options = calendarDays.map { it.calendar },
                    selected = selected.calendar,
                    onSelected = { cal -> selectedIndex = calendarDays.indexOfFirst { it.calendar.id == cal.id } },
                    optionLabel = { it.label },
                )
            }

            selected?.let { cd ->
                Text(
                    cd.calendar.label,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (cd.calendar.description.isNotBlank()) {
                    Text(
                        cd.calendar.description,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Legend(cd.config)
            }

            MonthHeader(
                month = month,
                onPrev = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
            )

            WeekdayHeader()

            MonthGrid(
                month = month,
                colorForDay = { date ->
                    selected?.let { cd -> cd.values[date]?.let { CalendarCalculator.colorFor(cd.config, it) } }
                        ?.let { hexToColor(it) }
                },
                popoverDay = popoverDay,
                linesForDay = ::linesForDay,
                onTapDay = { date -> if (linesForDay(date).isNotEmpty()) popoverDay = date },
                onDismissPopover = { popoverDay = null },
                onLongPressDay = { date -> logDay = date },
            )

            // Long press: that day's logs, in the app's normal read-only style.
            val day = logDay
            if (day != null && selected != null) {
                val dayLogs = CalendarCalculator.entriesOnDay(selected.config, fields, entries, day)
                dayLogs.forEach { entry -> DayLogCard(entry = entry, fields = fields) }
            }
        }
    }
}

/** The compact legend: each color swatch with its configured range. */
@Composable
private fun Legend(config: CalendarConfig) {
    if (config.colorRows.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        config.colorRows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(hexToColor(row.colorHex)),
                )
                Text(rangeLabel(row.minValue, row.maxValue), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun rangeLabel(min: String, max: String): String {
    val lo = min.trim()
    val hi = max.trim()
    return when {
        lo.isNotEmpty() && hi.isNotEmpty() -> "$lo–$hi"
        lo.isNotEmpty() -> "$lo+"
        hi.isNotEmpty() -> "≤$hi"
        else -> ""
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        WEEKDAYS.forEach { day ->
            Text(
                day,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The month laid out 7 days across, each day colored by its calculated result. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthGrid(
    month: YearMonth,
    colorForDay: (LocalDate) -> Color?,
    popoverDay: LocalDate?,
    linesForDay: (LocalDate) -> List<String>,
    onTapDay: (LocalDate) -> Unit,
    onDismissPopover: () -> Unit,
    onLongPressDay: (LocalDate) -> Unit,
) {
    val daysInMonth = month.lengthOfMonth()
    // Sunday-first offset: Monday=1 … Sunday=7, so Sunday maps to column 0.
    val leadingBlanks = month.atDay(1).dayOfWeek.value % 7
    val totalCells = leadingBlanks + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0..6) {
                    val dayNumber = row * 7 + col - leadingBlanks + 1
                    val date = if (dayNumber in 1..daysInMonth) month.atDay(dayNumber) else null
                    DayCell(
                        date = date,
                        dayNumber = dayNumber.takeIf { date != null },
                        color = date?.let(colorForDay),
                        popoverOpen = date != null && date == popoverDay,
                        popoverLines = date?.let(linesForDay).orEmpty(),
                        onTap = { date?.let(onTapDay) },
                        onLongPress = { date?.let(onLongPressDay) },
                        onDismissPopover = onDismissPopover,
                    )
                }
            }
        }
    }
}

/** One day cell: the date number over its result color, with the short-press popover. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.DayCell(
    date: LocalDate?,
    dayNumber: Int?,
    color: Color?,
    popoverOpen: Boolean,
    popoverLines: List<String>,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDismissPopover: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .let { if (color != null) it.background(color) else it }
            .let {
                if (date != null) it.combinedClickable(onClick = onTap, onLongClick = onLongPress) else it
            },
        contentAlignment = Alignment.Center,
    ) {
        if (dayNumber != null) {
            Text(dayNumber.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        DropdownMenu(expanded = popoverOpen, onDismissRequest = onDismissPopover) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                popoverLines.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** One of a long-pressed day's logs, shown read-only in the app's list style. */
@Composable
private fun DayLogCard(entry: LogEntry, fields: List<FieldDef>) {
    val values = remember(entry.valuesJson) { EntryValues.decode(entry.valuesJson) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                EntryValues.displayEntryTimestamp(entry.createdAt),
                style = MaterialTheme.typography.titleSmall,
            )
            fields.forEach { field ->
                EntryValues.displayValue(field, values)?.let { value ->
                    LabelledValue(field.label, value)
                }
            }
            EntryValues.notes(values)?.let { LabelledValue("Notes", it) }
        }
    }
}

@Composable
private fun LabelledValue(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Compose color from a "#RRGGBB" hex string; a bad value falls back to gray. */
private fun hexToColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
