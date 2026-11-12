package com.steamdl.app.data.repository

import com.steamdl.app.data.network.C139Api
import com.steamdl.app.data.network.C139Constants
import com.steamdl.app.data.network.ShareLinkParser
import com.steamdl.app.data.network.model.DownloadLink
import com.steamdl.app.data.network.model.ShareFile
import com.steamdl.app.data.network.model.ShareSession

/**
 * 139（和彩云）分享解析仓库：cookie → getOutLinkInfoV6 列目录 → getContentInfoFromOutLink 直链。
 * 139 分享无需转存（share host 直接列目录 + 取直链），credential 为登录 Cookie（含账号信息）；
 * authorization 从 cookie 提取，分享接口按需携带（可空）。
 * 列目录允许游客（cookie 为空）：getOutLinkInfoV6 是匿名端点；取直链仍要求登录。
 */
class C139ResolveRepository(private val api: C139Api) : ShareResolveRepository {

    override suspend fun createSession(link: String, pwd: String?, cookie: String): Result<ShareSession> {
        val parsed = ShareLinkParser.parse(link)
            ?: return Result.failure(IllegalArgumentException("无法识别分享链接"))
        // 游客模式：不再要求 cookie 里含账号信息 —— 139 的列表端点（getOutLinkInfoV6）本身就是
        // 匿名调用（account 固定空串、不带 authorization/mcloud-sign），空 cookie 也能列出目录。
        // 下载/转存仍会在各自入口要求登录。
        return runCatching {
            // 139 分享无 token：shareId 即 linkID，stoken 暂存提取码
            // 密码优先级：用户手输 > 139 getOutLinkGeneral 明文回吐的 passwd（避免下载因缺密码报 9188）
            val leakedPwd = api.getOutLinkPassword(parsed.shareId)
            val passwd = pwd?.takeIf { it.isNotBlank() } ?: leakedPwd.orEmpty()
            // 标题从 getOutLinkGeneral 拿（失败回退短链 ID）
            val title = api.getOutLinkTitle(parsed.shareId)
                ?.takeIf { it.isNotBlank() } ?: parsed.shareId
            ShareSession(parsed.shareId, passwd, title)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )
    }

    override suspend fun listFiles(session: ShareSession, dirFid: String, cookie: String): Result<List<ShareFile>> =
        runCatching {
            // 列表端点为匿名调用（§9530修复文档 §3）：无需 authorization/account，Api 内部走匿名请求
            // pCaID 根目录必须传 "root"（§16.2：空串会报 pCaID不能为空），子目录传父 caID / coID
            val pcaId = if (dirFid == "0" || dirFid.isBlank()) "root" else dirFid
            // passwd = 分享提取码（createSession 时存入 stoken；无则空串）
            val all = mutableListOf<ShareFile>()
            var begin = 1
            do {
                val batch = api.getShareFiles(session.shareId, pcaId, session.stoken, begin, begin + 199)
                all += batch
                begin += 200
            } while (batch.size == 200 && begin <= 20_000)
            all
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )

    /**
     * SteamDL：139 下载必须先转存到用户自己的网盘（站点的转存是收入来源），
     * 临时转存目录复用「SteamDL临时转存」，每次取链在其下建唯一子目录。
     */
    override suspend fun ensureTempDir(cookie: String): Result<String> = runCatching {
        api.listFolders("/", cookie).firstOrNull { it.isdir && it.fname == TEMP_DIR_NAME }?.fid
            ?: api.createDir("/", TEMP_DIR_NAME, cookie)
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun transferFile(
        session: ShareSession,
        file: ShareFile,
        toDirFid: String,
        cookie: String,
        skipSpaceCheck: Boolean
    ): Result<String> = runCatching {
        val account = C139Constants.extractAccountFull(cookie)
            ?: throw IllegalStateException("登录态缺少账号信息，请重新登录")
        val authorization = C139Constants.extractAuthorization(cookie)
        // 139 转存：创建批量任务（AES 加密接口）→ 轮询查询结果 → 返回转存后新 fileId
        // 转存前置空间校验：空间不足直接抛出，不再走后面的转存与轮询（避免被误报「转存超时」）；
        // 批量入口已做过整批预算校验时跳过（避免同一批多次查配额）
        if (!skipSpaceCheck) {
            TransferSpaceGuard.ensureEnoughSpace(file.fsize.takeIf { it > 0 }, "139") { api.getQuota(cookie) }
        }
        val taskId = api.createTransferTask(
            coIDLst = listOf(file.fid),
            catalogIDLst = emptyList(),
            toFolderId = toDirFid,
            linkID = session.shareId,
            account = account,
            authorization = authorization
        ) ?: throw IllegalStateException("创建转存任务失败")
        var newId: String? = null
        for (i in 0 until 30) {
            kotlinx.coroutines.delay(800)
            val result = api.queryTransferTask(taskId, account, authorization)
            if (result.done) {
                newId = result.mapping[file.fid]
                break
            }
        }
        newId ?: throw IllegalStateException("转存超时或失败")
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    /** 个人盘文件直链（转存后的新 fileId 走这里） */
    override suspend fun getDownloadLink(fid: String, cookie: String): Result<DownloadLink> = runCatching {
        api.getDownloadUrl(fid, cookie)
            ?: throw IllegalStateException("获取下载链接失败")
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    /** 批量转存前的整批空间预算校验：只查一次配额，不足时抛出（139 转存占用目标账号空间） */
    override suspend fun ensureBatchSpace(sizes: List<Long>, credential: String): Boolean =
        TransferSpaceGuard.ensureEnoughSpaceForBatch(sizes, "139") { api.getQuota(credential) }

    /**
     * SteamDL：139 分享下载一律先转存到用户自己网盘的临时子目录，再取个人盘直链；
     * 临时子目录由 cleanupDirFid 带回，下载完成后删除（转存是站点的收入来源，不走分享直链）。
     */
    override suspend fun getShareDownloadLink(
        session: ShareSession,
        file: ShareFile,
        cookie: String,
        skipSpaceCheck: Boolean
    ): Result<DownloadLink> = runCatching {
        val baseDir = ensureTempDir(cookie).getOrThrow()
        val subDir = api.createDir(baseDir, "tr_${System.currentTimeMillis()}", cookie)
        val newId = transferFile(session, file, subDir, cookie, skipSpaceCheck).getOrThrow()
        val link = api.getDownloadUrl(newId, cookie)
            ?: throw IllegalStateException("获取下载链接失败")
        link.copy(
            filename = file.fname.ifBlank { link.filename },
            cleanupDirFid = subDir
        )
    }.fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

    /** 下载完成/放弃后删除临时转存子目录（含其中的转存文件）。 */
    override suspend fun cleanupTempDir(dirFid: String, cookie: String) {
        if (dirFid.isBlank()) return
        runCatching { api.deleteFiles(listOf(dirFid), cookie) }
    }

    private companion object {
        const val TEMP_DIR_NAME = "SteamDL临时转存"
    }
}
