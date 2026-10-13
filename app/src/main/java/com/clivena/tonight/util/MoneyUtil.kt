package com.clivena.tonight.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

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
     * 月历格子里的紧凑写法 —— 那里只有一格宽，放不下 "¥128.50"。
     *
     * 12800 → "128"      只到元，分直接丢掉
     * 128000 → "1.3k"    上千就换单位，否则五个数字会把格子撑破
     *
     * 丢掉的分**只影响这一个格子的观感**，合计和明细仍然精确到分 ——
     * 月历是拿来看分布的，不是拿来对账的，对账有明细和汇总。
     */
    fun formatCompact(cents: Long): String {
        val yuan = cents / 100
        if (yuan < 1000L) return yuan.toString()
        val thousands = yuan / 1000.0
        return if (thousands < 9.95) {
            // 9.94 → "9.9k"，四舍五入到一位小数
            String.format(Locale.US, "%.1fk", (thousands * 10).roundToLong() / 10.0)
        } else {
            // 9.95 起进位到整数，否则会出现 "10.0k" 这种多余的写法
            "${thousands.roundToLong()}k"
        }
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
