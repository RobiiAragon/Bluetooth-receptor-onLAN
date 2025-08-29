package com.aragon.xvc.input

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.InputDevice

// --- Compatibilidad KeyEvent PlayStation ---
@Suppress("ClassName")
object PSKeyCodes {
    // Valores oficiales de Android KeyEvent (https://developer.android.com/reference/android/view/KeyEvent)
    const val KEYCODE_BUTTON_CIRCLE = 111
    const val KEYCODE_BUTTON_SQUARE = 99
    const val KEYCODE_BUTTON_TRIANGLE = 100
}


data class ControllerState(
    var btnMask: Int = 0,
    var lx: Int = 0, // -32768..32767
    var ly: Int = 0,
    var rx: Int = 0,
    var ry: Int = 0,
    var lt: Int = 0, // 0..255
    var rt: Int = 0
) {
    fun copyFrom(other: ControllerState) {
        btnMask = other.btnMask; lx = other.lx; ly = other.ly; rx = other.rx; ry = other.ry; lt = other.lt; rt = other.rt
    }
    override fun toString(): String = "state(btn=${btnMask}, lx=$lx, ly=$ly, rx=$rx, ry=$ry, lt=$lt, rt=$rt)"
}

// --- Perfiles de mapeo de controladores ---
interface ControllerProfile {
    fun mapKeyToBtn(keyCode: Int): Int
    fun applyMotionToState(ev: MotionEvent, state: ControllerState)
}

// Profile para mandos tipo Xbox (lógica actual)
object XboxProfile : ControllerProfile {
    override fun mapKeyToBtn(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> Btn.A
        KeyEvent.KEYCODE_BUTTON_B -> Btn.B
        KeyEvent.KEYCODE_BUTTON_X -> Btn.X
        KeyEvent.KEYCODE_BUTTON_Y -> Btn.Y
        KeyEvent.KEYCODE_BUTTON_L1 -> Btn.LB
        KeyEvent.KEYCODE_BUTTON_R1 -> Btn.RB
        KeyEvent.KEYCODE_BUTTON_THUMBL -> Btn.LS
        KeyEvent.KEYCODE_BUTTON_THUMBR -> Btn.RS
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> Btn.BACK
        KeyEvent.KEYCODE_BUTTON_START -> Btn.START
        KeyEvent.KEYCODE_BUTTON_MODE -> Btn.GUIDE
        KeyEvent.KEYCODE_DPAD_UP -> Btn.DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Btn.DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Btn.DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Btn.DPAD_RIGHT
        else -> 0
    }

    override fun applyMotionToState(ev: MotionEvent, state: ControllerState) {
        fun ax(code: Int): Float = ev.getAxisValue(code)
        val lx = listOf(MotionEvent.AXIS_X).map { ax(it) }.firstOrNull { it != 0f } ?: 0f
        val ly = listOf(MotionEvent.AXIS_Y).map { ax(it) }.firstOrNull { it != 0f } ?: 0f
        val rx = listOf(MotionEvent.AXIS_RX, MotionEvent.AXIS_Z).map { ax(it) }.firstOrNull { it != 0f } ?: 0f
        val ry = listOf(MotionEvent.AXIS_RY, MotionEvent.AXIS_RZ).map { ax(it) }.firstOrNull { it != 0f } ?: 0f
        state.lx = axisToInt16(lx)
        state.ly = axisToInt16(ly)
        state.rx = axisToInt16(rx)
        state.ry = axisToInt16(ry)
        val lt = listOf(
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_BRAKE
        ).map { ax(it) }.firstOrNull { it != 0f } ?: 0f
        val rt = listOf(
            MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_GAS
        ).map { ax(it) }.firstOrNull { it != 0f } ?: 0f
        state.lt = axisToByte(lt)
        state.rt = axisToByte(rt)
        val hatX = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)
        fun set(mask: Int, on: Boolean) { state.btnMask = if (on) (state.btnMask or mask) else (state.btnMask and mask.inv()) }
        set(Btn.DPAD_LEFT, hatX < -0.5f)
        set(Btn.DPAD_RIGHT, hatX > 0.5f)
        set(Btn.DPAD_UP, hatY < -0.5f)
        set(Btn.DPAD_DOWN, hatY > 0.5f)
    }
}


// Profile para Google Stadia Controller
object StadiaProfile : ControllerProfile {
    override fun mapKeyToBtn(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> Btn.A // A
        KeyEvent.KEYCODE_BUTTON_B -> Btn.B // B
        KeyEvent.KEYCODE_BUTTON_X -> Btn.X // X
        KeyEvent.KEYCODE_BUTTON_Y -> Btn.Y // Y
        KeyEvent.KEYCODE_BUTTON_L1 -> Btn.LB // LB
        KeyEvent.KEYCODE_BUTTON_R1 -> Btn.RB // RB
        KeyEvent.KEYCODE_BUTTON_THUMBL -> Btn.LS // Stick L
        KeyEvent.KEYCODE_BUTTON_THUMBR -> Btn.RS // Stick R
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> Btn.BACK // Back
        KeyEvent.KEYCODE_BUTTON_START -> Btn.START // Start
        KeyEvent.KEYCODE_BUTTON_MODE -> Btn.GUIDE // Stadia button
        KeyEvent.KEYCODE_DPAD_UP -> Btn.DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Btn.DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Btn.DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Btn.DPAD_RIGHT
        // Stadia tiene botones extra: captura y asistente, puedes mapearlos si lo deseas
        else -> 0
    }

    override fun applyMotionToState(ev: MotionEvent, state: ControllerState) {
        // Stadia usa ejes estándar, igual que Xbox
        XboxProfile.applyMotionToState(ev, state)
    }
}

// Profile para PlayStation (DualShock 4, DualSense/PS5)
object PlayStationProfile : ControllerProfile {
    override fun mapKeyToBtn(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_X -> Btn.A // X (abajo)
        PSKeyCodes.KEYCODE_BUTTON_CIRCLE -> Btn.B // Círculo (derecha)
        PSKeyCodes.KEYCODE_BUTTON_SQUARE -> Btn.X // Cuadrado (izquierda)
        PSKeyCodes.KEYCODE_BUTTON_TRIANGLE -> Btn.Y // Triángulo (arriba)
        KeyEvent.KEYCODE_BUTTON_L1 -> Btn.LB
        KeyEvent.KEYCODE_BUTTON_R1 -> Btn.RB
        KeyEvent.KEYCODE_BUTTON_THUMBL -> Btn.LS
        KeyEvent.KEYCODE_BUTTON_THUMBR -> Btn.RS
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> Btn.BACK // Share
        KeyEvent.KEYCODE_BUTTON_START -> Btn.START // Options
        KeyEvent.KEYCODE_BUTTON_MODE -> Btn.GUIDE // PS
        KeyEvent.KEYCODE_DPAD_UP -> Btn.DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Btn.DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Btn.DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Btn.DPAD_RIGHT
        else -> 0
    }

    override fun applyMotionToState(ev: MotionEvent, state: ControllerState) {
        // PlayStation sticks y triggers usan ejes estándar
        XboxProfile.applyMotionToState(ev, state)
    }
}

// Profile para Nintendo Switch Pro Controller
object SwitchProProfile : ControllerProfile {
    override fun mapKeyToBtn(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_B -> Btn.B // B (derecha)
        KeyEvent.KEYCODE_BUTTON_A -> Btn.A // A (abajo)
        KeyEvent.KEYCODE_BUTTON_Y -> Btn.Y // Y (arriba)
        KeyEvent.KEYCODE_BUTTON_X -> Btn.X // X (izquierda)
        KeyEvent.KEYCODE_BUTTON_L1 -> Btn.LB // L1
        KeyEvent.KEYCODE_BUTTON_R1 -> Btn.RB // R1
        KeyEvent.KEYCODE_BUTTON_L2 -> Btn.LT // ZL (gatillo izquierdo)
        KeyEvent.KEYCODE_BUTTON_R2 -> Btn.RT // ZR (gatillo derecho)
        KeyEvent.KEYCODE_BUTTON_THUMBL -> Btn.LS // Stick L
        KeyEvent.KEYCODE_BUTTON_THUMBR -> Btn.RS // Stick R
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> Btn.BACK // Back
        KeyEvent.KEYCODE_BUTTON_START -> Btn.START // Start
        KeyEvent.KEYCODE_BUTTON_MODE -> Btn.GUIDE // Guide
        KeyEvent.KEYCODE_DPAD_UP -> Btn.DPAD_UP // D-Pad Up
        KeyEvent.KEYCODE_DPAD_DOWN -> Btn.DPAD_DOWN // D-Pad Down
        KeyEvent.KEYCODE_DPAD_LEFT -> Btn.DPAD_LEFT // D-Pad Left
        KeyEvent.KEYCODE_DPAD_RIGHT -> Btn.DPAD_RIGHT // D-Pad Right
        else -> 0
    }

    override fun applyMotionToState(ev: MotionEvent, state: ControllerState) {
        // Switch Pro sticks y triggers usan ejes estándar
        XboxProfile.applyMotionToState(ev, state)
    }
}

// Profile para 8BitDo y mandos universales
object UniversalProfile : ControllerProfile {
    override fun mapKeyToBtn(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> Btn.A
        KeyEvent.KEYCODE_BUTTON_B -> Btn.B
        KeyEvent.KEYCODE_BUTTON_X -> Btn.X
        KeyEvent.KEYCODE_BUTTON_Y -> Btn.Y
        KeyEvent.KEYCODE_BUTTON_L1 -> Btn.LB
        KeyEvent.KEYCODE_BUTTON_R1 -> Btn.RB
        KeyEvent.KEYCODE_BUTTON_THUMBL -> Btn.LS
        KeyEvent.KEYCODE_BUTTON_THUMBR -> Btn.RS
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> Btn.BACK
        KeyEvent.KEYCODE_BUTTON_START -> Btn.START
        KeyEvent.KEYCODE_BUTTON_MODE -> Btn.GUIDE
        KeyEvent.KEYCODE_DPAD_UP -> Btn.DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Btn.DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Btn.DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Btn.DPAD_RIGHT
        else -> 0
    }

    override fun applyMotionToState(ev: MotionEvent, state: ControllerState) {
        // La mayoría usan ejes estándar
        XboxProfile.applyMotionToState(ev, state)
    }
}

// Profile para mandos genéricos Bluetooth/USB
object GenericProfile : ControllerProfile {
    override fun mapKeyToBtn(keyCode: Int): Int = UniversalProfile.mapKeyToBtn(keyCode)
    override fun applyMotionToState(ev: MotionEvent, state: ControllerState) = UniversalProfile.applyMotionToState(ev, state)
}

// Selección de profile según InputDevice
fun getProfileForDevice(device: InputDevice?): ControllerProfile {
    val name = device?.name?.lowercase() ?: ""
    return when {
        "stadia" in name -> StadiaProfile
        "dualshock" in name || "dualsense" in name || "playstation" in name || "ps4" in name || "ps5" in name -> PlayStationProfile
        "switch" in name || "nintendo" in name -> SwitchProProfile
        "8bitdo" in name || "universal" in name -> UniversalProfile
        "generic" in name || "bluetooth" in name || "usb" in name -> GenericProfile
        else -> XboxProfile
    }
}

// Botones (bitmask)
object Btn {
    const val A = 1 shl 0
    const val B = 1 shl 1
    const val X = 1 shl 2
    const val Y = 1 shl 3
    const val LB = 1 shl 4
    const val RB = 1 shl 5
    const val BACK = 1 shl 6
    const val START = 1 shl 7
    const val LS = 1 shl 8
    const val RS = 1 shl 9
    const val GUIDE = 1 shl 10
    const val DPAD_UP = 1 shl 11
    const val DPAD_DOWN = 1 shl 12
    const val DPAD_LEFT = 1 shl 13
    const val DPAD_RIGHT = 1 shl 14
    const val LT = 1 shl 15
    const val RT = 1 shl 16
}

// DEPRECATED: Usa ControllerProfile.mapKeyToBtn
fun mapKeyToBtn(keyCode: Int): Int = XboxProfile.mapKeyToBtn(keyCode)

private fun axisToInt16(v: Float): Int {
    val clamped = v.coerceIn(-1f, 1f)
    return (clamped * 32767f).toInt()
}
private fun axisToByte(v: Float): Int {
    val clamped = v.coerceIn(0f, 1f)
    return (clamped * 255f).toInt()
}

// DEPRECATED: Usa ControllerProfile.applyMotionToState
fun applyMotionToState(ev: MotionEvent, state: ControllerState) = XboxProfile.applyMotionToState(ev, state)

// Usa el profile adecuado para aplicar el evento de tecla
fun applyKeyToStateWithProfile(ev: KeyEvent, state: ControllerState, profile: ControllerProfile): Boolean {
    val m = profile.mapKeyToBtn(ev.keyCode)
    if (m == 0) return false
    state.btnMask = if (ev.action == KeyEvent.ACTION_DOWN) state.btnMask or m else state.btnMask and m.inv()
    return true
}
