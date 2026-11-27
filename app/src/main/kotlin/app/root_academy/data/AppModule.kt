package app.root_academy.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext c: Context): AppDatabase =
        Room.databaseBuilder(c, AppDatabase::class.java, "root_academy.db").build()

    @Provides fun clock(): Clock = SystemClock

    @Provides @Singleton
    fun progress(db: AppDatabase, clock: Clock) = ProgressRepository(db, clock)

    @Provides @Singleton
    fun settings(@ApplicationContext c: Context) = SettingsRepository(c)

    @Provides @Singleton
    fun content(@ApplicationContext c: Context) = ContentRepository(c)
}
