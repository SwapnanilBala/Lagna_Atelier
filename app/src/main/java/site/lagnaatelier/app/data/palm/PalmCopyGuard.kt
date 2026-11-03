package site.lagnaatelier.app.data.palm

/*
 * The reading's prose is the model's, and the site's content line holds for
 * it on this screen too: nothing about death, disease or disability. The
 * prompt already says the life line does not predict lifespan and not to
 * diagnose illness; this is what holds if a sentence slips through anyway.
 *
 * Whole sentences go, not words, so what is left still reads as written. Only
 * what is shown is filtered: a follow-up question replays answers exactly as
 * they were given.
 */
object PalmCopyGuard {
    private val SENSITIVE = Regex(
        """\b(death|deaths|die|dies|died|dying|dead|deadly|demise|mortal|mortality|lifespan|life span|""" +
            """longevity|short life|fatal|terminal|disease|diseases|illness|illnesses|ill health|sickness|sick|""" +
            """cancer|tumou?r|diabet\w*|heart attack|stroke|surgery|disab\w*|handicap\w*|blind|blindness|""" +
            """deaf|deafness|paraly\w*|chronic)\b""",
        RegexOption.IGNORE_CASE,
    )

    /* After a full stop, question or exclamation mark, or a danda, and the space that follows. */
    private val SENTENCE_END = Regex("""(?<=[.!?।])\s+""")

    fun isSensitive(sentence: String): Boolean = SENSITIVE.containsMatchIn(sentence)

    /** [text] without the sentences that touch death, disease or disability. */
    fun clean(text: String): String =
        text.split(SENTENCE_END)
            .filter { it.isNotBlank() && !isSensitive(it) }
            .joinToString(" ")
            .trim()
}
