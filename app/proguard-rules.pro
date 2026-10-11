# Room 生成的实现类，不需要混淆（v0.1 未开启混淆，先备着）
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
