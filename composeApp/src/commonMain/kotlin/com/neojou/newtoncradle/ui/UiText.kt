package com.neojou.newtoncradle.ui

import com.neojou.newtoncradle.AppVersion

/**
 * Traditional Chinese copy for the joints-style panel.
 * [glyphSource] is the character set baked into the MSDF atlas.
 */
object UiText {
    const val APP = AppVersion.APP_NAME
    const val MENU = "選單"
    const val CLOSE = "關閉"
    const val SETTINGS = "設定"
    const val UI_SIZE = "介面尺寸"
    const val SIZE_S = "小"
    const val SIZE_M = "中"
    const val SIZE_L = "大"
    const val RENDER_SCALE = "繪製比例"
    const val DEBUG = "除錯資訊"
    const val FULLSCREEN = "全螢幕"
    const val MENU_OPEN = "展開選單"
    const val ABOUT = "關於"
    const val VERSION = "版本"

    const val SECTION_CONTROL = "操作"
    const val RESET = "重置"
    const val RELEASE = "放下"
    const val PULL_LEFT = "拉起左球"
    const val PULL_RIGHT = "拉起右球"
    const val PAUSE = "暫停"
    const val RESUME = "繼續"

    const val SECTION_PHYSICS = "物理"
    const val ANALYTIC = "解析物理"
    const val MODE_HYBRID = "PhysX 混合"
    const val MODE_PURE = "PhysX 純剛體（實驗）"
    const val PURE_HINT = "實驗，可能不像理想牛頓擺"
    const val PHYSX_UNAVAILABLE = "PhysX 尚未載入"
    const val RESTITUTION = "恢復係數"
    const val GAP = "球間隙"
    const val BALL_COUNT = "球數"
    const val PULL_ANGLE = "拉起角度"
    const val TIME_SCALE = "時間倍率"
    const val SECTION_VISUAL = "視覺化"
    const val VIS_NICE = "精緻模型"
    const val VIS_COLLISION = "碰撞體"
    const val JOINTS = "關節"
    const val COLLISION_ANALYTIC = "碰撞體只在 PhysX 模式"

    const val SECTION_CAMERA = "鏡頭"
    const val CAM_FRONT = "正面"
    const val CAM_SIDE = "側面"
    const val CAM_TOP = "俯視"
    const val CAM_HERO = "產品照"
    const val CAM_MACRO = "微距"
    const val CAM_TURN = "環繞"
    const val CAM_FREE = "自由"

    const val SECTION_QUALITY = "畫質"
    const val SSAO = "環境光遮蔽"
    const val RADIUS = "半徑"
    const val STRENGTH = "強度"
    const val DEFERRED = "延遲著色"
    const val SSR = "螢幕空間反射"
    const val PHOTO = "照片模式"
    const val PHOTO_HINT = "靜止時逐幀變清晰"
    const val PHOTO_WAIT = "等待靜止"
    const val DEFERRED_FAIL = "延遲著色無法建立"
    const val NO_COMPUTE = "此後端沒有 compute，仍使用舊版反射"
    const val COMPUTE = "計算著色"
    const val YES = "有"
    const val NO = "無"
    const val MODEL_GLTF = "glTF 模型"
    const val MODEL_PROC = "程序模型"

    const val SECTION_STATS = "統計"
    const val FPS = "幀率"
    const val KINETIC = "動能"
    const val BACKEND = "後端"

    val glyphSource: String = buildString {
        listOf(
            APP,
            AppVersion.APP_NAME_EN,
            AppVersion.DISPLAY,
            AppVersion.SUMMARY,
            MENU,
            CLOSE,
            SETTINGS,
            UI_SIZE,
            SIZE_S,
            SIZE_M,
            SIZE_L,
            RENDER_SCALE,
            DEBUG,
            FULLSCREEN,
            MENU_OPEN,
            ABOUT,
            VERSION,
            SECTION_CONTROL,
            RESET,
            RELEASE,
            PULL_LEFT,
            PULL_RIGHT,
            PAUSE,
            RESUME,
            SECTION_PHYSICS,
            ANALYTIC,
            MODE_HYBRID,
            MODE_PURE,
            PURE_HINT,
            PHYSX_UNAVAILABLE,
            RESTITUTION,
            GAP,
            BALL_COUNT,
            PULL_ANGLE,
            TIME_SCALE,
            SECTION_CAMERA,
            CAM_FRONT,
            CAM_SIDE,
            CAM_TOP,
            CAM_HERO,
            CAM_MACRO,
            CAM_TURN,
            CAM_FREE,
            SECTION_VISUAL,
            VIS_NICE,
            VIS_COLLISION,
            JOINTS,
            COLLISION_ANALYTIC,
            SECTION_QUALITY,
            SSAO,
            RADIUS,
            STRENGTH,
            DEFERRED,
            SSR,
            PHOTO,
            PHOTO_HINT,
            PHOTO_WAIT,
            DEFERRED_FAIL,
            NO_COMPUTE,
            COMPUTE,
            YES,
            NO,
            MODEL_GLTF,
            MODEL_PROC,
            SECTION_STATS,
            FPS,
            KINETIC,
            BACKEND,
            "°",
        ).forEach { append(it) }
        for (code in 32..126) append(code.toChar())
    }
}
