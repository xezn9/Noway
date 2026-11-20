package com.mfp.filemanager.ui.adapters
import android.graphics.Bitmap
import android.graphics.Color
import android.text.format.DateFormat
import android.text.format.Formatter
import android.transition.ChangeBounds
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import coil.dispose
import coil.load
import coil.request.CachePolicy
import coil.request.videoFrameMicros
import coil.size.Scale
import com.mfp.filemanager.R
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.databinding.ItemFileListBinding
import java.io.File
import java.util.Date

class FileListAdapter(
    private val onItemClick: (FileModel) -> Unit,
    private val onLongClick: (FileModel) -> Unit,
    private val onActionClick: (FileModel, String) -> Unit, // "copy", "move", etc.
    private val allowSelection: Boolean = true,
    private val showItemMenu: Boolean = true
) : ListAdapter<FileModel, FileListAdapter.ViewHolder>(DiffCallback()) {

    var thumbnailSeed: Long = 0L

    private var expandedPath: String? = null
    private var recyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    var isSelectionMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                recyclerView?.let { rv ->
                    // Disable default fade animations so TransitionManager takes full control
                    (rv.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

                    val transition = TransitionSet()
                        .addTransition(ChangeBounds())
                        .addTransition(Slide(Gravity.START).addTarget(R.id.checkbox_select))
                        .setDuration(200)
                        .setInterpolator(LinearInterpolator())
                    TransitionManager.beginDelayedTransition(rv, transition)
                }
                notifyItemRangeChanged(0, itemCount)
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFileListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private fun toggleExpansion(path: String) {
        val oldPath = expandedPath
        expandedPath = if (expandedPath == path) null else path
        
        currentList.indexOfFirst { it.path == oldPath }.takeIf { it != -1 }?.let { notifyItemChanged(it) }
        currentList.indexOfFirst { it.path == expandedPath }.takeIf { it != -1 }?.let { notifyItemChanged(it) }
    }

    inner class ViewHolder(private val binding: ItemFileListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // Folder icon = real Backdrop liquid glass (one tiny Compose view per row)
            setupFolderIcon()

            binding.layoutMain.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            
            if (allowSelection) {
                binding.layoutMain.setOnLongClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onLongClick(getItem(position))
                    }
                    true
                }
            }

            if (showItemMenu) {
                binding.btnMore.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val file = getItem(position)
                        TransitionManager.beginDelayedTransition(binding.root as ViewGroup)
                        toggleExpansion(file.path)
                    }
                }
            } else {
                binding.btnMore.visibility = View.GONE
                binding.btnMore.setOnClickListener(null)
            }

            // Action Buttons
            binding.btnCopy.setOnClickListener { onActionClick(getItem(bindingAdapterPosition), "copy") }
            binding.btnMove.setOnClickListener { onActionClick(getItem(bindingAdapterPosition), "move") }
            binding.btnShare.setOnClickListener { onActionClick(getItem(bindingAdapterPosition), "share") }
            binding.btnRename.setOnClickListener { onActionClick(getItem(bindingAdapterPosition), "rename") }
            binding.btnInfo.setOnClickListener { onActionClick(getItem(bindingAdapterPosition), "info") }
            binding.btnDelete.setOnClickListener { onActionClick(getItem(bindingAdapterPosition), "delete") }
        }

        private var actionsReady = false

        /** Real Backdrop liquid-glass action buttons; composed only while the row is expanded. */
        private fun setActionsVisible(visible: Boolean) {
            val cv = binding.composeActions
            if (visible && !actionsReady) {
                actionsReady = true
                cv.setViewCompositionStrategy(
                    androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                cv.setContent {
                    com.mfp.filemanager.ui.compose.LiquidActionMenu(
                        dark = com.mfp.filemanager.ui.util.Immersive.isDarkTheme(binding.root.context),
                        onCopy = { binding.btnCopy.performClick() },
                        onMove = { binding.btnMove.performClick() },
                        onShare = { binding.btnShare.performClick() },
                        onRename = { binding.btnRename.performClick() },
                        onInfo = { binding.btnInfo.performClick() },
                        onDelete = { binding.btnDelete.performClick() }
                    )
                }
            } else if (!visible && actionsReady) {
                actionsReady = false
                cv.disposeComposition()
            }
        }

        private fun setupFolderIcon() {
            binding.composeFolder.setViewCompositionStrategy(
                androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            binding.composeFolder.setContent {
                com.mfp.filemanager.ui.compose.LiquidFolderIcon(
                    dark = com.mfp.filemanager.ui.util.Immersive.isDarkTheme(binding.root.context)
                )
            }
        }

        fun bind(file: FileModel) {
            val isExpanded = file.path == expandedPath
            val showMenu = isExpanded && !isSelectionMode && showItemMenu
            binding.layoutMenu.visibility = if (showMenu) View.VISIBLE else View.GONE
            setActionsVisible(showMenu)
            
            if (showItemMenu) {
                binding.btnMore.setImageResource(if (isExpanded) R.drawable.ic_more_horiz_24 else R.drawable.ic_more_vert_24)
                binding.btnMore.visibility = if (isSelectionMode) View.INVISIBLE else View.VISIBLE
            } else {
                binding.btnMore.visibility = View.GONE
            }

            binding.checkboxSelect.visibility = if (isSelectionMode && allowSelection) View.VISIBLE else View.GONE
            binding.checkboxSelect.isChecked = file.isSelected

            binding.layoutMain.isSelected = file.isSelected
            binding.layoutMain.setBackgroundColor(
                if (file.isSelected) 
                    androidx.core.graphics.ColorUtils.setAlphaComponent(com.mfp.filemanager.ui.theme.GlassConfig.accentArgb, 0x33) // 20% alpha app colour
                else 
                    Color.TRANSPARENT
            )
            
            binding.textName.text = file.name
            
            // Date formatting
            val date = Date(file.dateModified)
            val dateFormat = DateFormat.getMediumDateFormat(binding.root.context)
            binding.textDate.text = dateFormat.format(date)

            // Size or Item count
            if (file.isDirectory) {
               // If we have item count in FileModel, use it, otherwise just show nothing or "dir"
               // Assuming FileModel has size for files only usually, but let's check.
               // For now, if directory, we might not show size unless calculated.
               binding.textSize.text = binding.root.context.getString(R.string.label_directory)
            } else {
               binding.textSize.text = Formatter.formatFileSize(binding.root.context, file.size)
            }

            if (file.isDirectory) {
                // folders: Backdrop liquid glass (Compose)
                binding.imgIcon.dispose()
                binding.imgIcon.setImageDrawable(null)
                binding.imgIcon.visibility = View.GONE
                binding.composeFolder.visibility = View.VISIBLE
            } else {
                binding.composeFolder.visibility = View.GONE
                binding.imgIcon.visibility = View.VISIBLE
                // Liquid glass badge / glass-framed thumbnail (see ui/glass)
                com.mfp.filemanager.ui.glass.GlassFileIcon.bind(binding.imgIcon, file, thumbnailSeed)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FileModel>() {
        override fun areItemsTheSame(oldItem: FileModel, newItem: FileModel): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: FileModel, newItem: FileModel): Boolean {
            return oldItem == newItem
        }
    }
}

// PdfFetcher moved to separate file: PdfFetcher.kt
