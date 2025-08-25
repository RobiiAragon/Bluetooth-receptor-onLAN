package com.aragon.xvc.input

import android.view.KeyEvent
import android.view.MotionEvent

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
}

fun mapKeyToBtn(keyCode: Int): Int = when (keyCode) {
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

private fun axisToInt16(v: Float): Int {
    val clamped = v.coerceIn(-1f, 1f)
    return (clamped * 32767f).toInt()
}
private fun axisToByte(v: Float): Int {
    val clamped = v.coerceIn(0f, 1f)
    return (clamped * 255f).toInt()
}

fun applyMotionToState(ev: MotionEvent, state: ControllerState) {
    fun ax(code: Int): Float = ev.getAxisValue(code)

    // Sticks (varía por mando; múltiples ejes posibles)
    val lx = listOf(MotionEvent.AXIS_X).map { ax(it) }.firstOrNull { it != 0f } ?: 0f
    val ly = listOf(MotionEvent.AXIS_Y).map { ax(it) }.firstOrNull { it != 0f } ?: 0f
    val rx = listOf(MotionEvent.AXIS_RX, MotionEvent.AXIS_Z).map { ax(it) }.firstOrNull { it != 0f } ?: 0f
    val ry = listOf(MotionEvent.AXIS_RY, MotionEvent.AXIS_RZ).map { ax(it) }.firstOrNull { it != 0f } ?: 0f

    state.lx = axisToInt16(lx)
    state.ly = axisToInt16(ly)
    state.rx = axisToInt16(rx)
    state.ry = axisToInt16(ry)

    // Triggers (intenta varios ejes comunes)
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

    // DPAD por ejes HAT_X/HAT_Y (comportamiento simple y directo)
    val hatX = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
    val hatY = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)
    fun set(mask: Int, on: Boolean) { state.btnMask = if (on) (state.btnMask or mask) else (state.btnMask and mask.inv()) }
    set(Btn.DPAD_LEFT, hatX < -0.5f)
    set(Btn.DPAD_RIGHT, hatX > 0.5f)
    set(Btn.DPAD_UP, hatY < -0.5f)
    set(Btn.DPAD_DOWN, hatY > 0.5f)
}

fun applyKeyToState(ev: KeyEvent, state: ControllerState): Boolean {
    val m = mapKeyToBtn(ev.keyCode)
    if (m == 0) return false
    state.btnMask = if (ev.action == KeyEvent.ACTION_DOWN) state.btnMask or m else state.btnMask and m.inv()
    return true
}
