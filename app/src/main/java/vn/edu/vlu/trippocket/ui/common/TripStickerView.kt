package vn.edu.vlu.trippocket.ui.common

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.TextViewCompat
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.domain.TripSticker

/**
 * The trip's die-cut sticker (DESIGN.md, signature move): a rounded square in one of five fills
 * with a white border, the destination's code in Be Vietnam Pro Black and a small stable tilt.
 * Decorative: the full name and dates always sit next to it, so TalkBack skips it.
 *
 * Use it in XML at 64dp inside an 80dp slot so the tilt and shadow never touch the text:
 * ```
 * <vn.edu.vlu.trippocket.ui.common.TripStickerView android:layout_width="64dp" android:layout_height="64dp" />
 * ```
 * then call [bind]. Owner: Tiến.
 */
class TripStickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatTextView(context, attrs) {

    private val cornerRadius = resources.getDimension(R.dimen.sticker_corner_radius)
    private val borderWidth = resources.getDimensionPixelSize(R.dimen.sticker_border_width)
    private var code: String? = null

    init {
        gravity = Gravity.CENTER
        maxLines = 1
        includeFontPadding = true
        TextViewCompat.setTextAppearance(this, R.style.TextAppearance_TripPocket_StickerCode)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        // A soft short shadow on the light theme only; at night the white border carries it.
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        elevation = if (night) 0f else resources.getDimension(R.dimen.sticker_elevation)
        if (isInEditMode) bind("Đà Lạt", "preview")
    }

    /** Colour and code from [destination], tilt from [tripId]; the same inputs always look the same. */
    fun bind(destination: String, tripId: String) {
        val index = TripSticker.colorIndex(destination)
        val fill = ContextCompat.getColor(context, FILLS[index])
        val ink = ContextCompat.getColor(context, INKS[index])
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = this@TripStickerView.cornerRadius
            setColor(fill)
            setStroke(borderWidth, ContextCompat.getColor(context, R.color.sticker_border))
        }
        setTextColor(ink)
        rotation = TripSticker.tiltDegrees(tripId)
        code = TripSticker.code(destination)
        showCode(code)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // With large system font sizes the code may not fit: drop the lettering rather than shrink it.
        val inner = width - 2 * borderWidth - paddingLeft - paddingRight
        val fits = code?.let { paint.measureText(it) <= inner } ?: true
        if (!fits && text.isNotEmpty()) post { showCode(null) }
    }

    private fun showCode(value: String?) {
        if (value != null) {
            text = value
            setCompoundDrawables(null, null, null, null)
        } else {
            text = ""
            val pocket = ContextCompat.getDrawable(context, R.drawable.ic_sticker_pocket)?.mutate()
            pocket?.let { DrawableCompat.setTint(it, currentTextColor) }
            setCompoundDrawablesRelativeWithIntrinsicBounds(null, pocket, null, null)
        }
    }

    private companion object {
        val FILLS = intArrayOf(
            R.color.sticker_violet, R.color.sticker_lime, R.color.sticker_pink,
            R.color.sticker_sky, R.color.sticker_apricot,
        )
        val INKS = intArrayOf(
            R.color.on_sticker_violet, R.color.on_sticker_lime, R.color.on_sticker_pink,
            R.color.on_sticker_sky, R.color.on_sticker_apricot,
        )
    }
}
