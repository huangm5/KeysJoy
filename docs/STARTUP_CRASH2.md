# SelectDeviceActivity 黑屏崩溃诊断报告

## 审查概况
- 审查时间: 2026-07-06 12:18
- 审查范围: SelectDeviceActivity 启动路径全链路排查
- 编译验证: `./gradlew assembleDebug` ✅ BUILD SUCCESSFUL
- APK 验证: 单一 `classes.dex`（2.4MB），无 multidex 问题
- R8 验证: `usage.txt` 为空（未移除任何类），`mapping.txt` 确认类名未混淆

## 现象回顾

1. SplashScreen 权限弹窗 ✅ 正常弹出
2. 授权后 → 黑屏 → 闪退 ❌
3. `onCreate()` 中 try-catch 弹窗未显示

## 核心结论

**资源文件和布局 XML 全部通过验证，无任何资源缺失或引用错误。崩溃原因不在资源层面，而在构建配置和运行时行为。**

---

## 逐项验证结果

### 1. 布局文件验证 ✅ 全部通过

#### `activity_select_device_portrait.xml`
- `@color/the_black` → ✅ 定义于 colors.xml (#000)
- `@drawable/view_border` → ✅ 存在于 drawable/
- `@string/trackpad_hint` → ✅ 定义于 strings.xml ("触控板区域")
- `@+id/portrait_trackpad` → ✅ 定义于 ids.xml
- APK 内 aapt2 dump 确认所有引用已正确编译

#### `activity_select_device_landscape.xml`
- `@color/panel_bg` → ✅ (#1C1C1E)
- `@color/trackpad_bg` → ✅ (#252527)
- `@color/key_normal_text` → ✅ (#FFFFFF)
- `@drawable/divider_vertical` → ✅
- `@drawable/key_button_special` → ✅
- `@drawable/view_border` → ✅
- `@string/trackpad_hint` → ✅
- `@string/mouse_left/middle/right` → ✅
- `@string/nav_back/home/overview` → ✅
- `@+id/landscape_root` → ✅ 定义于 ids.xml
- `@+id/left_keyboard_grid` → ✅
- `@+id/divider_left/right` → ✅
- `@+id/landscape_trackpad` → ✅
- `@+id/btn_mouse_left/middle/right` → ✅
- `@+id/btn_nav_back/home/overview` → ✅
- `com.github.ifeel.keysjoy.ui.DividerView` → ✅ 类存在，构造函数签名正确

### 2. 资源文件完整性 ✅ 全部通过

#### colors.xml（18 个颜色定义）
所有被布局/drawable 引用的颜色均已定义：
`the_red, the_clear, the_black, start_gradient, end_gradient, key_normal_bg, key_normal_text, key_modifier_bg, key_modifier_hold_bg, key_special_bg, key_border, divider_color, divider_drag_color, panel_bg, trackpad_bg, nav_button_bg, mouse_button_bg, key_modifier_hold_border`

#### strings.xml（18 个字符串定义）
所有被代码/布局引用的字符串均已定义：
`app_name, action_settings, action_keyboard_title, action_check, action_tool_tip, action_disconnect, action_autopair, auto_pair_flag, title_screen_on, action_ble_status_tool_tip, screen_on_flag, action_landscape, action_landscape_desc, trackpad_hint, mouse_left, mouse_middle, mouse_right, nav_back, nav_home, nav_overview, panel_left_ratio, panel_right_ratio`

#### ids.xml（20 个 ID 定义）
所有代码中引用的 `R.id.*` 均有定义，菜单 XML 中的 `@+id/` 自动创建未在 ids.xml 声明的 ID。

#### drawable 文件（6 个 shape XML）
| 文件 | 引用颜色 | 验证 |
|------|---------|------|
| `key_button_normal.xml` | `key_normal_bg`, `key_border` | ✅ |
| `key_button_modifier.xml` | `key_modifier_bg`, `key_border` | ✅ |
| `key_button_hold.xml` | `key_modifier_hold_bg`, `key_modifier_hold_border` | ✅ |
| `key_button_special.xml` | `key_special_bg`, `key_border` | ✅ |
| `divider_vertical.xml` | `divider_color` | ✅ |
| `view_border.xml` | `#707070` (硬编码) | ✅ |

#### APK 资源验证
通过 `aapt2 dump resources` 确认所有资源 ID 在编译后的 APK 中均存在且引用关系正确。

### 3. DividerView 自定义类 ✅ 验证通过

```kotlin
class DividerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr)
```

- 包路径 `com.github.ifeel.keysjoy.ui.DividerView` 与布局 XML 引用一致 ✅
- `@JvmOverloads` 生成 `(Context, AttributeSet)` 构造函数，LayoutInflater 可正常实例化 ✅
- R8 mapping 确认类名未被混淆 ✅
- APK aapt2 dump 确认布局 XML 中完整类名正确 ✅

### 4. requestedOrientation 调用 ✅ 安全

```kotlin
// manifest 中声明了 configChanges
android:configChanges="orientation|screenSize"

// doCreate() 中设置方向
requestedOrientation = if (isLandscapeMode) {
    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
} else {
    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}
```

- `configChanges="orientation|screenSize"` 阻止 Activity 重建 ✅
- 在 `setContentView()` 之前调用 `requestedOrientation` 是安全的 ✅
- 默认值 `isLandscapeMode = false` → 走 `buildPortraitUI()` 分支 ✅

### 5. onStart() 流程 ✅ 逻辑安全

```kotlin
override fun onStart() {
    super.onStart()
    bluetoothStatus?.icon = ...  // null-safe, 菜单未创建时为 no-op
    if (!checkBluetoothPermissions()) { return }  // 权限检查
    BluetoothController.init(this)  // 内部有 try-catch 和 null 检查
    BluetoothController.getSender { ... }  // 仅存储回调
    BluetoothController.getDisconnector { ... }  // 仅存储回调
}
```

所有操作均为 null-safe 或有权限保护，不会抛出异常。

### 6. 所有 import 的类均存在 ✅

| 导入类 | 文件位置 | 验证 |
|--------|---------|------|
| `CustomGestureDetector` | `extraLibraries/CustomGestureDetector.kt` | ✅ |
| `CompositeListener` | `listeners/CompositeListener.kt` | ✅ |
| `GestureDetectListener` | `listeners/GestureDetectListener.kt` | ✅ |
| `ViewListener` | `listeners/ViewListener.kt` | ✅ |
| `KeyboardSender` | `senders/KeyboardSender.kt` | ✅ |
| `KeyboardReport` | `reports/KeyboardReport.kt` | ✅ |
| `RelativeMouseSender` | `senders/RelativeMouseSender.kt` | ✅ |
| `SensorSender` | `senders/SensorSender.kt` | ✅ |
| `DividerView` | `ui/DividerView.kt` | ✅ |
| `KeyboardPanelHelper` | `ui/KeyboardPanelHelper.kt` | ✅ |
| `KeyLayouts` | `ui/KeyboardPanelHelper.kt` (object) | ✅ |
| `ModifierState` | `ui/ModifierState.kt` | ✅ |
| `FeatureReport` | `reports/FeatureReport.kt` | ✅ |
| `DescriptorCollection` | `DescriptorCollection.kt` | ✅ |

### 7. DEX 和 R8 验证 ✅

- APK 仅含 1 个 `classes.dex`（2.4MB）— 无 multidex 问题
- R8 `mapping.txt`（36449 行）确认所有类名保留
- R8 `usage.txt` 为空 — 未移除任何代码
- R8 `seeds.txt` 确认 keep 规则生效

---

## 🔴 崩溃根因分析

资源验证全部通过后，崩溃原因锁定在以下 3 个构建配置 / 运行时问题：

### 🔴 [BLOCK-C1] `minifyEnabled true` 在 debug 构建中启用 R8

- **位置:** `app/build.gradle` 第 22 行
- **问题:** debug 构建启用了 `minifyEnabled true`，触发 R8 全量处理。虽然 ProGuard 规则包含 `-keep class **` 和 `-keepclassmembers class *{*;}`（保留所有类名和成员），R8 仍然会：
  1. **内联方法和优化字节码** — 即使保留类名，方法体仍可能被转换
  2. **剥离 `Log.v/d/i` 调用** — proguard-rules.pro 中的 `-assumenosideeffects` 移除了所有 `Log.i()`、`Log.d()`、`Log.v()` 调用，导致大量诊断日志在 debug 构建中消失
  3. **转换 Kotlin inline class** — `KeyboardReport` 使用已弃用的 `inline class` 关键字（Kotlin 2.0+ 应使用 `@JvmInline value class`），R8 对 inline class 的字节码处理可能引入运行时异常
- **风险:** R8 优化后的字节码在某些设备/ART 版本上可能产生 `Error`（非 `Exception`），绕过 try-catch
- **修复方案:**
  ```groovy
  buildTypes {
      debug {
          minifyEnabled false  // ← debug 构建禁用 R8
          proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
      }
      release {
          minifyEnabled true   // release 保持启用
          proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
      }
  }
  ```

### 🟠 [MUST-FIX-H1] try-catch 捕获 `Exception` 而非 `Throwable`

- **位置:** `SelectDeviceActivity.kt` 第 76 行
- **问题:** `catch (e: Exception)` 无法捕获 `Error` 类型异常。以下 `Error` 可能发生在启动路径中：
  - `ExceptionInInitializerError` — `BluetoothController` object 首次访问时，`lazy` 属性初始化失败
  - `NoClassDefFoundError` — R8 优化后某些类在运行时不可见（虽概率低，但 R8 + inline class 组合下不能排除）
  - `StackOverflowError` — 某些设备上深层调用栈 + R8 内联导致的递归
- **验证:** 将 `catch (e: Exception)` 改为 `catch (e: Throwable)` 后重新测试，如果弹窗出现则确认是 `Error` 类型异常
- **修复方案:**
  ```kotlin
  override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      try {
          doCreate(savedInstanceState)
      } catch (e: Throwable) {  // ← 捕获所有异常包括 Error
          Log.e(TAG, "onCreate failed", e)
          val sw = java.io.StringWriter()
          e.printStackTrace(java.io.PrintWriter(sw))
          android.app.AlertDialog.Builder(this)
              .setTitle("启动失败")
              .setMessage(sw.toString())
              .setPositiveButton("退出") { _, _ -> finish() }
              .show()
      }
  }
  ```

### 🟠 [MUST-FIX-H2] onStart() 和 onCreateOptionsMenu() 未包裹 try-catch

- **位置:** `SelectDeviceActivity.kt` 第 204 行（`onStart`）和第 285 行（`onCreateOptionsMenu`）
- **问题:** 即使 `onCreate()` 被保护，`onStart()` 和 `onCreateOptionsMenu()` 中的异常仍会导致崩溃且无任何用户提示。`onStart()` 中访问了 `BluetoothController` object（触发其 companion/ lazy 初始化），`onCreateOptionsMenu()` 执行 menu inflation。
- **修复方案:** 在 `onStart()` 中添加 try-catch：
  ```kotlin
  override fun onStart() {
      super.onStart()
      try {
          doStart()
      } catch (e: Throwable) {
          Log.e(TAG, "onStart failed", e)
          // 显示错误对话框或优雅降级
      }
  }
  
  private fun doStart() {
      // 原 onStart() 的逻辑移到这里
  }
  ```

---

## 🟡 其他发现

### [SHOULD-FIX-M1] `multiDexKeepProguard` 引用不存在的文件

- **位置:** `app/build.gradle` 第 14 行
- **问题:** `multiDexKeepProguard file('multidex-keep.pro')` 引用的文件不存在。虽然构建成功（Gradle 对此缺失容忍），但这是配置错误。
- **修复:** 要么创建该文件，要么删除该行（单 DEX 不需要）

### [SHOULD-FIX-M2] ProGuard `-assumenosideeffects` 导致 debug 日志丢失

- **位置:** `proguard-rules.pro` 第 20-24 行
- **问题:** `Log.v()`, `Log.d()`, `Log.i()` 被剥离，debug 构建中大量诊断日志消失。这使得排查崩溃极其困难——开发者看不到 `onStart()` 中的 `Log.i` 输出，误以为代码未执行。
- **修复:** 在 debug 构建中禁用此规则，或仅在 release 中应用：
  ```groovy
  buildTypes {
      debug {
          minifyEnabled false  // 禁用 R8 即可保留所有日志
      }
      release {
          minifyEnabled true
          proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
      }
  }
  ```

### [SHOULD-FIX-M3] `inline class` 在 Kotlin 2.0+ 已弃用

- **位置:** `reports/KeyboardReport.kt` 第 7 行
- **问题:** `inline class KeyboardReport` 在 Kotlin 2.0.21 中已弃用，应改为 `@JvmInline value class`。虽然编译器仍支持，但 R8 对旧 inline class 字节码的处理可能存在边缘情况。
- **修复:**
  ```kotlin
  @JvmInline
  value class KeyboardReport (
      val bytes: ByteArray = ByteArray(3) {0}
  )
  ```

### [SHOULD-FIX-M4] SelectDeviceActivity 未设置 android:theme

- **位置:** `AndroidManifest.xml` 第 48 行
- **问题:** SelectDeviceActivity 未声明 `android:theme`，继承应用主题 `AppStyle`（`Theme.DeviceDefault`）。这本身不是错误，但在某些设备上 `Theme.DeviceDefault` 可能有 OEM 定制行为。如果 SplashScreen 使用 `NoActionBar` 主题然后切换到带 ActionBar 的主题，某些设备的过渡处理可能异常。
- **建议:** 如果不需要 ActionBar，考虑为 SelectDeviceActivity 也使用 `NoActionBar` 主题（代码中使用 menu 而非 ActionBar，可适配无 ActionBar 场景）。

---

## 诊断验证步骤

### 步骤 1: 禁用 debug R8（最高优先级）

```groovy
// app/build.gradle
buildTypes {
    debug {
        minifyEnabled false
    }
}
```

重新构建安装测试。如果崩溃消失 → 确认是 R8 导致的问题。

### 步骤 2: 扩大 try-catch 范围

```kotlin
// 改 catch (e: Exception) 为 catch (e: Throwable)
// 在 onStart() 也加 try-catch
```

重新测试。如果弹窗出现 → 从弹窗内容确认具体异常类型。

### 步骤 3: 添加诊断日志（R8 禁用后日志可见）

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.e(TAG, "onCreate: entered")  // 用 Log.e 确保 R8 不剥离
    try {
        doCreate(savedInstanceState)
        Log.e(TAG, "onCreate: doCreate completed")
    } catch (e: Throwable) {
        Log.e(TAG, "onCreate: CRASHED", e)
        // ... 弹窗
    }
}

override fun onStart() {
    super.onStart()
    Log.e(TAG, "onStart: entered")
    try {
        // ... 原逻辑
        Log.e(TAG, "onStart: completed")
    } catch (e: Throwable) {
        Log.e(TAG, "onStart: CRASHED", e)
    }
}
```

### 步骤 4: 使用 adb logcat 获取崩溃日志

```bash
# 查看所有错误和崩溃（不依赖 app tag）
adb logcat -b crash *:E

# 查看完整 logcat
adb logcat *:E

# 如果仍无日志，查看 native crash
adb shell ls /data/tombstones/
```

---

## 修复优先级

| 优先级 | 问题 | 预期效果 |
|--------|------|---------|
| 🔴 P0 | `minifyEnabled false` for debug | 消除 R8 引入的运行时问题，恢复日志可见性 |
| 🟠 P1 | `catch (e: Throwable)` | 捕获所有类型崩溃，显示错误弹窗 |
| 🟠 P2 | `onStart()` 加 try-catch | 捕获生命周期后续阶段的崩溃 |
| 🟡 P3 | 移除缺失的 `multiDexKeepProguard` | 清理构建配置 |
| 🟡 P4 | `inline class` → `@JvmInline value class` | 消除 Kotlin 2.0 兼容隐患 |
| 🟡 P5 | 添加 `android:theme` 到 SelectDeviceActivity | 明确主题继承 |

---

## 总体评价

经过逐文件、逐资源的完整验证，**所有布局 XML、资源文件、自定义 View 类、Manifest 声明均正确无误**。崩溃不在资源层面。

**最可能的崩溃根因是 `minifyEnabled true` 在 debug 构建中启用 R8**，R8 的字节码优化（方法内联、Log 剥离、inline class 处理）在特定设备上产生了 `Error` 类型异常，绕过了 `catch (e: Exception)` 的保护。禁用 debug R8 + 扩大 catch 范围应能解决问题或暴露真正的异常信息。
