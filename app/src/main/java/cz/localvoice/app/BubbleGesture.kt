package cz.localvoice.app

class BubbleGesture(private val dragSlop: Int = 6) {
    enum class Action { NONE, TAP, HOLD_START, HOLD_END, DRAG, DRAG_END }

    private var active = false
    private var dragged = false
    private var held = false

    fun down() {
        active = true
        dragged = false
        held = false
    }

    fun move(deltaX: Int, deltaY: Int): Action {
        if (!active || held) return Action.NONE
        if (!dragged && maxOf(kotlin.math.abs(deltaX), kotlin.math.abs(deltaY)) <= dragSlop) return Action.NONE
        dragged = true
        return Action.DRAG
    }

    fun hold(): Action {
        if (!active || dragged || held) return Action.NONE
        held = true
        return Action.HOLD_START
    }

    fun up(): Action {
        if (!active) return Action.NONE
        active = false
        return when {
            held -> Action.HOLD_END
            dragged -> Action.DRAG_END
            else -> Action.TAP
        }
    }
}
