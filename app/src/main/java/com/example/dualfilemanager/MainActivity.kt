package com.example.dualfilemanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dualfilemanager.adapter.FileAdapter
import com.example.dualfilemanager.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Holds everything needed to drive one pane (left or right). */
    private inner class Pane(
        val include: androidx.viewbinding.ViewBinding,
        val recyclerView: androidx.recyclerview.widget.RecyclerView,
        val txtPath: android.widget.TextView,
        val btnUp: android.widget.ImageButton,
        val btnSearch: android.widget.ImageButton,
        val btnChoose: android.widget.Button,
        val activeBar: android.view.View
    ) {
        var root: DocumentFile? = null                 // tree root granted by the user
        val backStack = ArrayDeque<DocumentFile>()      // folders visited, for "Up"
        var current: DocumentFile? = null               // folder currently shown
        val selected = mutableSetOf<Uri>()
        lateinit var adapter: FileAdapter

        fun selectedDocsBlocking(): List<DocumentFile> =
            current?.listFiles()?.filter { selected.contains(it.uri) } ?: emptyList()
    }

    private lateinit var paneLeft: Pane
    private lateinit var paneRight: Pane
    private var activePane: Pane? = null

    private val openTreeLeft = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { onTreeGranted(paneLeft, it) } }

    private val openTreeRight = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { onTreeGranted(paneRight, it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val leftInclude = binding.paneLeft
        val rightInclude = binding.paneRight

        paneLeft = Pane(
            leftInclude,
            leftInclude.recyclerView, leftInclude.txtPath, leftInclude.btnUp, leftInclude.btnSearch,
            leftInclude.btnChooseFolder, leftInclude.activeBar
        )
        paneRight = Pane(
            rightInclude,
            rightInclude.recyclerView, rightInclude.txtPath, rightInclude.btnUp, rightInclude.btnSearch,
            rightInclude.btnChooseFolder, rightInclude.activeBar
        )

        setupPane(paneLeft, openTreeLeft)
        setupPane(paneRight, openTreeRight)
        setActive(paneLeft)

        binding.btnNewFolder.setOnClickListener { onNewFolder() }
        binding.btnRename.setOnClickListener { onRename() }
        binding.btnCopy.setOnClickListener { onCopyOrMove(move = false) }
        binding.btnMove.setOnClickListener { onCopyOrMove(move = true) }
        binding.btnDelete.setOnClickListener { onDelete() }
        binding.btnRefresh.setOnClickListener { activePane?.let { refresh(it) } }
    }

    // ---------- pane setup ----------

    private fun setupPane(
        pane: Pane,
        launcher: androidx.activity.result.ActivityResultLauncher<Uri?>
    ) {
        pane.adapter = FileAdapter(
            onOpenFolder = { doc -> navigateInto(pane, doc) },
            onToggleSelect = { doc ->
                if (!pane.selected.remove(doc.uri)) pane.selected.add(doc.uri)
                refresh(pane)
            }
        )
        pane.recyclerView.layoutManager = LinearLayoutManager(this)
        pane.recyclerView.adapter = pane.adapter
        pane.recyclerView.setOnTouchListener { _, _ -> setActive(pane); false }

        pane.btnChoose.setOnClickListener { launcher.launch(null) }
        pane.btnUp.setOnClickListener { navigateUp(pane) }
        pane.btnSearch.setOnClickListener { onSearch(pane) }
    }

    private fun setActive(pane: Pane) {
        activePane = pane
        paneLeft.activeBar.setBackgroundColor(resources.getColor(
            if (pane === paneLeft) R.color.accent_active else R.color.divider, theme
        ))
        paneRight.activeBar.setBackgroundColor(resources.getColor(
            if (pane === paneRight) R.color.accent_active else R.color.divider, theme
        ))
    }

    private fun otherPane(pane: Pane): Pane = if (pane === paneLeft) paneRight else paneLeft

    // ---------- tree / navigation ----------

    private fun onTreeGranted(pane: Pane, uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val root = DocumentFile.fromTreeUri(this, uri) ?: return
        pane.root = root
        pane.current = root
        pane.backStack.clear()
        pane.selected.clear()
        refresh(pane)
    }

    private fun navigateInto(pane: Pane, doc: DocumentFile) {
        pane.current?.let { pane.backStack.addLast(it) }
        pane.current = doc
        pane.selected.clear()
        setActive(pane)
        refresh(pane)
    }

    private fun navigateUp(pane: Pane) {
        val prev = pane.backStack.removeLastOrNull() ?: return
        pane.current = prev
        pane.selected.clear()
        refresh(pane)
    }

    private fun refresh(pane: Pane) {
        val current = pane.current
        if (current == null) {
            pane.txtPath.text = getString(R.string.no_folder_selected)
            pane.adapter.submit(emptyList(), emptySet())
            return
        }
        pane.txtPath.text = current.name ?: current.uri.lastPathSegment ?: "/"
        lifecycleScope.launch {
            val children = withContext(Dispatchers.IO) {
                current.listFiles().sortedWith(
                    compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name?.lowercase() }
                )
            }
            pane.adapter.submit(children, pane.selected.toSet())
        }
    }

    // ---------- toolbar actions ----------

    private fun onNewFolder() {
        val pane = activePane ?: return
        val parent = pane.current ?: run { toast("Open a folder first"); return }
        promptText("New folder name") { name ->
            if (name.isBlank()) return@promptText
            lifecycleScope.launch {
                val created = FileOps.createFolder(parent, name)
                if (created == null) toast("Could not create folder") else refresh(pane)
            }
        }
    }

    private fun onRename() {
        val pane = activePane ?: return
        lifecycleScope.launch {
            val docs = withContext(Dispatchers.IO) { pane.selectedDocsBlocking() }
            if (docs.size != 1) { toast("Select exactly one item to rename"); return@launch }
            val doc = docs.first()
            promptText("Rename to", doc.name ?: "") { newName ->
                if (newName.isBlank()) return@promptText
                lifecycleScope.launch {
                    val ok = FileOps.rename(doc, newName)
                    if (!ok) toast("Rename failed")
                    pane.selected.clear()
                    refresh(pane)
                }
            }
        }
    }

    private fun onCopyOrMove(move: Boolean) {
        val source = activePane ?: return
        val dest = otherPane(source)
        val destFolder = dest.current ?: run { toast("Open a destination folder in the other pane"); return }
        val sourceParent = source.current ?: run { toast("Open a folder first"); return }

        lifecycleScope.launch {
            val docs = withContext(Dispatchers.IO) { source.selectedDocsBlocking() }
            if (docs.isEmpty()) { toast("Select one or more items first"); return@launch }

            var failures = 0
            for (doc in docs) {
                val ok = if (move) {
                    FileOps.moveInto(this@MainActivity, doc, sourceParent, destFolder)
                } else {
                    FileOps.copyInto(this@MainActivity, doc, destFolder)
                }
                if (!ok) failures++
            }
            source.selected.clear()
            refresh(source)
            refresh(dest)
            toast(
                if (failures == 0) (if (move) "Moved" else "Copied")
                else "$failures item(s) failed"
            )
        }
    }

    private fun onDelete() {
        val pane = activePane ?: return
        lifecycleScope.launch {
            val docs = withContext(Dispatchers.IO) { pane.selectedDocsBlocking() }
            if (docs.isEmpty()) { toast("Select one or more items first"); return@launch }

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Delete ${docs.size} item(s)?")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        var failures = 0
                        for (doc in docs) if (!FileOps.delete(doc)) failures++
                        pane.selected.clear()
                        refresh(pane)
                        toast(if (failures == 0) "Deleted" else "$failures item(s) failed")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ---------- search ----------

    private fun onSearch(pane: Pane) {
        setActive(pane)
        val root = pane.current ?: pane.root ?: run { toast("Open a folder first"); return }
        promptText("Search filenames for") { query ->
            if (query.isBlank()) return@promptText
            showSearchResults(pane, root, query.trim())
        }
    }

    private fun showSearchResults(pane: Pane, searchRoot: DocumentFile, query: String) {
        val dialogBinding = com.example.dualfilemanager.databinding.DialogSearchResultsBinding
            .inflate(layoutInflater)
        dialogBinding.results.layoutManager = LinearLayoutManager(this)

        var cancelled = false
        lateinit var dialog: AlertDialog

        val adapter = com.example.dualfilemanager.adapter.SearchResultAdapter { result ->
            dialog.dismiss()
            lifecycleScope.launch { jumpToResult(pane, result) }
        }
        dialogBinding.results.adapter = adapter

        dialog = AlertDialog.Builder(this)
            .setTitle("Search: \u201C$query\u201D")
            .setView(dialogBinding.root)
            .setNegativeButton("Close", null)
            .create()

        val job = lifecycleScope.launch {
            SearchEngine.search(
                root = searchRoot,
                query = query,
                isCancelled = { cancelled },
                onResult = { result ->
                    withContext(Dispatchers.Main) { adapter.add(result) }
                }
            )
            withContext(Dispatchers.Main) {
                dialogBinding.status.text = if (adapter.size() == 0) {
                    "No matches for \u201C$query\u201D"
                } else {
                    "${adapter.size()} match(es) found"
                }
            }
        }

        dialog.setOnDismissListener {
            cancelled = true
            job.cancel()
        }
        dialog.show()
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.75).toInt()
        )
    }

    /** Opens the pane straight to a search hit, rebuilding its Up-history along the way. */
    private suspend fun jumpToResult(pane: Pane, result: SearchEngine.SearchResult) {
        pane.backStack.clear()
        for (i in 0 until result.ancestors.size - 1) {
            pane.backStack.addLast(result.ancestors[i])
        }
        pane.current = result.ancestors.last()
        pane.selected.clear()
        pane.selected.add(result.doc.uri)
        setActive(pane)
        refresh(pane)
    }

    // ---------- small helpers ----------

    private fun promptText(title: String, prefill: String = "", onSubmit: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefill)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK") { _, _ -> onSubmit(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
