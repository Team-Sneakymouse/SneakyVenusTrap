package com.sneakyvenustrap.service

/** Never echo arbitrary exception payloads (paths, credentials or multiline API responses). */
object FailureDiagnostic {
    fun describe(error: Exception): String {
        val reason = when (error.message) {
            "Refusing unowned world sneakyvenustrap_stomach; choose another world name manually before retrying." ->
                "Refusing unowned world sneakyvenustrap_stomach; choose another world name manually before retrying."
            "Could not create stomach world" -> "Could not create stomach world"
            else -> "details withheld"
        }
        val type = error.javaClass.simpleName.filter { it.isLetterOrDigit() || it == '_' }.take(64)
        return "$type: $reason".take(200)
    }
}
