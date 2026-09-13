# ZhihuLite

ZhihuLite 是一个用 Kotlin 与 Jetpack Compose 编写的轻量知乎客户端：浏览推荐信息流、问题与回答、
评论、图片与视频，并通过 WebView 完成登录。

项目按职责**分层并拆成 8 个 Gradle 模块**，分层规则由模块边界在编译期强制执行，而不是靠约定——
详见[架构与分层](#架构与分层)。

> 本项目并非知乎官方客户端，仅用于 Android、Compose、网络请求、本地缓存、字节码插桩与性能分析等
> 技术实践。上游接口、页面结构与登录流程都可能随知乎服务变化而失效。

## 功能概览

- WebView 登录，Cookie 状态保存
- 首页推荐信息流：下拉刷新、游标分页、失败重试
- 问题与回答列表；从信息流带答案进入时，该回答置顶显示
- 评论列表：按分数 / 时间切换排序；支持楼中楼（展开回复、分页加载、"回复 @谁"）；
  评论里的图片直接以缩略图展示，发布行右侧显示点赞数
- 回答正文 HTML 渲染：段落、图片、视频、链接、引用、代码块等
- 图片查看器：点信息流里的图片封面进入，左右翻页；长按可分享图片或保存到 `Pictures/ZhihuLite`
- 全屏视频播放器：视频封面带播放图标，点它进入播放器（是视频还是图片按正文里有没有视频判断）；
  横版竖版都按自身比例全宽展示、竖直居中；可拖动进度条（带暗色描边，亮画面下也看得清），
  单击画面暂停 / 恢复，长按画面 2 倍速播放并在顶部提示
- 系统分享
- Room 本地缓存：冷启动清空上一进程数据并整屏加载，不展示上次内容
- 仅 `perfetto` 构建：基于 ASM 的业务方法 Trace 自动插桩

## 技术栈

| 类型 | 技术 |
|---|---|
| 语言 / 构建 | Kotlin 2.1.21、Gradle 8.9、AGP 8.7.3、JDK 17 |
| UI | Jetpack Compose、Material 3 |
| 导航 | Navigation Compose，`@Serializable` 类型安全路由 |
| 状态 | AndroidX ViewModel、Kotlin Flow |
| 网络 | Ktor Client 3、OkHttp、kotlinx.serialization |
| 本地存储 | Room 2.7、multiplatform-settings |
| 图片 | Coil 3 |
| 日志 | Napier |
| 代码生成 | KSP（仅用于 Room 编译器） |
| 性能分析 | Perfetto、`android.os.Trace`、ASM 字节码插桩 |
| 测试 | JUnit4、Robolectric、kotlinx-coroutines-test |

## 架构与分层

### 模块划分

| 模块 | 类型 | 职责 | 直接依赖 |
|---|---|---|---|
| `:model` | kotlin-jvm | 接口 DTO 与分页模型（纯数据，无 Android） | — |
| `:base_logic` | kotlin-jvm | 与业务无关的纯逻辑（数字、时间格式化） | — |
| `:base_navigation` | kotlin-jvm | 路由词汇表（`@Serializable` 路由定义） | — |
| `:base_ui` | android-library | 与业务无关的 Compose 基础件（图片加载、修饰符、颜色 / 字符串扩展） | — |
| `:business_logic` | android-library | 业务逻辑与数据层：Ktor 接口、Room 缓存、解析、上报 | `:model` |
| `:business_ui` | android-library | 各功能的 Compose 界面与 ViewModel | `:business_logic`、`:base_ui`、`:base_logic`、`:base_navigation` |
| `:performance` | android-library | 插桩注解 `@NoBusinessTrace` 与运行时桥接 | — |
| `:assemble` | android-application | 应用装配：`MainActivity`、`NavHost`、`AppContainer`；**单元测试都在这里** | `:business_ui`、`:business_logic`、`:performance` |

依赖方向只能向下：

```text
:assemble ──▶ :business_ui ──▶ :business_logic ──▶ :model
     │              │
     │              ├──▶ :base_ui
     │              ├──▶ :base_logic
     │              └──▶ :base_navigation
     └──▶ :performance
```

### 为什么用 Gradle 模块而不是包约定

因为**只有依赖方向上的隔离才是可验证的隔离**：

- 在 `:business_logic` 里 `import org.nigao.zhihuLite.business_ui.*` 会直接编译失败
  （`Unresolved reference`）——那一层不在它的编译类路径上，不需要靠 code review 或 grep 去发现。
- `:model`、`:base_logic`、`:base_navigation` 是纯 `kotlin-jvm` 模块，连 `import android.*`
  都编译不过，所以"逻辑层偷偷依赖 Android 框架"这类问题在类型系统层面就不存在。
- 跨模块无法智能转换 / 无法访问 `internal`，这类约束会以编译错误的形式暴露出来。

代价写在 `docs/REFACTOR_PLAN.md` 里：拆分后每个模块各自拥有 `R` 类
（`android.nonTransitiveRClass=true`），perfetto 插桩的作用域必须从"隐式全部"改成显式配置，
而单元测试只能放在 `:assemble`（跨模块接缝需要完整的应用类路径）。

### 装配方式

功能模块不直接拿依赖，而是声明自己需要什么：

- 每个功能声明一个 `*Wiring` 接口（如 `FeedWiring`、`AnswerWiring`），说明"装配这个功能需要提供
  哪些对象"。
- `:assemble` 的 `AppContainer` 实现这些接口，并通过 `Application` 持有。
- ViewModel 用 `CreationExtras.requireWiring<FeedWiring>()` 取到它，因此 `:business_ui` 不需要
  知道 `:assemble` 的存在。

一个信息流请求的路径：

```text
FeedScreen / FeedViewModel  (:business_ui)
        │  FeedOperations / FeedRepository
        ▼
Room + Ktor                 (:business_logic)
        │  DTO
        ▼
FeedItem / Target ...       (:model)
```

### 导航与页面切换

导航图写在 `:assemble` 的 `AssembleAppNavHost` 里（类型安全的 `@Serializable` 路由来自
`:base_navigation`），不用事件总线也不用代码生成。页面切换是 push / pop 的横向滑动（300 ms）：

- 进入下层页面：新页面从右侧推入，当前页面向左让位；返回时反向滑回。
- 全屏播放器与图片查看器同样如此，因此它们的画面是**随页面一起移走**的，而不是原地淡出。
- 不使用系统"预测性返回"的页面预览（manifest 里 `enableOnBackInvokedCallback=false`）：
  边缘滑动返回时不会实时预览目标页面，松手后才执行。
- 这四个方向（push 进 / push 出 / pop 进 / pop 出）是**同一个决定的两半**：只做一侧会在动画
  期间露出底色。原因与取舍写在 `docs/REFACTOR_PLAN.md` 的 §7.25。

## 项目结构

```text
ZhihuLite/
├── model/                    # 纯数据 DTO 与分页模型（kotlin-jvm）
├── base_logic/               # 与业务无关的纯逻辑（kotlin-jvm）
├── base_navigation/          # 路由定义（kotlin-jvm）
├── base_ui/                  # 与业务无关的 Compose 基础件
├── business_logic/           # 业务逻辑 + 数据层（Room、Ktor）
│   └── schemas/              # Room schema 导出
├── business_ui/              # 各功能的界面与 ViewModel
├── performance/              # @NoBusinessTrace 与 Trace 运行时桥接
├── assemble/                 # 应用装配与全部单元测试
│   ├── src/main/             # MainActivity、NavHost、AppContainer
│   ├── src/perfetto/         # perfetto 变体专用 Manifest
│   └── src/test/             # 148 个单元用例
├── buildSrc/                 # ASM 业务方法 Trace 插桩
├── perfetto/
│   └── business-methods.pbtxt
├── scripts/
│   └── capture-perfetto.sh
├── docs/
│   ├── REFACTOR_PLAN.md
│   └── perfetto-business-method-tracing.md
└── captures/                 # 本地 Trace 输出，不提交
```

## 数据层与缓存

- 信息流按"查询"缓存：`feed_query` 记录查询本身与分页游标，`feed_item` 以 `(query_id, id)`
  为主键存放条目，载荷是接口原始 JSON，避免字段变化就要迁移表结构。
- **冷启动会丢弃上一进程的缓存**：`DefaultApplication.onCreate` 触发一次清理，界面整屏加载，
  因此不会看到上次运行留下的内容。
- **离开问题页会释放该查询**：`clearQuery(query)` 之后做一次压缩（`VACUUM` +
  `wal_checkpoint(TRUNCATE)`），否则被删掉的页面仍以 WAL 高水位的形式占着空间。
- 已知未做：`feed_query` 的行数与存活时间仍无上限，需要 schema v2 与迁移策略。

## 环境要求

- JDK 17
- Android SDK Platform 35、Build Tools 35.0.0
- Android 设备或模拟器（`minSdk` 26）

应用配置：

| 配置 | 值 |
|---|---:|
| `applicationId` | `org.nigao.zhihuLite` |
| `minSdk` | 26 |
| `targetSdk` | 35 |
| `compileSdk` | 35 |
| Java/Kotlin 字节码目标 | 11 |
| Gradle Wrapper | 8.9 |

如果项目根目录没有可用的 `local.properties`，需要指定 Android SDK：

```properties
sdk.dir=/path/to/Android/sdk
```

## 构建项目

以下命令都在项目根目录执行。

### Debug APK

```bash
./gradlew :assemble:assembleDebug
```

APK 输出到：

```text
assemble/build/outputs/apk/debug/assemble-debug.apk
```

直接安装到已连接设备：

```bash
./gradlew :assemble:installDebug
```

### Release APK 和 AAB

```bash
./gradlew :assemble:assembleRelease :assemble:bundleRelease
```

产物：

```text
assemble/build/outputs/apk/release/assemble-release-unsigned.apk
assemble/build/outputs/bundle/release/
```

版本号可以通过 Gradle 属性传入：

```bash
./gradlew :assemble:assembleRelease :assemble:bundleRelease \
  -PversionName=1.2.3 \
  -PversionCode=102003
```

本地没有提供签名信息时，Release 产物不会自动签名。需要签名时设置：

```bash
export RELEASE_KEYSTORE_PATH=/absolute/path/to/release.keystore
export RELEASE_KEYSTORE_PASSWORD=your_store_password
export RELEASE_KEY_ALIAS=your_key_alias
export RELEASE_KEY_PASSWORD=your_key_password
```

### Perfetto APK

`perfetto` 是专门用于性能采集的构建类型：

- 包名为 `org.nigao.zhihuLite.perfetto`，不会覆盖普通安装包。
- 使用 Debug 签名，便于本地安装。
- 关闭代码压缩和资源压缩，保留可读的方法名。
- 开启 `<profileable android:shell="true">`。
- 对本项目所有模块的业务方法执行 ASM Trace 插桩。

构建与安装：

```bash
./gradlew :assemble:assemblePerfetto
./gradlew :assemble:installPerfetto
```

产物位于：

```text
assemble/build/outputs/apk/perfetto/assemble-perfetto.apk
```

如果临时不需要业务方法自动插桩：

```bash
./gradlew :assemble:assemblePerfetto -PbusinessTraceEnabled=false
```

### 运行测试

```bash
./gradlew test
```

只运行单元测试任务（全部用例都在应用模块）：

```bash
./gradlew :assemble:testDebugUnitTest
```

测试都是 JVM 单元测试：Room 相关用例通过 Robolectric 跑真实 SQLite，网络与播放器等外部依赖用
假实现替换，因此不需要设备。

## Perfetto 性能 Trace

项目支持完全脱离 Android Studio 进行性能采集：

1. Gradle 构建并安装 `perfetto` 变体。
2. 脚本通过设备上的 `perfetto` 命令开始采集。
3. 在手机上执行目标业务流程。
4. 回到终端按下回车。
5. 脚本停止采集、等待数据刷盘并拉取 Trace 文件。
6. 使用 Perfetto Web Viewer 打开文件。

### 手动开始和停止

首次使用，构建、安装并启动 App：

```bash
./scripts/capture-perfetto.sh --install --launch
```

App 已经安装时：

```bash
./scripts/capture-perfetto.sh --launch
```

脚本成功启动后会进入等待状态：

```text
Perfetto is recording. Interact with the app now.
Press Enter to stop, flush, and save the trace.
```

在设备上完成操作后，按下回车。默认输出文件类似：

```text
captures/zhihulite-YYYYMMDD-HHMMSS.perfetto-trace
```

如果希望自己手动打开 App，可以不传 `--launch`：

```bash
./scripts/capture-perfetto.sh
```

指定输出文件：

```bash
./scripts/capture-perfetto.sh \
  --launch \
  --output captures/login.perfetto-trace
```

多设备环境：

```bash
./scripts/capture-perfetto.sh \
  --serial DEVICE_SERIAL \
  --launch
```

也可以显式传入 `--manual`，它与默认行为相同：

```bash
./scripts/capture-perfetto.sh --manual --launch
```

手动模式会周期性地将内存缓冲区写入设备文件，避免较长时间采集时覆盖开头的数据。
采集结束后，设备上的临时文件会自动删除。

### 固定时长采集

无人值守场景可以使用 `--duration`：

```bash
./scripts/capture-perfetto.sh \
  --duration 20 \
  --launch \
  --output captures/feed.perfetto-trace
```

`--manual` 和 `--duration` 不能同时使用。

查看所有脚本参数：

```bash
./scripts/capture-perfetto.sh --help
```

### 在 Perfetto Web Viewer 中分析

1. 打开 [Perfetto Web Viewer](https://perfetto.dev/#viewer)。
2. 将生成的 `.perfetto-trace` 文件拖入页面。
3. 找到 `org.nigao.zhihuLite.perfetto` 进程。
4. 展开主线程、RenderThread 和其他相关线程。
5. 搜索 `BM:` 查看业务方法切片。

业务切片示例（取自真实采集）：

```text
BM:assemble.container.AppContainer#discardPreviousSessionFeeds()
BM:assemble.shell.AppKt#App(Modifier,Composer,int,int)
BM:business_logic.feed.EventReporter#<init>(HttpClient)
BM:base_navigation.FullScreenVideoRoute#<clinit>()
```

切片宽度表示该次同步方法调用的耗时。嵌套调用会显示为嵌套切片，可以结合以下系统
Slice 判断耗时发生在哪个阶段：

```text
Choreographer#doFrame
traversal
measure
layout
draw-VRI
DrawFrames
postAndWait
binder transaction
```

当前采集配置还会记录：

- CPU 调度和线程状态
- CPU 频率与空闲状态
- Activity 和 WindowManager 事件
- View、Graphics 和 Input 事件
- Binder 调用
- ART/Dalvik 事件
- 内存及进程统计

### 业务方法插桩

只有 `perfetto` 构建会为方法添加 Trace 切片，`debug` 与 `release` 都不会。插桩在编译期通过
`buildSrc` 中的 ASM Visitor 完成，不进入业务代码，也不需要手写埋点。

插桩范围是**本项目全部 8 个模块**（类名前缀 `org.nigao.zhihuLite`），第三方依赖不处理。
这一点是实测的：反汇编 `assemble-perfetto.apk` 的 dex 后，共 480 个类包含插桩调用，分属全部
8 个模块——`:model`（136）、`:business_ui`（182）、`:business_logic`（116）、`:assemble`（21）、
`:base_navigation`（12）、`:base_ui`（7）、`:base_logic`（4）、`:performance`（2）。
`:model`、`:base_logic` 这类纯 JVM 模块虽然在事件流里出现得少，但确实被插桩了。

如果某个高频或耗时极短的方法没有分析价值，可以排除它：

```kotlin
import org.nigao.zhihuLite.performance.NoBusinessTrace

@NoBusinessTrace
fun trivialGetter(): String = value
```

### Trace 注意事项

- 同步 Trace Slice 表示同一线程内的同步执行区间。
- `suspend` 方法发生挂起或线程切换时，不代表完整的端到端耗时；必要时应补充异步
  Trace。
- 全量方法插桩存在一定观测开销，尤其是高频 Compose 方法和极短方法。定位热点后，
  建议使用 `@NoBusinessTrace` 排除低价值切片。
- 手动采集文件会随录制时间增长，应避免长时间无目标录制。
- `captures/` 已被 `.gitignore` 忽略，Trace 文件不会默认提交到仓库。

更多细节参见：

- [Perfetto 业务方法耗时说明](docs/perfetto-business-method-tracing.md)
- [Perfetto Web Viewer](https://perfetto.dev/#viewer)

## 文档

- [`docs/REFACTOR_PLAN.md`](docs/REFACTOR_PLAN.md)：分层重构方案、每一轮的修订记录，以及每个已修
  缺陷的根因分析。项目当前的架构约定与已知取舍以它为准。
- [`docs/perfetto-business-method-tracing.md`](docs/perfetto-business-method-tracing.md)：
  perfetto 变体的插桩与采集细节。
