# 渲染：PBR / IBL / SSR / AO / Path Tracing 參考

> [已查證] 原始碼確認；[推測] 待驗證。kool main @ e79b10f、v0.19.0 @ a8f0788。

## 1. 結論先講
- **kool 引擎本身沒有 path tracer API。** 只有一個 demo：`kool-demo/src/commonMain/kotlin/de/fabmax/kool/demo/pathtracing/PathTracingDemo.kt`（473 行，0.19.0 也有），是《Ray Tracing in One Weekend》的 **compute shader** 實作：只支援**球體**（`SphereStruct` storage buffer，最多 1024 顆）、Lambertian / Metal / Glass 材質、`maxBounces = 10`、漸進累積（`numSamples` 到 10000 停止）、景深。不支援三角網格/BVH/glTF。在 `Demos.kt` 標記 `NeedsComputeShaders`；README 標示 **WebGPU only**（web 端）。[已查證]
- SSR：有（Deferred 管線）。AO：有（Forward SSAO 全後端；Deferred2 用 compute AO）。[已查證]
- 「像產品照」的實際路線 = **PBR + HDRI IBL + 陰影 + SSAO +（WebGPU/Vulkan 時）SSR + ACES/PBR Neutral tone mapping**，外加可選的「照片模式」漸進路徑追蹤（自製，見 §5）。

## 2. 後端功能矩陣
`BackendFeatures.computeShaders`（[已查證] 各後端原始碼）：
| 後端 | computeShaders |
|---|---|
| Desktop Vulkan | `device.computeQueue == device.graphicsQueue`（一般 GPU 為 true）|
| Desktop OpenGL | true |
| Desktop wgpu4k | true |
| Web WebGPU（js / wasmJs）| true |
| Web WebGL2 | **false** |
| Android GLES3 | false |

功能可用性：
| 功能 | API | Vulkan/GL desktop | WebGPU | WebGL2 | 版本 |
|---|---|---|---|---|---|
| Forward PBR + IBL + shadow | `KslPbrShader { lighting { addShadowMaps; imageBasedAmbientLight(ibl.irradianceMap) }; reflectionMap = ibl.reflectionMap }` | ✔ | ✔ | ✔ | 0.19 / main [已查證] |
| Forward SSAO | `AoPipeline.createForward(scene)` → 有 compute 用 `ComputeAoPipeline`，否則 `ForwardAoPipeline`(legacy)；shader `enableSsao(ao.aoMap)` | ✔ | ✔ | ✔(legacy) | main 自動選擇 [已查證]；0.19 只有 legacy `createForward` [已查證] |
| 舊 Deferred + SSR + AO + Bloom | `DeferredPipeline(scene, DeferredPipelineConfig().apply { isWithScreenSpaceReflections = true; isWithAmbientOcclusion = true; useImageBasedLighting(env) })` | ✔ | ✔ | 可能 ✔ | 0.19 主力；main `@Deprecated("Use Deferred2Pipeline instead")` [已查證]。0.19 的 `ReflectionDemo`(ssr) 未標 NeedsComputeShaders → WebGL2 應可跑 [推測] |
| Deferred2（SSR + compute AO + TSAA 時間濾波）| `Deferred2Pipeline(content, scene, ibl, camera, lighting, shadowMapConfig, maxGlobalLights, renderScale, tsaa)`；`enableScreenSpaceReflections(numRays = 3)`；`aoPass.radius = AoRadius.absoluteRadius(..)`；輸出 `addNode(pipeline.defaultOutputQuad(null))`；`installBloomPass()` | ✔ | ✔ | ✘（用 compute pass）| **main only** [已查證]；WebGL 不可用 [推測，因使用 `ComputeAoPass`/`TemporalFilterPass` 等 compute pass] |
| Bloom | `BloomPass` | ✔ | ✔ | ✘ | README：WebGPU only [已查證] |
| Path tracing（demo）| 自寫 `KslComputeShader` | ✔ | ✔ | ✘ | demo only |
README 原文：Vehicle demo「deferred rendering with screen-space reflections (incl. artifacts) **WebGPU only**」；Deferred Shading demo「**WebGPU only**」；Pathtracing「**WebGPU only**」；Bloom「**WebGPU only**」。[已查證]

## 3. IBL / HDRI [已查證]
- `Assets.hdriEnvironment(path, brightness)` / DemoScene 的 `hdriImage(path)` → `EnvironmentMap(irradianceMap, reflectionMap)`；載入方式是 `loadTexture2d(path, RGBA, MipMapping.Off, nearest)` → **需 RGBE 編碼 PNG（`*.rgbe.png`）**，不是 `.hdr`（`pipeline/ibl/EnvironmentMap.kt`）。
- 轉檔：desktop 工具 `de.fabmax.kool.util.HdrImageConversion.convertHdrImageToRgbe(File)` → `BufferedImage` → `ImageIO.write(..., "png", ...)`（`kool-core/src/desktopMain/.../util/HdrImageConversion.kt`）。可做成 Gradle task / 一次性 JVM 小程式。
- 天空盒：`Skybox.cube(ibl.reflectionMap, 1.5f)`（產品照建議模糊背景或純色漸層 `hdriGradient(...)`，但仍用攝影棚 HDRI 打反射）。
- 建議 HDRI（CC0，Poly Haven，皆已用 api.polyhaven.com/info 查到）：`studio_small_09`、`brown_photostudio_02`、`photo_studio_01`。下載例：`https://dl.polyhaven.org/file/ph-assets/HDRIs/hdr/2k/studio_small_09_2k.hdr`（HTTP 200）。web 版用 1k–2k 以控制大小。

## 4. 材質（KslPbrShader）[已查證 API；數值推測]
- 鋼球：`color(Color("c8c8c8"))`、`metallic(1f)`、`roughness(0.05–0.12f)`，必須有 IBL `reflectionMap` 才有鏡面感；可加極淡 normal/roughness 雜訊避免 CG 感。
- 頂桿 / 支柱（鍍鉻或拉絲鋁）：metallic 1、roughness 0.08（鍍鉻）/ 0.25–0.35（拉絲，搭配各向異性 —— kool 無 anisotropy 參數 [已查證 KslPbrShader 無此設定]，用 roughness 貼圖條紋近似）。
- 底座：檀木（沿用 repo `sandalwood.jpg` 當 baseColor，metallic 0、roughness 0.35–0.5）或黑大理石（metallic 0、roughness 0.05–0.15，產生地面反射需 SSR）。
- 繩：極細，metallic 0、roughness 0.6，或釣魚線（半透明不易，建議不透明細線）。
- 家紋金箔（現行 `kamon_*.jpg`）：做成 baseColor + metallic 遮罩貼圖。
- Tone mapping：`ColorSpaceConversion(toneMapping = ToneMapping.AcesApproximated)` 預設；產品照可試 Khronos PBR Neutral（`modules/ksl/blocks/ColorSpaceConversion.kt`）。

## 5. Path tracing 的現實選項
| 選項 | 說明 | 工作量 | 平台 |
|---|---|---|---|
| P0. 「Path-traced look」| IBL + 雙 spot 陰影 + SSAO + SSR(可用時) + 好 HDRI + 接觸陰影 | 小 | 全平台 |
| P1. 移植現有 CPU RayTracer 成「照片模式」| repo 已有 `render/RayTracer.kt`（698 行解析光追：球/圓柱/盒/平面、鏡面反彈、ACES）。改輸出到 kool `Texture2d`（CPU 上傳），畫面靜止時漸進累積、全螢幕 quad 顯示 | 中 | 全平台（含 WebGL）；速度受 CPU 限制 |
| P2. 參考 `PathTracingDemo` 寫 KSL compute 路徑追蹤 | 場景幾乎全是解析幾何（球 + 圓柱 + 盒）→ 不需 BVH；加入 HDRI 取樣、GGX 金屬、累積 buffer | 大（新 shader 數百~上千行）| 需 compute（Vulkan/GL desktop、WebGPU）；WebGL fallback 到 P0/P1 |
| P3. 離線 Blender Cycles | 產生行銷用「英雄照」/ About 背景圖；或烘焙 AO/lightmap 進 glTF `occlusionTexture` | 中（美術）| 全平台（靜態資產）|
| P4. 完整三角網格 GPU path tracer（BVH）| 可用 glTF 幾何 | 非常大 | compute only |
建議：P0 一定做；P3 烘焙 AO 必做（便宜且效果大）；「照片模式」先 P1（重用既有程式碼），有餘力再 P2。

## 6. 其他
- 陰影：`SimpleShadowMap(scene, light)`，設定 `shadowBounds`、`clipNear/Far`、`shaderDepthOffset`（JointsDemo）。
- Deferred2 的 glTF：`GltfMaterialConfig(shaderFactory = GltfDeferredShaderFactory)`（opaque → `gbufferShader`）（`modules/gltf/GltfConfig.kt`）[已查證]；舊 Deferred 用 `isDeferredShading = true`。
- `renderScale`（Deferred2）/ demo Settings「Render scale」用來控制 web 效能。
