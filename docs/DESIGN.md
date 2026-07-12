# KeysJoy 三栏横屏触控板+键盘布局 — 设计文档

> 版本: 1.1 | 日期: 2026-07-05 | 作者: coder | 更新: 审查修复

---

## 1. 架构概览

### 1.1 现有架构（不变）
```
┌─────────────────────────────────────────────┐
│  BluetoothController (singleton)            │
│  ├─ btHid: BluetoothHidDevice               │
│  ├─ hostDevice: BluetoothDevice             │
│  ├─ getSender( (hidd, device) -> Unit )    │
│  └─ getDisconnector( () -> Unit )          │
├─────────────────────────────────────────────┤
│  HID Reports (不可变)                       │
│  ├─ KeyboardReport (3 bytes, reportID=8)   │
│  │   Byte0: 修饰键位掩码                    │
│  │   Byte1: 保留(0)                         │
│  │   Byte2: 按键码 (单一按键)               │
│  ├─ ScrollableTrackpadMouseReport           │
│  │   (7 bytes, reportID=4)                 │
│  │   Byte0: 左右键                         │
│  │   Byte1-2: dx (16bit LE)                │
│  │   Byte3-4: dy (16bit LE)                │
│  │   Byte5: vScroll                        │
│  │   Byte6: hScroll (AC Pan)               │
│  └─ DescriptorCollection.MOUSE_KEYBOARD_COMBO│
├─────────────────────────────────────────────┤
│  Senders (不可变)                           │
│  ├─ KeyboardSender.sendKeyboard()          │
│  ├─ RelativeMouseSender.sendTestClick()    │
│  ├─ RelativeMouseSender.sendScroll()       │
│  ├─ RelativeMouseSender.sendRightClick()   │
│  └─ RelativeMouseSender.sendLeftClickOn/Off│
├─────────────────────────────────────────────┤
│  Listeners (复用)                           │
│  ├─ ViewListener (单指光标移动)             │
│  ├─ CustomGestureDetector                   │
│  ├─ GestureDetectListener (单击/双击/滚动)  │
│  └─ CompositeListener                      │
└─────────────────────────────────────────────┘
```

### 1.2 新增UI层架构
```
SelectDeviceActivity
├─ 竖屏模式
│   └─ activity_select_device_portrait.xml
│       └─ 全屏触控板 (复用 ViewListener + GestureDetectListener)
├─ 横屏模式
│   └─ activity_select_device_landscape.xml
│       ├─ 左键盘面板 (KeyboardPanelHelper)
│       │   └─ 6×6 GridLayout — 字母/符号/修饰键
│       ├─ 可拖拽分隔条 #1 (DividerView)
│       ├─ 中触控板面板
│       │   ├─ 触控区域 (触摸事件 → ViewListener + GestureDetectListener)
│       │   ├─ 鼠标按键栏 (🖱L | 🖱M | 🖱R)
│       │   └─ Android 导航栏 (◀Back | ●Home | ██Overview)
│       ├─ 可拖拽分隔条 #2 (DividerView)
│       └─ 右键盘面板 (KeyboardPanelHelper)
│           └─ 6×6 GridLayout — 字母/符号/方向键
└─ Toolbar
    └─ "横向" 按钮 → 切换横屏/竖屏
```

---

## 2. 组件设计

### 2.1 ModifierState（修饰键状态管理）

```kotlin
// 数据结构：四个修饰键的锁定状态
data class ModifierState(
    var leftCtrl: Boolean = false,
    var leftShift: Boolean = false,
    var leftAlt: Boolean = false,
    var leftGui: Boolean = false  // ⌘ (Apple Command / Windows key)
)

// 转换为 HID Report Byte0 的位掩码
fun toModifierByte(): Int
```

**行为规则：**
- 单击修饰键 → 切换锁定状态（toggle on/off）
- 锁定状态开启 → 按键视觉高亮（背景色变化）
- 锁定状态下按普通键 → 键盘Report Byte0 携带修饰位
- 再次点击已锁定的修饰键 → 释放锁定/恢复普通
- 按普通键后→发送key down→短暂延迟→发送key up（所有键清零，但修饰位保留锁定状态）

### 2.2 KeyboardPanelHelper（键盘面板辅助类）

**职责：** 
- 根据键位定义列表，动态生成 6×6 GridLayout
- 管理普通键和修饰键的视觉状态
- 处理按键事件 → 组装 HID Report → 通过 KeyboardSender 发送

**键位定义数据结构：**
```kotlin
data class KeyDef(
    val label: String,        // 显示文本
    val hidCode: Int,         // USB HID Usage ID
    val isModifier: Boolean = false,  // 修饰键标记
    val colSpan: Int = 1       // 列跨度（Space=6）
)
```

### 2.3 DividerView（可拖拽分隔条）

**职责：**
- 宽度 ~8dp，显示为竖条
- 支持横向拖拽以调整相邻面板比例
- 通过 `leftChildIndex` 和 `rightChildIndex` 属性指定调整的相邻面板
- 拖拽完成后通过 `onDragComplete(leftRatio, rightRatio)` 回调报告两个相邻面板的比例
- 左分隔条：leftChildIndex=0 (左面板), rightChildIndex=2 (中间面板) → 回调中 leftRatio 存入 leftPanelRatio
- 右分隔条：leftChildIndex=2 (中间面板), rightChildIndex=4 (右面板) → 回调中 rightRatio 存入 rightPanelRatio
- 限制最小/最大比例（min 20%, max 50%）
- 两侧面板独立调整，不再对称假设

### 2.4 按键发送流程

```
用户点击按键
    │
    ├─ 是否为修饰键？
    │   ├─ YES → 切换 ModifierState → 更新UI高亮 → End
    │   └─ NO  → 继续
    │
    ├─ 是否为导航键（Back/Home/Overview）？
    │   ├─ YES → 构造 Consumer Report → 通过 hidDevice 直接发送 → End
    │   └─ NO  → 继续
    │
    └─ 普通键 →
         ① 构造 KeyboardReport
         ② Byte0 = ModifierState.toModifierByte()
         ③ Byte1 = 0
         ④ Byte2 = hidCode.toByte()
         ⑤ hidDevice.sendReport(reportID=8, bytes)
         ⑥ Handler.postDelayed(50ms):
            无修饰模式：报告清零（释放按键）→ sendReport
            修饰锁模式：保留修饰位，key清零 → sendReport
```

### 2.5 导航键处理

Android 导航键使用 HID Consumer Page (0x0C)：
- Back: AC_BACK = 0x0224
- Home: AC_HOME = 0x0223
- Overview: AC_HOME 重复（或使用 Keyboard Application 0x0065 作为回退）

Consumer Report 构造方式：通过 KeyboardReport 的报告ID(8)但使用 Consumer Page 的 usage。由于现有 HID 描述符中包含 Consumer Page（AC Pan 在鼠标报告中），导航键通过独立的 consumer report byte array 发送：
```
[modifier byte, 0x00, usageCodeLowByte] // 使用 report ID 8, usage page 0x0C
```
实际上，通过蓝牙HID发送 Consumer 报告需要一个独立的使用页面。由于当前描述符的限制，导航键采用以下回退策略：
- Back → HID ESC (0x29)
- Home → HID Keyboard Home (0x4A)  
- Overview → HID Keyboard Application (0x65)

> 注：未来改进可添加独立的 Consumer Control descriptor 来实现原生 Android 导航支持。

### 2.6 Bluetooth 横竖屏切换保持连接

横竖屏切换通过 `toggleLandscapeMode()` → `recreate()` 实现。为避免 `onStop()` 中断开 Bluetooth
连接导致重新配对，切换前设置 SharedPreferences flag `keep_bt_alive=true`，`onStop()` 检测到此
flag 时跳过 `unregisterApp()` 调用，保留 Bluetooth 连接。新 Activity 实例启动后正常恢复使用。

---

## 3. 数据流

### 3.1 蓝牙连接流（不变）
```
BluetoothController.init(ctx)
  └─ onServiceConnected() → registerApp(sdpRecord)
      └─ onAppStatusChanged() → autoPair if enabled
          └─ onConnectionStateChanged(CONNECTED)
              └─ deviceListener(hidd, device)
                  └─ SelectDeviceActivity: 创建 Sender 实例
                      └─ 注册 TouchListener
```

### 3.2 按键数据流（新增）
```
KeyboardPanelView (按钮点击)
  └─ KeyboardPanelHelper.onKeyPress(keyDef, modifierState)
      ├─ keyDef.isModifier == true
      │   └─ modifierState.toggle(keyDef) → updateButtonVisual()
      └─ keyDef.isModifier == false
          └─ buildKeyboardReport(modifierState, keyDef.hidCode)
              └─ keyboardSender.hidDevice.sendReport(reportID=8, reportBytes)
                  └─ postDelayed → send null report (release key)
```

### 3.3 触控板数据流（不变，触点区域变小）
```
TrackpadView.onTouchEvent()
  ├─ CompositeListener
  │   ├─ ViewListener.onTouch() → RelativeMouseSender → hidDevice.sendReport(4, ...)
  │   └─ GestureDetectListener.onTouchEvent() → GestureDetector
  │       ├─ onSingleTapConfirmed → sendTestClick()
  │       ├─ onDoubleTap → sendDoubleTapClick()
  │       ├─ onScroll → sendScroll()
  │       └─ ACTION_POINTER_DOWN(2指) → 右键识别
```

### 3.4 鼠标按键数据流（新增）
```
🖱L 按钮点击
  └─ mouseSender.sendTestClick() [原方法，单击=按下+释放]

🖱M 按钮点击  
  └─ mouseSender.sendTestClick() [中键用左键替代，因HID描述符只定义2键]

🖱R 按钮点击
  └─ mouseSender.sendRightClick() [原方法]
```

---

## 4. 键位映射表

### 4.1 左面板键位 (HID Usage IDs)

| Row | Col 1 | Col 2 | Col 3 | Col 4 | Col 5 | Col 6 |
|-----|-------|-------|-------|-------|-------|-------|
| 1 | \` (53) | 1 (30) | 2 (31) | 3 (32) | 4 (33) | 5 (34) |
| 2 | Tab (43) | Q (20) | W (26) | E (8) | R (21) | T (23) |
| 3 | CapsL (57) | A (4) | S (22) | D (7) | F (9) | G (10) |
| 4 | Shift* | Z (29) | X (27) | C (6) | V (25) | B (5) |
| 5 | Ctrl* | ⌘(Gui)* | Alt* | Esc (41) | BkSp (42) | Del (76) |
| 6 | Space (44) colspan=6 | - | - | - | - | - |

`*` = 修饰键（toggle模式）

### 4.2 右面板键位 (HID Usage IDs)

| Row | Col 1 | Col 2 | Col 3 | Col 4 | Col 5 | Col 6 |
|-----|-------|-------|-------|-------|-------|-------|
| 1 | 6 (35) | 7 (36) | 8 (37) | 9 (38) | 0 (39) | - (45) |
| 2 | Y (28) | U (24) | I (12) | O (18) | P (19) | BkSp (42) |
| 3 | H (11) | J (13) | K (14) | L (15) | ; (51) | ' (52) |
| 4 | N (17) | M (16) | [ (47) | ] (48) | / (56) | = (46) |
| 5 | Home (74) | End (77) | ↑ (82) | \ (49) | RShift* | Enter (40) |
| 6 | , (54) | ← (80) | ↓ (81) | → (79) | Space (44) | . (55) |

`*` = 修饰键（toggle模式）

---

## 5. 横屏面板比例设计

### 5.1 默认比例
```
左面板 35% | 触控板 30% | 右面板 35%
```

### 5.2 可拖拽调整
- 两个分隔条，每个宽度 8dp
- 拖拽范围: 最小 20%, 最大 50%
- 两端面板同步调整，中间触控板区自动填充剩余空间
- SharedPreferences 持久化: key=`panel_left_ratio` (默认 0.35), key=`panel_right_ratio` (默认 0.35)
- 左右面板比例独立保存和恢复，中间面板自动填充剩余空间

---

## 6. 文件变更清单

### 6.1 新增文件
| 文件 | 说明 |
|------|------|
| `docs/DESIGN.md` | 本文档 |
| `res/layout/activity_select_device_landscape.xml` | 横屏三栏布局 |
| `res/layout/keyboard_panel.xml` | 键盘面板通用容器 |
| `res/drawable/key_button_normal.xml` | 按键普通态背景 |
| `res/drawable/key_button_modifier.xml` | 修饰键按下态背景 |
| `res/drawable/key_button_hold.xml` | 修饰键锁定态背景 |
| `res/drawable/divider_vertical.xml` | 分隔条背景 |
| `com/.../ui/ModifierState.kt` | 修饰键状态管理 |
| `com/.../ui/KeyboardPanelHelper.kt` | 键盘面板辅助类 |
| `com/.../ui/DividerView.kt` | 可拖拽分隔条 |

### 6.2 修改文件
| 文件 | 变更 |
|------|------|
| `SelectDeviceActivity.kt` | 完全重写UI层，添加横竖屏切换逻辑 |
| `res/menu/select_device_activity_menu.xml` | 添加"横向"菜单项 |
| `res/values/strings.xml` | 添加新字符串 |
| `res/values/colors.xml` | 添加键盘主题色 |
| `res/values/ids.xml` | 添加新ID |

### 6.3 不变文件（红线）
| 文件 | 说明 |
|------|------|
| `BluetoothController.kt` | 蓝牙连接核心 |
| `DescriptorCollection.kt` | HID描述符 |
| `senders/*` | 键盘/鼠标/传感器 Sender |
| `reports/*` | HID报告数据结构 |
| `listeners/*` | 触控监听器 |
| `extraLibraries/*` | 手势检测 |
| `SplashScreen.kt` | 启动页 |
| `KeysJoy.kt` | Application类 |

---

## 7. 测试要点

### 7.1 功能测试
- [ ] 竖屏模式：全屏触控板，光标移动/点击/滚动正常
- [ ] 点击Toolbar"横向"按钮 → 切换到横屏三栏布局
- [ ] 横屏再点击"横向" → 切回竖屏
- [ ] 左右键盘面板所有按键点击后正常发送
- [ ] 修饰键（Shift/Ctrl/Alt/⌘）：点击锁定高亮，再点释放
- [ ] 修饰键锁定后按字母键 → 字母带修饰发送
- [ ] 拖动分隔条 → 面板比例实时变化
- [ ] 比例持久化：重启app后比例恢复
- [ ] 鼠标三键 🖱L/🖱M/🖱R 正常工作
- [ ] 导航栏按钮正常响应
- [ ] 横屏触控板区域手势正常工作
- [ ] 蓝牙连接/断开状态正常更新

### 7.2 边界测试
- [ ] 分隔条拖到最小/最大边界不崩溃
- [ ] 快速连续按键不丢失
- [ ] 蓝牙未连接时按键无崩溃
- [ ] 多次快速切换横竖屏无内存泄漏
- [ ] 配置变更（设备旋转）状态保持

---

## 8. 设计决策记录 (ADR)

### ADR-001: 使用 XML 布局替代 Anko DSL
**决策:** 横屏复杂布局使用 XML 布局文件
**原因:** 
- Anko DSL 已废弃，不适合复杂UI维护
- XML 布局有更好的IDE支持（预览、lint）
- 竖屏仍可用 Anko（兼容现有代码）
- 与项目现有 Anko 依赖共存

### ADR-002: 修饰键使用 toggle 而非 press-hold
**决策:** 修饰键采用点击锁定/解锁模式
**原因:**
- 移动端触屏无法实现物理键盘的"按住"操作
- Toggle模式契合触控板仅支持 modifier byte 1个byte 的约束
- 用户直观：点击高亮=锁定中，再点=释放

### ADR-003: 键盘使用 GridLayout + 程序化填充
**决策:** 键盘布局使用 XML 定义 GridLayout 容器，Kotlin 代码填充按钮
**原因:**
- 72+按键手动XML冗余且易错
- 程序化生成便于维护键位映射表
- 与数据驱动方式一致

### ADR-004: 导航键采用键盘键位回退
**决策:** Back/Home/Overview 使用标准键盘HID码，非Consumer Page
**原因:**
- 当前HID描述符不包含独立的Consumer Control collection
- 不修改描述符保持向后兼容
- 后期可通过添加Consumer descriptor实现原生支持
