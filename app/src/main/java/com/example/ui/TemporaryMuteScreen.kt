package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemporaryMuteScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val focusState by viewModel.focusState.collectAsState()
    val isFocusOn = focusState?.isFocusModeOn == true
    val isTemporary = focusState?.isTemporary == true
    val remainingTime by viewModel.remainingTimeFormatted.collectAsState()

    var selectedPresetMinutes by remember { mutableIntStateOf(60) } // Default 1 hour
    var customMinutesSlider by remember { mutableFloatStateOf(45f) }
    var isCustomSelected by remember { androidx.compose.runtime.mutableStateOf(false) }

    val activeMinutes = if (isCustomSelected) customMinutesSlider.toInt() else selectedPresetMinutes
    val calculatedEndTime = remember(activeMinutes) {
        val cal = Calendar.getInstance().apply {
            add(Calendar.MINUTE, activeMinutes)
        }
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Temporary Mute", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.HOME) },
                        modifier = Modifier.testTag("btn_back_from_temp_mute")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // If already actively running a temporary mute session:
            if (isFocusOn && isTemporary) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassBottom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Temporary Mute is Active",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = remainingTime,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.cancelTemporaryMute() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("End Now")
                            }
                            Button(
                                onClick = { viewModel.startTemporaryMute(activeMinutes + 30) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+30 Mins")
                            }
                        }
                    }
                }
            }

            Text(
                text = "Mute selected apps for:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Presets listed as scannable cards/options matching prompt example:
            // 30 minutes, 1 hour, 2 hours, Until tonight, Until tomorrow, Custom
            val tonightMinutes = calculateMinutesUntil(22, 0) // 10:00 PM tonight
            val tomorrowMinutes = calculateMinutesUntilTomorrow(8, 0) // 8:00 AM tomorrow

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PresetOptionRow(
                    label = "30 minutes",
                    subtitle = "Quick focus session",
                    isSelected = !isCustomSelected && selectedPresetMinutes == 30,
                    onClick = {
                        isCustomSelected = false
                        selectedPresetMinutes = 30
                    }
                )

                PresetOptionRow(
                    label = "1 hour",
                    subtitle = "Standard meeting / study",
                    isSelected = !isCustomSelected && selectedPresetMinutes == 60,
                    onClick = {
                        isCustomSelected = false
                        selectedPresetMinutes = 60
                    }
                )

                PresetOptionRow(
                    label = "2 hours",
                    subtitle = "Deep work block",
                    isSelected = !isCustomSelected && selectedPresetMinutes == 120,
                    onClick = {
                        isCustomSelected = false
                        selectedPresetMinutes = 120
                    }
                )

                PresetOptionRow(
                    label = "Until tonight",
                    subtitle = "Silenced until 10:00 PM",
                    isSelected = !isCustomSelected && selectedPresetMinutes == tonightMinutes,
                    onClick = {
                        isCustomSelected = false
                        selectedPresetMinutes = tonightMinutes
                    }
                )

                PresetOptionRow(
                    label = "Until tomorrow",
                    subtitle = "Silenced until 8:00 AM",
                    isSelected = !isCustomSelected && selectedPresetMinutes == tomorrowMinutes,
                    onClick = {
                        isCustomSelected = false
                        selectedPresetMinutes = tomorrowMinutes
                    }
                )

                PresetOptionRow(
                    label = "Custom duration",
                    subtitle = if (isCustomSelected) "${customMinutesSlider.toInt()} minutes" else "Choose custom minutes",
                    isSelected = isCustomSelected,
                    onClick = {
                        isCustomSelected = true
                    }
                )
            }

            // Custom Slider if custom is chosen
            if (isCustomSelected) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Custom duration:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = formatMinutesToHours(customMinutesSlider.toInt()),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = customMinutesSlider,
                            onValueChange = { customMinutesSlider = it },
                            valueRange = 10f..480f,
                            steps = 46 // 10-minute increments
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Calculated End Time Preview
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mute will end at:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = calculatedEndTime,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Start Button
            Button(
                onClick = { viewModel.startTemporaryMute(activeMinutes) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_start_temporary_mute")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "START",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PresetOptionRow(
    label: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.5.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun calculateMinutesUntil(targetHour: Int, targetMinute: Int): Int {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, targetHour)
        set(Calendar.MINUTE, targetMinute)
        set(Calendar.SECOND, 0)
        if (before(now)) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    val diffMillis = target.timeInMillis - now.timeInMillis
    return (diffMillis / (1000 * 60)).coerceAtLeast(15).toInt()
}

private fun calculateMinutesUntilTomorrow(targetHour: Int, targetMinute: Int): Int {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, targetHour)
        set(Calendar.MINUTE, targetMinute)
        set(Calendar.SECOND, 0)
    }
    val diffMillis = target.timeInMillis - now.timeInMillis
    return (diffMillis / (1000 * 60)).coerceAtLeast(60).toInt()
}

private fun formatMinutesToHours(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
