package com.clivena.tonight.widget

import com.clivena.tonight.R

/**
 * 2×2：只有记账。
 *
 * 这是四张卡里最窄的一张 —— 2 格宽 = 110dp，扣掉左右内边距只剩 82dp，
 * 一行 12sp 中文大约 4 个字。所以：
 *
 *  - 金额用 autoSize（15~24sp）而不是固定字号。「¥128.50」24sp 放得下，
 *    「¥1,234.56」放不下，固定字号只会在四位数以上那几天突然截断，
 *    而截断的样子（「¥1,23…」）看起来像数据错了。
 *  - 明细只放 2 笔，单列（备注左、金额右）。3 笔每行只剩 17dp，
 *    字号得压到 11sp，整张卡会显得拥挤。
 *  - 不放「今天」这类日期抬头 —— 一张只有钱的卡不写日期也没人会认错。
 */
class LedgerWidgetProvider : BaseWidgetProvider() {

    override val layoutRes = R.layout.widget_ledger

    // 这张卡没有任务区
    override fun taskCap(heightDp: Int): Int = 0

    override fun moneyCap(heightDp: Int): Int = if (heightDp >= 135) 3 else 2
}
