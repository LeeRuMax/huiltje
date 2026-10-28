package io.github.leerumax.huiltje

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * Logboek + instellingen in SQLite. Elke wijziging krijgt een lokaal volgnummer (lseq); een partner vraagt
 * "alles na nummer N" en de ontvanger houdt per regel de nieuwste versie (rev, dan dev).
 */
class Db(ctx: Context, naam: String) : SQLiteOpenHelper(ctx, naam, null, 1) {
    companion object {
        @Volatile private var inst: Db? = null

        fun get(ctx: Context): Db = inst ?: synchronized(this) {
            inst ?: Db(ctx.applicationContext, "huiltje.db").also {
                // Eerst registreren: put() in het overzetten roept via de haken zelf Db.get() aan.
                // Andersom liep dat eindeloos rond (0.2.0 bleef hangen op een wit scherm).
                inst = it
                runCatching { it.migreerCsv(ctx.applicationContext) }
            }
        }

        /** Alleen voor tests: de volgende get() begint opnieuw. */
        fun vergeet() { inst = null }

        /** Telt op bij elke wijziging; schermen lezen opnieuw als dit verandert. */
        val versie = MutableStateFlow(0L)

        /** Na elke wijziging (lokaal = door deze telefoon, anders = van de partner). Gezet in App. */
        @Volatile var naWijziging: ((lokaal: Boolean) -> Unit)? = null
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE ev(id TEXT PRIMARY KEY, type TEXT NOT NULL, start INTEGER NOT NULL, eind INTEGER, kant TEXT,
               ml INTEGER, ml2 INTEGER, sessie TEXT, gram INTEGER, cm REAL, label INTEGER, probs TEXT, emb TEXT, tekst TEXT,
               sinds INTEGER, wie TEXT, weg INTEGER NOT NULL DEFAULT 0, rev INTEGER NOT NULL, dev TEXT NOT NULL,
               lseq INTEGER NOT NULL)""",
        )
        db.execSQL("CREATE INDEX ev_type ON ev(type, start)")
        db.execSQL("CREATE INDEX ev_lseq ON ev(lseq)")
        db.execSQL("CREATE TABLE inst(k TEXT PRIMARY KEY, v TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oud: Int, nieuw: Int) {}

    // ---- instellingen (alleen op dit toestel) ----

    fun get(k: String): String? = readableDatabase.rawQuery("SELECT v FROM inst WHERE k = ?", arrayOf(k)).use {
        if (it.moveToFirst()) it.getString(0) else null
    }

    fun set(k: String, v: String?) {
        if (v == null) writableDatabase.delete("inst", "k = ?", arrayOf(k))
        else writableDatabase.insertWithOnConflict("inst", null, ContentValues().apply { put("k", k); put("v", v) }, SQLiteDatabase.CONFLICT_REPLACE)
        versie.value++
    }

    fun keys(prefix: String): List<String> =
        readableDatabase.rawQuery("SELECT k FROM inst WHERE k LIKE ?", arrayOf("$prefix%")).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    val dev: String by lazy { get("dev") ?: nieuwId().also { set("dev", it) } }
    val mijnNaam get() = get("naam")?.takeIf { it.isNotBlank() }

    // ---- logboek ----

    private val kolommen = "id,type,start,eind,kant,ml,ml2,sessie,gram,cm,label,probs,tekst,sinds,wie,weg,rev,dev"

    /** Alles behalve de embeddings (die zijn groot en alleen nodig voor het bijleren en delen). */
    fun alle(): List<Ev> = readableDatabase.rawQuery("SELECT $kolommen, NULL FROM ev WHERE weg = 0", null).use { lees(it) }

    fun een(id: String): Ev? = readableDatabase.rawQuery("SELECT $kolommen, emb FROM ev WHERE id = ?", arrayOf(id)).use { lees(it).firstOrNull() }

    /** Bevestigde huiltjes met embedding: de eigen voorbeelden voor het bijleren. */
    fun eigenVoorbeelden(): Personal = Personal(
        readableDatabase.rawQuery("SELECT $kolommen, emb FROM ev WHERE type = ? AND weg = 0 AND label IS NOT NULL AND emb IS NOT NULL", arrayOf(T.HUIL))
            .use { lees(it) }.map { it.label!! to it.emb!! }.toMutableList(),
    )

    /** Rijen met lseq > [na], inclusief verwijderde, en het hoogste meegestuurde nummer. */
    fun sinds(na: Long): Pair<List<Ev>, Long> {
        var max = na
        val rows = readableDatabase.rawQuery("SELECT $kolommen, emb, lseq FROM ev WHERE lseq > ? ORDER BY lseq", arrayOf(na.toString())).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(rij(c))
                    max = maxOf(max, c.getLong(19))
                }
            }
        }
        return rows to max
    }

    /** Lokale wijziging: nieuwe rev (nooit lager dan de vorige) en eigen dev. */
    fun put(vararg evs: Ev) {
        if (evs.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (e in evs) {
                val oud = revVan(db, e.id)?.first ?: 0
                schrijf(db, e.copy(rev = maxOf(System.currentTimeMillis(), oud + 1), dev = dev, wie = e.wie ?: mijnNaam))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        gewijzigd(true)
    }

    /** Rijen van de partner of uit een backup: alleen overnemen wat nieuwer is. Geeft het aantal gewijzigde. */
    fun merge(rows: List<Ev>): Int {
        var n = 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (r in rows) {
                val oud = revVan(db, r.id)
                if (oud == null || r.rev > oud.first || (r.rev == oud.first && r.dev > oud.second)) {
                    schrijf(db, r)
                    n++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (n > 0) gewijzigd(false)
        return n
    }

    private fun gewijzigd(lokaal: Boolean) {
        versie.value++
        naWijziging?.invoke(lokaal)
    }

    private fun revVan(db: SQLiteDatabase, id: String): Pair<Long, String>? =
        db.rawQuery("SELECT rev, dev FROM ev WHERE id = ?", arrayOf(id)).use { if (it.moveToFirst()) it.getLong(0) to it.getString(1) else null }

    private fun schrijf(db: SQLiteDatabase, e: Ev) {
        val lseq = db.rawQuery("SELECT IFNULL(MAX(lseq), 0) + 1 FROM ev", null).use { it.moveToFirst(); it.getLong(0) }
        val v = ContentValues().apply {
            put("id", e.id); put("type", e.type); put("start", e.start); put("eind", e.eind); put("kant", e.kant)
            put("ml", e.ml); put("ml2", e.ml2); put("sessie", e.sessie); put("gram", e.gram); put("cm", e.cm)
            put("label", e.label); put("probs", e.probs?.let(::f2s)); put("emb", e.emb?.let(::f2s)); put("tekst", e.tekst)
            put("sinds", e.sinds); put("wie", e.wie); put("weg", if (e.weg) 1 else 0); put("rev", e.rev); put("dev", e.dev)
            put("lseq", lseq)
        }
        db.insertWithOnConflict("ev", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun lees(c: Cursor): List<Ev> = buildList { while (c.moveToNext()) add(rij(c)) }

    private fun rij(c: Cursor): Ev {
        fun l(i: Int) = if (c.isNull(i)) null else c.getLong(i)
        fun n(i: Int) = if (c.isNull(i)) null else c.getInt(i)
        fun s(i: Int) = if (c.isNull(i)) null else c.getString(i)
        return Ev(
            id = c.getString(0), type = c.getString(1), start = c.getLong(2), eind = l(3), kant = s(4), ml = n(5), ml2 = n(6),
            sessie = s(7), gram = n(8), cm = if (c.isNull(9)) null else c.getFloat(9), label = n(10), probs = s(11)?.let(::s2f),
            tekst = s(12), sinds = l(13), wie = s(14), weg = c.getInt(15) == 1, rev = c.getLong(16), dev = c.getString(17),
            emb = s(18)?.let(::s2f),
        )
    }

    /** Versie 0.1 bewaarde huiltjes in csv-bestanden; eenmalig overzetten zodat bevestigingen blijven. */
    fun migreerCsv(ctx: Context) {
        val hist = File(ctx.filesDir, "geschiedenis.csv")
        if (!hist.exists()) return
        val opnames = File(ctx.getExternalFilesDir(null), "opnames")
        val evs = hist.readLines().filter { it.isNotBlank() }.map { line ->
            val (id, p, l) = line.split(';')
            Ev(
                "huil-$id", T.HUIL, id.toLong(), label = l.toIntOrNull(), probs = p.split(',').map { it.toFloat() }.toFloatArray(),
                emb = File(opnames, "$id.emb").takeIf { it.exists() }?.readText()?.split(',')?.map { it.toFloat() }?.toFloatArray(),
            )
        }
        put(*evs.toTypedArray())
        hist.renameTo(File(ctx.filesDir, "geschiedenis.csv.oud"))
        File(ctx.filesDir, "eigen.csv").let { if (it.exists()) it.renameTo(File(ctx.filesDir, "eigen.csv.oud")) }
    }
}

fun opnamesMap(ctx: Context) = File(ctx.getExternalFilesDir(null), "opnames").apply { mkdirs() }

fun f2s(a: FloatArray): String {
    val b = ByteBuffer.allocate(a.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    a.forEach { b.putFloat(it) }
    return Base64.getEncoder().encodeToString(b.array())
}

fun s2f(s: String): FloatArray {
    val b = ByteBuffer.wrap(Base64.getDecoder().decode(s)).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray(b.remaining() / 4) { b.getFloat() }
}

fun Ev.json(): JSONObject = JSONObject().apply {
    put("id", id); put("type", type); put("start", start); put("rev", rev); put("dev", dev)
    if (weg) put("weg", true)
    eind?.let { put("eind", it) }; kant?.let { put("kant", it) }; ml?.let { put("ml", it) }; ml2?.let { put("ml2", it) }
    sessie?.let { put("sessie", it) }; gram?.let { put("gram", it) }; cm?.let { put("cm", it.toDouble()) }
    label?.let { put("label", it) }; probs?.let { put("probs", f2s(it)) }; emb?.let { put("emb", f2s(it)) }
    tekst?.let { put("tekst", it) }; sinds?.let { put("sinds", it) }; wie?.let { put("wie", it) }
}

fun evVan(o: JSONObject): Ev {
    fun l(k: String) = if (o.has(k)) o.getLong(k) else null
    fun n(k: String) = if (o.has(k)) o.getInt(k) else null
    fun s(k: String) = if (o.has(k)) o.getString(k) else null
    return Ev(
        id = o.getString("id"), type = o.getString("type"), start = o.getLong("start"), eind = l("eind"), kant = s("kant"),
        ml = n("ml"), ml2 = n("ml2"), sessie = s("sessie"), gram = n("gram"), cm = if (o.has("cm")) o.getDouble("cm").toFloat() else null,
        label = n("label"), probs = s("probs")?.let(::s2f), emb = s("emb")?.let(::s2f), tekst = s("tekst"), sinds = l("sinds"),
        wie = s("wie"), weg = o.optBoolean("weg"), rev = o.getLong("rev"), dev = o.getString("dev"),
    )
}

fun rijenJson(rows: List<Ev>) = JSONArray().apply { rows.forEach { put(it.json()) } }
fun rijenVan(a: JSONArray) = List(a.length()) { evVan(a.getJSONObject(it)) }
