package com.github.ifeel.keysjoy

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.bluetooth.BluetoothProfile
import android.view.HapticFeedbackConstants
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.os.VibrationEffect
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.PopupMenu
import android.widget.Toast
import com.github.ifeel.keysjoy.listeners.ViewListener
import com.github.ifeel.keysjoy.senders.KeyboardSender
import com.github.ifeel.keysjoy.senders.RelativeMouseSender
import com.github.ifeel.keysjoy.ui.DividerView
import com.github.ifeel.keysjoy.ui.KeyLayouts
import com.github.ifeel.keysjoy.ui.KeyboardPanelHelper
import com.github.ifeel.keysjoy.reports.KeyboardReport

@OptIn(ExperimentalUnsignedTypes::class)
class SelectDeviceActivity : Activity(), KeyEvent.Callback {

    private val TAG = "SelectDeviceActivity"

    // Current layout mode
    private var isFullKeyboardMode = false

    // Track whether activity was in background (for BT lifecycle re-init)
    private var wasInBackground = false

    private var autoLandscapeEnabled: Boolean = true // default ON

    // Haptic feedback toggle (default ON)
    private var hapticEnabled: Boolean = true

    // Screen always-on toggle (default ON)
    private var screenOn = true

    // Auto-pair toggle
    private var autoPairEnabled = true

    // Bluetooth-connected resources (null until a device connects)
    private var rMouseSender: RelativeMouseSender? = null
    private var rKeyboardSender: KeyboardSender? = null
    private var keyboardHelper: KeyboardPanelHelper? = null
    private var viewListener: ViewListener? = null

    // Custom toolbar button references (replace MenuItem)
    private var btnBtStatus: ImageView? = null
    private var btnScreenOn: TextView? = null
    private var btnAutoPair: TextView? = null
    private var btnOrientation: TextView? = null
    private var btnSpeed: TextView? = null
    private var customToolbar: View? = null
    private var btnDeviceName: TextView? = null

    // Password EditText to force English-only keyboard
    private var keyboardInputView: android.widget.EditText? = null
    private var isClearingText = false

    // Height presets: 100% → 90% → 80% → 70% (cycles)
    private val heightPresets = intArrayOf(100, 90, 80, 70)
    private var currentHeightIndex = 0  // starts at 100%

    // Reference to the height toggle button in the left keyboard panel
    private var heightButton: Button? = null

    // Speed level: 1-5, persisted in SharedPreferences, default 3
    private var currentSpeedLevel: SpeedLevel = SpeedLevel.DEFAULT

    // UI reference for the trackpad area
    private var trackpad: TextView? = null

    // Simple mode UI references
    private var simpleTrackpad: TextView? = null

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Restore haptic preference from SharedPreferences (default ON)
        hapticEnabled = getPreferences(Context.MODE_PRIVATE).getBoolean("haptic_enabled", true)
        // Restore auto-pair preference from SharedPreferences (default ON)
        autoPairEnabled = getPreferences(Context.MODE_PRIVATE).getBoolean("auto_pair", true)
        BluetoothController.autoPairFlag = autoPairEnabled  // Sync to Controller
        if (autoPairEnabled) {
            BluetoothController.startAutoReconnect()
        }
        // Restore screen-on preference from SharedPreferences (default ON)
        screenOn = getPreferences(Context.MODE_PRIVATE).getBoolean("screen_on", true)
        if (screenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        // Restore height preset index from SharedPreferences
        currentHeightIndex = getPreferences(Context.MODE_PRIVATE).getInt("height_index", 0)
        // Restore speed level from SharedPreferences (default 3)
        val savedSpeed = getPreferences(Context.MODE_PRIVATE).getInt("speed_level", 4)
        currentSpeedLevel = SpeedLevel.forLevel(savedSpeed)
        try {
            // Detect initial orientation for landscape devices
            if (autoLandscapeEnabled && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                loadFullKeyboardMode()
            } else {
                loadSimpleMode()
            }
            Log.i(TAG, "onCreate: mode loaded")
        } catch (e: Throwable) {
            showError("onCreate crash", e)
        }
    }

    // -----------------------------------------------------------------------
    // Reconnect HID after app switch (back from background)
    override fun onResume() {
        super.onResume()
        try {
            // Force full re-init when returning from background
            if (wasInBackground) {
                wasInBackground = false
                Log.i(TAG, "onResume: returning from background, forcing full BT re-init")
                BluetoothController.btHid = null
                BluetoothController.hostDevice = null
                BluetoothController.init(this)
            } else if (BluetoothController.btHid == null) {
                Log.i(TAG, "onResume: btHid is null, re-initializing")
                BluetoothController.init(this)
            }
            // Back from background: re-init if no active connection
            if (BluetoothController.hostDevice == null && BluetoothController.btHid != null) {
                Log.i(TAG, "onResume: no active connection, forcing re-init")
                BluetoothController.btHid = null
                BluetoothController.hostDevice = null
                BluetoothController.init(this)
            }
            // Re-register status listener
            BluetoothController.statusListener = { msg ->
                runOnUiThread {
                    if (rMouseSender == null) {
                        simpleTrackpad?.text = msg
                        trackpad?.text = msg
                    }
                    updateBtIcon()
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "onResume failed", e)
        }
    }

    override fun onStop() {
        super.onStop()
        wasInBackground = true
    }

    // -----------------------------------------------------------------------
    // Handle orientation changes manually (Activity is not recreated)
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!autoLandscapeEnabled) return
        try {
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (!isFullKeyboardMode) loadFullKeyboardMode()
            } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
                if (isFullKeyboardMode) loadSimpleMode()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "orientation switch failed", e)
            loadSimpleMode()
        }
    }

    // -----------------------------------------------------------------------
    // Options menu (original Kontroller style)
    // -----------------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Custom toolbar replaces the menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        else -> super.onOptionsItemSelected(item)
    }

    // -----------------------------------------------------------------------
    // Back press: return from full keyboard mode to simple mode
    // -----------------------------------------------------------------------

    override fun onBackPressed() {
        if (isFullKeyboardMode) {
            loadSimpleMode()
        } else {
            super.onBackPressed()
        }
    }

    // ---- KeyEvent.Callback for forwarding keyboard input to target device ----

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0) {
            rKeyboardSender?.sendKeyboard(keyCode, event, 0) // Normal mode
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Don't call sendKeyboard on key up — already handled by key down in Normal mode
        return true
    }

    override fun onKeyMultiple(keyCode: Int, count: Int, event: KeyEvent): Boolean {
        return false
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        return false
    }

    // -----------------------------------------------------------------------
    // Simple mode (default) — full-screen trackpad + original menu
    // -----------------------------------------------------------------------

    private fun loadSimpleMode() {
        isFullKeyboardMode = false

        setContentView(R.layout.activity_select_device_simple)

        // Setup custom toolbar (replaces ActionBar menu)
        setupToolbar()
        customToolbar?.visibility = View.VISIBLE

        // Restore normal UI (status bar visible)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE

        // Hidden password EditText to force English-only keyboard
        val passwordInput = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setBackgroundColor(0x00000000)
            setTextColor(0x00000000)
            isCursorVisible = false
            layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
        }
        keyboardInputView = passwordInput
        // Prevent Enter from dismissing the keyboard — forward to target instead
        passwordInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_UNSPECIFIED ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                rKeyboardSender?.let { sender ->
                    val enterEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
                    sender.sendKeyboard(KeyEvent.KEYCODE_ENTER, enterEvent, 0)
                }
                return@setOnEditorActionListener true  // consume event, keep keyboard open
            }
            false
        }

        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0 && s != null) {
                    // Character added — convert char to proper KeyEvent via KeyCharacterMap
                    val lastChar = s[s.length - 1]
                    rKeyboardSender?.let { sender ->
                        val events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                            .getEvents(charArrayOf(lastChar))
                        if (events != null) {
                            for (event in events) {
                                // Only process ACTION_DOWN: KeyboardSender.customSender(0)
                                // already handles press+release for each call.
                                // ACTION_UP would cause duplicate keystrokes.
                                if (event.action == KeyEvent.ACTION_DOWN) {
                                    sender.sendKeyboard(event.keyCode, event, 0)
                                }
                            }
                        }
                    }
                    isClearingText = true
                    passwordInput.setText("")
                } else if (before > 0 && count == 0 && !isClearingText) {
                    // Character deleted — send backspace
                    rKeyboardSender?.let { sender ->
                        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                        sender.sendKeyboard(KeyEvent.KEYCODE_DEL, event, 0)
                    }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {
                isClearingText = false
            }
        })
        (findViewById<android.widget.LinearLayout>(R.id.simple_root))?.addView(passwordInput)

        simpleTrackpad = findViewById(R.id.simple_trackpad_touch)

        // Set middle button text to "Wheel" (same as landscape mode)
        findViewById<Button>(R.id.btn_mouse_middle).text = "Wheel"

        // Show mouse buttons bar (hidden by default in XML)
        findViewById<android.widget.LinearLayout>(R.id.mouse_buttons_bar)?.visibility = View.VISIBLE

        // --- Mouse three-button bar (mirrors setupConnectedUI for landscape mode) ---
        findViewById<Button>(R.id.btn_mouse_left).setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (hapticEnabled) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    viewListener?.setButtonHeld(true)
                    rMouseSender?.sendLeftClickOn()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    viewListener?.setButtonHeld(false)
                    rMouseSender?.sendLeftClickOff()
                    true
                }
                else -> false
            }
        }

        // Wheel button: hold to enter wheel/scroll mode on trackpad
        findViewById<Button>(R.id.btn_mouse_middle).setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    viewListener?.setWheelMode(true)
                    if (hapticEnabled) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    viewListener?.setWheelMode(false)
                    true
                }
                else -> false
            }
        }

        findViewById<Button>(R.id.btn_mouse_right).setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (hapticEnabled) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    rMouseSender?.sendRightClickOn()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    rMouseSender?.sendRightClickOff()
                    true
                }
                else -> false
            }
        }

        // Connection status callback
        BluetoothController.statusListener = { msg ->
            runOnUiThread {
                if (!isFullKeyboardMode && rMouseSender == null) {
                    simpleTrackpad?.text = msg
                }
                updateBtIcon()
            }
        }

        // If already connected, init trackpad in simple mode too
        if (BluetoothController.btHid != null && BluetoothController.hostDevice != null) {
            setupSimpleModeTouch(BluetoothController.btHid!!, BluetoothController.hostDevice!!)
        }

        // Restore screen-on state
        if (screenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // -----------------------------------------------------------------------
    // Custom toolbar setup (replaces ActionBar menu)
    // -----------------------------------------------------------------------

    private fun setupToolbar() {
        customToolbar = findViewById(R.id.custom_toolbar)

        btnBtStatus = findViewById(R.id.btn_bt_status)
        btnScreenOn = findViewById(R.id.btn_screen_on)
        btnAutoPair = findViewById(R.id.btn_auto_pair)
        btnOrientation = findViewById(R.id.btn_orientation)
        btnDeviceName = findViewById(R.id.btn_device_name)

        // Set initial emoji states
        btnScreenOn?.text = if (screenOn) "🌝" else "🌚"
        btnAutoPair?.text = if (autoPairEnabled) "🔗" else "⛓️‍💥"

        // --- BT status button (connect/disconnect) ---
        btnBtStatus?.setOnClickListener {
            if (BluetoothController.hostDevice != null) {
                // Connected → disconnect
                BluetoothController.btHid?.disconnect(BluetoothController.hostDevice)
                BluetoothController.hostDevice = null
                updateBtIcon()
                Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
            } else {
                // Disconnected → try connect
                BluetoothController.mpluggedDevice?.let { device ->
                    if (BluetoothController.btHid?.getConnectionState(device) == BluetoothProfile.STATE_DISCONNECTED) {
                        BluetoothController.btHid?.connect(device)
                        Toast.makeText(this, "Connecting...", Toast.LENGTH_SHORT).show()
                    }
                } ?: Toast.makeText(this, "No paired device", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Speed level button ---
        btnSpeed = findViewById(R.id.btn_speed)
        btnSpeed?.text = currentSpeedLevel.emoji
        btnSpeed?.setOnClickListener {
            val nextLevel = (currentSpeedLevel.level % 5) + 1
            currentSpeedLevel = SpeedLevel.forLevel(nextLevel)
            btnSpeed?.text = currentSpeedLevel.emoji
            // Persist to SharedPreferences
            getPreferences(Context.MODE_PRIVATE).edit().putInt("speed_level", nextLevel).apply()
            // Update ViewListener if active
            viewListener?.let {
                it.mouseSpeed = currentSpeedLevel.mouse
                it.scrollSpeed = currentSpeedLevel.scroll
            }
            Toast.makeText(this, "Speed: ${currentSpeedLevel.emoji}", Toast.LENGTH_SHORT).show()
        }

        // --- Input method button ---
        findViewById<TextView>(R.id.btn_input_method)?.setOnClickListener {
            keyboardInputView?.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(keyboardInputView, 0)
        }

        // --- Orientation button ---
        btnOrientation?.setOnClickListener {
            if (isFullKeyboardMode) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                loadSimpleMode()
                Toast.makeText(this, "⌨️ Portrait", Toast.LENGTH_SHORT).show()
            } else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                loadFullKeyboardMode()
                Toast.makeText(this, "⌨️ Landscape", Toast.LENGTH_SHORT).show()
            }
        }

        // --- Screen always-on button ---
        btnScreenOn?.setOnClickListener {
            screenOn = !screenOn
            if (screenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            getPreferences(Context.MODE_PRIVATE).edit().putBoolean("screen_on", screenOn).apply()
            btnScreenOn?.text = if (screenOn) "🌝" else "🌚"
            Toast.makeText(this, if (screenOn) "Screen Always On" else "Screen Auto Off", Toast.LENGTH_SHORT).show()
        }

        // --- Auto-pair button ---
        btnAutoPair?.setOnClickListener {
            autoPairEnabled = !autoPairEnabled
            BluetoothController.autoPairFlag = autoPairEnabled
            getPreferences(Context.MODE_PRIVATE).edit().putBoolean("auto_pair", autoPairEnabled).apply()
            btnAutoPair?.text = if (autoPairEnabled) "🔗" else "⛓️‍💥"
            if (autoPairEnabled) {
                BluetoothController.startAutoReconnect()
            } else {
                BluetoothController.stopAutoReconnect()
            }
            if (autoPairEnabled && BluetoothController.btHid?.getConnectionState(BluetoothController.mpluggedDevice) == 0
                && BluetoothController.mpluggedDevice != null) {
                BluetoothController.btHid?.connect(BluetoothController.mpluggedDevice)
            }
            Toast.makeText(this, if (autoPairEnabled) "Auto-pair ON" else "Auto-pair OFF", Toast.LENGTH_SHORT).show()
        }

        // --- Device name button (tap to switch paired devices) ---
        btnDeviceName?.setOnClickListener {
            showDeviceSwitcher()
        }

        // Set initial BT icon based on connection state
        updateBtIcon()
    }

    private fun updateBtIcon() {
        btnBtStatus?.setImageResource(
            if (BluetoothController.hostDevice != null)
                R.drawable.ic_action_app_connected
            else
                R.drawable.ic_action_app_not_connected
        )
        // Update device name display
        val host = BluetoothController.hostDevice
        btnDeviceName?.text = host?.name ?: "No Device ▼"
    }

    private fun setupSimpleModeTouch(hidDevice: BluetoothHidDevice, host: BluetoothDevice) {
        val sender = RelativeMouseSender(hidDevice, host)
        rMouseSender = sender
        rKeyboardSender = KeyboardSender(hidDevice, host)

        val vListener = ViewListener(hidDevice, host, sender).also {
            it.mouseSpeed = currentSpeedLevel.mouse
            it.scrollSpeed = currentSpeedLevel.scroll
        }
        viewListener = vListener

        simpleTrackpad?.text = ""
        simpleTrackpad?.isHapticFeedbackEnabled = true
        simpleTrackpad?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    (v as TextView).setTextColor(0xFF888888.toInt())
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    (v as TextView).setTextColor(0xFF666666.toInt())
                }
            }
            vListener.onTouch(v, event)
            true
        }
    }

    // -----------------------------------------------------------------------
    // Full keyboard mode — three-panel landscape layout
    // -----------------------------------------------------------------------

    private fun loadFullKeyboardMode() {
        isFullKeyboardMode = true
        try {
            setContentView(R.layout.activity_select_device_landscape)

        // Full immersive mode: hide status bar + action bar
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        customToolbar?.visibility = View.GONE

        trackpad = findViewById(R.id.landscape_trackpad)
        trackpad?.textSize = 16f

        setupDividers()

        // Restore saved divider ratios from SharedPreferences
        val prefs = getPreferences(Context.MODE_PRIVATE)
        val savedLeftRatio = prefs.getFloat("divider_left_ratio", 0.3f)
        val savedRightRatio = prefs.getFloat("divider_right_ratio", 0.3f)
        val totalWidth = 100f
        val leftWeight = savedLeftRatio * totalWidth
        val rightWeight = savedRightRatio * totalWidth
        val midWeight = totalWidth - leftWeight - rightWeight
        val contentLayout = findViewById<android.widget.LinearLayout>(R.id.landscape_content)
        (contentLayout.getChildAt(0).layoutParams as? android.widget.LinearLayout.LayoutParams)?.weight = leftWeight
        (contentLayout.getChildAt(2).layoutParams as? android.widget.LinearLayout.LayoutParams)?.weight = midWeight
        (contentLayout.getChildAt(4).layoutParams as? android.widget.LinearLayout.LayoutParams)?.weight = rightWeight
        contentLayout.requestLayout()

        // Wheel button (hold to enter wheel/scroll mode)
        val wheelBtn = findViewById<Button>(R.id.btn_mouse_middle)
        wheelBtn.text = "Wheel"

        // Apply initial height preset (deferred to after layout)
        val initPercent = heightPresets[currentHeightIndex]
        contentLayout.post {
            Log.d(TAG, "loadFullKeyboardMode: deferred applyHeightPreset($initPercent%)" +
                " displayH=${resources.displayMetrics.heightPixels}" +
                " target=${resources.displayMetrics.heightPixels * initPercent / 100}")
            applyHeightPreset(initPercent)
            contentLayout.requestLayout()
        }

        // Status callback to update trackpad text
        BluetoothController.statusListener = { msg ->
            runOnUiThread {
                if (rMouseSender == null) {
                    trackpad?.text = msg
                }
                updateBtIcon()
            }
        }

        // Always show keyboard panels (even without BT connection)
        val hidDevice = BluetoothController.btHid
        val host = BluetoothController.hostDevice
        keyboardHelper = KeyboardPanelHelper(this, hidDevice, host, hapticEnabled)
        keyboardHelper!!.onPortraitRequested = {
            runOnUiThread {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                loadSimpleMode()
            }
        }

        val leftGrid = findViewById<GridLayout>(R.id.left_keyboard_grid)
        val rightGrid = findViewById<GridLayout>(R.id.right_keyboard_grid)
        keyboardHelper!!.buildKeyboard(leftGrid, KeyLayouts.LEFT_PANEL)
        keyboardHelper!!.buildKeyboard(rightGrid, KeyLayouts.RIGHT_PANEL)
        configureHapticButton(leftGrid)
        configureHeightButton(leftGrid)

        if (hidDevice != null && host != null) {
            setupConnectedUI(hidDevice, host)
        }
        } catch (e: Throwable) {
            Log.e(TAG, "loadFullKeyboardMode failed", e)
            showError("Keyboard mode init failed", e)
            loadSimpleMode()
        }
    }

    // -----------------------------------------------------------------------
    // Additional lifecycle
    // -----------------------------------------------------------------------

    override fun onStart() {
        super.onStart()
        try {
            if (!checkBluetoothPermissions()) {
                Log.e(TAG, "onStart: missing BT permissions")
                showError("Missing permissions", RuntimeException("Missing Bluetooth permissions"))
                return
            }
            if (BluetoothAdapter.getDefaultAdapter()?.isEnabled != true) {
                showError("BT not enabled", RuntimeException("Please enable Bluetooth"))
                return
            }

            Log.i(TAG, "onStart: calling BluetoothController.init")
            BluetoothController.init(this)

            // Update status text
            if (isFullKeyboardMode) {
                trackpad?.text = "Starting BT service..."
            } else {
                simpleTrackpad?.text = "Starting BT service..."
            }

            // Register sender callback — fires immediately if already connected,
            // or later when a device connects (on binder thread → runOnUiThread)
            BluetoothController.getSender { hidDevice, host ->
                Log.i(TAG, "getSender callback: device connected")
                runOnUiThread {
                    Toast.makeText(this, "Connected: ${host.name}", Toast.LENGTH_SHORT).show()
                    rKeyboardSender = KeyboardSender(hidDevice, host)
                    if (isFullKeyboardMode) {
                        setupConnectedUI(hidDevice, host)
                    } else {
                        setupSimpleModeTouch(hidDevice, host)
                    }
                }
            }

            // Register disconnector callback
            BluetoothController.getDisconnector {
                Log.i(TAG, "getDisconnector callback: device disconnected")
                runOnUiThread {
                    Toast.makeText(this, "Bluetooth disconnected", Toast.LENGTH_SHORT).show()
                    rMouseSender = null
                    rKeyboardSender = null
                    keyboardHelper = null
                    viewListener = null
                    if (isFullKeyboardMode) {
                        onDeviceDisconnected()
                    } else {
                        simpleTrackpad?.text = "Disconnected, waiting..."
                        simpleTrackpad?.setOnTouchListener(null)
                    }
                }
            }
        } catch (e: Throwable) {
            showError("onStart crash", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        BluetoothController.stopAutoReconnect()
        BluetoothController.statusListener = null
    }

    // -----------------------------------------------------------------------
    // Connected-UI setup (full keyboard mode only)
    // -----------------------------------------------------------------------

    /**
     * Wire up all components that depend on an active Bluetooth HID connection:
     * mouse sender, keyboard panels, trackpad gestures, mouse buttons, nav buttons.
     */
    private fun setupConnectedUI(hidDevice: BluetoothHidDevice, host: BluetoothDevice) {
        try {
            // Clear the trackpad hint text and click listener once connected
            trackpad?.text = ""
            trackpad?.setOnClickListener(null)

            // --- RelativeMouseSender (used by trackpad + mouse buttons) ---
            val sender = RelativeMouseSender(hidDevice, host)
            rMouseSender = sender

            // --- Keyboard panels ---
            val kbHelper = KeyboardPanelHelper(this, hidDevice, host, hapticEnabled)
            kbHelper.onPortraitRequested = {
                runOnUiThread {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    loadSimpleMode()
                }
            }
            kbHelper.onScrollRequested = { vDelta, hDelta ->
                rMouseSender?.sendScroll(vDelta, hDelta)
            }
            keyboardHelper = kbHelper

            val leftGrid = findViewById<GridLayout>(R.id.left_keyboard_grid)
            val rightGrid = findViewById<GridLayout>(R.id.right_keyboard_grid)
            kbHelper.buildKeyboard(leftGrid, KeyLayouts.LEFT_PANEL)
            kbHelper.buildKeyboard(rightGrid, KeyLayouts.RIGHT_PANEL)
            configureHapticButton(leftGrid)
            configureHeightButton(leftGrid)

            // --- Trackpad: gesture detection + relative mouse movement ---
            val vListener = ViewListener(hidDevice, host, sender).also {
                it.mouseSpeed = currentSpeedLevel.mouse
                it.scrollSpeed = currentSpeedLevel.scroll
            }
            viewListener = vListener

            trackpad?.isHapticFeedbackEnabled = true
            trackpad?.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        (v as TextView).setTextColor(0xFF888888.toInt())
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        (v as TextView).setTextColor(0xFF666666.toInt())
                    }
                }
                vListener.onTouch(v, event)
                true
            }

            // --- Mouse three-button bar ---
            findViewById<Button>(R.id.btn_mouse_left).setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (hapticEnabled) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewListener?.setButtonHeld(true)
                        sender.sendLeftClickOn()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        viewListener?.setButtonHeld(false)
                        sender.sendLeftClickOff()
                        true
                    }
                    else -> false
                }
            }

            // Wheel button: hold to enter wheel/scroll mode on trackpad
            findViewById<Button>(R.id.btn_mouse_middle).setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        viewListener?.setWheelMode(true)
                        if (hapticEnabled) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        viewListener?.setWheelMode(false)
                        true
                    }
                    else -> false
                }
            }

            findViewById<Button>(R.id.btn_mouse_right).setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (hapticEnabled) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        sender.sendRightClickOn()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        sender.sendRightClickOff()
                        true
                    }
                    else -> false
                }
            }

            // --- Navigation buttons ---
            // All nav buttons use KeyboardSender.sendKeyPress() for consistent 3-byte HID report format:
            // byte 0 = modifier (0x08 = Left GUI), byte 1 = reserved, byte 2 = HID keycode

            // Back: Meta+Esc (HID keycode 0x29)
            findViewById<Button>(R.id.btn_nav_back).setOnClickListener {
                if (hapticEnabled) it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                rKeyboardSender?.sendKeyPress(modifier = 0x08, keycode = 0x29)
                    ?: Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            }

            // Home: Meta+Enter (HID keycode 0x28)
            findViewById<Button>(R.id.btn_nav_home).setOnClickListener {
                if (hapticEnabled) it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                rKeyboardSender?.sendKeyPress(modifier = 0x08, keycode = 0x28)
                    ?: Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            }

            // Task: Meta+Tab (HID keycode 0x2B)
            findViewById<Button>(R.id.btn_nav_overview).setOnClickListener {
                if (hapticEnabled) it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                rKeyboardSender?.sendKeyPress(modifier = 0x08, keycode = 0x2B)
                    ?: Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            }

            Log.i(TAG, "setupConnectedUI: all components initialized")
        } catch (e: Throwable) {
            showError("UI init failed after connect", e)
        }
    }

    // -----------------------------------------------------------------------
    // Disconnection handling (full keyboard mode only)
    // -----------------------------------------------------------------------

    /**
     * Called when the Bluetooth device disconnects — release resources and
     * reset the trackpad to its hint state.
     */
    private fun onDeviceDisconnected() {
        try {
            rMouseSender = null
            keyboardHelper = null
            viewListener = null

            trackpad?.setOnTouchListener(null)
            trackpad?.text = "Disconnected, waiting..."
            trackpad?.setOnClickListener {
                try {
                    val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                        putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                    }
                    startActivity(discoverableIntent)
                } catch (e: Throwable) {
                    showError("Discoverability request failed", e)
                }
            }

            Toast.makeText(this, "Device disconnected", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "onDeviceDisconnected: UI reset")
        } catch (e: Throwable) {
            Log.e(TAG, "onDeviceDisconnected error", e)
        }
    }

    // -----------------------------------------------------------------------
    // Divider drag-to-resize (full keyboard mode only)
    // -----------------------------------------------------------------------

    /**
     * Configure DividerView child indices to match the landscape layout:
     *
     *   0: left_panel        1: divider_left        2: trackpad_container
     *   3: divider_right     4: right_panel
     */
    private fun setupDividers() {
        val dividerLeft = findViewById<DividerView>(R.id.divider_left)
        dividerLeft.leftChildIndex = 0   // left_panel
        dividerLeft.rightChildIndex = 2  // trackpad_container

        val dividerRight = findViewById<DividerView>(R.id.divider_right)
        dividerRight.leftChildIndex = 2  // trackpad_container
        dividerRight.rightChildIndex = 4 // right_panel

        dividerLeft.onDragComplete = { leftRatio, _ ->
            Log.d(TAG, "Divider left drag: leftPanel=%.2f".format(leftRatio))
            // Persist left divider ratio
            getPreferences(Context.MODE_PRIVATE).edit().putFloat("divider_left_ratio", leftRatio).apply()
        }
        dividerRight.onDragComplete = { _, rightRatio ->
            Log.d(TAG, "Divider right drag: rightPanel=%.2f".format(rightRatio))
            // Persist right divider ratio
            getPreferences(Context.MODE_PRIVATE).edit().putFloat("divider_right_ratio", rightRatio).apply()
        }


    }

    // -----------------------------------------------------------------------
    // Height preset (full keyboard mode only)
    // -----------------------------------------------------------------------

    private fun applyHeightPreset(percent: Int) {
        val content = findViewById<android.widget.LinearLayout>(R.id.landscape_content)
        val displayHeight = resources.displayMetrics.heightPixels
        val targetHeight = (displayHeight * percent / 100)
        val lp = content.layoutParams as? android.widget.FrameLayout.LayoutParams ?: run {
            Log.e(TAG, "applyHeightPreset: unexpected LayoutParams type: ${content.layoutParams?.javaClass}")
            return
        }
        lp.height = targetHeight
        content.layoutParams = lp
        content.requestLayout()
        Log.d(TAG, "applyHeightPreset($percent%): displayHeight=$displayHeight, targetHeight=$targetHeight")
    }

    /**
     * Find the r6c6 haptic toggle button in the left keyboard panel and override its
     * click listener to sync haptic state across [SelectDeviceActivity.hapticEnabled],
     * [KeyboardPanelHelper.hapticEnabled], and SharedPreferences.
     *
     * This ensures the haptic button is the single source of truth for haptic control.
     */
    private fun configureHapticButton(container: android.view.ViewGroup) {
        val helper = keyboardHelper ?: return
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is Button && (child.text == "👋" || child.text == "✋")) {
                child.setOnClickListener {
                    // Perform haptic feedback based on current state (before toggle)
                    if (hapticEnabled) {
                        child.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    // Toggle
                    hapticEnabled = !hapticEnabled
                    helper.hapticEnabled = hapticEnabled
                    // Update button emoji
                    child.text = if (hapticEnabled) "👋" else "✋"
                    // Persist to SharedPreferences
                    getPreferences(Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("haptic_enabled", hapticEnabled)
                        .apply()
                }
                break
            }
        }
    }

    /**
     * Find the r6c4 height toggle button in the left keyboard panel and configure
     * its click listener to cycle through height presets (100% → 90% → 80% → 70%).
     */
    private fun configureHeightButton(container: ViewGroup) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is Button && (child.text as String).endsWith("%")) {
                heightButton = child
                val percent = heightPresets[currentHeightIndex]
                child.text = "$percent%"
                child.setOnClickListener {
                    if (hapticEnabled) it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    currentHeightIndex = (currentHeightIndex + 1) % heightPresets.size
                    val newPercent = heightPresets[currentHeightIndex]
                    applyHeightPreset(newPercent)
                    (it as Button).text = "$newPercent%"
                    // Persist height preset index
                    getPreferences(Context.MODE_PRIVATE).edit().putInt("height_index", currentHeightIndex).apply()
                    Toast.makeText(this, "Height: $newPercent%", Toast.LENGTH_SHORT).show()
                }
                break
            }
        }
    }

    // -----------------------------------------------------------------------
    // Permissions & error display (preserved from original)
    // -----------------------------------------------------------------------

    private fun showDeviceSwitcher() {
        val anchor = btnDeviceName ?: return
        val popup = PopupMenu(this, anchor, android.view.Gravity.START)

        val devices = BluetoothController.pairedDevices.toList()
        val currentHost = BluetoothController.hostDevice

        // Connected device header (disabled)
        if (currentHost != null) {
            popup.menu.add(Menu.NONE, -1, 0, "Connected: ${currentHost.name}")?.let { item ->
                item.isEnabled = false
            }
            popup.menu.add(Menu.NONE, -1, 1, "──────────────")?.let { item ->
                item.isEnabled = false
            }
        } else {
            popup.menu.add(Menu.NONE, -1, 0, "No device connected")?.let { item ->
                item.isEnabled = false
            }
            popup.menu.add(Menu.NONE, -1, 1, "──────────────")?.let { item ->
                item.isEnabled = false
            }
        }

        // Make Discoverable menu item
        popup.menu.add(Menu.NONE, -2, 2, "📡 Make Discoverable")

        // Paired devices list
        var itemId = 0
        for (device in devices) {
            val label = if (device.address == currentHost?.address) "▶ ${device.name}" else "  ${device.name}"
            popup.menu.add(Menu.NONE, itemId, itemId + 3, label)
            itemId++
        }

        if (devices.isEmpty()) {
            popup.menu.add(Menu.NONE, -1, 3, "No paired devices")?.let { item ->
                item.isEnabled = false
            }
        }

        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == -2) {
                // Make Discoverable
                try {
                    BluetoothAdapter.getDefaultAdapter()?.javaClass?.getMethod("setScanMode",
                        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)?.invoke(
                        BluetoothAdapter.getDefaultAdapter(),
                        BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, 300)
                } catch (_: Exception) {}
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                startActivity(intent)
                return@setOnMenuItemClickListener true
            }
            val idx = item.itemId
            if (idx in 0 until devices.size) {
                val targetDevice = devices[idx]
                if (targetDevice.address != currentHost?.address) {
                    BluetoothController.switchTo(targetDevice)
                    Toast.makeText(this, "Switching to ${targetDevice.name}...", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
        popup.show()
    }

    private fun checkBluetoothPermissions(): Boolean {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) return false
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) return false
        }
        return true
    }

    private fun showError(title: String, e: Throwable) {
        Log.e(TAG, title, e)
        setContentView(TextView(this).apply {
            text = "$title:\n${e.javaClass.simpleName}\n${e.message}"
            textSize = 14f
            setPadding(40, 100, 40, 40)
        })
    }
}
