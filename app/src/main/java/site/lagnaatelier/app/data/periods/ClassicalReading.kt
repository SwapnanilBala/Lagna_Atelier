package site.lagnaatelier.app.data.periods

import org.json.JSONObject

/*
 * A reading cited to the classical books, as the period and life-chapter
 * routes send it (lib/knowledge/classical-reading.ts): the text in segments,
 * each carrying the numbers of the passages it rests on, and those passages
 * as the books print them.
 */

data class ClassicalSegment(val text: String, val sources: List<Int>)

data class ClassicalSource(
    val number: Int,
    /** The book's slug: "brihat-jataka-1885", "strijataka-1931". */
    val book: String,
    /** Chapter and verse, "18.19", or a chapter alone for a prose book. */
    val ref: String,
    /** True for a translator's note rather than the book's own text. */
    val isNote: Boolean,
    val text: String,
)

data class ClassicalReading(val segments: List<ClassicalSegment>, val sources: List<ClassicalSource>) {
    /** A reading with nothing to cite is not one the site would ship; the screens treat it as none. */
    val isUsable: Boolean get() = segments.any { it.text.isNotBlank() } && sources.isNotEmpty()

    /**
     * The reading as paragraphs of cited pieces (readingParagraphs on the web).
     * Paragraphs break at blank lines wherever a segment holds one, and a
     * segment's numbers stay on its last piece, after the words they support.
     */
    fun paragraphs(): List<List<ClassicalSegment>> {
        val paragraphs = mutableListOf(mutableListOf<ClassicalSegment>())
        for (segment in segments) {
            val parts = segment.text.split(PARAGRAPH_BREAK)
            parts.forEachIndexed { index, part ->
                if (index > 0) paragraphs.add(mutableListOf())
                val last = index == parts.size - 1
                paragraphs.last().add(ClassicalSegment(part, if (last) segment.sources else emptyList()))
            }
        }
        return paragraphs
            .map { pieces ->
                val trimmed = pieces.toMutableList()
                if (trimmed.isNotEmpty()) {
                    trimmed[0] = trimmed[0].copy(text = trimmed[0].text.trimStart())
                    trimmed[trimmed.size - 1] = trimmed.last().copy(text = trimmed.last().text.trimEnd())
                }
                trimmed.filter { it.text.isNotEmpty() || it.sources.isNotEmpty() }
            }
            .filter { pieces -> pieces.any { it.text.isNotBlank() } }
    }

    /** Whether any passage comes from the book on women's charts, which has its own credit. */
    val quotesStrijataka: Boolean get() = sources.any { it.book == STRIJATAKA }
    val quotesOtherBooks: Boolean get() = sources.any { it.book != STRIJATAKA }

    companion object {
        const val STRIJATAKA = "strijataka-1931"
        private val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")

        /** The `reading` object, or null when it is missing or malformed. */
        fun parse(json: JSONObject?): ClassicalReading? {
            if (json == null) return null
            val segments = json.optJSONArray("segments") ?: return null
            val sources = json.optJSONArray("sources") ?: return null
            return ClassicalReading(
                segments = segments.objects().map { segment ->
                    val numbers = segment.optJSONArray("sources")
                    ClassicalSegment(
                        text = segment.optString("text"),
                        sources = if (numbers == null) emptyList() else (0 until numbers.length()).mapNotNull {
                            (numbers.opt(it) as? Number)?.toInt()
                        },
                    )
                },
                sources = sources.objects().map {
                    ClassicalSource(
                        number = it.optInt("number"),
                        book = it.optString("book"),
                        ref = it.optString("ref"),
                        isNote = it.optString("kind") == "note",
                        text = it.optString("text"),
                    )
                },
            )
        }
    }
}
