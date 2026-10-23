package com.sneakyvenustrap.service

object Noise {
    fun score(running: Boolean, sneaking: Boolean, microphone: Boolean): Double =
        (if (running) 12.0 else if (sneaking) 0.25 else 2.0) + if (microphone) 10.0 else 0.0
}
