# HandyPage

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://github.com/Cristallin2006/HandyPage)
[![Release](https://img.shields.io/github/v/release/Cristallin2006/HandyPage)](https://github.com/Cristallin2006/HandyPage/releases)

**An offline-first English-reading app for language learners — free magazines & papers, tap-to-lookup dictionary, and a BYOK AI tutor, all on-device.**

面向英语学习者的 Android 阅读器：直连抓取免费英文期刊 / 报纸 / 论文，划词即查，收藏好句，AI 私教随问随答。无后端、无账号，数据全在本机。

## 下载

GitHub [Releases](https://github.com/Cristallin2006/HandyPage/releases) 页提供签名 APK，直接安装即可（与开发版 `HandyPage Dev` 可共存）。

## 功能

### 内容获取

- **多源抓取**：内置 19 个英文源（新闻 / 科普 / 文学 / 杂志），calibre recipe 体系改编的源配置 DSL；全部源经国内直连可达性审计，无需代理
- **图片离线内嵌**：抓取期下载配图并重写进 EPUB（含防盗链头组、webp/avif 转码、失败远程兜底），缓存文章离线可读
- **arXiv 论文源**：分类全集浏览；搜索支持官方字段语法（`ti:` / `au:` / `cat:` / 布尔直通）；内置限流闸 + 指数退避 + 查询缓存，慢链与 429 自动恢复

### 阅读

- **双轨阅读**：EPUB 重排视图（Readium，两端对齐、主题 / 字号 / 页边距可调）+ PDF 原版视图（pdf.js，保留论文原始排版，捏合缩放）
- **沉浸式双语翻译**：arXiv HTML 版逐段对照翻译——分块批处理 + Room 持久缓存（换模型不串缓存），约 250 段论文 99 秒完成；译文中的公式自动替换为原文数学渲染
- **夜读模式**：去眩光暗色（非纯黑纯白满对比），阅读器 chrome、词卡面板随阅读主题整体换肤

### 学习工具

- **划词查词**：选中即弹离线词卡（ECDICT condensed 离线词库 + 词形还原），非模态不抢焦点；生词在正文弱高亮，已掌握的词自动退出高亮
- **生词本管理**：同词跨文章聚合（×N 出处计数）、搜索 / 掌握度三档（新词 / 学习中 / 已掌握）/ 多维排序、长按批量操作、CSV 导出（可直接导入 Anki）
- **收藏体系**：文章 + 论文星标混排、好句收藏（重开文章文内下划线标出）、手动注释 + AI 拆解笔记

### AI 私教（BYOK）

- **自带 API Key**（DeepSeek 等 OpenAI 兼容接口），费用只有自己的 API 账单；token 用量逐笔记账 + 日预算闸门
- **按文章持久化会话**：导读、单词精讲、长句拆解、自由问答，流式输出；工具调用（查词 / 搜文章 / 收藏偏好）代码侧执行，UI 只信数据库回执
- **论文通读**：大纲索引 + 分节钻取 + 文内检索三工具，AI 能逐节读完整篇论文而非只见开头；工具轮次按论文场景放宽

### 界面

- **编辑排印设计系统**：报刊式刊头、Fraunces 衬线展示字体、Pinyon Script 花体底栏字标，全局统一过渡动效
- **六款界面主题**：经典墨 / 森林绿 / 勃艮第红 / 普鲁士蓝 / 鸢尾花 / 燕麦拿铁，各带暗色变体，设置页一键切换

## 技术栈

Kotlin · Jetpack Compose · Room · OkHttp · Readium Kotlin Toolkit · pdf.js · pdfbox-android · jsoup · Markwon

## 开源声明

本项目的源抓取架构改造自 **calibre** 的 recipe 体系（GPLv3）；阅读渲染基于 **Readium**（BSD-3）与 **pdf.js**（Apache-2.0）；PDF 重排算法移植自 **OpenDataLoader PDF**（Apache-2.0），文本抽取用 **pdfbox-android**（Apache-2.0）；离线词典为 **ECDICT**（MIT）；字体 Fraunces / Pinyon Script（SIL OFL）。完整归属见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

抓取内容的版权归原出版方所有，仅供个人学习使用。

## License

[GPLv3](LICENSE)。源抓取体系衍生自 GPLv3 的 calibre，依 copyleft 要求本项目整体以 GPLv3 发布。
