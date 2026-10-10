package com.ghtnql.kkkeyboard

typealias FlickDirection = com.ghtnql.kkkeyboard.sharedcore.FlickDirection
typealias FlickKey = com.ghtnql.kkkeyboard.sharedcore.FlickKey

object HangulFlickLayout {
    val rows get() = com.ghtnql.kkkeyboard.sharedcore.HangulFlickLayout.rows
    val punctuation get() = com.ghtnql.kkkeyboard.sharedcore.HangulFlickLayout.punctuation
}

object FlickDirectionResolver {
    fun resolve(dx: Float, dy: Float, threshold: Float, previous: FlickDirection = FlickDirection.CENTER): FlickDirection =
        com.ghtnql.kkkeyboard.sharedcore.FlickDirectionResolver.resolve(dx, dy, threshold, previous)
}
