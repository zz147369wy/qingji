# 鲸记 · QingJi

一个**纯本地、自用向**的 Android 日程 / 待办 / 课表应用。主打「今天要干什么」这一件事，围绕它做了日历课表、长期计划、统计、桌面小组件、系统日历同步，以及一个能用大白话直接改数据的 AI 助手「空空」。不联网账号、不上传云端，所有数据留在本机 Room 数据库里。

> 一句话：把「想到什么、随手说一句」变成「今天的清单和课表」，并让一只叫**空空**的鲸鱼在旁边陪着你。

---

## 功能

### 今日
- 按时间轴 / 分组列出当天任务，勾选完成；全部完成会有庆祝动效
- 左滑露出操作（完成 / 编辑 / 删除），滑动交互自带手势数学单元测试
- 顶部进度环 + 每日一句问候；一键「新建」「批量录入」「AI 排期」
- 「每日结算」：到点自动弹出，给当天的完成情况打分、写复盘

### 日历 / 课表
- 月历视图（可左右翻月）+ 周课表视图（可左右翻周），顶部切换器跟随滑动实时更新
- 课程支持学期、周次（单双周）、节次、教室、颜色；节假日与假期表
- 分类筛选 chips，可直接在日历上新建

### 长期计划
- 把长期目标拆成每天能落地的任务（支持按周期生成、自动排期到未来日期）
- 与今日列表联动：计划生成的条目就是普通任务

### 统计
- 完成率、累计完成、结算天数、平均分、趋势图
- 「类别占比 · 鱼群」：用气泡堆叠展示各类别的时间/数量占比

### AI 助手「空空」
- 底部导航正中那颗凸出的头像钮，点开从底部浮出整页聊天
- 自然语言指令 → AI 返回 **JSON actions 数组** → **先预览、用户回复「确认」才执行**（增/改/删任务、课程、计划）
- 支持批量录入、图片导入（从截图里读日程）
- 兼容任意 OpenAI 格式的 API：设置页填 **API 地址 / API Key / 模型名**，可「测试连接」
- 提示词与站点原版逐字对齐（`data/remote/AiPrompts.kt`）

### 提醒与系统集成
- `AlarmManager` 精确闹钟 + 通知渠道；重启、升级、改时间、换时区后自动重排
- 冷启动时再排一次，专门对付国产 ROM 清后台
- 通知权限状态机：区分「从未询问 / 拒绝一次 / 永久拒绝」，并用 `areNotificationsEnabled()` 兜住 ROM 的通知总开关
- 可把日程写入**手机自带日历**（选目标日历、读写权限运行时申请）
- **桌面小组件**（Glance）：不开 App 也能看今天要干的事，数据库变更自动刷新

### 桌宠「空空」
- 用 WebView 承载 `assets/whale_pet/` 里的角色 web 包（骨骼/贴图/表情），叠在界面上
- 8 套配色皮肤（默认 + chatgpt / claude / gemini / harness / kimi / minimax / qwen 风格）
- 会对你做的事有反应：完成一条她跳一下、全完成转个圈、删掉一条摇摇头；可开关「对我的操作做反应」「偶尔出门逛逛」

### 主题与外观
- 鲸鱼主题「空空的海」：8 套鲸鱼配色 × 浅色/深色 = 16 套，全部由主色按固定公式推导
- 圆形换肤动画（点哪个配色就从哪扩散）、玻璃质感卡片、触感反馈（按语义分级）

### 数据
- 备份/恢复：导出人可读的 JSON；恢复 = **覆盖**（先清空再导入）
- 恢复前自动存一份临时备份，导入前先解析预览数量
- 分类管理、每节课配色、每日结算时间等均可在设置里改

---

## 技术栈

| 项 | 选择 |
|---|---|
| 语言 | Kotlin 2.2.10（AGP 9 内置 Kotlin） |
| 构建 | Gradle 9.6 + AGP 9.4.1，Version Catalog（`gradle/libs.versions.toml`） |
| 最低/目标 | minSdk 26 / targetSdk 36，compileSdk 37，JDK 17 |
| UI | Jetpack Compose（BOM 2026.09.00）+ Material 3 + Navigation Compose |
| 数据 | Room + KSP，kotlinx.serialization |
| 网络 | OkHttp（OpenAI 兼容接口） |
| 图片 | Coil |
| 小组件 | Glance AppWidget + WorkManager |
| 测试 | JUnit4 + Robolectric + Compose UI Test（40+ 测试类，覆盖状态机/解析/排期/布局数学） |

**架构**：`QingJiApp` 做极简依赖容器（不上 Hilt，避免注解处理器），Repository + ViewModel + State 单向数据流；纯逻辑（解析、排期、结算、权限状态机）全部抽成可单测的纯函数。

---

## 目录结构

```
app/src/main/java/com/qingji/plan/
├── MainActivity.kt / QingJiApp.kt      入口与依赖容器
├── data/
│   ├── db/            Room 数据库、DAO、Entity
│   ├── model/         领域模型（分类、优先级、节次、节假日…）
│   ├── remote/        AI 客户端、提示词、JSON action 解析与执行
│   ├── repo/          Repository 层
│   └── backup/        备份导出/恢复
├── notify/            闹钟、通知、通知权限状态机
├── service/           批量录入解析、自动排期
├── syscal/            写入系统日历
├── util/              日期/时间/结算/图片工具
├── widget/            Glance 桌面小组件
└── ui/
    ├── nav/           底部导航与页面路由
    ├── today/ calendar/ plans/ stats/ settings/ ai/ batch/ edit/ course/
    ├── components/    QingJiCard、Sticker、SegmentedControl、滑动手势等
    ├── pet/           桌宠 WebView 与控制器
    └── theme/         鲸鱼配色体系与换肤动画
```

资源：`app/src/main/assets/whale_pet/` 是桌宠的 Web 角色包；`app/src/main/res/font/` 是 Fredoka 字体；`app/src/main/res/drawable-nodpi/` 是空空的头像。

---

## 构建

```bash
# 需要 JDK 17 与 Android SDK（compileSdk 37）
# local.properties 里写：sdk.dir=D:\\dev-tools\\android-sdk

./gradlew :app:assembleDebug        # Windows: gradlew.bat :app:assembleDebug
./gradlew :app:testDebugUnitTest    # 单元测试
```

> 项目自用、不上架：`USE_EXACT_ALARM` 等权限按「安装即授予、零交互」的策略选的，详见 `AndroidManifest.xml` 注释。

---

## 隐私

- 无自有后端、无账号体系；任务、课表、统计全部存在本机 Room
- AI 功能直连**你自己填的** OpenAI 兼容接口，API Key 存在本机设置里，不经过任何第三方服务器
- 备份是本地 JSON 文件，导到哪里由你决定

---

## 关于这个仓库

- 内容为本地工程 `D:\qingji` 的源码快照：包含 `app/` 全部源码与资源、`gradle/` 版本目录与 wrapper
- 恢复过程中原目录的 **根构建脚本（`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradlew`）** 已按 `app/build.gradle.kts` 与 `gradle/libs.versions.toml` 的版本信息重建
- 构建产物（`build/`、`.gradle/`、`.kotlin/`、`local.properties`）已由 `.gitignore` 排除
