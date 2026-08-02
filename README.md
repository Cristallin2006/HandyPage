# HandyPage

**An offline-first English-reading app for language learners — free magazines & papers, tap-to-lookup dictionary, and a BYOK AI tutor, all on-device.**

面向英语学习者的 Android 阅读器：直连抓取免费英文期刊 / 报纸 / 论文，划词即查，收藏好句，AI 私教随问随答。无后端、无账号，数据全在本机。

## 功能

- **多源抓取**：内置英文期刊、报纸、科普、文学杂志等免费源（RSS / HTML 索引），外加 arXiv 论文源；源清单经全量可达性审计，国内直连可用
- **双轨阅读**：EPUB 重排视图（Readium，两端对齐、主题/字号可调）+ PDF 原版视图（pdf.js，保留论文原始排版，捏合缩放）
- **划词查词**：选中单词弹离线词卡（ECDICT  condensed 离线词库），自动匹配语法形态；生词在正文弱高亮
- **收藏体系**：文章星标 + 好句收藏，收藏的句子在阅读视图中以下划线标出，支持注释
- **AI 私教（BYOK）**：自带 API Key（DeepSeek 等 OpenAI 兼容接口），单词精讲、长句拆解、自由问答，流式输出；费用只有自己的 API 账单
- **编辑排印 UI**：报刊式刊头、衬线展示字体、花体底栏字标，全局统一过渡动效

## 技术栈

Kotlin · Jetpack Compose · Room · OkHttp · Readium Kotlin Toolkit · pdf.js · pdfbox-android · jsoup · Markwon

## 开源声明

本项目的源抓取架构改造自 **calibre** 的 recipe 体系（GPLv3）；阅读渲染基于 **Readium**（BSD-3）与 **pdf.js**（Apache-2.0）；PDF 重排算法移植自 **OpenDataLoader PDF**（Apache-2.0），文本抽取用 **pdfbox-android**（Apache-2.0）；离线词典为 **ECDICT**（MIT）；字体 Fraunces / Pinyon Script（SIL OFL）。完整归属见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

抓取内容的版权归原出版方所有，仅供个人学习使用。

## License

[GPLv3](LICENSE)。源抓取体系衍生自 GPLv3 的 calibre，依 copyleft 要求本项目整体以 GPLv3 发布。
