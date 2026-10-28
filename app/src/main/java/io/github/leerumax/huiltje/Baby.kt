package io.github.leerumax.huiltje

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Eén regel in het logboek. Alle soorten in één tabel, zodat delen met de partner één mechanisme is:
 * de laatste wijziging wint (rev, bij gelijk dev) en verwijderen is weg = true.
 *
 * Wat de velden per soort betekenen:
 *  - borst / kolf: sessie; eind = klaar (null = loopt). kolf: ml = links, ml2 = rechts.
 *  - borst_deel / kolf_deel: één kant van een sessie; kant L/R, sessie = id van de sessie.
 *  - fles: ml, kant = moedermelk/kunstvoeding.
 *  - voorraad: ml, kant = koelkast/vriezer/vriesvak/ontdooid, sinds = sinds wanneer daar, eind = op/weg.
 *  - luier: kant = nat/poep. slaap: start/eind. vitamine: kant = D/K. groei: gram, cm. afspraak: tekst.
 *  - huil: probs, label (bevestigd), emb; tekst = kansen alleen op geluid.
 *  - baby (id "baby"): tekst = naam, start = geboortedatum (ms), kant = jongen/meisje.
 */
data class Ev(
    val id: String,
    val type: String,
    val start: Long,
    val eind: Long? = null,
    val kant: String? = null,
    val ml: Int? = null,
    val ml2: Int? = null,
    val sessie: String? = null,
    val gram: Int? = null,
    val cm: Float? = null,
    val label: Int? = null,
    val probs: FloatArray? = null,
    val emb: FloatArray? = null,
    val tekst: String? = null,
    val sinds: Long? = null,
    val wie: String? = null,
    val weg: Boolean = false,
    val rev: Long = 0,
    val dev: String = "",
)

object T {
    const val BORST = "borst"
    const val KOLF = "kolf"
    const val FLES = "fles"
    const val VOORRAAD = "voorraad"
    const val LUIER = "luier"
    const val SLAAP = "slaap"
    const val HUIL = "huil"
    const val VITAMINE = "vitamine"
    const val GROEI = "groei"
    const val AFSPRAAK = "afspraak"
    const val BABY = "baby"
    fun deel(soort: String) = soort + "_deel"
}

const val MIN = 60_000L
const val UUR = 60 * MIN
const val DAG = 24 * UUR

/** Een borst- of kolfsessie zonder lopende timer en zonder tik in dit tijdsbestek telt als klaar. */
const val SESSIE_STIL = 30 * MIN

fun nieuwId(): String = UUID.randomUUID().toString()

fun Long.dag(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
fun LocalDate.ms(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun Long.uur(): Int = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).hour

/**
 * Een borst- of kolfsessie met zijn kanten (delen, op starttijd). Begon = de kant die als eerste startte,
 * eindigde = de kant die als laatste stopte; bij kolven kunnen beide kanten tegelijk lopen.
 */
class Sessie(val ev: Ev, val delen: List<Ev>) {
    val begon get() = delen.firstOrNull()?.kant
    val eindigde get() = delen.maxByOrNull { it.eind ?: Long.MAX_VALUE }?.kant
    val lopend get() = delen.filter { it.eind == null }
    val loopt get() = lopend.lastOrNull()
    val laatsteTik get() = if (delen.isEmpty()) ev.start else delen.maxOf { it.eind ?: Long.MAX_VALUE }
    val einde get() = ev.eind ?: delen.mapNotNull { it.eind }.maxOrNull() ?: ev.start
    fun ms(kant: String, nu: Long) = delen.filter { it.kant == kant }.sumOf { (it.eind ?: nu) - it.start }
    fun open(nu: Long) = ev.eind == null && (loopt != null || nu - laatsteTik < SESSIE_STIL)
}

fun sessies(evs: List<Ev>, soort: String): List<Sessie> {
    val delen = evs.filter { it.type == T.deel(soort) && !it.weg }.groupBy { it.sessie }
    return evs.filter { it.type == soort && !it.weg }.sortedBy { it.start }
        .map { s -> Sessie(s, delen[s.id].orEmpty().sortedBy { it.start }) }
}

fun openSessie(evs: List<Ev>, soort: String, nu: Long) = sessies(evs, soort).lastOrNull()?.takeIf { it.open(nu) }

/**
 * Tik op links of rechts. Borst: één kant tegelijk, de lopende kant stopt. Kolven: elke kant start en stopt los,
 * beide mogen tegelijk lopen. Tik op een kant die loopt = die kant stopt. Geeft de regels die opgeslagen moeten worden.
 */
fun tik(evs: List<Ev>, soort: String, kant: String, nu: Long, wie: String?): List<Ev> {
    val out = mutableListOf<Ev>()
    val alle = sessies(evs, soort)
    // Vergeten op Klaar te drukken: die sessie sluiten op de laatste tik.
    alle.filter { it.ev.eind == null && !it.open(nu) }.forEach { out += it.ev.copy(eind = it.einde) }
    val s = alle.lastOrNull()?.takeIf { it.open(nu) }
    val sid = s?.ev?.id ?: nieuwId().also { out += Ev(it, soort, nu, wie = wie) }
    val lopend = s?.lopend.orEmpty()
    val dezeKant = lopend.find { it.kant == kant }
    if (dezeKant != null) out += dezeKant.copy(eind = nu)
    else {
        if (soort == T.BORST) lopend.forEach { out += it.copy(eind = nu) }
        out += Ev(nieuwId(), T.deel(soort), nu, kant = kant, sessie = sid, wie = wie)
    }
    return out
}

fun klaar(s: Sessie, nu: Long): List<Ev> {
    val gestopt = s.lopend.map { it.copy(eind = nu) }
    return gestopt + s.ev.copy(eind = if (gestopt.isEmpty()) s.einde else nu)
}

/** Begin waar je vorige keer eindigde als je beide kanten gaf; gaf je één kant, dan de andere. */
fun volgendeKant(evs: List<Ev>): String? {
    val s = sessies(evs, T.BORST).lastOrNull { it.delen.isNotEmpty() } ?: return null
    return if (s.delen.map { it.kant }.toSet().size == 2) s.eindigde else if (s.eindigde == "L") "R" else "L"
}

fun kantNaam(k: String?) = when (k) { "L" -> "links"; "R" -> "rechts"; else -> "?" }

/** Start van de laatste voeding (borst of fles). */
fun laatsteVoeding(evs: List<Ev>): Ev? =
    evs.filter { !it.weg && (it.type == T.BORST || it.type == T.FLES) }.maxByOrNull { it.start }

sealed interface Slaap {
    data class Slaapt(val sinds: Long) : Slaap
    data class Wakker(val sinds: Long, val slaapId: String) : Slaap
}

fun slaapStatus(evs: List<Ev>): Slaap? {
    val s = evs.filter { it.type == T.SLAAP && !it.weg }.maxByOrNull { it.start } ?: return null
    return if (s.eind == null) Slaap.Slaapt(s.start) else Slaap.Wakker(s.eind, s.id)
}

/** Hoe lang baby's van deze leeftijd meestal wakker blijven, in minuten. Vuistregels, geen wet. */
fun wakkerVenster(dagenOud: Long?): IntRange = when {
    dagenOud == null -> 45..75
    dagenOud < 28 -> 35..60
    dagenOud < 84 -> 60..90
    dagenOud < 120 -> 75..120
    else -> 120..180
}

fun dagenOud(baby: Ev?, nu: Long): Long? =
    baby?.takeIf { it.start > 0 && !it.weg }?.let { java.time.temporal.ChronoUnit.DAYS.between(it.start.dag(), nu.dag()) }

fun leeftijd(dagen: Long): String = when {
    dagen < 14 -> "$dagen ${if (dagen == 1L) "dag" else "dagen"}"
    dagen < 91 -> "${dagen / 7} weken" + (dagen % 7).let { if (it == 0L) "" else " en $it ${if (it == 1L) "dag" else "dagen"}" }
    else -> "${(dagen / 30.44).toInt()} maanden"
}

/** Voedingscentrum (2023): koelkast 4 °C 3 dagen, vriezer *** 6 maanden, vriesvak 2 weken, ontdooid 24 uur. */
fun houdbaar(plek: String?): Long = when (plek) {
    "koelkast" -> 3 * DAG
    "vriezer" -> 182 * DAG
    "vriesvak" -> 14 * DAG
    else -> DAG
}

fun opTot(v: Ev): Long = (v.sinds ?: v.start) + houdbaar(v.kant)

fun vitamineKNodig(dagen: Long?): Boolean = dagen == null || dagen in 8..84

fun duur(ms: Long): String {
    val m = (ms / MIN).coerceAtLeast(0)
    return if (m < 60) "$m min" else "${m / 60} u ${"%02d".format(m % 60)}"
}

/**
 * De klok als voorkennis bij het herkennen: lang na de voeding maakt honger (Neh) waarschijnlijker, lang wakker
 * maakt moe (Owh) waarschijnlijker, vlak na de voeding een boertje (Eh). Volgorde = SOUNDS.
 * ponytail: vaste vuistregels; uit eigen bevestigingen leren als ze er vaak naast blijken te zitten.
 */
fun klokFactor(minNaVoeding: Long?, minWakker: Long?): FloatArray {
    val f = FloatArray(5) { 1f }
    if (minNaVoeding != null) {
        f[0] = (0.4f + minNaVoeding / 120f).coerceIn(0.4f, 2f)
        if (minNaVoeding <= 30) f[4] = 1.6f
    }
    if (minWakker != null) f[1] = (0.5f + minWakker / 90f).coerceIn(0.5f, 2f)
    return f
}

fun metKlok(p: FloatArray, f: FloatArray): FloatArray {
    val q = FloatArray(p.size) { p[it] * f[it] }
    val s = q.sum()
    return FloatArray(p.size) { q[it] / s }
}

fun huilLabel(e: Ev): Int? = e.label ?: e.probs?.let { p -> p.indices.maxBy { p[it] } }

class DagStat(
    val dag: LocalDate, val voedingen: Int, val borstMin: Long, val flesMl: Int, val kolfMl: Int,
    val nat: Int, val poep: Int, val slaapMin: Long, val huiltjes: Int,
)

fun dagStats(evs: List<Ev>, dagen: Int, nu: Long): List<DagStat> {
    val levend = evs.filter { !it.weg }
    val borst = sessies(levend, T.BORST)
    val kolf = sessies(levend, T.KOLF)
    val slaap = levend.filter { it.type == T.SLAAP }
    return (dagen - 1 downTo 0).map { terug ->
        val d = nu.dag().minusDays(terug.toLong())
        val van = d.ms()
        val tot = d.plusDays(1).ms()
        fun op(t: Long) = t in van until tot
        val bs = borst.filter { op(it.ev.start) }
        DagStat(
            d,
            voedingen = bs.size + levend.count { it.type == T.FLES && op(it.start) },
            borstMin = bs.sumOf { it.ms("L", nu) + it.ms("R", nu) } / MIN,
            flesMl = levend.filter { it.type == T.FLES && op(it.start) }.sumOf { it.ml ?: 0 },
            kolfMl = kolf.filter { op(it.ev.start) }.sumOf { (it.ev.ml ?: 0) + (it.ev.ml2 ?: 0) },
            nat = levend.count { it.type == T.LUIER && it.kant == "nat" && op(it.start) },
            poep = levend.count { it.type == T.LUIER && it.kant == "poep" && op(it.start) },
            slaapMin = slaap.sumOf { s -> (minOf(s.eind ?: nu, tot) - maxOf(s.start, van)).coerceAtLeast(0) } / MIN,
            huiltjes = levend.count { it.type == T.HUIL && op(it.start) },
        )
    }
}

private val DAGDEEL = listOf("'s nachts", "'s ochtends", "'s middags", "'s avonds")

/** Korte zinnetjes over wat opvalt in de laatste twee weken. Pas vanaf 5 keer per geluid. */
fun inzichten(evs: List<Ev>, nu: Long): List<String> {
    val levend = evs.filter { !it.weg && it.start > nu - 14 * DAG }
    val voedingen = levend.filter { it.type == T.BORST || it.type == T.FLES }.map { it.start }.sorted()
    val out = mutableListOf<String>()
    levend.filter { it.type == T.HUIL }.groupBy { huilLabel(it) }.forEach { (l, hs) ->
        if (l == null || hs.size < 5) return@forEach
        val s = SOUNDS[l]
        val (deel, n) = hs.groupingBy { it.start.uur() / 6 }.eachCount().maxBy { it.value }
        if (n * 2 >= hs.size) out += "${s.emoji} ${s.meaning} vooral ${DAGDEEL[deel]} ($n van ${hs.size})."
        val naVoeding = hs.count { h -> voedingen.any { h.start - it in 0..UUR } }
        if (naVoeding * 10 >= hs.size * 6) out += "${s.emoji} ${s.meaning} vaak binnen een uur na een voeding ($naVoeding van ${hs.size})."
    }
    val recent = voedingen.filter { it > nu - 3 * DAG }
    if (recent.size >= 4) {
        val gem = recent.zipWithNext { a, b -> b - a }.average().toLong()
        out += "🍼 Gemiddeld ${duur(gem)} tussen het begin van twee voedingen (laatste 3 dagen)."
    }
    return out
}

/** WHO-groeistandaard: waarde bij z-score uit L, M, S. */
fun lmsWaarde(l: Float, m: Float, s: Float, z: Float): Float =
    if (l == 0f) m * kotlin.math.exp(s * z) else m * Math.pow((1 + l * s * z).toDouble(), 1.0 / l).toFloat()

/** Percentiellijn per week (0..52) uit een WHO-tabel van [L, M, S]-drietallen per week. */
fun lmsLijn(tabel: FloatArray, z: Float): List<Float> = (0 until tabel.size / 3).map { w ->
    lmsWaarde(tabel[w * 3], tabel[w * 3 + 1], tabel[w * 3 + 2], z)
}

/** Slaapknop: slaapt de baby, dan is die nu wakker; anders begint nu een slaapje. */
fun slaapTik(evs: List<Ev>, nu: Long): Ev =
    evs.filter { it.type == T.SLAAP && !it.weg && it.eind == null }.maxByOrNull { it.start }?.copy(eind = nu)
        ?: Ev(nieuwId(), T.SLAAP, nu)

/**
 * Borst- of kolfsessie uit handmatige invoer: de kanten na elkaar vanaf [start], eerst [begon] (als beide > 0).
 * Bij aanpassen gaan de oude kanten weg en houdt de sessie zijn id. Geeft alle regels om op te slaan.
 */
fun sessieUitInvoer(soort: String, oud: Sessie?, start: Long, minL: Int, minR: Int, begon: String, mlL: Int, mlR: Int): List<Ev> {
    val eerst = if (minL > 0 && minR > 0) begon else if (minL > 0) "L" else "R"
    val sessie = oud?.ev ?: Ev(nieuwId(), soort, start)
    var t = start
    val delen = listOf(eerst, if (eerst == "L") "R" else "L").map { it to if (it == "L") minL else minR }.filter { it.second > 0 }
        .map { (k, m) -> Ev(nieuwId(), T.deel(soort), t, eind = t + m * MIN, kant = k, sessie = sessie.id).also { t += m * MIN } }
    val kolf = soort == T.KOLF
    return listOf(sessie.copy(start = start, eind = t, ml = if (kolf) mlL else null, ml2 = if (kolf) mlR else null)) +
        oud?.delen.orEmpty().map { it.copy(weg = true) } + delen
}
