package com.example.dualfilemanager.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.dualfilemanager.SearchEngine.SearchResult
import com.example.dualfilemanager.databinding.ItemSearchResultBinding

class SearchResultAdapter(
    private val onClick: (SearchResult) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.VH>() {

    private val items = mutableListOf<SearchResult>()

    fun add(result: SearchResult) {
        items.add(result)
        notifyItemInserted(items.size - 1)
    }

    fun size() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val result = items[position]
        holder.binding.icon.text = if (result.doc.isDirectory) "📁" else "📄"
        holder.binding.name.text = result.doc.name ?: "(unknown)"
        holder.binding.path.text = result.relativePath()
        holder.binding.root.setOnClickListener { onClick(result) }
    }

    override fun getItemCount(): Int = items.size

    class VH(val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)
}
