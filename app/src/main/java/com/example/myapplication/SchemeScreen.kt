package com.example.myapplication

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

private const val SCHEME_FILE = "scheme_v2.json"
private const val SCENE_W = 600f
private const val SCENE_H = 400f
private const val DOT_RATIO = 0.3f

private val json = Json { ignoreUnknownKeys = true }

// --- ЦВЕТОВАЯ ПАЛИТРА (светлая, лавандово-фиолетовая) ---
private val AppBackground = Color(0xFFF6F1FB)   // почти белый с лавандовым отливом
private val AppSurface = Color(0xFFFFFFFF)      // карточки, панели, диалоги — белые
private val AppGrid = Color(0xFF9C27B0)         // нежная серо-лавандовая сетка
private val AppBorder = Color(0xFFB191F3)       // спокойные сиреневые рамки
private val AppText = Color(0xFF2A1B3D)         // тёмная слива — основной текст
private val AppTextDim = Color(0xFF7A6A90)      // приглушённый серо-фиолетовый
private val AppAccent = Color(0xFF7C3AED)       // насыщенный фиолетовый акцент
private val AppError = Color(0xFFE5484D)        // сигнальный красный


private fun parseColor(hex: String): Color = try {
    Color(hex.toColorInt())
} catch (_: Exception) {
    AppAccent
}

private fun createSampleScheme(): Scheme = json.decodeFromString(
    """
    {
      "projectName": "Танец Весна",
      "videoPath": "/test.mp4",
      "createdAt": "2026-05-15T14:30:00",
      "settings": { "speed": 1.0, "mirror": false },
      "dancers": [
        { "id": 1, "name": "Анна", "color": "#9B4DFF" },
        { "id": 2, "name": "Иван", "color": "#FF5E43" }
      ],
      "formations": [
        {
          "id": 1, "name": "Первый круг", "type": "circle", "startTime": 0.0, "endTime": 5.0,
          "points": [
            { "dancerId": 1, "x": 150.0, "y": 150.0 },
            { "dancerId": 2, "x": 400.0, "y": 300.0 }
          ]
        }
      ]
    }
    """.trimIndent()
)

private fun loadScheme(context: Context): Scheme? = try {
    val file = File(context.filesDir, SCHEME_FILE)
    if (file.exists()) json.decodeFromString(file.readText()) else null
} catch (_: Exception) {
    null
}

private fun saveScheme(context: Context, scheme: Scheme): Boolean = try {
    File(context.filesDir, SCHEME_FILE).writeText(json.encodeToString(Scheme.serializer(), scheme))
    true
} catch (_: Exception) {
    false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchemeScreen() {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()

    val baseScheme = remember { loadScheme(context) ?: createSampleScheme() }
    val dancers = remember { mutableStateListOf(*baseScheme.dancers.toTypedArray()) }
    val formations = remember { mutableStateListOf(*baseScheme.formations.toTypedArray()) }

    var currentFormationIndex by remember { mutableIntStateOf(0) }
    val points = remember(currentFormationIndex) {
        val f = formations.getOrNull(currentFormationIndex)
        mutableStateListOf(*(f?.points?.toTypedArray() ?: emptyArray()))
    }

    var selectedPointIndex by remember { mutableIntStateOf(-1) }
    var gridSizeX by remember { mutableIntStateOf(10) }
    var projectName by remember { mutableStateOf(baseScheme.projectName) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showFormationMenu by remember { mutableStateOf(false) }
    var showRenameFormationDialog by remember { mutableStateOf(false) }
    var newFormationName by remember { mutableStateOf("") }
    var showActionDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    fun syncCurrentPoints() {
        val idx = currentFormationIndex
        if (idx in formations.indices) {
            formations[idx] = formations[idx].copy(points = points.toList())
        }
    }

    fun selectFormation(index: Int) {
        if (index == currentFormationIndex) return
        syncCurrentPoints()
        selectedPointIndex = -1
        currentFormationIndex = index
    }

    fun addFormation() {
        syncCurrentPoints()
        val newId = (formations.maxOfOrNull { it.id } ?: 0) + 1
        val source = formations.getOrNull(currentFormationIndex)
        formations.add(
            Formation(
                id = newId,
                name = "Формация $newId",
                type = source?.type ?: "circle",
                startTime = source?.startTime ?: 0.0f,
                endTime = source?.endTime ?: 5.0f,
                points = source?.points?.map { it.copy() } ?: emptyList()
            )
        )
        selectedPointIndex = -1
        currentFormationIndex = formations.lastIndex
    }

    fun deleteFormation(index: Int) {
        if (formations.size <= 1) {
            Toast.makeText(context, "Нельзя удалить последнюю формацию", Toast.LENGTH_SHORT).show()
            return
        }
        val wasCurrent = index == currentFormationIndex
        if (!wasCurrent) syncCurrentPoints()
        formations.removeAt(index)
        selectedPointIndex = -1
        currentFormationIndex = when {
            wasCurrent -> index.coerceAtMost(formations.lastIndex)
            currentFormationIndex > index -> currentFormationIndex - 1
            else -> currentFormationIndex
        }
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Схема: $projectName") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppSurface,
                    titleContentColor = AppText
                ),
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Настройки",
                            tint = AppTextDim
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Название формации над сценой ---
            val currentFormation = formations.getOrNull(currentFormationIndex)
            Text(
                text = currentFormation?.name ?: "",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppAccent
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // --- СЦЕНА (1.5:1) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .shadow(6.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppSurface)
                    .pointerInput(currentFormationIndex, gridSizeX) {
                        detectTapGestures { offset ->
                            val scale = size.width / SCENE_W
                            val cellPx = size.width / gridSizeX
                            val thresholdPx = cellPx * DOT_RATIO * 1.4f
                            val vx = offset.x / scale
                            val vy = offset.y / scale
                            selectedPointIndex = points.indexOfFirst { point ->
                                hypot(point.x - vx, point.y - vy) * scale < thresholdPx
                            }
                            if (selectedPointIndex != -1) showActionDialog = true
                        }
                    }
                    .pointerInput(currentFormationIndex, gridSizeX) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val scale = size.width / SCENE_W
                                val cellPx = size.width / gridSizeX
                                val thresholdPx = cellPx * DOT_RATIO * 1.4f
                                val vx = offset.x / scale
                                val vy = offset.y / scale
                                selectedPointIndex = points.indexOfFirst { point ->
                                    hypot(point.x - vx, point.y - vy) * scale < thresholdPx
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (selectedPointIndex != -1) {
                                    val scale = size.width / SCENE_W
                                    val oldPoint = points[selectedPointIndex]
                                    points[selectedPointIndex] = oldPoint.copy(
                                        x = (oldPoint.x + dragAmount.x / scale).coerceIn(0f, SCENE_W),
                                        y = (oldPoint.y + dragAmount.y / scale).coerceIn(0f, SCENE_H)
                                    )
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val scale = size.width / SCENE_W
                    val cellPx = size.width / gridSizeX
                    val radius = cellPx * DOT_RATIO

                    drawGrid(gridSizeX)

                    points.forEach { point ->
                        val dancer = dancers.find { it.id == point.dancerId }
                        val color = parseColor(dancer?.color ?: "#9B4DFF")

                        val center = Offset(point.x * scale, point.y * scale)

                        // Простая мягкая тень под точкой (объём без градиентов)
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.25f),
                            radius = radius,
                            center = Offset(center.x + 2f, center.y + 3f)
                        )
                        drawCircle(color = color, radius = radius, center = center)
                        // Тонкая светлая обводка, чтобы точка читалась на тёмном фоне
                        drawCircle(
                            color = Color.White.copy(alpha = 0.35f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )

                        val nameText = dancer?.name ?: ""
                        if (nameText.isNotEmpty()) {
                            val textLayoutResult = textMeasurer.measure(
                                text = nameText,
                                style = TextStyle(
                                    color = AppText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            val textX = center.x - (textLayoutResult.size.width / 2)
                            val textY = center.y - radius - textLayoutResult.size.height - 2f
                            drawText(textLayoutResult, topLeft = Offset(textX, textY))
                        }
                    }
                }
            }

            Text(
                text = if (selectedPointIndex != -1)
                    "Выбрана точка. Перетащите её или нажмите для меню."
                else
                    "Нажмите на точку для редактирования.",
                style = MaterialTheme.typography.bodySmall.copy(color = AppTextDim),
                modifier = Modifier.padding(top = 8.dp)
            )

            // --- РЯД МИНИАТЮР ФОРМАЦИЙ + ТРИ ТОЧКИ ---
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(formations) { index, formation ->
                    val selected = index == currentFormationIndex
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(
                            modifier = Modifier
                                .size(width = 80.dp, height = 50.dp)
                                .shadow(if (selected) 6.dp else 2.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppSurface)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) AppAccent else AppBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .pointerInput(index) {
                                    detectTapGestures { selectFormation(index) }
                                }
                        ) {
                            drawThumbnail(formation, dancers)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formation.name,
                            style = MaterialTheme.typography.labelSmall.copy(color = AppText),
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                item {
                    Box {
                        IconButton(onClick = { showFormationMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Меню формаций",
                                tint = AppTextDim
                            )
                        }
                        DropdownMenu(
                            expanded = showFormationMenu,
                            onDismissRequest = { showFormationMenu = false },
                            containerColor = AppSurface
                        ) {
                            DropdownMenuItem(
                                text = { Text("Добавить формацию", color = AppText) },
                                onClick = {
                                    showFormationMenu = false
                                    addFormation()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Переименовать формацию", color = AppText) },
                                onClick = {
                                    showFormationMenu = false
                                    val f = formations.getOrNull(currentFormationIndex)
                                        ?: return@DropdownMenuItem
                                    newFormationName = f.name
                                    showRenameFormationDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Удалить формацию", color = AppError) },
                                onClick = {
                                    showFormationMenu = false
                                    deleteFormation(currentFormationIndex)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // --- КНОПКИ ВНИЗУ ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        syncCurrentPoints()
                        val updatedScheme = baseScheme.copy(
                            projectName = projectName,
                            dancers = dancers.toList(),
                            formations = formations.toList()
                        )
                        val saved = saveScheme(context, updatedScheme)
                        Toast.makeText(
                            context,
                            if (saved) "Схема сохранена!" else "Ошибка сохранения",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppAccent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Сохранить", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val newId = (dancers.maxOfOrNull { it.id } ?: 0) + 1
                        dancers.add(Dancer(id = newId, name = "Танцор $newId", color = "#B366FF"))
                        points.add(
                            Point(
                                dancerId = newId,
                                x = Random.nextInt(50, 550).toFloat(),
                                y = Random.nextInt(50, 350).toFloat()
                            )
                        )
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = AppSurface,
                        contentColor = AppAccent
                    ),
                    border = BorderStroke(1.dp, AppBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Добавить танцора", fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    // --- ДИАЛОГ НАСТРОЕК ---
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = AppSurface,
            titleContentColor = AppText,
            textContentColor = AppTextDim,
            title = { Text("Настройки сцены") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Клеток по ширине: $gridSizeX", color = AppText)
                    Slider(
                        value = gridSizeX.toFloat(),
                        onValueChange = { gridSizeX = it.toInt() },
                        valueRange = 5f..20f,
                        steps = 14,
                        colors = SliderDefaults.colors(
                            thumbColor = AppAccent,
                            activeTrackColor = AppAccent,
                            inactiveTrackColor = AppBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Название проекта") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppText,
                            unfocusedTextColor = AppText,
                            focusedBorderColor = AppAccent,
                            unfocusedBorderColor = AppBorder,
                            focusedLabelColor = AppAccent,
                            unfocusedLabelColor = AppTextDim
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Готово", color = AppAccent)
                }
            }
        )
    }

    // --- ДИАЛОГ ПЕРЕИМЕНОВАНИЯ ФОРМАЦИИ ---
    if (showRenameFormationDialog) {
        AlertDialog(
            onDismissRequest = { showRenameFormationDialog = false },
            containerColor = AppSurface,
            titleContentColor = AppText,
            title = { Text("Переименовать формацию") },
            text = {
                OutlinedTextField(
                    value = newFormationName,
                    onValueChange = { newFormationName = it },
                    label = { Text("Название формации") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText,
                        focusedBorderColor = AppAccent,
                        unfocusedBorderColor = AppBorder,
                        focusedLabelColor = AppAccent,
                        unfocusedLabelColor = AppTextDim
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val idx = currentFormationIndex
                    if (idx in formations.indices) {
                        formations[idx] = formations[idx].copy(name = newFormationName)
                    }
                    showRenameFormationDialog = false
                }) { Text("Сохранить", color = AppAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameFormationDialog = false }) {
                    Text("Отмена", color = AppTextDim)
                }
            }
        )
    }

    // --- ДЕЙСТВИЯ С ТОЧКОЙ ---
    if (showActionDialog && selectedPointIndex != -1) {
        val currentPoint = points[selectedPointIndex]
        val currentDancer = dancers.find { it.id == currentPoint.dancerId }

        AlertDialog(
            onDismissRequest = { showActionDialog = false },
            containerColor = AppSurface,
            titleContentColor = AppText,
            title = { Text("Действия с ${currentDancer?.name ?: "точкой"}") },
            text = {
                Column {
                    TextButton(onClick = {
                        dancers.removeAll { it.id == currentPoint.dancerId }
                        points.removeAt(selectedPointIndex)
                        showActionDialog = false
                        selectedPointIndex = -1
                    }) { Text("Удалить", color = AppError) }

                    TextButton(onClick = {
                        showActionDialog = false
                        showColorDialog = true
                    }) { Text("Изменить цвет", color = AppAccent) }

                    TextButton(onClick = {
                        newName = currentDancer?.name ?: ""
                        showActionDialog = false
                        showRenameDialog = true
                    }) { Text("Переименовать", color = AppText) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActionDialog = false }) {
                    Text("Отмена", color = AppTextDim)
                }
            }
        )
    }

    // --- ВЫБОР ЦВЕТА ---
    if (showColorDialog && selectedPointIndex != -1) {
        val currentPoint = points[selectedPointIndex]
        val availableColors = listOf(
            "#FF0000", // красный
            "#0000FF", // синий
            "#00FF00", // зелёный
            "#FFA500", // оранжевый
            "#800080"  // пурпурный
        )

        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            containerColor = AppSurface,
            titleContentColor = AppText,
            title = { Text("Выберите цвет") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    availableColors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(parseColor(hex))
                                .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        val dancerIndex =
                                            dancers.indexOfFirst { it.id == currentPoint.dancerId }
                                        if (dancerIndex != -1) {
                                            dancers[dancerIndex] =
                                                dancers[dancerIndex].copy(color = hex)
                                        }
                                        showColorDialog = false
                                        selectedPointIndex = -1
                                    }
                                }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorDialog = false }) {
                    Text("Отмена", color = AppTextDim)
                }
            }
        )
    }

    // --- ПЕРЕИМЕНОВАНИЕ ТАНЦОРА ---
    if (showRenameDialog && selectedPointIndex != -1) {
        val currentPoint = points[selectedPointIndex]
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = AppSurface,
            titleContentColor = AppText,
            title = { Text("Переименовать танцора") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Имя") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText,
                        focusedBorderColor = AppAccent,
                        unfocusedBorderColor = AppBorder,
                        focusedLabelColor = AppAccent,
                        unfocusedLabelColor = AppTextDim
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val dancerIndex = dancers.indexOfFirst { it.id == currentPoint.dancerId }
                    if (dancerIndex != -1) {
                        dancers[dancerIndex] = dancers[dancerIndex].copy(name = newName)
                    }
                    showRenameDialog = false
                    selectedPointIndex = -1
                }) { Text("Сохранить", color = AppAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Отмена", color = AppTextDim)
                }
            }
        )
    }
}

// --- СЕТКА С КВАДРАТНЫМИ КЛЕТКАМИ ---
private fun DrawScope.drawGrid(gridSizeX: Int) {
    val gridStroke = 1.dp.toPx()
    val borderStroke = 2.dp.toPx()

    val cell = size.width / gridSizeX
    val rows = (size.height / cell + 0.5f).roundToInt().coerceAtLeast(1)
    val rowStep = size.height / rows

    for (i in 0..gridSizeX) {
        val x = i * cell
        drawLine(
            color = AppGrid,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = gridStroke
        )
    }

    for (j in 0..rows) {
        val y = j * rowStep
        drawLine(
            color = AppGrid,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = gridStroke
        )
    }

    drawRect(
        color = AppBorder,
        topLeft = Offset(borderStroke / 2, borderStroke / 2),
        size = Size(size.width - borderStroke, size.height - borderStroke),
        style = Stroke(width = borderStroke)
    )
}

// --- МИНИАТЮРА ФОРМАЦИИ ---
private fun DrawScope.drawThumbnail(formation: Formation, dancers: List<Dancer>) {
    val scale = size.width / SCENE_W
    val stroke = 0.5f
    val cols = 6
    val rows = 4
    val stepX = size.width / cols
    val stepY = size.height / rows

    for (i in 0..cols) {
        drawLine(AppGrid, Offset(i * stepX, 0f), Offset(i * stepX, size.height), stroke)
    }
    for (j in 0..rows) {
        drawLine(AppGrid, Offset(0f, j * stepY), Offset(size.width, j * stepY), stroke)
    }
    drawRect(AppBorder, style = Stroke(width = 1.5f))

    val radius = stepX * DOT_RATIO

    formation.points.forEach { point ->
        val dancer = dancers.find { it.id == point.dancerId }
        drawCircle(
            color = parseColor(dancer?.color ?: "#9B4DFF"),
            radius = radius,
            center = Offset(point.x * scale, point.y * scale)
        )
    }
}
