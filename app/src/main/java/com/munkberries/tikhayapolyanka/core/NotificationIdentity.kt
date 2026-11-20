package com.munkberries.tikhayapolyanka.core

object NotificationIdentity {
    fun timer(tag: String?,channel: String): Boolean =
        tag=="timer" || tag==null && channel.startsWith("timer_")
}
