package com.clivena.tonight.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 全 App 唯一的数据库。
 *
 * ##### 版本历史
 * - v1：tasks 单表（v0.1 / v0.2 都是它）
 * - v2：新增 expenses 表（v0.3 记账）。tasks 定义未做任何改动。
 * - v3：新增 journal 表（v0.6 日记）。tasks / expenses 定义都未做任何改动。
 *
 * ##### 关于升级安全
 * 迁移由 [MIGRATION_1_2] / [MIGRATION_2_3] 显式声明。**不使用 fallbackToDestructiveMigration()** ——
 * 那是「结构对不上就把库删了重建」，用户数据会在某一版升级时无声消失。
 * 宁可 App 闪退并让问题暴露出来，也不能悄悄清掉用户的账和日记。
 *
 * ##### 三张表的关系
 * 表之间不 join、不互相引用。唯一共享的东西是「日期」这个字符串的格式（yyyy-MM-dd）——
 * 这让「某一天都发生了什么」将来可以靠三次独立查询拼出来，而不必现在就把它们绑在一起。
 */
@Database(
    entities = [TaskEntity::class, ExpenseEntity::class, JournalEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun journalDao(): JournalDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tonight.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
