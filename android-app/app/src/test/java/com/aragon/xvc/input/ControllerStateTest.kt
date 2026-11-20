package com.aragon.xvc.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerStateTest {
    @Test
    fun axisIsClampedToInt16() {
        assertEquals(0, axisToInt16(0f))
        assertEquals(32767, axisToInt16(1f))
        assertEquals(-32767, axisToInt16(-1f))
        assertEquals(32767, axisToInt16(3f))
        assertEquals(-32767, axisToInt16(-3f))
        assertEquals(0, axisToInt16(Float.NaN))
    }

    @Test
    fun triggerIsClampedToByte() {
        assertEquals(0, axisToByte(-0.5f))
        assertEquals(255, axisToByte(1f))
        assertEquals(255, axisToByte(2f))
        assertEquals(127, axisToByte(0.5f))
    }

    @Test
    fun copyFromAndEquality() {
        val a = ControllerState(btnMask = Btn.A or Btn.START, lx = 100, ly = -100, rt = 255)
        val b = ControllerState()
        assertNotEquals(a, b)
        b.copyFrom(a)
        assertEquals(a, b)
    }

    @Test
    fun buttonBitsMatchThePcServerProtocol() {
        // Must stay in sync with pc-server/Protocol.cs (Btn).
        val expected = listOf(
            Btn.A, Btn.B, Btn.X, Btn.Y, Btn.LB, Btn.RB, Btn.BACK, Btn.START,
            Btn.LS, Btn.RS, Btn.GUIDE, Btn.DPAD_UP, Btn.DPAD_DOWN, Btn.DPAD_LEFT, Btn.DPAD_RIGHT
        )
        expected.forEachIndexed { i, bit -> assertEquals("bit $i", 1 shl i, bit) }
    }

    @Test
    fun xboxProfileMapsFaceButtonsAndIgnoresUnknownKeys() {
        assertEquals(Btn.A, XboxProfile.mapKeyToBtn(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(Btn.START, XboxProfile.mapKeyToBtn(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(Btn.DPAD_LEFT, XboxProfile.mapKeyToBtn(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(0, XboxProfile.mapKeyToBtn(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun everyProfileMapsTheMainButtons() {
        for (p in listOf(XboxProfile, PlayStationProfile, SwitchProProfile, StadiaProfile, UniversalProfile, GenericProfile)) {
            val mapped = listOf(
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_START
            ).map { p.mapKeyToBtn(it) }
            assertTrue("${p.javaClass.simpleName} leaves a main button unmapped: $mapped", mapped.all { it != 0 })
        }
    }

    @Test
    fun playStationFaceButtonsArePositional() {
        assertEquals(Btn.A, PlayStationProfile.mapKeyToBtn(KeyEvent.KEYCODE_BUTTON_A)) // Cross
        assertEquals(Btn.B, PlayStationProfile.mapKeyToBtn(KeyEvent.KEYCODE_BUTTON_B)) // Circle
        assertEquals(Btn.X, PlayStationProfile.mapKeyToBtn(KeyEvent.KEYCODE_BUTTON_X)) // Square
        assertEquals(Btn.Y, PlayStationProfile.mapKeyToBtn(KeyEvent.KEYCODE_BUTTON_Y)) // Triangle
        assertEquals(0, PlayStationProfile.mapKeyToBtn(KeyEvent.KEYCODE_ESCAPE))
    }
}
