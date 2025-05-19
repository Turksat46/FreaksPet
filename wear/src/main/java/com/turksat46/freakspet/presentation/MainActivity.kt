package com.turksat46.freakspet.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator // Korrekter Import für StatDisplay
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable // Import für rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.wear.compose.material.* // Wear Compose Material Komponenten
import androidx.wear.tooling.preview.devices.WearDevices

import kotlinx.coroutines.*
import kotlin.math.*
import kotlin.random.Random
// --- Konstanten ---
// Vitalwerte & Simulation (Angepasst für langsameres Leeren, realistischer für Tamagotchi-ähnliches Spiel)
private const val STAT_INCREASE_INTERVAL_MS = 30000L // Zeitintervall für Stat-Anstieg (ms, 30 Sekunden)
private const val HUNGER_INCREASE_RATE = 0.01f    // Anstiegsrate Hunger pro Intervall (ca. 1% pro 30s -> 50 Minuten bis voll)
private const val THIRST_INCREASE_RATE = 0.012f   // Anstiegsrate Durst pro Intervall (etwas schneller, ca. 1.2% pro 30s -> 41 Minuten bis voll)
private const val FATIGUE_INCREASE_RATE = 0.006f   // Anstiegsrate Müdigkeit pro Intervall (langsamer, ca. 0.6% pro 30s -> 1 Stunde 23 Minuten bis voll)
private const val FEED_AMOUNT = 0.4f               // Reduktion Hunger durch Füttern
private const val DRINK_AMOUNT = 0.5f              // Reduktion Durst durch Trinken
private const val REST_AMOUNT = 0.6f               // Reduktion Müdigkeit durch Ausruhen
private const val HUNGER_THRESHOLD = 0.7f          // Schwelle für sichtbaren Hunger-Effekt
private const val THIRST_THRESHOLD = 0.7f          // Schwelle für sichtbaren Durst-Effekt
private const val FATIGUE_THRESHOLD = 0.7f         // Schwelle für sichtbaren Müdigkeits-Effekt
// Blinzeln
private const val BLINK_INTERVAL_MIN_MS = 2500L    // Minimale Zeit zwischen Blinzeln (ms)
private const val BLINK_INTERVAL_MAX_MS = 7000L    // Maximale Zeit zwischen Blinzeln (ms)
private const val FATIGUED_BLINK_INTERVAL_MIN_MS = 500L // Min. Zeit bei Müdigkeit
private const val FATIGUED_BLINK_INTERVAL_MAX_MS = 2000L // Max. Zeit bei Müdigkeit
private const val BLINK_CLOSE_DURATION_MS = 100    // Dauer Augenschließen (ms)
private const val BLINK_PAUSE_MS = 60L             // Pause bei geschlossenen Augen (ms)
private const val BLINK_OPEN_DURATION_MS = 150     // Dauer Augenöffnen (ms)
// Augenbewegung
private const val SACCADE_INTERVAL_MIN_MS = 800L   // Minimale Zeit zwischen Sakkaden (ms)
private const val SACCADE_INTERVAL_MAX_MS = 3000L  // Maximale Zeit zwischen Sakkaden (ms)
private const val SACCADE_DURATION_MS = 100        // Dauer einer Sakkade (ms)
private const val PUPIL_MAX_OFFSET_FACTOR = 0.4f   // Maximale Pupillenverschiebung (relativ zu Augenradius)
private const val TOUCH_FOLLOW_DURATION_MS = 90    // Dauer Pupillenbewegung zu Touch (ms)
private const val PUPIL_MAX_OFFSET_PIXELS_TOUCH = 15f // Maximale Pupillenverschiebung bei Touch (px)
// Pupillengröße
private const val NORMAL_PUPIL_SIZE_FACTOR = 0.4f  // Normale Pupillengröße (relativ zu Augenradius)
private const val HUNGRY_PUPIL_SIZE_FACTOR = 0.15f // Pupillengröße bei Hunger
// Mund
private const val MOUTH_OPEN_DURATION_MS = 250     // Dauer Mundöffnen bei Touch (ms)
private const val MOUTH_CLOSED_FACTOR = 0.20f      // Faktor für geschlossenen Mund (0=oben, 1=max offen)
private const val THIRSTY_MOUTH_OPENNESS_INCREASE = 0.1f // Zusätzliche Mundöffnung bei Durst
// Atmung
private const val BREATHING_MIN_OPENNESS = 0.35f   // Minimale Mundöffnung beim Atmen
private const val BREATHING_MAX_OPENNESS = 0.65f   // Maximale Mundöffnung beim Atmen
private const val BREATHING_INHALE_DURATION_MS = 1800 // Dauer Einatmen (ms)
private const val BREATHING_EXHALE_DURATION_MS = 2400 // Dauer Ausatmen (ms)
private const val BREATHING_PAUSE_MS = 1500L       // Pause zwischen Atemzügen (ms)
private const val BREATH_START_TRANSITION_DURATION_MS = 600 // Dauer Übergang zur Atmung (ms)

// --- NEUE KONSTANTEN für zusätzliche Verhaltensweisen ---
// Hunger-Effekt auf Sakkaden (Schneller und weiter blicken bei Hunger)
private const val HUNGER_SACCADE_INTERVAL_MULTIPLIER = 0.6f // Reduziert Interval bei Hunger (z.B. 0.6 macht es 40% schneller bei max Hunger)
private const val HUNGER_SACCADE_OFFSET_MULTIPLIER = 1.5f // Erhöht max Offset bei Hunger (z.B. 1.5 macht es 50% weiter bei max Hunger)
// Müdigkeit-Effekt (Seufzen)
private const val SIGH_FATIGUE_THRESHOLD = 0.5f // Müdigkeitsschwelle für Seufzen zu beginnen
private const val SIGH_INTERVAL_MIN_MS = 15000L // Minimale Zeit zwischen Seufzern
private const val SIGH_INTERVAL_MAX_MS = 30000L // Maximale Zeit zwischen Seufzern
private const val SIGH_DURATION_MS = 800L       // Dauer der Seufz-Animation (Mundbewegung)
private const val SIGH_MOUTH_OPENNESS_PEAK = 0.8f // Maximale Mundöffnung beim Seufzen (etwas mehr als Atmen)
// Fütter/Trink-Feedback (Schnelle Mundanimation)
private const val FEED_DRINK_ANIMATION_DURATION_MS = 600L // Dauer der Feedback-Animation (z.B. Schmatzen/Schlucken)
private const val FEED_DRINK_MOUTH_PEAK = 0.75f // Maximale Mundöffnung/Form beim Feedback
// Zufälliges Wackeln (Random Wiggle) - Ausdruck von Wohlbefinden oder leichter Langeweile
private const val WIGGLE_INTERVAL_MIN_MS = 8000L  // Minimale Zeit zwischen Wiggles
private const val WIGGLE_INTERVAL_MAX_MS = 20000L // Maximale Zeit zwischen Wiggles
private const val WIGGLE_DURATION_MS = 500L       // Dauer eines Wiggles (der Animation, nicht des Intervals)
private const val WIGGLE_AMOUNT_PX = 6f           // Stärke des Wackelns (kleiner als Excitement)
// Wiggle-Chance: Höher, wenn Stats gut sind (badStatsFactor niedrig)
private const val WIGGLE_CHANCE_GOOD_STATS = 0.6f // Höhere Chance bei guten Stats (niedrigere Werte)
private const val WIGGLE_CHANCE_BAD_STATS = 0.1f  // Niedrigere Chance bei schlechten Stats (höhere Werte)
// Psychologischer Effekt: Gelegentliche "Aufmerksamkeits-Sakkade" bei guten Stats
private const val ATTENTION_SACCADE_CHANCE_GOOD_STATS = 0.3f // Chance auf eine spezielle Sakkade bei guten Stats
private const val ATTENTION_SACCADE_INTERVAL_MIN_MS = 10000L // Min Zeit zwischen Aufmerksamkeits-Sakkaden
private const val ATTENTION_SACCADE_INTERVAL_MAX_MS = 25000L // Max Zeit zwischen Aufmerksamkeits-Sakkaden
private const val ATTENTION_SACCADE_OFFSET_MULTIPLIER = 1.8f // Größerer Offset für Aufmerksamkeits-Sakkade
private const val ATTENTION_SACCADE_DURATION_MS = 200L // Etwas langsamere, auffälligere Sakkade


// Müdigkeit Visuell
private const val MAX_DROOP_FACTOR = 0.38f         // Maximale Stärke des hängenden Lids (relativ zu Augenradius)
private val SWEAT_DROP_COLOR = Color.Cyan.copy(alpha = 0.7f) // Farbe der Schweißtropfen
private const val SWEAT_DROP_RADIUS_FACTOR = 0.1f // Größe Schweißtropfen (relativ zu Augenradius)
private const val FATIGUE_ICON_SIZE_MULTIPLIER = 0.5f // Größenfaktor für "Zzz"-Icon
// Schlaf
private const val NORMAL_SLEEP_IDLE_THRESHOLD_MS = 60000L // Inaktivitäts-Schwelle für Schlaf (normal, ms)
private const val FATIGUED_SLEEP_IDLE_THRESHOLD_MS = 8000L // Inaktivitäts-Schwelle (max. müde, ms)
private const val SLEEP_CHECK_INTERVAL_MS = 1000L  // Intervall für Schlaf-Check (ms)
private const val SLEEP_EYE_CLOSE_DURATION_MS = 800 // Dauer Augenschließen beim Einschlafen (ms)
private const val SLEEP_MOUTH_CLOSE_DURATION_MS = 1000 // Dauer Mundschließen beim Einschlafen (ms)
// Interaktion
private const val TAP_TIMEOUT_MS = 200L            // Maximale Dauer für einen Tap (ms)
private val TAP_MAX_MOVE_PX = 20f                  // Maximale Bewegung für einen Tap (px)
// Excitement (Bewegungsreaktion auf Schütteln)
private const val SHAKE_THRESHOLD = 2.0f          // Schwellenwert für Beschleunigungsmagnitude (Anpassen!)
private const val EXCITEMENT_COOLDOWN_MS = 100L   // Mindestabstand zw. Excitement-Animationen (ms)
private const val EXCITEMENT_DURATION_MS = 3000L    // Dauer der Excitement-Animation (ms)
private const val EXCITEMENT_WOBBLE_AMOUNT_PX = 12f // Stärke des Augenwackelns (px)
private const val EXCITEMENT_MOUTH_OPEN_FACTOR = 0.9f // Mundöffnung bei Excitement
// Google API Client
class MainActivity : ComponentActivity() {
    private var sensorManager: SensorManager? = null // Für den Zugriff auf Sensoren
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // SensorManager initialisieren
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        setContent {
            // Übergib den SensorManager an die WearApp Composable
            WearApp(sensorManager)
        }

        // Initialisiere GoogleApiClient nur einmal

    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
    }

// onResume/onPause für Sensor nicht hier, wird im Composable via DisposableEffect gehandhabt
}
@Composable
fun WearApp(sensorManager: SensorManager?) { // Nimmt SensorManager als Parameter
    MaterialTheme { // Verwende Wear Material Theme
        SmileyFaceInteractive(sensorManager) // Übergib SensorManager weiter
    }
}
@Composable
fun SmileyFaceInteractive(sensorManager: SensorManager?) {
// --- Zustände (saveable für Persistenz über Prozess-Tod/Konfigurationsänderung) ---
    val hunger = rememberSaveable { mutableStateOf(0.1f) }
    val thirst = rememberSaveable { mutableStateOf(0.1f) }
    val fatigue = rememberSaveable { mutableStateOf(0.0f) }
// --- Animationen & UI-Zustände (nicht saveable, da sie von anderen Zuständen abhängen oder temporär sind) ---
    val blinkProgress = remember { Animatable(0f) } // Fortschritt der Blinzel-Animation (0=offen, 1=geschlossen)
    val pupilOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) } // Offset für normale Sakkaden/Touch
    val mouthOpenness = remember { Animatable(MOUTH_CLOSED_FACTOR) } // Grund-Mundöffnung (wird von verschiedenen Effekten animiert)
    var isTouched by remember { mutableStateOf(false) } // Ob der Bildschirm gerade berührt wird
    var isSleeping by remember { mutableStateOf(false) } // Ob das Pet schläft
    var showStatsScreen by rememberSaveable { mutableStateOf(false) } // Ob der Stats-Screen angezeigt wird
    var targetPupilOffset by remember { mutableStateOf(Offset.Zero) } // Ziel-Offset für Touch-Folgen
    var lastInteractionTime by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) } // Zeitstempel der letzten Interaktion
    var lastExcitementTime by rememberSaveable { mutableLongStateOf(0L) } // Zeitstempel der letzten Aufregung (für Cooldown)


// --- Animation Triggers (ersetzen die direkten Animatable.animateTo Aufrufe an manchen Stellen) ---
    var triggerExcitement by remember { mutableStateOf(false) } // Signal zum Starten der Excitement-Animation
    var triggerSigh by remember { mutableStateOf(false) } // Signal zum Starten der Seufz-Animation
    var triggerFeedAnimation by remember { mutableStateOf(false) } // Signal zum Starten der Fütter-Animation
    var triggerDrinkAnimation by remember { mutableStateOf(false) } // Signal zum Starten der Trink-Animation
    var triggerAttentionSaccade by remember { mutableStateOf(false) } // Signal für spezielle Aufmerksamkeits-Sakkade


// --- Additive Animation Values (managed by their own effects) ---
    // excitementPupilWobble wird für Excitement und Wiggle verwendet
    val excitementPupilWobble = remember { Animatable(Offset.Zero, Offset.VectorConverter) } // Additiver Offset für Excitement/Wackel-Augen
    // excitementMouthOpenness wird für Excitement verwendet
    val excitementMouthOpenness = remember { Animatable(0f) } // Additive Mundöffnung für Excitement


// --- Coroutine Scope & Jobs ---
    var breathingJob by remember { mutableStateOf<Job?>(null) } // Job für die Atem-Animation
    var sighJob by remember { mutableStateOf<Job?>(null) } // Job für Seufz-Timing
    var wiggleJob by remember { mutableStateOf<Job?>(null) } // Job für Wackel-Timing
    var attentionSaccadeJob by remember { mutableStateOf<Job?>(null) } // Job für Aufmerksamkeits-Sakkade Timing
    val scope = rememberCoroutineScope() // Coroutine Scope für Animationen etc.

// --- Geometrie-Daten (werden im Canvas ermittelt) ---
    var canvasCenter by remember { mutableStateOf(Offset.Zero) } // Zentrum des Canvas
    var eyeCenterY by remember { mutableStateOf(0f) } // Y-Position der Augenmitte
    var eyeRadiusPx by remember { mutableStateOf(0f) } // Radius der Augen in Pixeln
    val density = LocalDensity.current // Für DP zu PX Umrechnung
    val tapMaxMovePx = remember(density) { with(density) { TAP_MAX_MOVE_PX.dp.toPx() } } // Max. Bewegung für Tap in PX

// --- Sensor Listener Setup (DisposableEffect für korrektes Lifecycle Management) ---
    val context = LocalContext.current
    DisposableEffect(sensorManager, context, isSleeping, isTouched, showStatsScreen) { // Reagiert auch auf diese Zustände
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) // Sensor für Beschleunigung ohne Gravitation
        var lastSensorUpdate: Long = 0 // Zeitstempel letztes Sensor-Update (für Throttling)

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                // Verarbeite Sensordaten nur, wenn das Pet wach, nicht berührt und nicht im Stats-Screen ist
                if (isSleeping || isTouched || showStatsScreen) return

                val now = System.currentTimeMillis()
                // Optional: Verringere Update-Frequenz, wenn zu viele Events kommen
                if (now - lastSensorUpdate < 100) return // Z.B. max alle 100ms
                lastSensorUpdate = now

                if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    // Berechne die Magnitude der Beschleunigung
                    val magnitude = sqrt(x * x + y * y + z * z)

                    // Log.d("Sensor", "Magnitude: $magnitude") // Zum Debuggen des Thresholds

                    // Prüfe, ob der Schwellenwert überschritten wurde
                    if (magnitude > SHAKE_THRESHOLD) {
                        val currentTime = System.currentTimeMillis()
                        // Prüfe Cooldown und ob das Pet wach ist und keine andere Interaktion stattfindet
                        if (currentTime - lastExcitementTime > EXCITEMENT_COOLDOWN_MS) {
                            // Setze nur den Trigger, wenn er nicht schon gesetzt ist (verhindert Mehrfachauslösung bei langem Shake)
                            if (!triggerExcitement) {
                                Log.d("Interaction", "Shake Detected! Triggering Excitement.")
                                lastExcitementTime = currentTime // Cooldown-Zeitpunkt merken
                                triggerExcitement = true // Animation auslösen
                            }
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* Nicht benötigt */ }
        }

        // Registriere den Listener nur, wenn Sensor und Manager verfügbar sind UND das Pet nicht schläft/im Stats-Screen ist/berührt wird
        if (accelerometer != null && sensorManager != null && !isSleeping && !isTouched && !showStatsScreen) {
            Log.d("Sensor", "Registering Accelerometer Listener")
            sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI) // UI-Rate ist meist ausreichend
        } else {
            if (accelerometer == null) Log.w("Sensor", "Accelerometer not available.")
            if (sensorManager == null) Log.w("Sensor", "SensorManager is null.")
            if (isSleeping) Log.d("Sensor", "Not registering sensor listener: Pet is sleeping.")
            if (isTouched) Log.d("Sensor", "Not registering sensor listener: Pet is touched.")
            if (showStatsScreen) Log.d("Sensor", "Not registering sensor listener: Stats screen is open.")
        }

        // Wird ausgeführt, wenn das Composable aus dem Baum entfernt wird oder die Keys sich ändern
        onDispose {
            Log.d("Sensor", "Unregistering Accelerometer Listener onDispose")
            sensorManager?.unregisterListener(sensorListener) // Wichtig: Listener abmelden!
        }
    }


// --- Abgeleitete Zustände (berechnen Werte basierend auf anderen Zuständen) ---
// Pupillengröße basierend auf Hunger
    val currentPupilSizeFactor by remember {
        derivedStateOf {
            lerp(NORMAL_PUPIL_SIZE_FACTOR, HUNGRY_PUPIL_SIZE_FACTOR,
                ((hunger.value - (HUNGER_THRESHOLD - 0.2f)) / (1f - (HUNGER_THRESHOLD - 0.2f))).coerceIn(0f, 1f)) // Effekt startet etwas früher
        }
    }
// Faktor für visuelle Müdigkeit (hängende Lider, schnelles Blinken)
    val fatigueFactor by remember {
        derivedStateOf { ((fatigue.value - FATIGUE_THRESHOLD) / (1f - FATIGUE_THRESHOLD)).coerceIn(0f, 1f) }
    }
// Faktor für Müdigkeit, die Seufzen beeinflusst
    val fatigueSighFactor by remember {
        derivedStateOf { ((fatigue.value - SIGH_FATIGUE_THRESHOLD) / (1f - SIGH_FATIGUE_THRESHOLD)).coerceIn(0f, 1f) }
    }
// Faktor für Müdigkeit, die den Schlaftimer beeinflusst (startet etwas früher)
    val fatigueSleepinessFactor by remember {
        derivedStateOf { ((fatigue.value - (FATIGUE_THRESHOLD - 0.3f)) / (1f - (FATIGUE_THRESHOLD - 0.3f))).coerceIn(0f, 1f) }
    }
// Faktor für visuelle Durst-Effekte (Mundöffnung)
    val thirstFactor by remember {
        derivedStateOf { ((thirst.value - THIRST_THRESHOLD) / (1f - THIRST_THRESHOLD)).coerceIn(0f, 1f) }
    }
// Dynamischer Schlaftimer basierend auf Müdigkeit
    val currentSleepIdleThreshold by remember {
        derivedStateOf {
            lerp(NORMAL_SLEEP_IDLE_THRESHOLD_MS, FATIGUED_SLEEP_IDLE_THRESHOLD_MS, fatigueSleepinessFactor).toLong()
        }
    }
// Faktor für schlechte Stats (für Wiggle-Chance und andere "unhappy" Verhaltensweisen)
    val badStatsFactor by remember {
        derivedStateOf {
            // Nutze den höchsten Wert der negativen Stats als Indikator für den "schlechtesten" Zustand
            maxOf(
                (hunger.value - HUNGER_THRESHOLD + 0.2f).coerceIn(0f, 1f), // Startet Effekt früher
                (thirst.value - THIRST_THRESHOLD + 0.2f).coerceIn(0f, 1f),
                (fatigue.value - FATIGUE_THRESHOLD + 0.2f).coerceIn(0f, 1f)
            )
        }
    }
// Faktor für gute Stats (für positive Verhaltensweisen)
    val goodStatsFactor by remember {
        derivedStateOf { 1f - badStatsFactor } // Kehrwert des badStatsFactor
    }


// --- Stat Update & Schlaf-Check Effekt (läuft kontinuierlich) ---
    LaunchedEffect(Unit) { // Key = Unit -> Nur einmal starten
        var lastStatUpdateTime = System.currentTimeMillis()
        while (true) { // Endlosschleife
            // Dynamisches Delay für den Check, mindestens SLEEP_CHECK_INTERVAL_MS
            val checkDelay = min(SLEEP_CHECK_INTERVAL_MS, currentSleepIdleThreshold / 2).coerceAtLeast(500L) // Mind. 500ms Check
            delay(checkDelay)
            val currentTime = System.currentTimeMillis()

            // 1. Schlaf-Check: Prüfe, ob Zeit seit letzter Interaktion den dynamischen Schwellenwert überschreitet
            val timeSinceLastInteraction = currentTime - lastInteractionTime
            if (!isTouched && !isSleeping && timeSinceLastInteraction >= currentSleepIdleThreshold && !showStatsScreen) {
                if (!isSleeping) { // Nur einmal auslösen
                    Log.d("State", "Idle threshold reached ($currentSleepIdleThreshold ms), fatigue: ${fatigue.value}. Going to sleep.")
                    isSleeping = true // Pet einschlafen lassen
                }
            }

            // 2. Stat-Update: Erhöhe Stats nur, wenn das Pet wach ist und das Intervall abgelaufen ist
            if (!isSleeping && (currentTime - lastStatUpdateTime) >= STAT_INCREASE_INTERVAL_MS) {
                hunger.value = (hunger.value + HUNGER_INCREASE_RATE).coerceIn(0f, 1f)
                thirst.value = (thirst.value + THIRST_INCREASE_RATE).coerceIn(0f, 1f)
                fatigue.value = (fatigue.value + FATIGUE_INCREASE_RATE).coerceIn(0f, 1f)
                lastStatUpdateTime = currentTime // Zeit des Updates merken
                // Log.d("Stats", "H: ${hunger.value}, T: ${thirst.value}, F: ${fatigue.value}") // Optional: Stats loggen
            }
        }
    }

// --- Effekt für Schlafzustand-Übergänge (wird bei Änderung von isSleeping ausgelöst) ---
    LaunchedEffect(isSleeping) {
        // Abort all other non-essential animations when sleeping state changes
        breathingJob?.cancel()
        sighJob?.cancel() // Cancel sigh timer
        wiggleJob?.cancel() // Cancel wiggle timer
        attentionSaccadeJob?.cancel() // Cancel attention saccade timer

        triggerExcitement = false
        triggerSigh = false // Reset trigger immediately
        triggerFeedAnimation = false
        triggerDrinkAnimation = false
        triggerAttentionSaccade = false // Reset trigger immediately

        // Stop additive animations and reset eye/mouth base animations
        pupilOffset.stop()
        excitementPupilWobble.snapTo(Offset.Zero)
        excitementMouthOpenness.snapTo(0f)
        mouthOpenness.stop() // Stop any ongoing mouth animation


        if (isSleeping) {
            // Einschlafen:
            Log.d("State", "Starting sleep transition")
            coroutineScope { // Starte Animationen parallel
                launch { pupilOffset.animateTo(Offset.Zero, tween(SLEEP_EYE_CLOSE_DURATION_MS / 2)) } // Pupillen zur Mitte
                launch { blinkProgress.animateTo(1f, tween(SLEEP_EYE_CLOSE_DURATION_MS)) } // Augen schließen
                launch { mouthOpenness.animateTo(MOUTH_CLOSED_FACTOR, tween(SLEEP_MOUTH_CLOSE_DURATION_MS)) } // Mund schließen
            }
        } else {
            // Aufwachen:
            Log.d("State", "Starting wake transition")
            lastInteractionTime = System.currentTimeMillis() // Aufwachen ist eine Interaktion
            // Augen öffnen, falls sie geschlossen waren
            if (blinkProgress.value > 0.5f) {
                blinkProgress.animateTo(0f, tween(BLINK_OPEN_DURATION_MS))
            }
            // Mouth state will be handled by the Mouth LE reacting to isSleeping becoming false
            // Breathing will automatically restart via the Mouth LE
        }
    }

// --- Effekt für Mund (Atmen / Touch / Durst / Sigh / Feed/Drink Feedback / Schlaf) ---
// This LE orchestrates the mouth animation based on priority
    LaunchedEffect(isTouched, isSleeping, showStatsScreen, triggerExcitement, triggerSigh, triggerFeedAnimation, triggerDrinkAnimation, thirstFactor) {
        // Cancel any running mouth-related animations (breathing, sigh, feedback) before starting a new one
        breathingJob?.cancel()
        mouthOpenness.stop() // Stop current mouth animation to allow the new one to take over

        // High Priority: Stats Screen or Sleeping -> Mouth closed
        if (showStatsScreen || isSleeping) {
            Log.d("MouthLE", "Stats Screen or Sleeping: Closing mouth.")
            // Ensure mouth closes smoothly unless it's already closed
            if (abs(mouthOpenness.value - MOUTH_CLOSED_FACTOR) > 0.01f) {
                mouthOpenness.animateTo(MOUTH_CLOSED_FACTOR, tween(BREATH_START_TRANSITION_DURATION_MS))
            } else {
                mouthOpenness.snapTo(MOUTH_CLOSED_FACTOR) // Snap if very close
            }
            return@LaunchedEffect
        }

        // Next Priority: Excitement -> Additive open (handled by excitement LE)
        // This LE doesn't *drive* the excitement mouth, but must yield to it.
        if (triggerExcitement || excitementMouthOpenness.value > 0.01f) { // Check excitementMouthOpenness value as well
            Log.d("MouthLE", "Excitement active.")
            // Mouth openness driven by additive 'excitementMouthOpenness' animatable.
            // Just need to ensure breathing/other animations don't conflict.
            return@LaunchedEffect
        }

        // Next Priority: Feed Feedback -> Specific animation
        if (triggerFeedAnimation) {
            Log.d("MouthLE", "Triggering Feed Animation.")
            try {
                // Quick open and close animation (simulates chewing/satisfaction)
                mouthOpenness.animateTo(FEED_DRINK_MOUTH_PEAK, tween((FEED_DRINK_ANIMATION_DURATION_MS * 0.4).toInt(), easing = FastOutLinearInEasing))
                mouthOpenness.animateTo(MOUTH_CLOSED_FACTOR, tween((FEED_DRINK_ANIMATION_DURATION_MS * 0.6).toInt(), easing = LinearOutSlowInEasing))
            } catch (e: CancellationException) {
                Log.d("MouthLE", "Feed Animation cancelled.")
                // Ensure mouth returns to a reasonable state if cancelled
                if (isActive) launch { mouthOpenness.animateTo(MOUTH_CLOSED_FACTOR, tween(100)) }
            } finally {
                triggerFeedAnimation = false // Reset trigger after animation (or if cancelled)
                // Allow logic to fall through to start breathing or other default state
            }
            // DO NOT return here. Let the logic continue to determine the state AFTER the animation.
        }

        // Next Priority: Drink Feedback -> Specific animation
        // Use 'else if' because Feed and Drink triggers shouldn't be true simultaneously.
        else if (triggerDrinkAnimation) {
            Log.d("MouthLE", "Triggering Drink Animation.")
            try {
                // Similar quick open and close for drinking (simulates gulping/satisfaction)
                mouthOpenness.animateTo(FEED_DRINK_MOUTH_PEAK, tween((FEED_DRINK_ANIMATION_DURATION_MS * 0.4).toInt(), easing = FastOutLinearInEasing))
                mouthOpenness.animateTo(MOUTH_CLOSED_FACTOR, tween((FEED_DRINK_ANIMATION_DURATION_MS * 0.6).toInt(), easing = LinearOutSlowInEasing))
            } catch (e: CancellationException) {
                Log.d("MouthLE", "Drink Animation cancelled.")
                if (isActive) launch { mouthOpenness.animateTo(MOUTH_CLOSED_FACTOR, tween(100)) }
            } finally {
                triggerDrinkAnimation = false // Reset trigger after animation
                // Allow logic to fall through
            }
            // DO NOT return here.
        }


        // Next Priority: Sigh -> Specific animation
        // Use 'else if' because Sigh should not happen during feed/drink feedback.
        else if (triggerSigh) {
            Log.d("MouthLE", "Triggering Sigh Animation.")
            try {
                // Sanfter Übergang zur Seufz-Öffnung
                mouthOpenness.animateTo(SIGH_MOUTH_OPENNESS_PEAK, tween((SIGH_DURATION_MS * 0.4).toInt(), easing = FastOutSlowInEasing))
                // Kurz halten (simuliert Seufzer)
                delay((SIGH_DURATION_MS * 0.2).toLong())
                // Sanft schließen
                mouthOpenness.animateTo(MOUTH_CLOSED_FACTOR, tween((SIGH_DURATION_MS * 0.4).toInt(), easing = LinearOutSlowInEasing))
            } catch (e: CancellationException) {
                Log.d("MouthLE", "Sigh Animation cancelled.")
                if (isActive) launch { mouthOpenness.animateTo(MOUTH_CLOSED_FACTOR, tween(200)) }
            } finally {
                triggerSigh = false // Reset trigger after animation (or if cancelled)
                // Allow logic to fall through
            }
            // DO NOT return here.
        }


        // Next Priority: Touched -> Mouth open
        // Use 'else if' to ensure touch overrides breathing/other default states
        else if (isTouched) {
            Log.d("MouthLE", "Touched: Opening mouth.")
            // Don't re-animate if already fully open by excitement (already checked above)
            if (excitementMouthOpenness.value < 0.01f) { // Only if not excited
                launch { mouthOpenness.animateTo(1f, tween(MOUTH_OPEN_DURATION_MS)) }
            }
            return@LaunchedEffect // Don't start breathing while touched
        }

        // Default State: Idle -> Breathing
        Log.d("MouthLE", "Idle: Starting breathing.")
        // If not covered by any higher priority state, start breathing
        val adjustedMinOpenness = lerp(BREATHING_MIN_OPENNESS, BREATHING_MIN_OPENNESS + THIRSTY_MOUTH_OPENNESS_INCREASE, thirstFactor).coerceIn(MOUTH_CLOSED_FACTOR, 1f)
        val adjustedMaxOpenness = lerp(BREATHING_MAX_OPENNESS, BREATHING_MAX_OPENNESS + THIRSTY_MOUTH_OPENNESS_INCREASE, thirstFactor).coerceIn(MOUTH_CLOSED_FACTOR, 1f)

        // Ensure mouth is at the baseline (min breathing openness) before starting the breathing cycle
        if (abs(mouthOpenness.value - adjustedMinOpenness) > 0.01f) {
            try {
                // Animate to the baseline over a short duration
                mouthOpenness.animateTo(adjustedMinOpenness, tween(BREATH_START_TRANSITION_DURATION_MS / 2))
                delay(BREATHING_PAUSE_MS / 2) // Short pause before starting the first full breath
            } catch (e: CancellationException) {
                Log.d("MouthLE", "Transition to breathing baseline cancelled.")
                return@LaunchedEffect // Exit if cancelled
            }
        }


        breathingJob = launch {
            // Start the breathing cycle from the current openness level (should be close to adjustedMinOpenness)
            while (isActive) { // Check isActive in the loop
                try {
                    // Animate from current value to max (inhale)
                    mouthOpenness.animateTo(adjustedMaxOpenness, tween(BREATHING_INHALE_DURATION_MS, easing = LinearEasing))
                    if (!isActive) break

                    delay(BREATHING_PAUSE_MS)
                    if (!isActive) break

                    // Animate from current value (should be max) to min (exhale)
                    mouthOpenness.animateTo(adjustedMinOpenness, tween(BREATHING_EXHALE_DURATION_MS, easing = LinearEasing))
                    if (!isActive) break

                    delay(BREATHING_PAUSE_MS)
                    if (!isActive) break
                } catch (e: CancellationException) {
                    Log.d("Breathing", "Breathing job cancelled")
                    break // Beende Schleife bei Abbruch
                }
            }
        }
    }

// --- Effekt für Excitement Animation ---
    LaunchedEffect(triggerExcitement) {
        if (triggerExcitement) {
            Log.d("Animation", "Starting Excitement Animation")
            // Laufende Animationen unterbrechen, die stören könnten
            pupilOffset.stop() // Normale Augenbewegung stoppen
            // Note: breathingJob is cancelled by the Mouth LE when triggerExcitement becomes true
            // Cancel other pending or ongoing temporary animations/timers
            sighJob?.cancel()
            wiggleJob?.cancel()
            attentionSaccadeJob?.cancel()
            triggerSigh = false
            triggerFeedAnimation = false
            triggerDrinkAnimation = false
            triggerAttentionSaccade = false

            coroutineScope {
                // 1. Mund schnell auf und kurz halten, dann zu (Additive animation)
                launch {
                    try {
                        // Animate the additive value
                        excitementMouthOpenness.animateTo(
                            targetValue = EXCITEMENT_MOUTH_OPEN_FACTOR, // Animate to max additive value
                            animationSpec = tween(durationMillis = (EXCITEMENT_DURATION_MS * 0.3).toInt(), easing = FastOutLinearInEasing)
                        )
                        // Implicitly held by the duration of the next animateTo
                        excitementMouthOpenness.animateTo(
                            targetValue = 0f, // Return to additive zero
                            animationSpec = tween(durationMillis = (EXCITEMENT_DURATION_MS * 0.7).toInt(), easing = LinearOutSlowInEasing)
                        )
                    } catch (e: Exception) {
                        Log.d("Animation", "Excitement Mouth Animation cancelled/failed: ${e.message}")
                        excitementMouthOpenness.snapTo(0f) // Reset bei Abbruch
                    }
                }
                // 2. Augen wackeln lassen (Additive animation)
                launch {
                    try {
                        val startTime = withFrameNanos { it }
                        // Wackle für die Dauer der Animation
                        while (isActive && withFrameNanos { it } - startTime < EXCITEMENT_DURATION_MS * 1_000_000) {
                            val wobbleTarget = Offset(
                                Random.nextFloat() * 2f - 1f, // Zufällige X-Richtung
                                Random.nextFloat() * 2f - 1f  // Zufällige Y-Richtung
                            ) * EXCITEMENT_WOBBLE_AMOUNT_PX // Skaliert mit Wackel-Stärke
                            excitementPupilWobble.animateTo(
                                wobbleTarget,
                                tween(durationMillis = Random.nextInt(60, 120), easing = LinearEasing) // Kurze, schnelle Bewegungen
                            )
                            if (!isActive) break // Check again after animation step
                        }
                        // Am Ende sanft zur Mitte zurück
                        if (isActive) {
                            excitementPupilWobble.animateTo(Offset.Zero, tween(100))
                        }
                    } catch (e: Exception) {
                        Log.d("Animation", "Excitement Pupil Animation cancelled/failed: ${e.message}")
                        excitementPupilWobble.snapTo(Offset.Zero) // Reset bei Abbruch
                    }
                }
            }
            Log.d("Animation", "Excitement Animation Finished")
            triggerExcitement = false // Trigger zurücksetzen nach Animation
            // Mouth/Breathing and Eye/Saccade LEs will react to triggerExcitement becoming false
        }
    }

// --- Effekt für Augen Blinzeln (berücksichtigt Müdigkeit) ---
    LaunchedEffect(fatigueFactor, isSleeping, showStatsScreen, triggerExcitement) { // Reagiert auch auf Müdigkeit/Schlaf/Stats/Excitement
        // Abort current blink animation if state changes
        if (blinkProgress.isRunning) {
            scope.launch { blinkProgress.stop() } // Stop ongoing animation
        }
        // Ensure eye is open or snaps open if state changes away from sleeping
        if (!isSleeping && blinkProgress.value > 0.01f) {
            scope.launch { blinkProgress.animateTo(0f, tween(BLINK_OPEN_DURATION_MS)) }
        } else if (isSleeping) {
            scope.launch { blinkProgress.snapTo(1f) } // Ensure closed when sleeping state is true
        }


        // Don't blink if sleeping, stats screen is open, or excitement is active
        if (isSleeping || showStatsScreen || triggerExcitement) {
            Log.d("BlinkLE", "Stopping blink loop: Sleeping=$isSleeping, Stats=$showStatsScreen, Excitement=$triggerExcitement.")
            return@LaunchedEffect // Stop this LE
        }

        Log.d("BlinkLE", "Starting blink loop. Fatigue factor: $fatigueFactor")
        while (isActive) { // Loop runs as long as LE is active
            // Passe Blink-Intervall basierend auf Müdigkeit an
            val minDelay = lerp(BLINK_INTERVAL_MIN_MS, FATIGUED_BLINK_INTERVAL_MIN_MS, fatigueFactor)
            val maxDelay = lerp(BLINK_INTERVAL_MAX_MS, FATIGUED_BLINK_INTERVAL_MAX_MS, fatigueFactor)
            val blinkDelay = Random.nextLong(minDelay.toLong(), maxDelay.toLong())
            Log.d("BlinkLE", "Next blink in ${blinkDelay} ms")

            try {
                delay(blinkDelay) // Warte zufällige Zeit
            } catch (e: CancellationException) {
                Log.d("BlinkLE", "Blink delay cancelled.")
                break // Exit loop if cancelled
            }

            // Blinzle nur, wenn:
            // - nicht berührt
            // - Auge aktuell offen (oder fast offen)
            // - Keine Blinzel-Animation läuft bereits
            // - Keine Excitement-Animation läuft (Augenwackeln)
            // - Keine Wiggle-Animation läuft (additive wobble indicates this)
            if (!isTouched && blinkProgress.value < 0.1f && !blinkProgress.isRunning && !excitementPupilWobble.isRunning) {
                // Starte Blinzel-Animation im Scope, damit sie nicht durch Müdigkeitsänderung abgebrochen wird (unless LE restarts)
                scope.launch {
                    Log.d("BlinkLE", "Starting blink animation.")
                    try {
                        blinkProgress.animateTo(1f, tween(BLINK_CLOSE_DURATION_MS, easing = LinearEasing)) // Schließen
                        delay(BLINK_PAUSE_MS) // Kurz geschlossen halten
                        blinkProgress.animateTo(0f, tween(BLINK_OPEN_DURATION_MS, easing = LinearEasing)) // Öffnen
                        Log.d("BlinkLE", "Blink animation finished.")
                    } catch (e: CancellationException) {
                        // Bei Abbruch sicherstellen, dass das Auge offen ist
                        Log.d("BlinkLE", "Blink animation cancelled mid-animation.")
                        if (isActive) blinkProgress.snapTo(0f) // Snap open
                    } catch (e: Exception) {
                        // Andere Fehler: Auge sicherheitshalber öffnen
                        Log.e("BlinkLE", "Error during blink animation", e)
                        if (isActive) blinkProgress.snapTo(0f) // Snap open
                    }
                }
            } else {
                // Log why blink didn't happen
                if(isTouched) Log.d("BlinkLE", "Skipped blink: Touched")
                if(blinkProgress.value >= 0.1f || blinkProgress.isRunning) Log.d("BlinkLE", "Skipped blink: Animation running or eye not open")
                if(excitementPupilWobble.isRunning) Log.d("BlinkLE", "Skipped blink: Additive eye animation running")
            }
        }
        Log.d("BlinkLE", "Blink loop terminated.")
    }

// --- Effekt für Augen Sakkaden (zufällige Blicksprünge im Leerlauf, beeinflusst von Hunger und "Attention") ---
// Reagiert auf Schlaf, StatsScreen, Berührung, Excitement, AttentionSaccade trigger
    LaunchedEffect(isSleeping, showStatsScreen, isTouched, triggerExcitement, triggerAttentionSaccade) {
        // Abort current saccade animation if state changes
        if (pupilOffset.isRunning) {
            pupilOffset.stop()
            Log.d("SaccadeLE", "Saccade animation stopped due to state change.")
        }

        // No Saccades if any of these conditions are true (or additive wobble is active from Excitement/Wiggle)
        if (isSleeping || showStatsScreen || isTouched || triggerExcitement || excitementPupilWobble.isRunning) {
            Log.d("SaccadeLE", "Stopping saccade loop due to state: Sleeping=$isSleeping, Stats=$showStatsScreen, Touched=$isTouched, Excitement=$triggerExcitement, AdditiveWobble=${excitementPupilWobble.isRunning}.")
            // If not touched and no additive wobble, but pupil not centered -> move to center
            if (!isTouched && !excitementPupilWobble.isRunning && pupilOffset.value.getDistanceSquared() > 1f && !pupilOffset.isRunning) {
                Log.d("SaccadeLE", "Moving pupil to center.")
                pupilOffset.animateTo(Offset.Zero, tween(SACCADE_DURATION_MS * 2))
            }
            return@LaunchedEffect // Exit this LE
        }

        // --- Triggered Attention Saccade (Higher Priority than regular Saccades) ---
        if (triggerAttentionSaccade) {
            Log.d("SaccadeLE", "Triggering Attention Saccade.")
            try {
                val maxAttentionOffset = eyeRadiusPx * ATTENTION_SACCADE_OFFSET_MULTIPLIER.coerceIn(0f, 1f)
                val randomAngle = Random.nextFloat() * 2 * PI.toFloat()
                val randomRadius = Random.nextFloat() * maxAttentionOffset
                val attentionTarget = Offset(
                    cos(randomAngle) * randomRadius,
                    sin(randomAngle) * randomRadius * 0.7f // Leichte ovale Bewegung
                )
                // Animate to the attention target
                pupilOffset.animateTo(attentionTarget, tween(ATTENTION_SACCADE_DURATION_MS.toInt()))
                // Animate back to center or a slight random offset after the attention duration
                delay(ATTENTION_SACCADE_DURATION_MS.toLong()) // Hold at target briefly
                pupilOffset.animateTo(Offset.Zero, tween(SACCADE_DURATION_MS * 2)) // Animate back to center
            } catch (e: CancellationException) {
                Log.d("SaccadeLE", "Attention Saccade animation cancelled.")
                if (isActive) launch { pupilOffset.animateTo(Offset.Zero, tween(100)) } // Snap or animate back to center on cancel
            } finally {
                triggerAttentionSaccade = false // Reset trigger
                // The loop below will restart if conditions are still met
            }
            // DO NOT return here. Let the logic fall through to potentially start regular saccades after the attention one.
        }

        // Saccades start (only when Pet is "idle" and no Attention Saccade was just triggered)
        Log.d("SaccadeLE", "Starting regular saccade loop.")
        val saccadeJob = launch {
            // Initial sanft zur Mitte zurück, falls nicht schon dort and no other additive offset
            if (pupilOffset.value.getDistanceSquared() > 1f && !excitementPupilWobble.isRunning) {
                try {
                    pupilOffset.animateTo(Offset.Zero, tween(SACCADE_DURATION_MS * 2))
                    delay(SACCADE_DURATION_MS / 2L) // Kurze Pause in der Mitte
                } catch (e: CancellationException) {
                    Log.d("SaccadeLE", "Initial center animation cancelled.")
                    return@launch // Exit on cancellation
                }
            }

            // Start random saccades, as long as "idle"
            if (eyeRadiusPx > 0f && isActive) {
                while (isActive) { // Loop runs as long as the job is active
                    // Re-check conditions before delay and animation
                    if (isTouched || isSleeping || showStatsScreen || triggerExcitement || triggerAttentionSaccade || excitementPupilWobble.isRunning) {
                        Log.d("SaccadeLE", "Exiting saccade loop: State changed mid-loop.")
                        break
                    }

                    // --- HUNGER-DRIVEN SACCADES ---
                    // Adjust interval and max offset based on hunger
                    val hungerFactor = hunger.value.coerceIn(0f, 1f) // Use raw hunger for effect intensity
                    val currentMinDelay = lerp(SACCADE_INTERVAL_MIN_MS.toFloat(), SACCADE_INTERVAL_MIN_MS * HUNGER_SACCADE_INTERVAL_MULTIPLIER, hungerFactor)
                    val currentMaxDelay = lerp(SACCADE_INTERVAL_MAX_MS.toFloat(), SACCADE_INTERVAL_MAX_MS * HUNGER_SACCADE_INTERVAL_MULTIPLIER, hungerFactor)
                    val saccadeDelay = Random.nextLong(currentMinDelay.toLong().coerceAtLeast(100L), currentMaxDelay.toLong().coerceAtLeast(200L)) // Ensure min delay
                    val currentMaxSaccadeOffsetFactor = lerp(PUPIL_MAX_OFFSET_FACTOR, PUPIL_MAX_OFFSET_FACTOR * HUNGER_SACCADE_OFFSET_MULTIPLIER, hungerFactor)
                    val maxSaccadeOffset = eyeRadiusPx * currentMaxSaccadeOffsetFactor.coerceIn(0f, 1f) // Max offset based on eye size and hunger

                    Log.d("SaccadeLE", "Next saccade in ${saccadeDelay} ms. Max offset: $maxSaccadeOffset (Hunger: $hungerFactor)")

                    try {
                        delay(saccadeDelay)
                    } catch (e: CancellationException) {
                        Log.d("SaccadeLE", "Saccade delay cancelled.")
                        break // Exit loop if cancelled
                    }

                    // Re-check conditions after delay
                    if (!isActive || isTouched || isSleeping || showStatsScreen || triggerExcitement || triggerAttentionSaccade || excitementPupilWobble.isRunning) {
                        Log.d("SaccadeLE", "Exiting saccade loop: State changed after delay.")
                        break
                    }

                    // Calculate random saccade target within the adjusted limited area
                    val randomAngle = Random.nextFloat() * 2 * PI.toFloat()
                    val randomRadius = Random.nextFloat() * maxSaccadeOffset // Use the hunger-adjusted max offset
                    val saccadeTarget = Offset(
                        cos(randomAngle) * randomRadius,
                        sin(randomAngle) * randomRadius * 0.7f // Leichte ovale Bewegung
                    )

                    // Animate to the saccade target
                    try {
                        Log.d("SaccadeLE", "Animating to new saccade target: $saccadeTarget")
                        // Stop any existing animation on pupilOffset before starting the new one
                        pupilOffset.stop()
                        pupilOffset.animateTo(saccadeTarget, tween(SACCADE_DURATION_MS))
                    } catch (e: CancellationException) {
                        Log.d("SaccadeLE", "Saccade animation cancelled mid-animation.")
                        break // Exit loop on cancellation
                    }
                }
            }
            Log.d("SaccadeLE", "Regular Saccade loop finished or cancelled.")
        }
        // This job (saccadeJob) is automatically cancelled when the LaunchedEffect key changes
    }

// --- Effekt für Augen-Folgen bei Touch ---
    LaunchedEffect(isTouched, targetPupilOffset, isSleeping, showStatsScreen) { // React to changes in these states
        // Touch follow is only active when touched AND not sleeping AND not in stats screen
        if (isTouched && !isSleeping && !showStatsScreen) {
            Log.d("TouchLE", "Touched: Animating pupil to target $targetPupilOffset")
            // Under normal circumstances, the Saccade LE will be stopped when isTouched is true.
            // However, if it was already animating when touch started, stopping it here ensures the touch animation takes over immediately.
            if (pupilOffset.isRunning) {
                pupilOffset.stop() // Stops the current animation immediately
            }
            // Start animation to the new touch target
            try {
                pupilOffset.animateTo(targetPupilOffset, tween(TOUCH_FOLLOW_DURATION_MS))
            } catch (e: CancellationException) {
                Log.d("TouchLE", "Touch follow animation cancelled.")
                // If cancelled mid-animation, let the Saccade LE (if conditions allow) take over
            }
        }
        // No else needed. When isTouched becomes false (or sleeping/stats become true), this LE exits,
        // and the Saccade LE (if not prevented by other states) will automatically restart and handle the return to center.
    }

// --- Effekt für Seufz-Animation Timer (abhängig von Müdigkeit) ---
    LaunchedEffect(fatigueSighFactor, isSleeping, isTouched, showStatsScreen, triggerExcitement, triggerFeedAnimation, triggerDrinkAnimation, triggerAttentionSaccade) {
        // Cancel previous sigh timer job
        sighJob?.cancel()

        // Don't start sigh timer if any of these states are active or fatigue is too low
        if (isSleeping || isTouched || showStatsScreen || triggerExcitement || triggerFeedAnimation || triggerDrinkAnimation || triggerAttentionSaccade || fatigueSighFactor < 0.01f) {
            Log.d("SighTimerLE", "Stopping sigh timer loop: Sleeping=$isSleeping, Touched=$isTouched, Stats=$showStatsScreen, Excitement=$triggerExcitement, Feedback=${triggerFeedAnimation || triggerDrinkAnimation}, AttentionSaccade=$triggerAttentionSaccade, FatigueFactor=$fatigueSighFactor")
            triggerSigh = false // Ensure trigger is reset
            return@LaunchedEffect
        }

        Log.d("SighTimerLE", "Starting sigh timer loop. Fatigue factor: $fatigueSighFactor")
        sighJob = launch {
            while(isActive) {
                // Calculate delay based on fatigue (more fatigue -> shorter max delay)
                val baseDelay = lerp(SIGH_INTERVAL_MAX_MS.toFloat(), SIGH_INTERVAL_MIN_MS.toFloat(), fatigueSighFactor)
                val randomDelay = Random.nextLong((baseDelay * 0.8f).toLong().coerceAtLeast(SIGH_INTERVAL_MIN_MS / 3), (baseDelay * 1.2f).toLong().coerceAtLeast(SIGH_INTERVAL_MIN_MS))

                Log.d("SighTimerLE", "Next sigh possibility in ${randomDelay} ms.")

                try {
                    delay(randomDelay)
                } catch (e: CancellationException) {
                    Log.d("SighTimerLE", "Sigh delay cancelled.")
                    break // Exit loop
                }

                // Re-check conditions after delay before triggering
                if (!isActive || isSleeping || isTouched || showStatsScreen || triggerExcitement || triggerFeedAnimation || triggerDrinkAnimation || triggerAttentionSaccade || fatigueSighFactor < 0.01f) {
                    Log.d("SighTimerLE", "Exiting sigh timer loop after delay: State changed.")
                    break
                }

                // Trigger the sigh animation (handled by Mouth LE)
                Log.d("SighTimerLE", "Triggering sigh animation.")
                triggerSigh = true
                // Wait for the sigh animation to complete before the next potential sigh delay starts
                try {
                    delay(SIGH_DURATION_MS + 200L) // Wait for animation + buffer
                } catch (e: CancellationException) {
                    Log.d("SighTimerLE", "Waiting for sigh animation completion cancelled.")
                    break // Exit loop
                }

                // Add a brief mandatory pause after a sigh animation before the next delay starts
                try {
                    delay(1000L) // Pause for 1 second after a sigh finishes
                } catch (e: CancellationException) {
                    Log.d("SighTimerLE", "Post-sigh pause cancelled.")
                    break // Exit loop
                }
            }
            Log.d("SighTimerLE", "Sigh timer loop terminated.")
        }
    }

// --- Effekt für Zufälliges Wackeln Timer (Random Wiggle, abhängig von guten Stats) ---
    LaunchedEffect(isSleeping, isTouched, showStatsScreen, triggerExcitement, badStatsFactor) {
        // Cancel previous wiggle timer job
        wiggleJob?.cancel()

        // Stop wiggle timer if any of these states are active or stats are very bad
        if (isSleeping || isTouched || showStatsScreen || triggerExcitement || badStatsFactor > 0.8f) { // Don't wiggle much if very unhappy
            Log.d("WiggleTimerLE", "Stopping wiggle timer loop: Sleeping=$isSleeping, Touched=$isTouched, Stats=$showStatsScreen, Excitement=$triggerExcitement, BadStatsFactor=$badStatsFactor.")
            // Ensure additive wobble is reset if it was mid-animation
            if (excitementPupilWobble.value.getDistanceSquared() > 1f && !excitementPupilWobble.isRunning) {
                scope.launch { excitementPupilWobble.animateTo(Offset.Zero, tween(100)) }
            }
            return@LaunchedEffect
        }
        Log.d("WiggleTimerLE", "Starting wiggle timer loop. Bad Stats Factor: $badStatsFactor (Good Stats Factor: $goodStatsFactor)")

        wiggleJob = launch {
            while(isActive) {
                val wiggleDelay = Random.nextLong(WIGGLE_INTERVAL_MIN_MS, WIGGLE_INTERVAL_MAX_MS)
                Log.d("WiggleTimerLE", "Next wiggle possibility in ${wiggleDelay} ms.")

                try {
                    delay(wiggleDelay)
                } catch (e: CancellationException) {
                    Log.d("WiggleTimerLE", "Wiggle delay cancelled.")
                    break // Exit loop
                }

                // Re-check conditions after delay
                if (!isActive || isSleeping || isTouched || showStatsScreen || triggerExcitement || badStatsFactor > 0.8f) {
                    Log.d("WiggleTimerLE", "Exiting wiggle timer loop after delay: State changed.")
                    break
                }

                // Determine chance based on stats (Higher chance if stats are good)
                val chance = lerp(WIGGLE_CHANCE_GOOD_STATS, WIGGLE_CHANCE_BAD_STATS, badStatsFactor)
                val randomValue = Random.nextFloat()

                Log.d("WiggleTimerLE", "Wiggle chance: $chance (Random: $randomValue)")

                // Trigger wiggle if chance is met AND no major eye animation is currently happening
                // (Saccade is okay, it's additive, but let's avoid conflict if pupilOffset is mid-animation from something else)
                // Also check that the additive wobble Animatable isn't already running (Excitement or previous Wiggle)
                if (randomValue < chance && !pupilOffset.isRunning && !excitementPupilWobble.isRunning) {
                    Log.d("WiggleTimerLE", "Triggering wiggle animation.")
                    // Launch the wiggle animation directly in the scope
                    scope.launch {
                        try {
                            // Random target for the small wobble
                            val wobbleTarget = Offset(
                                Random.nextFloat() * 2f - 1f,
                                Random.nextFloat() * 2f - 1f
                            ) * WIGGLE_AMOUNT_PX // Use the smaller wiggle amount

                            Log.d("WiggleTimerLE", "Animating additive wiggle to: $wobbleTarget")
                            // Animate wobble out and back in
                            excitementPupilWobble.animateTo(wobbleTarget, tween((WIGGLE_DURATION_MS * 0.4).toInt()))
                            excitementPupilWobble.animateTo(Offset.Zero, tween((WIGGLE_DURATION_MS * 0.6).toInt()))
                            Log.d("WiggleTimerLE", "Wiggle animation finished.")
                        } catch (e: CancellationException) {
                            Log.d("WiggleTimerLE", "Wiggle animation cancelled.")
                            excitementPupilWobble.snapTo(Offset.Zero) // Reset on cancel
                        }
                    }
                }
            }
            Log.d("WiggleTimerLE", "Wiggle timer loop terminated.")
        }
    }

// --- Effekt für Aufmerksamkeits-Sakkade Timer (abhängig von guten Stats und Inaktivität) ---
    LaunchedEffect(isSleeping, isTouched, showStatsScreen, triggerExcitement, badStatsFactor, lastInteractionTime) {
        // Cancel previous attention saccade timer job
        attentionSaccadeJob?.cancel()

        // Don't start timer if any of these states are active or stats are bad
        // Only trigger attention saccade if stats are reasonably good AND pet has been idle for a bit
        val timeSinceLastInteraction = System.currentTimeMillis() - lastInteractionTime
        if (isSleeping || isTouched || showStatsScreen || triggerExcitement || badStatsFactor > 0.4f || timeSinceLastInteraction < 5000L) { // Requires at least 5s idle
            Log.d("AttentionSaccadeTimerLE", "Stopping attention saccade timer loop: Sleeping=$isSleeping, Touched=$isTouched, Stats=$showStatsScreen, Excitement=$triggerExcitement, BadStatsFactor=$badStatsFactor, IdleTime=${timeSinceLastInteraction/1000}s.")
            triggerAttentionSaccade = false // Ensure trigger is reset
            // Ensure pupils are not stuck if cancelled mid-animation (Pupil LE handles returning to center/saccade normally)
            return@LaunchedEffect
        }
        Log.d("AttentionSaccadeTimerLE", "Starting attention saccade timer loop. Good Stats Factor: $goodStatsFactor")

        attentionSaccadeJob = launch {
            while(isActive) {
                // Calculate delay
                val randomDelay = Random.nextLong(ATTENTION_SACCADE_INTERVAL_MIN_MS, ATTENTION_SACCADE_INTERVAL_MAX_MS)
                Log.d("AttentionSaccadeTimerLE", "Next attention saccade possibility in ${randomDelay} ms.")

                try {
                    delay(randomDelay)
                } catch (e: CancellationException) {
                    Log.d("AttentionSaccadeTimerLE", "Attention saccade delay cancelled.")
                    break // Exit loop
                }

                // Re-check conditions after delay before triggering
                val currentTime = System.currentTimeMillis()
                val currentIdleTime = currentTime - lastInteractionTime
                if (!isActive || isSleeping || isTouched || showStatsScreen || triggerExcitement || badStatsFactor > 0.4f || currentIdleTime < 5000L) { // Check idle time again
                    Log.d("AttentionSaccadeTimerLE", "Exiting attention saccade timer loop after delay: State changed.")
                    break
                }

                // Determine chance based on good stats
                val chance = lerp(0f, ATTENTION_SACCADE_CHANCE_GOOD_STATS, goodStatsFactor) // Chance increases with good stats
                val randomValue = Random.nextFloat()

                Log.d("AttentionSaccadeTimerLE", "Attention saccade chance: $chance (Random: $randomValue)")

                // Trigger attention saccade if chance is met AND no major eye animation is currently happening
                if (randomValue < chance && !pupilOffset.isRunning && !excitementPupilWobble.isRunning) {
                    Log.d("AttentionSaccadeTimerLE", "Triggering attention saccade.")
                    // Trigger the attention saccade (handled by Saccade LE)
                    triggerAttentionSaccade = true
                    // Wait for the animation to complete before the next potential delay starts
                    try {
                        // Wait for the attention saccade animation duration PLUS the animation back to center
                        delay(ATTENTION_SACCADE_DURATION_MS + SACCADE_DURATION_MS * 2 + 200L) // Wait for anim out, anim back, and buffer
                    } catch (e: CancellationException) {
                        Log.d("AttentionSaccadeTimerLE", "Waiting for attention saccade completion cancelled.")
                        break // Exit loop
                    }
                }
            }
            Log.d("AttentionSaccadeTimerLE", "Attention saccade timer loop terminated.")
        }
    }


    // --- Interaktionsfunktionen ---
    fun interact() {
        lastInteractionTime = System.currentTimeMillis() // Zeitstempel aktualisieren
        if (isSleeping) {
            isSleeping = false // Pet aufwecken
            Log.d("Interaction", "Pet woken up.")
        }
        // Jede Interaktion beendet laufende temporäre Animationen und ihre Timer
        Log.d("Interaction", "Processing interaction, cancelling temp states/timers.")
        if (triggerExcitement || excitementPupilWobble.isRunning || excitementMouthOpenness.isRunning) {
            Log.d("Interaction", "Interaction cancelled excitement.")
            triggerExcitement = false
            // Stop additive animations gently or abruptly (snapTo for immediate)
            scope.launch { excitementPupilWobble.animateTo(Offset.Zero, tween(100)) }
            scope.launch { excitementMouthOpenness.animateTo(0f, tween(100)) }
        }
        if (triggerSigh) {
            Log.d("Interaction", "Interaction cancelled sigh trigger.")
            triggerSigh = false
            // Mouth LE will handle returning to breathing
        }
        sighJob?.cancel() // Also cancel the sigh timer job

        // Wiggle animation is handled by its own LE animating `excitementPupilWobble` directly.
        // Canceling the wiggle timer job is sufficient. If a wiggle was mid-animation,
        // the check in the Wiggle LE or the general pupilOffset handling will eventually reset `excitementPupilWobble`.
        Log.d("Interaction", "Interaction cancelling wiggle timer.")
        wiggleJob?.cancel()

        if (triggerAttentionSaccade) {
            Log.d("Interaction", "Interaction cancelled attention saccade trigger.")
            triggerAttentionSaccade = false
            // Pupil LE handles returning to center/saccade
        }
        attentionSaccadeJob?.cancel() // Also cancel the attention saccade timer job


        if (triggerFeedAnimation || triggerDrinkAnimation) {
            Log.d("Interaction", "Interaction cancelled feed/drink animation triggers.")
            triggerFeedAnimation = false
            triggerDrinkAnimation = false
            // Mouth LE will handle returning to breathing
        }
        // Note: Blink is NOT cancelled by interaction, it's a natural process.
        // Saccades resume automatically when isTouched becomes false and no other higher priority eye animation is active.
        // Breathing resumes automatically when isTouched becomes false and no other higher priority mouth animation is active.
    }

    fun feedPet() {
        hunger.value = (hunger.value - FEED_AMOUNT).coerceIn(0f, 1f) // Hunger reduzieren
        interact() // Als Interaktion werten (weckt auf, setzt Zeit, stoppt temp animations)
        triggerFeedAnimation = true // Trigger feedback animation
        Log.d("Action", "Pet fed. Hunger: ${hunger.value}")
    }

    fun waterPet() {
        thirst.value = (thirst.value - DRINK_AMOUNT).coerceIn(0f, 1f) // Durst reduzieren
        interact() // Als Interaktion werten
        triggerDrinkAnimation = true // Trigger feedback animation
        Log.d("Action", "Pet watered. Thirst: ${thirst.value}")
    }

    fun restPet() {
        fatigue.value = (fatigue.value - REST_AMOUNT).coerceIn(0f, 1f) // Müdigkeit reduzieren
        interact() // Auch Ausruhen ist eine Interaktion und weckt auf
        Log.d("Action", "Pet rested. Fatigue: ${fatigue.value}")
        // No specific animation for rest besides waking up (handled by isSleeping transition)
    }


// --- UI ---
    Box(
        modifier = Modifier
            .fillMaxSize() // Füllt den gesamten Bildschirm
            .background(Color.Black) // Schwarzer Hintergrund
            // Input-Handler für Touch-Gesten
            .pointerInput(Unit) { // Key = Unit -> Handler bleibt über Komposition hinweg bestehen
                forEachGesture { // Verarbeitet jede Geste (Down, Move, Up)
                    awaitPointerEventScope { // Scope für die Verarbeitung von Pointer-Events
                        // Warte auf das erste Down-Event (Finger auf dem Bildschirm)
                        val down = awaitFirstDown(requireUnconsumed = false) // false: Event muss nicht unkonsumiert sein
                        val downTime = System.currentTimeMillis() // Zeit des Down-Events merken
                        val downPosition = down.position // Position des Down-Events merken
                        var fingerMoved = false // Flag, ob sich der Finger signifikant bewegt hat
                        var potentialTap = true // Flag, ob die Geste noch ein Tap sein könnte

                        // --- Touch Start ---
                        isTouched = true // Touch-Zustand setzen
                        interact()       // Interaktion auslösen (weckt auf, stoppt Excitement etc.)
                        Log.d("Interaction", "Touch started.")

                        // Initialen Pupillen-Offset für das Folgen des Fingers berechnen
                        if (canvasCenter != Offset.Zero) { // Nur wenn Canvas-Mitte bekannt ist
                            val initialVectorX = downPosition.x - canvasCenter.x
                            val initialVectorY = downPosition.y - eyeCenterY // Y-Offset relativ zur Augen-Y-Achse
                            val initialDist = sqrt(initialVectorX * initialVectorX + initialVectorY * initialVectorY)
                            // Skaliere den Vektor auf die max. Touch-Distanz
                            val initialScale = if (initialDist > 1e-6f) min(1f, PUPIL_MAX_OFFSET_PIXELS_TOUCH / initialDist) else 0f
                            targetPupilOffset = Offset(initialVectorX * initialScale, initialVectorY * initialScale)
                            Log.d("Interaction", "Calculated initial touch target offset: $targetPupilOffset")
                            // Immediately animate pupil to the initial touch position
                            scope.launch {
                                try {
                                    pupilOffset.animateTo(targetPupilOffset, tween(TOUCH_FOLLOW_DURATION_MS))
                                } catch (e: CancellationException) {
                                    Log.d("Interaction", "Initial touch follow animation cancelled.")
                                }
                            }
                        } else {
                            Log.w("Interaction", "Canvas center not measured yet, cannot calculate initial touch offset.")
                        }

                        var pointerId = down.id // ID des Pointers merken (für Multi-Touch-Szenarien, hier meist nur einer)
                        try {
                            // Schleife, die läuft, solange der Finger auf dem Bildschirm ist (Move/Drag)
                            do {
                                val event = awaitPointerEvent() // Warte auf das nächste Pointer-Event
                                // Finde das Change-Objekt für unseren Pointer
                                val currentPointer = event.changes.firstOrNull { it.id == pointerId }

                                if (currentPointer != null) {
                                    // --- Tap-Erkennung ---
                                    // Prüfe, ob sich der Finger zu weit bewegt hat
                                    if (!fingerMoved && (currentPointer.position - downPosition).getDistance() > tapMaxMovePx) {
                                        fingerMoved = true
                                        potentialTap = false // Wenn bewegt, kein Tap mehr
                                        Log.d("Interaction", "Finger moved too far, no longer potential tap.")
                                    }
                                    // Prüfe, ob der Finger zu lange gehalten wurde
                                    if (potentialTap && System.currentTimeMillis() - downTime > TAP_TIMEOUT_MS) {
                                        potentialTap = false // Wenn zu lange gehalten, kein Tap mehr
                                        Log.d("Interaction", "Finger held too long, no longer potential tap.")
                                    }

                                    // --- Verarbeitung je nach Zustand ---
                                    if (currentPointer.pressed) {
                                        // Finger ist immer noch auf dem Bildschirm (Move/Still)
                                        lastInteractionTime = System.currentTimeMillis() // Update Interaktionszeit während Drag
                                        // Berechne neues Ziel für Pupillen-Offset
                                        if (canvasCenter != Offset.Zero) {
                                            val currentPosition = currentPointer.position
                                            val vectorX = currentPosition.x - canvasCenter.x
                                            val vectorY = currentPosition.y - eyeCenterY
                                            val dist = sqrt(vectorX * vectorX + vectorY * vectorY)
                                            val scale = if (dist > 1e-6f) min(1f, PUPIL_MAX_OFFSET_PIXELS_TOUCH / dist) else 0f
                                            val newTarget = Offset(vectorX * scale, vectorY * scale)
                                            // Nur aktualisieren, wenn sich das Ziel merklich ändert (verhindert unnötige Recomposition)
                                            if ((newTarget - targetPupilOffset).getDistanceSquared() > 0.1f) {
                                                targetPupilOffset = newTarget
                                                // Log.v("Interaction", "New touch target: $targetPupilOffset") // Verbose logging for touch movement
                                            }
                                        }
                                        // Konsumiere das Event, wenn der Finger bewegt wurde (verhindert z.B. Scrollen der System-UI)
                                        if (fingerMoved) currentPointer.consume()

                                    } else {
                                        // Finger wurde losgelassen (Up-Event)
                                        isTouched = false // Touch-Zustand beenden
                                        lastInteractionTime = System.currentTimeMillis() // Letzte Interaktionszeit merken
                                        Log.d("Interaction", "Touch ended. Potential tap: $potentialTap, Moved: $fingerMoved")

                                        // Prüfe, ob es ein gültiger Tap war
                                        if (potentialTap && !fingerMoved) {
                                            showStatsScreen = !showStatsScreen // Stats-Screen ein-/ausblenden
                                            Log.d("Interaction", "Tap detected, toggling stats screen to: $showStatsScreen")
                                        } else {
                                            Log.d("Interaction", "Drag/Hold finished.")
                                        }
                                        // Wichtig: Konsumiere das Up-Event, um die Geste abzuschließen
                                        currentPointer.consume()
                                        break // Beende die Drag-Schleife
                                    }
                                } else {
                                    // Pointer nicht mehr gefunden (sollte selten passieren)
                                    isTouched = false
                                    lastInteractionTime = System.currentTimeMillis()
                                    Log.w("Interaction", "Pointer $pointerId lost during gesture.")
                                    break
                                }
                                // Optional: Alle Events konsumieren, kann aber andere Gesten stören
                                // event.changes.forEach { it.consume() }

                            } while (currentPointer?.pressed == true) // Loop while pointer is pressed
                        } finally {
                            // Sicherstellen, dass isTouched zurückgesetzt wird, auch wenn eine Exception auftritt
                            if (isTouched) {
                                isTouched = false
                                lastInteractionTime = System.currentTimeMillis()
                                Log.w("Interaction", "Gesture ended unexpectedly, resetting touch state.")
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center // Inhalt zentrieren
    ) {
        // Zeige entweder den Stats-Screen oder das Smiley-Gesicht
        if (showStatsScreen) {
            StatsScreen(
                hunger = hunger.value,
                thirst = thirst.value,
                fatigue = fatigue.value,
                onFeed = ::feedPet,
                onDrink = ::waterPet,
                onRest = ::restPet,
                // Schließen-Button setzt showStatsScreen auf false und zählt als Interaktion
                onClose = { showStatsScreen = false; interact() }
            )
        } else {
            SmileyFaceDrawing(
                blinkProgress = blinkProgress.value,
                // Kombiniere normalen Offset und Excitement/Wiggle-Wobble für die Pupillenposition
                pupilOffset = pupilOffset.value + excitementPupilWobble.value,
                // Kombiniere normale Mundöffnung und Excitement-Öffnung
                mouthOpenness = (mouthOpenness.value + excitementMouthOpenness.value).coerceIn(0f, 1f),
                currentPupilSizeFactor = currentPupilSizeFactor, // Dynamische Pupillengröße
                fatigueDroopFactor = fatigueFactor, // Faktor für hängende Lider
                hunger = hunger.value, // Für Hunger-Icon
                thirst = thirst.value, // Für Durst-Icon & Schweiß
                fatigue = fatigue.value, // Für Müdigkeits-Icon
                isSleeping = isSleeping, // Für spezielle Darstellung im Schlaf (z.B. Zzz)
                badStatsFactor = badStatsFactor, // Für Icons basierend auf schlechten Stats
                // Callback, um Geometrie-Daten vom Canvas zu erhalten
                onMeasured = { center, eyeY, eyeRad ->
                    // Nur einmal initial setzen oder wenn sich die Größe ändert (z.e. bei Geräte-Rotation, selten bei Wear)
                    if (canvasCenter == Offset.Zero || eyeRad != eyeRadiusPx) {
                        canvasCenter = center
                        eyeCenterY = eyeY
                        eyeRadiusPx = eyeRad
                        Log.d("Layout", "Canvas measured: Center=$center, EyeY=$eyeY, EyeRadius=$eyeRad")
                    }
                },
                modifier = Modifier.fillMaxSize() // Smiley füllt den verfügbaren Platz
            )
        }
    }
}
@Composable
fun StatsScreen(
    hunger: Float,
    thirst: Float,
    fatigue: Float,
    onFeed: () -> Unit,
    onDrink: () -> Unit,
    onRest: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)), // Halbdurchsichtiger Hintergrund
        contentAlignment = Alignment.Center
    ) {
// ScalingLazyColumn ist optimiert für runde Wear OS Bildschirme
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
// Padding anpassen für runde Displays
                .padding(horizontal = 15.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                space = 8.dp, // Mehr Abstand zwischen Items
                alignment = Alignment.Top
            ),
            autoCentering = AutoCenteringParams(itemIndex = 0) // Erstes Item (Titel) zentrieren
        ) {
// Titel
            item {
                Text(
                    "Stats",
                    style = MaterialTheme.typography.title3, // Passende Typographie für Wear
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp) // Abstand nach Titel
                )
            }
// Stat-Anzeigen
            item { StatDisplay("Hunger", hunger, onFeed, "Feed") }
            item { StatDisplay("Thirst", thirst, onDrink, "Drink") }
            item { StatDisplay("Fatigue", fatigue, onRest, "Rest") }

            // Spacer vor dem Button
            item { Spacer(modifier = Modifier.height(12.dp)) }

            // Titel für andere Sachen
            item{
                Text(
                    "Other",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }


            item{Spacer(modifier = Modifier.height(8.dp))}

            item{Button(onClick = {}, modifier = Modifier.size(width = 110.dp, height = 40.dp)) {
              Text("More infos ")
            }}

            item{Button(onClick = {}, modifier = Modifier.size(width = 110.dp, height = 40.dp)) {
                Text("Support")
            }}

            // Schließen Button
            item {
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.secondaryButtonColors(), // Sekundäre Farbe für Schließen
                    modifier = Modifier.size(width = 110.dp, height = 40.dp) // Etwas größerer Button
                ) {
                    Text("Close", style = MaterialTheme.typography.button)
                }
            }
        }
    }
}
@Composable
fun StatDisplay(
    label: String,
    value: Float,
    onAction: () -> Unit,
    actionLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween // Sorgt für Abstand
    ) {
// Label (nimmt verfügbaren Platz links)
        Text(
            label,
            style = MaterialTheme.typography.body2, // Kleinere Schrift für Stats
            modifier = Modifier.weight(1.5f) // Mehr Platz für Label
        )
// Fortschrittsbalken (mittig)
        LinearProgressIndicator(
            progress = {  // Verwendung von androidx.compose.material3
                value // Korrekter Parametername in M3
            },
            modifier = Modifier
                .weight(3f) // Mehr Platz für Balken
                .height(8.dp) // Etwas dicker
                .padding(horizontal = 8.dp), // Abstand zu Label und Button
            color = androidx.compose.ui.graphics.lerp(Color.Green, Color.Red, value * 1.2f), // Interpoliert Farbe (Rot wird schneller erreicht)
            trackColor = Color.DarkGray.copy(alpha = 0.8f), // Dunklerer Hintergrund
            strokeCap = StrokeCap.Round, // Runde Enden
        )
// Action Button (rechts)
        Button(
            onClick = onAction,
            modifier = Modifier
                .weight(1.5f) // Mehr Platz für Button
// Mindestgröße für gute Klickbarkeit auf Wear OS
                .defaultMinSize(minWidth = 50.dp, minHeight = 36.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = MaterialTheme.colors.primary, // Primärfarbe für Aktionen
                contentColor = MaterialTheme.colors.onPrimary
            ),
        ) {
            Text(
                actionLabel,
                fontSize = 11.sp, // Kleinere, aber lesbare Schrift
                textAlign = TextAlign.Center // Text im Button zentrieren
            )
        }
    }
}
@Composable
fun SmileyFaceDrawing(
    blinkProgress: Float,        // Aktueller Blinzel-Fortschritt (0=offen, 1=geschlossen)
    pupilOffset: Offset,         // Kombinierter Pupillen-Offset (Sakkade/Touch + Excitement/Wiggle)
    mouthOpenness: Float,        // Kombinierte Mundöffnung (Atmen/Touch + Excitement + Sigh + Feed/Drink)
    currentPupilSizeFactor: Float, // Aktueller Faktor für Pupillengröße (abhängig von Hunger)
    fatigueDroopFactor: Float,   // Faktor für hängende Augenlider (0-1)
    hunger: Float,               // Aktueller Hungerwert (0-1)
    thirst: Float,               // Aktueller Durstwert (0-1)
    fatigue: Float,              // Aktueller Müdigkeitswert (0-1)
    isSleeping: Boolean,         // Ob das Pet gerade schläft
    badStatsFactor: Float,       // Faktor für schlechte Stats (0-1) - für Icons
    onMeasured: (center: Offset, eyeCenterY: Float, eyeRadius: Float) -> Unit, // Callback für Layout-Daten
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer() // Für Textrendering (Zzz-Icon)
    val density = LocalDensity.current
    Canvas(modifier = modifier) { // Zeichenbereich
        val canvasWidth = size.width
        val canvasHeight = size.height
        val center = Offset(canvasWidth / 2, canvasHeight / 2)

        // Augen-Geometrie berechnen
        val eyeRadius = canvasWidth * 0.13f // Augenradius relativ zur Canvas-Breite
        val eyeCenterY = canvasHeight * 0.3f // Y-Position der Augenmitte
        val eyeOffsetX = canvasWidth * 0.24f // Horizontaler Abstand der Augen vom Zentrum
        val leftEyeCenter = Offset(center.x - eyeOffsetX, eyeCenterY)
        val rightEyeCenter = Offset(center.x + eyeOffsetX, eyeCenterY)

        // Basis-Pupillengröße basierend auf Hungerfaktor
        val basePupilRadius = eyeRadius * currentPupilSizeFactor
        // Pupillengröße während des Blinzelns leicht reduzieren (wirkt natürlicher)
        val animatedPupilRadius = basePupilRadius * (1f - blinkProgress * 0.5f)

        // Layout-Daten zurückgeben
        onMeasured(center, eyeCenterY, eyeRadius)

        // Augen zeichnen
        drawEye(this, leftEyeCenter, eyeRadius, animatedPupilRadius.coerceAtLeast(0.5f), pupilOffset, blinkProgress, fatigueDroopFactor)
        drawEye(this, rightEyeCenter, eyeRadius, animatedPupilRadius.coerceAtLeast(0.5f), pupilOffset, blinkProgress, fatigueDroopFactor)

        // Mund zeichnen (nur wenn nicht schlafend oder die Schlaf-Animation noch nicht fertig ist)
        val mouthBaseCenterY = center.y + canvasHeight * 0.15f // Basis-Y-Position des Mundes
        // Prüfe, ob der Mund weit genug geöffnet ist, um sichtbar zu sein, ODER ob das Pet gerade aufwacht (mouthOpenness animiert von geschlossen)
        val isMouthVisiblyOpen = mouthOpenness > MOUTH_CLOSED_FACTOR + 0.01f
        val isWakingUp = isSleeping && mouthOpenness > MOUTH_CLOSED_FACTOR + 0.01f // Simplified check for waking up animation

        if (!isSleeping || isWakingUp || isMouthVisiblyOpen) {
            drawAnimatedMouth(
                drawScope = this,
                centerX = center.x,
                baseCenterY = mouthBaseCenterY,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                openness = mouthOpenness // Übergibt die kombinierte Öffnung
            )
        }


        // --- Status Icons zeichnen ---
        val statusIconSize = eyeRadius * 0.35f // Etwas größer für Icons
        val statusIconY = eyeCenterY - eyeRadius * 1.7f // Weiter oben
        val statusIconSpacing = canvasWidth * 0.18f // Mehr Abstand zwischen Icons
        val statusIconStrokeWidth = 1.5.dp.toPx()

        // Icons nur zeichnen, wenn nicht schlafend
        if (!isSleeping) {
            // Hunger Icon (leerer gelber Kreis)
            if (hunger >= HUNGER_THRESHOLD) {
                drawCircle(
                    color = Color.Yellow,
                    radius = statusIconSize,
                    center = Offset(center.x - statusIconSpacing, statusIconY),
                    style = Stroke(width = statusIconStrokeWidth) // Nur Umriss
                )
            }

            // Durst Icon (voller cyanfarbener Kreis) & Schweißtropfen
            if (thirst >= THIRST_THRESHOLD) {
                drawCircle(
                    color = Color.Cyan,
                    radius = statusIconSize,
                    center = Offset(center.x, statusIconY) // Mittiges Icon
                )
                // Schweißtropfen an den Schläfen zeichnen
                drawSweatDrops(this, center, canvasWidth, eyeRadius)
            }
        }

        // Müdigkeits Icon (Zzz) - wird auch im Schlaf gezeichnet!
        // Nur zeichnen, wenn Müdigkeit über Schwelle ODER das Pet schläft
        if (fatigue >= FATIGUE_THRESHOLD || isSleeping) {
            // Calculate the position for the Zzz icon
            // Position rechts oben, adjust slightly based on icon size and desired padding
            val zzzIconX = center.x + statusIconSpacing - statusIconSize * FATIGUE_ICON_SIZE_MULTIPLIER * 0.5f // Adjust X based on text width guess
            val zzzIconY = statusIconY - statusIconSize * FATIGUE_ICON_SIZE_MULTIPLIER * 0.7f // Adjust Y relative to status icons
            val zzzPosition = Offset(zzzIconX, zzzIconY)

            // Measure text to get accurate size/positioning
            val textLayoutResult = textMeasurer.measure(
                text = "Zzz",
                style = TextStyle(fontSize = (statusIconSize * FATIGUE_ICON_SIZE_MULTIPLIER).sp) // Use target font size for measurement
            )

            // Adjust drawing position based on measured text size to center it around zzzPosition
            val actualZzzDrawPosition = Offset(
                zzzPosition.x - textLayoutResult.size.width / 2f,
                zzzPosition.y - textLayoutResult.size.height / 2f
            )

            drawText(
                textMeasurer = textMeasurer,
                text = "Zzz",
                topLeft = actualZzzDrawPosition, // Use calculated position
                style = TextStyle(
                    color = Color.LightGray.copy(alpha = if(isSleeping) 0.9f else 0.7f), // Im Schlaf etwas sichtbarer
                    fontSize = (statusIconSize * FATIGUE_ICON_SIZE_MULTIPLIER).sp, // Angepasste Größe
                    textAlign = TextAlign.Center // TextMeasurer positioning handles centering differently, but keep this for consistency
                )
            )
        }
    }
}
// --- Hilfsfunktion zum Zeichnen der Schweißtropfen ---
fun drawSweatDrops(
    drawScope: DrawScope,
    canvasCenter: Offset, // Mitte des Canvas
    canvasWidth: Float,   // Breite des Canvas
    eyeRadius: Float      // Radius der Augen (als Referenzgröße)
) {
    drawScope.apply {
        val dropRadius = eyeRadius * SWEAT_DROP_RADIUS_FACTOR // Basisradius der Tropfen
        val templeOffsetX = canvasWidth * 0.33f // Horizontaler Abstand von der Mitte (Schläfenbereich)
        val templeOffsetY = -eyeRadius * 0.6f   // Vertikaler Offset nach oben
// Tropfen 1 (linke Schläfe)
        drawCircle(
            color = SWEAT_DROP_COLOR,
            radius = dropRadius,
            center = Offset(canvasCenter.x - templeOffsetX, canvasCenter.y + templeOffsetY)
        )
        // Tropfen 2 (rechte Schläfe, leicht versetzt und kleiner)
        drawCircle(
            color = SWEAT_DROP_COLOR,
            radius = dropRadius * 0.85f, // Etwas kleiner
            center = Offset(canvasCenter.x + templeOffsetX * 0.95f, canvasCenter.y + templeOffsetY * 1.2f) // Leicht andere Position
        )
    }
}
// --- Funktion zum Zeichnen eines Auges (mit Müdigkeitseffekt) ---
fun drawEye(
    drawScope: DrawScope,     // Der DrawScope zum Zeichnen
    center: Offset,           // Zentrum des Auges
    radius: Float,            // Radius des Augapfels
    pupilRadius: Float,       // Aktueller Radius der Pupille (kann animiert sein)
    pupilOffset: Offset,      // Aktueller Offset der Pupille (kombiniert)
    blinkProgress: Float,     // Fortschritt der Blinzelanimation (0=offen, 1=geschlossen)
    fatigueDroopFactor: Float // Faktor für hängende Lider (0=normal, 1=max. müde)
) {
    drawScope.apply {
// 1. Weißer Augapfel zeichnen
        drawCircle(color = Color.White, radius = radius, center = center)
// 2. Pupille zeichnen
        // Begrenze den Pupillen-Offset, damit die Pupille innerhalb des Augapfels bleibt
        val maxDist = (radius - pupilRadius).coerceAtLeast(0f) // Max. möglicher Abstand vom Zentrum
        val currentOffsetMagnitudeSq = pupilOffset.getDistanceSquared() // Quadrat der aktuellen Distanz
        val limitedOffset = if (maxDist <= 1e-6f) { // Wenn Pupille so groß wie Auge, kein Offset
            Offset.Zero
        } else {
            // Wenn Offset größer als max. Distanz, skaliere ihn herunter
            if (currentOffsetMagnitudeSq > maxDist * maxDist && currentOffsetMagnitudeSq > 1e-9f) {
                pupilOffset * (maxDist / sqrt(currentOffsetMagnitudeSq))
            } else {
                pupilOffset // Ansonsten verwende den gegebenen Offset
            }
        }
        val pupilCenter = center + limitedOffset // Endgültige Position der Pupillenmitte

        // Zeichne Pupille nur, wenn Auge nicht fast komplett geschlossen ist (verhindert Zeichnen unter Lid)
        if (blinkProgress < 0.95f) {
            drawCircle(color = Color.Black, radius = pupilRadius, center = pupilCenter)
        }

        // 3. Müdes Augenlid (hängendes Lid oben)
        // Nur zeichnen, wenn müde UND das Auge gerade NICHT blinzelt ODER nur ein bisschen blinzelt
        // Zeichne den Droop auch leicht während des Blinzelns, bis es fast geschlossen ist.
        if (fatigueDroopFactor > 0.01f && blinkProgress < 0.8f) { // Blend out droop as blink progresses
            // Interpolate droop height based on fatigue and blend it with blink progress
            val baseDroopHeight = fatigueDroopFactor * radius * MAX_DROOP_FACTOR * 2f
            // Reduce droop height as eye closes due to blink
            val animatedDroopHeight = baseDroopHeight * (1f - blinkProgress * 0.8f).coerceIn(0f, 1f) // Blend out faster than blink
            val lidTop = center.y - radius // Oberkante des Auges
            // Rechteck zeichnen, das den oberen Teil des Auges überdeckt
            drawRect(
                color = Color.Black, // Gleiche Farbe wie Blinzel-Lid
                topLeft = Offset(center.x - radius - 0.5f, lidTop - 0.5f), // Beginnt leicht über dem Auge
                size = Size(radius * 2 + 1f, animatedDroopHeight + 0.5f) // Breite des Auges, Höhe basiert auf Müdigkeit und Blinzeln
            )
        }


        // 4. Augenlid für die Blinzel-Animation
        if (blinkProgress > 0.01f) { // Nur zeichnen, wenn Blinzeln begonnen hat
            // Höhe des Lids interpolieren von 0 bis zur vollen Augenhöhe
            val lidHeight = lerp(0f, radius * 2 + 1f, blinkProgress)
            val lidTop = center.y - radius // Startet am oberen Rand des Auges
            // Rechteck für das Lid zeichnen
            drawRect(
                color = Color.Black,
                topLeft = Offset(center.x - radius - 0.5f, lidTop), // X-Position am linken Rand
                size = Size(radius * 2 + 1f, lidHeight) // Breite des Auges, Höhe animiert
            )
        }
    }
}
// --- Funktion zum Zeichnen des animierten Mundes ---
fun drawAnimatedMouth(
    drawScope: DrawScope,     // Der DrawScope zum Zeichnen
    centerX: Float,           // X-Koordinate der Mundmitte
    baseCenterY: Float,       // Basis-Y-Koordinate der Mundmitte
    canvasWidth: Float,       // Breite des Canvas
    canvasHeight: Float,      // Höhe des Canvas
    openness: Float           // Aktueller Öffnungsgrad des Mundes (0=geschlossen, 1=max offen)
) {
// Code ist unverändert zur vorherigen Version, da die Logik hier nur vom openness-Wert abhängt.
    drawScope.apply {
// Konstanten für Mundform
        val mouthWidthRatio = 0.75f
        val openCornerHeightRatio = 0.15f
        val openBottomDepthRatio = 0.4f
        val openTopDipRatio = 0.06f
        val cornerRadiusRatio = 0.07f
// Absolute Dimensionen berechnen
        val mouthWidth = canvasWidth * mouthWidthRatio
        val openCornerHeight = canvasHeight * openCornerHeightRatio
        val openBottomDepth = canvasHeight * openBottomDepthRatio
        val openTopDip = canvasHeight * openTopDipRatio
        val cornerRadius = canvasWidth * cornerRadiusRatio

        // Y-Positionen der Kurvenpunkte basierend auf 'openness' interpolieren
        val mouthTopY = lerp(baseCenterY - cornerRadius * 0.5f, baseCenterY - openCornerHeight / 2f, openness)
        val topCurveLowestY = lerp(mouthTopY + cornerRadius * 0.1f, mouthTopY + openTopDip, openness)
        val mouthBottomYCurve = lerp(mouthTopY + cornerRadius, baseCenterY + openBottomDepth, openness)
        val cornerBottomY = mouthTopY + cornerRadius // Y-Position der unteren Ecke der Rundung

        // X-Positionen der Mundränder
        val mouthLeftX = centerX - mouthWidth / 2
        val mouthRightX = centerX + mouthWidth / 2

        // Zahn-Dimensionen und Position (wird nur gezeichnet, wenn Mund etwas offen ist)
        val toothWidth = canvasWidth * 0.075f
        val toothHeight = canvasWidth * 0.1f * lerp(0.5f, 1f, openness) // Zahn wird größer bei Öffnung
        val toothStartX = centerX + canvasWidth * 0.08f // Leicht rechts von der Mitte
        // Zahn-Oberkante relativ zur maximal geöffneten Mundposition verankern
        val toothTopAnchorY = baseCenterY - openCornerHeight / 2f // Oberkante bei max Öffnung
        // Tatsächliche obere Zahnposition bewegt sich nach oben beim Öffnen
        val toothActualTopY = lerp(toothTopAnchorY + toothHeight * 0.5f, toothTopAnchorY, openness)

        // Pfad für den Mund erstellen
        val mouthPath = Path().apply {
            moveTo(mouthLeftX + cornerRadius, mouthTopY) // Start nach der linken oberen Rundung
            // Obere Kurve (kubische Bezier-Kurve)
            cubicTo(centerX - mouthWidth * 0.25f, topCurveLowestY, centerX + mouthWidth * 0.25f, topCurveLowestY, mouthRightX - cornerRadius, mouthTopY)
            // Rechte Rundung (Bogen von oben nach unten)
            arcTo(Rect(mouthRightX - 2 * cornerRadius, mouthTopY, mouthRightX, mouthTopY + 2 * cornerRadius), 270f, 90f, false)
            // Untere Kurve (kubische Bezier-Kurve)
            cubicTo(mouthRightX - mouthWidth * 0.18f, mouthBottomYCurve, mouthLeftX + mouthWidth * 0.18f, mouthBottomYCurve, mouthLeftX, cornerBottomY) // Endet an der linken unteren Ecke der Rundung
            // Linke Rundung (Bogen von unten nach oben)
            arcTo(Rect(mouthLeftX, mouthTopY, mouthLeftX + 2 * cornerRadius, mouthTopY + 2 * cornerRadius), 180f, 90f, false)
            close() // Schließt den Pfad zurück zum Startpunkt
        }

        // Mundfläche zeichnen (weiße Füllung)
        drawPath(path = mouthPath, color = Color.White)

        // Zahn zeichnen, wenn der Mund ausreichend geöffnet ist und der Zahn eine sichtbare Höhe hat
        if (openness > MOUTH_CLOSED_FACTOR + 0.05f && toothHeight > 1f) {
            drawRect(
                color = Color.Black, // Schwarzer Zahn
                topLeft = Offset(toothStartX, toothActualTopY),
                size = Size(toothWidth, toothHeight)
            )
        }
    }
}
// --- Preview-Funktionen ---
// Zeigen Vorschaubilder in Android Studio für verschiedene Wear OS Geräte
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true, showBackground = true)
@Composable
fun WearAppPreviewRoundInteractive() {
// Übergib null, da SensorManager in der Preview nicht benötigt wird
    WearApp(null)
}
@Preview(device = WearDevices.SQUARE, showSystemUi = true, showBackground = true)
@Composable
fun WearAppPreviewSquareInteractive() {
    WearApp(null)
}
// Eigene Preview für den Stats Screen
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, showBackground = true)
@Composable
fun StatsScreenPreview() {
    MaterialTheme { // Theme für die Preview
        StatsScreen(
            hunger = 0.75f, // Beispielwerte für die Vorschau
            thirst = 0.3f,
            fatigue = 0.9f,
            onFeed = {}, // Leere Lambda-Funktionen für die Aktionen
            onDrink = {},
            onRest = {},
            onClose = {}
        )
    }
}
// Preview mit hoher Müdigkeit
@Preview(device = WearDevices.LARGE_ROUND, name = "Fatigued State", showSystemUi = true)
@Composable
fun WearAppPreviewFatigued() {
    MaterialTheme {
// Erstelle einen Fake-Zustand für die Preview
        val previewHunger = remember { mutableStateOf(0.2f) }
        val previewThirst = remember { mutableStateOf(0.4f) }
        val previewFatigue = remember { mutableStateOf(0.95f) } // Hohe Müdigkeit
        val previewBlink = remember { Animatable(0f) }
        val previewPupilOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val previewMouth = remember { Animatable(BREATHING_MIN_OPENNESS) }
        val previewPupilFactor by remember { derivedStateOf { NORMAL_PUPIL_SIZE_FACTOR } } // Vereinfacht
        val previewDroopFactor by remember { derivedStateOf { 1f } } // Voller Droop
        val previewBadStats = remember { derivedStateOf { 1f } }

        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            SmileyFaceDrawing(
                blinkProgress = previewBlink.value,
                pupilOffset = previewPupilOffset.value,
                mouthOpenness = previewMouth.value,
                currentPupilSizeFactor = previewPupilFactor,
                fatigueDroopFactor = previewDroopFactor,
                hunger = previewHunger.value,
                thirst = previewThirst.value,
                fatigue = previewFatigue.value,
                isSleeping = false,
                badStatsFactor = previewBadStats.value,
                onMeasured = { _, _, _ -> },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
// Preview mit hohem Durst
@Preview(device = WearDevices.LARGE_ROUND, name = "Thirsty State", showSystemUi = true)
@Composable
fun WearAppPreviewThirst() {
    MaterialTheme {
// Erstelle einen Fake-Zustand für die Preview
        val previewHunger = remember { mutableStateOf(0.2f) }
        val previewThirst = remember { mutableStateOf(0.9f) }
        val previewFatigue = remember { mutableStateOf(0.0f) } // Hohe Müdigkeit
        val previewBlink = remember { Animatable(0f) }
        val previewPupilOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val previewMouth = remember { Animatable(BREATHING_MIN_OPENNESS) }
        val previewPupilFactor by remember { derivedStateOf { NORMAL_PUPIL_SIZE_FACTOR } } // Vereinfacht
        val previewDroopFactor by remember { derivedStateOf { 0f } } // Voller Droop
        val previewBadStats = remember { derivedStateOf { 1f } }

        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            SmileyFaceDrawing(
                blinkProgress = previewBlink.value,
                pupilOffset = previewPupilOffset.value,
                mouthOpenness = previewMouth.value,
                currentPupilSizeFactor = previewPupilFactor,
                fatigueDroopFactor = previewDroopFactor,
                hunger = previewHunger.value,
                thirst = previewThirst.value,
                fatigue = previewFatigue.value,
                isSleeping = false,
                badStatsFactor = previewBadStats.value,
                onMeasured = { _, _, _ -> },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// Preview mit hohem Durst
@Preview(device = WearDevices.LARGE_ROUND, name = "Hungry State", showSystemUi = true)
@Composable
fun WearAppPreviewHunger() {
    MaterialTheme {
// Erstelle einen Fake-Zustand für die Preview
        val previewHunger = remember { mutableStateOf(0.9f) }
        val previewThirst = remember { mutableStateOf(0.0f) }
        val previewFatigue = remember { mutableStateOf(0.0f) } // Hohe Müdigkeit
        val previewBlink = remember { Animatable(0f) }
        val previewPupilOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val previewMouth = remember { Animatable(BREATHING_MIN_OPENNESS) }
        val previewPupilFactor by remember { derivedStateOf { HUNGRY_PUPIL_SIZE_FACTOR } } // Vereinfacht
        val previewDroopFactor by remember { derivedStateOf { 0f } } // Voller Droop
        val previewBadStats = remember { derivedStateOf { 1f } }
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            SmileyFaceDrawing(
                blinkProgress = previewBlink.value,
                pupilOffset = previewPupilOffset.value,
                mouthOpenness = previewMouth.value,
                currentPupilSizeFactor = previewPupilFactor,
                fatigueDroopFactor = previewDroopFactor,
                hunger = previewHunger.value,
                thirst = previewThirst.value,
                fatigue = previewFatigue.value,
                isSleeping = false,
                badStatsFactor = previewBadStats.value,
                onMeasured = { _, _, _ -> },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}