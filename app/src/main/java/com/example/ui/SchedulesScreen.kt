package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.automation.AutoJoinPhase
import com.example.automation.AutoJoinSession
import com.example.data.MeetingCodeParser
import com.example.data.MeetingScheduleEntity
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusWaiting
import kotlinx.coroutines.delay

@Composable
fun SchedulesScreen(
    uiState: MeetPilotUiState,
    onAddClick: () -> Unit,
    onEditClick: (MeetingScheduleEntity) -> Unit,
    onToggleSchedule: (MeetingScheduleEntity, Boolean) -> Unit,
    onDeleteSchedule: (Long) -> Unit,
    onJoinNowClick: (MeetingScheduleEntity) -> Unit,
    onStopAutoJoin: () -> Unit,
    onDismissConnected: () -> Unit,
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(15_000L)
        }
    }

    var schedulePendingDelete by remember { mutableStateOf<MeetingScheduleEntity?>(null) }

    val nextScheduleWithTrigger = remember(uiState.schedules, nowMillis) {
        uiState.schedules
            .filter { it.isEnabled }
            .map { schedule ->
                schedule to MeetingCodeParser.computeNextTriggerMillis(
                    hour = schedule.hour,
                    minute = schedule.minute,
                    daysBitmask = schedule.daysOfWeekBitmask,
                    nowMillis = nowMillis
                )
            }
            .minByOrNull { it.second }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MeetPilot",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = uiState.readiness.deviceDisplayLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (uiState.readiness.coreReady) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        onClick = onNavigateToSetup,
                        modifier = Modifier.testTag("readiness_pill_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.readiness.coreReady) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.Warning
                                },
                                contentDescription = "Readiness status",
                                modifier = Modifier.size(16.dp),
                                tint = if (uiState.readiness.coreReady) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                }
                            )
                            Text(
                                text = "${uiState.readiness.readyCount}/5 Ready",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (uiState.readiness.coreReady) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                }
                            )
                        }
                    }
                }
            }

            item(key = "active_session_banner") {
                val showBanner = uiState.activeSession.phase.isActive() ||
                    uiState.activeSession.phase == AutoJoinPhase.CONNECTED
                AnimatedVisibility(
                    visible = showBanner,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    ActiveAutoJoinBanner(
                        session = uiState.activeSession,
                        onStop = onStopAutoJoin,
                        onDismissConnected = onDismissConnected
                    )
                }
            }

            if (!uiState.readiness.coreReady) {
                item(key = "setup_nudge") {
                    Card(
                        onClick = onNavigateToSetup,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_nudge_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Setup required",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Finish device setup for hands-free join",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Enable Auto-Join Accessibility, Exact Alarms & Notifications.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Go to setup",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            if (nextScheduleWithTrigger != null) {
                val (nextSchedule, triggerMillis) = nextScheduleWithTrigger
                item(key = "next_up_card") {
                    NextMeetingHeroCard(
                        schedule = nextSchedule,
                        countdown = MeetingCodeParser.formatCountdown(triggerMillis, nowMillis),
                        onJoinNow = { onJoinNowClick(nextSchedule) }
                    )
                }
            }

            if (uiState.schedules.isEmpty()) {
                item(key = "empty_state") {
                    EmptyScheduleHeroCard(onAddClick = onAddClick)
                }
            } else {
                item(key = "list_label") {
                    Text(
                        text = "CLASS & MEETING SCHEDULES (${uiState.schedules.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(
                    items = uiState.schedules,
                    key = { it.id }
                ) { schedule ->
                    ScheduleItemCard(
                        schedule = schedule,
                        onToggle = { enabled -> onToggleSchedule(schedule, enabled) },
                        onEdit = { onEditClick(schedule) },
                        onDelete = { schedulePendingDelete = schedule },
                        onJoinNow = { onJoinNowClick(schedule) }
                    )
                }
            }
        }
    }

    if (schedulePendingDelete != null) {
        val target = schedulePendingDelete!!
        AlertDialog(
            onDismissRequest = { schedulePendingDelete = null },
            title = { Text("Delete schedule?") },
            text = {
                Text("Remove \"${target.title}\" (${target.meetingCode}) and cancel its automatic alarm?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSchedule(target.id)
                        schedulePendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { schedulePendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ActiveAutoJoinBanner(
    session: AutoJoinSession,
    onStop: () -> Unit,
    onDismissConnected: () -> Unit
) {
    val isConnected = session.phase == AutoJoinPhase.CONNECTED
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val accentColor = if (isConnected) StatusConnected else StatusWaiting
    val containerColor = if (isConnected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_session_banner")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .alpha(if (isConnected) 1f else dotAlpha)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = when (session.phase) {
                        AutoJoinPhase.LAUNCHING_MEET -> "OPENING GOOGLE MEET"
                        AutoJoinPhase.LOOKING_FOR_JOIN_BUTTON -> "AUTO-TAPPING JOIN"
                        AutoJoinPhase.WAITING_FOR_ADMISSION -> "WAITING FOR HOST ADMISSION"
                        AutoJoinPhase.RETRYING -> "RETRYING (ATTEMPT #${session.attemptCount})"
                        AutoJoinPhase.CONNECTED -> "CONNECTED TO GOOGLE MEET"
                        else -> "AUTO-JOIN"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor
                )
            }

            Text(
                text = session.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = buildString {
                    append(session.meetingCode)
                    if (session.preferredGmail.isNotEmpty()) {
                        append(" • ")
                        append(session.preferredGmail)
                    }
                    append(" • ")
                    append(session.lastActionMessage)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isConnected) {
                FilledTonalButton(
                    onClick = onDismissConnected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dismiss_connected_button")
                ) {
                    Text("Dismiss")
                }
            } else {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stop_auto_join_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop auto-join",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Stop Auto-Join")
                }
            }
        }
    }
}

@Composable
private fun NextMeetingHeroCard(
    schedule: MeetingScheduleEntity,
    countdown: String,
    onJoinNow: () -> Unit
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer
        )
    )
    Card(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("next_meeting_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Next scheduled class",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "NEXT AUTO-JOIN",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = countdown,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = schedule.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = MeetingCodeParser.formatTime12Hour(schedule.hour, schedule.minute),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = schedule.meetingCode,
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        if (schedule.preferredGmail.isNotEmpty()) {
                            Text(
                                text = schedule.preferredGmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Button(
                        onClick = onJoinNow,
                        modifier = Modifier.testTag("next_join_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Join now",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Join Now")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyScheduleHeroCard(onAddClick: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_schedules_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_meet_hero),
                contentDescription = "MeetPilot schedule automation illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
            Text(
                text = "Hands-free Google Meet joining",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Set your class time (e.g. Monday 5:00 PM), meeting code, and Gmail account. MeetPilot opens Google Meet, turns off cam/mic, taps Join, retries until accepted, and alerts you once inside.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onAddClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_add_schedule_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add first schedule",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Add First Schedule")
            }
        }
    }
}

@Composable
private fun ScheduleItemCard(
    schedule: MeetingScheduleEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onJoinNow: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (schedule.isEnabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = BorderStroke(
            1.dp,
            if (schedule.isEnabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("schedule_card_${schedule.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = MeetingCodeParser.formatTime12Hour(schedule.hour, schedule.minute),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (schedule.isEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = schedule.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = schedule.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("schedule_toggle_${schedule.id}")
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = schedule.meetingCode,
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                if (schedule.autoMuteMic) {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "Auto-mute mic enabled",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (schedule.autoTurnOffCamera) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = "Auto camera off enabled",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Retry ${schedule.retryIntervalSeconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (schedule.preferredGmail.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Selected Gmail account",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = schedule.preferredGmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            DayPillsRow(daysBitmask = schedule.daysOfWeekBitmask)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onJoinNow,
                    modifier = Modifier.testTag("join_now_button_${schedule.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Join now",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Join Now")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_schedule_${schedule.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit schedule",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_schedule_${schedule.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete schedule",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPillsRow(daysBitmask: Int) {
    if (daysBitmask == 0) {
        Text(
            text = "One-time (next upcoming occurrence)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MeetingCodeParser.DAY_SHORT_LABELS.forEachIndexed { index, shortLabel ->
            val selected = MeetingCodeParser.isDaySelected(daysBitmask, index)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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
