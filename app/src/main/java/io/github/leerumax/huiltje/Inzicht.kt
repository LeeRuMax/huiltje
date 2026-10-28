@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.leerumax.huiltje

import android.graphics.Paint
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Inzicht(db: Db, evs: List<Ev>) {
    val nu = System.currentTimeMillis()
    var meting by remember { mutableStateOf(false) }
    val baby = evs.find { it.type == T.BABY }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(16.dp))
        Text("📈 Inzicht", fontSize = 26.sp, fontWeight = FontWeight.Bold)

        val zinnen = remember(evs) { inzichten(evs, nu) }
        Kaart {
            Kop("Wat opvalt")
            if (zinnen.isEmpty()) Klein("Na een paar dagen loggen en luisteren verschijnen hier patronen, zoals wanneer de krampjes vooral komen.")
            zinnen.forEach { Text("• $it", Modifier.padding(vertical = 2.dp)) }
        }

        Kaart {
            Kop("Afgelopen 7 dagen")
            remember(evs) { dagStats(evs, 7, nu) }.reversed().forEach { d ->
                Text(if (d.dag == nu.dag()) "Vandaag" else datum(d.dag.ms()), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                Klein(
                    listOfNotNull(
                        "🍼 ${d.voedingen}×",
                        d.borstMin.takeIf { it > 0 }?.let { "🤱 $it min" },
                        d.flesMl.takeIf { it > 0 }?.let { "fles $it ml" },
                        d.kolfMl.takeIf { it > 0 }?.let { "🥛 $it ml" },
                        "💧 ${d.nat} 💩 ${d.poep}",
                        d.slaapMin.takeIf { it > 0 }?.let { "😴 ${duur(it * MIN)}" },
                        d.huiltjes.takeIf { it > 0 }?.let { "👂 $it" },
                    ).joinToString(" · "),
                )
            }
        }

        Kaart {
            Kop("Huiltjes per uur (2 weken)")
            HuilKlok(evs, nu)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SOUNDS.forEach { s ->
                    Text("■", fontSize = 12.sp, color = Color(s.kleur))
                    Text(s.name, fontSize = 12.sp)
                }
            }
        }

        var gewicht by remember { mutableStateOf(true) }
        Kaart {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Kop("Groei")
                Spacer(Modifier.weight(1f))
                FilterChip(gewicht, { gewicht = true }, { Text("Gewicht") })
                Spacer(Modifier.width(6.dp))
                FilterChip(!gewicht, { gewicht = false }, { Text("Lengte") })
            }
            val metingen = evs.filter { it.type == T.GROEI && (if (gewicht) it.gram != null else it.cm != null) }.sortedBy { it.start }
            if (baby == null || baby.start <= 0) Klein("Vul bij Meer de geboortedatum in voor de groeicurve.")
            else GroeiCurve(metingen, baby, gewicht)
            if (baby?.kant == null) Klein("Kies bij Meer jongen of meisje voor de WHO-lijnen.")
            else Klein("Lijnen: WHO-groeistandaard, 3e/15e/50e/85e/97e percentiel. Het consultatiebureau gebruikt de Nederlandse (TNO) curves; kleine verschillen zijn normaal.")
            metingen.takeLast(5).reversed().forEach { m ->
                Text("${datum(m.start)}: " + if (gewicht) "${m.gram} g" else "${cmTekst(m.cm!!)} cm", fontSize = 14.sp)
            }
            Button(onClick = { meting = true }, colors = zacht()) { Text("＋ Gewicht of lengte invullen") }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (meting) BewerkDialoog(db, Ev(nieuwId(), T.GROEI, System.currentTimeMillis()), nieuw = true) { meting = false }
}

@Composable
fun HuilKlok(evs: List<Ev>, nu: Long) {
    val per = remember(evs) {
        Array(24) { IntArray(5) }.also { a ->
            evs.filter { it.type == T.HUIL && it.start > nu - 14 * DAG }.forEach { h -> huilLabel(h)?.let { a[h.start.uur()][it]++ } }
        }
    }
    val max = per.maxOf { it.sum() }.coerceAtLeast(1)
    val tekst = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val b = size.width / 24
        val hoog = size.height - 18.dp.toPx()
        for (u in 0 until 24) {
            var y = hoog
            for (s in 0 until 5) {
                val h = hoog * per[u][s] / max
                if (h > 0) drawRect(Color(SOUNDS[s].kleur), Offset(u * b + 1, y - h), Size(b - 2, h))
                y -= h
            }
        }
        drawIntoCanvas { c ->
            val p = Paint().apply { color = tekst; textSize = 10.dp.toPx(); textAlign = Paint.Align.CENTER }
            for (u in listOf(0, 6, 12, 18)) c.nativeCanvas.drawText("$u u", u * b + b / 2, size.height - 2, p)
        }
    }
}

/** Metingen tegen leeftijd in weken, met WHO-percentiellijnen (z = ±1,88 / ±1,04 / 0). */
@Composable
fun GroeiCurve(metingen: List<Ev>, baby: Ev, gewicht: Boolean) {
    val tabel = when {
        baby.kant == "jongen" && gewicht -> WFA_JONGEN
        baby.kant == "meisje" && gewicht -> WFA_MEISJE
        baby.kant == "jongen" -> LFA_JONGEN
        baby.kant == "meisje" -> LFA_MEISJE
        else -> null
    }
    val punten = metingen.map { (it.start - baby.start) / (7f * DAG) to (if (gewicht) it.gram!! / 1000f else it.cm!!) }
    val weken = maxOf(12f, (punten.maxOfOrNull { it.first } ?: 0f) + 4f).coerceAtMost(52f)
    val lijnen = tabel?.let { t -> listOf(-1.881f, -1.036f, 0f, 1.036f, 1.881f).map { z -> lmsLijn(t, z) } }.orEmpty()
    val alleY = lijnen.flatMap { it.take(weken.toInt() + 1) } + punten.map { it.second }
    if (alleY.isEmpty()) {
        Klein("Nog geen metingen.")
        return
    }
    val yMin = alleY.min() * 0.95f
    val yMax = alleY.max() * 1.03f
    val lijnKleur = MaterialTheme.colorScheme.outline
    val midden = MaterialTheme.colorScheme.tertiary
    val punt = MaterialTheme.colorScheme.primary
    val tekst = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    Canvas(Modifier.fillMaxWidth().height(220.dp).padding(vertical = 8.dp)) {
        val links = 34.dp.toPx()
        val onder = 16.dp.toPx()
        fun x(w: Float) = links + (size.width - links) * w / weken
        fun y(v: Float) = (size.height - onder) * (1 - (v - yMin) / (yMax - yMin))
        lijnen.forEachIndexed { i, l ->
            for (w in 0 until weken.toInt()) drawLine(if (i == 2) midden else lijnKleur, Offset(x(w.toFloat()), y(l[w])), Offset(x(w + 1f), y(l[w + 1])), strokeWidth = if (i == 2) 3f else 2f)
        }
        punten.zipWithNext { a, b -> drawLine(punt, Offset(x(a.first), y(a.second)), Offset(x(b.first), y(b.second)), strokeWidth = 4f) }
        punten.forEach { drawCircle(punt, 6.dp.toPx() / 2 + 2, Offset(x(it.first), y(it.second))) }
        drawIntoCanvas { c ->
            val p = Paint().apply { color = tekst; textSize = 10.dp.toPx() }
            c.nativeCanvas.drawText(if (gewicht) "%.1f kg".format(yMax) else "%.0f cm".format(yMax), 0f, 10.dp.toPx(), p)
            c.nativeCanvas.drawText(if (gewicht) "%.1f kg".format(yMin) else "%.0f cm".format(yMin), 0f, size.height - onder, p)
            for (w in 0..weken.toInt() step 4) c.nativeCanvas.drawText("${w}w", x(w.toFloat()) - 6.dp.toPx(), size.height, p)
        }
    }
}
