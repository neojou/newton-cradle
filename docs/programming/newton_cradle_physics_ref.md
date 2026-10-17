# 牛頓擺物理（kool-physics / PhysX）參考

> 標記：[已查證] 原始碼/官方文件；[推測] 經驗推論，實作需驗證。
> kool-physics 路徑：`kool-physics/src/commonMain/kotlin/de/fabmax/kool/physics/`（main @ e79b10f；0.19.0 同等 API，PhysX 5.6 via physx-jni 2.7.1 / physx-js-webidl 2.7.1）

## 1. 現行 newton-cradle 物理（要保留的知識）[已查證]
- `composeApp/src/commonMain/kotlin/com/neojou/newtoncradle/sim/CradlePhysics.kt`（319 行）、`sim/Cradle.kt`（86 行）。
- 模型：5 個**平面單擺**（角度 `theta[i]`、角速度 `omega[i]`），單位 1 = 球半徑（約 15 mm），`STRING_LEN = 10.5`、`BAR_Z = 2.55`（V 形雙線吊掛，平面擺長 `SWING_L = sqrt(10.5² − 2.55²) = 10.1857`），`GRAVITY = 402`（調成約 1 s 週期），`MAX_ANGLE = 90°`。
- 積分：固定內步 1/360 s，每幀最多 18 步；`resolveCollisions()` 用「**依序成對交換角速度**」（等質量彈性碰撞），`transferChainRight/Left` 沿接觸鏈把動量傳到最遠的球 → 理想牛頓擺行為。
- 放手後 `energyLock` 鎖定能量，每步 `restoreEnergy()` 以縮放 omega 補償數值損耗（永不停止）。
- 互動：只能抓端點球（index 0 / 4）、`setHoldAngle` 防穿透、`takeImpact()` 回傳碰撞強度觸發 `clang.wav`。
- → 這套解析模型**本身就是最可靠的牛頓擺解法**，改寫時建議保留為「理想解析」模式。

## 2. kool-physics 可用的 API [已查證]
- 世界：`PhysicsWorld(scene, isContinuousCollisionDetection = false)`；`gravity`；`simStepper.desiredTimeFactor`、`isPaused`、`cpuMillisPerStep`；`physicsStepListeners`（`InterpolatableSimulation.simulateStep(dt)` 在每個物理步**之前**於物理執行緒呼叫）；`registerContactListener(ContactListener)`（`onTouchFound(a, b, contactPoints: List<ContactPoint>?)`，ContactPoint 含 `impulse`）。
- **固定步長**：`PhysicsWorld` 內部 `AsyncSimulationStepper(SimStepCallbacks(), Physics.physicsDispatcher)`，`simulationTimeStep` 預設 **1/60 s**，從 PhysicsWorld 無法改（`kool-core/src/commonMain/kotlin/de/fabmax/kool/util/SimulationStepper.kt`）。→ 無法直接做 substep；`stepSimulation` 是 protected。[已查證]
- 剛體：`RigidDynamic(mass, pose, isKinematic)`、`RigidStatic()`、`attachShape(Shape(SphereGeometry(r), material, localPose, simFilterData))`；`linearVelocity`、`angularVelocity`、`linearDamping`、`angularDamping`、`maxLinearVelocity`、`addImpulseAtPos`、`setKinematicTarget`（必須在物理執行緒）、`wakeUp()`。
- 材質：`Material(staticFriction, dynamicFriction = staticFriction, restitution = 0.2f)`。
- Joints（`joints/`）：`RevoluteJoint(bodyA?, bodyB, pivotA, pivotB, axisA, axisB)`（含 `enableLimit`、`enableAngularMotor`）、`SphericalJoint`、`DistanceJoint(bodyA?, bodyB, frameA, frameB)`（`setMaxDistance/setMinDistance`）、`D6Joint`（`D6JointMotion.Free/Limited/Locked`）、`FixedJoint`、`PrismaticJoint`；`bodyA = null` 代表固定在世界。
- 碰撞過濾：`FilterData { setCollisionGroup(g); clearCollidesWith(g) }`（JointsDemo 用法）。
- CCD：`PhysicsWorld(..., isContinuousCollisionDetection = true)` → scene `eENABLE_CCD`，且 addActor 時對 RigidBody 設 `eENABLE_CCD` + pair flag `eDETECT_CCD_CONTACT`（`PhysicsWorld.desktop.kt` L62/L254/L258）。
- **未在 common API 暴露**：solver 迭代數、solver 型別（PGS/TGS）、`bounceThresholdVelocity`、sleep threshold、contact/rest offset。可透過平台層取得原生物件：desktop `PhysicsWorldImpl.pxScene: PxScene`（public）、`RigidDynamicImpl.holder.px`；web 同名（`PhysicsWorld.web.kt` 的 `pxScene`）。web 綁定確有 `PxScene.bounceThresholdVelocity`（var）、`PxRigidDynamic.setSolverIterationCounts(pos, vel)`、`PxRigidBodyFlagEnum.eENABLE_SPECULATIVE_CCD`（`kool-physics/src/webMain/kotlin/physx/Physics.kt`）。→ 以 `expect/actual fun tunePhysx(world, bodies)` 實作。
- PhysX 文件：預設 solver 迭代 4 position / 1 velocity，`PxRigidDynamic::setSolverIterationCounts` 為每剛體下限；TGS 把時間步依 position 迭代數切成 substeps（https://nvidia-omniverse.github.io/PhysX/physx/5.6.0/docs/RigidBodyDynamics.html）。[已查證]
- PhysX `bounceThresholdVelocity` 預設約 `0.2 × tolerancesScale.speed`（= 2 m/s，若 speed = 10）：相對速度低於此值的接觸**不反彈**。[推測，依 PhysX API 記憶，需驗證]；PhysX 預設 angularDamping 0.05、linearDamping 0。[推測]

## 3. 吊掛方式選項
| 方案 | 作法 | 優點 | 缺點 |
|---|---|---|---|
| A. RevoluteJoint（建議）| 每球一個 `RevoluteJoint(null, ball, pivotA = (x_i, PIVOT_Y, 0), pivotB = (0, SWING_L, 0), axis = Z)`；繩子只做視覺（兩條線從頂桿 `(x_i, PIVOT_Y, ±BAR_Z)` 到球頂）| V 形雙線的運動學就等於「繞頂桿連線的鉸鏈」，最穩定、無側擺 | 不模擬繩鬆弛 |
| B. 兩條 DistanceJoint | 頂桿兩點 → 球頂附著點，`setMaxDistance(L)`（可加 `setMinDistance(L)` 變剛性桿）| 真的「線」，可做繩鬆弛/側擺 | 兩約束 + 接觸耦合，迭代收斂差、會抖 [推測] |
| C. D6Joint | 鎖定除一個轉軸外所有自由度 | 等同 A，可加軟限制 | 設定繁瑣 |
| D. SphericalJoint | 單點吊掛 | 簡單 | 會側擺、不像牛頓擺 |

## 4. 已知問題：剛體引擎很難做出正確的牛頓擺 [推測，業界常見經驗]
- 5 球**同時接觸**時，PGS/TGS 迭代求解器把衝量分散到整條接觸鏈，結果常是「多顆球一起慢慢動」而非「最後一顆彈出」；同時接觸的理想解依賴碰撞**順序**（sequential），這不是同時 LCP 求解的自然結果。
- 加上 1/60 s 固定步、restitution 與 bounce threshold、角阻尼、sleep，會逐漸失去能量或停住。
- 緩解：
  1. 球間留**微小間隙**（例如 0.1–1% 半徑，模型單位 0.002–0.01），使碰撞變成依序的兩兩碰撞。
  2. `restitution = 1`、摩擦 0（PhysX combine mode 預設平均，兩邊都設 1/0）。
  3. `linearDamping = angularDamping = 0`；關閉 sleep（`setSleepThreshold(0)` 平台層）。
  4. `bounceThresholdVelocity` 降到極小（例如 1e-3）。
  5. 提高 solver 迭代（如 32/8）、啟用 CCD 或 speculative CCD。
  6. kool 固定 60 Hz 步長無法 substep → 以時間倍率不可解；若需更細步長只能修改/包裝 stepper（需改 kool 或平台層自行呼叫 `pxScene.simulate`，[推測] 風險高）。
- **混合法（建議作為 PhysX 模式的主力）**：
  - PhysX 負責：球的剛體、RevoluteJoint 吊掛、與框架/底座/滑鼠拖曳的互動、陰影/視覺同步。
  - 球–球碰撞由**自寫解析碰撞**處理：用 `FilterData` 讓球彼此不碰撞（同一 collision group + `clearCollidesWith(group)`），在 `physicsStepListeners.simulateStep` 中（物理執行緒）偵測相鄰球距離 ≤ 2r 且接近 → 沿鏈依序交換法向速度（移植 `CradlePhysics.transferChainRight/Left` 邏輯），用 `linearVelocity`/`angularVelocity` 寫回；加能量鎖。
- **理想解析模式**：直接移植現行 `CradlePhysics`（擴為 3D 姿態）；球用 kinematic 剛體或純 Node transform。作為預設模式，保證「看起來對」。
- **PhysX 純剛體（實驗）**：完全交給 PhysX，用來展示問題；UI 提示「可能不符合理想牛頓擺」。

## 5. 建議參數（起點，[推測] 需調）
- 尺寸：若改用公尺：球徑 25 mm（r = 0.0125 m）、擺長 ~0.10–0.15 m、g = 9.81 → 週期 ~0.7 s；或保留現行單位 + `GRAVITY = 402`（`world.gravity = Vec3f(0f, -402f, 0f)`）。PhysX 對極小尺度敏感，建議**保留「1 單位 = 球半徑」**並放大重力，或設 `tolerancesScale`（kool 未暴露 → 保留現行單位較安全）。
- 球質量 1，`Material(0f, 0f, 1f)`；框架 `RigidStatic`；拖曳：抓起時改由 kinematic 目標或在 simulateStep 設定角度/速度。
- 音效：kool `Assets.loadAudioClip("assets/clang.wav")` → `AudioClip.play()`、`volume`、`minIntervalMs`（`kool-core/.../modules/audio/AudioClip.kt`）[已查證 API]；觸發點用解析碰撞強度（沿用 `takeImpact()`）。

## 6. 驗收指標
- 拉 1 顆放手：另一端 1 顆彈出，中間 3 顆位移 < 1% 半徑；拉 2 顆 → 2 顆彈出。
- 理想模式 10 分鐘振幅衰減 < 1%；PhysX 混合模式 60 s 衰減 < 5%。
- 同參數 desktop 與 web 行為一致（週期誤差 < 2%）。
