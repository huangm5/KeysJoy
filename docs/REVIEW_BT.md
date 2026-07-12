# 代码审查报告 — KeysJoy 蓝牙权限闪退修复

## 审查概况
- 审查时间: 2026-07-06 10:32
- 审查分支: working tree (蓝牙权限修复)
- 变更规模: 3 个文件，约 120 行新增/修改
- 问题统计: Critical: 0 / High: 2 / Medium: 3 / Low: 3

## 审查结论
- [x] 有条件通过（有 High 级问题，修复后可进入测试）
- [ ] 需修复后重审
- [ ] 驳回

---

## 逐文件审查

### 文件: `BluetoothController.kt`

#### 🟠 High

**[MUST-FIX-H1]** 第 122 行 — `registerApp` 调用缺少 SecurityException 保护

- **问题:** `onServiceConnected()` 中 `btHid.registerApp(sdpRecord, null, qosOut, {it.run()}, this)` 直接调用，没有 try-catch。虽然进入此回调的前提是 `getProfileProxy` 已成功（受 `init()` 中的 try-catch 保护），但如果用户在 BT 服务连接过程中从系统设置撤销了 BLUETOOTH_CONNECT 权限，`registerApp` 仍会抛出 SecurityException。
- **风险:** 后台权限撤销场景下崩溃。虽然概率低，但这是 Android 12+ 真实存在的竞态：系统服务回调可能比权限撤销延迟到达。
- **修复建议:**
  ```kotlin
  try {
      btHid.registerApp(sdpRecord, null, qosOut, {it.run()}, this)
  } catch (e: SecurityException) {
      Log.e(TAG, "registerApp failed: permission denied", e)
  }
  ```

**[MUST-FIX-H2]** 第 175-193 行 — `onAppStatusChanged` 中的蓝牙 API 调用缺少 SecurityException 保护

- **问题:** `btHid?.getDevicesMatchingConnectionStates(...)` 和 `btHid?.connect(pluggedDevice)` 均未包裹 try-catch。这两个方法在 Android 12+ 需要 BLUETOOTH_CONNECT 权限。该回调由系统蓝牙服务触发，时机不可控——如果用户在 app 运行期间从系统设置撤销权限，回调触发时就会崩溃。
- **风险:** 运行时权限撤销导致崩溃。`onStart()` 的权限检查只在 Activity 生命周期切换时生效，无法保护异步系统回调。
- **修复建议:** 将 `onAppStatusChanged` 整个方法体包裹在 try-catch 中：
  ```kotlin
  override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
      super.onAppStatusChanged(pluggedDevice, registered)
      try {
          // 现有逻辑
      } catch (e: SecurityException) {
          Log.e(TAG, "onAppStatusChanged: BLUETOOTH_CONNECT permission denied", e)
      }
  }
  ```

#### 🟡 Medium

**[SHOULD-FIX-M1]** 第 143 行 — `onConnectionStateChanged` 中 `btHid!!` 非空断言

- **问题:** `deviceListener?.invoke(btHid!!, device)` 使用了 `!!` 强制非空。虽然逻辑上此回调触发时 `btHid` 应该已赋值（来自 `onServiceConnected`），但使用 `!!` 是脆弱写法。如果因任何异常原因 `btHid` 为 null（如服务断开后系统仍触发了连接状态回调），将 NPE 崩溃。
- **建议:** 使用安全调用替代：
  ```kotlin
  val hid = btHid
  if (hid != null && device != null) {
      hostDevice = device
      deviceListener?.invoke(hid, device)
  } else {
      Log.e(TAG, "onConnectionStateChanged: btHid or device is null")
  }
  ```

**[SHOULD-FIX-M2]** 第 35 行 — `onGetReport` 中 `replyReport` 调用缺少保护

- **问题:** `btHid?.replyReport(device, type, FeatureReport.ID, featureReport.bytes)` 使用了安全调用 `?.`，所以不会 NPE。但 `replyReport` 本身需要 BLUETOOTH_CONNECT 权限，异步回调场景下可能抛 SecurityException。
- **建议:** 包裹 try-catch，与 `onAppStatusChanged` 同理。

#### 🟢 Low

**[NICE-L1]** `btAdapter` 的 lazy 初始化与 `init()` 的空检查存在隐性耦合

- **问题:** `btAdapter` 是 `by lazy` 属性，首次访问触发 try-catch。`init()` 中 `val adapter = btAdapter` 触发 lazy 初始化。如果 `btAdapter` 返回 null（权限被拒），`init()` 正确地提前返回。但这个依赖关系是隐式的——未来如果有人在其他地方首次访问 `btAdapter`，可能不知道它可能为 null。
- **建议:** 在 `btAdapter` 的文档注释中补充 "首次访问可能返回 null（权限被拒时），调用方必须做空检查"。

---

### 文件: `SplashScreen.kt`

#### 🟡 Medium

**[SHOULD-FIX-M3]** 权限检查逻辑重复

- **问题:** `requestNeededPermissions()` 和 `allRequiredPermissionsGranted()` 包含完全相同的权限列表和检查逻辑。如果未来新增权限（如 `BLUETOOTH_ADVERTISE`），需要同时修改两处，容易遗漏。
- **建议:** 抽取一个共享方法：
  ```kotlin
  private fun requiredPermissions(): List<String> {
      val perms = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          perms.add(Manifest.permission.BLUETOOTH_CONNECT)
          perms.add(Manifest.permission.BLUETOOTH_SCAN)
      }
      return perms
  }

  private fun allRequiredPermissionsGranted(): Boolean =
      requiredPermissions().all {
          checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
      }
  ```

#### 🟢 Low

**[NICE-L2]** 缺少 `shouldShowRequestPermissionRationale` 处理

- **问题:** 用户首次拒绝权限后直接跳转到"去设置"对话框。Android 最佳实践建议在首次拒绝后先展示权限说明（rationale），再次请求。只有当用户勾选"不再询问"时才引导去系统设置。
- **建议:** 当前实现可用，但体验可优化。如果产品上接受"一次拒绝即引导设置"的交互，可保持现状。

**[NICE-L3]** 对话框文案是中文硬编码

- **问题:** `"需要蓝牙权限"` 和 `"此应用需要蓝牙和位置权限..."` 是中文硬编码字符串，未走 `strings.xml` 国际化。
- **建议:** 迁移到 `strings.xml`，保持与项目其他文案一致的国际化方式。

---

### 文件: `SelectDeviceActivity.kt`

#### 🟢 Low

**[NICE-L4]** `checkBluetoothPermissions()` 与 `SplashScreen` 中的权限检查逻辑完全相同

- **问题:** 同样的三权限检查代码在两个文件中各出现一次。虽然这种"纵深防御"式重复检查是正确的（`SplashScreen` 是入口门禁，`SelectDeviceActivity.onStart()` 是运行时防线），但逻辑可以提取到公共工具类。
- **建议:** 可提取到 `PermissionUtils.checkBluetoothPermissions(context: Context): Boolean`，两个 Activity 共用。

---

## 审查要点逐项回答

### 1. 是否所有蓝牙 API 调用都有权限保护？

**部分。** 主要路径已保护：
- `BluetoothAdapter.getDefaultAdapter()` — ✅ try-catch (lazy)
- `adapter.getProfileProxy()` — ✅ try-catch (init)
- `btAdapter?.setScanMode()` — ✅ try-catch (onServiceConnected)
- `SelectDeviceActivity.onStart()` — ✅ 前置 `checkBluetoothPermissions()` + try-catch 包裹 `init()`

**未保护的异步回调路径：**
- `btHid.registerApp()` — ❌ [H1]
- `btHid?.getDevicesMatchingConnectionStates()` — ❌ [H2]
- `btHid?.connect()` — ❌ [H2]
- `btHid?.replyReport()` — ❌ [M2]

这些回调由系统蓝牙服务异步触发，`onStart()` 的权限检查无法覆盖。

### 2. SplashScreen 权限流程是否正确？

**正确。** 流程完整：
1. `onCreate` → 检查权限 → 已授予则直接进主页
2. 未授予 → `requestPermissions` → 回调检查 `allRequiredPermissionsGranted()`
3. 全部授予 → 进主页
4. 任一未授予 → 显示对话框 → "去设置"或"退出"
5. 对话框不可取消（`setCancelable(false)`），防止用户绕过

### 3. 是否有遗漏的崩溃点？

**有，见 H1/H2/M1/M2。** 最关键的是 `onAppStatusChanged` 中的 `connect()` 和 `getDevicesMatchingConnectionStates()`——如果用户在 app 运行时从系统设置撤销权限，这些回调会崩溃。全局 `KeysJoy.kt` 的 UncaughtExceptionHandler 可作为最后防线，但不应依赖它。

### 4. 修复是否引入新问题？

**未引入逻辑回归。** `btAdapter` 改为可空类型后，所有调用点已适配（使用 `?.` 或空检查提前返回）。`btHid` 本身就是可空的，未受影响。

### 5. 用户体验：权限被拒后的提示是否友好？

**基本友好。** 对话框清楚说明了需要什么权限、去哪里授权。但有两点可改进：
- 首次拒绝即跳转设置，未给 rationale 二次请求的机会 [NICE-L2]
- 文案为中文硬编码，未走国际化 [NICE-L3]

### 6. 代码质量：try-catch 范围是否合理？

**合理但不够全面。** `init()` 和 `onServiceConnected()` 中的 try-catch 范围精确，没有过宽捕获。但异步回调（`onAppStatusChanged`、`onGetReport`）缺少保护。

---

## 总体评价

修复方向正确，三个核心问题（btAdapter 空安全、权限拒绝后仍启动主页、缺少前置权限检查）都得到了有效解决。`SplashScreen` 的权限流程设计完整，`SelectDeviceActivity.onStart()` 的纵深防御检查是亮点。主要待修复项是 `BluetoothController` 中异步回调路径的 SecurityException 保护缺失——这些路径无法被 Activity 层的权限检查覆盖，需要在回调内部加 try-catch。修复 H1/H2 后可进入测试。

## 审查清单
- [x] 功能正确性: 通过 — 修复有效解决了目标崩溃问题
- [x] 代码可读性: 通过 — 命名清晰，注释合理，代码结构整洁
- [x] 安全性: 有条件通过 — 主路径已保护，异步回调路径待补充
- [x] 性能与健壮性: 通过 — 无性能隐患，lazy 初始化合理
