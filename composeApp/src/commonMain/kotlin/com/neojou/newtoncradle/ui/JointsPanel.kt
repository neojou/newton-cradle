package com.neojou.newtoncradle.ui

import com.neojou.newtoncradle.AppVersion
import com.neojou.newtoncradle.Cameras
import com.neojou.newtoncradle.CradleSession
import com.neojou.newtoncradle.sim.physx.PhysicsMode
import de.fabmax.kool.KoolContext
import de.fabmax.kool.KoolSystem
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Time
import kotlin.math.roundToInt

private val panelAccent = Color.fromHex("7F97A8")
private val panelBg = Color.fromHex("101010D0")
private val drawerBg = Color.fromHex("14161AE8")
private val scrim = Color(0f, 0f, 0f, 0.4f)

/**
 * Right-hand joints-style window plus a left drawer.
 * Only controls that do something in this phase are shown.
 */
fun installCradleUi(
    ui: Scene,
    world: Scene,
    ctx: KoolContext,
    session: CradleSession,
    debug: Scene,
) {
    val minimized = mutableStateOf(false)
    val drawerOpen = mutableStateOf(false)
    val drawerT = mutableStateOf(0f)
    val ssao = mutableStateOf(true)
    val ssaoRadius = mutableStateOf(session.rig.ao.radius)
    val ssaoStrength = mutableStateOf(session.rig.ao.strength)
    val renderScale = mutableStateOf(ctx.window.renderResolutionFactor)
    val fullscreen = mutableStateOf(ctx.window.flags.isFullscreen)
    val debugOn = mutableStateOf(debug.isVisible)

    val photoLayer = UiSurface(world, Colors.singleColorDark(panelAccent, Color(0f, 0f, 0f, 0f)), UiSizes.uiSize.value, "photo")
    photoLayer.content = {
        val tex = session.photoImage.use()
        if (tex != null) {
            Image(tex) {
                modifier
                    .size(Grow.Std, Grow.Std)
                    .imageSize(ImageSize.Stretch)
                    .isBlocking(false)
            }
        }
    }
    ui.addNode(photoLayer)

    val dockable = UiDockable(
        "settings",
        floatingX = UiSizes.baseSize * 2f,
        floatingY = UiSizes.baseSize * 2f,
        floatingWidth = UiSizes.menuWidth,
        floatingHeight = Dp(760f),
        floatingAlignmentX = AlignmentX.End,
        floatingAlignmentY = AlignmentY.Top,
    )

    ui.onUpdate {
        val target = if (drawerOpen.value) 1f else 0f
        val current = drawerT.value
        if (current != target) {
            val step = Time.deltaT / 0.2f
            val next = if (current < target) {
                minOf(target, current + step)
            } else {
                maxOf(target, current - step)
            }
            drawerT.set(next)
        }
    }

    ui.addNode(
        WindowSurface(
            parentScene = world,
            windowDockable = dockable,
            colors = Colors.singleColorDark(panelAccent, panelBg),
            sizes = UiSizes.uiSize.value,
        ) {
            surface.sizes = UiSizes.uiSize.use()
            Column(Grow.Std, Grow.Std) {
                TitleBar(
                    dockable,
                    title = UiText.APP,
                    onMinimizeAction = {
                        minimized.toggle()
                        dockable.setFloatingBounds(
                            height = if (minimized.value) FitContent else Dp(760f),
                        )
                    },
                    isMinimizedToTitle = minimized.use(),
                )
                if (!minimized.use()) {
                    ScrollArea(
                        height = Grow.Std,
                        withHorizontalScrollbar = false,
                        containerModifier = { it.background(null) },
                    ) {
                        modifier.width(Grow.Std)
                        Column(Grow.Std) {
                            settingsBody(
                                ctx, session, debug, drawerOpen, debugOn,
                                ssao, ssaoRadius, ssaoStrength, renderScale, fullscreen,
                            )
                        }
                    }
                }
            }
        },
    )

    val drawer = UiSurface(world, Colors.singleColorDark(panelAccent, panelBg), UiSizes.uiSize.value, "drawer")
    drawer.content = {
        surface.sizes = UiSizes.uiSize.use()
        val openAmount = drawerT.use()
        if (openAmount <= 0.001f) {
            Button(UiText.MENU) {
                modifier
                    .margin(sizes.gap)
                    .align(AlignmentX.Start, AlignmentY.Top)
                    .onClick { drawerOpen.set(true) }
            }
        } else {
            Box {
                modifier
                    .size(Grow.Std, Grow.Std)
                    .backgroundColor(scrim.withAlpha(0.45f * openAmount))
                    .onClick { drawerOpen.set(false) }
            }
            Column {
                modifier
                    .width(UiSizes.menuWidth)
                    .height(Grow.Std)
                    .margin(start = Dp(UiSizes.menuWidth.value * (openAmount - 1f)))
                    .backgroundColor(drawerBg)
                    .onClick { it.isConsumed = true }
                ScrollArea(
                    height = Grow.Std,
                    withHorizontalScrollbar = false,
                    containerModifier = { it.background(null) },
                ) {
                    modifier.width(Grow.Std)
                    Column(Grow.Std) {
                        Text(UiText.MENU) { sectionTitleStyle() }
                        MenuRow {
                            Button(UiText.CLOSE) {
                                modifier.width(Grow.Std).onClick { drawerOpen.set(false) }
                            }
                        }
                        drawerSettings(ctx, debug, drawerOpen, debugOn, renderScale, fullscreen)
                        aboutBlock()
                    }
                }
            }
        }
    }
    ui.addNode(drawer)
}

private fun UiScope.settingsBody(
    ctx: KoolContext,
    session: CradleSession,
    debug: Scene,
    drawerOpen: MutableStateValue<Boolean>,
    debugOn: MutableStateValue<Boolean>,
    ssao: MutableStateValue<Boolean>,
    ssaoRadius: MutableStateValue<Float>,
    ssaoStrength: MutableStateValue<Float>,
    renderScale: MutableStateValue<Float>,
    fullscreen: MutableStateValue<Boolean>,
) {
    Text(UiText.SECTION_CONTROL) { sectionTitleStyle() }
    MenuRow {
        Button(UiText.RESET) {
            modifier.width(Grow.Std).onClick { session.reset() }
        }
    }
    MenuRow {
        Button(UiText.RELEASE) {
            modifier.width(Grow.Std).onClick { session.releaseHold() }
        }
    }
    MenuRow {
        Button(UiText.PULL_LEFT) {
            modifier.width(Grow.Std).onClick { session.pullLeft() }
        }
    }
    MenuRow {
        Button(UiText.PULL_RIGHT) {
            modifier.width(Grow.Std).onClick { session.pullRight() }
        }
    }
    MenuRow {
        val paused = session.paused.use()
        Button(if (paused) UiText.RESUME else UiText.PAUSE) {
            modifier.width(Grow.Std).onClick { session.paused.set(!session.paused.value) }
        }
    }

    Text(UiText.SECTION_PHYSICS) { sectionTitleStyle() }
    val mode = session.mode.use()
    LabeledRadioButton(UiText.ANALYTIC, mode == PhysicsMode.ANALYTIC) {
        session.setMode(PhysicsMode.ANALYTIC)
    }
    LabeledRadioButton(UiText.MODE_HYBRID, mode == PhysicsMode.HYBRID) {
        session.setMode(PhysicsMode.HYBRID)
    }
    LabeledRadioButton(UiText.MODE_PURE, mode == PhysicsMode.PURE) {
        session.setMode(PhysicsMode.PURE)
    }
    val note = session.modeNote.use()
    if (note.isNotEmpty()) {
        MenuRow {
            Text(note) { labelStyle(Grow.Std) }
        }
    }
    val balls = session.ballCountUi.use()
    MenuSlider2(
        label = UiText.BALL_COUNT,
        value = balls,
        min = 3f,
        max = 7f,
        txtFormat = { it.roundToInt().toString() },
        onChange = { session.ballCountUi.set(it) },
        onChangeEnd = { session.rebuild(it.roundToInt()) },
    )
    val pull = session.pullDegrees.use()
    MenuSlider2(
        label = UiText.PULL_ANGLE,
        value = pull,
        min = 10f,
        max = 75f,
        txtFormat = { "${it.roundToInt()}°" },
        onChange = { session.pullDegrees.set(it) },
    )
    val scale = session.timeScale.use()
    MenuSlider2(
        label = UiText.TIME_SCALE,
        value = scale,
        min = 0.1f,
        max = 2f,
        txtFormat = { "${it.uiNumber(2)}×" },
        onChange = { session.timeScale.set(it) },
    )
    val restitution = session.restitution.use()
    MenuSlider2(
        label = UiText.RESTITUTION,
        value = restitution,
        min = 0.9f,
        max = 1f,
        txtFormat = { it.uiNumber(2) },
        onChange = { session.setRestitution(it) },
    )
    val gap = session.gapMm.use()
    MenuSlider2(
        label = UiText.GAP,
        value = gap,
        min = 0f,
        max = 0.5f,
        txtFormat = { "${it.uiNumber(2)} mm" },
        onChange = { session.gapMm.set(it) },
        onChangeEnd = { session.setGapMm(it) },
    )

    Text(UiText.SECTION_VISUAL) { sectionTitleStyle() }
    MenuRow {
        Text(session.modelNote.use()) { labelStyle(Grow.Std) }
    }
    val collision = session.showCollision.use()
    LabeledRadioButton(UiText.VIS_NICE, !collision) {
        session.setCollisionView(false)
    }
    LabeledRadioButton(UiText.VIS_COLLISION, collision) {
        session.setCollisionView(true)
    }
    if (collision && mode == PhysicsMode.ANALYTIC) {
        MenuRow {
            Text(UiText.COLLISION_ANALYTIC) { labelStyle(Grow.Std) }
        }
    }
    LabeledSwitch(UiText.JOINTS, session.showJoints)

    Text(UiText.SECTION_CAMERA) { sectionTitleStyle() }
    val cameraId = session.cameraId.use()
    for (preset in Cameras.all) {
        LabeledRadioButton(preset.label, cameraId == preset.id) {
            session.applyCamera(preset)
        }
    }

    Text(UiText.SECTION_QUALITY) { sectionTitleStyle() }
    LabeledSwitch(UiText.SSAO, ssao) { enabled ->
        session.setSsao(enabled, ssaoRadius.value, ssaoStrength.value)
    }
    val radius = ssaoRadius.use()
    MenuSlider2(
        label = UiText.RADIUS,
        value = radius,
        min = 0.4f,
        max = 6f,
        onChange = {
            ssaoRadius.set(it)
            session.setSsao(ssao.value, it, ssaoStrength.value)
        },
    )
    val strength = ssaoStrength.use()
    MenuSlider2(
        label = UiText.STRENGTH,
        value = strength,
        min = 0f,
        max = 2.5f,
        onChange = {
            ssaoStrength.set(it)
            session.setSsao(ssao.value, ssaoRadius.value, it)
        },
    )
    LabeledSwitch(UiText.DEFERRED, session.deferredOn) { session.setDeferred(it) }
    if (session.deferredOn.use()) {
        LabeledSwitch(UiText.SSR, session.ssrOn) { session.setSsr(it) }
    }
    val shadingNote = session.shadingNote.use()
    if (shadingNote.isNotEmpty()) {
        MenuRow { Text(shadingNote) { labelStyle(Grow.Std) } }
    }
    LabeledSwitch(UiText.PHOTO, session.photoOn)
    val photoNote = session.photoNote.use()
    if (photoNote.isNotEmpty()) {
        MenuRow { Text(photoNote) { labelStyle(Grow.Std) } }
    }

    Text(UiText.SECTION_STATS) { sectionTitleStyle() }
    val frame = remember(0)
    surface.onEachFrame { frame.set(frame.value + 1) }
    frame.use()
    MenuRow {
        Text("${UiText.FPS}  ${Time.fps.uiNumber(0)}") { labelStyle(Grow.Std) }
    }
    MenuRow {
        Text("${UiText.TIME_SCALE}  ${session.timeScale.value.uiNumber(2)}×") { labelStyle(Grow.Std) }
    }
    MenuRow {
        Text("${UiText.KINETIC}  ${session.kineticEnergy().uiNumber(1)}") { labelStyle(Grow.Std) }
    }
    MenuRow {
        Text("${UiText.BACKEND}  ${KoolSystem.requireContext().backend.apiName}") { labelStyle(Grow.Std) }
    }
    val compute = KoolSystem.requireContext().backend.features.computeShaders
    MenuRow {
        Text("${UiText.COMPUTE}  ${if (compute) UiText.YES else UiText.NO}") { labelStyle(Grow.Std) }
    }

    Text(UiText.SETTINGS) { sectionTitleStyle() }
    LabeledSwitch(UiText.MENU_OPEN, drawerOpen)
    appSettings(ctx, debug, debugOn, renderScale, fullscreen)
}

private fun UiScope.drawerSettings(
    ctx: KoolContext,
    debug: Scene,
    drawerOpen: MutableStateValue<Boolean>,
    debugOn: MutableStateValue<Boolean>,
    renderScale: MutableStateValue<Float>,
    fullscreen: MutableStateValue<Boolean>,
) {
    Text(UiText.SETTINGS) { sectionTitleStyle() }
    LabeledSwitch(UiText.MENU_OPEN, drawerOpen)
    appSettings(ctx, debug, debugOn, renderScale, fullscreen)
}

private fun UiScope.appSettings(
    ctx: KoolContext,
    debug: Scene,
    debugOn: MutableStateValue<Boolean>,
    renderScale: MutableStateValue<Float>,
    fullscreen: MutableStateValue<Boolean>,
) {
    Text(UiText.UI_SIZE) { labelStyle(Grow.Std) }
    val size = UiSizes.uiSize.use()
    LabeledRadioButton(UiText.SIZE_S, size == Sizes.small) { UiSizes.uiSize.set(Sizes.small) }
    LabeledRadioButton(UiText.SIZE_M, size == Sizes.medium) { UiSizes.uiSize.set(Sizes.medium) }
    LabeledRadioButton(UiText.SIZE_L, size == Sizes.large) { UiSizes.uiSize.set(Sizes.large) }
    val scale = renderScale.use()
    MenuSlider2(
        label = UiText.RENDER_SCALE,
        value = scale,
        min = 0.5f,
        max = 1f,
        txtFormat = { "${(it * 100f).roundToInt()}%" },
        onChange = {
            renderScale.set(it)
            ctx.window.renderResolutionFactor = it
        },
    )
    LabeledSwitch(UiText.DEBUG, debugOn) { debug.isVisible = it }
    LabeledSwitch(UiText.FULLSCREEN, fullscreen) { ctx.window.setFullscreen(it) }
}

private fun UiScope.aboutBlock() {
    Text(UiText.ABOUT) { sectionTitleStyle() }
    MenuRow { Text(AppVersion.APP_NAME) { labelStyle(Grow.Std) } }
    MenuRow { Text(AppVersion.APP_NAME_EN) { labelStyle(Grow.Std) } }
    MenuRow { Text("${UiText.VERSION} ${AppVersion.DISPLAY}") { labelStyle(Grow.Std) } }
    MenuRow { Text(AppVersion.SUMMARY) { labelStyle(Grow.Std) } }
}

private fun Double.uiNumber(digits: Int): String = toFloat().uiNumber(digits)
