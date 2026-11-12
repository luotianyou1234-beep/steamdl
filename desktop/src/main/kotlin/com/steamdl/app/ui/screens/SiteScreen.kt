package com.steamdl.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.steamdl.app.data.network.SiteApi
import com.steamdl.app.ui.components.RemoteImage
import com.steamdl.app.ui.theme.Brutal
import com.steamdl.app.ui.theme.brutalButton
import com.steamdl.app.ui.theme.brutalCard
import com.steamdl.app.ui.viewmodel.SiteViewModel
import com.steamdl.app.util.DesktopActions

/**
 * 「游戏」页：站内搜索 Steam图书馆 游戏库 → 点卡片 → 选网盘转存下载。
 *
 * 网盘按钮把分享链接喂给解析页（[onResolve]），后续「登录网盘 → 转存 →
 * 多线程下载」全部走既有 ResolveViewModel 流程 —— 不提供免转存通道。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteScreen(
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: SiteViewModel,
    /** (分享链接, 提取码) → 交给解析页开始转存流程 */
    onResolve: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 搜索栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = viewModel.query,
                onValueChange = { viewModel.query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索 Steam图书馆 游戏库…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (viewModel.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() })
            )
            // 站点主按钮同款：黄底 + 黑边 + 硬阴影（不用 M3 Button，走 brutalButton 修饰符）
            Box(
                modifier = Modifier
                    .brutalButton(Brutal.Yellow)
                    .clickable(enabled = viewModel.query.isNotBlank() && !viewModel.searching) {
                        viewModel.search()
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Brutal.Ink
                )
            }
        }

        when {
            viewModel.items.isEmpty() && viewModel.searching -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            viewModel.items.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        viewModel.error != null -> "搜索失败：${viewModel.error}"
                        viewModel.searched -> "没有找到相关游戏"
                        else -> "输入游戏名搜索，选网盘转存后即可高速下载"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(viewModel.items, key = { it.id }) { game ->
                    GameCardView(game = game, onClick = { viewModel.openGame(game) })
                }
                if (viewModel.hasMore) {
                    item(key = "load-more", span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (viewModel.searching) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            } else {
                                TextButton(onClick = { viewModel.loadMore() }) {
                                    Text("加载更多")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 游戏详情底栏：网盘按钮列表
    val game = viewModel.selected
    if (game != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeGame() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            GameLinksSheet(
                game = game,
                loading = viewModel.linksLoading,
                links = viewModel.links,
                error = viewModel.linksError,
                onRetry = { viewModel.retryLinks() },
                onDrive = { drive ->
                    viewModel.reportOpen()
                    viewModel.closeGame()
                    onResolve(drive.url, drive.code)
                }
            )
        }
    }
}

@Composable
private fun GameCardView(game: SiteApi.GameCard, onClick: () -> Unit) {
    // 站点游戏卡同款：白底 + 黑边 + 右下硬阴影
    Column(
        modifier = Modifier
            .brutalCard(Brutal.Card)
            .clickable(onClick = onClick)
    ) {
        RemoteImage(
            url = game.coverUrl,
            contentDescription = game.displayName,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(460f / 215f),
            shape = RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp)
        )
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = game.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val meta = listOfNotNull(
                game.releaseYear?.toString(),
                game.sizeText,
                game.ratingDesc
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = Brutal.Text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GameLinksSheet(
    game: SiteApi.GameCard,
    loading: Boolean,
    links: SiteApi.GameLinks?,
    error: String?,
    onRetry: () -> Unit,
    onDrive: (SiteApi.DriveLink) -> Unit
) {
    var copied by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = game.displayName + (game.version?.let { "（$it）" } ?: ""),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "选择已登录的网盘转存，转存后即可多线程高速下载",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            error != null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "获取链接失败：$error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRetry) { Text("重试") }
            }

            links != null -> {
                links.drives.forEach { drive ->
                    DriveButton(drive = drive, onClick = { onDrive(drive) })
                }
                // 补丁 / 更新下载（站点 GamePatch，字段名与主链接不同，解析见 SiteApi.parseLinks）
                links.patches.forEach { patch ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = patch.title + listOfNotNull(
                            patch.version?.let { "v$it" },
                            patch.size
                        ).let { if (it.isEmpty()) "" else "（" + it.joinToString(" · ") + "）" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    patch.note?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    patch.drives.forEach { drive ->
                        DriveButton(drive = drive, onClick = { onDrive(drive) })
                    }
                }
                if (links.drives.isEmpty() && links.patches.isEmpty() && links.magnet == null) {
                    Text(
                        text = "该游戏暂无可转存的网盘分享",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 磁力链：站内规则是「有网盘分享的磁链要积分解锁」，能到这里的就是免费磁链
                links.magnet?.let { magnet ->
                    TextButton(
                        onClick = {
                            DesktopActions.copyToClipboard(magnet)
                            copied = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (copied) "磁力链接已复制" else "复制磁力链接")
                    }
                }
                links.unzipPassword?.let { pwd ->
                    Text(
                        text = "解压密码：$pwd",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

/** 一个网盘转存按钮：站点五盘各配一个点缀色（与 game-site 的 DRIVE 卡片同套色板） */
@Composable
private fun DriveButton(drive: SiteApi.DriveLink, onClick: () -> Unit) {
    val accent = when (drive.key) {
        "quark" -> Brutal.Blue
        "xl" -> Brutal.Green
        "gy" -> Brutal.Pink
        "bd" -> Brutal.Purple
        "yd" -> Brutal.Orange
        else -> Brutal.Yellow
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .brutalButton(accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.CloudDownload,
            contentDescription = null,
            tint = Brutal.Ink,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = drive.name + (drive.code?.let { "（提取码 $it）" } ?: ""),
            color = Brutal.Ink,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "转存下载",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Brutal.Ink
        )
    }
}
