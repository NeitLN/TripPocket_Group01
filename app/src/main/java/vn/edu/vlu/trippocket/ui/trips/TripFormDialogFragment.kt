package vn.edu.vlu.trippocket.ui.trips

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.util.Pair as AndroidPair
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.FragmentTripFormBinding
import vn.edu.vlu.trippocket.domain.TripProblem
import vn.edu.vlu.trippocket.util.Format
import vn.edu.vlu.trippocket.util.applySystemBarPadding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A2: new trip (from the trip list) or edit trip (from Trip Detail), full screen. Reports the
 * saved trip id through [REQUEST_KEY] so the list can open the new trip.
 */
class TripFormDialogFragment : DialogFragment() {

    private var _binding: FragmentTripFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TripFormViewModel by viewModels { TripFormViewModel.Factory }

    private var fieldsFilled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_TripPocket)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTripFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fieldsFilled = false
        binding.root.applySystemBarPadding()

        binding.btnSave.setOnClickListener { viewModel.save() }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.etDates.setOnClickListener { showDatePicker() }
        binding.tilDates.setEndIconOnClickListener { showDatePicker() }
        (childFragmentManager.findFragmentByTag(TAG_DATE_PICKER) as? MaterialDatePicker<*>)?.let(::listen)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: TripFormUiState) {
        state.savedTripId?.let { tripId ->
            viewModel.savedShown()
            parentFragmentManager.setFragmentResult(REQUEST_KEY, bundleOf(RESULT_TRIP_ID to tripId))
            dismiss()
            return
        }
        if (state.notFound) {
            Snackbar.make(binding.root, R.string.trip_error_not_found, Snackbar.LENGTH_LONG).show()
            dismiss()
            return
        }

        binding.tvFormTitle.setText(if (state.editing) R.string.trip_form_edit_title else R.string.trip_form_new_title)
        binding.btnSave.isEnabled = !state.saving && !state.loading
        if (state.loading) return
        if (!fieldsFilled) fillFields(state)

        val start = state.startDate
        val end = state.endDate
        binding.etDates.setText(if (start != null && end != null) Format.dateRange(start, end) else "")

        binding.tilName.error = when {
            state.has(TripProblem.BLANK_NAME) -> getString(R.string.trip_error_name_empty)
            state.has(TripProblem.NAME_TOO_LONG) -> getString(R.string.trip_error_name_too_long)
            else -> null
        }
        binding.tilDestination.error = when {
            state.has(TripProblem.BLANK_DESTINATION) -> getString(R.string.trip_error_destination_empty)
            state.has(TripProblem.DESTINATION_TOO_LONG) -> getString(R.string.trip_error_destination_too_long)
            else -> null
        }
        binding.tilDates.error = when {
            state.datesMissing -> getString(R.string.trip_error_dates_missing)
            state.has(TripProblem.DATES_REVERSED) -> getString(R.string.trip_error_dates_reversed)
            state.has(TripProblem.DAYS_IN_USE) -> getString(R.string.trip_error_days_in_use)
            else -> null
        }
        if (state.saveFailed) {
            Snackbar.make(binding.root, R.string.trip_error_save_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun fillFields(state: TripFormUiState) {
        fieldsFilled = true
        binding.etName.setText(state.name)
        binding.etDestination.setText(state.destination)
        binding.etName.doAfterTextChanged { viewModel.onNameChanged(it?.toString().orEmpty()) }
        binding.etDestination.doAfterTextChanged { viewModel.onDestinationChanged(it?.toString().orEmpty()) }
    }

    private fun showDatePicker() {
        if (childFragmentManager.findFragmentByTag(TAG_DATE_PICKER) != null) return
        val state = viewModel.uiState.value
        val start = state.startDate
        val end = state.endDate
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.trip_dates_picker_title)
            .apply { if (start != null && end != null) setSelection(AndroidPair(start.toPickerMillis(), end.toPickerMillis())) }
            .build()
        listen(picker)
        picker.show(childFragmentManager, TAG_DATE_PICKER)
    }

    /** Re-attached after rotation, when the picker is restored by the FragmentManager. */
    private fun listen(picker: MaterialDatePicker<*>) {
        picker.addOnPositiveButtonClickListener { selection ->
            val range = selection as? AndroidPair<*, *> ?: return@addOnPositiveButtonClickListener
            val first = range.first as? Long ?: return@addOnPositiveButtonClickListener
            val second = range.second as? Long ?: return@addOnPositiveButtonClickListener
            viewModel.onDatesChanged(first.toPickerDate(), second.toPickerDate())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TripFormDialogFragment"
        const val REQUEST_KEY = "request_trip_form"
        const val RESULT_TRIP_ID = "trip_id"
        private const val TAG_DATE_PICKER = "trip_dates_picker"

        /** [tripId] null = new trip. */
        fun show(fragmentManager: FragmentManager, tripId: String? = null) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            TripFormDialogFragment().apply {
                arguments = bundleOf(ARG_EDIT_TRIP_ID to tripId)
            }.show(fragmentManager, TAG)
        }

        /** MaterialDatePicker works in UTC midnight millis. */
        private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        private fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    }
}
