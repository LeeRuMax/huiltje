@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.leerumax.huiltje

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Een pagina uit het Meer-menu: een log-onderdeel, of een instelling/info met eigen inhoud. */
class Pagina(val titel: String, val onderdeel: Onderdeel? = null, val inhoud: @Composable (Db, List<Ev>, Melder) -> Unit = { _, _, _ -> })

val INSTELLINGEN: List<Pagina> = listOf(
    Pagina("👶 Baby en jij", inhoud = { db, evs, _ -> BabyKaart(db, evs) }),
    Pagina("⏰ Herinneringen", inhoud = { db, evs, _ -> HerinneringKaart(db, evs) }),
    Pagina("📅 Afspraken", inhoud = { db, evs, _ -> AfsprakenKaart(db, evs, System.currentTimeMillis()) }),
    Pagina("🧊 Voorraad", inhoud = { db, evs, _ -> VoorraadKaart(db, evs, System.currentTimeMillis()) }),
    Pagina("💞 Delen met partner", inhoud = { db, evs, _ -> DelenKaart(db, evs) }),
    Pagina("💾 Backup", inhoud = { db, _, m -> BackupKaart(db, m) }),
    Pagina("🌙 Nachtstand", inhoud = { db, _, _ -> NachtKaart(db) }),
    Pagina("🌱 Week voor week", inhoud = { _, evs, _ -> WekenKaart(dagenOud(evs.find { it.type == T.BABY }, System.currentTimeMillis()), altijdOpen = true) }),
    Pagina("👂 De geluidjes", inhoud = { _, _, _ -> GeluidjesInfo() }),
    Pagina("ℹ️ Over", inhoud = { _, _, _ -> OverKaart() }),
)

/** Inhoud van het menu dat omhoog schuift: eerst de log-onderdelen, dan instellingen en info. */
@Composable
fun MeerMenu(open: (Pagina) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
        Kop("Loggen")
        Raster(ONDERDELEN.map { Pagina(it.titel, onderdeel = it) }, open)
        Spacer(Modifier.height(16.dp))
        Kop("Instellingen en info")
        Raster(INSTELLINGEN, open)
    }
}

@Composable
private fun Raster(ps: List<Pagina>, open: (Pagina) -> Unit) {
    ps.chunked(3).forEach { rij ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rij.forEach { p ->
                FilledTonalButton(
                    onClick = { open(p) }, modifier = Modifier.weight(1f).height(64.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) { Text(p.titel, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 16.sp) }
            }
            repeat(3 - rij.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun PaginaScherm(db: Db, evs: List<Ev>, melder: Melder, p: Pagina, terug: () -> Unit) {
    p.onderdeel?.let {
        OnderdeelPagina(db, evs, it, terug)
        return
    }
    BackHandler(onBack = terug)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = terug) { Text("← Terug") }
        p.inhoud(db, evs, melder)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun NachtKaart(db: Db) {
    Kaart {
        Kop("🌙 Nachtstand")
        Klein("Zwart en amber, scherm gedimd, grote knoppen. Automatisch tussen 21 en 7 uur.")
        val stand = db.get("nacht") ?: "auto"
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("auto" to "Automatisch", "aan" to "Altijd", "uit" to "Nooit").forEach { (k, t) ->
                FilterChip(stand == k, { db.set("nacht", if (k == "auto") null else k) }, { Text(t) })
            }
        }
    }
}

@Composable
fun GeluidjesInfo() {
    Kaart {
        Kop("👂 De vijf geluidjes")
        Text(
            "Volgens Priscilla Dunstan maken baby's van 0 tot ongeveer 3 maanden vlak vóór het huilen een reflexgeluid " +
                "dat vertelt wat er is. Wetenschappelijk is het niet bewezen; zie het als hint. De app leert van wat jullie bevestigen, " +
                "en de klok (tijd sinds voeding, wakkertijd) telt mee.",
        )
    }
    SOUNDS.forEach { s ->
        Kaart(s.tint()) {
            Text("${s.emoji} ${s.name}: ${s.meaning}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(s.hoorJe, Modifier.padding(vertical = 4.dp))
            s.doen.forEach { Text("• $it") }
        }
    }
    Kaart {
        Kop("Wanneer bel je de huisarts?")
        BEL_DE_HUISARTS.forEach { Text("• $it") }
    }
}

@Composable
fun OverKaart() {
    val ctx = LocalContext.current
    Kaart {
        Kop("ℹ️ Over Huiltje")
        Klein(
            "Versie ${ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName}. Alles staat op deze telefoon. " +
                "Delen gaat alleen rechtstreeks naar de telefoon van je partner op dezelfde wifi, versleuteld met jullie " +
                "partnercode; de app praat nooit met een server of het internet.",
        )
        Spacer(Modifier.height(8.dp))
        Text("Geen medisch hulpmiddel. De herkenning is een hint, geen diagnose. Twijfel je over je baby, bel de huisarts; bij direct gevaar 112.")
        Spacer(Modifier.height(8.dp))
        Klein("Open source (MIT). Broncode, bugs en ideeën: github.com/LeeRuMax/huiltje")
    }
}

@Composable
fun BabyKaart(db: Db, evs: List<Ev>) {
    val ctx = LocalContext.current
    val baby = evs.find { it.type == T.BABY } ?: Ev(T.BABY, T.BABY, 0)
    var naam by remember(baby.tekst) { mutableStateOf(baby.tekst.orEmpty()) }
    var mijn by remember { mutableStateOf(db.mijnNaam.orEmpty()) }
    Kaart {
        Kop("👶 Jullie baby")
        OutlinedTextField(naam, { naam = it }, label = { Text("Naam") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        if (naam != baby.tekst.orEmpty()) TextButton(onClick = { db.put(baby.copy(tekst = naam.trim())) }) { Text("Naam opslaan") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (baby.start > 0) "Geboren ${datum(baby.start)}" else "Geboortedatum nog niet ingevuld", Modifier.weight(1f))
            TextButton(onClick = { kiesDatum(ctx, if (baby.start > 0) baby.start else System.currentTimeMillis()) { db.put(baby.copy(start = it.dag().ms())) } }) { Text("Kies") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("meisje", "jongen").forEach { k -> FilterChip(baby.kant == k, { db.put(baby.copy(kant = k)) }, { Text(k) }) }
        }
        Klein("Geboortedatum en jongen/meisje zijn voor week voor week en de groeicurve.")
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        OutlinedTextField(mijn, { mijn = it }, label = { Text("Jouw naam (op deze telefoon)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        if (mijn != db.mijnNaam.orEmpty()) TextButton(onClick = { db.set("naam", mijn.trim()) }) { Text("Opslaan") }
        Klein("Zo zie je in het logboek wie wat deed.")
    }
}

@Composable
fun HerinneringKaart(db: Db, evs: List<Ev>) {
    val ctx = LocalContext.current
    val dagen = dagenOud(evs.find { it.type == T.BABY }, System.currentTimeMillis())
    fun schakel(k: String, aan: Boolean) {
        db.set(k, if (aan) "1" else null)
        Herinneringen.controleer(ctx)
    }
    Kaart {
        Kop("⏰ Herinneringen")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💊 Vitamine D (elke dag)", Modifier.weight(1f))
            Switch(db.get("vit.D") == "1", { schakel("vit.D", it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💊 Vitamine K (borstvoeding, dag 8 t/m week 12)", Modifier.weight(1f))
            Switch(db.get("vit.K") == "1", { schakel("vit.K", it) })
        }
        if (dagen != null && !vitamineKNodig(dagen)) Klein("Vitamine K is voorbij week 12; de herinnering staat stil.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Herinneren om ${db.get("vit.tijd") ?: "09:00"}", Modifier.weight(1f))
            TextButton(onClick = {
                val t = (db.get("vit.tijd") ?: "09:00").split(":").map { it.toInt() }
                android.app.TimePickerDialog(ctx, { _, h, m -> db.set("vit.tijd", "%02d:%02d".format(h, m)); Herinneringen.controleer(ctx) }, t[0], t[1], true).show()
            }) { Text("Tijd") }
        }
        Klein("Alleen als het die dag nog niet gegeven is, ook niet door je partner.")
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("😴 Seintje als de wakkertijd om is", Modifier.weight(1f))
            Switch(db.get("wakker.uit") != "1", { db.set("wakker.uit", if (it) null else "1"); Herinneringen.controleer(ctx) })
        }
        Klein("Wakkertijd op deze leeftijd: ${wakkerVenster(dagen).let { "${it.first}–${it.last}" }} min (vuistregel).")
    }
}

@Composable
fun AfsprakenKaart(db: Db, evs: List<Ev>, nu: Long) {
    var nieuw by remember { mutableStateOf(false) }
    Kaart {
        Kop("📅 Afspraken")
        val komend = evs.filter { it.type == T.AFSPRAAK && it.start > nu - UUR }.sortedBy { it.start }
        if (komend.isEmpty()) Klein("Consultatiebureau, vaccinaties of huisarts: je krijgt de avond ervoor en een uur van tevoren een seintje.")
        komend.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.tekst ?: "Afspraak", fontWeight = FontWeight.Bold)
                    Klein("${datum(a.start)} ${klok(a.start)}")
                }
                TextButton(onClick = { db.put(a.copy(weg = true)) }) { Text("Weg") }
            }
        }
        TextButton(onClick = { nieuw = true }) { Text("＋ Afspraak") }
    }
    if (nieuw) AfspraakDialoog({ nieuw = false }) { db.put(it) }
}

@Composable
fun AfspraakDialoog(sluit: () -> Unit, opslaan: (Ev) -> Unit) {
    val ctx = LocalContext.current
    var wat by remember { mutableStateOf("Consultatiebureau") }
    var tijd by remember { mutableLongStateOf(System.currentTimeMillis().dag().plusDays(1).ms() + 10 * UUR) }
    AlertDialog(
        onDismissRequest = sluit,
        title = { Text("📅 Afspraak") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Consultatiebureau", "Vaccinatie", "Huisarts").forEach { k -> FilterChip(wat == k, { wat = k }, { Text(k, fontSize = 12.sp) }) }
                }
                OutlinedTextField(wat, { wat = it }, label = { Text("Wat") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${datum(tijd)} ${klok(tijd)}", Modifier.weight(1f))
                    TextButton(onClick = { kiesDatum(ctx, tijd) { tijd = it } }) { Text("Dag") }
                    TextButton(onClick = { kiesTijd(ctx, tijd) { tijd = it } }) { Text("Tijd") }
                }
            }
        },
        confirmButton = { Button(enabled = wat.isNotBlank(), onClick = { opslaan(Ev(nieuwId(), T.AFSPRAAK, tijd, tekst = wat.trim())); sluit() }) { Text("Opslaan") } },
        dismissButton = { TextButton(onClick = sluit) { Text("Annuleer") } },
    )
}

@Composable
fun VoorraadKaart(db: Db, evs: List<Ev>, nu: Long) {
    var nieuw by remember { mutableStateOf(false) }
    Kaart {
        Kop("🧊 Voorraad moedermelk")
        val v = evs.filter { it.type == T.VOORRAAD && it.eind == null }.sortedBy { opTot(it) }
        if (v.isEmpty()) Klein("Leeg. Na het kolven kies je waar de melk heen gaat; hij verschijnt dan hier.")
        v.forEach { e ->
            val over = opTot(e) - nu
            Column(Modifier.padding(vertical = 4.dp)) {
                Text("${e.ml ?: "?"} ml · ${e.kant} · gekolfd ${datum(e.start)}", fontWeight = FontWeight.Bold)
                Text(
                    if (over < 0) "⚠️ over de datum" else "op tot ${datum(opTot(e))} ${klok(opTot(e))} (nog ${duur(over)})",
                    fontSize = 13.sp, color = if (over < 12 * UUR) Color(0xFFB8622A) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = {
                        val t = System.currentTimeMillis()
                        db.put(e.copy(eind = t), Ev(nieuwId(), T.FLES, t, ml = e.ml, kant = "moedermelk"))
                    }) { Text("Gegeven") }
                    if (e.kant == "vriezer" || e.kant == "vriesvak") FilledTonalButton(onClick = { db.put(e.copy(kant = "ontdooid", sinds = System.currentTimeMillis())) }) { Text("Ontdooien") }
                    TextButton(onClick = { db.put(e.copy(eind = System.currentTimeMillis(), tekst = "weggegooid")) }) { Text("Weg") }
                }
            }
        }
        TextButton(onClick = { nieuw = true }) { Text("＋ Zelf toevoegen") }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Klein("Bewaren (Voedingscentrum):")
        BEWAREN.forEach { Klein("• $it") }
    }
    if (nieuw) {
        var ml by remember { mutableIntStateOf(100) }
        var plek by remember { mutableStateOf("koelkast") }
        AlertDialog(
            onDismissRequest = { nieuw = false },
            title = { Text("🧊 Voorraad") },
            text = {
                Column {
                    Stepper("Hoeveel", ml, 10) { ml = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("koelkast", "vriezer", "vriesvak").forEach { k -> FilterChip(plek == k, { plek = k }, { Text(k) }) }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val t = System.currentTimeMillis()
                    db.put(Ev(nieuwId(), T.VOORRAAD, t, ml = ml, kant = plek, sinds = t))
                    nieuw = false
                }) { Text("Opslaan") }
            },
            dismissButton = { TextButton(onClick = { nieuw = false }) { Text("Annuleer") } },
        )
    }
}

@SuppressLint("BatteryLife")
@Composable
fun DelenKaart(db: Db, evs: List<Ev>) {
    val ctx = LocalContext.current
    var invullen by remember { mutableStateOf(false) }
    var ontkoppel by remember { mutableStateOf(false) }
    val code = db.get("partner.code")
    Kaart {
        Kop("💞 Delen met je partner")
        if (code == null) {
            Text("Zie allebei hetzelfde logboek. De telefoons praten rechtstreeks met elkaar op de wifi thuis, versleuteld met een partnercode. Geen server, geen account.")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Delen.koppel(ctx, Code.nieuw()) }) { Text("Code maken") }
                FilledTonalButton(onClick = { invullen = true }) { Text("Code invullen") }
            }
            Klein("De één maakt een code, de ander vult die in.")
        } else {
            Text("Partnercode", fontWeight = FontWeight.Bold)
            Text(code, fontFamily = FontFamily.Monospace, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Klein("Laat je partner deze code invullen bij 'Code invullen'. Deel hem met niemand anders.")
            Spacer(Modifier.height(8.dp))
            val peers = Delen.peers(db)
            if (peers.isEmpty()) Text("Nog geen contact gehad. Beide telefoons op dezelfde wifi en de app open.")
            peers.forEach { (_, naam, laatst) ->
                Text("📱 $naam: " + (laatst?.let { "bijgewerkt ${duur(System.currentTimeMillis() - it)} geleden" } ?: "nog niet"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { Delen.nu(ctx) }) { Text("Nu bijwerken") }
                TextButton(onClick = { ontkoppel = true }) { Text("Ontkoppelen") }
            }
            val pm = ctx.getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Klein("Samsung zet apps graag in slaap; dan wacht het delen tot je de app opent.")
                TextButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                }) { Text("Batterijbeperking uitzetten") }
            }
        }
    }
    if (invullen) {
        var tekst by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { invullen = false },
            title = { Text("Partnercode invullen") },
            text = {
                OutlinedTextField(tekst, { tekst = it.uppercase() }, label = { Text("XXXX-XXXX-XXXX") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            },
            confirmButton = { Button(enabled = Code.schoon(tekst) != null, onClick = { Delen.koppel(ctx, tekst); invullen = false }) { Text("Koppelen") } },
            dismissButton = { TextButton(onClick = { invullen = false }) { Text("Annuleer") } },
        )
    }
    if (ontkoppel) {
        AlertDialog(
            onDismissRequest = { ontkoppel = false },
            title = { Text("Ontkoppelen?") },
            text = { Text("Deze telefoon deelt dan niets meer. Wat er al staat blijft staan, ook bij je partner.") },
            confirmButton = { Button(onClick = { Delen.ontkoppel(ctx); ontkoppel = false }) { Text("Ontkoppelen") } },
            dismissButton = { TextButton(onClick = { ontkoppel = false }) { Text("Annuleer") } },
        )
    }
}

@Composable
fun BackupKaart(db: Db, melder: Melder) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val maak = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val n = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { Backup.maak(ctx, db, it) } } }
            melder(n.fold({ "💾 Backup gemaakt: $it regels" }, { "Backup mislukt: ${it.message}" }), null)
        }
    }
    val terug = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val n = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)!!.use { Backup.terug(ctx, db, it) } } }
            melder(n.fold({ "💾 Teruggezet: $it regels nieuw of bijgewerkt" }, { "Terugzetten mislukt: ${it.message}" }), null)
        }
    }
    Kaart {
        Kop("💾 Backup")
        Klein("Eén zip met het hele logboek en de opnames. Bewaar hem waar je wilt (bijvoorbeeld Nextcloud). Terugzetten voegt samen: er gaat niets verloren. De partnercode zit er bewust niet in.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { maak.launch("huiltje-backup-${System.currentTimeMillis().dag()}.zip") }) { Text("Backup maken") }
            FilledTonalButton(onClick = { terug.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Terugzetten") }
        }
    }
}

object Backup {
    private val WAV = Regex("[0-9]{10,16}\\.wav")

    fun maak(ctx: Context, db: Db, os: OutputStream): Int {
        val rijen = db.sinds(0).first
        run {
            ZipOutputStream(os).use { z ->
                z.putNextEntry(ZipEntry("huiltje.json"))
                z.write(JSONObject().put("versie", 1).put("rijen", rijenJson(rijen)).toString().toByteArray())
                z.closeEntry()
                opnamesMap(ctx).listFiles()?.filter { WAV.matches(it.name) }?.forEach { f ->
                    z.putNextEntry(ZipEntry("opnames/${f.name}"))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
        return rijen.size
    }

    /** Alleen huiltje.json en opnames/<getal>.wav worden gelezen; andere namen (ook ../) worden genegeerd. */
    fun terug(ctx: Context, db: Db, ins: InputStream): Int {
        var n = 0
        run {
            ZipInputStream(ins).use { z ->
                generateSequence { z.nextEntry }.forEach { e ->
                    val naam = e.name.substringAfterLast('/')
                    when {
                        e.name == "huiltje.json" -> n += db.merge(rijenVan(JSONObject(z.readBytes().decodeToString()).getJSONArray("rijen")))
                        e.name == "opnames/$naam" && WAV.matches(naam) -> File(opnamesMap(ctx), naam).let { f ->
                            if (!f.exists()) f.outputStream().use { z.copyTo(it) }
                        }
                    }
                }
            }
        }
        return n
    }
}

@Composable
fun WekenKaart(dagen: Long?, altijdOpen: Boolean = false) {
    var open by remember { mutableStateOf(altijdOpen) }
    val huidig = weekVan(dagen)
    Kaart {
        Kop("📅 Week voor week")
        if (!open) {
            huidig?.let { Text("Nu: ${it.titel}") }
            TextButton(onClick = { open = true }) { Text("Alle weken tonen") }
        } else {
            WEKEN.forEach { w ->
                Text(w.titel + if (w == huidig) "  ← nu" else "", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp),
                    color = if (w == huidig) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(w.tekst, fontSize = 14.sp)
            }
            TextButton(onClick = { open = false }) { Text("Inklappen") }
        }
    }
}
