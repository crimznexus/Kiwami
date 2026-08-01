package ani.dantotsu.home

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import ani.dantotsu.R
import ani.dantotsu.ZoomOutPageTransformer
import ani.dantotsu.databinding.ActivityNoInternetBinding
import ani.dantotsu.download.anime.OfflineAnimeFragment
import ani.dantotsu.download.manga.OfflineMangaFragment
import ani.dantotsu.initActivity
import ani.dantotsu.navBarHeight
import ani.dantotsu.offline.OfflineFragment
import ani.dantotsu.selectedOption
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.snackString
import ani.dantotsu.themes.ThemeManager
import ani.dantotsu.util.TvUtils
import ani.dantotsu.widgets.LiquidBottomTabs
import ani.dantotsu.widgets.LiquidBottomTab
import ani.dantotsu.widgets.LiquidBottomBarMetrics
import ani.dantotsu.widgets.LiquidGlassBottomBar
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import com.kyant.backdrop.backdrops.layerBackdrop
import eightbitlab.com.blurview.BlurView
import com.google.android.material.bottomnavigation.BottomNavigationView


class NoInternet : AppCompatActivity() {
    private lateinit var binding: ActivityNoInternetBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()

        binding = ActivityNoInternetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // LiquidGlassBottomBar handles its own glass background drawing


        var doubleBackToExitPressedOnce = false
        onBackPressedDispatcher.addCallback(this) {
            if (doubleBackToExitPressedOnce) {
                finishAffinity()
            }
            doubleBackToExitPressedOnce = true
            snackString(this@NoInternet.getString(R.string.back_to_exit))
            Handler(Looper.getMainLooper()).postDelayed(
                { doubleBackToExitPressedOnce = false },
                2000
            )
        }

        binding.root.doOnAttach {
            initActivity(this)
            val startUpTab: Int = PrefManager.getVal(PrefName.DefaultStartUpTab)
            // A saved manga tab has nowhere to go on a TV build; fall back to home.
            selectedOption =
                if (startUpTab >= 2 && !TvUtils.supportsManga(this)) 1 else startUpTab
        }

        // Check if Liquid Glass theme is active
        val isLiquidGlassTheme = PrefManager.getVal<String>(PrefName.Theme) == "LIQUID_GLASS"

        val mangaEnabled = TvUtils.supportsManga(this)
        val pageCount = if (mangaEnabled) 3 else 2
        if (selectedOption >= pageCount) selectedOption = 1

        if (isLiquidGlassTheme) {
            // Use Compose HorizontalPager for Liquid Glass theme
            binding.viewpager.visibility = View.GONE
            binding.includedNavbar.root.visibility = View.GONE
            binding.composeMainContent.visibility = View.VISIBLE
            
            binding.composeMainContent.setContent {
                val pagerState = rememberPagerState(
                    initialPage = selectedOption,
                    pageCount = { pageCount }
                )
                val coroutineScope = rememberCoroutineScope()
                val backdrop = rememberLayerBackdrop()

                // Sync pager to selectedOption changes ONLY
                LaunchedEffect(selectedOption) {
                    if (pagerState.currentPage != selectedOption) {
                        pagerState.scrollToPage(selectedOption)
                    }
                }
                
                Box(modifier = Modifier.fillMaxSize()) {
                    // Main content pager (offline fragments)
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(backdrop)
                    ) { page ->
                        when (page) {
                            0 -> OfflineAnimePageComposable(supportFragmentManager)
                            1 -> OfflineHomePageComposable(supportFragmentManager)
                            2 -> OfflineMangaPageComposable(supportFragmentManager)
                        }
                    }
                    
                    val navInset = with(LocalDensity.current) { navBarHeight.toDp() }
                    LiquidBottomTabs(
                        selectedTabIndex = { selectedOption },
                        onTabSelected = { index ->
                            selectedOption = index
                            coroutineScope.launch { pagerState.scrollToPage(index) }
                        },
                        backdrop = backdrop,
                        tabsCount = pageCount,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom = LiquidBottomBarMetrics.BottomInset + navInset,
                                start = LiquidBottomBarMetrics.HorizontalInset,
                                end = LiquidBottomBarMetrics.HorizontalInset
                            )
                            .padding(LiquidBottomBarMetrics.AnimationPadding)
                    ) {
                        LiquidBottomTab(onClick = {
                            selectedOption = 0
                            coroutineScope.launch { pagerState.scrollToPage(0) }
                        }) {
                            Icon(
                                painterResource(R.drawable.ic_round_movie_filter_24),
                                contentDescription = stringResource(R.string.anime),
                                modifier = Modifier.size(LiquidBottomBarMetrics.IconSize)
                            )
                        }

                        LiquidBottomTab(onClick = {
                            selectedOption = 1
                            coroutineScope.launch { pagerState.scrollToPage(1) }
                        }) {
                            Icon(
                                painterResource(R.drawable.ic_round_home_24),
                                contentDescription = stringResource(R.string.home),
                                modifier = Modifier.size(LiquidBottomBarMetrics.IconSize)
                            )
                        }

                        if (mangaEnabled) {
                            LiquidBottomTab(onClick = {
                                selectedOption = 2
                                coroutineScope.launch { pagerState.scrollToPage(2) }
                            }) {
                                Icon(
                                    painterResource(R.drawable.ic_round_import_contacts_24),
                                    contentDescription = stringResource(R.string.manga),
                                    modifier = Modifier.size(LiquidBottomBarMetrics.IconSize)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Use ViewPager2 for other themes
            binding.viewpager.visibility = View.VISIBLE
            binding.includedNavbar.root.visibility = View.VISIBLE
            binding.composeMainContent.visibility = View.GONE
            
            val mainViewPager = binding.viewpager
            val navbar = binding.includedNavbar.navbar
            
            mainViewPager.isUserInputEnabled = false
            mainViewPager.adapter = ViewPagerAdapter(supportFragmentManager, lifecycle, pageCount)
            mainViewPager.setPageTransformer(ZoomOutPageTransformer())

            navbar.clearTabs()
            navbar.addTab(navbar.createTab(R.drawable.ic_round_movie_filter_24, R.string.anime))
            navbar.addTab(navbar.createTab(R.drawable.ic_round_home_24, R.string.home))
            if (mangaEnabled) {
                navbar.addTab(
                    navbar.createTab(R.drawable.ic_round_import_contacts_24, R.string.manga)
                )
            }
            navbar.setOnTabSelectListener(object : LiquidGlassBottomBar.OnTabSelectListener {
                override fun onTabSelected(
                    oldIndex: Int,
                    oldTab: LiquidGlassBottomBar.Tab?,
                    newIndex: Int,
                    newTab: LiquidGlassBottomBar.Tab
                ) {
                    selectedOption = newIndex
                    mainViewPager.setCurrentItem(newIndex, false)
                }
            })
            navbar.selectTabAt(selectedOption)

            if (mainViewPager.currentItem != selectedOption) {
                mainViewPager.post {
                    mainViewPager.setCurrentItem(selectedOption, false)
                }
            }
        }
    }


    private class ViewPagerAdapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle,
        private val pages: Int
    ) : FragmentStateAdapter(fragmentManager, lifecycle) {

        override fun getItemCount(): Int = pages

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> OfflineAnimeFragment()
                2 -> OfflineMangaFragment()
                else -> OfflineFragment()
            }
        }
    }
}

