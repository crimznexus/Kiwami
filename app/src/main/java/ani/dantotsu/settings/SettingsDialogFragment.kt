package ani.dantotsu.settings

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import ani.dantotsu.BottomSheetDialogFragment
import ani.dantotsu.MainActivity
import ani.dantotsu.R
import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.databinding.BottomSheetSettingsBinding
import ani.dantotsu.download.anime.OfflineAnimeFragment
import ani.dantotsu.download.manga.OfflineMangaFragment
import ani.dantotsu.getThemeColor
import ani.dantotsu.home.AnimeFragment
import ani.dantotsu.home.HomeFragment
import ani.dantotsu.home.LoginFragment
import ani.dantotsu.home.MangaFragment
import ani.dantotsu.home.NoInternet
import ani.dantotsu.incognitoNotification
import ani.dantotsu.loadImage
import ani.dantotsu.offline.OfflineFragment
import ani.dantotsu.profile.ProfileActivity
import ani.dantotsu.profile.activity.FeedActivity
import ani.dantotsu.profile.notification.NotificationActivity
import ani.dantotsu.setSafeOnClickListener
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.startMainActivity
import ani.dantotsu.util.customAlertDialog
import eu.kanade.tachiyomi.util.system.getSerializableCompat
import java.util.Timer
import kotlin.concurrent.schedule
import android.view.WindowManager
import android.os.Build
import android.util.TypedValue
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import androidx.core.graphics.ColorUtils




class SettingsDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var pageType: PageType

    override fun getTheme(): Int {
        // Use transparent theme for Liquid Glass, otherwise use default
        val isLiquidGlassTheme = PrefManager.getVal<String>(PrefName.Theme) == "LIQUID_GLASS"
        return if (isLiquidGlassTheme) {
            R.style.TransparentBottomSheetDialog
        } else {
            super.getTheme()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageType = arguments?.getSerializableCompat("pageType") as? PageType ?: PageType.HOME
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val window = dialog?.window
        val isLiquidGlassTheme = PrefManager.getVal<String>(PrefName.Theme) == "LIQUID_GLASS"

        if (isLiquidGlassTheme) {
            // Window transparency only; the frosted fill and dim are set in onStart(), which
            // is where we can tell whether the system will actually blur behind us.
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        } else {
            binding.settingsContainer.setBackgroundResource(R.drawable.bottom_sheet_background)
        }

        window?.navigationBarColor =
            requireContext().getThemeColor(com.google.android.material.R.attr.colorSurface)
        val notificationIcon = if (Anilist.unreadNotificationCount > 0) {
            R.drawable.ic_round_notifications_active_24
        } else {
            R.drawable.ic_round_notifications_none_24
        }
        binding.settingsNotification.setImageResource(notificationIcon)

        if (Anilist.token != null) {
            binding.settingsLogin.setText(R.string.logout)
            binding.settingsLogin.setOnClickListener {
                requireContext().customAlertDialog().apply {
                    setTitle(R.string.logout)
                    setMessage(R.string.logout_confirm)
                    setPosButton(R.string.yes) {
                        Anilist.removeSavedToken()
                        startMainActivity(requireActivity())
                    }
                    setNegButton(R.string.no)
                    show()
                }
            }
            binding.settingsUsername.text = Anilist.username
            binding.settingsUserAvatar.loadImage(Anilist.avatar)
        } else {
            binding.settingsUsername.visibility = View.GONE
            binding.settingsLogin.setText(R.string.login)
            binding.settingsLogin.setOnClickListener {
                dismiss()
                Anilist.loginIntent(requireActivity())
            }
        }
        binding.settingsNotificationCount.isVisible = Anilist.unreadNotificationCount > 0
        binding.settingsNotificationCount.text = Anilist.unreadNotificationCount.toString()
        binding.settingsUserAvatar.setOnClickListener {
            ContextCompat.startActivity(
                requireContext(), Intent(requireContext(), ProfileActivity::class.java)
                    .putExtra("userId", Anilist.userid), null
            )
        }

        binding.settingsIncognito.isChecked = PrefManager.getVal(PrefName.Incognito)
        binding.settingsIncognito.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.Incognito, isChecked)
            incognitoNotification(requireContext())
        }

        binding.settingsExtensionSettings.setSafeOnClickListener {
            startActivity(Intent(activity, ExtensionsActivity::class.java))
            dismiss()
        }

        binding.settingsSettings.setSafeOnClickListener {
            startActivity(Intent(activity, SettingsActivity::class.java))
            dismiss()
        }

        binding.settingsActivity.setSafeOnClickListener {
            startActivity(Intent(activity, FeedActivity::class.java))
            dismiss()
        }

        binding.settingsNotification.setOnClickListener {
            startActivity(Intent(activity, NotificationActivity::class.java))
            dismiss()
        }
        binding.settingsDownloads.isChecked = PrefManager.getVal(PrefName.OfflineMode)
        binding.settingsDownloads.setOnCheckedChangeListener { _, isChecked ->
            Timer().schedule(300) {
                when (pageType) {
                    PageType.MANGA -> {
                        val intent = Intent(activity, NoInternet::class.java)
                        intent.putExtra(
                            "FRAGMENT_CLASS_NAME",
                            OfflineMangaFragment::class.java.name
                        )
                        startActivity(intent)
                    }

                    PageType.ANIME -> {
                        val intent = Intent(activity, NoInternet::class.java)
                        intent.putExtra(
                            "FRAGMENT_CLASS_NAME",
                            OfflineAnimeFragment::class.java.name
                        )
                        startActivity(intent)
                    }

                    PageType.HOME -> {
                        val intent = Intent(activity, NoInternet::class.java)
                        intent.putExtra("FRAGMENT_CLASS_NAME", OfflineFragment::class.java.name)
                        startActivity(intent)
                    }

                    PageType.OfflineMANGA -> {
                        val intent = Intent(activity, MainActivity::class.java)
                        intent.putExtra("FRAGMENT_CLASS_NAME", MangaFragment::class.java.name)
                        startActivity(intent)
                    }

                    PageType.OfflineHOME -> {
                        val intent = Intent(activity, MainActivity::class.java)
                        intent.putExtra(
                            "FRAGMENT_CLASS_NAME",
                            if (Anilist.token != null) HomeFragment::class.java.name else LoginFragment::class.java.name
                        )
                        startActivity(intent)
                    }

                    PageType.OfflineANIME -> {
                        val intent = Intent(activity, MainActivity::class.java)
                        intent.putExtra("FRAGMENT_CLASS_NAME", AnimeFragment::class.java.name)
                        startActivity(intent)
                    }
                }

                dismiss()
                PrefManager.setVal(PrefName.OfflineMode, isChecked)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val isLiquidGlassTheme = PrefManager.getVal<String>(PrefName.Theme) == "LIQUID_GLASS"

        if (isLiquidGlassTheme) {
            // Cross-window blur is a privilege the system grants, not a flag we can rely on:
            // it needs API 31+ and is switched off on low-end devices, in battery saver, and
            // on most emulators. Asking first matters, because a 60%-transparent panel with
            // no blur behind it just shows the home screen straight through the menu.
            val blurred = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    requireContext().getSystemService(WindowManager::class.java)
                        ?.isCrossWindowBlurEnabled == true

            dialog?.window?.let { w ->
                if (blurred) {
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply { blurBehindRadius = BLUR_RADIUS }
                }
                // A real blur separates the layers on its own; without one the scrim has to
                // do that work, so it goes darker.
                w.setDimAmount(if (blurred) DIM_WITH_BLUR else DIM_WITHOUT_BLUR)
            }

            val surface =
                requireContext().getThemeColor(com.google.android.material.R.attr.colorSurface)
            val outline =
                requireContext().getThemeColor(com.google.android.material.R.attr.colorOutline)
            val radius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 28f, resources.displayMetrics
            )
            // Applied to the fragment's own root rather than the dialog's internal
            // design_bottom_sheet view, which we may or may not be handed depending on how
            // the sheet was themed — this one we always have.
            binding.root.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                // Top corners only — the sheet sits flush against the bottom edge.
                cornerRadii = floatArrayOf(
                    radius, radius, radius, radius, 0f, 0f, 0f, 0f
                )
                setColor(
                    ColorUtils.setAlphaComponent(
                        surface, if (blurred) FILL_ALPHA_WITH_BLUR else FILL_ALPHA_WITHOUT_BLUR
                    )
                )
                // Hairline edge so the panel reads as a distinct surface rather than a
                // washed-out patch of whatever is behind it.
                setStroke(
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics
                    ).toInt(),
                    ColorUtils.setAlphaComponent(outline, 90)
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        // Glass has to stay readable over whatever the home screen happens to be showing —
        // cover art, bright banners, dense text. Anything much below these values lets that
        // content read straight through the menu instead of sitting behind it.
        private const val BLUR_RADIUS = 64
        private const val DIM_WITH_BLUR = 0.4f
        private const val DIM_WITHOUT_BLUR = 0.55f
        private const val FILL_ALPHA_WITH_BLUR = 246    // 96%
        private const val FILL_ALPHA_WITHOUT_BLUR = 252 // 99%

        enum class PageType {
            MANGA, ANIME, HOME, OfflineMANGA, OfflineANIME, OfflineHOME
        }

        fun newInstance(pageType: PageType): SettingsDialogFragment {
            val fragment = SettingsDialogFragment()
            val args = Bundle()
            args.putSerializable("pageType", pageType)
            fragment.arguments = args
            return fragment
        }
    }
}
