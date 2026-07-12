package com.github.ifeel.keysjoy.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout

/**
 * A vertical divider that can be dragged horizontally to resize adjacent panels.
 *
 * Assumes it is placed in a horizontal LinearLayout between two child views that use
 * layout_weight for sizing. On drag, it adjusts the weight of the two adjacent children
 * (specified by [leftChildIndex] and [rightChildIndex]), redistributing the weight delta
 * between them.
 *
 * @property leftChildIndex  Index in parent LinearLayout of the left adjacent panel
 * @property rightChildIndex Index in parent LinearLayout of the right adjacent panel
 * @property onDragComplete  Callback: (leftPanelRatio, rightPanelRatio) when drag ends.
 *                           Caller decides which ratio(s) to persist.
 */
class DividerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val MIN_RATIO = 0.20f   // Min panel percentage (20%)
        private const val MAX_RATIO = 0.50f   // Max panel percentage (50%)
    }

    /** Index of the left adjacent panel in the parent LinearLayout (default: left panel) */
    var leftChildIndex: Int = 0

    /** Index of the right adjacent panel in the parent LinearLayout (default: right panel) */
    var rightChildIndex: Int = 4

    /**
     * Callback when drag completes: (leftAdjacentPanelRatio, rightAdjacentPanelRatio).
     * Each ratio is weight / totalWeight of the corresponding adjacent child.
     */
    var onDragComplete: ((Float, Float) -> Unit)? = null

    private var isDragging = false
    private var dragStartX = 0f
    private var initialLeftWeight = 0f
    private var initialRightWeight = 0f
    private var totalWeight = 0f
    private var parentWidth = 0

    init {
        isFocusableInTouchMode = true
        isClickable = true
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                dragStartX = event.rawX

                val parent = parent as? LinearLayout ?: return false
                parentWidth = parent.width
                if (parentWidth <= 0) return false

                // Capture initial weights of the two adjacent panels
                val leftChild = parent.getChildAt(leftChildIndex)
                initialLeftWeight = (leftChild?.layoutParams as? LinearLayout.LayoutParams)?.weight ?: 1f

                val rightChild = parent.getChildAt(rightChildIndex)
                initialRightWeight = (rightChild?.layoutParams as? LinearLayout.LayoutParams)?.weight ?: 1f

                // Calculate total weight of all children
                totalWeight = 0f
                for (i in 0 until parent.childCount) {
                    val lp = parent.getChildAt(i)?.layoutParams as? LinearLayout.LayoutParams
                    totalWeight += lp?.weight ?: 0f
                }

                // Highlight divider during drag
                setBackgroundColor(context.resources.getColor(
                    com.github.ifeel.keysjoy.R.color.divider_drag_color, null))

                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false

                val parent = parent as? LinearLayout ?: return false
                val dx = event.rawX - dragStartX
                val weightDelta = (dx / parentWidth) * totalWeight

                val minWeight = totalWeight * MIN_RATIO
                val maxWeight = totalWeight * MAX_RATIO

                // Adjust left adjacent panel
                var newLeftWeight = (initialLeftWeight + weightDelta).coerceIn(minWeight, maxWeight)
                val leftChild = parent.getChildAt(leftChildIndex)
                val leftLp = leftChild?.layoutParams as? LinearLayout.LayoutParams
                leftLp?.weight = newLeftWeight
                leftChild?.requestLayout()

                // Adjust right adjacent panel (keep the delta balanced)
                var newRightWeight = (initialRightWeight - weightDelta).coerceIn(minWeight, maxWeight)
                val rightChild = parent.getChildAt(rightChildIndex)
                val rightLp = rightChild?.layoutParams as? LinearLayout.LayoutParams
                rightLp?.weight = newRightWeight
                rightChild?.requestLayout()

                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false

                // Restore divider color
                setBackgroundResource(com.github.ifeel.keysjoy.R.drawable.divider_vertical)

                // Report both adjacent panel ratios to the callback
                if (parent is LinearLayout) {
                    val leftChild = (parent as LinearLayout).getChildAt(leftChildIndex)
                    val rightChild = (parent as LinearLayout).getChildAt(rightChildIndex)
                    val leftWeight = (leftChild?.layoutParams as? LinearLayout.LayoutParams)?.weight ?: 35f
                    val rightWeight = (rightChild?.layoutParams as? LinearLayout.LayoutParams)?.weight ?: 35f
                    val leftRatio = leftWeight / totalWeight
                    val rightRatio = rightWeight / totalWeight
                    onDragComplete?.invoke(leftRatio, rightRatio)
                }

                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }

        return super.onTouchEvent(event)
    }
}
