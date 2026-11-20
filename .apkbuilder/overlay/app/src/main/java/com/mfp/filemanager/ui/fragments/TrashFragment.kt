package com.mfp.filemanager.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.mfp.filemanager.data.FileRepository
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.databinding.FragmentTrashBinding
import com.mfp.filemanager.ui.viewmodels.HomeViewModel
import com.mfp.filemanager.ui.viewmodels.HomeViewModelFactory
import kotlinx.coroutines.launch
import com.mfp.filemanager.data.trash.TrashedFile
import com.mfp.filemanager.ui.adapters.TrashAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.navigation.fragment.findNavController
import com.mfp.filemanager.R

class TrashFragment : Fragment() {

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): android.view.animation.Animation? {
        val isMovingRight = com.mfp.filemanager.data.cache.AppCache.getData<Boolean>("isMovingRight") ?: true
        val animRes = if (enter) {
            if (isMovingRight) R.anim.slide_in_right else R.anim.slide_in_left
        } else {
            if (isMovingRight) R.anim.slide_out_left else R.anim.slide_out_right
        }
        return android.view.animation.AnimationUtils.loadAnimation(context, animRes)
    }

    private var _binding: FragmentTrashBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(
            FileRepository(requireContext().applicationContext),
            SettingsRepository(requireContext().applicationContext)
        )
    }

    private lateinit var trashAdapter: TrashAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupObservers()
        setupSwipeRefresh()
        
        // Initial Load
        viewModel.loadTrashedFiles()
        
        binding.btnRestoreAll.setOnClickListener {
            if (viewModel.trashedFiles.value.isNotEmpty()) {
                com.mfp.filemanager.ui.compose.LiquidDialogs.confirm(
                    title = "Restore All Files?",
                    message = "All items currently in the Bin will be moved back to their original folders. If the original folder no longer exists, it will be recreated.",
                    confirmText = "Restore All"
                ) { viewModel.restoreAllFiles() }
            }
        }

        binding.btnEmptyTrash.setOnClickListener {
             if (viewModel.trashedFiles.value.isNotEmpty()) {
                com.mfp.filemanager.ui.compose.LiquidDialogs.confirm(
                    title = "Empty Bin?",
                    message = "This will permanently remove all items from the Bin. You will not be able to recover these files later. This action cannot be undone.",
                    confirmText = "Empty Now",
                    destructive = true
                ) { viewModel.emptyTrash() }
            }
        }
        
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadTrashedFiles()
            viewLifecycleOwner.lifecycleScope.launch {
                kotlinx.coroutines.delay(1000)
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun setupRecyclerView() {
        trashAdapter = TrashAdapter(
            onItemClick = { file ->
                if (viewModel.isTrashSelectionMode.value) {
                    viewModel.toggleTrashSelection(file.id)
                } else {
                    showFileOptions(file)
                }
            },
            onLongClick = { file ->
                viewModel.toggleTrashSelection(file.id)
            },
            onMenuClick = { /* No-op, menu is hidden */ }
        )
        binding.recyclerTrash.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = trashAdapter
        }
    }

    private fun showFileOptions(file: TrashedFile) {
        val options = listOf("Restore", "Delete Permanently")
        com.mfp.filemanager.ui.compose.LiquidDialogs.choices(title = file.name, items = options) { which ->
            when (options[which]) {
                "Restore" -> com.mfp.filemanager.ui.compose.LiquidDialogs.confirm(
                    title = "Restore Item?",
                    message = "This item will be moved back to its original location. Do you want to proceed?",
                    confirmText = "Restore"
                ) { viewModel.restoreFiles(listOf(file)) }
                "Delete Permanently" -> com.mfp.filemanager.ui.compose.LiquidDialogs.confirm(
                    title = "Delete Permanently?",
                    message = "This item will be permanently removed from your device and cannot be recovered. Are you sure you want to delete it?",
                    confirmText = "Delete",
                    destructive = true
                ) { viewModel.deleteFilesPermanently(listOf(file)) }
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.trashedFiles.collect { files ->
                        binding.textEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
                        trashAdapter.submitList(files)
                        
                        binding.btnRestoreAll.isEnabled = files.isNotEmpty()
                        binding.btnEmptyTrash.isEnabled = files.isNotEmpty()
                    }
                }
                launch {
                    viewModel.isTrashSelectionMode.collect { isSelectionMode ->
                        updateToolbarForSelection(isSelectionMode)
                    }
                }
                launch {
                    viewModel.selectedTrashFiles.collect { selected ->
                        if (viewModel.isTrashSelectionMode.value) {
                            binding.toolbar.title = "${selected.size} selected"
                        }
                    }
                }
            }
        }
    }

    private fun updateToolbarForSelection(isSelectionMode: Boolean) {
        if (isSelectionMode) {
            binding.toolbar.menu.clear()
            binding.toolbar.inflateMenu(R.menu.menu_trash_selection)
            binding.toolbar.setNavigationIcon(R.drawable.ic_close_24)
            binding.toolbar.setNavigationOnClickListener {
                viewModel.exitTrashSelectionMode()
            }
            binding.toolbar.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_restore -> {
                        val count = viewModel.selectedTrashFiles.value.size
                        com.mfp.filemanager.ui.compose.LiquidDialogs.confirm(
                            title = "Restore $count Items?",
                            message = "The selected items will be moved back to their original locations. Do you want to proceed?",
                            confirmText = "Restore"
                        ) { viewModel.restoreSelectedFiles() }
                        true
                    }
                    R.id.action_delete_perm -> {
                        showPermanentDeleteDialog()
                        true
                    }
                    R.id.action_select_all -> {
                        viewModel.selectAllTrashFiles()
                        true
                    }
                    else -> false
                }
            }
        } else {
            binding.toolbar.menu.clear()
            // binding.toolbar.inflateMenu(R.menu.menu_trash) // If we have one
            binding.toolbar.title = ""
            binding.toolbar.navigationIcon = null
            binding.toolbar.setNavigationOnClickListener(null)
        }
    }

    private fun showPermanentDeleteDialog() {
        com.mfp.filemanager.ui.compose.LiquidDialogs.confirm(
            title = "Delete Permanently?",
            message = "The selected items will be permanently removed from your device and cannot be recovered. Are you sure you want to delete them?",
            confirmText = "Delete",
            destructive = true
        ) { viewModel.deleteSelectedTrashPermanently() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
