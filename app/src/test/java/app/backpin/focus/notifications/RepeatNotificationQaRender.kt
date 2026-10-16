package app.backpin.focus.notifications

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import app.backpin.focus.domain.RepeatBand
import java.io.File
import java.io.FileOutputStream

/**
 * Design-QA lock-screen renders of the Morning band group.
 * Uses the same copy helpers as the live notifications. Not production UI.
 */
internal object RepeatNotificationQaRender {
    const val WIDTH: Int = 720
    const val HEIGHT: Int = 1600

    private val midnight = Color.parseColor("#10141F")
    private val surface = Color.parseColor("#1B2230")
    private val text = Color.parseColor("#F3F6FB")
    private val muted = Color.parseColor("#A7B2C4")
    private val mint = Color.parseColor("#71E2BE")
    private val outline = Color.parseColor("#3A465C")

    fun render(
        openTitles: List<String>,
        caption: String,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(midnight)
        drawStatusBar(canvas)
        drawClock(canvas)
        drawGroup(canvas, openTitles)
        drawCaption(canvas, caption)
        return bitmap
    }

    fun writePng(
        bitmap: Bitmap,
        file: File,
    ) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG compress failed for ${file.name}" }
        }
    }

    fun outputDir(): File {
        val artifacts = File("/opt/cursor/artifacts")
        if (artifacts.exists() || artifacts.mkdirs()) {
            if (artifacts.canWrite()) return artifacts
        }
        return File("app/build/outputs/repeat-notification-qa").also { it.mkdirs() }
    }

    private fun drawStatusBar(canvas: Canvas) {
        val time = paint(text, 22f, Typeface.DEFAULT)
        canvas.drawText("9:41", 36f, 48f, time)
        val icon = fill(text)
        canvas.drawCircle(WIDTH - 96f, 40f, 5f, icon)
        canvas.drawCircle(WIDTH - 76f, 40f, 5f, icon)
        canvas.drawRoundRect(RectF(WIDTH - 60f, 30f, WIDTH - 36f, 50f), 4f, 4f, icon)
    }

    private fun drawClock(canvas: Canvas) {
        val clock = paint(text, 118f, Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL))
        clock.textAlign = Paint.Align.CENTER
        canvas.drawText("7:00", WIDTH / 2f, 230f, clock)
        val date = paint(muted, 28f, Typeface.DEFAULT)
        date.textAlign = Paint.Align.CENTER
        canvas.drawText("Saturday, October 17", WIDTH / 2f, 290f, date)
    }

    private fun drawGroup(
        canvas: Canvas,
        openTitles: List<String>,
    ) {
        val band = RepeatBand.MORNING
        val summary = bandSummaryTitle(band, openTitles.size)
        val body = bandChildText(band)
        val cardTop = 360f
        val rowHeight = 148f
        val cardBottom = cardTop + 92f + openTitles.size * rowHeight + 28f
        val card = RectF(28f, cardTop, WIDTH - 28f, cardBottom)
        canvas.drawRoundRect(card, 36f, 36f, fill(surface))

        // Summary that alerts: app name plus the Figma title.
        val header = paint(text, 28f, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
        canvas.drawCircle(64f, cardTop + 46f, 10f, fill(mint))
        canvas.drawText("Backpin  ·  $summary", 88f, cardTop + 56f, header)
        val now = paint(muted, 22f, Typeface.DEFAULT)
        now.textAlign = Paint.Align.RIGHT
        canvas.drawText("now", WIDTH - 52f, cardTop + 56f, now)

        openTitles.forEachIndexed { index, title ->
            val top = cardTop + 92f + index * rowHeight
            if (index > 0) {
                canvas.drawLine(52f, top, WIDTH - 52f, top, stroke(outline, 2f))
            }
            val titlePaint = paint(text, 34f, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
            canvas.drawText(title, 52f, top + 58f, titlePaint)
            val bodyPaint = paint(muted, 24f, Typeface.DEFAULT)
            canvas.drawText(body, 52f, top + 98f, bodyPaint)
            drawDone(canvas, WIDTH - 52f, top + 36f)
        }
    }

    private fun drawDone(
        canvas: Canvas,
        right: Float,
        top: Float,
    ) {
        val label = "Done"
        val labelPaint = paint(mint, 26f, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
        val width = labelPaint.measureText(label) + 48f
        val height = 80f
        val rect = RectF(right - width, top, right, top + height)
        val border = stroke(mint, 3f)
        border.style = Paint.Style.STROKE
        canvas.drawRoundRect(rect, 32f, 32f, fill(Color.TRANSPARENT))
        canvas.drawRoundRect(rect, 32f, 32f, border)
        labelPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, rect.centerX(), rect.centerY() + 9f, labelPaint)
    }

    private fun drawCaption(
        canvas: Canvas,
        caption: String,
    ) {
        val bar = RectF(36f, HEIGHT - 210f, 44f, HEIGHT - 70f)
        canvas.drawRoundRect(bar, 4f, 4f, fill(Color.parseColor("#B7A4FF")))
        val paint = paint(muted, 24f, Typeface.DEFAULT)
        wrap(caption, 60).forEachIndexed { index, line ->
            canvas.drawText(line, 64f, HEIGHT - 180f + index * 34f, paint)
        }
    }

    private fun wrap(
        text: String,
        max: Int,
    ): List<String> {
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.isNotEmpty() && current.length + 1 + word.length > max) {
                lines += current.toString()
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    private fun paint(
        color: Int,
        size: Float,
        typeface: Typeface,
    ): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            this.typeface = typeface
        }

    private fun fill(color: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }

    private fun stroke(
        color: Int,
        width: Float,
    ): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = width
        }
}
