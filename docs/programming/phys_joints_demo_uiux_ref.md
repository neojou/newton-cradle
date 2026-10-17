# 「Physics - Joints」（Chain Drive）Demo 的 UI/UX 模式 —— 牛頓擺要複製的樣式

> 線上：https://kool-engine.github.io/live/demos/?demo=phys-joints （HTTP 200，2026-10-18 查）
> 原始碼（kool main @ e79b10f；v0.19.0 的 `JointsDemo.kt` 內容完全相同 [已查證 diff]）：
> - `kool-demo/src/commonMain/kotlin/de/fabmax/kool/demo/physics/joints/JointsDemo.kt`（655 行）
> - `.../physics/joints/GearChainMeshGen.kt`（401 行，「nice meshes」程序化幾何）
> - `.../demo/DemoScene.kt`（221）、`DemoUi.kt`（153）、`Demos.kt`（141）、`DemoLoader.kt`、`LoadingScreen.kt`、`Settings.kt`
> - `.../demo/menu/`：`DemoMenu.kt`、`DemoListContent.kt`、`SettingsContent.kt`、`DrawerButton.kt`、`NavDemoButton.kt`、`NavSettingsButton.kt`、`TitleBgRenderer.kt`
> 這些都在 `kool-demo`（**未發佈**），需複製到本專案並精簡（Apache-2.0，保留來源註記）。

## 1. 畫面結構 [已查證原始碼]
1. **主 3D 場景**（`mainScene`）：全螢幕。
2. **左側抽屜選單**（`DemoMenu.ui`，`UiScene("Demo Menu")`，`Colors.neon`）：
   - 左上角漢堡 / Drawer 按鈕；展開動畫 `animateFloatAsState` 0.2 s（easeOutQuart / easeInQuart）；點擊選單外自動收合。
   - 寬 `UiSizes.menuWidth = baseSize * 7.5`，`baseSize = sizes.gap * 4`。
   - 左邊直向 NavigationBar（寬 baseSize）：「Demo 列表」與「Settings」兩顆按鈕 + 旋轉 270° 的 "kool Demos" 字樣。
   - Settings 內容（`SettingsContent.kt`）：Language（ComboBox）、UI size（ComboBox）、Render scale（ComboBox）、Menu initially expanded / Debug overlay / Fullscreen / Hidden demos 開關，以及「Show demo menus」開關。
   - 設定以 `MutableStateSettings("koolDemo.xxx", default)` 存在 KeyValueStore（`Settings.kt`）。
3. **右上浮動「Demo 面板」**（`DemoScene.menuSurface { }`）：
   - `WindowSurface(mainScene, UiDockable(name, floatingX = baseSize*2, floatingY = baseSize*2, floatingWidth = menuWidth, floatingHeight = FitContent, floatingAlignmentX = AlignmentX.End))`，可拖曳。
   - 配色 `Colors.singleColorDark(accent, Color("101010d0"))`（半透明黑底 + 每個 demo 的 accent 色，accent 來自 `Demos.demoColors` 漸層）。
   - TitleBar：高 baseSize，`TitleBgRenderer` 漸層背景 + 發光標題字（`glowColor = black 50%`），右側圓形最小化箭頭（hover 變紅）。
   - 內容放 `ScrollArea` > `Column`。
4. **右下 DebugOverlay**（`de.fabmax.kool.util.DebugOverlay(Position.LOWER_RIGHT)`）：FPS、後端名稱等（點 `+` 展開）。
5. **LoadingScreen**：資源載入時顯示 "Loading <name>" + 目前資源名。

## 2. Joints 面板的控制項（要對照改成牛頓擺）[已查證]
| 原 Chain Drive | 元件 | 牛頓擺對應（建議）|
|---|---|---|
| Number of links（10–50 → ×2）| `MenuSlider2` | 球數 3–7（預設 5），變更即重建物理 |
| Motor strength 0–100 | `MenuSlider2` | 拉起角度 0–75°（或「初始拉起球數」1–4）|
| Motor speed 0–10 | `MenuSlider2` | 時間倍率 0.1–2.0（`simStepper.desiredTimeFactor`；原 demo 用 2f）|
| Reverse motor | `MenuRow { Text; Switch }` | 「左右交替拉起」開關 |
| Visualization：Draw nice meshes / Draw physics meshes | `RadioButton` | 「glTF 精緻模型 / 物理碰撞體」 |
| Draw joint infos | `LabeledSwitch` | 「顯示繩索/關節資訊」（LineMesh 畫關節軸與誤差顏色）|
| Statistics：joints / bodies / Physics step CPU time / Time factor | `MenuRow { Text(label) Text(value) }` | 同上 + 「總能量 %」「碰撞次數」 |
其他 helper：`MenuRow`、`MenuSlider`/`MenuSlider1`/`MenuSlider2`、`LabeledRadioButton`、`LabeledSwitch`、`TextScope.sectionTitleStyle()`、`labelStyle(Grow.Std)`（`DemoUi.kt`）。

## 3. 牛頓擺面板建議區段（zh-TW）
1. **操作**：重置、放下（release）、拉起左/右球、暫停 / 繼續。
2. **物理**：球數、拉起角度、時間倍率、物理模式（RadioButton：理想解析 / PhysX 混合 / PhysX 純剛體（實驗））、恢復係數（0.9–1.0）、球間隙（0–0.5 mm）。
3. **鏡頭視角**（新增；原 joints demo **只有** `defaultOrbitCamera(-20f, -20f)` + `setZoom(50.0, max = 200.0)`，沒有視角切換 [已查證]）：
   - 正面 Front、側面 Side、俯視 Top、產品照 3/4 Hero（低角度 ~15° 仰、45° 方位）、微距 Macro（對準中間球的反射）、自動環繞 Turntable、自由 Free orbit。
   - 實作：同一個 `OrbitInputTransform`，切換時呼叫 `setRotation(yaw, pitch)`、`setZoom(z)`、`setTranslation(...)`；它內部有 `ExponentialDecay` 動畫器 → 自然平滑過渡 [已查證 OrbitInputTransform.kt]。Turntable 在 `onUpdate` 遞增 yaw。
   - 快捷鍵 1–7 對應視角（推測需自行用 `KeyboardInput` 實作）。
4. **畫質**：渲染管線（Forward / Deferred+SSR）、SSAO 開關 + 半徑/強度、SSR 開關、陰影、曝光、Tone mapping、HDRI 選擇、「照片模式（漸進累積）」。
5. **視覺化**：精緻模型 / 物理體、關節資訊。
6. **統計**：FPS、物理步 CPU ms、時間倍率、能量、後端（WebGPU/WebGL/Vulkan）。
7. **About**：保留 `AppVersion`（名稱、版本 `DISPLAY`、`SUMMARY`）——可放在左側抽屜 Settings 底部，或 Demo 面板最下方按鈕開啟小視窗。

## 4. 場景/燈光樣式（原 demo）[已查證]
- 兩盞 SpotLight（暖白 50000、冷白 25000，45°）+ 兩張 `SimpleShadowMap`；`AoPipeline.createForward(this)`；IBL `colorful_studio_1k.rgbe.png`；`Skybox.cube(ibl.reflectionMap, 1.5f)`；地面 PBR 磁磚貼圖 + normal map。
- 每種剛體用 instanced `Mesh`（`InstanceLayouts.ModelMat`）每幀更新矩陣 → 牛頓擺 5 顆球可同理（或 glTF 節點直接設 transform）。
- 物理世界 `PhysicsWorld(mainScene)`；`onUpdate` 中依 `resetPhysics` 旗標重建。
