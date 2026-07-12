package com.github.ifeel.keysjoy.ui

import android.bluetooth.BluetoothHidDevice
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import com.github.ifeel.keysjoy.R
import com.github.ifeel.keysjoy.reports.KeyboardReport

/**
 * Definition of a single keyboard key.
 *
 * @param label        Display text on the key
 * @param hidCode      USB HID Usage ID (Keyboard/Keypad page 0x07)
 * @param isModifier   Whether this is a toggle modifier key (Shift/Ctrl/Alt/Gui)
 * @param modifierId   For modifier keys, the modifier type ID from ModifierState
 * @param colSpan      Number of columns this key spans (e.g. Space=6)
 */
data class KeyDef(
    val label: String,
    val hidCode: Int,
    val isModifier: Boolean = false,
    val modifierId: Int = 0,
    val colSpan: Int = 1,
    val isSpecial: Boolean = false,
    val specialAction: String? = null
)

/**
 * Complete key layout definitions for left and right keyboard panels.
 */
object KeyLayouts {

    /** Left panel: 6 columns x 6 rows */
    val LEFT_PANEL: List<List<KeyDef>> = listOf(
        // Row 1
        listOf(
            KeyDef("`", 0x35),      // Grave accent
            KeyDef("1", 0x1E),
            KeyDef("2", 0x1F),
            KeyDef("3", 0x20),
            KeyDef("4", 0x21),
            KeyDef("5", 0x22)
        ),
        // Row 2
        listOf(
            KeyDef("Tab", 0x2B),
            KeyDef("Q", 0x14),
            KeyDef("W", 0x1A),
            KeyDef("E", 0x08),
            KeyDef("R", 0x15),
            KeyDef("T", 0x17)
        ),
        // Row 3
        listOf(
            KeyDef("Caps", 0x39, isModifier = true),  // CapsLock
            KeyDef("A", 0x04),
            KeyDef("S", 0x16),
            KeyDef("D", 0x07),
            KeyDef("F", 0x09),
            KeyDef("G", 0x0A)
        ),
        // Row 4
        listOf(
            KeyDef("Shift", 0, isModifier = true, modifierId = ModifierState.HID_MOD_SHIFT),
            KeyDef("Z", 0x1D),
            KeyDef("X", 0x1B),
            KeyDef("C", 0x06),
            KeyDef("V", 0x19),
            KeyDef("B", 0x05)
        ),
        // Row 5
        listOf(
            KeyDef("Ctrl", 0, isModifier = true, modifierId = ModifierState.HID_MOD_CTRL),
            KeyDef("\u2318", 0, isModifier = true, modifierId = ModifierState.HID_MOD_GUI), // ⌘
            KeyDef("Alt", 0, isModifier = true, modifierId = ModifierState.HID_MOD_ALT),
            KeyDef("Esc", 0x29),
            KeyDef("BkSp", 0x2A),
            KeyDef("Del", 0x4C)  // Forward Delete
        ),
        // Row 6
        listOf(
            KeyDef("Space", 0x2C, colSpan = 3),  // Space spans first 3 columns
            KeyDef("70%", 0, isSpecial = true, specialAction = "height_toggle"),  // col4: height preset
            KeyDef("🖱️", 0, isSpecial = true, specialAction = "portrait"),
            KeyDef("👋", 0, isSpecial = true, specialAction = "haptic_toggle")
        )
    )

    /** Right panel: 6 columns x 6 rows */
    val RIGHT_PANEL: List<List<KeyDef>> = listOf(
        // Row 1
        listOf(
            KeyDef("6", 0x23),
            KeyDef("7", 0x24),
            KeyDef("8", 0x25),
            KeyDef("9", 0x26),
            KeyDef("0", 0x27),
            KeyDef("-", 0x2D)
        ),
        // Row 2
        listOf(
            KeyDef("Y", 0x1C),
            KeyDef("U", 0x18),
            KeyDef("I", 0x0C),
            KeyDef("O", 0x12),
            KeyDef("P", 0x13),
            KeyDef("BkSp", 0x2A)
        ),
        // Row 3
        listOf(
            KeyDef("H", 0x0B),
            KeyDef("J", 0x0D),
            KeyDef("K", 0x0E),
            KeyDef("L", 0x0F),
            KeyDef(";", 0x33),
            KeyDef("'", 0x34)
        ),
        // Row 4
        listOf(
            KeyDef("N", 0x11),
            KeyDef("M", 0x10),
            KeyDef("[", 0x2F),
            KeyDef("]", 0x30),
            KeyDef("/", 0x38),
            KeyDef("=", 0x2E)
        ),
        // Row 5
        listOf(
            KeyDef("Home", 0x4A),
            KeyDef("End", 0x4D),
            KeyDef("\u2191", 0x52),   // ↑ Up Arrow
            KeyDef("\\", 0x31),
            KeyDef("⌥", 0, isModifier = true, modifierId = ModifierState.HID_MOD_ALT),
            KeyDef("Enter", 0x28)
        ),
        // Row 6
        listOf(
            KeyDef(",", 0x36),
            KeyDef("\u2190", 0x50),   // ← Left Arrow
            KeyDef("\u2193", 0x51),   // ↓ Down Arrow
            KeyDef("\u2192", 0x4F),   // → Right Arrow
            KeyDef("Space", 0x2C),
            KeyDef(".", 0x37)
        )
    )
}

/**
 * Helper class responsible for building keyboard panels and handling key events.
 *
 * Constructs a 6×6 GridLayout from KeyDef lists, manages modifier key visual state,
 * and sends HID keyboard reports via BluetoothHidDevice.
 *
 * ## Relationship with KeyboardSender
 *
 * This class and [com.github.ifeel.keysjoy.senders.KeyboardSender] serve different
 * input paths and are intentionally independent:
 *
 * - **KeyboardSender**: Handles external physical keyboard events (Android KeyEvent → HID report).
 *   It maintains its own key state tracking for modifier keys pressed on the external keyboard.
 *
 * - **KeyboardPanelHelper**: Handles on-screen virtual keyboard buttons (raw HID usage codes →
 *   HID report). It maintains its own [ModifierState] for toggle-lock modifier keys on the
 *   virtual keyboard.
 *
 * Both send reports to the same [BluetoothHidDevice] but do not interfere because:
 * 1. They serve different input sources (external keyboard vs. on-screen keyboard)
 * 2. Their modifier states are independent (a physical Shift press does not affect the
 *    virtual Shift toggle lock, and vice versa)
 * 3. Each HID report fully specifies the current modifier byte, so consecutive reports
 *    from different sources do not leave stale modifier bits
 *
 * @param context   Android context for resource access
 * @param hidDevice Bluetooth HID device for sending reports
 * @param host      Connected Bluetooth host device
 */
class KeyboardPanelHelper(
    private val context: Context,
    private val hidDevice: BluetoothHidDevice?,
    private val host: android.bluetooth.BluetoothDevice?,
    var hapticEnabled: Boolean = true
) {
    companion object {
        private const val TAG = "KeyboardPanelHelper"
        private const val KEY_RELEASE_DELAY_MS = 50L
    }

    /** Callback invoked when the user presses the "Port" (portrait) special key */
    var onPortraitRequested: (() -> Unit)? = null

    /** Callback invoked for Shift+Arrow key scroll: (vScrollDelta, hScrollDelta) */
    var onScrollRequested: ((vDelta: Int, hDelta: Int) -> Unit)? = null

    private val modifierState = ModifierState()
    private var capsLockActive: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Map from button view to its KeyDef for visual state updates */
    private val keyButtonMap = mutableMapOf<View, KeyDef>()

    /**
     * Build a keyboard panel inside the given GridLayout.
     * Removes all existing child views before building to prevent duplicate buttons
     * on reconnection (e.g. Bluetooth disconnect/reconnect cycle).
     *
     * @param gridLayout  Target GridLayout (must have 6 columns)
     * @param keyLayout   Row-major list of key definitions
     */
    fun buildKeyboard(gridLayout: GridLayout, keyLayout: List<List<KeyDef>>) {
        gridLayout.removeAllViews()
        // Don't clear keyButtonMap — allow multiple panels to coexist

        for (row in keyLayout) {
            for (keyDef in row) {
                val button = createKeyButton(keyDef)
                val params = createGridParams(keyDef)
                gridLayout.addView(button, params)
                keyButtonMap[button] = keyDef
            }
        }
        refreshKeyLabels()
    }

    /**
     * Create a styled button for a single key.
     */
    private fun createKeyButton(keyDef: KeyDef): Button {
        return Button(context).apply {
            text = keyDef.label
            textSize = if (keyDef.label.length > 2) 10f else 13f
            setTextColor(context.resources.getColor(R.color.key_normal_text, null))
            gravity = Gravity.CENTER
            isAllCaps = false
            setPadding(2, 2, 2, 2)
            // Set initial background based on key type
            background = when {
                keyDef.isSpecial -> context.resources.getDrawable(R.drawable.key_button_modifier, null)
                keyDef.isModifier -> context.resources.getDrawable(R.drawable.key_button_modifier, null)
                else -> context.resources.getDrawable(R.drawable.key_button_normal, null)
            }

            // Haptic toggle button emoji reflects current state
            if (keyDef.specialAction == "haptic_toggle") {
                text = if (hapticEnabled) "👋" else "✋"
            }

            setOnClickListener {
                onKeyClick(keyDef, this)
            }
        }
    }

    /**
     * Create GridLayout.LayoutParams with column span support.
     */
    private fun createGridParams(keyDef: KeyDef): GridLayout.LayoutParams {
        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = 0
            columnSpec = if (keyDef.colSpan > 1) {
                GridLayout.spec(GridLayout.UNDEFINED, keyDef.colSpan, 1f)
            } else {
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(1, 1, 1, 1)
        }
        return params
    }

    /**
     * Handle key click: toggle modifier or send HID report.
     */
    private fun onKeyClick(keyDef: KeyDef, button: Button) {
        if (hapticEnabled) {
            button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (keyDef.isSpecial) {
            handleSpecialKey(keyDef, button)
            return
        }
        if (keyDef.isModifier) {
            handleModifierKey(keyDef, button)
        } else {
            handleRegularKey(keyDef)
        }
    }

    /**
     * Activate modifier key as sticky (one-shot).
     * A single press activates the modifier for the next regular key only.
     * Pressing again while active cancels the sticky state.
     */
    private fun handleModifierKey(keyDef: KeyDef, button: Button) {
        // Caps Lock: send HID keycode as toggle (not a sticky modifier)
        if (keyDef.label == "Caps") {
            val device = hidDevice ?: return
            val btHost = host ?: return

            capsLockActive = !capsLockActive

            // Send CapsLock keycode (press)
            val report = byteArrayOf(0, 0, keyDef.hidCode.toByte())
            device.sendReport(btHost, KeyboardReport.ID, report)

            // Release after delay
            mainHandler.postDelayed({
                device.sendReport(btHost, KeyboardReport.ID, byteArrayOf(0, 0, 0))
            }, KEY_RELEASE_DELAY_MS)

            button.background = if (capsLockActive) {
                context.resources.getDrawable(R.drawable.key_button_hold, null)
            } else {
                context.resources.getDrawable(R.drawable.key_button_modifier, null)
            }
            // Caps does NOT change key labels (no refreshKeyLabels call)
            return
        }

        val wasActive = modifierState.isActive(keyDef.modifierId)

        if (wasActive) {
            // Already active → release (cancel sticky)
            modifierState.toggle(keyDef.modifierId)
            button.background = context.resources.getDrawable(R.drawable.key_button_modifier, null)
            Log.d(TAG, "Modifier ${keyDef.label} sticky cancelled")
        } else {
            // Activate as sticky (one-shot)
            modifierState.toggle(keyDef.modifierId)
            button.background = context.resources.getDrawable(R.drawable.key_button_hold, null)
            Log.d(TAG, "Modifier ${keyDef.label} sticky activated")
        }
        refreshKeyLabels()
    }

    /**
     * Check if a key definition represents an arrow key.
     */
    private fun isArrowKey(keyDef: KeyDef): Boolean {
        return keyDef.hidCode in listOf(0x52, 0x50, 0x51, 0x4F)
        // 0x52 = Up, 0x50 = Left, 0x51 = Down, 0x4F = Right
    }

    /**
     * Get scroll delta for an arrow key.
     */
    private fun getScrollDelta(keyDef: KeyDef): Pair<Int, Int> {
        return when (keyDef.hidCode) {
            0x52 -> Pair(-5, 0)  // Up: scroll up (negative vScroll)
            0x51 -> Pair(5, 0)   // Down: scroll down (positive vScroll)
            0x50 -> Pair(0, -5)  // Left: scroll left (negative hScroll)
            0x4F -> Pair(0, 5)   // Right: scroll right (positive hScroll)
            else -> Pair(0, 0)
        }
    }

    /**
     * Send a regular key: construct HID report, send via Bluetooth.
     */
    private fun handleRegularKey(keyDef: KeyDef) {
        val device = hidDevice ?: run {
            Log.w(TAG, "Cannot send key: HID device is null (not connected)")
            return
        }
        val btHost = host ?: run {
            Log.w(TAG, "Cannot send key: host device is null")
            return
        }

        val modifierByte = modifierState.toModifierByte()
        val keyCodeByte = keyDef.hidCode.toByte()

        // Create keyboard report: [modifier, reserved, keycode]
        val report = byteArrayOf(modifierByte.toByte(), 0, keyCodeByte)

        // Send key-down report
        if (!device.sendReport(btHost, KeyboardReport.ID, report)) {
            Log.e(TAG, "Failed to send key-down report")
            return
        }

        Log.d(TAG, "Sent key: ${keyDef.label} (HID 0x${keyDef.hidCode.toString(16)}) mod=0x${modifierByte.toString(16)}")

        // Auto-release all sticky modifiers after sending the key
        if (modifierState.hasAnyActive()) {
            // Reset all modifier button backgrounds first
            // Caps Lock is a toggle-lock (not sticky) — keep it active if locked
            for ((view, def) in keyButtonMap) {
                if (def.isModifier) {
                    view.background = if (def.label == "Caps" && capsLockActive) {
                        context.resources.getDrawable(R.drawable.key_button_hold, null)
                    } else {
                        context.resources.getDrawable(R.drawable.key_button_modifier, null)
                    }
                }
            }
            // Clear all modifier states
            modifierState.leftCtrl = false
            modifierState.leftShift = false
            modifierState.leftAlt = false
            modifierState.leftGui = false
            // Update key labels (e.g., Shift released → lowercase)
            refreshKeyLabels()
        }

        // Schedule key release
        val releaseReport = byteArrayOf(0, 0, 0)

        mainHandler.postDelayed({
            if (!device.sendReport(btHost, KeyboardReport.ID, releaseReport)) {
                Log.e(TAG, "Failed to send key-up report for ${keyDef.label}")
            }
        }, KEY_RELEASE_DELAY_MS)
    }

    /**
     * Handle special action keys (portrait switch, haptic toggle, etc.).
     */
    private fun handleSpecialKey(keyDef: KeyDef, button: Button) {
        when (keyDef.specialAction) {
            "portrait" -> {
                onPortraitRequested?.invoke()
            }
            "haptic_toggle" -> {
                hapticEnabled = !hapticEnabled
                button.text = if (hapticEnabled) "👋" else "✋"
            }
        }
    }

    /**
     * Refresh key labels based on current Shift state.
     */
    private fun refreshKeyLabels() {
        val shiftActive = modifierState.leftShift || capsLockActive
        for ((view, keyDef) in keyButtonMap) {
            if (keyDef.isSpecial || keyDef.specialAction != null) continue
            if (!keyDef.isModifier && view is Button) {
                view.text = getShiftedLabel(keyDef.label, shiftActive)
            }
        }
    }

    /**
     * Get the shifted/unshifted label for a key.
     */
    private fun getShiftedLabel(base: String, shift: Boolean): String {
        if (base.length > 1) return base
        if (!shift) {
            return if (base[0].isLetter()) base.lowercase() else base
        }
        return when (base) {
            "`" -> "~"; "1" -> "!"; "2" -> "@"; "3" -> "#"; "4" -> "$"; "5" -> "%"
            "6" -> "^"; "7" -> "&"; "8" -> "*"; "9" -> "("; "0" -> ")"; "-" -> "_"; "=" -> "+"
            "[" -> "{"; "]" -> "}"; ";" -> ":"; "'" -> "\""; "," -> "<"; "." -> ">"; "/" -> "?"
            "\\" -> "|"
            else -> base.uppercase()
        }
    }

    /**
     * Get the current modifier state (for external synchronization).
     */
    fun getModifierState(): ModifierState = modifierState
}
