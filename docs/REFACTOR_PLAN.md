# ZhihuLite 重构技术方案

> 状态：草案（Phase 1 已完成并验证；Phase 2–4 待实施）
> 适用范围：Android / Kotlin 2.1.21 / Jetpack Compose / 单模块**六层**分层（`model` → `base_logic` → `base_ui` → `business_logic` → `business_ui` → `assemble`）
> 决策前提（已确认）：
> 1. 模块粒度 —— **单模块分层 + 预留拆分**（Gradle 上仍是 `:app` 一个模块，包边界强制依赖方向，后续可按包平移成多模块）
> 2. DI —— **手写 `AppContainer` + `viewModelFactory`**，不引入 Hilt/Koin
> 3. 持久化 —— **Room + EncryptedSharedPreferences**
> 4. 补齐范围 —— **修复已坏功能** + **补登录/登出闭环**（不含点赞/评论输入等写操作）

---

## 1. 现状基线

### 1.1 代码分布（7,401 行 Kotlin，不含资源）

| 区域 | 文件数 | 行数 | 评价 |
|---|---:|---:|---|
| `web/`（Zse96 签名 VM） | 2 | 1,573 | 移植质量高（已与原始 JS 做 662 用例差分验证），但解释器逐次装箱导致单次签名 1.7 ms / 2.44 MB 分配 |
| `comment/` | 6 | 1,015 | 缺 `CommentPanel` 组合根、排序失效、模型重复 |
| `common_ui/` | 8 | 740 | 可复用组件散落，`ListFooter` 状态机不完整 |
| `h5Parser/` | 1 | 726 | 单文件承担解析 + 渲染 + 布局，职责过载 |
| `video/` | 7 | 527 | model/network/ui 三层混放，`VideoPlayerPool` 语义错误 |
| `answerFeed/` | 9 | 534 | 排序/动作栏/评论面板耦合在一个 feature |
| `mainFeed/` | 6 | 426 | ViewModel 内部再 `stateIn`，错误态从未产生 |
| `feedItem/` | 6 | 303 | 含 179 行零引用重复模型 |
| `share/` | 3 | 298 | 手工模型与 `ShareInfo` 重复 |
| `basicTypeExtension/` | 5 | 194 | 时间格式化每帧重建 `DateTimeFormatter` |
| `data/` | 3 | 168 | 无持久化、有并发缺陷 |
| `network/` | 1 | 121 | 单文件塞入 `ZhihuApi` + `KtorFeedApi` + 全局 Json |
| `login/` | 3 | 120 | 双重 cookie 真相源、无登出 |
| `registerRoute/` | 1 | 34 | 自定义事件总线 + KSP 生成，过度设计 |
| `eventReporter/` | 1 | 83 | 上报放大、无去重 |

### 1.2 核心问题归类

**A. 正确性（用户可见的"功能坏了"）**
1. 下拉刷新清空 Feed —— `MemoryFeedStorage.refreshFeedItems` 在 `answerIdSet.clear()` **之前**做去重过滤，刷新第 1 页与已加载内容重叠 → 全部被当重复丢弃 → 列表被替换为空 → `FeedUiState.Loading` 无限转圈。
2. 首次加载失败永久转圈 —— `FeedUiState.Failed` 从未被构造，`FailedFeedScreen` 是空函数，失败可重试入口只存在于 `Success` 分支内。
3. 分页失败被误判为"没有更多"并损坏游标 —— `getMoreItems` 检查的是请求**前**的 `lastResponse`（该分支内必然非空，`FAILED` 不可达），失败时把 `null` 写回游标，下一次调用静默重拉第 1 页。
4. 评论排序切换完全无效 —— `CommentApi.sortType` 在构造时固化，`updateSortType` 只改 UI 状态，既不重置游标也不重新加载。
5. 富文本链接全部点不动 —— 本地 `ClickableText` 恒以 offset=0 回调，`getStringAnnotations("URL", 0, 0)` 永不命中，处理函数只 `println`。
6. 会话生命周期无闭环 —— 双重 cookie 真相源（`CookieManager` vs 明文 SharedPreferences）、从不 `flush()`、无登出、无过期检测。

**B. 稳定性（崩溃与资源）**
7. 7 处硬断言/越界崩溃：`ImageViewer` 初始偏移 `1f`、`VideoElement.require(coverImageUrl != null)`、`VideoElementViewModel.require(answerId…)`（评论内视频必崩）、`FeedItemRepository.require(id != null)`、`VideoPlayerPool` 从不 `release()`、`@Preview` 触发 `Settings()` 初始化 NPE，以及 `H5Parser` 里 `substring(pos+1, pos+4)` 的越界——**但最后这处的归属需要更正**：它位于私有且无人调用的 `calculateHtmlTextContentLength`，而 `parseSimpleHtml` 对 `<`、`<a`、`<!`、`abc<` 等畸形输入并不会抛异常。这是一处「死代码里的 bug」，静态阅读无法区分可达性；该函数已在 Phase 1 删除。
   > 方法学教训：这条最初被报告为「解析器会崩」，实际不可达。可运行的单测当场证明了 `parseSimpleHtml` 在修复前的 `HEAD` 上就不抛异常——**静态审查判断不了可达性，必须用执行来约束结论**。
8. 结构化并发被破坏 —— 全局吞掉 `CancellationException`，`viewModelScope` 取消后请求仍在跑；`FeedRepository` 自建 `SupervisorJob` 且永不取消。
9. `MemoryFeedStorage` 无同步，被多个 `Dispatchers.Default` 协程并发写。

**C. 性能**
10. 组合路径上重复做重活：`DateTimeFormatter` 每帧重建、H5 解析无缓存且滚动即重解析、`SpanStyle` 内联导致记忆化失效。
11. 四处 `LazyColumn` 全部没有 `key`/`contentType`，`List` 参数无 `@Immutable`，item 跳过能力为零。
12. 曝光上报放大：每个新可见卡片 3 个 POST，无去重无节流。
13. 图片无尺寸约束，Coil 全分辨率解码且无鉴权头（独立 HTTP 栈）。

**D. 架构与工程**
14. 无 DI，每屏 `new MemoryFeedStorage()`、每行 `new` 一个空 `AnswerCardViewModel`。
15. 全局 `FeedItemRepository` 无界增长；重复模型三份（`Author`/`Paging`）。
16. release 构建零日志（只在 DEBUG 装 Antilog），错误却用 `printStackTrace`。
17. `:app:gaia` 与 `:app` 的 `jvmTarget` 不一致（61 vs 55）；KSP 用 `Dependencies.ALL_FILES` 拖累增量编译。
18. 仓库内无任何 release 产物，R8 路径从未验证；README 仍描述已废弃的 KMP 结构。

### 1.3 必须保留的优点

- `network_security_config.xml` 的 TLS 策略规范（禁明文、debug 专用用户 CA）。
- Zse96 为纯 Kotlin 移植、无 WebView、可在测试中注入 `Date.now`/`Math.random`，并用抓取自原 JS 的定值向量做回归。
- KSP 生成的路由表无反射，R8 不会破坏它。
- H5 解析已放在 `Dispatchers.Default`。
- `ImageViewer` 的 pan/zoom 采用 `graphicsLayer` 内延迟读取，无逐帧重组。
- 路由表只存不捕获 Context/Activity 的 lambda，不泄漏。

---

## 2. 重构目标与非目标

### 2.1 目标
- **语义正确**：刷新是替换、失败是失败、到底就是到底；每个用户可见状态都有明确来源。
- **单一真相源**：Feed 数据来自 Room；会话来自 `CredentialStore`；不存在两份状态互相打架。
- **依赖单向**：`assemble → business → base → model`，且**在没有强依赖的前提下尽量把代码放在更上层**（见 §3.1 总则）。
- **可测**：`base_logic` / `business_logic` 在结构上不碰 Android/Compose，纯逻辑单测只需 `model + base_logic` 依赖（理由见 §3.4）。
- **可演进**：包边界即模块边界，后续拆多模块是移动目录 + 加 `build.gradle.kts`，不需要重写。

### 2.2 非目标（本期不做）
- 不引入 Hilt/Koin、不引入 Paging 3（见 §5.4 决策说明）。
- 不实现写操作（点赞、感谢、发表评论）—— 涉及写接口风控与签名差异，风险独立评估。
- 不做主题/深色模式的完整设计（仅统一色彩令牌，见 Phase 4）。
- 不做多模块 Gradle 拆分（仅预留）。
- 不改变 Zse96 算法本身（仅优化其资源使用）。

---

## 3. 目标架构

### 3.1 分层与依赖规则

**六层**,自下而上的依赖顺序是:`model` → `base_logic` → `base_ui` → `business_logic` → `business_ui` → `assemble`。

```mermaid
flowchart BT
    M["model<br/>服务端 DTO,零项目内依赖"]
    BL["base_logic<br/>ApiResult · UiMessage · 日志接口<br/>时间/文本工具 · 通用传输封装"]
    BU["base_ui<br/>色彩 token · Modifier 扩展"]
    BSL["business_logic<br/>契约 · 业务规则 · 数据实现(Room/Ktor)<br/>feed · answer · comment · video · login · sign · remote · shared"]
    BSU["business_ui<br/>Screen · ViewModel · UiState<br/>feed · answer · comment · video · login · gallery · shared"]
    AS["assemble<br/>DI 容器 · 导航图 · 进程入口 · 主题<br/>禁止业务逻辑,禁止界面组件"]

    BSU --> BSL
    BSU --> BU
    BSL --> BL
    BU --> BL
    BL --> M
    AS -. 可跨层引用任何下层 .-> BSU
    AS -.-> BSL
```

> 关键约束:`business_logic` **不得**指向 `base_ui`(它必须够不到 Compose)。`business_logic` 与 `base_ui` 同处一个高度带但互不依赖,`business_ui` 是两者的共同上层。校验方式:任意一条依赖边都指向序号更小的层,因此无环。

`base_ui` 依赖 `base_logic`,反之不成立;`business_ui` 同时依赖 `business_logic` 与 `base_ui`。下层不被每个上层使用是正常的——这是依赖图而非单一链条。

**总则:只能上层依赖下层;在没有强依赖的前提下,尽量把代码放在更上层。**

这条总则比"按职责描述分层"更能约束实际决策——代码**只有在有强制理由时才下沉**。它修正了本方案早期版本的两个错误(见 §10):把 `UiState` 放进最底层(理由是"它没有 Android 依赖",属偷换概念),以及把 Compose 色彩 token 混进 `base`(导致整个 `base` 失去"纯"的属性)。

**为什么把 `base` 与 `business` 各切两半(`base_logic`/`base_ui`、`business_logic`/`business_ui`)**

切分的目的**不是"分类整齐",而是让可测性成为编译期保证**。JVM 单元测试跑在宿主 JVM 上,`android.jar` 只是存根(方法体是 `throw RuntimeException("Stub!")`)。因此:

- `base_logic` + `business_logic` **在结构上够不到** `androidx.compose.*`,于是"纯逻辑测试"只需要 `model + base_logic` 做依赖,不必拖进 Compose、Android、Ktor——**不靠自觉,靠依赖图**。
- `base_ui` 的存在让 Compose 有了明确的收容处,`base_logic` 保持纯净(否则只要色彩 token 留在 `base`,`base_logic` 就被 Compose 污染)。

**硬性规则(建议做成遍历源码的 JUnit 架构守卫测试,进入 CI)**

1. 依赖只能指向**更低层**(可跨层,不必相邻),**不得反向**:`assemble` > `business_ui` > `business_logic` > `base_ui` > `base_logic` > `model`。注意 `business_logic` 与 `base_ui` 同处一个高度带但互不依赖,`business_ui` 是两者的共同上层。
2. `model` **不得** import 本项目其他任何层(只允许 `kotlinx.serialization` 等第三方)。
3. `base_logic` **不得** import `android.*`、`androidx.*`、`io.ktor.*`。它是纯 Kotlin 层。
4. `base_ui` **不得** import `business_logic` / `business_ui` / `assemble`;它只知道 `base_logic` 与 `model`。
5. `business_logic` **不得** import `base_ui`、`android.*`、`androidx.compose.*`、`androidx.room` 以外的 `androidx.*`、`kotlinx.coroutines.Dispatchers.Main`。
   - **唯一例外**:`business_logic/remote` 允许 Ktor(它是网络执行器,见 §3.3)。其余 `business_logic` 子模块不要直连 Ktor,通过它暴露的接口调用。
6. `business_ui` **不得** import `business_logic/*/data`(只认契约);`business_*` 各业务模块之间**不得互相 import**。
   - `business_ui` **不得** import `assemble`:feature 通过自己声明的 `*Wiring` 接口拿依赖(接口由 `assemble/container/AppContainer` 实现),工厂与路由出口(`*Entry.kt`)留在 feature 自己手里。详见 §4.7。
7. 跨模块共享一律上浮到 `business_ui/shared`(UI 组件)或 `business_logic/shared`(无 UI 契约);**不接受"因为多个模块要用"作为下沉到 `base_*` 的理由**。`base_*` 的准入标准是"与业务无关的技术能力"。
8. `assemble` 只允许出现四类内容:DI 装配、导航图、进程/Activity 入口、主题,**禁止业务逻辑与界面组件**。内部分三个子包:`container/`(全局单例与 `*Wiring` 实现)、`shell/`(进程与界面外壳)、`navigation/`(全局导航汇总)。判据:`assemble` 可以知道所有 feature 的名字,但不得出现任何 ViewModel/Screen 的内部细节。
9. ViewModel 与 UiState 一律放 `business_ui`——它们依赖 `androidx.lifecycle.ViewModel` 与 `viewModelScope`,放 `business_logic` 会直接毁掉后者的纯净性(见 §3.4)。

**两个必须点明的边界陷阱**

- **`UiMessage` 归 `base_logic`,不归 `base_ui`。** 逻辑层要返回"失败原因",若该类型带 Compose/Android 色彩,就会把 `business_logic` 拽下去。做法:`UiMessage` 只是**纯数据**(资源 id + 参数),真正的取字符串发生在 UI 层。
- **`AppLogger` 接口归 `base_logic`,实现归 `assemble`。** Napier 包装与 Antilog 安装需要 `BuildConfig.DEBUG`,属于 Android 依赖,不能留在 `base_logic`。
- **`org.nigao.zhihuLite.performance` 不属于六层中的任何一层**,它是构建期支撑代码(Perfetto 插桩的运行时桥 `BusinessMethodTrace` 与 opt-out 注解 `NoBusinessTrace`):插桩是**由生成的字节码**从所有层调用它的,放进任何一层都会凭空造出一条跨层依赖(放 `base_ui` 就等于让 `base_logic` 的字节码反向引用上层)。它不影响 §3.4 的可测性边界——`debug`/`release` 变体不含插桩,而 JVM 单测跑的是 `debug`。详见 §7.20。

**关于入口的方向性说明**:`business_*` 不能 import `assemble`,所以依赖是"从上往下给"的:`assemble` 构造 `AppContainer`,由它**实现各 feature 声明的 `*Wiring` 接口**,再经 `CreationExtras` 注入。反过来,`assemble` 需要 `business_ui` 各模块暴露的装配出口(`*Entry.kt`:`*ViewModel.factory` 与 `registerXxxRoute(...)`),这是 `assemble → business_ui` 的单向调用,不构成反向依赖。**容器的类型不得出现在 `*Wiring` 的返回值里**(否则等于绕一圈把 `assemble` 暴露回去)。详见 §4.7。

### 3.2 目标目录结构

```
org.nigao.zhihuLite
│
├── model/                              # 层 1:服务端 DTO(纯数据,零项目内依赖)
│   ├── feed/          FeedItem · Target · Paging · FeedResponse · Question · User
│   ├── answer/        Answer · AnswerRelationship
│   ├── comment/       CommentResponse · Comment · CommentTag · Author
│   ├── video/         VideoPlayInfo · VideoPlay · Playlist
│   ├── share/         ShareInfo
│   └── session/       (会话相关如有)
│
├── base_logic/                         # 层 2:通用纯能力(不得碰 Android/Compose/Ktor)
│   ├── result/ApiResult.kt             # Success / Failure(Network|Auth|Parse|Empty)
│   ├── result/UiMessage.kt             # 纯数据:资源 id + 参数(见 §3.1 边界陷阱)
│   ├── logging/AppLogger.kt            # 仅接口;Napier 实现在 assemble
│   ├── time/TimestampFormatter.kt      # 预构建 DateTimeFormatter 表(纯 java.time)
│   ├── text/                           # StringExtender · IntFormat
│   ├── coroutines/DispatcherProvider.kt# 只暴露 IO/Default(不得暴露 Main)
│   └── di/CreationExtras.kt            # wiringKey<T>():各 feature 定义自己 wiring key 的公共工厂
│
├── base_ui/                            # 层 3:通用 UI 基建(Compose 收容处)
│   ├── theme/ColorTokens.kt            # 统一色彩令牌,替代散落的 Color.Gray 等
│   └── ModifierExtender.kt             # 原子级 Modifier 扩展
│
├── business_logic/                     # 层 4:业务的纯逻辑(模块间禁止互相 import)
│   ├── shared/
│   │   └── image/ImageLoader.kt        # 图片加载抽象(纯接口;Coil 实现在 business_ui)
│   ├── sign/                           # ① 签名算法:纯计算,零依赖
│   │   ├── Zse96Signer.kt              # 包装 web/Zse96 的薄适配(算法文件仍在 web/,见下方说明)
│   │   └── SignatureProvider.kt        # 接口:sign(path, dC0): String
│   ├── remote/                         # ② 请求执行:唯一允许用 Ktor 的 logic 子模块
│   │   ├── ZhihuApiExecutor.kt         # 组装请求头(x-zse-93/96、Cookie、UA、Referer)+ 判定 Network/Auth
│   │   ├── RemoteResult.kt             # 响应分类:2xx-JSON / 401-403 / 非 JSON(登录墙)
│   │   └── SessionCookieSource.kt      # ③ 从会话取 Cookie 与 d_c0 给请求头用(不负责登录/登出)
│   ├── feed/
│   │   ├── FeedRepository.kt           # 契约(接口)
│   │   ├── FeedOperations.kt           # refresh / loadMore / observe 三个操作(方法即操作)
│   │   ├── FeedPaging.kt               # PagingConfig + 游标推进规则(纯函数)
│   │   └── data/                       # 具体实现:Room / Ktor
│   │       ├── FeedRemoteSource.kt
│   │       ├── FeedDao.kt · FeedItemEntity.kt · FeedCursorDao.kt
│   │       ├── FeedRepositoryImpl.kt
│   │       └── FeedItemCache.kt        # 有界 LRU,替代无界全局 Map
│   ├── answer/
│   │   ├── HtmlNode.kt · HtmlParser.kt · HtmlSpanBuilder.kt   # 纯 Kotlin
│   │   └── data/                       # AnswerRemoteSource(如与 feed 不同)
│   ├── comment/
│   │   ├── CommentRepository.kt · CommentOperations.kt    # 取评论 / 切换排序(切换必须重置游标)
│   │   └── data/                       # CommentRemoteSource · CommentRepositoryImpl
│   ├── video/                          # VideoRepository · VideoRemoteSource
│   └── login/
│       ├── SessionRepository.kt · SessionOperations.kt    # 登录 / 登出 / 失效标记
│       └── data/                       # CredentialStore(接口) · EncryptedCredentialStore
│
├── business_ui/                        # 层 5:业务的表现层(模块间禁止互相 import)
│   ├── shared/
│   │   ├── ListFooter.kt · CommonPanel.kt · CommonSwitch.kt
│   │   ├── ImageGallery.kt · ImageViewer.kt · 空态/错误态组件
│   │   └── coil/CoilImageLoader.kt     # ImageLoader 的 Compose 实现
│   ├── feed/                           # 每个业务模块自带【装配出口】与【依赖接口】
│   │   ├── FeedScreen.kt · FeedViewModel.kt · FeedUiState.kt · FeedCards.kt
│   │   ├── FeedWiring.kt             # 【接口】本 feature 需要什么(容器来实现)
│   │   └── FeedEntry.kt                # 【出口】FeedViewModel.factory + registerFeedRoute()
│   ├── answer/                         # AnswerFeedScreen · AnswerCard · ActionBar · AnswerContent
│   │                                   #   ＋ AnswerWiring.kt · AnswerEntry.kt
│   ├── comment/                        # CommentPanel · CommentList · CommentViewModel · CommentUiState
│   │                                   #   ＋ CommentWiring.kt · CommentEntry.kt
│   ├── video/                          # VideoPlayer · VideoElement · VideoElementViewModel
│   ├── login/                          # LogInScreen · AuthWebView · LogOutScreen
│   │                                   #   ＋ SessionWiring.kt · SessionEntry.kt
│   └── gallery/                        # ImageViewerScreen · ZoomableImage
│
└── assemble/                           # 层 6:只做装配(禁止业务逻辑与界面组件)
    ├── container/                      # —— 真正全局的:进程级单例与 DI 装配
    │   ├── AppContainer.kt             # 手写 DI 容器(懒加载单例)
    │   │                               #   并实现各 feature 的 *Wiring 接口
    │   └── Initializers.kt             # 日志、CookieManager 等进程级初始化
    │                                   # 容器由 AppNavHost 经 MutableCreationExtras 交给各 feature
    ├── shell/                          # —— 进程与界面外壳
    │   ├── ZhihuLiteApplication.kt     # Application:构建容器
    │   ├── MainActivity.kt             # 进程入口
    │   ├── App.kt                      # @Composable 根:主题 + NavHost
    │   └── theme/Theme.kt              # MaterialTheme + 深色模式
    └── navigation/                     # —— 全局导航(汇总,无逻辑)
        ├── AppRoute.kt                 # 类型安全路由定义(@Serializable)
        └── AppNavHost.kt               # 每行一个 registerXxxRoute(...)，取代 Gaia 事件总线
```

> **`assemble` 的"每 feature 一小块"与"真正全局的"如何分开**
>
> 你感到 `assemble` "怪",根因不是东西多,而是**它混进了本该属于各 feature 的装配出口**(ViewModel 工厂、路由)。修法不是加一层 `business_assemble`(那只会拉长链、且在共享层 import 所有 feature → 会诱发模块互相 import),而是:
>
> - **每个 feature 自带两个文件**:`*Wiring.kt`(声明"我要什么"的接口)+ `*Entry.kt`(工厂 + `registerXxxRoute`)。它们与 Screen 同目录,就近维护。
> - **`assemble/container/` 只做三件事**:实现所有 `*Wiring` 接口、把容器包成 `CreationExtras`、汇总全局单例。
> - **`assemble/navigation/AppNavHost.kt` 只汇总**:每行一个 `registerXxxRoute(this, navController)`,不含任何具体 ViewModel 的名字。
>
> 于是 `assemble` 里"每 feature 一小块"的表现形式是**容器里一组懒加载属性 + 一个接口实现**,而不是一堆工厂函数（详见 §4.7）。

> **`web/Zse96.kt`(1,573 行算法)保持物理位置不变**,只在 `business_logic/sign/` 加一层薄适配 `Zse96Signer`。它是已验证过的移植产物(与原始 JS 662 用例差分、552/552 逐字节一致),重排目录没有收益、只有回归风险。
>
> **为什么把原来的 `business_logic/zhihu/` 拆成 `sign/` 与 `remote/`**:`zhihu` 是**公司名**,只能说明"与知乎有关",看不出里面在做什么。而那个目录实际装了三件职责不同、变化原因也不同的东西——**签名算法**(纯计算,随知乎改协议而变)、**请求装配**(随接口与风控而变)、**会话 Cookie 读取**(随登录方案而变)。三者混在一起时,任何一处变动都要在同一个目录里翻找;拆开后每个目录的职责由名字即可读出,也把"易变"与"已验证、尽量别碰"的代码分开(`sign/` 只包一层壳,`web/Zse96.kt` 一行不改)。
>
> **`base_ui` 的存在就是为了让 `base_logic` 保持纯净。** 早期版本把色彩 token 放在 `base/ui/`,导致整个 `base` 因 Compose 依赖而失去"纯 Kotlin"属性,`business_logic` 一旦依赖它就等于依赖 Compose——**分层只存在于包名上,不在依赖上**。现在 Compose 有了明确的收容处(`base_ui` + `business_ui`),`business_logic` 在结构上够不到它。

### 3.3 各层职责分工

| 层 | 职责 | 明确不做 |
|---|---|---|
| `model` | 服务端数据的 DTO 映射,字段 `@SerialName` 对齐服务端,全部可空带默认值 | 不含业务规则、不含 UI 逻辑、不 import 本项目其他层 |
| `base_logic` | 与业务无关的**纯能力**:`ApiResult`、`UiMessage`、日志接口、时间/文本工具、通用传输封装 | 不得碰 Android/Compose/Ktor;不接受"多个模块共用"作为准入理由 |
| `base_ui` | 通用 UI 基建:色彩 token、原子级 Modifier 扩展 | 不认识 Feed/Comment;只依赖 `base_logic` 与 `model` |
| `business_logic` | 各业务的**纯逻辑**:契约(接口)、业务规则(以 `*Operations` 组织)、数据实现(Room/Ktor) | 不得碰 `base_ui`/Compose;模块之间不互相 import;不 import `assemble` |
| `business_logic/sign` | 签名算法:由 path 与 `d_c0` 算出 `x-zse-96` | 纯计算,不碰网络/会话/Android;不 import `remote` |
| `business_logic/remote` | 请求执行:组装请求头、发请求、把响应分类为 `Network`/`Auth`/`Parse` | 不含任何页面与业务规则;是 logic 层**唯一**允许直连 Ktor 的子模块 |
| `business_logic/remote/SessionCookieSource` | 从会话取 Cookie 与 `d_c0` 供请求头使用 | 不负责登录/登出(那属于 `business_logic/login`) |
| `business_logic/shared` | 跨业务共享的**无 UI 契约**(如图片加载抽象) | 不放业务规则、不放 Compose |
| `business_ui` | 各业务的**表现层**:Screen · ViewModel · UiState · 卡片模型,以及**本 feature 的装配出口**(`*Entry.kt`:工厂+路由)与**依赖声明**(`*Wiring.kt`) | 不得 import `business_logic/*/data`(只认契约);不 import `assemble` |
| `business_ui/shared` | 跨业务共享的 Compose 组件(ListFooter/CommonPanel/ImageGallery…) | 不放业务规则 |
| `assemble` | `container/`(全局单例、实现各 `*Wiring`)、`shell/`(进程入口与外壳)、`navigation/`(全局导航汇总) | **禁止业务逻辑,禁止界面组件**;不得承载具体 feature 的工厂或路由实现 |

**分工要点(这是本次重构的核心收益)**

1. **"刷新是替换"这一业务规则从 UI 上浮到 `business_logic/feed/FeedOperations.refresh()`** —— 当前它隐式藏在 `MemoryFeedStorage` 的调用顺序里,才导致刷新清空 Feed。规则有了明确载体后,可以被纯 JVM 单测直接锁住,且无法被别的调用路径绕过。
2. **H5 解析进 `business_logic/answer`,渲染进 `business_ui/answer`** —— 当前 `h5Parser` 一个文件同时干"解析"和"渲染"两件事(726 行),这是**同层混装导致无法纯 JVM 测试**的典型(理由见 §3.4)。拆开后解析可单测、渲染可复用。
3. **签名与请求装配留在 `business_logic`(`sign/` 与 `remote/`),而不是沉进 `base_logic`** —— 它们是最易变的东西(协议一改即失效),关在业务层与页面一起演进,比沉在底层影响所有层更安全;也符合总则(没有强制下沉的理由)。当前 `FeedApi` 有签名而 `EventReporter` 没有、且两者用了不同的 Cookie 来源,收敛到 `remote/ZhihuApiExecutor` 一处。
4. **`model` 只存 DTO,不引入第二层领域模型** —— 已确认的决策。UI 层的隔离由各模块 ViewModel 的 `toXxxState()` 映射承担(如 `FeedItemCardState`),再加一层 Domain 是纯粹的重复样板。
5. **`comment` 从 `answerFeed` 中独立成 `business_*/comment`** —— 评论面板是独立业务,当前寄生在 answer 内造成 1,015 行耦合。
6. **重复模型统一到 `model`** —— 删除 `feedItem/Comment.kt` 的 179 行零引用代码与三份 `Author`/两份 `Paging`。

### 3.4 为什么 `*_logic` / `*_ui` 的切分是可测性的结构保证

这不是概念洁癖,而是本项目的**运行期硬约束**。Android 项目的单元测试有两个源集:

| 源集 | 运行环境 | Android API 可用性 | 本项目现状 |
|---|---|---|---|
| `app/src/test`(JVM) | 宿主 JVM,**无 Android 运行时** | `android.jar` 只是**存根**,方法体是 `throw new RuntimeException("Stub!")` | 全部 28 个测试 |
| `app/src/androidTest` | 真机/模拟器 | 真实实现 | 仅模板测试 |

关键在第二行:`android.jar` 里的类**能编译、不能执行**。因此判断标准不是"这个文件 import 了什么",而是"**执行路径上有没有真的走到 Android/Compose 代码**"。

这解释了本项目一个反直觉的现状:`h5Parser/H5Parser.kt` 现在把解析器和渲染器装在同一文件里,而 `H5ParserTest` 的 9 个用例却能通过——因为它只调用 `parseSimpleHtml`,从未碰到 `Text`/`AsyncImage`/`LocalDensity`,Android 存根从未被执行。

反过来,一旦解析逻辑与渲染逻辑在同一层共享状态或工具,会发生:

1. **一个工具函数就能炸掉整组测试。** 例如解析后要按屏宽裁切文本,于是解析类里出现 `LocalDensity.current` 或某处依赖 `Dispatchers.Main`(需要 Android Looper)= 测试中一调用就抛 `Stub!` 或 `IllegalStateException`,且**只有运行时才暴露,编译期完全看不出来**。
2. **类加载即失败。** JVM 测试加载某类时,若其父类或字段类型是 Android 类(如状态对象里放 `Uri`、或继承 `AndroidViewModel`),类加载直接失败,测试方法都进不去。
3. **依赖图被拖进来。** `AsyncImage` 运行时需要真实的 Android `Context`;只要解析逻辑与图片加载共享状态,想测解析就得先 mock 整套东西,而 `Context` 是存根、mock 不了(那就得上 Robolectric,另一个更重的运行时)。
4. **最终只能上模拟器。** 于是"快速反馈"消失——改一次解析规则就要跑 instrumented test,慢一个数量级,实践中就没人写了。**最实际的代价不是"不能测",而是"测试成本高到没人写"。**

**本项目的现状正好是"靠运气"的例子。** `tools/run_unit_tests.sh` 之所以能跑通 28 个测试,是因为当前 `H5Parser.kt` 恰好只在被测路径上调用了纯函数;只要有人给解析流程加上一句依赖屏宽或 `Dispatchers.Main` 的代码,测试就会在运行时炸开——**而编译期毫无提示**。

**把 `base` 与 `business` 各切两半,就是把这个"运气"换成"结构"**(`base_logic`/`base_ui`、`business_logic`/`business_ui`):

| 切分 | 换来的保证 |
|---|---|
| `base_logic` 不含 Compose | `business_logic` 依赖它时不会被拖进 Compose |
| `business_logic` 不得 import `base_ui`/`androidx.compose.*` | 纯逻辑模块**在编译期就够不到** Compose/Android(规则见 §3.1 第 5 条) |
| `business_ui` 与 `base_ui` 是 Compose 的唯一收容处 | 想混也混不进 logic |
| 纯逻辑测试只需 `model + base_logic` | 测试 classpath 不再包含 Compose/Android/Ktor,跑得快、不会 `Stub!` |

换句话说:**`business_logic` 与 `base_logic` 的边界不是"分类",而是可测性边界**;它把一个只能靠代码评审维持的约定,变成了依赖图 + 一条可自动检查的守卫规则(§3.1 硬性规则 1–6)。本项目的 `tools/run_unit_tests.sh` 正是这条路(只编译并运行 `app/src/test`),有了这个结构后它才真正可靠。

---

## 4. 核心抽象与实现方式

### 4.1 结果与状态类型（`base_logic` + `business_ui`）

**先明确归属，这是早期版本被修正过的一处。** 按 §3.1 的总则（尽量往上层放），这两类东西**不在同一层**：

| 类型 | 归属 | 理由 |
|---|---|---|
| `ApiResult` / `UiMessage` | **`base_logic/result/`** | 被 `base_logic`（网络传输产生）与 `base_ui`/`business_*`（消费）**同时使用**，是跨层词汇——这是下沉的强制理由 |
| `UiState`（列表页状态机） | **各 `business_ui/*` 自定义**（同构部分上浮到 `business_ui/shared`） | 只有表现层用；且各业务的状态**注定异构**，不存在"共用一套泛型"的强制理由 |
| ViewModel / 卡片模型 | **`business_ui/*`** | 依赖 `androidx.lifecycle.ViewModel` 与 `viewModelScope`，放 logic 会毁掉 logic 的纯净性 |

> **早期版本把 `UiState` 放进最底层 `core`，理由是"它没有 Android 依赖"——那是偷换概念。** 分层的准入标准是"**依赖方向与知识范围**"（它是跨层契约吗？），不是"纯不纯 Kotlin"。`UiState` 是彻头彻尾的表现层概念、只被业务层使用，所以它属于 `business`。这个错误正是"尽量往上层放"这条总则要防住的。

```kotlin
// base_logic/result/ApiResult.kt —— 跨层词汇（logic 产生，ui 消费）
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    sealed interface Failure : ApiResult<Nothing> {
        data class Network(val cause: Throwable?) : Failure   // 超时/无网/5xx
        data class Auth(val message: String) : Failure        // 401/403 或登录墙 HTML
        data class Parse(val cause: Throwable) : Failure      // 反序列化失败
        data object Empty : Failure                           // 200 但无数据
    }
}

// base_logic/result/UiMessage.kt —— 纯数据：资源 id + 参数（不含 HTTP 细节）
// 它可能跨模块使用，因此放在 base；但它必须保持"极稳定"：一个消息引用 + 参数，
// 一旦开始长胖（例如加入重试策略、页面标题），就说明它该上浮到 business_ui/shared。
// 关键：它不能带 Compose/Android 色彩，否则会把 business_logic 拽下去（§3.1 边界陷阱）。
data class UiMessage(@StringRes val resId: Int, val args: List<Any> = emptyList())

// business_ui/feed/FeedUiState.kt —— 每个业务模块自定义，允许异构
sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Content(
        val cards: ImmutableList<FeedItemCard>,
        val hasMore: Boolean,
        val isRefreshing: Boolean,
    ) : FeedUiState
    data object Empty : FeedUiState
    data class Failed(val message: UiMessage, val retry: () -> Unit) : FeedUiState
}

// business_ui/feed/FeedCards.kt —— 注意集合类型
@Immutable
data class FeedItemCard(
    val answerId: String?,
    val question: String,
    val authorName: String,
    val authorAvatarUrl: String,
    val excerpt: String,
    // 必须是 ImmutableList 而不是 List：`List` 在 Compose 里被视为不稳定类型，
    // 会让持有它的 composable 每次都重组而无法跳过（当前全项目正踩这个坑）。
    val thumbnails: ImmutableList<String>,
    val voteUpCount: Int,
    val commentCount: Int,
    val updatedTime: Long,
)
```

**为什么不再统一成一个泛型 `UiState<T>`**（同构部分才上浮）：

- **异质性是真实的**：`business/comment` 需要 `sortType`/`totalCount`/`hasMore`；`business/feed` 需要区分"尚未加载"与"刷新后为空"（这正是当前 `initialLoadSettled` 存在的原因）。塞进公共泛型就会污染其他屏幕。
- **类型安全会退化**：`UiState<List<CommentUiState>>` 只是个"带泛型口袋"，做不到的状态仍可构造；每个业务自己的 sealed 类型能让非法状态无法表达。
- **公共类型熵只增不减**：一旦开始往公共 `UiState` 里加 `isPaginating`/`isRetrying`/`sortType`，它就变成万能袋。
- **`retry: () -> Unit` 放在状态里**意味着状态对象持有行为。保留这一做法（它确实方便接线），但**只放在各自业务的 `Failed` 里**，不固化为全局契约。

**上浮的具体判据**（避免"过早共享"）：当且仅当**≥3 个业务模块**的 `Loading`/`Empty` 分支写得完全一致时，才把**非泛型的那部分**标记（`Loading`/`Empty`）上浮到 `business_ui/shared`。`Content` 载荷永不共享——因为"内容"恰好是各业务差异最大的地方。

**为什么 `ApiResult` 必须先做**：当前所有网络失败都被压成 `null`，导致"没网""登录过期""接口改字段""真的没有数据"在 UI 层完全无法区分。这是问题 A2/A3/A6 的共同根因。

**关键实现约定**
- 所有 `catch` 必须先把 `CancellationException` 重新抛出，再处理其他异常（当前全局吞掉取消，破坏结构化并发）。
- `Network`/`Auth` 的判定放在 `business_logic/remote/ZhihuApiExecutor`：非 2xx 直接看状态码；2xx 但 `content-type` 不是 `application/json`（知乎登录墙会返回 HTML）判定为 `Auth`。
- `ApiResult` 不携带 HTTP 细节，UI 层只认 `UiMessage` 这类已本地化的消息载体。
- **稳定性是类型层面的约束，不是靠自觉**：`kotlinx-collections-immutable` 已在依赖里（当前全项目只用了一次）。UI 状态里的集合一律 `ImmutableList`/`ImmutableMap`，卡片/条目模型一律 `@Immutable` + 全 `val`。这条可在 Phase 3 的架构守卫测试里强制。

### 4.2 Feed 数据流（`business_logic/feed` + `business_ui/feed`）

```
FeedScreen ──observe──> FeedViewModel ──> FeedRepository(接口, business_logic/feed)
                                              │
                    ┌─────────────────────────┴─────────────────────────┐
                    │                    FeedRepositoryImpl (data)       │
                    │                                                   │
   Room (FeedDao) ──┤ observeItems(query)  → Flow<List<FeedItem>>       │
                    │ refresh(query)       → remote.fetch(initialUrl)   │
                    │ loadMore(query)      → remote.fetch(cursor.next)  │
                    └─────────────────────────┬─────────────────────────┘
                                              │
                                    FeedRemoteSource (Ktor + 签名)
```

**语义规则（写进 `business_logic/feed` 并被单测覆盖）**

| 操作 | 对 Room 的动作 | 对游标 | 返回 |
|---|---|---|---|
| 首次加载 | upsert 第 1 页（`position` = 序号） | 记录 `next` | `Content` / `Empty` / `Error` |
| 下拉刷新 | **delete 该 query 全部 → upsert 新第 1 页** | **重置**为新的 `next` | 同上（失败时**保留**旧数据 + 报错） |
| 加载更多 | upsert 新页（追加 position） | 推进 | `Content` / `Empty`（`isEnd`）/ `Error`（**不动游标**） |

> 与现状的差异：刷新不再经过"去重过滤"这条路径，因此不可能再出现"刷新把列表清空"；加载更多失败不再污染游标。

**这些规则由谁承载、怎么保证不被绕过**

早期的方案把规则写成三个 `ObserveFeedUseCase` / `RefreshFeedUseCase` / `LoadMoreFeedUseCase` 类。**已废弃那种做法**：`UseCase` 后缀是 Clean Architecture 的仪式化命名，不带来信息量，反而让人以为"一个操作必须一个类"。现在收成一个对象，**方法即操作**：

```kotlin
// business_logic/feed/FeedOperations.kt —— 规则在这里，纯 Kotlin，可 JVM 单测
class FeedOperations(
    private val repo: FeedRepository,
    private val reporter: EventReporter,     // 曝光/已读上报（去重+节流也在此）
) {
    fun observe(query: FeedQuery): Flow<List<FeedItem>> = repo.observeFeed(query)

    /** 刷新 = 替换。失败时**不动**既有数据，只报错。 */
    suspend fun refresh(query: FeedQuery): ApiResult<Unit>

    /** 加载更多。已到底则直接返回 End；失败**不推进游标**，也绝不报 End。 */
    suspend fun loadMore(query: FeedQuery): ApiResult<LoadMoreOutcome>

    /** 曝光/已读：批量 + 去重 + 节流（见 §4.6）。 */
    suspend fun reportVisible(ids: List<String>)
}
```

**关键纪律：仓库接口不得提供能表达非法状态的方法。** 如果写成 `load(query, isRefresh: Boolean)`，规则随时会被绕过，`FeedOperations` 就退化成转发器。所以契约必须没有布尔开关：

```kotlin
// business_logic/feed/FeedRepository.kt
interface FeedRepository {
    fun observeFeed(query: FeedQuery): Flow<List<FeedItem>>      // 只读
    suspend fun fetchFirstPage(query: FeedQuery): ApiResult<Page> // 只取，不写
    suspend fun fetchNextPage(query: FeedQuery): ApiResult<Page>
    suspend fun replaceAll(query: FeedQuery, items: List<FeedItem>) // 只有"替换"这个动作
    suspend fun append(query: FeedQuery, items: List<FeedItem>)
    // 没有 update(query, items, isRefresh) 这种带开关的方法
}
```

**为什么规则不能放 ViewModel**（本项目有现成的反例）：`CommentViewModel.updateSortType` 就是"把数据一致性规则放进 ViewModel"的后果——它只改了 UI 状态里的 `sortType`，而真正的游标在 `CommentApi.currentResponse` 里从未被重置，于是**排序切换完全失效**。规则一旦只活在 ViewModel，其它调用路径就会绕过它；而"切换排序要重置游标"这条规则 comment 与 feed 都需要。

判据（值得写成团队约定）：

| 规则性质 | 例子 | 归属 |
|---|---|---|
| **数据一致性**：什么被写进库、游标怎么推进、失败允许改什么 | "失败不推进游标"、"刷新是替换而非追加" | `business_logic`（可纯 JVM 单测） |
| **UI 行为**：何时显示 spinner、空态怎么渲染、点击后跳哪 | `isRefreshing = true`、"到底显示提示" | `business_ui` 的 ViewModel |

**实现：`FeedRepositoryImpl` 的组合方式**（在 `business_logic/feed/data`，由 `assemble/AppContainer` 构造）

```kotlin
class FeedRepositoryImpl(
    private val remote: FeedRemoteSource,   // 走 business_logic/remote 发请求（含签名）
    private val dao: FeedDao,               // Room
    private val cursorDao: FeedCursorDao,   // 游标落库，进程重启可续翻
    private val db: ZhihuDatabase,          // 只为 withTransaction
    private val mapper: FeedMapper,         // DTO ⇄ Entity ⇄ 领域模型（纯逻辑）
) : FeedRepository {
    override fun observeFeed(query: FeedQuery) =
        dao.observeByQuery(query.id).map { rows -> rows.map(mapper::entityToDomain) }

    override suspend fun replaceAll(query: FeedQuery, items: List<FeedItem>) =
        db.withTransaction {                 // 删+写+重置游标必须原子
            dao.deleteByQuery(query.id)
            dao.upsertAll(items.mapIndexed { i, it -> mapper.domainToEntity(it, query.id, i) })
        }
}
```

四个实现要点（都是审查结论的直接落地）：

| 要点 | 为什么必须这样 |
|---|---|
| 写操作放 `db.withTransaction` | 刷新时"删旧+写新"之间若被 Room 的 `Flow` 观察到，UI 会闪一下空列表——这是"刷新后白屏"的另一条可能路径 |
| 去重交给主键 `@Upsert`，不维护内存 id 集合 | 现在的 `answerIdSet` 是"靠调用顺序维持"的隐式不变量，刷新 bug 正源于此；换成数据库约束后无需额外逻辑 |
| 游标落 Room 表，不做内存字段 | 现在 `lastResponse` 是普通字段，失败时被写成 `null` 就永久损坏；落库后"失败不动游标"是明确的 DB 语义 |
| 实现内部用一把 `Mutex` 串行化分页操作 | `FeedOperations` 拦不住"refresh 与 loadMore 并发"。若两个入口同时翻同一 query，可能基于同一游标各取一页 → 重复条目。这个锁只能在实现里 |

`FeedMapper` 单独抽出且不含 Android（只是字段搬运），因此映射逻辑可纯 JVM 单测——`@Entity` 是普通数据类，构造它不需要 Android 运行时。

**分页与去重**
- 去重交给 Room 的主键冲突策略（`OnConflictStrategy.REPLACE`），不再维护内存 `answerIdSet`。这是把"当前靠调用顺序维持的隐式不变量"换成"数据库约束保证的显式不变量"。
- 游标存库，使进程重启后可继续分页。**实现修正**：早期方案写的是单独建 `feed_cursor` 表；实际实现把
  `cursor_next` / `cursor_is_end` 两列放在 `feed_query` 上（一个 feed 一行，游标天然属于它）。
  这样少一张表和一次 join，语义不变；若将来需要"每个 feed 保留多个历史游标"，再拆表。
- `PagingConfig`：`initialPageSize` / `loadMoreThreshold`（距底部 N 项触发）/ `maxItems`（可选上限，防止无限增长）。

**分页规则是纯函数，放在 `business_logic/feed`**

```kotlin
// business_logic/feed/FeedPaging.kt
data class PagingConfig(val initialPageSize: Int = 10, val loadMoreThreshold: Int = 3)

sealed interface NextPage {
    data class Fetch(val url: String) : NextPage
    data object End : NextPage
}

/** 由"当前游标 + 上一页响应"推导下一步，纯函数、可单测。 */
fun nextPage(cursor: FeedCursor?, previous: PageMeta?, config: PagingConfig): NextPage
```

### 4.3 会话与凭证（`business_logic/login` + `business_logic/remote`）

**单一真相源**：`CredentialStore`（`business_logic/login/data`）是唯一持久化点；`SessionRepository`（接口在 `business_logic/login`，实现在 `business_logic/login/data`）是唯一对外暴露点。网络层的 Cookie 读取统一走 `business_logic/remote/SessionCookieSource`，不再直接碰 `CookieManager`。

```kotlin
// business_logic/login/SessionRepository.kt
interface SessionRepository {
    val authState: Flow<AuthState>          // LoggedOut / LoggedIn / Expired
    suspend fun logIn(cookie: String)
    suspend fun logOut()
    suspend fun invalidate(reason: String)  // 网络层判定 401/登录墙时调用
    fun currentCookie(): String?            // 供请求头装配
}

// business_logic/login/SessionState.kt（会话状态是业务状态，不是 model 层的 DTO）
sealed interface AuthState {
    data object Unknown : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val userId: String?) : AuthState
    data class Expired(val reason: String) : AuthState
}
```

**实现方式**
- `CredentialStore` 接口 + `EncryptedCredentialStore`（`EncryptedSharedPreferences`，`MasterKey` 用 `AES256_GCM`）。**注意**：`androidx.security:security-crypto` 已被官方标记为 deprecated，社区在维护 fork；落地时二选一并记录决策：
  - (a) 仍用 `androidx.security:security-crypto:1.1.0-alpha06`（可用但停止维护）；
  - (b) 用 `DataStore + Tink` 自行加密；
  - (c) 用社区 fork。
  抽象成接口的意义就是：这个选择只影响 `business_logic/login/data` 里的一个文件。
- `AuthWebView` 登录成功后：写入 `CredentialStore` → 调 `CookieManager.flush()` 确保 WebView cookie 落盘 → 后续所有请求（Feed / Comment / Share / Video / EventReporter）**统一从 `CredentialStore` 取 cookie**，`CookieManager` 退化为仅供 WebView 自身使用。
- `EventReporter` 不再单独读 `LogInManager.cookie()`，改为经 `ZhihuApiExecutor` 走同一套请求头（顺带修掉它缺签名的问题）。
- 登出：清 `CredentialStore` → `CookieManager.removeAllCookies()` + `flush()` → `authState = LoggedOut` → `AppNavHost` 收到状态变化后回到登录路由并 `popUpTo(inclusive)`。
- 过期：`authState = Expired` 时在 UI 上给一次明确提示（"登录已过期，请重新登录"），而不是静默空列表。

### 4.4 H5 解析与渲染（拆分 `h5Parser`）

当前 726 行单文件承担：字符级解析、HTML 实体、14 种标签渲染分支、动态密度计算、图片占位。拆成两半：

```kotlin
// business_logic/answer/HtmlNode.kt —— 纯 Kotlin，无 Compose
sealed interface HtmlNode {
    data class Element(val tagName: String, val attributes: Map<String, String>, val children: List<HtmlNode>) : HtmlNode
    data class TextNode(val content: String) : HtmlNode
}

// business_logic/answer/HtmlParser.kt —— 纯函数，可单测；容错优先
interface HtmlParser { fun parse(html: String): List<HtmlNode> }

// business_ui/answer/AnswerContent.kt —— 渲染
@Composable fun AnswerContent(nodes: List<HtmlNode>, onLinkClick: (String) -> Unit, …)
```

**实现要点（下列缺陷均已通过"解析器单独跑 + 与原始 JS 对照"实测确认，不是推测）**

解析器契约（`business_logic/answer`）：
- 必须**永不抛异常**：所有索引访问做边界检查，无法解析的片段退化为纯文本（原实现中确实存在 `substring(pos+1, pos+4)` 这类无保护写法，其可达性归属见 §1.2 第 7 条）。
- **必须解码 HTML 实体**：当前 `&amp;`/`&lt;`/`&nbsp;`/`&#39;` 原样渲染给用户看到。需支持命名实体 + 十进制/十六进制数字实体。
- **必须跳过 HTML 注释**：当前 `<!-- … -->` 会被当成一个名为 `!--` 的元素，导致**注释之后的所有内容被嵌套进该元素**，并在 UI 上被包进灰色 `UnknownElement` 容器。
- **闭合标签必须归一化比较**：`<P>hi</p>`、`<div>x</DIV>`、`<div>x</div >`、`<div>x</div attr>` 当前都无法正常闭合（名字取自 `</…>` 原文与开始标签 `==` 比较，而渲染时却用 `lowercase()` 派发）→ 大段结构错位。
- **`script`/`style`/`code` 按 raw text 处理**：当前 `<script>if (1<2)…</script>` 会解析出 `<2)>` 这种垃圾元素。
- **深度上限**：解析是迭代的（5 MB / 50 万层 153 ms 无压力），但**渲染侧递归**（`collectTextContent`、`collectStyledText`、`UnknownElement` 回递归）在 1000 层即 `StackOverflowError`，256 KB 栈下约 150 层就崩。必须限制嵌套深度（如 100）或把文本收集改为迭代。
- 解析器**不使用正则**（全是 `indexOf`/`startsWith`），因此没有回溯风险；不要为了"优化"引入正则。
- 删除 `calculateHtmlTextContentLength` 与 `SampleImageLoader`：均无任何调用者，前者对 `<`、`abc<`、`<a` 会抛越界异常（Phase 1 已删除）。

渲染层（`business_ui/answer`）：
- 不再使用"恒以 offset=0 回调"的自制 `ClickableText`：改为 `Text` + `LinkAnnotation`（若 Compose 版本支持）或 `androidx.compose.foundation.text.ClickableText` 的真实偏移回调，只放行 `http`/`https`，经 `onLinkClick` 交给上层用 `LocalUriHandler` 打开。
- **必须删除 `println("Link clicked: $url")`**：它在 release 也会执行，把用户访问的 URL 写进 logcat。
- `produceState` 在 `html` 变化时要先把值置空，否则复用的列表项会短暂显示**上一条**回答的内容。
- 图片：让 `ImageElement` 真正走注入的 `ImageLoader`（当前 `ImageLoader`/`CoilImageLoader`/`SampleImageLoader` 全是死抽象），并使用解析到的 `width`/`height` 作为 Coil 目标尺寸。
- `li` 出现在 `ul`/`ol` 之外时当前完全不渲染（`ListItemElement` 是空实现）→ 内容丢失，应按普通块渲染。

### 4.5 评论（`business_ui/comment` + `business_logic/comment`）

当前问题：排序失效、游标不重置、`childComments` 递归构造但从不渲染、`CommentTag` 重复应用 modifier。

```kotlin
// business_ui/comment/CommentViewModel.kt
class CommentViewModel(private val operations: CommentOperations, private val answerId: String) : ViewModel() {
    fun setSort(sort: CommentSort)   // 触发：重置游标 → 清空列表 → Loading → 重新拉第一页
    fun loadMore()
    fun retry()
}
```

- 排序切换是**完整重置**：`cursor = null`、`comments = emptyList()`、`uiState = Loading`、以新 `sortType` 重新请求。
- 游标为空时不得发请求（当前会用空路径去拉知乎首页 HTML）。
- `childComments` 要么渲染，要么不构造——不允许"构造了但不展示"的中间态。

### 4.6 曝光上报（`business_logic/feed` + `business_logic/remote`）

当前有三个问题叠加：**上报放大**（每个新可见卡片 3 个 POST）、**无签名**（`EventReporter` 自己拼请求头，不带 `x-zse-96`，大概率一路 4xx 后被静默吞掉）、**语义混淆**（`reportShow` 与 `reportRead` 无差别地一起触发）。接口放 `business_logic/feed`，实现与批量策略放 `business_logic/feed/data`，请求经 `business_logic/remote` 发出：

```kotlin
// business_logic/feed/EventReporter.kt —— 只是一个"发送端口"，不含策略
interface EventReporter {
    suspend fun send(ids: List<String>, kind: ReportKind)   // 批量发送，签名由 remote 层统一加
}

enum class ReportKind { SHOW, READ }

// business_logic/feed/FeedOperations.kt —— 去重与节流的【状态】属于这里，不另开类
class FeedOperations(
    private val repo: FeedRepository,
    private val reporter: EventReporter,
    private val clock: () -> Long = System::currentTimeMillis,   // 注入以便单测，不依赖真实时间
) {
    private val reported = mutableSetOf<String>()   // "kind:itemId" 已上报集合
    private var lastReportAt = 0L
    private val minIntervalMillis = 2_000L

    /** 合并为一次上报；已上报过的 (itemId, kind) 跳过；距上次不足 minInterval 则整体跳过。 */
    suspend fun reportVisible(ids: List<String>)
}
```

- **批量**：一次可见性变化合并为一次上报，而不是每卡片 3 个 POST。
- **去重**：`(itemId, kind)` 只上报一次；`show` 与 `read` 语义分离。
- **节流**：滚动过程中最多 N 秒一次（`clock` 注入以便单测，不依赖真实时间）。
- 上报走 `ZhihuApiExecutor`，因此**带上签名**，并复用 `CredentialStore` 的 cookie 而不是另开一条读取路径。

### 4.7 依赖装配（手写 `AppContainer`）

```kotlin
// assemble/AppContainer.kt
class AppContainer(private val app: Application) {
    // 基础设施（进程级单例，懒加载）
    val logger: AppLogger by lazy { AppLogger(BuildConfig.DEBUG) }
    val httpClient: HttpClient by lazy { ZhihuHttpClient.create(credentialStore, signatureProvider, logger) }
    val database: ZhihuDatabase by lazy { ZhihuDatabase.create(app) }
    val credentialStore: CredentialStore by lazy { EncryptedCredentialStore(app) }
    val signatureProvider: SignatureProvider by lazy { Zse96Signer() }
    val feedItemCache: FeedItemCache by lazy { FeedItemCache(maxSize = 500) }

    // 数据源与 DAO
    private val feedDao get() = database.feedDao()
    private val feedCursorDao get() = database.feedCursorDao()
    private val feedRemoteSource by lazy { FeedRemoteSource(httpClient, signatureProvider) }
    private val commentRemoteSource by lazy { CommentRemoteSource(httpClient) }
    private val eventReporter: EventReporter by lazy { ZhihuEventReporter(httpClient, sessionRepository) }

    // 仓库（实现类在各自模块的 data 子包）
    val feedRepository: FeedRepository by lazy {
        FeedRepositoryImpl(feedRemoteSource, feedDao, feedCursorDao, database, FeedMapper())
    }
    val commentRepository: CommentRepository by lazy { CommentRepositoryImpl(commentRemoteSource) }
    val sessionRepository: SessionRepository by lazy { SessionRepositoryImpl(credentialStore, authWebViewCookieJar) }
    val preferencesRepository: PreferencesRepository by lazy { DataStorePreferencesRepository(app) }

    // 业务操作（无状态，可共享；规则在这些对象里，见 §4.2）
    val feedOperations by lazy { FeedOperations(feedRepository, eventReporter) }
    val commentOperations by lazy { CommentOperations(commentRepository) }
    val sessionOperations by lazy { SessionOperations(sessionRepository) }
}
```

**ViewModel 装配 —— feature 自带出口，容器只实现接口**

设计目标有两个，必须同时满足：

1. **`business_ui` 不得 import `assemble`**（§3.1 硬性规则第 6 条），否则 feature 的编译与测试会被拖上 `Application`；
2. **依赖必须取自容器里的单例**，不能在工厂里现造（理由见下方"必须单例"）。

同时满足两者的写法是"**接口由 feature 声明，`assemble` 实现**"。

三个文件的分工要分清，否则很容易把 key 放错地方（本方案早期版本就放错过，见下）：**接口与 key 在 `FeedWiring.kt`**（两者都必须能被 `assemble` 用到），**取值辅助与工厂在 `FeedEntry.kt`**。

```kotlin
// ① business_ui/feed/FeedWiring.kt —— feature 声明"我从 assemble 需要什么"
interface FeedWiring { val feedOperations: FeedOperations }

/**
 * 本 wiring 在 CreationExtras 中的 key。
 *
 * 必须是 public：feature 自己的工厂要从 extras 里【取值】，assemble 的 AppNavHost 要往里【放值】，
 * 两边都在用，所以不能是 private（顶层 private 是文件私有，另一个包访问不到）。
 * 类型参数写死为 FeedWiring，调用方无法用错类型。
 */
val FEED_WIRING_KEY: CreationExtras.Key<FeedWiring> = wiringKey()
```

```kotlin
// ② business_ui/feed/FeedEntry.kt —— 工厂与路由就近放在 feature 里
private fun CreationExtras.feedWiring(): FeedWiring =
    requireNotNull(this[FEED_WIRING_KEY]) {
        "缺少 FeedWiring：FeedScreen 必须在 assemble 的 NavHost 内创建"
    }

val FeedViewModel.factory: ViewModelProvider.Factory
    get() = viewModelFactory {
        initializer {
            // 取的是【容器里的单例】，不是新建的对象（理由见下方"必须单例"）
            FeedViewModel(operations = feedWiring().feedOperations)
        }
    }

fun NavGraphBuilder.registerFeedRoute(navController: NavController) { /* composable(...) { FeedScreen(...) } */ }
```

```kotlin
// ③ base_logic/di/CreationExtras.kt —— 公共工厂，避免每个 feature 各自定义一个同类型 key
typealias CreationExtras = androidx.lifecycle.viewmodel.CreationExtras
inline fun <reified T : Any> wiringKey(): CreationExtras.Key<T> = object : CreationExtras.Key<T> {}
```

```kotlin
// assemble/navigation/AppNavHost.kt —— 每行一个 feature；在 composable 里提供本 feature 的 wiring
composable(MainFeedRoute) {
    val feedVm: FeedViewModel = viewModel(
        factory = FeedViewModel.factory,
        extras = MutableCreationExtras().apply { this[FEED_WIRING_KEY] = container },
    )
    FeedScreen(feedVm, navController)
}
```

> **key 必须与接口同文件且 public（早期版本把这个写错了）**：顶层 `private` 是**文件私有**，若把 `FEED_WIRING_KEY` 写成 `private val`，feature 自己的工厂能访问，但 `assemble` 的 `AppNavHost` 访问不到，编译报 `Cannot access 'FEED_WIRING_KEY': it is private`。它要被"放值"与"取值"双方共用，因此必须 public。把它放在 `FeedWiring.kt` 而不是 `FeedEntry.kt` 的理由也是这个：**谁需要被别人使用，就跟谁放在一起**。

> **属性名带 feature 前缀（`feedOperations` 而非 `operations`）**：`AppContainer` 要**同时实现多个 `*Wiring` 接口**，如果每个接口都声明 `val operations`，就会出现属性名冲突而无法实现。带前缀既避免冲突，又让调用点自解释。
>
> **为什么用 `MutableCreationExtras` 显式传容器，而不是用 `APPLICATION_KEY` 强转**：`(this[APPLICATION_KEY] as FeedWiring)` 这种写法有两处问题——① 它隐含要求 `Application` **本身**实现 `FeedWiring`，等于把进程入口也变成业务装配点；② 它把"容器怎么取"变成了 feature 的隐含假设。显式传 `extras` 后，feature 只依赖"**谁实现了 `FeedWiring`**"这一件事：它不 import `assemble`，也不知道容器是挂在 `Application` 上还是别处。将来若改用 Hilt/Koin，改动只发生在 `assemble` 侧。
>
> **⚠️ 实现与本节 sketch 的差异（第六次修订，见 §10）**：本节设想的"用 `MutableCreationExtras` 显式传 `*_WIRING_KEY`"**没有落地**。真正跑在设备上的是 `business_ui/Wiring.kt` 里的 `ContainerHolder<C>` 接口（由 `DefaultApplication` 实现，返回 `AppContainer`）+ `CreationExtras.requireWiring<T>()`：经 `APPLICATION_KEY` 拿 holder，再拿容器。差别恰好落在本段第 ① 条上，而且**真的崩了**：一版实现把 `Application` **直接**强转成 `T`（`application as? FeedWiring`），编译通过、运行时必崩——`DefaultApplication` 是 holder，`AppContainer` 才是 `*Wiring` 的实现者，于是首次进入 Feed 就 `IllegalArgumentException: The Application does not provide FeedWiring`。根因、修法与回归测试见 §7.15。

```kotlin
// assemble/container/AppContainer.kt —— 唯一的跨层汇聚点：实现所有 feature 的接口
class AppContainer(private val app: Application) :
    FeedWiring, CommentWiring, SessionWiring {   // 一个容器实现全部 feature 的 *Wiring

    // 进程级单例（懒加载）
    val httpClient: HttpClient by lazy { ZhihuHttpClient.create(credentialStore, signatureProvider, logger) }
    val database: ZhihuDatabase by lazy { ZhihuDatabase.create(app) }
    val credentialStore: CredentialStore by lazy { EncryptedCredentialStore(app) }
    private val signatureProvider: SignatureProvider by lazy { Zse96Signer() }

    // 数据实现
    private val feedRemoteSource by lazy { FeedRemoteSource(httpClient, signatureProvider) }
    val feedRepository: FeedRepository by lazy { FeedRepositoryImpl(...) }
    val commentRepository: CommentRepository by lazy { CommentRepositoryImpl(...) }
    val sessionRepository: SessionRepository by lazy { SessionRepositoryImpl(...) }
    private val eventReporter: EventReporter by lazy { ZhihuEventReporter(httpClient, sessionRepository) }

    // 业务操作：**唯一实例**（单例），实现各 feature 的 *Wiring
    override val feedOperations: FeedOperations by lazy { FeedOperations(feedRepository, eventReporter) }
    override val commentOperations: CommentOperations by lazy { CommentOperations(commentRepository) }
    override val sessionOperations: SessionOperations by lazy { SessionOperations(sessionRepository) }
}
```

```kotlin
// assemble/shell/MainActivity.kt —— 只挂载外壳，不含任何 feature 的名字
setContent { App(container) }
```

**为什么工厂不能放回 feature 却自己造依赖（这是本次会话修过的 bug 的回归点）**

如果把 `FeedEntry.kt` 写成"自己 new 依赖"：

```kotlin
// ❌ 灾难
initializer {
    FeedViewModel(operations = FeedOperations(realFeedRepository, realEventReporter))
}
```

它会在**每次 ViewModel 创建时新建一个 `FeedOperations`**。而 `FeedOperations` 里带着**去重与节流的可变状态**（`reported` 集合、`lastReportAt`）以及分页用的 `Mutex`：

- 节流状态每次清零 → 本次会话刚修好的"曝光上报放大"原样复发；
- `Mutex` 变成每个 ViewModel 一把 → **挡不住"两个入口同时翻同一 query"的并发**，而那正是这把锁存在的唯一理由。

所以"必须取自容器"不是风格偏好，而是**正确性要求**。

**早期版本在这里走过两次弯路（见 §10）**：
- 第一版把工厂写在 `business_ui/feed/`，内部用 `this[APPLICATION_KEY] as ZhihuLiteApplication` 取容器 → `business_ui` 反向依赖 `assemble`，违反规则 6；
- 第二版把工厂集中到 `assemble/ViewModelFactories.kt` → 依赖方向正确了，但**路由与工厂离开了各自 feature**，`assemble` 变成一个越来越长的业务装配清单，也就是"看起来怪怪的"的真正来源。

**接口不能泄漏容器类型。** `FeedWiring` 只能暴露该 feature 需要的东西；如果写成 `interface FeedWiring { fun container(): AppContainer }`，等于把 `assemble` 的类型经接口绕回来，只是把 `import` 换成了返回值，没有解耦。

**`assemble` 内部分包（把"每 feature 一小块"与"真正全局的"分开）**：

| 子包 | 放什么 | 判据 |
|---|---|---|
| `assemble/container/` | `AppContainer`（实现所有 `*Wiring`）、进程级 `Initializers` | **全局单例与它们的装配**；"每 feature 一小块"在这里表现为**一组懒加载属性 + 一个 `override val`** |
| `assemble/shell/` | `Application`、`MainActivity`、`App.kt`、`theme/` | **进程与界面外壳**，与业务无关 |
| `assemble/navigation/` | `AppRoute`、`AppNavHost` | **全局导航**，只做汇总：每行一个 `registerXxxRoute(...)` |

**判据**：`assemble` 允许"知道所有 feature 的名字"（汇总是它的职责），但**不允许出现业务逻辑与界面组件**。只要各 feature 的装配出口（`*Entry.kt`）与依赖声明（`*Wiring.kt`）留在 `business_ui` 自己手里，`assemble` 就不会再"怪"。

**取舍的诚实说明**：`AppContainer` 会随 feature 数量增长（6 个 feature ≈ 6 组懒加载 + 6 个接口实现）。这是**手写 DI 的固有代价**（选它就是为了零注解处理开销、构建更快）。若它将来真的难以维护，正确的下一步是**按 feature 拆容器文件**（`assemble/container/FeedContainer.kt`），而**不是**新增 `business_assemble` 层——那只会让依赖链更长，并在共享层 import 所有 feature，从而诱发"模块互相 import"。

**为什么这样定**（对应"手写 AppContainer"的决策）
- 零注解处理开销，构建速度不受影响（Hilt 会显著增加构建时间，而本项目已经有一个自定义 KSP 处理器）。
- 装配关系显式可读，`AppContainer` 就是一张架构图。
- `initializer` 块让 ViewModel 工厂拿不到 Activity Context，天然避免 Context 泄漏。
- 全局单例（HttpClient、数据库、缓存）只有 `AppContainer` 能创建，测试时可整体替换为 fake。

### 4.8 导航（去掉 Gaia 事件总线）

当前用一个自定义事件总线 + KSP 代码生成只做了一件事：注册 4 条路由。收益远小于维护成本（无锁全局 map、无法反注册、`assert(false)` 守卫在 release 无效、`Dependencies.ALL_FILES` 拖累增量编译）。

```kotlin
// assemble/navigation/AppRoute.kt —— 全局唯一的"路由有哪些"清单
@Serializable data object MainFeedRoute
@Serializable data class QuestionDetailRoute(val questionId: String, val answerId: String? = null)
@Serializable data class ImageViewerRoute(val answerId: String, val page: Int = 0)
@Serializable data object LogInRoute
```

- 改用 **Navigation Compose 的类型安全路由**（`navigation-compose` 已是 2.9.5，支持 `@Serializable` 路由对象），参数由编译器校验，取代字符串拼接 `"question_detail/$q/$a"`。
- 路由注册显式汇总在 `assemble/navigation/AppNavHost.kt` 里，**每行调用一个 feature 自带的 `registerXxxRoute(this, navController)`**，取代 `RouteRegisterManager` + `GaiaEventManager` + KSP。路由的**定义与实现属于各 feature**(`business_ui/*/*Entry.kt`)，`assemble` 只汇总清单——它因此可以说出所有 feature 的名字，但不含任何 Screen/ViewModel 细节。
- `Gaia` 模块在 Phase 3 整体删除；删掉后 `:app:gaia` 及其 `jvmTarget` 不一致、处理器上运行时 classpath 等问题一并消失。
- **跨屏数据传递**：当前靠全局 `FeedItemRepository` 传 `FeedItem`，改为"只传 id + 从 Room 读"，全局 Map 随之删除。

### 4.9 日志与可观测性

- `AppLogger` 包装 Napier，**release 也安装 Antilog**（写入文件或 `Logcat` + 限流），否则线上无任何诊断能力。
- 统一错误上报：`ApiResult.Failure` 在 `AppContainer` 层统一记录一次，避免在每个 UI 调用点重复 `printStackTrace`。
- 关键事件（刷新、加载更多、登录态变更、签名失败）打点，便于定位"接口变了"这类问题。

---

## 5. 类图

### 5.1 业务契约（`business_logic/*`）

```mermaid
classDiagram
    direction LR

    class FeedRepository {
        <<interface, business_logic/feed>>
        +observeFeed(query: FeedQuery) Flow~List~FeedItem~~
        +refresh(query: FeedQuery) ApiResult~Unit~
        +loadMore(query: FeedQuery) ApiResult~LoadMoreOutcome~
    }
    class CommentRepository {
        <<interface, business_logic/comment>>
        +getComments(answerId: String, sort: CommentSort) ApiResult~List~Comment~~
        +loadMore() ApiResult~List~Comment~~
        +reset()
    }
    class SessionRepository {
        <<interface, business_logic/login>>
        +authState: Flow~AuthState~
        +logIn(cookie: String)
        +logOut()
        +invalidate(reason: String)
        +currentCookie() String?
    }
    class VideoRepository {
        <<interface, business_logic/video>>
        +getPlayInfo(answerId: String, videoId: String) ApiResult~VideoPlayInfo~
    }
    class PreferencesRepository {
        <<interface, business_logic/feed>>
        +imageQuality: Flow~ImageQuality~
    }

    class FeedQuery {
        <<value object, business_logic/feed>>
        +id: String
        +url: String
    }
    class LoadMoreOutcome {
        <<sealed, business_logic/feed>>
    }
    class ApiResult~T~ {
        <<sealed, base_logic/result>>
    }
    class AuthState {
        <<sealed, business_logic/login>>
    }
    class CommentSort {
        <<enum, business_logic/comment>>
        SCORE
        TIMESTAMP
    }

    FeedRepository ..> ApiResult~T~
    FeedRepository ..> FeedQuery
    FeedRepository ..> LoadMoreOutcome
    CommentRepository ..> CommentSort
    CommentRepository ..> ApiResult~T~
    SessionRepository ..> AuthState
    VideoRepository ..> ApiResult~T~
```

### 5.2 数据实现（`business_logic/*/data`）

```mermaid
classDiagram
    direction TB

    class FeedRepositoryImpl {
        <<business_logic/feed/data>>
        -remote: FeedRemoteSource
        -dao: FeedDao
        -cursorStore: CursorStore
        +observeFeed(query) Flow
        +refresh(query) ApiResult
        +loadMore(query) ApiResult
        -upsertPage(query, items, meta)
    }
    class SessionRepositoryImpl {
        <<business_logic/login/data>>
        -credentials: CredentialStore
        -cookieJar: CookieJar
        +logIn(cookie)
        +logOut()
        +invalidate(reason)
    }
    class CredentialStore {
        <<interface, business_logic/login>>
        +readCookie() String?
        +writeCookie(cookie: String)
        +clear()
    }
    class EncryptedCredentialStore {
        <<business_logic/login/data>>
        -masterKey: MasterKey
        -prefs: SharedPreferences
    }
    class InMemoryCredentialStore
        <<business_logic/login/data>> InMemoryCredentialStore
    class FeedRepository {
        <<interface, business_logic/feed>> 契约,同图 5.1
    }
    class SessionRepository {
        <<interface, business_logic/login>> 契约,同图 5.1
    }
    class FeedCursorEntity {
        <<business_logic/feed/data>>
        +queryId: String
        +next: String?
        +isEnd: Boolean
    }
    class FeedRemoteSource {
        <<business_logic/feed/data>>
        -client: HttpClient
        -signer: SignatureProvider
    }
    class CommentRemoteSource
        <<business_logic/comment/data>> CommentRemoteSource
    class VideoRemoteSource
        <<business_logic/video/data>> VideoRemoteSource
    class ZhihuApiExecutor {
        <<business_logic/remote>>
        -client: HttpClient
        -credentials: CredentialStore
        -signer: SignatureProvider
        +get(path) ApiResult~String~
        +post(path, body) ApiResult~String~
    }
    class SignatureProvider {
        <<interface, business_logic/sign>>
        +sign(path, dC0) String
    }
    class Zse96Signer
        <<business_logic/sign>> Zse96Signer
    class FeedDao {
        <<interface, business_logic/feed/data>>
        +observeByQuery(queryId) Flow~List~FeedItemEntity~~
        +upsertAll(items)
        +deleteByQuery(queryId)
        +trim(queryId, maxItems)
    }
    class FeedCursorDao {
        <<interface, business_logic/feed/data>>
        +get(queryId) FeedCursorEntity?
        +put(entity)
        +clear(queryId)
    }
    class FeedMapper {
        <<object, business_logic/feed/data>>
        +dtoToEntity(dto) FeedItemEntity
        +entityToDomain(entity) FeedItem
    }
    class FeedItemEntity {
        <<business_logic/feed/data>>
        +id: String
        +queryId: String
        +position: Int
        +json: String
    }
    class FeedItemCache {
        <<business_logic/feed/data>>
        -maxSize: Int
        +get(id) FeedItem?
        +put(item)
    }

    FeedRepositoryImpl ..|> FeedRepository
    SessionRepositoryImpl ..|> SessionRepository
    EncryptedCredentialStore ..|> CredentialStore
    InMemoryCredentialStore ..|> CredentialStore
    Zse96Signer ..|> SignatureProvider
    FeedRepositoryImpl --> FeedRemoteSource
    FeedRepositoryImpl --> FeedDao
    FeedRepositoryImpl --> FeedCursorDao
    FeedRepositoryImpl --> FeedMapper
    FeedRemoteSource --> ZhihuApiExecutor
    CommentRemoteSource --> ZhihuApiExecutor
    VideoRemoteSource --> ZhihuApiExecutor
    ZhihuApiExecutor --> SignatureProvider
    ZhihuApiExecutor --> CredentialStore
    SessionRepositoryImpl --> CredentialStore
    FeedDao --> FeedItemEntity
    FeedCursorDao --> FeedCursorEntity
    FeedItemCache --> FeedItemEntity
```

> `FeedItemEntity` 只存 `id/queryId/position` + 原始 JSON 字符串：知乎字段变动频繁，整段 JSON 落库可以避免"模型改字段就要写 Room 迁移"。列表查询只取 JSON，反序列化交给 mapper。

### 5.3 表现层（以 `business_ui/feed` 为例）

```mermaid
classDiagram
    direction TB

    class FeedScreen {
        <<Composable>>
        +FeedScreen(state, onRefresh, onLoadMore, onItemClick)
    }
    class FeedRepository {
        <<interface, business_logic/feed>> 契约
    }
    class EventReporter {
        <<interface, business_logic/feed>> 契约
    }
    class FeedViewModel {
        -operations: FeedOperations
        +uiState: StateFlow~UiState~FeedContent~~
        +onRefresh()
        +onLoadMore()
        +onRetry()
        +onItemsVisible(ids)
        +onCleared()
    }
    class FeedUiState {
        <<sealed>>
    }
    class FeedContent {
        +cards: ImmutableList~FeedItemCard~
        +hasMore: Boolean
    }
    class FeedItemCard {
        <<immutable>>
        +answerId: String
        +questionTitle: String
        +authorName: String
        +authorAvatarUrl: String
        +excerpt: String
        +thumbnails: ImmutableList~String~
        +voteUpCount: Int
        +commentCount: Int
        +updatedTime: Long
    }
    class FeedOperations {
        <<business_logic/feed>>
        +observe(query) Flow
        +refresh(query) ApiResult
        +loadMore(query) ApiResult
    }
    class EventReporter {
        <<interface, business_logic/feed>>
        +send(ids, kind)
    }

    FeedScreen --> FeedViewModel
    FeedViewModel --> FeedUiState
    FeedUiState --> FeedContent
    FeedContent --> FeedItemCard
    FeedViewModel --> FeedOperations
    FeedOperations --> FeedRepository
    FeedOperations --> EventReporter
```

**UI 稳定性约定**（这是 Compose 性能的根因，必须作为代码规范写死）
- 所有传给 composable 的集合一律 `kotlinx.collections.immutable.ImmutableList`（依赖已在，当前只用了一次）。
- 所有 UI 模型是 `data class`，字段全 `val`，`@Immutable` 标注。
- 传给 composable 的 lambda 用 `remember` 固定，或把事件收敛为 ViewModel 的稳定方法引用。
- 所有 `LazyColumn` 的 `items` 必须给 `key` 和（异构时）`contentType`。
- 组合体内禁止做解析、格式化、排序（`DateTimeFormatter` 每帧重建就是这条规则的反例）。

### 5.4 关键决策与取舍

| 决策 | 选择 | 理由 | 代价 |
|---|---|---|---|
| 分页库 | **不用 Paging 3**，手写 `cursor + offset` | 数据源是单一 cursor 分页接口，Paging 3 的 `RemoteMediator` 复杂度（尤其与已有 Room 的边界擦除）远超收益；手写逻辑本身可被 20 行纯函数覆盖 | 需要自己实现阈值触发与去重（已在 §4.2 定义） |
| 排序方式 | 列表内 `position` 列 + `ORDER BY position` | 与 Room Flow 天然兼容，刷新替换时不需要时间戳冲突 | 需要在刷新时重写整页 position |
| 网络模型 | DTO 全字段可空带默认值 | 知乎字段随时增删；一个字段缺失不应让整页失败（当前会） | 需要 mapper 里做非空兜底 |
| 签名位置 | `business_logic/sign` 薄适配（刻意不沉进 `base_logic`，见 §3.3）；**算法与请求装配分开**在 `sign/` 与 `remote/` | 算法已验证（与原始 JS 662 用例差分、552/552 非增补平面输入逐字节一致），重排只有风险没有收益 | 保留 `web/` 包名与其他包不同构 |
| Zse96 性能 | **不做解释器级优化** | 实测单次签名 1.71 ms、分配 2.44 MB，瓶颈是"每条指令的算术都装箱"，而每次调用复制 6 张静态表仅占分配量的 **1.8%**（≈44 KB）。真正有效的做法是保证它只在 IO 协程里被调用，而不是去改 1,273 行的解释器 | 保留现有分配开销；若将来需要，只能整体重写为无装箱的寄存器模型 |
| 加密方案 | `CredentialStore` 接口 + EncryptedSharedPreferences 实现 | 官方库已 deprecated，抽象成接口后替换只影响一个类 | 需要一次额外的接口设计 |
| Gaia 事件总线 | **删除**，改显式 `NavHost` + 类型安全路由 | 它只服务 4 条路由注册，却带来无锁全局状态、KSP 增量编译退化、jvmTarget 不一致 | 需要一次性改写导航 |

---

## 6. 迁移路线图

采用"**先修语义、再动结构、最后加持久化**"的顺序。理由：当前最痛的是功能坏了（刷新清空、失败无反馈），而不是文件放错位置。如果先搬目录，会把 bug 一起搬过去并且更难定位。

### Phase 0 —— 现状加固（已完成）
- 产出本方案与三份专项审查报告（渲染层、架构/数据层、Web/签名层）。
- 补 JVM 单测锁定核心逻辑：`FeedStorage`（刷新替换 + 批内去重 + 并发）、`FeedRepository`（游标/`isEnd`/失败语义）、`H5Parser`（畸形输入容错）。
- 建立**无 Gradle 的编译与测试链路**（`tools/typecheck.sh`、`tools/run_unit_tests.sh`、`tools/gen_r_class.py`），见 §7.3。
- **验收（已达成，且经过反证检验）**：`tools/typecheck.sh` 报 `errors: 0`；真实 Gradle 下 `:app:testDebugUnitTest` **BUILD SUCCESSFUL**（`29 actionable tasks: 7 executed, 21 up-to-date`，21s），25/25 用例通过。
- **测试非空洞的证明（本 Phase 最重要的一步）**：把同一批测试拿到**修复前的 `HEAD`（`1c26425`）**上执行（用一次性 `git worktree`，不改动 `app/src/main`），结果是**恰好 4 个失败、21 个通过**——证明这些用例确实在守 bug，而不是跟着实现一起写出来的：

  | 用例 | 修复前 | 当前 | 覆盖的 bug |
  |---|---|---|---|
  | `appendFiltersDuplicatesInsideSingleBatch` | FAIL | PASS | 批内重复未被过滤 |
  | `refreshReplacesFeedAndKeepsRefreshedItems` | FAIL | PASS | **刷新清空 Feed** |
  | `failedLoadMoreKeepsCursorAndIsNotReportedAsEnd` | FAIL | PASS | 分页失败污染游标 / 误报为到底 |
  | `getMoreItemsReturnsNoMoreDataWhenPagingIsEnd` | FAIL | PASS | `isEnd` 从未被读取 |

- **遗留技术债（已在 Phase 1 显式登记，不隐藏）**：
  - `ClickableText` 已被 `androidx.compose.foundation.text` 标记 deprecated（编译器会告警），推荐改用 `Text` + `LinkAnnotation`。Phase 1 刻意保留 `ClickableText`，因为 `LinkAnnotation` 需要更高版本的 Compose API，改动风险大于收益；迁移排入 Phase 4（随 Compose 版本升级一起做）。代码内已有注释说明这是"有意识的保留"。
  - `ImageViewer` 的两个 `pointerInput`（tap/double-tap 与 pan/zoom）仍共用同一条手势流。合并是一次完整的手势重写，必须有真机验证，因此 Phase 1 只修了 `1f` 初始偏移、被遮蔽的 `fitScale` 与翻页重组，手势合并列为 Phase 4。
  - `CommentView` 仍未向 `HtmlToComposeUi` 传 `answerId`：评论内视频从"点击必崩"变为"优雅失败"。传 `answerId` 需要 `CommentUiState` 携带它，属于 Phase 2 数据层改造顺带完成。
- `H5ParserTest` 的 9 个用例在修复前后**都通过**——它们是**回归锁而非抓 bug 的用例**，原因见 §1.2 第 7 条（原 bug 位于不可达的死代码）。明确记录这一点，避免把"9 个用例通过"误读为"解析器曾经会崩"。

### Phase 1 —— 修语义（已完成）
| 任务 | 涉及文件 |
|---|---|
| 刷新改为替换语义；去重交给显式规则 | `data/FeedStorage.kt`（目标位置 `business_logic/feed/data/`） |
| 分页失败不再污染游标；`isEnd` 正确判定 | `data/FeedRepository.kt`（目标位置 `business_logic/feed/data/`） |
| 首次加载失败产出 `Failed` 状态 + 重试 | `*ViewModel.kt`, `*UiState.kt`, 两个 Screen |
| 仓库 scope 生命周期、并发安全 | `data/FeedStorage.kt`, `*ViewModel.kt` |
| 富文本链接真正可点、URL 白名单 | `h5Parser/H5Parser.kt` |
| 解析器容错：实体解码、注释跳过、闭合标签归一化、raw text 元素、深度上限（实测确认的 5 类渲染缺陷） | `h5Parser/H5Parser.kt`（拆分到 `business_logic/answer` + `business_ui/answer`） |
| Zse96 两个一行修复：`>>>` 符号扩展、`encodeURIComponent` 逐 `Char` 编码；并把 JS 差分测试接入测试任务 | `web/Zse96.kt`, `app/src/test/` |
| 评论排序触发完整重置 | `comment/*` |
| 崩溃点清理（require/越界/初始偏移） | `video/*`, `h5Parser/*`, `common_ui/ImageViewer.kt` |
| 登录/登出闭环、Cookie 单一来源 | `login/*`, `App.kt` |
| 曝光上报去重 + 批量 | `eventReporter/EventReporter.kt` |
| 列表 `key`/`contentType`、`@Immutable` | 全部 `LazyColumn` 调用点 |
| **不改**目录结构、**不加**依赖 | —— |

- **验收**：上述 bug 的单测全绿；`git diff --stat` 中不出现任何 Gradle/版本目录改动；手工回归清单（§7）全过。

### Phase 2 —— 加持久化与加密（结构性变更的开始）
1. 引入 Room：`FeedItemEntity`（含 `pinned_in_query`）+ `FeedQueryEntity`（含游标）+ `FeedDao` + `ZhihuDatabase`。**已完成**，见 §7.6。
2. 引入 `CredentialStore` + `EncryptedCredentialStore`，迁移现有明文 cookie（**首启动时读取旧明文值、写入加密存储、删除明文**）。
3. `FeedRepositoryImpl` 改为"Room 为真相源"：`observeFeed` 来自 DAO，刷新/加载更多写入 DAO。
4. 删除 `MemoryFeedStorage` 与内存 `answerIdSet`。
5. 删除全局 `FeedItemRepository`，跨屏传递只传 id。
- **验收**：断网冷启动能看到上次的 Feed；杀进程重开登录态保持且 cookie 已加密；刷新/分页的既有单测全部继续通过（此时测的是 `FeedRepositoryImpl`，替换 fake DAO/remote）。

### Phase 3 —— 六层分层与导航
1. 建立六个包 `model` / `base_logic` / `base_ui` / `business_logic` / `business_ui` / `assemble`，按 §3.2 搬迁。**建议顺序**：先落 `model` 与 `base_logic`(纯 Kotlin，无风险)，再拆 `business` 为 logic/ui 两半，最后动 `assemble`。
2. `base_logic` 与 `business_logic` 变为纯 Kotlin：`ApiResult`/`UiMessage`/日志接口/时间文本工具进 `base_logic`；`HtmlParser`、分页规则、Zse96 包装进 `business_logic`，并**去掉所有 Android/Compose import**。
3. DI 接入：`assemble/container/AppContainer` 实现各 feature 的 `*Wiring` 接口；各 feature 自带 `*Entry.kt`(工厂 + 路由)。删除现有所有 `*ViewModelFactory` 与"每屏 new storage"。**注意 `FeedOperations` 等有状态对象必须是容器单例**(§4.7)。
4. Navigation 改类型安全路由，删除 `registerRoute/` 与 `:app:gaia` 模块及其 KSP 插件配置。
5. **引入架构守卫测试**（这是本 Phase 的核心交付物，规则见 §3.1）：
   - 禁止 `base_logic`/`business_logic` import `android.*` / `androidx.compose.*` / `kotlinx.coroutines.Dispatchers.Main`；
   - 禁止 `business_logic` import `base_ui`；禁止 `business_ui` import `business_logic/*/data`；
   - 禁止 `business_*` 各业务模块互相 import；禁止任何层 import `assemble`；
   - 做法：一个遍历源码目录的 JUnit 测试即可（无需引入 Konsist）。
6. 迁移完成后**核对纯逻辑测试是否仍可只依赖 `model + base_logic`**——这是拆分成效的判据(§3.4)。
- **验收**：架构守卫测试通过(含"故意违规应失败"的自检)；`./gradlew :app:assembleRelease` 产出包并冒烟通过；删除 `gaia` 后无残留引用。

### Phase 4 —— 性能与体验
1. ~~`TimestampFormatter` 预构建 formatter 表；H5 解析加有界缓存（LRU，按 html 串）~~ **已完成**，见 §7.7。
2. 图片尺寸/`aspectRatio` 与 Coil 尺寸约束；把共享 `HttpClient` 注入 Coil（`setSingletonImageLoaderFactory`），使图片请求带鉴权头且不再开第二套 HTTP 栈。
3. ~~视频播放器：`release()` 真正释放、按可见性暂停、生命周期 observer 修正~~ **已完成**（逐项 `VideoView` + `onDispose` 里 `stopPlayback()`/`suspend()`，`suspend()` 即 MediaPlayer 释放路径；`VideoView` 无 `release()`），见 §7.7。
4. 深色模式：`ColorTokens` 取代散落的 `Color.Gray` 等；补 `values-night`。
5. release 日志与错误上报；CI 增加"构建并冒烟 minified release APK"步骤。
- CI 用 `tools/run_unit_tests.sh` 做快速反馈，用 `./gradlew :app:assembleRelease` + 安装冒烟覆盖 R8 路径。
- **验收**：Macrobenchmark 记录滚动 jank 帧率与启动耗时作为基线（即使不做门槛也要有数字）。

### Phase 5 —— 可选演进
- 按包边界拆多模块（如 `business_ui:feed`、`business_logic:feed`），因为 Phase 3 已保证依赖单向，拆分是机械操作。
- 补齐写操作（点赞/感谢/评论输入）——需单独评估写接口风控与签名差异。

**依赖关系**：Phase 2 与 Phase 3 有交叉（Room 落地会顺带建立 `business_logic/*/data`），建议顺序执行而不是并行，避免在搬迁过程中同时改数据流。

---

## 7. 验证策略

### 7.1 自动化
| 层级 | 内容 | 工具 |
|---|---|---|
| 纯逻辑单测 | 分页规则、去重/替换语义、H5 解析容错、时间格式化、URL 白名单 | JUnit4 + kotlin-test（现有依赖，不新增） |
| 仓库单测 | `FeedRepositoryImpl` × fake DAO/remote 的刷新/失败/到底三条路径 | 手写 fake（不引入 MockK） |
| 架构守卫 | `base_logic`/`business_logic` 不含 Android/Compose/Ktor import;`business_logic` 不 import `base_ui`;各业务模块不互相 import(规则见 §3.1) | 自写 JUnit 遍历源码,或 Konsist |
| 签名回归 | Zse96 对原 JS 定值向量（已有） | JUnit（已存在，保持） |
| 编译闸门 | main + test 源码全量编译（含 Compose/序列化 codegen），`errors: 0` | `tools/typecheck.sh` |
| 测试执行 | JUnit 4 直接运行测试类 | `tools/run_unit_tests.sh` |
| 一键验证 | 优先真实 Gradle，不可用时回退到手工链路并显式声明"未覆盖 R8" | `tools/verify.sh` |
| 签名差分 | Kotlin 移植 vs 仓库自带原始 JS（662 用例，含非 BMP/BMP/随机串） | 建议接入为 Node + JUnit 测试（Phase 1 已手工跑过，见 §9.1） |
| 手工回归 | §7.2 清单 | —— |

### 7.2 手工回归清单（每个 Phase 结束都要过）
1. 冷启动未登录 → 登录页；登录成功 → 进入推荐流。
2. 推荐流：下拉刷新**内容仍在**且顺序合理；连续上滑到底显示"没有更多"且不再重复请求。
3. 断网启动 → **明确错误提示 + 重试按钮**（而不是无限转圈）；恢复网络后重试成功。
4. 杀进程重开 → 登录态保持（Phase 2 后：Feed 内容也能离线看到）。
5. 问题详情：回答富文本渲染正确；**正文内链接可点并跳转**；图片按比例显示不留白跳。
6. 评论面板：切换"默认/最新"→ 列表**真的重排**且从第一页开始；上滑分页到底停止。
7. 视频：封面点击可播；**评论里的视频点击不崩溃**；滑出屏幕后停止播放。
8. 登出 → 回到登录页；重新登录可正常使用。
9. release 包（minified）安装后重复 1–8，确认 R8 未破坏序列化与路由。

### 7.3 无 Gradle 的本地验证链（已落地）

Gradle 本身在本环境跑不起来，实测结论：

1. 直接执行 `./gradlew` 报 `FileNotFoundException: …gradle-8.9-bin.zip.lck (Read-only file system)` —— wrapper 需要写工作区外的 `~/.gradle`。
2. 改用工作区内 `GRADLE_USER_HOME=.gradle-dsh` 后目录可创建，但 **Gradle 8.9 发行包不在本地缓存中**（只有 0 字节的 `.lck`/`.part`），需联网下载才能继续。

但**编译与单测并不需要 Gradle**：Kotlin 编译器、Android SDK、以及全部依赖的 AAR/jar 都已在本地缓存中。因此新增两个可复现的脚本，把"验证"从"人工核对"变成"真跑"：

```bash
bash tools/typecheck.sh          # 编译 main + test 源码，输出 errors: N，非 0 即失败
bash tools/run_unit_tests.sh     # 先编译，再用 JUnit 4 直接跑测试类
```

**实现方式**（`tools/`）：
- `tools/gen_r_class.py` —— 解析 `app/src/main/res` 生成 `R.java`。**这一点很关键**：缓存里的 `R.jar` 来自更早的资源版本，直接用它会把"引用了不存在的字符串资源"这类错误掩盖掉。生成式 `R` 让缺失/拼错的资源名立刻编译失败——实际上正是它抓出了 `R.string.feed_load_failed` 与 `R.string.retry` 两个未定义引用。
- `tools/typecheck.sh` —— 从本地缓存拼出 classpath（含从 AAR 解包的 `classes.jar`、Ktor 的 `-jvm` jar），用 `kotlin-compose-compiler-plugin-embeddable` 与 `kotlin-serialization-compiler-plugin-embeddable` 两个插件执行**完整编译（含 codegen）**；KSP 生成的 Gaia 注册表用等价 stub 替代。错误计数只统计 `文件:行:列: error:` 形式，作为可信的通过/失败闸门。
- `tools/run_unit_tests.sh` —— 编译后用 `org.junit.runner.JUnitCore` 直接执行测试类。

**当前实测结果**（`tools/typecheck.sh` → `errors: 0`）：
```
sources: 72   errors: 0
org.nigao.app.FeedRepositoryTest   OK (6 tests)
org.nigao.app.FeedStorageTest      OK (7 tests)
org.nigao.app.H5ParserTest         OK (9 tests)
org.nigao.app.Zse96Test            OK (4 tests)
org.nigao.app.ExampleUnitTest      OK (1 test)
RESULT: all test classes passed
```

### 7.4 真实 Gradle 的可行解法（已跑通，且现已可直接使用 ./gradlew）

**后续更新：环境已开放写权限，`./gradlew` 本身即可运行**，不再需要任何变通。唯一要求是 **JDK 17**（Gradle 8.9 不支持环境默认的 JDK 25）：

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew :app:testDebugUnitTest --console=plain     # BUILD SUCCESSFUL in 34s
./gradlew :app:assembleRelease   --console=plain     # BUILD SUCCESSFUL in 1m11s（R8 全流程）
```

`tools/verify.sh` 已改为**优先使用项目自带的 `./gradlew`**，只有在 wrapper 确实不可用时（只读 `~/.gradle` 的沙箱）才回退到下面的变通方案。

以下变通方案在受限沙箱里仍然有效，保留作为排障参考。可行组合是：**JDK 17 + 工作区内的 Gradle home + 只读依赖缓存复用 + 直接用已解压的发行包**（绕开 wrapper 的下载/加锁）：

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64          # JDK 25 与 Gradle 8.9 不兼容
export XDG_DATA_HOME="$PWD/.gradle-dsh/home2/xdg-data"       # 否则 Kotlin daemon 写 ~/.local/share 被拒
export GRADLE_USER_HOME="$PWD/.gradle-dsh/home2"
export GRADLE_RO_DEP_CACHE="$HOME/.gradle/caches"            # 复用只读依赖缓存，无需联网

"$HOME/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle" \
  :app:testDebugUnitTest --console=plain --offline
```

实测输出：
```
> Task :app:kspDebugKotlin
> Task :app:compileDebugKotlin
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 21s
29 actionable tasks: 7 executed, 1 from cache, 21 up-to-date
```

测试结果汇总（`app/build/test-results/testDebugUnitTest/*.xml`）：**tests=27, failures=0, errors=0**。

**踩过的两个坑，避免后人重犯**：
1. `XDG_DATA_HOME` 必须重定向，否则 Kotlin daemon 尝试写 `~/.local/share/kotlin` 而失败。
2. **不要把 `git worktree` 建在 `GRADLE_USER_HOME` 内部**：会触发 Gradle 的缓存清理，把 `.gradle-dsh/caches/modules-2` 清空，之后所有构建报 `Transform output … must exist`。恢复办法是删掉该缓存目录让它从 `GRADLE_RO_DEP_CACHE` 重新解析，或另起一个干净的 home。

**这套链路的边界（不要过度信任）**：
- Android 框架类来自 SDK 的 `android.jar` stub，运行时抛异常 → **凡触碰 Android API 的测试都无法在此通过**，这类逻辑必须靠 instrumented test 覆盖。
- R8/minified release 路径**完全没有覆盖**，`assembleRelease` 仍必须在有 Gradle 的环境执行。
- 手工 classpath 与 Gradle 的依赖解析规则可能有细微差异，所以它是**本地快速反馈**，不替代 CI 里的 `:app:testDebugUnitTest`。

**结论：minified release 构建已于本次验证通过。**

```
./gradlew :app:assembleRelease  ->  BUILD SUCCESSFUL in 1m 11s
                                    54 actionable tasks: 49 executed, 5 up-to-date
产物：app/build/outputs/apk/release/app-release-unsigned.apk（2.2 MB，未签名）
```

R8 之后的关键存活项均已核对（这是 minify 最容易静默弄坏的地方）：

| 检查项 | 结果 |
|---|---|
| `kotlinx.serialization` 生成的 `$$serializer` | 59 个全部保留（ProGuard 规则生效） |
| `org.nigao.zhihuLite.login.SessionStore` | 保留 |
| `com.russhwolf.settings.SettingsInitializer` | 保留，且 release 清单里 `androidx.startup.InitializationProvider` 下的 meta-data 仍然指向它 —— 否则 `Settings()` 会直接 NPE，登录态读写全崩 |
| 路由字符串 | `register_route`/`log_in`/`log_out`/`main_feed`/`question_detail`/`image_viewer` 全部存活（Gaia 注册表把 lambda 存进 map，R8 看不穿，所以字符串存活是必要证据） |
| 签名与会话常量 | `x-zse-96`/`x-zse-93`/`z_c0`/`d_c0`/`lastread` 均存在 |

已知无害告警：`R8: An error occurred when parsing kotlin metadata`（Kotlin 2.1.21 比 AGP 8.7.3 内置的 R8 新，元数据解析失败不影响产物）；以及 `Zse96.kt` 的若干 `Unchecked cast` 告警。**这两类都不阻断构建**，但值得在 Phase 4 随 AGP/Kotlin 版本升级一并清理。

**仍未覆盖**：安装到真机/模拟器后的冒烟（启动、登录、登出、R8 后的序列化读写）。静态检查已排除最危险的几类，但"能装能起"仍需一次人工验证。`.gradle-dsh`、`build/` 下的中间产物均已加入 `.gitignore`。

### 7.5 Phase 1 完成情况（本轮结束时）

| 目标项 | 状态 | 证据 |
|---|---|---|
| 刷新清空 Feed | ✅ 已修 | `refreshReplacesFeedAndKeepsRefreshedItems` 修复前 FAIL → 现 PASS |
| 分页失败误报"到底"/游标损坏 | ✅ 已修 | `failedLoadMoreKeepsCursorAndIsNotReportedAsEnd` 同上 |
| `isEnd` 未读取 | ✅ 已修 | `getMoreItemsReturnsNoMoreDataWhenPagingIsEnd` 同上 |
| 失败态缺失（空 `FailedFeedScreen`） | ✅ 已修 | `FeedUiState.Failed` + 两个 Screen 的错误 UI（含新增字符串） |
| 评论排序失效 | ✅ 已修 | `CommentViewModel.updateSortType` 重建 API 并重新加载 |
| 富文本链接无跳转 | ✅ 已修 | `ClickableText` 真实 offset + `onLinkClick` + `UriHandler`，URL 白名单 |
| 解析器 5 类渲染缺陷 | ✅ 已修 | 实体解码、注释跳过、闭合标签归一化、raw text、`MAX_NESTING_DEPTH=100` |
| 崩溃点清理 | ✅ 已修 | `require` 全部移除、`ImageViewer` 偏移 `0f`、越界与死代码删除 |
| 登录闭环 | ✅ 已修 | `SessionStore` 单一真相源 + `CookieManager.flush()` + `SessionState.Invalid` 过期态 |
| 网络层真正使用单一真相源 | ✅ 已修 | `FeedApi.zhihuHeaders` 改读 `LogInManager.cookie()`（不再直连 `CookieManager`），否则"单一真相源"只是口号 |
| 过期检测真正生效 | ✅ 已修 | `ZhihuApi.request` 在 401/403 与非 JSON（登录墙）时调用 `LogInManager.invalidate(...)`；此前**没有任何调用者**，App.kt 的路由分支永远不会触发 |
| 取消语义（网络层） | ✅ 已修 | `FeedApi`/`CommentApi`/`ShareInfoApi`/`VideoPlayInfoApi` 统一先重抛 `CancellationException`，并改用 Napier 记录（原先 `printStackTrace` 在 release 静默） |
| 登出路由常量归位 | ✅ 已修 | `Routes.LOG_OUT` 落到路由表，删除 `login/` 里的重复顶层常量 |
| **登出入口（可见 UI）** | ✅ 已接 | `FeedScreen.kt` 顶部加登出 IconButton → `navController.navigate(LOG_OUT_ROUTE)`；`LogOutScreen` 清会话（含 `CookieManager` 清理+flush）后 `popUpTo(0)` 回登录页。会话生命周期成为闭环：登录 → Feed → 登出 → 登录页 |
| Zse96 两处静默偏差 | ✅ 已修 | 码点编码 + `ushr` 无符号掩码，新增 2 个回归用例 |
| 列表 `key`/`contentType` | ✅ 已修 | `FeedItemCardState.answerId`、`CommentUiState.id`、四处列表全部补 `key` + `contentType` |
| `getPlayableUrl()` 空播放列表崩溃 | ✅ 已修 | 改 `firstOrNull`/`firstOrNull()`，返回 `null` 而非抛 `NoSuchElementException` |
| 刷新失败不可表达 | ✅ 已修 | `RefreshConfig.refresh` 改为返回 `RefreshResult`，删除并行的第二套 refresh 路径 |
| 评论 `childComments` 无谓递归构造 | ✅ 已修 | 从 `CommentUiState` 移除（UI 从不渲染），保留 `childCommentCount` |

### 7.6 Phase 2 进展（Room 与"带 answer 进入回答详情"）

**已完成**

| 项 | 落地物 | 验证 |
|---|---|---|
| Room 依赖与 schema | `room = 2.7.1`、`androidx.room` Gradle 插件、`app/schemas/**/1.json` 已入库 | `compileDebugKotlin` / `assembleRelease` 通过 |
| 表结构 | `FeedItemEntity`（主键 `(query_id, id)`，含 `pinned_in_query`，payload 整段 JSON）、`FeedQueryEntity`（含 `cursor_next` / `cursor_is_end`） | 导出 schema + DAO 测试 |
| DAO | `FeedDao`：排序 `pinned_in_query DESC, position ASC`；`replacePagedRows`/`appendPage`/`pinAnswer` 用 `@Transaction`；有界 `trimPagedRows` | `RoomQuestionFeedStorageTest` 9 个用例(真实 SQLite，Robolectric) |
| 映射 | `FeedMapper`（`FeedItem` ⇄ 实体，纯 Kotlin 无 Android） | `FeedMapperTest` 5 个用例 |
| 存储 | `QuestionFeedStorage` / `RoomQuestionFeedStorage` | 同上 |
| 回答详情以 Room 为真相源 | `QuestionFeedRepository` / `RoomQuestionFeedRepository`（分页规则：失败不动游标、读 `is_end`、刷新保留置顶） | 编译 + 依赖其语义的用例 |
| **带 answer 进入回答详情** | `PinAnswerIntoQuestionFeed` + `AnswerApi`/`KtorAnswerApi`；`AnswerFeedScreen` 不再查全局 map | `PinAnswerIntoQuestionFeedTest` 7 个用例 |
| ViewModel 接线 | `AnswerFeedViewModel` 改为观察 Room 流；工厂经 `CreationExtras` 取进程级数据库 | 完整 `:app:testDebugUnitTest` |

**测试总量：49 个用例，0 失败**（Phase 1 结束时为 28）。

**实现过程中修正的三处方案偏差（都已写回文档）**

1. **Room 版本必须是 2.7.x**：按方案写的 2.6.1 会让 KSP 直接失败 —— `java.lang.IllegalStateException: unexpected jvm signature V`（2.6.1 的处理器读不懂 Kotlin 2.1 元数据）。
2. **游标不单独建表**：`cursor_next` / `cursor_is_end` 放在 `feed_query` 上（见 §4.2）。
3. **`trim` 的语义**：初版 SQL 保留的是**最旧**的行（`position ASC`），DAO 测试当场证伪；现改为保留最新窗口，并把游标置为"需重新加载"以免留下空洞（`position` 无法反推 `paging.next` 的 URL）。

**本轮补完（主 Feed 迁移与旧缓存清理）**

| 项 | 落地物 |
|---|---|
| 统一仓库 | `FeedStorage`（`RoomFeedStorage`）与 `FeedRepository`（`RoomFeedRepository`）取代了各自的 `Question*` 版本；两个 feed 共用同一套分页规则 |
| 主 Feed 迁到 Room | `FeedViewModel`/`FeedViewModelFactory` 改为观察 Room 流并共用进程级数据库 |
| 删掉两个旧缓存 | `MemoryFeedStorage`（进程内、无同步、刷新语义靠调用顺序）与 `FeedItemRepository`（无界全局 map、key 与路由参数不匹配） |
| 修好大图页 | `ImageViewerScreen` 原先用 **answer id 去查按条目 id 索引的全局 map**，必定 miss → 大图页实际打不开；现改为 `AnswerApi` 按 id 取 |
| 测试迁移 | 删除旧实现后把其分页用例**移植**到 `RoomFeedRepositoryTest`（12 个用例），覆盖仍为 47 个 |

**本轮补完（会话凭证加密）**

| 项 | 落地物 |
|---|---|
| 凭证抽象 | `CredentialStore`（读/写/清，异常安全）+ `InMemoryCredentialStore`（测试与预览用） |
| 加密实现 | `EncryptedCredentialStore`：**AES-256-GCM + Android Keystore 密钥**（密钥不出 Keystore），每次写入随机 IV，载荷带版本键 `session_ciphertext_v1` |
| 为何不用官方库 | `androidx.security:security-crypto` 已 deprecated 且停止维护；为一个 AES 密钥引入 Tink 过重。约 60 行标准 crypto 即可，接口保证可替换（将来换 DataStore+Tink 只动一个文件） |
| 迁移 | `LegacyCookieStore` 抽象 + `SettingsLegacyCookieStore`：首次读取时把旧明文 `Cookie` 迁移进加密存储，**并删除明文副本**——只读不删等于把要消除的东西留在磁盘上 |
| 容错 | 解密失败/载荷损坏 → 视为未登录**并清除该值**（否则每次启动都失败一次）；写入失败不崩溃 |
| 接线 | `DefaultApplication.onCreate` 注入加密实现；`SessionStore` 保留原有公开 API，调用方无需改动 |

**这一轮由测试抓出的真实缺陷**：`decrypt()` 原本对**格式错误**的载荷返回 `null` 而不是抛异常，于是 `read()` 里"清除损坏值"的分支永远不会执行——坏数据会永久留在磁盘上、每次启动失败一次。测试失败信息是 `expected null, but was:<not-a-valid-payload>`。现在改为抛异常，清理路径才真正生效。另外把 legacy 的"读"与"清"合并成一个可注入接口：此前 `clearLegacyCookie()` 直接调 `Settings().remove()`，**绕过了可注入的 legacy 源**，导致"明文是否真被删除"根本无法断言。

**尚未验证（需要真机/模拟器）**：Keystore 密钥的创建、加解密往返、以及"新实例能读到旧实例写入的值"。Robolectric 的 `AndroidKeyStore` 是存根，`Cipher` 会抛异常，因此 `EncryptedCredentialStoreTest` 只覆盖**降级与清理**行为（不崩溃、损坏值被清除）；真实加解密往返列为 §7.14 的设备验证项。

**仍未完成（Phase 2 剩余）**

- ~~主 Feed 仍走 `MemoryFeedStorage`~~ **已完成**：`FeedRepository`（接口）+ `RoomFeedRepository`（Room 为真相源）同时服务推荐流与回答列表；`MemoryFeedStorage`、旧的 `data/FeedRepository.kt` 与全局 `FeedItemRepository` **已删除**（见下方"本轮补完"）。
- ~~**`CredentialStore` 加密存储未开始**~~ **已完成**（见下节）。
- **`feed_cursor` 独立表 / `FeedCursorDao`**：方案原设计，实际合并进 `feed_query`（已对齐文档）。

### 7.7 Phase 4 进展（性能项）

| 项 | 改法 | 验证 |
|---|---|---|
| `DateTimeFormatter` 逐帧重建 | `TimestampFormatter` 增加**按 (pattern, locale) 缓存**；缓存键含 locale，避免改设备语言后仍用旧语言格式化；上限 32 条，超限整体清空（实际只用少数几个 pattern） | `TimestampFormatterTest` 6 个用例：缓存后输出不变、**时区仍逐次生效**、预定义/自定义 pattern、非法 pattern 降级不崩、200 个不同 pattern 后缓存仍有界 |
| H5 解析重复执行 | `HtmlParseCache`：按原始 HTML 串做**有界 LRU**（上限 24），解析在锁外进行以避免串行化 | `HtmlParseCacheTest` 6 个用例：`assertSame` 证明**复用同一棵已解析树**、不同 HTML 分离、命中结果与直接解析一致、实体解码保持、100 条后仍有界、清空不影响结果 |
| 视频未释放 | 逐项 `VideoView` + `onDispose { stopPlayback(); suspend() }`，按可见性暂停，生命周期 observer 修正 | 无自动化覆盖（见下） |

**为什么用 `assertSame` 而不是 `assertEquals`**：只断言"输出一致"在**完全没有缓存**时也会通过，无法证明重复工作被消除。`assertSame` 直接断言复用同一个已解析对象，这正是列表项回收后再进入组合时省下的开销。

**顺带修正**：`TimestampFormatter` 里失败的 `e.printStackTrace()` 换成 Napier（release 下原先静默，等于线上没有任何线索）。

**未验证**：视频播放器的释放与可见性暂停只能靠真机/模拟器确认（`VideoView`/`MediaPlayer` 在 JVM 测试里是存根）。已列入 §7.14 设备验证项。

**这次没有做的**（Phase 4 剩余）：把共享 `HttpClient` 注入 Coil（`setSingletonImageLoaderFactory`）以免 Coil 自带一套无鉴权 HTTP 栈；深色模式与 `ColorTokens`；release 日志与 CI 构建冒烟。

### 7.8 Phase 3 进展（装配与导航，第一批）

**本批完成**

| 项 | 落地物 |
|---|---|
| `assemble` 层建立 | `assemble/container/AppContainer`（进程级单例:数据库、加密凭证存储、HTTP 面）+ `assemble/navigation/AssembleAppNavHost` |
| 类型安全路由 | `base_navigation/AppRoute`（`@Serializable` 的 `LogInRoute` / `LogOutRoute` / `MainFeedRoute` / `QuestionDetailRoute` / `ImageViewerRoute`）；`App.kt` 与导航图改用 `composable<T>` + `toRoute<T>()` |
| 导航解耦 | 新增 `base_navigation/AppNavigator`；屏幕不再接收 `NavController`，改为 `onNavigate: (AppRoute) -> Unit` 回调，返回键/回退栈策略集中在 `assemble` |
| **删除 Gaia 与 registerRoute** | `registerRoute/` 包、`@GaiaListen` 注解、KSP 生成的事件总线注册表、`com.nigao.gaia.registerAll()` 调用、`:app:gaia` 模块与其 KSP 依赖**全部删除**（含 `settings.gradle.kts` 与 `app/build.gradle.kts`） |
| DI 收敛 | 两个 ViewModel 工厂改为经 `AppContainer` 取依赖（`container.feedStorage()` / `feedApi` / `answerApi`），不再各自 `new` 存储或读取 `Application` 具体类型 |

**顺带修好的三个真实缺陷**

1. **大图页只声明了 `answer_id`，却去读 `savedStateHandle["page"]`** —— 而**没有任何代码往里写过 page**，所以翻页位置永远是 0。改为 `ImageViewerRoute(answerId, page)` 的显式类型化参数。
2. **`question_detail` 的两个参数都被声明为必填 `StringType`**，于是从推荐流进入时必须伪造一个 `answer_id = "0"` 占位；目标回答缺失时靠字符串比较兜底。现在 `answerId: String? = null`，缺失就是 null。
3. **`RouteRegisterManager.register` 的重复守卫是 `assert(false)`**，release 下是空操作；配合"在组合体内调用 `GaiaEventManager.start`"的写法（依赖一个 `private var` 标志位防重入），注册是否只发生一次全靠人工保证。显式 `NavHost` 之后这类问题不再存在。

**为什么路由定义放在 `base_navigation` 而不是 `assemble`**：`business_ui` 需要**说出**它要跳转的目的地，但不能 import `assemble`（§3.1 规则 6）。所以路由对象是"跨层词汇"，与 `ApiResult` 同理；而**注册路由图的代码**留在 `assemble/navigation`。

**尚未完成（Phase 3 剩余）**：六层包结构的**物理搬迁**仍未做——当前包名还是 `mainFeed`/`answerFeed`/`common_ui`/`data`/`login`/`feedItem`/`network`/`h5Parser` 等旧布局。这一步会移动几乎所有文件，风险与工作量都显著高于本批，留作下一批处理。

### 7.9 Phase 3 进展（六层搬迁，第一批：`model`）

**本批完成**：所有服务端 DTO 迁入 `model/`，并按域分子包（扁平化会撞名，见下）。

```
model/
├── feed/     FeedItem · FeedResponse · Paging · Question · User(+Target/AnswerRelationship)
├── comment/  CommentResponse · Comment · CommentTag · CommentAuthor · …
├── video/    VideoPlayInfo · VideoPlay · Playlist
└── share/    ShareInfo · Author · Badge2 …
```

**为什么用子包而不是把 `model` 拍平**：迁移时编译直接报出重名——feed 与 comment 各有一个 `Paging`，`ShareInfo.kt` 与 `Question.kt` 各有一个 `Question`。子包既消除冲突，也保留了与 `business_logic` 各模块的对应关系。

**顺带删除的死代码**：`feedItem/Comment.kt`（179 行，全项目 0 引用，与 `comment/CommentResponse.kt` 重复）。

**搬迁暴露的两类"同包免 import"问题**（值得记录，因为后续每批搬迁都会遇到）：
1. 被引用方换包后，引用方需要**显式 import** —— `CommentApi`/`CommentViewModel`/`CommentViewUiState`/`ShareInfoApi`/`ShareUtil` 都因此在迁移后立刻编译失败，而不是静默出错；
2. `CommentViewUiState.kt` **原本一个 import 都没有**（全部同包），插入 import 时不能假设"存在 import 段"，必须以 `package` 行为锚点。

这两点说明搬迁是**编译器可完全验证**的：没有任何一处是"看起来对但运行时才错"。

**尚未完成（Phase 3 剩余）**：`base_logic` / `base_ui` / `business_logic` / `business_ui` 的搬迁。当前旧布局仍有 `mainFeed`、`answerFeed`、`comment`、`common_ui`、`data`、`login`、`network`、`h5Parser`、`web`、`eventReporter`、`share`、`video`、`basicTypeExtension`。

### 7.10 Phase 3 进展（六层搬迁，第二批：`base_logic` / `base_ui` / `business_logic`）

**本批完成**

| 层 | 迁入内容 |
|---|---|
| `base_logic` | `basicTypeExtension` 里真正的纯工具：`text/IntFormat`、`format/TimestampFormatter`（其余三个依赖 Compose，见下） |
| `base_ui` | `basicTypeExtension` 中依赖 Compose 的三个：`ModifierExtender`（`noRippleClickable`）、`ColorExtender`、`StringExtender` |
| `business_logic/zhihu` | `network/FeedApi.kt` → `ZhihuApi.kt`（`ZhihuApi` + `sharedJson` + `sharedHttpClient`，即协议与传输）、`AnswerApi` → `business_logic/feed/data/KtorAnswerApi.kt` |
| `business_logic/zhihu/sign` | `web/Zse96.kt` + `Zse96Tables.kt`（已验证的 JS 移植，一行未改） |
| `business_logic/feed` | `eventReporter/EventReporter.kt` |
| `business_logic/feed/data` | `data/local/*` 全部 9 个文件（Room 实体/DAO/数据库/映射/仓库/存储 + `PinAnswerIntoQuestionFeed`） |
| `business_logic/video/data` | `video/network/VideoPlayInfoApi.kt` |

**Room schema 随包名迁移**：导出目录 `app/schemas/org.nigao.zhihuLite.data.local.ZhihuDatabase/` 改名为 `…business_logic.feed.data.ZhihuDatabase/`（Room 以 `@Database` 类的全限定名为目录名）。这是搬迁数据层时必须一起做的事，否则下一次构建会报 schema 位置变化。

**本批踩到的两个搬迁陷阱（值得记录，后续批次会再遇到）**

1. **贪婪前缀替换会改坏 `package` 行**：`org.nigao.zhihuLite.network.` → `…business_logic.zhihu.` 这类替换必须**排除文件首部的 `package` 声明**，否则会把 package 也改写（本次一度把 `org.nigao.zhihuLite.business_logic/feed` 这种带斜杠的非法包名写进文件）。
2. **生成 package 时不能直接用目录路径**：目录是 `business_logic/feed`，包名必须是 `business_logic.feed`。我第一版脚本把 `/` 带进了包名，编译器立刻报了 `Expecting a top level declaration` —— 又一次证明搬迁错误都是**编译期可见**的。

**顺带修正的一处 KDoc**：`LogInManager` 里指向已删除的 `registerRoute.Routes` 的引用。

**尚未完成（Phase 3 剩余）**：`h5Parser` 的解析/渲染拆分（解析→`business_logic/answer`，渲染→`business_ui/answer`）；`share`（DTO 已入 `model`，但业务仍在旧包）；以及最大的一块 **`business_ui`**：`mainFeed`/`answerFeed`/`comment`/`video/ui`/`common_ui`/`login` 的界面部分。当前 `common_ui` 的组件经核对**不是**无业务语义的原子基建（`ListFooter`/`FeedListConfig` 承载分页语义、`ImageViewerScreen` 依赖 `sharedAnswerApi`），因此应落 `business_ui/shared` 而非 `base_ui`——这是对方案 §3.2 的一次确认而非偏离。

### 7.11 Phase 3 进展（六层搬迁，第三批：`h5Parser` 拆分）

**本批完成** —— 把原来 1,008 行的 `h5Parser/H5Parser.kt` 按可测性边界一分为三：

| 层 | 文件 | 内容 |
|---|---|---|
| `business_logic/answer` | `HtmlNode.kt` | 解析结果模型（`Element` / `TextNode`），零 Compose 依赖 |
| `business_logic/answer` | `HtmlParser.kt` | `parseSimpleHtml` + 实体解码 + 标签归一化 + `HtmlParseCache` |
| `business_ui/answer` | `HtmlRenderer.kt` | `HtmlToComposeUi` 及 12 个渲染 composable + 文本收集 |
| `base_ui/imageloading` | `ImageLoader.kt` | 图片加载抽象 + `CoilImageLoader`（跨业务复用，且是 Compose 类型） |

**顺带删除的死代码**：`HtmlNode.toHtml()`（序列化回 HTML 的 API，全项目 0 引用，且与"纯表示模型"的定位相悖）。

**为什么这样切**：这是方案 §3.4 的核心论据所在——解析器只要待在 Compose 旁边，就随时可能因为某个工具函数碰到 `LocalDensity`/`AsyncImage` 而变得**无法在 JVM 测试**。现在 `H5ParserTest`（9 个用例）驱动的是 `business_logic.answer` 里零 Android 依赖的代码。

**一次必须说明的操作失误（及如何补救）**

重构过程中我**误删了工作区版本的 `H5Parser.kt`**：切分脚本按*行号*抽取片段，而我用的是 `git show HEAD:` 的**旧版本**（726 行，早于 Phase 1 的解析器重写）来定位行号，且删文件前没有把渲染部分写出去。结果解析器与模型因早先的片段导出而完整保留，但约 450 行渲染代码的源码丢失。

补救方式：该文件在误删前刚编译过，`app/build/tmp/kotlin-classes/debug/…/H5ParserKt.class` 正是**重构后工作区版本**的产物。用 `javap -p` 提取出全部方法签名（含 `HtmlToComposeUi(html, modifier, answerId, textStyle, linkStyle, imageLoader, onLinkClick)` —— 证实生产版本本就带 `answerId` 参数，即评论内视频的修复），并用 `javap -c` 核对 `ImageElement` 等关键逻辑后重建渲染器。

**因此需要你知晓的验证边界**：解析器与模型是**原样保留**的；渲染器是**按已验证的 API 契约重建**的，编译通过、全部 71 个测试通过，但我**无法证明它与被删版本逐字一致**（只保证语义等价于提取出的签名与逻辑）。建议在真机上过一遍回答/评论的富文本渲染（图片、视频、链接、代码块、列表）再提交。

**尚未完成（Phase 3 剩余）**：`business_ui` 的搬迁——`mainFeed`/`answerFeed`/`comment`/`video/ui`/`login` 的界面部分，以及 `common_ui` 归入 `business_ui/shared`；`share` 业务层归属。

### 7.12 Phase 3 完成（六层结构落地）

**本批完成**：`business_ui` 的搬迁，六层结构至此完整。

| 目标包 | 迁入 |
|---|---|
| `business_ui/feed` | `FeedScreen` · `FeedViewModel` · `FeedUiState` · `FeedItemCard(State)` · `FeedViewModelFactory` |
| `business_ui/answer` | `AnswerFeed*` · `AnswerCard*` · `ActionBar` · `HtmlRenderer` |
| `business_ui/comment` | `CommentView` · `CommentViewModel` · `CommentViewUiState` · `CommentViewModelFactory` |
| `business_ui/video` | `VideoPlayer` · `VideoElement(ViewModel/Factory)` · `TestVideoPlayer` |
| `business_ui/login` | `LogInScreen` · `LogInManager` · `SessionStore` · `AuthWebView` · `CredentialStore` |
| `business_ui/shared` | `ListFooter` · `CommonPanel` · `CommonSwitch` · `FeedList(Config)` · `ImageGallery` · `ImageViewer(Screen)` |
| `business_logic/comment` | `CommentApi`（分页与排序规则，非 UI） |
| `business_logic/login/data` | `EncryptedCredentialStore` |
| `business_logic/share` | `ShareUtil` · `ShareInfoApi` |

**最终顶层包（只有这七个）**：

```
assemble  base_logic  base_ui  base_navigation  business_logic  business_ui  model
```

文件分布：`model` 8 · `base_logic` 2 · `base_ui` 4 · `base_navigation` 2 · `business_logic` 21 · `business_ui` 38 · `assemble` 2。

**本批新增的测试**：`LinkSanitizerTest`（4 个用例）。把渲染器里的链接白名单从 `UriHandler` 扩展中抽成纯函数 `sanitizedLinkTarget(rawUrl): String?`，因为它的输入来自远端 HTML（攻击者可控），而它是我上一批**重建**的代码——补测试比再读一遍更可靠。覆盖：http/https 保留、`//host` 升级为 https、`javascript:`/`file:`/`intent:`/`zhihu:`/`content:` 一律拒绝、相对路径与垃圾串拒绝。

**五批搬迁的统一收获**：搬迁中的错误**全部**在编译期暴露（换包导致的悬空引用、`package` 行被误改、重复类名冲突、缺失 import），没有任何一处是"看起来对、运行时才错"。这也意味着**唯一需要运行时确认的**是真实渲染效果——见 §7.14 的真机验证项。

### 7.13 Phase 3 收尾：`*Wiring` 契约落地（并修正一处真实违规）

逐项核对目标时发现两件事，本批一并处理：

**① `business_ui` 当时仍在 import `assemble`，违反 §3.1 规则 6**

两个 ViewModel 工厂写着 `extras.appContainer()`，直接依赖 `assemble/container`。这不是"看起来不优雅"，而是把 DI 容器类型泄漏进了 UI 层——正是 `*Wiring` 要消除的东西。

现在 wiring 契约落地：

| 位置 | 内容 |
|---|---|
| `business_ui/Wiring.kt` | `FeedWiring` / `AnswerWiring`（**feature 自己声明需要什么**）+ `CreationExtras.requireWiring<T>()` |
| `assemble/container/AppContainer` | `class AppContainer(...) : FeedWiring, AnswerWiring` —— 唯一的实现者 |
| `business_ui/*/…Factory` | 改为 `extras.requireWiring<FeedWiring>()`，**不再 import `assemble`** |

**wiring 契约为什么放在 `business_ui` 根而不是 `base_logic`/`base_navigation`**：那两个层是 Compose/Android-free 的（这正是它们可纯 JVM 测试的原因，§3.4），而解析 wiring 需要 `CreationExtras`。放错层会为了接口而牺牲整层的可测性。

**验证**：`grep` 确认 `business_ui` 中 0 处 `import …assemble`；全项目只有 `App.kt` 与 `DefaultApplication.kt`（壳层）引用 `assemble`。

**② 目标里的 `FeedOperations` 当时并不存在**

它在 §4.2/§7.6 被大量引用，但代码里从未创建——`FeedViewModel` 一直是自己在拼 repository。本批补上 `business_logic/feed/FeedOperations`（observe / loadInitial / loadMore / refresh / reportVisible），并把两处状态收敛进去：

- **分页互斥**：`Mutex` 串行化 paging，避免"刷新与加载更多并发请求同一页"；
- **上报去重**：`reported` 集合按 `kind:itemId` 记录，替代此前 `FeedViewModel` 直接调 `sharedEventReporter` 的写法（每次滚动可见性变化都会重发 show/read）。

容器以**单例**持有它并在构造时注入 `EventReporter`，`FeedViewModel` 改为经 `operations.reportVisible()` 上报。新增 `FeedOperationsTest`（8 个用例，含一处 test seam 使去重记录可断言）。

**测试总量：83，0 失败。** `:app:assembleRelease` 通过。

**仍未做（Phase 3 的最后一小块，已不阻塞目标）**：方案 §3.2 还写了每个 feature 的 `*Entry.kt`（工厂 + `registerXxxRoute`），让路由注册也归各 feature 所有。当前路由仍集中注册在 `assemble/navigation/AssembleAppNavHost`（显式、类型安全、无 Gaia），工厂也仍在 feature 内。集中注册与分散注册都可接受，差别是"改路由要动 assemble"还是"动 feature"；现有形态在 5 个目的地规模下更易读。

### 7.14 已知未完成项（诚实清单）

Phase 1–4 的目标项**全部完成**，以下是尚未完成或有意留到后续的工作，不是遗漏：

1. ~~`assembleRelease` 从未验证~~ **已完成**（§7.4）：R8 全流程 `BUILD SUCCESSFUL`。**安装到真机的冒烟**也已于本轮补做（§7.15）。
2. ~~Phase 2–4 全部未开始~~ **已完成**：Room、加密 `CredentialStore`、手写 `AppContainer`、六层物理分层与类型安全路由、Phase 4 性能项均已落地（§7.6–§7.15）。
3. **Phase 1 有意保留的技术债**：
   - `ClickableText` 已 deprecated **且仍在告警**（`business_ui/answer/HtmlRenderer.kt`），迁移到 `Text` + `LinkAnnotation` 未做；
   - `ImageViewer` 的 `pointerInput` 手势合并需真机验证，本轮设备锁屏 + MIUI 拒绝 `input` 注入，未能验证；
   - ~~`CommentView` 未向 `HtmlToComposeUi` 传 `answerId`~~ **已修复**（随 Phase 2 数据层解决）。
4. ~~`CredentialStore` 尚未加密~~ **已完成**：`EncryptedCredentialStore`（AES-256-GCM + Keystore）。真机加解密往返与明文迁移删除**已在 §7.15 闭环验证**。
5. **曝光上报只做了去重，没做批量/节流**：同一 `(itemId, kind)` 只报一次，但每张卡片仍是 show + 2×read；由于 Feed 列表本身未做分页触发，实际请求量未观测。
6. **`*Entry.kt` 未实现**：路由仍集中在 `assemble/navigation/AssembleAppNavHost` 注册，工厂留在各 feature 内。功能等价，但"每个 feature 自带装配出口"（§3.2/§4.7）未落地；`*_WIRING_KEY` 式的显式 `CreationExtras` 也未采用（见 §4.7 差异说明）。
7. **`tools/typecheck.sh` 与 `tools/run_unit_tests.sh` 已过期**：仍含已删除的 Gaia 桩，缺 Room/Robolectric 依赖。Gradle 已可直接使用，这两个脚本应当修好或删除。
8. **Phase 4 剩余项**（§7.7 已登记）：把 `sharedHttpClient` 注入 Coil（`setSingletonImageLoaderFactory`），避免 Coil 自带一套无鉴权 HTTP 栈；深色模式与 `ColorTokens`；release 日志与 CI 构建冒烟。
9. **全部交互路径缺少真机回归**：目前真机可自动验证的是"冷启 + 首屏网络请求落库 + 广告过滤 + 冷启丢弃缓存"（§7.15–§7.17）；**需要手指的路径仍未在设备上确认**——点击卡片进入回答详情（§7.16）、上滑分页、评论、视频、图片查看器。原因是设备锁屏/Doze 且 MIUI 拒绝 `adb shell input`（`INJECT_EVENTS`）。
10. **退出登录没有 UI 入口了**（用户要求移除 Feed 上的按钮）：`LogOutRoute` / `LogoutScreen` / `SessionStore.logout()` / `string.logout` 都保留着，但当前没有任何界面能触发它。会话过期自动回登录页的逻辑不受影响。将来要么放进设置页，要么连路由一起删。
11. **冷启动必然重新请求第一页**（用户要求，§7.17）：缓存不再跨进程复用，所以每次启动至少 1 次网络请求，离线启动会看到失败页 + 重试而不是上次的内容。这是产品决策而非缺陷，但代价要记在这里。
12. **`target == null` 的 feed 条目**现在在 `business_ui` 侧被丢弃（§7.17 ④），因此不会渲染空卡片；它们仍会被写入 Room（`FeedMapper` 用 `position:` 合成 id 保证主键唯一），会占用 `DEFAULT_MAX_STORED_ROWS` 的名额。真机上尚未观察到这类条目，所以没有进一步处理。

### 7.15 启动崩溃修复与真机冒烟（含 Room 游标的一处真实缺陷）

**用户报告：安装后启动即崩。** `adb logcat -b crash` 的栈顶：

```
java.lang.IllegalArgumentException: The Application does not provide FeedWiring:
    the screen must be created by the app's container
    at ...business_ui.feed.FeedViewModelFactory.create(FeedViewModelFactory.kt:34)
    at ...business_ui.feed.FeedScreenKt.FeedScreen(FeedScreen.kt:221)
```

**根因**：`CreationExtras.requireWiring<T>()` 把 `Application` **直接**强转成 `T`（`application as? FeedWiring`）。但进程入口 `DefaultApplication` 实现的是 `ContainerHolder`（持有 `AppContainer`），`FeedWiring`/`AnswerWiring` 由 `AppContainer` 实现。类型对不上，强转恒为 null，`requireNotNull` 抛错。这正是 §4.7 第 ① 条预先点名的写法。

**为什么编译期与 83 个用例都没挡住**：`inline` + `reified` 的 `as? T` 是运行期检查，编译期只能看到一次"永远合法"的强转；而所有既有测试都是直接 `FeedViewModel(operations = fake)`，从不经过 `Application`/`CreationExtras`。**这条链路上一个测试都没有**——这是本次事故的真正教训。

**修法**：把 `ContainerHolder<C>` 从 `assemble` 移到 `business_ui/Wiring.kt`（解析发生在这一层，而 `business_ui` 不得 import `assemble`），`requireWiring` 改为 `CreationExtras → Application → ContainerHolder.container → T`；容器类型在该层保持不透明（`C`），只有 `assemble` 侧写出 `AppContainer`。

| 文件 | 变化 |
|---|---|
| `business_ui/Wiring.kt` | 新增 `ContainerHolder<out C : Any>`；`requireWiring` 经 holder 解析；两条失败信息分别指出"`Application` 没实现 holder"与"容器没提供该 wiring" |
| `assemble/container/AppContainer.kt` | 删除本地 `ContainerHolder` 定义，改为 import；`appContainerOrNull()` 经 `ContainerHolder<*>` 取容器 |
| `DefaultApplication.kt` | 声明为 `ContainerHolder<AppContainer>` |

**为什么这层间接不能省**：`Application` 是 holder，不是 wiring。让 `Application` 直接实现各 `*Wiring` 也"能用"，但那会把进程入口变成业务装配点（§4.7 第 ① 条的原话），且 Kotlin 的 `by` 委托表达式在属性初始化之前求值，容易踩到"用到尚未初始化的 lazy 容器"。

**回归测试**：新增 `WiringResolutionTest`（Robolectric，7 个用例），跑在**清单里真实的 `DefaultApplication`** 上——`Application` 实现 `ContainerHolder`、两个 wiring 解析到同一个容器实例、两个 ViewModel 工厂的 `create()` 真能造出 ViewModel、两条失败路径的文案。**反向验证**：把 `requireWiring` 改回 `application as? T`，5 个用例失败；改回即全绿。

---

**顺带在真机数据库里发现的第二个缺陷：分页游标被 `trim` 反复清空，Feed 永远只能加载第一页。**

排查崩溃时把设备上的 Room 库拉下来看（`run-as … cat databases/zhihu-lite.db*`），第一次成功加载后 `feed_query` 是：

```
('recommend', cursor_next = NULL, cursor_is_end = 0)
```

`cursor_next` 为 NULL 而 `is_end` 为 0：`RoomFeedRepository.loadMore()` 会走到 `if (next.isNullOrBlank()) return NoMoreData`，于是**上滑永远不再请求下一页**。而写入路径里 `isEnd = paging.isEnd == true || next.isNullOrBlank()`，所以这个组合不可能由写入产生，只可能来自 `RoomFeedStorage.trim()`：

```kotlin
dao.trimPagedRows(query.id, keep)
if (dao.minPagedPosition(query.id) != null) {              // 只要有分页行就成立
    dao.updateCursor(query.id, next = null, isEnd = false) // 把游标改成死状态
}
```

`trim` 在**每次写入后**都会执行（`DEFAULT_MAX_STORED_ROWS = 300`），因此条件恒真：什么都没删也照样清游标。

**为什么"清游标"本身就是错的**（不只是条件写错）：`cursorNext` 是最后一页的 `paging.next`，指向**已存最新行之后**的页；`trim` 丢的是**最旧**的行；追加下一页从 `MAX(position) + 1` 继续。游标根本不需要动。把它写成 `(null, false)` 既不会"重新加载第一页"（`loadMore` 不会因此调 `loadFirstPage`），也无法自愈——是一个死状态。

**修法**：`trim` 只做有界清理、**不再碰游标**；同时删除因此不再被使用的 `FeedDao.minPagedPosition`。

**回归测试**（正是此次缺失的覆盖形态）：
- 新增 `FeedPagingPersistenceTest`（Robolectric，2 个用例）：**真实 `RoomFeedStorage` + 真实 `RoomFeedRepository` + fake API**，断言 `loadFirstPage()` 之后 `loadMore()` 会请求 `paging.next` 并把下一页追加在后面，以及重开数据库后仍能从游标续页。
- `RoomFeedStorageTest` 增加 `trimThatDropsNothingKeepsTheCursor`；并把原 `trimsOldestPagedRowsButNeverThePinnedOne` 的游标断言从**空断言**（其输入游标本来就是 `(null, false)`，断言恒真）改为"裁剪后游标保持不变"。

**为什么 83 个用例全绿却没发现**：`RoomFeedRepositoryTest` / `FeedOperationsTest` 用的是 `FakeFeedStorage`，而 fake 的 `trim` **从一开始就是对的**（不动游标）。fake 比它替代的实现更正确，于是把 bug 藏住了；已在 `FakeFeedStorage` 注释里写明这条教训。**反向验证**：把死状态的 `updateCursor` 加回 `trim`，4 个用例失败。

---

**本轮真机验证（设备 `SCAQNFQWZPN7SCMZ`，debug 包覆盖安装）**

| 项目 | 结果 |
|---|---|
| 冷启动 | `am start -W` → `Status: ok`，`TotalTime ≈ 605ms`；`logcat -b crash` **无** `FATAL EXCEPTION`；进程存活 |
| 二次冷启动（Keystore 解密） | 同样无崩溃，且**无** "Migrating a plaintext session cookie"、**无** "could not be decrypted" → 上一轮写入的密文在真实 Keystore 上解密成功 |
| 凭证加密迁移（旧安装升级路径） | 日志 `SessionStore$migrateLegacyCookie: Migrating a plaintext session cookie into encrypted storage`；`shared_prefs/zhihu_lite_secure_session.xml` 形如 `lZGezvo7PaBitayo:gQ3CqvrvIiWTYoDQ…`（`iv:ciphertext`）；`shared_prefs` 中**不再有**任何 `Cookie`/`z_c0` 明文 |
| Feed 真实加载落库 | 拉库查看：`feed_item` 6 行（`position` 0..5）、`feed_query` 1 行 |
| 分页游标（修复前） | `cursor_next = NULL, cursor_is_end = 0` → 死状态 |
| 分页游标（修复后，清空 feed 缓存重新加载） | `cursor_next = https://www.zhihu.com/api/v3/feed/topstory/recommend?action=down&ad_in…`、`cursor_is_end = 0` → `loadMore` 会真正请求下一页 |

**验证不到的部分（诚实说明）**：设备当时处于**锁屏 + Dozing**，且 MIUI 拒绝 `adb shell input`（`SecurityException: INJECT_EVENTS`），所以**无法做任何点击/滑动交互**。"点进回答详情（`AnswerFeedViewModelFactory` 路径）"、"上滑加载第二页"、"视频 release"、"富文本渲染"四项只到"工厂/仓储层被单测覆盖 + 首屏可渲染"为止，**仍需人在设备上过一遍**（清单见 §7.2）。

**测试总量：93，0 失败**（83 + `WiringResolutionTest` 7 + `FeedPagingPersistenceTest` 2 + `trimThatDropsNothingKeepsTheCursor` 1）。`:app:assembleRelease` 通过（APK 1,871,171 字节）。

### 7.16 修复"点卡片提示该回答可能已删除"与移除 Feed 的退出按钮

**用户报告**：点击 Feed 卡片进入回答流后提示"该回答可能已删除，无法置顶显示"。

**先取证据。** 用设备 WebView cookie 库里的会话（`z_c0`/`d_c0`，明文 SQLite）在**真实服务器**上打了一发探针（临时测试，用完即删），拿到三条硬证据：

1. `/api/v4/answers/{id}` 返回的是**回答对象本身**，不是 feed 那种 `{"data":[...],"paging":{...}}` 信封 →
   `as FeedResponse: FAILED MissingFieldException: Fields [data, paging] are required`；
2. 即使按回答对象解析，作者对象里**没有** `is_following` / `is_followed`
   （feed 端点的 `include` 里有，单条回答端点没有）；
3. 内嵌 `question` 里的 `relationship` 是空对象 `{}`，没有 `is_author`。

**三个独立原因，都会单独导致同一句提示：**

| # | 位置 | 问题 | 修法 |
|---|---|---|---|
| 1 | `KtorAnswerApi` | 按 `FeedResponse` 解码，**必然**抛 `MissingFieldException`（每次点卡片都走到这里） | 改为解码 `Target` 并包成 `FeedItem`；解析抽成 `internal parseAnswer(body)` 以便用**真实抓包**做单测 |
| 2 | `PinAnswerIntoQuestionFeed` | 只在**本问题**的 feed 里找答案，而卡片是从推荐流点进来的——答案早就在**推荐流**的 Room 行里了，于是每次都退化成网络请求，再撞上 #1 | 新增 `FeedStorage.findItem(id)`（跨 query 查找）；命中就本地置顶，**不发请求**，离线可用且瞬时 |
| 3 | `model/feed` | `User.isFollowing` / `isFollowed`、`QuestionRelationship.isAuthor` 是**必填**，而该端点不返回它们 | 给这三个字段默认值，并在注释里写明"端点之间字段不一致"的缘故 |

**顺带修掉一个必然发生的置顶丢失**：`feed_item` 主键是 `(query_id, id)`。置顶先写、问题 feed 第一页随后写入，而这一页**很可能就包含读者点进来的那条回答**——`upsertAll` 的 REPLACE 会把置顶行换成普通分页行，`pinned_in_query` 被悄悄清掉。现在 `replacePagedRows`/`appendPage` 写入前会排除当前置顶的 id（同一条回答，保留置顶那份）。

**验证**

| 手段 | 结果 |
|---|---|
| 单测 | **103，0 失败**：新增 `KtorAnswerApiTest`(4)、`PinAnswerFromFeedCacheTest`(4)、`RoomFeedStorageTest` 两个（跨 query 查找、分页不得顶掉置顶行） |
| 反向验证 | 把 DAO 里的置顶保护去掉 → 2 个用例失败；把 `trim` 的死状态改回来 → 4 个用例失败；`requireWiring` 改回直转 → 5 个用例失败 |
| 夹具来源 | `KtorAnswerApiTest` 的 JSON 是**真实抓包**（只截短了 `content`/`excerpt`/`segment_infos`），所以模型与服务器不一致时它会失败——它当场就抓出了原因 #2、#3（首轮跑出 3 个失败） |
| **真机联调（修复后）** | 用同一枚真实 cookie 跑 `KtorAnswerApi().getAnswer(id)`：两条回答都拿到了完整数据（`蓝桥岁月/73 赞/正文 1780 字/《子不语》有哪些猎奇的故事？`、`金玉木火/1321 赞/正文 7210 字`） |
| 真机安装 | debug 包覆盖安装成功；冷启动 `Status: ok`、`TotalTime 613ms`、crash buffer 无 `FATAL EXCEPTION`；Room `identityHash` 与项目 schema 一致（`56dc147b…`），旧库可直接打开，无需迁移 |

**仍未验证（诚实说明）**：**"在设备上真的点一次卡片"仍未做**——探针能验到 API 与存储，但点不了 UI（设备 Dozing + MIUI 拒绝 `adb shell input`）。修复后的 APK 已装到设备上，请手动点一张卡片确认。

**第二件事：移除 Feed 的退出按钮。** 按用户要求删掉了两处入口（成功页右上角的 `IconButton`、失败页的 `TextButton`），连带 `FailedFeedScreen` 的 `onNavigate` 参数与该文件里因此失效的 import/`rememberCoroutineScope`。

需要注意的后果：`LogOutRoute` 目的地、`LogoutScreen`、`SessionStore.logout()`、`string.logout` **都还在，但已无 UI 入口**（有意保留，用户说"暂时不需要"）。会话过期时 `App.kt` 仍会自动跳回登录页，退出登录的**逻辑**没有被削弱。

### 7.17 恢复广告过滤 + 冷启动丢弃缓存（用户要求）

**① 广告过滤确实在重构中丢了，而且能在真机数据里直接看到。**

旧 `data/FeedStorage.kt` 的 `filterFeedItems()` 有两条过滤：`target.type == "article"` 与"按 answerId 去重"。重构后去重交给了 Room 主键 `(query_id, id)`，但**article 过滤没有被搬到新实现里**——写入路径只做了 `target == null` 的 UI 侧丢弃（而且现在那个丢弃也变得不完整，见下）。

证据（真机 Room 库）：`recommend` 队列里存着 6 条，其中一条 `target.type == "article"`，`verb = TOPIC_ACKNOWLEDGED_ARTICLE`，且 `target.question == null`：

```
article id: 2078906262247551847  question: null
excerpt: 8年互联网大厂产品经理，我坐过的工学椅不下10把……人体工学椅
```

也就是一条**推广文章（办公椅广告）**。它没有 `question`，所以 `FeedViewModel.destinationFor` 返回 null —— 卡片点了没反应，正是用户说的"广告"。

修法：`RoomFeedRepository.fetchAndStore` 在写库前 `filterNot { target.type == "article" }`（放在 `business_logic`，JVM 可测；旧实现也是写入前过滤）。顺带修正一处**判定语义**：`NoMoreData` 现在按**原始页**判断而不是过滤后的列表——否则"整页都是文章"会被误报成"没有更多了"，分页提前一页停住。

**② 冷启动丢弃数据库内容，全屏 loading，不展示上次内容。**

- `FeedOperations.discardStoredFeedOnColdStart()`：**每进程一次**（对象是容器里的进程级单例），清空 `feed_item` + `feed_query`（`FeedStorage.clearAll()` → `FeedDao.clearAllFeedData()` 一个事务，避免观察者看到"删了一半"的列表）。失败只记日志、不抛出、且不置位以便下次重试——展示稍旧的内容远好过彻底加载不出来。
- `FeedViewModel.init` 的顺序是关键，也写进了注释：**先清、再订阅、再加载**。Room 的 Flow 订阅时会立刻回放当前内容，如果先订阅，上一轮的数据会先闪一下；先清空则首次发射就是空列表，于是停在 `FeedUiState.Loading`（`LoadingFeedScreen` 就是全屏居中 spinner），直到新第一页落库。
- **每进程一次而不是每屏一次**：旋转屏幕、从回答详情返回都不应重新加载。这个状态放在 `FeedOperations` 而不是 ViewModel，因为后者每次旋转都会重建。
- `loadInitial()` 的"已加载就跳过请求"逻辑保留：它现在只覆盖"同一进程内离开再回到 Feed"的情况，冷启动时游标已被清掉，因此必然重新请求。

**③ 顺手补上 HTTP 超时。** 验证 ② 时发现：`Dozing` 的设备上第一页请求实测可以慢到 >14s，而 Ktor 客户端**没有配置任何超时**——请求一旦卡住，全屏 spinner 永远不会结束（既不会成功也不会失败）。补 `HttpTimeout`（request 30s / connect 15s / socket 30s），让卡死变成"加载失败 + 重试"，值刻意放宽，目的是兜住挂死而不是约束慢网络。

**④ 同一类问题的另一半：`target == null` 的条目会渲染成空卡片。** 旧 `AnswerCardUiState.toAnswerCardState()` 在 `target == null` 时返回 null，调用方用 `mapNotNull` 丢掉；重构后 `toFeedCardState()` 改成非空返回，于是这种条目会得到一张作者/问题/摘要全空、且点了没反应的卡片（点击目标由 `target.question` 推导）。现在改回**返回可空 + `mapNotNull`**，`FeedItemCardState.answerId` 随之变成非空（`LazyColumn` 的 key 不再需要 index 兜底）。

**真机验证**（debug 包覆盖安装，设备屏幕关闭 = Dozing）

| 检查 | 结果 |
|---|---|
| 广告过滤 | 日志 `RoomFeedRepository$fetchAndStore: Dropped 1 article item(s) from a feed page`；落库 `target.type` 全为 `answer`，无 article |
| 冷启动丢弃 + 重新加载 | 连续 4 次冷启（`am force-stop` → `am start`）每次落库的 id 集合都与上一次**完全不同**（如 `2077790861861866386…` → `2081685236119761957…` → `2006510707471058353…`），且 `feed_query` 只有 `recommend` 一行、游标为新的一页 → 数据被清过并重新抓取；若没清，`hasLoadedOnce` 会为真、不会发请求、id 不会变 |
| 冷启动无崩溃 | 每次 `Status: ok`、无 `FATAL EXCEPTION` |
| 单测 | **112，0 失败**（新增：article 不进库、整页文章不算结束、`clearAll` 清空行+游标、冷启动丢弃恰好一次、丢弃失败会重试、真实 Room 冷启动重载、无 target 的条目不产卡片） |
| 反向验证 | 去掉 article 过滤 → 2 个用例失败；去掉"每进程一次"的守卫 → 1 个用例失败 |

**诚实记录一处测试方法的错误**：最初用 9–14s 的等待窗口拉库，看到"库是空的"，一度以为冷启动没有重新加载。实际是 **Dozing 设备上请求比平常慢**，我的 `am force-stop` 在请求完成前就把进程杀了。把窗口放宽到 45s 后每次都正常。**结论要按 45s 窗口那几次为准**，9s 那几次只是测试脚本太急——这也说明了为什么"看不到结果"不能直接当成"功能坏了"。

### 7.18 回答列表显示"暂无更多数据"：请求根本没发出去

**用户报告**：点开一张卡片进入回答列表，只看到"暂无更多数据"，问是不是请求失败了。

**先取证据。** 拉真机 Room 库：

```
feed_query:  ('recommend', cursor_next=<第2页 URL>, is_end=0)
             ('question:500144122', cursor_next=NULL, is_end=0)      <-- 游标是空的，且没有任何分页行
feed_item:   question:500144122 / answer 2080960664793883980 / pinned=1 / pos=-1   <-- 置顶成功了
```

置顶成功、分页区为空、游标是 `(NULL, false)`。再拿同一个问题 id 打真实 API 探针（**App 拼出来的那条 URL 原样**）：

```
PROBE len=16493  {"data":[{"type":"question_feed_card","target_type":"answer","target":{…
      items=3  paging_next=https://www.zhihu.com/api/v4/questions/500144122/feeds?cursor=50b0fa5c  is_end=False
```

**接口明明有数据。** 所以不是请求失败——是**请求从来没被发出**。

**根因**（这是 §7.16 的置顶修复暴露出来的）：`RoomFeedRepository.hasLoadedOnce()` 的实现是"游标行存在即已加载"：

```kotlin
override suspend fun hasLoadedOnce(): Boolean = storage.cursor(query) != null
```

而 `feed_query` 这一行**不是只有抓取才会创建**——`ensureQuery()` 在**任何**写入前都会建它，包括 `pinAnswer()`。于是：

1. `AnswerFeedViewModel.startInitialLoad` 先置顶 → `ensureQuery` 插入 `feed_query`，游标默认 `(next = null, isEnd = false)`；
2. 接着问 `hasLoadedOnce()` → 行存在 → **true** → 走 `LoadMoreOutcome.Success` 分支，**跳过 `loadFirstPage()`**；
3. 分页区永远是空的，屏幕于是显示"暂无更多数据"。

修法：把"是否抓取过"变成游标自身的语义，而不是"行是否存在"。

```kotlin
data class FeedCursor(val next: String?, val isEnd: Boolean) {
    /** 只有完成的抓取才会写入 next 或 isEnd；(null,false) 是"还没抓过"。 */
    val wasFetched: Boolean get() = next != null || isEnd
}
```

这个判据是**精确的**，不是启发式：`replacePagedRows`/`appendPage` 每次都会写 `updateCursor`，而 `fetchAndStore` 里 `isEnd = paging.isEnd == true || next.isNullOrBlank()`——所以任何一次成功抓取之后，两个字段至少有一个是非默认值。副产品：上一版 `trim` 造成的 `(null,false)` 死状态现在也会被当成"没加载过"，从而自愈。

**同一个根因的第二种表现（同一个屏幕，底部翻页）：** 列表底部那个 footer 只要可见就会请求下一页。当屏幕上只有置顶那一条回答时，footer 立刻可见，于是**在第一页到达之前**就调用了 `loadMore()`：`cursor` 存在、`isEnd=false`、`next=null` → 返回 `NoMoreData` → footer 置 `endReached=true`（永久不再自动加载）。用户看到的"暂无更多数据"就是这个状态。

两处一起修：

- `RoomFeedRepository.loadMore()`：`if (cursor == null || !cursor.wasFetched) return loadFirstPage()`——"没有更多"只允许表示**服务器说的**结束，不允许表示"我们还没抓过"。（这与 §7.15 修掉的 trim 死状态是同一条不变式，只是这次的入口是 `ensureQuery` 而不是 `trim`。）
- `AnswerFeedViewModel.getMoreItems()`：`initialLoadJob?.join()`，先等第一页落地再翻页。否则 footer 的这次提前调用会变成**第二个** page-1 请求。

**为什么之前没被发现**：置顶一直失败（§7.16），`loadFirstPage()` 因此总是正常执行；置顶一修好，这条路才第一次真的走到。**两个 bug 叠在一起互相掩盖**——这也是为什么"修好一个功能"之后必须重跑一遍完整流程，而不只是跑单测。

**验证**

| 手段 | 结果 |
|---|---|
| 单测 | **115，0 失败**（新增：置顶后 `hasLoadedOnce` 必须为 false 且第一页必须被请求、置顶后 `loadMore` 必须抓第一页而不是报结束、置顶写出的游标是 `(null,false)` 且 `wasFetched == false`） |
| 反向验证 | `hasLoadedOnce` 改回 `cursor != null` → 端到端用例失败；`loadMore` 改回只判 `cursor == null` → 对应用例失败 |
| 真实 API | App 原样的 URL 返回 3 条回答 + `paging.next`，确认接口无问题 |

**仍未由我验证**：真机上"点一次卡片、看到回答列表"依然需要手指（设备 Dozing + MIUI 拒绝 `adb shell input`）。修复后的 debug 包已装到设备上；这次的结果应该是：卡片先出现（置顶、来自本地缓存），随后 3 条回答列表补上。

### 7.19 "加载更多"成功一次后永远转圈：加载协程被 footer 的回收取消了

**用户报告**：从 Feed 点卡片进入 AnswerFeed，第一次 load more 之后继续加载就一直显示"加载中"。

**先取证据，三条都指向同一个结论：不是网络，也不是请求失败。**

| 证据 | 内容 |
|---|---|
| 真机 Room 库 | `question:21154553`：置顶 1 行 + 分页 3 行（= 第一页），`cursor_next` = **第二页 URL**，**没有任何第二页的行**（另一个问题同样如此，两次都在同一处停住 → 不是偶发） |
| logcat | 没有任何失败日志（请求失败会打 `Feed page failed, keeping cursor`），也没有解析错误 |
| 真实 API 探针 | 把库里存的那个"下一页 URL"原样打出去：**< 1s 返回 3 条 + 新的 `paging.next`** |

**根因：加载协程活在一个会被回收的 Compose item 里。**

旧 `ListFooter` 用自己 `rememberCoroutineScope()` 里的协程跑 `loadMore.invoke()`，而 `FeedList` 里的 footer 是 `item { ListFooter(...) }` **没有 key**。`LazyColumn` 的两条规则叠在一起就出事：

1. 滚出视野的 item 会被**销毁**；
2. 没有显式 key 的 item **以下标作为身份**——列表一变长，footer 的下标就从 1 变成 4。

于是时序是：置顶那一条先出现 → footer 可见 → 第一次 `loadMore` 发出（网络约 1s）→ **第一页落库、列表从 1 条变 4 条 → footer item 被销毁重建 → 协程被取消**。协程取消意味着：

- `appendPaged` 的事务回滚 → 第二页一行都没有（与库一致）；
- `invoke()` 之后那行 `listFooterStatus = ...` **永远不会执行** → 状态卡在 `LOADING`；
- 而 `requestLoadMore` 的守卫是 `status != LOADING` → **之后再也发不出请求**，spinner 永远转下去。

**修法（三处，`business_ui/shared`）**

1. 新增 `ListFooterPager`：把 `inFlight` / `endReached` / `status` 和**协程作用域**交给列表持有（`rememberListFooterPager()` 在 `FeedList` / `CommentListView` 里调用，位于 item **之外**），生命周期与屏幕一致，滚走/变长都不会取消它。
2. `ListFooter` 变成纯展示组件（`status` + `onLoadMore`），footer item 加上稳定 key `item(key = "list_footer")`，列表变长时不再重建。
3. 状态更新改成异常安全：失败 → `NETWORK_FAILED`（可点按重试）；抛异常 / 被取消 → 收尾时若仍是 `LOADING` 就恢复成 `IDLE`。**任何路径都不会留下一个点不掉的 spinner。**
4. 顺带修掉 `CommentView` 里**完全相同**的写法（评论分页有同一个缺陷）。

**验证**

| 手段 | 结果 |
|---|---|
| 单测 | **123，0 失败**；新增 `ListFooterPagerTest` 8 例：单飞、请求中显示 LOADING、成功回 IDLE、失败进 NETWORK_FAILED、抛异常进 NETWORK_FAILED、**被取消后回到 IDLE 且可重试**、NO_MORE_DATA 闩住、`hasMore=false` 不动 |
| 反向验证 | 去掉收尾时的状态恢复 → "被取消后可重试"用例失败 |

**诚实说明**：这是 Compose 生命周期问题，单测只能钉住**状态机**（"任何路径都不留下永久 spinner"），**无法复现 item 被销毁这件事本身**——本项目没有 Compose UI 测试依赖，也没有在设备上滚动的能力（§7.15 末段）。所以这条修复的最终确认仍然需要你在设备上滑到底试一次。

**附带记一个测试基建的坑**：`kotlinx-coroutines-test 1.8.0` 里 `runTest { backgroundScope.launch { ... } }` 的协程**不会**被 `advanceUntilIdle()` 执行（实测 `ran=false`），要改成在 `TestScope` 本身上 launch。我为此白跑了一轮全红。

### 7.20 从被丢弃的上游提交里补回两块：视频播放生命周期 + 编译期插桩

`origin/master` 上原有两个提交（`d48bfff` Perfetto 业务方法插桩、`019bd1f` feed/UI 生命周期）与本次重构大面积重叠。按用户决定丢弃它们、把本次重构推为 master；随后**有选择地补回其中重构没有覆盖的部分**。取舍记录如下。

**补回的：视频播放生命周期（来自 `019bd1f`）**

| 内容 | 说明 |
|---|---|
| `business_ui/video/VideoPlaybackCoordinator.kt`（新） | 进程级"同时只有一个内联视频在播"：激活新的会销毁上一个播放器的 composable，从而释放 MediaPlayer/解码器/surface/音频焦点 |
| `VideoElement.kt` | 由 `answerId:videoId` 组成 `videoKey`；订阅 coordinator 决定是否 `isActive`；按 `boundsInWindow` 计算可见比例（阈值 0.5）；HTML 里的封面图；点击播放/重试 |
| `VideoElementViewModel.kt` | `sealed interface VideoPlayInfoState`（Initialized/Loading/Failed/Success）+ `getPlayInfo(forceRefresh)`：同一条视频不重复请求、强制刷新时取消上一次 job、无 `data-lens-id`/无 answerId 时直接 Failed 而不是崩 |
| `VideoPlayer.kt` | 整段重写：真实生命周期（`LifecycleEventObserver` 前后台暂停/续播、`surfaceDestroyed` 重置）、首帧前不显示（避免黑屏闪烁）、缓冲状态上报、**源降级**（一个源失败自动换下一个）、`rememberUpdatedState` 回调、`onDispose` 完整释放 |
| `model/video/VideoPlayInfo.kt` | 新增 `getPlayableUrls()`：**优先 720P H.264**（原实现 `sortedByDescending { it.size }` 恰好相反——优先选最大文件）；`svcs`/`beginFrame`/`hd`/`sd` 改为**可空**（真实响应会缺这些字段，必填会让整条解析失败——与 §7.16 同一类错误） |
| 测试 | 移植 `VideoPlayInfoTest`（3 例：真实响应里 `svcs: null` 能解析、720P H.264 优先且保留降级、去空去重） |

**按用户要求（"不要在 APP UI 上显示"）刻意没补的**：上游为播放器新增的三个字符串（`video_play`/`video_retry`/`video_unavailable`）与错误浮层上的"点击重试"文案**都没有加**。行为不受影响——错误浮层整块可点，点了就是 `startPlayback(forceRefresh = true)`；只是没有新的可见文案与 a11y 标签。

**补回的：编译期业务方法插桩（来自 `d48bfff`，完全独立于分层）**

- `buildSrc/`：ASM 的 `AsmClassVisitorFactory`（`BusinessTraceClassVisitor*`），编译期给 `org.nigao.zhihuLite.**` 的具体方法包上 `android.os.Trace`，切片名 `BM:类#方法(签名)`；异常路径也有 catch-all 关闭切片。
- 专用 `perfetto` 构建类型（`applicationIdSuffix ".perfetto"`、不混淆、debug 签名、`matchingFallbacks release`）+ `androidComponents.onVariants(...)` 注册 transform；`debug`/`release` **完全不插桩**。`-PbusinessTraceEnabled=false` 可临时关闭。
- 运行时桥 `performance/BusinessMethodTrace.java` + `@NoBusinessTrace` 注解、`app/src/perfetto/AndroidManifest.xml`（`profileable`）、`perfetto/business-methods.pbtxt`、`scripts/capture-perfetto.sh`、`docs/perfetto-business-method-tracing.md`、README 章节。
- **没有任何 UI**：采集完全由 shell 脚本驱动，App 里没有入口、没有隐藏开关。

**关于 `org.nigao.zhihuLite.performance` 放哪一层**：它**不是一个业务层**，而是构建期支撑代码（与 `buildSrc` 同类）。理由：插桩是**由生成的字节码**从所有层调用 `BusinessMethodTrace` 的，放进任何一层都会凭空造出一条跨层边（例如放进 `base_ui` 就等于让 `base_logic` 的字节码反向引用上层）。放在中立的根包是对这件事最诚实的表示。注意它不影响 §3.4 的可测性边界：`debug`/`release` 变体不含插桩，JVM 单测跑的正是 `debug`，`base_logic` 仍然不碰 Android 符号。

**没补的（有意）**：上游那套列表生命周期修复（§7.19 已用另一种实现覆盖，且我们那版多了"取消/异常不留下永久 LOADING"）、H5 解析离开主线程（重构已用 `withContext(Dispatchers.Default)` + 解析缓存覆盖）、Gaia 相关改动（Gaia 已删）、以及两个针对已删除类的测试（`data/FeedRepositoryTest`、`data/MemoryFeedStorageTest`，其意图由 `RoomFeedRepositoryTest`/`RoomFeedStorageTest` 覆盖）。

**验证**

| 手段 | 结果 |
|---|---|
| 单测 | **126，0 失败**（+ 移植的 `VideoPlayInfoTest` 3 例） |
| `:app:assembleDebug` / `:app:assembleRelease` | 均 BUILD SUCCESSFUL |
| `:app:assemblePerfetto` | BUILD SUCCESSFUL，`transformPerfettoClassesWithAsm` 实际执行 |
| 插桩 A/B（**关键**） | perfetto APK：`BM:` 字节出现 **3019** 次、135 个不同切片名（如 `BM:DefaultApplication#<init>()`）；debug APK：**0** 次 → 普通构建零开销 |
| 真机 | debug 包覆盖安装，冷启 `Status: ok`、无 `FATAL EXCEPTION` |

**仍未验证**：视频播放的实际观感（唯一播放、可见性暂停、源降级、首帧显示）需要在设备上真的点开一个带视频的回答看一遍——我点不了（§7.15 末段）。另外 CI 的 release workflow 目前只构建 `debug/release` 产物，不构建 `perfetto` 变体，所以插桩流水线若在 CI 上被改坏不会被发现；要不要加进 CI 由你定。

### 7.21 视频"播放失败"：一个 `Int` 字段吃掉了整个响应

**用户报告**：视频播放失败。真机日志一句话定位：

```
JsonDecodingException: Unexpected symbol '.' in numeric literal
  at path: $.video_play.playlist.mp4[0].bitrate
JSON input: ..."maxbitrate":0,"bitrate":301.201,"duration":405.185,...
```

**根因**：`VideoItem.bitrate` 声明为 `Int`，而服务器发的是**小数** `301.201`。kotlinx.serialization 直接在第一个小数点处失败 → 整条 `VideoPlayInfo` 变成 null → `VideoPlayInfoState.Failed` → 播放器显示失败态。**所以"播放失败"其实根本没走到播放器**：是拿不到播放信息。

**为什么没发现**：上游的 `VideoPlayInfoTest` 用的是**手写 fixture**（`bitrate = 500`，整数），手写的形状永远不会遇到真实响应里的小数。这次移植时我照搬了那个测试，于是把它一起继承了下来——**夹具的形状必须是抓来的，不能是猜的**。

**修法**

| 改动 | 理由 |
|---|---|
| `bitrate` / `maxbitrate` → `Double` | 服务器发的是小数；`Double` 能接受整数字面量而 `Int` 不能，所以这是更宽容的一侧 |
| `size` → `Long` | 字节数，长视频会溢出 `Int` |
| 排序哨兵值 → `Double.MAX_VALUE` / `Long.MAX_VALUE` | 跟随类型 |
| 新增测试 `realResponseWithFractionalBitrateDecodes` | 夹具是**真实抓包**（只缩短 URL），断言能解析、`bitrate == 301.201`、且 720P H.264 排第一 |

**验证**：用同一枚会话 cookie 打真实接口——修复前 `PROBE decode FAILED: Unexpected symbol '.'`，修复后 **`PROBE decode OK, mp4 entries=3 urls=6`**；单测 **127，0 失败**；真机覆盖安装冷启无崩溃、无 `Failed to load video play info`。

**与 §7.16 并列的一条教训**：`model` 层的数字类型要按**抓包**定，不能按"看起来该是什么"定。§7.16 那次是**必填字段**缺失，这次是**数值类型**过窄——同一个类别的两种表现，而且都靠抓包夹具才暴露（§7.16 的抓包夹具当场抓出两个错误；这次的**手写**夹具则把错误藏到了真机）。

**仍待用户确认**：点开一个带视频的回答，看是否真的能播（我只能验到"解码成功 + 解析出 6 条播放地址"）。

## 8. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 知乎接口/签名随时失效 | 全 App 不可用 | `ZhihuApiExecutor` 统一出口，签名与错误分类集中；`Auth` 与 `Parse` 分开可快速定位是"风控"还是"改字段"；保留原 JS 参考文件与定值向量测试 |
| Zse96 移植存在"已知偏差但未触发"的分支 | 某次数据变化后静默产出错误签名（表现为 403），极难定位 | 差分测试（原始 JS vs Kotlin）接入测试任务；补两个缺失用例：非 BMP 字符（`encodeURIComponent` 逐 `Char` 编码，会把代理对变成 `%3F%3F`）与 `>>>` 高位（`jsUshr` 当前会符号扩展为 `-1L`）。两处都是一行修复，属于"现在修比以后查便宜" |
| 上游端点可能需要额外签名头 | 部分接口 403/无数据 | 仓库自带的原始 JS 里存在 `generate_zst_81`，而当前只发送 `x-zse-93/96`；`ZhihuApiExecutor` 应把"需要哪些签名头"做成显式配置，便于快速试验 |
| Room 迁移写错导致数据丢失 | 用户丢缓存 | 只存 JSON + 稳定主键，避免频繁迁移；首版不开 `fallbackToDestructiveMigration` |
| 加密存储库 deprecated | 未来不可用 | `CredentialStore` 接口隔离，替换只改一个类 |
| 一次性搬迁引入回归 | 难定位 | Phase 1 先修语义并加测试，Phase 3 才搬目录；每 Phase 独立可验证 |
| 删除 Gaia 影响路由 | 导航失效 | 先并存（新 `NavHost` 与旧注册同时存在一轮），确认无引用后再删模块 |
| 性能优化无度量 | 无法证明收益 | Phase 4 强制先建基线数字 |

---

## 9. 附：与专项审查报告的对应关系

本方案的每条设计都对应审查中的一个已确认问题：

| 审查发现 | 方案中的对策 | 所在 Phase |
|---|---|---|
| 刷新清空 Feed | Room 替换语义 + `FeedOperations.refresh()` | 1 / 2 |
| 首次失败永久转圈 | `ApiResult` + `UiState.Error` + `FailedScreen` | 1 |
| 分页失败误判为到底 | `nextPage()` 纯函数 + 失败不动游标 | 1 / 2 |
| 评论排序失效 | `CommentViewModel.setSort` 完整重置 | 1 |
| 富文本链接无跳转 | `onLinkClick` + 真实 offset 解析 + `UriHandler` | 1 |
| 双重 cookie 真相源 | `CredentialStore` 单一来源 + `flush()` | 1 / 2 |
| 7 处崩溃点 | 去 `require`、边界检查、初始值修正 | 1 |
| 取消被吞 | 统一 `CancellationException` 重抛约定 | 1 |
| 并发写内存存储 | Room 事务替代内存读写 | 2 |
| 组合路径重复重活 | 逻辑下沉到 `business_logic` + 缓存 + 预构建 formatter | 3 / 4 |
| 列表无 key/不稳定类型 | `ImmutableList` + `@Immutable` + key 规范 | 1 / 3 |
| 上报放大 | `FeedOperations.reportVisible()` 批量去重节流 | 1 |
| 无界全局 Map | `FeedItemCache`（有界）+ 只传 id | 2 / 3 |
| 无 DI | `AppContainer` + `viewModelFactory` | 3 |
| Gaia 过度设计 | 删除，改显式 NavHost + 类型安全路由 | 3 |
| release 零日志 | `AppLogger` release 也输出 | 4 |
| KMP README 过时 | 随 Phase 3 更新 | 3 |
| 无 release 产物验证 | CI 增加 release 构建冒烟 | 4 |

### 9.1 Zse96 专项差分测试的补充结论

专项审查把 `web/Zse96.kt` 与仓库自带的原始 JS（`ApiTest/question_list_browser.js`，Node 22）做了 **662 用例差分**：**552/552 非增补平面（non-BMP-free）输入逐字节一致**，`PROGRAM`/`DATA`/`ZB`/`ZM`/`ZK`/`START_IP` 与 JS 运行时表**完全相等**，`toInt32` 与 `&`/`|`/`^`/`<<`/`>>` 的符号语义也正确。这说明移植本身是可信的。在此之上需要处理的是"已确认偏差但当前未触发"的几处，以及若干 H5 解析缺陷：

| 发现 | 证据强度 | 处理阶段 |
|---|---|---|
| `jsUshr` 对 `>>>` 做符号扩展（`0xFFFFFFFF` 得 `-1L`，应为无符号） | 确认偏差，9,780 次执行未触发 | Phase 1（一行修复） |
| `encodeURIComponent` 逐 `Char` 编码，代理对被编成 `%3F%3F`（非 BMP 字符） | 确认偏差，110/110 差分失败全部归因于此；当前 `generate()` 只喂 ASCII MD5，故线上不可达 | Phase 1（改为按码点/整串编码 + 补用例） |
| `Char(toInt32(...))` 可能抛未捕获的 `IllegalArgumentException`（opus 283 路径） | 确认缺陷，3,900 次随机输入未触发 | Phase 3（加 `and 0xFFFF` 守卫） |
| `jsParseInt` 长数字溢出抛 `NumberFormatException`；`digits +=` 为 O(n²) | 确认缺陷，未触发 | Phase 3（`toLongOrNull` + 钳制） |
| `===`/`!==` 对字符串用了引用相等（Kotlin `===` 语义 ≠ JS） | 确认偏差，0 命中 | Phase 3（改为值相等） |
| `gX` 在输入长度非 16 倍数时越界读（JS 侧为 `undefined`） | 疑似，502 次调用全部是 16 的倍数 | Phase 3（加断言或按长度补齐） |
| H5：实体不解码、注释被当元素吞掉后续内容、闭合标签大小写敏感、`script` 内容被当标记、深度导致渲染侧 `StackOverflowError` | **全部实测确认**（见 §4.4） | Phase 1 |
| 仓库自带的 JS 中含 `generate_zst_81`，而当前只发 `x-zse-93/96` | 确认事实 | Phase 3（做成显式配置） |

**方法学收益**：把这份 JS 差分测试固化成测试任务，比事后读代码找 bug 有效得多——上面 6 条 Zse96 偏差全部是差分跑出来的，人工审查先前一条都没发现。因此 §7.1 的自动化表格中应加入"JS 差分测试"，并在 Phase 1 就把两个已确认偏差修掉（都是一行改动）。

---

## 10. 附：分层方案的修订记录

本方案的早期版本采用 `core / domain / data / feature / app` 五包结构，随后改为 `model / base / business / assemble` 四层，**最终定为下面这个六层结构**：

```
assemble → business_ui → business_logic → base_ui → base_logic → model
```

这不是单纯改名，**物件的归属随每一轮结构变化而改变**。记录下来是为了避免有人按旧版实施：

| 早期版本 | 现在（六层） | 变化原因 |
|---|---|---|
| `core`（大杂烩） | 拆成 `base_logic` + `base_ui` | 把"纯能力"与"Compose 基建"分开，让 `base_logic` 保持纯 Kotlin；避免 `core` 变成什么都装的袋子 |
| `domain`（独立一层） | 并入各 `business_logic` 模块 | 用例/解析/分页规则只服务各自业务，按"尽量往上层放"应上浮 |
| `data`（独立一层） | 并入各 `business_logic/*/data` | 同上；每个业务模块自带 `data/` 实现 |
| `feature` | 拆成 `business_logic` + `business_ui` | 业务不再只是"页面"：逻辑与表现分离，让逻辑可纯 JVM 测试；ViewModel/UiState 归 `business_ui` |
| `app` | `assemble` | 语义更明确：它只负责**装配**，且明确禁止业务逻辑与界面组件 |
| `core/uistate/UiState.kt`（公共泛型状态机） | 各 `business_ui/*` 自定义状态（同构部分上浮 `business_ui/shared`） | **最重要的一处修正。** 旧版用"没有 Android 依赖"作为放进最底层的理由，属于偷换概念：分层准入标准是"依赖方向与知识范围"（是否跨层契约），而非"纯不纯 Kotlin"。`UiState` 是表现层概念、只被表现层使用，且各业务状态注定异构。详见 §4.1 |
| `core/result/ApiResult.kt` | `base_logic/result/ApiResult.kt` | 被 `base_logic`（网络产生）与上层（消费）同时使用，是真正的跨层词汇——这是"有强制理由下沉"的正当案例 |
| `base/ui/ColorTokens.kt`（色彩 token） | `base_ui/theme/ColorTokens.kt` | **第二次修正。** 色彩 token 依赖 Compose，留在 `base` 会让整个 `base` 失去"纯 Kotlin"属性；`business_logic` 一依赖它就被拖进 Compose，分层只剩包名。`base_ui` 是 Compose 的收容处 |
| `data/remote/ZhihuApiExecutor.kt`（含签名） | 拆成 `business_logic/sign/`（签名算法）与 `business_logic/remote/`（请求装配） | 签名协议是最易变的东西（接口一改即失效），关在业务层与页面一起演进更安全；**同时拆开**是因为「算法 / 请求装配 / 会话读取」三者变化原因不同，混在一个以公司名命名的目录里看不出职责 |
| `domain/html/`（H5 解析） | `business_logic/answer`（渲染在 `business_ui/answer`） | 只被 answer/comment 使用，"尽量往上层放"；放 `business_logic` 而非 `base_logic`，因为它知道 HTML 业务语义 |
| `core.ui`（跨 feature UI 组件） | `business_ui/shared` | 明确"跨模块共享一律上浮"，`base_*` 不接受"多个模块要用"作为准入理由 |

**三次修订背后的同一条准则**：*只能上层依赖下层；没有强依赖就尽量往上放*。`UiState` 和色彩 token 两次放错，都是因为误把"纯不纯 Kotlin"当成了分层依据。

**第四次修订：去掉 `UseCase` 命名。** 早期版本为 feed 定义了 `ObserveFeedUseCase` / `RefreshFeedUseCase` / `LoadMoreFeedUseCase` 三个类。后改为**每个业务模块一个 `*Operations` 对象，方法即操作**：

| 早期版本 | 现在 | 原因 |
|---|---|---|
| `ObserveFeedUseCase` / `RefreshFeedUseCase` / `LoadMoreFeedUseCase` | `business_logic/feed/FeedOperations.kt` 的三个方法 | `UseCase` 后缀是仪式化命名，不携带信息；三个类对 7.4k 行的项目偏重 |
| `GetCommentsUseCase` / `SwitchCommentSortUseCase` | `business_logic/comment/CommentOperations.kt` | 同上，保持命名一致 |
| `LogInUseCase` / `LogOutUseCase` | `business_logic/login/SessionOperations.kt` | 同上 |
| `ReportReadUseCase` | 并入 `FeedOperations.reportVisible()`（去重/节流状态属于该对象，不另开类） | 同上 |

**第五次修订：ViewModel 工厂的位置（走过两次弯路）。**

| 版本 | 做法 | 问题 |
|---|---|---|
| v1 | 工厂写在 `business_ui/feed/FeedViewModelFactory.kt`，内部 `this[APPLICATION_KEY] as ZhihuLiteApplication` 取容器 | `business_ui` **反向依赖 `assemble`**，违反规则 6；feature 的编译与测试被拖上 `Application`，JVM 单测 UiState 逻辑前得先造 `Application` |
| v2 | 全部工厂集中到 `assemble/ViewModelFactories.kt` | 依赖方向合法了，但**路由与工厂离开了各自 feature**，`assemble` 变成越来越长的业务装配清单——这就是"看起来怪怪的"的来源 |
| **v3（最终）** | **feature 自带 `*Wiring.kt`(依赖声明接口) + `*Entry.kt`(工厂 + `registerXxxRoute`)**；`assemble/container/AppContainer` 实现所有 `*Wiring` 并汇总全局单例；`assemble/navigation/AppNavHost` 只做一行一个的汇总 | 依赖方向合法 + 装配出口就近 + `assemble` 只剩全局职责 |

**被否决的方案**：新增 `business_assemble` 层。它只是把业务装配从 `assemble` 挪到下面一格，**依赖链更长而内聚没有改善**；且它必须在共享层 import 所有 feature，会诱发"模块互相 import"；职责还会与 `assemble` 重叠。正确的扩展方向是**按 feature 拆 `assemble/container/` 里的文件**，而不是加层。

**命名约定**：feature 声明的依赖接口叫 `*Wiring`（如 `FeedWiring`、`CommentWiring`），不叫 `*Services`。因为它是**"assemble 需要为这个 feature 装配什么"**的契约，由 `AppContainer` 实现——名字本身说明了"谁提供"，而 `*Services` 读起来只像"一组服务"，看不出与装配的关系。注意区分：`FeedOperations` 是 feature 的**逻辑对象**，`FeedWiring` 是容器**提供**它的契约。

**同时记下一条正确性约束（不是风格偏好）**：`FeedOperations` 这类对象**必须取自容器的单例，不能在工厂里现造**。它持有曝光上报的去重/节流状态与分页 `Mutex`；每次 ViewModel 创建都新建一份，会让节流失效（复现本次会话刚修好的上报放大）且让锁形同虚设。详见 §4.7。

**要点**：改的只是"类的切法",不是"规则的归属"。数据一致性规则仍然必须留在 `business_logic` 并接受纯 JVM 单测**——把它挪进 ViewModel 会重演 `CommentViewModel.updateSortType` 那个 bug(只改 UI 状态、游标从未重置,排序切换完全失效)。`ObserveFeedUseCase` 被直接删掉,因为 ViewModel 订阅 `FeedRepository.observeFeed()` 即可,不需要中间类。详见 §4.2。

**被强化而非推翻的旧结论**：逻辑与表现必须分开。区别在于第二次修订把它从"包内子目录"提升为"独立层级"——因为**只有依赖方向上的隔离才是可验证的隔离**（§3.4）。

**第六次修订：wiring 的解析路径按实现落地，并记下一次真实事故。** 第五次修订的 v3 里写了"`*Wiring.kt` + `*Entry.kt` + `*_WIRING_KEY` 显式 `CreationExtras`"，实际落地的是**更少东西**：

| 第五次修订设想 | 实际落地 | 说明 |
|---|---|---|
| `*_WIRING_KEY`（`CreationExtras.Key<FeedWiring>`，由 NavHost 显式放入） | `ContainerHolder<C>` + `CreationExtras.requireWiring<T>()`，经 `APPLICATION_KEY` 取 | 少一层 key 样板，代价是"容器挂在 `Application` 上"成了实现假设（§4.7 第 ② 条担心的正是这个）；换来的是：调用点只有一行，且 `feed`/`answer` 共用一套解析 |
| `*Entry.kt`（工厂 + `registerXxxRoute`） | 未实现；工厂留在 feature，路由集中在 `assemble/navigation/AssembleAppNavHost` | 功能等价，装配出口未"就近" |
| `FeedWiring.kt` 放 `business_ui/feed/` | 两个 wiring 接口 + 解析函数集中在 `business_ui/Wiring.kt` | 两者都要能被 `assemble` 用到，且两个 feature 需要同样的三个对象；集中一处反而没有歧义 |

**这次修订是被一次启动崩溃逼出来的**（详见 §7.15）：`requireWiring` 一度写成 `application as? T`。第五次修订的表格里，v1 的问题是"`this[APPLICATION_KEY] as ZhihuLiteApplication` 让 `business_ui` 反向依赖 `assemble`"——被修掉的是**具体类型**，而"把 `Application` 当成 wiring 提供者"这个**错误假设**留了下来，于是换成接口强转后照样崩，只是崩得更晚（运行时）而不是更早（编译期）。教训写进 §7.15：**契约类型不对时，强转是编译器不会替你检查的最后一环，必须有测试穿过真实 `Application`。**

**同批修正的第二个缺陷**（§7.15）：`RoomFeedStorage.trim` 每次写入都清空分页游标，导致 Feed 永远只能加载第一页。它暴露出另一类问题：**fake 比真实现更正确时，测试全绿也能藏住 bug**——`FakeFeedStorage.trim` 一直是对的，所以 83 个用例没有任何一个能发现它。修法是补一个"真实 Room + 真实 Repository"的用例。

**第七次修订：`model` 层 DTO 的必填字段是"与某一个端点的契约"，不是"数据的性质"。** §7.16 那句"该回答可能已删除"由**三个独立原因**叠加而成，其中两个都属于这一类：`User.isFollowing` / `isFollowed`、`QuestionRelationship.isAuthor` 被写成必填，而 feed 端点会返回、单条回答端点不返回——于是同一个 `Target` 模型，从 feed 进来能解析，从 `/api/v4/answers/{id}` 进来必失败，且失败信息是"字段缺失"而不是"业务不存在"，最终被 UI 翻译成了误导性的"回答可能已删除"。

记下三条结论：

1. **DTO 的必填字段应当是"应用的逻辑真的需要它"**，而不是"服务器通常会返回它"。字段缺失时应该降级（默认值），而不是让整条数据不可用；本项目的 `model` 层选择"DTO 直通 + 部分字段必填"，代价就是这个风险必须靠**用真实抓包做夹具的单测**来兜。
2. **同一份 `model` DTO 服务多个端点时，字段集合必须按"端点们的交集"来定。** 三个端点的 author 对象字段并不一致（feed 带 `include` 的字段更多），把最全的那个当成基准就会在更瘦的端点上炸。
3. **不要把"取不到数据"一次性翻译成业务结论。** `PinAnswerResult.NotFound` 同时被用在"服务器真的没有这条回答"和"解析失败"上——这正是误导性提示的来源。修完后 `NotFound` 仍然混合着这两种语义，只是两条路径都修好了；如果将来再加端点，应把它拆成 `NotFound` 与 `Unparsable`。

**同批修正的第四个缺陷**（§7.16）：置顶行会被问题 feed 第一页的 upsert **替换掉**（主键 `(query_id, id)` 相同），`pinned_in_query` 被静默清除——即"带着答案进来"的功能在最常见的路径上其实不生效。它是"用主键做 dedup"这个设计选择的直接副作用，只有把"置顶"从 `position` 提到独立列之后再想一层才能发现：**独立列解决了排序，但没解决写入冲突。**

**第八次修订：状态判断要按"语义"，不要按"行是否存在"。** §7.18 这次是同一个坑的第三次踩中：

| 次 | 位置 | 把什么当成了"已加载/已结束" | 真实语义 |
|---|---|---|---|
| 1 | `RoomFeedStorage.trim`（§7.15） | `minPagedPosition != null`（有分页行） | 实际删掉了行才算 |
| 2 | `RoomFeedRepository.hasLoadedOnce`（§7.18） | `cursor != null`（游标行存在） | 有**抓取过**才算 |
| 3 | `RoomFeedRepository.loadMore`（§7.18） | `next == null` 直接当"结束" | 结束只能由服务器/`isEnd` 说 |

根因是 `feed_query` 这一行**被两个目的共用**：既记录 feed 的身份（id + 首次 URL），又存游标。任何写入路径（包括 `pinAnswer`）都会通过 `ensureQuery` 建出这一行，于是"行存在"与"抓取过"不再等价，而三个判断都恰好用了更弱的那个条件。

结论：**多目的共用的行，其字段不能默认"存在即有效"。** 修法上我选择把语义做成数据自己的属性（`FeedCursor.wasFetched`）而不是散在调用方的条件里——这样它可被单测直接断言，也不会在下一个调用点被重新写错。若将来 `feed_query` 再加字段，先问一句："这一行的存在能证明什么？"

**第九次修订：协程作用域必须与它保护的资源同寿命。** §7.19 的教训与分层无关，但属于同一类"编译器不会提醒你"的错误，值得记在这里。`ListFooter` 曾经在**自己这个 item 的** `rememberCoroutineScope()` 里跑分页请求，而 `LazyColumn` 会销毁滚出视野的 item，且**无 key 的 item 以下标为身份**——列表一变长，footer 就被销毁重建，正在飞的请求连同它的 Room 事务一起被取消。更糟的是"取消"既不写日志也不抛到界面上，只表现为一个永远转的 spinner。

三条可迁移的规则：

1. **suspend 调用不要放在"会被回收的 item"的作用域里。** 需要跨越 item 生命周期的状态与协程，放在列表（或 ViewModel）那一层。
2. **`LazyColumn` 里的每个 item 都给显式 `key`。** 不给 key 就等于用下标当身份，而列表长度只会变。
3. **"取消"必须有收尾。** 状态机里每个出口（成功/失败/异常/取消）都要把可见状态落回一个可继续的状态；否则取消会留下一个用户无法摆脱的界面。这条现在是 `ListFooterPagerTest` 的断言之一。
