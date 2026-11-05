package com.djkfc.pistoletik

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class State { MENU, PLAY, LEVELUP, PART, PAUSE, GAMEOVER }

class Game(val ctx: Context) {
    // экран
    var w = 1080f; var h = 1920f; var u = 2.7f; var zoom = 2.4f
    var state = State.MENU; var stateT = 0f; var animT = 0f
    val prefs = ctx.getSharedPreferences("pistoletik", Context.MODE_PRIVATE)
    var hero = Hero.GUNNER

    // забег
    var player = Player()
    var gun = Gun()
    val enemies = ArrayList<Enemy>()
    val bullets = ArrayList<Bullet>()
    val ebullets = ArrayList<Bullet>()
    val pickups = ArrayList<Pickup>()
    val chests = ArrayList<Chest>()
    val particles = ArrayList<Particle>()
    val texts = ArrayList<FloatText>()
    val beams = ArrayList<Beam>()
    val drones = ArrayList<Drone>()
    val vortices = ArrayList<Vortex>()
    val stickies = ArrayList<Sticky>()
    var nextId = 1
    var time = 0f; var kills = 0; var credits = 0f; var spawnT = 0f
    var chestT = 20f; var chestsOpened = 0
    var nextBoss = 180f; var bossIdx = 0; var boss: Enemy? = null; var swarmT = 75f
    var camX = 0f; var camY = 0f; var shake = 0f
    var banner = ""; var bannerT = 0f
    var pendingLevels = 0
    val pendingParts = ArrayList<PartDef>()
    val choices = ArrayList<Choice>()
    var rerolls = 0
    var chronoT = 0f; var novaCounter = 0; var goldMsgT = 0f
    var newRecord = false
    var selectedItem: Item? = null

    // джойстик / ввод
    var joyId = -1; var joyOx = 0f; var joyOy = 0f; var joyX = 0f; var joyY = 0f; var jx = 0f; var jy = 0f
    var downValid = false; var downState = State.MENU
    val uiButtons = ArrayList<Btn>()

    // отрисовка
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    val path = Path()
    val rect = RectF()
    val wrapCache = HashMap<String, List<String>>()

    // сетка для столкновений
    val GC = 48f
    val GN = 48
    val grid = Array(GN * GN) { ArrayList<Enemy>(8) }
    var gox = 0f; var goy = 0f

    fun resize(nw: Int, nh: Int) {
        w = nw.toFloat(); h = nh.toFloat()
        u = min(w, h) / 400f
        zoom = min(w, h) / 430f
        wrapCache.clear()
    }

    fun goTo(s: State) { state = s; stateT = 0f; downValid = false; resetJoy() }
    fun resetJoy() { joyId = -1; jx = 0f; jy = 0f }

    fun onPause() { if (state == State.PLAY) goTo(State.PAUSE) }

    fun onBack(): Boolean {
        when (state) {
            State.PLAY -> goTo(State.PAUSE)
            State.PAUSE -> goTo(State.PLAY)
            State.GAMEOVER -> goTo(State.MENU)
            State.MENU -> return false
            else -> {}
        }
        return true
    }

    // ═══════════════════════════ Забег ═══════════════════════════
    fun startRun() {
        player = Player(); gun = Gun()
        enemies.clear(); bullets.clear(); ebullets.clear(); pickups.clear(); chests.clear()
        particles.clear(); texts.clear(); beams.clear(); drones.clear(); vortices.clear(); stickies.clear()
        pendingParts.clear(); choices.clear()
        val p = player
        p.maxHpBase = hero.hp; p.speedBase = hero.speed; p.equip = hero.equip; p.color = hero.color
        for (id in hero.parts) Parts.byId[id]?.let { gun.parts[it.slot] = it }
        time = 0f; kills = 0; credits = 3f; spawnT = 0f; chestT = 20f; chestsOpened = 0
        nextBoss = 180f; bossIdx = 0; boss = null; swarmT = 75f; camX = 0f; camY = 0f
        pendingLevels = 0; rerolls = 0; chronoT = 0f; novaCounter = 0; newRecord = false
        p.xpNeed = xpFor(1)
        recomputeAll()
        p.hp = p.maxHp
        showBanner("Выживи! Собирай детали для пушки")
        goTo(State.PLAY)
    }

    fun xpFor(l: Int): Float = 6f + l * 5f + l * l * 0.35f

    fun recomputeAll() { recomputeGun(); recomputePlayer() }

    fun recomputeGun() {
        val s = GunStats()
        for (slot in Slot.values()) gun.parts[slot]?.apply?.invoke(s)
        val counts = IntArray(Origin.values().size)
        for (pd in gun.parts.values) counts[pd.origin.ordinal]++
        for (o in Origin.values()) {
            val n = counts[o.ordinal]
            if (n >= 3) applySet(o, s, if (n >= 5) 2 else 1)
        }
        val p = player
        s.fireRate *= 1f + 0.12f * p.n(Item.SYRINGE)
        s.damage *= 1f + 0.08f * p.n(Item.LEAD)
        s.damage *= 1f + 0.8f * p.n(Item.GLASS)
        s.crit += 0.08f * p.n(Item.LENS)
        s.bounce += p.n(Item.BOUNCY)
        s.count += p.n(Item.DOUBLE)
        s.critMult += 1f * p.n(Item.BLOODLUST)
        if (p.n(Item.BEHEMOTH) > 0) s.explode += 28f * p.n(Item.BEHEMOTH)
        s.spread = max(0f, s.spread)
        s.fireRate = max(0.3f, s.fireRate)
        s.size = max(2f, s.size)
        gun.stats = s
    }

    fun activeSets(): List<Pair<Origin, Int>> {
        val counts = IntArray(Origin.values().size)
        for (pd in gun.parts.values) counts[pd.origin.ordinal]++
        val res = ArrayList<Pair<Origin, Int>>()
        for (o in Origin.values()) if (counts[o.ordinal] >= 3) res.add(Pair(o, counts[o.ordinal]))
        return res
    }

    fun recomputePlayer() {
        val p = player
        val oldMax = p.maxHp
        var mh = p.maxHpBase + 20f * p.n(Item.MEDKIT)
        mh *= gun.stats.hpMul
        mh *= 0.7f.pow(p.n(Item.GLASS))
        p.maxHp = max(10f, mh)
        if (p.maxHp > oldMax) p.hp += p.maxHp - oldMax
        p.hp = min(p.hp, p.maxHp)
        p.speed = p.speedBase * (1f + 0.1f * p.n(Item.SNEAKERS))
        p.pickup = 55f * (1f + 0.4f * p.n(Item.MAGNET))
        p.regen = 0.6f * p.n(Item.MEDKIT)
        p.armor = 2f * p.n(Item.PLATE)
        p.maxShield = p.maxHp * 0.15f * p.n(Item.SHIELD)
        while (drones.size < p.n(Item.DRONE)) drones.add(Drone(drones.size * 2.1f))
    }

    fun showBanner(s: String) { banner = s; bannerT = 2.6f }

    // ═══════════════════════════ Обновление ═══════════════════════════
    fun update(dt0: Float) {
        val dt = min(dt0, 0.05f)
        animT += dt; stateT += dt
        when (state) {
            State.PLAY -> updatePlay(dt)
            State.GAMEOVER -> updateParticles(dt)
            else -> {}
        }
    }

    private fun updatePlay(dt: Float) {
        time += dt
        val p = player
        buildGrid()

        // движение
        var mx = jx; var my = jy
        val len = hypot(mx, my)
        if (len > 1f) { mx /= len; my /= len }
        val spd = p.speed * (if (p.chillT > 0f) 0.6f else 1f)
        p.x += mx * spd * dt; p.y += my * spd * dt
        if (len > 0.1f) { p.moveX = mx / len; p.moveY = my / len }
        p.walkT += dt * min(1f, len) * 10f

        p.invuln -= dt; p.hitFlash -= dt; p.domeT -= dt; p.chillT -= dt
        if (p.equipCd > 0f) p.equipCd -= dt
        p.hp = min(p.maxHp, p.hp + p.regen * dt)
        if (p.maxShield > 0f) {
            p.shieldDelay -= dt
            if (p.shieldDelay <= 0f) p.shield = min(p.maxShield, p.shield + p.maxShield * 0.5f * dt)
        } else p.shield = 0f
        chronoT -= dt
        camX += (p.x - camX) * min(1f, dt * 8f)
        camY += (p.y - camY) * min(1f, dt * 8f)
        shake = max(0f, shake - dt * 30f)
        bannerT -= dt; goldMsgT -= dt

        updateDirector(dt)
        updateGun(dt)
        updateDrones(dt)
        updateEnemies(dt)
        updateBullets(dt)
        updateEnemyBullets(dt)
        updateVortices(dt)
        updateStickies(dt)
        updatePickups(dt)
        updateChests(dt)
        updateParticles(dt)
        cleanup()

        if (state != State.PLAY) return
        if (p.hp <= 0f) {
            if (p.n(Item.TOTEM) > 0) {
                p.items[Item.TOTEM.ordinal]--
                p.hp = p.maxHp * 0.5f; p.invuln = 2.5f
                showBanner("🗿 Тотем спас тебя!")
                for (i in 0 until enemies.size) {
                    val e = enemies[i]
                    if (!e.dead && dist2(e.x, e.y, p.x, p.y) < 200f * 200f) damageEnemy(e, 9999f, false)
                }
                ring(p.x, p.y, 200f, c(0xFFFFE27A))
            } else {
                gameOver(); return
            }
        }
        if (pendingParts.isNotEmpty()) openPartSwap()
        else if (pendingLevels > 0) openLevelUp()
    }

    fun gameOver() {
        val bt = prefs.getFloat("bestTime", 0f)
        val bk = prefs.getInt("bestKills", 0)
        newRecord = time > bt
        prefs.edit()
            .putFloat("bestTime", max(bt, time))
            .putInt("bestKills", max(bk, kills))
            .putInt("bestLevel", max(prefs.getInt("bestLevel", 0), player.level))
            .putInt("runs", prefs.getInt("runs", 0) + 1)
            .apply()
        goTo(State.GAMEOVER)
    }

    fun buildGrid() {
        for (cell in grid) cell.clear()
        gox = player.x - GN * GC / 2f; goy = player.y - GN * GC / 2f
        for (i in 0 until enemies.size) {
            val e = enemies[i]
            if (e.dead) continue
            val cx = ((e.x - gox) / GC).toInt(); val cy = ((e.y - goy) / GC).toInt()
            if (cx in 0 until GN && cy in 0 until GN) grid[cy * GN + cx].add(e)
        }
    }

    inline fun forNear(x: Float, y: Float, r: Float, f: (Enemy) -> Unit) {
        val x0 = max(0, ((x - r - gox) / GC).toInt()); val x1 = min(GN - 1, ((x + r - gox) / GC).toInt())
        val y0 = max(0, ((y - r - goy) / GC).toInt()); val y1 = min(GN - 1, ((y + r - goy) / GC).toInt())
        if (x + r < gox || y + r < goy) return
        for (cy in y0..y1) for (cx in x0..x1) {
            val cell = grid[cy * GN + cx]
            for (k in 0 until cell.size) f(cell[k])
        }
    }

    fun nearestEnemy(x: Float, y: Float, maxR: Float, b: Bullet? = null, exclude: Enemy? = null): Enemy? {
        var best: Enemy? = null; var bd = maxR * maxR
        forNear(x, y, maxR) { e ->
            if (!e.dead && e !== exclude && (b == null || b.canHit(e.id))) {
                val d = dist2(x, y, e.x, e.y)
                if (d < bd) { bd = d; best = e }
            }
        }
        return best
    }

    // ── Режиссёр волн (как в Risk of Rain)
    private fun updateDirector(dt: Float) {
        val m = time / 60f
        credits += dt * (1.0f + m * 0.9f + m * m * 0.06f)
        spawnT -= dt
        if (spawnT <= 0f) {
            spawnT = 0.4f
            var guard = 0
            val avail = EType.values().filter { it.cost > 0 && it.minTime <= time }
            val total = avail.sumOf { it.weight.toDouble() }.toFloat()
            while (credits >= 1f && enemies.size < 300 && guard++ < 14) {
                var r = rf(0f, total); var t = avail[0]
                for (a in avail) { r -= a.weight; if (r <= 0f) { t = a; break } }
                val elite = time > 45f && chance(min(0.15f, 0.01f + time / 900f))
                val cost = t.cost * (if (elite) 4f else 1f)
                if (cost > credits) break
                credits -= cost
                val a = rf(0f, TAU); val rr = spawnRadius()
                val aff = if (elite) Affix.values()[rnd.nextInt(Affix.values().size)] else null
                enemies.add(makeEnemy(t, player.x + cos(a) * rr, player.y + sin(a) * rr, aff))
            }
        }
        swarmT -= dt
        if (swarmT <= 0f) {
            swarmT = 70f
            val n = 22 + (m * 4).toInt()
            val rr = spawnRadius() * 0.85f
            for (i in 0 until n) {
                val a = i * TAU / n
                enemies.add(makeEnemy(EType.BAT, player.x + cos(a) * rr, player.y + sin(a) * rr, null))
            }
            showBanner("🦇 Стая окружает!")
        }
        if (boss == null && time >= nextBoss) {
            val types = arrayOf(EType.GOLEM, EType.HIVE, EType.UFO)
            val t = types[bossIdx % 3]
            val a = rf(0f, TAU); val rr = spawnRadius()
            val b = makeEnemy(t, player.x + cos(a) * rr, player.y + sin(a) * rr, null)
            b.maxHp *= 1f + bossIdx * 0.9f; b.hp = b.maxHp
            enemies.add(b); boss = b; bossIdx++
            nextBoss += 180f
            showBanner("⚠ БОСС: ${t.title}")
            shake = 8f
        }
        chestT -= dt
        if (chestT <= 0f) {
            chestT = rf(35f, 55f)
            if (chests.size < 3) {
                val a = rf(0f, TAU); val rr = rf(250f, 450f)
                chests.add(Chest(player.x + cos(a) * rr, player.y + sin(a) * rr, 15 + chestsOpened * 12))
            }
        }
    }

    fun spawnRadius(): Float = hypot(w, h) / 2f / zoom + 40f

    fun makeEnemy(t: EType, x: Float, y: Float, aff: Affix?): Enemy {
        val e = Enemy(t, nextId++)
        e.x = x; e.y = y
        val m = time / 60f
        val hpMul = 1f + m * 0.3f + m * m * 0.035f
        e.maxHp = t.hp * hpMul; e.speed = t.speed * rf(0.9f, 1.1f); e.r = t.r
        e.dmg = t.dmg * (1f + m * 0.12f); e.xp = t.xp
        if (aff != null) {
            e.elite = aff; e.maxHp *= 4f; e.r *= 1.3f; e.xp *= 4; e.dmg *= 1.3f
            when (aff) {
                Affix.FIRE -> e.dmg *= 1.5f
                Affix.FAST -> e.speed *= 1.6f
                Affix.TOUGH -> e.maxHp *= 2f
                Affix.ICE -> {}
            }
        }
        e.hp = e.maxHp
        return e
    }

    // ── Пушка
    private fun updateGun(dt: Float) {
        val s = gun.stats; val p = player
        gun.cooldown -= dt
        if (gun.burstLeft > 0) {
            gun.burstTimer -= dt
            if (gun.burstTimer <= 0f) { fireVolley(gun.burstAngle); gun.burstLeft--; gun.burstTimer = 0.07f }
        }
        val range = min(s.speed * s.life, 470f).coerceAtLeast(200f)
        val target = nearestEnemy(p.x, p.y, range)
        val ang: Float
        if (target != null) {
            ang = atan2(target.y - p.y, target.x - p.x)
            p.aimX = cos(ang); p.aimY = sin(ang)
        } else {
            p.aimX += (p.moveX - p.aimX) * min(1f, dt * 6f); p.aimY += (p.moveY - p.aimY) * min(1f, dt * 6f)
            ang = atan2(p.aimY, p.aimX)
        }
        val rateMul = when (s.mode) {
            FireMode.BURST -> 0.45f; FireMode.BEAM -> 0.8f; FireMode.ORBIT -> 0.6f
            FireMode.BOOMERANG -> 0.7f; FireMode.MINE -> 0.8f; FireMode.SPIRAL -> 1.8f
            FireMode.NOVA -> 0.35f; FireMode.NORMAL -> 1f
        }
        val ambient = s.mode == FireMode.ORBIT || s.mode == FireMode.SPIRAL || s.mode == FireMode.NOVA
        val canFire = target != null || (ambient && nearestEnemy(p.x, p.y, 320f) != null)
        if (gun.cooldown <= 0f && canFire) {
            gun.cooldown = 1f / (s.fireRate * rateMul)
            when (s.mode) {
                FireMode.SPIRAL -> { gun.spiralAngle += rad(41f); fireVolley(gun.spiralAngle) }
                FireMode.BURST -> { fireVolley(ang); gun.burstLeft = s.burst - 1; gun.burstTimer = 0.07f; gun.burstAngle = ang }
                FireMode.NOVA -> {
                    val n = 6 + s.count * 4
                    for (i in 0 until n) spawnPlayerBullet(ang + i * TAU / n)
                }
                FireMode.BEAM -> fireBeams(ang)
                else -> fireVolley(ang)
            }
            for (i in 0 until 3) {
                particles.add(Particle(p.x + cos(ang) * 22f, p.y + sin(ang) * 22f,
                    cos(ang) * rf(40f, 120f) + rf(-30f, 30f), sin(ang) * rf(40f, 120f) + rf(-30f, 30f),
                    0.15f, 2.5f, s.color))
            }
        }
    }

    private fun baseDirs(ang: Float, out: FloatArray): Int {
        val s = gun.stats
        var n = 0
        out[n++] = ang
        if (s.rear) out[n++] = ang + PI_F
        if (s.sides) { out[n++] = ang + PI_F / 2f; out[n++] = ang - PI_F / 2f }
        return n
    }

    private val dirBuf = FloatArray(4)

    fun fireVolley(ang: Float) {
        val s = gun.stats
        val nd = baseDirs(ang, dirBuf)
        val n = s.count
        val fan = rad(min(10f * (n - 1) + (if (n > 1) s.spread else 0f), 150f))
        val jitter = rad(s.spread * 0.5f)
        for (d in 0 until nd) {
            val base = dirBuf[d]
            for (i in 0 until n) {
                val off = if (n > 1) -fan / 2f + fan * i / (n - 1) else 0f
                spawnPlayerBullet(base + off + rf(-jitter, jitter))
            }
        }
    }

    fun spawnPlayerBullet(a: Float) {
        val s = gun.stats; val p = player
        val b = Bullet()
        b.x = p.x + cos(a) * 16f; b.y = p.y + sin(a) * 16f
        b.vx = cos(a) * s.speed; b.vy = sin(a) * s.speed; b.speed0 = s.speed
        b.dmg = s.damage; b.size = s.size; b.life = s.life; b.maxLife = s.life
        b.pierce = s.pierce; b.bounce = s.bounce; b.homing = s.homing
        b.shape = s.shape; b.color = s.color; b.wave = s.wave
        b.explode = s.explode; b.split = s.split; b.chain = s.chain; b.burn = s.burn
        b.freeze = s.freeze; b.pull = s.pull; b.accel = s.accel; b.growth = s.growth
        when (s.mode) {
            FireMode.ORBIT -> {
                var cnt = 0
                for (i in 0 until bullets.size) if (bullets[i].kind == BKind.ORBIT && !bullets[i].dead) cnt++
                if (cnt >= 4 + s.count * 4) return
                b.kind = BKind.ORBIT; b.multiHit = true
                b.orbitA = a; b.orbitR = 14f; b.orbitMax = rf(38f, 80f)
                b.life = s.life * 3.5f; b.maxLife = b.life
            }
            FireMode.BOOMERANG -> { b.kind = BKind.BOOMERANG; b.multiHit = true; b.life = s.life * 1.8f; b.maxLife = b.life }
            FireMode.MINE -> {
                b.kind = BKind.MINE; b.vx *= 0.6f; b.vy *= 0.6f; b.life = s.life * 5f; b.maxLife = b.life
                b.explode = max(b.explode, 45f); b.homing = 0f; b.wave = 0f
            }
            else -> {}
        }
        bullets.add(b)
    }

    fun fireBeams(ang: Float) {
        val s = gun.stats; val p = player
        val nd = baseDirs(ang, dirBuf)
        val n = s.count
        val fan = rad(min(12f * (n - 1), 120f))
        val len = min(s.speed * s.life, 520f) * (1f + s.pierce * 0.05f)
        for (d in 0 until nd) for (i in 0 until n) {
            val a = dirBuf[d] + (if (n > 1) -fan / 2f + fan * i / (n - 1) else 0f)
            val dx = cos(a); val dy = sin(a)
            val sx = p.x + dx * 16f; val sy = p.y + dy * 16f
            val ex = sx + dx * len; val ey = sy + dy * len
            beams.add(Beam(sx, sy, ex, ey, 0.12f, s.color, s.size * 1.1f, false))
            // проверка врагов вдоль луча
            val hits = ArrayList<Enemy>()
            val steps = (len / 40f).toInt() + 1
            for (k in 0..steps) {
                val cx = sx + dx * len * k / steps; val cy = sy + dy * len * k / steps
                forNear(cx, cy, 45f) { e ->
                    if (!e.dead && !hits.contains(e)) {
                        // расстояние до отрезка
                        val t = clampf(((e.x - sx) * dx + (e.y - sy) * dy), 0f, len)
                        val px = sx + dx * t; val py = sy + dy * t
                        val rr = e.r + s.size
                        if (dist2(px, py, e.x, e.y) < rr * rr) hits.add(e)
                    }
                }
            }
            val fake = Bullet()
            fake.dmg = s.damage; fake.size = s.size; fake.burn = s.burn; fake.freeze = s.freeze
            fake.explode = s.explode; fake.chain = s.chain; fake.split = 0; fake.multiHit = true
            fake.vx = dx * 300f; fake.vy = dy * 300f; fake.color = s.color
            for (e in hits) { fake.x = e.x; fake.y = e.y; hitEnemy(fake, e) }
        }
    }

    // ── Дроны
    private fun updateDrones(dt: Float) {
        val p = player; val s = gun.stats
        for ((i, d) in drones.withIndex()) {
            d.angle += dt * 1.6f
            val tx = p.x + cos(d.angle + i * 2.1f) * 34f; val ty = p.y + sin(d.angle + i * 2.1f) * 34f - 10f
            d.x += (tx - d.x) * min(1f, dt * 6f); d.y += (ty - d.y) * min(1f, dt * 6f)
            d.cd -= dt
            if (d.cd <= 0f) {
                val t = nearestEnemy(d.x, d.y, 340f)
                if (t != null) {
                    d.cd = 0.7f
                    val a = atan2(t.y - d.y, t.x - d.x)
                    val b = Bullet()
                    b.x = d.x; b.y = d.y; b.vx = cos(a) * 520f; b.vy = sin(a) * 520f
                    b.dmg = s.damage * 0.6f; b.size = 3.5f; b.life = 0.9f; b.maxLife = 0.9f
                    b.color = c(0xFF7CFF7C); b.kind = BKind.DRONE; b.proc = 0.5f; b.child = true
                    bullets.add(b)
                } else d.cd = 0.2f
            }
        }
    }

    // ── Враги
    private fun updateEnemies(dt: Float) {
        val p = player
        val frozen = chronoT > 0f
        val n = enemies.size
        for (i in 0 until n) {
            val e = enemies[i]
            if (e.dead) continue
            e.t += dt; e.flash -= dt
            if (e.burnT > 0f) {
                e.burnT -= dt; e.burnTick -= dt
                if (e.burnTick <= 0f) {
                    e.burnTick = 0.4f
                    damageEnemy(e, e.burnDps * 0.4f, false, c(0xFFFF9040))
                    particles.add(Particle(e.x + rf(-e.r, e.r), e.y, rf(-10f, 10f), rf(-50f, -20f), 0.5f, 2.5f, c(0xFFFF8030)))
                }
                if (e.dead) continue
            }
            if (e.slowT > 0f) e.slowT -= dt
            val dx = p.x - e.x; val dy = p.y - e.y
            val d = sqrt(dx * dx + dy * dy) + 0.001f
            val nx = dx / d; val ny = dy / d
            var spd = e.speed * (if (e.slowT > 0f) 1f - e.slowAmt else 1f)
            if (frozen) spd = 0f
            var mvx = nx * spd; var mvy = ny * spd
            if (!frozen) when (e.type) {
                EType.SPITTER -> {
                    if (d < 190f) { mvx = -nx * spd * 0.6f; mvy = -ny * spd * 0.6f }
                    else if (d < 270f) { mvx = -ny * spd * 0.5f; mvy = nx * spd * 0.5f }
                    e.cd -= dt
                    if (e.cd <= 0f && d < 420f) { e.cd = rf(1.8f, 2.6f); enemyShoot(e, atan2(dy, dx), 190f, e.dmg) }
                }
                EType.GHOST -> { val wob = sin(e.t * 3f) * 0.9f; mvx += -ny * spd * wob; mvy += nx * spd * wob }
                EType.BOMBER -> if (d < e.r + p.r + 12f) { bomberExplode(e); continue }
                EType.GOLEM -> {
                    e.cd -= dt
                    if (e.cd <= 0f) {
                        e.cd = 3.4f
                        val k = 18
                        for (j in 0 until k) enemyShoot(e, j * TAU / k + e.phase, 150f, e.dmg * 0.6f)
                        e.phase += 0.17f; shake = 5f
                    }
                }
                EType.HIVE -> {
                    e.cd -= dt
                    if (e.cd <= 0f) {
                        e.cd = 3f
                        for (j in 0 until 5) {
                            val a = rf(0f, TAU)
                            enemies.add(makeEnemy(EType.BAT, e.x + cos(a) * 40f, e.y + sin(a) * 40f, null))
                        }
                    }
                }
                EType.UFO -> {
                    e.cd -= dt
                    if (e.cd <= 0f && (e.t % 5f) < 3f) {
                        e.cd = 0.13f; e.phase += 0.42f
                        enemyShoot(e, e.phase, 150f, e.dmg * 0.5f)
                        enemyShoot(e, e.phase + PI_F, 150f, e.dmg * 0.5f)
                    }
                    mvx = mvx * 0.8f - ny * spd * 0.6f; mvy = mvy * 0.8f + nx * spd * 0.6f
                }
                else -> {}
            }
            e.x += (mvx + e.vx) * dt; e.y += (mvy + e.vy) * dt
            val decay = exp(-7f * dt); e.vx *= decay; e.vy *= decay
            if (!e.type.boss) {
                forNear(e.x, e.y, e.r + 20f) { o ->
                    if (o !== e && !o.dead) {
                        val ox = e.x - o.x; val oy = e.y - o.y
                        val dd = ox * ox + oy * oy; val rr = e.r + o.r
                        if (dd < rr * rr && dd > 0.01f) {
                            val dl = sqrt(dd); val push = (rr - dl) / dl * 0.35f
                            e.x += ox * push; e.y += oy * push
                        }
                    }
                }
            }
            if (d < e.r + p.r) {
                if (p.domeT > 0f) { e.vx -= nx * 400f; e.vy -= ny * 400f }
                else {
                    hurtPlayer(e.dmg)
                    if (e.elite == Affix.ICE) p.chillT = 1.5f
                }
            }
            if (d > 1100f && !e.type.boss) {
                val a = atan2(p.moveY, p.moveX) + rf(-1f, 1f); val rr = spawnRadius()
                e.x = p.x + cos(a) * rr; e.y = p.y + sin(a) * rr
            }
        }
    }

    fun enemyShoot(e: Enemy, a: Float, speed: Float, dmg: Float) {
        val b = Bullet()
        b.x = e.x + cos(a) * e.r; b.y = e.y + sin(a) * e.r
        b.vx = cos(a) * speed; b.vy = sin(a) * speed
        b.dmg = dmg; b.size = 5f; b.life = 5f; b.maxLife = 5f
        b.kind = BKind.ENEMY; b.color = c(0xFFFF4FD8)
        ebullets.add(b)
    }

    private fun bomberExplode(e: Enemy) {
        val p = player
        ring(e.x, e.y, 60f, c(0xFFFF8A2A))
        if (dist2(e.x, e.y, p.x, p.y) < (60f + p.r) * (60f + p.r)) hurtPlayer(e.dmg)
        killEnemy(e)
    }

    fun hurtPlayer(d: Float) {
        val p = player
        if (p.invuln > 0f || p.domeT > 0f) return
        var dmg = max(1f, d - p.armor)
        p.shieldDelay = 3f
        if (p.shield > 0f) { val a = min(p.shield, dmg); p.shield -= a; dmg -= a }
        p.hp -= dmg; p.invuln = 0.45f; p.hitFlash = 0.25f
        shake = max(shake, 4f)
        texts.add(FloatText(p.x, p.y - 20f, "-${dmg.toInt()}", c(0xFFFF5050), 0.8f, 13f))
    }

    fun heal(a: Float) {
        val p = player
        if (a <= 0f || p.hp >= p.maxHp) return
        p.hp = min(p.maxHp, p.hp + a)
    }

    // ── Пули
    private fun updateBullets(dt: Float) {
        val p = player
        var i = 0
        while (i < bullets.size) {
            val b = bullets[i]; i++
            if (b.dead) continue
            b.t += dt; b.life -= dt; b.tickHits(dt)
            when (b.kind) {
                BKind.ORBIT -> {
                    b.orbitA += dt * 3.4f
                    b.orbitR += (b.orbitMax - b.orbitR) * min(1f, dt * 4f)
                    b.x = p.x + cos(b.orbitA) * b.orbitR; b.y = p.y + sin(b.orbitA) * b.orbitR
                    b.vx = -sin(b.orbitA) * 250f; b.vy = cos(b.orbitA) * 250f
                }
                BKind.BOOMERANG -> {
                    val turn = b.maxLife * 0.4f
                    if (b.t < turn) {
                        val k = 1f - b.t / turn
                        b.x += b.vx * k * dt; b.y += b.vy * k * dt
                    } else {
                        val dx = p.x - b.x; val dy = p.y - b.y; val d = sqrt(dx * dx + dy * dy) + 0.01f
                        val sp = b.speed0 * min(1.6f, (b.t - turn) / turn * 1.2f + 0.2f)
                        b.x += dx / d * sp * dt; b.y += dy / d * sp * dt
                        if (d < 16f) b.dead = true
                    }
                    b.spin += dt * 15f
                }
                BKind.MINE -> {
                    val k = exp(-3.5f * dt); b.vx *= k; b.vy *= k
                    b.x += b.vx * dt; b.y += b.vy * dt
                }
                else -> {
                    if (b.homing > 0f) {
                        b.retarget -= dt
                        val tg = b.target
                        if (b.retarget <= 0f || tg == null || tg.dead) {
                            b.retarget = 0.2f; b.target = nearestEnemy(b.x, b.y, 260f, b)
                        }
                        val t = b.target
                        if (t != null) {
                            val desired = atan2(t.y - b.y, t.x - b.x)
                            val cur = atan2(b.vy, b.vx)
                            var diff = desired - cur
                            while (diff > PI_F) diff -= TAU
                            while (diff < -PI_F) diff += TAU
                            val mt = b.homing * dt
                            val na = cur + clampf(diff, -mt, mt)
                            val sp = hypot(b.vx, b.vy)
                            b.vx = cos(na) * sp; b.vy = sin(na) * sp
                        }
                    }
                    if (b.accel != 0f) {
                        val sp = hypot(b.vx, b.vy) + 0.01f
                        val ns = min(sp + b.accel * dt, 2000f)
                        b.vx *= ns / sp; b.vy *= ns / sp
                    }
                    b.x += b.vx * dt; b.y += b.vy * dt
                    if (b.wave > 0f) {
                        val sp = hypot(b.vx, b.vy) + 0.01f
                        val px = -b.vy / sp; val py = b.vx / sp
                        val off = sin(b.t * 11f) * b.wave * 16f
                        val dOff = off - b.waveOff; b.waveOff = off
                        b.x += px * dOff; b.y += py * dOff
                    }
                    if (b.kind == BKind.MISSILE && chance(0.6f)) {
                        particles.add(Particle(b.x, b.y, rf(-15f, 15f), rf(-15f, 15f), 0.4f, 3f, c(0xFF999999)))
                    }
                }
            }
            if (b.growth > 0f) {
                val g = 1f + b.growth * dt
                if (b.size < 30f) { b.size *= g; b.dmg *= 1f + b.growth * 0.5f * dt }
            }
            if (b.pull > 0f && chronoT <= 0f) {
                forNear(b.x, b.y, b.pull) { e ->
                    if (!e.dead && !e.type.boss) {
                        val dx = b.x - e.x; val dy = b.y - e.y
                        val d = sqrt(dx * dx + dy * dy) + 0.01f
                        if (d < b.pull) { e.x += dx / d * 110f * dt; e.y += dy / d * 110f * dt }
                    }
                }
            }
            if (!b.dead) {
                forNear(b.x, b.y, b.size + 45f) { e ->
                    if (!b.dead && !e.dead) {
                        val rr = e.r + b.size
                        if (dist2(b.x, b.y, e.x, e.y) < rr * rr && b.canHit(e.id)) hitEnemy(b, e)
                    }
                }
            }
            if (!b.dead && b.life <= 0f) {
                if (b.kind == BKind.MINE) explosion(b.x, b.y, b.explode, b.dmg, b.color, b.burn)
                killBullet(b)
            }
        }
    }

    fun killBullet(b: Bullet) {
        if (b.dead) return
        b.dead = true
        if (b.split > 0 && !b.child) {
            val n = b.split
            val a0 = rf(0f, TAU)
            for (k in 0 until n) {
                val a = a0 + k * TAU / n
                val c2 = Bullet()
                c2.x = b.x; c2.y = b.y
                val sp = max(260f, b.speed0 * 0.8f)
                c2.vx = cos(a) * sp; c2.vy = sin(a) * sp; c2.speed0 = sp
                c2.dmg = b.dmg * 0.5f; c2.size = max(2.5f, b.size * 0.6f); c2.life = 0.45f; c2.maxLife = 0.45f
                c2.shape = b.shape; c2.color = b.color; c2.child = true; c2.proc = 0.3f
                c2.burn = b.burn; c2.freeze = b.freeze; c2.homing = b.homing
                for (j in 0 until 8) { c2.hitIds[j] = b.hitIds[j]; c2.hitT[j] = b.hitT[j] }
                bullets.add(c2)
            }
        }
    }

    fun hitEnemy(b: Bullet, e: Enemy) {
        val s = gun.stats; val p = player
        var dmg = b.dmg
        val crit = chance(s.crit)
        if (crit) dmg *= s.critMult
        val cb = p.n(Item.CROWBAR)
        if (cb > 0 && e.hp >= e.maxHp * 0.9f) dmg *= 1f + 0.5f * cb
        b.mark(e.id, if (b.multiHit) 0.45f else 99f)
        if (b.kind == BKind.MINE) {
            explosion(b.x, b.y, b.explode, dmg, b.color, b.burn)
            b.dead = true
            return
        }
        damageEnemy(e, dmg, crit)
        if (crit && p.n(Item.BLOODLUST) > 0) heal(1f * p.n(Item.BLOODLUST))
        val sp = hypot(b.vx, b.vy) + 0.01f
        val kb = if (e.type.boss) 0.1f else if (e.elite != null) 0.4f else 1f
        e.vx += b.vx / sp * s.knock * kb * 2f; e.vy += b.vy / sp * s.knock * kb * 2f
        if (b.burn > 0f) ignite(e, b.burn)
        if (b.freeze > 0f) slow(e, b.freeze)
        if (s.leech > 0f && b.kind != BKind.DRONE) heal(s.leech)
        if (b.explode > 0f && b.kind != BKind.MISSILE) explosion(b.x, b.y, b.explode, dmg * 0.5f, b.color, 0f)
        if (b.kind == BKind.MISSILE) explosion(b.x, b.y, max(35f, b.explode), dmg * 0.6f, c(0xFFFF9A40), 0f)
        if (b.chain > 0) lightning(e, b.chain, dmg * 0.5f)
        if (b.proc > 0f) procItems(e, dmg, b.proc)
        if (b.multiHit) return
        if (b.bounce > 0) {
            val t = nearestEnemy(e.x, e.y, 240f, b, e)
            if (t != null) {
                b.bounce--
                val a = atan2(t.y - b.y, t.x - b.x)
                b.vx = cos(a) * sp; b.vy = sin(a) * sp
                b.life = max(b.life, 0.5f)
                return
            }
        }
        if (b.pierce > 0) { b.pierce--; return }
        killBullet(b)
    }

    fun procItems(e: Enemy, dmg: Float, coef: Float) {
        val p = player
        val gu = p.n(Item.GUITAR)
        if (gu > 0 && chance(0.2f * coef)) lightning(e, 2 + gu, dmg * 0.8f)
        val ro = p.n(Item.ROCKET)
        if (ro > 0 && chance(0.1f * ro * coef)) spawnMissile(p.x, p.y, dmg * 3f)
        val st = p.n(Item.STICKY)
        if (st > 0 && chance(0.05f * st * coef)) stickies.add(Sticky(e, 1f, dmg * 1.8f))
        val fr = p.n(Item.FIRE_RING)
        if (fr > 0 && chance(0.15f * coef)) ignite(e, 4f * fr + dmg * 0.3f)
        val ir = p.n(Item.ICE_RING)
        if (ir > 0 && chance(0.15f * coef)) slow(e, min(0.75f, 0.35f + 0.1f * ir))
        val le = p.n(Item.LEECH)
        if (le > 0) heal(0.25f * le * coef)
        val sn = p.n(Item.SUPERNOVA)
        if (sn > 0) {
            novaCounter++
            if (novaCounter >= 12) {
                novaCounter = 0
                val k = 12 * sn
                for (j in 0 until k) {
                    val a = j * TAU / k
                    val nb = Bullet()
                    nb.x = e.x; nb.y = e.y; nb.vx = cos(a) * 420f; nb.vy = sin(a) * 420f
                    nb.dmg = gun.stats.damage; nb.size = 4f; nb.life = 0.6f; nb.maxLife = 0.6f
                    nb.color = c(0xFFFFF0A0); nb.shape = Shape.STAR; nb.child = true; nb.proc = 0f
                    nb.mark(e.id, 99f)
                    bullets.add(nb)
                }
                ring(e.x, e.y, 50f, c(0xFFFFF0A0))
            }
        }
    }

    fun ignite(e: Enemy, dps: Float) { e.burnDps = max(e.burnDps, dps); e.burnT = 3f }
    fun slow(e: Enemy, amt: Float) { e.slowAmt = max(if (e.slowT > 0f) e.slowAmt else 0f, if (e.type.boss) amt * 0.4f else amt); e.slowT = 2f }

    fun spawnMissile(x: Float, y: Float, dmg: Float) {
        val b = Bullet()
        val a = rf(0f, TAU)
        b.x = x; b.y = y; b.vx = cos(a) * 250f; b.vy = sin(a) * 250f; b.speed0 = 250f
        b.dmg = dmg; b.size = 4.5f; b.life = 3f; b.maxLife = 3f
        b.homing = 7f; b.accel = 350f; b.shape = Shape.TRIANGLE; b.color = c(0xFFFF9A40)
        b.kind = BKind.MISSILE; b.proc = 0f; b.child = true
        bullets.add(b)
    }

    fun damageEnemy(e: Enemy, dmg: Float, crit: Boolean, col: Int = 0) {
        if (e.dead) return
        e.hp -= dmg; e.flash = 0.08f
        if (texts.size < 70) {
            val color = if (col != 0) col else if (crit) c(0xFFFF5A3A) else c(0xFFFFFFFF)
            texts.add(FloatText(e.x + rf(-6f, 6f), e.y - e.r, if (crit) "${dmg.toInt()}!" else "${dmg.toInt()}",
                color, 0.6f, if (crit) 13f else 10f))
        }
        val ex = gun.stats.execute
        if (ex > 0f && !e.type.boss && e.hp < e.maxHp * ex) e.hp = 0f
        if (e.hp <= 0f) killEnemy(e)
    }

    fun explosion(x: Float, y: Float, r: Float, dmg: Float, color: Int, burn: Float) {
        forNear(x, y, r + 40f) { e ->
            if (!e.dead) {
                val rr = r + e.r
                if (dist2(x, y, e.x, e.y) < rr * rr) {
                    damageEnemy(e, dmg, false, c(0xFFFFC060))
                    if (burn > 0f && !e.dead) ignite(e, burn)
                }
            }
        }
        ring(x, y, r, color)
        if (particles.size < 500) for (k in 0 until 6) {
            val a = rf(0f, TAU); val sp = rf(40f, 160f)
            particles.add(Particle(x, y, cos(a) * sp, sin(a) * sp, rf(0.2f, 0.45f), rf(2f, 4f), color))
        }
        shake = max(shake, min(6f, r / 15f))
    }

    fun ring(x: Float, y: Float, r: Float, color: Int) {
        val pr = Particle(x, y, 0f, 0f, 0.3f, r * 0.3f, color, true)
        pr.grow = r * 0.7f / 0.3f
        particles.add(pr)
    }

    fun lightning(start: Enemy, jumps: Int, dmg: Float) {
        var cur = start
        val visited = ArrayList<Enemy>()
        visited.add(start)
        for (j in 0 until jumps) {
            var best: Enemy? = null; var bd = 170f * 170f
            forNear(cur.x, cur.y, 170f) { e ->
                if (!e.dead && !visited.contains(e)) {
                    val d = dist2(cur.x, cur.y, e.x, e.y)
                    if (d < bd) { bd = d; best = e }
                }
            }
            val nx = best ?: break
            beams.add(Beam(cur.x, cur.y, nx.x, nx.y, 0.18f, c(0xFF8FE3FF), 2f, true))
            visited.add(nx)
            damageEnemy(nx, dmg, false, c(0xFF8FE3FF))
            cur = nx
        }
    }

    fun killEnemy(e: Enemy) {
        if (e.dead) return
        e.dead = true; kills++
        val p = player
        if (particles.size < 500) for (k in 0 until 7) {
            val a = rf(0f, TAU); val sp = rf(30f, 140f)
            particles.add(Particle(e.x, e.y, cos(a) * sp, sin(a) * sp, rf(0.25f, 0.5f), rf(2f, 4.5f), e.type.color))
        }
        dropXp(e.x, e.y, e.xp)
        if (chance(0.2f)) pickups.add(Pickup(PKind.GOLD, e.x, e.y, ri(1, 3)))
        if (chance(0.012f)) pickups.add(Pickup(PKind.HEAL, e.x, e.y))
        if (chance(0.003f)) pickups.add(Pickup(PKind.MAGNET, e.x, e.y))
        if (e.elite != null) {
            pickups.add(Pickup(PKind.GOLD, e.x, e.y, ri(6, 12)))
            if (chance(0.55f)) dropPart(e.x, e.y, rollRarity())
        }
        if (e.type.boss) {
            dropPart(e.x - 20f, e.y, max(1, rollRarity()))
            dropPart(e.x + 20f, e.y, max(1, rollRarity()))
            val it = randomItem(if (chance(0.4f)) 2 else 1)
            val pk = Pickup(PKind.ITEM, e.x, e.y + 20f); pk.item = it; pickups.add(pk)
            if (chance(0.5f)) {
                val eq = Pickup(PKind.EQUIP, e.x, e.y - 24f)
                eq.equip = Equip.values()[rnd.nextInt(Equip.values().size)]
                pickups.add(eq)
            }
            pickups.add(Pickup(PKind.GOLD, e.x, e.y, 30))
            if (boss === e) boss = null
            showBanner("🏆 ${e.type.title} повержен!")
            shake = 10f
        }
        val fang = p.n(Item.FANG); if (fang > 0) heal(2f * fang)
        val gas = p.n(Item.GAS)
        if (gas > 0) explosion(e.x, e.y, 36f + 12f * gas, gun.stats.damage * 1.1f, c(0xFFFF7030), 4f * gas)
        if (e.type == EType.SPLITTER) {
            for (k in 0 until 3) {
                val a = rf(0f, TAU)
                enemies.add(makeEnemy(EType.MINI, e.x + cos(a) * 12f, e.y + sin(a) * 12f, null))
            }
        }
    }

    fun dropXp(x: Float, y: Float, v: Int) {
        var xpCount = 0
        for (i in 0 until pickups.size) if (pickups[i].kind == PKind.XP) xpCount++
        if (xpCount > 320) {
            for (i in pickups.indices.reversed()) {
                val k = pickups[i]
                if (k.kind == PKind.XP && !k.dead) { k.value += v; return }
            }
        }
        pickups.add(Pickup(PKind.XP, x, y, v))
    }

    fun dropPart(x: Float, y: Float, rarity: Int) {
        val pk = Pickup(PKind.PART, x, y)
        pk.part = randomPart(rarity)
        pickups.add(pk)
    }

    fun luck(): Int = player.n(Item.CLOVER)

    fun rollRarity(): Int {
        val r = rnd.nextFloat()
        val l = luck()
        return when {
            r < 0.05f + 0.03f * l -> 2
            r < 0.33f + 0.05f * l -> 1
            else -> 0
        }
    }

    fun randomPart(rarity: Int): PartDef {
        val pool = Parts.all.filter { it.rarity == rarity }
        val list = if (pool.isEmpty()) Parts.all else pool
        return list[rnd.nextInt(list.size)]
    }

    fun randomItem(rarity: Int): Item {
        val pool = Item.values().filter { it.rarity == rarity }
        return pool[rnd.nextInt(pool.size)]
    }

    private fun updateEnemyBullets(dt: Float) {
        val p = player
        val frozen = chronoT > 0f
        for (i in 0 until ebullets.size) {
            val b = ebullets[i]
            if (b.dead) continue
            if (!frozen) { b.x += b.vx * dt; b.y += b.vy * dt; b.life -= dt }
            b.t += dt
            if (b.life <= 0f) { b.dead = true; continue }
            val rr = b.size + p.r
            if (p.domeT > 0f && dist2(b.x, b.y, p.x, p.y) < 50f * 50f) { b.dead = true; continue }
            if (dist2(b.x, b.y, p.x, p.y) < rr * rr) { hurtPlayer(b.dmg); b.dead = true }
        }
    }

    private fun updateVortices(dt: Float) {
        for (v in vortices) {
            v.life -= dt; v.t += dt
            forNear(v.x, v.y, v.r) { e ->
                if (!e.dead) {
                    val dx = v.x - e.x; val dy = v.y - e.y
                    val d = sqrt(dx * dx + dy * dy) + 0.01f
                    if (d < v.r) {
                        val f = if (e.type.boss) 30f else 170f
                        e.x += dx / d * f * dt; e.y += dy / d * f * dt
                        if (d < 40f) {
                            e.hp -= v.dps * dt; e.flash = 0.05f
                            if (e.hp <= 0f) killEnemy(e)
                        }
                    }
                }
            }
            if (chance(0.5f)) {
                val a = rf(0f, TAU)
                particles.add(Particle(v.x + cos(a) * v.r, v.y + sin(a) * v.r, -cos(a) * v.r * 2f, -sin(a) * v.r * 2f, 0.45f, 2f, c(0xFFB46CFF)))
            }
        }
        vortices.removeAll { it.life <= 0f }
    }

    private fun updateStickies(dt: Float) {
        for (s in stickies) {
            s.t -= dt
            if (s.t <= 0f) explosion(s.e.x, s.e.y, 45f, s.dmg, c(0xFFFF5050), 0f)
        }
        stickies.removeAll { it.t <= 0f }
    }

    private fun updatePickups(dt: Float) {
        val p = player
        for (i in 0 until pickups.size) {
            val k = pickups[i]
            if (k.dead) continue
            k.t += dt
            k.x += k.vx * dt; k.y += k.vy * dt
            val dec = exp(-5f * dt); k.vx *= dec; k.vy *= dec
            val dx = p.x - k.x; val dy = p.y - k.y
            val d = sqrt(dx * dx + dy * dy) + 0.01f
            if (k.t > 0.3f && (k.vacuum || d < p.pickup)) {
                k.vacuum = true
                val sp = 260f + k.t * 60f
                k.x += dx / d * sp * dt; k.y += dy / d * sp * dt
            }
            if (d < p.r + 8f && k.t > 0.3f) collect(k)
        }
    }

    fun collect(k: Pickup) {
        val p = player
        k.dead = true
        when (k.kind) {
            PKind.XP -> {
                p.xp += k.value
                while (p.xp >= p.xpNeed) { p.xp -= p.xpNeed; p.level++; p.xpNeed = xpFor(p.level); pendingLevels++ }
            }
            PKind.GOLD -> p.gold += k.value
            PKind.HEAL -> { heal(p.maxHp * 0.3f); texts.add(FloatText(p.x, p.y - 22f, "+ХП", c(0xFF6CE46C), 0.9f, 13f)) }
            PKind.MAGNET -> { for (q in pickups) if (q.kind == PKind.XP || q.kind == PKind.GOLD) q.vacuum = true; showBanner("🧲 Магнит!") }
            PKind.PART -> k.part?.let { pendingParts.add(it) }
            PKind.ITEM -> k.item?.let { giveItem(it) }
            PKind.EQUIP -> k.equip?.let { p.equip = it; p.equipCd = 0f; showBanner("${it.glyph} Оборудование: ${it.title}") }
        }
    }

    fun giveItem(it: Item) {
        player.items[it.ordinal]++
        recomputeAll()
        showBanner("${it.glyph} ${it.title} ×${player.n(it)}")
    }

    private fun updateChests(dt: Float) {
        val p = player
        for (ch in chests) {
            ch.t += dt
            if (dist2(ch.x, ch.y, p.x, p.y) < 30f * 30f) {
                if (p.gold >= ch.cost) {
                    p.gold -= ch.cost; ch.dead = true; chestsOpened++
                    ring(ch.x, ch.y, 50f, c(0xFFFFD54A))
                    for (k in 0 until 14) {
                        val a = rf(0f, TAU)
                        particles.add(Particle(ch.x, ch.y, cos(a) * rf(40f, 160f), sin(a) * rf(40f, 160f), 0.6f, 3f, c(0xFFFFD54A)))
                    }
                    if (chance(0.12f)) {
                        val eq = Pickup(PKind.EQUIP, ch.x, ch.y)
                        eq.equip = Equip.values()[rnd.nextInt(Equip.values().size)]
                        pickups.add(eq)
                    } else if (chance(0.25f)) {
                        dropPart(ch.x, ch.y, rollRarity())
                    } else {
                        giveItem(randomItem(rollRarity()))
                    }
                } else if (goldMsgT <= 0f) {
                    goldMsgT = 1.5f
                    texts.add(FloatText(ch.x, ch.y - 24f, "Нужно ${ch.cost} 💰", c(0xFFFFD54A), 1.2f, 12f))
                }
            }
        }
    }

    fun updateParticles(dt: Float) {
        for (i in 0 until particles.size) {
            val pr = particles[i]
            pr.life -= dt; pr.x += pr.vx * dt; pr.y += pr.vy * dt
            pr.vx *= 0.94f; pr.vy *= 0.94f
            if (pr.ring) pr.size += pr.grow * dt
        }
        for (t in texts) { t.life -= dt; t.y -= 30f * dt }
        for (b in beams) b.life -= dt
    }

    private fun cleanup() {
        enemies.removeAll { it.dead }
        bullets.removeAll { it.dead }
        ebullets.removeAll { it.dead }
        pickups.removeAll { it.dead }
        chests.removeAll { it.dead }
        particles.removeAll { it.life <= 0f }
        texts.removeAll { it.life <= 0f }
        beams.removeAll { it.life <= 0f }
        if (particles.size > 600) particles.subList(0, particles.size - 600).clear()
        val p = player
        bullets.removeAll { dist2(it.x, it.y, p.x, p.y) > 1400f * 1400f }
        ebullets.removeAll { dist2(it.x, it.y, p.x, p.y) > 1200f * 1200f }
        pickups.removeAll { it.kind == PKind.XP && dist2(it.x, it.y, p.x, p.y) > 1600f * 1600f }
    }

    // ── Оборудование
    fun useEquip() {
        val p = player
        val eq = p.equip ?: return
        if (p.equipCd > 0f) return
        p.equipCd = eq.cd
        val s = gun.stats
        when (eq) {
            Equip.GRENADE -> {
                val t = nearestEnemy(p.x, p.y, 400f)
                val tx = t?.x ?: (p.x + p.aimX * 150f); val ty = t?.y ?: (p.y + p.aimY * 150f)
                explosion(tx, ty, 110f, s.damage * 6f + 40f, c(0xFFFF7030), 6f)
                beams.add(Beam(p.x, p.y, tx, ty, 0.2f, c(0xFFFFB060), 2f, false))
            }
            Equip.DASH -> {
                var dx = p.moveX; var dy = p.moveY
                if (hypot(jx, jy) > 0.2f) { val l = hypot(jx, jy); dx = jx / l; dy = jy / l }
                for (k in 0 until 10) particles.add(Particle(p.x + dx * k * 14f, p.y + dy * k * 14f, 0f, 0f, 0.35f, 6f, p.color))
                p.x += dx * 140f; p.y += dy * 140f
                p.invuln = max(p.invuln, 0.4f)
                ring(p.x, p.y, 40f, p.color)
            }
            Equip.DOME -> { p.domeT = 3f; ring(p.x, p.y, 60f, c(0xFF7FE8FF)) }
            Equip.BLACKHOLE -> {
                val t = nearestEnemy(p.x, p.y, 400f)
                val tx = t?.x ?: (p.x + p.aimX * 150f); val ty = t?.y ?: (p.y + p.aimY * 150f)
                vortices.add(Vortex(tx, ty, 4f, 150f, s.damage * 4f + 30f))
            }
            Equip.CHRONO -> { chronoT = 4f; showBanner("⏳ Время остановлено") }
            Equip.SALVO -> for (k in 0 until 12) spawnMissile(p.x, p.y, s.damage * 2.5f + 10f)
        }
    }

    // ── Повышение уровня
    fun openLevelUp() {
        pendingLevels--
        rollChoices()
        goTo(State.LEVELUP)
    }

    fun rollChoices() {
        choices.clear()
        val n = min(5, 3 + luck())
        var guard = 0
        while (choices.size < n && guard++ < 40) {
            val ch = if (chance(0.25f)) Choice(null, randomPart(rollRarity())) else Choice(randomItem(rollRarity()), null)
            if (choices.any { (it.item != null && it.item == ch.item) || (it.part != null && it.part == ch.part) }) continue
            choices.add(ch)
        }
    }

    fun pickChoice(ch: Choice) {
        ch.item?.let { giveItem(it) }
        ch.part?.let { pendingParts.add(it) }
        choices.clear()
        goTo(State.PLAY)
    }

    fun rerollCost() = 10 + rerolls * 5

    fun reroll() {
        val cost = rerollCost()
        if (player.gold < cost) return
        player.gold -= cost; rerolls++
        rollChoices()
    }

    // ── Замена детали
    fun openPartSwap() {
        val np = pendingParts[0]
        val cur = gun.parts[np.slot]
        if (cur == null) {
            pendingParts.removeAt(0)
            installPart(np)
            return
        }
        goTo(State.PART)
    }

    fun installPart(np: PartDef) {
        gun.parts[np.slot] = np
        recomputeAll()
        showBanner("${np.slot.glyph} Установлено: ${np.name}")
        ring(player.x, player.y, 40f, np.origin.color)
    }

    fun resolvePart(install: Boolean) {
        if (pendingParts.isEmpty()) { goTo(State.PLAY); return }
        val np = pendingParts.removeAt(0)
        if (install) installPart(np) else player.gold += 6
        if (install) player.gold += 4
        goTo(State.PLAY)
    }

    // ═══════════════════════════ Отрисовка ═══════════════════════════
    fun draw(cv: Canvas) {
        uiButtons.clear()
        if (state == State.MENU) { drawMenu(cv); return }
        drawWorld(cv)
        drawHud(cv)
        when (state) {
            State.LEVELUP -> drawLevelUp(cv)
            State.PART -> drawPartSwap(cv)
            State.PAUSE -> drawPause(cv)
            State.GAMEOVER -> drawGameOver(cv)
            else -> {}
        }
    }
}
