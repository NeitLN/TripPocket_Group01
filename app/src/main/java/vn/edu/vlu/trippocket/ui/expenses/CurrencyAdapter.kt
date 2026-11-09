package vn.edu.vlu.trippocket.ui.expenses

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.model.CurrencyRateItem
import vn.edu.vlu.trippocket.databinding.ItemCurrencyBinding
import vn.edu.vlu.trippocket.util.clearRemote
import vn.edu.vlu.trippocket.util.loadRemote
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/**
 * RecyclerView adapter for foreign currency exchange rates (Milestone 4).
 * Loads national flags via Glide ([loadRemote]) from FlagCDN, and clears bitmaps in [onViewRecycled].
 */
class CurrencyAdapter : ListAdapter<CurrencyRateItem, CurrencyAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCurrencyBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.binding.ivFlag.clearRemote()
    }

    class ViewHolder(val binding: ItemCurrencyBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CurrencyRateItem) {
            binding.tvCurrencyCode.text = item.code
            binding.tvCurrencyName.setText(item.nameRes)

            val rateFormatted = formatRateToVnd(item.rateToVnd)
            binding.tvRateToVnd.text = binding.root.context.getString(
                R.string.exchange_rates_rate_format,
                item.code,
                rateFormatted,
            )

            val flagUrl = "https://flagcdn.com/w80/${item.flagCountryCode}.png"
            binding.ivFlag.loadRemote(flagUrl, R.drawable.ic_flag, R.drawable.ic_flag)
        }
    }

    companion object {
        private val integerFormat = NumberFormat.getIntegerInstance(Locale.US)
        private val decimalFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))

        /**
         * Formats a conversion rate to VND string for display.
         * Values >= 100 (e.g. USD, EUR, JPY) are displayed rounded to the nearest dong.
         * Values < 100 (e.g. IDR, KRW, THB) retain up to 2 decimal places to avoid precision loss.
         */
        fun formatRateToVnd(rate: Double): String {
            return if (rate >= 100.0) {
                "${integerFormat.format(rate.roundToLong())} ₫"
            } else {
                "${decimalFormat.format(rate)} ₫"
            }
        }

        private val DiffCallback = object : DiffUtil.ItemCallback<CurrencyRateItem>() {
            override fun areItemsTheSame(oldItem: CurrencyRateItem, newItem: CurrencyRateItem): Boolean {
                return oldItem.code == newItem.code
            }

            override fun areContentsTheSame(oldItem: CurrencyRateItem, newItem: CurrencyRateItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
