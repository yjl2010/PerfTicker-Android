# 性能行情终端 · PerfTicker for Android

把 [QiuSui1145/PerfTicker](https://github.com/QiuSui1145/PerfTicker)（Windows 版「任务管理器 × 炒股行情终端」）
移植到 Android，用 GitHub Actions 云构建出可安装的 APK。

> 原作：**QiuSui** · 组织 **QiuSui_Studio** · 协议 **GPL-3.0**
> 本工程是其 GPL-3.0 授权的安卓移植版，保留原作者署名与协议，衍生代码同样以 GPL-3.0 发布。

---

## 一、为什么不是「把它编译一遍」

原版是 C# WinForms + GDI+ 全自绘程序，数据全部来自 Windows 私有接口：

| 原版数据源 | 说明 | Android 可移植性 |
| --- | --- | --- |
| `NtQuerySystemInformation` | 逐核占用、全量进程快照 | 无此接口；进程表被 `hidepid` 限制 |
| PDH 计数器 | CPU 频率、GPU Engine、磁盘、网络 | 无 PDH |
| NVML (`nvml.dll`) | GPU 频率 / 显存 / 温度 / 功耗 / 风扇 / 逐进程 GPU | 无 NVML |
| `GlobalMemoryStatusEx` | 内存占用 | 有等价来源 |
| WinForms + GDI+ | 全部界面 | 需换成 Android Canvas 自绘 |

所以本工程是**用 Java 从零重写的安卓移植版**：界面语言（行情终端）、K 线聚合规则、涨跌口径、
营收结算公式（`cap × load^1.7 − cap × be^1.7`）都与原版一致；采集层换成 Android 允许读取的来源。

---

## 二、已移植的能力

| 原版功能 | 安卓版实现 |
| --- | --- |
| 指数条（8 格 + 迷你 K） | ✅ CPU 综合指数 / CPU 负载 / 内存 / GPU 负载 / 电池温度 / CPU 温度 / 网络下行 / 上下文切换 |
| 自选行情（52 项」） | ✅ 按板块分组的全部可读指标（指数 / 处理器 / 图形 / 存储 / 温度 / 网络 / 系统） |
| 逐核频率 | ✅ 每个逻辑核一只「个股」，取 `cpufreq/scaling_cur_freq` |
| 交易所风格代码 | ✅ `000001`、`000101`… |
| K 线（2s/5s/10s/30s/1min 聚合 OHLC） | ✅ 采样点即 tick，按周期聚合，红涨实心 / 绿跌实心 |
| MA5/10/20 + 成交量 + 十字光标 OHLC 浮窗 | ✅ 主图自绘，触摸拖动看单根 K 线 |
| 涨跌 / 涨跌幅 / 今开 / 最高 / 最低 / 均价 / 振幅 | ✅ 全部按各自单位显示（`+0.12GHz`、`-2.8pt`、`+0.4W` 风格） |
| 逐笔成交（告警 / 主频跳变 / 新高新低） | ✅ 温度与负载阈值、主频跳变、品种新高新低、运行统计 |
| 采样档位 0.5s–10s | ✅ 设置里可切换，K 线周期自动可用 |
| 红涨绿跌 / 绿涨红跌 | ✅ 设置可切 |
| 营收中心（5 个经营单元 + 盈亏平衡线 + 净收走势） | ✅ 公式一致，硬件自适应折算 0.3–4.0 倍，金额本地累计 |
| 主力动向 Top 10 进程 | ⚠️ 改为「板块热度」口径之外的全品种表（Android 无法枚举其它 App 进程） |
| 全量进程表（PID / CPU% / GPU% / 句柄） | ⚠️ 改为「全品种行情表」，可按代码 / 现价 / 涨跌 / 成交量排序 |
| 托盘 / 桌面挂件 / 窗口置顶 | ⚠️ Android 无此形态，改为前后台暂停采样 |

---

## 三、数据口径（全部为只读查询，不修改系统状态）

| 指标 | 采集方式 |
| --- | --- |
| CPU 占用 | `/proc/stat` 逐核 busy/total 差分，整机取平均 |
| CPU 频率 | `/sys/devices/system/cpu/cpuN/cpufreq/scaling_cur_freq` |
| 内存 | `/proc/meminfo`（MemTotal / MemAvailable） |
| 电池温度 / 电量 | `BatteryManager.ACTION_BATTERY_CHANGED` |
| 机身 / CPU 温度 | `/sys/class/thermal/thermal_zone*/type` + `temp` |
| 存储 | `StatFs`（数据分区） |
| 网络上下行 | `TrafficStats` 累计字节差分 |
| 存储 IO | `/proc/diskstats` 扇区差分（过滤分区，避免重复计数） |
| 成交量 | `/proc/stat` 的 `ctxt`（上下文切换）差分，与原版同口径 |
| GPU | 厂商 sysfs（`kgsl` / `mali` / `devfreq`）。**多数机型对第三方 App 不可读**，读不到就显示 `—`，不伪造 |

读不到的项一律 `—`；`GPU 负载 / GPU 温度 / GPU 频率` 在大多数非 root 设备上会是 `—`。

---

## 四、云构建（GitHub Actions）

工作流：`.github/workflows/build-apk.yml`，push 到 `main` / `master` 或手动触发即构建，产出：

- `app-release.apk`（正式签名，见下方说明）
- `app-debug.apk`（调试签名，可共存安装）

### 方式 A：一键脚本（推荐，本机没有 git 也能用）

```powershell
# 在 Windows PowerShell 里执行（需要能访问 github.com 与 api.github.com）
powershell -ExecutionPolicy Bypass -File .\云构建.ps1
```

脚本会：创建/复用仓库 → 用 GitHub Git Data API 推送全部源码 → 等 Actions 跑完 →
下载 `PerfTicker-apk` 制品并解出 APK 到本目录。Token 只在本地使用，不会写进仓库。

> Token 需要 `repo` + `workflow` 权限（Fine-grained PAT 勾选 Contents / Actions / Administration）。

### 方式 B：手动用 GitHub 网页 / GitHub Desktop

1. 新建空仓库（**不要**勾选 Add README / .gitignore / license，否则会与首次推送冲突）。
2. 把本目录**内容**（含 `.github` 文件夹）传到仓库根目录。
   - 网页上传会忽略以 `.` 开头的目录，`.github` 需要手动新建同名文件：`New file` → 输入路径 `.github/workflows/build-apk.yml` → 粘贴内容。
3. 打开 `Actions` 标签，等 `Build APK` 变绿 → 进 run 页面下载 `PerfTicker-apk`。
4. 打 tag（如 `v1.0.0`）会自动把 APK 附加到 Release。

### 方式 C：有 Android SDK 的机器本地构建

```bash
gradle :app:assembleDebug        # 不需要签名配置
gradle :app:assembleRelease      # 需要 app/keystore/perfticker.jks（见 build.gradle 的 PT_KEYSTORE 环境变量）
```

工程没有 Gradle Wrapper（避免提交二进制 jar），用 Gradle 8.7+ / JDK 17 / compileSdk 34。

---

## 五、安装

1. 手机打开「允许安装未知来源应用」。
2. 安装 `app-release.apk`（或 `app-debug.apk`，包名带 `.debug`，可与正式版共存）。
3. 首次进入会立即开始 1s 采样；顶部状态胶囊可暂停/继续。

> CI 里的正式签名用的是构建时**临时生成**的 keystore（口令 `perfticker`），只为让 Release 包能装。
> 如果要长期更新同一 App（覆盖安装保留数据），请自己固定一份 keystore 并改成仓库 Secret。

---

## 六、代码结构

```
app/src/main/java/studio/qiusui/perfticker/
├─ MainActivity.java   标题栏 / 四页签 / 设置 / 关于 / 刷新调度
├─ Probe.java          采集层：/proc、sysfs、BatteryManager、TrafficStats、StatFs
├─ Snap.java           一次采样的快照
├─ Catalog.java        指标目录（代码 / 名称 / 单位 / 精度 / 板块）
├─ Ticker.java         品种行情状态 + 5 个周期 K 线聚合
├─ Candle.java         单根 K 线
├─ Sampler.java        采样线程、事件生成、逐笔流水
├─ Economy.java        营收结算（与原版同公式）
├─ Store.java          设置持久化（SharedPreferences）
├─ Theme.java          深色 / 浅色证券终端配色与画笔
├─ Ui.java             排版工具
├─ ChartView.java      主图：K 线 + MA + 成交量 + 十字光标
├─ SparkView.java      迷你 K 线
├─ IndexCard.java      指数格
├─ MeterView.java      盈亏平衡负载条
├─ LineView.java       净收走势
├─ Marquee.java        跑马灯
├─ PanelHeader.java    品种头
├─ PanelWatch.java     自选行情
├─ PanelTable.java     全品种表（可排序）
├─ PanelTape.java      逐笔成交
└─ PanelEconomy.java   营收中心
```

---

## 七、已知限制

- **GPU 指标**：`kgsl` / `mali` 的 sysfs 多数对 App 不可读（SELinux），显示 `—`。要真数据需要 root 或使用厂商私有 SDK。
- **进程表**：Android 9+ 起 `/proc` 对其它 App 隐藏（hidepid），无法像 Windows 那样列出全量进程、句柄数、逐进程 GPU。因此改为全品种行情表。
- **CPU 型号**：部分 ARM 机型 `/proc/cpuinfo` 不暴露型号，退回 `Build.SOC_MODEL` / `Build.HARDWARE`。
- **后台采样**：进入后台即暂停采样（省电，也不需要前台服务权限）。若要长期后台监控，需要加 `FOREGROUND_SERVICE` 与常驻通知。
- **温度口径**：`thermal_zone` 的 type 命名各厂商不同，CPU 温度可能取到的是 SoC 温度。
- **K 线周期**：最短 2 秒；采样间隔 10s 时 2s/5s 周期的 K 线会很稀疏（与原版「周期不低于采样间隔 2 倍」的取舍一致，这里不强制改写用户选择）。

---

## 八、协议

GPL-3.0。原版版权归 QiuSui / QiuSui_Studio；本移植版同样以 GPL-3.0 发布，
分发衍生作品需保留版权声明并以同协议开源。
