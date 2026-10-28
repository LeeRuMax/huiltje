package io.github.leerumax.huiltje

import android.app.Application

/** Draait bij elke start van de app (ook voor widget, wekker en delen): wat er na een wijziging moet gebeuren. */
class HuiltjeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Db.naWijziging = { lokaal ->
            Widget.bijwerken(this)
            Herinneringen.controleer(this)
            if (lokaal) Delen.nu(this)
        }
    }
}
