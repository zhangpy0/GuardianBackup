package top.zhangpy.guardianbackup.ui

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import top.zhangpy.guardianbackup.R
import top.zhangpy.guardianbackup.core.domain.model.FileFilter

class FilterDialogFragment(
        private var initialFilter: FileFilter = FileFilter.NO_FILTER,
        private val onFilterApplied: (FileFilter) -> Unit
) : DialogFragment() {

    private lateinit var cbImages: CheckBox
    private lateinit var cbVideos: CheckBox
    private lateinit var cbAudio: CheckBox
    private lateinit var cbDocuments: CheckBox
    private lateinit var etNamePattern: EditText
    private lateinit var etMinSize: EditText
    private lateinit var etMaxSize: EditText
    private lateinit var spinnerSizeUnit: Spinner

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_filter, null)

        bindViews(view)
        setupInitialState()

        builder.setView(view)
                .setTitle("Filter Files")
                .setPositiveButton("Apply") { _, _ ->
                    val filter = createFilterFromInputs()
                    onFilterApplied(filter)
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
                .setNeutralButton("Reset") { _, _ -> onFilterApplied(FileFilter.NO_FILTER) }

        return builder.create()
    }

    private fun bindViews(view: View) {
        cbImages = view.findViewById(R.id.cbImages)
        cbVideos = view.findViewById(R.id.cbVideos)
        cbAudio = view.findViewById(R.id.cbAudio)
        cbDocuments = view.findViewById(R.id.cbDocuments)
        etNamePattern = view.findViewById(R.id.etNamePattern)
        etMinSize = view.findViewById(R.id.etMinSize)
        etMaxSize = view.findViewById(R.id.etMaxSize)
        spinnerSizeUnit = view.findViewById(R.id.spinnerSizeUnit)

        // Setup Spinner
        val units = arrayOf("KB", "MB", "GB")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, units)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSizeUnit.adapter = adapter
    }

    private fun setupInitialState() {
        // Pre-check boxes based on extensions
        val exts = initialFilter.extensions
        if (exts.containsAll(FileFilter.IMAGES_ONLY.extensions)) cbImages.isChecked = true
        if (exts.containsAll(FileFilter.VIDEOS_ONLY.extensions)) cbVideos.isChecked = true
        if (exts.containsAll(FileFilter.AUDIO_ONLY.extensions)) cbAudio.isChecked = true
        if (exts.containsAll(FileFilter.DOCUMENTS_ONLY.extensions)) cbDocuments.isChecked = true

        // Name pattern
        initialFilter.namePattern?.pattern?.let { etNamePattern.setText(it) }

        // Size (simplified display, assuming MB roughly for now or just raw inputs if we were more
        // detailed)
        // For simplicity in this demo, strictly clearing inputs if no filter or just showing empty.
        // A full implementation would reverse-calculate the size.
    }

    private fun createFilterFrom_inputs():
            FileFilter { // Helper function renamed for clarity in logic below
        return createFilterFromInputs()
    }

    private fun createFilterFromInputs(): FileFilter {
        val selectedExtensions = mutableListOf<String>()
        if (cbImages.isChecked) selectedExtensions.addAll(FileFilter.IMAGES_ONLY.extensions)
        if (cbVideos.isChecked) selectedExtensions.addAll(FileFilter.VIDEOS_ONLY.extensions)
        if (cbAudio.isChecked) selectedExtensions.addAll(FileFilter.AUDIO_ONLY.extensions)
        if (cbDocuments.isChecked) selectedExtensions.addAll(FileFilter.DOCUMENTS_ONLY.extensions)

        val namePatternStr = etNamePattern.text.toString()
        val nameRegex = if (namePatternStr.isNotEmpty()) Regex(namePatternStr) else null

        // Parse Size
        val unitMultiplier =
                when (spinnerSizeUnit.selectedItemPosition) {
                    0 -> 1024L // KB
                    1 -> 1024L * 1024L // MB
                    2 -> 1024L * 1024L * 1024L // GB
                    else -> 1L
                }

        val minSize = etMinSize.text.toString().toLongOrNull()?.times(unitMultiplier)
        val maxSize = etMaxSize.text.toString().toLongOrNull()?.times(unitMultiplier)

        return FileFilter(
                extensions = selectedExtensions.distinct(),
                namePattern = nameRegex,
                minSize = minSize,
                maxSize = maxSize
        )
    }
}
