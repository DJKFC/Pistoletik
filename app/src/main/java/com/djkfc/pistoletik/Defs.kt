package com.djkfc.pistoletik

import kotlin.math.max

fun c(v: Long): Int = v.toInt()

// ───────────────────────── Происхождение деталей ─────────────────────────
enum class Origin(val title: String, val color: Int, val setDesc: String) {
    COMMON("Обычная", c(0xFFC9C3B6), "+25% скорострельность, +1 пробитие"),
    MAGIC("Магическая", c(0xFFB46CFF), "+2 наведение, +1 осколок"),
    ALIEN("Инопланетная", c(0xFF4DFFB0), "+2 пробития, пули крупнее и притягивают"),
    DEMON("Адская", c(0xFFFF4A3A), "+6 горения, вампиризм"),
    CYBER("Кибер", c(0xFF3FB6FF), "+2 цепные молнии, +1 снаряд");
}

enum class Slot(val title: String, val glyph: String) {
    BARREL("Ствол", "🔫"),
    CORE("Кристалл", "💎"),
    BINDING("Обвязка", "🧵"),
    MECH("Механизм", "⚙️"),
    AMMO("Патроны", "🔸");
}

enum class FireMode(val title: String) {
    NORMAL("Обычный"), BURST("Очередь"), BEAM("Луч"), ORBIT("Орбита"),
    BOOMERANG("Бумеранг"), MINE("Мины"), SPIRAL("Спираль"), NOVA("Кольцо");
}

enum class Shape { CIRCLE, NEEDLE, STAR, DIAMOND, RING, TRIANGLE, SQUARE }

val RARITY_COLORS = intArrayOf(c(0xFFE8E8E8), c(0xFF6CE46C), c(0xFFFF5A4A))
val RARITY_NAMES = arrayOf("Обычный", "Редкий", "Легендарный")

// ───────────────────────── Характеристики пушки ─────────────────────────
class GunStats {
    var damage = 10f
    var fireRate = 2.2f
    var speed = 480f
    var spread = 4f
    var count = 1
    var pierce = 0
    var bounce = 0
    var size = 5f
    var life = 1.1f
    var homing = 0f
    var knock = 50f
    var crit = 0.05f
    var critMult = 2f
    var mode = FireMode.NORMAL
    var shape = Shape.CIRCLE
    var color = c(0xFFFFE27A)
    var wave = 0f
    var explode = 0f
    var split = 0
    var chain = 0
    var burn = 0f
    var freeze = 0f
    var pull = 0f
    var rear = false
    var sides = false
    var accel = 0f
    var growth = 0f
    var burst = 3
    var leech = 0f
    var execute = 0f
    var hpMul = 1f
}

class PartDef(
    val id: String,
    val name: String,
    val slot: Slot,
    val origin: Origin,
    val rarity: Int,
    val desc: String,
    val tint: Int = origin.color,
    val apply: GunStats.() -> Unit
)

object Parts {
    val all: List<PartDef> = listOf(
        // ── Стволы: меняют логику стрельбы
        PartDef("b_short", "Короткий ствол", Slot.BARREL, Origin.COMMON, 0, "+20% скорострельность, точнее") {
            fireRate *= 1.2f; spread -= 2f
        },
        PartDef("b_sawed", "Обрез", Slot.BARREL, Origin.COMMON, 1, "Дробовик: +4 дробинки, широкий разброс, малая дальность") {
            count += 4; spread += 26f; life *= 0.5f; damage *= 0.65f; fireRate *= 0.75f; knock += 60f
        },
        PartDef("b_sniper", "Снайперский ствол", Slot.BARREL, Origin.COMMON, 1, "×2.2 урон, +2 пробития, пули-иглы, стреляет реже") {
            damage *= 2.2f; fireRate *= 0.5f; speed *= 1.6f; life *= 1.3f; pierce += 2; shape = Shape.NEEDLE
        },
        PartDef("b_spiral", "Жезл спирали", Slot.BARREL, Origin.MAGIC, 1, "Стреляет вращающейся спиралью во все стороны, +1 снаряд") {
            mode = FireMode.SPIRAL; count += 1
        },
        PartDef("b_nova", "Кольцевое дуло", Slot.BARREL, Origin.MAGIC, 1, "Выпускает кольцо пуль во все стороны") {
            mode = FireMode.NOVA; damage *= 1.1f
        },
        PartDef("b_boomer", "Дуло-бумеранг", Slot.BARREL, Origin.MAGIC, 0, "Пули возвращаются к тебе и бьют повторно") {
            mode = FireMode.BOOMERANG; size += 2f
        },
        PartDef("b_beam", "Излучатель", Slot.BARREL, Origin.ALIEN, 1, "Мгновенный луч, пронзающий всех на линии") {
            mode = FireMode.BEAM; damage *= 0.8f
        },
        PartDef("b_warp", "Деформатор", Slot.BARREL, Origin.ALIEN, 0, "Пули летят волной, +15% урон") {
            wave += 1f; size += 1.5f; damage *= 1.15f
        },
        PartDef("b_orbit", "Гравитационное кольцо", Slot.BARREL, Origin.ALIEN, 2, "Пули кружат вокруг тебя живым щитом") {
            mode = FireMode.ORBIT; damage *= 1.3f
        },
        PartDef("b_maw", "Адская пасть", Slot.BARREL, Origin.DEMON, 1, "Очередь из 4 выстрелов, поджигает") {
            mode = FireMode.BURST; burst = 4; burn += 4f; damage *= 1.1f
        },
        PartDef("b_rail", "Рельсотрон", Slot.BARREL, Origin.CYBER, 2, "Сверхбыстрые иглы, +5 пробитий, +40% урон") {
            speed *= 2.4f; pierce += 5; damage *= 1.4f; fireRate *= 0.65f; shape = Shape.NEEDLE
        },
        PartDef("b_mortar", "Минный раструб", Slot.BARREL, Origin.CYBER, 1, "Раскидывает мины, взрывающиеся от касания") {
            mode = FireMode.MINE; explode += 50f; damage *= 1.6f
        },

        // ── Кристаллы: стихии и форма пуль
        PartDef("c_glass", "Стекляшка", Slot.CORE, Origin.COMMON, 0, "+10% шанс крита", c(0xFFDDEEFF)) {
            crit += 0.1f
        },
        PartDef("c_amethyst", "Аметист", Slot.CORE, Origin.MAGIC, 1, "Пули-звёзды самонаводятся", c(0xFFC77DFF)) {
            homing += 3.5f; shape = Shape.STAR; color = c(0xFFD9A0FF)
        },
        PartDef("c_moon", "Лунный камень", Slot.CORE, Origin.MAGIC, 1, "Пуля раскалывается на 3 осколка", c(0xFFE6F0FF)) {
            split += 3; color = c(0xFFE6F0FF)
        },
        PartDef("c_ice", "Ледяной сапфир", Slot.CORE, Origin.MAGIC, 0, "Замедляет врагов на 40%, пули-ромбы", c(0xFF7FE8FF)) {
            freeze = max(freeze, 0.4f); shape = Shape.DIAMOND; color = c(0xFF9FF0FF)
        },
        PartDef("c_void", "Кристалл пустоты", Slot.CORE, Origin.ALIEN, 2, "Пули-кольца затягивают врагов в себя", c(0xFF33FFC8)) {
            pull += 70f; shape = Shape.RING; color = c(0xFF55FFD0); size += 2f
        },
        PartDef("c_spore", "Споровый кристалл", Slot.CORE, Origin.ALIEN, 0, "Пули растут в полёте и бьют сильнее", c(0xFF9CFF4D)) {
            growth += 1.3f; color = c(0xFFB8FF6A)
        },
        PartDef("c_ruby", "Кровавый рубин", Slot.CORE, Origin.DEMON, 1, "Поджигает и лечит, пули-клыки", c(0xFFFF3355)) {
            burn += 5f; leech += 0.3f; shape = Shape.TRIANGLE; color = c(0xFFFF4060)
        },
        PartDef("c_energy", "Энергоядро", Slot.CORE, Origin.CYBER, 1, "Попадание бьёт цепной молнией (2 прыжка)", c(0xFF5AC8FF)) {
            chain += 2; color = c(0xFF7FD4FF)
        },

        // ── Обвязки: нитки, ленты, провода
        PartDef("t_tape", "Изолента", Slot.BINDING, Origin.COMMON, 0, "+10% урон и +10% скорострельность", c(0xFF4A6CFF)) {
            damage *= 1.1f; fireRate *= 1.1f
        },
        PartDef("t_rubber", "Резинка", Slot.BINDING, Origin.COMMON, 0, "Пули отскакивают ещё на 2 врага", c(0xFFFFB0C0)) {
            bounce += 2
        },
        PartDef("t_fate", "Нить судьбы", Slot.BINDING, Origin.MAGIC, 1, "+15% шанс крита, +50% крит-урон") {
            crit += 0.15f; critMult += 0.5f
        },
        PartDef("t_web", "Паутина", Slot.BINDING, Origin.MAGIC, 0, "Замедляет на 25%, пули крупнее", c(0xFFEFEFEF)) {
            freeze = max(freeze, 0.25f); size += 2.5f
        },
        PartDef("t_tentacle", "Щупальце", Slot.BINDING, Origin.ALIEN, 1, "Лёгкое наведение, +1 пробитие") {
            homing += 1.8f; pierce += 1
        },
        PartDef("t_barbed", "Колючая проволока", Slot.BINDING, Origin.DEMON, 1, "+25% урон, добивает врагов ниже 12% ХП") {
            damage *= 1.25f; execute = max(execute, 0.12f); knock += 30f
        },
        PartDef("t_wire", "Медный провод", Slot.BINDING, Origin.CYBER, 0, "+1 цепная молния, +15% скорострельность", c(0xFFFF9E40)) {
            chain += 1; fireRate *= 1.15f
        },

        // ── Механизмы
        PartDef("m_spring", "Пружина", Slot.MECH, Origin.COMMON, 0, "+35% скорострельность") {
            fireRate *= 1.35f
        },
        PartDef("m_rear", "Задний спуск", Slot.MECH, Origin.COMMON, 0, "Дополнительно стреляет назад") {
            rear = true
        },
        PartDef("m_servo", "Сервопривод", Slot.MECH, Origin.CYBER, 1, "Стреляет ещё и в стороны") {
            sides = true; fireRate *= 1.1f
        },
        PartDef("m_grav", "Гравидвигатель", Slot.MECH, Origin.ALIEN, 1, "Пули разгоняются в полёте, +20% урон") {
            accel += 700f; damage *= 1.2f; life *= 1.2f
        },
        PartDef("m_heart", "Сердце демона", Slot.MECH, Origin.DEMON, 2, "+70% скорострельность, но −25% макс. ХП") {
            fireRate *= 1.7f; hpMul *= 0.75f
        },
        PartDef("m_clock", "Часовой механизм", Slot.MECH, Origin.MAGIC, 0, "Пули летят на 60% дальше и крупнее") {
            life *= 1.6f; size += 2f
        },

        // ── Патроны
        PartDef("a_exp", "Экспансивные", Slot.AMMO, Origin.COMMON, 0, "+30% урон") {
            damage *= 1.3f; size += 1f
        },
        PartDef("a_ap", "Бронебойные", Slot.AMMO, Origin.COMMON, 0, "+2 пробития") {
            pierce += 2
        },
        PartDef("a_inc", "Зажигательные", Slot.AMMO, Origin.DEMON, 0, "Поджигают и слегка взрываются") {
            burn += 5f; explode += 22f
        },
        PartDef("a_frag", "Разрывные", Slot.AMMO, Origin.CYBER, 1, "Взрыв при каждом попадании") {
            explode += 45f; damage *= 0.9f
        },
        PartDef("a_plasma", "Плазменные", Slot.AMMO, Origin.ALIEN, 1, "Огромные сгустки плазмы, +40% урон") {
            size += 4f; damage *= 1.4f; speed *= 0.8f; color = c(0xFF6AFFE0)
        },
        PartDef("a_twin", "Двоящиеся", Slot.AMMO, Origin.MAGIC, 1, "+1 снаряд и +2 осколка") {
            count += 1; split += 2
        },
    )
    val byId: Map<String, PartDef> = all.associateBy { it.id }
}

fun applySet(o: Origin, s: GunStats, k: Int) {
    when (o) {
        Origin.COMMON -> { s.fireRate *= 1f + 0.25f * k; s.pierce += k }
        Origin.MAGIC -> { s.homing += 2f * k; s.split += k }
        Origin.ALIEN -> { s.pierce += 2 * k; s.size += 1.5f * k; s.pull += 25f * k }
        Origin.DEMON -> { s.burn += 6f * k; s.leech += 0.4f * k }
        Origin.CYBER -> { s.chain += 2 * k; s.count += k }
    }
}

// ───────────────────────── Предметы (стакаются) ─────────────────────────
enum class Item(val title: String, val rarity: Int, val desc: String, val glyph: String) {
    SYRINGE("Шприц с кофеином", 0, "+12% скорострельность", "💉"),
    SNEAKERS("Кроссовки", 0, "+10% скорость бега", "👟"),
    FANG("Клык", 0, "Убийство лечит 2 ХП", "🦷"),
    CROWBAR("Монтировка", 0, "+50% урона по врагам с почти полным ХП", "🔧"),
    LENS("Линзы", 0, "+8% шанс крита", "👓"),
    STICKY("Липкая бомба", 0, "5% шанс прилепить бомбу (180% урона)", "💣"),
    PLATE("Бронепластина", 0, "−2 входящего урона", "🛡️"),
    GAS("Канистра", 0, "Убитые враги взрываются огнём", "⛽"),
    MEDKIT("Аптечка", 0, "+20 макс. ХП и регенерация", "💗"),
    MAGNET("Магнит", 0, "+40% радиус подбора", "🧲"),
    LEAD("Свинцовые пули", 0, "+8% урон", "🔩"),
    BOUNCY("Попрыгунчик", 0, "+1 рикошет от врагов", "🏀"),
    GUITAR("Электрогитара", 1, "20% шанс цепной молнии", "🎸"),
    ROCKET("Ракетница", 1, "10% шанс выпустить самонаводящуюся ракету", "🚀"),
    LEECH("Пиявка", 1, "Каждое попадание лечит", "🩸"),
    FIRE_RING("Огненное кольцо", 1, "15% шанс поджечь врага", "🔥"),
    ICE_RING("Ледяное кольцо", 1, "15% шанс сильно замедлить врага", "❄️"),
    SHIELD("Генератор щита", 1, "Щит 15% ХП, восстанавливается", "🔰"),
    DOUBLE("Второй ствол", 1, "+1 снаряд", "➕"),
    DRONE("Дрон-помощник", 1, "Дрон летает рядом и стреляет", "🤖"),
    TOTEM("Тотем бессмертия", 2, "Воскрешает один раз", "🗿"),
    SUPERNOVA("Сверхновая", 2, "Каждые 12 попаданий — взрыв из 12 пуль", "✨"),
    BLOODLUST("Кровавая жажда", 2, "+100% крит-урон, криты лечат", "🦇"),
    CLOVER("Клевер", 2, "+1 карта при новом уровне, больше удачи", "🍀"),
    BEHEMOTH("Бегемот", 2, "Все пули взрываются", "💥"),
    GLASS("Стеклянная пушка", 2, "+80% урон, но −30% ХП", "🔮");
}

// ───────────────────────── Активное оборудование ─────────────────────────
enum class Equip(val title: String, val desc: String, val cd: Float, val glyph: String) {
    GRENADE("Граната", "Мощный взрыв по ближайшей толпе", 9f, "🧨"),
    DASH("Рывок", "Телепорт-рывок с неуязвимостью", 3.5f, "💨"),
    DOME("Купол", "Неуязвимость 3 сек, отталкивает врагов", 18f, "🌐"),
    BLACKHOLE("Чёрная дыра", "Затягивает и перемалывает врагов", 20f, "🌀"),
    CHRONO("Хронометр", "Останавливает врагов на 4 сек", 28f, "⏳"),
    SALVO("Ракетный залп", "Выпускает 12 самонаводящихся ракет", 14f, "🎆");
}

// ───────────────────────── Герои ─────────────────────────
enum class Hero(
    val title: String, val desc: String, val color: Int,
    val hp: Float, val speed: Float, val parts: List<String>, val equip: Equip
) {
    GUNNER("Стрелок", "Короткий ствол, экспансивные патроны, граната", c(0xFFF2B33D), 100f, 150f,
        listOf("b_short", "a_exp"), Equip.GRENADE),
    WITCH("Ведьмак", "Аметист и нить судьбы, купол", c(0xFFB46CFF), 80f, 155f,
        listOf("c_amethyst", "t_fate"), Equip.DOME),
    ZORG("Зорг", "Инопланетный излучатель, рывок", c(0xFF4DFFB0), 90f, 165f,
        listOf("b_beam"), Equip.DASH),
    IMP("Бес", "Адская пасть и кровавый рубин, чёрная дыра", c(0xFFFF5A4A), 115f, 145f,
        listOf("b_maw", "c_ruby"), Equip.BLACKHOLE);
}

// ───────────────────────── Враги ─────────────────────────
enum class EType(
    val title: String, val hp: Float, val speed: Float, val r: Float, val dmg: Float,
    val color: Int, val xp: Int, val minTime: Float, val cost: Int, val weight: Float, val boss: Boolean = false
) {
    ZOMBIE("Зомби", 14f, 52f, 11f, 8f, c(0xFF5FAF4A), 1, 0f, 1, 10f),
    BAT("Мышь", 7f, 105f, 8f, 5f, c(0xFF9A5CD6), 1, 20f, 1, 6f),
    SPITTER("Плевун", 22f, 45f, 11f, 7f, c(0xFFD6C14A), 2, 55f, 3, 3f),
    BRUTE("Громила", 75f, 38f, 18f, 15f, c(0xFF9B6A43), 4, 90f, 4, 3f),
    BOMBER("Подрывник", 16f, 88f, 10f, 22f, c(0xFFFF8A2A), 2, 120f, 3, 2.5f),
    SPLITTER("Слизень", 40f, 45f, 15f, 10f, c(0xFF3FC9C9), 3, 150f, 4, 2.5f),
    GHOST("Призрак", 28f, 66f, 12f, 10f, c(0xFFBFD8FF), 2, 210f, 3, 3f),
    MINI("Слизнёнок", 8f, 90f, 7f, 5f, c(0xFF6FE0E0), 1, 99999f, 0, 0f),
    GOLEM("Каменный Голем", 1500f, 34f, 40f, 25f, c(0xFF8C8C9A), 60, 99999f, 0, 0f, true),
    HIVE("Королева Улья", 2000f, 30f, 36f, 22f, c(0xFFE0A030), 70, 99999f, 0, 0f, true),
    UFO("Матка Пришельцев", 2600f, 46f, 36f, 20f, c(0xFF60FF9A), 80, 99999f, 0, 0f, true);
}

enum class Affix(val title: String, val color: Int) {
    FIRE("Огненный", c(0xFFFF5A2A)),
    ICE("Ледяной", c(0xFF7FE8FF)),
    FAST("Быстрый", c(0xFFFFFF60)),
    TOUGH("Крепкий", c(0xFFB0B0B0));
}
