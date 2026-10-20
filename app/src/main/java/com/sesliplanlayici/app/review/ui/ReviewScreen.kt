@file:OptIn(ExperimentalMaterial3Api::class)

package com.sesliplanlayici.app.review.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sesliplanlayici.app.R
import com.sesliplanlayici.app.capture.domain.TaskDraft
import com.sesliplanlayici.app.core.domain.TaskSource
import com.sesliplanlayici.app.core.ui.theme.SesliPlanlayiciTheme
import com.sesliplanlayici.app.review.domain.DayLabel
import com.sesliplanlayici.app.review.domain.ReviewItem
import com.sesliplanlayici.app.review.domain.ReviewRules
import com.sesliplanlayici.app.review.domain.dayLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TURKISH = Locale.forLanguageTag("tr-TR")
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", TURKISH)
private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", TURKISH)
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// Saving arrives with step B (Room); until then the button stays visible but disabled.
private const val SAVE_AVAILABLE = false

@Composable
fun ReviewScreen(onBack: () -> Unit, viewModel: ReviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReviewContent(
        state = state,
        onPickDate = viewModel::setDate,
        onPickTime = viewModel::setTime,
        onEdit = viewModel::startEditing,
        onSaveEdit = viewModel::saveEdit,
        onCancelEdit = viewModel::cancelEditing,
        onDelete = viewModel::delete,
        onAddManual = viewModel::addManual,
        onSpeakAgain = onBack,
    )
}

@Composable
fun ReviewContent(
    state: ReviewUiState,
    onPickDate: (Long, LocalDate) -> Unit,
    onPickTime: (Long, LocalTime?) -> Unit,
    onEdit: (Long) -> Unit,
    onSaveEdit: (Long, String, LocalDate?, LocalTime?) -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: (Long) -> Unit,
    onAddManual: () -> Unit,
    onSpeakAgain: () -> Unit,
) {
    // Which picker is open is short-lived UI state; the card data lives in the ViewModel.
    var datePickerFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var timePickerFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    fun leave() {
        if (state.hasChanges) confirmDiscard = true else onSpeakAgain()
    }
    BackHandler(enabled = state.hasChanges) { confirmDiscard = true }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (state.fromVoice) {
                        pluralStringResource(R.plurals.review_title, state.items.size, state.items.size)
                    } else {
                        stringResource(R.string.review_title_manual)
                    },
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = stringResource(R.string.review_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.items.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.review_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.items, key = { it.id }) { item ->
                    ReviewCard(
                        item = item,
                        now = state.now,
                        onPickDate = { datePickerFor = item.id },
                        onPickTime = { timePickerFor = item.id },
                        onEdit = { onEdit(item.id) },
                    )
                }
                item {
                    TextButton(onClick = onAddManual) {
                        Text(stringResource(R.string.review_add_manually))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(
                        if (state.canSave) R.string.review_save_coming else R.string.review_save_needs_days,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {},
                    enabled = SAVE_AVAILABLE && state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(R.string.review_save_all), style = MaterialTheme.typography.labelLarge)
                }
                TextButton(
                    onClick = ::leave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(stringResource(R.string.review_speak_again))
                }
            }
        }
    }

    state.items.firstOrNull { it.id == datePickerFor }?.let { item ->
        DayPickerDialog(
            initial = item.draft.date,
            today = state.today,
            onPick = { onPickDate(item.id, it) },
            onDismiss = { datePickerFor = null },
        )
    }
    state.items.firstOrNull { it.id == timePickerFor }?.let { item ->
        TimePickerDialog(
            initial = item.draft.time,
            onPick = { onPickTime(item.id, it) },
            onDismiss = { timePickerFor = null },
        )
    }
    state.items.firstOrNull { it.id == state.editingId }?.let { item ->
        EditTaskDialog(
            item = item,
            isNew = state.editingIsNew,
            today = state.today,
            onSave = { title, date, time -> onSaveEdit(item.id, title, date, time) },
            onDelete = { onDelete(item.id) },
            onCancel = onCancelEdit,
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.review_discard_title)) },
            text = { Text(stringResource(R.string.review_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onSpeakAgain()
                }) { Text(stringResource(R.string.review_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.review_cancel)) }
            },
        )
    }
}

@Composable
private fun ReviewCard(
    item: ReviewItem,
    now: LocalDateTime,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onEdit: () -> Unit,
) {
    val draft = item.draft
    val inPast = ReviewRules.isInPast(draft, now)
    val needsAttention = draft.date == null || draft.time == null || inPast
    val colors = MaterialTheme.colorScheme
    val title = draft.title.ifBlank { stringResource(R.string.review_new_task) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        border = if (needsAttention) BorderStroke(2.dp, colors.primary) else null,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                FilledIconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.background,
                        contentColor = colors.onSurface,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = stringResource(R.string.review_edit_content_description, title),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val date = draft.date
                if (date == null) {
                    MissingChip(stringResource(R.string.review_pick_day), onPickDate)
                } else {
                    ValueChip(dayText(dayLabel(date, now.toLocalDate())), onPickDate)
                }
                val time = draft.time
                if (time == null) {
                    MissingChip(stringResource(R.string.review_pick_time), onPickTime)
                } else {
                    ValueChip(time.format(TIME), onPickTime)
                }
            }

            when {
                draft.date == null -> Note(stringResource(R.string.review_no_day_note))
                inPast -> Note(stringResource(R.string.review_past_note))
                draft.time == null -> Note(stringResource(R.string.review_no_time_note))
            }
        }
    }
}

@Composable
private fun ValueChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** Dashed accent chip for a field the user still has to fill in (design 03). */
@Composable
private fun MissingChip(text: String, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.drawBehind {
            val stroke = 1.5.dp.toPx()
            drawRoundRect(
                color = accent,
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
            )
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Note(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun DayPickerDialog(
    initial: LocalDate?,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayMillis = today.toUtcMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.toUtcMillis(),
        // Days before today cannot be picked.
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMillis
            override fun isSelectableYear(year: Int) = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { onPick(it.toUtcDate()) }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.review_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.review_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun TimePickerDialog(
    initial: LocalTime?,
    onPick: (LocalTime?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial?.hour ?: 9,
        initialMinute = initial?.minute ?: 0,
        is24Hour = true,
    )
    // A plain Dialog: AlertDialog's width is too narrow for the clock face.
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.review_pick_time_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
                TimePicker(state = state)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (initial != null) {
                        TextButton(onClick = {
                            onPick(null)
                            onDismiss()
                        }) { Text(stringResource(R.string.review_clear_time)) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.review_cancel)) }
                    TextButton(onClick = {
                        onPick(LocalTime.of(state.hour, state.minute))
                        onDismiss()
                    }) { Text(stringResource(R.string.review_ok)) }
                }
            }
        }
    }
}

/**
 * Title, day and time in one place. Changes stay local until "Kaydet", so "Vazgeç" leaves the
 * card as it was.
 */
@Composable
private fun EditTaskDialog(
    item: ReviewItem,
    isNew: Boolean,
    today: LocalDate,
    onSave: (String, LocalDate?, LocalTime?) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var title by rememberSaveable(item.id) { mutableStateOf(item.draft.title) }
    var date by rememberSaveable(item.id) { mutableStateOf(item.draft.date) }
    var time by rememberSaveable(item.id) { mutableStateOf(item.draft.time) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(if (isNew) R.string.review_new_task else R.string.review_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(ReviewRules.MAX_TITLE_LENGTH) },
                    label = { Text(stringResource(R.string.review_field_title)) },
                    singleLine = true,
                    supportingText = { Text("${title.length} / ${ReviewRules.MAX_TITLE_LENGTH}") },
                )
                FieldRow(label = stringResource(R.string.review_field_day)) {
                    val picked = date
                    if (picked == null) {
                        MissingChip(stringResource(R.string.review_pick_day)) { pickingDate = true }
                    } else {
                        ValueChip(dayText(dayLabel(picked, today))) { pickingDate = true }
                    }
                }
                FieldRow(label = stringResource(R.string.review_field_time)) {
                    val picked = time
                    if (picked == null) {
                        MissingChip(stringResource(R.string.review_pick_time)) { pickingTime = true }
                    } else {
                        ValueChip(picked.format(TIME)) { pickingTime = true }
                    }
                }
                if (!isNew) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.review_delete), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title, date, time) },
            ) { Text(stringResource(R.string.review_save)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.review_cancel)) }
        },
    )

    if (pickingDate) {
        DayPickerDialog(initial = date, today = today, onPick = { date = it }, onDismiss = { pickingDate = false })
    }
    if (pickingTime) {
        TimePickerDialog(initial = time, onPick = { time = it }, onDismiss = { pickingTime = false })
    }
}

@Composable
private fun FieldRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        content()
    }
}

@Composable
private fun dayText(label: DayLabel): String {
    val date = label.date.format(SHORT_DATE)
    return when (label) {
        is DayLabel.Today -> stringResource(R.string.review_day_today, date)
        is DayLabel.Tomorrow -> stringResource(R.string.review_day_tomorrow, date)
        is DayLabel.Other -> stringResource(
            R.string.review_day_other,
            label.date.format(WEEKDAY).replaceFirstChar { it.titlecase(TURKISH) },
            date,
        )
    }
}

// DatePicker works in UTC midnight milliseconds.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private val previewNow = LocalDateTime.of(2026, 10, 11, 21, 40)

@Preview(showBackground = true, heightDp = 844)
@Composable
private fun ReviewContentPreview() {
    SesliPlanlayiciTheme {
        ReviewContent(
            state = ReviewUiState(
                items = listOf(
                    ReviewItem(1, TaskDraft("Maç izle", LocalDate.of(2026, 10, 12), LocalTime.of(10, 0), 120, null), TaskSource.VOICE),
                    ReviewItem(2, TaskDraft("Market alışverişi", LocalDate.of(2026, 10, 12), LocalTime.of(15, 30), 45, null), TaskSource.VOICE),
                    ReviewItem(3, TaskDraft("Annemi ara", LocalDate.of(2026, 10, 12), null, null, null), TaskSource.VOICE),
                    ReviewItem(4, TaskDraft("Dişçi", null, null, null, null), TaskSource.VOICE),
                ),
                now = previewNow,
            ),
            onPickDate = { _, _ -> },
            onPickTime = { _, _ -> },
            onEdit = {},
            onSaveEdit = { _, _, _, _ -> },
            onCancelEdit = {},
            onDelete = {},
            onAddManual = {},
            onSpeakAgain = {},
        )
    }
}
