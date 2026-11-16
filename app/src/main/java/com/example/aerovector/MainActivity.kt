package com.example.aerovector

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private data class FlightState(
    var thrust: Int = 0,
    var lift: Int = 0,
    var drag: Int = 0,
    var weight: Int = 120,
    var brake: Float = 0.6f,
    var speed: Float = 0f,
    var distance: Float = 0f,
    var altitude: Float = 0f,
    var acceleration: Float = 0f,
    var phase: String = "rest",
    var showLiftWeight: Boolean = true,
    var showThrustDrag: Boolean = true,
    var showGround: Boolean = true,
    var showDynamic: Boolean = true,
    var zoom: Float = 1f,
    var cameraAngle: Float = 0f
) {
    fun normalForce() = if (altitude <= 0.05f) max(0, weight - lift) else 0
    fun frictionKn() = if (altitude <= 0.05f && (speed > 0.1f || thrust > 0))
        min(thrust * 0.15f + 8f, normalForce() * brake) else 0f
    fun netForce() {
        val fx = thrust - drag - frictionKn()
        val fy = lift + normalForce() - weight
        // Deliberately returned in kN, as the displayed force is a resultant.
        resultant = sqrt(fx * fx + fy * fy)
    }
    var resultant: Float = 0f
}

class MainActivity : AppCompatActivity() {
    private val flight = FlightState()
    private lateinit var scene: FlightSceneView
    private lateinit var graph: HistoryGraphView
    private lateinit var speedValue: TextView
    private lateinit var altitudeValue: TextView
    private lateinit var distanceValue: TextView
    private lateinit var accelerationValue: TextView
    private lateinit var netValue: TextView
    private lateinit var normalValue: TextView
    private lateinit var frictionValue: TextView
    private lateinit var angleValue: TextView
    private val history = ArrayDeque<Float>()
    private var lastTick = 0L
    private var immersive = false

    private val tick = object : Runnable {
        override fun run() {
            val now = android.os.SystemClock.elapsedRealtime()
            val dt = if (lastTick == 0L) 0f else min((now - lastTick) / 1000f, 0.08f)
            lastTick = now
            simulate(dt)
            scene.invalidate()
            graph.invalidate()
            refreshMetrics()
            scene.postDelayed(this, 50)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        buildScreen()
        applyPhase("rest")
        scene.post(tick)
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(5, 8, 24))
            isFillViewport = true
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(24))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(20, 25, 59), Color.rgb(3, 5, 15))
            )
        }
        scroll.addView(page)
        setContentView(scroll)

        val header = TextView(this).apply {
            text = "✈  AeroVector 3D Studio\n     Dynamic Flight & Ground Vector Kinematics"
            setTextColor(Color.WHITE)
            textSize = 19f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(14), dp(16), dp(14), dp(16))
            background = cardBackground()
        }
        page.addView(header, matchWrap())

        val sceneHeader = sectionTitle("3D FLIGHT VIEW · DRAG TO ORBIT · PINCH TO ZOOM")
        page.addView(sceneHeader)
        scene = FlightSceneView(this, flight)
        page.addView(scene, LinearLayout.LayoutParams(-1, dp(290)).apply { topMargin = dp(8) })

        val sceneActions = horizontalRow()
        sceneActions.addView(actionButton("Camera / Vectors") { showSettings() }, weighted())
        sceneActions.addView(actionButton("Fullscreen") { toggleImmersive() }, weighted().apply { leftMargin = dp(8) })
        page.addView(sceneActions)

        page.addView(sectionTitle("FLIGHT PHASE"))
        val phases = horizontalRow()
        phases.addView(actionButton("🛑 Rest") { applyPhase("rest") }, weighted())
        phases.addView(actionButton("🛫 Flight") { applyPhase("flight") }, weighted().apply { leftMargin = dp(5) })
        phases.addView(actionButton("🛬 Land") { applyPhase("landing") }, weighted().apply { leftMargin = dp(5) })
        page.addView(phases)

        page.addView(sectionTitle("AERODYNAMIC & ENGINE CONTROLS"))
        val controls = cardColumn()
        addSlider(controls, "Thrust (T)", 0, 250, flight.thrust, Color.rgb(48, 209, 88)) { flight.thrust = it }
        addSlider(controls, "Lift (L)", 0, 250, flight.lift, Color.rgb(10, 132, 255)) { flight.lift = it }
        addSlider(controls, "Drag (D)", 0, 250, flight.drag, Color.rgb(255, 159, 10)) { flight.drag = it }
        addSlider(controls, "Weight (W)", 50, 250, flight.weight, Color.rgb(255, 69, 58)) { flight.weight = it }
        addSlider(controls, "Wheel brakes / friction", 0, 100, (flight.brake * 100).toInt(), Color.rgb(255, 214, 10)) { flight.brake = it / 100f }
        page.addView(controls)

        page.addView(sectionTitle("LIVE TELEMETRY"))
        val metrics = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row1 = horizontalRow()
        val net = metricCard("RESULTANT NET FORCE", "0.0 kN", Color.rgb(191, 90, 242))
        netValue = net.second
        val angle = metricCard("FLIGHT VECTOR ANGLE", "0.0°", Color.WHITE)
        angleValue = angle.second
        row1.addView(net.first, weighted())
        row1.addView(angle.first, weighted().apply { leftMargin = dp(8) })
        metrics.addView(row1)
        val row2 = horizontalRow()
        val speed = metricCard("AIRSPEED", "0 km/h", Color.rgb(50, 215, 75))
        speedValue = speed.second
        val alt = metricCard("ALTITUDE (AGL)", "0.0 m", Color.rgb(100, 210, 255))
        altitudeValue = alt.second
        row2.addView(speed.first, weighted())
        row2.addView(alt.first, weighted().apply { leftMargin = dp(8) })
        metrics.addView(row2)
        val row3 = horizontalRow()
        val dist = metricCard("DISPLACEMENT", "0.0 m", Color.rgb(100, 210, 255))
        distanceValue = dist.second
        val acc = metricCard("ACCELERATION", "0.00 m/s²", Color.rgb(191, 90, 242))
        accelerationValue = acc.second
        row3.addView(dist.first, weighted())
        row3.addView(acc.first, weighted().apply { leftMargin = dp(8) })
        metrics.addView(row3)
        page.addView(metrics)

        page.addView(sectionTitle("KINEMATICS · RECENT HISTORY"))
        graph = HistoryGraphView(this, history)
        page.addView(graph, LinearLayout.LayoutParams(-1, dp(190)).apply { topMargin = dp(8) })

        val groundRow = horizontalRow()
        val normal = metricCard("NORMAL FORCE", "120 kN", Color.rgb(50, 215, 75))
        normalValue = normal.second
        val friction = metricCard("BRAKING FRICTION", "0 kN", Color.rgb(255, 214, 10))
        frictionValue = friction.second
        groundRow.addView(normal.first, weighted())
        groundRow.addView(friction.first, weighted().apply { leftMargin = dp(8) })
        page.addView(groundRow)
    }

    private fun addSlider(parent: LinearLayout, label: String, minValue: Int, maxValue: Int, initial: Int, color: Int, changed: (Int) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(6))
        }
        val text = TextView(this).apply {
            setTextColor(color); textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val bar = SeekBar(this).apply {
            max = maxValue - minValue
            progress = initial - minValue
            progressTintList = android.content.res.ColorStateList.valueOf(color)
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }
        text.text = "$label: $initial kN"
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + minValue
                text.text = if (label.contains("brakes")) "$label: $value%" else "$label: $value kN"
                changed(value)
                scene.invalidate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        row.addView(text)
        row.addView(bar)
        parent.addView(row)
    }

    private fun simulate(dt: Float) {
        if (dt <= 0f) return
        val onGround = flight.altitude <= 0.05f
        val normal = if (onGround) max(0, flight.weight - flight.lift) else 0
        val friction = if (onGround && (flight.speed > .1f || flight.thrust > 0))
            min(flight.thrust * .15f + 8f, normal * flight.brake) else 0f
        val ax = (flight.thrust - flight.drag - friction) * 1000f / 12240f
        var ay = ((flight.lift + normal - flight.weight) * 1000f / 12240f)
        if (onGround && ay < 0) ay = 0f
        flight.acceleration = ax
        flight.speed = max(0f, flight.speed + ax * dt)
        flight.distance += flight.speed * dt
        flight.altitude = min(25f, max(0f, flight.altitude + ay * dt * 2.8f))
        flight.netForce()
        if (history.size >= 75) history.removeFirst()
        history.addLast(flight.speed)
    }

    private fun refreshMetrics() {
        val normal = flight.normalForce()
        val friction = flight.frictionKn()
        val angle = Math.toDegrees(atan2((flight.lift - flight.weight).toDouble(), (flight.thrust - flight.drag).toDouble())).toFloat()
        speedValue.text = "${(flight.speed * 3.6f).toInt()} km/h"
        altitudeValue.text = "${(flight.altitude * 10f).let { String.format("%.1f", it) }} m"
        distanceValue.text = "${String.format("%.1f", flight.distance)} m"
        accelerationValue.text = "${if (flight.acceleration >= 0) "+" else ""}${String.format("%.2f", flight.acceleration)} m/s²"
        netValue.text = "${String.format("%.1f", flight.resultant)} kN"
        normalValue.text = "$normal kN"
        frictionValue.text = "${String.format("%.0f", friction)} kN"
        angleValue.text = "${String.format("%.1f", angle)}°"
    }

    private fun applyPhase(phase: String) {
        flight.phase = phase
        when (phase) {
            "rest" -> {
                flight.thrust = 0; flight.lift = 0; flight.drag = 0; flight.weight = 120
                flight.brake = .6f; flight.speed = 0f; flight.altitude = 0f
            }
            "flight" -> {
                flight.thrust = 160; flight.drag = 120; flight.lift = 145; flight.weight = 120
                flight.brake = .1f
            }
            else -> {
                flight.thrust = 25; flight.drag = 100; flight.lift = 90; flight.weight = 120
                flight.brake = .45f
            }
        }
        // Refresh slider values to reflect preset values.
        buildScreen()
        scene.post(tick)
    }

    private fun showSettings() {
        val items = arrayOf("Toggle lift & weight vectors", "Toggle thrust & drag vectors", "Toggle ground reaction vectors", "Toggle net force & velocity vectors", "Zoom in", "Zoom out", "Reset camera")
        AlertDialog.Builder(this).setTitle("Camera and vector settings").setItems(items) { _, which ->
            when (which) {
                0 -> flight.showLiftWeight = !flight.showLiftWeight
                1 -> flight.showThrustDrag = !flight.showThrustDrag
                2 -> flight.showGround = !flight.showGround
                3 -> flight.showDynamic = !flight.showDynamic
                4 -> flight.zoom = max(.65f, flight.zoom - .12f)
                5 -> flight.zoom = min(1.8f, flight.zoom + .12f)
                else -> { flight.zoom = 1f; flight.cameraAngle = 0f }
            }
            scene.invalidate()
        }.setNegativeButton("Close", null).show()
    }

    private fun toggleImmersive() {
        immersive = !immersive
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (immersive) (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            ) else View.SYSTEM_UI_FLAG_VISIBLE
    }

    override fun onDestroy() {
        scene.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun metricCard(label: String, value: String, color: Int): Pair<LinearLayout, TextView> {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(11), dp(11), dp(8), dp(11)); background = cardBackground()
        }
        val title = TextView(this).apply { text = label; textSize = 10f; setTextColor(Color.LTGRAY) }
        val number = TextView(this).apply { text = value; textSize = 17f; setTextColor(color); setTypeface(typeface, android.graphics.Typeface.BOLD) }
        box.addView(title); box.addView(number)
        return box to number
    }

    private fun sectionTitle(title: String) = TextView(this).apply {
        text = title; textSize = 12f; setTextColor(Color.rgb(190, 208, 255)); setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(dp(3), dp(16), dp(3), dp(2))
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 12f; setTextColor(Color.WHITE); setOnClickListener { action() }
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(36, 48, 88))
        minHeight = dp(44); isAllCaps = false
    }

    private fun cardColumn() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = cardBackground(); setPadding(dp(10), dp(4), dp(10), dp(8))
    }
    private fun horizontalRow() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
    private fun cardBackground() = android.graphics.drawable.GradientDrawable().apply {
        setColor(Color.argb(225, 21, 28, 57)); cornerRadius = dp(16).toFloat(); setStroke(dp(1), Color.argb(90, 190, 210, 255))
    }
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    private fun weighted() = LinearLayout.LayoutParams(0, -2, 1f)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

private class FlightSceneView(context: android.content.Context, private val model: FlightState) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, Color.rgb(13, 25, 51), Color.rgb(35, 18, 48), android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f, 0f, w, h, 22f, 22f, paint)
        paint.shader = null
        // Stylized cloud shapes
        paint.color = Color.argb(105, 235, 244, 255)
        for (i in 0..4) {
            val x = ((i * 137 + model.distance * 2) % (w + 130)) - 60
            canvas.drawCircle(x, h * (.22f + (i % 3) * .1f), 17f + (i % 2) * 5, paint)
            canvas.drawCircle(x + 18, h * (.22f + (i % 3) * .1f) - 6, 14f, paint)
        }
        val groundY = h * .77f - model.altitude * 2.2f
        paint.color = Color.rgb(29, 34, 57)
        canvas.drawRect(0f, h * .77f, w, h, paint)
        paint.color = Color.argb(170, 240, 244, 255)
        val drift = (model.distance * 16f) % 120f
        for (i in -1..8) canvas.drawRoundRect(i * 120f - drift, h * .88f, i * 120f + 45f - drift, h * .895f, 4f, 4f, paint)

        val centerX = w * .5f
        val centerY = groundY - 23f
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(model.cameraAngle)
        canvas.scale(model.zoom, model.zoom)
        drawPlane(canvas)
        canvas.restore()
        drawVectors(canvas, centerX, centerY)
        paint.color = Color.WHITE; paint.textSize = 12f; paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        canvas.drawText("Drag to orbit  ·  pinch/zoom via settings", 12f, h - 12f, paint)
    }

    private fun drawPlane(canvas: Canvas) {
        // Slightly perspective-styled airplane assembled from native Canvas paths.
        val fuselage = Path().apply { moveTo(-88f, -7f); quadTo(-65f, -19f, 48f, -10f); lineTo(82f, 0f); lineTo(48f, 11f); quadTo(-65f, 19f, -88f, 7f); close() }
        paint.color = Color.rgb(239, 244, 255); canvas.drawPath(fuselage, paint)
        val wing = Path().apply { moveTo(-26f, -5f); lineTo(-3f, -57f); lineTo(16f, -57f); lineTo(5f, -5f); lineTo(16f, 57f); lineTo(-3f, 57f); close() }
        paint.color = Color.rgb(10, 132, 255); canvas.drawPath(wing, paint)
        val tail = Path().apply { moveTo(-71f, -4f); lineTo(-82f, -30f); lineTo(-68f, -27f); lineTo(-54f, -4f); lineTo(-68f, 27f); lineTo(-82f, 30f); close() }
        paint.color = Color.rgb(255, 69, 58); canvas.drawPath(tail, paint)
        paint.color = Color.rgb(100, 210, 255); canvas.drawOval(RectF(32f, -7f, 57f, 4f), paint)
        paint.color = Color.rgb(255, 214, 10); canvas.strokeWidth = 3f
        canvas.drawLine(87f, -13f, 87f, 13f, paint); canvas.drawLine(80f, 0f, 94f, 0f, paint)
        paint.color = Color.DKGRAY; canvas.strokeWidth = 3f
        canvas.drawLine(18f, 8f, 18f, 25f, paint); canvas.drawLine(-43f, 9f, -43f, 25f, paint)
        paint.color = Color.BLACK; canvas.drawCircle(18f, 27f, 5f, paint); canvas.drawCircle(-43f, 27f, 5f, paint)
    }

    private fun drawVectors(canvas: Canvas, x: Float, y: Float) {
        val normal = model.normalForce().toFloat(); val friction = model.frictionKn()
        if (model.showLiftWeight) {
            arrow(canvas, x - 15f, y - 10f, x - 15f, y - 30f - model.lift * .45f, Color.rgb(10, 132, 255), "L ${model.lift}")
            arrow(canvas, x + 20f, y + 8f, x + 20f, y + 30f + model.weight * .35f, Color.rgb(255, 69, 58), "W ${model.weight}")
        }
        if (model.showThrustDrag) {
            arrow(canvas, x + 72f, y - 9f, x + 118f + model.thrust * .35f, y - 9f, Color.rgb(48, 209, 88), "T ${model.thrust}")
            arrow(canvas, x - 72f, y + 11f, x - 108f - model.drag * .25f, y + 11f, Color.rgb(255, 159, 10), "D ${model.drag}")
        }
        if (model.showGround && model.altitude <= .05f) {
            arrow(canvas, x + 28f, y + 30f, x + 28f, y - 5f - normal * .25f, Color.rgb(50, 215, 75), "N")
            if (friction > .5f) arrow(canvas, x + 35f, y + 30f, x - 1f, y + 30f, Color.rgb(255, 214, 10), "f")
        }
        if (model.showDynamic) {
            val fx = model.thrust - model.drag - friction
            val fy = model.lift + normal - model.weight
            val magnitude = max(1f, sqrt((fx * fx + fy * fy).toFloat()))
            arrow(canvas, x, y - 72f, x + fx / magnitude * 48f, y - 72f - fy / magnitude * 48f, Color.rgb(191, 90, 242), "Net")
        }
    }

    private fun arrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, label: String) {
        paint.color = color; paint.strokeWidth = 3.5f; paint.style = Paint.Style.STROKE
        canvas.drawLine(x1, y1, x2, y2, paint)
        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()).toFloat()
        val head = Path().apply {
            moveTo(x2, y2)
            lineTo(x2 - 11f * cos(angle - .45f), y2 - 11f * sin(angle - .45f))
            lineTo(x2 - 11f * cos(angle + .45f), y2 - 11f * sin(angle + .45f))
            close()
        }
        paint.style = Paint.Style.FILL; canvas.drawPath(head, paint)
        paint.textSize = 11f; paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        canvas.drawText(label, x2 + 4f, y2 - 3f, paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; dragging = true; return true }
            MotionEvent.ACTION_MOVE -> if (dragging && event.pointerCount == 1) {
                model.cameraAngle += (event.x - lastX) * .008f
                lastX = event.x; lastY = event.y; invalidate(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragging = false; performClick(); return true }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}

private class HistoryGraphView(context: android.content.Context, private val data: ArrayDeque<Float>) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        paint.color = Color.rgb(9, 12, 27); canvas.drawRoundRect(0f, 0f, w, h, 18f, 18f, paint)
        val left = 48f; val top = 14f; val right = w - 12f; val bottom = h - 25f
        paint.color = Color.argb(48, 255, 255, 255); paint.strokeWidth = 1f
        for (i in 0..4) {
            val y = top + (bottom - top) * i / 4f
            canvas.drawLine(left, y, right, y, paint)
        }
        paint.color = Color.LTGRAY; paint.textSize = 10f
        canvas.drawText("v (m/s)", 8f, 13f, paint)
        canvas.drawText("0", 17f, bottom, paint)
        canvas.drawText("time →", right - 35f, h - 5f, paint)
        if (data.size < 2) return
        val maxV = max(20f, data.maxOrNull() ?: 20f)
        val path = Path()
        data.forEachIndexed { index, value ->
            val x = left + (right - left) * index / max(1, data.size - 1)
            val y = bottom - (bottom - top) * (value / maxV).coerceIn(0f, 1f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.color = Color.rgb(100, 210, 255); paint.strokeWidth = 3f; paint.style = Paint.Style.STROKE
        canvas.drawPath(path, paint); paint.style = Paint.Style.FILL
        paint.textSize = 10f; canvas.drawText("${String.format("%.0f", maxV)} m/s", 8f, top + 12f, paint)
    }
}
