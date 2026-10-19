package vn.edu.vlu.trippocket.ui.weather

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.Place
import vn.edu.vlu.trippocket.databinding.ItemPlaceResultBinding

class PlaceResultAdapter(
    private val onPick: (Place) -> Unit,
) : ListAdapter<Place, PlaceResultAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemPlaceResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemPlaceResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(place: Place) {
            val context = binding.root.context
            binding.tvResultName.text = place.name
            binding.tvResultRegion.text =
                context.getString(R.string.weather_place_title, place.region, place.country)
            binding.tvResultCoords.text =
                context.getString(R.string.weather_coordinates, place.latitude, place.longitude)
            binding.root.setOnClickListener { onPick(place) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Place>() {
            override fun areItemsTheSame(old: Place, new: Place) =
                old.latitude == new.latitude && old.longitude == new.longitude

            override fun areContentsTheSame(old: Place, new: Place) = old == new
        }
    }
}
