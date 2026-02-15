package com.saulhdev.feeder.manager.service

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.libraries.gsa.d.a.OverlayController
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.saulhdev.feeder.NeoApp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.manager.sync.SyncRestClient
import com.saulhdev.feeder.ui.feed.FeedAdapter
import com.saulhdev.feeder.ui.theme.CardTheme
import com.saulhdev.feeder.ui.theme.OverlayThemeHolder
import com.saulhdev.feeder.ui.views.AbstractFloatingView
import com.saulhdev.feeder.utils.extensions.isDark
import com.saulhdev.feeder.utils.extensions.setCustomTheme
import com.saulhdev.feeder.viewmodels.ArticleListViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject

class OverlayView(
    val context: Context,
) : OverlayController(context, R.style.AppTheme, R.style.WindowTheme),
    KoinComponent,
    OverlayBridge.OverlayBridgeCallback {
    private lateinit var themeHolder: OverlayThemeHolder
    private val syncScope = CoroutineScope(Dispatchers.IO) + CoroutineName("NeoFeedSync")
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val viewModel: ArticleListViewModel by inject(ArticleListViewModel::class.java)
    private val articles: SyncRestClient by inject(SyncRestClient::class.java)
    val prefs: FeedPreferences by inject()

    private lateinit var rootView: View
    private lateinit var adapter: FeedAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootView =
            View.inflate(
                ContextThemeWrapper(this, R.style.AppTheme),
                R.layout.overlay_layout,
                this.container,
            )
        val mainContainer = rootView.findViewById<ViewGroup>(R.id.overlay_root)
        AbstractFloatingView.container = mainContainer
        AbstractFloatingView.closeAllOpenViews(context)

        themeHolder = OverlayThemeHolder(this)
        setTheme(force = null)
        val bgColor = themeHolder.currentTheme.get(CardTheme.Colors.OVERLAY_BG.ordinal)
        val color = (prefs.overlayTransparency.getValue() * 255.0f).toInt() shl 24 or (bgColor and 0x00ffffff)
        getWindow().setBackgroundDrawable(ColorDrawable(color))

        initRecyclerView()
        refreshNotifications()

        syncScope.launch {
            viewModel.articleListState.collect {
                mainScope.launch {
                    adapter.replace(it.articles)
                    mainScope.launch {
                        rootView.findViewById<SwipeRefreshLayout>(R.id.swipe_to_refresh).isRefreshing =
                            it.isSyncing
                    }
                }
            }
        }
        syncScope.launch {
            prefs.overlayTheme.get().collect {
                mainScope.launch {
                    applyNewTheme(it)
                }
            }
        }
        NeoApp.bridge.setCallback(this)
    }

    override fun closePanelIfNeeded(flags: Int) {
        super.closePanelIfNeeded(flags)
        AbstractFloatingView.closeAllOpenViews(context)
    }

    override fun onBackPressed() {
        if (AbstractFloatingView.isAnyOpen()) {
            AbstractFloatingView.closeAllOpenViews(context)
            return
        } else {
            super.onBackPressed()
        }
    }

    private fun updateTheme(force: String? = null) {
        setTheme(force)
        updateStubUi()
        adapter.setTheme(themeHolder.currentTheme)
    }

    private fun setTheme(force: String?) {
        themeHolder.setTheme(
            when (force ?: prefs.overlayTheme.getValue()) {
                "auto_system_black" -> CardTheme.getThemeBySystem(context, true)
                "auto_system" -> CardTheme.getThemeBySystem(context, false)
                "dark" -> CardTheme.defaultDarkThemeColors
                "black" -> CardTheme.defaultBlackThemeColors
                else -> CardTheme.defaultLightThemeColors
            },
        )
        setCustomTheme()
    }

    private fun updateStubUi() {
    }

    private fun initRecyclerView() {
        val recyclerView = rootView.findViewById<RecyclerView>(R.id.recycler)
        val buttonReturnToTop =
            rootView.findViewById<FloatingActionButton>(R.id.button_return_to_top).apply {
                visibility = View.GONE
                setOnClickListener {
                    visibility = View.GONE
                    recyclerView.smoothScrollToPosition(0)
                }
            }

        val swipeRefresh = rootView.findViewById<SwipeRefreshLayout>(R.id.swipe_to_refresh)
        val isDark = themeHolder.currentTheme.get(CardTheme.Colors.OVERLAY_BG.ordinal).isDark()
        if (isDark) {
            swipeRefresh.setColorSchemeColors(android.graphics.Color.WHITE)
            swipeRefresh.setProgressBackgroundColorSchemeColor(0xFF333333.toInt())
        }
        swipeRefresh.setOnRefreshListener {
            rootView.findViewById<RecyclerView>(R.id.recycler).recycledViewPool.clear()
            refreshNotifications()
        }

        adapter = FeedAdapter()
        val screenWidthDp =
            context.resources.displayMetrics.widthPixels /
                context.resources.displayMetrics.density
        val spanCount = (screenWidthDp / 360).toInt().coerceAtLeast(1)
        recyclerView.apply {
            layoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
            adapter = this@OverlayView.adapter
        }

        recyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(
                    recyclerView: RecyclerView,
                    dx: Int,
                    dy: Int,
                ) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as StaggeredGridLayoutManager
                    val firstPositions = layoutManager.findFirstCompletelyVisibleItemPositions(null)
                    val firstVisible = firstPositions.minOrNull() ?: 0
                    if (firstVisible < 5) {
                        buttonReturnToTop.visibility = View.GONE
                    } else if (firstVisible > 5) {
                        buttonReturnToTop.visibility = View.VISIBLE
                    }
                }
            },
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        NeoApp.bridge.setCallback(null)
    }

    override fun onScroll(f: Float) {
        super.onScroll(f)

        val bgColor = themeHolder.currentTheme.get(CardTheme.Colors.OVERLAY_BG.ordinal)
        val color =
            (prefs.overlayTransparency.getValue() * 255.0f).toInt() shl 24 or (bgColor and 0x00ffffff)
        getWindow().setBackgroundDrawable(ColorDrawable(color))
    }

    override fun onClientMessage(action: String) {
        if (prefs.debugging.getValue()) {
            Log.d("OverlayView", "New message by OverlayBridge: $action")
        }
    }

    override fun applyNewTheme(value: String) {
        updateTheme(value)
    }

    override fun applyNewTransparency(value: Float) {
        themeHolder.prefs.overlayTransparency.setValue(value)
    }

    override fun applyCompactCard(value: Boolean) {
        adapter = FeedAdapter()
        adapter.setTheme(themeHolder.currentTheme)
        rootView.findViewById<RecyclerView>(R.id.recycler).adapter = adapter
        refreshNotifications()
    }

    private fun refreshNotifications() {
        syncScope.launch {
            articles.syncAllFeeds()
        }
    }
}
