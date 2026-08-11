# HandyPage

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://github.com/Cristallin2006/HandyPage)
[![Release](https://img.shields.io/github/v/release/Cristallin2006/HandyPage)](https://github.com/Cristallin2006/HandyPage/releases)

**读免费英文报刊，点词即查，AI 私教随身 —— 给英语学习者的 Android 阅读器。**

无后端、无账号、无广告。数据全在本机，AI 只花你自己的 API 余额。

## 快速开始

1. 在 [Releases](https://github.com/Cristallin2006/HandyPage/releases) 下载 APK 安装
2. 打开即读：「阅读」页选源 → 点文章，无需任何配置
3. （可选）「设置 → AI 配置」填入 OpenAI 兼容 Key（如 DeepSeek），解锁双语翻译和 AI 私教

## 功能一览

**📰 读什么**

- 19 个免费英文源：新闻、科普、文学、杂志，国内直连，不需要代理
- arXiv 论文：按分类浏览，关键词搜索
- 文章图片随抓取离线保存，没网也能读

**✏️ 怎么学**

- 选中单词即出释义：离线词典 + 词形还原，查词不打断阅读
- 查过的生词在正文里弱高亮；标「已掌握」后高亮自动消失
- 生词本：同词聚合、搜索、掌握度筛选、批量管理、一键导出 Anki
- 好句收藏 + 笔记，文章和论文都能星标
- 论文双语对照翻译：逐段对照，公式原样渲染

**🤖 AI 私教**

- 导读、精讲单词、拆解长句、随问随答
- 论文可以整篇通读：按大纲逐节深入，不是只读个摘要
- 按文章记住对话，下次打开接着聊

**🎨 看起来**

- 报纸式排版：刊头、衬线标题、花体底栏
- 6 款主题色，夜间阅读模式不刺眼

## 技术亮点

- 源抓取引擎改造自 calibre recipe 体系，配置化 DSL，全源经直连可达性审计
- 双轨阅读：EPUB 重排（Readium）+ PDF 原版（pdf.js 文本层桥接全部学习功能）
- 翻译管线：分块并发 + Room 持久缓存，250 段论文约 99 秒；译文公式按 alttext 匹配还原真数学渲染
- 论文 AI 记忆：大纲索引 + 分节钻取 + 文内检索（端侧替代向量 RAG），工具轮次按论文场景分档
- arXiv 官方字段查询语法直通（`ti:` / `au:` / `cat:` / 布尔）+ 限流闸 / 指数退避 / 查询缓存
- 290+ JVM 单元测试，纯函数核心（分组 / 导出 / 查询构造 / 索引）全部可测

## 技术栈

Kotlin · Jetpack Compose · Room · OkHttp · Readium Kotlin Toolkit · pdf.js · pdfbox-android · jsoup · Markwon

## 开源声明

本项目的源抓取架构改造自 **calibre** 的 recipe 体系（GPLv3）；阅读渲染基于 **Readium**（BSD-3）与 **pdf.js**（Apache-2.0）；PDF 重排算法移植自 **OpenDataLoader PDF**（Apache-2.0），文本抽取用 **pdfbox-android**（Apache-2.0）；离线词典为 **ECDICT**（MIT）；字体 Fraunces / Pinyon Script（SIL OFL）。完整归属见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

抓取内容的版权归原出版方所有，仅供个人学习使用。

## License

[GPLv3](LICENSE)。源抓取体系衍生自 GPLv3 的 calibre，依 copyleft 要求本项目整体以 GPLv3 发布。
