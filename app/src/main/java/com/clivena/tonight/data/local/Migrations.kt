package com.clivena.tonight.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2：新增 expenses 表。
 *
 * 这是本工程第一次动数据库结构。刻意只做「加一张新表」：
 * tasks 表一个字段都没碰，所以旧数据不需要搬运，也没有被覆盖的可能。
 *
 * ##### 这段 SQL 为什么必须逐字写对
 *
 * Room 在迁移结束后会**校验实际表结构与 Entity 的期望结构是否一致**：
 *   · 一致 → 正常打开，旧数据原样保留
 *   · 不一致 → 抛异常，App 当场闪退（但数据库文件完好，数据还在）
 * 所以最坏结果是「崩」，不是「数据没了」。真正会导致静默清库的只有
 * fallbackToDestructiveMigration() —— 本工程从头到尾没有用它，也不打算用。
 *
 * 基准文件（由 KSP 生成，可逐字对照）：
 *   app/schemas/com.clivena.tonight.data.local.AppDatabase/2.json
 */
val MIGRATION_1_2 = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `expenses` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`amountCents` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`remoteId` TEXT, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)")
    }
}

/**
 * v2 → v3：新增 journal 表。
 *
 * 和 v1→v2 同一种做法：**只加一张新表，一列都不碰旧表**。
 * tasks 和 expenses 的定义一个字没改，所以旧数据不需要搬运，也没有被覆盖的可能。
 *
 * 这一版和上一版的差别在于「主键」：journal 用日期做主键（一天一篇），
 * 所以没有自增 id、没有 AUTOINCREMENT，末尾是 `PRIMARY KEY(\`date\`)`。
 * 这行写法必须和 Room 的期望逐字一致，否则开库时校验不过、App 当场闪退。
 *
 * 基准文件：
 *   app/schemas/com.clivena.tonight.data.local.AppDatabase/3.json
 */
val MIGRATION_2_3 = object : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `journal` (" +
                "`date` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, " +
                "`mood` INTEGER, " +
                "`imageName` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`date`))"
        )
    }
}
