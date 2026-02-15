/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Saul Henriquez <henriquez.saul@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.saulhdev.feeder.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.manager.sync.SyncRestClient
import com.saulhdev.feeder.ui.components.ArticleItem
import com.saulhdev.feeder.ui.components.PullToRefreshLazyColumn
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.CaretUp
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.utils.extensions.launchView
import com.saulhdev.feeder.utils.openLinkInCustomTab
import com.saulhdev.feeder.viewmodels.ArticleListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)
@Composable
fun ArticleListPage(
    prefs: FeedPreferences = koinInject(),
    syncClient: SyncRestClient = koinInject(),
    viewModel: ArticleListViewModel = koinNeoViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val paneNavigator = rememberListDetailPaneScaffoldNavigator<Any>()
    val articleId = remember { mutableStateOf("") }

    val state by viewModel.articleListState.collectAsState()

    val gridState = rememberLazyStaggeredGridState()
    val showFAB by remember { derivedStateOf { gridState.firstVisibleItemIndex > 4 } }

    NavigableListDetailPaneScaffold(
        navigator = paneNavigator,
        listPane = {
            AnimatedPane {
                Scaffold(
                    containerColor = Color.Transparent,
                    floatingActionButton = {
                        AnimatedVisibility(
                            visible = showFAB,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            FloatingActionButton(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = {
                                    scope.launch {
                                        gridState.animateScrollToItem(0)
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Phosphor.CaretUp,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                ) { paddingValues ->
                    PullToRefreshLazyColumn(
                        isRefreshing = state.isSyncing,
                        onRefresh = syncClient::syncAllFeeds,
                        modifier = Modifier.padding(paddingValues),
                        content = {
                            items(state.articles, key = { it.id }) { item ->
                                ArticleItem(
                                    article = item,
                                    onBookmark = {
                                        viewModel.bookmarkArticle(item.id, it)
                                    },
                                ) {
                                    if (prefs.openInBrowser.getValue()) {
                                        context.launchView(item.link)
                                    } else {
                                        if (prefs.offlineReader.getValue()) {
                                            scope.launch {
                                                paneNavigator.navigateTo(
                                                    ListDetailPaneScaffoldRole.Detail,
                                                    item.id,
                                                )
                                            }
                                        } else {
                                            openLinkInCustomTab(
                                                context,
                                                item.link,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        },
        detailPane = {
            articleId.value =
                paneNavigator.currentDestination
                    ?.takeIf { it.pane == this.paneRole }
                    ?.contentKey
                    ?.toString()
                    .orEmpty()

            articleId.value.takeIf { it.isNotEmpty() }?.let { id ->
                AnimatedPane {
                    ArticlePage(id) {
                        scope.launch {
                            paneNavigator.navigateBack()
                        }
                    }
                }
            }
        },
    )
}
