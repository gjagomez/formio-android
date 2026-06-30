package com.genesis.formio.ui.form

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.genesis.formio.ui.selfie.SelfieCaptureActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.genesis.formio.R
import com.genesis.formio.databinding.ActivityFormWizardBinding
import com.genesis.formio.ui.widgets.FormioLoadingOverlay
import com.genesis.formio.util.CameraHelper
import com.genesis.formio.util.PhotoStorage
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FormWizardActivity : AppCompatActivity(), PhotoCaptureLauncher {

    companion object {
        const val EXTRA_SCHEMA_JSON     = "formio_schema_json"
        const val EXTRA_SCHEMA_PATH     = "formio_schema_path"
        const val EXTRA_PREFILL_JSON    = "formio_prefill_json"
        const val EXTRA_TITLE           = "formio_title"
        const val EXTRA_RESULT_DATA     = "formio_result_data"
        const val EXTRA_RESULT_IS_FINAL = "formio_result_is_final"
    }

    private lateinit var binding: ActivityFormWizardBinding
    private val viewModel: FormWizardViewModel by viewModels()
    private lateinit var pagerAdapter: FormPagerAdapter
    private val gson = Gson()
    private var formLoadingOverlay: FormioLoadingOverlay? = null
    private var pagerSetupDone = false

    private var cameraPhotoCallback: ((String) -> Unit)? = null
    private var galleryPhotoCallback: ((String) -> Unit)? = null
    private var scannerPhotoCallback: ((String) -> Unit)? = null
    private var selfiePhotoCallback: ((String) -> Unit)? = null
    private var tempCameraUri: Uri? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) doLaunchCamera()
        else com.genesis.formio.ui.widgets.FormioToast.show(this, "", getString(R.string.formio_msg_permiso_camara), "warning")
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val uri = tempCameraUri ?: return@registerForActivityResult
            lifecycleScope.launch(Dispatchers.IO) {
                val path = PhotoStorage.saveFromUri(this@FormWizardActivity, uri)
                withContext(Dispatchers.Main) { cameraPhotoCallback?.invoke(path) }
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val path = PhotoStorage.saveFromUri(this@FormWizardActivity, uri)
            withContext(Dispatchers.Main) { galleryPhotoCallback?.invoke(path) }
        }
    }

    private val documentScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.firstOrNull()?.imageUri ?: return@registerForActivityResult
            lifecycleScope.launch(Dispatchers.IO) {
                val path = PhotoStorage.saveFromUri(this@FormWizardActivity, imageUri)
                withContext(Dispatchers.Main) { scannerPhotoCallback?.invoke(path) }
            }
        }
    }

    private val selfieLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra(SelfieCaptureActivity.EXTRA_PHOTO_PATH) ?: return@registerForActivityResult
            selfiePhotoCallback?.invoke(path)
            selfiePhotoCallback = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFormWizardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show loading overlay immediately — the form parse + render will complete async
        formLoadingOverlay = FormioLoadingOverlay(this)
        binding.root.post { formLoadingOverlay?.show() }

        val schemaJson: String = run {
            val path = intent.getStringExtra(EXTRA_SCHEMA_PATH)
            if (path != null) {
                val f = java.io.File(path)
                val text = try { f.readText() } catch (_: Exception) { finish(); return }
                f.delete()
                text
            } else {
                intent.getStringExtra(EXTRA_SCHEMA_JSON) ?: run { finish(); return }
            }
        }
        val title      = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val prefillJson = intent.getStringExtra(EXTRA_PREFILL_JSON)
        val prefillData: Map<String, Any?> = prefillJson?.let {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            gson.fromJson(it, type) ?: emptyMap()
        } ?: emptyMap()

        binding.tvFormTitle.text = title
        binding.btnBack.setOnClickListener { setResult(RESULT_CANCELED); finish() }

        binding.btnSave.setOnClickListener {
            val allPages = viewModel.pages.value ?: emptyList()
            val hidden = viewModel.hiddenPageIndices.value ?: emptySet()
            val validationEngine = com.genesis.formio.engine.ValidationEngine(this@FormWizardActivity)
            val allErrors = mutableListOf<String>()
            for ((index, page) in allPages.withIndex()) {
                if (index in hidden) continue
                allErrors.addAll(validationEngine.validateRecursive(page.components, viewModel.formData))
            }
            if (allErrors.isNotEmpty()) {
                showValidationErrors(allErrors)
                return@setOnClickListener
            }
            returnResult(isFinal = false)
        }

        viewModel.loadFromJson(schemaJson, prefillData)

        viewModel.displayType.observe(this) { display ->
            val isWizard = display == "wizard"
            binding.tabsScrollView.visibility = if (isWizard) View.VISIBLE else View.GONE
            binding.progressBar.visibility = if (isWizard) View.VISIBLE else View.GONE
            if (!isWizard) {
                binding.btnPrev.visibility = View.GONE
                binding.btnNext.text = getString(R.string.formio_btn_guardar)
            }
        }

        viewModel.pages.observe(this) { pages ->
            if (pagerSetupDone) return@observe   // ignore re-fires on foreground resume
            pagerSetupDone = true
            val isWizard = viewModel.displayType.value == "wizard"
            try {
                // Render while the overlay is still covering — then fade it out to reveal the ready form
                setupPager(pages)
                if (isWizard) setupTabs(pages)
            } catch (_: Exception) {}
            formLoadingOverlay?.hide()
            formLoadingOverlay = null
        }

        viewModel.hiddenPageIndices.observe(this) { hidden ->
            applyPageHiding(hidden)
        }

        viewModel.debugInfo.observe(this) { msg ->
            if (!msg.isNullOrBlank()) android.util.Log.d("FormWizard", msg)
        }
    }

    private fun returnResult(isFinal: Boolean) {
        val data = Intent().apply {
            putExtra(EXTRA_RESULT_DATA, viewModel.getFormDataJson())
            putExtra(EXTRA_RESULT_IS_FINAL, isFinal)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun setupPager(pages: List<FormWizardViewModel.FormPage>) {
        pagerAdapter = FormPagerAdapter(this, pages.size)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.currentPageIndex.value = position
                updateTabSelection(position)
                updateProgress(position, pages.size)
                updateButtons(position, pages.size)
            }
        })

        binding.btnNext.setOnClickListener {
            val currentIndex = binding.viewPager.currentItem
            val hidden = viewModel.hiddenPageIndices.value ?: emptySet()
            var next = currentIndex + 1
            while (next < pages.size && next in hidden) next++
            val isLastPage = next >= pages.size
            val validationEngine = com.genesis.formio.engine.ValidationEngine(this@FormWizardActivity)

            if (isLastPage) {
                // Validar TODOS los tabs antes de guardar
                val allErrors = mutableListOf<String>()
                for ((index, page) in pages.withIndex()) {
                    if (index in hidden) continue
                    allErrors.addAll(validationEngine.validateRecursive(page.components, viewModel.formData))
                }
                if (allErrors.isNotEmpty()) {
                    showValidationErrors(allErrors)
                    return@setOnClickListener
                }
                returnResult(isFinal = true)
            } else {
                // Validar solo la página actual antes de avanzar
                val fragment = fragmentAt(currentIndex)
                var errors = fragment?.validate() ?: emptyList()
                if (errors.isEmpty()) {
                    val pageComponents = pages.getOrNull(currentIndex)?.components ?: emptyList()
                    errors = validationEngine.validateRecursive(pageComponents, viewModel.formData)
                }
                if (errors.isNotEmpty()) {
                    showValidationErrors(errors)
                    return@setOnClickListener
                }
                binding.viewPager.currentItem = next
            }
        }

        binding.btnPrev.setOnClickListener {
            val currentIndex = binding.viewPager.currentItem
            val hidden = viewModel.hiddenPageIndices.value ?: emptySet()
            var prev = currentIndex - 1
            while (prev >= 0 && prev in hidden) prev--
            if (prev >= 0) {
                fragmentAt(currentIndex)?.clearErrors()
                binding.viewPager.currentItem = prev
            }
        }
    }

    private fun fragmentAt(position: Int): FormPageFragment? {
        // Primary: adapter's own registry, populated by createFragment()
        val fromAdapter = if (::pagerAdapter.isInitialized) pagerAdapter.getFragment(position) as? FormPageFragment else null
        if (fromAdapter != null) return fromAdapter
        // Fallback: search live fragments by page_index argument (handles config changes)
        return supportFragmentManager.fragments
            .filterIsInstance<FormPageFragment>()
            .firstOrNull { it.arguments?.getInt("page_index", -1) == position }
    }

    private fun setupTabs(pages: List<FormWizardViewModel.FormPage>) {
        binding.tabsContainer.removeAllViews()
        pages.forEachIndexed { index, page ->
            val tab = LayoutInflater.from(this)
                .inflate(R.layout.item_form_tab, binding.tabsContainer, false) as TextView
            tab.text = page.title
            tab.tag = index
            tab.setOnClickListener {
                val targetIndex = index
                val currentIndex = binding.viewPager.currentItem
                if (targetIndex == currentIndex) return@setOnClickListener
                fragmentAt(currentIndex)?.clearErrors()
                binding.viewPager.currentItem = targetIndex
            }
            binding.tabsContainer.addView(tab)
        }
        updateTabSelection(0)
        updateProgress(0, pages.size)
        updateButtons(0, pages.size)
    }

    private fun applyPageHiding(hidden: Set<Int>) {
        for (i in 0 until binding.tabsContainer.childCount) {
            binding.tabsContainer.getChildAt(i)?.visibility =
                if (i in hidden) View.GONE else View.VISIBLE
        }
        val current = binding.viewPager.currentItem
        val total = viewModel.pages.value?.size ?: 0
        updateButtons(current, total)
        // If current page became hidden, jump to first visible page
        if (current in hidden) {
            val first = (0 until total).firstOrNull { it !in hidden } ?: return
            binding.viewPager.currentItem = first
        }
    }

    private fun updateTabSelection(selectedIndex: Int) {
        val dp = resources.displayMetrics.density
        for (i in 0 until binding.tabsContainer.childCount) {
            val tab = binding.tabsContainer.getChildAt(i) as? TextView ?: continue
            if (tab.tag == selectedIndex) {
                tab.setBackgroundResource(R.drawable.bg_tab_active)
                tab.setTextColor(getColor(R.color.text_on_accent))
                tab.setTypeface(null, android.graphics.Typeface.BOLD)
                tab.elevation = 6f * dp
                tab.alpha = 1f
            } else {
                tab.setBackgroundResource(R.drawable.bg_tab_inactive)
                tab.setTextColor(getColor(R.color.text_secondary))
                tab.setTypeface(null, android.graphics.Typeface.NORMAL)
                tab.elevation = 0f
                tab.alpha = 0.7f
            }
        }
        binding.tabsContainer.post {
            val tab = binding.tabsContainer.getChildAt(selectedIndex) ?: return@post
            val scrollX = tab.left - binding.tabsScrollView.width / 2 + tab.width / 2
            binding.tabsScrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }

    private fun updateProgress(current: Int, total: Int) {
        binding.progressBar.max = total
        binding.progressBar.progress = current + 1
    }

    private fun updateButtons(current: Int, total: Int) {
        val hidden = viewModel.hiddenPageIndices.value ?: emptySet()
        val hasPrev = (0 until current).any { it !in hidden }
        val hasNext = (current + 1 until total).any { it !in hidden }
        binding.btnPrev.visibility = if (hasPrev) View.VISIBLE else View.GONE
        binding.btnNext.text = if (!hasNext) getString(R.string.formio_btn_guardar)
        else getString(R.string.formio_btn_siguiente)
    }

    override fun launchCamera(fieldKey: String, onResult: (String) -> Unit) {
        cameraPhotoCallback = onResult
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) doLaunchCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun doLaunchCamera() {
        try {
            tempCameraUri = CameraHelper.createTempImageUri(this)
            cameraLauncher.launch(tempCameraUri!!)
        } catch (_: Exception) {
            com.genesis.formio.ui.widgets.FormioToast.show(this, "", getString(R.string.formio_msg_sin_camara), "danger")
            cameraPhotoCallback = null
        }
    }

    override fun launchGallery(fieldKey: String, onResult: (String) -> Unit) {
        galleryPhotoCallback = onResult
        galleryLauncher.launch("image/*")
    }

    override fun launchSelfieCamera(fieldKey: String, onResult: (String) -> Unit) {
        selfiePhotoCallback = onResult
        val intent = Intent(this, SelfieCaptureActivity::class.java)
        selfieLauncher.launch(intent)
    }

    override fun launchDocumentScanner(fieldKey: String, onResult: (String) -> Unit) {
        android.util.Log.d("FormioScan", "launchDocumentScanner called for field=$fieldKey")
        scannerPhotoCallback = onResult
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                documentScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FormioScan", "Scanner failed: ${e.javaClass.simpleName}: ${e.message}", e)
                com.genesis.formio.ui.widgets.FormioToast.show(this, "", "Error escáner: ${e.message}", "danger")
                scannerPhotoCallback = null
            }
    }

    private fun showValidationErrors(errors: List<String>) {
        val dp = resources.displayMetrics.density
        val errorColor = getColor(R.color.error_color)
        val sheet = BottomSheetDialog(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(getColor(R.color.bg_card))
                cornerRadii = floatArrayOf(24*dp, 24*dp, 24*dp, 24*dp, 0f, 0f, 0f, 0f)
            }
            setPadding(0, 0, 0, (28 * dp).toInt())
        }

        val handleRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * dp).toInt(); bottomMargin = (8 * dp).toInt() }
        }
        handleRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(getColor(R.color.bg_elevated)); cornerRadius = 4 * dp
            }
        })
        root.addView(handleRow)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((24 * dp).toInt(), (12 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt())
        }

        val iconCircleSize = (64 * dp).toInt()
        val iconFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconCircleSize, iconCircleSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = (16 * dp).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(30, Color.red(errorColor), Color.green(errorColor), Color.blue(errorColor)))
            }
        }
        iconFrame.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_error); setColorFilter(errorColor)
            val p = (14 * dp).toInt(); setPadding(p, p, p, p)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        })
        header.addView(iconFrame)

        header.addView(TextView(this).apply {
            text = getString(R.string.formio_campos_incompletos)
            setTextColor(getColor(R.color.text_primary)); textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (6 * dp).toInt() }
        })
        header.addView(TextView(this).apply {
            text = if (errors.size == 1) getString(R.string.formio_hay_campo_atencion)
                   else getString(R.string.formio_hay_campos_atencion, errors.size)
            setTextColor(getColor(R.color.text_secondary)); textSize = 13f; gravity = Gravity.CENTER
        })
        root.addView(header)

        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).apply { marginStart = (24*dp).toInt(); marginEnd = (24*dp).toInt(); bottomMargin = (16*dp).toInt() }
            setBackgroundColor(getColor(R.color.stroke))
        })

        val maxListHeight = (resources.displayMetrics.heightPixels * 0.35f).toInt()
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, maxListHeight
            ).apply { marginStart = (24*dp).toInt(); marginEnd = (24*dp).toInt(); bottomMargin = (20*dp).toInt() }
            isVerticalScrollBarEnabled = false
        }
        val errorList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        errors.forEachIndexed { index, error ->
            val colonIdx = error.indexOf(": ")
            val fieldLabel = if (colonIdx > 0) error.substring(0, colonIdx) else error
            val errorMsg   = if (colonIdx > 0) error.substring(colonIdx + 2) else ""

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8 * dp).toInt() }
                background = GradientDrawable().apply { setColor(getColor(R.color.bg_input)); cornerRadius = 10 * dp }
                clipToOutline = true
            }
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams((3 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
                background = GradientDrawable().apply {
                    setColor(errorColor); cornerRadii = floatArrayOf(10*dp, 0f, 10*dp, 0f, 0f, 0f, 0f, 0f)
                }
            })
            val badgeSize = (26 * dp).toInt()
            val badge = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                    gravity = Gravity.CENTER_VERTICAL; marginStart = (12*dp).toInt(); marginEnd = (10*dp).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(40, Color.red(errorColor), Color.green(errorColor), Color.blue(errorColor)))
                }
            }
            badge.addView(TextView(this).apply {
                text = "${index + 1}"; setTextColor(errorColor); textSize = 10f
                setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                )
            })
            row.addView(badge)

            val textBlock = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    topMargin = (12*dp).toInt(); bottomMargin = (12*dp).toInt(); marginEnd = (12*dp).toInt()
                }
            }
            textBlock.addView(TextView(this).apply {
                text = fieldLabel; setTextColor(getColor(R.color.text_primary)); textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            if (errorMsg.isNotBlank()) {
                textBlock.addView(TextView(this).apply {
                    text = errorMsg; setTextColor(errorColor); textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = (2 * dp).toInt() }
                })
            }
            row.addView(textBlock)
            errorList.addView(row)
        }

        scrollView.addView(errorList)
        root.addView(scrollView)

        val btnContainer = LinearLayout(this).apply {
            setPadding((24 * dp).toInt(), 0, (24 * dp).toInt(), 0)
        }
        btnContainer.addView(com.google.android.material.button.MaterialButton(this).apply {
            text = getString(R.string.formio_revisar_campos)
            setTextColor(errorColor); setBackgroundColor(Color.TRANSPARENT)
            strokeColor = android.content.res.ColorStateList.valueOf(errorColor)
            strokeWidth = (1 * dp).toInt(); cornerRadius = (10 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (48 * dp).toInt()
            )
            setOnClickListener { sheet.dismiss() }
        })
        root.addView(btnContainer)

        sheet.setContentView(root)
        sheet.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        (sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet))
            ?.setBackgroundColor(Color.TRANSPARENT)
        sheet.show()
    }
}
