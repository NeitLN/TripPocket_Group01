package vn.edu.vlu.trippocket.ui.itinerary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.data.local.MockData
import vn.edu.vlu.trippocket.databinding.FragmentActivityFormBinding
import vn.edu.vlu.trippocket.domain.ActivityProblem
import vn.edu.vlu.trippocket.ui.detail.TripDetailViewModel
import vn.edu.vlu.trippocket.ui.detail.TripGate
import vn.edu.vlu.trippocket.ui.detail.tripViewModels
import vn.edu.vlu.trippocket.util.Format
import java.time.LocalTime

/**
 * Screen to add or edit an itinerary activity (I1).
 */
class ActivityFormFragment : Fragment() {

    private var _binding: FragmentActivityFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ItineraryFormViewModel by tripViewModels { ItineraryFormViewModel.Factory }
    private val detailViewModel: TripDetailViewModel by activityViewModels { TripDetailViewModel.Factory }

    private var fieldsFilled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childFragmentManager.setFragmentResultListener(
            ConfirmDeleteActivityDialogFragment.REQUEST_KEY,
            this,
        ) { _, bundle ->
            if (bundle.getBoolean(ConfirmDeleteActivityDialogFragment.RESULT_CONFIRMED, false)) {
                viewModel.delete()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentActivityFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fieldsFilled = false

        binding.btnSave.setOnClickListener { viewModel.save() }
        binding.btnDelete.setOnClickListener {
            val title = viewModel.uiState.value.title.trim()
            ConfirmDeleteActivityDialogFragment.newInstance(title)
                .show(childFragmentManager, ConfirmDeleteActivityDialogFragment.TAG)
        }

        binding.etTime.setOnClickListener { showTimePicker(viewModel.uiState.value.startTime) }
        binding.tilTime.setOnClickListener { showTimePicker(viewModel.uiState.value.startTime) }
        binding.tilTime.setEndIconOnClickListener { showTimePicker(viewModel.uiState.value.startTime) }

        // Reconnect listener if time picker was open before configuration change
        (childFragmentManager.findFragmentByTag(TAG_TIME_PICKER) as? MaterialTimePicker)?.let { picker ->
            picker.addOnPositiveButtonClickListener {
                viewModel.onStartTimeChanged(LocalTime.of(picker.hour, picker.minute))
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun showTimePicker(currentTime: LocalTime) {
        if (childFragmentManager.findFragmentByTag(TAG_TIME_PICKER) != null) return
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(currentTime.hour)
            .setMinute(currentTime.minute)
            .setTitleText(R.string.itinerary_field_time)
            .build()
        picker.addOnPositiveButtonClickListener {
            viewModel.onStartTimeChanged(LocalTime.of(picker.hour, picker.minute))
        }
        picker.show(childFragmentManager, TAG_TIME_PICKER)
    }

    private fun render(state: ItineraryFormUiState) {
        if (state.saved || state.deleted) {
            viewModel.savedShown()
            viewModel.deletedShown()
            findNavController().navigateUp()
            return
        }

        if (state.notFound) {
            Snackbar.make(binding.root, R.string.itinerary_error_not_found, Snackbar.LENGTH_LONG).show()
            findNavController().navigateUp()
            return
        }

        binding.tvFormTitle.setText(
            if (state.editing) R.string.itinerary_form_edit_title else R.string.itinerary_form_add_title
        )
        binding.btnDelete.isVisible = state.editing
        binding.btnSave.isEnabled = !state.saving
        binding.btnDelete.isEnabled = !state.saving

        if (state.loading) return

        if (!fieldsFilled) {
            fillFields(state)
        }

        binding.etTime.setText(state.startTime.format(Format.time))

        binding.tilTitle.error = when {
            state.has(ActivityProblem.BLANK_TITLE) -> getString(R.string.itinerary_error_title_empty)
            state.has(ActivityProblem.TITLE_TOO_LONG) -> getString(R.string.itinerary_error_title_too_long)
            else -> null
        }
        binding.tilPlace.error = when {
            state.has(ActivityProblem.PLACE_TOO_LONG) -> getString(R.string.itinerary_error_place_too_long)
            else -> null
        }
        binding.tilNote.error = when {
            state.has(ActivityProblem.NOTE_TOO_LONG) -> getString(R.string.itinerary_error_note_too_long)
            else -> null
        }
        binding.tilCost.error = when {
            state.has(ActivityProblem.COST_INVALID) || state.has(ActivityProblem.COST_NEGATIVE) ->
                getString(R.string.itinerary_error_cost_invalid)
            else -> null
        }
        binding.tilReminder.error = when {
            state.has(ActivityProblem.REMINDER_INVALID) -> getString(R.string.itinerary_error_reminder_invalid)
            else -> null
        }
        binding.tilDay.error = when {
            state.has(ActivityProblem.DAY_OUT_OF_RANGE) -> getString(R.string.itinerary_error_day_invalid)
            else -> null
        }
        binding.tilTime.error = when {
            state.has(ActivityProblem.TIME_OUT_OF_RANGE) -> getString(R.string.itinerary_error_time_invalid)
            else -> null
        }

        if (state.limitReached) {
            Snackbar.make(binding.root, R.string.itinerary_limit_reached, Snackbar.LENGTH_LONG).show()
        }
        if (state.saveFailed) {
            Snackbar.make(binding.root, R.string.itinerary_error_save_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun fillFields(state: ItineraryFormUiState) {
        fieldsFilled = true

        binding.etTitle.setText(state.title)
        binding.etPlace.setText(state.place)
        binding.etCost.setText(state.cost)
        binding.etNote.setText(state.note)
        binding.etReminder.setText(state.reminder)

        binding.etTitle.doAfterTextChanged { viewModel.onTitleChanged(it?.toString().orEmpty()) }
        binding.etPlace.doAfterTextChanged { viewModel.onPlaceChanged(it?.toString().orEmpty()) }
        binding.etCost.doAfterTextChanged { viewModel.onCostChanged(it?.toString().orEmpty()) }
        binding.etNote.doAfterTextChanged { viewModel.onNoteChanged(it?.toString().orEmpty()) }
        binding.etReminder.doAfterTextChanged { viewModel.onReminderChanged(it?.toString().orEmpty()) }

        val trip = (detailViewModel.gate.value as? TripGate.Ready)?.trip ?: MockData.trip(detailViewModel.tripId)
        val dayLabels = (0 until state.dayCount).map { i ->
            val date = trip?.dateOfDay(i)
            if (date != null) {
                getString(R.string.itinerary_day_option, i + 1, date.format(Format.dayMonth))
            } else {
                getString(R.string.itinerary_day_simple, i + 1)
            }
        }
        val dayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, dayLabels)
        binding.actvDay.setAdapter(dayAdapter)
        binding.actvDay.setOnItemClickListener { _, _, position, _ ->
            viewModel.onDayChanged(position)
        }
        binding.actvDay.setText(dayLabels.getOrNull(state.dayIndex).orEmpty(), false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG_TIME_PICKER = "activity_time_picker"
    }
}

