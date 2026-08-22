package cz.localvoice.app

data class BubblePreferences(
    val sizePercent: Int = DEFAULT_SIZE,
    val opacityPercent: Int = DEFAULT_OPACITY,
) {
    companion object {
        val sizes = listOf(70, 85, 100, 115)
        val opacities = listOf(20, 40, 60, 80, 100)

        fun from(sizePercent: Int, opacityPercent: Int): BubblePreferences = BubblePreferences(
            sizePercent = sizePercent.takeIf { it in sizes } ?: DEFAULT_SIZE,
            opacityPercent = opacityPercent.takeIf { it in opacities } ?: DEFAULT_OPACITY,
        )

        private const val DEFAULT_SIZE = 85
        private const val DEFAULT_OPACITY = 80
    }
}
