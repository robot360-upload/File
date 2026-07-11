package com.example.dualfilemanager.adapter

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.RecyclerView
import com.example.dualfilemanager.databinding.ItemFileBinding

/**
 * Lists the children of the currently open folder in one pane.
 * Tap a folder -> navigate. Tap a file -> toggle selection.
 * Long-press anything -> toggle selection (so folders can be selected without opening them).
 */
class FileAdapter(
    private val onOpenFolder: (DocumentFile) -> Unit,
    private val onToggleSelect: (DocumentFile) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    private var items: List<DocumentFile> = emptyList()
    private var selected: Set<Uri> = emptySet()

    fun submit(newItems: List<DocumentFile>, selectedUris: Set<Uri>) {
        items = newItems
        selected = selectedUris
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val doc = items[position]
        val isDir = doc.isDirectory
        holder.binding.icon.text = if (isDir) "📁" else "📄"
        holder.binding.name.text = doc.name ?: "(unknown)"

        val meta = if (isDir) {
            DateUtils.getRelativeTimeSpanString(doc.lastModified()).toString()
        } else {
            val size = Formatter.formatShortFileSize(holder.binding.root.context, doc.length())
            val date = DateUtils.getRelativeTimeSpanString(doc.lastModified())
            "$size  •  $date"
        }
        holder.binding.meta.text = meta

        val isSelected = selected.contains(doc.uri)
        holder.binding.root.setBackgroundColor(
            if (isSelected) 0x33E8A33D else 0x00000000
        )

        holder.binding.root.setOnClickListener {
            if (isDir && selected.isEmpty()) {
                onOpenFolder(doc)
            } else {
                onToggleSelect(doc)
            }
        }
        holder.binding.root.setOnLongClickListener {
            onToggleSelect(doc)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root)
}
