package com.github.ifeel.keysjoy.senders

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import com.github.ifeel.keysjoy.reports.KeyboardReport

@Suppress("MemberVisibilityCanBePrivate")
open class KeyboardSender(
    val hidDevice: BluetoothHidDevice,
    val host: BluetoothDevice
) {
    val keyboardReport = KeyboardReport()
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val TAG = "KeyboardSender"
        const val CONSUMER_REPORT_ID = 9
        private const val KEY_PRESS_RELEASE_DELAY_MS = 50L
    }


    protected open fun sendKeys() {
        if (!hidDevice.sendReport(host, KeyboardReport.ID, keyboardReport.bytes)) {
            Log.e(TAG, "Report wasn't sent")
        }
    }

    /**
     * Send a single key press with modifier, then auto-release after a short delay.
     * This is the preferred method for sending key combinations (e.g., Meta+Esc for Back).
     *
     * Sends a 3-byte HID keyboard report matching the project's report descriptor:
     *   byte 0 = modifier mask
     *   byte 1 = reserved (always 0)
     *   byte 2 = HID keycode
     *
     * After [KEY_PRESS_RELEASE_DELAY_MS], sends an all-zero report to release all keys.
     *
     * @param modifier HID modifier byte (e.g., 0x08 = Left GUI)
     * @param keycode  HID key usage ID (e.g., 0x29 = Esc, 0x28 = Enter, 0x2B = Tab)
     */
    fun sendKeyPress(modifier: Int, keycode: Int) {
        // Reset keyboard report state first
        keyboardReport.bytes.fill(0)

        // Build report: [modifier, reserved=0, keycode]
        keyboardReport.bytes[0] = modifier.toByte()
        keyboardReport.bytes[2] = keycode.toByte()

        // Send key-down report
        if (!hidDevice.sendReport(host, KeyboardReport.ID, keyboardReport.bytes)) {
            Log.e(TAG, "sendKeyPress: failed to send key-down " +
                    "(mod=0x${modifier.toString(16)}, key=0x${keycode.toString(16)})")
            return
        }
        Log.d(TAG, "sendKeyPress: key-down mod=0x${modifier.toString(16)} key=0x${keycode.toString(16)}")

        // Schedule key-up (release all keys)
        mainHandler.postDelayed({
            sendNullKeys()
            Log.d(TAG, "sendKeyPress: key-up released all")
        }, KEY_PRESS_RELEASE_DELAY_MS)
    }

    protected open fun customSender(modifier_checked_state: Int) {
        sendKeys()
        if(modifier_checked_state==0) sendNullKeys()
        else {
            keyboardReport.key1=0.toByte()
            sendKeys()
        }
    }

    protected open fun setModifiers(event:KeyEvent) {
        if(event.isShiftPressed) keyboardReport.leftShift=true
        if(event.isAltPressed) keyboardReport.leftAlt=true
        if(event.isCtrlPressed) keyboardReport.leftControl=true
        if(event.isMetaPressed) keyboardReport.leftGui=true
    }

    fun sendNullKeys() {
        keyboardReport.bytes.fill(0)
        if (!hidDevice.sendReport(host, KeyboardReport.ID, keyboardReport.bytes)) {
            Log.e(TAG, "Report wasn't sent")
        }
    }

    fun keyEventHandler(keyEventCode: Int, event : KeyEvent, modifier_checked_state: Int,keyCode:Int): Boolean{


        val byteKey = KeyboardReport.KeyEventMap[keyEventCode]

        if(byteKey!=null)
        {
            setModifiers(event)
            if(event.keyCode== KeyEvent.KEYCODE_AT || event.keyCode== KeyEvent.KEYCODE_POUND || event.keyCode== KeyEvent.KEYCODE_STAR)
            {
                keyboardReport.leftShift=true
            }
            keyboardReport.key1=byteKey.toByte()
            customSender(modifier_checked_state)

            return true
        }
        else
        {
            return false
        }



    }


    fun sendKeyboard(keyCode : Int, event : KeyEvent, modifier_checked_state :Int): Boolean {


        return keyEventHandler(event.keyCode,event,modifier_checked_state,keyCode)


//        return when (event.keyCode) {
//            KeyEvent.KEYCODE_A -> {
//                if(event.isShiftPressed) keyboardReport.leftShift=true
//                //else keyboardReport.leftShift = false
//                if(event.isAltPressed) keyboardReport.leftAlt=true
//                //else keyboardReport.leftAlt=false
//                if(event.isCtrlPressed) keyboardReport.leftControl=true
//                //else keyboardReport.leftControl=false
//                if(event.isMetaPressed) keyboardReport.leftGui=true
//                //else keyboardReport.leftGui=false
//
//                keyboardReport.key1=KeyboardReport.KEYCODE_A.toByte()
//                customSender(modifier_checked_state)
//
//                true
//            }
//
//            KeyEvent.KEYCODE_B -> {
//                setModifiers(event)
//
//                keyboardReport.key1=KeyboardReport.KEYCODE_B.toByte()
//                customSender(modifier_checked_state)
//
//                true
//            }
//
//            else -> false
//        }

    }






//fun sendTestMouseMove() {
//    mouseReport.dxLsb = 20
//    mouseReport.dyLsb = 20
//    mouseReport.dxMsb = 20
//    mouseReport.dyMsb = 20
//    sendMouse()
//}
//
//fun sendTestClick() {
//    mouseReport.leftButton = true
//    sendMouse()
//    mouseReport.leftButton = false
//    sendMouse()
////        Timer().schedule(20L) {
////
////        }
//}
//fun sendDoubleTapClick() {
//    mouseReport.leftButton = true
//    sendMouse()
//    Timer().schedule(100L) {
//        mouseReport.leftButton = false
//        sendMouse()
//        Timer().schedule(100L) {
//            mouseReport.leftButton = true
//            sendMouse()
//            Timer().schedule(100L) {
//                mouseReport.leftButton = false
//                sendMouse()
//            }
//
//
//
//
//        }
//    }
//}
//
//
//
//fun sendLeftClickOn() {
//    mouseReport.leftButton = true
//    sendMouse()
//
//
//}
//fun sendLeftClickOff() {
//    mouseReport.leftButton = false
//    sendMouse()
//
//}
//fun sendRightClick() {
//    mouseReport.rightButton = true
//    sendMouse()
//    Timer().schedule(50L) {
//        mouseReport.rightButton= false
//        sendMouse()
//    }
//}
//
//fun sendScroll(vscroll:Int,hscroll:Int){
//
//    var hscrollmutable=0
//    var vscrollmutable =0
//
//    hscrollmutable=hscroll
//    vscrollmutable= vscroll
//
////        var dhscroll= hscrollmutable-previoushscroll
////        var dvscroll= vscrollmutable-previousvscroll
////
////        dhscroll = Math.abs(dhscroll)
////        dvscroll = Math.abs(dvscroll)
////        if(dvscroll>=dhscroll)
////        {
////            hscrollmutable=0
////
////        }
////        else
////        {
////            vscrollmutable=0
////        }
//    var vs:Int =(vscrollmutable)
//    var hs:Int =(hscrollmutable)
//    Log.i("vscroll ",vscroll.toString())
//    Log.i("vs ",vs.toString())
//    Log.i("hscroll ",hscroll.toString())
//    Log.i("hs ",hs.toString())
//
//
//    mouseReport.vScroll=vs.toByte()
//    mouseReport.hScroll= hs.toByte()
//
//    sendMouse()
//
////        previousvscroll=-1*vscroll
////        previoushscroll=hscroll
//
//
//}




    // Consumer Report 9 buffer (2 bytes: usage LSB, usage MSB)
    private val consumerReportBuffer = ByteArray(2)

    /**
     * Send a Consumer Page report (Report ID 9).
     * @param data 2-byte consumer usage code (LSB first), e.g. byteArrayOf(0x24, 0x02) for AC Back.
     *             Send byteArrayOf(0, 0) to release.
     */
    fun sendConsumerReport(data: ByteArray) {
        if (!hidDevice.sendReport(host, CONSUMER_REPORT_ID, data)) {
            Log.e(TAG, "Consumer report wasn't sent")
        }
    }

}