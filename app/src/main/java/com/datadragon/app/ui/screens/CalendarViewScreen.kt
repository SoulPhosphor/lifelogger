package com.datadragon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.Calendar
import com.datadragon.app.ui.CalendarViewModel
import com.datadragon.app.ui.components.AppDropdownRow
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** The shortened weekday headings, Sunday first — the exact owner-facing strings. */
private val WEEKDAYS = listOf("Sun", "Mon", "Tues", "Wed", "Thur", "Fri", "Sat")

/**
 * The calendar viewing screen for a form (reached by the calendar icon on the
 * form's entry list). Shows "[Form Name] Calendar" at the top, a View Calendar
 * dropdown when the form has more than one configured calendar, then the selected
 * calendar's label, its description, the month grid (7 days across, enough rows
 * for the month), and month navigation.
 *
 * This phase draws the structure. Day coloring from the configured rule, the
 * legend, and the short/long-press behavior are added by later phases.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarViewScreen(
    logId: String?,
    onBack: () -> Unit,
    viewModel: CalendarViewModel = viewModel(),
) {
    val templateId = logId?.toLongOrNull()
    LaunchedEffect(templateId) { templateId?.let { viewModel.load(it) } }

    val formName by viewModel.formName.collectAsStateWithLifecycle()
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()

    var selectedIndex by remember { mutableStateOf(0) }
    val selected: Calendar? = calendars.getOrNull(selectedIndex.coerceIn(0, (calendars.size - 1).coerceAtLeast(0)))

    var month by remember { mutableStateOf(YearMonth.now()) }

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
            // Only offer the picker when there is more than one calendar to view.
            if (calendars.size > 1 && selected != null) {
                AppDropdownRow(
                    label = "View Calendar",
                    options = calendars,
                    selected = selected,
                    onSelected = { selectedIndex = calendars.indexOf(it) },
                    optionLabel = { it.label },
                )
            }

            selected?.let { calendar ->
                Text(
                    calendar.label,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (calendar.description.isNotBlank()) {
                    Text(
                        calendar.description,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            MonthHeader(
                month = month,
                onPrev = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
            )

            WeekdayHeader()

            MonthGrid(month = month)
        }
    }
}

/** The centered Month and Year with previous / next month navigation. */
@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

/** The month laid out 7 days across, with blank leading/trailing cells. */
@Composable
private fun MonthGrid(month: YearMonth) {
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
                    DayCell(day = dayNumber.takeIf { it in 1..daysInMonth })
                }
            }
        }
    }
}

/** One day cell: the date number, or blank for a padding cell. */
@Composable
private fun RowScope.DayCell(day: Int?) {
    Box(
        modifier = Modifier.weight(1f).aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        if (day != null) {
            Text(day.toString(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
