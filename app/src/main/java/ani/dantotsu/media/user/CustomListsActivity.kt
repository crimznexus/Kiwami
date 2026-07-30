package ani.dantotsu.media.user

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.dantotsu.R
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.databinding.ActivityCustomListsBinding
import ani.dantotsu.databinding.ItemUserListBinding
import ani.dantotsu.initActivity
import ani.dantotsu.loadImage
import ani.dantotsu.media.Media
import ani.dantotsu.media.MediaListViewActivity
import ani.dantotsu.navBarHeight
import ani.dantotsu.px
import ani.dantotsu.snackString
import ani.dantotsu.statusBarHeight
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.util.customAlertDialog
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "My Lists": the user's AniList custom lists as rich cards — name, entry count, and a
 * strip of cover previews — with create/delete inline. Tapping a card opens that list's
 * entries directly in MediaListViewActivity: one screen to manage, one tap to content,
 * no intermediate tab UI.
 */
class CustomListsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomListsBinding
    private var anime = true

    private data class ListRow(val name: String, val entries: ArrayList<Media>)

    private val rows = mutableListOf<ListRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        binding = ActivityCustomListsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initActivity(this)

        anime = intent.getStringExtra("type") != "MANGA"

        binding.customListsAppBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin += statusBarHeight
        }
        binding.customListsRecycler.setPadding(
            binding.customListsRecycler.paddingLeft,
            binding.customListsRecycler.paddingTop,
            binding.customListsRecycler.paddingRight,
            binding.customListsRecycler.paddingBottom + navBarHeight
        )

        binding.customListsBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.customListsCreate.setOnClickListener { promptCreate() }

        binding.customListsRecycler.layoutManager = LinearLayoutManager(this)
        binding.customListsRecycler.adapter = adapter

        loadRows()
    }

    private fun listNames(): List<String> =
        (if (anime) Anilist.animeCustomLists else Anilist.mangaCustomLists) ?: emptyList()

    private fun loadRows() {
        binding.customListsProgress.isVisible = true
        binding.customListsRecycler.isVisible = false
        binding.customListsEmpty.isVisible = false
        lifecycleScope.launch {
            // One MediaListCollection fetch supplies entries and covers for every list.
            val collection = withContext(Dispatchers.IO) {
                runCatching {
                    Anilist.query.getMediaLists(anime, Anilist.userid ?: 0)
                }.getOrNull()
            } ?: emptyMap()
            rows.clear()
            listNames().forEach { name ->
                rows.add(ListRow(name, collection[name] ?: arrayListOf()))
            }
            binding.customListsProgress.isVisible = false
            adapter.notifyDataSetChanged()
            updateEmptyState()
        }
    }

    private fun updateEmptyState() {
        binding.customListsEmpty.isVisible = rows.isEmpty()
        binding.customListsRecycler.isVisible = rows.isNotEmpty()
    }

    private fun promptCreate() {
        val input = EditText(this).apply {
            hint = getString(R.string.list_name)
            setSingleLine()
        }
        customAlertDialog().apply {
            setTitle(getString(R.string.create_new_list))
            setCustomView(input)
            setPosButton(getString(R.string.ok)) {
                val name = input.text.toString().trim()
                when {
                    name.isEmpty() -> {}
                    rows.any { it.name.equals(name, ignoreCase = true) } ->
                        snackString(getString(R.string.list_already_exists))

                    else -> createList(name)
                }
            }
            setNegButton(getString(R.string.cancel))
            show()
        }
    }

    private fun createList(name: String) {
        lifecycleScope.launch {
            val newLists = listNames() + name
            val ok = withContext(Dispatchers.IO) {
                Anilist.mutation.updateCustomLists(
                    animeCustomLists = if (anime) newLists else null,
                    mangaCustomLists = if (anime) null else newLists
                )
            }
            if (ok) {
                if (anime) Anilist.animeCustomLists = newLists
                else Anilist.mangaCustomLists = newLists
                rows.add(ListRow(name, arrayListOf()))
                adapter.notifyItemInserted(rows.size - 1)
                updateEmptyState()
                snackString(getString(R.string.list_created))
            } else snackString(getString(R.string.list_create_failed))
        }
    }

    private fun deleteList(position: Int) {
        val row = rows.getOrNull(position) ?: return
        customAlertDialog().apply {
            setTitle(getString(R.string.delete_list_title))
            setMessage(getString(R.string.delete_list_desc, row.name))
            setPosButton(getString(R.string.delete)) {
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        Anilist.mutation.deleteCustomList(
                            row.name,
                            if (anime) "ANIME" else "MANGA"
                        )
                    }
                    if (ok) {
                        val remaining = listNames().filter { it != row.name }
                        if (anime) Anilist.animeCustomLists = remaining
                        else Anilist.mangaCustomLists = remaining
                        rows.removeAt(position)
                        adapter.notifyItemRemoved(position)
                        updateEmptyState()
                        snackString(getString(R.string.list_deleted))
                    } else snackString(getString(R.string.list_delete_failed))
                }
            }
            setNegButton(getString(R.string.cancel))
            show()
        }
    }

    private fun openList(row: ListRow) {
        MediaListViewActivity.passedMedia = row.entries
        startActivity(
            Intent(this, MediaListViewActivity::class.java)
                .putExtra("title", row.name)
        )
    }

    private val adapter = object : RecyclerView.Adapter<ListHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListHolder =
            ListHolder(
                ItemUserListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: ListHolder, position: Int) {
            val row = rows[position]
            holder.binding.userListName.text = row.name
            holder.binding.userListCount.text = when (row.entries.size) {
                0 -> getString(R.string.list_entry_empty)
                1 -> getString(R.string.list_entry_one)
                else -> getString(R.string.list_entry_count, row.entries.size)
            }

            // Card backdrop: the first entry's banner (falling back to its cover) gives
            // each list a personality from its own content; empty lists show the plain
            // themed surface under the scrim.
            val first = row.entries.firstOrNull()
            holder.binding.userListBanner.isVisible = first != null
            if (first != null) {
                holder.binding.userListBanner.loadImage(first.banner ?: first.cover)
            }

            // Overlapping deck of up to three entry covers, anchored right. Later views
            // draw on top, so each card after the first tucks under via negative margin.
            val covers = holder.binding.userListCovers
            covers.removeAllViews()
            covers.isVisible = row.entries.isNotEmpty()
            row.entries.take(3).forEachIndexed { index, media ->
                covers.addView(ShapeableImageView(covers.context).apply {
                    layoutParams = ViewGroup.MarginLayoutParams(56f.px, 80f.px).apply {
                        if (index > 0) marginStart = (-22f).px
                    }
                    shapeAppearanceModel = ShapeAppearanceModel.builder()
                        .setAllCornerSizes(10f.px.toFloat())
                        .build()
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    loadImage(media.cover)
                })
            }

            holder.binding.userListCard.setOnClickListener { openList(row) }
            holder.binding.userListDelete.setOnClickListener {
                deleteList(holder.bindingAdapterPosition)
            }
        }
    }

    class ListHolder(val binding: ItemUserListBinding) : RecyclerView.ViewHolder(binding.root)
}
