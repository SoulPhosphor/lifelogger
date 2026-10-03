package com.datadragon.app.ui.screens

import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.datadragon.app.ui.components.AccessibleOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.ClickerField
import com.datadragon.app.data.ClickerStatisticsCalculator
import com.datadragon.app.data.ClickerStatisticsConfig
import com.datadragon.app.data.ClickerStatisticsRange
import com.datadragon.app.data.ClickerStats
import com.datadragon.app.data.ClickerTrackerStatistics
import com.datadragon.app.ui.ClickerStatisticsViewModel
import com.datadragon.app.ui.components.AppDropdownRow
import com.datadragon.app.ui.theme.AppTheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Shown for a statistic when the chosen range holds no value for it. */
private const val NO_VALUE = "—"

/** The Custom Date Range box accepts digits only; this just keeps the number parseable. */
private const val CUSTOM_DAYS_MAX_DIGITS = 9

/** The designer's name for each statistic, in display order. */
private fun statName(stat: String): String = when (stat) {
    ClickerStats.HIGHEST_VALUE -> "Highest Value"
    ClickerStats.LOWEST_VALUE -> "Lowest Value"
    ClickerStats.AVERAGE -> "Average"
    ClickerStats.TOTAL -> "Total"
    ClickerStats.DEFAULT_VALUE_CHANGED -> "Default value changed"
    else -> stat
}

/** One chip on the Statistics page. [key] survives rotation; [range] drives the math. */
private data class RangeChip(val key: String, val label: String, val range: ClickerStatisticsRange)

private fun rangeChips(config: ClickerStatisticsConfig): List<RangeChip> = buildList {
    add(RangeChip("all", "All Time", ClickerStatisticsRange.AllTime))
    if (config.last7Days) add(RangeChip("7", "Last 7 Days", ClickerStatisticsRange.LastDays(7)))
    if (config.last30Days) add(RangeChip("30", "Last 30 Days", ClickerStatisticsRange.LastDays(30)))
    val custom = config.customDayCount
    if (config.customRange && custom != null) {
        add(RangeChip("custom", "Last $custom Days", ClickerStatisticsRange.LastDays(custom)))
    }
}

/** "Default value changed in X of X logs", with X filled in from [stats]. */
private fun defaultChangedLine(stats: ClickerTrackerStatistics?): String =
    "Default value changed in ${stats?.defaultChangedCount ?: 0} of ${stats?.logCount ?: 0} logs"

private fun formatAverage(value: Double): String =
    BigDecimal(value).setScale(1, RoundingMode.HALF_UP).toPlainString()

/**
 * A Clicker grouping's Statistics page: the chosen range's numbers for each
 * number tracker. Opened from the Bar Chart icon on the grouping's Main screen;
 * the cog opens the Statistics Designer.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClickerStatisticsScreen(
    logId: Long,
    onBack: () -> Unit,
    onOpenDesigner: (Long) -> Unit,
    viewModel: ClickerStatisticsViewModel = viewModel(),
) {
    LaunchedEffect(logId) { viewModel.start(logId) }
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()

    val chips = rangeChips(config)
    var selectedKey by rememberSaveable { mutableStateOf("all") }
    // A range turned off in the designer falls back to the default, All Time.
    val selected = chips.firstOrNull { it.key == selectedKey } ?: chips.first()
    val statistics = ClickerStatisticsCalculator.calculate(config, fields, cards, selected.range, LocalDate.now())
        .filter { stats -> ClickerStats.all.any { config.isEnabled(stats.field.id, it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Data Summary of ${cards.size} Total Logs",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                        }
                        IconButton(onClick = { onOpenDesigner(logId) }) {
                            Icon(
                                Icons.Filled.SettingsApplications,
                                contentDescription = "Statistics Designer",
                                modifier = Modifier.size(AppTheme.sizes.settingsCog),
                            )
                        }
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
                .padding(AppTheme.spacing.screenInset),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.related)) {
                chips.forEach { chip ->
                    FilterChip(
                        selected = chip.key == selected.key,
                        onClick = { selectedKey = chip.key },
                        label = { Text(chip.label) },
                        shape = AppTheme.shapes.control,
                    )
                }
            }
            statistics.forEach { stats ->
                HorizontalDivider()
                TrackerStatistics(stats, config)
            }
        }
    }
}

@Composable
private fun TrackerStatistics(stats: ClickerTrackerStatistics, config: ClickerStatisticsConfig) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related)) {
        Text(stats.field.label, style = AppTheme.textStyles.sectionHeader)
        ClickerStats.all.filter { config.isEnabled(stats.field.id, it) }.forEach { stat ->
            when (stat) {
                ClickerStats.DEFAULT_VALUE_CHANGED -> Text(
                    defaultChangedLine(stats),
                    style = AppTheme.textStyles.settingTitle,
                )
                else -> {
                    val value = when (stat) {
                        ClickerStats.HIGHEST_VALUE -> stats.highest?.toString()
                        ClickerStats.LOWEST_VALUE -> stats.lowest?.toString()
                        ClickerStats.AVERAGE -> stats.average?.let(::formatAverage)
                        else -> stats.total?.toString()
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(statName(stat), style = AppTheme.textStyles.settingTitle, modifier = Modifier.weight(1f))
                        Text(value ?: NO_VALUE, style = AppTheme.textStyles.settingTitle)
                    }
                }
            }
        }
    }
}

/**
 * The Statistics Designer: which date ranges the Statistics page offers, which
 * date places a log in a range, whether today counts, and which statistics show
 * for each tracker. Every change saves at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickerStatisticsDesignerScreen(
    logId: Long,
    onBack: () -> Unit,
    viewModel: ClickerStatisticsViewModel = viewModel(),
) {
    LaunchedEffect(logId) { viewModel.start(logId) }
    val log by viewModel.log.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()

    val trackers = ClickerStatisticsCalculator.numericFields(fields)
    val dateFields = ClickerStatisticsCalculator.dateFields(fields)
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    // The designer has no range of its own, so its "X of X" shows All Time, the
    // Statistics page's default range.
    val allTime = ClickerStatisticsCalculator
        .calculate(config, fields, cards, ClickerStatisticsRange.AllTime, LocalDate.now())
        .associateBy { it.field.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics Designer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        // Wait for the saved choices before drawing, so the Custom Date Range box
        // starts from what was saved rather than blank.
        if (log == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(AppTheme.spacing.screenInset),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
        ) {
            Text("Date Ranges", style = AppTheme.textStyles.sectionHeader)
            CheckRow("Last 7 Days", config.last7Days) { on -> viewModel.update { it.copy(last7Days = on) } }
            CheckRow("Last 30 Days", config.last30Days) { on -> viewModel.update { it.copy(last30Days = on) } }
            CustomRangeRow(
                checked = config.customRange,
                initialDays = config.customDays,
                onCheckedChange = { on -> viewModel.update { it.copy(customRange = on) } },
                onDaysChange = { days -> viewModel.update { it.copy(customDays = days) } },
            )
            if (dateFields.isNotEmpty()) {
                val options: List<ClickerField?> = dateFields + null
                AppDropdownRow(
                    label = "Date Used for Ranges",
                    options = options,
                    selected = dateFields.firstOrNull { it.id == config.dateFieldId },
                    onSelected = { field -> viewModel.update { it.copy(dateFieldId = field?.id) } },
                    optionLabel = { it?.label ?: "Log Date" },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = config.excludeToday, role = Role.Switch, onValueChange = { on -> viewModel.update { it.copy(excludeToday = on) } }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Don't count current date's statistics when calculating",
                    style = AppTheme.textStyles.settingTitle,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(AppTheme.spacing.rowInset))
                Switch(
                    checked = config.excludeToday,
                    onCheckedChange = null,
                    modifier = Modifier.sizeIn(minWidth = AppTheme.sizes.minimumTouchTarget, minHeight = AppTheme.sizes.minimumTouchTarget),
                )
            }

            HorizontalDivider()
            Text("Statistics", style = AppTheme.textStyles.sectionHeader)
            if (trackers.isNotEmpty()) {
                val allOn = trackers.all { t -> ClickerStats.all.all { config.isEnabled(t.id, it) } }
                val allOff = trackers.all { t -> ClickerStats.all.none { config.isEnabled(t.id, it) } }
                TriStateRow(
                    label = "Select All",
                    state = toggleState(allOn, allOff),
                    onClick = { viewModel.update { it.withStats(trackers, on = !allOn) } },
                )
                trackers.forEach { tracker ->
                    val on = ClickerStats.all.filter { config.isEnabled(tracker.id, it) }
                    TriStateRow(
                        label = tracker.label,
                        state = toggleState(on.size == ClickerStats.all.size, on.isEmpty()),
                        onClick = {
                            val turnOn = on.size != ClickerStats.all.size
                            viewModel.update { it.withStats(listOf(tracker), on = turnOn) }
                        },
                    )
                    ClickerStats.all.forEach { stat ->
                        CheckRow(
                            label = if (stat == ClickerStats.DEFAULT_VALUE_CHANGED) {
                                defaultChangedLine(allTime[tracker.id])
                            } else {
                                statName(stat)
                            },
                            checked = stat in on,
                            modifier = Modifier.padding(start = AppTheme.spacing.listIndent),
                        ) { checked -> viewModel.update { it.withStat(tracker.id, stat, checked) } }
                    }
                }
            }
        }
    }
}

private fun toggleState(allOn: Boolean, allOff: Boolean): ToggleableState = when {
    allOn -> ToggleableState.On
    allOff -> ToggleableState.Off
    else -> ToggleableState.Indeterminate
}

/** Turn every statistic on or off for [trackers]. */
private fun ClickerStatisticsConfig.withStats(trackers: List<ClickerField>, on: Boolean): ClickerStatisticsConfig =
    copy(
        disabledStats = disabledStats.toMutableMap().apply {
            trackers.forEach { t -> if (on) remove(t.id) else put(t.id, ClickerStats.all) }
        },
    )

/** Turn one statistic on or off for one tracker. */
private fun ClickerStatisticsConfig.withStat(fieldId: String, stat: String, on: Boolean): ClickerStatisticsConfig {
    val off = disabledStats[fieldId].orEmpty().toMutableSet().apply { if (on) remove(stat) else add(stat) }
    return copy(disabledStats = disabledStats + (fieldId to off.toList()))
}

/** A checkbox with its label; tapping anywhere on the row toggles it. */
@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.sizeIn(minWidth = AppTheme.sizes.minimumTouchTarget, minHeight = AppTheme.sizes.minimumTouchTarget))
        Text(label, style = AppTheme.textStyles.settingTitle)
    }
}

/**
 * A three-state checkbox with its label (Select All, or one tracker, whose
 * statistics are always listed under it). Tapping anywhere on the row turns
 * them all on, or all off when every one is already on.
 */
@Composable
private fun TriStateRow(
    label: String,
    state: ToggleableState,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .triStateToggleable(state = state, role = Role.Checkbox, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(state = state, onClick = null, modifier = Modifier.sizeIn(minWidth = AppTheme.sizes.minimumTouchTarget, minHeight = AppTheme.sizes.minimumTouchTarget))
        Text(label, style = AppTheme.textStyles.settingTitle, modifier = Modifier.weight(1f))
    }
}

/** "Custom Date Range [   ] Days": its checkbox, and the day count typed in. */
@Composable
private fun CustomRangeRow(
    checked: Boolean,
    initialDays: String,
    onCheckedChange: (Boolean) -> Unit,
    onDaysChange: (String) -> Unit,
) {
    var days by remember { mutableStateOf(initialDays) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.sizeIn(minWidth = AppTheme.sizes.minimumTouchTarget, minHeight = AppTheme.sizes.minimumTouchTarget))
        Text(
            "Custom Date Range",
            style = AppTheme.textStyles.settingTitle,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(AppTheme.spacing.related))
        OutlinedTextField(
            value = days,
            accessibleLabel = "Custom Date Range, Days",
            onValueChange = { input ->
                days = input.filter { it.isDigit() }.take(CUSTOM_DAYS_MAX_DIGITS)
                onDaysChange(days)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(AppTheme.sizes.statisticsNumberWidth),
        )
        Spacer(Modifier.width(AppTheme.spacing.related))
        Text("Days", style = AppTheme.textStyles.settingTitle)
    }
}
