/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.di

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.commonmark.node.BlockQuote
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import ua.acclorite.book_story.data.local.room.BookDatabase
import ua.acclorite.book_story.data.local.room.DatabaseHelper
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCommonmarkParser(): Parser {
        return Parser
            .builder()
            .enabledBlockTypes(
                setOf(
                    Heading::class.java,
                    HtmlBlock::class.java,
                    ThematicBreak::class.java,
                    BlockQuote::class.java,
                    FencedCodeBlock::class.java,
                    IndentedCodeBlock::class.java,
                    ThematicBreak::class.java
                )
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideBookDatabase(app: Application): BookDatabase {
        return Room.databaseBuilder(
            app,
            BookDatabase::class.java,
            "book_db"
        ).setJournalMode(
            // Room would pick a rollback journal on a low-RAM device, and
            // DatabaseSnapshot can only tell a quiet copy from a torn one in WAL.
            RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING
        ).addMigrations(
            *DatabaseHelper.MANUAL_MIGRATIONS
        ).addCallback(
            DatabaseHelper.DEFAULT_CATEGORY_CALLBACK
        ).addCallback(
            DatabaseHelper.BUILT_IN_COLOR_PRESETS_CALLBACK
        ).build()
    }
}