package io.github.leerumax.huiltje

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

/** Widget op het beginscherm: laatste voeding als lopende klok, en met één tik borst L/R, luier en slaap. */
class Widget : AppWidgetProvider() {
    companion object {
        fun bijwerken(ctx: Context) {
            val am = AppWidgetManager.getInstance(ctx) ?: return
            val ids = am.getAppWidgetIds(ComponentName(ctx, Widget::class.java))
            if (ids.isNotEmpty()) am.updateAppWidget(ids, views(ctx))
        }

        private fun views(ctx: Context): RemoteViews {
            val evs = Db.get(ctx).alle()
            val nu = System.currentTimeMillis()
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            val loopt = openSessie(evs, T.BORST, nu)?.loopt
            val laatste = laatsteVoeding(evs)
            fun klok(vanaf: Long) = v.setChronometer(R.id.klok, SystemClock.elapsedRealtime() - (nu - vanaf), null, true)
            when {
                loopt != null -> {
                    v.setTextViewText(R.id.titel, "🤱 ${kantNaam(loopt.kant).replaceFirstChar { it.uppercase() }} loopt")
                    klok(loopt.start)
                }
                laatste != null -> {
                    v.setTextViewText(R.id.titel, "🍼 Laatste voeding")
                    klok(laatste.start)
                }
                else -> v.setTextViewText(R.id.titel, "🍼 Nog geen voeding")
            }
            v.setViewVisibility(R.id.klok, if (loopt != null || laatste != null) View.VISIBLE else View.GONE)
            v.setTextViewText(R.id.sub, volgendeKant(evs)?.let { "Volgende keer beginnen: ${kantNaam(it)}" } ?: "")
            for ((id, k) in listOf(R.id.L to "L", R.id.R to "R")) {
                v.setInt(id, "setBackgroundResource", if (loopt?.kant == k) R.drawable.knop_aan else R.drawable.knop)
                v.setOnClickPendingIntent(id, actie(ctx, k))
            }
            v.setTextViewText(R.id.slaap, if (slaapStatus(evs) is Slaap.Slaapt) "☀️ Wakker" else "😴 Slaapt")
            listOf(R.id.nat to "nat", R.id.poep to "poep", R.id.slaap to "slaap").forEach { (id, a) -> v.setOnClickPendingIntent(id, actie(ctx, a)) }
            v.setOnClickPendingIntent(
                R.id.fles,
                PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java).putExtra("open", "fles"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            return v
        }

        private fun actie(ctx: Context, a: String) = PendingIntent.getBroadcast(
            ctx, a.hashCode(), Intent(ctx, Widget::class.java).setAction("huiltje.$a"), PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onUpdate(ctx: Context, am: AppWidgetManager, ids: IntArray) = bijwerken(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        val a = intent.action?.takeIf { it.startsWith("huiltje.") }?.removePrefix("huiltje.") ?: return
        val db = Db.get(ctx)
        val evs = db.alle()
        val nu = System.currentTimeMillis()
        when (a) {
            "L", "R" -> db.put(*tik(evs, T.BORST, a, nu, null).toTypedArray())
            "nat", "poep" -> db.put(Ev(nieuwId(), T.LUIER, nu, kant = a))
            "slaap" -> db.put(slaapTik(evs, nu))
        }
    }
}
