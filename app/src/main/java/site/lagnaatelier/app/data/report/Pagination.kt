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
 * block after it.
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
) {
    init {
        require(edges.size >= 2 && edges.first() == 0f) { "A block needs edges starting at 0 and at least one line." }
    }

    val lineCount: Int get() = edges.size - 1
    val height: Float get() = edges.last()

    companion object {
        /** An unbreakable box of [height]. */
        fun box(height: Float, spaceBefore: Float = 0f, keepWithNext: Boolean = false, newPage: Boolean = false) =
            FlowBlock(listOf(0f, height), spaceBefore, breakable = false, keepWithNext = keepWithNext, newPage = newPage)
    }
}

/** Lines [fromLine, toLine) of block [block], drawn on [page] with their top at [top] in the content area. */
data class Slice(val block: Int, val page: Int, val fromLine: Int, val toLine: Int, val top: Float)

object Paginator {
    /**
     * Lays [blocks] out on pages whose content area is [pageHeight] tall, in
     * order. Returns one slice per piece of a block on a page; a block taller
     * than a page and unbreakable gets a page to itself and overflows it.
     */
    fun paginate(blocks: List<FlowBlock>, pageHeight: Float): List<Slice> {
        require(pageHeight > 0f)
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
