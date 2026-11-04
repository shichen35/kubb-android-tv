package com.kubb.tv.data

/*
 * 续播 policy: where a viewer stands in a
 * title, answered from its episode list and the stored [Watch]. Episodes follow
 * their `index`, never their place in the list.
 */

/**
 * The resume point of a title. [position] 0 means "start this episode from the beginning"
 * (the previous one was finished). [finished] marks [lastEpisode] watched to its end with
 * no next episode at the time, kept so a newly aired episode can pick up from it.
 */
data class Watch(val lastEpisode: Int, val position: Long, val duration: Long, val finished: Boolean = false)

private const val RESUME_MIN_MS = 30_000L // only resume when more than this was watched
private const val FINISHED_TAIL_MS = 15_000L // this close to the end counts as finished

/** The episode after [index] (listed or not), or null after the last one. */
fun nextEpisode(episodes: List<Episode>, index: Int): Episode? =
    episodes.filter { it.index > index }.minByOrNull { it.index }

/** Milliseconds into episode [index] to start at; 0 = from the beginning. */
fun resumePosition(watch: Watch?, index: Int): Long =
    if (watch != null && !watch.finished && watch.lastEpisode == index && watch.position > RESUME_MIN_MS) watch.position else 0

/**
 * The watch entry to store after playing episode [index] up to [position] of [duration].
 * Near the end counts as finished: the resume point moves to the start of the next
 * episode, or, with none, stays on this one marked finished.
 */
fun progressEntry(episodes: List<Episode>, index: Int, position: Long, duration: Long): Watch {
    if (duration - position > FINISHED_TAIL_MS) return Watch(index, position, duration)
    val next = nextEpisode(episodes, index)
    return if (next != null) Watch(next.index, 0, duration) else Watch(index, duration, duration, finished = true)
}

/** An episode's display title: a bare number ("01", "第06集") reads 第6集 so every screen spells it the same. */
fun episodeTitle(ep: Episode): String {
    val ordinal = "第${ep.index}集"
    return if (ep.title.isBlank() || Regex("^(第)?0*${ep.index}(集)?$").matches(ep.title)) ordinal else ep.title
}

/** The detail page's primary button: what it plays and what it says. */
data class PrimaryAction(val playIndex: Int, val resumeIndex: Int?, val label: String)

/**
 * An unfinished entry resumes while its episode is still listed. A finished one moves on to
 * an episode that aired since; with none, the button offers a rewatch rather than silently
 * resetting to "播放 第1集".
 */
fun primaryAction(episodes: List<Episode>, watch: Watch?): PrimaryAction {
    val listed = watch != null && episodes.any { it.index == watch.lastEpisode }
    val resumeIndex = when {
        watch == null -> null
        watch.finished -> nextEpisode(episodes, watch.lastEpisode)?.index
        listed -> watch.lastEpisode
        else -> null
    }
    val caughtUp = watch?.finished == true && listed && resumeIndex == null
    val playIndex = resumeIndex ?: nextEpisode(episodes, Int.MIN_VALUE)?.index ?: 1
    val label = when {
        resumeIndex != null -> "继续观看 第${resumeIndex}集"
        caughtUp -> "重新观看 第${playIndex}集"
        else -> "播放 第${playIndex}集"
    }
    return PrimaryAction(playIndex, resumeIndex, if (episodes.size <= 1) label.replace(Regex(" 第\\d+集$"), "") else label)
}
