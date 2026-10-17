# kool 引擎總覽（給 newton-cradle 改寫用）

> 查證時間：2026-10-18（UTC+8）。標記：**[已查證]** = 直接讀原始碼 / Maven Central / 官方 README 確認；**[推測]** = 依經驗或間接證據推論，實作時需驗證。
> 來源版本：
> - kool `main` @ `e79b10f`（2026-10-13 05:00 UTC+8），根 `build.gradle.kts` 版本 `0.20.0-SNAPSHOT`
> - kool tag `v0.19.0` @ `a8f0788`（Maven Central 最新正式版）
> - kool-templates @ 2026-01-18（UTC+8），使用 kool 0.19.0 / Kotlin 2.2.21

## 1. 是什麼
- Kotlin Multiplatform 的 Vulkan / WebGPU / OpenGL 遊戲引擎（README）。[已查證]
- 內建：PBR（metallic workflow）+ IBL、glTF 2.0、陰影、SSAO、Deferred + SSR、Bloom、tone mapping（ACES 預設 / Khronos PBR Neutral / Uncharted2 / Reinhard）、KSL 著色語言（轉 GLSL/WGSL）、自家 UI 框架 `ui2`（Compose 風格 API 但自己實作）、PhysX 物理、Box2D、音效。[已查證 README]

## 2. 平台 / 後端（README Platform Support）[已查證]
| 平台 | 後端 | 狀態 |
|---|---|---|
| Desktop JVM | Vulkan（預設 `RenderBackendVk`）| 完整 |
| Desktop JVM | OpenGL（`useOpenGlFallback = true`）| 完整（macOS 無 OpenGL）|
| Desktop JVM | WebGPU（`kool-backend-wgpu4k`，需 Java 22+）| 大致可用 |
| Browser (JS + WASM) | WebGPU | 完整 |
| Browser (JS + WASM) | WebGL2 | 完整（但部分功能/範例不支援）|
| Android | OpenGL ES 3 | 完整（預設關閉）|

**重要：wasm 支援只在 main 分支，正式版 0.19.0 沒有。**
- `main`：`buildSrc/src/main/kotlin/kool.lib-conventions.gradle.kts` 有 `jvm("desktop")`、`js { browser() }`、`wasmJs { browser() }`，`jvmToolchain(25)`。[已查證]
- `v0.19.0`：`kool-core/src` 只有 `androidMain/commonMain/desktopMain/jsMain`，沒有 wasmJsMain；`jvmToolchain(17)`。[已查證]
- Maven Central `de.fabmax.kool:kool-core:0.19.0` 的 `.module` variants：common、androidJvm、jvm(desktop)、js —— **無 wasmJs**。[已查證]
- README：WASM 因大量 JS interop 呼叫，多數情況比 JS 慢，**建議用 JS target**。[已查證]
- SNAPSHOT：`https://central.sonatype.com/repository/maven-snapshots/de/fabmax/kool/kool-core/0.20.0-SNAPSHOT/maven-metadata.xml` 回 404，目前查不到可用的已發佈 snapshot。[已查證 404] → 若要用 main 必須自行 `publishToMavenLocal` 或 Gradle composite build（`includeBuild`）。

## 3. 版本矩陣
| 項目 | kool 0.19.0（Central）| kool main（0.20.0-SNAPSHOT）| newton-cradle 現況 |
|---|---|---|---|
| Kotlin | 2.2.21（kotlin-stdlib requires 2.2.21）| 2.4.21 | 2.3.0 |
| Compose | 不依賴 | compose runtime 1.11.1（kool-core 用 runtime；`kool-compose-ui` 模組）| Compose MP 1.10.0 |
| Gradle | 9.2.1 建置 | wrapper 見 repo | 9.3.0 |
| JDK toolchain | 17 | 25 | 25 |
| physx-jni / physx-js-webidl | 2.7.1 | 2.8.0 | — |
| LWJGL | 3.3.6 | 3.4.3 | — |
| Web target | js | js + wasmJs | wasmJs |
| 預設 window subsystem | GLFW | SDL（`SdlWindowSubsystem`）| Compose Window |
來源：`gradle/libs.versions.toml`（兩個 tag）、Maven `.module`。[已查證]

Kotlin 2.3.0 專案使用 2.2.21 編出的 klib/jar：一般向前相容可行。[推測，需實際 build 驗證]
使用 main（Kotlin 2.4.21 編）時，消費端 Kotlin 版本需 ≥ 2.4（較新編譯器產出的 klib 舊編譯器通常不能讀）。[推測]

## 4. 依賴方式 [已查證 README / kool-templates]
```kotlin
repositories { mavenCentral() }
// commonMain
implementation("de.fabmax.kool:kool-core:0.19.0")
implementation("de.fabmax.kool:kool-physics:0.19.0")
```
- kool-basic-template（`kool-templates/kool-basic-template`）：只用 `kotlin("multiplatform")` 外掛；`jvm { binaries.executable { mainClass = "LauncherKt"; jvmArgs --add-opens=java.base/java.lang=ALL-UNNAMED, --enable-native-access=ALL-UNNAMED, macOS 加 -XstartOnFirstThread } }`；`js { binaries.executable(); browser { ... }; compilerOptions.target = "es2015" }`。
- Web 需要 `webpack.config.d/ignoreNodeDependencies.js`（`config.resolve = { fallback: { path: false, fs: false } }`，因 emscripten 產物引用 node 模組）。
- Template 把 `physx-js-webidl.wasm` 放在 `src/jsMain/resources/`，`index.html` 提供 `<canvas id="glCanvas" tabindex="0">`，JS main 用 `KoolApplication(KoolConfigJs(canvasName = "glCanvas", defaultAssetLoader = NativeAssetLoader(".")))`。
- Web 後端：`KoolConfigJs(renderBackend = RenderBackendWebGpu（預設）, useWebGlFallback = true, powerPreference, deviceScaleLimit = 3.0, ...)`；demo 網址的 `&backend=webgl` 是 kool-demo 自己處理的參數，本專案需自行讀 URL 參數決定 backend。[已查證欄位；URL 參數處理位置推測在 demo 端]
- Desktop main：`KoolApplication(KoolConfigJvm(renderBackend = RenderBackendVk, windowTitle = ..., windowSize = Vec2i(1600, 900))) { ... }`（`kool-demo/src/desktopMain/.../Main.kt`）。
- 物理要先 `Physics.loadPhysics()`（suspend，`kool-physics/src/commonMain/.../Physics.kt`）。
- `kool-demo` **不是**發佈的函式庫（Central 目錄只有 core / physics / physics-2d / editor / backend-wgpu4k）；DemoScene/DemoMenu 等 UI 框架要**複製改寫**（Apache-2.0，保留授權聲明）。[已查證]

## 5. 和 Compose Multiplatform 的關係 [已查證 + 推測]
- kool 自帶 UI（`de.fabmax.kool.modules.ui2`），在引擎的 GPU context 內繪製，**不是** Compose UI（Skia）。
- main 分支新增 `kool-compose-ui`：用 Compose **runtime**（+ compose-mini modifier）驅動 kool 自己的 UI 節點（`UiNodeApplier.kt`），不能直接使用 material3 / foundation composable。未發佈到 Central。[已查證]
- Desktop：kool 有 `SwingWindowSubsystem`（AWT Canvas，`platform/swing/CanvasWrapper.kt`，0.19.0 也有），理論上可放進 Compose Desktop `SwingPanel`，但 heavyweight 元件疊層、輸入焦點、Vulkan surface 等風險高。[推測]
- Web：Compose wasm 用自己的 canvas；kool 0.19.0 只有 JS target，與 Compose wasm 是不同 target，混用不實際。[推測]
- **結論**：以 kool 取代 Compose（含 UI 改用 kool ui2，正好就是 phys-joints 的視覺風格）。`composeApp` 模組要重構或另開新模組。

## 6. 中文字型（zh-TW UI）[已查證 API，細節推測]
- 預設字型是 MSDF Roboto（`DEFAULT_MSDF_FONT_INFO`），**不含中文字**。
- 方案 A：用 desktop 工具 `de.fabmax.kool.util.MsdfFontGenerator.writeMsdfFont(...)`（`kool-core/src/desktopMain/.../util/MsdfFontGenerator.kt`，0.19.0 也有）從 repo 現有 `notosanstc_regular.otf` 只產生 UI 會用到的字元，輸出 `.json + .png`，執行期 `MsdfFont(path)` 載入。
- 方案 B：`AtlasFont(family = ..., chars = "需要的字")`，平台字型光柵化（desktop AWT / web canvas）。
- `KoolConfigJs` / `KoolConfigJvm` 都有 `defaultFont: MsdfFontInfo`，可把中文 MSDF 設為全域預設字型；`KoolConfigJs.customTtfFonts: Map<String,String>` 可給 AtlasFont 用自訂 TTF（0.19.0 `jsMain/.../KoolConfigJs.kt`）。[已查證欄位]
- 需驗證 glyph 子集設定方式（`GeneratorGlyphSet`）。[推測]

## 7. 關鍵檔案路徑（kool main）
- 引擎：`kool-core/src/commonMain/kotlin/de/fabmax/kool/`
  - `scene/OrbitInputTransform.kt`（`defaultOrbitCamera(yaw, pitch)`、`setRotation`、`setZoom(z, min, max)`、`setTranslation`、`smoothingDecay`、`minHorizontalRot/maxHorizontalRot`）
  - `modules/ksl/KslPbrShader.kt`、`modules/ksl/KslLitShader.kt`（`enableSsao`、`imageBasedAmbientLight`、`reflectionMap`、`emission`）
  - `pipeline/ao/AoPipeline.kt`、`pipeline/deferred/`（舊，main 標 @Deprecated）、`pipeline/deferred2/Deferred2Pipeline.kt`（新，main only）
  - `pipeline/ibl/EnvironmentMap.kt`（`hdriEnvironment(path)`）
  - `modules/gltf/*`（`Assets.loadGltfModel(path, GltfLoadConfig)`）
  - `util/DebugOverlay.kt`、`util/ShadowMap.kt`（`SimpleShadowMap`）
- 物理：`kool-physics/src/commonMain/kotlin/de/fabmax/kool/physics/`（joints/、PhysicsWorld.kt、Material.kt、ContactListener.kt）
- Demo：`kool-demo/src/commonMain/kotlin/de/fabmax/kool/demo/`（`DemoScene.kt`、`DemoUi.kt`、`menu/`、`physics/joints/JointsDemo.kt`、`pathtracing/PathTracingDemo.kt`）
