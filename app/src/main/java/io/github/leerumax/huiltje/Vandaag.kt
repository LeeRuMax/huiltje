@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.leerumax.huiltje

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun Vandaag(db: Db, evs: List<Ev>, nacht: Boolean, melder: Melder, verzoek: MutableState<String?>, naarLuisteren: () -> Unit) {
    var nu by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); nu = System.currentTimeMillis() } }
    var toonBorst by remember { mutableStateOf(false) }
    var toonKolf by remember { mutableStateOf(false) }
    var fles by remember { mutableStateOf(false) }
    var vitamine by remember { mutableStateOf(false) }
    var kolfKlaar by remember { mutableStateOf<Sessie?>(null) }
    LaunchedEffect(verzoek.value) {
        if (verzoek.value == "fles") fles = true
        verzoek.value = null
    }
    val baby = evs.find { it.type == T.BABY }
    val dagen = dagenOud(baby, nu)
    val borst = openSessie(evs, T.BORST, nu)
    val kolf = openSessie(evs, T.KOLF, nu)
    val mij = remember(evs) { db.mijnNaam }
    val hoog = if (nacht) 88.dp else 68.dp

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("👶 ${baby?.tekst?.takeIf { it.isNotBlank() } ?: "Vandaag"}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                dagen?.let { Klein("${leeftijd(it)} oud") }
            }
            TextButton(onClick = { db.set("nacht", if (nacht) "uit" else "aan") }) { Text(if (nacht) "☀️ Dag" else "🌙 Nacht") }
        }

        if (borst != null || toonBorst) {
            SessieKaart(db, evs, T.BORST, borst, nu, if (borst == null) volgendeKant(evs) else null, hoog,
                sluit = { toonBorst = false }, klaar = { s -> db.put(*klaar(s, nu).toTypedArray()); toonBorst = false; melder("🤱 Voeding opgeslagen", null) })
        }
        if (kolf != null || toonKolf) {
            SessieKaart(db, evs, T.KOLF, kolf, nu, null, hoog, sluit = { toonKolf = false }, klaar = { kolfKlaar = it })
        }

        StatusKaart(evs, dagen, nu, mij)

        val slaapt = slaapStatus(evs) is Slaap.Slaapt
        val knoppen = listOf<Triple<String, Boolean, () -> Unit>>(
            Triple("🤱 Borstvoeding", borst != null, { toonBorst = true }),
            Triple("🍼 Fles", false, { fles = true }),
            Triple("🥛 Kolven", kolf != null, { toonKolf = true }),
            Triple(if (slaapt) "☀️ Wakker" else "😴 Slaapt", slaapt, {
                val e = slaapTik(evs, System.currentTimeMillis())
                db.put(e)
                melder(if (e.eind == null) "😴 Slaapje begonnen" else "☀️ Wakker na ${duur(e.eind - e.start)}", null)
            }),
            Triple("💧 Nat", false, { Ev(nieuwId(), T.LUIER, System.currentTimeMillis(), kant = "nat").also { db.put(it); melder("💧 Natte luier opgeslagen", it) } }),
            Triple("💩 Poep", false, { Ev(nieuwId(), T.LUIER, System.currentTimeMillis(), kant = "poep").also { db.put(it); melder("💩 Poepluier opgeslagen", it) } }),
            Triple("💊 Vitamine", false, { vitamine = true }),
            Triple("👂 Luister", false, naarLuisteren),
        )
        knoppen.chunked(2).forEach { rij ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rij.forEach { (t, aan, klik) ->
                    Button(
                        onClick = klik, modifier = Modifier.weight(1f).height(hoog),
                        colors = if (aan) zacht()
                        else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
                    ) { Text(t, fontSize = 17.sp) }
                }
            }
        }

        if (!nacht) WeekKaart(dagen)

        val vandaag = regels(evs, mij, nu).filter { it.ev.start.dag() == nu.dag() }
        if (vandaag.isNotEmpty()) {
            Kaart {
                Kop("Vandaag")
                vandaag.take(8).forEach { RegelRij(it) }
                if (vandaag.size > 8) Klein("… en ${vandaag.size - 8} meer in het Logboek")
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (fles) FlesDialoog(evs, { fles = false }) { e -> db.put(e); melder("🍼 ${e.ml} ml opgeslagen", e) }
    if (vitamine) VitamineDialoog(dagen, { vitamine = false }) { soorten ->
        soorten.forEach { db.put(Ev(nieuwId(), T.VITAMINE, System.currentTimeMillis(), kant = it)) }
        melder("💊 Vitamine ${soorten.joinToString(" + ")} gegeven", null)
    }
    kolfKlaar?.let { s ->
        KolfKlaarDialoog({ kolfKlaar = null }) { ml, ml2, plek ->
            val t = System.currentTimeMillis()
            val rijen = klaar(s, t).map { if (it.id == s.ev.id) it.copy(ml = ml, ml2 = ml2) else it }.toMutableList()
            when (plek) {
                "meteen" -> rijen += Ev(nieuwId(), T.FLES, t, ml = ml + ml2, kant = "moedermelk")
                "koelkast", "vriezer", "vriesvak" -> rijen += Ev(nieuwId(), T.VOORRAAD, s.ev.start, ml = ml + ml2, kant = plek, sinds = t)
            }
            db.put(*rijen.toTypedArray())
            kolfKlaar = null
            toonKolf = false
            melder("🥛 ${ml + ml2} ml gekolfd", null)
        }
    }
}

/** Twee grote knoppen, links en rechts, elk met een eigen timer. Borst: één tegelijk; kolven: beide mogen tegelijk. */
@Composable
fun SessieKaart(db: Db, evs: List<Ev>, soort: String, s: Sessie?, nu: Long, aanbevolen: String?, hoog: androidx.compose.ui.unit.Dp, sluit: () -> Unit, klaar: (Sessie) -> Unit) {
    Kaart(MaterialTheme.colorScheme.secondaryContainer) {
        Kop(if (soort == T.BORST) "🤱 Borstvoeding" else "🥛 Kolven")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in listOf("L", "R")) {
                val loopt = s?.lopend?.any { it.kant == k } == true
                Button(
                    onClick = { db.put(*tik(evs, soort, k, System.currentTimeMillis(), null).toTypedArray()) },
                    modifier = Modifier.weight(1f).height(hoog + 40.dp),
                    colors = if (loopt) zacht()
                    else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(kantNaam(k).replaceFirstChar { it.uppercase() }, fontSize = 18.sp)
                        Text(mmss(s?.ms(k, nu) ?: 0), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                loopt -> if (soort == T.BORST) "▶ loopt · tik = pauze" else "▶ loopt · tik = stop"
                                aanbevolen == k -> "★ begin hier"
                                else -> " "
                            },
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (s != null && s.delen.isNotEmpty()) {
            Text(sessieVolgorde(s).orEmpty(), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
        } else {
            Klein(
                if (soort == T.BORST) "Tik op een kant om te beginnen. Wisselen: tik op de andere kant."
                else "Tik een kant aan als je begint en weer als je stopt. Beide kanten mogen tegelijk lopen.",
            )
        }
        var weg by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (s == null) TextButton(onClick = sluit) { Text("Annuleer") }
            else {
                TextButton(onClick = { weg = true }) { Text("Annuleren") }
                Button(onClick = { klaar(s) }) { Text("Klaar") }
            }
        }
        if (weg && s != null) WeggooiDialoog(soort == T.KOLF, { weg = false }) {
            db.put(s.ev.copy(weg = true), *s.delen.map { it.copy(weg = true) }.toTypedArray())
            weg = false
            sluit()
        }
    }
}

@Composable
fun StatusKaart(evs: List<Ev>, dagen: Long?, nu: Long, mij: String?) {
    Kaart {
        val lv = laatsteVoeding(evs)
        if (lv == null) Text("🍼 Nog geen voeding gelogd", fontSize = 18.sp)
        else {
            Text("🍼 Laatste voeding ${duur(nu - lv.start)} geleden", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            val r = regel(lv, sessies(evs, T.BORST).find { it.ev.id == lv.id }, mij, nu)
            Klein("${klok(lv.start)} · ${r.titel}" + (r.sub?.let { "\n$it" } ?: ""))
            r.nadruk?.let { Nadruk(it) }
        }
        volgendeKant(evs)?.let {
            Text("➡️ Volgende keer beginnen met ${kantNaam(it)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        when (val s = slaapStatus(evs)) {
            is Slaap.Slaapt -> Text("😴 Slaapt sinds ${duur(nu - s.sinds)}")
            is Slaap.Wakker -> {
                val v = wakkerVenster(dagen)
                val min = (nu - s.sinds) / MIN
                Text("☀️ Wakker sinds ${duur(nu - s.sinds)}" + if (min >= v.first) " · tijd voor een slaapje?" else "")
                Klein("Op deze leeftijd meestal ${v.first}–${v.last} min wakker.")
            }
            null -> Klein("😴 Tik op 'Slaapt' als de baby gaat slapen, dan houdt de app de wakkertijd bij.")
        }
        val vandaag = evs.filter { it.start.dag() == nu.dag() }
        val nat = vandaag.count { it.type == T.LUIER && it.kant == "nat" }
        val poep = vandaag.count { it.type == T.LUIER && it.kant == "poep" }
        Text("💧 $nat nat · 💩 $poep poep vandaag", Modifier.padding(top = 6.dp))
        if (dagen == null || dagen >= 5) Klein("Vanaf dag 5: 5 of meer flinke natte luiers per dag = genoeg drinken.")
        val d = vandaag.any { it.type == T.VITAMINE && it.kant == "D" }
        val k = vandaag.any { it.type == T.VITAMINE && it.kant == "K" }
        Text(
            "💊 Vitamine D ${if (d) "✓" else "nog niet"}" + if (vitamineKNodig(dagen)) " · K ${if (k) "✓" else "nog niet"}" else "",
            Modifier.padding(top = 6.dp),
        )
        val voorraad = evs.filter { it.type == T.VOORRAAD && it.eind == null }
        if (voorraad.isNotEmpty()) {
            val eerst = voorraad.minBy { opTot(it) }
            Text("🧊 Voorraad: ${voorraad.size} × · ${voorraad.sumOf { it.ml ?: 0 }} ml", Modifier.padding(top = 6.dp))
            val over = opTot(eerst) - nu
            if (over < 12 * UUR) Text(
                if (over < 0) "⚠️ ${eerst.ml} ml uit de ${eerst.kant} is over de datum" else "⚠️ ${eerst.ml} ml uit de ${eerst.kant} moet vóór ${klok(opTot(eerst))} op",
                color = Color(0xFFB8622A),
            ) else Klein("Eerstvolgende op: ${datum(opTot(eerst))}")
        }
    }
}

@Composable
fun WeekKaart(dagen: Long?) {
    val w = weekVan(dagen)
    Kaart(Color(0xFFB39DEB).copy(alpha = 0.25f)) {
        if (w == null) {
            Kop("📅 Week voor week")
            Text("Vul bij Meer de geboortedatum in, dan zie je hier wat er deze week normaal is.")
        } else {
            Kop("📅 ${w.titel}")
            Text(w.tekst)
        }
    }
}

/** [nadruk] = de volgorde van de kanten bij borst/kolf: vet, want dat wil je in één oogopslag zien. */
class Regel(val ev: Ev, val titel: String, val sub: String?, val nadruk: String? = null)

fun sessieKanten(s: Sessie, nu: Long, kolf: Boolean): String {
    if (s.delen.isEmpty()) return "nog geen kant gekozen"
    return listOf("L", "R").filter { k -> s.delen.any { it.kant == k } }.joinToString(" · ") { k ->
        val ml = if (kolf) (if (k == "L") s.ev.ml else s.ev.ml2)?.let { " ($it ml)" } ?: "" else ""
        "${kantNaam(k).replaceFirstChar { it.uppercase() }} ${duur(s.ms(k, nu))}$ml"
    } + if (s.lopend.isNotEmpty()) " · loopt nog" else ""
}

fun sessieVolgorde(s: Sessie): String? = when {
    s.delen.isEmpty() -> null
    s.delen.map { it.kant }.toSet().size == 1 -> "Alleen ${kantNaam(s.begon)}"
    s.lopend.isNotEmpty() -> "Begon ${kantNaam(s.begon)} → nu ${s.lopend.joinToString(" + ") { kantNaam(it.kant) }}"
    else -> "Begon ${kantNaam(s.begon)} → eindigde ${kantNaam(s.eindigde)}"
}

@Composable
fun Nadruk(t: String) = Text(t, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

/** Hoe een logboekregel eruitziet, voor Vandaag, Logboek en de widget-status. */
fun regel(e: Ev, s: Sessie?, mij: String?, nu: Long): Regel {
    val door = e.wie?.takeIf { it.isNotBlank() && it != mij }?.let { " · $it" } ?: ""
    fun tot(eind: Long?) = eind?.let { "–${klok(it)}" } ?: ""
    return when (e.type) {
        T.BORST -> Regel(e, "🤱 Borstvoeding${s?.takeIf { it.loopt == null && it.ev.eind != null }?.let { tot(it.einde) } ?: ""}$door", s?.let { sessieKanten(it, nu, false) }, s?.let(::sessieVolgorde))
        T.KOLF -> Regel(e, "🥛 Kolven${tot(e.eind)}${if (e.ml != null || e.ml2 != null) " · ${(e.ml ?: 0) + (e.ml2 ?: 0)} ml" else ""}$door", s?.let { sessieKanten(it, nu, true) }, s?.let(::sessieVolgorde))
        T.FLES -> Regel(e, "🍼 Fles ${e.ml ?: "?"} ml$door", e.kant)
        T.LUIER -> Regel(e, (if (e.kant == "poep") "💩 Poepluier" else "💧 Natte luier") + door, null)
        T.SLAAP -> Regel(e, "😴 Slaap${tot(e.eind)}$door", if (e.eind == null) "slaapt nog · ${duur(nu - e.start)}" else duur(e.eind - e.start))
        T.HUIL -> {
            val l = huilLabel(e)
            val s2 = l?.let { SOUNDS[it] }
            Regel(
                e, "👂 Huiltje: ${s2?.emoji ?: ""} ${s2?.name ?: "?"} (${s2?.meaning ?: "?"})$door",
                if (e.label != null) "bevestigd" else "app dacht ${e.probs?.let { p -> "${(p.max() * 100).toInt()} %" } ?: ""} · tik om te bevestigen",
            )
        }
        T.VITAMINE -> Regel(e, "💊 Vitamine ${e.kant}$door", null)
        T.GROEI -> Regel(e, "📏 " + listOfNotNull(e.gram?.let { "$it g" }, e.cm?.let { "${cmTekst(it)} cm" }).joinToString(" · ") + door, null)
        T.VOORRAAD -> Regel(e, "🧊 ${e.ml ?: "?"} ml in de ${e.kant}$door", if (e.eind != null) "op" else "op tot ${datum(opTot(e))} ${klok(opTot(e))}")
        T.AFSPRAAK -> Regel(e, "📅 ${e.tekst ?: "Afspraak"}$door", null)
        else -> Regel(e, e.type, null)
    }
}

fun regels(evs: List<Ev>, mij: String?, nu: Long): List<Regel> {
    val ss = (sessies(evs, T.BORST) + sessies(evs, T.KOLF)).associateBy { it.ev.id }
    return evs.filter { !it.weg && it.type != T.deel(T.BORST) && it.type != T.deel(T.KOLF) && it.type != T.BABY && it.type != T.SERVER }
        .sortedByDescending { it.start }.map { regel(it, ss[it.id], mij, nu) }
}

@Composable
fun RegelRij(r: Regel, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(klok(r.ev.start), Modifier.width(52.dp), fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f)) {
            Text(r.titel)
            r.sub?.let { Klein(it) }
            r.nadruk?.let { Nadruk(it) }
        }
    }
}

@Composable
fun FlesDialoog(evs: List<Ev>, sluit: () -> Unit, opslaan: (Ev) -> Unit) {
    val ctx = LocalContext.current
    val vorige = evs.filter { it.type == T.FLES }.maxByOrNull { it.start }
    var ml by remember { mutableIntStateOf(vorige?.ml ?: 90) }
    var soort by remember { mutableStateOf(vorige?.kant ?: "kunstvoeding") }
    var tijd by remember { mutableLongStateOf(System.currentTimeMillis()) }
    AlertDialog(
        onDismissRequest = sluit,
        title = { Text("🍼 Fles") },
        text = {
            Column {
                Stepper("Gedronken", ml, 10) { ml = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("kunstvoeding", "moedermelk").forEach { k ->
                        FilterChip(selected = soort == k, onClick = { soort = k }, label = { Text(k) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Om ${klok(tijd)}", Modifier.weight(1f))
                    TextButton(onClick = { kiesTijd(ctx, tijd) { tijd = it } }) { Text("Andere tijd") }
                }
            }
        },
        confirmButton = { Button(onClick = { opslaan(Ev(nieuwId(), T.FLES, tijd, ml = ml, kant = soort)); sluit() }) { Text("Opslaan") } },
        dismissButton = { TextButton(onClick = sluit) { Text("Annuleer") } },
    )
}

@Composable
fun VitamineDialoog(dagen: Long?, sluit: () -> Unit, gegeven: (List<String>) -> Unit) {
    val metK = vitamineKNodig(dagen)
    var d by remember { mutableStateOf(true) }
    var k by remember { mutableStateOf(metK) }
    AlertDialog(
        onDismissRequest = sluit,
        title = { Text("💊 Vitamine") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(d, { d = it }); Text("Vitamine D") }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(k, { k = it }); Text("Vitamine K" + if (metK) "" else " (na week 12 meestal niet meer)") }
            }
        },
        confirmButton = { Button(enabled = d || k, onClick = { gegeven(listOfNotNull("D".takeIf { d }, "K".takeIf { k })); sluit() }) { Text("Gegeven") } },
        dismissButton = { TextButton(onClick = sluit) { Text("Annuleer") } },
    )
}

@Composable
fun KolfKlaarDialoog(sluit: () -> Unit, opslaan: (ml: Int, ml2: Int, plek: String) -> Unit) {
    var l by remember { mutableIntStateOf(0) }
    var r by remember { mutableIntStateOf(0) }
    var plek by remember { mutableStateOf("koelkast") }
    AlertDialog(
        onDismissRequest = sluit,
        title = { Text("🥛 Hoeveel gekolfd?") },
        text = {
            Column {
                Stepper("Links", l, 5) { l = it }
                Stepper("Rechts", r, 5) { r = it }
                Text("Samen ${l + r} ml", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Waar gaat het heen?")
                listOf("koelkast" to "Koelkast", "vriezer" to "Vriezer", "vriesvak" to "Vriesvak", "meteen" to "Meteen gegeven").chunked(2).forEach { rij ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rij.forEach { (k, t) -> FilterChip(selected = plek == k, onClick = { plek = k }, label = { Text(t) }) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { opslaan(l, r, plek) }) { Text("Opslaan") } },
        dismissButton = { OutlinedButton(onClick = sluit) { Text("Terug") } },
    )
}
