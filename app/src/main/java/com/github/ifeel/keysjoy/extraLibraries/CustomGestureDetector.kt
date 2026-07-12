package com.github.ifeel.keysjoy.extraLibraries

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import com.github.ifeel.keysjoy.listeners.GestureDetectListener

class CustomGestureDetector(context: Context, internal var mListener: GestureDetectListener) :
    GestureDetector(context, mListener) {

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val consume = mListener.onTouchEvent(ev)
        return consume || super.onTouchEvent(ev)
    }


}
