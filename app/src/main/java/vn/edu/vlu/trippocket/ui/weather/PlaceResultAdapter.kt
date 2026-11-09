package vn.edu.vlu.trippocket.ui.weather

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.model.Place
import vn.edu.vlu.trippocket.databinding.ItemPlaceResultBinding
import vn.edu.vlu.trippocket.util.clearRemote
import vn.edu.vlu.trippocket.util.loadRemote

/**
 * The search dropdown. Each row shows the place and its Wikipedia photo (Milestone 4): the photo
 * arrives a moment after the list, so the same coordinates are updated in place.
 */
class PlaceResultAdapter(
    private val onPick: (Place) -> Unit,
) : ListAdapter<SearchResult, PlaceResultAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemPlaceResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.clearPhoto()
    }

    inner class ViewHolder(private val binding: ItemPlaceResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(result: SearchResult) {
            val context = binding.root.context
            val place = result.place
            binding.tvResultName.text = place.name
            binding.tvResultRegion.text =
                context.getString(R.string.weather_place_title, place.region, place.country)
            binding.tvResultCoords.text =
                context.getString(R.string.weather_coordinates, place.latitude, place.longitude)
            // A place without a Wikipedia thumbnail keeps the default image: that is not an error,
            // so the placeholder and the error image are the same drawable. Only a real photo is
            // worth describing, the placeholder is decorative.
            binding.ivThumbnail.loadRemote(
                result.photoUrl,
                R.drawable.ic_photo,
                R.drawable.ic_photo,
            )
            binding.ivThumbnail.contentDescription = result.photoUrl?.let {
                context.getString(R.string.weather_photo_of, place.name)
            }
            binding.root.setOnClickListener { onPick(place) }
        }

        /** Drops the downloaded picture of a row that scrolled away. */
        fun clearPhoto() {
            binding.ivThumbnail.clearRemote()
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<SearchResult>() {
            // One place is identified by its coordinates, so a photo arriving later updates the
            // existing row instead of adding a second one.
            override fun areItemsTheSame(old: SearchResult, new: SearchResult) =
                old.place.latitude == new.place.latitude &&
                    old.place.longitude == new.place.longitude

            // The whole result, photo included: when the photo URL arrives the row must redraw.
            override fun areContentsTheSame(old: SearchResult, new: SearchResult) = old == new
        }
    }
}
