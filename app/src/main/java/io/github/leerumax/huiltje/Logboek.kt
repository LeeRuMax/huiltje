@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.leerumax.huiltje

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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

@Composable
fun Logboek(db: Db, evs: List<Ev>) {
    val nu = System.currentTimeMillis()
    LogLijst(db, evs, { it.type != T.AFSPRAAK || it.start <= nu }) {
        Spacer(Modifier.height(16.dp))
        Text("📖 Logboek", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Klein("Tik op een regel om te wijzigen of te verwijderen. Huiltjes: tik om te zeggen wat het was.")
    }
}

/** Regels per dag (nieuwste eerst) die aan [filter] voldoen; tikken = wijzigen. [kop] staat erboven. */
@Composable
fun LogLijst(db: Db, evs: List<Ev>, filter: (Ev) -> Boolean, eersteDagen: Int = 14, kop: @Composable () -> Unit) {
    var dagen by remember { mutableIntStateOf(eersteDagen) }
    var kies by remember { mutableStateOf<Ev?>(null) }
    val nu = System.currentTimeMillis()
    val mij = remember(evs) { db.mijnNaam }
    val alle = remember(evs) { regels(evs, mij, nu).filter { filter(it.ev) } }
    val grens = nu.dag().minusDays(dagen.toLong() - 1).ms()
    val perDag = alle.filter { it.ev.start >= grens }.groupBy { it.ev.start.dag() }

    LazyColumn(Modifier.fillMaxSize()) {
        item { kop() }
        if (alle.isEmpty()) item { Klein("Nog niets gelogd.") }
        dagLijst(perDag, nu) { kies = it }
        item {
            if (alle.any { it.ev.start < grens }) TextButton(onClick = { dagen += 14 }) { Text("Oudere dagen laden") }
            Spacer(Modifier.height(24.dp))
        }
    }
    kies?.let { WijzigDialoog(db, evs, it) { kies = null } }
}

fun LazyListScope.dagLijst(perDag: Map<java.time.LocalDate, List<Regel>>, nu: Long, kies: (Ev) -> Unit) {
    perDag.forEach { (dag, regels) ->
        item {
            val wanneer = when (dag) {
                nu.dag() -> "Vandaag"
                nu.dag().minusDays(1) -> "Gisteren"
                else -> datum(dag.ms())
            }
            Text(wanneer, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            HorizontalDivider()
        }
        items(regels, key = { it.ev.id }) { r -> RegelRij(r, Modifier.clickable { kies(r.ev) }) }
    }
}

/** Het juiste wijzigscherm per soort: huiltje bevestigen, borst/kolf per kant, de rest tijd/hoeveelheid. */
@Composable
fun WijzigDialoog(db: Db, evs: List<Ev>, e: Ev, sluit: () -> Unit) {
    when (e.type) {
        T.HUIL -> AlertDialog(
            onDismissRequest = sluit,
            title = { Text("Wat was het?") },
            text = {
                LabelKnoppen { label ->
                    db.een(e.id)?.let { db.put(it.copy(label = label)) }
                    sluit()
                }
            },
            confirmButton = { TextButton(onClick = { db.put(e.copy(weg = true)); sluit() }) { Text("Verwijderen") } },
            dismissButton = { TextButton(onClick = sluit) { Text("Annuleer") } },
        )
        T.BORST, T.KOLF -> {
            val s = sessies(evs, e.type).find { it.ev.id == e.id }
            if (s != null && s.lopend.isNotEmpty()) AlertDialog(
                onDismissRequest = sluit,
                title = { Text("Loopt nog") },
                text = { Text("Deze timer loopt nog. Gebruik Klaar of Annuleren op Vandaag.") },
                confirmButton = { TextButton(onClick = sluit) { Text("OK") } },
            ) else SessieDialoog(db, evs, e.type, s, sluit)
        }
        else -> BewerkDialoog(db, e, nieuw = false, sluit = sluit)
    }
}

/**
 * Tijd, hoeveelheid of soort aanpassen, of weghalen. Met [nieuw] is het een lege regel die hier wordt ingevuld
 * (dan heet de tweede knop Annuleer in plaats van Verwijderen).
 */
@Composable
fun BewerkDialoog(db: Db, e: Ev, nieuw: Boolean, sluit: () -> Unit) {
    val ctx = LocalContext.current
    var start by remember { mutableLongStateOf(e.start) }
    var eind by remember { mutableStateOf(e.eind) }
    var ml by remember { mutableIntStateOf(e.ml ?: 0) }
    var kant by remember { mutableStateOf(e.kant) }
    var gram by remember { mutableStateOf(e.gram?.toString().orEmpty()) }
    var cm by remember { mutableStateOf(e.cm?.let(::cmTekst).orEmpty()) }
    val titel = when (e.type) {
        T.FLES -> "🍼 Fles"
        T.LUIER -> "Luier"
        T.SLAAP -> "😴 Slaap"
        T.VITAMINE -> "💊 Vitamine"
        T.GROEI -> "📏 Gewicht en lengte"
        T.VOORRAAD -> "🧊 Voorraad"
        T.AFSPRAAK -> "📅 ${e.tekst ?: "Afspraak"}"
        else -> e.type
    }
    AlertDialog(
        onDismissRequest = sluit,
        title = { Text(titel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (e.type == T.SLAAP) "Ging slapen " else "") + "${datum(start)} ${klok(start)}", Modifier.weight(1f))
                    TextButton(onClick = { kiesTijd(ctx, start) { start = it } }) { Text("Tijd") }
                    TextButton(onClick = { kiesDatum(ctx, start) { start = it } }) { Text("Dag") }
                }
                if (e.type == T.SLAAP && eind != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Wakker ${klok(eind!!)}", Modifier.weight(1f))
                        TextButton(onClick = { kiesTijd(ctx, eind!!) { t -> eind = if (t < start) t + DAG else t } }) { Text("Tijd") }
                    }
                    Klein("Samen ${duur(eind!! - start)}")
                }
                when (e.type) {
                    T.FLES -> {
                        Stepper("Gedronken", ml, 10) { ml = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("kunstvoeding", "moedermelk").forEach { k -> FilterChip(kant == k, { kant = k }, { Text(k) }) }
                        }
                    }
                    T.VOORRAAD -> Stepper("Hoeveel", ml, 10) { ml = it }
                    T.LUIER -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("nat" to "💧 Nat", "poep" to "💩 Poep").forEach { (k, t) -> FilterChip(kant == k, { kant = k }, { Text(t) }) }
                    }
                    T.VITAMINE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("D", "K").forEach { k -> FilterChip(kant == k, { kant = k }, { Text("Vitamine $k") }) }
                    }
                    T.GROEI -> {
                        OutlinedTextField(gram, { gram = it.filter(Char::isDigit) }, label = { Text("Gewicht in gram") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        OutlinedTextField(cm, { cm = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, label = { Text("Lengte in cm (mag leeg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    }
                }
            }
        },
        confirmButton = {
            val g = gram.toIntOrNull()
            val c = cm.replace(',', '.').toFloatOrNull()
            Button(enabled = e.type != T.GROEI || g != null || c != null, onClick = {
                db.put(
                    e.copy(
                        start = start, eind = eind, kant = kant,
                        ml = if (e.type == T.FLES || e.type == T.VOORRAAD) ml else e.ml,
                        gram = if (e.type == T.GROEI) g else e.gram, cm = if (e.type == T.GROEI) c else e.cm,
                    ),
                )
                sluit()
            }) { Text("Opslaan") }
        },
        dismissButton = {
            if (nieuw) TextButton(onClick = sluit) { Text("Annuleer") }
            else TextButton(onClick = { db.put(e.copy(weg = true)); sluit() }) { Text("Verwijderen") }
        },
    )
}
