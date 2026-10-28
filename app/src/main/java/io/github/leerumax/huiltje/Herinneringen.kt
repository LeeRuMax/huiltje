package io.github.leerumax.huiltje

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalTime

/**
 * Eén wekker voor alles: bij afgaan kijkt [controleer] wat er nu te melden is en zet de wekker op het volgende moment.
 * Of iets al gemeld is staat als "gemeld.<sleutel>" in de instellingen, dus een herstart meldt niets dubbel.
 */
object Herinneringen {
    private const val KANAAL = "herinneringen"

    class Moment(val sleutel: String, val tijd: Long, val titel: String, val tekst: String, val vitamine: String? = null)

    /** Alles wat ooit gemeld moet worden, met het moment. Wat al niet meer hoeft (gegeven, al op) valt hier af. */
    fun momenten(db: Db, evs: List<Ev>, nu: Long): List<Moment> {
        val out = mutableListOf<Moment>()
        val dagen = dagenOud(evs.find { it.type == T.BABY }, nu)
        val vandaag = nu.dag()
        val tijd = runCatching { LocalTime.parse(db.get("vit.tijd") ?: "09:00") }.getOrDefault(LocalTime.of(9, 0))
        for (soort in listOf("D", "K")) {
            if (db.get("vit.$soort") != "1" || (soort == "K" && !vitamineKNodig(dagen))) continue
            val gegeven = evs.any { it.type == T.VITAMINE && it.kant == soort && it.start.dag() == vandaag }
            if (!gegeven) out += Moment(
                "vit$soort.$vandaag", vandaag.ms() + tijd.toSecondOfDay() * 1000L,
                "💊 Vitamine $soort", "Vandaag nog niet gegeven.", vitamine = soort,
            )
        }
        if (db.get("wakker.uit") != "1") {
            (slaapStatus(evs) as? Slaap.Wakker)?.let { w ->
                val v = wakkerVenster(dagen)
                out += Moment(
                    "wakker.${w.slaapId}", w.sinds + v.first * MIN, "😴 Al ${v.first} min wakker",
                    "Let op slaapsignaaltjes: geeuwen, wegkijken, in de oogjes wrijven.",
                )
            }
        }
        evs.filter { it.type == T.AFSPRAAK && it.start > nu }.forEach { a ->
            val avond = a.start.dag().minusDays(1).ms() + 19 * UUR
            val wat = a.tekst ?: "Afspraak"
            out += Moment("afs.${a.id}.avond", avond, "📅 Morgen: $wat", "Om ${tijdTekst(a.start)}.")
            out += Moment("afs.${a.id}.uur", a.start - UUR, "📅 Over een uur: $wat", "Om ${tijdTekst(a.start)}.")
        }
        evs.filter { it.type == T.VOORRAAD && it.eind == null }.forEach { v ->
            out += Moment("vr.${v.id}.${v.kant}", opTot(v) - 12 * UUR, "🥛 ${v.ml ?: "?"} ml moet op", "Uit de ${v.kant}: uiterlijk ${tijdTekst(opTot(v))}.")
        }
        return out.filter { db.get("gemeld.${it.sleutel}") == null }
    }

    fun controleer(ctx: Context) {
        val db = Db.get(ctx)
        val nu = System.currentTimeMillis()
        val ms = momenten(db, db.alle(), nu)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(KANAAL, "Herinneringen", NotificationManager.IMPORTANCE_DEFAULT))
        ms.filter { it.tijd <= nu }.forEach { m ->
            // Ouder dan een dag (telefoon lang uit): niet meer melden.
            if (nu - m.tijd < DAG) {
                val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                val b = Notification.Builder(ctx, KANAAL).setSmallIcon(R.drawable.ic_voor).setContentTitle(m.titel)
                    .setContentText(m.tekst).setContentIntent(open).setAutoCancel(true)
                m.vitamine?.let { v ->
                    val gegeven = PendingIntent.getBroadcast(
                        ctx, v.hashCode(), Intent(ctx, Wekker::class.java).setAction("gegeven").putExtra("v", v).putExtra("id", m.sleutel.hashCode()),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    b.addAction(Notification.Action.Builder(null, "Gegeven ✓", gegeven).build())
                }
                runCatching { nm.notify(m.sleutel.hashCode(), b.build()) }
            }
            db.set("gemeld.${m.sleutel}", "1")
        }
        plan(ctx, ms.filter { it.tijd > nu }.minOfOrNull { it.tijd })
    }

    private fun plan(ctx: Context, tijd: Long?) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, Wekker::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // Morgen opnieuw kijken, ook als er nu niets gepland staat (vitamine van morgen).
        val volgende = tijd ?: (System.currentTimeMillis().dag().plusDays(1).ms() + 5 * MIN)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, volgende, pi)
    }

    fun tijdTekst(ms: Long): String = java.text.SimpleDateFormat("EEE d MMM HH:mm", java.util.Locale("nl", "NL")).format(java.util.Date(ms))
}

class Wekker : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == "gegeven") {
            val v = intent.getStringExtra("v") ?: return
            Db.get(ctx).put(Ev(nieuwId(), T.VITAMINE, System.currentTimeMillis(), kant = v))
            ctx.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra("id", 0))
        }
        Herinneringen.controleer(ctx)
    }
}

class NaOpstarten : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Herinneringen.controleer(ctx)
        DelenService.start(ctx)
    }
}
