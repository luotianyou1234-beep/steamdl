package com.steamdl.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steamdl.app.data.network.SiteApi
import kotlinx.coroutines.launch

/**
 * 「游戏」页：搜索 Steam图书馆 游戏库 → 点卡片取网盘分享链接 → 交给解析流程。
 *
 * 链接获取走站点 PoW guard（SiteApi 内部完成），拿到的分享仍须经
 * ResolveViewModel 的「登录 + 转存」流程才能下载 —— 与网页端口径一致。
 */
class SiteViewModel : ViewModel() {

    var query by mutableStateOf("")

    var searching by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var items by mutableStateOf<List<SiteApi.GameCard>>(emptyList())
        private set

    var page by mutableIntStateOf(1)
        private set

    var pages by mutableIntStateOf(1)
        private set

    var searched by mutableStateOf(false)
        private set

    val hasMore: Boolean get() = page < pages

    /** 当前打开详情卡片；非空时页面弹底栏展示网盘按钮 */
    var selected by mutableStateOf<SiteApi.GameCard?>(null)
        private set

    var linksLoading by mutableStateOf(false)
        private set

    var links by mutableStateOf<SiteApi.GameLinks?>(null)
        private set

    var linksError by mutableStateOf<String?>(null)
        private set

    fun search() {
        if (searching) return
        page = 1
        runSearch(page = 1, append = false)
    }

    fun loadMore() {
        if (searching || !hasMore) return
        runSearch(page = page + 1, append = true)
    }

    private fun runSearch(page: Int, append: Boolean) {
        searching = true
        error = null
        viewModelScope.launch {
            when (val r = SiteApi.search(query, page)) {
                is SiteApi.Result.Success -> {
                    items = if (append) items + r.data.items else r.data.items
                    this@SiteViewModel.page = r.data.page
                    pages = r.data.pages
                    searched = true
                }
                is SiteApi.Result.Failure -> {
                    if (!append) items = emptyList()
                    error = r.message
                    searched = true
                }
            }
            searching = false
        }
    }

    /** 打开游戏卡片：取链接 + 记一次浏览（与网页端同口径） */
    fun openGame(card: SiteApi.GameCard) {
        selected = card
        links = null
        linksError = null
        linksLoading = true
        viewModelScope.launch {
            launch { SiteApi.hit(card.id) }
            when (val r = SiteApi.links(card.id)) {
                is SiteApi.Result.Success -> links = r.data
                is SiteApi.Result.Failure -> linksError = r.message
            }
            linksLoading = false
        }
    }

    fun retryLinks() {
        val card = selected ?: return
        linksError = null
        linksLoading = true
        viewModelScope.launch {
            when (val r = SiteApi.links(card.id)) {
                is SiteApi.Result.Success -> links = r.data
                is SiteApi.Result.Failure -> linksError = r.message
            }
            linksLoading = false
        }
    }

    /** 用户点了某个网盘按钮：上报一次「打开下载面板」统计 */
    fun reportOpen() {
        val card = selected ?: return
        viewModelScope.launch { SiteApi.open(card.id) }
    }

    fun closeGame() {
        selected = null
        links = null
        linksError = null
        linksLoading = false
    }
}
