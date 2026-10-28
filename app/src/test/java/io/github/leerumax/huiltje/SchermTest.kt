package io.github.leerumax.huiltje

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Geen echte test: rendert de schermen met voorbeeldgegevens naar build/schermen/ om ze te kunnen bekijken. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1500dp-mdpi")
class SchermTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun voorbeeld(nacht: String): Db {
        Db.naWijziging = null
        val db = Db(rule.activity, "s-${System.nanoTime()}.db")
        val nu = System.currentTimeMillis()
        db.set("naam", "Ouder A"); db.set("meldingen.gevraagd", "1"); db.set("nacht", nacht)
        db.put(Ev(T.BABY, T.BABY, nu.dag().minusDays(17).ms(), tekst = "Baby", kant = "meisje"))
        val evs = mutableListOf<Ev>()
        fun op(rijen: List<Ev>) { rijen.forEach { n -> evs.removeAll { it.id == n.id }; evs += n } }
        op(tik(evs, T.BORST, "L", nu - 150 * MIN, "Ouder B")); op(tik(evs, T.BORST, "R", nu - 138 * MIN, "Ouder B"))
        op(klaar(sessies(evs, T.BORST).last(), nu - 129 * MIN))
        op(tik(evs, T.KOLF, "L", nu - 50 * MIN, null)); op(tik(evs, T.KOLF, "R", nu - 49 * MIN, null))
        op(tik(evs, T.KOLF, "L", nu - 35 * MIN, null)); op(tik(evs, T.KOLF, "R", nu - 33 * MIN, null))
        op(klaar(sessies(evs, T.KOLF).last(), nu - 33 * MIN).map { if (it.type == T.KOLF) it.copy(ml = 60, ml2 = 55) else it })
        evs += Ev(nieuwId(), T.VOORRAAD, nu - 33 * MIN, ml = 115, kant = "koelkast", sinds = nu - 33 * MIN)
        evs += Ev(nieuwId(), T.SLAAP, nu - 120 * MIN, eind = nu - 55 * MIN)
        evs += Ev(nieuwId(), T.LUIER, nu - 100 * MIN, kant = "nat", wie = "Ouder B")
        evs += Ev(nieuwId(), T.LUIER, nu - 90 * MIN, kant = "poep")
        evs += Ev(nieuwId(), T.VITAMINE, nu - 300 * MIN, kant = "D")
        evs += Ev(nieuwId(), T.FLES, nu - 400 * MIN, ml = 90, kant = "moedermelk")
        evs += Ev(nieuwId(), T.HUIL, nu - 20 * MIN, probs = floatArrayOf(.55f, .2f, .1f, .1f, .05f), tekst = "1 u 50 na de voeding")
        evs += Ev(nieuwId(), T.GROEI, nu.dag().minusDays(17).ms(), gram = 3400, cm = 50f)
        evs += Ev(nieuwId(), T.GROEI, nu.dag().minusDays(3).ms(), gram = 3950, cm = 53f)
        db.put(*evs.toTypedArray())
        return db
    }

    private fun foto(naam: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        File("build/schermen").apply { mkdirs() }.resolve("$naam.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun dag() {
        val db = voorbeeld("uit")
        rule.setContent { App(db, mutableStateOf(null)) }
        foto("1-vandaag")
        rule.onNodeWithText("🤱 Borstvoeding").performClick()
        foto("2-borst-kiezen")
        rule.onNodeWithText("Links").performClick()
        foto("2b-borst-loopt")
        rule.onNodeWithText("Logboek").performClick(); foto("3-logboek")
        rule.onNodeWithText("Inzicht").performClick(); foto("4-inzicht")
        // Meer schuift een menu omhoog, vanaf elk scherm; een knop opent de pagina.
        rule.onNodeWithText("Meer").performClick()
        rule.onNodeWithText("🤱 Borstvoeding").performClick(); foto("8-onderdeel-borst")
        rule.onNodeWithText("Meer").performClick() // ook vanuit een pagina weer het menu
        rule.onNodeWithText("📏 Gewicht en lengte").performClick(); foto("9-onderdeel-groei")
        rule.onNodeWithText("Meer").performClick()
        rule.onNodeWithText("👶 Baby en jij").performClick(); foto("5-baby-en-jij")
        rule.onNodeWithText("← Terug").performClick()
        rule.onNodeWithText("📈 Inzicht").assertExists() // terug waar je was
    }

    @Test
    fun borstEnKolvenTegelijk() {
        val db = voorbeeld("uit")
        val nu = System.currentTimeMillis()
        db.put(*tik(db.alle(), T.BORST, "L", nu - 6 * MIN, null).toTypedArray())
        db.put(*tik(db.alle(), T.KOLF, "R", nu - 4 * MIN, null).toTypedArray())
        rule.setContent { App(db, mutableStateOf(null)) }
        foto("7-samen")
    }

    /** Het menu zelf (in de app schuift het als los venster omhoog; hier plat getekend om te bekijken). */
    @Test
    fun menu() {
        rule.setContent { androidx.compose.material3.MaterialTheme(colorScheme = LICHT) { androidx.compose.material3.Surface { MeerMenu {} } } }
        foto("5-menu")
    }

    @Test
    fun nacht() {
        val db = voorbeeld("aan")
        rule.setContent { App(db, mutableStateOf(null)) }
        foto("6-nacht")
    }
}
