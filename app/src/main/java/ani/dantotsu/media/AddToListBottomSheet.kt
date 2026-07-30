package ani.dantotsu.media

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import ani.dantotsu.R
import ani.dantotsu.Refresh
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.databinding.BottomSheetAddToListBinding
import ani.dantotsu.others.getSerialized
import ani.dantotsu.snackString
import ani.dantotsu.util.customAlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable

/**
 * Quick "add to list" flow: pick the custom AniList lists a media belongs to, optionally
 * creating a new list inline. If the media is not in the user's library yet, saving also
 * adds it with PLANNING status — the default landing list — so a bare "add" always ends
 * up somewhere sensible.
 */
class AddToListBottomSheet : BottomSheetDialogFragment() {

    private lateinit var media: Media

    companion object {
        fun newInstance(m: Media): AddToListBottomSheet =
            AddToListBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable("media", m as Serializable)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { media = it.getSerialized("media")!! }
    }

    private var _binding: BottomSheetAddToListBinding? = null
    private val binding get() = _binding!!

    // list name -> checked; seeded from the entry's memberships when known
    private val membership = linkedMapOf<String, Boolean>()

    private val isAnime get() = media.anime != null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddToListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.addToListMediaTitle.text = media.userPreferredName
        binding.addToListPlanningNote.visibility =
            if (media.userStatus == null) View.VISIBLE else View.GONE

        val allLists =
            (if (isAnime) Anilist.animeCustomLists else Anilist.mangaCustomLists) ?: emptyList()
        allLists.forEach { name ->
            membership[name] = media.inCustomListsOf?.get(name) ?: false
        }
        rebuildSwitches()

        binding.addToListCreateNew.setOnClickListener { promptCreate() }
        binding.addToListSave.setOnClickListener { save() }
    }

    private fun rebuildSwitches() {
        val container = binding.addToListContainer
        container.removeAllViews()
        membership.forEach { (name, checked) ->
            container.addView(MaterialSwitch(requireContext()).apply {
                text = name
                isChecked = checked
                setOnCheckedChangeListener { _, isChecked -> membership[name] = isChecked }
            })
        }
    }

    private fun promptCreate() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.list_name)
            setSingleLine()
        }
        requireContext().customAlertDialog().apply {
            setTitle(getString(R.string.create_new_list))
            setCustomView(input)
            setPosButton(getString(R.string.ok)) {
                val name = input.text.toString().trim()
                when {
                    name.isEmpty() -> {}
                    membership.keys.any { it.equals(name, ignoreCase = true) } ->
                        snackString(getString(R.string.list_already_exists))

                    else -> createList(name)
                }
            }
            setNegButton(getString(R.string.cancel))
            show()
        }
    }

    private fun createList(name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val newLists = membership.keys.toList() + name
            val ok = withContext(Dispatchers.IO) {
                Anilist.mutation.updateCustomLists(
                    animeCustomLists = if (isAnime) newLists else null,
                    mangaCustomLists = if (isAnime) null else newLists
                )
            }
            if (ok) {
                if (isAnime) Anilist.animeCustomLists = newLists
                else Anilist.mangaCustomLists = newLists
                // A list created from this sheet is presumably where the user wants the
                // media, so it starts checked.
                membership[name] = true
                if (_binding != null) rebuildSwitches()
                snackString(getString(R.string.list_created))
            } else snackString(getString(R.string.list_create_failed))
        }
    }

    private fun save() {
        val checked = membership.mapNotNull { if (it.value) it.key else null }
        val status = media.userStatus ?: "PLANNING"
        binding.addToListSave.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                Anilist.mutation.editList(
                    mediaID = media.id,
                    status = status,
                    customList = checked
                )
            }
            media.userStatus = status
            media.inCustomListsOf =
                membership.toMutableMap().also { map -> checked.forEach { map[it] = true } }
            Refresh.all()
            snackString(getString(R.string.added_to_list))
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
