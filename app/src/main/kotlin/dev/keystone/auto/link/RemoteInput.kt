package dev.keystone.auto.link

import dev.keystone.auto.core.Panel
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The phone as a mouse, keyboard and remote for the projector.
 *
 * The projector ships Android's `uinput` tool, which turns JSON lines on stdin into a virtual input device (no root: the
 * shell user may use /dev/uhid). One persistent `uinput -` process is started over ADB and fed live. Android sees a
 * real mouse (so it draws its normal pointer), a real keyboard and a real set of remote keys.
 */
object Evdev {
    const val EV_SYN = 0
    const val EV_KEY = 1
    const val EV_REL = 2
    const val REL_X = 0
    const val REL_Y = 1
    const val REL_HWHEEL = 6
    const val REL_WHEEL = 8
    const val BTN_LEFT = 272
    const val BTN_RIGHT = 273
    const val BTN_MIDDLE = 274
    const val EV_ABS = 3
    const val BTN_TOUCH = 330
    const val ABS_MT_SLOT = 47
    const val ABS_MT_POSITION_X = 53
    const val ABS_MT_POSITION_Y = 54
    const val ABS_MT_TRACKING_ID = 57
}

/** Linux key codes (what `uinput` wants) for the keys the app sends. Android maps them with its generic key layout. */
object RemoteKey {
    const val ESC = 1
    const val BACKSPACE = 14
    const val TAB = 15
    const val ENTER = 28
    const val SHIFT = 42
    const val SPACE = 57
    const val MUTE = 113
    const val VOLUME_DOWN = 114
    const val VOLUME_UP = 115
    const val POWER = 116
    const val DELETE = 111
    const val UP = 103
    const val LEFT = 105
    const val RIGHT = 106
    const val DOWN = 108
    const val MENU = 139
    const val BACK = 158
    const val HOME = 172
    const val SEARCH = 217
    const val PREVIOUS = 165
    const val NEXT = 163
    const val PLAY_PAUSE = 164
    const val REWIND = 168
    const val FAST_FORWARD = 208
    const val OK = 353              // KEY_SELECT -> DPAD_CENTER
    const val APP_SWITCH = 580      // recent apps
    const val INFO = 358
    const val CHANNEL_UP = 402
    const val CHANNEL_DOWN = 403
    const val PAGE_UP = 104
    const val PAGE_DOWN = 109
}

/** Pure JSON building for `uinput`, kept separate so it can be unit-tested. */
object InputProtocol {
    const val MOUSE_ID = 1
    const val KEYBOARD_ID = 2
    const val COMMAND = "uinput -"

    /** Keys the virtual keyboard declares: every ordinary key plus the remote/TV keys above 248. */
    val KEYBOARD_KEYS: List<Int> = (1..248).toList() + listOf(352, 353, 358, 362, 402, 403, 580)

    fun registerMouse(): String =
        """{"id":$MOUSE_ID,"command":"register","name":"ProjectorConnect Mouse","vid":6353,"pid":11330,"bus":"usb","configuration":[""" +
            """{"type":100,"data":[1,2]},{"type":101,"data":[272,273,274]},{"type":102,"data":[0,1,6,8]}],"ff_effects_max":0}"""

    fun registerKeyboard(): String =
        """{"id":$KEYBOARD_ID,"command":"register","name":"ProjectorConnect Keyboard","vid":6353,"pid":11331,"bus":"usb","configuration":[""" +
            """{"type":100,"data":[1]},{"type":101,"data":[${KEYBOARD_KEYS.joinToString(",")}]}],"ff_effects_max":0}"""

    const val TOUCH_ID = 3
    const val TOUCH_NAME = "ProjectorConnect Touch"
    const val TOUCH_SLOTS = 2

    /** A virtual multi-touch screen (protocol B) the size of the projector's 1280x720 framebuffer. */
    fun registerTouch(w: Int = 1280, h: Int = 720): String {
        fun abs(code: Int, max: Int) = """{"code":$code,"info":{"value":0,"minimum":0,"maximum":$max,"fuzz":0,"flat":0,"resolution":0}}"""
        return """{"id":$TOUCH_ID,"command":"register","name":"$TOUCH_NAME","vid":6353,"pid":11332,"bus":"usb","configuration":[""" +
            """{"type":100,"data":[1,3]},{"type":101,"data":[${Evdev.BTN_TOUCH}]},""" +
            """{"type":103,"data":[0,1,${Evdev.ABS_MT_SLOT},${Evdev.ABS_MT_POSITION_X},${Evdev.ABS_MT_POSITION_Y},${Evdev.ABS_MT_TRACKING_ID}]},{"type":110,"data":[1]}],""" +
            """"abs_info":[${abs(0, w - 1)},${abs(1, h - 1)},${abs(Evdev.ABS_MT_SLOT, TOUCH_SLOTS - 1)},${abs(Evdev.ABS_MT_POSITION_X, w - 1)},""" +
            """${abs(Evdev.ABS_MT_POSITION_Y, h - 1)},${abs(Evdev.ABS_MT_TRACKING_ID, 65535)}],"ff_effects_max":0}"""
    }

    private val SYN = listOf(Evdev.EV_SYN, 0, 0)

    /** A finger goes down in [slot] at (x, y); [firstFinger] is true when no other finger is down (it sets BTN_TOUCH). */
    fun touchDown(slot: Int, trackingId: Int, x: Int, y: Int, firstFinger: Boolean): String {
        val e = arrayListOf(Evdev.EV_ABS, Evdev.ABS_MT_SLOT, slot, Evdev.EV_ABS, Evdev.ABS_MT_TRACKING_ID, trackingId,
            Evdev.EV_ABS, Evdev.ABS_MT_POSITION_X, x, Evdev.EV_ABS, Evdev.ABS_MT_POSITION_Y, y)
        if (firstFinger) e += listOf(Evdev.EV_KEY, Evdev.BTN_TOUCH, 1)
        return inject(TOUCH_ID, e + SYN)
    }

    /** Moves of several fingers in one frame: pairs of slot to (x, y). */
    fun touchMove(points: List<Triple<Int, Int, Int>>): String {
        val e = ArrayList<Int>()
        for ((slot, x, y) in points) e += listOf(Evdev.EV_ABS, Evdev.ABS_MT_SLOT, slot, Evdev.EV_ABS, Evdev.ABS_MT_POSITION_X, x, Evdev.EV_ABS, Evdev.ABS_MT_POSITION_Y, y)
        return inject(TOUCH_ID, e + SYN)
    }

    /** The finger in [slot] lifts; [lastFinger] is true when it was the only one down (it clears BTN_TOUCH). */
    fun touchUp(slot: Int, lastFinger: Boolean): String {
        val e = arrayListOf(Evdev.EV_ABS, Evdev.ABS_MT_SLOT, slot, Evdev.EV_ABS, Evdev.ABS_MT_TRACKING_ID, -1)
        if (lastFinger) e += listOf(Evdev.EV_KEY, Evdev.BTN_TOUCH, 0)
        return inject(TOUCH_ID, e + SYN)
    }

    private fun inject(id: Int, events: List<Int>) = """{"id":$id,"command":"inject","events":[${events.joinToString(",")}]}"""

    fun move(dx: Int, dy: Int): String {
        val e = ArrayList<Int>()
        if (dx != 0) e += listOf(Evdev.EV_REL, Evdev.REL_X, dx)
        if (dy != 0) e += listOf(Evdev.EV_REL, Evdev.REL_Y, dy)
        e += listOf(Evdev.EV_SYN, 0, 0)
        return inject(MOUSE_ID, e)
    }

    /** One wheel notch is 1; positive scrolls up (content moves down). */
    fun wheel(v: Int, h: Int = 0): String {
        val e = ArrayList<Int>()
        if (v != 0) e += listOf(Evdev.EV_REL, Evdev.REL_WHEEL, v)
        if (h != 0) e += listOf(Evdev.EV_REL, Evdev.REL_HWHEEL, h)
        e += listOf(Evdev.EV_SYN, 0, 0)
        return inject(MOUSE_ID, e)
    }

    fun button(code: Int, down: Boolean) = inject(MOUSE_ID, listOf(Evdev.EV_KEY, code, if (down) 1 else 0, Evdev.EV_SYN, 0, 0))

    fun key(code: Int, down: Boolean) = inject(KEYBOARD_ID, listOf(Evdev.EV_KEY, code, if (down) 1 else 0, Evdev.EV_SYN, 0, 0))

    /** A whole key press in one line (down, up). */
    fun tap(code: Int, shift: Boolean = false): String {
        val e = ArrayList<Int>()
        if (shift) e += listOf(Evdev.EV_KEY, RemoteKey.SHIFT, 1, Evdev.EV_SYN, 0, 0)
        e += listOf(Evdev.EV_KEY, code, 1, Evdev.EV_SYN, 0, 0, Evdev.EV_KEY, code, 0, Evdev.EV_SYN, 0, 0)
        if (shift) e += listOf(Evdev.EV_KEY, RemoteKey.SHIFT, 0, Evdev.EV_SYN, 0, 0)
        return inject(KEYBOARD_ID, e)
    }

    /** US-keyboard mapping for typing text: character -> (key code, needs shift). */
    private val unshifted = buildMap<Char, Int> {
        "qwertyuiop".forEachIndexed { i, c -> put(c, 16 + i) }
        "asdfghjkl".forEachIndexed { i, c -> put(c, 30 + i) }
        "zxcvbnm".forEachIndexed { i, c -> put(c, 44 + i) }
        "1234567890".forEachIndexed { i, c -> put(c, 2 + i) }
        put(' ', 57); put('\n', 28); put('\t', 15)
        put('-', 12); put('=', 13); put('[', 26); put(']', 27); put(';', 39); put('\'', 40); put('`', 41)
        put('\\', 43); put(',', 51); put('.', 52); put('/', 53)
    }
    private val shiftedPairs = mapOf(
        '!' to '1', '@' to '2', '#' to '3', '$' to '4', '%' to '5', '^' to '6', '&' to '7', '*' to '8', '(' to '9', ')' to '0',
        '_' to '-', '+' to '=', '{' to '[', '}' to ']', ':' to ';', '"' to '\'', '~' to '`', '|' to '\\', '<' to ',', '>' to '.', '?' to '/',
    )

    /** The key presses that type [c], or null when the character is not on a US keyboard. */
    fun keyFor(c: Char): Pair<Int, Boolean>? {
        unshifted[c]?.let { return it to false }
        if (c in 'A'..'Z') return unshifted[c.lowercaseChar()]!! to true
        shiftedPairs[c]?.let { return unshifted[it]!! to true }
        return null
    }

    /** Lines that type [text]; characters that cannot be typed are returned separately. */
    fun typeLines(text: String): Pair<List<String>, List<Char>> {
        val lines = ArrayList<String>()
        val skipped = ArrayList<Char>()
        for (c in text.replace("\r\n", "\n")) {
            val k = keyFor(c)
            if (k == null) skipped += c else lines += tap(k.first, k.second)
        }
        return lines to skipped
    }
}

/**
 * Owns the `uinput` process. Everything is written from one thread; pointer movement is gathered and sent about 80 times
 * a second so a fast swipe does not flood the connection.
 */
class RemoteInput(private val sh: Shell, private val registerTimeoutMs: Long = REGISTER_WAIT_MS) : AutoCloseable {
    private var stream: ShellStream? = null
    private val exec: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "remote-input").apply { isDaemon = true } }
    private var pendingX = 0.0
    private var pendingY = 0.0
    private var pendingWheel = 0.0
    private val lock = Any()
    @Volatile var ready = false
        private set

    /** Starts the process and registers the mouse and keyboard. Blocks for the short time Android needs to notice them. */
    fun start() {
        val s = sh.openStream(InputProtocol.COMMAND)
        s.write(InputProtocol.registerMouse() + "\n")
        s.write(InputProtocol.registerKeyboard() + "\n")
        stream = s
        // The projector's JVM is slow to start: wait until Android lists the keyboard (the second device registered).
        val deadline = System.currentTimeMillis() + registerTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            val seen = try { sh.run("dumpsys input | grep -c 'ProjectorConnect Keyboard'").out.trim().toIntOrNull() ?: 0 } catch (_: Exception) { 0 }
            if (seen > 0) break
            Thread.sleep(300)
        }
        ready = true
        exec.scheduleWithFixedDelay({ flushMotion() }, 12, 12, TimeUnit.MILLISECONDS)
    }

    val isOpen get() = ready && stream?.isOpen == true

    private fun write(line: String) {
        val s = stream ?: return
        if (!s.isOpen) return
        try { s.write(line + "\n") } catch (_: Exception) { ready = false }
    }

    private fun flushMotion() {
        var dx = 0; var dy = 0; var w = 0
        synchronized(lock) {
            dx = pendingX.toInt(); dy = pendingY.toInt(); w = pendingWheel.toInt()
            pendingX -= dx; pendingY -= dy; pendingWheel -= w
        }
        if (dx != 0 || dy != 0) write(InputProtocol.move(dx, dy))
        if (w != 0) write(InputProtocol.wheel(w))
    }

    /** Pointer movement in projector pixels; fractions are carried over to the next call. */
    fun move(dx: Double, dy: Double) = synchronized(lock) { pendingX += dx; pendingY += dy }

    /** Scroll by notches (fractions accumulate). Positive = scroll up. */
    fun scroll(notches: Double) = synchronized(lock) { pendingWheel += notches }

    private fun ordered(line: String) = exec.execute { flushMotion(); write(line) }

    fun buttonDown(code: Int = Evdev.BTN_LEFT) = ordered(InputProtocol.button(code, true))
    fun buttonUp(code: Int = Evdev.BTN_LEFT) = ordered(InputProtocol.button(code, false))

    fun click(code: Int = Evdev.BTN_LEFT) = exec.execute {
        flushMotion(); write(InputProtocol.button(code, true)); write(InputProtocol.button(code, false))
    }

    fun key(code: Int) = ordered(InputProtocol.tap(code))

    /** Types text; returns the characters that could not be typed (outside a US keyboard). */
    fun type(text: String): List<Char> {
        val (lines, skipped) = InputProtocol.typeLines(text)
        exec.execute { flushMotion(); lines.forEach { write(it) } }
        return skipped
    }

    override fun close() {
        ready = false
        exec.shutdown()
        try { stream?.close() } catch (_: Exception) { }
        stream = null
    }

    companion object {
        const val REGISTER_WAIT_MS = 9000L
    }
}


/**
 * A virtual touch screen on the projector, alive only while the screen mirror is open (so the projector never sees a touch
 * screen otherwise). Coordinates are projector framebuffer pixels (1280x720), before the keystone warp.
 */
class TouchInput(private val sh: Shell, private val registerTimeoutMs: Long = RemoteInput.REGISTER_WAIT_MS) : AutoCloseable {
    private var stream: ShellStream? = null
    private val exec: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "touch-input").apply { isDaemon = true } }
    private val down = BooleanArray(InputProtocol.TOUCH_SLOTS)
    private val pending = HashMap<Int, Pair<Int, Int>>()
    private val lock = Any()
    private var nextId = 1
    @Volatile var ready = false
        private set

    fun start() {
        val s = sh.openStream(InputProtocol.COMMAND)
        s.write(InputProtocol.registerTouch() + "\n")
        stream = s
        val deadline = System.currentTimeMillis() + registerTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            val seen = try { sh.run("dumpsys input | grep -c '${InputProtocol.TOUCH_NAME}'").out.trim().toIntOrNull() ?: 0 } catch (_: Exception) { 0 }
            if (seen > 0) break
            Thread.sleep(300)
        }
        ready = true
        exec.scheduleWithFixedDelay({ flush() }, 16, 16, TimeUnit.MILLISECONDS)
    }

    val isOpen get() = ready && stream?.isOpen == true

    private fun write(line: String) {
        val s = stream ?: return
        if (!s.isOpen) return
        try { s.write(line + "\n") } catch (_: Exception) { ready = false }
    }

    private fun flush() {
        val moves: List<Triple<Int, Int, Int>>
        synchronized(lock) {
            if (pending.isEmpty()) return
            moves = pending.filter { down[it.key] }.map { Triple(it.key, it.value.first, it.value.second) }
            pending.clear()
        }
        if (moves.isNotEmpty()) write(InputProtocol.touchMove(moves))
    }

    private fun clamp(x: Int, y: Int) = x.coerceIn(0, Panel.W.toInt() - 1) to y.coerceIn(0, Panel.H.toInt() - 1)

    fun touchDown(slot: Int, x: Int, y: Int) {
        if (slot !in down.indices) return
        val (cx, cy) = clamp(x, y)
        exec.execute {
            flush()
            if (down[slot]) return@execute
            val first = down.none { it }
            down[slot] = true
            write(InputProtocol.touchDown(slot, nextId++ and 0xFFFF, cx, cy, first))
        }
    }

    fun touchMove(slot: Int, x: Int, y: Int) {
        if (slot !in down.indices) return
        synchronized(lock) { pending[slot] = clamp(x, y) }
    }

    fun touchUp(slot: Int) {
        if (slot !in down.indices) return
        exec.execute {
            flush()
            if (!down[slot]) return@execute
            down[slot] = false
            write(InputProtocol.touchUp(slot, down.none { it }))
        }
    }

    /** Lifts every finger (the screen was left while a finger was down). */
    fun releaseAll() { for (i in down.indices) touchUp(i) }

    override fun close() {
        ready = false
        exec.shutdown()
        try { stream?.close() } catch (_: Exception) { }
        stream = null
    }
}
