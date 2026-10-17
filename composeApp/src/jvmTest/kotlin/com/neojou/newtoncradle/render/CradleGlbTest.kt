package com.neojou.newtoncradle.render

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class CradleGlbTest {
    @Test
    fun glbNamesTheCradleNodes() {
        var dir = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        val glb = File(dir, "composeApp/src/commonMain/resources/assets/models/newton_cradle.glb")
        assertTrue(glb.isFile, "missing ${glb.absolutePath}")
        val text = glb.readBytes().toString(Charsets.UTF_8)
        for (name in listOf(
            "NC_Ball_0",
            "NC_Ball_4",
            "NC_Wire_0_L",
            "NC_Wire_4_R",
            "NC_Base",
            "NC_Frame",
            "NC_Rods",
            "M_Steel_Ball",
            "image/jpeg",
        )) {
            assertTrue(name in text, "glb json missing $name")
        }
    }
}
