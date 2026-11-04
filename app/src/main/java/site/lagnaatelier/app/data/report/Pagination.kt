package site.lagnaatelier.app.data.report

/*
 * Where each block of the report lands on the page, as a pure function of
 * heights, so it can be tested without a Canvas.
 *
 * A block is a run of lines (a paragraph measured by StaticLayout) or a single
 * unbreakable box (a rule, a card, a table row), described by its line edges:
 * the y of each line's top from the block's top, plus its bottom, so a block
 * of n lines has n + 1 edges starting at 0. A paragraph may break between
 * lines, never leaving fewer than [FlowBlock.minLines] lines on either side
 * (no orphans or widows); a heading can ask to stay with the start of the
 * block after it, and a row can ask to stay with the one before it, so a
 * short table moves as one rather than leaving a lone row on a page.
 *
 * A section (from one new-page block to the next) whose last page would
 * hold only a sliver is laid out again with its spacing tightened, and the
 * tighter version is kept if it saves that page: a chapter's last few rows
 * should not get a page to themselves.
 */

data class FlowBlock(
    /** 0, then the bottom of each line, so `edges.last()` is the block's height. */
    val edges: List<Float>,
    /** Space above the block; dropped at the top of a page. */
    val spaceBefore: Float = 0f,
    /** False for a box that must not split. */
    val breakable: Boolean = true,
    /** True for a heading: never the last thing on a page. */
    val keepWithNext: Boolean = false,
    /** Start a new page before this block. */
    val newPage: Boolean = false,
    /** The fewest lines a split may leave on either side. */
    val minLines: Int = 2,
    /** True for the second and later rows of a table: never at the top of a page without the row before it. */
    val keepWithPrevious: Boolean = false,
) {
    init {
        require(edges.size >= 2 && edges.first() == 0f) { "A block needs edges starting at 0 and at least one line." }
    }

    val lineCount: Int get() = edges.size - 1
    val height: Float get() = edges.last()

    companion object {
        /** An unbreakable box of [height]. */
        fun box(
            height: Float,
            spaceBefore: Float = 0f,
            keepWithNext: Boolean = false,
            newPage: Boolean = false,
            keepWithPrevious: Boolean = false,
        ) = FlowBlock(
            listOf(0f, height),
            spaceBefore,
            breakable = false,
            keepWithNext = keepWithNext,
            newPage = newPage,
            keepWithPrevious = keepWithPrevious,
        )
    }
}

/** Lines [fromLine, toLine) of block [block], drawn on [page] with their top at [top] in the content area. */
data class Slice(val block: Int, val page: Int, val fromLine: Int, val toLine: Int, val top: Float)

object Paginator {
    /** A last page filled less than this is a sliver worth a tighter layout to save. */
    const val SLIVER = 0.35f

    /** How much of each block's space above is kept when a section is tightened. */
    const val TIGHT_SPACING = 0.4f

    /* A run of rows kept together is one box, if it is no taller than this share of a page. */
    private const val GROUP_LIMIT = 0.6f

    /**
     * Lays [blocks] out on pages whose content area is [pageHeight] tall, in
     * order. Returns one slice per piece of a block on a page; a block taller
     * than a page and unbreakable gets a page to itself and overflows it.
     */
    fun paginate(blocks: List<FlowBlock>, pageHeight: Float): List<Slice> {
        require(pageHeight > 0f)
        val slices = mutableListOf<Slice>()
        var firstPage = 0
        for (section in sections(blocks)) {
            val part = blocks.subList(section.first, section.last + 1)
            var laid = grouped(part, pageHeight)
            val pages = pageCount(laid)
            if (pages > 1 && lastPageFill(part, laid, pageHeight) < SLIVER * pageHeight) {
                val tight = grouped(part.map { it.copy(spaceBefore = it.spaceBefore * TIGHT_SPACING) }, pageHeight)
                if (pageCount(tight) < pages) laid = tight
            }
            laid.forEach { slices += it.copy(block = it.block + section.first, page = it.page + firstPage) }
            firstPage += pageCount(laid)
        }
        return slices
    }

    /* Index ranges of the sections: each starts at a new-page block (or the first block). */
    private fun sections(blocks: List<FlowBlock>): List<IntRange> {
        if (blocks.isEmpty()) return emptyList()
        val starts = blocks.indices.filter { it == 0 || blocks[it].newPage }
        return starts.mapIndexed { i, start -> start until (starts.getOrNull(i + 1) ?: blocks.size) }
    }

    private fun lastPageFill(blocks: List<FlowBlock>, slices: List<Slice>, pageHeight: Float): Float {
        val last = pageCount(slices) - 1
        return slices.filter { it.page == last }.maxOfOrNull { slice ->
            val block = blocks[slice.block]
            slice.top + block.edges[slice.toLine] - block.edges[slice.fromLine]
        } ?: pageHeight
    }

    /*
     * Rows that keep with the one before them are laid out as one box (when
     * the run is short enough to move as one), then handed back as a slice
     * per row at its place inside the box.
     */
    private fun grouped(blocks: List<FlowBlock>, pageHeight: Float): List<Slice> {
        val groups = mutableListOf<IntRange>()
        var start = 0
        for (index in 1..blocks.size) {
            if (index == blocks.size || !blocks[index].keepWithPrevious) {
                groups += start until index
                start = index
            }
        }
        /* A run too tall to move whole stays as separate rows. */
        val units = groups.flatMap { range ->
            val height = range.sumOf { (if (it == range.first) 0f else blocks[it].spaceBefore).toDouble() + blocks[it].height }
            if (range.count() > 1 && height <= GROUP_LIMIT * pageHeight && range.all { !canSplit(blocks[it]) }) listOf(range)
            else range.map { it..it }
        }
        val unitBlocks = units.map { range ->
            if (range.count() == 1) blocks[range.first]
            else {
                val first = blocks[range.first]
                val height = range.sumOf { (if (it == range.first) 0f else blocks[it].spaceBefore).toDouble() + blocks[it].height }.toFloat()
                FlowBlock.box(height, first.spaceBefore, keepWithNext = blocks[range.last].keepWithNext, newPage = first.newPage)
            }
        }
        return layOut(unitBlocks, pageHeight).flatMap { slice ->
            val range = units[slice.block]
            if (range.count() == 1) listOf(slice.copy(block = range.first))
            else {
                var y = slice.top
                range.map { index ->
                    if (index != range.first) y += blocks[index].spaceBefore
                    Slice(index, slice.page, 0, blocks[index].lineCount, y).also { y += blocks[index].height }
                }
            }
        }
    }

    private fun layOut(blocks: List<FlowBlock>, pageHeight: Float): List<Slice> {
        val slices = mutableListOf<Slice>()
        var page = 0
        var y = 0f
        var pageUsed = false

        fun newPage() {
            page++
            y = 0f
            pageUsed = false
        }

        blocks.forEachIndexed { index, block ->
            if (block.newPage && pageUsed) newPage()
            if (pageUsed) y += block.spaceBefore

            /* A heading moves on with the first lines of what follows it. */
            if (block.keepWithNext && pageUsed) {
                val next = blocks.getOrNull(index + 1)
                val needed = block.height + (next?.let { it.spaceBefore + leadHeight(it) } ?: 0f)
                if (y + needed > pageHeight) newPage()
            }

            if (y + block.height <= pageHeight || (!pageUsed && !canSplit(block))) {
                slices += Slice(index, page, 0, block.lineCount, y)
                y += block.height
                pageUsed = true
                return@forEachIndexed
            }

            if (!canSplit(block)) {
                newPage()
                slices += Slice(index, page, 0, block.lineCount, 0f)
                y = block.height
                pageUsed = true
                return@forEachIndexed
            }

            var from = 0
            while (from < block.lineCount) {
                val top = block.edges[from]
                val remaining = block.lineCount - from
                val fitsAll = y + (block.height - top) <= pageHeight
                if (fitsAll) {
                    slices += Slice(index, page, from, block.lineCount, y)
                    y += block.height - top
                    pageUsed = true
                    break
                }
                var fit = 0
                while (fit < remaining && y + (block.edges[from + fit + 1] - top) <= pageHeight) fit++
                /* Widows: leave at least minLines for the next page. */
                if (remaining - fit < block.minLines) fit = remaining - block.minLines
                /* Orphans: fewer than minLines here, and the paragraph starts over on the next page. */
                if (fit < block.minLines) {
                    if (!pageUsed) {
                        /* Already at the top of a page: take what fits, at least one line. */
                        fit = maxOf(1, minOf(remaining, countFitting(block, from, pageHeight)))
                    } else {
                        newPage()
                        continue
                    }
                }
                slices += Slice(index, page, from, from + fit, y)
                from += fit
                newPage()
            }
        }
        return slices
    }

    /** How many pages [slices] use. */
    fun pageCount(slices: List<Slice>): Int = (slices.maxOfOrNull { it.page } ?: -1) + 1

    private fun canSplit(block: FlowBlock) = block.breakable && block.lineCount >= 2 * block.minLines

    /* What must fit after a heading: the next block whole, or its first minLines lines. */
    private fun leadHeight(block: FlowBlock): Float =
        if (!canSplit(block)) block.height else block.edges[block.minLines]

    private fun countFitting(block: FlowBlock, from: Int, pageHeight: Float): Int {
        val top = block.edges[from]
        var fit = 0
        while (from + fit < block.lineCount && block.edges[from + fit + 1] - top <= pageHeight) fit++
        return fit
    }
}
