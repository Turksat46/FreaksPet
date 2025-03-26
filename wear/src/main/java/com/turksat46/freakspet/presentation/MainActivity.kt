package com.turksat46.freakspet.presentation // Ersetze dies mit deinem Paketnamen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.util.lerp
import androidx.wear.compose.material.MaterialTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// --- Konstanten ---
private const val BLINK_INTERVAL_MIN_MS = 2500L
private const val BLINK_INTERVAL_MAX_MS = 7000L
private const val BLINK_CLOSE_DURATION_MS = 100
private const val BLINK_PAUSE_MS = 60L
private const val BLINK_OPEN_DURATION_MS = 150

private const val SACCADE_INTERVAL_MIN_MS = 800L // Etwas länger für ruhigere Augen
private const val SACCADE_INTERVAL_MAX_MS = 3000L
private const val SACCADE_DURATION_MS = 350 // Etwas langsamer
private const val PUPIL_MAX_OFFSET_FACTOR = 0.4f // Faktor des Augenradius für Sakkaden
private const val TOUCH_FOLLOW_DURATION_MS = 90 // Etwas langsamer für sanfteres Folgen
private const val PUPIL_MAX_OFFSET_PIXELS_TOUCH = 15f // Max Offset in Pixeln für Touch-Folgen

private const val MOUTH_IDLE_CLOSE_DELAY_MS = 4000L
private const val MOUTH_CLOSE_DURATION_MS = 450
private const val MOUTH_OPEN_DURATION_MS = 250
private const val MOUTH_CLOSED_FACTOR = 0.05f // Wie "geschlossen" der Mund ist (0=Linie, 1=offen)


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    MaterialTheme {
        SmileyFaceInteractive()
    }
}

@Composable
fun SmileyFaceInteractive() {
    // --- Zustände ---
    val blinkProgress = remember { Animatable(0f) }
    val pupilOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val mouthOpenness = remember { Animatable(1f) } // 0.0 = geschlossen, 1.0 = offen
    var isTouched by remember { mutableStateOf(false) }
    // Ziel-Offset für die Pupille (relativ zum Augenzentrum)
    var targetPupilOffset by remember { mutableStateOf(Offset.Zero) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Scope für Koroutinen, die nicht direkt in LaunchedEffect sind (z.B. aus pointerInput)
    val scope = rememberCoroutineScope()
    var mouthCloseJob by remember { mutableStateOf<Job?>(null) }

    // Ungefähre Geometrie-Daten vom Canvas für externe Berechnungen
    var canvasCenter by remember { mutableStateOf(Offset.Zero) }
    var eyeCenterY by remember { mutableStateOf(0f) }
    var eyeRadiusPx by remember { mutableStateOf(0f) } // Tatsächlicher Augenradius in Pixel

    // --- Effekt für Mund öffnen/schließen basierend auf Touch und Idle-Zeit ---
    LaunchedEffect(isTouched) {
        if (isTouched) {
            // Bei Berührung: Idle-Timer abbrechen, Mund öffnen
            mouthCloseJob?.cancel()
            mouthCloseJob = null
            lastInteractionTime = System.currentTimeMillis() // Interaktionszeit aktualisieren
            if (mouthOpenness.value < 1f) { // Nur animieren, wenn nicht schon offen
                // Starte im Scope des LaunchedEffect
                launch { mouthOpenness.animateTo(1f, tween(MOUTH_OPEN_DURATION_MS)) }
            }
        } else {
            // Wenn nicht berührt: Starte Timer zum Schließen
            /*mouthCloseJob = launch { // Starte neuen Schließ-Job mit Delay
                delay(MOUTH_IDLE_CLOSE_DELAY_MS)
                // Nach Delay prüfen, ob immer noch nicht berührt
                if (!isTouched) {
                    mouthOpenness.animateTo(MOUTH_CLOSED_FACTOR, tween(MOUTH_CLOSE_DURATION_MS))
                }
            }

             */
        }
    }

    // --- Effekt für Augen Blinzeln ---
    LaunchedEffect(Unit) {
        while (true) {
            val blinkDelay = Random.nextLong(BLINK_INTERVAL_MIN_MS, BLINK_INTERVAL_MAX_MS)
            delay(blinkDelay)
            // Nicht blinzeln, wenn gerade berührt wird oder Mund fast zu ist
            if (!isTouched && mouthOpenness.value > MOUTH_CLOSED_FACTOR + 0.1f) {
                // Animationen innerhalb des LaunchedEffect Scopes
                launch { blinkProgress.animateTo(3f, tween(BLINK_CLOSE_DURATION_MS, easing = LinearEasing)) }
                delay(BLINK_PAUSE_MS)
                launch { blinkProgress.animateTo(0f, tween(BLINK_OPEN_DURATION_MS, easing = LinearEasing)) }
            }
        }
    }

    // --- Effekt für Augen Sakkaden & Touch-Folgen ---
    LaunchedEffect(isTouched, targetPupilOffset) { // Reagiert auf Touch-Status und Zieländerung
        if (isTouched) {
            // Wenn berührt, animiere zum aktuellen *relativen* targetPupilOffset
            // Dieses Ziel wurde bereits im pointerInput berechnet und limitiert
            launch { pupilOffset.animateTo(targetPupilOffset, tween(TOUCH_FOLLOW_DURATION_MS)) }
        } else {
            // Wenn nicht berührt:
            // 1. Animiere sanft zur Mitte zurück (falls nicht schon dort)
            if (pupilOffset.value.getDistanceSquared() > 1e-6f) {
                // Diese Animation kann durch eine Sakkade unterbrochen werden
                launch { pupilOffset.animateTo(Offset.Zero, tween(SACCADE_DURATION_MS * 2)) }
            }
            // 2. Starte zufällige Sakkaden (nur wenn eyeRadius bekannt ist)
            if (eyeRadiusPx > 0f) {
                while (true) { // Diese Schleife läuft nur, wenn isTouched false ist
                    val saccadeDelay = Random.nextLong(SACCADE_INTERVAL_MIN_MS, SACCADE_INTERVAL_MAX_MS)
                    delay(saccadeDelay)
                    // Wichtige Prüfung: Ist isTouched *immer noch* false?
                    if (isTouched) break // Beende Sakkaden sofort, wenn zwischenzeitlich berührt wurde

                    // Berechne zufälliges Sakkadenziel (relativer Offset)
                    val maxSaccadeOffset = eyeRadiusPx * PUPIL_MAX_OFFSET_FACTOR // Nutze tatsächlichen Radius
                    val randomAngle = Random.nextFloat() * 2 * Math.PI.toFloat()
                    val randomRadius = Random.nextFloat() * maxSaccadeOffset
                    val saccadeTarget = Offset(
                        cos(randomAngle) * randomRadius,
                        sin(randomAngle) * randomRadius * 0.7f // Vertikal etwas weniger
                    )
                    // Animiere zum Sakkadenziel
                    // Diese Animation wird durch die nächste Iteration oder durch isTouched=true unterbrochen
                    launch { pupilOffset.animateTo(saccadeTarget, tween(SACCADE_DURATION_MS)) }
                }
            }
        }
    }

    // --- UI ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                forEachGesture {
                    awaitPointerEventScope {
                        val down = awaitFirstDown()
                        isTouched = true // Löst LaunchedEffect(isTouched, ...) aus
                        lastInteractionTime = System.currentTimeMillis()

                        // --- Berechne initialen *relativen* Offset für Touch ---
                        if (canvasCenter != Offset.Zero) { // Nur wenn Geometrie bekannt
                            val initialTouchPosition = down.position
                            val initialVectorX = initialTouchPosition.x - canvasCenter.x
                            // Verwende gespeicherte Augen-Y-Position
                            val initialVectorY = initialTouchPosition.y - eyeCenterY
                            val initialDist = kotlin.math.sqrt(initialVectorX * initialVectorX + initialVectorY * initialVectorY)
                            // Skaliere/Limitiere den Vektor basierend auf Pixel-Konstante
                            val initialScale = if (initialDist > 1e-6f) min(1f, PUPIL_MAX_OFFSET_PIXELS_TOUCH / initialDist) else 0f
                            // Setze das Ziel-State, was den LaunchedEffect triggert
                            targetPupilOffset = Offset(initialVectorX * initialScale, initialVectorY * initialScale)
                        }
                        // --- ---

                        do {
                            val event = awaitPointerEvent()
                            // Finde den Pointer, der ursprünglich gedrückt wurde
                            val currentPointer = event.changes.firstOrNull { it.id == down.id }

                            if (currentPointer != null && currentPointer.pressed) {
                                if (isTouched && canvasCenter != Offset.Zero) { // Safety check & Geometrie-Check
                                    lastInteractionTime = System.currentTimeMillis() // Update bei Drag

                                    // --- Berechne *relativen* Offset während des Drags ---
                                    val currentPosition = currentPointer.position
                                    val vectorX = currentPosition.x - canvasCenter.x
                                    val vectorY = currentPosition.y - eyeCenterY
                                    val dist = kotlin.math.sqrt(vectorX * vectorX + vectorY * vectorY)
                                    // Skaliere/Limitiere den Vektor
                                    val scale = if (dist > 1e-6f) min(1f, PUPIL_MAX_OFFSET_PIXELS_TOUCH / dist) else 0f
                                    val newTarget = Offset(vectorX * scale, vectorY * scale)
                                    // --- ---

                                    // Aktualisiere den Ziel-State *nur wenn geändert*, um LaunchedEffect zu triggern
                                    if (newTarget != targetPupilOffset) {
                                        targetPupilOffset = newTarget
                                    }
                                }
                            } else {
                                // Finger wurde losgelassen ODER es ist nicht mehr der ursprüngliche Pointer
                                if (currentPointer?.id == down.id) { // Nur wenn der ursprüngliche Finger losgelassen wurde
                                    isTouched = false // Löst LaunchedEffect(isTouched, ...) aus -> Sakkaden/Zurück zur Mitte
                                }
                                break // Beende die Drag-Schleife
                            }
                        } while (true) // Schleife wird durch break verlassen

                        // Sicherstellen, dass Touch-Status korrekt ist, falls Schleife unerwartet endet
                        if (isTouched) {
                            isTouched = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        SmileyFaceDrawing(
            blinkProgress = blinkProgress.value,
            pupilOffsetAnimatable = pupilOffset, // Wird direkt im Canvas gelesen
            isTouched = isTouched, // Evtl. für zukünftige Logik
            mouthOpenness = mouthOpenness.value,
            // Callback, um Canvas-Geometrie einmalig nach außen zu geben
            onMeasured = { center, eyeY, eyeRad ->
                if (canvasCenter == Offset.Zero) { // Nur einmal setzen
                    canvasCenter = center
                    eyeCenterY = eyeY
                    eyeRadiusPx = eyeRad // Speichere tatsächlichen Radius
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun SmileyFaceDrawing(
    blinkProgress: Float,
    pupilOffsetAnimatable: Animatable<Offset, *>,
    isTouched: Boolean,
    mouthOpenness: Float,
    onMeasured: (center: Offset, eyeCenterY: Float, eyeRadius: Float) -> Unit, // Callback mit Radius
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val center = Offset(canvasWidth / 2, canvasHeight / 2)

        // --- Augen-Geometrie ---
        val eyeRadius = canvasWidth * 0.13f // Relative Größe
        val eyeCenterY = canvasHeight * 0.3f
        val eyeOffsetX = canvasWidth * 0.24f
        val leftEyeCenter = Offset(center.x - eyeOffsetX, eyeCenterY)
        val rightEyeCenter = Offset(center.x + eyeOffsetX, eyeCenterY)
        val pupilRadius = eyeRadius * 0.40f

        // Geometrie nach außen geben (einmalig oder bei Größenänderung)
        onMeasured(center, eyeCenterY, eyeRadius)

        // --- Pupillen Offset ---
        // Lese den *aktuell animierten* Wert direkt aus dem Animatable
        val actualPupilOffset = pupilOffsetAnimatable.value

        // --- Augen zeichnen ---
        drawEye(this, leftEyeCenter, eyeRadius, pupilRadius, actualPupilOffset, blinkProgress)
        drawEye(this, rightEyeCenter, eyeRadius, pupilRadius, actualPupilOffset, blinkProgress)

        // --- Mund ---
        val mouthBaseCenterY = center.y + canvasHeight * 0.10f
        drawAnimatedMouth(
            drawScope = this,
            centerX = center.x,
            baseCenterY = mouthBaseCenterY,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            openness = mouthOpenness
        )
    }
}

// --- drawEye Funktion ---
fun drawEye(
    drawScope: DrawScope,
    center: Offset,
    radius: Float,
    pupilRadius: Float,
    pupilOffset: Offset, // Wird jetzt direkt verwendet
    blinkProgress: Float
) {
    drawScope.apply {
        // 1. Weißer Augapfel
        drawCircle(color = Color.White, radius = radius, center = center)

        // 2. Pupille (Offset vom Zentrum) - Limitiere den Offset hier sicherheitshalber
        val distanceSquared = pupilOffset.getDistanceSquared() // Effizienter als getDistance()
        val maxDist = radius - pupilRadius
        val maxDistSquared = maxDist * maxDist

        val limitedOffset = if (distanceSquared > maxDistSquared && distanceSquared > 1e-9f) {
            // Berechne Skalierungsfaktor nur wenn nötig
            val distance = kotlin.math.sqrt(distanceSquared)
            pupilOffset * (maxDist / distance)
        } else {
            pupilOffset
        }
        val pupilCenter = center + limitedOffset
        drawCircle(color = Color.Black, radius = pupilRadius, center = pupilCenter)

        // 3. Augenlid (animiert)
        if (blinkProgress > 0.01f) {
            val lidHeight = radius * 2 * blinkProgress
            val lidRect = Rect(center.x - radius, center.y - radius, center.x + radius, center.y - radius + lidHeight)
            drawArc(color = Color.Black, startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = lidRect.topLeft, size = lidRect.size)
        }
    }
}


// --- drawAnimatedMouth Funktion ---
fun drawAnimatedMouth(
    drawScope: DrawScope,
    centerX: Float,
    baseCenterY: Float, // Y-Position, um die sich der Mund zentriert
    canvasWidth: Float,
    canvasHeight: Float,
    openness: Float // 0.0 (geschlossen) bis 1.0 (offen)
) {
    drawScope.apply {
        // --- Grundverhältnisse (für offenen Zustand) ---
        val mouthWidthRatio = 0.75f
        val openCornerHeightRatio = 0.15f // Höhe der "Ecke" bei offenem Mund
        val openBottomDepthRatio = 0.4f  // Tiefe der unteren Kurve bei offenem Mund (relativ zur Höhe)
        val openTopDipRatio = 0.06f     // Tiefe der oberen Kurve bei offenem Mund
        val cornerRadiusRatio = 0.07f   // Größerer Radius für weichere Ecken

        // --- Maße für offenen Zustand ---
        val mouthWidth = canvasWidth * mouthWidthRatio
        val openCornerHeight = canvasHeight * openCornerHeightRatio
        val openBottomDepth = canvasHeight * openBottomDepthRatio
        val openTopDip = canvasHeight * openTopDipRatio
        val cornerRadius = canvasWidth * cornerRadiusRatio

        // --- Y-Koordinaten interpolieren ---
        // Mundwinkel-Höhe (oben): Interpoliert zwischen fast geschlossen und offen
        val mouthTopY = lerp(baseCenterY - cornerRadius * 0.5f, baseCenterY - openCornerHeight / 2f, openness)
        // Tiefster Punkt der oberen Kurve: Interpoliert
        val topCurveLowestY = lerp(mouthTopY + cornerRadius * 0.1f, mouthTopY + openTopDip, openness)
        // Tiefster Punkt der unteren Kurve: Interpoliert
        val mouthBottomYCurve = lerp(mouthTopY + cornerRadius, baseCenterY + openBottomDepth, openness)
        // Y-Position der unteren Ecke (relevant für Arcs), relativ zu mouthTopY
        val cornerBottomY = mouthTopY + cornerRadius

        // X-Koordinaten der Mundränder
        val mouthLeftX = centerX - mouthWidth / 2
        val mouthRightX = centerX + mouthWidth / 2

        // --- Zahn (Position bleibt relativ zu den offenen Mundwinkeln) ---
        val toothWidth = canvasWidth * 0.075f
        val toothHeight = canvasWidth * 0.1f * lerp(0.5f, 1f, openness) // Zahn wird kleiner beim Schließen
        val toothStartX = centerX + canvasWidth * 0.08f
        val toothEndX = toothStartX + toothWidth
        // Zahn-Oberkante: Bleibt an der *offenen* Mundwinkelhöhe, um nicht mitzuwandern
        val toothTopAnchorY = baseCenterY - openCornerHeight / 2f
        // Lässt Zahn leicht nach oben verschwinden beim Schließen
        val toothActualTopY = lerp(toothTopAnchorY + toothHeight * 0.5f, toothTopAnchorY, openness)
        val toothActualBottomY = toothActualTopY + toothHeight

        // --- Pfad mit Kubischen Bezierkurven für mehr Glättung ---
        val mouthPath = Path().apply {
            // Startpunkt: Nach linker oberer Rundung
            moveTo(mouthLeftX + cornerRadius, mouthTopY)

            // Obere Kurve (Cubic Bezier)
            // Kontrollpunkte etwas nach außen/oben für breiteres Lächeln oben
            val topCp1X = centerX - mouthWidth * 0.25f
            val topCp1Y = topCurveLowestY
            val topCp2X = centerX + mouthWidth * 0.25f
            val topCp2Y = topCurveLowestY
            cubicTo(
                x1 = topCp1X, y1 = topCp1Y, // CP1
                x2 = topCp2X, y2 = topCp2Y, // CP2
                x3 = mouthRightX - cornerRadius, y3 = mouthTopY // Endpunkt vor rechter Rundung
            )

            // Obere rechte Ecke (Rundung)
            arcTo(
                rect = Rect(mouthRightX - 2 * cornerRadius, mouthTopY, mouthRightX, mouthTopY + 2 * cornerRadius),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            ) // Endet bei (mouthRightX, mouthTopY + cornerRadius) -> jetzt cornerBottomY

            // Untere Kurve (Cubic Bezier)
            // Kontrollpunkte näher an den Ecken für vollere untere Kurve
            val bottomCp1X = mouthRightX - mouthWidth * 0.18f
            val bottomCp1Y = mouthBottomYCurve
            val bottomCp2X = mouthLeftX + mouthWidth * 0.18f
            val bottomCp2Y = mouthBottomYCurve
            cubicTo(
                x1 = bottomCp1X, y1 = bottomCp1Y, // CP1
                x2 = bottomCp2X, y2 = bottomCp2Y, // CP2
                x3 = mouthLeftX, y3 = cornerBottomY // Endpunkt: Beginn der linken unteren Rundung
            )

            // Untere linke Ecke (Rundung)
            arcTo(
                rect = Rect(mouthLeftX, mouthTopY, mouthLeftX + 2 * cornerRadius, mouthTopY + 2 * cornerRadius),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            ) // Endet bei (mouthLeftX + cornerRadius, mouthTopY)

            close() // Schließt zum moveTo-Punkt
        }

        // 1. Mundpfad zeichnen (gefüllt)
        drawPath(path = mouthPath, color = Color.White)

        // 2. Zahn überzeichnen (nur wenn Mund etwas offen ist)
        if (openness > MOUTH_CLOSED_FACTOR + 0.05f && toothHeight > 1f) { // Nur zeichnen wenn sichtbar
            drawRect(
                color = Color.Black, // Hintergrundfarbe
                topLeft = Offset(toothStartX, toothActualTopY), // Verwende animierte Y-Position
                size = Size(toothWidth, toothHeight) // Verwende animierte Höhe
            )
        }
    }
}


// --- Preview ---
@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun WearAppPreviewRoundInteractive() {
    WearApp()
}

@Preview(device = Devices.WEAR_OS_SQUARE, showSystemUi = true)
@Composable
fun WearAppPreviewSquareInteractive() {
    WearApp()
}

// Hinweis: Stelle sicher, dass die lerp-Funktion verfügbar ist (aus androidx.compose.ui.util.lerp)
// import androidx.compose.ui.util.lerp