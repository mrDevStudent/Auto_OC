package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.MeetingCodeParser
import com.example.data.MeetingScheduleEntity
import com.example.data.UserPreferences
import com.example.ui.theme.JetBrainsMono

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditorSheet(
    existingSchedule: MeetingScheduleEntity?,
    defaults: UserPreferences,
    onDismiss: () -> Unit,
    onSave: (
        existingId: Long?,
        title: String,
        rawCode: String,
        hour: Int,
        minute: Int,
        daysBitmask: Int,
        autoMuteMic: Boolean,
        autoTurnOffCamera: Boolean,
        retryIntervalSeconds: Int
    ) -> String?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(existingSchedule?.title ?: "") }
    var codeInput by remember { mutableStateOf(existingSchedule?.meetingCode ?: "") }
    var selectedHour by remember { mutableIntStateOf(existingSchedule?.hour ?: 17) }
    var selectedMinute by remember { mutableIntStateOf(existingSchedule?.minute ?: 0) }
    var daysBitmask by remember { mutableIntStateOf(existingSchedule?.daysOfWeekBitmask ?: 0b0000001) }
    var autoMuteMic by remember {
        mutableStateOf(existingSchedule?.autoMuteMic ?: defaults.defaultAutoMuteMic)
    }
    var autoCamOff by remember {
        mutableStateOf(existingSchedule?.autoTurnOffCamera ?: defaults.defaultAutoCamOff)
    }
    var retrySeconds by remember {
        mutableIntStateOf(existingSchedule?.retryIntervalSeconds ?: defaults.defaultRetryIntervalSeconds)
    }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }

    val parsedCodePreview = remember(codeInput) {
        MeetingCodeParser.normalizeMeetCode(codeInput)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (existingSchedule == null) "New Auto-Join Schedule" else "Edit Schedule",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    errorText = null
                },
                label = { Text("Class or meeting name") },
                placeholder = { Text("e.g. Monday CS Lecture") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_title_input")
            )

            OutlinedTextField(
                value = codeInput,
                onValueChange = {
                    codeInput = it
                    errorText = null
                },
                label = { Text("Google Meet code or URL") },
                placeholder = { Text("xxx-xxxx-xxx or https://meet.google.com/...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.VideoCall,
                        contentDescription = "Google Meet code"
                    )
                },
                supportingText = {
                    if (parsedCodePreview != null) {
                        Text(
                            text = "Normalized link: meet.google.com/$parsedCodePreview",
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text("Paste a 10-letter code (xxx-xxxx-xxx) or full Meet link")
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_code_input")
            )

            Surface(
                onClick = { showTimePicker = true },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_time_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Pick meeting time",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Start time",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = MeetingCodeParser.formatTime12Hour(selectedHour, selectedMinute),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Repeat days (${MeetingCodeParser.formatDaysSummary(daysBitmask)})",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MeetingCodeParser.DAY_SHORT_LABELS.forEachIndexed { index, label ->
                        val selected = MeetingCodeParser.isDaySelected(daysBitmask, index)
                        Surface(
                            shape = CircleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable {
                                    daysBitmask = MeetingCodeParser.toggleDay(daysBitmask, index)
                                }
                                .testTag("day_chip_$index")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = daysBitmask == 0b0011111,
                        onClick = { daysBitmask = 0b0011111 },
                        label = { Text("Weekdays") }
                    )
                    FilterChip(
                        selected = daysBitmask == 0b1111111,
                        onClick = { daysBitmask = 0b1111111 },
                        label = { Text("Every day") }
                    )
                    FilterChip(
                        selected = daysBitmask == 0,
                        onClick = { daysBitmask = 0 },
                        label = { Text("One-time") }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Retry interval if timed out / not admitted yet",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(10, 15, 30, 60).forEach { sec ->
                        FilterChip(
                            selected = retrySeconds == sec,
                            onClick = { retrySeconds = sec },
                            label = { Text("${sec}s") }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-mute microphone", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Turns off mic before tapping Join",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoMuteMic,
                    onCheckedChange = { autoMuteMic = it },
                    modifier = Modifier.testTag("editor_mute_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-turn-off camera", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Turns off camera before tapping Join",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoCamOff,
                    onCheckedChange = { autoCamOff = it },
                    modifier = Modifier.testTag("editor_cam_switch")
                )
            }

            if (errorText != null) {
                Text(
                    text = errorText!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val err = onSave(
                            existingSchedule?.id,
                            title,
                            codeInput,
                            selectedHour,
                            selectedMinute,
                            daysBitmask,
                            autoMuteMic,
                            autoCamOff,
                            retrySeconds
                        )
                        if (err != null) {
                            errorText = err
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("editor_save_button")
                ) {
                    Text("Save Schedule")
                }
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select meeting start time") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedHour = timePickerState.hour
                        selectedMinute = timePickerState.minute
                        showTimePicker = false
                    },
                    modifier = Modifier.testTag("time_picker_confirm")
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
