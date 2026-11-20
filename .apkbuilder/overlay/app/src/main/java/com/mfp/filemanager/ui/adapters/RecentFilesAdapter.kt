package com.mfp.filemanager.ui.adapters

import android.graphics.Color
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.videoFrameMicros
import coil.size.Scale
import com.mfp.filemanager.R
import com.mfp.filemanager.data.FileModel
import com.mfp.filemanager.data.FileType
import com.mfp.filemanager.databinding.ItemRecentFileBinding
import java.io.File
import kotlin.math.abs

class RecentFilesAdapter(
    private val onItemClick: (FileModel) -> Unit,
    private val onLongClick: (FileModel) -> Unit,
    private val isSelectionModeActive: () -> Boolean
) : ListAdapter<RecentsListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    var thumbnailSeed: Long = 0L

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_FILE = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is RecentsListItem.Header -> TYPE_HEADER
            is RecentsListItem.FileItem -> TYPE_FILE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val binding = ItemRecentFileBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            FileViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is RecentsListItem.Header -> (holder as HeaderViewHolder).bind(item.title)
            is RecentsListItem.FileItem -> (holder as FileViewHolder).bind(item.file)
        }
    }

    class HeaderViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val textView: android.widget.TextView = itemView as android.widget.TextView
        fun bind(title: String) {
            textView.text = title
        }
    }

    inner class FileViewHolder(private val binding: ItemRecentFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    if (item is RecentsListItem.FileItem) {
                        onItemClick(item.file)
                    }
                }
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    if (item is RecentsListItem.FileItem) {
                        onLongClick(item.file)
                    }
                }
                true
            }
        }

        fun bind(file: FileModel) {
            binding.root.isSelected = file.isSelected
            binding.root.alpha = if (file.isSelected) 0.6f else 1.0f
            
            binding.textFileName.text = file.name
            binding.textFileSize.text = Formatter.formatFileSize(binding.root.context, file.size)
            
            if (isSelectionModeActive()) {
                binding.checkbox.visibility = android.view.View.VISIBLE
                binding.checkbox.isChecked = file.isSelected
            } else {
                binding.checkbox.visibility = android.view.View.GONE
            }

            // Liquid glass badge / glass-framed thumbnail (see ui/glass)
            com.mfp.filemanager.ui.glass.GlassFileIcon.bind(binding.imgThumbnail, file, thumbnailSeed)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RecentsListItem>() {
        override fun areItemsTheSame(oldItem: RecentsListItem, newItem: RecentsListItem): Boolean {
            return if (oldItem is RecentsListItem.FileItem && newItem is RecentsListItem.FileItem) {
                oldItem.file.path == newItem.file.path
            } else if (oldItem is RecentsListItem.Header && newItem is RecentsListItem.Header) {
                oldItem.title == newItem.title
            } else {
                false
            }
        }

        override fun areContentsTheSame(oldItem: RecentsListItem, newItem: RecentsListItem): Boolean {
            return oldItem == newItem
        }
    }
}
