@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.leerumax.huiltje

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

const val MAX_SECONDS = 8

class MainActivity : ComponentActivity() {
    private val verzoek = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val db = Db.get(this)
        verzoek.value = intent.getStringExtra("open")
        DelenService.start(this)
        setContent { App(db, verzoek) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        verzoek.value = intent.getStringExtra("open")
    }

    override fun onResume() {
        super.onResume()
        Herinneringen.controleer(this)
        Widget.bijwerken(this)
        Delen.nu(this)
    }
}

// Pastel: roze, mint en lavendel op crème; donkere modus dezelfde tinten op diep paars.
// primary = leesbare accenttekst/knoppen; primaryContainer = de zachte pastel voor grote knoppen.
internal val LICHT = lightColorScheme(
    primary = Color(0xFFC2577A), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF2A7BB), onPrimaryContainer = Color(0xFF5A2A3C),
    secondary = Color(0xFF7FCFB4), onSecondary = Color(0xFF1F4A3D),
    secondaryContainer = Color(0xFFFBD3DE), onSecondaryContainer = Color(0xFF5A2A3C),
    tertiary = Color(0xFFB39DEB), onTertiary = Color(0xFF2E2350),
    background = Color(0xFFFFF8F2), onBackground = Color(0xFF4A3B42),
    surface = Color(0xFFFFF8F2), onSurface = Color(0xFF4A3B42), onSurfaceVariant = Color(0xFF7A6870),
    surfaceContainer = Color(0xFFFCEEF2), surfaceContainerHigh = Color(0xFFFFFFFF), surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFFE6C9D3),
)
private val DONKER = darkColorScheme(
    primary = Color(0xFFF2A7BB), onPrimary = Color(0xFF4A2533),
    primaryContainer = Color(0xFF7A3A52), onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFF7FCFB4), onSecondary = Color(0xFF14352B),
    secondaryContainer = Color(0xFF4A3A55), onSecondaryContainer = Color(0xFFF6DDE6),
    tertiary = Color(0xFFB39DEB), onTertiary = Color(0xFF241A40),
    background = Color(0xFF1E1A26), onBackground = Color(0xFFEDE3EA),
    surface = Color(0xFF1E1A26), onSurface = Color(0xFFEDE3EA), onSurfaceVariant = Color(0xFFBFB2BA),
    surfaceContainer = Color(0xFF2A2433), surfaceContainerHigh = Color(0xFF332C3D), surfaceContainerHighest = Color(0xFF332C3D),
    outline = Color(0xFF5A4E63),
)

// Nachtstand: zwart met gedempt amber, zo min mogelijk fel licht bij de nachtvoeding.
private val NACHT = darkColorScheme(
    primary = Color(0xFFE0A070), onPrimary = Color(0xFF2A1408),
    primaryContainer = Color(0xFF5A3020), onPrimaryContainer = Color(0xFFFFD9B8),
    secondary = Color(0xFF6B4A2E), onSecondary = Color(0xFFFFD9B8),
    secondaryContainer = Color(0xFF3A2418), onSecondaryContainer = Color(0xFFE8B98F),
    tertiary = Color(0xFF7A3B2A), onTertiary = Color(0xFFFFD9B8),
    background = Color(0xFF000000), onBackground = Color(0xFFD9A57A),
    surface = Color(0xFF000000), onSurface = Color(0xFFD9A57A), onSurfaceVariant = Color(0xFFA07A5A),
    surfaceContainer = Color(0xFF140C08), surfaceContainerHigh = Color(0xFF1C120C), surfaceContainerHighest = Color(0xFF1C120C),
    outline = Color(0xFF4A3020),
)

/** Doorschijnende tint van het geluid, voor kaartjes en knoppen. */
@Composable
fun Sound.tint(): Color = Color(kleur).copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.3f else 0.35f)

/** Zachte pastelknop (lopende timer, Luister). */
@Composable
fun zacht() = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)

fun cmTekst(c: Float): String = if (c % 1f == 0f) c.toInt().toString() else "%.1f".format(Locale("nl", "NL"), c)

/** Melding onderin; met [ongedaan] verschijnt "Ongedaan maken" (dan wordt die regel weggehaald). */
typealias Melder = (tekst: String, ongedaan: Ev?) -> Unit

@Composable
fun App(db: Db, verzoek: MutableState<String?>) {
    val v by Db.versie.collectAsState()
    // ponytail: hele logboek bij elke wijziging opnieuw lezen (zonder embeddings); pas pagineren als het traag wordt.
    val evs = remember(v) { db.alle() }
    var tab by remember { mutableIntStateOf(0) }
    // Meer = menu dat omhoog schuift, vanaf elk scherm; een gekozen pagina ligt over het tabblad heen.
    var menu by remember { mutableStateOf(false) }
    var pagina by remember { mutableStateOf<Pagina?>(null) }
    var minuut by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); minuut = System.currentTimeMillis() } }
    val nacht = when (remember(v) { db.get("nacht") }) {
        "aan" -> true
        "uit" -> false
        else -> minuut.uur() >= 21 || minuut.uur() < 7
    }
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val melder: Melder = { tekst, ongedaan ->
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            val r = snack.showSnackbar(tekst, actionLabel = ongedaan?.let { "Ongedaan maken" }, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed && ongedaan != null) db.put(ongedaan.copy(weg = true))
        }
    }
    val meldingen = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && db.get("meldingen.gevraagd") == null) {
            db.set("meldingen.gevraagd", "1")
            meldingen.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(verzoek.value) {
        if (verzoek.value != null) {
            tab = 0
            pagina = null
        }
    }

    val schema = if (nacht) NACHT else if (isSystemInDarkTheme()) DONKER else LICHT
    MaterialTheme(colorScheme = schema, shapes = Shapes(medium = RoundedCornerShape(24.dp))) {
        LaunchedEffect(nacht) {
            (ctx as? Activity)?.window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = if (nacht) 0.08f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
        Scaffold(snackbarHost = { SnackbarHost(snack) }, bottomBar = {
            NavigationBar {
                listOf("🏠" to "Vandaag", "👂" to "Luisteren", "📖" to "Logboek", "📈" to "Inzicht").forEachIndexed { i, (e, t) ->
                    NavigationBarItem(
                        selected = pagina == null && tab == i, onClick = { pagina = null; tab = i },
                        icon = { Text(e, fontSize = 20.sp) }, label = { Text(t, fontSize = 12.sp) },
                    )
                }
                NavigationBarItem(
                    selected = menu || pagina != null, onClick = { menu = true },
                    icon = { Text("☰", fontSize = 20.sp) }, label = { Text("Meer", fontSize = 12.sp) },
                )
            }
        }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
                val p = pagina
                if (p != null) PaginaScherm(db, evs, melder, p) { pagina = null }
                else when (tab) {
                    0 -> Vandaag(db, evs, nacht, melder, verzoek) { tab = 1 }
                    1 -> Luisteren(db, evs)
                    2 -> Logboek(db, evs)
                    else -> Inzicht(db, evs)
                }
            }
        }
        if (menu) {
            ModalBottomSheet(onDismissRequest = { menu = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                MeerMenu { menu = false; pagina = it }
            }
        }
    }
}

// ---- kleine bouwstenen ----

@Composable
fun Kaart(kleur: Color? = null, modifier: Modifier = Modifier, inhoud: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = if (kleur != null) CardDefaults.cardColors(containerColor = kleur) else CardDefaults.cardColors(),
    ) { Column(Modifier.padding(16.dp), content = inhoud) }
}

@Composable
fun Kop(t: String) = Text(t, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))

@Composable
fun Klein(t: String) = Text(t, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun Stepper(label: String, waarde: Int, stap: Int, eenheid: String = "ml", bij: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        FilledTonalButton(onClick = { bij((waarde - stap).coerceAtLeast(0)) }) { Text("−$stap") }
        Text("$waarde $eenheid", Modifier.width(80.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        FilledTonalButton(onClick = { bij(waarde + stap) }) { Text("+$stap") }
    }
}

fun klok(ms: Long): String = SimpleDateFormat("HH:mm", Locale("nl", "NL")).format(Date(ms))
fun datum(ms: Long): String = SimpleDateFormat("EEE d MMM", Locale("nl", "NL")).format(Date(ms))
fun mmss(ms: Long): String = (ms / 1000).coerceAtLeast(0).let { s ->
    if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Tijd aanpassen met de gewone Android-klok; de datum blijft die van [ms]. */
fun kiesTijd(ctx: Context, ms: Long, klaar: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    TimePickerDialog(ctx, { _, h, m ->
        c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, m); c.set(Calendar.SECOND, 0)
        klaar(c.timeInMillis)
    }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
}

fun kiesDatum(ctx: Context, ms: Long, klaar: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    DatePickerDialog(ctx, { _, j, mnd, d ->
        c.set(j, mnd, d)
        klaar(c.timeInMillis)
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
}

// ---- luisteren ----

sealed interface Fase {
    data object Klaar : Fase
    data object Luistert : Fase
    data object Denkt : Fase
    data object GeenBaby : Fase
    class Uitslag(val ev: Ev, val geluid: FloatArray) : Fase
}

@Composable
fun Luisteren(db: Db, evs: List<Ev>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var analyzer by remember { mutableStateOf<Analyzer?>(null) }
    LaunchedEffect(Unit) { analyzer = withContext(Dispatchers.IO) { Analyzer(ctx) } }
    var fase by remember { mutableStateOf<Fase>(Fase.Klaar) }
    var level by remember { mutableFloatStateOf(0f) }
    var seconds by remember { mutableFloatStateOf(0f) }
    val stop = remember { AtomicBoolean(false) }
    var mag by remember { mutableStateOf(ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val vraag = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { mag = it }

    fun start(a: Analyzer) {
        stop.set(false)
        fase = Fase.Luistert
        scope.launch {
            val t0 = System.currentTimeMillis()
            val pcm = withContext(Dispatchers.IO) {
                record(MAX_SECONDS, { stop.get() }) { l ->
                    level = l
                    seconds = (System.currentTimeMillis() - t0) / 1000f
                }
            }
            fase = Fase.Denkt
            val nu = System.currentTimeMillis()
            val res = withContext(Dispatchers.Default) { a.analyze(pcm, db.eigenVoorbeelden()) }
            withContext(Dispatchers.IO) { writeWav(File(opnamesMap(ctx), "$nu.wav"), pcm) }
            if (res == null) {
                fase = Fase.GeenBaby
                return@launch
            }
            // De klok telt mee: hoe lang na de voeding en hoe lang wakker.
            val naVoeding = laatsteVoeding(evs)?.let { (nu - it.start) / MIN }
            val wakker = when (val s = slaapStatus(evs)) {
                is Slaap.Wakker -> (nu - s.sinds) / MIN
                is Slaap.Slaapt -> 0L
                null -> null
            }
            val probs = metKlok(res.probs, klokFactor(naVoeding, wakker))
            val klokTekst = listOfNotNull(naVoeding?.let { "${duur(it * MIN)} na de voeding" }, wakker?.let { "${duur(it * MIN)} wakker" })
                .joinToString(", ").ifBlank { null }
            val ev = Ev("huil-$nu", T.HUIL, nu, probs = probs, emb = res.embedding, tekst = klokTekst)
            db.put(ev)
            fase = Fase.Uitslag(ev, res.probs)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text("👶 Huiltje", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Tik op Luister zodra het geluidje begint, nog vóór het echte huilen.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        when {
            !mag -> Button(onClick = { vraag.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Microfoon toestaan") }
            analyzer == null -> CircularProgressIndicator()
            fase == Fase.Luistert -> {
                Button(
                    onClick = { stop.set(true) }, shape = CircleShape, modifier = Modifier.size(200.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                ) { Text("Stop\n${seconds.roundToInt()} / $MAX_SECONDS s", fontSize = 22.sp, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth())
            }
            fase == Fase.Denkt -> CircularProgressIndicator()
            else -> Button(onClick = { analyzer?.let { start(it) } }, shape = CircleShape, modifier = Modifier.size(200.dp), colors = zacht()) {
                Text("👂\nLuister", fontSize = 26.sp, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(24.dp))

        when (val f = fase) {
            Fase.GeenBaby -> Text("Geen babygeluidje gehoord. Telefoon wat dichterbij (± 30 cm) en nog een keer?", textAlign = TextAlign.Center)
            is Fase.Uitslag -> UitslagKaart(f.ev, f.geluid, evs.find { it.id == f.ev.id }?.label) { label ->
                db.een(f.ev.id)?.let { db.put(it.copy(label = label)) }
            }
            else -> {}
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun UitslagKaart(ev: Ev, geluid: FloatArray, bevestigd: Int?, bevestig: (Int) -> Unit) {
    val p = ev.probs!!
    val order = p.indices.sortedByDescending { p[it] }
    val top = SOUNDS[order[0]]
    val tweede = SOUNDS[order[1]]

    Kaart(top.tint()) {
        if (p[order[0]] < 0.45f) Text("Twijfel tussen ${top.name} en ${tweede.name}", fontWeight = FontWeight.Bold)
        Text("${top.emoji} ${top.name}: ${top.meaning}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("${(p[order[0]] * 100).roundToInt()} % zeker")
        Spacer(Modifier.height(12.dp))
        Text("Wat kun je doen", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        top.doen.forEach { Text("• $it", Modifier.padding(top = 4.dp)) }
        if (p[order[1]] >= 0.2f) {
            Spacer(Modifier.height(8.dp))
            Text("Helpt dat niet? Misschien ${tweede.meaning.lowercase()} ${tweede.emoji} (${tweede.name}): ${tweede.doen.first()}")
        }
    }
    Kaart {
        order.forEach { i -> KansRij(SOUNDS[i], p[i]) }
        if (ev.tekst != null) {
            val g = geluid.indices.maxBy { geluid[it] }
            Spacer(Modifier.height(6.dp))
            Klein("🕐 De klok telt mee (${ev.tekst}). Alleen op het geluid: ${SOUNDS[g].name} ${(geluid[g] * 100).roundToInt()} %.")
        }
    }
    Kaart {
        if (bevestigd != null) {
            Text("Opgeslagen: ${SOUNDS[bevestigd].emoji} ${SOUNDS[bevestigd].name} (${SOUNDS[bevestigd].meaning}). Huiltje leert hiervan.")
        } else {
            Text("Wat bleek het te zijn?", fontWeight = FontWeight.Bold)
            Text("Kies achteraf wat hielp; dan leert de app jullie baby kennen. Kan ook later in het Logboek.")
            Spacer(Modifier.height(8.dp))
            LabelKnoppen(bevestig)
        }
    }
}

@Composable
fun KansRij(s: Sound, p: Float) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${s.emoji} ${s.name}", Modifier.width(88.dp))
        LinearProgressIndicator(
            progress = { p }, modifier = Modifier.weight(1f).height(10.dp),
            color = Color(s.kleur), trackColor = MaterialTheme.colorScheme.surfaceContainer,
        )
        Text("${(p * 100).roundToInt()}%", Modifier.width(48.dp).padding(start = 8.dp))
    }
}

@Composable
fun LabelKnoppen(kies: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SOUNDS.forEachIndexed { i, s ->
            Button(
                onClick = { kies(i) }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = s.tint(), contentColor = MaterialTheme.colorScheme.onSurface),
            ) { Text("${s.emoji} ${s.name}: ${s.meaning}") }
        }
    }
}
