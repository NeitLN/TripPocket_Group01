package vn.edu.vlu.trippocket.ui.common

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ReplacementSpan
import androidx.annotation.ColorInt
import kotlin.math.ceil

/**
 * Sets one word of a title on a horizontal lime sticker with a white die-cut border
 * ("My [trips]"). The text itself is unchanged, so TalkBack reads the plain title.
 * One word per screen at most (DESIGN.md).
 */
class StickerHighlightSpan(
    @param:ColorInt private val fill: Int,
    @param:ColorInt private val border: Int,
    @param:ColorInt private val textColor: Int,
    private val borderWidth: Float,
    private val radius: Float,
    private val padX: Float,
) : ReplacementSpan() {

    private val rect = RectF()
    private val shape = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) {
            // Grow the line box by the border so the white edge and the lime bottom are not clipped.
            paint.getFontMetricsInt(fm)
            val extra = ceil(borderWidth).toInt() + 1
            fm.ascent -= extra
            fm.top -= extra
            fm.descent += extra
            fm.bottom += extra
        }
        return (paint.measureText(text, start, end) + 2 * padX + 2 * borderWidth).toInt()
    }

    override fun draw(
        canvas: Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint,
    ) {
        val width = paint.measureText(text, start, end) + 2 * padX
        val metrics = paint.fontMetrics
        rect.set(x + borderWidth, y + metrics.ascent, x + borderWidth + width, y + metrics.descent)
        shape.style = Paint.Style.FILL
        shape.color = border
        canvas.drawRoundRect(
            rect.left - borderWidth, rect.top - borderWidth, rect.right + borderWidth, rect.bottom + borderWidth,
            radius + borderWidth, radius + borderWidth, shape,
        )
        shape.color = fill
        canvas.drawRoundRect(rect, radius, radius, shape)
        val original = paint.color
        paint.color = textColor
        canvas.drawText(text, start, end, x + borderWidth + padX, y.toFloat(), paint)
        paint.color = original
    }

    companion object {
        /** [title] with [word] (its first occurrence) set on the sticker highlight. */
        fun apply(title: String, word: String, span: StickerHighlightSpan): CharSequence {
            val start = title.indexOf(word)
            if (start < 0) return title
            return SpannableString(title).apply {
                setSpan(span, start, start + word.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}
