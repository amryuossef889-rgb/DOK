package com.dok.editor.engine.subtitle

data class SubtitleCue(
    val index: Int,
    val startTimeUs: Long,
    val endTimeUs: Long,
    val text: String
)

object SrtSubtitleCodec {
    fun parse(input: String): List<SubtitleCue> {
        val normalized = input.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (normalized.isEmpty()) return emptyList()
        return normalized.split(Regex("\\n\\s*\\n"))
            .mapNotNull { block ->
                val lines = block.lines()
                val timingIndex = lines.indexOfFirst { it.contains("-->") }
                if (timingIndex < 0 || lines.size <= timingIndex + 1) return@mapNotNull null
                val timing = lines[timingIndex].split("-->")
                if (timing.size != 2) return@mapNotNull null
                val start = parseTimestamp(timing[0].trim()) ?: return@mapNotNull null
                val end = parseTimestamp(timing[1].trim().split(" ").first()) ?: return@mapNotNull null
                val index = lines.take(timingIndex).firstNotNullOfOrNull { it.trim().toIntOrNull() } ?: 0
                val text = lines.drop(timingIndex + 1).joinToString("\n").trim()
                if (text.isEmpty() || end <= start) null else SubtitleCue(index, start, end, text)
            }
            .sortedBy { it.startTimeUs }
            .mapIndexed { i, cue -> cue.copy(index = i + 1) }
    }

    fun write(cues: List<SubtitleCue>): String =
        cues.sortedBy { it.startTimeUs }.mapIndexed { i, cue ->
            buildString {
                append(i + 1).append("\n")
                append(formatTimestamp(cue.startTimeUs)).append(" --> ")
                append(formatTimestamp(cue.endTimeUs)).append("\n")
                append(cue.text.trim()).append("\n")
            }
        }.joinToString("\n")

    private fun parseTimestamp(value: String): Long? {
        val m = Regex("^(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})$").matchEntire(value) ?: return null
        val h = m.groupValues[1].toLong()
        val min = m.groupValues[2].toLong()
        val sec = m.groupValues[3].toLong()
        val ms = m.groupValues[4].toLong()
        if (min >= 60 || sec >= 60) return null
        return ((h * 3600 + min * 60 + sec) * 1_000 + ms) * 1_000
    }

    private fun formatTimestamp(timeUs: Long): String {
        val safe = timeUs.coerceAtLeast(0L)
        val totalMs = safe / 1_000
        val ms = totalMs % 1_000
        val totalSec = totalMs / 1_000
        val sec = totalSec % 60
        val totalMin = totalSec / 60
        val min = totalMin % 60
        val h = totalMin / 60
        return "%02d:%02d:%02d,%03d".format(h, min, sec, ms)
    }
}
