package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.RepeatAlarm
import java.time.LocalTime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val alarms by viewModel.allAlarms.collectAsStateWithLifecycle()
    val permissionStates by viewModel.permissionStates.collectAsStateWithLifecycle()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedAlarmForEdit by remember { mutableStateOf<RepeatAlarm?>(null) }

    // Refresh permission states on screen load and wake
    LaunchedEffect(Unit) {
        viewModel.updatePermissionStates(context)
        viewModel.syncAlarmsWithSystem(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Repeat Alarms",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("app_title")
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedAlarmForEdit = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_alarm_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Alarm")
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Permissions Warning Banner Block (Display ONLY if user has not configured all settings)
            val issuesCount = listOf(
                permissionStates.isBatteryOptimizationsIgnored,
                permissionStates.canDrawOverlays,
                permissionStates.canScheduleExact
            ).count { !it }

            if (issuesCount > 0) {
                item {
                    PermissionsCard(
                        status = permissionStates,
                        onRequestBattery = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        },
                        onRequestOverlay = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        },
                        onRequestExact = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val intent = Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }

            // Section title
            item {
                Text(
                    text = "My Reminders",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            // Alarms list
            if (alarms.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = "Empty icon",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Repeat Alarms Yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the '+' icon to register your first recurring interval.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmItemCard(
                        alarm = alarm,
                        onToggle = { viewModel.toggleAlarmActive(context, alarm) },
                        onDelete = { viewModel.deleteAlarm(context, alarm) },
                        onEdit = {
                            selectedAlarmForEdit = alarm
                            showAddEditDialog = true
                        },
                        onQuickIntervalAdjust = { delta ->
                            val currentInterval = alarm.intervalSeconds
                            val newInterval = (currentInterval + delta).coerceAtLeast(1L)
                            viewModel.quickUpdateAlarmInterval(context, alarm, newInterval)
                        }
                    )
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditAlarmDialog(
            alarm = selectedAlarmForEdit,
            onDismiss = { showAddEditDialog = false },
            onSave = { alarmToSave ->
                viewModel.saveAlarm(context, alarmToSave)
                showAddEditDialog = false
            }
        )
    }
}

@Composable
fun DashboardHeaderCard(firstActive: RepeatAlarm?) {
    val hasActive = firstActive != null
    val nextTimeStr = if (hasActive) {
        val now = java.time.LocalTime.now()
        val startLocalTime = java.time.LocalTime.of(firstActive!!.startHour, firstActive.startMinute)
        val endLocalTime = java.time.LocalTime.of(firstActive.endHour, firstActive.endMinute)
        if (now.isBefore(startLocalTime)) {
            String.format(java.util.Locale.getDefault(), "%02d:%02d", firstActive.startHour, firstActive.startMinute)
        } else if (now.isAfter(endLocalTime)) {
            String.format(java.util.Locale.getDefault(), "%02d:%02d", firstActive.startHour, firstActive.startMinute)
        } else {
            val elapsedSecs = (System.currentTimeMillis() - firstActive.lastTriggeredTime) / 1000
            val remaining = (firstActive.intervalSeconds - elapsedSecs).coerceIn(0, firstActive.intervalSeconds)
            if (firstActive.lastTriggeredTime == 0L || remaining <= 0) {
                "Active"
            } else {
                val mins = remaining / 60
                val secs = remaining % 60
                if (mins > 0) {
                    String.format(java.util.Locale.getDefault(), "%02d:%02d", mins, secs)
                } else {
                    String.format(java.util.Locale.getDefault(), "0s:%02d", secs)
                }
            }
        }
    } else {
        "--:--"
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFEADDFF),
            contentColor = Color(0xFF21005D)
        ),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "NEXT ALERT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = Color(0xFF21005D).copy(alpha = 0.7f)
                    )
                    Text(
                        text = nextTimeStr,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 44.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        ),
                        color = Color(0xFF21005D)
                    )
                }
                // Styled Switch replica from mockup
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF21005D))
                        .padding(2.dp),
                    contentAlignment = if (hasActive) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEADDFF))
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider(color = Color(0xFF21005D).copy(alpha = 0.1f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1.5f)) {
                    Text(
                        text = "INTERVAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF21005D).copy(alpha = 0.6f)
                    )
                    Text(
                        text = firstActive?.getIntervalString() ?: "No custom timer active",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF21005D)
                    )
                }
                Column(
                    modifier = Modifier.weight(1.5f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "ACTIVE WINDOW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF21005D).copy(alpha = 0.6f)
                    )
                    Text(
                        text = if (hasActive) {
                            String.format(
                                java.util.Locale.getDefault(),
                                "%02d:%02d - %02d:%02d",
                                firstActive!!.startHour,
                                firstActive.startMinute,
                                firstActive.endHour,
                                firstActive.endMinute
                            )
                        } else {
                            "Inactive"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF21005D)
                    )
                }
            }
        }
    }
}

@Composable
fun PixelOptimizationNoticeCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF381E72).copy(alpha = 0.15f)
        ),
        border = BorderStroke(1.dp, Color(0xFF381E72)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Notification Exemption Detail",
                tint = Color(0xFFD0BCFF),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Screen timeout will be forced to 5 seconds when ringing to preserve battery life during recurring high-frequency cycles.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD0BCFF),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun PermissionsCard(
    status: PermissionStatus,
    onRequestBattery: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestExact: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val issuesCount = listOf(
        status.isBatteryOptimizationsIgnored,
        status.canDrawOverlays,
        status.canScheduleExact
    ).count { !it }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (issuesCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            ) {
                Icon(
                    imageVector = if (issuesCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = "Permission Status Icon",
                    tint = if (issuesCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (issuesCount > 0) "$issuesCount Android Exemption Required" else "Background Settings Optimized",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (issuesCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (issuesCount > 0) "Tap to optimize for Pixel 10 limitations" else "App has all precise privileges to fire alarms instantly",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (issuesCount > 0) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle Details",
                    tint = if (issuesCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExpanded || issuesCount > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = if (issuesCount > 0) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                // 1. Battery optimizations
                PermissionItem(
                    title = "Battery Unrestricted Mode",
                    description = "Allows the background scheduler to wake up without delay during standby hours.",
                    isGranted = status.isBatteryOptimizationsIgnored,
                    onRequestSetting = onRequestBattery
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Draw over other apps
                PermissionItem(
                    title = "Draw Over Other Apps",
                    description = "Allows the app launch full screen overlay with single tap dismiss buttons instantly even when keyguard is locked.",
                    isGranted = status.canDrawOverlays,
                    onRequestSetting = onRequestOverlay
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(modifier = Modifier.height(12.dp))
                    // 3. Exact alarm
                    PermissionItem(
                        title = "Alarms & Reminders Engine",
                        description = "Enables system RTC wakeup exact scheduling blocks for alarms scheduled >= 60 seconds.",
                        isGranted = status.canScheduleExact,
                        onRequestSetting = onRequestExact
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequestSetting: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = "Status",
            tint = if (isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (!isGranted) {
            Button(
                onClick = onRequestSetting,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(32.dp)
                    .align(Alignment.CenterVertically),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("OPTIMIZE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .height(28.dp)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = "ACTIVE",
                    color = Color(0xFF2E7D32),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun AlarmItemCard(
    alarm: RepeatAlarm,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onQuickIntervalAdjust: (Long) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_card_${alarm.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title, edit/delete, toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AccessAlarm,
                    contentDescription = "Alarm icon",
                    tint = if (alarm.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = alarm.name.ifEmpty { "Repeat Alarm" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (alarm.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Active hours: ${String.format(Locale.getDefault(), "%02d:%02d", alarm.startHour, alarm.startMinute)} to ${String.format(Locale.getDefault(), "%02d:%02d", alarm.endHour, alarm.endMinute)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Alarm", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Alarm", tint = MaterialTheme.colorScheme.error)
                }
                Switch(
                    checked = alarm.isActive,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("alarm_switch_${alarm.id}")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))

            // Body info and quick adjustments
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Interval Detail",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = alarm.getIntervalString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Ring Mode: ${alarm.ringMode} | Sound: ${alarm.soundName} | Vibe: ${alarm.vibrationPattern}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                // Quick Adjust Panel
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Quick Adjust",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clickable { onQuickIntervalAdjust(-10) }
                                .height(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("-10s", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clickable { onQuickIntervalAdjust(-60) }
                                .height(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("-1m", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clickable { onQuickIntervalAdjust(60) }
                                .height(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("+1m", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clickable { onQuickIntervalAdjust(10) }
                                .height(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("+10s", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditAlarmDialog(
    alarm: RepeatAlarm?,
    onDismiss: () -> Unit,
    onSave: (RepeatAlarm) -> Unit
) {
    var name by remember { mutableStateOf(alarm?.name ?: "") }
    
    // Interval split fields
    var daysText by remember { mutableStateOf(if (alarm != null) (alarm.intervalSeconds / 86400).toString() else "0") }
    var hoursText by remember { mutableStateOf(if (alarm != null) ((alarm.intervalSeconds % 86400) / 3600).toString() else "0") }
    var minutesText by remember { mutableStateOf(if (alarm != null) ((alarm.intervalSeconds % 3600) / 60).toString() else "0") }
    var secondsText by remember { mutableStateOf(if (alarm != null) (alarm.intervalSeconds % 60).toString() else "10") }

    // Start Hours
    var startHourText by remember { mutableStateOf(if (alarm != null) alarm.startHour.toString() else "0") }
    var startMinuteText by remember { mutableStateOf(if (alarm != null) alarm.startMinute.toString() else "0") }

    // End Hours
    var endHourText by remember { mutableStateOf(if (alarm != null) alarm.endHour.toString() else "23") }
    var endMinuteText by remember { mutableStateOf(if (alarm != null) alarm.endMinute.toString() else "59") }

    // Sound and vibration patterns
    var soundUri by remember { mutableStateOf(alarm?.soundUri ?: "") }
    var soundName by remember { mutableStateOf(alarm?.soundName ?: "Default Alarm") }
    var vibrationPattern by remember { mutableStateOf(alarm?.vibrationPattern ?: "Standard") }
    var ringMode by remember { mutableStateOf(alarm?.ringMode ?: "Alarm") }

    var expandedVibeMenu by remember { mutableStateOf(false) }
    var expandedRingModeMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                soundUri = uri.toString()
                soundName = try {
                    val ringtone = RingtoneManager.getRingtone(context, uri)
                    ringtone.getTitle(context) ?: "Custom Sound"
                } catch (e: Exception) {
                    "Custom Sound"
                }
            } else {
                soundUri = ""
                soundName = "Silent"
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = if (alarm == null) "Create Repeat Alarm" else "Edit Repeat Alarm",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Name field
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Alarm Name") },
                        placeholder = { Text("e.g., Hydration reminder, Stand up") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Interval section
                item {
                    Text("Time Interval Duration", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("Trigger recurrences after every custom length of days/hours/mins/seconds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = daysText,
                            onValueChange = { daysText = it.filter { char -> char.isDigit() } },
                            label = { Text("Days") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = hoursText,
                            onValueChange = { hoursText = it.filter { char -> char.isDigit() } },
                            label = { Text("Hours") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = minutesText,
                            onValueChange = { minutesText = it.filter { char -> char.isDigit() } },
                            label = { Text("Mins") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = secondsText,
                            onValueChange = { secondsText = it.filter { char -> char.isDigit() } },
                            label = { Text("Secs") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Start / Stop boundaries to curb scheduling at night automatically
                item {
                    Text("Daily Active Hours Selection", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("The alarm will skip ringing if the time lies outside active boundaries (e.g. overnight 22:00 to 07:00).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Hour:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = startHourText,
                            onValueChange = { startHourText = it.filter { char -> char.isDigit() } },
                            label = { Text("HH (0-23)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Text(":", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = startMinuteText,
                            onValueChange = { startMinuteText = it.filter { char -> char.isDigit() } },
                            label = { Text("MM (0-59)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("End Hour:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = endHourText,
                            onValueChange = { endHourText = it.filter { char -> char.isDigit() } },
                            label = { Text("HH (0-23)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Text(":", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = endMinuteText,
                            onValueChange = { endMinuteText = it.filter { char -> char.isDigit() } },
                            label = { Text("MM (0-59)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Customizable Alarm Sound block
                item {
                    Text("Alarm Sound", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    if (soundUri.isNotEmpty()) {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(soundUri))
                                    }
                                }
                                ringtonePickerLauncher.launch(intent)
                            }
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Music note icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = soundName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap to customize alarm sound",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Arrow",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Ring Mode customization block
                item {
                    Text("Ring Output Mode", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("Choose whether this alarm overrides silent/vibrate profiles or respects phone ringer settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedTextField(
                            value = if (ringMode == "Alarm") "Alarm Mode (Always Ring Aloud)" else "Ring Mode (Respect Silent/Vibrate)",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { expandedRingModeMenu = true }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "dropdown")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedRingModeMenu = true }
                        )
                        DropdownMenu(
                            expanded = expandedRingModeMenu,
                            onDismissRequest = { expandedRingModeMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Alarm Mode", fontWeight = FontWeight.Bold)
                                        Text("Always sound aloud (ignores silent/vibrate profiles)", style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    ringMode = "Alarm"
                                    expandedRingModeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Ring Mode", fontWeight = FontWeight.Bold)
                                        Text("Respect system settings (vibrates if in vibrate, silent if in silent)", style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    ringMode = "Ring"
                                    expandedRingModeMenu = false
                                }
                            )
                        }
                    }
                }

                // Vibration customization block
                item {
                    Text("Vibration Rhythm Pattern", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Box {
                        OutlinedTextField(
                            value = vibrationPattern,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { expandedVibeMenu = true }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "dropdown")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedVibeMenu = true }
                        )
                        DropdownMenu(
                            expanded = expandedVibeMenu,
                            onDismissRequest = { expandedVibeMenu = false }
                        ) {
                            val items = listOf("Standard", "Pulse", "Tick-Tock", "None")
                            items.forEach { rhythm ->
                                DropdownMenuItem(
                                    text = { Text(rhythm) },
                                    onClick = {
                                        vibrationPattern = rhythm
                                        expandedVibeMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Buttons Row
                item {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("CANCEL")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                // Accumulate totals
                                val d = daysText.toIntOrNull() ?: 0
                                val h = hoursText.toIntOrNull() ?: 0
                                val m = minutesText.toIntOrNull() ?: 0
                                val s = secondsText.toIntOrNull() ?: 0
                                
                                val totalSeconds = (((d * 24L + h) * 60L + m) * 60L) + s
                                val realInterval = totalSeconds.coerceAtLeast(1L) // Min limit 1 second

                                val sH = (startHourText.toIntOrNull() ?: 0).coerceIn(0, 23)
                                val sM = (startMinuteText.toIntOrNull() ?: 0).coerceIn(0, 59)
                                val eH = (endHourText.toIntOrNull() ?: 23).coerceIn(0, 23)
                                val eM = (endMinuteText.toIntOrNull() ?: 59).coerceIn(0, 59)

                                val finalAlarm = (alarm ?: RepeatAlarm(name = name, intervalSeconds = realInterval)).copy(
                                    name = name,
                                    intervalSeconds = realInterval,
                                    startHour = sH,
                                    startMinute = sM,
                                    endHour = eH,
                                    endMinute = eM,
                                    soundUri = soundUri,
                                    soundName = soundName,
                                    vibrationPattern = vibrationPattern,
                                    ringMode = ringMode,
                                    isActive = alarm?.isActive ?: true, // Maintain active state if editing
                                    lastTriggeredTime = 0L // Reset timing state on save to trigger immediately
                                )
                                onSave(finalAlarm)
                            }
                        ) {
                            Text("SAVE ALARM")
                        }
                    }
                }
            }
        }
    }
}
