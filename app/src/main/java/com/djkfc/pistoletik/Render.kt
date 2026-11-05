package com.djkfc.pistoletik

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

val GROUND1 = c(0xFF1E2921)
val GROUND2 = c(0xFF212D24)
val WHITE = c(0xFFFFFFFF)
val BLACK = c(0xFF000000)
val SHADOW = c(0x44000000)

fun tileHash(x: Int, y: Int): Int {
    var h = x * 73856093 xor y * 19349663
    h = h xor (h ushr 13); h *= 0x5bd1e995; h = h xor (h ushr 15)
    return h and 0x7fffffff
}

fun Game.drawWorld(cv: Canvas) {
    cv.drawColor(GROUND1)
    cv.save()
    val sx = if (shake > 0f) rf(-shake, shake) else 0f
    val sy = if (shake > 0f) rf(-shake, shake) else 0f
    cv.translate(w / 2f + sx, h / 2f + sy)
    cv.scale(zoom, zoom)
    cv.translate(-camX, -camY)
    val hw = w / 2f / zoom + 60f; val hh = h / 2f / zoom + 60f
    val l = camX - hw; val r = camX + hw; val t = camY - hh; val b = camY + hh

    drawGround(cv, l, t, r, b)

    // вихри
    for (v in vortices) drawVortex(cv, v)
    // сундуки
    for (ch in chests) drawChest(cv, ch)
    // подбираемое
    for (k in pickups) if (k.x in l..r && k.y in t..b) drawPickup(cv, k)
    // тени + враги
    for (e in enemies) if (e.x in l..r && e.y in t..b) drawEnemy(cv, e)
    // липкие бомбы
    for (s in stickies) {
        fill.color = if ((s.t * 10).toInt() % 2 == 0) c(0xFFFF3030) else c(0xFF801010)
        cv.drawCircle(s.e.x + s.e.r * 0.5f, s.e.y - s.e.r * 0.5f, 3.5f, fill)
    }
    drawPlayer(cv)
    for (d in drones) drawDrone(cv, d)
    // пули
    val glow = bullets.size < 260
    for (bl in bullets) if (bl.x in l..r && bl.y in t..b) drawBullet(cv, bl, glow)
    for (bl in ebullets) if (bl.x in l..r && bl.y in t..b) {
        fill.color = withAlpha(bl.color, 70); cv.drawCircle(bl.x, bl.y, bl.size * 1.9f, fill)
        fill.color = bl.color; cv.drawCircle(bl.x, bl.y, bl.size, fill)
        fill.color = c(0xFF400030); cv.drawCircle(bl.x, bl.y, bl.size * 0.45f, fill)
    }
    // лучи / молнии
    for (bm in beams) drawBeam(cv, bm)
    // частицы
    for (pr in particles) {
        val a = (255 * (pr.life / pr.maxLife)).toInt()
        if (pr.ring) {
            stroke.color = withAlpha(pr.color, a); stroke.strokeWidth = 3f
            cv.drawCircle(pr.x, pr.y, pr.size, stroke)
        } else {
            fill.color = withAlpha(pr.color, a)
            cv.drawCircle(pr.x, pr.y, pr.size * (0.4f + 0.6f * pr.life / pr.maxLife), fill)
        }
    }
    // тексты
    for (ft in texts) {
        val a = (255 * min(1f, ft.life * 2.5f)).toInt()
        text.textSize = ft.size
        text.color = withAlpha(BLACK, a / 2)
        cv.drawText(ft.text, ft.x + 1f, ft.y + 1f, text)
        text.color = withAlpha(ft.color, a)
        cv.drawText(ft.text, ft.x, ft.y, text)
    }
    cv.restore()

    if (chronoT > 0f) { fill.color = c(0x223F6FFF); cv.drawRect(0f, 0f, w, h, fill) }
    if (player.hitFlash > 0f) {
        fill.color = withAlpha(c(0xFFFF2020), (player.hitFlash * 280).toInt())
        cv.drawRect(0f, 0f, w, h * 0.03f, fill); cv.drawRect(0f, h * 0.97f, w, h, fill)
        cv.drawRect(0f, 0f, w * 0.04f, h, fill); cv.drawRect(w * 0.96f, 0f, w, h, fill)
    }
}

fun Game.drawGround(cv: Canvas, l: Float, t: Float, r: Float, b: Float) {
    val T = 64f
    val x0 = floor(l / T).toInt(); val x1 = floor(r / T).toInt()
    val y0 = floor(t / T).toInt(); val y1 = floor(b / T).toInt()
    for (ty in y0..y1) for (tx in x0..x1) {
        val px = tx * T; val py = ty * T
        if (((tx + ty) and 1) == 0) { fill.color = GROUND2; cv.drawRect(px, py, px + T, py + T, fill) }
        val hs = tileHash(tx, ty)
        val ox = px + (hs % 40) + 12f; val oy = py + ((hs / 40) % 40) + 12f
        when (hs % 19) {
            0, 1, 2 -> {
                stroke.color = c(0xFF2F4A33); stroke.strokeWidth = 1.6f
                cv.drawLine(ox, oy, ox - 3f, oy - 7f, stroke)
                cv.drawLine(ox + 3f, oy, ox + 3f, oy - 9f, stroke)
                cv.drawLine(ox + 6f, oy, ox + 9f, oy - 6f, stroke)
            }
            3 -> { fill.color = c(0xFF2C332F); cv.drawCircle(ox, oy, 5f, fill); fill.color = c(0xFF353D38); cv.drawCircle(ox - 1f, oy - 1.5f, 3f, fill) }
            4 -> {
                fill.color = if (hs % 2 == 0) c(0xFF6B4E7A) else c(0xFF7A6B3A)
                cv.drawCircle(ox, oy, 2.2f, fill)
                cv.drawCircle(ox + 5f, oy + 3f, 2.2f, fill)
            }
            5 -> { fill.color = c(0xFF26342A); cv.drawOval(ox - 10f, oy - 4f, ox + 10f, oy + 4f, fill) }
            else -> {}
        }
    }
}

fun Game.shadow(cv: Canvas, x: Float, y: Float, r: Float) {
    fill.color = SHADOW
    cv.drawOval(x - r, y + r * 0.6f, x + r, y + r * 1.1f, fill)
}

fun Game.eyes(cv: Canvas, x: Float, y: Float, r: Float, ax: Float, ay: Float, white: Int = WHITE, pupil: Int = c(0xFF101010)) {
    val er = r * 0.28f
    val ex = r * 0.38f
    fill.color = white
    cv.drawCircle(x - ex, y, er, fill); cv.drawCircle(x + ex, y, er, fill)
    fill.color = pupil
    cv.drawCircle(x - ex + ax * er * 0.45f, y + ay * er * 0.45f, er * 0.55f, fill)
    cv.drawCircle(x + ex + ax * er * 0.45f, y + ay * er * 0.45f, er * 0.55f, fill)
}

fun Game.drawEnemy(cv: Canvas, e: Enemy) {
    val p = player
    val dx = p.x - e.x; val dy = p.y - e.y; val d = hypot(dx, dy) + 0.01f
    val ax = dx / d; val ay = dy / d
    val col = if (e.flash > 0f) WHITE else e.type.color
    val r = e.r
    val bob = sin(e.t * 8f) * r * 0.06f
    shadow(cv, e.x, e.y, r)
    // аура элиты
    e.elite?.let {
        fill.color = withAlpha(it.color, 60)
        cv.drawCircle(e.x, e.y, r + 6f + sin(animT * 6f) * 2f, fill)
        stroke.color = it.color; stroke.strokeWidth = 2f
        cv.drawCircle(e.x, e.y, r + 3f, stroke)
    }
    val x = e.x; val y = e.y + bob
    when (e.type) {
        EType.ZOMBIE, EType.MINI -> {
            fill.color = col; cv.drawCircle(x, y, r, fill)
            fill.color = withAlpha(BLACK, 50); cv.drawCircle(x + r * 0.3f, y + r * 0.35f, r * 0.45f, fill)
            eyes(cv, x, y - r * 0.15f, r, ax, ay, c(0xFFFFF4C0))
        }
        EType.BAT -> {
            val flap = sin(e.t * 22f) * r * 0.7f
            fill.color = col
            path.reset()
            path.moveTo(x, y); path.lineTo(x - r * 2.2f, y - flap); path.lineTo(x - r * 1.2f, y + r * 0.5f); path.close()
            path.moveTo(x, y); path.lineTo(x + r * 2.2f, y - flap); path.lineTo(x + r * 1.2f, y + r * 0.5f); path.close()
            cv.drawPath(path, fill)
            cv.drawCircle(x, y, r, fill)
            fill.color = c(0xFFFF4040)
            cv.drawCircle(x - r * 0.35f, y - r * 0.1f, r * 0.2f, fill); cv.drawCircle(x + r * 0.35f, y - r * 0.1f, r * 0.2f, fill)
        }
        EType.BRUTE -> {
            fill.color = c(0xFFE0D0B0)
            path.reset()
            path.moveTo(x - r * 0.7f, y - r * 0.6f); path.lineTo(x - r * 1.0f, y - r * 1.4f); path.lineTo(x - r * 0.3f, y - r * 0.8f); path.close()
            path.moveTo(x + r * 0.7f, y - r * 0.6f); path.lineTo(x + r * 1.0f, y - r * 1.4f); path.lineTo(x + r * 0.3f, y - r * 0.8f); path.close()
            cv.drawPath(path, fill)
            fill.color = col
            rect.set(x - r, y - r * 0.9f, x + r, y + r); cv.drawRoundRect(rect, r * 0.5f, r * 0.5f, fill)
            eyes(cv, x, y - r * 0.2f, r * 0.9f, ax, ay, c(0xFFFFD040))
            fill.color = c(0xFF3A2010); rect.set(x - r * 0.5f, y + r * 0.3f, x + r * 0.5f, y + r * 0.5f); cv.drawRect(rect, fill)
        }
        EType.SPITTER -> {
            fill.color = col; cv.drawCircle(x, y, r, fill)
            stroke.color = col; stroke.strokeWidth = r * 0.5f
            cv.drawLine(x, y, x + ax * r * 1.5f, y + ay * r * 1.5f, stroke)
            fill.color = c(0xFF603010); cv.drawCircle(x + ax * r * 1.5f, y + ay * r * 1.5f, r * 0.25f, fill)
            eyes(cv, x, y - r * 0.35f, r * 0.8f, ax, ay)
        }
        EType.BOMBER -> {
            fill.color = col; cv.drawCircle(x, y, r, fill)
            stroke.color = c(0xFF4A3020); stroke.strokeWidth = 2f
            cv.drawLine(x, y - r, x + r * 0.4f, y - r * 1.6f, stroke)
            fill.color = if ((e.t * 12).toInt() % 2 == 0) c(0xFFFFFF80) else c(0xFFFF4020)
            cv.drawCircle(x + r * 0.4f, y - r * 1.6f, r * 0.3f, fill)
            eyes(cv, x, y, r, ax, ay)
        }
        EType.SPLITTER -> {
            fill.color = withAlpha(col, 220)
            cv.drawCircle(x - r * 0.4f, y + r * 0.2f, r * 0.7f, fill)
            cv.drawCircle(x + r * 0.4f, y + r * 0.2f, r * 0.7f, fill)
            cv.drawCircle(x, y - r * 0.3f, r * 0.75f, fill)
            fill.color = withAlpha(WHITE, 90); cv.drawCircle(x - r * 0.2f, y - r * 0.5f, r * 0.2f, fill)
            eyes(cv, x, y - r * 0.2f, r, ax, ay)
        }
        EType.GHOST -> {
            fill.color = withAlpha(col, 170)
            cv.drawCircle(x, y - r * 0.2f, r, fill)
            path.reset()
            path.moveTo(x - r, y - r * 0.2f); path.lineTo(x - r, y + r)
            val wv = sin(e.t * 6f) * r * 0.2f
            path.lineTo(x - r * 0.5f, y + r * 0.6f + wv); path.lineTo(x, y + r); path.lineTo(x + r * 0.5f, y + r * 0.6f - wv)
            path.lineTo(x + r, y + r); path.lineTo(x + r, y - r * 0.2f); path.close()
            cv.drawPath(path, fill)
            fill.color = c(0xFF202040)
            cv.drawCircle(x - r * 0.35f, y - r * 0.3f, r * 0.22f, fill); cv.drawCircle(x + r * 0.35f, y - r * 0.3f, r * 0.22f, fill)
        }
        EType.GOLEM -> {
            fill.color = c(0xFF5A5A66); rect.set(x - r, y - r * 0.8f, x + r, y + r); cv.drawRoundRect(rect, 8f, 8f, fill)
            fill.color = col; rect.set(x - r * 0.85f, y - r, x + r * 0.85f, y + r * 0.7f); cv.drawRoundRect(rect, 10f, 10f, fill)
            stroke.color = c(0xFF6A6A78); stroke.strokeWidth = 3f
            cv.drawLine(x - r * 0.5f, y - r * 0.6f, x - r * 0.1f, y + r * 0.2f, stroke)
            cv.drawLine(x + r * 0.6f, y - r * 0.2f, x + r * 0.2f, y + r * 0.5f, stroke)
            fill.color = c(0xFFFF6A20)
            cv.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.13f, fill); cv.drawCircle(x + r * 0.35f, y - r * 0.35f, r * 0.13f, fill)
        }
        EType.HIVE -> {
            fill.color = col
            path.reset()
            for (k in 0 until 6) {
                val a = k * TAU / 6f
                val px = x + cos(a) * r; val py = y + sin(a) * r
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close(); cv.drawPath(path, fill)
            stroke.color = c(0xFF8A5010); stroke.strokeWidth = 3f
            for (k in 0 until 3) cv.drawLine(x - r * 0.8f, y - r * 0.4f + k * r * 0.4f, x + r * 0.8f, y - r * 0.4f + k * r * 0.4f, stroke)
            eyes(cv, x, y - r * 0.1f, r * 0.8f, ax, ay, c(0xFF202020), c(0xFFFF3030))
        }
        EType.UFO -> {
            fill.color = withAlpha(c(0xFFB0FFE0), 140); cv.drawCircle(x, y - r * 0.3f, r * 0.55f, fill)
            fill.color = col; rect.set(x - r, y - r * 0.25f, x + r, y + r * 0.35f); cv.drawOval(rect, fill)
            for (k in 0 until 6) {
                val a = animT * 4f + k * TAU / 6f
                fill.color = if (k % 2 == 0) c(0xFFFFFF60) else c(0xFFFF60FF)
                cv.drawCircle(x + cos(a) * r * 0.8f, y + 0.05f * r + sin(a) * r * 0.15f, r * 0.08f, fill)
            }
            fill.color = c(0xFF102010)
            cv.drawCircle(x - r * 0.2f, y - r * 0.35f, r * 0.1f, fill); cv.drawCircle(x + r * 0.2f, y - r * 0.35f, r * 0.1f, fill)
        }
    }
    if (e.slowT > 0f) { stroke.color = c(0x997FE8FF); stroke.strokeWidth = 2f; cv.drawCircle(x, y, r + 1.5f, stroke) }
    if (e.burnT > 0f) {
        fill.color = withAlpha(c(0xFFFF7020), 160 + (sin(animT * 20f) * 60).toInt())
        cv.drawCircle(x + r * 0.5f, y - r * 0.8f, 2.5f, fill)
    }
    // полоска ХП (элиты и подбитые громилы)
    if ((e.elite != null || e.maxHp > 60f) && !e.type.boss && e.hp < e.maxHp) {
        val bw = r * 2f
        fill.color = c(0xAA000000); cv.drawRect(x - bw / 2, y - r - 8f, x + bw / 2, y - r - 5f, fill)
        fill.color = c(0xFFFF4040); cv.drawRect(x - bw / 2, y - r - 8f, x - bw / 2 + bw * max(0f, e.hp / e.maxHp), y - r - 5f, fill)
    }
}

fun Game.drawPlayer(cv: Canvas) {
    val p = player
    val x = p.x; val y = p.y
    shadow(cv, x, y, p.r)
    if (p.invuln > 0f && p.domeT <= 0f && (animT * 20).toInt() % 2 == 0) return
    val step = sin(p.walkT) * 2.5f
    // ноги
    fill.color = c(0xFF2A2A30)
    cv.drawCircle(x - 5f, y + p.r - 1f + step * 0.5f, 3.5f, fill)
    cv.drawCircle(x + 5f, y + p.r - 1f - step * 0.5f, 3.5f, fill)
    // тело
    fill.color = p.color; cv.drawCircle(x, y, p.r, fill)
    fill.color = withAlpha(WHITE, 60); cv.drawCircle(x - p.r * 0.35f, y - p.r * 0.4f, p.r * 0.35f, fill)
    // шапка героя
    when (hero) {
        Hero.WITCH -> {
            fill.color = c(0xFF4A2A6A)
            path.reset(); path.moveTo(x - p.r * 1.1f, y - p.r * 0.5f); path.lineTo(x + p.r * 0.3f, y - p.r * 2.3f); path.lineTo(x + p.r * 1.1f, y - p.r * 0.5f); path.close()
            cv.drawPath(path, fill)
        }
        Hero.ZORG -> {
            stroke.color = p.color; stroke.strokeWidth = 2f
            cv.drawLine(x - 4f, y - p.r, x - 7f, y - p.r - 9f, stroke); cv.drawLine(x + 4f, y - p.r, x + 7f, y - p.r - 9f, stroke)
            fill.color = c(0xFFFFFF80); cv.drawCircle(x - 7f, y - p.r - 9f, 2.5f, fill); cv.drawCircle(x + 7f, y - p.r - 9f, 2.5f, fill)
        }
        Hero.IMP -> {
            fill.color = c(0xFF3A1010)
            path.reset()
            path.moveTo(x - 8f, y - p.r + 3f); path.lineTo(x - 11f, y - p.r - 8f); path.lineTo(x - 3f, y - p.r + 1f); path.close()
            path.moveTo(x + 8f, y - p.r + 3f); path.lineTo(x + 11f, y - p.r - 8f); path.lineTo(x + 3f, y - p.r + 1f); path.close()
            cv.drawPath(path, fill)
        }
        Hero.GUNNER -> {
            fill.color = c(0xFF5A3A1A)
            rect.set(x - p.r * 1.0f, y - p.r * 0.95f, x + p.r * 1.0f, y - p.r * 0.55f); cv.drawRoundRect(rect, 3f, 3f, fill)
            rect.set(x - p.r * 0.6f, y - p.r * 1.4f, x + p.r * 0.6f, y - p.r * 0.7f); cv.drawRoundRect(rect, 4f, 4f, fill)
        }
    }
    eyes(cv, x, y - 1f, p.r, p.aimX, p.aimY)
    // пушка
    cv.save()
    cv.translate(x, y + 2f)
    val ang = atan2(p.aimY, p.aimX) * 180f / PI_F
    cv.rotate(ang)
    if (p.aimX < 0f) cv.scale(1f, -1f)
    cv.translate(8f, 3f)
    drawGun(cv, gun, 1.15f, p.aimX < 0f)
    cv.restore()
    // щит
    if (p.shield > 0f) {
        stroke.color = withAlpha(c(0xFF60D0FF), (60 + 140 * p.shield / max(1f, p.maxShield)).toInt()); stroke.strokeWidth = 2f
        cv.drawCircle(x, y, p.r + 6f, stroke)
    }
    if (p.domeT > 0f) {
        fill.color = withAlpha(c(0xFF7FE8FF), 50); cv.drawCircle(x, y, 40f, fill)
        stroke.color = withAlpha(c(0xFF7FE8FF), 200); stroke.strokeWidth = 2.5f; cv.drawCircle(x, y, 40f + sin(animT * 8f) * 2f, stroke)
    }
    // радиус подбора — лёгкий круг
    stroke.color = c(0x14FFFFFF); stroke.strokeWidth = 1f
    cv.drawCircle(x, y, p.pickup, stroke)
}

val SOCKETS = floatArrayOf(
    2f, -8f, 12f, -7.5f, 21f, -6.5f, -5f, -8.5f, 10f, 6.5f, 17f, 5f,
    -8f, 3f, 27f, -1.5f, 6f, -14f, 16f, -13.5f, 26f, -10f, -1f, 14f
)

/** Рисует пушку и всё, что к ней прикручено. k — масштаб, ствол смотрит вправо. flip — холст отражён по Y. */
fun Game.drawGun(cv: Canvas, g: Gun, k: Float, flip: Boolean = false) {
    val counts = IntArray(Origin.values().size)
    for (pd in g.mods) counts[pd.origin.ordinal]++
    var dom = -1; var best = 0
    for (i in counts.indices) if (counts[i] > best) { best = counts[i]; dom = i }
    val tint = if (dom >= 0) Origin.values()[dom].color else c(0xFF6A6A78)
    val bLen = 12f + min(g.mods.size, 8) * 1.2f
    // рукоять
    fill.color = c(0xFF34343E)
    path.reset(); path.moveTo(-4f * k, 0f); path.lineTo(1f * k, 0f); path.lineTo(-1f * k, 10f * k); path.lineTo(-6f * k, 10f * k); path.close()
    cv.drawPath(path, fill)
    // корпус
    fill.color = c(0xFF4A4A58)
    rect.set(-6f * k, -4.5f * k, 8f * k, 2f * k); cv.drawRoundRect(rect, 1.5f * k, 1.5f * k, fill)
    // ствол
    fill.color = tint
    rect.set(7f * k, -3.6f * k, (7f + bLen) * k, 0.6f * k); cv.drawRoundRect(rect, 1.2f * k, 1.2f * k, fill)
    fill.color = withAlpha(WHITE, 50)
    rect.set(7f * k, -3.6f * k, (7f + bLen) * k, -2.4f * k); cv.drawRect(rect, fill)
    // спусковая скоба
    stroke.color = c(0xFF34343E); stroke.strokeWidth = 1f * k
    cv.drawCircle(2f * k, 3.2f * k, 1.8f * k, stroke)
    // прикрученные штуки
    val es = 6.2f * k
    for ((i, pd) in g.mods.withIndex()) {
        if (i * 2 + 1 >= SOCKETS.size) break
        val px = SOCKETS[i * 2] * k; val py = SOCKETS[i * 2 + 1] * k
        stroke.color = withAlpha(pd.origin.color, 160); stroke.strokeWidth = 0.7f * k
        cv.drawLine(px, py, clampf(px, -5f * k, (7f + bLen) * k), clampf(py, -4f * k, 1f * k), stroke)
        fill.color = withAlpha(pd.origin.color, 70); cv.drawCircle(px, py, es * 0.62f, fill)
        cv.save()
        cv.translate(px, py)
        if (flip) cv.scale(1f, -1f)
        text.textAlign = Paint.Align.CENTER; text.textSize = es; text.color = WHITE
        cv.drawText(pd.glyph, 0f, es * 0.36f, text)
        cv.restore()
    }
}

fun Game.drawBullet(cv: Canvas, b: Bullet, glow: Boolean) {
    val sp = hypot(b.vx, b.vy) + 0.01f
    var dx = b.vx / sp; var dy = b.vy / sp
    if (b.kind == BKind.BOOMERANG) { dx = cos(b.spin); dy = sin(b.spin) }
    val s = b.size
    if (b.kind == BKind.MINE) {
        fill.color = c(0xFF30303A); cv.drawCircle(b.x, b.y, s + 2f, fill)
        fill.color = b.color; cv.drawCircle(b.x, b.y, s, fill)
        fill.color = if ((b.t * 6).toInt() % 2 == 0) c(0xFFFF3030) else c(0xFF601010)
        cv.drawCircle(b.x, b.y, s * 0.4f, fill)
        return
    }
    if (glow) { fill.color = withAlpha(b.color, 55); cv.drawCircle(b.x, b.y, s * 2f, fill) }
    fill.color = b.color
    when (b.shape) {
        Shape.CIRCLE -> {
            cv.drawCircle(b.x, b.y, s, fill)
            fill.color = withAlpha(WHITE, 200); cv.drawCircle(b.x, b.y, s * 0.45f, fill)
        }
        Shape.NEEDLE -> {
            stroke.color = b.color; stroke.strokeWidth = s * 0.9f
            cv.drawLine(b.x - dx * s * 3f, b.y - dy * s * 3f, b.x + dx * s * 1.5f, b.y + dy * s * 1.5f, stroke)
            stroke.color = withAlpha(WHITE, 200); stroke.strokeWidth = s * 0.35f
            cv.drawLine(b.x - dx * s * 2f, b.y - dy * s * 2f, b.x + dx * s * 1.2f, b.y + dy * s * 1.2f, stroke)
        }
        Shape.STAR -> {
            val rot = b.t * 8f
            path.reset()
            for (k in 0 until 10) {
                val rr = if (k % 2 == 0) s * 1.3f else s * 0.55f
                val a = rot + k * PI_F / 5f
                val px = b.x + cos(a) * rr; val py = b.y + sin(a) * rr
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close(); cv.drawPath(path, fill)
        }
        Shape.DIAMOND -> {
            path.reset()
            path.moveTo(b.x + dx * s * 1.6f, b.y + dy * s * 1.6f)
            path.lineTo(b.x - dy * s * 0.8f, b.y + dx * s * 0.8f)
            path.lineTo(b.x - dx * s * 1.2f, b.y - dy * s * 1.2f)
            path.lineTo(b.x + dy * s * 0.8f, b.y - dx * s * 0.8f)
            path.close(); cv.drawPath(path, fill)
        }
        Shape.RING -> {
            stroke.color = b.color; stroke.strokeWidth = s * 0.4f
            cv.drawCircle(b.x, b.y, s, stroke)
            fill.color = withAlpha(b.color, 80); cv.drawCircle(b.x, b.y, s * 0.6f, fill)
        }
        Shape.TRIANGLE -> {
            path.reset()
            path.moveTo(b.x + dx * s * 1.6f, b.y + dy * s * 1.6f)
            path.lineTo(b.x - dx * s - dy * s, b.y - dy * s + dx * s)
            path.lineTo(b.x - dx * s + dy * s, b.y - dy * s - dx * s)
            path.close(); cv.drawPath(path, fill)
        }
        Shape.SQUARE -> {
            path.reset()
            path.moveTo(b.x + dx * s, b.y + dy * s); path.lineTo(b.x - dy * s, b.y + dx * s)
            path.lineTo(b.x - dx * s, b.y - dy * s); path.lineTo(b.x + dy * s, b.y - dx * s); path.close()
            cv.drawPath(path, fill)
        }
    }
}

fun Game.drawBeam(cv: Canvas, bm: Beam) {
    val a = (255 * (bm.life / bm.maxLife)).toInt()
    if (bm.zap) {
        stroke.color = withAlpha(bm.color, a); stroke.strokeWidth = bm.width
        val segs = 5
        var px = bm.x1; var py = bm.y1
        val dx = bm.x2 - bm.x1; val dy = bm.y2 - bm.y1
        val len = hypot(dx, dy) + 0.01f
        val nx = -dy / len; val ny = dx / len
        for (i in 1..segs) {
            val t = i.toFloat() / segs
            val off = if (i == segs) 0f else rf(-8f, 8f)
            val qx = bm.x1 + dx * t + nx * off; val qy = bm.y1 + dy * t + ny * off
            cv.drawLine(px, py, qx, qy, stroke)
            px = qx; py = qy
        }
    } else {
        stroke.color = withAlpha(bm.color, a / 2); stroke.strokeWidth = bm.width * 2.2f
        cv.drawLine(bm.x1, bm.y1, bm.x2, bm.y2, stroke)
        stroke.color = withAlpha(WHITE, a); stroke.strokeWidth = bm.width * 0.7f
        cv.drawLine(bm.x1, bm.y1, bm.x2, bm.y2, stroke)
    }
}

fun Game.drawPickup(cv: Canvas, k: Pickup) {
    val bob = sin(k.t * 5f) * 1.5f
    val x = k.x; val y = k.y + bob
    when (k.kind) {
        PKind.XP -> {
            val col = when { k.value >= 20 -> c(0xFFFF5A6A); k.value >= 5 -> c(0xFF6CE46C); else -> c(0xFF4AA8FF) }
            val s = if (k.value >= 20) 6f else if (k.value >= 5) 5f else 4f
            fill.color = col
            path.reset(); path.moveTo(x, y - s * 1.3f); path.lineTo(x + s, y); path.lineTo(x, y + s * 1.3f); path.lineTo(x - s, y); path.close()
            cv.drawPath(path, fill)
            fill.color = withAlpha(WHITE, 160); cv.drawCircle(x - s * 0.25f, y - s * 0.4f, s * 0.3f, fill)
        }
        PKind.GOLD -> {
            fill.color = c(0xFFB08010); cv.drawCircle(x, y + 1f, 5f, fill)
            fill.color = c(0xFFFFD54A); cv.drawCircle(x, y, 5f, fill)
            fill.color = c(0xFFFFF0A0); cv.drawCircle(x - 1.5f, y - 1.5f, 1.5f, fill)
        }
        PKind.HEAL, PKind.MAGNET, PKind.ITEM, PKind.EQUIP -> {
            val glyph = when (k.kind) {
                PKind.HEAL -> "🍗"; PKind.MAGNET -> "🧲"
                PKind.ITEM -> k.item?.glyph ?: "?"; else -> k.equip?.glyph ?: "?"
            }
            val col = when (k.kind) {
                PKind.ITEM -> RARITY_COLORS[k.item?.rarity ?: 0]; PKind.EQUIP -> c(0xFFFF9A40); else -> c(0xFFFFFFFF)
            }
            fill.color = withAlpha(col, 70); cv.drawCircle(x, y, 13f + sin(k.t * 4f) * 2f, fill)
            text.textSize = 15f; text.color = WHITE
            cv.drawText(glyph, x, y + 5f, text)
        }
        PKind.PART -> {
            val part = k.part ?: return
            val col = part.origin.color
            fill.color = withAlpha(col, 60); cv.drawCircle(x, y, 15f + sin(k.t * 4f) * 2.5f, fill)
            fill.color = c(0xFF2A2A34); rect.set(x - 9f, y - 9f, x + 9f, y + 9f); cv.drawRoundRect(rect, 3f, 3f, fill)
            stroke.color = RARITY_COLORS[part.rarity]; stroke.strokeWidth = 1.5f; cv.drawRoundRect(rect, 3f, 3f, stroke)
            text.textSize = 12f; text.color = WHITE
            cv.drawText(part.glyph, x, y + 4.5f, text)
            // луч над редкой деталью
            if (part.rarity >= 1) {
                fill.color = withAlpha(RARITY_COLORS[part.rarity], 40)
                cv.drawRect(x - 3f, y - 60f, x + 3f, y - 10f, fill)
            }
        }
    }
}

fun Game.drawChest(cv: Canvas, ch: Chest) {
    val x = ch.x; val y = ch.y
    shadow(cv, x, y, 14f)
    fill.color = c(0xFF7A4A20); rect.set(x - 14f, y - 8f, x + 14f, y + 10f); cv.drawRoundRect(rect, 2f, 2f, fill)
    fill.color = c(0xFF9A6030); rect.set(x - 14f, y - 14f, x + 14f, y - 4f); cv.drawRoundRect(rect, 5f, 5f, fill)
    fill.color = c(0xFFFFD54A)
    cv.drawRect(x - 15f, y - 5f, x + 15f, y - 3f, fill)
    cv.drawRect(x - 2.5f, y - 6f, x + 2.5f, y + 1f, fill)
    val afford = player.gold >= ch.cost
    text.textSize = 10f
    text.color = if (afford) c(0xFFFFE27A) else c(0xFFBBBBBB)
    cv.drawText("💰${ch.cost}", x, y - 20f + sin(ch.t * 3f) * 1.5f, text)
}

fun Game.drawDrone(cv: Canvas, d: Drone) {
    shadow(cv, d.x, d.y + 10f, 5f)
    fill.color = c(0xFF8A8A98); cv.drawCircle(d.x, d.y, 6f, fill)
    fill.color = c(0xFF7CFF7C); cv.drawCircle(d.x, d.y, 2.5f, fill)
    stroke.color = c(0xFFCCCCDD); stroke.strokeWidth = 1.2f
    val a = animT * 30f
    cv.drawLine(d.x - cos(a) * 8f, d.y - 7f, d.x + cos(a) * 8f, d.y - 7f, stroke)
}

fun Game.drawVortex(cv: Canvas, v: Vortex) {
    val a = min(1f, v.life)
    fill.color = withAlpha(c(0xFF100018), (200 * a).toInt()); cv.drawCircle(v.x, v.y, 30f, fill)
    stroke.strokeWidth = 3f
    for (i in 0 until 4) {
        stroke.color = withAlpha(c(0xFFB46CFF), (150 * a).toInt() - i * 25)
        val rr = 30f + i * 22f + (v.t * 40f) % 22f
        rect.set(v.x - rr, v.y - rr, v.x + rr, v.y + rr)
        cv.drawArc(rect, v.t * 200f + i * 70f, 220f, false, stroke)
    }
}

@Suppress("unused")
fun Game.paintStyleReset() { stroke.strokeCap = Paint.Cap.ROUND }
