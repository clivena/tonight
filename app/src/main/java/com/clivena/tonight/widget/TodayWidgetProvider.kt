package com.clivena.tonight.widget

import com.clivena.tonight.R

/**
 * 3×2：只有待办。
 *
 * 180dp 宽、110dp 高，一行能放约 10 个 13sp 汉字 ——
 * 比合并卡左列（约 6 个字）宽出不少，这是它存在的理由：
 * 任务名长的时候，合并卡会把「写周报第三部分」截成「写周报第三…」。
 *
 * 3 行是 110dp 下的极限（标签 13 + 7 间距 + 3×17 = 71，剩下一截留给
 * 不同 ROM 桌面给组件的实际高度误差）。拉高到 3 格给 5 行，4 格给 6 行 ——
 * 高度是这里唯一能响应尺寸的东西，布局重排要 API 31。
 */
class TodayWidgetProvider : BaseWidgetProvider() {

    override val layoutRes = R.layout.widget_today

    override fun taskCap(heightDp: Int): Int = when {
        heightDp >= 195 -> 6
        heightDp >= 165 -> 5
        heightDp >= 135 -> 4
        else -> 3
    }

    // 这张卡没有记账区。返回 0，渲染层就不会去碰 money_sum 这些不存在的 id
    override fun moneyCap(heightDp: Int): Int = 0
}
