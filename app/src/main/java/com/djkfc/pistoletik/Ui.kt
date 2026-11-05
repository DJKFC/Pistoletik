package com.djkfc.pistoletik

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

val PANEL = c(0xEE1A1D26)
val PANEL2 = c(0xFF252A36)
val ACCENT = c(0xFFFFE27A)
val GREY = c(0xFFA0A4B0)

// ───────────────────────── помощники ─────────────────────────
fun Game.textC(cv: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
    text.textAlign = Paint.Align.CENTER; text.textSize = size; text.color = color; cv.drawText(s, x, y, text)
}
fun Game.textL(cv: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
    text.textAlign = Paint.Align.LEFT; text.textSize = size; text.color = color; cv.drawText(s, x, y, text)
    text.textAlign = Paint.Align.CENTER
}
fun Game.textR(cv: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
    text.textAlign = Paint.Align.RIGHT; text.textSize = size; text.color = color; cv.drawText(s, x, y, text)
    text.textAlign = Paint.Align.CENTER
}
fun Game.textShadowC(cv: Canvas, s: String, x: Float, y: Float, size: Float, color: Int) {
    textC(cv, s, x + size * 0.06f, y + size * 0.06f, size, c(0xAA000000)); textC(cv, s, x, y, size, color)
}

fun Game.wrap(s: String, maxW: Float, size: Float): List<String> {
    val key = "${size.toInt()}|${maxW.toInt()}|$s"
    wrapCache[key]?.let { return it }
    text.textSize = size
    val lines = ArrayList<String>()
    var cur = ""
    for (wd in s.split(' ')) {
        val t = if (cur.isEmpty()) wd else "$cur $wd"
        if (text.measureText(t) > maxW && cur.isNotEmpty()) { lines.add(cur); cur = wd } else cur = t
    }
    if (cur.isNotEmpty()) lines.add(cur)
    if (wrapCache.size > 400) wrapCache.clear()
    wrapCache[key] = lines
    return lines
}

fun Game.panel(cv: Canvas, l: Float, t: Float, r: Float, b: Float, bg: Int, border: Int, bw: Float = 2f * u) {
    rect.set(l, t, r, b)
    fill.color = bg; cv.drawRoundRect(rect, 12f * u, 12f * u, fill)
    if (border != 0) { stroke.color = border; stroke.strokeWidth = bw; cv.drawRoundRect(rect, 12f * u, 12f * u, stroke) }
}

fun Game.button(cv: Canvas, l: Float, t: Float, r: Float, b: Float, label: String, color: Int, enabled: Boolean = true, act: () -> Unit) {
    val press = 1f + sin(animT * 3f) * 0.0f
    rect.set(l, t, r, b)
    fill.color = if (enabled) color else c(0xFF3A3D48)
    cv.drawRoundRect(rect, 12f * u, 12f * u, fill)
    fill.color = withAlpha(WHITE, 30)
    rect.set(l, t, r, t + (b - t) * 0.45f * press); cv.drawRoundRect(rect, 12f * u, 12f * u, fill)
    val size = min((b - t) * 0.42f, 20f * u)
    textC(cv, label, (l + r) / 2f, (t + b) / 2f + size * 0.36f, size, if (enabled) c(0xFF14161C) else GREY)
    if (enabled) uiButtons.add(Btn(l, t, r, b, act))
}

fun Game.dim(cv: Canvas, a: Int = 190) { fill.color = withAlpha(c(0xFF0A0C12), a); cv.drawRect(0f, 0f, w, h, fill) }

fun Game.pauseX() = w - 26f * u
fun Game.pauseY() = 38f * u
fun Game.equipX() = w - 64f * u
fun Game.equipY() = h - 96f * u

// ───────────────────────── HUD ─────────────────────────
fun Game.drawHud(cv: Canvas) {
    val p = player
    // опыт
    fill.color = c(0xCC000000); cv.drawRect(0f, 0f, w, 9f * u, fill)
    fill.color = c(0xFF4AA8FF); cv.drawRect(0f, 0f, w * min(1f, p.xp / p.xpNeed), 9f * u, fill)
    // здоровье
    val hl = 10f * u; val ht = 16f * u; val hr = 160f * u; val hb = 32f * u
    rect.set(hl, ht, hr, hb); fill.color = c(0xCC000000); cv.drawRoundRect(rect, 5f * u, 5f * u, fill)
    rect.set(hl + 2f * u, ht + 2f * u, hl + 2f * u + (hr - hl - 4f * u) * max(0f, p.hp / p.maxHp), hb - 2f * u)
    fill.color = if (p.hp / p.maxHp < 0.3f) c(0xFFFF3A3A) else c(0xFFE84A4A); cv.drawRoundRect(rect, 4f * u, 4f * u, fill)
    if (p.maxShield > 0f) {
        rect.set(hl + 2f * u, hb - 6f * u, hl + 2f * u + (hr - hl - 4f * u) * (p.shield / p.maxShield), hb - 2f * u)
        fill.color = c(0xFF60D0FF); cv.drawRect(rect, fill)
    }
    textC(cv, "${max(0, p.hp.toInt())} / ${p.maxHp.toInt()}", (hl + hr) / 2f, hb - 4.5f * u, 11f * u, WHITE)
    textL(cv, "УР. ${p.level}", hl, hb + 15f * u, 13f * u, c(0xFF8FD0FF))
    // таймер
    textShadowC(cv, fmtTime(time), w / 2f, 52f * u, 22f * u, WHITE)
    // золото и убийства
    textR(cv, "💰 ${p.gold}", pauseX() - 24f * u, 30f * u, 14f * u, ACCENT)
    textR(cv, "💀 $kills", pauseX() - 24f * u, 48f * u, 12f * u, GREY)
    // пауза
    fill.color = c(0xAA000000); cv.drawCircle(pauseX(), pauseY(), 17f * u, fill)
    fill.color = WHITE
    cv.drawRect(pauseX() - 6f * u, pauseY() - 7f * u, pauseX() - 2f * u, pauseY() + 7f * u, fill)
    cv.drawRect(pauseX() + 2f * u, pauseY() - 7f * u, pauseX() + 6f * u, pauseY() + 7f * u, fill)

    // босс
    boss?.let { b ->
        val bl = w * 0.15f; val br = w * 0.85f; val bt = 64f * u
        rect.set(bl, bt, br, bt + 12f * u); fill.color = c(0xCC000000); cv.drawRoundRect(rect, 4f * u, 4f * u, fill)
        rect.set(bl + 2f * u, bt + 2f * u, bl + 2f * u + (br - bl - 4f * u) * max(0f, b.hp / b.maxHp), bt + 10f * u)
        fill.color = c(0xFFB040FF); cv.drawRoundRect(rect, 3f * u, 3f * u, fill)
        textShadowC(cv, b.type.title, w / 2f, bt + 26f * u, 12f * u, c(0xFFE0C0FF))
    }

    // стрелки к сундукам
    for (ch in chests) {
        val sx = w / 2f + (ch.x - camX) * zoom; val sy = h / 2f + (ch.y - camY) * zoom
        if (sx in 0f..w && sy in 0f..h) continue
        val a = atan2(sy - h / 2f, sx - w / 2f)
        val m = 34f * u
        val ex = clampf(w / 2f + cos(a) * w, m, w - m); val ey = clampf(h / 2f + sin(a) * h, m + 60f * u, h - m)
        fill.color = withAlpha(c(0xFFFFD54A), 200)
        path.reset()
        path.moveTo(ex + cos(a) * 12f * u, ey + sin(a) * 12f * u)
        path.lineTo(ex + cos(a + 2.4f) * 8f * u, ey + sin(a + 2.4f) * 8f * u)
        path.lineTo(ex + cos(a - 2.4f) * 8f * u, ey + sin(a - 2.4f) * 8f * u)
        path.close(); cv.drawPath(path, fill)
        textC(cv, "📦", ex - cos(a) * 14f * u, ey - sin(a) * 14f * u + 5f * u, 13f * u, WHITE)
    }

    // баннер
    if (bannerT > 0f) {
        val a = (255 * min(1f, bannerT)).toInt()
        val lines = wrap(banner, w - 40f * u, 17f * u)
        var y = h * 0.24f
        for (ln in lines) {
            textC(cv, ln, w / 2f + 1.5f * u, y + 1.5f * u, 17f * u, withAlpha(BLACK, a))
            textC(cv, ln, w / 2f, y, 17f * u, withAlpha(ACCENT, a))
            y += 22f * u
        }
    }

    // оборудование
    p.equip?.let { eq ->
        val ex = equipX(); val ey = equipY(); val r = 34f * u
        fill.color = c(0xAA000000); cv.drawCircle(ex, ey, r, fill)
        if (p.equipCd > 0f) {
            fill.color = c(0x88FFFFFF)
            rect.set(ex - r, ey - r, ex + r, ey + r)
            cv.drawArc(rect, -90f, 360f * (1f - p.equipCd / eq.cd), true, fill)
            fill.color = c(0xCC000000); cv.drawCircle(ex, ey, r - 4f * u, fill)
        } else {
            stroke.color = c(0xFFFF9A40); stroke.strokeWidth = 3f * u; cv.drawCircle(ex, ey, r, stroke)
        }
        textC(cv, eq.glyph, ex, ey + 10f * u, 28f * u, if (p.equipCd > 0f) withAlpha(WHITE, 110) else WHITE)
        if (p.equipCd > 0f) textC(cv, "${p.equipCd.toInt() + 1}", ex, ey + r + 14f * u, 12f * u, GREY)
    }

    // джойстик
    if (joyId != -1) {
        fill.color = c(0x33FFFFFF); cv.drawCircle(joyOx, joyOy, 55f * u, fill)
        stroke.color = c(0x55FFFFFF); stroke.strokeWidth = 2f * u; cv.drawCircle(joyOx, joyOy, 55f * u, stroke)
        fill.color = c(0x99FFFFFF); cv.drawCircle(joyOx + jx * 55f * u, joyOy + jy * 55f * u, 22f * u, fill)
    } else if (time < 6f && state == State.PLAY) {
        textC(cv, "Проведи пальцем, чтобы бежать. Пушка стреляет сама", w / 2f, h - 40f * u, 12f * u, withAlpha(WHITE, 180))
    }
}

// ───────────────────────── Меню ─────────────────────────
val demoGun = Gun().apply {
    for (id in listOf("banana", "void", "sock", "fate", "battery", "chili", "duck")) Parts.byId[id]?.let { mods.add(it) }
}

fun Game.drawMenu(cv: Canvas) {
    cv.drawColor(GROUND1)
    cv.save()
    cv.scale(zoom, zoom)
    val ox = animT * 18f; val oy = animT * 9f
    cv.translate(-ox, -oy)
    drawGround(cv, ox - 20f, oy - 20f, ox + w / zoom + 20f, oy + h / zoom + 20f)
    cv.restore()
    dim(cv, 120)

    textShadowC(cv, "PISTOLETIK", w / 2f, 78f * u, 46f * u, ACCENT)
    textC(cv, "собери свою безумную пушку", w / 2f, 100f * u, 13f * u, GREY)

    // крутящаяся пушка
    cv.save()
    cv.translate(w / 2f - 30f * u, 150f * u)
    cv.rotate(sin(animT * 1.3f) * 8f)
    drawGun(cv, demoGun, 5.2f * u)
    cv.restore()
    // пульки
    for (i in 0 until 5) {
        val t = (animT * 1.4f + i * 0.2f) % 1f
        fill.color = withAlpha(c(0xFF55FFD0), (255 * (1f - t)).toInt())
        cv.drawCircle(w / 2f + 90f * u + t * 120f * u, 140f * u + sin(t * 12f) * 10f * u, 5f * u, fill)
    }

    textC(cv, "Выбери героя", w / 2f, 222f * u, 15f * u, WHITE)
    val heroes = Hero.values()
    val gap = 10f * u
    val cw = (w - 32f * u - gap) / 2f
    val chh = 118f * u
    for ((i, hr) in heroes.withIndex()) {
        val col = i % 2; val row = i / 2
        val l = 16f * u + col * (cw + gap); val t = 234f * u + row * (chh + gap)
        val sel = hr == hero
        panel(cv, l, t, l + cw, t + chh, if (sel) PANEL2 else PANEL, if (sel) hr.color else c(0xFF333844), if (sel) 3f * u else 1.5f * u)
        fill.color = hr.color; cv.drawCircle(l + 26f * u, t + 28f * u, 14f * u, fill)
        eyes(cv, l + 26f * u, t + 27f * u, 14f * u, 0.5f, 0.2f)
        textL(cv, hr.title, l + 48f * u, t + 26f * u, 16f * u, if (sel) hr.color else WHITE)
        textL(cv, "❤${hr.hp.toInt()}  👟${hr.speed.toInt()}", l + 48f * u, t + 42f * u, 10f * u, GREY)
        var y = t + 62f * u
        for (ln in wrap(hr.desc, cw - 20f * u, 11f * u)) { textL(cv, ln, l + 10f * u, y, 11f * u, c(0xFFD0D4DC)); y += 14f * u }
        textL(cv, "${hr.equip.glyph} ${hr.equip.title}", l + 10f * u, t + chh - 10f * u, 11f * u, c(0xFFFF9A40))
        uiButtons.add(Btn(l, t, l + cw, t + chh) { hero = hr })
    }
    val by = 234f * u + 2 * (chh + gap) + 6f * u
    button(cv, 40f * u, by, w - 40f * u, by + 56f * u, "▶  ИГРАТЬ", ACCENT) { startRun() }

    val bt = prefs.getFloat("bestTime", 0f)
    if (bt > 0f) {
        textC(cv, "Рекорд: ${fmtTime(bt)}  ·  💀 ${prefs.getInt("bestKills", 0)}  ·  ур. ${prefs.getInt("bestLevel", 0)}",
            w / 2f, by + 80f * u, 12f * u, GREY)
    }
    var hy = by + 108f * u
    val tips = listOf(
        "🔫 К пушке можно прикрутить что угодно: кристалл, носок, банан, фонарик, гирю… Детали падают с элиток и боссов",
        "🌀 Режимы стрельбы сочетаются: спираль + луч, кольцо мин, очереди бумерангов",
        "✨ 3 детали одного происхождения = бонус комплекта",
        "📦 Сундуки открываются за золото и дают предметы"
    )
    for (tp in tips) {
        for (ln in wrap(tp, w - 50f * u, 11f * u)) {
            if (hy < h - 10f * u) textC(cv, ln, w / 2f, hy, 11f * u, c(0xFF8A90A0))
            hy += 15f * u
        }
        hy += 4f * u
    }
}

// ───────────────────────── Карточки ─────────────────────────
fun Game.partCard(cv: Canvas, part: PartDef, l: Float, t: Float, r: Float, b: Float, highlight: Boolean) {
    val rc = RARITY_COLORS[part.rarity]
    panel(cv, l, t, r, b, if (highlight) PANEL2 else PANEL, rc, if (highlight) 3f * u else 1.5f * u)
    val ch = b - t
    val isz = min(56f * u, ch - 20f * u)
    val il = l + 12f * u; val it = t + (ch - isz) / 2f
    rect.set(il, it, il + isz, it + isz)
    fill.color = withAlpha(part.origin.color, 60); cv.drawRoundRect(rect, 10f * u, 10f * u, fill)
    stroke.color = part.origin.color; stroke.strokeWidth = 2f * u; cv.drawRoundRect(rect, 10f * u, 10f * u, stroke)
    textC(cv, part.glyph, il + isz / 2f, it + isz / 2f + isz * 0.2f, isz * 0.55f, WHITE)
    val tx = il + isz + 12f * u
    val tw = r - tx - 10f * u
    var y = t + 22f * u
    textL(cv, part.name, tx, y, 16f * u, rc)
    y += 16f * u
    val have = gun.mods.count { it.id == part.id }
    textL(cv, "Деталь · ${part.origin.title} · ${RARITY_NAMES[part.rarity]}" + (if (have > 0) " · стоит ×$have" else ""), tx, y, 10.5f * u, part.origin.color)
    y += 17f * u
    for (ln in wrap(part.desc, tw, 12.5f * u)) {
        if (y > b - 6f * u) break
        textL(cv, ln, tx, y, 12.5f * u, c(0xFFE0E4EC)); y += 15f * u
    }
}

fun Game.itemCard(cv: Canvas, item: Item, l: Float, t: Float, r: Float, b: Float) {
    val rc = RARITY_COLORS[item.rarity]
    panel(cv, l, t, r, b, PANEL, rc, 1.5f * u)
    val ch = b - t
    val isz = min(56f * u, ch - 20f * u)
    val il = l + 12f * u; val it = t + (ch - isz) / 2f
    rect.set(il, it, il + isz, it + isz)
    fill.color = withAlpha(rc, 40); cv.drawRoundRect(rect, 10f * u, 10f * u, fill)
    textC(cv, item.glyph, il + isz / 2f, it + isz / 2f + isz * 0.2f, isz * 0.55f, WHITE)
    val tx = il + isz + 12f * u
    val tw = r - tx - 10f * u
    var y = t + 22f * u
    textL(cv, item.title, tx, y, 16f * u, rc)
    y += 16f * u
    val have = player.n(item)
    textL(cv, "Предмет · ${RARITY_NAMES[item.rarity]}" + (if (have > 0) " · есть ×$have" else ""), tx, y, 10.5f * u, GREY)
    y += 17f * u
    for (ln in wrap(item.desc, tw, 12.5f * u)) {
        if (y > b - 6f * u) break
        textL(cv, ln, tx, y, 12.5f * u, c(0xFFE0E4EC)); y += 15f * u
    }
}

fun Game.drawLevelUp(cv: Canvas) {
    dim(cv)
    textShadowC(cv, "НОВЫЙ УРОВЕНЬ ${player.level}!", w / 2f, 100f * u, 26f * u, c(0xFF8FD0FF))
    textC(cv, "Выбери награду", w / 2f, 124f * u, 13f * u, GREY)
    val n = choices.size
    val top = 142f * u; val gap = 10f * u
    val avail = h - top - 100f * u
    val cardH = min(100f * u, avail / max(1, n) - gap)
    for ((i, ch) in choices.withIndex()) {
        val t = top + i * (cardH + gap)
        val l = 16f * u; val r = w - 16f * u; val b = t + cardH
        val it = ch.item; val pt = ch.part
        if (it != null) itemCard(cv, it, l, t, r, b)
        else if (pt != null) {
            partCard(cv, pt, l, t, r, b, false)
            textR(cv, "ДЕТАЛЬ", r - 10f * u, t + 18f * u, 10f * u, ACCENT)
        }
        uiButtons.add(Btn(l, t, r, b) { pickChoice(ch) })
    }
    val by = top + n * (cardH + gap) + 6f * u
    val cost = rerollCost()
    button(cv, 50f * u, by, w - 50f * u, by + 44f * u, "🎲 Перебросить — $cost 💰", c(0xFF8FD0FF), player.gold >= cost) { reroll() }
    textC(cv, "У тебя: ${player.gold} 💰", w / 2f, by + 64f * u, 12f * u, ACCENT)
}

fun Game.drawPartSwap(cv: Canvas) {
    dim(cv)
    val np = pendingParts.firstOrNull() ?: return
    textShadowC(cv, "НОВАЯ ДЕТАЛЬ!", w / 2f, 70f * u, 24f * u, ACCENT)
    textC(cv, "Все гнёзда заняты (${gun.mods.size}/${capacity()}). Что заменить?", w / 2f, 92f * u, 12.5f * u, GREY)
    val cardH = 96f * u
    var t = 104f * u
    partCard(cv, np, 16f * u, t, w - 16f * u, t + cardH, true)
    t += cardH + 10f * u
    // комплект после установки
    val counts = IntArray(Origin.values().size)
    for (pd in gun.mods) counts[pd.origin.ordinal]++
    val o = np.origin
    val cnt = counts[o.ordinal] + 1
    val setText = if (cnt >= 3) "✨ «${o.title}» ($cnt): ${o.setDesc}" + (if (cnt >= 5) " ×2" else "")
                  else "Комплект «${o.title}»: $cnt/3 — ${o.setDesc}"
    for (ln in wrap(setText, w - 40f * u, 11.5f * u)) { textC(cv, ln, w / 2f, t + 10f * u, 11.5f * u, if (cnt >= 3) o.color else GREY); t += 15f * u }
    t += 8f * u
    // список текущих деталей — тапни, чтобы заменить
    val n = gun.mods.size
    val bottom = h - 80f * u
    val rowH = min(46f * u, (bottom - t) / max(1, n) - 4f * u)
    for ((i, pd) in gun.mods.withIndex()) {
        val rt = t + i * (rowH + 4f * u)
        panel(cv, 16f * u, rt, w - 16f * u, rt + rowH, PANEL, RARITY_COLORS[pd.rarity], 1.2f * u)
        val gs = min(24f * u, rowH * 0.7f)
        textC(cv, pd.glyph, 36f * u, rt + rowH / 2f + gs * 0.36f, gs, WHITE)
        textL(cv, pd.name, 58f * u, rt + rowH * 0.42f, min(13f * u, rowH * 0.32f), pd.origin.color)
        val d = wrap(pd.desc, w - 150f * u, 10f * u).firstOrNull() ?: ""
        textL(cv, d, 58f * u, rt + rowH * 0.8f, min(10f * u, rowH * 0.26f), c(0xFFB8BCC8))
        textR(cv, "заменить ↺", w - 26f * u, rt + rowH / 2f + 4f * u, 11f * u, c(0xFF6CE46C))
        uiButtons.add(Btn(16f * u, rt, w - 16f * u, rt + rowH) { resolvePart(i) })
    }
    val by = h - 66f * u
    button(cv, 40f * u, by, w - 40f * u, by + 50f * u, "Разобрать новую  +6💰", c(0xFFB0B4C0)) { resolvePart(-1) }
}

fun Game.statsLines(): List<String> {
    val s = gun.stats
    val l1 = "Урон ${s.damage.toInt()}  ·  ${"%.1f".format(s.fireRate)}/с  ·  Снарядов ${s.count}"
    val l2 = "Пробитие ${s.pierce}  ·  Рикошет ${s.bounce}  ·  Крит ${(s.crit * 100).toInt()}% ×${"%.1f".format(s.critMult)}"
    val ex = ArrayList<String>()
    ex.add(if (s.kinds.isEmpty()) "пули" else s.kinds.joinToString("/") { it.title.lowercase() })
    if (s.spiral) ex.add("спираль")
    if (s.nova) ex.add("кольцо")
    if (s.burstOn) ex.add("очередь ×${s.burst}")
    if (s.curve != 0f) ex.add("дуга")
    if (s.mirror > 0) ex.add("отражение ×${s.mirror}")
    if (s.chaos) ex.add("хаос-урон")
    if (s.homing > 0f) ex.add("наведение")
    if (s.explode > 0f) ex.add("взрыв ${s.explode.toInt()}")
    if (s.split > 0) ex.add("осколки ×${s.split}")
    if (s.chain > 0) ex.add("молния ×${s.chain}")
    if (s.burn > 0f) ex.add("горение ${s.burn.toInt()}")
    if (s.freeze > 0f) ex.add("заморозка ${(s.freeze * 100).toInt()}%")
    if (s.pull > 0f) ex.add("притяжение")
    if (s.rear) ex.add("назад")
    if (s.sides) ex.add("в стороны")
    if (s.wave > 0f) ex.add("волна")
    if (s.growth > 0f) ex.add("рост")
    if (s.accel > 0f) ex.add("разгон")
    if (s.leech > 0f) ex.add("вампиризм")
    if (s.execute > 0f) ex.add("добивание")
    val res = arrayListOf(l1, l2)
    res.add("Эффекты: " + ex.joinToString(", "))
    return res
}

fun Game.drawPause(cv: Canvas) {
    dim(cv, 215)
    textShadowC(cv, "ПАУЗА", w / 2f, 60f * u, 24f * u, WHITE)
    // пушка
    panel(cv, 16f * u, 76f * u, w - 16f * u, 156f * u, PANEL, c(0xFF333844))
    cv.save(); cv.translate(w / 2f - 25f * u, 122f * u); drawGun(cv, gun, 3.6f * u); cv.restore()
    var y = 172f * u
    textL(cv, "Гнёзда: ${gun.mods.size}/${capacity()}", 20f * u, y, 12f * u, GREY)
    y += 6f * u
    val n = gun.mods.size
    val rowH = if (n > 8) 22f * u else 28f * u
    for (pd in gun.mods) {
        y += rowH
        textL(cv, pd.glyph, 20f * u, y, rowH * 0.6f, WHITE)
        textL(cv, pd.name, 46f * u, y - rowH * 0.12f, rowH * 0.42f, pd.origin.color)
        val d = wrap(pd.desc, w - 70f * u, rowH * 0.34f).firstOrNull() ?: ""
        textL(cv, d, 46f * u, y + rowH * 0.28f, rowH * 0.34f, c(0xFFB8BCC8))
    }
    y += rowH * 0.6f + 6f * u
    val sets = activeSets()
    for ((o, n) in sets) {
        for (ln in wrap("✨ ${o.title} ($n): ${o.setDesc}" + (if (n >= 5) " ×2" else ""), w - 40f * u, 11.5f * u)) {
            textL(cv, ln, 20f * u, y, 11.5f * u, o.color); y += 14f * u
        }
    }
    y += 4f * u
    for (ln in statsLines()) for (l in wrap(ln, w - 40f * u, 11f * u)) { textL(cv, l, 20f * u, y, 11f * u, c(0xFFE0E4EC)); y += 14f * u }
    y += 8f * u

    // предметы
    val p = player
    val owned = Item.values().filter { p.n(it) > 0 }
    textL(cv, "Предметы (${owned.sumOf { p.n(it) }})" + (p.equip?.let { "   ·   ${it.glyph} ${it.title}" } ?: ""), 20f * u, y, 12f * u, WHITE)
    y += 8f * u
    val cell = 40f * u
    val cols = max(1, ((w - 32f * u) / (cell + 4f * u)).toInt())
    for ((i, it) in owned.withIndex()) {
        val cx = 16f * u + (i % cols) * (cell + 4f * u); val cy = y + (i / cols) * (cell + 4f * u)
        panel(cv, cx, cy, cx + cell, cy + cell, PANEL2, RARITY_COLORS[it.rarity], if (selectedItem == it) 3f * u else 1.2f * u)
        textC(cv, it.glyph, cx + cell / 2f, cy + cell * 0.66f, cell * 0.5f, WHITE)
        textR(cv, "${p.n(it)}", cx + cell - 3f * u, cy + cell - 4f * u, 10f * u, WHITE)
        uiButtons.add(Btn(cx, cy, cx + cell, cy + cell) { selectedItem = it })
    }
    if (owned.isNotEmpty()) y += ((owned.size - 1) / cols + 1) * (cell + 4f * u)
    y += 4f * u
    selectedItem?.let { si ->
        textL(cv, "${si.glyph} ${si.title}: ${si.desc}", 20f * u, y + 12f * u, 11f * u, RARITY_COLORS[si.rarity])
    }
    val by = h - 70f * u
    val bw = (w - 48f * u) / 2f
    button(cv, 16f * u, by, 16f * u + bw, by + 50f * u, "Продолжить", ACCENT) { goTo(State.PLAY) }
    button(cv, 32f * u + bw, by, w - 16f * u, by + 50f * u, "Сдаться", c(0xFFFF7A6A)) { gameOver() }
}

fun Game.drawGameOver(cv: Canvas) {
    dim(cv, 200)
    textShadowC(cv, "ТЫ ПАЛ", w / 2f, h * 0.2f, 36f * u, c(0xFFFF5A4A))
    var y = h * 0.2f + 40f * u
    if (newRecord) { textC(cv, "🏆 НОВЫЙ РЕКОРД!", w / 2f, y, 16f * u, ACCENT); y += 26f * u }
    textC(cv, "Время: ${fmtTime(time)}", w / 2f, y, 16f * u, WHITE); y += 24f * u
    textC(cv, "Убийств: $kills", w / 2f, y, 16f * u, WHITE); y += 24f * u
    textC(cv, "Уровень: ${player.level}", w / 2f, y, 16f * u, WHITE); y += 34f * u
    cv.save(); cv.translate(w / 2f - 25f * u, y + 20f * u); drawGun(cv, gun, 4f * u); cv.restore()
    y += 70f * u
    val owned = Item.values().filter { player.n(it) > 0 }
    val line = owned.joinToString(" ") { it.glyph + (if (player.n(it) > 1) "×${player.n(it)}" else "") }
    for (ln in wrap(line, w - 40f * u, 15f * u)) { textC(cv, ln, w / 2f, y, 15f * u, WHITE); y += 20f * u }
    val by = h - 150f * u
    button(cv, 40f * u, by, w - 40f * u, by + 54f * u, "↻  Ещё раз", ACCENT) { startRun() }
    button(cv, 40f * u, by + 66f * u, w - 40f * u, by + 116f * u, "В меню", c(0xFFB0B4C0)) { goTo(State.MENU) }
}

// ───────────────────────── Ввод ─────────────────────────
fun Game.touch(ev: MotionEvent) {
    val am = ev.actionMasked
    val idx = ev.actionIndex
    val pid = ev.getPointerId(idx)
    val x = ev.getX(idx); val y = ev.getY(idx)
    if (state == State.PLAY) {
        when (am) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (hypot(x - pauseX(), y - pauseY()) < 26f * u) { goTo(State.PAUSE); return }
                if (player.equip != null && hypot(x - equipX(), y - equipY()) < 42f * u) { useEquip(); return }
                if (joyId == -1) { joyId = pid; joyOx = x; joyOy = y; joyX = x; joyY = y; jx = 0f; jy = 0f }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    if (ev.getPointerId(i) != joyId) continue
                    joyX = ev.getX(i); joyY = ev.getY(i)
                    val R = 55f * u
                    var dx = joyX - joyOx; var dy = joyY - joyOy
                    val d = hypot(dx, dy)
                    if (d > R) { joyOx = joyX - dx / d * R; joyOy = joyY - dy / d * R; dx = joyX - joyOx; dy = joyY - joyOy }
                    jx = dx / R; jy = dy / R
                    if (abs(jx) < 0.08f && abs(jy) < 0.08f) { jx = 0f; jy = 0f }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetJoy()
            MotionEvent.ACTION_POINTER_UP -> if (pid == joyId) resetJoy()
        }
        return
    }
    when (am) {
        MotionEvent.ACTION_DOWN -> { downState = state; downValid = stateT > 0.3f }
        MotionEvent.ACTION_UP -> {
            if (downValid && downState == state) {
                for (i in uiButtons.indices.reversed()) {
                    val b = uiButtons[i]
                    if (b.contains(x, y)) { b.act(); break }
                }
            }
            downValid = false
        }
    }
}
