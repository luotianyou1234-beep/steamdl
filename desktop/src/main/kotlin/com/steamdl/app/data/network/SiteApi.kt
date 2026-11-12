package com.steamdl.app.data.network

import com.steamdl.app.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Steam图书馆（steamdownload.cn）站内游戏搜索与网盘链接获取。
 *
 * 用到的接口（与网页端同一套，见 game-site/server/src）：
 * - `GET /api/games?q=&page=&size=`：公开搜索，返回卡片数据（封面 / 名称 / 大小 / 评分）；
 * - `GET /api/games/:id/links`：网盘分享链接（含提取码），需要 `X-Guard` token；
 * - `GET|POST /api/guard`：链接防护 —— 服务端出一个 PoW 挑战，
 *   客户端找到 nonce 使 `sha256("$c:$nonce")` 前 16 位为 0 后换回 token
 *   （绑定来源 IP，有效期约 2 小时；网页端同样走这条路）；
 * - `POST /api/games/:id/hit?k=click`、`POST /api/games/:id/open`：
 *   浏览 / 下载统计，与网页端口径一致，保证站内热度统计不丢。
 *
 * 注意点：
 * - 服务端 UA 黑名单含 okhttp / java / python 等字样，UA 必须固定为 [UA]；
 * - `/api/games/:id/links` 要求带 `Accept-Language` 头（无此头一律 403）；
 * - 拿到的仍只是「分享链接」，下载前用户必须先在应用内登录对应网盘并转存
 *   （ResolveViewModel 已强制此流程），这里不涉及任何免转存通道。
 */
object SiteApi {

    const val BASE_URL = "https://steamdownload.cn"
    private const val TAG = "SteamDL-Site"

    /** 桌面端固定 UA：不能含服务端 BOT_RE 命中的字样（okhttp / java / curl / python…） */
    private const val UA = "SteamDL-Desktop/1.0 (+https://steamdownload.cn)"

    private val JSON_MEDIA = "application/json".toMediaType()

    /** 搜索结果里的一张游戏卡片（/api/games 的 items[] 子集） */
    data class GameCard(
        val id: Int,
        val name: String,
        val nameCn: String?,
        val coverUrl: String?,
        val releaseYear: Int?,
        val sizeText: String?,
        val ratingDesc: String?,
        val version: String?,
    ) {
        val displayName: String get() = nameCn?.takeIf { it.isNotBlank() } ?: name
    }

    data class GameList(
        val items: List<GameCard>,
        val total: Int,
        val page: Int,
        val pages: Int,
    )

    /** 一个可转存下载的网盘分享 */
    data class DriveLink(
        val key: String,
        val name: String,
        val url: String,
        val code: String?,
    )

    /** 一条补丁 / 更新下载（server repo.ts 的 GamePatch：title 必有，各盘分享可选） */
    data class PatchEntry(
        val title: String,
        val version: String?,
        val size: String?,
        val note: String?,
        val drives: List<DriveLink>,
    )

    /** /api/games/:id/links 的解析结果：网盘列表 + 补丁 + 可选磁力 + 解压密码 */
    data class GameLinks(
        val drives: List<DriveLink>,
        val patches: List<PatchEntry>,
        val magnet: String?,
        val unzipPassword: String?,
    )

    sealed interface Result<out T> {
        data class Success<T>(val data: T) : Result<T>
        data class Failure(val message: String) : Result<Nothing>
    }

    // ---------- guard token ----------

    @Volatile
    private var guardToken: String? = null

    @Volatile
    private var guardExpireAt: Long = 0

    private val tokenLock = Any()

    /** 搜索游戏（公开接口，无需 guard） */
    suspend fun search(query: String, page: Int = 1, size: Int = 24): Result<GameList> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            // dedupe=1：同一 steam_appid 多个版本只出一张卡（与首页口径一致）
            val url = "$BASE_URL/api/games?q=$q&page=$page&size=$size&dedupe=1"
            try {
                val obj = getJson(url) ?: return@withContext Result.Failure("搜索请求失败")
                val arr = obj.optJSONArray("items")
                val items = ArrayList<GameCard>(arr?.length() ?: 0)
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        items.add(
                            GameCard(
                                id = o.optInt("id"),
                                name = o.optString("name"),
                                nameCn = o.stringOrNull("nameCn"),
                                coverUrl = o.stringOrNull("coverUrl"),
                                releaseYear = o.intOrNull("releaseYear"),
                                sizeText = o.stringOrNull("sizeText"),
                                ratingDesc = o.stringOrNull("ratingDesc"),
                                version = o.stringOrNull("version"),
                            )
                        )
                    }
                }
                Result.Success(
                    GameList(
                        items = items,
                        total = obj.optInt("total"),
                        page = obj.optInt("page", page),
                        pages = obj.optInt("pages", 1),
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "搜索异常（$url）：${e.message}", e)
                Result.Failure("${e.javaClass.simpleName}: ${e.message ?: "网络异常"}")
            }
        }

    /** 拉取某游戏的网盘分享链接（内部自动完成 PoW guard；401 时刷新 token 重试一次） */
    suspend fun links(gameId: Int): Result<GameLinks> = withContext(Dispatchers.IO) {
        for (attempt in 0..1) {
            val token = guardToken(forceRefresh = attempt > 0)
                ?: return@withContext Result.Failure("人机校验未通过，请稍后重试")
            val url = "$BASE_URL/api/games/$gameId/links"
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", UA)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("X-Guard", token)
                    .get()
                    .build()
                HttpClients.apiClient().newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.code == 401 && attempt == 0) {
                        // token 过期 / IP 变了：清缓存重取一次
                        invalidateToken()
                        return@use
                    }
                    if (resp.code == 403) return@withContext Result.Failure("请求被拒绝（403）")
                    val obj = try {
                        JSONObject(text)
                    } catch (e: Exception) {
                        Log.e(TAG, "links 响应非 JSON（HTTP ${resp.code}，前 100 字=${text.take(100)}）")
                        return@withContext Result.Failure("响应格式异常（HTTP ${resp.code}）")
                    }
                    return@withContext Result.Success(parseLinks(obj))
                }
            } catch (e: Exception) {
                Log.e(TAG, "links 请求异常（$url）：${e.message}", e)
                return@withContext Result.Failure("${e.javaClass.simpleName}: ${e.message ?: "网络异常"}")
            }
        }
        Result.Failure("人机校验未通过，请稍后重试")
    }

    /** 浏览统计（与网页端点开游戏卡同口径）；fire-and-forget，失败静默 */
    suspend fun hit(gameId: Int) = postSilently("$BASE_URL/api/games/$gameId/hit?k=click")

    /** 「打开下载面板」统计（首页「全站已下载 N 次」的口径）；fire-and-forget */
    suspend fun open(gameId: Int) = postSilently("$BASE_URL/api/games/$gameId/open")

    private fun parseLinks(o: JSONObject): GameLinks {
        fun drivesOf(src: JSONObject): List<DriveLink> {
            val drives = ArrayList<DriveLink>(5)
            fun add(key: String, name: String, urlField: String, codeField: String) {
                val url = src.stringOrNull(urlField) ?: return
                drives.add(DriveLink(key, name, url, src.stringOrNull(codeField)))
            }
            // 主字段用 quarkUrl（= quark_content_url 真文件分享）；
            // quarkMagnetUrl 是旧磁力式分享，网页端不展示，这里同样不取
            add("quark", "夸克网盘", "quarkUrl", "quarkCode")
            add("xl", "迅雷云盘", "xunleiUrl", "xunleiCode")
            add("gy", "光鸭网盘", "guangyaUrl", "guangyaCode")
            add("bd", "百度网盘", "baiduUrl", "baiduCode")
            add("yd", "移动云盘", "ydUrl", "ydCode")
            return drives
        }

        // 补丁 / 更新（GamePatch[]）：字段名与主链接不同（quark / xunlei / gy / baidu / yd）
        val patches = ArrayList<PatchEntry>()
        o.optJSONArray("patches")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val title = p.stringOrNull("title") ?: continue
                val pd = ArrayList<DriveLink>(5)
                fun pAdd(key: String, name: String, urlField: String, codeField: String) {
                    val url = p.stringOrNull(urlField) ?: return
                    pd.add(DriveLink(key, name, url, p.stringOrNull(codeField)))
                }
                pAdd("quark", "夸克网盘", "quark", "quarkCode")
                pAdd("xl", "迅雷云盘", "xunlei", "xunleiCode")
                pAdd("gy", "光鸭网盘", "gy", "gyCode")
                pAdd("bd", "百度网盘", "baidu", "baiduCode")
                pAdd("yd", "移动云盘", "yd", "ydCode")
                if (pd.isNotEmpty()) {
                    patches.add(
                        PatchEntry(
                            title = title,
                            version = p.stringOrNull("version"),
                            size = p.stringOrNull("size"),
                            note = p.stringOrNull("note"),
                            drives = pd,
                        )
                    )
                }
            }
        }

        return GameLinks(
            drives = drivesOf(o),
            patches = patches,
            magnet = o.stringOrNull("magnet"),
            unzipPassword = o.stringOrNull("unzipPassword"),
        )
    }

    // ---------- PoW guard ----------

    /**
     * 取一个可用 token：缓存未过期直接返回；否则走「领挑战 → 找 nonce → 换 token」。
     * 服务端语义（guard.ts）：`sha256("$c:$nonce")` 前 16 个 bit 为 0，等价于 hex 前 4 位全 '0'。
     */
    private fun guardToken(forceRefresh: Boolean): String? {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            guardToken?.let { if (now < guardExpireAt - 60_000) return it }
        }
        synchronized(tokenLock) {
            guardToken?.let {
                if (!forceRefresh && System.currentTimeMillis() < guardExpireAt - 60_000) return it
            }
            return solveGuard()
        }
    }

    private fun invalidateToken() {
        synchronized(tokenLock) {
            guardToken = null
            guardExpireAt = 0
        }
    }

    private fun solveGuard(): String? {
        val challengeObj = getJson("$BASE_URL/api/guard") ?: return null
        val c = challengeObj.optString("c")
        if (c.isEmpty()) return null
        val nonce = findNonce(c) ?: return null
        val body = JSONObject().put("c", c).put("n", nonce.toString()).toString()
            .toRequestBody(JSON_MEDIA)
        val req = Request.Builder()
            .url("$BASE_URL/api/guard")
            .header("Accept", "application/json")
            .header("User-Agent", UA)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .post(body)
            .build()
        return try {
            HttpClients.apiClient().newCall(req).execute().use { resp ->
                val obj = JSONObject(resp.body?.string().orEmpty())
                val token = obj.stringOrNull("token")
                if (token != null) {
                    val ttl = obj.optLong("ttl", 2 * 3600_000L)
                    guardToken = token
                    guardExpireAt = System.currentTimeMillis() + ttl
                }
                token
            }
        } catch (e: Exception) {
            Log.e(TAG, "guard POST 异常：${e.message}", e)
            null
        }
    }

    /** 16 个前导零 bit ⇔ sha256 hex 前 4 位全 '0'，期望 ~65k 次哈希，毫秒级 */
    private fun findNonce(c: String): Long? {
        val md = MessageDigest.getInstance("SHA-256")
        var n = 0L
        while (n < 50_000_000L) {
            val digest = md.digest("$c:$n".toByteArray(Charsets.UTF_8))
            if (digest[0].toInt() == 0 && digest[1].toInt() == 0) return n
            n++
        }
        return null
    }

    private fun getJson(url: String): JSONObject? {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", UA)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .get()
            .build()
        return try {
            HttpClients.apiClient().newCall(req).execute().use { resp ->
                JSONObject(resp.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET $url 异常：${e.message}", e)
            null
        }
    }

    private suspend fun postSilently(url: String) = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .post("".toRequestBody(null))
                .build()
            HttpClients.apiClient().newCall(req).execute().close()
        } catch (e: Exception) {
            Log.w(TAG, "统计上报失败（$url）：${e.message}")
        }
        Unit
    }
}

private fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

private fun JSONObject.intOrNull(key: String): Int? =
    if (isNull(key)) null else optInt(key)
