package com.pietro.musicplayer.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pietro.musicplayer.data.db.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE songs ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")

            // Ricrea playlist_song_cross_ref con foreign keys e indici, rimuovendo eventuali record orfani
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlist_song_cross_ref_new` (
                    `playlistId` INTEGER NOT NULL,
                    `songId` INTEGER NOT NULL,
                    `addedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`playlistId`, `songId`),
                    FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `playlist_song_cross_ref_new` (`playlistId`, `songId`, `addedAt`)
                SELECT `playlistId`, `songId`, `addedAt` FROM `playlist_song_cross_ref`
                WHERE `playlistId` IN (SELECT `id` FROM `playlists`)
                  AND `songId` IN (SELECT `id` FROM `songs`)
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `playlist_song_cross_ref`")
            db.execSQL("ALTER TABLE `playlist_song_cross_ref_new` RENAME TO `playlist_song_cross_ref`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_cross_ref_playlistId` ON `playlist_song_cross_ref` (`playlistId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_cross_ref_songId` ON `playlist_song_cross_ref` (`songId`)")

            // Ricrea play_history con foreign key e indice, rimuovendo eventuali record orfani
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `play_history_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `songId` INTEGER NOT NULL,
                    `playedAt` INTEGER NOT NULL,
                    `completionPercent` INTEGER NOT NULL,
                    FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `play_history_new` (`id`, `songId`, `playedAt`, `completionPercent`)
                SELECT `id`, `songId`, `playedAt`, `completionPercent` FROM `play_history`
                WHERE `songId` IN (SELECT `id` FROM `songs`)
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `play_history`")
            db.execSQL("ALTER TABLE `play_history_new` RENAME TO `play_history`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_history_songId` ON `play_history` (`songId`)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): MusicDatabase =
        Room.databaseBuilder(ctx, MusicDatabase::class.java, "music_db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun provideSongDao(db: MusicDatabase): SongDao = db.songDao()
    @Provides fun providePlaylistDao(db: MusicDatabase): PlaylistDao = db.playlistDao()
    @Provides fun providePlayHistoryDao(db: MusicDatabase): PlayHistoryDao = db.playHistoryDao()
}
