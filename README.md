<div align="center">

<img src="desktop/src/main/resources/icon.png" width="120" alt="SteamDL" />

# SteamDL · Steam图书馆下载器

**Steam图书馆（steamdownload.cn）配套的网盘高速下载工具**

粘贴站内网盘分享链接 → 登录自己的网盘账号 → 转存到个人网盘 → 多线程高速下载；
也支持磁力 / BT 种子 / ed2k 等 P2P 链接下载。

[![License](https://img.shields.io/badge/license-AGPL--3.0-blue?style=flat-square)](./LICENSE)
[![Platform](https://img.shields.io/badge/platform-Windows-0078D6?style=flat-square&logo=windows&logoColor=white)](#构建)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)

</div>

***

> **本项目使用 AGPL-3.0 开源协议。** 本项目由开源项目
> [CYQawa/YunX](https://github.com/CYQawa/YunX)（Android）与
> [tidain/YunX-Desktop](https://github.com/tidain/YunX-Desktop)（Windows 桌面移植）二次开发，
> 同样以 AGPL-3.0 开放源代码；若你使用了本项目的代码，请遵守同一协议。

## 简介

SteamDL 是 [Steam图书馆](https://steamdownload.cn) 的 Windows 桌面端下载工具。

- **网盘分享下载**：支持夸克 / 迅雷 / 百度 / 139 / 光鸭五家网盘的分享链接。
  粘贴分享链接后自动识别平台，登录自己的网盘账号、把分享文件转存到个人网盘，
  再取直链高速下载 —— 转存动作不可跳过（这也是网站资源可持续分享的前提）。
- **高速分片下载**：Range 分片并发 + 断点续传 + 慢连接自动换线，最多 32 线程。
- **P2P 下载**：磁力 / BT 种子 / ed2k 交给内置 Gopeed 引擎。
- **多账号**：各网盘支持多账号（主账号 / 备注），凭证加密存本机，可导出加密备份。
- **视频链接解析**（沿自上游）：哔哩哔哩 / 小红书 / 抖音 / 微博 / 快手 / AcFun / Pixiv。

## 使用

1. 解压便携版或安装安装版，启动 SteamDL。
2. 在「网盘」页登录自己的网盘账号（扫码 / Cookie / 账密，凭证只存本机）。
3. 复制站内游戏页给出的网盘分享链接，回到 SteamDL 粘贴并解析。
4. 选中要下载的文件 → 下载。应用会自动把文件转存到你自己的网盘临时目录，
   取得直链后开始多线程下载，完成后自动清理转存副本。

## 构建

需要 JDK 17（`run.ps1` 会自动定位 / 下载）：

```powershell
.\run.ps1 build      # 编译
.\run.ps1 run        # 编译并启动
.\run.ps1 package    # 免安装便携版 → release\SteamDL\
.\run.ps1 installer  # 安装程序   → release\SteamDL-setup-*.exe
```

详见 [docs/BUILD.md](docs/BUILD.md)。

## 浏览器插件

`browser-extension/` 是配套的 Chromium MV3 扩展（嗅探网页资源交给 SteamDL 下载）。
以「加载已解压的扩展程序」方式安装即可。

## 许可与致谢

- 许可证：[AGPL-3.0](./LICENSE)
- 上游：[CYQawa/YunX](https://github.com/CYQawa/YunX) · [tidain/YunX-Desktop](https://github.com/tidain/YunX-Desktop)
- 上游差异说明：[docs/UPSTREAM-DIFF.md](docs/UPSTREAM-DIFF.md)
