# glTF 資產管線（Blender → .glb → kool）

> [已查證] = kool 原始碼 `kool-core/src/commonMain/kotlin/de/fabmax/kool/modules/gltf/`（main @ e79b10f）；[推測] = 待驗證。

## 1. kool glTF 載入能力 [已查證]
- 檔案：`.gltf`、`.glb`，以及 `.gltf.gz` / `.glb.gz`（`GltfFile.kt`：副檔名 `.gz` 先 inflate）。
- 載入：`Assets.loadGltfModel(path, GltfLoadConfig(...))` → `Model`；或 DemoScene 的 `model(path, config)`。`Model.nodes: Map<String, Node>`、`Model.meshes: Map<String, Mesh>`、`Node.findNode(name)`、`model.printHierarchy()`（`scene/Model.kt`、`scene/Node.kt`）。
- `GltfLoadConfig`：`generateNormals`、`applyMaterials`、`materialConfig`、`applyTransforms`、`removeEmptyNodes`（預設 true！空節點會被移除 → 錨點節點要注意）、`mergeMeshesByMaterial`、`instanceLayout`、`pbrBlock`（可對每個 primitive 改 PBR 設定）。
- `GltfMaterialConfig`：`shadowMaps`、`scrSpcAmbientOcclusionMap`、`environmentMap`、`isDeferredShading`、`maxNumberOfLights`、`shaderFactory`（Deferred2 用 `GltfDeferredShaderFactory`）。
- 材質只讀核心 metallic-roughness：`baseColorFactor/Texture`、`metallicFactor`、`roughnessFactor`、`metallicRoughnessTexture`、`normalTexture`、`occlusionTexture`（含 strength）、`emissiveTexture/Factor`、`alphaMode`、`doubleSided`（`GltfMaterial.kt`）。
- **不支援任何 KHR / EXT 擴充**：原始碼中找不到 `KHR_` 字串（`KHR_materials_*`、`KHR_texture_transform`、`KHR_draco_mesh_compression`、`EXT_meshopt_compression`、`KHR_lights_punctual` 都不會被處理）。→ Blender 匯出時**全部關閉**擴充/壓縮。
- **只支援 TEXCOORD_0**：有 TEXCOORD_1 時會 `logW("Second set of UVs is not yet supported")` 並忽略（`GltfMesh.kt`）。→ 烘焙 AO/lightmap 必須放在 UV0（或用獨立貼圖節點以 UV0 展開）。

## 2. Blender 建模規格（建議）
單位：Blender 1 m = kool 1 單位。建議**模型以「球半徑 = 1」為單位建模**（對齊現行 `sim/Cradle.kt` 常數，物理不必換算），或以公尺建模後在程式 `transform.scale` —— 二選一並寫入 README。[推測：保留 1=半徑 可避免 PhysX 小尺度問題]

對齊現行常數（`Cradle.kt` [已查證]）：球距 `2r`（相切）、頂桿高 `PIVOT_Y = 8.35`、雙線間距 `±BAR_Z = 2.55`、線長 `10.5`、支柱 `POST_X = ±9.45`、桿半徑 `0.112`、底座頂 `-6.58` / 底 `-10.25`、半寬 `14.4 × 7.35`。

### 節點命名（程式綁定用）
```
NC_Root
├─ NC_Base                 （底座，static）
├─ NC_Frame                （支柱 + 頂桿，static）
│   ├─ NC_Bar_Front / NC_Bar_Back
├─ NC_Ball_0 … NC_Ball_4    （球 mesh，原點=球心）
├─ NC_Wire_0_F / NC_Wire_0_B …（繩，原點在頂桿附著點，沿 -Y 長度 1 → 程式依球位置縮放/旋轉；或程式生成）
├─ NC_Pivot_0 … NC_Pivot_4  （空物件，頂桿上的鉸鏈點；因 removeEmptyNodes=true，載入時設 removeEmptyNodes=false，或改從常數計算）
└─ NC_Cam_Front / NC_Cam_Hero / …（空物件，鏡頭預設位置，可選）
```
- 命名規則：`NC_<Part>_<index>[_<side>]`；程式用 `model.nodes["NC_Ball_$i"]` 取節點，每幀把物理姿態寫入 `transform`。
- 球數可變（3–7）時：glTF 只放一顆 `NC_Ball_Proto`，程式 instanced 複製（`instanceLayout`）。[建議]
- 動態物件不要 apply 到父層非單位縮放；static 物件可合併。

### 材質（Principled BSDF → glTF）
| 名稱 | Base Color | Metallic | Roughness | 備註 |
|---|---|---|---|---|
| `M_Steel_Ball` | #C8C8C8（linear ~0.58）| 1.0 | 0.05–0.12 | 球，產品照的主角 |
| `M_Chrome` | #D0D0D0 | 1.0 | 0.06–0.10 | 頂桿/支柱 |
| `M_BrushedAlu` | #B8BCC0 | 1.0 | 0.25–0.35 | 選項；kool 無 anisotropy，以 roughness 條紋貼圖近似 |
| `M_Wood_Base` | `sandalwood.jpg` 或 Poly Haven `wood_table_001` | 0 | 0.35–0.55 | + normal、AO |
| `M_Marble_Base` | Poly Haven `marble_01` | 0 | 0.05–0.2 | 黑/白大理石選項 |
| `M_Wire` | #E0E0E0 | 0 | 0.6 | 細繩（半徑 ~0.02）|
| `M_Gold_Inlay` | 金 #FFC870 | 1 | 0.2 | 家紋 `kamon_*.jpg` 遮罩 |
Poly Haven 材質頁（已用 api.polyhaven.com/info 確認存在）：https://polyhaven.com/a/wood_table_001 、https://polyhaven.com/a/marble_01 。授權 CC0：https://polyhaven.com/license 。

### 匯出設定（Blender glTF 2.0 exporter，KhronosGroup/glTF-Blender-IO）
- Format：**glTF Binary (.glb)**；Include：Selected / Visible；Transform：+Y Up。
- Mesh：Apply Modifiers、UVs、Normals、Tangents（normal map 用）；**不勾** Draco / meshopt 壓縮。
- Material：Export；Images：JPEG（baseColor）/ PNG（normal、ORM）；貼圖 ≤ 2048（web 建議 1024）。
- 不匯出 lights/cameras（kool 不讀 KHR_lights_punctual）；動畫不需要。
- ORM 貼圖：glTF 規定 R=Occlusion、G=Roughness、B=Metallic；kool 會分別讀 occlusionTexture 與 metallicRoughnessTexture。
- 烘焙：Cycles 烘 AO（含球與底座之間的接觸暗部）到 UV0 → occlusionTexture；動態球與底座之間的動態接觸陰影交給 SSAO + shadow map。
- 檔案放置：`src/commonMain/resources/assets/models/newton_cradle.glb`（依 kool template 慣例 `assets/`）。可另存 `.glb.gz` 減少 web 下載量。

## 3. HDRI
- Poly Haven（CC0）：`studio_small_09`、`brown_photostudio_02`、`photo_studio_01`（API 已確認）。
- `.hdr` → `.rgbe.png`：用 `HdrImageConversion.convertHdrImageToRgbe(File)`（kool desktop）轉，放 `assets/hdri/*.rgbe.png`。

## 4. 程序化 fallback（沒有 glb 也能跑）
- kool `MeshBuilder`：`generate { uvSphere { ... } / icoSphere { ... }、cylinder { ... }、cube { ... } }`；JointsDemo 的 `GearChainMeshGen.kt` 是程序化「nice mesh」範例。[已查證：`scene/geometry/MeshBuilder.kt` 有 `uvSphere {}`、`icoSphere {}`、`cylinder {}`、`cube {}`]
- 建議實作 `CradleModelSource`：`GltfSource`（優先）與 `ProceduralSource`（fallback）共用同一組節點命名/介面，讓物理綁定程式不變。
- 現行 Compose 版的幾何常數與材質（`render/Scene.kt`、`render/Materials.kt`、`render/StudioEnv.kt`）可直接當程序化版本規格。
