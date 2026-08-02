# Handypage 主界面设计系统(编辑排印 · Editorial Print)

> 2026-07-28 定稿。来源:qiaomu-design 四方向预览,用户选定 **C · 排印**(拨盘:视觉冒险度 6 / 动效 4 / 密度 6),调整建议:① 「源」改名「阅读」 ② 分类更醒目。
> 适用范围:主界面四 tab(阅读/本机/Agent/设置)+ 底栏 + 共享件(ChatPanel/对话框);**M16 起延伸进阅读器 chrome**(报刊顶栏/Aa 设置面板/查词面板/AI 抽屉,见 §4.11),**M17 起覆盖论文阅读器 chrome 与共享 AgentDrawer**。阅读器正文渲染(Readium/pdf.js 页面内部)不受影响。
> **本文件是主界面视觉唯一事实源;改风格先改本文件。**

## 0. DNA 注入清单(偷了谁的具体值)

| # | 供体 | 偷来的值 | 落点 |
|---|------|----------|------|
| 1 | Notion | whisper border `rgba(0,0,0,.10)` | 全系统 hairline,禁止更重的分隔线(刊头/分类的实墨线除外) |
| 2 | Notion | 暖灰阶 `#f6f5f4 / #615d59 / #a39e98` | 纸感底色与次级文字,禁用冷灰 |
| 3 | Notion | 徽章 pill 999 + `0.125px` 微字距小标签 | 「需代理」徽章 |
| 4 | Notion | 四层低透明度阴影(单层 ≤0.04) | 对话框/弹层,不用单层灰阴影 |
| 5 | Sanity | 单点高饱和重音纪律(#f36458 单用) | 编辑红 `#b3352b` 仅:需代理徽章 + 危险操作 |
| 6 | Sanity | IBM Plex Mono 大写技术标签 | meta/期号行:大写 + 宽字距 11sp |

## 1. 视觉主题与氛围

报纸刊头 + 国际主义平面。纸白底、墨黑字、衬线展示标题、细规则线(rules)做全部分隔。**零圆角卡片分组、零色块选中态、零阴影列表**。记忆点 = 刊头 masthead(衬线双语大标题 + 期号式 meta 行 + 双细线)。

## 2. 色板与角色

**亮色(主)**
- `paper` `#fbfaf7` 背景/表面
- `paperAlt` `#f3f0e9` 次表面(surfaceVariant,徽章底/输入框底)
- `ink` `#141414` 主文字/主按钮/实墨规则线(off-black,禁 #000000)
- `sub` `#5c5c58` 次级文字(onSurfaceVariant)
- `faint` `#a39e98` 占位/第三级文字
- `hairline` `rgba(0,0,0,.10)` 行分隔线
- `red` `#b3352b` 编辑红,**仅**:需代理徽章、删除/危险操作(error 角色复用)

**暗色(随系统)**
- 背景 `#171512`(暖黑,禁纯黑)/ 次表面 `#26231e`
- 主文字 `#ece7db`(≈ 暖白 88%,正文对比 ≥7:1)/ 次级 `#a8a29a`
- hairline `rgba(255,255,255,.14)` / 实墨线用 `#ece7db`
- 编辑红提亮 `#d4574a`

M3 角色映射:`primary=ink`、`onPrimary=paper`、`primaryContainer=paperAlt`、`error=red`、`surface*`=paper 系。

## 3. 排版规则

- **Fraunces**(`res/font/fraunces_semibold.ttf` 600 / `fraunces_bold.ttf` 700,OFL 1.1 于 `assets/fraunces/OFL.txt`):刊头标题 34sp/700、分类标题 18sp/600、衬线 monogram 20sp/600、生词 headword 18sp/600、空态标题 20sp/600。中文自动回落系统栈,英文/数字走衬线。
- **Pinyon Script**(`res/font/pinyon_script.ttf` 子集 ~9KB,OFL 1.1 于 `assets/pinyon_script/OFL.txt`):仅含 R/L/A/S,**唯一用途 = 底栏四项字形**(M23);不得挪作标题/正文,花体在全 App 只此一处。
- **正文/UI**:系统栈,M3 默认档级;中文行高 ≥1.5;字重只用 400/500/600;中西文之间留空格(盘古之白)。
- **meta/标签行**:11sp / 500 / 全大写 / letterSpacing .12em / `sub` 色;数字 tabular-nums。
- **禁斜体**(强调用字重/字号/下划线)。

## 4. 组件样式

1. **EditorialMasthead**:kicker 行(「HANDYPAGE」11sp 大写宽字距 + 右侧工具位)→ 双语标题行(中文 34sp Fraunces 700 + 英文小字大写)→ meta 行(真实计数 + 日期 `MMM D, YYYY` 大写)→ 双细线(2dp ink,间距 3dp,1dp ink)。**工具位挂零高锚点**(0dp + wrapContentHeight unbounded),36dp IconButton 不撑高 kicker 行——跨 tab 标题严格等高。**M22 起四 tab 刊头由壳层托管**:屏内经 `EditorialMastheadSlot(route)` 注册进 `MastheadHost`,MainActivity 在 NavHost 上方统一渲染(§9;位置不动,文字 crossfade);standalone 宿主(SettingsActivity)与设置子页仍内联渲染。
2. **EditorialSectionHeader**(分类更醒目,用户调整项):上行 = 中文衬线 18sp/600 + 英文大写 11sp `sub`(基线对齐)+ 右端计数 12sp tabular;下行 = **1dp 实墨线**(不是 hairline)通栏。
3. **SourceRow**:衬线首字母 monogram(22sp Fraunces 600,无圈无底)+ 源名 15sp/500 + 域名 12sp `sub` + (可选)需代理徽章;行高 60dp;行间 hairline;无卡片。
4. **ProxyBadge**:pill 999,红字 + 红 12% 底 + 红 35% 描边,11sp/600,字距 .125px(Notion 徽章式)。
5. **EditorialNavBar**(自绘,替 M3 NavigationBar):顶 2dp 实墨线;四项字形+文字;选中 = 文字 600 + 文字下 2dp 墨短线;未选 `sub`;**无色块、无 pill**。**M22 起墨短线为单指示器**——逐项透明布局孪生报位,单件 `Animatable` 滑动 200ms(§9),图标/文字色 120ms 渐换,禁逐项跳变。**M23 起字形 = Pinyon Script 花体大写字母**(R=Reading / L=Library / A=Agent / S=Settings,22sp 于 28dp 固定盒内,与刊头英文小字同语义;子集 TTF ~9KB 入 `res/font/pinyon_script.ttf`,OFL 1.1 于 `assets/pinyon_script/OFL.txt`;四方向预览 `design-previews/2026-08-02-tab-icons/` 用户选定 B)。
6. **EditorialTabRow**(本机四段,自绘):文字 15sp;选中 ink/600 + 2dp 下划线;底线通栏 hairline;横向可滚动。**M22 起下划线同为单指示器滑动 200ms**(机制同 §4.5)。
7. **按钮**:主 = ink 填充 4dp 圆角/paper 字;次 = 1dp ink 描边透明底;对话框 paper 底 8dp 圆角 + Notion 四层影(Compose 近似:shadowElevation 12dp)。
8. **ChatPanel 气泡**:4dp 圆角;用户 = ink 底 paper 字;AI = 透明底 + hairline 描边;Markdown 照旧。
9. **EmptyState**(左对齐):Fraunces 600 20sp 标题 + hairline + 引导文 13sp `sub`。Agent 屏必须有空态(修诊断 #7)。
10. **输入框/滑杆/分段钮**:M3 件,圆角收敛 4dp,主色 ink;设置屏分组用 SectionHeader(修诊断 #3 区块头两套写法)。
11. **阅读器 chrome(M16)**:签名动作「底部浮层 = 2dp 实墨顶线」——底栏/查词面板/Aa 面板/AI 抽屉共用。报刊顶栏:kicker 11sp 大写宽字距 + Fraunces 17sp 单行标题 + 无底图标,hairline 底缘,点按显隐。Aa 面板:8dp 顶圆角 + 墨线,分段钮 hairline 框 6dp(选中墨块纸字),高亮五色板(淡墨默认/杏黄/青瓷/黛蓝/编辑红,alpha ≤32%),滑杆墨轨 tabular 输出,无遮罩。查词面板(View 层):纸底+墨顶线无圆角,Fraunces 词头 22sp,音标宽字距**不**大写,墨块/描边按钮 4dp,色板见 `values/colors.xml`(Compose scheme 的 XML 孪生)。
12. **AgentDrawer 与状态图标(M17)**:AI 抽屉 = 全高纯动画浮层(AnimatedVisibility slide,8dp 顶圆角 + 2dp 墨线),**无拖拽手势**;关闭 = 头部「细线圆环 × 钮」(26dp hairline 圆环 + 14dp ×,48dp 触控目标)或系统返回;两个阅读器共用。报刊顶栏同构覆盖论文阅读器(kicker = 来源·分类·年份)。星标等有态图标用**双重编码**:实心墨 = 已收藏/实心灰 = 未收藏(extended 集 `Icons.Outlined.Star` 实为实心造型,靠灰/墨色相区分;M21 起列表行尾/EPUB 顶栏文章星同款);**M22 起四处星标统一 `EditorialStarIcon`**,双态 crossfade 120ms(§9)。
13. **SettingsCategoryRow(M19)**:设置中心页分类行——15sp/500 标题 + 12sp `sub` 实时状态摘要(「两端对齐 · 100% · 羊皮纸」「DeepSeek · deepseek-v4-flash」)+ 尾部 chevron(`sub` 色),行间 hairline,无卡片;子页独立刊头 + kicker 返回钮,back 先回中心页。
14. **文内学习标记(M8/M16/M20)**:生词 = 底色弱高亮(五色板可选,alpha ≤32%,「这个词我查过」);好句 = **下划线**(同色族但 55% alpha——1–2px 细线在背景 alpha 下不可见,「这句我收藏过」);两标记同句可叠加,如铅笔批注,均弱于系统选区。
15. **收藏行(M21)**:本机「收藏」段混排行 = 类型图标(文章=Newspaper/论文=Science,20dp `sub` 色)+ 标题两行截断 + meta 行(论文:作者一行截断 + 日期·分类;文章:来源);行间 hairline,无卡片;长按移除确认(仅删收藏行)。列表行尾/阅读器顶栏星标见 §4.12 双重编码。

## 5. 布局原则

间距 token(`ui/Spacing.kt` 或并入 Editorial.kt):`xs=4 / sm=8 / md=12 / lg=16 / xl=24 / xxl=32`。页左右边距 16;刊头块上 20;meta 距双细线 12;分类组间距 24;行内元素间距 12。组内间距 < 组间间距。

## 6. 深度层级

列表/分组:**零阴影**,只靠规则线。对话框/弹层:Notion 四层低透明度影(近似实现)。底栏:无 elevation,2dp 实墨顶线。禁「悬浮卡片」美学——一切贴在纸面上。

## 7. Do's / Don'ts

**Do**:hairline 做分隔;红色单点使用;真实数据进 meta 行(源数/生词数/日期);删除确认对话框保留;底栏字形用 Pinyon Script 字母(M23),其余功能图标用 Material Symbols 线性风格,无底。
**Don't**:禁卡片分组、禁 ElevatedCard 进主界面;禁选中色块/pill 指示;禁三色轮转 monogram(诊断 #6,改单色衬线字形);禁同一屏两个设置入口(诊断 #4,阅读/Agent 刊头不再放齿轮,底栏设置 tab 唯一入口);禁斜体、禁纯黑、禁冷灰。

## 8. 响应式行为

手机单列;底栏四项均分;SourceRow 域名超长截断(单行 ellipsis);刊头标题一行不换;触控目标 ≥48dp。平板/宽屏:内容 max-width 560dp 居中。

## 9. Motion 哲学(拨盘 5 → B · 墨线轴线)

> 2026-08-02 四方向预览(`design-previews/2026-08-02-motion-system/`)用户选定 **B · 墨线轴线**(M3 纵轴推进),拨盘 5/5/6;落选:A 纯淡变(无方向感)、C 共享元素编排(跨 Activity 成本)、D 弹簧(与纸面气质张力)。DNA 供体 = M3 motion spec:emphasized 三曲线 + fade-through 时值 + shared axis Y 语义。

M3 emphasized 曲线族 + 纵轴语义:**向前 = 上推,返回 = 下沉**。所有转场数值唯一来源 `ui/Motion.kt`(`HpMotion`),任何组件不许自写时值/曲线。

- **曲线**:入场 `decelerate (0.05,0.7,0.1,1)`;退场 `accelerate (0.3,0,0.8,0.15)`;屏上移动(指示器)`standard (0.2,0,0,1)`。
- **tab/同级切换**:刊头由壳层托管(`MastheadHost`,四屏经 `EditorialMastheadSlot` 注册,位置永不动,文字 crossfade 120ms);NavHost 内容 fade + 2.3% 上推:出 90ms accelerate,入 210ms decelerate **无延迟**。教训链:R1 整屏位移 → 刊头跳动(手测反馈);R2 纯淡变 → "几乎没了"(手测反馈);R3 结构件剥离壳层后,内容恢复上推,二者得兼。
- **前进(列表→文章列表/历史,列表→阅读器 Activity)= 纵轴**:入 240ms 上推 3% + 淡,出 120ms 微升 1.2% + 淡;返回镜像(下沉)。
- **底部浮层(AI 抽屉/Aa 面板)**:底滑 250ms decelerate 入 / 180ms accelerate 出(非对称,出更快)。
- **底栏显隐(前进/返回/IME)**:底滑 200ms decelerate 入 / 150ms accelerate 出——底栏是底部 surface,禁止瞬现瞬灭。
- **托管刊头显隐(前进/返回)**:塌缩/展开 240ms + 淡(出 90/入 120),与内容轴同一拍,禁止槽位瞬塌。
- **顶栏显隐**:顶滑 200ms decelerate 入 / 150ms accelerate 出。
- **指示器(底栏墨短线/分段下划线)**:单指示器跨项滑动 200ms standard,禁止瞬跳。
- **状态微反馈(星标/选中色)**:crossfade 120ms standard。
- **禁**:编排/stagger/共享元素(C 落选)、弹簧(D 落选)、>250ms 的 UI 动效、正文区(Readium/pdf.js 页面内部)动画、高频键盘路径动画。

## 改名(用户调整项)

「源」→「阅读」:底栏标签、屏标题、用户可见字符串;内部标识符(SourcesScreen/Routes.SOURCES 等)不动。
「本机」→「我的」(M23,底栏图标试衣间调整项):`tab_local` 字符串一处改,底栏标签与「我的」屏刊头同步;内部标识符(LocalScreen/Routes.LOCAL 等)不动。

## 应用图标(M24)

- **概念**:方向 A 花体字标——铜板花体大写 H + 细编辑线,墨黑 `#1C1917` 落暖纸 `#F4EDE0`,与底栏 R/L/A/S 字标同一血统。
- **资产管线**:AI 生图(2048²)→ 本地修复(去黑角/水印/描边残线,`tools/icon/fix_icon.py`)→ 暗度取 alpha 抠图;`tools/icon/gen_icons.py` 一键重出全套。
- **规格**:adaptive 三层(背景纯色 / 前景字形居中 64% bbox / monochrome 剪影);legacy 圆方五密度;Play 512 在 `artwork/play-icon-512.png`。
- **安全区红线**:字形 bbox ≤ 画布 64%,圆形遮罩不裁花饰;48dp 下游丝消失、主竖笔可读——花体字标的尺寸下限,别再缩。
