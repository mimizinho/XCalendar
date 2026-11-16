package com.example.xcalendar

import android.content.Context
import android.os.Bundle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// ---------- Modelo ----------
data class Event(
    val id: Long,
    val title: String,
    val date: LocalDate,
    val time: LocalTime?,
    val color: Color
)

private val Orange = Color(0xFFFF6A00)
private val Bg = Color(0xFFF4F4F6)
private val Palette = listOf(Orange, Color(0xFF2F80ED), Color(0xFF27AE60), Color(0xFF9B51E0))
private val PT = Locale("pt", "BR")
private const val BASE_PAGE = 1200

// ---------- Activity ----------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Orange)) {
                CalendarScreen()
            }
        }
    }
}

// ---------- Tela principal ----------
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen() {
    val today = remember { LocalDate.now() }
    var selected by remember { mutableStateOf(today) }
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Event?>(null) }
    val context = LocalContext.current
    val events = remember { loadEvents(context) }
    val pager = rememberPagerState(initialPage = BASE_PAGE, pageCount = { BASE_PAGE * 2 })
    val scope = rememberCoroutineScope()
    val visibleMonth = YearMonth.from(today).plusMonths((pager.currentPage - BASE_PAGE).toLong())

    // Ao trocar de mês, ajusta o dia selecionado
    LaunchedEffect(pager.currentPage) {
        if (YearMonth.from(selected) != visibleMonth) {
            selected = if (YearMonth.from(today) == visibleMonth) today else visibleMonth.atDay(1)
        }
    }

    Scaffold(
        containerColor = Bg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = null; showDialog = true },
                containerColor = Orange, contentColor = Color.White, shape = CircleShape
            ) { Icon(Icons.Default.Add, "Novo evento") }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Cabeçalho
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        visibleMonth.month.getDisplayName(TextStyle.FULL, PT)
                            .replaceFirstChar { it.uppercase() },
                        fontSize = 32.sp, fontWeight = FontWeight.Bold
                    )
                    Text("${visibleMonth.year}", fontSize = 14.sp, color = Color.Gray)
                }
                OutlinedButton(
                    onClick = {
                        selected = today
                        scope.launch { pager.animateScrollToPage(BASE_PAGE) }
                    },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Orange)
                ) { Text("Hoje", color = Orange) }
            }

            // Cartão do calendário
            Card(
                Modifier.padding(horizontal = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    WeekdayHeader()
                    HorizontalPager(state = pager, modifier = Modifier.height(ROW_H * 6)) { page ->
                        val month = YearMonth.from(today).plusMonths((page - BASE_PAGE).toLong())
                        MonthGrid(month, today, selected, events) { selected = it }
                    }
                }
            }

            // Agenda do dia
            val dayEvents = events.filter { it.date == selected }
                .sortedBy { it.time ?: LocalTime.MIN }
            Text(
                "${selected.dayOfWeek.getDisplayName(TextStyle.FULL, PT)}, " +
                    "${selected.dayOfMonth} de ${selected.month.getDisplayName(TextStyle.FULL, PT)}",
                Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                fontWeight = FontWeight.SemiBold, fontSize = 16.sp
            )
            Text(metaLine(selected), Modifier.padding(horizontal = 20.dp), color = Color.Gray, fontSize = 13.sp)
            holidayOf(selected)?.let { HolidayCard(it) }
            if (dayEvents.isEmpty() && holidayOf(selected) == null) {
                Text("Nenhum evento", Modifier.padding(horizontal = 20.dp), color = Color.Gray)
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dayEvents, key = { it.id }) { ev ->
                    EventCard(ev, onEdit = { editing = ev; showDialog = true }) { events.remove(ev); saveEvents(context, events) }
                }
            }
        }
    }

    if (showDialog) {
        AddEventDialog(
            date = selected,
            onDismiss = { showDialog = false },
            existing = editing,
            onSave = { ev ->
                val i = events.indexOfFirst { it.id == ev.id }
                if (i >= 0) events[i] = ev else events.add(ev)
                saveEvents(context, events)
                showDialog = false
            }
        )
    }
}

// ---------- Componentes ----------
private val ROW_H = 64.dp

@Composable
fun WeekdayHeader() {
    val names = listOf("D", "S", "T", "Q", "Q", "S", "S")
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        names.forEachIndexed { i, n ->
            Text(
                n, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = if (i == 0 || i == 6) Orange.copy(alpha = 0.8f) else Color.Gray,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun MonthGrid(
    month: YearMonth, today: LocalDate, selected: LocalDate,
    events: List<Event>, onSelect: (LocalDate) -> Unit
) {
    val offset = month.atDay(1).dayOfWeek.value % 7 // domingo = 0
    Column {
        repeat(6) { row ->
            Row(Modifier.fillMaxWidth().height(ROW_H)) {
                repeat(7) { col ->
                    val day = row * 7 + col - offset + 1
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            DayCell(
                                date, date == today, date == selected,
                                events.filter { it.date == date }.map { it.color }.distinct().take(3), daySub(date),
                                col == 0 || col == 6
                            ) { onSelect(date) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DayCell(
    date: LocalDate, isToday: Boolean, isSelected: Boolean,
    dots: List<Color>, sub: Pair<String, Boolean>, weekend: Boolean, onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(38.dp).clip(CircleShape)
                .background(
                    when {
                        isToday -> Orange
                        isSelected -> Orange.copy(alpha = 0.15f)
                        else -> Color.Transparent
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${date.dayOfMonth}", fontSize = 16.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> Color.White
                    isSelected -> Orange
                    weekend -> Color(0xFF8A8A8A)
                    else -> Color(0xFF1C1C1E)
                }
            )
        }
        Text(sub.first, fontSize = 9.sp, maxLines = 1, color = if (sub.second) Color(0xFFE5484D) else Color.Gray,
            fontWeight = if (sub.second) FontWeight.SemiBold else FontWeight.Normal)
        Row(Modifier.height(8.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            dots.forEach { Box(Modifier.size(4.dp).clip(CircleShape).background(it)) }
        }
    }
}

@Composable
fun EventCard(ev: Event, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.clickable(onClick = onEdit).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(ev.color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ev.title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text(ev.time?.toString() ?: "Dia inteiro", color = Color.Gray, fontSize = 13.sp)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Excluir", tint = Color.Gray) }
        }
    }
}

@Composable
fun AddEventDialog(date: LocalDate, existing: Event? = null, onDismiss: () -> Unit, onSave: (Event) -> Unit) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var time by remember { mutableStateOf(existing?.time?.toString() ?: "") }
    var color by remember { mutableStateOf(existing?.color ?: Palette[0]) }
    val parsed = runCatching { LocalTime.parse(time) }.getOrNull()
    val timeOk = time.isBlank() || parsed != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Novo evento" else "Editar evento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true)
                OutlinedTextField(
                    time, { time = it }, label = { Text("Hora (HH:mm) – opcional") },
                    singleLine = true, isError = !timeOk
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Palette.forEach { c ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(c)
                                .then(if (c == color) Modifier.background(Color.Black.copy(0.25f)) else Modifier)
                                .clickable { color = c },
                            contentAlignment = Alignment.Center
                        ) { if (c == color) Text("✓", color = Color.White) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && timeOk,
                onClick = { onSave(Event(existing?.id ?: System.nanoTime(), title.trim(), existing?.date ?: date, parsed, color)) }
            ) { Text("Salvar", color = Orange) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ---------- Dados de exemplo ----------
private const val PREFS = "xcalendar"
private const val KEY = "events"

fun loadEvents(context: Context): SnapshotStateList<Event> {
    val list = mutableStateListOf<Event>()
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY, null) ?: return list
    runCatching {
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Event(
                    o.getLong("id"), o.getString("title"),
                    LocalDate.parse(o.getString("date")),
                    if (o.isNull("time")) null else LocalTime.parse(o.getString("time")),
                    Color(o.getInt("color"))
                )
            )
        }
    }
    return list
}

fun saveEvents(context: Context, events: List<Event>) {
    val arr = JSONArray()
    events.forEach { e ->
        arr.put(JSONObject().apply {
            put("id", e.id)
            put("title", e.title)
            put("date", e.date.toString())
            put("time", e.time?.toString() ?: JSONObject.NULL)
            put("color", e.color.toArgb())
        })
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString(KEY, arr.toString()).apply()
}

// ---------- Feriados, calendário lunar e fases da lua ----------
data class Holiday(val short: String, val name: String, val note: String)

fun easter(y: Int): LocalDate {
    val a = y % 19; val b = y / 100; val c = y % 100
    val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4; val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val t = h + l - 7 * m + 114
    return LocalDate.of(y, t / 31, t % 31 + 1)
}

private val holCache = HashMap<Int, Map<LocalDate, Holiday>>()

fun holidayOf(d: LocalDate): Holiday? = holCache.getOrPut(d.year) {
    val y = d.year; val n = "Feriado nacional"; val e = easter(y)
    mapOf(
        LocalDate.of(y, 1, 1) to Holiday("Ano Novo", "Confraternização Universal", n),
        e.minusDays(47) to Holiday("Carnaval", "Carnaval", "Ponto facultativo"),
        e.minusDays(2) to Holiday("Sexta Santa", "Sexta-feira Santa", n),
        e to Holiday("Páscoa", "Páscoa", "Data comemorativa"),
        LocalDate.of(y, 4, 21) to Holiday("Tiradentes", "Tiradentes", n),
        LocalDate.of(y, 5, 1) to Holiday("Trabalho", "Dia do Trabalho", n),
        e.plusDays(60) to Holiday("Corpus", "Corpus Christi", "Ponto facultativo"),
        LocalDate.of(y, 9, 7) to Holiday("Independ.", "Independência do Brasil", n),
        LocalDate.of(y, 10, 12) to Holiday("Aparecida", "Nossa Senhora Aparecida", n),
        LocalDate.of(y, 11, 2) to Holiday("Finados", "Finados", n),
        LocalDate.of(y, 11, 15) to Holiday("República", "Proclamação da República", n),
        LocalDate.of(y, 11, 20) to Holiday("Consc. Negra", "Consciência Negra", n),
        LocalDate.of(y, 12, 25) to Holiday("Natal", "Natal", n)
    )
}[d]

fun moonOf(d: LocalDate): String {
    val p = 29.530588853
    val age = (((d.toEpochDay() + 2440588.0 - 2451550.1) % p) + p) % p
    val names = listOf("🌑 Lua nova", "🌒 Crescente", "🌓 Quarto crescente", "🌔 Gibosa crescente",
        "🌕 Lua cheia", "🌖 Gibosa minguante", "🌗 Quarto minguante", "🌘 Minguante")
    return names[((age / p * 8 + 0.5).toInt()) % 8]
}

fun daySub(d: LocalDate): Pair<String, Boolean> =
    holidayOf(d)?.let { it.short to true } ?: ("" to false)

fun metaLine(d: LocalDate): String = moonOf(d)

@Composable
fun HolidayCard(h: Holiday) {
    Card(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFE5484D)))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(h.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Text(h.note, color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}
