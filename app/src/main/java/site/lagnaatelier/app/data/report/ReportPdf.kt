package site.lagnaatelier.app.data.report

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import site.lagnaatelier.app.R
import java.io.File
import java.io.FileOutputStream

/** What the cover says besides the story itself. */
data class ReportCover(
    val name: String,
    /** "Virgo", or null when the chart has none to give. */
    val ascendant: String?,
    /** "New Delhi, Delhi, India". */
    val place: String?,
    /** "3 November 2026". */
    val preparedOn: String?,
)

/*
 * The report, typeset on the phone: an A4 PDF drawn with the platform's
 * PdfDocument, Canvas and StaticLayout, in the web PDF's own paper palette
 * (app/(desktop)/insights/components/personal-story-pdf.tsx) and the app's
 * Geist faces.
 *
 * In order: the cover; "Begin here" with the introduction, the preface, the
 * at-a-glance facts and the three threads; one section per chapter, each on
 * a new page (number, eyebrow, title, support, opening, the narrative, then
 * what to carry forward, the question, and the signals it rests on); and a
 * closing section with the verification summary and the calculation record.
 *
 * Everything after the cover flows through [Paginator]: paragraphs break
 * between lines with no orphans or widows, headings stay with what follows,
 * and boxes (cards, rows) never split.
 */
class ReportPdf(private val context: Context) {

    private object Palette {
        const val PAPER = 0xFFF7F4EE.toInt()
        const val PAPER_DEEP = 0xFFEEE9DF.toInt()
        const val INK = 0xFF171915.toInt()
        const val MUTED = 0xFF68675F.toInt()
        const val FAINT = 0xFF928F86.toInt()
        const val GOLD = 0xFF9B783B.toInt()
        const val GOLD_SOFT = 0xFFD8C8A8.toInt()
        const val GREEN = 0xFF315D50.toInt()
        const val GREEN_SOFT = 0xFFDDE8E2.toInt()
        const val ROSE = 0xFF7C5048.toInt()
        const val ROSE_SOFT = 0xFFEEE0DC.toInt()
        const val LINE = 0xFFD8D2C6.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }

    /* A4 in points, and the type area. */
    private val pageWidth = 595
    private val pageHeight = 842
    private val marginX = 62f
    private val marginTop = 74f
    private val marginBottom = 68f
    private val contentWidth = pageWidth - 2 * marginX
    private val contentHeight = pageHeight - marginTop - marginBottom

    /*
     * Static instances of Geist (res/font/report_geist_*.ttf, cut from the
     * variable font at each weight the report uses). Android's PDF writer
     * embeds a variable font as Type 3 glyph drawings, whose text a reader
     * cannot reliably copy or search; a static TrueType face goes in as real
     * text with its Unicode map.
     */
    private val faces: Map<Int, Typeface> = mapOf(
        300 to R.font.report_geist_300,
        400 to R.font.report_geist_400,
        500 to R.font.report_geist_500,
        600 to R.font.report_geist_600,
    ).mapValues { (_, id) -> ResourcesCompat.getFont(context, id) ?: Typeface.SANS_SERIF }
    private val mono: Typeface = ResourcesCompat.getFont(context, R.font.report_geist_mono_400) ?: Typeface.MONOSPACE

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    private data class PaintKey(val size: Float, val color: Int, val weight: Int, val monospace: Boolean)

    /* Shared: a layout never changes the paint it was given. */
    private val paints = HashMap<PaintKey, TextPaint>()

    /*
     * No letter-spacing and no ligatures: both are drawn as glyphs placed one
     * by one or merged, and a PDF reader then copies "L A G N A" or "veri ed"
     * where the page says LAGNA and verified. Small caps labels are set plain.
     */
    private fun paint(size: Float, color: Int, weight: Int = 400, monospace: Boolean = false): TextPaint =
        paints.getOrPut(PaintKey(size, color, weight, monospace)) {
            TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = if (monospace) mono else faces[weight] ?: faces.getValue(400)
                textSize = size
                this.color = color
                fontFeatureSettings = "'liga' 0, 'clig' 0, 'dlig' 0"
            }
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** Something the flow places: its heights, and how to draw lines [from, to) with their top at y. */
    private class Element(val block: FlowBlock, val draw: (canvas: Canvas, from: Int, to: Int, top: Float) -> Unit)

    /** Writes the report to [file] and returns how many pages it has. */
    fun write(story: PersonalStory, cover: ReportCover, file: File): Int {
        val elements = buildList {
            addAll(beginHere(story))
            story.chapters.forEachIndexed { index, chapter -> addAll(chapter(story, chapter, index)) }
            addAll(closing(story))
        }
        val slices = Paginator.paginate(elements.map { it.block }, contentHeight)
        val flowPages = Paginator.pageCount(slices)
        val totalPages = flowPages + 1

        val document = PdfDocument()
        try {
            document.page(1) { canvas -> drawCover(canvas, story, cover) }
            val byPage = slices.groupBy { it.page }
            for (flowPage in 0 until flowPages) {
                document.page(flowPage + 2) { canvas ->
                    drawRunningElements(canvas, cover.name, flowPage + 2, totalPages)
                    canvas.save()
                    canvas.translate(marginX, marginTop)
                    byPage[flowPage].orEmpty().forEach { slice ->
                        elements[slice.block].draw(canvas, slice.fromLine, slice.toLine, slice.top)
                    }
                    canvas.restore()
                }
            }
            FileOutputStream(file).use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return totalPages
    }

    private inline fun PdfDocument.page(number: Int, draw: (Canvas) -> Unit) {
        val page = startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, number).create())
        page.canvas.drawColor(Palette.PAPER)
        draw(page.canvas)
        finishPage(page)
    }

    // ── The cover ────────────────────────────────────────────────────────

    private fun drawCover(canvas: Canvas, story: PersonalStory, cover: ReportCover) {
        val left = 58f
        val width = pageWidth - 2 * left
        val top = 62f

        /* Top row: the brand, the edition, a hairline. */
        val brand = paint(9.5f, Palette.INK, weight = 600)
        val edition = paint(8.5f, Palette.FAINT)
        canvas.drawText(s(R.string.report_pdf_brand).uppercase(), left, top + 9f, brand)
        val editionText = s(R.string.report_pdf_edition).uppercase()
        canvas.drawText(editionText, left + width - edition.measureText(editionText), top + 9f, edition)
        hairline(canvas, left, top + 22f, width)

        /* Bottom row: what the chart is, and the verified badge. */
        val metaParts = listOfNotNull(
            cover.ascendant?.let { s(R.string.report_pdf_cover_ascendant, it) },
            cover.place?.takeIf { it.isNotBlank() },
            cover.preparedOn?.let { s(R.string.report_pdf_cover_prepared, it) },
        )
        val badgeText = s(if (story.verification.isVerified) R.string.report_pdf_verified else R.string.report_pdf_qualified).uppercase()
        val badgePaint = paint(7.5f, Palette.GREEN, weight = 600)
        val badgeWidth = badgePaint.measureText(badgeText) + 20f
        val meta = layout(clean(metaParts.joinToString(" · ")), paint(9.5f, Palette.MUTED), width - badgeWidth - 24f, spacing = 1.45f)
        val metaTop = pageHeight - 54f - meta.height
        hairline(canvas, left, metaTop - 16f, width)
        draw(canvas, meta, left, metaTop)
        val badgeRect = RectF(left + width - badgeWidth, metaTop + meta.height - 22f, left + width, metaTop + meta.height)
        fill.color = Palette.GREEN_SOFT
        canvas.drawRoundRect(badgeRect, 11f, 11f, fill)
        stroke.color = Palette.GREEN
        stroke.strokeWidth = 0.75f
        canvas.drawRoundRect(badgeRect, 11f, 11f, stroke)
        canvas.drawText(badgeText, badgeRect.left + 10f, badgeRect.centerY() + 2.7f, badgePaint)

        /* The body, centred in what is left. */
        val kicker = layout(s(R.string.report_pdf_cover_kicker).uppercase(), paint(9f, Palette.GOLD, weight = 600), width)
        val name = layout(clean(cover.name).uppercase(), paint(32f, Palette.INK, weight = 600), 450f, spacing = 1.08f)
        val title = layout(s(R.string.report_pdf_cover_title), paint(19f, Palette.GOLD, weight = 400), width)
        val deck = layout(clean(story.subtitle), paint(13.5f, Palette.MUTED, weight = 300), 340f, spacing = 1.5f)
        val bodyHeight = 2f + 26f + kicker.height + 14f + name.height + 12f + title.height + 18f + deck.height
        val areaTop = top + 22f
        val areaBottom = metaTop - 16f
        var y = areaTop + (areaBottom - areaTop - bodyHeight) / 2f - 24f
        fill.color = Palette.GOLD
        canvas.drawRect(left, y, left + 44f, y + 2f, fill)
        y += 28f
        draw(canvas, kicker, left, y); y += kicker.height + 14f
        draw(canvas, name, left, y); y += name.height + 12f
        draw(canvas, title, left, y); y += title.height + 18f
        draw(canvas, deck, left, y)
    }

    private fun drawRunningElements(canvas: Canvas, name: String, pageNumber: Int, totalPages: Int) {
        val head = paint(7.5f, Palette.FAINT)
        val y = 38f
        canvas.drawText(s(R.string.report_pdf_running_head).uppercase(), marginX, y, head)
        val right = clean(name).uppercase()
        canvas.drawText(right, pageWidth - marginX - head.measureText(right), y, head)
        hairline(canvas, marginX, y + 7f, contentWidth)
        val folio = paint(8.5f, Palette.FAINT, monospace = true)
        val number = s(R.string.report_pdf_folio, pageNumber, totalPages)
        canvas.drawText(number, pageWidth - marginX - folio.measureText(number), pageHeight - 34f, folio)
    }

    // ── Begin here ───────────────────────────────────────────────────────

    private fun beginHere(story: PersonalStory): List<Element> = buildList {
        add(kicker(s(R.string.report_pdf_begin_kicker)))
        add(sectionTitle(s(R.string.report_pdf_begin_title)))
        add(text(story.introduction, paint(12.5f, Palette.MUTED, weight = 300), spacing = 1.5f, spaceBefore = 10f))
        add(rule(spaceBefore = 18f))
        story.preface.forEachIndexed { index, paragraph ->
            add(text(paragraph, bodyPaint(12f), spacing = 1.58f, justify = true, spaceBefore = if (index == 0) 14f else 9f))
        }
        add(rule(spaceBefore = 14f))

        if (story.atAGlance.isNotEmpty()) {
            add(miniHeading(s(R.string.report_pdf_glance), spaceBefore = 22f))
            story.atAGlance.chunked(2).forEachIndexed { index, row -> add(glanceRow(row, spaceBefore = if (index == 0) 0f else 9f, follows = index > 0)) }
        }
        if (story.centralThemes.isNotEmpty()) {
            add(miniHeading(s(R.string.report_pdf_threads), spaceBefore = 22f))
            story.centralThemes.take(3).forEachIndexed { index, theme ->
                add(numberedRow(index + 1, theme, bodyPaint(11f), spaceBefore = if (index == 0) 0f else 7f, follows = index > 0))
            }
        }
    }

    private fun glanceRow(items: List<StoryGlance>, spaceBefore: Float, follows: Boolean = false): Element {
        val gap = 10f
        val cardWidth = (contentWidth - gap) / 2f
        val pad = 11f
        val inner = cardWidth - 2 * pad
        val cards = items.map { item ->
            Triple(
                layout(clean(item.label).uppercase(), paint(7.5f, Palette.FAINT, weight = 600), inner),
                layout(clean(item.value), paint(13f, Palette.INK, weight = 500), inner),
                layout(clean(item.context), paint(9.5f, Palette.MUTED), inner, spacing = 1.35f),
            )
        }
        val height = cards.maxOf { (label, value, context) -> pad + label.height + 5f + value.height + 4f + context.height + pad }
        return box(height, spaceBefore, keepWithPrevious = follows) { canvas, top ->
            cards.forEachIndexed { index, (label, value, context) ->
                val x = index * (cardWidth + gap)
                card(canvas, RectF(x, top, x + cardWidth, top + height))
                var y = top + pad
                draw(canvas, label, x + pad, y); y += label.height + 5f
                draw(canvas, value, x + pad, y); y += value.height + 4f
                draw(canvas, context, x + pad, y)
            }
        }
    }

    // ── A chapter ────────────────────────────────────────────────────────

    private fun chapter(story: PersonalStory, chapter: StoryChapter, index: Int): List<Element> = buildList {
        val number = (index + 1).toString().padStart(2, '0')
        add(text(number, paint(30f, Palette.GOLD_SOFT, weight = 400, monospace = true), keepWithNext = true, newPage = true))
        add(kicker(chapter.eyebrow, spaceBefore = 8f))
        add(text(chapter.title, paint(21f, Palette.INK, weight = 500), spacing = 1.18f, spaceBefore = 6f, keepWithNext = true))
        add(supportRow(chapter))

        val opening = clean(chapter.opening.orEmpty())
        val paragraphs = chapter.narrative.ifEmpty { listOf(chapter.body) }.map(::clean).filter { it.isNotEmpty() && it != opening }
        if (opening.isNotEmpty()) add(opening(opening))
        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            add(text(paragraph, bodyPaint(11.5f), spacing = 1.6f, justify = true, spaceBefore = if (paragraphIndex == 0) 16f else 9f))
        }

        if (chapter.id == "timing" && story.timeline.isNotEmpty()) {
            add(miniHeading(s(R.string.report_pdf_seasons), spaceBefore = 22f))
            story.timeline.forEachIndexed { itemIndex, item -> add(timelineRow(item, spaceBefore = if (itemIndex == 0) 0f else 10f, follows = itemIndex > 0)) }
        }

        val practices = chapter.practices.ifEmpty { chapter.highlights }
        if (practices.isNotEmpty()) {
            add(miniHeading(s(R.string.report_pdf_carry), spaceBefore = 22f))
            practices.forEachIndexed { practiceIndex, practice ->
                add(numberedRow(practiceIndex + 1, practice, paint(10.5f, Palette.INK), spaceBefore = if (practiceIndex == 0) 0f else 6f, follows = practiceIndex > 0))
            }
        }

        chapter.reflectionPrompt?.let(::clean)?.takeIf { it.isNotEmpty() }?.let { prompt ->
            add(miniHeading(s(R.string.report_pdf_question), spaceBefore = 20f))
            add(text(prompt, paint(11.5f, Palette.MUTED, weight = 300), spacing = 1.45f, breakable = false))
        }

        if (chapter.signals.isNotEmpty()) {
            add(miniHeading(s(R.string.report_pdf_why), spaceBefore = 20f))
            chapter.signals.take(4).forEachIndexed { signalIndex, signal ->
                add(signalRow(signal, spaceBefore = if (signalIndex == 0) 0f else 4f, follows = signalIndex > 0))
            }
        }
    }

    private fun supportRow(chapter: StoryChapter): Element {
        val (label, ink, ground) = when (chapter.support) {
            "well-supported" -> Triple(R.string.report_pdf_support_well, Palette.GREEN, Palette.GREEN_SOFT)
            "exploratory" -> Triple(R.string.report_pdf_support_exploratory, Palette.ROSE, Palette.ROSE_SOFT)
            else -> Triple(R.string.report_pdf_support_supported, Palette.GOLD, Palette.PAPER_DEEP)
        }
        val pillPaint = paint(7.5f, ink, weight = 600)
        val pillText = s(label).uppercase()
        val pillWidth = pillPaint.measureText(pillText) + 16f
        val pillHeight = 18f
        val note = layout(clean(chapter.supportNote.orEmpty()), paint(9.5f, Palette.FAINT), contentWidth - pillWidth - 10f, spacing = 1.35f)
        val height = maxOf(pillHeight, note.height.toFloat())
        return box(height, spaceBefore = 12f, keepWithNext = true) { canvas, top ->
            val rect = RectF(0f, top, pillWidth, top + pillHeight)
            fill.color = ground
            canvas.drawRoundRect(rect, 9f, 9f, fill)
            canvas.drawText(pillText, 8f, rect.centerY() + 2.7f, pillPaint)
            draw(canvas, note, pillWidth + 10f, top + (pillHeight - minOf(note.height.toFloat(), pillHeight)) / 2f)
        }
    }

    /* The pull quote: larger and lighter, with the gold rule beside every line of it, on whichever page. */
    private fun opening(text: String): Element {
        val indent = 14f
        val layout = layout(text, paint(14f, Palette.INK, weight = 300), contentWidth - indent, spacing = 1.5f)
        return Element(FlowBlock(edges(layout), spaceBefore = 16f)) { canvas, from, to, top ->
            val height = (layout.getLineTop(to) - layout.getLineTop(from)).toFloat()
            fill.color = Palette.GOLD
            canvas.drawRect(0f, top + 2f, 2f, top + height - 2f, fill)
            drawLines(canvas, layout, indent, top, from, to)
        }
    }

    private fun timelineRow(item: StoryTimelineItem, spaceBefore: Float, follows: Boolean = false): Element {
        val rail = 16f
        val windowPaint = paint(9.5f, Palette.GOLD, monospace = true)
        val window = clean(item.window)
        val windowWidth = windowPaint.measureText(window)
        val label = layout(clean(item.label), paint(11f, Palette.INK, weight = 500), contentWidth - rail - windowWidth - 12f)
        val narrative = layout(clean(item.narrative), paint(10f, Palette.MUTED), contentWidth - rail, spacing = 1.4f)
        val height = label.height + 3f + narrative.height
        return box(height, spaceBefore, keepWithPrevious = follows) { canvas, top ->
            fill.color = Palette.GOLD
            canvas.drawCircle(3.5f, top + 6.5f, 3.5f, fill)
            draw(canvas, label, rail, top)
            canvas.drawText(window, contentWidth - windowWidth, top + label.getLineBaseline(0), windowPaint)
            draw(canvas, narrative, rail, top + label.height + 3f)
        }
    }

    private fun signalRow(signal: StorySignal, spaceBefore: Float, follows: Boolean = false): Element {
        val labelWidth = 118f
        val label = layout(clean(signal.label), paint(9.5f, Palette.FAINT), labelWidth - 8f, spacing = 1.35f)
        val value = layout(clean(signal.value), paint(9.5f, Palette.MUTED), contentWidth - labelWidth, spacing = 1.35f)
        val height = maxOf(label.height, value.height).toFloat()
        return box(height, spaceBefore, keepWithPrevious = follows) { canvas, top ->
            draw(canvas, label, 0f, top)
            draw(canvas, value, labelWidth, top)
        }
    }

    // ── The close: how to keep using it, and how it was checked ──────────

    private fun closing(story: PersonalStory): List<Element> = buildList {
        add(kicker(s(R.string.report_pdf_close_kicker), newPage = true))
        add(sectionTitle(s(R.string.report_pdf_close_title)))
        add(text(story.reflectionNote, bodyPaint(12f), spacing = 1.58f, spaceBefore = 10f))
        add(rule(spaceBefore = 18f))
        listOf(
            R.string.report_pdf_close_note1_title to R.string.report_pdf_close_note1,
            R.string.report_pdf_close_note2_title to R.string.report_pdf_close_note2,
            R.string.report_pdf_close_note3_title to R.string.report_pdf_close_note3,
        ).forEachIndexed { index, (heading, body) ->
            add(closingNote(index + 1, s(heading), s(body), spaceBefore = 14f, follows = index > 0))
        }
        add(text(s(R.string.report_pdf_sign_off), paint(12.5f, Palette.INK, weight = 300), spacing = 1.5f, spaceBefore = 22f, breakable = false))

        val verification = story.verification
        add(miniHeading(s(R.string.report_pdf_checked), spaceBefore = 30f))
        add(text(verification.summary, paint(11f, Palette.MUTED), spacing = 1.45f, spaceBefore = 0f, breakable = false))
        verification.checks.forEachIndexed { index, check -> add(checkRow(check, spaceBefore = if (index == 0) 12f else 7f, follows = index > 0)) }
        add(rule(spaceBefore = 16f))
        add(text(provenance(verification), paint(8.5f, Palette.FAINT), spacing = 1.5f, spaceBefore = 10f, breakable = false))
    }

    private fun closingNote(number: Int, heading: String, body: String, spaceBefore: Float, follows: Boolean = false): Element {
        val mark = 28f
        val title = layout(heading, paint(11.5f, Palette.INK, weight = 500), contentWidth - mark)
        val text = layout(body, paint(10.5f, Palette.MUTED), contentWidth - mark, spacing = 1.5f)
        val markPaint = paint(11f, Palette.GOLD, monospace = true)
        return box(title.height + 4f + text.height, spaceBefore, keepWithPrevious = follows) { canvas, top ->
            canvas.drawText(number.toString().padStart(2, '0'), 0f, top + title.getLineBaseline(0), markPaint)
            draw(canvas, title, mark, top)
            draw(canvas, text, mark, top + title.height + 4f)
        }
    }

    private fun checkRow(check: VerificationCheck, spaceBefore: Float, follows: Boolean = false): Element {
        val mark = 22f
        val label = layout(clean(check.label), paint(10f, Palette.INK, weight = 500), contentWidth - mark)
        val detail = layout(clean(check.detail), paint(9.5f, Palette.MUTED), contentWidth - mark, spacing = 1.35f)
        return box(label.height + 2f + detail.height, spaceBefore, keepWithPrevious = follows) { canvas, top ->
            statusMark(canvas, check.status, 6.5f, top + 6.5f)
            draw(canvas, label, mark, top)
            draw(canvas, detail, mark, top + label.height + 2f)
        }
    }

    /* A tick for passed, a bar for a qualification, a cross for a failure; each in a small disc. */
    private fun statusMark(canvas: Canvas, status: String, cx: Float, cy: Float) {
        val (ink, ground) = when (status) {
            "passed" -> Palette.GREEN to Palette.GREEN_SOFT
            "warning" -> Palette.GOLD to Palette.PAPER_DEEP
            else -> Palette.ROSE to Palette.ROSE_SOFT
        }
        fill.color = ground
        canvas.drawCircle(cx, cy, 6.5f, fill)
        stroke.color = ink
        stroke.strokeWidth = 1.2f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        val path = Path()
        when (status) {
            "passed" -> {
                path.moveTo(cx - 3f, cy + 0.2f)
                path.lineTo(cx - 0.8f, cy + 2.4f)
                path.lineTo(cx + 3.2f, cy - 2.4f)
            }
            "warning" -> {
                path.moveTo(cx - 2.8f, cy)
                path.lineTo(cx + 2.8f, cy)
            }
            else -> {
                path.moveTo(cx - 2.4f, cy - 2.4f)
                path.lineTo(cx + 2.4f, cy + 2.4f)
                path.moveTo(cx + 2.4f, cy - 2.4f)
                path.lineTo(cx - 2.4f, cy + 2.4f)
            }
        }
        canvas.drawPath(path, stroke)
    }

    /* The calculation record as one sentence of small print, as the web's closing page has it. */
    private fun provenance(verification: StoryVerification): String {
        val profile = verification.profile
        val checks = if (verification.warningCount > 0) {
            s(R.string.report_pdf_provenance_qualified, verification.passedCount, verification.totalCount, verification.warningCount)
        } else {
            s(R.string.report_pdf_provenance_clean, verification.totalCount)
        }
        return s(
            R.string.report_pdf_provenance,
            profile.provider,
            profile.engine,
            profile.ayanamsha,
            profile.houseSystem,
            profile.birthTimeReliability.lowercase(),
            checks,
        )
    }

    // ── Building blocks ──────────────────────────────────────────────────

    private fun bodyPaint(size: Float) = paint(size, Palette.INK, weight = 400)

    private fun kicker(text: String, spaceBefore: Float = 0f, newPage: Boolean = false) =
        text(text.uppercase(), paint(8.5f, Palette.GOLD, weight = 600), spaceBefore = spaceBefore, keepWithNext = true, newPage = newPage)

    private fun sectionTitle(text: String) =
        text(text, paint(23f, Palette.INK, weight = 500), spacing = 1.15f, spaceBefore = 8f, keepWithNext = true)

    private fun miniHeading(text: String, spaceBefore: Float) =
        text(text.uppercase(), paint(8f, Palette.GOLD, weight = 600), spaceBefore = spaceBefore, keepWithNext = true).let {
            /* The space under a heading belongs to it, so it travels with the heading. */
            val height = it.block.height + 8f
            Element(FlowBlock(listOf(0f, height), it.block.spaceBefore, breakable = false, keepWithNext = true)) { canvas, _, _, top ->
                it.draw(canvas, 0, it.block.lineCount, top)
            }
        }

    private fun numberedRow(number: Int, text: String, textPaint: TextPaint, spaceBefore: Float, follows: Boolean = false): Element {
        val mark = 24f
        val layout = layout(clean(text), textPaint, contentWidth - mark, spacing = 1.42f)
        val markPaint = paint(9.5f, Palette.GOLD, monospace = true)
        return box(layout.height.toFloat(), spaceBefore, keepWithPrevious = follows) { canvas, top ->
            canvas.drawText(number.toString().padStart(2, '0'), 0f, top + layout.getLineBaseline(0), markPaint)
            draw(canvas, layout, mark, top)
        }
    }

    private fun rule(spaceBefore: Float) = box(0.75f, spaceBefore) { canvas, top -> hairline(canvas, 0f, top, contentWidth) }

    private fun text(
        value: String,
        textPaint: TextPaint,
        spacing: Float = 1.25f,
        justify: Boolean = false,
        spaceBefore: Float = 0f,
        keepWithNext: Boolean = false,
        newPage: Boolean = false,
        breakable: Boolean = true,
    ): Element {
        val layout = layout(clean(value), textPaint, contentWidth, spacing, justify)
        return Element(FlowBlock(edges(layout), spaceBefore, breakable, keepWithNext, newPage)) { canvas, from, to, top ->
            drawLines(canvas, layout, 0f, top, from, to)
        }
    }

    /* [keepWithPrevious]: a table's second and later rows, which move with the row above rather than start a page alone. */
    private fun box(
        height: Float,
        spaceBefore: Float,
        keepWithNext: Boolean = false,
        keepWithPrevious: Boolean = false,
        draw: (Canvas, Float) -> Unit,
    ) = Element(FlowBlock.box(height, spaceBefore, keepWithNext, keepWithPrevious = keepWithPrevious)) { canvas, _, _, top -> draw(canvas, top) }

    /*
     * [spacing] is a line height in ems, as CSS means it (1.6 = 1.6 x the type
     * size), not StaticLayout's multiplier of the font's own ascent-to-descent,
     * which for Geist is 1.3 em and would open every paragraph by nearly a third.
     *
     * Words are never hyphenated: a word split over two lines is two words
     * to a PDF reader's search. WrongConstant: the builder's annotations name
     * LineBreaker's constants, which are API 29; Layout's (API 23 and 26) are
     * the same values and this app runs from API 28.
     */
    @SuppressLint("WrongConstant")
    private fun layout(text: String, textPaint: TextPaint, width: Float, spacing: Float = 1.25f, justify: Boolean = false): StaticLayout {
        val metrics = textPaint.fontMetrics
        val extra = spacing * textPaint.textSize - (metrics.descent - metrics.ascent)
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(extra, 1f)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setJustificationMode(if (justify) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE)
            .build()
    }

    private fun edges(layout: StaticLayout): List<Float> = (0..layout.lineCount).map { layout.getLineTop(it).toFloat() }

    private fun draw(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) = drawLines(canvas, layout, x, y, 0, layout.lineCount)

    /* Lines [from, to) of a layout with the first one's top at y: shift up by that line's top, clip to the run. */
    private fun drawLines(canvas: Canvas, layout: StaticLayout, x: Float, y: Float, from: Int, to: Int) {
        if (layout.lineCount == 0 || from >= to) return
        val lineTop = layout.getLineTop(from).toFloat()
        val lineBottom = layout.getLineTop(to).toFloat()
        canvas.save()
        canvas.translate(x, y - lineTop)
        canvas.clipRect(-6f, lineTop, layout.width + 6f, lineBottom)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun card(canvas: Canvas, rect: RectF) {
        fill.color = Palette.WHITE
        canvas.drawRoundRect(rect, 5f, 5f, fill)
        stroke.color = Palette.LINE
        stroke.strokeWidth = 0.75f
        canvas.drawRoundRect(rect, 5f, 5f, stroke)
    }

    private fun hairline(canvas: Canvas, x: Float, y: Float, width: Float) {
        fill.color = Palette.LINE
        canvas.drawRect(x, y, x + width, y + 0.75f, fill)
    }

    companion object {
        /** Whitespace runs to one space; the type is set by the layout, not by the text's own breaks. */
        fun clean(value: String): String = value.replace(Regex("\\s+"), " ").trim()
    }
}
