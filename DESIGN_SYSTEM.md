# TChat Design System

> 本文档拆为两部分：
> - **Part A：设计原则** — 理念、定位、不可量化的判断准则
> - **Part B：实现 Token** — 设计标记的实现指引（标有 [复制] 的可直接粘贴，标有 [模板] 的需适配项目后使用）
>
> **落地规则**：必须先创建共享模块再落 token。Part B 中所有标记必须先于任何页面改动落地到对应 `.kt` 文件。文档自身不做代码生效，代码以实际编译文件为准。

---

# Part A：设计原则

## A1 核心原则

| 原则 | 说明 |
|------|------|
| **克制** | 不堆砌颜色/动效，每个设计决策服务于信息传递效率 |
| **流体** | 动画是引导视线的手段，不是装饰 |
| **分层** | 界面深度 = 信息层级。平坦主界面，悬浮弹层，明确 Z 轴 |
| **品牌低调** | 品牌色只用在对用户有意义的地方，不做装饰背景 |

## A2 色彩定位

品牌色以 **Material 淡蓝** 为基调：

```
TChat Icon 白色 → Material 淡蓝 (#1976D2)
   → 干净、专业、不打扰
      → 与 Telegram 深蓝区别开，接近 Google/Material 原生气质
```

**为什么不是茶色？** 当前 Color.kt 的茶色 (#205A56) 与图标气质不匹配。Material 淡蓝色更符合"AI 聊天工具"的科技感，在暖白背景上更通透。

## A3 与 Telegram 的区别

```
Telegram 的做法               →  我们的转化
────────────────────────────────────────────
深蓝品牌色贯穿                →  Material 淡蓝主色调，更轻快
抽屉导航                      →  保留现有 Drawer，统一动效
列表式会话                    →  保持卡片结构，增加分层感
大圆角头像                    →  自定义 ChatAvatar 组件
spring 动效                  →  统一 SpringSpec 体系
```

## A4 可访问性基线

- 色板已满足 WCAG AA 对比度（4.5:1 文本 / 3:1 大文本），正文链接使用 `AppColors.linkText`（浅色 Blue 900 / 深色浅蓝）
- **减少动效**：系统 `animatorDurationScale == 0f` 时，所有 spring → 0ms snap，跳过入场动画
- **触觉反馈**：普通点击无触觉；仅长按/破坏性操作用 `LongPress`（详细见 B6b）
- 触摸目标 ≥ 48dp：M3 标准按钮/列表行已有 `minimumInteractiveComponentSize` 保障；自绘 icon、自定义 `clickable` Row 等需要显式封装：

```kotlin
// DesignTouchTargets.kt — 放在 :design-system [复制]
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

fun Modifier.tchatTouchTarget(): Modifier =
    sizeIn(minWidth = 48.dp, minHeight = 48.dp)
```

仅用于**非 Material 标准组件**（自绘 icon 按钮、自定义 clickable Row）。标准 Button/ListItem 不需要。

---

# Part B：实现 Token

---

## B0 模块归属 — 必须最先决策

设计 token 必须放在所有模块都能访问的位置。当前关键约束：

```
app 依赖 feature-chat；feature-chat 不能反向依赖 app
```

feature-chat **不能**反向依赖 app，因此 token 不能全放在 app 中。

### 方案：新建 `:design-system` Android Library

```
  :design-system (新增)
    │  Color.kt         [复制] 完整色板
    │  Theme.kt         [模板] 仅 ColorScheme + Shapes，不含 i18n
    │  Spacing.kt       [复制] 间距常量
    │  Motion.kt        [模板] 动效曲线（见 B4a）
    │  DesignTokens.kt  [复制] ChatColors + AppColors + AppShapes
    │  PageLevel.kt     [复制] 页面层级枚举
    │  DesignTouchTargets.kt [复制] 自定义点击区域最小触摸目标
```

**settings.gradle.kts** 新增：
```kotlin
include(":design-system")
```

**design-system/build.gradle.kts**（完整模板）：
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tchat.designsystem"
    compileSdk = 36

    defaultConfig { minSdk = 26 }

    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
}
```

**app/build.gradle.kts** 与 **feature-chat/build.gradle.kts** 各加一行：
```kotlin
implementation(project(":design-system"))
```

### 依赖链修正

模块关系是 DAG，不是线性链：

```
app ────────────────► feature-chat
 │                         │
 ├────────► data ◄─────────┤
 │           │             │
 ├────────► network ◄──────┤
 │           │             │
 ├────────► core ◄─────────┤
 │                         │
 └────────► design-system ◄┘   ← 新增；只依赖 Compose 库

data ─────► network ─────► core
data ───────────────────► core
```

- `:design-system` 只依赖 Compose 库，不依赖任何项目模块
- 任何需要 design token 的模块加 `implementation(project(":design-system"))`

---

## B1 色板与主题 — `:design-system` [复制/模板]

以 **Material 淡蓝** 为主色调。`:design-system` 模块提供色板常量，**不包含** i18n/状态栏逻辑。

### 两层结构

```
:design-system/Color.kt        [复制] 完整 light/dark 色板常量
:design-system/Theme.kt        [模板] 仅定义 TChatColorScheme + Shapes，无 ProvideStrings
:app/Theme.kt                  保留  TChatTheme 包裹 ProvideStrings + 系统栏处理，委托色板给 DesignSystemTheme
```

### :design-system/Theme.kt

```kotlin
// 仅颜色方案 + 形状，不涉及 i18n 或 window
private val LightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val DarkScheme = darkColorScheme(
    // 同上，值来自 B1 dark 色板
)

@Composable
fun DesignSystemTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemeOverride: ColorScheme? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = colorSchemeOverride ?: if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,  // 来自 B3
        content = content,
    )
}
```

### app/Theme.kt 精简后

```kotlin
@Composable
fun TChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    language: Language = Language.ZH_CN,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorSchemeOverride = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> null
    }

    ProvideStrings(language = language) {
        DesignSystemTheme(
            darkTheme = darkTheme,
            colorSchemeOverride = colorSchemeOverride,
        ) {
            SideEffect { /* 系统栏设置 */ }
            content()
        }
    }
}
```

```kotlin
// ===== Light Theme =====
val primaryLight            = Color(0xFF1565C0)  // Material Blue 800（AA 通过：背景白上 5.2:1）
val onPrimaryLight          = Color(0xFFFFFFFF)
val primaryContainerLight   = Color(0xFFD1E4FF)
val onPrimaryContainerLight = Color(0xFF001D36)

val linkTextLight           = Color(0xFF0D47A1)  // Blue 900，小字号链接确保 AA 安全

val secondaryLight          = Color(0xFF535F70)
val onSecondaryLight        = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFD7E3F7)
val onSecondaryContainerLight = Color(0xFF101C2B)

val tertiaryLight           = Color(0xFF6B5778)
val onTertiaryLight         = Color(0xFFFFFFFF)
val tertiaryContainerLight  = Color(0xFFF2DAFF)
val onTertiaryContainerLight= Color(0xFF251431)

val errorLight              = Color(0xFFBA1A1A)
val onErrorLight            = Color(0xFFFFFFFF)
val errorContainerLight     = Color(0xFFFFDAD6)
val onErrorContainerLight   = Color(0xFF410002)

val backgroundLight         = Color(0xFFFDFBFF)
val onBackgroundLight       = Color(0xFF1A1B1E)
val surfaceLight            = Color(0xFFFDFBFF)
val onSurfaceLight          = Color(0xFF1A1B1E)
val surfaceVariantLight     = Color(0xFFDFE2EB)
val onSurfaceVariantLight   = Color(0xFF43474E)

val outlineLight            = Color(0xFF73777F)
val outlineVariantLight     = Color(0xFFC3C6CF)

val surfaceContainerLowestLight  = Color(0xFFFFFFFF)
val surfaceContainerLowLight     = Color(0xFFF7F5FA)
val surfaceContainerLight        = Color(0xFFF1EFF4)
val surfaceContainerHighLight    = Color(0xFFECEAEF)
val surfaceContainerHighestLight = Color(0xFFE6E4E9)

// ===== Dark Theme =====
// Dark 模式保持原有蓝绿色感知，由 #9ECAFF 在深色背景上提供足够对比
val primaryDark            = Color(0xFF9ECAFF)
val onPrimaryDark          = Color(0xFF003258)
val primaryContainerDark   = Color(0xFF00497D)
val onPrimaryContainerDark = Color(0xFFD1E4FF)

val linkTextDark           = Color(0xFF9ECAFF)  // 深色正文链接，复用高对比浅蓝

val secondaryDark          = Color(0xFFBBC7DB)
val onSecondaryDark        = Color(0xFF253140)
val secondaryContainerDark = Color(0xFF3B4858)
val onSecondaryContainerDark = Color(0xFFD7E3F7)

val tertiaryDark           = Color(0xFFD7BDE4)
val onTertiaryDark         = Color(0xFF3B2948)
val tertiaryContainerDark  = Color(0xFF523F5F)
val onTertiaryContainerDark= Color(0xFFF2DAFF)

val errorDark              = Color(0xFFFFB4AB)
val onErrorDark            = Color(0xFF690005)
val errorContainerDark     = Color(0xFF93000A)
val onErrorContainerDark   = Color(0xFFFFDAD6)

val backgroundDark         = Color(0xFF111318)
val onBackgroundDark       = Color(0xFFE2E2E9)
val surfaceDark            = Color(0xFF111318)
val onSurfaceDark          = Color(0xFFE2E2E9)
val surfaceVariantDark     = Color(0xFF43474E)
val onSurfaceVariantDark   = Color(0xFFC3C6CF)

val outlineDark            = Color(0xFF8D9199)
val outlineVariantDark     = Color(0xFF43474E)

val surfaceContainerLowestDark  = Color(0xFF0C0E13)
val surfaceContainerLowDark     = Color(0xFF191B20)
val surfaceContainerDark        = Color(0xFF1D1F25)
val surfaceContainerHighDark    = Color(0xFF282A2F)
val surfaceContainerHighestDark = Color(0xFF33353A)
```

### 语义色用法

```
surface 层级（从低到高）：
  background                    → 最底层（activity 背景）
  surfaceContainerLowest        → 主界面内容区
  surfaceContainerLow           → 设置分组外层
  surfaceContainer              → 卡片默认背景
  surfaceContainerHigh          → 可交互元素（输入框、可点击行）
  surfaceContainerHighest       → 弹出层、底部表单
  
  规则：两层之间至少差一级，不跳级使用
```

```
primary (#1565C0)：
  使用：按钮填充、FAB、开关选中态、未读标记
  禁止：小字号正文链接、大面积填充、装饰线条

linkText (light #0D47A1 / dark #9ECAFF)：
  使用：可点击文字链接、内联 action text（light/dark 均满足 AA）
  注意：primary 在浅色小字号上曾 AA 擦边，正文链接一律用 AppColors.linkText

primaryContainer (#D1E4FF)：
  使用：选中态背景、置顶标记、输入框聚焦边框

tertiary (#6B5778)：
  使用：标签、徽章、AI 模型名称标记
```

### B1a 应用级 token — 不污染 M3 全局语义

不要将 `secondaryContainer` 绑定为"仅用户气泡"。将 `linkTextLight` 原值纳入 composable 入口：

```kotlin
// DesignTokens.kt — 放在 :design-system，集中所有应用层色彩 token
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

object ChatColors {
    // 用户聊天气泡
    val userBubbleContainer: Color
        @Composable get() = MaterialTheme.colorScheme.secondaryContainer
    val onUserBubble: Color
        @Composable get() = MaterialTheme.colorScheme.onSecondaryContainer

    // AI 消息（沿用 surface 层级）
    val aiBubbleSurface: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

    // 模型名/标签
    val modelBadgeBackground: Color
        @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
    val modelBadgeText: Color
        @Composable get() = MaterialTheme.colorScheme.onTertiaryContainer
}

// 链接文字色 — primary 在小字号上 AA 擦边，正文链接专用
object AppColors {
    val linkText: Color
        @Composable get() {
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            return if (isDark) linkTextDark else linkTextLight
        }
}
```

## B2 间距 — 替换所有硬编码 padding

```kotlin
// Spacing.kt
object Spacing {
    val xs  = 4.dp     // 图标-文字间隙
    val sm  = 8.dp     // 组内元素间距
    val md  = 12.dp    // 分组间距
    val lg  = 16.dp    // 屏幕边缘 / 卡片内部水平
    val xl  = 24.dp    // 大区块间距
    val xxl = 32.dp    // 页面顶部/底部留白
}
```

**规则**：
- 屏幕边缘 padding → `Spacing.lg`
- 卡片内部水平 → `Spacing.lg`
- 卡片内部垂直 → `Spacing.md`
- 列表项之间 → `Spacing.sm`
- 列表分组之间 → `Spacing.lg`

## B3 圆角 — 替换 `AppSectionShape` (22dp)

当前 `Theme.kt` 的 shapes（6/12/18/26/34dp）和 `AppChrome.kt` 的 22dp/24dp 全部废弃。

```kotlin
// Theme.kt — Shapes
val shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),    // 按钮、小标签
    small      = RoundedCornerShape(8.dp),    // 输入框、小头像
    medium     = RoundedCornerShape(12.dp),   // 卡片、对话框
    large      = RoundedCornerShape(16.dp),   // 底部表单、大卡片
    extraLarge = RoundedCornerShape(28.dp),   // 聊天气泡
)

// DesignTokens.kt — 额外专用形状（单独定义，不塞进 shapes）
object AppShapes {
    val avatarSmall = RoundedCornerShape(24.dp)    // 对话列表头像
    val avatarLarge = RoundedCornerShape(40.dp)    // 聊天界面头像
    val sheetTop    = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
}
```

## B4 动效 — 替换 Motion 规则 [模板]

### B4a 核心曲线

```kotlin
// Motion.kt — 泛型工厂，需补全包名和 import
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FiniteAnimationSpec

// 泛型工厂：适用于 Float（Animatable）、IntOffset（slideInHorizontally）、Dp 等
// 使用方式：val spec: FiniteAnimationSpec<IntOffset> = Motion.pageTransition()
//          val spec: FiniteAnimationSpec<Float> = Motion.pageTransition()

object Motion {
    // 页面位移/尺寸变化 — spring
    inline fun <reified T> pageTransition(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
    // 列表项入场 — spring，无弹跳
    inline fun <reified T> listItemEnter(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )
    // 按钮缩放 — spring，快速回弹
    inline fun <reified T> pressFeedback(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh
    )
    // 弹窗/遮罩 — spring，无弹跳
    inline fun <reified T> sheetTransition(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
    // 透明度 fade — tween（仅 Float，透明度动画不涉及 IntOffset）
    val fadeTween: FiniteAnimationSpec<Float> = tween(
        durationMillis = 150,
        easing = FastOutSlowInEasing
    )
}
```

### B4b 场景选型（重要！纠正"禁用 tween"过激策略）

| 场景 | 动效 | 原因 |
|------|------|------|
| 页面滑动、列表入场 | `Motion.pageTransition<IntOffset>()` / `listItemEnter<Float>()` (spring) | 位移需要自然跟随感 |
| 淡入淡出、遮罩 scrim | `Motion.fadeTween` (tween 150ms) | 纯透明度用 spring 浪费性能，tween 更可预测 |
| 按钮缩放 | `Motion.pressFeedback` (spring) | 快速回弹是小尺度反馈的灵魂 |
| 颜色切换（checkbox/开关的 thumb + track） | `animateColorAsState` 默认曲线 (tween 300ms ease) | M3 AnimatedColor 默认已足够，颜色不适合 spring 弹跳 |
| 开关 thumb 位移 | spring | 物理位移，跟随手势 |
| 侧滑删除 | spring + drag 跟随 | 手势驱动必须物理跟随 |

**规则**：
- **位移 / 尺寸 / 拖拽** → spring
- **纯透明度 / 遮罩 alpha** → `Motion.fadeTween` (tween 150ms)
- **颜色值过渡** → `animateColorAsState()` 默认 (tween 300ms ease)，不要用 spring
- 永远不要 `tween()` 替代位移类 spring

### B4c 列表入场动画（修正版）

```kotlin
// 示意实现，需适配项目后编译
// 关键设计：
//   1. 用 stableKey: (T) -> Any 追踪 item，不用 index
//   2. seenKeys 避免重复动画
//   3. LocalReducedMotion 控制是否跳过入场

// --- 统一 reduce motion 入口（放在 DesignTokens.kt 或 Motion.kt 旁边）---
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}
// ---

@Composable
fun <T> StaggeredVerticalList(
    items: List<T>,
    modifier: Modifier = Modifier,
    stableKey: (T) -> Any,              // 必填：业务稳定 id
    isFirstRender: Boolean,
    content: @Composable LazyItemScope.(T) -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    val seenKeys = remember { mutableSetOf<Any>() }

    LazyColumn(modifier = modifier) {
        itemsIndexed(items, key = { _, item -> stableKey(item) }) { index, item ->
            val key = stableKey(item)
            val shouldAnimate = isFirstRender && key !in seenKeys && !reducedMotion
            val alpha = remember(item) { Animatable(if (shouldAnimate) 0f else 1f) }
            val translationY = remember(item) { Animatable(if (shouldAnimate) 30f else 0f) }

            LaunchedEffect(item, reducedMotion) {
                if (shouldAnimate) {
                    delay(index * 40L)
                    launch { alpha.animateTo(1f, Motion.listItemEnter<Float>()) }
                    translationY.animateTo(0f, Motion.listItemEnter<Float>())
                    seenKeys.add(key)
                }
            }

            Box(
                modifier = Modifier
                    .animateItemPlacement()
                    .graphicsLayer {
                        this.alpha = alpha.value
                        this.translationY = translationY.value
                    }
            ) {
                content(item)
            }
        }
    }
}
```

### B4d 微交互（修正版 — 保留 Material ripple）

| 元素 | 反馈 |
|------|------|
| 列表项点击 | Material ripple (默认，不要替换) + 可选 `primaryContainer@8%` 背景切换 |
| 发送按钮 | 缩放 0.96x (pressFeedback spring) + Material ripple |
| 开关/checkbox | spring toggle + 颜色动画 |
| 头像点击 | 弹性缩放 0.95x → 回弹 1.0x + ripple |
| 长按弹出菜单 | menu 从手指位置展开 (spring) + 触觉 LongPress |

**不要全局移除 ripple**。ripple 是平台可访问性的一部分。缩放反馈是**附加**的，不是替换 ripple。

## B5 组件规范

### B5a TopAppBar — 带 `PageLevel` 参数

改造 `AppPageScaffold`，增加 `pageLevel` 和 `scrollBehavior` 参数，让规范由组件强制执行。

```kotlin
// PageLevel.kt
enum class PageLevel {
    PRIMARY,   // 聊天列表：无背景，headlineMedium，透明
    SECONDARY, // 设置分类页：透明背景，滚动后毛玻璃
    TERTIARY   // 详情编辑页：实体 surface 背景，标准 titleLarge
}

// 改造 AppPageScaffold
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    pageLevel: PageLevel = PageLevel.SECONDARY,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { true }
    )
    val topBarColors = when (pageLevel) {
        PageLevel.PRIMARY -> TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        )
        PageLevel.SECONDARY -> TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        )
        PageLevel.TERTIARY -> TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        )
    }

    // 必须把 nestedScrollConnection 传给最外层，否则 scrolledContainerColor 不会触发
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = when (pageLevel) {
                            PageLevel.PRIMARY -> MaterialTheme.typography.headlineMedium
                            PageLevel.SECONDARY,
                            PageLevel.TERTIARY -> MaterialTheme.typography.titleLarge
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    }
                },
                actions = actions,
                colors = topBarColors,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        content(innerPadding)
    }
}
```

**废弃**：`eyebrow`、`subtitle`、`showTopBar` 参数（从未正确实现，移除减少困惑）。

### B5b **ChatAvatar（新增）**

```kotlin
enum class AvatarSize(val dp: Dp) {
    SMALL(40.dp), MEDIUM(48.dp), LARGE(64.dp)
}

enum class StatusIndicator {
    ONLINE, TYPING, MUTED, NONE
}

@Composable
fun ChatAvatar(
    name: String,
    avatarUrl: String?,
    size: AvatarSize = AvatarSize.MEDIUM,
    status: StatusIndicator = StatusIndicator.NONE,
    onClick: (() -> Unit)? = null,
)
```

- 使用 `AsyncImage` (Coil) 加载头像（见 Priority 0 添加依赖）；或用项目现有 `AsyncBitmapLoader` 兜底
- 无头像时显示首字母 + 基于 name 哈希的柔和背景色
- 状态指示器：右下角圆点，尺寸按 AvatarSize 分级（SMALL=8dp, MEDIUM=10dp, LARGE=12dp），外圈 1.5dp `surface` 描边，带呼吸动画
- 点击状态：default ripple + `0.96x` spring 缩放

### B5c **对话列表项（新增）**

```
┌──────────┬────────────────────────────────┐
│          │  联系人/群名          时间戳    │
│  Avatar  │  最后一条消息预览   未读计数     │
│  (48dp)  │  状态图标                       │
└──────────┴────────────────────────────────┘
```

规范：
- Avatar 左对齐 48dp
- 标题: `titleMedium` + 右对齐 `bodySmall` 时间戳
- 预览: `bodyMedium` + 右对齐未读计数（`labelSmall` 填充 `primary`）
- 高度 64dp+

### B5d **SettingsGroup / SettingsRow — 替代 AppSectionCard**

新增组件，逐步替换现有 `AppSectionCard` / `AppSectionSurface`：

```kotlin
// SettingsGroup — 外层容器（非卡片，表面 containerLow）
@Composable
fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
)

// SettingsRow — 单行项
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
    onClick: (() -> Unit)? = null,
)
```

**视觉样式**：
```
┌──────────────────────────────────────────┐
│  Icon(24dp)  Title(16dp)        Chevron  │
│              Description(13dp)            │
├──────────────────────────────────────────┤
│  组外层 → surfaceContainerLow            │
│  组间间隔 → Spacing.lg (16dp)            │
│  项间分割 → HorizontalDivider            │
│  整行最小高度 52dp                        │
└──────────────────────────────────────────┘
```

`AppSectionCard` / `AppSectionSurface` **保留但标记废弃**，按 Round 2 逐屏迁移。

### B5e **BottomSheet**

```
  ┌──────────────────────────────────────┐
  ╱  ─── handle (6dp宽, 3dp高)           ╲  圆角 top 20dp
  │  标题                                  │
  │  ─────────────────────────────────     │
  │  Icon   Label                    >     │
  │  ...                                   │
  └──────────────────────────────────────┘
    背景: surfaceContainerHighest
    遮罩: scrim alpha 0.32f
```

- 使用 `ModalBottomSheet` + 定制 dragHandle
- 列表项全宽，icon + label + 可选 trailing
- 顶部 20dp 圆角（使用 `AppShapes.sheetTop`）

### B5f **空状态（替代 AppEmptyState，非卡片容器）**

```kotlin
@Composable
fun AppEmptyState(
    title: String,
    description: String = "",
    modifier: Modifier = Modifier,
    illustration: @Composable (() -> Unit)? = null, // 插画 slot，非 emoji
    action: @Composable (() -> Unit)? = null,
)
```

- 废弃 `AppSectionCard` 作为空状态容器
- 居中布局，无卡片背景、无边距、无圆角
- 插画 slot 接收任意 composable（矢量线稿 prefer）

## B6 手势与交互

### B6a 侧滑

| 列表 | 左滑 | 右滑 |
|------|------|------|
| 对话列表 | 删除（红色背景 + delete icon） | 置顶/取消置顶 |
| 消息列表 | 回复 | — |
| 设置列表 | — | — |

### B6b 触觉反馈（修正版）

| 操作 | 反馈强度 | 实现 |
|------|---------|------|
| 按钮/列表项点击 | 无触觉（视觉 feedback 足够） | — |
| 长按进入编辑/多选 | `LongPress` | `performHapticFeedback(LongPress)` |
| 侧滑越过删除阈值 | `LongPress` | — |
| 开关/滑动 | 无触觉 | — |

**原因**：普通按钮点击用 LongPress 会让频繁操作变吵，且与 Android 平台惯例不符。

### B6c 键盘

- 输入框获焦 → 列表平滑滚到底部
- 键盘弹出 → 布局整体上移，不压缩
- 发送后键盘保持打开

---

## B7 排版

### B7a 字体

保持 `SansSerif`，新增：

```kotlin
val messagePreview = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    overflow = TextOverflow.Ellipsis,
)
```

### B7b 节奏

```
对话列表：
  联系人名称: titleMedium, SemiBold
  未读对话:   titleMedium, Bold
  消息预览:   bodyMedium, Normal
  时间戳:     bodySmall, Normal

设置列表：
  标题:       bodyLarge, Medium
  描述:       bodySmall, Normal
  分组标题:   labelLarge, SemiBold, primary

聊天气泡：
  用户消息:   bodyLarge, Normal, onSecondaryContainer
  AI 消息:    bodyLarge, Normal, onSurface
  Token 统计: labelSmall, Normal, onSurfaceVariant
```

---

## B8 迁移策略

### 先落基础 Token（必须最先完成，否则页面反复返工）

```
Priority 0 (基础设施 — 必须先完成，否则页面反复返工)
  Gradle:
    □ 新建 :design-system Android Library module
    □ app/build.gradle.kts        + implementation(project(":design-system"))
    □ feature-chat/build.gradle.kts + implementation(project(":design-system"))
    □ :design-system 添加 Compose Material3 + Animation Core 依赖
    □ libs.versions.toml 添加 coil-compose (或沿用 AsyncBitmapLoader)

  文件:
    □ settings.gradle.kts       + include(":design-system")
    □ :design-system/build.gradle.kts  → 按 B0 模板创建
    □ :design-system/Color.kt    → 替换为 B1 色板
    □ :design-system/Theme.kt    → 按 B1 双层结构，仅 ColorScheme + Shapes
    □ :design-system/Spacing.kt  → 新增文件，粘贴 B2
    □ :design-system/Motion.kt   → 新增文件，粘贴 B4a [模板]
    □ :design-system/DesignTokens.kt → ChatColors + AppColors + AppShapes + LocalReducedMotion
    □ :design-system/PageLevel.kt  → 新增文件，粘贴 B5a 枚举
    □ :design-system/DesignTouchTargets.kt → 新增文件，粘贴 A4 helper
    □ app/Theme.kt               → 精简：委托 DesignSystemTheme，保留 ProvideStrings + 系统栏
```

### 再迁移页面

| Round | 内容 | 文件 |
|-------|------|------|
| R1 (2d) | 动效迁移：位移动画从 tween → Motion.pageTransition/spring；透明度/遮罩保留 Motion.fadeTween；列表 stagger 入场；按钮缩放；reduce motion 分支 | 全局 SearchReplace + 新增 Motion.kt |
| R2 (3d) | 新增 SettingsGroup/Rows → 逐屏替换 AppSectionCard；新增 ChatAvatar → 替换头像；硬编码 padding 替换为 Spacing 常量；AppPageScaffold 集成 PageLevel | AppChrome.kt + 各 Screen |
| R3 (2d) | 侧滑操作、定制 BottomSheet、空状态重构、骨架屏 | 新增 SwipeToDismiss + BottomSheet |
| R4 (2d) | 新增/删除动画、下拉刷新自定义、深色模式验证 | 全局打磨 |

### 实现检查清单

```
□ Color.kt 无茶色遗留
□ 所有 padding 引用 Spacing 常量
□ shapes 使用 B3 标准值，无 22dp 圆角遗留
□ 页面位移 → spring；透明度/遮罩 → fadeTween；颜色过渡 → animateColorAsState 默认
□ 列表首次展示有 stagger 入场动画
□ reduce motion (animatorDurationScale=0) 跳过所有动画
□ AppPageScaffold 使用 PageLevel
□ AppSectionCard 被 SettingsGroup/Rows 替换进度 ≥80%
□ ModalBottomSheet 使用 AppShapes.sheetTop + handle
□ AppEmptyState 使用 illustration slot 且无卡片背景
□ ripple 保留，未全局禁用
□ 触觉反馈仅用于长按和破坏性操作
```

---

## B9 参考原则

1. **减少认知负荷**：每页一个核心操作，次要放进 bottom sheet
2. **一致性高于美观**：参数定下来不改
3. **性能就是 UX**：卡顿的动画不如没有
4. **80/20 原则**：动效 + 间距 + 头像 → 80% 感知提升

---

> 本设计系统为专有资产。
> Part B 中的代码片段为示意代码，需适配项目实际依赖和 Compose 版本后编译。
> Part A 作为设计判断依据，不应出现在 PR 的 check 清单中。

---

## Progression / Fix 记录

### 2026-05-10 — DESIGN_SYSTEM.md 落地

**Progression**

- 完成 `:design-system` Android Library 模块创建，并接入 `settings.gradle.kts`、`app`、`feature-chat`。
- 完成 Material 淡蓝色板、`DesignSystemTheme`、标准 `shapes`、`Spacing`、`Motion`、`PageLevel`、`DesignTouchTargets`、`ChatColors`、`AppColors`、`AppShapes`、`LocalReducedMotion`。
- `app` 主题已精简为语言与系统栏外壳，颜色、形状、排版委托给 `DesignSystemTheme`。
- 新增并接入 `ChatAvatar`、`ConversationListItem`、`SettingsRow`、`SettingsGroup`、`TChatModalBottomSheet`、`StaggeredVerticalList`。
- 对话抽屉列表迁移到 `ConversationListItem`，支持左滑删除、右滑置顶/取消置顶；Room 增加 `Chat.isPinned`、`chats.isPinned` 与 28→29 迁移。
- 主导航、设置页、服务商页的位移动画从 `tween` 迁移到 `Motion.pageTransition` spring；scrim/fade 使用 `Motion.fade`。
- 消息列表新增 stable key stagger 入场，并接入 `LocalReducedMotion`。
- 发送按钮、头像点击接入 spring 缩放反馈并保留 Material ripple。
- BottomSheet 入口统一迁移为 `TChatModalBottomSheet`，使用 top 20dp 圆角、定制 handle、0.32 scrim。
- 旧 `AppSectionCard` / `AppSectionSurface` 业务调用已迁移到 `SettingsGroupCard` / `SettingsSurface`，旧 API 仅在 `AppChrome.kt` 保留 deprecated 兼容定义。
- `AppEmptyState` 已移除卡片容器，改为居中布局并支持 `illustration` slot；保留 nullable `icon` 作为旧调用兼容入口。
- 完成检查：无茶色 `#205A56` 遗留；无业务侧 22/24/26/34dp 圆角遗留；业务侧 `ModalBottomSheet` 已统一走 `TChatModalBottomSheet`。

**Fix**

- 修复新增模块使用 Gradle alias 插件导致的 classpath unknown version 问题：`design-system` 改为与现有 library 模块一致的插件 id 声明。
- 修复 `TChatModalBottomSheet` content receiver 类型，适配 Material3 `ModalBottomSheet` 的 `ColumnScope`。
- 避免 `AppPageScaffold` 暴露 experimental `TopAppBarScrollBehavior` 到所有调用页：scroll behavior 改为组件内部创建，仍强制挂载 nested scroll。
- 修复新增 `ChatRepository.updateChatPinned` 后测试 fake repository 缺少实现的问题。

**Verification**

- `.\gradlew.bat :app:assembleDebug` — passed。
- `.\gradlew.bat testDebugUnitTest` — passed。
- `.\gradlew.bat lint` — passed。

**复核说明**

- `AppSectionCard` / `AppSectionSurface` 业务调用从基线 151 处降至 0 处，旧命名仅剩 `AppChrome.kt` 中 2 个 deprecated 兼容定义。
- `Spacing` 已落地并用于新设计系统组件与本轮重点迁移路径；遗留页面中个别局部布局数值仍作为屏幕级微调值保留，后续不应新增未归因的硬编码 spacing。
- `scrollBehavior` 按文档目标由 `AppPageScaffold` 强制执行，但未作为公开参数暴露，原因是 Material3 类型仍为 experimental，公开会要求所有业务页面 opt-in。

### 2026-05-10 — 手感修正：降低过度弹性

**Fix**

- 用户反馈页面与按钮手感过于弹性，已将 `Motion.pageTransition` 从 `DampingRatioMediumBouncy + StiffnessMedium` 调整为 `DampingRatioNoBouncy + StiffnessMediumLow`。
- 已将 `Motion.pressFeedback` 从 `DampingRatioMediumBouncy` 调整为 `DampingRatioNoBouncy`，保留快速缩放反馈但移除回弹 overshoot。

### 2026-05-10 — 设置页层级修正：压平嵌套 surface

**Fix**

- 用户反馈设置页层次感异常，已将 `SettingsSurface` 从较重的 `surfaceContainer` 调整为 `surfaceContainerLow`，并把描边透明度从 `0.26f` 降到 `0.14f`。
- `SettingsRow` 默认行背景改为透明，选中态从 `primaryContainer.copy(alpha = 0.32f)` 降为 `0.22f`，避免侧栏每行都像独立卡片。
- 设置分割线透明度下调：通用 `SettingsDivider` 为 `0.18f`，平板侧栏与 TTS 页内部为 `0.16f`。
- TTS 设置页的 `ListItem` 改为透明容器，引擎选择项仅在选中时显示轻量填充和 1dp 弱描边，移除未选中项白底卡片感。
- “关于 TTS 引擎”移除 `SettingsGroupCard` 内部二次 `SettingsSurface`，避免分组容器中再套卡片。

**Verification**

- `.\gradlew.bat :app:assembleDebug` — passed。
- `.\gradlew.bat :app:installDebug` — passed，已安装到 `OPD2404 - 15`。
- `.\gradlew.bat :app:lintDebug` — passed。
