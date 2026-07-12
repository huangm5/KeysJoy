# 二轮代码审查报告 — KeysJoy 三栏横屏布局

## 审查概况
- 审查时间: 2026-07-05 21:19
- 审查分支: working tree (修复后)
- 审查范围: 上一轮 3 Critical + 5 High 问题修复验证 + 新问题检查
- 审查文件: DividerView.kt, SelectDeviceActivity.kt, KeyboardPanelHelper.kt, ModifierState.kt, 布局 XML, 资源文件, drawables

## 审查结论

- [x] **通过** — 所有 Critical 和 High 问题已正确修复，未引入新问题

---

## 逐项验证

### 🔴 Critical 问题修复验证

#### [BLOCK-C1] DividerView 通过 leftChildIndex/rightChildIndex 区分 — ✅ 已修复

**验证内容:**
- `DividerView` 新增 `leftChildIndex` (默认 0) 和 `rightChildIndex` (默认 4) 属性
- `onTouchEvent` ACTION_DOWN/MOVE/UP 全部使用 `parent.getChildAt(leftChildIndex)` 和 `parent.getChildAt(rightChildIndex)` 替代硬编码索引
- `setupDivider()` 正确传入索引：
  - 左分隔条: `leftIdx=0, rightIdx=2` → 调整左键盘 ↔ 触控板 ✅
  - 右分隔条: `leftIdx=2, rightIdx=4` → 调整触控板 ↔ 右键盘 ✅
- 拖拽逻辑: 向右拖拽增加左侧面板权重、减少右侧面板权重，方向正确 ✅
- `MIN_RATIO=0.20` / `MAX_RATIO=0.50` 约束面板大小在合理范围 ✅

#### [BLOCK-C2] 面板比例独立保存 leftPanelRatio + rightPanelRatio — ✅ 已修复

**验证内容:**
- 新增 `leftPanelRatio` (默认 0.35f) 和 `rightPanelRatio` (默认 0.35f) 两个独立字段
- `onCreate` 从 SharedPreferences 分别加载两个比例 (使用 `R.string.panel_left_ratio` 和 `R.string.panel_right_ratio` 键) ✅
- `applyPanelRatios()` 独立计算三个面板权重:
  - `leftWeight = leftPanelRatio * 100`
  - `rightWeight = rightPanelRatio * 100`
  - `centerWeight = (100 - leftWeight - rightWeight).coerceAtLeast(20)` ✅
- `setupDivider` 回调根据 `savesLeftPanel` 标志正确保存对应比例:
  - 左分隔条拖拽完成 → `leftPanelRatio = leftRatio` ✅
  - 右分隔条拖拽完成 → `rightPanelRatio = rightRatio` ✅
- `savePanelRatios()` 持久化两个比例 ✅
- `DividerView.onDragComplete` 回调返回 `(leftRatio, rightRatio)` 供调用方使用 ✅

#### [BLOCK-C3] buildKeyboard 增加 removeAllViews() — ✅ 已修复

**验证内容:**
- `buildKeyboard()` 第一行: `gridLayout.removeAllViews()` ✅
- 紧接 `keyButtonMap.clear()` ✅
- 蓝牙重连场景: `onStart` → BT callback → `setupLandscapeKeyboard` → `buildKeyboard` 会先清空旧按钮再重建 ✅

---

### 🟠 High 问题修复验证

#### [MUST-FIX-H1] onKeyUp → super.onKeyUp — ✅ 已修复

**验证内容:**
- 第 532 行: `else return super.onKeyUp(keyCode, event)` ✅
- 第 534 行: `return super.onKeyUp(keyCode, event)` ✅
- 不再有 `super.onKeyDown` 在 `onKeyUp` 方法中的错误调用 ✅

#### [MUST-FIX-H2] 鼠标/导航按钮仅在 BT 回调中设置 — ✅ 已修复

**验证内容:**
- `buildLandscapeUI()` 中不再调用 `setupMouseButtons()` 和 `setupNavButtons()` ✅
- 添加了注释说明: "Mouse/nav buttons are wired after BT connects (in the BT callback)"
- BT 回调 (`getSender` lambda) 中正确调用:
  ```
  setupLandscapeTrackpad(hidd, device, mouseSender)
  setupLandscapeKeyboard(hidd, device)
  setupMouseButtons()
  setupNavButtons()
  ```
- `setupMouseButtons()` 依赖 `rMouseSender`（在 BT 回调中创建），时序正确 ✅
- `setupNavButtons()` 依赖 `BluetoothController.btHid` 和 `hostDevice`（在 BT 回调时已设置），时序正确 ✅

#### [MUST-FIX-H3] 硬编码→资源引用 — ✅ 已修复

**验证内容:**
- Portrait 布局: `android:text="@string/trackpad_hint"` (原为硬编码 "Trackpad") ✅
- Landscape 布局: 所有按钮文本引用 `@string/` 资源 ✅
  - 鼠标按钮: `@string/mouse_left`, `@string/mouse_middle`, `@string/mouse_right` ✅
  - 导航按钮: `@string/nav_back`, `@string/nav_home`, `@string/nav_overview` ✅
  - 触控板: `@string/trackpad_hint` ✅
- `key_button_hold.xml`: stroke 颜色引用 `@color/key_modifier_hold_border` (原为硬编码 `#5588FF`) ✅
- `colors.xml` 定义了 `key_modifier_hold_border` = `#5588FF` ✅
- `sendSingleKeyReport`: 使用 `KeyboardReport.ID` 常量 (原为硬编码 `8`) ✅
- `strings.xml` 定义了 `panel_left_ratio` 和 `panel_right_ratio` SharedPreferences 键 ✅

#### [MUST-FIX-H4] KeyboardPanelHelper 文档说明与 KeyboardSender 的分工 — ✅ 已修复

**验证内容:**
- KeyboardPanelHelper 类 KDoc 包含 "Relationship with KeyboardSender" 章节 ✅
- 明确说明两者分工:
  - KeyboardSender: 外部物理键盘事件 (Android KeyEvent → HID report) ✅
  - KeyboardPanelHelper: 屏幕虚拟键盘按钮 (raw HID usage codes → HID report) ✅
- 解释不冲突的三个原因:
  1. 不同输入源 ✅
  2. 修饰键状态独立 ✅
  3. 每份 HID report 完整指定 modifier byte ✅

#### [MUST-FIX-H5] keep_bt_alive flag 避免切换时断开蓝牙 — ✅ 已修复

**验证内容:**
- `PREF_KEEP_BT_ALIVE = "keep_bt_alive"` 常量定义 ✅
- `toggleLandscapeMode()`:
  - 设置 `PREF_KEEP_BT_ALIVE = true` 后调用 `recreate()` ✅
- `onStop()`:
  - 读取 `PREF_KEEP_BT_ALIVE` 标志 ✅
  - 如果为 true: 清除标志 + `return`（跳过 BT 断开逻辑）✅
  - 如果为 false: 执行正常断开流程 ✅
- `onStop()` 中新增 `keyboardHelper = null`（同时修复了上一轮 M6 问题）✅
- 生命周期时序: `toggleLandscapeMode` → `onPause` → `onStop` (跳过断开) → `onDestroy` → `onCreate` → `onStart` (重新注册 BT 回调) — 流程正确 ✅

---

### 附带修复的 Medium 问题

以下上一轮 Medium 问题在修复 Critical/High 时一并解决:

| 问题 | 状态 | 说明 |
|------|------|------|
| M3: sendSingleKeyReport 硬编码 report ID | ✅ 已修复 | 改用 `KeyboardReport.ID` 常量 |
| M4: key_button_hold.xml stroke 颜色硬编码 | ✅ 已修复 | 改用 `@color/key_modifier_hold_border` |
| M6: keyboardHelper 在 onStop 后未置空 | ✅ 已修复 | `onStop` 中 `keyboardHelper = null` |
| M7: key release 未检查 sendReport 返回值 | ✅ 已修复 | postDelayed 中增加返回值检查和 Log.e |

---

### HID 层完整性验证

- `git diff` + `git status` 确认以下文件**完全未改动** ✅:
  - BluetoothController.kt
  - DescriptorCollection.kt
  - senders/KeyboardSender.kt
  - senders/RelativeMouseSender.kt
  - senders/SensorSender.kt
  - senders/Sender.kt
  - reports/KeyboardReport.kt
  - reports/ScrollableTrackpadMouseReport.kt
  - reports/MouseReport.kt
  - listeners/ViewListener.kt
  - listeners/GestureDetectListener.kt
  - listeners/CompositeListener.kt
  - extraLibraries/CustomGestureDetector.kt

---

## 新问题检查

### 检查结果: 未引入新的 Critical 或 High 问题

**详细检查项:**

1. **DividerView 权重约束独立性** — 🟢 Low
   - ACTION_MOVE 中 `newLeftWeight` 和 `newRightWeight` 独立 coerceIn，如果一侧触及极限另一侧不补偿
   - 实际影响: LinearLayout 按权重归一化，视觉比例仍然合理，不影响功能
   - 不阻塞

2. **applyPanelRatios 边界情况** — 🟢 Low
   - 如果 `leftPanelRatio + rightPanelRatio > 0.80`，centerWeight 被 coerceAtLeast(20) 后总权重 > 100
   - LinearLayout 归一化权重，实际比例与保存值略有偏差
   - 仅在极端拖拽操作下出现，不阻塞

3. **keep_bt_alive 生命周期** — ✅ 无问题
   - 标志设置 → onStop 检查并清除 → 新实例 onStart 重新注册回调，时序正确
   - `BluetoothController.btHid` 未被清除，BT 连接保持

4. **修复代码与 HID 层交互** — ✅ 无问题
   - `KeyboardReport.ID = 8` 常量值与 DescriptorCollection 描述符一致
   - report 格式 `[modifier, reserved, keycode]` 与 HID 描述符匹配

---

## 总体评价

8 个问题（3 Critical + 5 High）全部正确修复，修复质量高。代码结构清晰，注释充分，修复方式合理而非 hack。修复过程中还附带解决了 4 个 Medium 级问题。未引入任何新的 Critical 或 High 问题。HID 层完全未改动。代码可以进入测试阶段。

## 审查清单
- [x] 功能正确性: ✅ 分隔条拖拽逻辑正确，面板比例独立持久化/恢复，GridLayout 不再重复添加
- [x] 代码可读性: ✅ 文档完善（KeyboardPanelHelper 分工说明），命名清晰，注释充分
- [x] 安全性: ✅ 无安全漏洞
- [x] 性能与健壮性: ✅ 蓝牙切换不断连，资源正确释放，错误处理完善
