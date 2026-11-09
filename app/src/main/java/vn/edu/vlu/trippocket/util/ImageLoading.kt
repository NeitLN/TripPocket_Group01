package vn.edu.vlu.trippocket.util

import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

/**
 * Milestone 4: loads a remote image URL into this ImageView with Glide, off the main thread.
 *
 * - [placeholder] shows while the image downloads (and when [url] is blank).
 * - [error] shows when the URL is broken, the host is unreachable or the phone is offline.
 * - Glide keeps decoded images in its memory cache and the downloaded bytes in its disk cache
 *   (DiskCacheStrategy.AUTOMATIC), so scrolling back or reopening the screen offline reuses them.
 *
 * Safe to call from RecyclerView.Adapter.onBindViewHolder: Glide cancels the previous request of
 * a recycled ImageView, so a fast scroll never shows another row's picture.
 */
fun ImageView.loadRemote(url: String?, @DrawableRes placeholder: Int, @DrawableRes error: Int) {
    Glide.with(this)
        .load(url?.takeIf { it.isNotBlank() })
        .placeholder(placeholder)
        .error(error)
        .fallback(placeholder)
        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
        .centerCrop()
        .transition(DrawableTransitionOptions.withCrossFade())
        .into(this)
}

/** Stops the request of a recycled row and frees its bitmap (onViewRecycled). */
fun ImageView.clearRemote() {
    Glide.with(this).clear(this)
}
