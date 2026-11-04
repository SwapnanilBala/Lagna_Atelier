package site.lagnaatelier.app.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.report.FlowBlock
import site.lagnaatelier.app.data.report.Paginator
import site.lagnaatelier.app.data.report.Slice

class PaginatorTest {
    /** A paragraph of [lines] lines, each [lineHeight] tall. */
    private fun paragraph(lines: Int, lineHeight: Float = 10f, spaceBefore: Float = 0f, newPage: Boolean = false) =
        FlowBlock((0..lines).map { it * lineHeight }, spaceBefore = spaceBefore, newPage = newPage)

    private fun heading(height: Float = 20f, spaceBefore: Float = 0f) =
        FlowBlock.box(height, spaceBefore = spaceBefore, keepWithNext = true)

    @Test
    fun blocksThatFitStayOnOnePageWithTheirSpacing() {
        val slices = Paginator.paginate(listOf(paragraph(3), paragraph(2, spaceBefore = 5f)), pageHeight = 100f)
        assertEquals(listOf(Slice(0, 0, 0, 3, 0f), Slice(1, 0, 0, 2, 35f)), slices)
        assertEquals(1, Paginator.pageCount(slices))
    }

    @Test
    fun aParagraphBreaksBetweenLinesAcrossPages() {
        val slices = Paginator.paginate(listOf(paragraph(4), paragraph(10)), pageHeight = 100f)
        assertEquals(
            listOf(Slice(0, 0, 0, 4, 0f), Slice(1, 0, 0, 6, 40f), Slice(1, 1, 6, 10, 0f)),
            slices,
        )
    }

    @Test
    fun aLongParagraphRunsOverSeveralPages() {
        val slices = Paginator.paginate(listOf(paragraph(25)), pageHeight = 100f)
        assertEquals(listOf(Slice(0, 0, 0, 10, 0f), Slice(0, 1, 10, 20, 0f), Slice(0, 2, 20, 25, 0f)), slices)
        assertEquals(3, Paginator.pageCount(slices))
    }

    @Test
    fun noOrphanIsLeftAtThePageFoot() {
        /* One line would fit under the first paragraph; the second starts over on the next page instead. */
        val slices = Paginator.paginate(listOf(paragraph(9), paragraph(6)), pageHeight = 100f)
        assertEquals(listOf(Slice(0, 0, 0, 9, 0f), Slice(1, 1, 0, 6, 0f)), slices)
    }

    @Test
    fun noWidowIsCarriedOntoTheNextPage() {
        /* Ten of eleven lines would fit; two go over so the last line is not alone. */
        val slices = Paginator.paginate(listOf(paragraph(11)), pageHeight = 100f)
        assertEquals(listOf(Slice(0, 0, 0, 9, 0f), Slice(0, 1, 9, 11, 0f)), slices)
    }

    @Test
    fun aHeadingMovesOnWithTheStartOfWhatFollows() {
        /* After 70 of 100, the heading (20) fits but its paragraph's first two lines (20) do not. */
        val slices = Paginator.paginate(listOf(paragraph(7), heading(), paragraph(5)), pageHeight = 100f)
        assertEquals(listOf(Slice(0, 0, 0, 7, 0f), Slice(1, 1, 0, 1, 0f), Slice(2, 1, 0, 5, 20f)), slices)
    }

    @Test
    fun aBoxNeverSplitsAndSpaceIsDroppedAtThePageTop() {
        val slices = Paginator.paginate(listOf(paragraph(8), FlowBlock.box(30f, spaceBefore = 12f)), pageHeight = 100f)
        assertEquals(listOf(Slice(0, 0, 0, 8, 0f), Slice(1, 1, 0, 1, 0f)), slices)
    }

    @Test
    fun aNewPageBlockStartsAPageUnlessThePageIsEmpty() {
        val first = Paginator.paginate(listOf(paragraph(2, newPage = true), paragraph(2, newPage = true)), pageHeight = 100f)
        assertEquals(listOf(Slice(0, 0, 0, 2, 0f), Slice(1, 1, 0, 2, 0f)), first)
    }

    @Test
    fun aBoxTallerThanAPageGetsAPageToItself() {
        val slices = Paginator.paginate(listOf(paragraph(2), FlowBlock.box(150f), paragraph(2)), pageHeight = 100f)
        assertEquals(listOf(Slice(0, 0, 0, 2, 0f), Slice(1, 1, 0, 1, 0f), Slice(2, 2, 0, 2, 0f)), slices)
    }

    @Test
    fun everyLineIsPlacedOnceAndInsideThePage() {
        val blocks = (1..40).map { index ->
            if (index % 7 == 0) heading(18f, spaceBefore = 14f) else paragraph(1 + index % 13, lineHeight = 17f, spaceBefore = 9f)
        }
        val height = 706f
        val slices = Paginator.paginate(blocks, height)
        blocks.forEachIndexed { index, block ->
            val lines = slices.filter { it.block == index }.flatMap { it.fromLine until it.toLine }
            assertEquals((0 until block.lineCount).toList(), lines)
        }
        slices.forEach { slice ->
            val block = blocks[slice.block]
            val bottom = slice.top + block.edges[slice.toLine] - block.edges[slice.fromLine]
            assertTrue("slice $slice runs past the page", bottom <= height + 1e-3f)
        }
        /* Pages come in order. */
        assertTrue(slices.zipWithNext().all { (a, b) -> b.page >= a.page })
    }

    @Test
    fun aShortTableMovesWholeRatherThanLeaveALoneRow() {
        /* 70 used: the heading and first two rows (20 + 10 + 10) would fit, the third row would not. */
        val rows = (0 until 3).map { FlowBlock.box(10f, keepWithPrevious = it > 0) }
        val slices = Paginator.paginate(listOf(paragraph(6), heading(), *rows.toTypedArray(), paragraph(2)), pageHeight = 100f)
        val pages = slices.associate { it.block to it.page }
        assertEquals(0, pages[0])
        /* The heading and all three rows go over together. */
        assertEquals(listOf(1, 1, 1, 1), (1..4).map { pages[it] })
        assertEquals(listOf(0f, 20f, 30f, 40f), (1..4).map { index -> slices.single { it.block == index }.top })
    }

    @Test
    fun aSectionsSliverOfALastPageIsSavedByTighterSpacing() {
        /* Nine one-line paragraphs, each 10 tall with 2 above: 9 x 10 + 8 x 2 = 106, six over a page of 100. */
        val section = listOf(paragraph(1, newPage = true)) + (1 until 9).map { paragraph(1, spaceBefore = 2f) }
        val slices = Paginator.paginate(section + paragraph(1, newPage = true), pageHeight = 100f)
        /* Tightened to 0.8 above each, it fits one page; the next section still starts on a new one. */
        assertEquals(2, Paginator.pageCount(slices))
        assertTrue(slices.filter { it.block < 9 }.all { it.page == 0 })
        assertEquals(1, slices.single { it.block == 9 }.page)
    }

    @Test
    fun aFullLastPageIsLeftAsItIs() {
        val slices = Paginator.paginate(listOf(paragraph(9), paragraph(6, spaceBefore = 4f)), pageHeight = 100f)
        assertEquals(Slice(1, 1, 0, 6, 0f), slices.last())
    }
}
