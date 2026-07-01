package com.genesis.formio.ui.form

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.genesis.formio.databinding.FragmentFormPageBinding
import com.genesis.formio.engine.FormEngine
import com.genesis.formio.ui.widgets.FormioLoadingOverlay
import com.genesis.formio.ui.widgets.FormioToast
import com.genesis.formio.util.FormJsExecutor
import com.genesis.formio.util.MapPickerDialog

class FormPageFragment : Fragment() {

    companion object {
        private const val ARG_PAGE_INDEX = "page_index"

        fun newInstance(pageIndex: Int): FormPageFragment {
            return FormPageFragment().apply {
                arguments = Bundle().apply { putInt(ARG_PAGE_INDEX, pageIndex) }
            }
        }
    }

    private var _binding: FragmentFormPageBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FormWizardViewModel by activityViewModels()
    private lateinit var formEngine: FormEngine
    private var jsExecutor: FormJsExecutor? = null   // created lazily on first custom-button tap
    private lateinit var loadingOverlay: FormioLoadingOverlay
    private var renderedPageId: Int = -1              // hash of last rendered page to avoid double-render

    // ── Location permission for app.mapa() ────────────────────────────────────
    private var pendingLatKey: String = ""
    private var pendingLngKey: String = ""

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openMapPicker(pendingLatKey, pendingLngKey)
        else FormioToast.show(requireContext(), "", getString(com.genesis.formio.R.string.formio_msg_permiso_ubicacion), "warning")
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFormPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadingOverlay = FormioLoadingOverlay(requireActivity())
        // jsExecutor is created on demand — see getOrCreateJsExecutor()

        val pageIndex = arguments?.getInt(ARG_PAGE_INDEX, 0) ?: 0
        viewModel.pages.observe(viewLifecycleOwner) { pages ->
            val page = pages.getOrNull(pageIndex) ?: return@observe
            val pageId = System.identityHashCode(pages) xor pageIndex
            if (pageId == renderedPageId) return@observe   // same data, skip re-render
            renderedPageId = pageId
            renderPage(page)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        renderedPageId = -1   // reset so next view creation can render
        if (::loadingOverlay.isInitialized) loadingOverlay.hide()
        jsExecutor?.destroy()
        jsExecutor = null
        _binding = null
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun renderPage(page: FormWizardViewModel.FormPage) {
        try {
            formEngine = FormEngine(
                context = requireActivity(),
                container = binding.fieldsContainer,
                formData = viewModel.formData,
                onDataChange = { _, _ -> viewModel.reevaluatePageVisibility() },
                onButtonClick = { key, action, custom -> handleButtonClick(key, action, custom) }
            )
            formEngine.render(page.components)
        } catch (e: Exception) {
            android.util.Log.e("FormPage", "renderPage crashed: ${e.message}", e)
        }
    }

    // ── Button dispatch ───────────────────────────────────────────────────────

    private fun handleButtonClick(key: String, action: String?, custom: String?) {

        // Native handler: app.mapa('latKey','lngKey') opens a map dialog
        val mapaMatch = custom?.let {
            Regex("""app\.mapa\(\s*['"](\w+)['"]\s*,\s*['"](\w+)['"]\s*\)""").find(it)
        }
        if (mapaMatch != null) {
            pendingLatKey = mapaMatch.groupValues[1]
            pendingLngKey = mapaMatch.groupValues[2]
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                openMapPicker(pendingLatKey, pendingLngKey)
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            return
        }

        // Native handler: reset clears all data and re-renders the page
        if (action == "reset") {
            viewModel.formData.clear()
            val pageIndex = arguments?.getInt(ARG_PAGE_INDEX, 0) ?: 0
            viewModel.pages.value?.getOrNull(pageIndex)?.let { renderPage(it) }
            return
        }

        // Generic JS execution: runs the `custom` field in a shim WebView
        // Handles app.ws(), form.getComponent().setValue(), app.mostrarMensaje(), moment(), etc.
        if (!custom.isNullOrBlank()) {
            getOrCreateJsExecutor().execute(viewModel.formData, custom)
            return
        }
    }

    // ── JS executor (lazy) ────────────────────────────────────────────────────

    private fun getOrCreateJsExecutor(): FormJsExecutor =
        jsExecutor ?: FormJsExecutor(
            context      = requireContext(),
            onSetField   = { key, value -> updateField(key, value) },
            onGetField   = { key -> viewModel.formData[key]?.toString() ?: "" },
            onShowMessage = { title, msg, type ->
                FormioToast.show(requireContext(), title, msg, type)
            },
            onError = { msg ->
                android.util.Log.e("FormJS", "Button JS error: $msg")
                FormioToast.show(requireContext(), "Error", msg, "danger")
            },
            onWsStart = { loadingOverlay.show() },
            onWsEnd   = { loadingOverlay.hide() }
        ).also { executor ->
            // Attach the hidden WebView to the root so FormEngine.render() doesn't remove it
            executor.attachTo(binding.root as android.view.ViewGroup)
            jsExecutor = executor
        }

    // ── Map picker ────────────────────────────────────────────────────────────

    private fun openMapPicker(latKey: String, lngKey: String) {
        val initLat = viewModel.formData[latKey]?.toString()?.toDoubleOrNull()
        val initLng = viewModel.formData[lngKey]?.toString()?.toDoubleOrNull()

        MapPickerDialog(
            activity = requireActivity(),
            initialLat = initLat,
            initialLng = initLng
        ) { lat, lng ->
            val latStr = "%.6f".format(lat)
            val lngStr = "%.6f".format(lng)
            updateField(latKey, latStr)
            updateField(lngKey, lngStr)
        }.show()
    }

    // ── Shared field updater ──────────────────────────────────────────────────

    private fun updateField(key: String, value: String) {
        viewModel.formData[key] = value
        if (::formEngine.isInitialized) formEngine.externalUpdate(key, value)
    }

    // ── Validation ────────────────────────────────────────────────────────────

    fun validate(): List<String> =
        if (::formEngine.isInitialized) formEngine.validate() else emptyList()

    fun clearErrors() {
        if (::formEngine.isInitialized) formEngine.clearFieldErrors()
    }
}
