package com.datadragon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import com.datadragon.app.data.ChoiceOption
import com.datadragon.app.data.ComparisonRow
import com.datadragon.app.data.ComparisonValues
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.activeChoices
import com.datadragon.app.data.choice
import com.datadragon.app.data.comparisonButtonLabel
import com.datadragon.app.data.comparisonFirstLabel
import com.datadragon.app.data.comparisonItemLabel
import com.datadragon.app.data.comparisonSecondLabel
import com.datadragon.app.data.comparisonTotalLabel
import com.datadragon.app.ui.components.AccessibleOutlinedTextField as OutlinedTextField
import com.datadragon.app.ui.components.AppButton
import com.datadragon.app.ui.theme.AppTheme

/** The item drop-down's wording before an item is picked. */
private const val CHOOSE_ITEM = "Choose Item"

/** A number box with no digit limit is never narrower than this many characters. */
private const val MIN_NUMBER_CHARS = 3

/**
 * A Comparison field on the entry form: a header line, one row per item (the
 * item drop-down, the two number boxes, the row's difference and a remove X),
 * the Add Item and New Option buttons, then the total of the differences.
 *
 * Each row's drop-down offers only the items no other row has picked, so an
 * item can be in one row at most. The number boxes are sized to what they can
 * hold — the field's digit limit when it has one, otherwise what has been typed
 * — and the drop-down takes the rest of the line. When the line is too narrow
 * for that, every row wraps to two lines: the drop-down, then the numbers.
 *
 * With clicker style counting, the second column is a number with the field's
 * button beside it instead of a typed box: each press steps the number from the
 * Starting Number, which already counts once the row has an item.
 *
 * A List Item Quantities field draws the same table with only the item and its
 * one number box (no second column, no difference), and shows the total of the
 * amounts only with "Add Item Quantities" on.
 *
 * [onNewOption] is set only when the form allows additions; it opens the Add
 * Option pop-up, whose new items the caller places into the rows. [onNotice]
 * shows a short message, such as a refused decimal point.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComparisonField(
    field: FieldDef,
    rows: List<ComparisonRow>,
    onRowsChange: (List<ComparisonRow>) -> Unit,
    showError: Boolean,
    onNewOption: (() -> Unit)?,
    onNotice: (String) -> Unit,
) {
    val widths = measureComparisonWidths(field, rows, readout = false)
    val counted = ComparisonValues.withStartingNumbers(field, rows)
    val chosen = rows.map { it.item }.filter { it.isNotBlank() }.toSet()
    // A blank row is waiting for one of the unused items, so it counts against them.
    val canAddItem = field.activeChoices.count { it.id !in chosen } > rows.count { it.item.isBlank() }
    val total = ComparisonValues.total(field, counted)?.let(ComparisonValues::format).orEmpty()
    val showsTotal = !field.isQuantityList || field.addQuantities

    fun update(index: Int, change: (ComparisonRow) -> ComparisonRow) =
        onRowsChange(rows.mapIndexed { i, row -> if (i == index) change(row) else row })

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val oneLine = maxWidth >= widths.oneLineMinimum
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related)) {
            ComparisonHeader(field, widths, oneLine)
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider()
                val otherChoices = chosen - row.item
                ComparisonEditRow(
                    field = field,
                    row = row,
                    difference = ComparisonValues.difference(counted[index])?.let(ComparisonValues::format).orEmpty(),
                    options = field.offeredChoices(listOf(row.item)).filter { it.id !in otherChoices },
                    widths = widths,
                    oneLine = oneLine,
                    showError = showError && ComparisonValues.needsItem(row),
                    onChange = { changed -> update(index) { changed } },
                    onNotice = onNotice,
                    onRemove = { onRowsChange(rows.filterIndexed { i, _ -> i != index }) },
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
            ) {
                AppButton(onClick = { onRowsChange(rows + ComparisonRow()) }, enabled = canAddItem) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    // Both captions are laid out so the button keeps one width.
                    Box {
                        StateCaption("  Add Item", visible = canAddItem)
                        StateCaption("  No Items Left", visible = !canAddItem)
                    }
                }
                if (onNewOption != null) {
                    AppButton(onClick = onNewOption) {
                        Icon(Icons.Outlined.AddCircleOutline, contentDescription = null)
                        Text("  New Option")
                    }
                }
            }
            if (showsTotal) {
                HorizontalDivider()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(widths.gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(field.comparisonTotalLabel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (oneLine) {
                        Text(total, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(widths.total))
                        Spacer(Modifier.width(widths.remove))
                    } else {
                        Text(total, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** One of a button's state captions; the hidden one still takes up its room. */
@Composable
private fun StateCaption(text: String, visible: Boolean) {
    Text(
        text,
        maxLines = 1,
        softWrap = false,
        modifier = if (visible) Modifier else Modifier.alpha(0f).clearAndSetSemantics { },
    )
}

/** The column captions, lined up over the rows below them. */
@Composable
private fun ComparisonHeader(field: FieldDef, widths: ComparisonWidths, oneLine: Boolean) {
    val style = MaterialTheme.typography.labelLarge
    val numberCaptions: @Composable () -> Unit = {
        Text(field.comparisonFirstLabel, style = style, modifier = Modifier.width(widths.first))
        if (!field.isQuantityList) {
            Text(field.comparisonSecondLabel, style = style, modifier = Modifier.width(widths.second))
            Text(ComparisonValues.DIFFERENCE_LABEL, style = style, modifier = Modifier.width(widths.difference))
        }
    }
    if (oneLine) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(widths.gap),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(field.comparisonItemLabel, style = style, modifier = Modifier.weight(1f))
            numberCaptions()
            Spacer(Modifier.width(widths.remove))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.compact)) {
            Text(field.comparisonItemLabel, style = style)
            Row(
                horizontalArrangement = Arrangement.spacedBy(widths.gap),
                verticalAlignment = Alignment.Bottom,
            ) { numberCaptions() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComparisonEditRow(
    field: FieldDef,
    row: ComparisonRow,
    difference: String,
    options: List<ChoiceOption>,
    widths: ComparisonWidths,
    oneLine: Boolean,
    showError: Boolean,
    onChange: (ComparisonRow) -> Unit,
    onNotice: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val itemName = field.choice(row.item)?.label ?: row.item.ifBlank { null }
    val numberLabel = itemName ?: field.comparisonItemLabel
    val dropdown: @Composable (Modifier) -> Unit = { modifier ->
        ComparisonItemDropdown(
            accessibleLabel = field.comparisonItemLabel,
            options = options,
            selectedLabel = itemName,
            isError = showError,
            onSelected = { onChange(row.copy(item = it.id)) },
            modifier = modifier,
        )
    }
    val numbers: @Composable () -> Unit = {
        ComparisonNumberBox(
            field = field,
            value = row.first,
            accessibleLabel = "$numberLabel: ${field.comparisonFirstLabel}",
            onChange = { onChange(row.copy(first = it)) },
            onNotice = onNotice,
            modifier = Modifier.width(widths.first),
        )
        if (field.isQuantityList) {
            // One number box only: no second column and no difference.
        } else if (field.clickerCounting) {
            ComparisonClickerCell(
                value = ComparisonValues.format(ComparisonValues.clickerValue(field, row)),
                buttonLabel = field.comparisonButtonLabel,
                accessibleLabel = "$numberLabel: ${field.comparisonSecondLabel}",
                onPress = { onChange(ComparisonValues.clickerPressed(field, row)) },
                modifier = Modifier.width(widths.second),
            )
        } else {
            ComparisonNumberBox(
                field = field,
                value = row.second,
                accessibleLabel = "$numberLabel: ${field.comparisonSecondLabel}",
                onChange = { onChange(row.copy(second = it)) },
                onNotice = onNotice,
                modifier = Modifier.width(widths.second),
            )
        }
        if (!field.isQuantityList) {
            Text(
                difference,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(widths.difference),
            )
        }
    }
    val remove: @Composable () -> Unit = {
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove $numberLabel")
        }
    }
    if (oneLine) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(widths.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dropdown(Modifier.weight(1f))
            numbers()
            remove()
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related)) {
            dropdown(Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Very long numbers wrap rather than run off the card.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(widths.gap),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.related),
                    modifier = Modifier.weight(1f),
                ) { numbers() }
                remove()
            }
        }
    }
}

/**
 * A row's item drop-down, drawn like the Single Choice drop-down. A long item
 * name is cut short with "…" in the box; the menu shows every name in full.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComparisonItemDropdown(
    accessibleLabel: String,
    options: List<ChoiceOption>,
    selectedLabel: String?,
    isError: Boolean,
    onSelected: (ChoiceOption) -> Unit,
    modifier: Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        // The box's own text can't end in "…", so the name is drawn in the
        // placeholder slot (shown while the text is empty) in the normal color.
        OutlinedTextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            isError = isError,
            placeholder = {
                Text(
                    selectedLabel ?: CHOOSE_ITEM,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selectedLabel != null) MaterialTheme.colorScheme.onSurface else Color.Unspecified,
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            accessibleLabel = "$accessibleLabel: ${selectedLabel ?: CHOOSE_ITEM}",
            accessibleError = "Choose an item for this row.",
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ComparisonNumberBox(
    field: FieldDef,
    value: String,
    accessibleLabel: String,
    onChange: (String) -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (ComparisonValues.refusesDecimalPoint(input, value, field.wholeNumbersOnly)) {
                onNotice(ComparisonValues.WHOLE_NUMBERS_ONLY_NOTICE)
            }
            onChange(ComparisonValues.acceptInput(input, value, field.wholeNumbersOnly, field.digits))
        },
        singleLine = true,
        // Signed keyboards, so the minus sign is offered where the keyboard supports it.
        keyboardOptions = KeyboardOptions(
            keyboardType = if (field.wholeNumbersOnly) KeyboardType.NumberSigned else KeyboardType.DecimalSigned,
        ),
        accessibleLabel = accessibleLabel,
        modifier = modifier,
    )
}

/**
 * The second column under clicker style counting: the current value, then the
 * field's button, which steps it by the increment. There is no typed box.
 */
@Composable
private fun ComparisonClickerCell(
    value: String,
    buttonLabel: String,
    accessibleLabel: String,
    onPress: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { contentDescription = "$accessibleLabel: $value" },
        )
        AppButton(onClick = onPress) {
            Text(buttonLabel, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * A saved Comparison answer on an entry card: the same columns as the entry
 * form (item, the two numbers, Δ) and the total underneath, read-only. A List
 * Item Quantities answer shows the item and its amount, and its total only
 * with "Add Item Quantities" on.
 */
@Composable
internal fun ComparisonReadout(
    field: FieldDef,
    rows: List<ComparisonRow>,
    modifier: Modifier = Modifier,
) {
    val counted = ComparisonValues.withStartingNumbers(field, rows)
    val widths = measureComparisonWidths(field, counted, readout = true)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelWeight = AppTheme.textStyles.readoutLabelWeight
    val valueStyle = MaterialTheme.typography.bodyLarge
    // Like the entry form, each line wraps to two (the item, then its numbers)
    // when the columns can't all fit beside the item.
    BoxWithConstraints(modifier = modifier) {
        val oneLine = maxWidth >= widths.oneLineMinimum
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.compact)) {
            ComparisonReadoutLine(
                widths = widths,
                oneLine = oneLine,
                item = field.comparisonItemLabel,
                first = field.comparisonFirstLabel,
                second = field.comparisonSecondLabel,
                difference = ComparisonValues.DIFFERENCE_LABEL,
                header = true,
                twoNumbers = !field.isQuantityList,
            )
            counted.forEach { row ->
                ComparisonReadoutLine(
                    widths = widths,
                    oneLine = oneLine,
                    item = field.choice(row.item)?.label ?: row.item,
                    first = ComparisonValues.parse(row.first)?.let(ComparisonValues::format).orEmpty(),
                    second = ComparisonValues.parse(row.second)?.let(ComparisonValues::format).orEmpty(),
                    difference = ComparisonValues.difference(row)?.let(ComparisonValues::format).orEmpty(),
                    header = false,
                    twoNumbers = !field.isQuantityList,
                )
            }
            ComparisonValues.total(field, counted)?.let { total ->
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = labelColor, fontWeight = labelWeight)) {
                            append("${field.comparisonTotalLabel}: ")
                        }
                        append(ComparisonValues.format(total))
                    },
                    style = valueStyle,
                )
            }
        }
    }
}

/** One line of a saved Comparison table; the header line uses the muted readout label look. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComparisonReadoutLine(
    widths: ComparisonWidths,
    oneLine: Boolean,
    item: String,
    first: String,
    second: String,
    difference: String,
    header: Boolean,
    twoNumbers: Boolean,
) {
    val valueStyle = MaterialTheme.typography.bodyLarge
    val style = if (header) {
        valueStyle.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = AppTheme.textStyles.readoutLabelWeight,
        )
    } else {
        valueStyle
    }
    val numbers: @Composable () -> Unit = {
        Text(first, style = style, modifier = Modifier.width(widths.first))
        if (twoNumbers) {
            Text(second, style = style, modifier = Modifier.width(widths.second))
            Text(difference, style = style, modifier = Modifier.width(widths.difference))
        }
    }
    if (oneLine) {
        Row(horizontalArrangement = Arrangement.spacedBy(widths.gap)) {
            Text(item, style = style, modifier = Modifier.weight(1f))
            numbers()
        }
    } else {
        Column {
            Text(item, style = style)
            // Very long numbers wrap rather than run off the card.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(widths.gap)) { numbers() }
        }
    }
}

/**
 * The measured column widths of one Comparison table. A List Item Quantities
 * table has no second or difference column ([twoNumbers] false).
 */
private class ComparisonWidths(
    val first: Dp,
    val second: Dp,
    val difference: Dp,
    val remove: Dp,
    val gap: Dp,
    private val itemMinimum: Dp,
    private val twoNumbers: Boolean,
) {
    /** The narrowest line that still fits every column side by side. */
    val oneLineMinimum: Dp
        get() = itemMinimum + gap + first + (if (twoNumbers) gap + second + gap + difference else Dp(0f)) + gap + remove

    /** The column the total lines up under: Δ, or the amount column. */
    val total: Dp
        get() = if (twoNumbers) difference else first
}

/**
 * Measure the columns. A number column is as wide as its box or the longest
 * word of its caption, whichever is wider (a caption wraps between words). On
 * the entry form a box fits the field's digit limit — plus a decimal point
 * when decimals are allowed, plus a minus sign — or, with no limit, whatever
 * has been typed. The Δ column fits its caption and every difference shown;
 * a List Item Quantities amount column also fits the total shown under it.
 */
@Composable
private fun measureComparisonWidths(field: FieldDef, rows: List<ComparisonRow>, readout: Boolean): ComparisonWidths {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val numberStyle = MaterialTheme.typography.bodyLarge
    val captionStyle = if (readout) {
        numberStyle.copy(fontWeight = AppTheme.textStyles.readoutLabelWeight)
    } else {
        MaterialTheme.typography.labelLarge
    }
    val inset = AppTheme.sizes.fieldContentInset
    val gap = AppTheme.spacing.related
    val remove = if (readout) Dp(0f) else AppTheme.sizes.minimumTouchTarget
    val itemMinimum = AppTheme.sizes.comparisonItemMinWidth
    val buttonStyle = AppTheme.textStyles.controlLabel
    val buttonInset = AppTheme.spacing.rowInset
    val buttonGap = AppTheme.spacing.compact

    fun width(text: String, style: TextStyle): Dp = with(density) {
        measurer.measure(text = text, style = style, softWrap = false, maxLines = 1).size.width.toDp()
    }
    fun longestWord(caption: String): Dp =
        caption.split(' ').filter { it.isNotEmpty() }.maxOfOrNull { width(it, captionStyle) } ?: Dp(0f)
    fun shown(text: String): String = ComparisonValues.parse(text)?.let(ComparisonValues::format).orEmpty()
    fun clickerColumn(caption: String): Dp {
        val shown = rows.map { ComparisonValues.format(ComparisonValues.clickerValue(field, it)) }
        val value = shown.maxOfOrNull { width(it, numberStyle) } ?: Dp(0f)
        val button = width(field.comparisonButtonLabel, buttonStyle) + buttonInset * 2
        return maxOf(value + buttonGap + button, longestWord(caption))
    }
    fun column(caption: String, values: List<String>): Dp {
        val content = if (readout) {
            values.map(::shown).maxOfOrNull { width(it, numberStyle) } ?: Dp(0f)
        } else {
            val typed = values.maxOfOrNull { it.length } ?: 0
            // Sized from the digit limit (never more than the largest Max Digits,
            // whatever a stored setting says) or from what has been typed.
            val limit = field.digits?.coerceIn(1, ComparisonValues.MAX_DIGITS)
            val chars = limit?.let { digits ->
                maxOf(digits + (if (field.wholeNumbersOnly) 0 else 1) + 1, typed)
            } ?: maxOf(MIN_NUMBER_CHARS, typed)
            width("0".repeat(chars), numberStyle) + inset * 2
        }
        return maxOf(content, longestWord(caption))
    }

    val counted = ComparisonValues.withStartingNumbers(field, rows)
    val total = ComparisonValues.total(field, counted)?.let(ComparisonValues::format).takeIf { !readout }
    val differences = counted.mapNotNull(ComparisonValues::difference).map(ComparisonValues::format) +
        listOfNotNull(total.takeIf { !field.isQuantityList })
    val first = column(field.comparisonFirstLabel, rows.map { it.first })
    return ComparisonWidths(
        first = if (field.isQuantityList && total != null) maxOf(first, width(total, numberStyle)) else first,
        second = if (field.clickerCounting && !readout) {
            clickerColumn(field.comparisonSecondLabel)
        } else {
            column(field.comparisonSecondLabel, rows.map { it.second })
        },
        difference = maxOf(
            width(ComparisonValues.DIFFERENCE_LABEL, captionStyle),
            differences.maxOfOrNull { width(it, numberStyle) } ?: Dp(0f),
        ),
        remove = remove,
        gap = gap,
        itemMinimum = itemMinimum,
        twoNumbers = !field.isQuantityList,
    )
}

/** True for a List Item Quantities field: one number column, no difference. */
private val FieldDef.isQuantityList: Boolean
    get() = type == FieldType.LIST_ITEM_QUANTITIES
