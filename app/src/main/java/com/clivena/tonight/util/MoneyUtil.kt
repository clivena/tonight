package com.clivena.tonight.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

/**
 * 金额换算。
 *
 * 全 App 内部只用「分」（Long），元只出现在两个端点：用户输入、界面显示。
 * 中间层（仓储、聚合 SQL、领域模型）一律不出现小数。
 */
object MoneyUtil {

    /** 只收「数字 + 最多两位小数」，整数部分最多 7 位（上限 999 万元） */
    private val INPUT_PATTERN = Regex("""^\d{1,7}(\.\d{1,2})?$""")

    /** 3200 → "¥32.00" */
    fun format(cents: Long): String {
        val negative = cents < 0
        val abs = abs(cents)
        val body = String.format(Locale.US, "%d.%02d", abs / 100, abs % 100)
        return if (negative) "-¥$body" else "¥$body"
    }

    /**
     * 输入框 → 分。接受 "32"、"32.5"、"32.50"，也容忍前后空格和顺手带上的 ¥。
     *
     * 其余一律返回 null：超两位小数、非数字、负数、空串。
     * 刻意不去「猜」用户的意思（把 32.567 悄悄四舍五入成 32.57 之类）——
     * 账本上最不该有的就是系统替你改过的数字。
     */
    fun parseToCents(input: String): Long? {
        val cleaned = input.trim().removePrefix("¥").removePrefix("￥").trim()
        if (!INPUT_PATTERN.matches(cleaned)) return null
        return runCatching {
            BigDecimal(cleaned)
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        }.getOrNull()
    }

    /** 分 → 编辑框里好改的写法：3200 → "32"，3250 → "32.50" */
    fun toPlainInput(cents: Long): String {
        val abs = abs(cents)
        return if (abs % 100L == 0L) {
            (abs / 100L).toString()
        } else {
            String.format(Locale.US, "%d.%02d", abs / 100, abs % 100)
        }
    }
}
