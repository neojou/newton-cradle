# 遷移計畫：newton-cradle（Compose）→ kool 引擎

> 現況查證：newton-cradle @ `c06a221`（2026-08-16 04:25 UTC+8）。[已查證] / [推測] 標記同其他文件。

## 1. 現況盤點 [已查證]
- 單一模組 `:composeApp`；`kotlin { jvm("desktop"); wasmJs { outputModuleName = "NewtonCradle"; browser {}; binaries.executable() } }`，`jvmToolchain(25)`。**沒有 js target、沒有 Android。**
- 版本：Kotlin 2.3.0、Compose Multiplatform 1.10.0、Gradle 9.3.0（`gradle/libs.versions.toml`、wrapper）。
- `gradle.properties` 只有 jvmargs，**沒有 `app.version`**；repo **沒有 `configure.sh`**、**沒有 `docs/`**。
- `composeApp/build.gradle.kts`：`version = "0.1.0"`。
- `AppVersion.kt`（`com.neojou.newtoncradle`）：單一版本來源，`APP_NAME = "牛頓擺"`、`APP_NAME_EN`、`NAME = "0.1"`、`DISPLAY = "v$NAME"`、`SUMMARY`；KDoc 寫「Current release: **0.11**」與 `NAME = "0.1"` 不一致；引用的 `docs/VERSIONING.md` 不存在。
- 根 `build.gradle.kts` 註解提到「Coexists with the Vite/npm SPA (package.json)」，但 repo 內沒有 package.json（過時註解）。
- UI：`NewtonCradle.kt` 頂部選單 `MyTopMenuBar`（重置 / 放下 / About），About 對話框顯示 `AppVersion`；`ui/CradleViewport.kt`（357 行）負責每幀 `physics.step` + CPU 光追 + 拖曳（只抓端球）+ 鍵盤 + 品質自動調整（`QualityGovernor`，wasm 0.34、desktop 0.48）。`ui/HandOverlay.kt` 畫手的圖示。
- 渲染：`render/RayTracer.kt`（698 行解析式 CPU 光追，球/圓柱/盒/平面、鏡面反彈、ACES、底片顆粒）、`Scene.kt`、`Materials.kt`（檀木 + 伊達家紋金箔）、`StudioEnv.kt`（程序化攝影棚環境）、`Camera.kt`。
- 資源：`composeResources/files/clang.wav`、`kamon_*.jpg`、`sandalwood.jpg`、`font/notosanstc_regular.otf`；wasm `resources/index.html`、`clang.wav`。
- 平台碼：`CollisionSound.desktop/wasm.kt`、`ImageBitmapFactory`、`Texture` decode；`WasmMain.kt` 有 iOS/Safari 觸控與音訊解鎖 JS。
- 共用工具：`com.neojou.tools.MyLog`、`SystemSettings`、`ui/menu/MyTopMenuBar`。

## 2. 關鍵決策
| 決策 | 建議 | 理由 |
|---|---|---|
| kool 版本 | **0.19.0（Maven Central）**為主線 | 唯一正式版；main 無可用 snapshot（404）[已查證] |
| Web target | **wasmJs → js** | 0.19.0 無 wasmJs artifact；README 建議 JS（較快）[已查證] |
| 進階選項 | 之後可改用 kool main（composite build / mavenLocal）以取得 wasmJs + Deferred2 | 需 Kotlin 2.4.21、JDK 25、自建 kool [已查證版本]，維護成本高 |
| UI 框架 | Compose → **kool ui2**（複製 kool-demo 的 DemoScene/DemoMenu/DemoUi 樣式）| kool 不與 Compose UI 共存 [已查證/推測，見 overview §5] |
| 模組 | 新增 `:koolApp`（或 `:app`），`:composeApp` 先保留到功能對等，**刪除前先詢問** | 不破壞現有 build |
| 物理 | 預設「理想解析」（移植 CradlePhysics）+「PhysX 混合」+「PhysX 純剛體（實驗）」| 見 physics ref |
| 渲染 | Forward PBR + IBL + shadow + SSAO（全平台）；Deferred(+SSR) 可切換；「照片模式」漸進累積（移植 RayTracer 或 compute）| 見 rendering ref |
| 字型 | 從 `notosanstc_regular.otf` 產生 MSDF 子集 | 預設字型無中文 |
| 版本號 | `AppVersion.NAME` 0.1 → **0.2**（`composeApp`/`koolApp` gradle `version` 同步 0.2.0），修正 KDoc 的 0.11 不一致，新增 `docs/VERSIONING.md` | 引擎/UI 全面改寫屬重大變更；若 Neo 偏好以 1.0 標記重寫亦可 [建議] |

## 3. 目標模組結構（建議）
```
settings.gradle.kts            include(":composeApp", ":koolApp")
gradle/libs.versions.toml      + kool = "0.19.0"; libraries kool-core, kool-physics
koolApp/build.gradle.kts       kotlin("multiplatform")；jvm("desktop") executable + js(browser) executable（參照 kool-basic-template）
koolApp/webpack.config.d/ignoreNodeDependencies.js
koolApp/src/commonMain/kotlin/com/neojou/newtoncradle/
    AppVersion.kt              （搬移或共用，仍是單一版本來源）
    NewtonCradleApp.kt         （launch：Physics.loadPhysics()、scenes、DebugOverlay）
    demo/                      （精簡自 kool-demo：CradleScene : DemoScene 風格、DemoMenu、DemoUi helpers、Settings）
    sim/Cradle.kt, CradlePhysics.kt   （移植，保留解析模型）
    sim/physx/PhysxCradle.kt   （PhysX 剛體 + RevoluteJoint + 混合碰撞）
    render/CradleModel.kt      （glTF 綁定 + 程序化 fallback）
    render/Lighting.kt, CameraViews.kt, PhotoMode.kt
    audio/CollisionSound.kt    （kool AudioClip）
koolApp/src/commonMain/resources/assets/{models,hdri,textures,fonts,sounds}/
koolApp/src/desktopMain/kotlin/.../Main.kt     KoolApplication(KoolConfigJvm(...))
koolApp/src/jsMain/kotlin/.../Main.kt          KoolApplication(KoolConfigJs(canvasName = "glCanvas"))
koolApp/src/jsMain/resources/index.html, physx-js-webidl.wasm（依 template）
docs/programming/*.md, docs/VERSIONING.md
```
JVM 執行需 `--add-opens=java.base/java.lang=ALL-UNNAMED --enable-native-access=ALL-UNNAMED`（macOS 再加 `-XstartOnFirstThread`）[已查證 template]。

## 4. 分階段（目標：一次完成 Phase 1–4；做不完就以 Phase 為界停下仍可 build）
- **Phase 0 準備**：新增 `:koolApp` 空殼（desktop + js），`./gradlew :koolApp:runDesktop`[推測任務名] 與 `:koolApp:jsBrowserDevelopmentRun` 顯示 kool hello scene；`:composeApp` 照常 build。
- **Phase 1 場景 + 解析物理**：程序化幾何（球/框/底座/繩）+ Forward PBR + IBL（studio HDRI）+ 2 spot 陰影 + SSAO；移植 `CradlePhysics`；滑鼠拖端球（射線 ∩ 擺動平面，沿用 `Camera.intersectSwingPlane` 概念）；碰撞音效。
- **Phase 2 Demo UI**：左抽屜（Settings + About）、右上可拖曳面板（操作 / 物理 / 鏡頭視角 / 畫質 / 視覺化 / 統計）、DebugOverlay、LoadingScreen、zh-TW MSDF 字型；鏡頭 7 種預設視角平滑切換。
- **Phase 3 PhysX 模式**：RevoluteJoint 吊掛 + 球間解析碰撞（混合）、純剛體實驗模式、關節資訊 LineMesh、物理/精緻 mesh 切換。
- **Phase 4 glTF 產品級資產**：載入 `newton_cradle.glb`（節點命名規範），烘焙 AO，材質調校；Deferred+SSR 選項（0.19：舊 `DeferredPipeline`）；「照片模式」漸進累積。
- **Phase 5（選配）**：kool main（wasmJs、Deferred2、compute AO/TSAA）、compute 路徑追蹤、移除 `:composeApp`（**須先詢問**）。

## 5. 驗收標準
1. `./gradlew build` 全綠（含 `:composeApp`，直到確認移除）。
2. Desktop（Vulkan，OpenGL fallback）與 Web（WebGPU；以及 `?backend=webgl` 自訂參數或不支援 WebGPU 的瀏覽器走 WebGL2 fallback）都能啟動並互動；WebGL 下自動停用 compute 依賴功能且 UI 顯示原因。
3. 牛頓擺行為符合 physics ref §6。
4. UI 與 phys-joints demo 同構：左抽屜 + 右上標題列面板（可拖曳、可最小化）+ 右下 debug overlay；所有標籤 zh-TW 顯示正常。
5. About 顯示 `AppVersion.APP_NAME / APP_NAME_EN / DISPLAY / SUMMARY`，版本 0.2。
6. 產品照視角（Hero）截圖：鋼球可見 HDRI 反射、球間互相反射（SSR 開啟時）、接觸 AO、柔和陰影。
7. Web 首次載入資產 ≤ ~15 MB（HDRI 1k–2k、貼圖 1k、glb.gz）[建議值]。

## 6. 風險
| 風險 | 影響 | 緩解 |
|---|---|---|
| kool 0.19 vs Kotlin 2.3.0 相容 | build 失敗 | 先做 Phase 0 驗證；必要時降到 Kotlin 2.2.21 於 `:koolApp`（同一 build 不同 Kotlin 版本不可行 → 可能需全專案統一）[推測] |
| Compose 與 kool 同 build 外掛衝突 | 低 | 分模組；kool 模組不套 compose 外掛 |
| wasm → js 改變部署（輸出檔名、index.html）| 中 | 保留 composeApp wasm 產物直到新版上線 |
| PhysX 牛頓擺不正確 | 高 | 預設解析模式；混合模式 |
| WebGL 無 SSR/bloom/compute | 中 | 功能偵測 `KoolSystem.features.computeShaders`，降級 |
| 中文字型 MSDF 子集遺漏字 | 低 | 建置時從 l10n 字串自動收集字元 |
| kool-demo UI 程式碼複製的授權/維護 | 低 | Apache-2.0 註記；只拷需要的部分 |
| 「path tracing」期待落差 | 中 | 文件說明 kool 無 path tracer；提供照片模式 + 離線 Cycles |
| PhysX wasm 檔案載入（web）| 中 | 依 template 放 `physx-js-webidl.wasm` 與 webpack 設定 |
