package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.DateFormatter
import com.example.util.toPersianDigits

@Composable
fun FinTrackTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedHour by remember(initialHour) { mutableIntStateOf(initialHour.coerceIn(0, 23)) }
    var selectedMinute by remember(initialMinute) { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }
    var isKeyboardInput by remember { mutableStateOf(false) }

    var hourText by remember(initialHour) { mutableStateOf(selectedHour.toString().padStart(2, '0')) }
    var minuteText by remember(initialMinute) { mutableStateOf(selectedMinute.toString().padStart(2, '0')) }

    val hours = remember { (0..23).toList() }
    val minutes = remember { (0..59).toList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Title + Digital Preview + "اکنون" + Wheel/Keyboard Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "انتخاب ساعت تراکنش",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = DateFormatter.formatTime(selectedHour, selectedMinute),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = {
                                val (nowH, nowM) = DateFormatter.extractHourAndMinute(System.currentTimeMillis())
                                selectedHour = nowH
                                selectedMinute = nowM
                                hourText = nowH.toString().padStart(2, '0')
                                minuteText = nowM.toString().padStart(2, '0')
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.testTag("time_picker_now_button")
                        ) {
                            Text(
                                text = "اکنون",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }

                        Surface(
                            onClick = {
                                isKeyboardInput = !isKeyboardInput
                                hourText = selectedHour.toString().padStart(2, '0')
                                minuteText = selectedMinute.toString().padStart(2, '0')
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier.testTag("time_picker_mode_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isKeyboardInput) Icons.Default.SwapVert else Icons.Default.Keyboard,
                                    contentDescription = if (isKeyboardInput) "انتخاب چرخشی" else "ورود عددی",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isKeyboardInput) "چرخشی" else "تایپ",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Always keep Hour on the LEFT and Minute on the RIGHT (standard HH:MM convention)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    AnimatedContent(
                        targetState = isKeyboardInput,
                        label = "time_picker_input_mode"
                    ) { keyboardMode ->
                        if (keyboardMode) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .testTag("time_picker_keyboard_input"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(96.dp)
                                    ) {
                                        Text(
                                            text = "ساعت (۰۰-۲۳)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = hourText,
                                            onValueChange = { raw ->
                                                val digits = normalizeDigitsToEnglish(raw).filter { it.isDigit() }.take(2)
                                                hourText = digits
                                                val parsed = digits.toIntOrNull()
                                                if (parsed != null && parsed in 0..23) {
                                                    selectedHour = parsed
                                                }
                                            },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            textStyle = TextStyle(
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.testTag("time_hour_input")
                                        )
                                    }

                                    Text(
                                        text = ":",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 18.dp)
                                    )

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(96.dp)
                                    ) {
                                        Text(
                                            text = "دقیقه (۰۰-۵۹)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = minuteText,
                                            onValueChange = { raw ->
                                                val digits = normalizeDigitsToEnglish(raw).filter { it.isDigit() }.take(2)
                                                minuteText = digits
                                                val parsed = digits.toIntOrNull()
                                                if (parsed != null && parsed in 0..59) {
                                                    selectedMinute = parsed
                                                }
                                            },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            textStyle = TextStyle(
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.testTag("time_minute_input")
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("time_picker_wheel_input"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ساعت",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(24.dp))
                                    Text(
                                        text = "دقیقه",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    WheelPicker(
                                        items = hours,
                                        selectedIndex = selectedHour,
                                        onIndexChanged = { idx ->
                                            selectedHour = hours[idx]
                                            hourText = selectedHour.toString().padStart(2, '0')
                                        },
                                        itemLabel = { it.toString().padStart(2, '0').toPersianDigits() },
                                        modifier = Modifier.weight(1f)
                                    )

                                    Text(
                                        text = ":",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(24.dp)
                                    )

                                    WheelPicker(
                                        items = minutes,
                                        selectedIndex = selectedMinute,
                                        onIndexChanged = { idx ->
                                            selectedMinute = minutes[idx]
                                            minuteText = selectedMinute.toString().padStart(2, '0')
                                        },
                                        itemLabel = { it.toString().padStart(2, '0').toPersianDigits() },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalHour = if (isKeyboardInput) {
                        hourText.toIntOrNull()?.coerceIn(0, 23) ?: selectedHour
                    } else selectedHour
                    val finalMinute = if (isKeyboardInput) {
                        minuteText.toIntOrNull()?.coerceIn(0, 59) ?: selectedMinute
                    } else selectedMinute
                    onTimeSelected(finalHour, finalMinute)
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("time_picker_confirm_button")
            ) {
                Text("تأیید", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("time_picker_cancel_button")
            ) {
                Text("انصراف", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

private fun normalizeDigitsToEnglish(input: String): String {
    val persian = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    val arabic = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    var result = input
    for (i in 0..9) {
        result = result.replace(persian[i], ('0' + i)).replace(arabic[i], ('0' + i))
    }
    return result
}
