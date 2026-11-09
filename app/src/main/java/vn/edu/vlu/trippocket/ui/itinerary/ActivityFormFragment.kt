package vn.edu.vlu.trippocket.ui.itinerary

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
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
import vn.edu.vlu.trippocket.databinding.FragmentActivityFormBinding
import vn.edu.vlu.trippocket.domain.ActivityProblem
import vn.edu.vlu.trippocket.reminder.ReminderPermissions
import vn.edu.vlu.trippocket.reminder.ReminderReceiver
import vn.edu.vlu.trippocket.reminder.ReminderStatus
import vn.edu.vlu.trippocket.ui.detail.TripDetailViewModel
import vn.edu.vlu.trippocket.ui.detail.TripGate
import vn.edu.vlu.trippocket.ui.detail.tripViewModels
import vn.edu.vlu.trippocket.util.Format
import java.time.LocalTime

/**
 * Screen to add or edit an itinerary activity (I1). With a reminder set, the reminder field says
 * when the phone will not show it on time (I4): notifications off, or only inexact alarms.
 */
class ActivityFormFragment : Fragment() {

    private var _binding: FragmentActivityFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ItineraryFormViewModel by tripViewModels { ItineraryFormViewModel.Factory }
    private val detailViewModel: TripDetailViewModel by activityViewModels { TripDetailViewModel.Factory }

    private var fieldsFilled = false

    /**
     * I4: asked once per form when a reminder is set (typed in, or already there when editing),
     * before the user saves, so the answer shows under the reminder field. The activity is saved
     * whatever the answer.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Denied with no rationale to show: Android will not ask again, only Settings can.
            deniedForGood = !granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            _binding?.let { renderReminderStatus(viewModel.uiState.value.reminder) }
        }

    /** The permission was already asked in this form; kept across rotation. */
    private var permissionAsked = false
    private var deniedForGood = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionAsked = savedInstanceState?.getBoolean(KEY_PERMISSION_ASKED) ?: false
        deniedForGood = savedInstanceState?.getBoolean(KEY_DENIED_FOR_GOOD) ?: false
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
        binding.btnReminderPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canAskNotificationPermission()) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings()
            }
        }
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

    override fun onResume() {
        super.onResume()
        // The user may have changed the permissions in Settings while away.
        _binding?.let { renderReminderStatus(viewModel.uiState.value.reminder) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_PERMISSION_ASKED, permissionAsked)
        outState.putBoolean(KEY_DENIED_FOR_GOOD, deniedForGood)
    }

    /** true on Android 13+ while the permission is missing and Android can still show its dialog. */
    private fun canAskNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ReminderPermissions.needsNotificationPermission(requireContext()) &&
            !deniedForGood

    /** Asks for the permission the first time this form has a reminder. */
    private fun askOnceForReminder(reminder: String) {
        if (reminder.isBlank() || permissionAsked || !canAskNotificationPermission()) return
        permissionAsked = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** The app notification settings, or the reminder channel when only that one is off. */
    private fun openNotificationSettings() {
        val context = requireContext()
        val intent = if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderReceiver.CHANNEL_ID)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        }
        startActivity(intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }

    /**
     * Helper text under the reminder field: the default hint, or why it may not come on time.
     * While reminders are blocked, a button asks for the permission or opens Settings.
     */
    private fun renderReminderStatus(reminder: String) {
        val status = if (reminder.isBlank()) ReminderStatus.ON_TIME else ReminderPermissions.status(requireContext())
        binding.tilReminder.helperText = getString(
            when (status) {
                ReminderStatus.ON_TIME -> R.string.itinerary_reminder_helper
                ReminderStatus.MAY_BE_LATE -> R.string.reminder_status_may_be_late
                ReminderStatus.BLOCKED -> R.string.reminder_status_blocked
            }
        )
        binding.btnReminderPermission.isVisible = status == ReminderStatus.BLOCKED
        binding.btnReminderPermission.setText(
            if (canAskNotificationPermission()) R.string.reminder_allow_notifications
            else R.string.reminder_open_settings
        )
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
        askOnceForReminder(state.reminder)
        renderReminderStatus(state.reminder)

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

        val trip = (detailViewModel.gate.value as? TripGate.Ready)?.trip
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
        private const val KEY_PERMISSION_ASKED = "reminder_permission_asked"
        private const val KEY_DENIED_FOR_GOOD = "reminder_permission_denied_for_good"
    }
}

