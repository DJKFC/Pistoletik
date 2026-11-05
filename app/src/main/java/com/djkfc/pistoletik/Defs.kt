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

/** Чем стреляет пушка. Если вставлено несколько — чередуются по выстрелам. */
enum class ProjKind(val title: String) {
    NORMAL("Пули"), BEAM("Луч"), ORBIT("Орбита"), BOOMERANG("Бумеранг"), MINE("Мины");
}

enum class Shape { CIRCLE, NEEDLE, STAR, DIAMOND, RING, TRIANGLE, SQUARE }

val RARITY_COLORS = intArrayOf(c(0xFFE8E8E8), c(0xFF6CE46C), c(0xFFFF5A4A))
val RARITY_NAMES = arrayOf("Обычная", "Редкая", "Легендарная")

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
    val kinds = ArrayList<ProjKind>()
    var spiral = false
    var nova = false
    var burstOn = false
    var burst = 3
    var shape = Shape.CIRCLE
    var color = c(0xFFFFE27A)
    var wave = 0f
    var curve = 0f
    var mirror = 0
    var chaos = false
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
    var leech = 0f
    var execute = 0f
    var hpMul = 1f

    fun addKind(k: ProjKind) { if (!kinds.contains(k)) kinds.add(k) }
}

/** Любая штука, которую можно прикрутить к пушке. */
class PartDef(
    val id: String,
    val name: String,
    val glyph: String,
    val origin: Origin,
    val rarity: Int,
    val desc: String,
    val apply: GunStats.() -> Unit
)

object Parts {
    val all: List<PartDef> = listOf(
        // ── меняют логику стрельбы
        PartDef("saw", "Подпиленный ствол", "🪚", Origin.COMMON, 0, "+20% скорострельность, точнее") {
            fireRate *= 1.2f; spread -= 2f
        },
        PartDef("sawed", "Обрез", "🔫", Origin.COMMON, 1, "Дробовик: +4 дробинки, широкий разброс, малая дальность") {
            count += 4; spread += 26f; life *= 0.5f; damage *= 0.65f; fireRate *= 0.75f; knock += 60f
        },
        PartDef("scope", "Оптический прицел", "🔭", Origin.COMMON, 1, "×2.2 урон, +2 пробития, пули-иглы, стреляет реже") {
            damage *= 2.2f; fireRate *= 0.5f; speed *= 1.6f; life *= 1.3f; pierce += 2; shape = Shape.NEEDLE
        },
        PartDef("top", "Юла", "🌀", Origin.MAGIC, 1, "Стреляет вращающейся спиралью во все стороны, +1 снаряд") {
            spiral = true; count += 1
        },
        PartDef("wheel", "Колесо обозрения", "🎡", Origin.MAGIC, 1, "Каждый выстрел — кольцо во все стороны") {
            nova = true; damage *= 1.1f
        },
        PartDef("yoyo", "Йо-йо", "🪀", Origin.MAGIC, 0, "Снаряды возвращаются к тебе и бьют повторно") {
            addKind(ProjKind.BOOMERANG); size += 2f
        },
        PartDef("torch", "Фонарик", "🔦", Origin.ALIEN, 1, "Мгновенный луч, пронзающий всех на линии") {
            addKind(ProjKind.BEAM); damage *= 0.8f
        },
        PartDef("sat", "Спутник", "🛰️", Origin.ALIEN, 2, "Снаряды кружат вокруг тебя живым щитом") {
            addKind(ProjKind.ORBIT); damage *= 1.3f
        },
        PartDef("trap", "Мышеловка", "🪤", Origin.CYBER, 1, "Раскидывает мины, взрывающиеся от касания") {
            addKind(ProjKind.MINE); explode += 50f; damage *= 1.6f
        },
        PartDef("maw", "Адская пасть", "👹", Origin.DEMON, 1, "Стреляет очередями по 4, поджигает") {
            burstOn = true; burst = max(burst, 4); burn += 4f; damage *= 1.1f
        },
        PartDef("rail", "Рельсотрон", "⚡", Origin.CYBER, 2, "Сверхбыстрые иглы, +5 пробитий, +40% урон") {
            speed *= 2.4f; pierce += 5; damage *= 1.4f; fireRate *= 0.65f; shape = Shape.NEEDLE
        },
        PartDef("warp", "Деформатор", "〰️", Origin.ALIEN, 0, "Пули летят волной, +15% урон") {
            wave += 1f; size += 1.5f; damage *= 1.15f
        },
        PartDef("banana", "Банан", "🍌", Origin.COMMON, 0, "Пули летят по дуге, +10% урон") {
            curve += 2.4f; damage *= 1.1f; life *= 1.2f
        },
        PartDef("mirror", "Зеркальце", "🪞", Origin.MAGIC, 1, "Пули отражаются от краёв экрана 2 раза") {
            mirror += 2; life *= 1.5f
        },
        PartDef("soap", "Мыло", "🧼", Origin.COMMON, 0, "Стреляет пузырями: медленные, огромные, +3 пробития") {
            speed *= 0.45f; size += 5f; life *= 1.8f; pierce += 3; shape = Shape.RING; color = c(0xFFBFE8FF)
        },
        PartDef("hive", "Улей", "🐝", Origin.COMMON, 2, "Пули — пчёлы: +2 снаряда, наведение, жужжат волной") {
            count += 2; homing += 2.5f; wave += 0.5f; damage *= 0.6f; color = c(0xFFFFD23A)
        },
        PartDef("dice", "Игральный кубик", "🎲", Origin.MAGIC, 1, "Урон каждого попадания случаен: от ×0.2 до ×3") {
            chaos = true; damage *= 1.1f
        },

        // ── кристаллы и стихии
        PartDef("glass", "Стекляшка", "🔹", Origin.COMMON, 0, "+10% шанс крита") { crit += 0.1f },
        PartDef("amethyst", "Аметист", "🔮", Origin.MAGIC, 1, "Пули-звёзды самонаводятся") {
            homing += 3.5f; shape = Shape.STAR; color = c(0xFFD9A0FF)
        },
        PartDef("moon", "Лунный камень", "🌙", Origin.MAGIC, 1, "Пуля раскалывается на 3 осколка") {
            split += 3; color = c(0xFFE6F0FF)
        },
        PartDef("ice", "Ледяной сапфир", "💎", Origin.MAGIC, 0, "Замедляет врагов на 40%, пули-ромбы") {
            freeze = max(freeze, 0.4f); shape = Shape.DIAMOND; color = c(0xFF9FF0FF)
        },
        PartDef("void", "Осколок пустоты", "🕳️", Origin.ALIEN, 2, "Пули-кольца затягивают врагов в себя") {
            pull += 70f; shape = Shape.RING; color = c(0xFF55FFD0); size += 2f
        },
        PartDef("spore", "Гриб-спора", "🍄", Origin.ALIEN, 0, "Пули растут в полёте и бьют сильнее") {
            growth += 1.3f; color = c(0xFFB8FF6A)
        },
        PartDef("ruby", "Кровавый рубин", "♦️", Origin.DEMON, 1, "Поджигает и лечит, пули-клыки") {
            burn += 5f; leech += 0.3f; shape = Shape.TRIANGLE; color = c(0xFFFF4060)
        },
        PartDef("battery", "Батарейка", "🔋", Origin.CYBER, 1, "Попадание бьёт цепной молнией (2 прыжка)") {
            chain += 2; color = c(0xFF7FD4FF)
        },
        PartDef("meteor", "Кусок метеорита", "☄️", Origin.ALIEN, 1, "Огромные раскалённые сгустки, +40% урон") {
            size += 4f; damage *= 1.4f; speed *= 0.8f; color = c(0xFFFFA060); explode += 15f
        },

        // ── обмотки и прочая привязанная ерунда
        PartDef("tape", "Изолента", "🩹", Origin.COMMON, 0, "+10% урон и +10% скорострельность") {
            damage *= 1.1f; fireRate *= 1.1f
        },
        PartDef("rubber", "Резинка", "➰", Origin.COMMON, 0, "Пули отскакивают ещё на 2 врага") { bounce += 2 },
        PartDef("fate", "Нить судьбы", "🧵", Origin.MAGIC, 1, "+15% шанс крита, +50% крит-урон") {
            crit += 0.15f; critMult += 0.5f
        },
        PartDef("web", "Паутина", "🕸️", Origin.MAGIC, 0, "Замедляет на 25%, пули крупнее") {
            freeze = max(freeze, 0.25f); size += 2.5f
        },
        PartDef("squid", "Щупальце", "🦑", Origin.ALIEN, 1, "Лёгкое наведение, +1 пробитие") {
            homing += 1.8f; pierce += 1
        },
        PartDef("cactus", "Кактус", "🌵", Origin.DEMON, 1, "+25% урон, добивает врагов ниже 12% ХП") {
            damage *= 1.25f; execute = max(execute, 0.12f); knock += 30f
        },
        PartDef("wire", "Медный провод", "🔌", Origin.CYBER, 0, "+1 цепная молния, +15% скорострельность") {
            chain += 1; fireRate *= 1.15f
        },
        PartDef("sock", "Вонючий носок", "🧦", Origin.COMMON, 0, "Пули отравляют (урон со временем)") {
            burn += 5f; color = c(0xFF9CFF4D)
        },
        PartDef("duck", "Резиновая уточка", "🦆", Origin.COMMON, 1, "Всё прыгает: +3 рикошета и отскок от краёв") {
            bounce += 3; mirror += 1; size += 1f
        },

        // ── механизмы
        PartDef("spring", "Пружина", "⏩", Origin.COMMON, 0, "+35% скорострельность") { fireRate *= 1.35f },
        PartDef("rear", "Задний спуск", "🔙", Origin.COMMON, 0, "Дополнительно стреляет назад") { rear = true },
        PartDef("servo", "Механическая рука", "🦾", Origin.CYBER, 1, "Стреляет ещё и в стороны") {
            sides = true; fireRate *= 1.1f
        },
        PartDef("grav", "Гравидвигатель", "🌠", Origin.ALIEN, 1, "Пули разгоняются в полёте, +20% урон") {
            accel += 700f; damage *= 1.2f; life *= 1.2f
        },
        PartDef("heart", "Сердце демона", "🫀", Origin.DEMON, 2, "+70% скорострельность, но −25% макс. ХП") {
            fireRate *= 1.7f; hpMul *= 0.75f
        },
        PartDef("clock", "Часовой механизм", "⏱️", Origin.MAGIC, 0, "Пули летят на 60% дальше и крупнее") {
            life *= 1.6f; size += 2f
        },
        PartDef("flash", "Флешка", "💾", Origin.CYBER, 0, "+25% скорострельность") { fireRate *= 1.25f },
        PartDef("chip", "Микросхема", "💽", Origin.CYBER, 1, "+1 молния, лёгкое наведение, +5% крит") {
            chain += 1; homing += 1f; crit += 0.05f
        },
        PartDef("fan", "Вентилятор", "🌬️", Origin.COMMON, 0, "Сильно отбрасывает и слегка замедляет") {
            knock += 150f; freeze = max(freeze, 0.15f)
        },

        // ── боеприпасы и всякое тяжёлое
        PartDef("exp", "Экспансивные пули", "🔸", Origin.COMMON, 0, "+30% урон") { damage *= 1.3f; size += 1f },
        PartDef("nail", "Гвоздь", "📌", Origin.COMMON, 0, "+2 пробития, пули-иглы") { pierce += 2; shape = Shape.NEEDLE },
        PartDef("fish", "Рыбья кость", "🐟", Origin.COMMON, 0, "+1 пробитие, пули быстрее") {
            pierce += 1; speed *= 1.25f; shape = Shape.NEEDLE
        },
        PartDef("chili", "Перец чили", "🌶️", Origin.DEMON, 0, "Поджигает и слегка взрывается") {
            burn += 6f; explode += 18f; color = c(0xFFFF6A30)
        },
        PartDef("popper", "Хлопушка", "🎉", Origin.CYBER, 1, "Взрыв при каждом попадании") {
            explode += 45f; damage *= 0.9f
        },
        PartDef("popcorn", "Попкорн", "🍿", Origin.MAGIC, 1, "+1 снаряд, пули лопаются на 2 осколка") {
            count += 1; split += 2
        },
        PartDef("weight", "Гиря", "🏋️", Origin.COMMON, 1, "×1.8 урон, огромная отдача по врагам, но пули медленные") {
            damage *= 1.8f; speed *= 0.6f; knock += 100f; size += 3f; fireRate *= 0.85f
        },
        PartDef("feather", "Перо", "🪶", Origin.COMMON, 0, "Пули лёгкие: +50% скорость полёта, +20% скорострельность, −15% урон") {
            speed *= 1.5f; fireRate *= 1.2f; damage *= 0.85f
        },
        PartDef("eye", "Глаз", "👁️", Origin.ALIEN, 1, "Сильное наведение, +5% крит") { homing += 3f; crit += 0.05f },
        PartDef("skull", "Череп", "💀", Origin.DEMON, 1, "+15% урон, добивает врагов ниже 15% ХП") {
            damage *= 1.15f; execute = max(execute, 0.15f)
        },
        PartDef("candle", "Свечка", "🕯️", Origin.MAGIC, 0, "Поджигает, пули летят дальше") { burn += 3f; life *= 1.3f },
        PartDef("loupe", "Лупа", "🔍", Origin.COMMON, 0, "Пули крупнее, +20% урон") { size += 4f; damage *= 1.2f },
        PartDef("coin", "Счастливая монетка", "🪙", Origin.COMMON, 0, "+12% шанс крита") { crit += 0.12f },
        PartDef("dragon", "Зуб дракона", "🐉", Origin.DEMON, 1, "+20% урон, поджигает, маленький взрыв") {
            damage *= 1.2f; burn += 5f; explode += 15f
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
    GLUE("Суперклей", 1, "+1 гнездо для деталей на пушке", "🧴"),
    TOTEM("Тотем бессмертия", 2, "Воскрешает один раз", "🗿"),
    SUPERNOVA("Сверхновая", 2, "Каждые 12 попаданий — взрыв из 12 пуль", "✨"),
    BLOODLUST("Кровавая жажда", 2, "+100% крит-урон, криты лечат", "🦇"),
    CLOVER("Клевер", 2, "+1 карта при новом уровне, больше удачи", "🍀"),
    BEHEMOTH("Бегемот", 2, "Все пули взрываются", "💥"),
    GLASS("Стеклянная пушка", 2, "+80% урон, но −30% ХП", "🍷");
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
    GUNNER("Стрелок", "Подпиленный ствол и экспансивные пули, граната", c(0xFFF2B33D), 100f, 150f,
        listOf("saw", "exp"), Equip.GRENADE),
    WITCH("Ведьмак", "Аметист и нить судьбы, купол", c(0xFFB46CFF), 80f, 155f,
        listOf("amethyst", "fate"), Equip.DOME),
    ZORG("Зорг", "Инопланетный фонарик-луч, рывок", c(0xFF4DFFB0), 90f, 165f,
        listOf("torch"), Equip.DASH),
    IMP("Бес", "Адская пасть и кровавый рубин, чёрная дыра", c(0xFFFF5A4A), 115f, 145f,
        listOf("maw", "ruby"), Equip.BLACKHOLE);
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
