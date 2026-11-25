package com.bohdan.horror

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class HudView(ctx: Context, private val holder: GameHolder) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.WHITE }
    private val rect = RectF()

    private val ROLE_NONE = 0; private val ROLE_JOY = 1; private val ROLE_LOOK = 2
    private val ROLE_E = 3; private val ROLE_F = 4; private val ROLE_C = 5; private val ROLE_T = 6; private val ROLE_PHONE = 7
    private val roles = HashMap<Int, Int>()
    private val lastX = HashMap<Int, Float>()
    private val lastY = HashMap<Int, Float>()

    private fun joyCx() = width * 0.13f
    private fun joyCy() = height * 0.72f
    private fun joyR() = height * 0.2f
    private fun eX() = width * 0.90f; private fun eY() = height * 0.40f; private fun eR() = height * 0.11f
    private fun tX() = width * 0.86f; private fun tY() = height * 0.72f; private fun tR() = height * 0.15f
    private fun fX() = width * 0.60f; private fun fY() = height * 0.85f; private fun fR() = height * 0.09f
    private fun cX() = width * 0.72f; private fun cY() = height * 0.85f; private fun cR() = height * 0.09f

    private fun hit(x: Float, y: Float, cx: Float, cy: Float, r: Float): Boolean {
        val dx = x - cx; val dy = y - cy
        return dx * dx + dy * dy <= r * r * 1.4f
    }

    // phone layout
    private fun panel(): RectF = RectF(width * 0.26f, height * 0.04f, width * 0.74f, height * 0.96f)
    private fun keyRect(i: Int): RectF {
        val pn = panel()
        val col = i % 3; val row = i / 3
        val kw = (pn.width() - 4 * 14f) / 3f
        val top = pn.top + pn.height() * 0.36f
        val kh = (pn.bottom - top - 5 * 14f) / 4f
        val l = pn.left + 14f + col * (kw + 14f)
        val t = top + 14f + row * (kh + 14f)
        return RectF(l, t, l + kw, t + kh)
    }
    private val keyChars = charArrayOf('1', '2', '3', '4', '5', '6', '7', '8', '9', 'B', '0', 'C')
    private val keyLabels = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "←", "0", "Дзвінок")

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val g = holder.g
        val act = ev.actionMasked
        val idx = ev.actionIndex
        if (g.dead || g.endScreen) {
            if (act == MotionEvent.ACTION_UP) g.wantRestart = true
            return true
        }
        when (act) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val id = ev.getPointerId(idx); val x = ev.getX(idx); val y = ev.getY(idx)
                lastX[id] = x; lastY[id] = y
                var role = ROLE_NONE
                if (g.phoneOpen && panel().contains(x, y)) {
                    for (i in 0 until 12) if (keyRect(i).contains(x, y)) g.phoneKey(keyChars[i])
                    role = ROLE_PHONE
                } else if (g.holding && hit(x, y, tX(), tY(), tR())) { role = ROLE_T; g.throwHeld = true }
                else if (g.prompt != null && hit(x, y, eX(), eY(), eR())) { role = ROLE_E; g.pressE() }
                else if (g.showF && hit(x, y, fX(), fY(), fR())) { role = ROLE_F; g.pressF() }
                else if (g.phoneOpen && hit(x, y, fX(), fY(), fR())) { role = ROLE_F; g.pressF() }
                else if (g.showC && hit(x, y, cX(), cY(), cR())) { role = ROLE_C; g.pressC() }
                else if (x < width * 0.32f && y > height * 0.3f) { role = ROLE_JOY; updateJoy(g, x, y) }
                else role = ROLE_LOOK
                roles[id] = role
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val id = ev.getPointerId(i); val x = ev.getX(i); val y = ev.getY(i)
                    when (roles[id]) {
                        ROLE_JOY -> updateJoy(g, x, y)
                        ROLE_LOOK -> { g.addLook(x - (lastX[id] ?: x), y - (lastY[id] ?: y)) }
                    }
                    lastX[id] = x; lastY[id] = y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val id = ev.getPointerId(idx)
                when (roles[id]) {
                    ROLE_JOY -> { g.moveX = 0f; g.moveY = 0f }
                    ROLE_T -> g.throwHeld = false
                }
                roles.remove(id); lastX.remove(id); lastY.remove(id)
                if (act == MotionEvent.ACTION_CANCEL) { g.moveX = 0f; g.moveY = 0f; g.throwHeld = false }
            }
        }
        return true
    }

    private fun updateJoy(g: Game, x: Float, y: Float) {
        var dx = x - joyCx(); var dy = y - joyCy()
        val r = joyR()
        val len = sqrt(dx * dx + dy * dy)
        if (len > r) { dx = dx / len * r; dy = dy / len * r }
        g.moveX = dx / r          // right on screen = strafe right
        g.moveY = -dy / r         // UP on screen = FORWARD
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int = Color.WHITE, bold: Boolean = true, align: Paint.Align = Paint.Align.CENTER) {
        tp.textSize = size; tp.color = color; tp.isFakeBoldText = bold; tp.textAlign = align
        c.drawText(s, x, y, tp)
        tp.textAlign = Paint.Align.CENTER
    }

    private fun button(c: Canvas, cx: Float, cy: Float, r: Float, label: String, on: Boolean, size: Float) {
        p.style = Paint.Style.FILL; p.color = if (on) Color.argb(150, 220, 220, 230) else Color.argb(90, 120, 120, 130)
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = Color.argb(200, 255, 255, 255)
        c.drawCircle(cx, cy, r, p)
        text(c, label, cx, cy + size * 0.35f, size, Color.WHITE)
    }

    override fun onDraw(c: Canvas) {
        val g = holder.g
        val w = width.toFloat(); val h = height.toFloat()
        if (w < 10f) { postInvalidateOnAnimation(); return }

        // crosshair
        p.style = Paint.Style.FILL; p.color = Color.argb(160, 255, 255, 255)
        c.drawCircle(w / 2, h / 2, 4f, p)

        // melon charge meter
        if (g.holding && g.charging) {
            val col = if (g.charge < 0.55f) Color.rgb(60, 220, 80) else if (g.charge < 0.8f) Color.rgb(240, 220, 40) else Color.rgb(240, 50, 40)
            val r = h * 0.13f
            rect.set(w / 2 - r, h / 2 - r, w / 2 + r, h / 2 + r)
            p.style = Paint.Style.STROKE; p.strokeWidth = h * 0.025f; p.color = Color.argb(70, 255, 255, 255)
            c.drawCircle(w / 2, h / 2, r, p)
            p.color = col
            c.drawArc(rect, -90f, min(1f, g.charge) * 360f, false, p)
        }

        // joystick
        if (!g.dead) {
            p.style = Paint.Style.FILL; p.color = Color.argb(60, 255, 255, 255)
            c.drawCircle(joyCx(), joyCy(), joyR(), p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 4f; p.color = Color.argb(140, 255, 255, 255)
            c.drawCircle(joyCx(), joyCy(), joyR(), p)
            p.style = Paint.Style.FILL; p.color = Color.argb(170, 255, 255, 255)
            c.drawCircle(joyCx() + g.moveX * joyR(), joyCy() - g.moveY * joyR(), joyR() * 0.38f, p)
        }

        // buttons
        if (g.prompt != null) {
            button(c, eX(), eY(), eR(), "E", true, h * 0.07f)
            text(c, g.prompt!!, eX() - eR() * 1.3f, eY() + h * 0.018f, h * 0.04f, Color.WHITE, true, Paint.Align.RIGHT)
        }
        if (g.holding) button(c, tX(), tY(), tR(), "КИНУТИ", g.charging, h * 0.035f)
        if (g.showF || g.phoneOpen) button(c, fX(), fY(), fR(), "F", true, h * 0.06f)
        if (g.showC) button(c, cX(), cY(), cR(), "C", true, h * 0.06f)

        // timer
        if (g.keyTimer >= 0f && g.phase == Phase.KEY) {
            val t = g.keyTimer.toInt() + 1
            text(c, "$t", w / 2, h * 0.09f, h * 0.12f, if (t <= 15) Color.rgb(255, 60, 50) else Color.WHITE)
        }
        if (g.msgT > 0f && g.msg.isNotEmpty()) text(c, g.msg, w / 2, h * 0.2f, h * 0.042f, Color.argb(min(255, (g.msgT * 255).toInt()), 255, 255, 255))

        // phone
        if (g.phoneOpen) {
            val pn = panel()
            p.style = Paint.Style.FILL; p.color = Color.argb(235, 15, 15, 20); c.drawRoundRect(pn, 40f, 40f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.color = Color.argb(255, 90, 90, 110); c.drawRoundRect(pn, 40f, 40f, p)
            text(c, if (g.phoneNum.isEmpty()) "Введи номер" else g.phoneNum, w / 2, pn.top + pn.height() * 0.1f, h * 0.09f, if (g.phoneNum.isEmpty()) Color.GRAY else Color.WHITE)
            var ly = pn.top + pn.height() * 0.17f
            for (ln in g.phoneLines) { text(c, ln, w / 2, ly, h * 0.04f, Color.rgb(150, 230, 160), false); ly += h * 0.055f }
            for (i in 0 until 12) {
                val k = keyRect(i)
                p.style = Paint.Style.FILL
                p.color = if (i == 11) Color.rgb(30, 140, 60) else Color.rgb(45, 45, 58)
                c.drawRoundRect(k, 24f, 24f, p)
                text(c, keyLabels[i], k.centerX(), k.centerY() + h * 0.02f, if (i == 11) h * 0.04f else h * 0.07f)
            }
        }

        // big text, flash, fade
        if (g.bigT > 0f && g.bigText.isNotEmpty()) text(c, g.bigText, w / 2, h * 0.45f, h * 0.15f, Color.argb(min(255, (g.bigT * 255).toInt()), 200, 0, 0))
        if (g.flash > 0.01f) { p.style = Paint.Style.FILL; p.color = Color.argb((min(1f, g.flash) * 255).toInt(), 255, 255, 255); c.drawRect(0f, 0f, w, h, p) }
        if (g.fade > 0.01f) { p.style = Paint.Style.FILL; p.color = Color.argb((min(1f, g.fade) * 255).toInt(), 0, 0, 0); c.drawRect(0f, 0f, w, h, p) }

        if (g.dead) {
            p.style = Paint.Style.FILL; p.color = Color.argb(150, 40, 0, 0); c.drawRect(0f, 0f, w, h, p)
            text(c, "ТЕБЕ СПІЙМАЛИ", w / 2, h * 0.42f, h * 0.10f, Color.rgb(230, 30, 30))
            text(c, g.deadText, w / 2, h * 0.49f, h * 0.045f)
            text(c, "Торкнись екрана, щоб почати знову", w / 2, h * 0.6f, h * 0.04f, Color.LTGRAY, false)
        }
        if (g.endScreen) {
            p.style = Paint.Style.FILL; p.color = Color.BLACK; c.drawRect(0f, 0f, w, h, p)
            text(c, "Wait, Chapter 2", w / 2, h * 0.48f, h * 0.11f, Color.WHITE)
            text(c, "Торкнись екрана, щоб зіграти знову", w / 2, h * 0.9f, h * 0.035f, Color.GRAY, false)
        }
        postInvalidateOnAnimation()
    }
}
