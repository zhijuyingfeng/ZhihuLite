# Perfetto 业务方法耗时（无需 Android Studio）

专用的 `perfetto` 构建会在编译期自动为 `org.nigao.zhihuLite` 包下的具体方法添加
`android.os.Trace.beginSection/endSection` 调用，不需要在每个方法里手写埋点。
`debug` 和 `release` 构建均不进行业务方法插桩。

## 一键构建、安装和采集

连接 Android 9（API 28）或更高版本设备后运行：

```bash
./scripts/capture-perfetto.sh --install --launch
```

脚本会：

1. 用 Gradle 构建并安装 `perfetto` 变体。
2. 通过 `adb shell perfetto` 开始录制。
3. 启动 `org.nigao.zhihuLite.perfetto`。
4. 进入等待状态，此时直接在手机上操作要分析的业务流程。
5. 在终端按下回车后停止采集、等待 Perfetto 刷盘，并将文件拉取到 `captures/`。

默认是手动停止模式，也可以显式指定：

```bash
./scripts/capture-perfetto.sh --manual --launch
```

手动模式会启用周期性写盘，避免采集时间超过内存环形缓冲区容量后覆盖开头的数据。
采集文件会随录制时间增长，结束后脚本会删除设备上的临时文件。

需要无人值守采集时，可以指定固定时长：

```bash
./scripts/capture-perfetto.sh \
  --duration 20 \
  --output captures/feed.perfetto-trace \
  --launch
```

已经安装应用时不需要再次传 `--install`。

多设备环境使用：

```bash
./scripts/capture-perfetto.sh --serial 设备序列号 --launch
```

部分 OEM 设备首次通过 ADB 安装时会弹出确认框。如果出现
`INSTALL_FAILED_USER_RESTRICTED`，请在设备上允许本次安装，或在开发者选项中开启
“通过 USB 安装”，然后重新执行。

## 不使用脚本的纯命令方式

首次构建和安装：

```bash
./gradlew :app:installPerfetto
```

不使用脚本时，手动启动并停止采集：

```bash
DEVICE_TRACE=/data/misc/perfetto-traces/zhihulite.perfetto-trace

adb shell rm -f "$DEVICE_TRACE"
PID="$(adb shell perfetto \
  --background-wait \
  --txt \
  -c - \
  -o "$DEVICE_TRACE" \
  < <(sed \
      -e '/^[[:space:]]*duration_ms:/d' \
      -e '$a write_into_file: true' \
      -e '$a file_write_period_ms: 1000' \
      -e '$a flush_period_ms: 5000' \
      perfetto/business-methods.pbtxt) \
  | tr -d '\r')"

# 在手机上操作业务，按回车后结束并等待 Perfetto 刷盘。
read -r
adb shell kill -TERM "$PID"
while adb shell kill -0 "$PID" >/dev/null 2>&1; do sleep 0.1; done

adb pull "$DEVICE_TRACE" captures/zhihulite.perfetto-trace
adb shell rm -f "$DEVICE_TRACE"
```

## 使用 Web Viewer

1. 打开 `https://perfetto.dev/#viewer`。
2. 把生成的 `.perfetto-trace` 文件拖入页面。
3. 找到 `org.nigao.zhihuLite.perfetto` 进程。
4. 在线程 Slice 中搜索 `BM:`。

切片名称格式：

```text
BM:data.FeedRepository#loadFeed(long,Continuation)
```

切片宽度就是该次同步方法调用的耗时；嵌套方法会显示为嵌套切片。方法异常退出时，
插桩生成的 catch-all 路径也会关闭切片，避免后续 Trace 层级错乱。

## 过滤

自动插桩默认：

- 只处理当前 App 模块编译生成的类，不处理三方依赖。
- 排除 `R`、`BuildConfig`、`Manifest`。
- 排除 abstract、native、synthetic、bridge 方法。
- 只在 `perfetto` 变体启用，`debug` 和 `release` 没有这部分运行时开销。
- `perfetto` 变体使用独立包名 `org.nigao.zhihuLite.perfetto`，不会覆盖正式安装包。
- `perfetto` 变体通过 `<profileable android:shell="true">` 允许 shell 进行性能采集。

对高频、无分析价值的方法或整个类使用：

```kotlin
import org.nigao.zhihuLite.performance.NoBusinessTrace

@NoBusinessTrace
fun trivialGetter(): String = value
```

临时关闭整个自动插桩：

```bash
./gradlew :app:assemblePerfetto -PbusinessTraceEnabled=false
```

采集配置位于：

```text
perfetto/business-methods.pbtxt
```

配置显式设置了 `atrace_apps`，以采集该应用通过 `android.os.Trace` 写入的业务方法切片。

## 重要限制

- `android.os.Trace` 的同步切片只能表示同一线程上的同步执行区间。
- `suspend` 方法跨挂起点或切换线程时，会显示为多个同步执行片段，而不是一个完整的
  端到端区间。需要端到端协程耗时时，应为具体业务操作补充异步 Trace。
- “每个方法”插桩本身会产生观测开销，尤其是 Compose 重组和极短方法。先用全量 Trace
  找到热点，再使用 `@NoBusinessTrace` 排除噪声，可得到更可信、更易读的结果。
