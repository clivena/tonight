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
 *
 * ##### 关于升级安全
 * 迁移由 [MIGRATION_1_2] 显式声明。**不使用 fallbackToDestructiveMigration()** ——
 * 那是「结构对不上就把库删了重建」，用户数据会在某一版升级时无声消失。
 * 宁可 App 闪退并让问题暴露出来，也不能悄悄清掉用户的账。
 */
@Database(
    entities = [TaskEntity::class, ExpenseEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun expenseDao(): ExpenseDao

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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
