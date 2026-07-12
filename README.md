# KeysJoy

<p align="center">
  <strong>Turn your Android phone into a Bluetooth keyboard, mouse, and trackpad.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Gradle-8.7.3-02303A?logo=gradle" alt="Gradle" />
  <img src="https://img.shields.io/badge/Min%20SDK-28%20(Android%209)-3DDC84?logo=android" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Target%20SDK-35-3DDC84?logo=android" alt="Target SDK" />
  <img src="https://img.shields.io/badge/License-Apache%202.0-D22128" alt="License" />
</p>

---

KeysJoy is an Android application that uses the **Bluetooth HID Device profile** to turn your phone into a wireless keyboard, trackpad, and mouse. Control laptops, desktops, smart TVs, iPads — any device that supports standard Bluetooth HID peripherals. No companion software required on the target device.

---

## Highlights

- **Bluetooth HID Device profile** — no companion software or drivers needed on the target device; works with any OS that supports standard Bluetooth peripherals.
- **Three-panel landscape layout** — split keyboard flanking a central trackpad, with two draggable vertical dividers for resizing each panel.
- **Custom gesture detection** — tap, long-press drag, double-tap-to-drag, and two-finger scroll (vertical + horizontal), all with sub-pixel precision and a cursor speed ramp.
- **Dedicated mouse buttons** — press-and-hold Left, Wheel, and Right buttons for precise text selection and drag operations, no tap-and-hold ambiguity.
- **Multi-device pairing and auto-reconnect** — pair with multiple hosts, switch between them seamlessly from the device switcher, and auto-reconnect when a host comes back in range.
- **Haptic feedback with on-device toggle** — tactile vibration on every key press, mouse click, and drag activation, with a one-tap toggle on the keyboard row.
- **Portrait + Landscape dual-mode** — one-tap switch between a full-screen trackpad portrait mode and a three-panel split keyboard + trackpad landscape mode.

---

## Quick Start

1. Open the KeysJoy app on your Android phone.
2. Turn on Bluetooth and make the phone discoverable.
3. On the target device (PC, Mac, TV, iPad), search for Bluetooth devices and connect to **"Pixel HID1"**.

---

## Based on Kontroller

KeysJoy is built on top of **[Kontroller](https://github.com/rostopira/kontroller)** by [Dima Rostopira](https://github.com/rostopira), an open-source Android library that pioneered using Android's `BluetoothHidDevice` API to expose a phone as a Bluetooth HID peripheral.

Kontroller is licensed under the **Apache License 2.0**, the same license that KeysJoy uses.

### What Kontroller Provides

Kontroller established the foundational architecture that KeysJoy inherits and extends:

- **Bluetooth HID communication layer** — Registration of the HID device profile via `BluetoothHidDevice`, service discovery protocol (SDP) record setup, and the full connection lifecycle (pairing, connecting, disconnecting, auto-reconnect). The `BluetoothController` is directly derived from Kontroller's core controller.
- **HID report descriptors** — The keyboard + mouse combo descriptor (`DescriptorCollection.MOUSE_KEYBOARD_COMBO`), defining how keys, mouse movements, scroll, and buttons are encoded into raw HID byte reports that any host OS understands without drivers.
- **Report encoding classes** — The `MouseReport`, `KeyboardReport`, and related inline classes that map logical input state (button presses, key codes, movement deltas) to the exact byte arrays the HID descriptor expects.
- **Sender abstraction** — The `Sender`, `RelativeMouseSender`, and `KeyboardSender` classes that encapsulate `BluetoothHidDevice.sendReport()` with structured report objects.
- **Hidden API workarounds** — Reflection-based access to hidden Android Bluetooth APIs (`setScanMode`, `cancelBondProcess`, `removeBond`) needed for reliable discoverability and pairing cleanup.

### What KeysJoy Adds

On top of Kontroller's solid Bluetooth HID foundation, KeysJoy builds a complete, polished user-facing application:

- **Custom keyboard UI** — A responsive 6×6 split-grid keyboard layout in landscape mode, with sticky modifiers, Caps Lock toggle, and Shift-aware key labels. Portrait mode hides the keyboard for full-screen trackpad use.
- **Gesture detection** — Single-finger tap, double-tap, long-press drag, and two-finger scroll (vertical + horizontal), all translated into precise HID mouse and scroll reports with sub-pixel accumulation and a cursor speed ramp.
- **Toolbar controls** — A unified toolbar for Bluetooth connection management, device switching, orientation toggle, 5-level mouse/scroll speed presets, system keyboard input forwarding, screen always-on, and auto-reconnect.
- **Panel resizing** — Two draggable vertical dividers let you resize keyboard and trackpad panels to your preference, with height presets for the keyboard row.
- **Navigation buttons** — Android-style Back, Home, and Task buttons that send Meta-key shortcuts to the host.
- **Haptic feedback** — Vibration on key presses, mouse buttons, and drag activation, with a toggle to turn it off.
- **Multi-device switching** — A device switcher popup for seamless connection switching between multiple paired hosts, plus a "make discoverable" option.
- **External keyboard forwarding** — Physical keyboards connected to the phone can forward their input to the target host.

---

## Screenshots

### Portrait Mode

<p align="center">
  <img src="screenshots/portrait.jpg" alt="KeysJoy Portrait — Trackpad with toolbar and mouse buttons" width="360" />
</p>

*Full-screen trackpad with toolbar controls, and Left / Wheel / Right mouse buttons.*

### Landscape Mode — Full Keyboard + Trackpad

<p align="center">
  <img src="screenshots/landscape.jpg" alt="KeysJoy Landscape — Three-panel layout with split keyboard, trackpad, and side panel" width="720" />
</p>

*Three-panel split: left keyboard, central trackpad with mouse buttons, right keyboard. The complete keyboard + trackpad layout.*

---

## Portrait Mode

In portrait orientation, the keyboard panels are hidden and the entire screen becomes a touch surface for cursor control:

- **Full-screen trackpad** — The entire phone screen acts as a trackpad. Single-finger movement controls the cursor; multi-finger gestures are fully supported.
- **Mouse buttons** — Left, Wheel, and Right buttons sit below the trackpad area.
- **Toolbar** — All controls remain accessible at the top of the screen.
- **Optimized for one-handed use** — Maximum space for precise cursor control, no keyboard splitting.

Tap the ⌨️ **Orientation** button in the toolbar to switch between portrait trackpad mode and landscape keyboard mode.

---

## Toolbar Controls

The toolbar sits at the top of the screen in both portrait and landscape modes. Each icon reflects the actual visual design used in the app.

| Icon | Control | Description |
|------|---------|-------------|
| 🟢 / 🔴 | **Bluetooth Status** | Shows connection state. Tap to connect to the last paired device or disconnect from the current one. |
| Device Name ▼ | **Device Name** | Displays the connected host's name. Tap to open the **device switcher** popup — switch between paired hosts or make the phone discoverable. A `▶` marker indicates the active connection. |
| ⌨️ | **Orientation** | Toggles between portrait trackpad mode and landscape keyboard + trackpad mode (three-panel split). |
| 1️⃣ – 5️⃣ | **Speed Level** | Cycles through 5 mouse/scroll speed presets. State is persisted across app restarts. |

| Level | Emoji | Mouse Speed | Scroll Speed |
|-------|-------|-------------|-------------|
| 1 | 1️⃣ | 0.4× | 0.4× |
| 2 | 2️⃣ | 0.6× | 0.6× |
| 3 | 3️⃣ | 0.8× | 0.8× |
| 4 | 4️⃣ | 1.0× (default) | 1.0× |
| 5 | 5️⃣ | 1.2× | 1.2× |

| Icon | Control | Description |
|------|---------|-------------|
| 🔠 | **Input Method** | Opens the system keyboard for text input. Any text typed is forwarded to the target host as HID key reports. A hidden `EditText` field captures keystrokes and converts them to HID keycodes via `KeyCharacterMap`. The Enter key is forwarded to the host rather than dismissing the keyboard. |
| 🌝 / 🌚 | **Screen Always-On** | Toggles whether the phone screen stays on while KeysJoy is active. 🌝 = screen stays on. 🌚 = normal screen timeout. State is persisted. |
| 🔗 / ⛓️‍💥 | **Auto-Pair** | Toggles automatic reconnection. When enabled (🔗), the app attempts to reconnect every 5 seconds after disconnection. When disabled (⛓️‍💥), reconnection must be triggered manually. |

---

## Landscape Mode — Keyboard + Trackpad

Landscape mode presents the full three-panel layout: split keyboard on both sides flanking a central trackpad, designed for two-thumb typing on phones. Two draggable vertical dividers let you resize each panel.

### Keyboard Panels

**Left Panel (6×6 grid)**

| Row | Keys |
|-----|------|
| 1 | `` ` `` `1` `2` `3` `4` `5` |
| 2 | `Tab` `Q` `W` `E` `R` `T` |
| 3 | `Caps` `A` `S` `D` `F` `G` |
| 4 | `Shift` `Z` `X` `C` `V` `B` |
| 5 | `Ctrl` `⌘` `Alt` `Esc` `BkSp` `Del` |
| 6 | `Space` (×3 wide) `70%` `🖱️` `👋` |

**Right Panel (6×6 grid)**

| Row | Keys |
|-----|------|
| 1 | `6` `7` `8` `9` `0` `-` |
| 2 | `Y` `U` `I` `O` `P` `BkSp` |
| 3 | `H` `J` `K` `L` `;` `'` |
| 4 | `N` `M` `[` `]` `/` `=` |
| 5 | `Home` `End` `↑` `\` `⌥` `Enter` |
| 6 | `,` `←` `↓` `→` `Space` `.` |

Key labels automatically reflect the current Shift state: lowercase when Shift is off, uppercase when Shift or Caps Lock is active.

### App-Specific Keyboard Features

- **Sticky modifiers (Shift, Ctrl, Alt, ⌘, ⌥):** Tap once to activate the modifier for the next regular key only. The button highlights while active. Tap again to cancel. Auto-releases after any non-modifier key is pressed.

- **Caps Lock:** A toggle-lock — each tap flips the state and sends the actual HID CapsLock keycode to the host. The button shows a pressed state while active. Key labels update globally.

- **👋 / ✋ (Haptic toggle):** Toggles vibration feedback on key presses. 👋 = haptics on, ✋ = off. State is persisted across app restarts.

- **70% (Height preset):** Cycles the keyboard panel height through four presets: **100%** → **90%** → **80%** → **70%** → back to 100%. Useful for revealing content behind the keyboard. State is persisted.

- **🖱️ (Orientation switch):** Switches from landscape keyboard mode to portrait trackpad mode. Same function as the toolbar ⌨️ button.

---

### Trackpad — Gesture Detection

The central trackpad area translates finger movements into HID mouse reports with sub-pixel precision accumulation.

#### Single-Finger Gestures

| Gesture | Action | Details |
|---------|--------|---------|
| **Tap** | Left click | Confirmed after the double-tap timeout expires with no second tap. |
| **Move** | Cursor movement | Translates finger delta into relative mouse movement with configurable speed. Includes a speed ramp: cursor starts at 15% speed and accelerates to 100% over 400 ms — precise for small movements, fast for large sweeps. |
| **Long press (500 ms)** | Click-and-drag | Enters drag mode: left mouse button is held down. A 30 ms vibration signals activation. Release to drop. |
| **Double-tap** | Click-and-drag | Double-tap and hold on the second tap enters drag mode. A 30 ms vibration signals activation. Release to drop. |

#### Multi-Finger Gestures

| Gesture | Action | Details |
|---------|--------|---------|
| **Two-finger tap** | Right click | Detected via pointer count (≥2) or pointer lift with spread > 30 px. |
| **Two-finger swipe** | Scroll (vertical + horizontal) | Both fingers move together. Translates into HID scroll wheel reports with sub-pixel accumulation. A `scrolled` flag prevents ghost right-clicks on lift. |

#### Multi-Shot Wake

To prevent the host from entering deep sleep and missing the first mouse movement, 4 zero-byte mouse reports are sent at 5 ms intervals immediately on touch-down.

---

### Gestures Summary

| Gesture | Effect |
|---------|--------|
| Single tap | Left click |
| Single-finger move | Cursor movement (with speed ramp) |
| Long press (500 ms) | Click-and-drag |
| Double-tap-and-hold | Click-and-drag |
| Two-finger tap | Right click |
| Two-finger swipe | Scroll (vertical + horizontal) |


---

### Mouse Buttons

Dedicated mouse buttons exist to better mimic the physical feel of a real mouse. They are especially critical for **text selection in editing scenarios**: press and hold the **Left** button while dragging a finger on the trackpad, and you get the same precise, character-by-character control as holding a physical mouse button while moving — perfect for selecting text, adjusting cursor ranges, or resizing UI elements. Without dedicated buttons, text selection via tap-and-hold gestures is imprecise — the delay before drag mode activates and the absence of tactile feedback make it easy to overshoot or lose your selection.

Three dedicated buttons sit below the trackpad area in both landscape and portrait modes:

| Button | Behavior |
|--------|----------|
| **Left** | **Press-and-hold.** Pressing sends `leftButton = true` in the HID mouse report. Releasing sends `leftButton = false`. Use for click-and-drag: hold Left, then move your finger on the trackpad. Haptic feedback fires on press. |
| **Wheel** | **Mode toggle (hold).** While held, the trackpad enters wheel/scroll mode — single-finger movement is translated into HID scroll (vertical + horizontal) instead of mouse movement. Release to return to normal cursor mode. Haptic feedback fires on press. |
| **Right** | **Press-and-hold.** Pressing sends `rightButton = true`. Releasing sends `rightButton = false`. Use for right-click drag operations. Haptic feedback fires on press. |

#### Scroll Wheel Behavior

KeysJoy supports scrolling through two complementary mechanisms:

1. **Two-finger swipe on trackpad** — Move two fingers together on the trackpad area to scroll. Both vertical and horizontal scrolling are supported. Scroll deltas are accumulated with sub-pixel precision for fluid, jitter-free scrolling.

2. **Wheel button** — Press and hold the **Wheel** button below the trackpad, then move a single finger. While held, the trackpad switches from cursor mode to scroll mode: finger movement is converted to HID scroll wheel reports instead of mouse movement. Release the Wheel button to return to normal cursor control. This is ideal for one-handed scrolling.

Scroll speed is controlled by the **Speed Level** setting in the toolbar and applies to both methods equally.

---

## Mouse Cursor Smoothness

KeysJoy employs multiple layers of smoothing and optimization to deliver fluid, stutter-free cursor control over the Bluetooth HID connection.

### Speed Ramp

When a finger first touches the trackpad, cursor movement starts at **15% speed** and linearly accelerates to **100%** over **400 milliseconds**. This provides precision for small, careful adjustments while still allowing fast sweeps across the screen.

- **Ramp start:** 15% of configured mouse speed
- **Ramp duration:** 400 ms
- **Ramp curve:** Linear interpolation from 15% → 100%
- **Skipped during drag:** The ramp is disabled for long-press drag, double-tap drag, and toolbar-button drag — cursor moves at full speed immediately for these operations.
- **Applied to scroll:** Two-finger scrolling also benefits from the speed ramp, making scroll start gently before reaching full speed.

Implementation: `getRampMultiplier()` in `ViewListener.kt` is called on every movement frame. It computes `elapsed / RAMP_DURATION_MS`, interpolates linearly between `RAMP_START_FRACTION` (0.15) and `1.0`, and returns the multiplier.

### Sub-Pixel Precision Accumulation

Trackpad finger deltas are tracked as floating-point values and accumulated across frames. A HID mouse report is only sent when the accumulated delta reaches at least ±1 whole integer. This prevents 1-pixel stutter and ensures smooth diagonal movement.

- **Mouse accumulator (`accumMouseX`, `accumMouseY`):** Finger deltas × `mouseSpeed` × ramp multiplier are added to float accumulators. `Math.round()` is applied to the accumulated total. The integer portion is sent as a HID report, and the fractional remainder is carried forward to the next frame.
- **Scroll accumulator (`accumScrollX`, `accumScrollY`):** Same mechanism for two-finger scroll and wheel-mode scroll. Scroll deltas × `scrollSpeed` × ramp multiplier accumulate as floats. Integer scroll ticks are sent only when the accumulated value crosses a whole-number boundary.

Without sub-pixel accumulation, fractional deltas would be truncated to zero on every frame — producing visible stutter and dead zones at slow finger speeds.

### Multi-Shot Wake

To prevent the host from entering a deep-sleep state and dropping the first mouse report, KeysJoy sends **4 zero-byte mouse reports** at **5 ms intervals** immediately on touch-down.

- **Timing:** 4 empty reports queued via `Handler.postDelayed()` at 0 ms, 5 ms, 10 ms, and 15 ms after the finger touches down.
- **Report format:** Each report is a freshly zeroed `ScrollableTrackpadMouseReport` (7-byte HID report with all fields at zero).
- **Cancellation:** All pending wake runnables are cancelled the moment real movement begins (first `onScroll` event), preventing unnecessary radio traffic.
- **Drag safety:** Wake reports are skipped when `buttonHeld` is true, to avoid clearing the left-button state mid-drag.

This technique eliminates the perceptible "first-movement lag" that occurs when a host's Bluetooth HID stack has entered power saving between the last movement and a new touch.

### HID Range Clamping

Mouse movement deltas are clamped to the HID specification range of **±2047** before encoding into the report bytes. This prevents overflow in the 12-bit signed fields of the HID mouse descriptor and ensures the report remains valid even during rapid finger sweeps. Clamping is done in `RelativeMouseSender.sendMouseMove()`.

### Drag Threshold (Touch Slop)

A finger movement must exceed the Android system **touch slop** distance (`ViewConfiguration.scaledTouchSlop`) before a custom drag trigger is cancelled. This prevents tiny, unintended finger shifts from prematurely aborting a drag gesture, acting as a small dead zone for drag initiation.

### Gesture Discrimination Flags

Logical flags prevent conflicting HID reports between the gesture detectors (`GestureDetector` and raw `MotionEvent` handling):

| Flag | Role |
|------|------|
| `scrolled` | Set when two-finger scroll fires → suppresses ghost right-click on finger lift |

| `rightClickSent` | Set when right-click fires via `ACTION_POINTER_UP` → suppresses ghost left-click in `onSingleTapUp` |

---

### Navigation Buttons

Below the mouse buttons (landscape mode), three Android-style navigation buttons send keyboard shortcuts to the host using the **Meta (GUI/Windows) modifier** key:

| Button | Shortcut Sent | Typical Effect |
|--------|---------------|----------------|
| **Back** | `Meta + Esc` | Go back (browser back, dismiss dialog, navigate to previous screen) |
| **Home** | `Meta + Enter` | Go to home screen / desktop overview |
| **Task** | `Meta + Tab` | Open app switcher / task overview / recent apps |

Each press sends the key-down report immediately, followed by an all-keys-release report 50 ms later for a clean single-press effect. Haptic feedback fires on each tap.

---

### Panel Resizing

The landscape layout consists of three resizable panels separated by two vertical dividers.

**Draggable dividers:** Each vertical divider between the panels can be dragged left or right to resize the adjacent panels:

- **Left divider** — Drag to adjust the balance between the **left keyboard panel** and the **trackpad**. Dragging left makes the keyboard panel wider; dragging right makes the trackpad wider.
- **Right divider** — Drag to adjust the balance between the **trackpad** and the **right keyboard panel**. Dragging left makes the trackpad wider; dragging right makes the right keyboard panel wider.

Each panel is constrained to a minimum of **20%** and a maximum of **50%** of the total width, preventing any panel from becoming unusably narrow or overly wide. Divider positions are persisted across app restarts, so your preferred layout is remembered.

**Height presets:** In addition to width resizing, the entire keyboard panel height can be cycled through four presets using the `70%` button on the bottom row of the left keyboard panel: **100%** → **90%** → **80%** → **70%** of screen height. The button label updates to reflect the current percentage. The preset index is persisted.

---

## Multi-Touch Support

The trackpad supports up to multiple simultaneous touch points:

- **Two-finger gestures** — Scroll and right-click as described in the Trackpad section above.
- **Pointer tracking** — `activePointerCount` is maintained in real time to distinguish single-touch from multi-touch gestures.
- **Gesture discrimination** — `scrolled` flag prevents conflicting gestures (e.g., two-finger scroll won't trigger a ghost right-click on lift).
- **Right-click via both tap and lift** — Two-finger right click can fire either from a simultaneous tap (`onSingleTapUp` with ≥2 pointers) or from lifting the second finger after both have been down (with >30 px spread).

---

## Haptic Feedback

Vibration feedback provides tactile confirmation for user actions:

| Action | Vibration |
|--------|-----------|
| Key press (on-screen keyboard) | `HapticFeedbackConstants.KEYBOARD_TAP` |
| Mouse button press (Left, Right) | `HapticFeedbackConstants.KEYBOARD_TAP` |
| Drag mode activation (long-press / double-tap-hold) | 30 ms vibration (`VibrationEffect.DEFAULT_AMPLITUDE`) |
| Wheel button press | `HapticFeedbackConstants.KEYBOARD_TAP` |
| Navigation button tap | `HapticFeedbackConstants.KEYBOARD_TAP` |
| Height preset toggle | `HapticFeedbackConstants.KEYBOARD_TAP` |

Haptic feedback can be toggled on/off using the 👋/✋ button on the bottom row of the left keyboard panel. The setting persists across app restarts.

---

## External Keyboard Forwarding

If you connect a physical keyboard to your Android phone (via USB-OTG or Bluetooth), KeysJoy can forward its input to the target host:

- The activity implements `KeyEvent.Callback` to intercept key events.
- `KeyboardSender.sendKeyboard()` maps Android `KeyEvent` keycodes to HID usage codes via `KeyboardReport.KeyEventMap` and sends them to the target device.
- Supports full key mapping including letters, numbers, function keys (F1–F12), navigation keys, and symbols.

---

## Multi-Device Pairing & Switching

- **Pair with multiple hosts** — Each successful pairing is recorded in the paired devices list.
- **Device switcher popup** — Tap the device name in the toolbar to open a dropdown listing all paired devices. The currently connected device shows a `▶` prefix.
- **Seamless switching** — Select a different device to disconnect from the current host and connect to the new one. The switch is handled by `BluetoothController.switchTo()` which coordinates disconnect and connect callbacks.
- **Make Discoverable** — A menu option in the device switcher triggers a 5-minute discoverability window for pairing with new hosts.

---

## Auto-Reconnect

When enabled, the app automatically attempts to reconnect to the last paired device:

- **Polling interval:** Every 5 seconds, the app checks if the host is still disconnected.
- **Automatic stop:** Polling stops when the device reconnects or when auto-pair is toggled off.
- **Connection recovery:** If the target device goes out of range and returns, KeysJoy reconnects without user intervention.
- **Toggle:** Controlled via the 🔗/⛓️‍💥 button in the toolbar. State is persisted.

---

## How It Works

KeysJoy registers your phone as a **Bluetooth HID Device** (Human Interface Device) — the same profile used by wireless keyboards and mice. It presents a **keyboard + mouse combo** descriptor to the host, so your computer sees it as a standard input peripheral. No drivers, no companion apps, no special software on the target device.

The app sends raw HID reports over Bluetooth:

| Report Type | Purpose |
|---|---|
| **Keyboard Report** | Key presses with modifier state (Shift/Ctrl/Alt/Gui) |
| **Mouse Report** | Relative mouse movement, scroll wheel, left/right/middle buttons |
| **Absolute Mouse Report** | Absolute cursor positioning (absolute coordinates) |
| **Feature Report** | Device capability negotiation (resolution multiplier) |

---

## Tech Stack

| Component | Detail |
|---|---|
| **Language** | Kotlin |
| **Build System** | Gradle 8.7.3 |
| **Kotlin Version** | 2.0.21 |
| **Min SDK** | 28 (Android 9 Pie) |
| **Target SDK** | 35 (Android 15) |
| **Java Target** | JVM 17 |
| **Bluetooth HID** | Android `BluetoothHidDevice` API |
| **Multi-Dex** | androidx.multidex |
| **License** | Apache 2.0 |

### Dependencies

- `org.jetbrains.kotlin:kotlin-stdlib-jdk8`
- `androidx.multidex:multidex:2.0.1`
- `androidx.core:core-ktx:1.13.1`

The app directly uses Android's built-in `BluetoothHidDevice` API — no third-party Bluetooth HID library is required.

---

## Requirements

- **Android 9 (Pie) or later** — Bluetooth HID Device profile was introduced in API 28.
- **Bluetooth HID Device support** — Some manufacturers disable the HID Device profile on their devices. Use a [compatibility checker](https://play.google.com/store/apps/details?id=com.rkaneapplabs.bluetooth_hid.bluetoothproxy) to verify support on your phone.

---

## Setup & Build

### Prerequisites

- Android Studio (latest stable recommended)
- JDK 17+
- Android SDK 35

### Build from Source

```bash
# Clone the repository
git clone https://github.com/ifeel/keysjoy.git
cd keysjoy

# Build with Gradle
./gradlew assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

Alternatively, open the project in Android Studio and run it directly on a connected device or emulator.

---

## Usage

1. **Remove old pairings** — In your phone's Bluetooth settings, remove any previous pairings between the phone and the target host (one-time step).

2. **Open KeysJoy** — The app registers as a Bluetooth HID device ("Pixel HID1").

3. **Pair from the host** — On your computer, TV, or tablet, open Bluetooth settings and search for **"Pixel HID1"**. Initiate pairing from the host side.

4. **Accept pairing** — Accept the pairing request on your phone.

5. **Start using** — Your phone is now a wireless keyboard and mouse for the target device.

### Switching Devices

If you've paired with multiple hosts, tap on the target device name in the toolbar to switch connections. The app disconnects from the current host and connects to the selected one.

### Tips

- Use the **draggable dividers** in landscape mode to resize keyboard and trackpad panels to your preference.
- Enable **auto-reconnect** to automatically connect when a previously paired device comes back in range.
- The **haptic toggle** (👋 / ✋ button on the bottom keyboard row) controls vibration feedback on key presses.

---

## Bluetooth HID Device Compatibility

Many Android device manufacturers disable the Bluetooth HID Device profile. If you cannot find the HID service or pairing fails, your device may not support it.

- Check compatibility: [Bluetooth HID Device Profile Compatibility Checker](https://play.google.com/store/apps/details?id=com.rkaneapplabs.bluetooth_hid.bluetoothproxy)
- OnePlus users can [upvote this Idea](https://forums.oneplus.com/threads/converting-one-plus-devices-into-a-bluetooth-controller-mouse-keyboard-etc.1192272/) to request HID Device profile support.

---

## Project Structure

```
keysjoy/
├── app/
│   ├── build.gradle                          # App build configuration
│   └── src/main/
│       ├── AndroidManifest.xml               # Permissions, activities, features
│       ├── java/com/github/ifeel/keysjoy/
│       │   ├── BluetoothController.kt        # Bluetooth HID lifecycle & pairing
│       │   ├── DescriptorCollection.kt       # HID report descriptors
│       │   ├── Kontroller.kt                 # Application class (MultiDex)
│       │   ├── SelectDeviceActivity.kt       # Main UI: keyboard + trackpad
│       │   ├── SpeedLevel.kt                 # Mouse speed presets
│       │   ├── SplashScreenActivity.kt       # Launch/splash screen
│       │   ├── Unhide.kt                     # SDP record utilities
│       │   ├── extraLibraries/
│       │   │   └── CustomGestureDetector.kt  # Custom gesture detector
│       │   ├── listeners/
│       │   │   ├── CompositeListener.kt      # Merged event listener
│       │   │   ├── GestureDetectListener.kt  # Alternative tap/double-tap detection
│       │   │   └── ViewListener.kt           # Primary touch & gesture handler
│       │   ├── reports/
│       │   │   ├── AbsMouseReport.kt         # Absolute mouse report
│       │   │   ├── FeatureReport.kt          # HID feature report
│       │   │   ├── KeyboardReport.kt         # HID keyboard report
│       │   │   ├── MouseReport.kt            # Standard mouse report
│       │   │   ├── ScrollableTrackpadMouseReport.kt # Mouse + scroll report
│       │   │   ├── TestTrackpadMouseReport.kt
│       │   │   └── TrackpadMouseReport.kt    # Trackpad mouse report
│       │   ├── senders/
│       │   │   ├── KeyboardSender.kt         # External physical keyboard input
│       │   │   ├── RelativeMouseSender.kt    # Relative mouse movement & scroll
│       │   │   └── Sender.kt                 # Base HID sender
│       │   └── ui/
│       │       ├── DividerView.kt            # Draggable panel dividers
│       │       ├── KeyboardPanelHelper.kt    # Virtual keyboard builder & events
│       │       └── ModifierState.kt          # Shift/Ctrl/Alt/Gui state tracking
│       └── res/
│           ├── drawable/                     # Button backgrounds, dividers, icons
│           ├── layout/                       # Landscape & portrait activity layouts
│           └── values/                       # Colors, strings, styles, IDs
├── build.gradle                              # Root build configuration
├── settings.gradle                           # Module settings
├── gradle.properties                         # Gradle properties
├── LICENSE                                   # Apache License 2.0
└── README.md
```

---

## Contributing

Bug reports and contributions are welcome. Please open an issue or submit a pull request on GitHub.

If you have a device where Bluetooth HID Device profile behavior is unknown (especially OnePlus, Xiaomi, and other OEMs with custom Bluetooth stacks), testing reports are especially valuable.

---

## License

KeysJoy is licensed under the **Apache License 2.0**. See [LICENSE](LICENSE) for the full license text.

```
Copyright 2024 KeysJoy Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

---

<p align="center">
  <sub>Made with ❤️ by <a href="https://github.com/ifeel">ifeel</a> and <a href="https://github.com/rostopira">Dima Rostopira</a></sub>
</p>
