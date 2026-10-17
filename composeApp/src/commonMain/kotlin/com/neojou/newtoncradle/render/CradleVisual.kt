package com.neojou.newtoncradle.render

import de.fabmax.kool.scene.Node

/**
 * Pose target shared by the procedural cradle and a loaded glTF cradle.
 * [root] is what the scene parents. Ball and wire nodes are children of it.
 */
interface CradleVisual {
    val ballCount: Int
    val root: Node
    fun setDynamicsVisible(visible: Boolean)
    fun sync(theta: FloatArray)
    fun syncCenters(x: FloatArray, y: FloatArray, pivotX: FloatArray)
}
