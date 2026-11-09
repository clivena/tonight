package com.clivena.tonight.widget

import com.clivena.tonight.R

/**
 * 合并卡：左边今天的事，右边今天的钱。
 *
 * 4×2 和 5×2 是**同一份布局、同一套行数规则**，只有注册到桌面的默认宽度不同
 * （见各自的 info xml）。所以这里抽成一个基类，两个子类是空壳。
 *
 * 为什么不给 5×2 单独做一份更「丰富」的布局：两张卡的高度都是 2 格 = 110dp，
 * 行数上限一样是 3。宽度只影响每行能放几个字 —— 那是 match_parent 自己会做的事，
 * 多写一份布局只会多一个要同步维护的地方，而差异是零。
 */
abstract class MergedWidgetProvider : BaseWidgetProvider() {

    override val layoutRes = R.layout.widget_tonight

    override fun taskCap(heightDp: Int): Int = when {
        heightDp >= 165 -> 5
        heightDp >= 135 -> 4
        else -> 3
    }

    // 右列比左列拥挤：金额是大字，剩下那点高度只够放一到两笔
    override fun moneyCap(heightDp: Int): Int = if (heightDp >= 135) 2 else 1
}

/**
 * 4×2 合并卡（v0.11 就有的那个）。
 *
 * **类名不能改**：已经添加到桌面的组件把 ComponentName 写死在系统里，
 * 改名或删类会让它们变成「无法加载」，用户必须手动移除再重新添加。
 * 这也是 v0.12 保留它而不是用 5×2 替换的原因之一（另一个原因是
 * 它和 5×2 信息量完全一样，留着不多占任何东西）。
 */
class TonightWidgetProvider : MergedWidgetProvider()

/** 5×2 合并卡：同一份布局，只是注册宽度更宽，每行能多显示两个字 */
class WideWidgetProvider : MergedWidgetProvider()
