# 今晚

> 晚上做决定，早上不用想。

一个只做一件事的每日便签：**晚上把明天要做的事写下来，早上打开只看到当前该做的这一件。**

- 平台：Android（`minSdk 26` / `targetSdk 35`）
- 技术：Kotlin + Jetpack Compose + Room
- 数据：纯本地存储，**无账号、无联网、无数据收集**
- 状态：v0.1，已编译验证（见「验证记录」）

---

## 为什么做这个

大多数待办 App 的逻辑是「帮你记住」。但真正消耗人的不是「记不住」，而是「**不知道该先做哪个**」。

于是它们给你列表、优先级、进度条——让你在动手之前，先做一轮排序决策。**这正是我想消灭的那个动作。**

所以这里的规则反着来：

| 常规做法 | 这里的做法 | 为什么 |
|---|---|---|
| 列表 + 优先级 | 早上**只显示一件** | 不给列表 = 不给选择 = 不消耗判断力 |
| 无限添加 | 一晚最多 **5 件** | 超过 5 件早上就不会选了，等于没有 |
| 未完成自动顺延 | 第二天问「还做吗」 | 自动堆积会让列表两周内失控 |
| 打卡 / 统计 / 进度条 | **全部没有** | 每个维度都是一次额外判断 |

**没有的功能，都是有意去掉的。**

---

## 快速开始

### 方式 A：Android Studio（推荐）

1. Android Studio → **Open** → 选中本仓库根目录
2. 等待右下角 Gradle Sync 完成（首次会下载依赖）
3. 连接手机（开启「开发者选项 → USB 调试」），点绿色三角 Run

> 若提示缺少 SDK 组件，点提示里的安装即可。本项目在
> **Android SDK Platform 35** + **Build-Tools 34.0.0** 上编译通过。

### 方式 B：命令行（从仓库根目录执行）

```bash
# JDK 需 17+；Android Studio 自带的 JBR 即可
export JAVA_HOME="$LOCALAPPDATA/Programs/Android Studio/jbr"   # 路径按本机实际情况调整
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"

./gradlew :app:assembleDebug
```

产物与安装：

```bash
# 产物
app/build/outputs/apk/debug/app-debug.apk

# 装到手机（USB 调试已开启）
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> 也可以直接把 `app-debug.apk` 传到手机上点击安装（需允许「安装未知应用」）。

---

## 日常怎么用

| 时间 | 你做什么 | App 做什么 |
|---|---|---|
| 晚上 | 打开，写明天要做的事（最多 5 件） | 深色界面，安静地收下 |
| 早上 | 打开 | 只显示**第一件**，大字 |
| 做完 | 点「做完了」 | 自动出现下一件 |
| 全做完 | — | 一句「今天就这样。」 |

### 界面为什么一天一个样

- **晚上是深色**：睡前低刺激
- **早上是浅色**：一眼看清

这两套主题是**写死的，不跟随系统深色模式**。
因为深浅色在这里是功能，不是偏好——若跟随系统，一个开着浅色模式的用户晚上会看到刺眼白屏，产品意图直接破功。

---

## 项目结构

```
app/src/main/java/com/clivena/tonight/
├── MainActivity.kt              入口 + 四个屏幕的路由
├── MainViewModel.kt             决定进哪一屏
├── TonightApp.kt                手动持有依赖（未引入 Hilt）
├── data/
│   ├── TaskRepository.kt            接口 —— 数据层对 UI 的唯一出口
│   ├── LocalTaskRepository.kt       本地实现（含 5 件上限逻辑）
│   └── local/                       Room：Entity / Dao / Database
├── domain/Task.kt               领域模型（UI 只认这个）
├── ui/
│   ├── theme/                   两套硬绑定的主题
│   ├── evening/                 今晚屏：规划
│   ├── morning/                 明早屏：执行
│   └── common/QuietScreen.kt    安静的整屏画面
└── util/DateUtil.kt             决定任务归属哪一天
```

### 几个关键设计

**1. 只有一张表，但预留了同步字段**
`TaskEntity` 一张表存全部。不建「计划表」——日期可以从 `planDate` 派生。
但预先建好了 `remoteId` / `updatedAt` 两个空字段，**将来接云同步零迁移成本**（Room 加字段要写 Migration）。

**2. UI 层不碰 Room 类型**
UI 只认 `Task`（domain 模型），`TaskEntity` 封在 Repository 内部。
换存储引擎、加云同步，UI 一行不用改。

**3. 全链路 Flow**
Room 数据一变，界面自动刷新。没有手动 `refresh()`。

**4. 5 件上限写在 Repository 里**
`LocalTaskRepository.MAX_TASKS_PER_DAY`。它是**产品约束**，不是界面规则，所以不该放在 UI 层。

**5. 没有「自动顺延」**
没做完的事不会自动滚到第二天。第二天晚上它会问「这几件还做吗」。
让用户主动放弃，比系统悄悄堆积健康得多。

**6. 依赖只有一条链，不引额外框架**
没引入 Hilt、没引入 Navigation。屏幕只有四个、依赖链只有一条，引库的收益接近零、成本是一层抽象。这个判断写在代码注释里。

---

## 验证记录

```
> ./gradlew :app:assembleDebug

BUILD SUCCESSFUL in 7m 54s
37 actionable tasks: 37 executed
```

- 产出：`app/build/outputs/apk/debug/app-debug.apk`，**9.3 MB**
- 一次编译通过，无错误
- 编译环境：JDK 21（Android Studio JBR）· AGP 8.7.3 · Gradle 8.9 · Build-Tools 34.0.0 / Platform 35

> 构建时若网络受限，Gradle 官方源可能极慢（实测走代理仅 39 KB/s）。
> 可改用镜像：`https://mirrors.cloud.tencent.com/gradle/`。

---

## 已知边界（v0.1）

| 限制 | 说明 |
|---|---|
| 没有提醒 | 需要通知权限 + 国产 ROM 后台保活，放 v0.2 |
| 没有历史回顾 | 数据都在，只是没有界面 |
| **没有数据导出** | **这是 v0.1 最明显的缺口**，见下方 |
| 跨天不自动刷新 | App 长期驻留后台跨过零点时，需重启一次 |

### 关于数据安全

数据全在本机，**卸载 App 会全部丢失**。
在加上「导出」功能之前，重要内容请自己另外记一份。
这也是我把「数据导出」列为 v0.2 第一优先项的原因——它是唯一的后悔药。

---

## v0.2 方向

按价值排序：

1. **数据导出**（最该先做）—— 唯一的后悔药，几十行代码
2. **早上提醒** —— 涉及通知权限与国产 ROM 白名单引导
3. **历史回顾** —— 翻看过去写过什么
4. **任务拆解** —— 把「写周报」变成「打开上周周报，三个项目各写一行」
   这是把判断力消耗真正降到零的那一步，也是最值得接入 AI 的位置

---

## 技术栈版本

版本集中在 `gradle/libs.versions.toml`（Version Catalog），改版本只需动这一个文件：

```
AGP 8.7.3 · Kotlin 2.0.21 · Compose BOM 2024.11.00 · Room 2.6.1
```

若你的 Android Studio 要求更高的 AGP，直接改此文件即可，其他文件不用动。

---

## License

[MIT](LICENSE)
