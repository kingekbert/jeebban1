package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.example.util.JalaliDate
import com.example.util.toPersianDigits

private enum class DatePickerViewMode {
    CALENDAR,
    YEAR_GRID,
    MONTH_GRID,
    WHEEL
}

private const val MIN_JALALI_YEAR = 1350
private const val MAX_JALALI_YEAR = 1450

@Composable
fun JalaliDatePickerDialog(
    initialTimestamp: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val initialJalali = remember(initialTimestamp) { JalaliDate.fromTimestamp(initialTimestamp) }
    var selectedYear by remember(initialTimestamp) {
        mutableIntStateOf(initialJalali.year.coerceIn(MIN_JALALI_YEAR, MAX_JALALI_YEAR))
    }
    var selectedMonth by remember(initialTimestamp) { mutableIntStateOf(initialJalali.month.coerceIn(1, 12)) }
    var selectedDay by remember(initialTimestamp) { mutableIntStateOf(initialJalali.day) }

    val monthDaysCount = JalaliDate.getJalaliMonthLength(selectedYear, selectedMonth)
    if (selectedDay > monthDaysCount) {
        selectedDay = monthDaysCount
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            JalaliDatePickerContent(
                title = "انتخاب تاریخ شمسی",
                selectedYear = selectedYear,
                selectedMonth = selectedMonth,
                selectedDay = selectedDay,
                onYearChanged = { selectedYear = it.coerceIn(MIN_JALALI_YEAR, MAX_JALALI_YEAR) },
                onMonthChanged = { selectedMonth = it.coerceIn(1, 12) },
                onDayChanged = { selectedDay = it },
                onJumpToToday = {
                    val today = JalaliDate.today()
                    selectedYear = today.year.coerceIn(MIN_JALALI_YEAR, MAX_JALALI_YEAR)
                    selectedMonth = today.month
                    selectedDay = today.day
                }
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val safeDay = selectedDay.coerceIn(1, JalaliDate.getJalaliMonthLength(selectedYear, selectedMonth))
                    val jalali = JalaliDate(selectedYear, selectedMonth, safeDay)
                    onDateSelected(jalali.toStartOfDayTimestamp())
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("jalali_date_confirm_button")
            ) {
                Text("تأیید", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("jalali_date_cancel_button")
            ) {
                Text("انصراف")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JalaliDatePickerBottomSheet(
    initialTimestamp: Long = System.currentTimeMillis(),
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val initialJalali = remember(initialTimestamp) { JalaliDate.fromTimestamp(initialTimestamp) }
    var selectedYear by remember(initialTimestamp) {
        mutableIntStateOf(initialJalali.year.coerceIn(MIN_JALALI_YEAR, MAX_JALALI_YEAR))
    }
    var selectedMonth by remember(initialTimestamp) { mutableIntStateOf(initialJalali.month.coerceIn(1, 12)) }
    var selectedDay by remember(initialTimestamp) { mutableIntStateOf(initialJalali.day) }

    val monthDaysCount = JalaliDate.getJalaliMonthLength(selectedYear, selectedMonth)
    if (selectedDay > monthDaysCount) {
        selectedDay = monthDaysCount
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            JalaliDatePickerContent(
                title = "فیلتر بر اساس روز",
                selectedYear = selectedYear,
                selectedMonth = selectedMonth,
                selectedDay = selectedDay,
                onYearChanged = { selectedYear = it.coerceIn(MIN_JALALI_YEAR, MAX_JALALI_YEAR) },
                onMonthChanged = { selectedMonth = it.coerceIn(1, 12) },
                onDayChanged = { day ->
                    selectedDay = day
                },
                onJumpToToday = {
                    val today = JalaliDate.today()
                    selectedYear = today.year.coerceIn(MIN_JALALI_YEAR, MAX_JALALI_YEAR)
                    selectedMonth = today.month
                    selectedDay = today.day
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("انصراف")
                }

                Button(
                    onClick = {
                        val safeDay = selectedDay.coerceIn(1, JalaliDate.getJalaliMonthLength(selectedYear, selectedMonth))
                        val jalali = JalaliDate(selectedYear, selectedMonth, safeDay)
                        onDateSelected(jalali.toStartOfDayTimestamp())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("اعمال تاریخ", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun JalaliDatePickerContent(
    title: String,
    selectedYear: Int,
    selectedMonth: Int,
    selectedDay: Int,
    onYearChanged: (Int) -> Unit,
    onMonthChanged: (Int) -> Unit,
    onDayChanged: (Int) -> Unit,
    onJumpToToday: () -> Unit
) {
    val todayJalali = remember { JalaliDate.today() }
    var viewMode by remember { mutableStateOf(DatePickerViewMode.CALENDAR) }

    val monthDaysCount = JalaliDate.getJalaliMonthLength(selectedYear, selectedMonth)
    val safeDay = selectedDay.coerceIn(1, monthDaysCount)
    val currentJalali = remember(selectedYear, selectedMonth, safeDay) {
        JalaliDate(selectedYear, selectedMonth, safeDay)
    }
    val monthName = JalaliDate.getMonthName(selectedMonth)
    val yearsList = remember { (MIN_JALALI_YEAR..MAX_JALALI_YEAR).toList() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header with selected date preview + quick actions ("امروز" + Calendar/Wheel toggle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentJalali.format(includeDayName = true),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        onJumpToToday()
                        if (viewMode == DatePickerViewMode.YEAR_GRID || viewMode == DatePickerViewMode.MONTH_GRID) {
                            viewMode = DatePickerViewMode.CALENDAR
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.testTag("jalali_date_today_button")
                ) {
                    Text(
                        text = "امروز",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }

                val isWheel = viewMode == DatePickerViewMode.WHEEL
                Surface(
                    onClick = {
                        viewMode = if (isWheel) DatePickerViewMode.CALENDAR else DatePickerViewMode.WHEEL
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.testTag("jalali_date_mode_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isWheel) Icons.Default.CalendarMonth else Icons.Default.SwapVert,
                            contentDescription = if (isWheel) "نمای تقویم" else "نمای چرخشی",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isWheel) "تقویم" else "چرخشی",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Navigation bar (Month selector pill + Year selector pill + Prev/Next month arrows)
        if (viewMode != DatePickerViewMode.WHEEL) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (selectedMonth == 1) {
                            if (selectedYear > MIN_JALALI_YEAR) {
                                onMonthChanged(12)
                                onYearChanged(selectedYear - 1)
                            }
                        } else {
                            onMonthChanged(selectedMonth - 1)
                        }
                    },
                    modifier = Modifier.testTag("jalali_prev_month_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "ماه قبلی"
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clickable Month Pill -> instant 12-month grid
                    val isMonthPickerOpen = viewMode == DatePickerViewMode.MONTH_GRID
                    Surface(
                        onClick = {
                            viewMode = if (isMonthPickerOpen) DatePickerViewMode.CALENDAR else DatePickerViewMode.MONTH_GRID
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isMonthPickerOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.testTag("jalali_month_selector_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = monthName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isMonthPickerOpen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "انتخاب ماه",
                                tint = if (isMonthPickerOpen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Clickable Year Pill -> instant scrollable year grid
                    val isYearPickerOpen = viewMode == DatePickerViewMode.YEAR_GRID
                    Surface(
                        onClick = {
                            viewMode = if (isYearPickerOpen) DatePickerViewMode.CALENDAR else DatePickerViewMode.YEAR_GRID
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isYearPickerOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.testTag("jalali_year_selector_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedYear.toPersianDigits(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isYearPickerOpen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "انتخاب سال",
                                tint = if (isYearPickerOpen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        if (selectedMonth == 12) {
                            if (selectedYear < MAX_JALALI_YEAR) {
                                onMonthChanged(1)
                                onYearChanged(selectedYear + 1)
                            }
                        } else {
                            onMonthChanged(selectedMonth + 1)
                        }
                    },
                    modifier = Modifier.testTag("jalali_next_month_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "ماه بعدی"
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        AnimatedContent(
            targetState = viewMode,
            label = "jalali_date_picker_view_mode"
        ) { mode ->
            when (mode) {
                DatePickerViewMode.CALENDAR -> {
                    CalendarDaysGrid(
                        selectedYear = selectedYear,
                        selectedMonth = selectedMonth,
                        selectedDay = safeDay,
                        todayJalali = todayJalali,
                        onDaySelected = onDayChanged
                    )
                }

                DatePickerViewMode.YEAR_GRID -> {
                    YearSelectorGrid(
                        years = yearsList,
                        selectedYear = selectedYear,
                        currentYear = todayJalali.year,
                        onYearSelected = { year ->
                            onYearChanged(year)
                            viewMode = DatePickerViewMode.CALENDAR
                        }
                    )
                }

                DatePickerViewMode.MONTH_GRID -> {
                    MonthSelectorGrid(
                        selectedMonth = selectedMonth,
                        currentMonth = if (selectedYear == todayJalali.year) todayJalali.month else -1,
                        onMonthSelected = { month ->
                            onMonthChanged(month)
                            viewMode = DatePickerViewMode.CALENDAR
                        }
                    )
                }

                DatePickerViewMode.WHEEL -> {
                    JalaliWheelDateSelector(
                        years = yearsList,
                        selectedYear = selectedYear,
                        selectedMonth = selectedMonth,
                        selectedDay = safeDay,
                        onYearChanged = onYearChanged,
                        onMonthChanged = onMonthChanged,
                        onDayChanged = onDayChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDaysGrid(
    selectedYear: Int,
    selectedMonth: Int,
    selectedDay: Int,
    todayJalali: JalaliDate,
    onDaySelected: (Int) -> Unit
) {
    val daysOfWeek = remember { listOf("ش", "ی", "د", "س", "چ", "پ", "ج") }
    val monthDaysCount = JalaliDate.getJalaliMonthLength(selectedYear, selectedMonth)
    val emptyCellsCount = JalaliDate.getFirstDayOfWeekInMonth(selectedYear, selectedMonth)
    val totalCells = emptyCellsCount + monthDaysCount
    val rows = remember(selectedYear, selectedMonth) { (0 until totalCells).chunked(7) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            daysOfWeek.forEachIndexed { idx, dayName ->
                Text(
                    text = dayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (idx == 6) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            rows.forEach { rowIndices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowIndices.forEachIndexed { colIndex, index ->
                        if (index < emptyCellsCount) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        } else {
                            val day = index - emptyCellsCount + 1
                            val isSelected = day == selectedDay
                            val isToday = selectedYear == todayJalali.year &&
                                selectedMonth == todayJalali.month &&
                                day == todayJalali.day
                            val isFriday = colIndex == 6

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primaryContainer
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = if (isToday && !isSelected) 1.5.dp else 0.dp,
                                        color = if (isToday && !isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { onDaySelected(day) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.toPersianDigits(),
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                        isFriday -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                    val remainingInRow = 7 - rowIndices.size
                    repeat(remainingInRow) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearSelectorGrid(
    years: List<Int>,
    selectedYear: Int,
    currentYear: Int,
    onYearSelected: (Int) -> Unit
) {
    val selectedIdx = years.indexOf(selectedYear).coerceAtLeast(0)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = (selectedIdx - 6).coerceAtLeast(0))

    LaunchedEffect(selectedYear) {
        val target = (years.indexOf(selectedYear) - 6).coerceAtLeast(0)
        gridState.scrollToItem(target)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .testTag("jalali_year_grid"),
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(years) { year ->
            val isSelected = year == selectedYear
            val isCurrent = year == currentYear
            Surface(
                onClick = { onYearSelected(year) },
                shape = RoundedCornerShape(12.dp),
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                border = if (isCurrent && !isSelected) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                } else null
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = year.toPersianDigits(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isCurrent -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSelectorGrid(
    selectedMonth: Int,
    currentMonth: Int,
    onMonthSelected: (Int) -> Unit
) {
    val months = remember { (1..12).toList() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .testTag("jalali_month_grid"),
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(months) { month ->
            val isSelected = month == selectedMonth
            val isCurrent = month == currentMonth
            Surface(
                onClick = { onMonthSelected(month) },
                shape = RoundedCornerShape(12.dp),
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                border = if (isCurrent && !isSelected) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                } else null
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = JalaliDate.getMonthName(month),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isCurrent -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun JalaliWheelDateSelector(
    years: List<Int>,
    selectedYear: Int,
    selectedMonth: Int,
    selectedDay: Int,
    onYearChanged: (Int) -> Unit,
    onMonthChanged: (Int) -> Unit,
    onDayChanged: (Int) -> Unit
) {
    val monthLength = JalaliDate.getJalaliMonthLength(selectedYear, selectedMonth)
    val days = remember(monthLength) { (1..monthLength).toList() }
    val months = remember { (1..12).toList() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("jalali_wheel_picker"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "روز",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(0.85f)
            )
            Text(
                text = "ماه",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.3f)
            )
            Text(
                text = "سال",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WheelPicker(
                items = days,
                selectedIndex = (selectedDay - 1).coerceIn(0, days.lastIndex),
                onIndexChanged = { idx -> onDayChanged(days[idx]) },
                itemLabel = { it.toPersianDigits() },
                modifier = Modifier.weight(0.85f)
            )

            WheelPicker(
                items = months,
                selectedIndex = (selectedMonth - 1).coerceIn(0, months.lastIndex),
                onIndexChanged = { idx -> onMonthChanged(months[idx]) },
                itemLabel = { JalaliDate.getMonthName(it) },
                modifier = Modifier.weight(1.3f)
            )

            val yearIndex = years.indexOf(selectedYear).coerceIn(0, years.lastIndex)
            WheelPicker(
                items = years,
                selectedIndex = yearIndex,
                onIndexChanged = { idx -> onYearChanged(years[idx]) },
                itemLabel = { it.toPersianDigits() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
