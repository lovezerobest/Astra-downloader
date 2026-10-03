package com.astradownloader.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 应用数据库：各网盘平台的登录凭证。
 */
@Database(
    entities = [
        QuarkAccountEntity::class,
        UCAccountEntity::class,
        XunleiAccountEntity::class,
        BaiduAccountEntity::class,
        C139AccountEntity::class,
        Pan123AccountEntity::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun quarkAccountDao(): QuarkAccountDao
    abstract fun ucAccountDao(): UCAccountDao
    abstract fun xunleiAccountDao(): XunleiAccountDao
    abstract fun baiduAccountDao(): BaiduAccountDao
    abstract fun c139AccountDao(): C139AccountDao
    abstract fun pan123AccountDao(): Pan123AccountDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "astra_downloader.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}