package com.github.ifeel.keysjoy.ui

/**
 * Manages the toggle state of modifier keys (Shift, Ctrl, Alt, Gui/Cmd).
 *
 * Modifier keys operate in a click-to-lock, click-again-to-release mode.
 * When locked, subsequent regular key presses include the modifier in the HID report.
 */
data class ModifierState(
    var leftCtrl: Boolean = false,
    var leftShift: Boolean = false,
    var leftAlt: Boolean = false,
    var leftGui: Boolean = false   // ⌘ (Command / Windows key)
) {
    /**
     * Toggle a specific modifier by ID and return the new state.
     *
     * @param modifierId One of HID_MOD_CTRL, HID_MOD_SHIFT, HID_MOD_ALT, HID_MOD_GUI
     * @return true if modifier is now active (locked), false if released
     */
    fun toggle(modifierId: Int): Boolean {
        return when (modifierId) {
            HID_MOD_CTRL -> { leftCtrl = !leftCtrl; leftCtrl }
            HID_MOD_SHIFT -> { leftShift = !leftShift; leftShift }
            HID_MOD_ALT -> { leftAlt = !leftAlt; leftAlt }
            HID_MOD_GUI -> { leftGui = !leftGui; leftGui }
            else -> false
        }
    }

    /**
     * Check if a specific modifier is currently locked.
     */
    fun isActive(modifierId: Int): Boolean {
        return when (modifierId) {
            HID_MOD_CTRL -> leftCtrl
            HID_MOD_SHIFT -> leftShift
            HID_MOD_ALT -> leftAlt
            HID_MOD_GUI -> leftGui
            else -> false
        }
    }

    /**
     * Convert current modifier state to HID Report Byte0 bitmask.
     *
     * Bit layout (USB HID Keyboard specification):
     *   Bit 0: Left Control
     *   Bit 1: Left Shift
     *   Bit 2: Left Alt
     *   Bit 3: Left GUI (Windows/Command)
     *   Bit 4: Right Control (unused for now)
     *   Bit 5: Right Shift (unused for now)
     *   Bit 6: Right Alt (unused for now)
     *   Bit 7: Right GUI (unused for now)
     */
    fun toModifierByte(): Int {
        var b = 0
        if (leftCtrl) b = b or 0x01
        if (leftShift) b = b or 0x02
        if (leftAlt) b = b or 0x04
        if (leftGui) b = b or 0x08
        return b
    }

    /**
     * Check if any modifier is currently active.
     */
    fun hasAnyActive(): Boolean = leftCtrl || leftShift || leftAlt || leftGui

    companion object {
        const val HID_MOD_CTRL = 1
        const val HID_MOD_SHIFT = 2
        const val HID_MOD_ALT = 3
        const val HID_MOD_GUI = 4  // ⌘
    }
}
