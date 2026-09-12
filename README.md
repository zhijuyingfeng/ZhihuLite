# ZhihuLite

ZhihuLite 是一个使用 Kotlin 和 Jetpack Compose 编写的轻量 Android 客户端项目，主要用于
展示信息流、回答详情、评论、图片和视频内容，并通过 WebView 完成登录。

> 本项目并非知乎官方客户端，主要用于 Android、Compose、网络请求、代码生成和性能分析等
> 技术实践。服务端接口、页面结构和登录流程可能随上游服务变化。

## 功能概览

- WebView 登录及 Cookie 状态保存
- 首页推荐信息流
- 问题与回答列表
- 评论列表
- 图片预览
- 视频内容展示
- 系统分享
- 下拉刷新和分页加载
- 基于 KSP 的路由自动注册
- 基于 ASM 的业务方法 Perfetto 自动插桩

## 技术栈

| 类型 | 技术 |
|---|---|
| UI | Jetpack Compose、Material 3 |
| 导航 | Navigation Compose |
| 状态管理 | AndroidX ViewModel、Kotlin Flow |
| 网络 | Ktor Client、OkHttp、Kotlinx Serialization |
| 图片 | Coil 3 |
| 本地配置 | Multiplatform Settings |
| 日志 | Napier |
| 代码生成 | KSP、KotlinPoet |
| 性能分析 | Perfetto、`android.os.Trace`、ASM 字节码插桩 |

## 项目结构

```text
ZhihuLite/
├── app/
│   ├── src/main/                    # Android 应用代码和资源
│   ├── src/perfetto/                # Perfetto 构建专用 Manifest
│   └── gaia/                        # 基于 KSP 的路由注册代码生成器
├── buildSrc/                        # ASM 业务方法 Trace 插桩
├── perfetto/
│   └── business-methods.pbtxt       # Perfetto 采集配置
├── scripts/
│   └── capture-perfetto.sh          # 无需 Android Studio 的采集脚本
├── docs/
│   └── perfetto-business-method-tracing.md
└── captures/                        # 本地 Trace 输出目录，不提交到 Git
```

## 环境要求

- JDK 17
- Android SDK Platform 35
- Android SDK Build Tools 35.0.0
- Android 设备或模拟器
- 使用性能采集脚本时，需要：
  - Bash
  - `adb` 已加入 `PATH`
  - Android 9 / API 28 或更高版本设备
  - 设备已开启 USB 调试并授权当前电脑

应用配置：

| 配置 | 值 |
|---|---:|
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
./gradlew :app:assembleDebug
```

APK 默认输出到：

```text
app/build/outputs/apk/debug/
```

直接安装到已连接设备：

```bash
./gradlew :app:installDebug
```

### Perfetto APK

`perfetto` 是专门用于性能采集的构建类型：

- 包名为 `org.nigao.zhihuLite.perfetto`，不会覆盖普通安装包。
- 使用 Debug 签名，便于本地安装。
- 关闭代码压缩和资源压缩，保留可读的方法名。
- 开启 `<profileable android:shell="true">`。
- 默认对项目业务方法执行 ASM Trace 插桩。

构建：

```bash
./gradlew :app:assemblePerfetto
```

安装：

```bash
./gradlew :app:installPerfetto
```

如果临时不需要业务方法自动插桩：

```bash
./gradlew :app:assemblePerfetto -PbusinessTraceEnabled=false
```

### Release APK 和 AAB

```bash
./gradlew :app:assembleRelease :app:bundleRelease
```

版本号可以通过 Gradle 属性传入：

```bash
./gradlew :app:assembleRelease :app:bundleRelease \
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

### 运行测试

```bash
./gradlew test
```

只运行 App 模块单元测试：

```bash
./gradlew :app:testDebugUnitTest
```

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

业务切片示例：

```text
BM:data.FeedRepository#getInitialItems(Continuation)
BM:login.AuthWebViewKt#AuthWebView$lambda$3$lambda$2(...)
BM:MainActivity#onCreate(Bundle)
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

只有 `perfetto` 构建会为 `org.nigao.zhihuLite` 包下的具体方法添加：

```kotlin
android.os.Trace.beginSection(...)
android.os.Trace.endSection()
```

插桩在编译期通过 `buildSrc` 中的 ASM Visitor 完成，不需要在每个方法中手动添加代码，
并且不会进入 Debug 和 Release 构建。

如果某个高频或耗时极短的方法没有分析价值，可以排除它：

```kotlin
import org.nigao.zhihuLite.performance.NoBusinessTrace

@NoBusinessTrace
fun trivialGetter(): String = value
```

### Trace 注意事项

- 自动插桩只处理当前 App 模块生成的类，不处理第三方依赖。
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
