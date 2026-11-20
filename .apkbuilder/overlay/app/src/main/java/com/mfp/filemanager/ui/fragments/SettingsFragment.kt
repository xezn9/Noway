package com.mfp.filemanager.ui.fragments

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mfp.filemanager.R
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.databinding.FragmentSettingsBinding
import android.widget.ProgressBar
import android.widget.LinearLayout
import android.widget.TextView

import com.mfp.filemanager.ui.theme.AccentPalette
import com.mfp.filemanager.ui.theme.GlassConfig
import com.mfp.filemanager.ui.viewmodels.SettingsViewModel
import com.mfp.filemanager.ui.viewmodels.SettingsViewModelFactory
import com.mfp.filemanager.ui.viewmodels.UpdateCheckState
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): android.view.animation.Animation? {
        val isMovingRight = com.mfp.filemanager.data.cache.AppCache.getData<Boolean>("isMovingRight") ?: true
        val animRes = if (enter) {
            if (isMovingRight) R.anim.slide_in_right else R.anim.slide_in_left
        } else {
            if (isMovingRight) R.anim.slide_out_left else R.anim.slide_out_right
        }
        return android.view.animation.AnimationUtils.loadAnimation(context, animRes)
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(
            SettingsRepository(requireContext().applicationContext)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        setupObservers()
        setupColorGrid()
        setupGlassSliders()
        
        // Refresh cache size on entry
        viewModel.calculateCacheSize(requireContext())
        
        // Display actual version name
        val versionName = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) { "1.4.0" } ?: "1.4.0"
        binding.textVersion.text = "Version $versionName"
    }


    private fun setupListeners() {
        binding.layoutTheme.setOnClickListener {
            showThemeSelectionDialog()
        }

        binding.switchHiddenFiles.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleShowHiddenFiles(isChecked)
        }

        binding.switchSwipeNav.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleSwipeNavigation(isChecked)
        }
        
        binding.layoutClearCache.setOnClickListener {
             viewModel.clearThumbnailsCache(requireContext())
        }

        binding.layoutTrashRetention.setOnClickListener {
            showTrashRetentionDialog()
        }



        binding.switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleAutoUpdateEnabled(isChecked)
        }

        binding.layoutCheckUpdate.setOnClickListener {
            val versionName = try {
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
            } catch (e: Exception) { "1.4.0" } ?: "1.4.0"
            
            viewModel.checkForUpdates(versionName)
            Snackbar.make(binding.root, getString(R.string.settings_checking_updates), Snackbar.LENGTH_SHORT).show()
        }

        binding.btnChangelog.setOnClickListener {
            val versionName = try {
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
            } catch (e: Exception) { "1.4.0" } ?: "1.4.0"
            
            viewModel.fetchChangelog(versionName)
            Snackbar.make(binding.root, "Fetching changelog...", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.settingsState.collect { state ->
                        // Update Theme Text
                        binding.textCurrentTheme.text = when (state.themeMode) {
                            1 -> getString(R.string.settings_theme_light)
                            2 -> getString(R.string.settings_theme_dark)
                            3 -> getString(R.string.settings_theme_amoled)
                            else -> getString(R.string.settings_theme_system)
                        }
                        
                        if (binding.switchHiddenFiles.isChecked != state.showHiddenFiles) {
                            binding.switchHiddenFiles.isChecked = state.showHiddenFiles
                        }
                        
                        if (binding.switchSwipeNav.isChecked != state.isSwipeNavigationEnabled) {
                             binding.switchSwipeNav.isChecked = state.isSwipeNavigationEnabled
                        }

                        if (binding.switchAutoUpdate.isChecked != state.autoUpdateEnabled) {
                            binding.switchAutoUpdate.isChecked = state.autoUpdateEnabled
                        }
                        
                        // Retention & Speed Texts
                        binding.textTrashDays.text = getString(R.string.settings_trash_retention_days, state.trashRetentionDays)

                        
                        // Cache Size
                        binding.textCacheSize.text = Formatter.formatFileSize(requireContext(), state.currentCacheSize)
                    }
                }
                
                launch {
                    viewModel.updateState.collect { state ->
                        when (state) {
                            is UpdateCheckState.UpdateAvailable -> {
                                showUpdateDialog(state.release)
                            }
                            is UpdateCheckState.UpToDate -> {
                                Snackbar.make(binding.root, getString(R.string.settings_up_to_date), Snackbar.LENGTH_SHORT).show()
                                viewModel.resetUpdateState()
                            }
                            is UpdateCheckState.Downloading -> {
                                showProgressDialog(state.progress)
                            }
                            is UpdateCheckState.DownloadFinished -> {
                                hideProgressDialog()
                                viewModel.resetUpdateState()
                            }
                            is UpdateCheckState.Error -> {
                                hideProgressDialog()
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
                                viewModel.resetUpdateState()
                            }
                            else -> {}
                        }
                    }
                }

                launch {
                    viewModel.changelogState.collect { state ->
                        when(state) {
                            is com.mfp.filemanager.ui.viewmodels.ChangelogState.Loading -> {
                                Snackbar.make(binding.root, "Fetching changelog...", Snackbar.LENGTH_SHORT).show()
                            }
                            is com.mfp.filemanager.ui.viewmodels.ChangelogState.Success -> {
                                showChangelogDialog(state.release)
                                viewModel.resetChangelogState()
                            }
                            is com.mfp.filemanager.ui.viewmodels.ChangelogState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
                                viewModel.resetChangelogState()
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun showChangelogDialog(release: com.mfp.filemanager.data.GitHubRelease) {
        com.mfp.filemanager.ui.compose.LiquidDialogs.info(
            title = "What's New in ${release.tagName}",
            message = release.body ?: "No release notes available."
        )
    }

    private fun showTrashRetentionDialog() {
        val options = listOf("7 Days", "15 Days", "30 Days", "60 Days")
        val values = listOf(7, 15, 30, 60)
        com.mfp.filemanager.ui.compose.LiquidDialogs.choices(
            title = "Trash Retention",
            items = options,
            dismissText = getString(R.string.settings_action_later),
            onCancel = { viewModel.resetUpdateState() }
        ) { which -> viewModel.setTrashRetentionDays(values[which]) }
    }

    private var progressDialog: androidx.appcompat.app.AlertDialog? = null
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null

    private fun showProgressDialog(progress: Float) {
        if (progressDialog == null) {
            val context = requireContext()
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 40)
            }

            progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = false
                max = 100
            }
            
            progressText = TextView(context).apply {
                text = "0%"
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setPadding(0, 20, 0, 0)
            }

            layout.addView(progressBar)
            layout.addView(progressText)

            progressDialog = MaterialAlertDialogBuilder(context)
                .setTitle("Downloading Update")
                .setView(layout)
                .setCancelable(false)
                .create()
            progressDialog?.show()
        }
        
        val percent = (progress * 100).toInt()
        progressBar?.progress = percent
        progressText?.text = "$percent%"
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
        progressBar = null
        progressText = null
    }



    private fun showUpdateDialog(release: com.mfp.filemanager.data.GitHubRelease) {
        com.mfp.filemanager.ui.compose.LiquidDialogs.confirm(
            title = "Version ${release.tagName}",
            message = release.releaseDescription ?: release.body ?: "No release notes available.",
            confirmText = "Download",
            dismissText = "Later",
            onCancel = { viewModel.resetUpdateState() }
        ) { viewModel.downloadUpdate(release, requireContext()) }
    }

    private fun showThemeSelectionDialog() {
        val themes = listOf(
            getString(R.string.settings_theme_system),
            getString(R.string.settings_theme_light),
            getString(R.string.settings_theme_dark),
            getString(R.string.settings_theme_amoled)
        )
        val values = listOf(0, 1, 2, 3)
        
        com.mfp.filemanager.ui.compose.LiquidDialogs.choices(title = "Select Theme", items = themes) { which ->
                val newMode = values[which]
                
                // Use lifecycleScope to ensure the write is finished before recreating
                lifecycleScope.launch {
                    viewModel.setThemeMode(newMode)
                    
                    // For immediate effect:
                    val appCompatMode = when (newMode) {
                        1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                        2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                        3 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                        else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                    
                    val oldNightMode = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode()
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(appCompatMode)
                    
                    // If the night mode value itself didn't change (e.g. FOLLOW_SYSTEM to YES while system is dark),
                    // AppCompat might not recreate the activity. We must force it.
                    // Even if it did change, we wait for DataStore propagation to be safe.
                    kotlinx.coroutines.delay(400) // Slightly longer to be absolutely sure DataStore is ready
                    
                    if (activity?.isFinishing == false && activity?.isDestroyed == false) {
                        activity?.recreate()
                    }
                }
            }
    }

    // ------------------------------------------------------------------ App color

    /** One round swatch per colour of [AccentPalette]; the selected one gets a ring. */
    private fun setupColorGrid() {
        val ctx = requireContext()
        val grid = binding.gridColors
        grid.removeAllViews()

        val density = ctx.resources.displayMetrics.density
        val cell = (46 * density).toInt()
        val margin = (4 * density).toInt()
        val ringWidth = (3 * density).toInt()
        val ringInset = (6 * density).toInt()
        val plainInset = (3 * density).toInt()
        val ringColor = com.google.android.material.color.MaterialColors.getColor(
            ctx, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.GRAY
        )

        val selected = AccentPalette.find(GlassConfig.accentArgb)
        binding.textCurrentColor.text = selected.name

        AccentPalette.swatches.forEach { swatch ->
            val isSelected = swatch.argb == selected.argb

            val fill = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(swatch.argb)
            }
            val drawable = if (isSelected) {
                val ring = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.TRANSPARENT)
                    setStroke(ringWidth, ringColor)
                }
                android.graphics.drawable.LayerDrawable(arrayOf(ring, fill)).apply {
                    setLayerInset(1, ringInset, ringInset, ringInset, ringInset)
                }
            } else {
                android.graphics.drawable.LayerDrawable(arrayOf(fill)).apply {
                    setLayerInset(0, plainInset, plainInset, plainInset, plainInset)
                }
            }

            val v = View(ctx).apply {
                background = drawable
                contentDescription = swatch.name
                isClickable = true
                isFocusable = true
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = cell
                    height = cell
                    setMargins(margin, margin, margin, margin)
                }
                setOnClickListener { onColorPicked(swatch) }
            }
            grid.addView(v)
        }
    }

    private fun onColorPicked(swatch: AccentPalette.Swatch) {
        if (swatch.argb == GlassConfig.accentArgb) return

        // glass parts (folders, tabs, toggles, selection) change right now ...
        GlassConfig.accentArgb = swatch.argb
        binding.textCurrentColor.text = swatch.name
        setupColorGrid()

        // ... and the classic views get the new Material overlay when the screen is rebuilt
        lifecycleScope.launch {
            viewModel.saveAccentColor(swatch.argb)
            kotlinx.coroutines.delay(250)
            if (activity?.isFinishing == false && activity?.isDestroyed == false) {
                activity?.recreate()
            }
        }
    }

    // ------------------------------------------------------------------ Liquid Glass sliders

    private fun setupGlassSliders() {
        bindGlassSlider(binding.sliderBlur, binding.textBlurValue, GlassConfig.blur,
            onLive = { GlassConfig.blur = it },
            onSave = { viewModel.saveGlassBlur(it) })

        bindGlassSlider(binding.sliderTransparency, binding.textTransparencyValue, GlassConfig.transparency,
            onLive = { GlassConfig.transparency = it },
            onSave = { viewModel.saveGlassTransparency(it) })

        bindGlassSlider(binding.sliderRefraction, binding.textRefractionValue, GlassConfig.refraction,
            onLive = { GlassConfig.refraction = it },
            onSave = { viewModel.saveGlassRefraction(it) })

        binding.btnResetGlass.setOnClickListener {
            GlassConfig.resetGlass()
            binding.sliderBlur.value = 50f
            binding.sliderTransparency.value = 50f
            binding.sliderRefraction.value = 50f
            viewModel.saveGlassBlur(0.5f)
            viewModel.saveGlassTransparency(0.5f)
            viewModel.saveGlassRefraction(0.5f)
        }
    }

    /**
     * The glass changes live while the finger moves ([onLive] only touches GlassConfig, which is
     * cheap); the value is written to DataStore once, when the finger is lifted ([onSave]).
     */
    private fun bindGlassSlider(
        slider: com.google.android.material.slider.Slider,
        label: TextView,
        initial: Float,
        onLive: (Float) -> Unit,
        onSave: (Float) -> Unit
    ) {
        slider.value = Math.round(initial * 100f).toFloat().coerceIn(0f, 100f)
        label.text = "${slider.value.toInt()}%"
        slider.addOnChangeListener { _, value, fromUser ->
            label.text = "${value.toInt()}%"
            if (fromUser) onLive(value / 100f)
        }
        slider.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                onSave(slider.value / 100f)
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
