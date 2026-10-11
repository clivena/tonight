package com.clivena.tonight.util

import java.time.LocalDate
import java.time.LocalDateTime

object DateUtil {

    /**
     * 几点之后算是「晚上」。
     *
     * 注意：这个值只用来决定一件事归属到日历的哪一天，
     * 不会限制用户什么时候能规划（那会凭空增加摩擦）。
     */
    private const val EVENING_HOUR = 18

    /**
     * 今晚屏规划的目标日期。
     *
     * - 晚上 18 点之后打开 → 规划明天
     * - 白天打开           → 规划今天
     *
     * 任何时候都能规划，只是归属不同。这样「今晚写、明早做」
     * 这个核心动作在数据上才是成立的。
     */
    fun planDateFor(now: LocalDateTime = LocalDateTime.now()): LocalDate =
        if (now.hour >= EVENING_HOUR) now.toLocalDate().plusDays(1) else now.toLocalDate()

    fun today(): LocalDate = LocalDate.now()
}
