/*
 * Menu row, slider, switch and radio helpers adapted from kool-demo DemoUi.kt
 * (kool-engine/kool, tag v0.19.0), Apache License 2.0.
 * https://github.com/kool-engine/kool/blob/v0.19.0/kool-demo/src/commonMain/kotlin/de/fabmax/kool/demo/DemoUi.kt
 */
package com.neojou.newtoncradle.ui

import de.fabmax.kool.modules.ui2.*
import kotlin.math.abs
import kotlin.math.round

object UiSizes {
    val uiSize = mutableStateOf(Sizes.medium)

    val hGap: Dp get() = uiSize.value.gap * 1.25f
    val vGap: Dp get() = uiSize.value.gap
    val baseSize: Dp get() = uiSize.value.gap * 4f
    val menuWidth: Dp get() = baseSize * 7.5f
}

fun UiScope.MenuRow(vGap: Dp = UiSizes.vGap, block: UiScope.() -> Unit) {
    Row(width = Grow.Std) {
        modifier.margin(horizontal = UiSizes.hGap, vertical = vGap)
        block()
    }
}

fun UiScope.MenuSlider2(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    txtFormat: (Float) -> String = { it.uiNumber(2) },
    onChangeEnd: ((Float) -> Unit)? = null,
    onChange: (Float) -> Unit,
) {
    MenuRow {
        Text(label) { labelStyle(Grow.Std) }
        Text(txtFormat(value)) { labelStyle() }
    }
    MenuRow {
        Slider(value, min, max) {
            modifier
                .width(Grow.Std)
                .alignY(AlignmentY.Center)
                .onChange(onChange)
            if (onChangeEnd != null) {
                modifier.onChangeEnd(onChangeEnd)
            }
        }
    }
}

fun UiScope.LabeledRadioButton(label: String, toggleState: Boolean, onActivate: () -> Unit) {
    MenuRow {
        RadioButton(toggleState) {
            modifier
                .alignY(AlignmentY.Center)
                .margin(end = sizes.gap)
                .onToggle {
                    if (it) onActivate()
                }
        }
        Text(label) {
            labelStyle(Grow.Std)
            modifier.onClick { onActivate() }
        }
    }
}

fun UiScope.LabeledSwitch(
    label: String,
    toggleState: MutableStateValue<Boolean>,
    onToggle: ((Boolean) -> Unit)? = null,
) {
    MenuRow {
        Text(label) {
            labelStyle(Grow.Std)
            modifier.onClick {
                toggleState.toggle()
                onToggle?.invoke(toggleState.value)
            }
        }
        Switch(toggleState.use()) {
            modifier
                .alignY(AlignmentY.Center)
                .onToggle {
                    toggleState.set(it)
                    onToggle?.invoke(it)
                }
        }
    }
}

fun TextScope.sectionTitleStyle() {
    modifier
        .width(Grow.Std)
        .margin(vertical = UiSizes.hGap)
        .padding(vertical = sizes.smallGap)
        .textColor(colors.primary)
        .backgroundColor(colors.primaryVariant.withAlpha(0.2f))
        .font(sizes.largeText)
        .textAlignX(AlignmentX.Center)
}

fun TextScope.labelStyle(width: Dimension = FitContent) {
    modifier
        .width(width)
        .align(yAlignment = AlignmentY.Center)
        .padding(vertical = sizes.smallGap * 0.5f)
}

fun Float.uiNumber(digits: Int = 1): String {
    val sign = if (this < 0f) "-" else ""
    val scale = when (digits) {
        0 -> 1
        1 -> 10
        2 -> 100
        else -> 1000
    }
    val scaled = round(abs(this) * scale).toInt()
    val whole = scaled / scale
    if (digits <= 0) return sign + whole.toString()
    val frac = (scaled % scale).toString().padStart(digits, '0')
    return "$sign$whole.$frac"
}
