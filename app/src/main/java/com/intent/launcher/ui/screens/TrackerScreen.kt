package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.DateControls
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.TrackerEntry
import com.intent.launcher.domain.TrackerEntryStatus
import com.intent.launcher.domain.TrackerFieldControls
import com.intent.launcher.domain.TrackerFieldType
import com.intent.launcher.domain.TrackerModuleDef
import com.intent.launcher.ui.components.HeroCard
import com.intent.launcher.ui.components.HeroTone
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.IntentSecondaryButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.NumberWheel
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.theme.IntentColors
import java.util.TimeZone
import androidx.compose.runtime.mutableIntStateOf

@Composable
fun TrackerScreen(
    intentionTitle: String,
    modules: List<TrackerModuleDef>,
    entries: List<TrackerEntry>,
    honestyNote: String,
    onRecord: (moduleId: String, valueText: String, eventAtEpochMs: Long?) -> Unit,
    onCorrect: (priorEntryId: String, valueText: String, eventTimeEpochMs: Long?) -> Unit,
    onDelete: (entryId: String) -> Unit,
    onOpenReports: () -> Unit,
    onBack: () -> Unit,
    nowEpochMs: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
    modifier: Modifier = Modifier
) {
    val active = entries.filter { it.status == TrackerEntryStatus.ACTIVE }
        .sortedByDescending { it.eventAtEpochMs }
    var correctingEntryId by remember { mutableStateOf<String?>(null) }
    var daySelection by remember(nowEpochMs, timeZone) {
        mutableStateOf(DateControls.selectionFor(nowEpochMs, nowEpochMs, timeZone))
    }

    fun eventAtOrNull(): Long? {
        val eventAt = DateControls.eventAtEpochMs(daySelection, nowEpochMs, timeZone)
        return if (daySelection.isToday) null else eventAt
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 19.dp, vertical = 15.dp)
    ) {
        ScreenTopBar(contextLabel = "Tracker", onBack = onBack)
        Spacer(modifier = Modifier.height(23.dp))
        SmallLabel(text = intentionTitle.ifBlank { "Intention" }, color = IntentColors.Section)
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = "Tracker modules",
            style = MaterialTheme.typography.headlineLarge,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(8.dp))
        Lede("Log what happened in plain language. Corrections replace earlier entries.")
        Spacer(modifier = Modifier.height(18.dp))
        val plainHonesty = honestyNote
            .replace(Regex("\\bSAID\\b"), "reported")
            .replace(Regex("\\bOBSERVED\\b"), "recorded")
        Text(
            text = plainHonesty,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = TrackerFieldControls.HONESTY_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(18.dp))

        EntryDateControls(
            selection = daySelection,
            onPrevious = {
                daySelection = DateControls.stepPrevious(daySelection, timeZone)
            },
            onNext = {
                daySelection = DateControls.stepNext(daySelection, timeZone)
            }
        )

        Spacer(modifier = Modifier.height(18.dp))
        IntentSecondaryButton(text = "Open progress report", onClick = onOpenReports)

        modules.forEach { module ->
            Spacer(modifier = Modifier.height(22.dp))
            SectionTitle(module.title)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = module.prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Muted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Spacer(modifier = Modifier.height(0.dp))
            Text(
                text = "Saving for ${daySelection.label} · ${daySelection.localDate}",
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Accent
            )
            Spacer(modifier = Modifier.height(10.dp))
            ModuleFieldEntry(
                module = module,
                daySelection = daySelection,
                onSave = { value ->
                    onRecord(module.id, value, eventAtOrNull())
                }
            )
            val moduleEntries = active.filter { it.moduleId == module.id }
            if (moduleEntries.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No entries yet for this module.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                moduleEntries.forEach { entry ->
                    TrackerEntryRow(
                        entry = entry,
                        module = module,
                        correcting = correctingEntryId == entry.id,
                        nowEpochMs = nowEpochMs,
                        timeZone = timeZone,
                        daySelection = daySelection,
                        onToggleCorrect = {
                            correctingEntryId =
                                if (correctingEntryId == entry.id) null else entry.id
                        },
                        onCorrectValue = { value ->
                            onCorrect(entry.id, value, eventAtOrNull())
                            correctingEntryId = null
                        },
                        onDelete = { onDelete(entry.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ModuleFieldEntry(
    module: TrackerModuleDef,
    daySelection: DateControls.DaySelection,
    onSave: (String) -> Unit
) {
    when (module.fieldType) {
        TrackerFieldType.NUMERIC -> {
            val choices = module.quantityChoices
            val unitLabel = module.quantityUnitLabel ?: module.unit
            if (choices != null && unitLabel != null) {
                QuantityWheelEntry(
                    choices = choices,
                    unitLabel = unitLabel,
                    suggested = module.suggestedValues,
                    format = { TrackerFieldControls.formatNumeric(it.toString(), module.unit) },
                    onSave = onSave
                )
            } else {
                NumericOrDurationEntry(
                    label = "Exact value",
                    unit = module.unit,
                    keyboardType = KeyboardType.Decimal,
                    suggested = module.suggestedValues,
                    format = { TrackerFieldControls.formatNumeric(it, module.unit) },
                    onSave = onSave
                )
            }
        }
        TrackerFieldType.DURATION -> {
            val choices = module.quantityChoices
            val unitLabel = module.quantityUnitLabel ?: module.unit ?: "min"
            if (choices != null) {
                QuantityWheelEntry(
                    choices = choices,
                    unitLabel = unitLabel,
                    suggested = module.suggestedValues,
                    format = { mins ->
                        TrackerFieldControls.formatDurationMinutes(mins.toString())
                            ?: TrackerFieldControls.formatNumeric(
                                mins.toString(),
                                module.unit ?: "min"
                            )
                    },
                    onSave = onSave
                )
            } else {
                NumericOrDurationEntry(
                    label = "Minutes",
                    unit = module.unit ?: "min",
                    keyboardType = KeyboardType.Number,
                    suggested = module.suggestedValues,
                    format = { raw ->
                        TrackerFieldControls.formatDurationMinutes(raw)
                            ?: TrackerFieldControls.formatNumeric(raw, module.unit ?: "min")
                    },
                    onSave = onSave
                )
            }
        }
        TrackerFieldType.YES_NO -> {
            ChoiceRow(
                choices = module.suggestedValues.ifEmpty { TrackerFieldControls.YES_NO_VALUES },
                onPick = onSave
            )
        }
        TrackerFieldType.SINGLE_CHOICE -> {
            ChoiceRow(choices = module.suggestedValues, onPick = onSave)
        }
        TrackerFieldType.RATING -> {
            ChoiceRow(
                choices = module.suggestedValues.ifEmpty { TrackerFieldControls.RATING_VALUES },
                onPick = onSave
            )
        }
        TrackerFieldType.DATE_TIME -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = TrackerFieldControls.formatDateTimeValue(
                        daySelection.label,
                        daySelection.localDate
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentColors.Ink,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Uses the entry date above. Save records that day — " +
                        "not an automatic device clock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
                IntentSecondaryButton(
                    text = "Save entry date",
                    onClick = {
                        onSave(
                            TrackerFieldControls.formatDateTimeValue(
                                daySelection.label,
                                daySelection.localDate
                            )
                        )
                    }
                )
            }
        }
        TrackerFieldType.MULTIPLE_CHOICE -> {
            MultiChoiceEntry(
                choices = module.suggestedValues,
                onSave = onSave
            )
        }
    }
}

@Composable
private fun QuantityWheelEntry(
    choices: List<Int>,
    unitLabel: String,
    suggested: List<String>,
    format: (Int) -> String?,
    onSave: (String) -> Unit
) {
    var selected by remember(choices) {
        mutableIntStateOf(
            choices.firstOrNull { it == 20 } ?: choices.getOrElse(choices.size / 2) { choices.first() }
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberWheel(
            values = choices,
            selected = selected,
            onSelectedChange = { selected = it },
            unitLabel = unitLabel,
            summaryTemplate = "{value} {unit}"
        )
        val preview = format(selected)
        IntentSecondaryButton(
            text = if (preview != null) "Save $preview" else "Save $selected $unitLabel",
            onClick = {
                val value = preview ?: "$selected $unitLabel"
                onSave(value)
            }
        )
        if (suggested.isNotEmpty()) {
            Text(
                text = "Shortcuts",
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
            ChoiceRow(choices = suggested, onPick = onSave)
        }
    }
}

@Composable
private fun NumericOrDurationEntry(
    label: String,
    unit: String?,
    keyboardType: KeyboardType,
    suggested: List<String>,
    format: (String) -> String?,
    onSave: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    val preview = remember(draft, unit) { format(draft) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = {
                Text(if (unit.isNullOrBlank()) "Enter value" else "Enter $unit")
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true
        )
        if (draft.isNotBlank() && preview == null) {
            Text(
                text = "Enter a number to save — blank/unknown is not saved as zero.",
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }
        IntentSecondaryButton(
            text = if (preview != null) "Save $preview" else "Save value",
            onClick = {
                val value = preview ?: return@IntentSecondaryButton
                onSave(value)
                draft = ""
            }
        )
        if (suggested.isNotEmpty()) {
            Text(
                text = "Shortcuts",
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
            ChoiceRow(choices = suggested, onPick = onSave)
        }
    }
}

@Composable
private fun ChoiceRow(
    choices: List<String>,
    onPick: (String) -> Unit
) {
    if (choices.isEmpty()) {
        Text(
            text = "No choices configured for this module.",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { value ->
            IntentSecondaryButton(text = value, onClick = { onPick(value) })
        }
    }
}

@Composable
private fun MultiChoiceEntry(
    choices: List<String>,
    onSave: (String) -> Unit
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    if (choices.isEmpty()) {
        Text(
            text = "No choices configured for this module.",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { value ->
            val on = value in selected
            IntentSecondaryButton(
                text = if (on) "✓ $value" else value,
                onClick = {
                    selected = if (on) selected - value else selected + value
                },
                accentOutline = on
            )
        }
        val joined = TrackerFieldControls.joinMultiple(selected)
        IntentSecondaryButton(
            text = if (joined != null) "Save selection" else "Select at least one",
            onClick = {
                val value = joined ?: return@IntentSecondaryButton
                onSave(value)
                selected = emptySet()
            }
        )
    }
}

@Composable
private fun EntryDateControls(
    selection: DateControls.DaySelection,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
            .background(IntentColors.Card, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .semantics {
                contentDescription =
                    "Entry date ${selection.label} ${selection.localDate}. " +
                        selection.honestyNote
            }
    ) {
        SmallLabel(text = "Entry date", color = IntentColors.Section)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = onPrevious,
                enabled = selection.canGoPrevious
            ) {
                Text(
                    text = "Previous",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selection.canGoPrevious) {
                        IntentColors.AccentDark
                    } else {
                        IntentColors.Muted
                    }
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = selection.label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = IntentColors.Ink,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = selection.localDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
            }
            TextButton(
                onClick = onNext,
                enabled = selection.canGoNext
            ) {
                Text(
                    text = "Next",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selection.canGoNext) {
                        IntentColors.AccentDark
                    } else {
                        IntentColors.Muted
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = selection.honestyNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = DateControls.TRACKER_HONESTY_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
    }
}

@Composable
private fun TrackerEntryRow(
    entry: TrackerEntry,
    module: TrackerModuleDef,
    correcting: Boolean,
    nowEpochMs: Long,
    timeZone: TimeZone,
    daySelection: DateControls.DaySelection,
    onToggleCorrect: () -> Unit,
    onCorrectValue: (String) -> Unit,
    onDelete: () -> Unit
) {
    HeroCard(tone = HeroTone.Accent) {
        SmallLabel(
            text = provenanceChip(entry.provenance.kind),
            color = IntentColors.Accent
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = entry.valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Ink,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Source · ${entry.provenance.sourceLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = DateControls.entryDayLabel(entry.eventAtEpochMs, nowEpochMs, timeZone),
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(8.dp))
        IntentSecondaryButton(
            text = if (correcting) "Cancel correct" else "Correct entry",
            onClick = onToggleCorrect
        )
        Spacer(modifier = Modifier.height(4.dp))
        IntentQuietButton(text = "Delete entry", onClick = onDelete)
        if (correcting) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Corrected value for ${daySelection.label} — marks this row CORRECTED " +
                    "and inserts a new active correction row.",
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModuleFieldEntry(
                module = module.copy(
                    suggestedValues = module.suggestedValues.filter { it != entry.valueText }
                ),
                daySelection = daySelection,
                onSave = onCorrectValue
            )
        }
    }
}

private fun provenanceChip(kind: ProvenanceKind): String =
    when (kind) {
        ProvenanceKind.SAID -> "Reported"
        ProvenanceKind.OBSERVED -> "Recorded"
        ProvenanceKind.INFERRED -> "INFERRED"
    }
