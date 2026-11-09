package vn.edu.vlu.trippocket.ui.ideas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import vn.edu.vlu.trippocket.R
import vn.edu.vlu.trippocket.databinding.FragmentTripIdeaFormBinding
import vn.edu.vlu.trippocket.domain.TripIdeaProblem
import vn.edu.vlu.trippocket.util.applySystemBarPadding
import vn.edu.vlu.trippocket.util.loadRemote

/**
 * Share (POST) or edit (PUT) a trip idea, full screen (Milestone 4, owner: Tiến). Reports a
 * successful save through [REQUEST_KEY] so the list reloads.
 */
class TripIdeaFormDialogFragment : DialogFragment() {

    private var _binding: FragmentTripIdeaFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TripIdeaFormViewModel by viewModels { TripIdeaFormViewModel.Factory }

    /** True while the fields are filled from the ViewModel, so the watchers do not echo it back. */
    private var filling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_TripPocket)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTripIdeaFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.applySystemBarPadding()

        val editing = arguments?.getString(ARG_IDEA_ID) != null
        binding.tvFormTitle.setText(if (editing) R.string.idea_form_edit_title else R.string.idea_form_new_title)
        binding.btnSave.setText(if (editing) R.string.idea_save_edit else R.string.idea_save_new)
        binding.btnSave.setOnClickListener { viewModel.save() }
        binding.btnCancel.setOnClickListener { dismiss() }

        listOf(binding.etTitle, binding.etDestination, binding.etDays, binding.etBudget, binding.etCover, binding.etSummary)
            .forEach { field -> field.doAfterTextChanged { if (!filling) viewModel.onFieldsChanged(currentFields()) } }
        // Live preview of the cover URL with Glide; Glide cancels the previous request itself.
        binding.etCover.doAfterTextChanged { showCoverPreview(it?.toString()) }
        showCoverPreview(binding.etCover.text?.toString())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: TripIdeaFormUiState) {
        if (state.saved) {
            parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle())
            dismiss()
            return
        }
        if (state.notFound) {
            Snackbar.make(requireActivity().findViewById(android.R.id.content), R.string.idea_not_found, Snackbar.LENGTH_LONG).show()
            // The idea is gone: the list should drop it too.
            parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle())
            dismiss()
            return
        }
        state.fillFields?.let { fields ->
            fill(fields)
            viewModel.fieldsShown()
        }

        binding.pbLoading.isVisible = state.loading || state.saving
        binding.btnSave.isEnabled = !state.loading && !state.saving && state.loadError == null

        // One banner for both failures: the GET of an edit (Retry reloads) or the POST / PUT (Retry saves again).
        val error = state.loadError ?: state.saveError
        binding.networkError.root.isVisible = error != null
        if (error != null) {
            binding.networkError.tvNetworkError.setText(error.messageRes)
            binding.networkError.btnNetworkRetry.setOnClickListener {
                if (state.loadError != null) viewModel.retryLoad() else viewModel.save()
            }
        }

        binding.tilTitle.error = when {
            state.has(TripIdeaProblem.BLANK_TITLE) -> getString(R.string.idea_error_title_empty)
            state.has(TripIdeaProblem.TITLE_TOO_LONG) -> getString(R.string.idea_error_title_too_long)
            else -> null
        }
        binding.tilDestination.error = when {
            state.has(TripIdeaProblem.BLANK_DESTINATION) -> getString(R.string.idea_error_destination_empty)
            state.has(TripIdeaProblem.DESTINATION_TOO_LONG) -> getString(R.string.idea_error_destination_too_long)
            else -> null
        }
        binding.tilDays.error = if (state.has(TripIdeaProblem.BAD_DAYS)) getString(R.string.idea_error_days) else null
        binding.tilBudget.error = if (state.has(TripIdeaProblem.BAD_BUDGET)) getString(R.string.idea_error_budget) else null
        binding.tilCover.error = if (state.has(TripIdeaProblem.BAD_COVER_URL)) getString(R.string.idea_error_cover) else null
        binding.tilSummary.error =
            if (state.has(TripIdeaProblem.SUMMARY_TOO_LONG)) getString(R.string.idea_error_summary_too_long) else null
    }

    private fun fill(fields: TripIdeaFields) {
        filling = true
        binding.etTitle.setText(fields.title)
        binding.etDestination.setText(fields.destination)
        binding.etDays.setText(fields.days)
        binding.etBudget.setText(fields.budget)
        binding.etCover.setText(fields.coverUrl)
        binding.etSummary.setText(fields.summary)
        filling = false
        viewModel.onFieldsChanged(currentFields())
    }

    private fun currentFields() = TripIdeaFields(
        title = binding.etTitle.text?.toString().orEmpty(),
        destination = binding.etDestination.text?.toString().orEmpty(),
        days = binding.etDays.text?.toString().orEmpty(),
        budget = binding.etBudget.text?.toString().orEmpty(),
        coverUrl = binding.etCover.text?.toString().orEmpty(),
        summary = binding.etSummary.text?.toString().orEmpty(),
    )

    private fun showCoverPreview(url: String?) {
        val https = url?.trim()?.takeIf { it.startsWith("https://") }
        binding.ivCoverPreview.loadRemote(https, placeholder = R.drawable.ic_image, error = R.drawable.ic_broken_image)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TripIdeaFormDialogFragment"
        const val REQUEST_KEY = "request_trip_idea_form"

        /** [ideaId] null = share a new idea. */
        fun show(fragmentManager: FragmentManager, ideaId: String? = null) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            TripIdeaFormDialogFragment().apply {
                arguments = Bundle().apply { if (ideaId != null) putString(ARG_IDEA_ID, ideaId) }
            }.show(fragmentManager, TAG)
        }
    }
}
