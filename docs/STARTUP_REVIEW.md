# 启动崩溃诊断报告 — KeysJoy

## 审查概况
- 审查时间: 2026-07-06 11:51
- 审查分支: main (debug build)
- 变更规模: 全项目排查
- 编译验证: `./gradlew assembleDebug` ✅ BUILD SUCCESSFUL

## 现象回顾

> App 安装后打开，黑屏 0.5 秒然后闪退。logcat 里完全没有 App 的日志（连 Application.onCreate 中的 Log.e 都没执行）。

黑屏 = SplashStyle 的 `windowBackground` (@color/the_black = #000) 生效了，说明 starting window 成功创建。崩溃发生在 Application 类加载或实例化阶段，`onCreate()` 从未被执行。

---

## 问题统计

| 严重程度 | 数量 |
|---------|------|
| 🔴 Critical | 1 |
| 🟠 High | 2 |
| 🟡 Medium | 2 |
| 🟢 Low | 3 |

---

## 审查结论

**有条件通过** — 修复 Critical 和 High 级问题后可进入测试。

---

## 逐项审查

### 🔴 Critical

#### [BLOCK-C1] Application 类不在主 DEX 文件中

- **问题:** `com.github.ifeel.keysjoy.KeysJoy`（Application 类）被编译到 `classes5.dex`，而非 `classes.dex`。APK 共有 7 个 DEX 文件，Application 类位于第 5 个。
- **验证方法:** 对 APK 执行 `dexdump` 逐文件扫描，确认 `KeysJoy` 类仅存在于 `classes5.dex`。
- **风险:** 虽然在 API 28+ 上 ART 原生支持 multidex，但在某些设备的特定 ROM 版本上，Application 类不在主 DEX 中可能导致 `ClassNotFoundException` 或 native crash，且不会产生 Java 层 logcat 输出。这与用户描述的现象完全吻合：黑屏（starting window 创建成功）→ 闪退（类加载失败）→ 无 logcat（native crash 或类加载错误在 Java logger 初始化之前发生）。
- **根因:** 项目依赖了 `com.chibatching.kotpref:kotpref:2.6.0`（约 20 个类）和完整的 Kotlin stdlib，导致 `classes.dex` 被库类填满，应用自身类被挤到次要 DEX 文件。
- **修复方案:**
  1. **移除未使用的 Kotpref 依赖**（见 [MUST-FIX-H1]），可减少 1-2 个 DEX 文件
  2. **强制 Application 类进入主 DEX**：在 `app/build.gradle` 中添加 `multiDexKeepFile`
  3. 或者：在 `android.defaultConfig` 中添加 `multiDexEnabled true` 并创建 `app/multidex-config.txt` 文件，内容为：
     ```
     com.github.ifeel.keysjoy/KeysJoy.class
     com.github.ifeel.keysjoy/KeysJoy$Companion.class
     com.github.ifeel.keysjoy/SplashScreen.class
     com.github.ifeel.keysjoy/SplashScreen$Companion.class
     ```
  4. 在 `build.gradle` 中引用：
     ```groovy
     android {
         defaultConfig {
             multiDexEnabled true
             multiDexKeepFile file('multidex-config.txt')
         }
     }
     ```

---

### 🟠 High

#### [MUST-FIX-H1] Kotpref 2.6.0 未使用但作为运行时依赖

- **位置:** `app/build.gradle` 第 30 行
- **问题:** 项目声明了 `implementation 'com.chibatching.kotpref:kotpref:2.6.0'`，但全局搜索所有 `.kt` 文件，没有任何代码 import 或使用 Kotpref。这个依赖：
  - 发布于 2020 年，target SDK 27，针对 Kotlin 1.x 设计
  - 当前项目使用 Kotlin 2.0.21，存在潜在二进制不兼容风险
  - 占用 `classes.dex` 中约 20 个类的空间，是导致 Application 类被挤到 `classes5.dex` 的主要原因之一
- **修复建议:** 直接删除该依赖行：
  ```groovy
  // 删除这行：
  // implementation 'com.chibatching.kotpref:kotpref:2.6.0'
  ```

#### [MUST-FIX-H2] UncaughtExceptionHandler 内调用 SharedPreferences 存在递归崩溃风险

- **位置:** `KeysJoy.kt` 第 16-17 行
- **问题:** 全局 UncaughtExceptionHandler 内调用了 `getSharedPreferences("crash", MODE_PRIVATE).edit().putString("trace", sw.toString()).commit()`。如果崩溃本身发生在 SharedPreferences 访问期间（例如磁盘满、权限问题、文件损坏），则 handler 内的 SharedPreferences 调用会再次抛出异常，导致递归崩溃。虽然这不是当前启动崩溃的直接原因（因为 handler 在 `onCreate()` 中注册，而 `onCreate()` 从未执行），但这是一个潜在的应用稳定性隐患。
- **修复建议:** 用 try-catch 包裹 handler 内的 SharedPreferences 写入：
  ```kotlin
  Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
      try {
          val sw = StringWriter()
          throwable.printStackTrace(PrintWriter(sw))
          LAST_CRASH = sw.toString()
          getSharedPreferences("crash", MODE_PRIVATE)
              .edit().putString("trace", sw.toString()).commit()
      } catch (_: Throwable) {
          // 如果连写 crash log 都失败了，什么都不做，直接让默认 handler 处理
      }
      defaultHandler?.uncaughtException(Thread.currentThread(), throwable)
  }
  ```

---

### 🟡 Medium

#### [SHOULD-FIX-M1] `android:angle="45"` 属性用于 radial 渐变（无效属性）

- **位置:** `res/drawable/ic_launcher_background_ble.xml` 第 18 行
- **问题:** `<gradient>` 元素声明了 `android:type="radial"`，同时设置了 `android:angle="45"`。`android:angle` 仅对 linear 渐变有效，对 radial 渐变无意义。虽然 AOSP 实现会忽略此属性，但某些 OEM 定制 ROM 的资源渲染器可能对无效属性组合处理不当。
- **验证:** 在编译后的 APK 中确认该属性仍然存在（`aapt2 dump xmltree` 显示 `angle=45`）。
- **修复建议:** 删除 `android:angle="45"`：
  ```xml
  <gradient
      android:centerX="54"
      android:centerY="54"
      android:type="radial"
      android:gradientRadius="400"
      android:startColor="@color/start_gradient"
      android:endColor="@color/end_gradient">
  </gradient>
  ```

#### [SHOULD-FIX-M2] 建议添加诊断日志以确认类加载阶段

- **问题:** 当前 `KeysJoy` 类没有任何静态初始化日志，无法确认 Application 类是否被成功加载。
- **修复建议:** 在 companion object 初始化和 `onCreate()` 开头添加日志：
  ```kotlin
  companion object {
      @JvmStatic
      var LAST_CRASH: String? = null

      init {
          android.util.Log.e("KeysJoy", "companion object init — class loaded successfully")
      }
  }
  ```
  并在 `onCreate()` 开头添加：
  ```kotlin
  override fun onCreate() {
      super.onCreate()
      Log.e("KeysJoy", "onCreate() entered")
      // ...
  }
  ```
  部署后用 `adb logcat -s KeysJoy:E` 过滤查看。如果 companion init 日志出现但 onCreate 日志不出现，说明崩溃发生在构造函数或 `attachBaseContext` 阶段。如果两条日志都不出现，说明类加载本身失败。

---

### 🟢 Low

#### [NICE-L1] `ids.xml` 中存在未使用的 ID 声明

- **位置:** `res/values/ids.xml`
- **问题:** `checkModifierState`、`mouseView`、`textview` 等 ID 在代码中未被引用（菜单和布局使用的是 `check_modifier_state` 等不同名称）。
- **建议:** 清理未使用的 ID 声明，减少混淆。

#### [NICE-L2] `FOREGROUND_SERVICE` 权限声明但未使用

- **位置:** `AndroidManifest.xml` 第 17 行
- **问题:** 声明了 `android.permission.FOREGROUND_SERVICE` 权限，但项目中没有任何前台服务声明。在 API 34+ 上，前台服务还需要声明 `foregroundServiceType`。
- **建议:** 移除未使用的前台服务权限声明。

#### [NICE-L3] ProGuard 规则中 `-target 1.7` 与项目 Java 17 不一致

- **位置:** `app/proguard-rules.pro` 第 24 行
- **问题:** ProGuard 规则设置了 `-target 1.7`，但项目编译目标为 Java 17。在 debug 构建中（`minifyEnabled false`）不会触发 ProGuard，因此不影响当前调试。但在 release 构建中可能导致字节码版本冲突。
- **建议:** 将 `-target 1.7` 改为 `-target 17`，或直接删除此行让 ProGuard 使用默认目标。

---

## 逐文件审查清单

| 文件 | 检查结果 |
|------|---------|
| `AndroidManifest.xml` | ✅ Activity 注册正确，exported=true，theme 引用正确，Application 类名正确 |
| `res/values/styles.xml` | ✅ SplashStyle 和 AppStyle 定义正确，parent 引用有效 |
| `res/values/colors.xml` | ✅ 所有引用的颜色资源都存在 |
| `res/values/strings.xml` | ✅ 所有引用的字符串资源都存在（含中文，UTF-8 编码正常）|
| `res/layout/activity_select_device_portrait.xml` | ✅ 资源引用全部可解析 |
| `res/layout/activity_select_device_landscape.xml` | ✅ 资源引用全部可解析，DividerView 全限定类名正确 |
| `res/drawable/key_button_*.xml` | ✅ 4 个 shape drawable 语法正确 |
| `res/drawable/divider_vertical.xml` | ✅ 语法正确 |
| `res/drawable/view_border.xml` | ✅ 语法正确 |
| `res/drawable/ic_launcher_background_ble.xml` | ⚠️ radial 渐变使用了无效的 angle 属性 [M1] |
| `res/mipmap-anydpi-v26/ic_launcher_ble_round.xml` | ✅ adaptive icon 结构正确 |
| `KeysJoy.kt` | ⚠️ handler 内 SharedPreferences 调用无保护 [H2] |
| `SplashScreen.kt` | ✅ 逻辑正确，无启动风险 |
| `SelectDeviceActivity.kt` | ✅ 默认 portrait 模式，landscape 相关代码不影响启动 |
| `ui/DividerView.kt` | ✅ @JvmOverloads 生成正确的构造函数，不影响启动 |
| `app/build.gradle` | ⚠️ 未使用的 Kotpref 依赖 [H1] |
| APK DEX 分布 | 🔴 Application 类在 classes5.dex [C1] |

---

## 诊断结论与推荐修复步骤

### 最可能的崩溃原因

**Application 类 (`KeysJoy`) 位于 `classes5.dex` 而非 `classes.dex`，在特定设备上类加载失败导致进程在 Java logger 初始化前崩溃，产生无 logcat 的闪退。**

这一判断基于：
1. 黑屏 = starting window 成功创建（theme 的 windowBackground 生效）
2. 无 logcat = 崩溃发生在 Java 日志系统可用之前（类加载 native crash）
3. Application 类在 classes5.dex = 需要加载 5 个 DEX 文件才能找到 Application 类
4. 项目有 7 个 DEX 文件，部分由未使用的 Kotpref 库占据空间

### 推荐修复顺序

1. **[BLOCK-C1] + [MUST-FIX-H1]** 移除 Kotpref 依赖 + 配置 multiDexKeepFile 确保 Application 类在主 DEX
2. **[MUST-FIX-H2]** 加固 UncaughtExceptionHandler
3. **[SHOULD-FIX-M1]** 移除 radial 渐变的无效 angle 属性
4. **[SHOULD-FIX-M2]** 添加诊断日志
5. 重新构建、安装、测试

### 额外诊断步骤（如果修复后仍崩溃）

```bash
# 1. 查看 crash buffer（不依赖 app tag 过滤）
adb logcat -b crash *:E

# 2. 完全卸载后重装（清除损坏的 OAT/ODEX 缓存）
adb uninstall com.github.ifeel.keysjoy
adb install app/build/outputs/apk/debug/app-debug.apk

# 3. 检查 native crash tombstone
adb shell ls /data/tombstones/

# 4. 在模拟器上测试（排除设备特定问题）

# 5. 查看 logcat 全部错误（不过滤）
adb logcat *:E
```

---

## 总体评价

项目的资源文件、Manifest 声明、Activity 注册、主题配置均正确无误，编译完全通过。启动崩溃的最可能原因是 DEX 文件分布问题——Application 类被编译到第 5 个 DEX 文件中，在特定设备的 ART 实现上可能导致类加载失败。移除未使用的 Kotpref 依赖并配置 `multiDexKeepFile` 应能解决此问题。建议修复后优先在实机上验证。
