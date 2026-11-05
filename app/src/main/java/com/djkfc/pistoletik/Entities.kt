package com.djkfc.pistoletik

import kotlin.math.PI
import kotlin.random.Random

val rnd = Random(System.nanoTime())
val PI_F = PI.toFloat()
val TAU = (PI * 2).toFloat()
fun rf(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
fun ri(a: Int, b: Int) = a + rnd.nextInt(b - a + 1)
fun chance(p: Float) = rnd.nextFloat() < p
fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = ax - bx; val dy = ay - by; return dx * dx + dy * dy
}
fun clampf(v: Float, a: Float, b: Float) = if (v < a) a else if (v > b) b else v
fun rad(d: Float) = d * PI_F / 180f
fun withAlpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
fun fmtTime(t: Float): String { val s = t.toInt(); return "%d:%02d".format(s / 60, s % 60) }

class Gun {
    val parts = HashMap<Slot, PartDef>()
    var stats = GunStats()
    var cooldown = 0f
    var spiralAngle = 0f
    var burstLeft = 0
    var burstTimer = 0f
    var burstAngle = 0f
}

class Player {
    var x = 0f; var y = 0f; val r = 12f
    var hp = 0f; var maxHp = 0f; var maxHpBase = 100f
    var shield = 0f; var maxShield = 0f; var shieldDelay = 0f
    var speed = 150f; var speedBase = 150f
    var pickup = 55f; var regen = 0f; var armor = 0f
    var level = 1; var xp = 0f; var xpNeed = 10f
    var gold = 0
    var invuln = 0f; var hitFlash = 0f; var domeT = 0f; var chillT = 0f
    var aimX = 1f; var aimY = 0f; var moveX = 1f; var moveY = 0f; var walkT = 0f
    var color = c(0xFFF2B33D)
    val items = IntArray(Item.values().size)
    var equip: Equip? = null; var equipCd = 0f
    fun n(i: Item) = items[i.ordinal]
}

class Enemy(val type: EType, val id: Int) {
    var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
    var hp = 1f; var maxHp = 1f; var r = 10f; var speed = 50f; var dmg = 5f; var xp = 1
    var elite: Affix? = null
    var flash = 0f; var slowT = 0f; var slowAmt = 0f
    var burnT = 0f; var burnDps = 0f; var burnTick = 0f
    var cd = rf(1f, 3f); var t = rf(0f, 10f); var phase = 0f
    var dead = false
}

enum class BKind { NORMAL, ORBIT, BOOMERANG, MINE, MISSILE, ENEMY, DRONE }

class Bullet {
    var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
    var dmg = 0f; var size = 5f; var life = 1f; var maxLife = 1f; var t = 0f
    var pierce = 0; var bounce = 0; var homing = 0f
    var shape = Shape.CIRCLE; var color = -1
    var wave = 0f; var waveOff = 0f
    var explode = 0f; var split = 0; var chain = 0; var burn = 0f; var freeze = 0f
    var pull = 0f; var accel = 0f; var growth = 0f
    var kind = BKind.NORMAL
    var child = false; var proc = 1f; var multiHit = false
    var dead = false
    var orbitA = 0f; var orbitR = 0f; var orbitMax = 60f
    var speed0 = 0f; var spin = 0f
    var target: Enemy? = null; var retarget = 0f
    val hitIds = IntArray(8) { -1 }
    val hitT = FloatArray(8)
    var hitIdx = 0

    fun canHit(id: Int): Boolean {
        for (i in 0 until 8) if (hitIds[i] == id && hitT[i] > 0f) return false
        return true
    }
    fun mark(id: Int, cd: Float) {
        hitIds[hitIdx] = id; hitT[hitIdx] = cd; hitIdx = (hitIdx + 1) and 7
    }
    fun tickHits(dt: Float) { for (i in 0 until 8) hitT[i] -= dt }
}

enum class PKind { XP, GOLD, HEAL, MAGNET, PART, ITEM, EQUIP }

class Pickup(val kind: PKind, var x: Float, var y: Float, var value: Int = 1) {
    var vx = rf(-60f, 60f); var vy = rf(-60f, 60f)
    var t = 0f; var vacuum = false; var dead = false
    var part: PartDef? = null; var item: Item? = null; var equip: Equip? = null
}

class Chest(val x: Float, val y: Float, val cost: Int) { var dead = false; var t = 0f }

class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, var size: Float, var color: Int, val ring: Boolean = false
) { val maxLife = life; var grow = 0f }

class FloatText(var x: Float, var y: Float, val text: String, val color: Int, var life: Float, val size: Float)

class Beam(val x1: Float, val y1: Float, val x2: Float, val y2: Float, var life: Float, val color: Int, val width: Float, val zap: Boolean) {
    val maxLife = life
}

class Drone(var angle: Float) { var x = 0f; var y = 0f; var cd = 0.5f }

class Vortex(val x: Float, val y: Float, var life: Float, val r: Float, val dps: Float) { var t = 0f }

class Sticky(val e: Enemy, var t: Float, val dmg: Float)

class Choice(val item: Item?, val part: PartDef?)

class Btn(val l: Float, val t: Float, val r: Float, val b: Float, val act: () -> Unit) {
    fun contains(x: Float, y: Float) = x in l..r && y in t..b
}
