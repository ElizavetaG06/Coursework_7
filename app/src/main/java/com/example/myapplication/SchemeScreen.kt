package com.example.myapplication

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

private const val SCHEME_FILE = "scheme_v2.json"

// Виртуальный размер сцены: все координаты точек хранятся в этих единицах,
// а на экране масштабируются под реальный размер холста.
private const val SCENE_W = 600f
private const val SCENE_H = 400f

// Доля от размера клетки, которую занимает радиус точки
private const val DOT_RATIO = 0.3f

private val json = Json { ignoreUnknownKeys = true }

private fun createSampleScheme(): Scheme = json.decodeFromString(
    """
    {
      "projectName": "Танец Весна",
      "videoPath": "/test.mp4",
      "createdAt": "2026-05-15T14:30:00",
      "settings": { "speed": 1.0, "mirror": false },
      "dancers": [
        { "id": 1, "name": "Анна", "color": "#FF0000" },
        { "id": 2, "name": "Иван", "color": "#0000FF" }
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
    File(context.filesDir, SCHEME_FILE)
        .writeText(json.encodeToString(Scheme.serializer(), scheme))
    true
} catch (_: Exception) {
    false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchemeScreen() {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()

    // Базовая схема читается один раз и не мутирует — она нужна только как
    // шаблон для copy() при сохранении.
    val baseScheme = remember { loadScheme(context) ?: createSampleScheme() }

    val dancers = remember { mutableStateListOf(*baseScheme.dancers.toTypedArray()) }
    val formations = remember { mutableStateListOf(*baseScheme.formations.toTypedArray()) }

    var currentFormationIndex by remember { mutableIntStateOf(0) }

    // Точки текущей формации. При смене формации список пересоздаётся из её данных.
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

    // Сохраняет текущие точки в активную формацию
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
        // Новая формация — копия текущей: те же точки, новое имя и id
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
        // Последнюю формацию не удаляем — сцена без формаций не имеет смысла
        if (formations.size <= 1) {
            Toast.makeText(context, "Нельзя удалить последнюю формацию", Toast.LENGTH_SHORT).show()
            return
        }

        val wasCurrent = index == currentFormationIndex

        // Если удаляем не открытую формацию — сначала сохраняем её текущие точки
        if (!wasCurrent) syncCurrentPoints()

        formations.removeAt(index)

        selectedPointIndex = -1
        // Корректируем индекс: после удаления элементы сдвинулись влево
        currentFormationIndex = when {
            wasCurrent -> index.coerceAtMost(formations.lastIndex)
            currentFormationIndex > index -> currentFormationIndex - 1
            else -> currentFormationIndex
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Схема: $projectName") },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки сцены")
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
            // --- Название формации над сценой (11sp = вдвое меньше заголовка 22sp) ---
            val currentFormation = formations.getOrNull(currentFormationIndex)
            Text(
                text = currentFormation?.name ?: "",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // --- ПРЯМОУГОЛЬНАЯ СЦЕНА (соотношение 1.5:1) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .background(Color.White)
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
                    // Радиус точки пропорционален клетке: клетка мельче — точка меньше
                    val radius = cellPx * DOT_RATIO
                    drawGrid(gridSizeX)

                    points.forEach { point ->
                        val dancer = dancers.find { it.id == point.dancerId }
                        val colorHex = dancer?.color ?: "#000000"
                        val color = try {
                            Color(android.graphics.Color.parseColor(colorHex))
                        } catch (_: Exception) {
                            Color.Black
                        }

                        val center = Offset(point.x * scale, point.y * scale)
                        drawCircle(color = color, radius = radius, center = center)

                        val nameText = dancer?.name ?: ""
                        if (nameText.isNotEmpty()) {
                            val textLayoutResult = textMeasurer.measure(
                                text = nameText,
                                style = TextStyle(
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            val textX = center.x - (textLayoutResult.size.width / 2)
                            val textY = center.y - radius - textLayoutResult.size.height - 4f
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
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )

            // --- РЯД МИНИАТЮР ФОРМАЦИЙ + ТРИ ТОЧКИ ---
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(formations) { index, formation ->
                    val selected = index == currentFormationIndex
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(
                            modifier = Modifier
                                .size(width = 72.dp, height = 48.dp)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                                .pointerInput(index) {
                                    detectTapGestures { selectFormation(index) }
                                }
                        ) {
                            drawThumbnail(formation, dancers)
                        }
                        Text(
                            text = formation.name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }

                item {
                    Box {
                        IconButton(onClick = { showFormationMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Меню формаций")
                        }
                        DropdownMenu(
                            expanded = showFormationMenu,
                            onDismissRequest = { showFormationMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Добавить формацию") },
                                onClick = {
                                    showFormationMenu = false
                                    addFormation()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Переименовать формацию") },
                                onClick = {
                                    showFormationMenu = false
                                    val f = formations.getOrNull(currentFormationIndex)
                                        ?: return@DropdownMenuItem
                                    newFormationName = f.name
                                    showRenameFormationDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Удалить формацию", color = Color.Red) },
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
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Сохранить")
                }

                Button(
                    onClick = {
                        val newId = (dancers.maxOfOrNull { it.id } ?: 0) + 1
                        dancers.add(Dancer(id = newId, name = "Танцор $newId", color = "#808080"))
                        points.add(
                            Point(
                                dancerId = newId,
                                x = Random.nextInt(50, 550).toFloat(),
                                y = Random.nextInt(50, 350).toFloat()
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Добавить танцора")
                }
            }
        }
    }

    // --- ДИАЛОГ НАСТРОЕК ---
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Настройки сцены") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Клеток по ширине: $gridSizeX")
                    Slider(
                        value = gridSizeX.toFloat(),
                        onValueChange = { gridSizeX = it.toInt() },
                        valueRange = 5f..20f,
                        steps = 14
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        label = { Text("Название проекта") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Готово") }
            }
        )
    }

    // --- ДИАЛОГ ПЕРЕИМЕНОВАНИЯ ФОРМАЦИИ ---
    if (showRenameFormationDialog) {
        AlertDialog(
            onDismissRequest = { showRenameFormationDialog = false },
            title = { Text("Переименовать формацию") },
            text = {
                OutlinedTextField(
                    value = newFormationName,
                    onValueChange = { newFormationName = it },
                    label = { Text("Название формации") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val idx = currentFormationIndex
                    if (idx in formations.indices) {
                        formations[idx] = formations[idx].copy(name = newFormationName)
                    }
                    showRenameFormationDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameFormationDialog = false }) { Text("Отмена") }
            }
        )
    }

    // --- ДИАЛОГИ ТОЧЕК ---
    if (showActionDialog && selectedPointIndex != -1) {
        val currentPoint = points[selectedPointIndex]
        val currentDancer = dancers.find { it.id == currentPoint.dancerId }

        AlertDialog(
            onDismissRequest = { showActionDialog = false },
            title = { Text("Действия с ${currentDancer?.name ?: "точкой"}") },
            text = {
                Column {
                    TextButton(onClick = {
                        dancers.removeAll { it.id == currentPoint.dancerId }
                        points.removeAt(selectedPointIndex)
                        showActionDialog = false
                        selectedPointIndex = -1
                    }) { Text("Удалить", color = Color.Red) }

                    TextButton(onClick = {
                        showActionDialog = false
                        showColorDialog = true
                    }) { Text("Изменить цвет") }

                    TextButton(onClick = {
                        newName = currentDancer?.name ?: ""
                        showActionDialog = false
                        showRenameDialog = true
                    }) { Text("Переименовать") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActionDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showColorDialog && selectedPointIndex != -1) {
        val currentPoint = points[selectedPointIndex]
        val availableColors = listOf(
            "#FF0000" to Color.Red, "#0000FF" to Color.Blue, "#00FF00" to Color.Green,
            "#FFA500" to Color(0xFFFFA500), "#800080" to Color(0xFF800080)
        )

        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text("Выберите цвет") },
            text = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    availableColors.forEach { (hex, color) ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        val dancerIndex = dancers.indexOfFirst { it.id == currentPoint.dancerId }
                                        if (dancerIndex != -1) {
                                            dancers[dancerIndex] = dancers[dancerIndex].copy(color = hex)
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
                TextButton(onClick = { showColorDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showRenameDialog && selectedPointIndex != -1) {
        val currentPoint = points[selectedPointIndex]
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Переименовать танцора") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Имя") },
                    singleLine = true
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
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Отмена") }
            }
        )
    }
}

// --- СЕТКА С КВАДРАТНЫМИ КЛЕТКАМИ ---
private fun DrawScope.drawGrid(gridSizeX: Int) {
    val gridStroke = 1.dp.toPx()
    val borderStroke = 2.dp.toPx()

    val gridColor = Color(0xFFBDBDBD)

    val cell = size.width / gridSizeX
    val rows = (size.height / cell + 0.5f).roundToInt().coerceAtLeast(1)
    val rowStep = size.height / rows

    for (i in 0..gridSizeX) {
        val x = i * cell
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = gridStroke
        )
    }

    for (j in 0..rows) {
        val y = j * rowStep
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = gridStroke
        )
    }

    drawRect(
        color = Color.Black,
        topLeft = Offset(borderStroke / 2, borderStroke / 2),
        size = Size(size.width - borderStroke, size.height - borderStroke),
        style = Stroke(width = borderStroke)
    )
}

// --- МИНИАТЮРА ФОРМАЦИИ ---
private fun DrawScope.drawThumbnail(formation: Formation, dancers: List<Dancer>) {
    val scale = size.width / SCENE_W

    val gridColor = Color(0xFFDDDDDD)
    val stroke = 0.5f
    val cols = 6
    val rows = 4
    val stepX = size.width / cols
    val stepY = size.height / rows
    for (i in 0..cols) drawLine(gridColor, Offset(i * stepX, 0f), Offset(i * stepX, size.height), stroke)
    for (j in 0..rows) drawLine(gridColor, Offset(0f, j * stepY), Offset(size.width, j * stepY), stroke)
    drawRect(Color.Black, style = Stroke(width = 1.5f))

    // Радиус тоже привязан к клетке миниатюры
    val radius = stepX * DOT_RATIO

    formation.points.forEach { point ->
        val dancer = dancers.find { it.id == point.dancerId }
        val color = try {
            Color(android.graphics.Color.parseColor(dancer?.color ?: "#000000"))
        } catch (_: Exception) {
            Color.Black
        }
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(point.x * scale, point.y * scale)
        )
    }
}
