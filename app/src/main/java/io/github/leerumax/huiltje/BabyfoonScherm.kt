@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.leerumax.huiltje

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun BabyfoonScherm(db: Db, evs: List<Ev>) {
    val ctx = LocalContext.current
    val camAan by Camera.status.collectAsState()
    val kijkAan by Kijker.status.collectAsState()
    var fout by remember { mutableStateOf<String?>(null) }
    val nodig = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    var wil by remember { mutableStateOf("") }
    val vraag = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) fout = if (wil == "camera") Camera.start(ctx) else Kijker.start(ctx)
        else fout = "Zonder toestemming voor camera en microfoon kan de babyfoon niet werken."
    }
    fun begin(rol: String) {
        wil = rol
        val ontbreekt = nodig.filter { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (ontbreekt.isEmpty()) fout = if (rol == "camera") Camera.start(ctx) else Kijker.start(ctx)
        else vraag.launch(ontbreekt.toTypedArray())
    }

    when {
        camAan.isNotEmpty() -> CameraKaart()
        kijkAan.isNotEmpty() -> KijkKaart(db)
        else -> Kaart {
            Kop("📷 Babyfoon")
            Text("Eén telefoon is de camera bij de baby, de andere kijkt en luistert mee, en kan terugpraten. Wie wat is kies je per keer.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = { begin("camera") }, modifier = Modifier.fillMaxWidth().height(64.dp), colors = zacht()) { Text("📷 Deze telefoon is de camera", fontSize = 17.sp) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { begin("kijk") }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("📺 Kijken naar de camera", fontSize = 17.sp) }
            Spacer(Modifier.height(8.dp))
            val s = Server.van(evs)
            Klein(
                "Beeld en geluid zijn versleuteld van telefoon tot telefoon (met jullie partnercode); servers ertussen zien niets. " +
                    if (s.turn.isBlank()) "Zonder TURN-server werkt het meestal alleen op dezelfde wifi; stel hem in bij Meer → 📡 Babyfoon-server."
                    else "Via ${s.ntfy.removePrefix("https://")} en je eigen TURN-server, dus ook buiten de deur.",
            )
            fout?.let { Text(it, color = Color(0xFFB8622A), modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
fun Video(track: VideoTrack?, spiegel: Boolean = false, modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            SurfaceViewRenderer(c).apply {
                init(Rtc.egl.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                setMirror(spiegel)
                view = this
            }
        },
        onRelease = { it.release() },
    )
    DisposableEffect(track, view) {
        val v = view
        if (v != null) track?.addSink(v)
        onDispose { if (v != null) runCatching { track?.removeSink(v) } }
    }
}

@Composable
private fun SchermAan() {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val w = (ctx as? Activity)?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
fun CameraKaart() {
    val status by Camera.status.collectAsState()
    val beeld by Camera.beeld.collectAsState()
    Kaart {
        Kop("📷 Camera")
        Text(status, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
            Video(beeld, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        }
        Spacer(Modifier.height(8.dp))
        Klein("Leg de telefoon aan de lader met zicht op het bedje. Het scherm mag uit; de babyfoon blijft draaien. Wat de kijker zegt hoor je hier uit de luidspreker.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            FilledTonalButton(onClick = { Camera.wissel() }) { Text("🔄 Andere camera") }
            Button(onClick = { Camera.stop() }) { Text("Stoppen") }
        }
    }
}

@Composable
fun KijkKaart(db: Db) {
    SchermAan()
    val status by Kijker.status.collectAsState()
    val beeld by Kijker.beeld.collectAsState()
    val niveau by Kijker.niveau.collectAsState()
    val huil by Kijker.laatsteHuil.collectAsState()
    val geluid by Kijker.geluidAan.collectAsState()
    val alleen by Kijker.alleenGeluid.collectAsState()
    var praat by remember { mutableStateOf(false) }
    var drempel by remember { mutableFloatStateOf(db.get("babyfoon.drempel")?.toFloatOrNull() ?: 0.15f) }
    Kaart {
        Text(status, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
            if (alleen || beeld == null) Text(if (alleen) "🎧 Alleen geluid" else "…", color = Color.White, fontSize = 20.sp)
            else Video(beeld, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔉", Modifier.padding(end = 8.dp))
            LinearProgressIndicator(progress = { niveau }, modifier = Modifier.weight(1f).height(10.dp),
                color = if (niveau >= drempel) Color(0xFFB8622A) else MaterialTheme.colorScheme.primary)
        }
        huil?.let { Text("Laatst herkend: $it", modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { Kijker.geluid(!geluid) }, modifier = Modifier.weight(1f)) { Text(if (geluid) "🔊 Geluid aan" else "🔇 Geluid uit") }
            FilledTonalButton(onClick = { Kijker.alleenGeluid(!alleen) }, modifier = Modifier.weight(1f)) { Text(if (alleen) "🎥 Beeld aan" else "🎧 Alleen geluid") }
        }
        Spacer(Modifier.height(8.dp))
        // Ingedrukt houden = praten, loslaten = weer stil.
        Surface(
            color = if (praat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.fillMaxWidth().height(72.dp).pointerInput(Unit) {
                detectTapGestures(onPress = {
                    praat = true; Kijker.praat(true)
                    tryAwaitRelease()
                    praat = false; Kijker.praat(false)
                })
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (praat) "🎙️ Je praat nu…" else "🎙️ Houd vast om te praten", fontSize = 18.sp, textAlign = TextAlign.Center,
                    color = if (praat) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Melding bij geluid boven: ${(drempel * 100).toInt()} %", fontSize = 14.sp)
        Slider(drempel, { drempel = it }, valueRange = 0.03f..0.6f, onValueChangeFinished = { db.set("babyfoon.drempel", drempel.toString()) })
        Klein("Je krijgt ook een melding als Huiltje een geluidje herkent, ook met het scherm uit.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button(onClick = { Kijker.stop() }) { Text("Stoppen") } }
    }
}

/** Babyfoon-servers instellen; wordt naar de gekoppelde telefoons gedeeld (niet in de backup). */
@Composable
fun ServerKaart(db: Db, evs: List<Ev>) {
    val nu = Server.van(evs)
    var ntfy by remember(nu) { mutableStateOf(nu.ntfy) }
    var token by remember(nu) { mutableStateOf(nu.ntfyToken) }
    var turn by remember(nu) { mutableStateOf(nu.turn) }
    var user by remember(nu) { mutableStateOf(nu.turnUser) }
    var pass by remember(nu) { mutableStateOf(nu.turnPass) }
    Kaart {
        Kop("📡 Babyfoon-server")
        Klein("Signaal via ntfy (berichten versleuteld met de partnercode) en beeld/geluid via een TURN-relay als de telefoons elkaar niet rechtstreeks vinden. Ingevuld op één telefoon gaat het vanzelf naar je partner.")
        OutlinedTextField(ntfy, { ntfy = it.trim() }, label = { Text("ntfy-server") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, { token = it.trim() }, label = { Text("ntfy-token (mag leeg)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(turn, { turn = it.trim() }, label = { Text("TURN, bijv. turn:turn.voorbeeld.nl:3478") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(user, { user = it.trim() }, label = { Text("TURN-gebruiker") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, label = { Text("TURN-wachtwoord") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = { db.put(Ev(T.SERVER, T.SERVER, 0, tekst = Server(ntfy.ifBlank { "https://ntfy.sh" }, token, turn, user, pass).json())) }) { Text("Opslaan") }
            TextButton(onClick = { ntfy = "https://ntfy.sh"; token = ""; turn = ""; user = ""; pass = "" }) { Text("Standaard") }
        }
    }
}
