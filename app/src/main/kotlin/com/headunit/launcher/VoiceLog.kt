package com.headunit.launcher

import android.content.Context

/**
 * The last few spoken directions, for the problem report: on which screen, which call it was for the turn (the early heads-up,
 * the "In 300 metres" one or the one at the turn), the distance it was worked out from and how far the GPS really put you from
 * the turn. So a direction said too early, too late or not at all on Android Auto can be traced. Kept small: the last 12 lines.
 */
object VoiceLog {
    private const val KEY = "voice_log"

    fun note(ctx: Context, what: String) {
        try {
            val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.UK).format(java.util.Date())
            val sp = ctx.getSharedPreferences("diag", Context.MODE_PRIVATE)
            val old = sp.getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }
            sp.edit().putString(KEY, (old + "$t · $what").takeLast(12).joinToString("\n")).apply()
        } catch (e: Throwable) { }
    }

    fun text(ctx: Context): String =
        ctx.getSharedPreferences("diag", Context.MODE_PRIVATE).getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }.joinToString("\n").let { if (it.isBlank()) "" else it + "\n" }
}
