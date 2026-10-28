@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.leerumax.huiltje

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** De knoppen van Vandaag als eigen pagina: alles van dat onderdeel bekijken, toevoegen en aanpassen. */
class Onderdeel(val titel: String, val past: (Ev) -> Boolean, val nieuw: ((Long) -> Ev)?)

val ONDERDELEN = listOf(
    Onderdeel("🤱 Borstvoeding", { it.type == T.BORST }, null),
    Onderdeel("🍼 Fles", { it.type == T.FLES }) { Ev(nieuwId(), T.FLES, it, ml = 90, kant = "kunstvoeding") },
    Onderdeel("🥛 Kolven", { it.type == T.KOLF }, null),
    Onderdeel("😴 Slaap", { it.type == T.SLAAP }) { Ev(nieuwId(), T.SLAAP, it - UUR, eind = it) },
    Onderdeel("💧 Natte luiers", { it.type == T.LUIER && it.kant == "nat" }) { Ev(nieuwId(), T.LUIER, it, kant = "nat") },
    Onderdeel("💩 Poepluiers", { it.type == T.LUIER && it.kant == "poep" }) { Ev(nieuwId(), T.LUIER, it, kant = "poep") },
    Onderdeel("💊 Vitamine", { it.type == T.VITAMINE }) { Ev(nieuwId(), T.VITAMINE, it, kant = "D") },
    Onderdeel("👂 Huiltjes", { it.type == T.HUIL }, null),
    Onderdeel("📏 Gewicht en lengte", { it.type == T.GROEI }) { Ev(nieuwId(), T.GROEI, it) },
)

@Composable
fun OnderdeelPagina(db: Db, evs: List<Ev>, o: Onderdeel, terug: () -> Unit) {
    BackHandler(onBack = terug)
    var toevoegen by remember { mutableStateOf(false) }
    val nu = System.currentTimeMillis()
    val baby = evs.find { it.type == T.BABY }
    // Metingen zijn er weinig en de geboortemeting hoort erbij: alles tonen.
    LogLijst(db, evs, o.past, eersteDagen = if (o.titel.contains("Gewicht")) 3650 else 14) {
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = terug) { Text("← Terug") }
        Text(o.titel, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        val vandaag = evs.filter { o.past(it) && it.start.dag() == nu.dag() }
        Klein(
            when {
                o.titel.contains("Gewicht") -> evs.filter { it.type == T.GROEI && it.gram != null }.maxByOrNull { it.start }
                    ?.let { "Laatst: ${it.gram} g op ${datum(it.start)}" } ?: "Nog geen gewicht ingevuld."
                o.titel.contains("Fles") -> "Vandaag ${vandaag.size}× · ${vandaag.sumOf { it.ml ?: 0 }} ml"
                o.titel.contains("Slaap") -> "Vandaag ${duur(vandaag.sumOf { (it.eind ?: nu) - it.start })} geslapen"
                else -> "Vandaag ${vandaag.size}×"
            },
        )
        if (o.titel.contains("Gewicht")) {
            var gewicht by remember { mutableStateOf(true) }
            Row(Modifier.padding(top = 8.dp)) {
                FilterChip(gewicht, { gewicht = true }, { Text("Gewicht") })
                Spacer(Modifier.width(6.dp))
                FilterChip(!gewicht, { gewicht = false }, { Text("Lengte") })
            }
            if (baby != null && baby.start > 0) {
                GroeiCurve(evs.filter { it.type == T.GROEI && (if (gewicht) it.gram != null else it.cm != null) }.sortedBy { it.start }, baby, gewicht)
            } else Klein("Vul bij Meer de geboortedatum in, dan zie je ook de curve.")
        }
        Spacer(Modifier.height(8.dp))
        when {
            o.titel.contains("Huiltjes") -> Klein("Een huiltje voeg je toe met 👂 Luisteren.")
            else -> Button(onClick = { toevoegen = true }, colors = zacht()) { Text("＋ Toevoegen") }
        }
    }
    if (toevoegen) {
        val maak = o.nieuw
        when {
            maak != null -> BewerkDialoog(db, maak(System.currentTimeMillis()), nieuw = true) { toevoegen = false }
            o.titel.contains("Borst") -> SessieDialoog(db, evs, T.BORST, null) { toevoegen = false }
            else -> SessieDialoog(db, evs, T.KOLF, null) { toevoegen = false }
        }
    }
}

/**
 * Borstvoeding of kolfbeurt achteraf invullen of aanpassen: begintijd, minuten per kant, waarmee begonnen.
 * De kanten worden na elkaar gezet (eerst de begonnen kant).
 * ponytail: kolven dat tegelijk liep wordt bij aanpassen na elkaar gezet; per kant tijden kiezen als dat nodig blijkt.
 */
@Composable
fun SessieDialoog(db: Db, evs: List<Ev>, soort: String, s: Sessie?, sluit: () -> Unit) {
    val ctx = LocalContext.current
    val kolf = soort == T.KOLF
    var start by remember { mutableLongStateOf(s?.ev?.start ?: (System.currentTimeMillis() - 30 * MIN)) }
    var minL by remember { mutableStateOf(s?.let { (it.ms("L", it.einde) / MIN).toString() } ?: "10") }
    var minR by remember { mutableStateOf(s?.let { (it.ms("R", it.einde) / MIN).toString() } ?: "10") }
    var begon by remember { mutableStateOf(s?.begon ?: (if (kolf) null else volgendeKant(evs)) ?: "L") }
    var mlL by remember { mutableIntStateOf(s?.ev?.ml ?: 0) }
    var mlR by remember { mutableIntStateOf(s?.ev?.ml2 ?: 0) }
    var weg by remember { mutableStateOf(false) }
    val l = minL.toIntOrNull() ?: 0
    val r = minR.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = sluit,
        title = { Text(if (kolf) "🥛 Kolven" else "🤱 Borstvoeding") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Begon ${datum(start)} ${klok(start)}", Modifier.weight(1f))
                    TextButton(onClick = { kiesTijd(ctx, start) { start = it } }) { Text("Tijd") }
                    TextButton(onClick = { kiesDatum(ctx, start) { start = it } }) { Text("Dag") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(minL, { minL = it.filter(Char::isDigit).take(3) }, label = { Text("Min links") }, singleLine = true,
                        modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(minR, { minR = it.filter(Char::isDigit).take(3) }, label = { Text("Min rechts") }, singleLine = true,
                        modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                if (l > 0 && r > 0) {
                    Text("Begonnen met", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("L", "R").forEach { k -> FilterChip(begon == k, { begon = k }, { Text(kantNaam(k)) }) }
                    }
                    Klein("Dus geëindigd met ${kantNaam(if (begon == "L") "R" else "L")}.")
                }
                if (kolf) {
                    Stepper("Links", mlL, 5) { mlL = it }
                    Stepper("Rechts", mlR, 5) { mlR = it }
                }
                if (s != null) TextButton(onClick = { weg = true }) { Text("Verwijderen") }
            }
        },
        confirmButton = {
            Button(enabled = l + r > 0, onClick = {
                db.put(*sessieUitInvoer(soort, s, start, l, r, begon, mlL, mlR).toTypedArray())
                sluit()
            }) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = sluit) { Text("Annuleer") } },
    )
    if (weg && s != null) WeggooiDialoog(kolf, { weg = false }) {
        db.put(s.ev.copy(weg = true), *s.delen.map { it.copy(weg = true) }.toTypedArray())
        sluit()
    }
}

@Composable
fun WeggooiDialoog(kolf: Boolean, terug: () -> Unit, ja: () -> Unit) {
    AlertDialog(
        onDismissRequest = terug,
        title = { Text(if (kolf) "Kolfbeurt weggooien?" else "Voeding weggooien?") },
        text = { Text("De tijden van beide kanten worden niet bewaard.") },
        confirmButton = {
            Button(onClick = ja, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Weggooien") }
        },
        dismissButton = { TextButton(onClick = terug) { Text("Terug") } },
    )
}
