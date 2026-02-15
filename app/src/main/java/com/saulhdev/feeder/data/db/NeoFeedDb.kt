/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Neo Feed Team
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.saulhdev.feeder.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.saulhdev.feeder.data.db.dao.FeedArticleDao
import com.saulhdev.feeder.data.db.dao.FeedSourceDao
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.ArticleIdWithLink
import com.saulhdev.feeder.data.db.models.Feed
import org.threeten.bp.ZonedDateTime
import java.util.UUID

const val ID_UNSET: Long = 0
const val ID_ALL: Long = -1L

@Database(
    entities = [
        Feed::class,
        Article::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(
            from = 3,
            to = 4,
            spec = NeoFeedDb.MigrationMoveToArticle::class,
        ),
        AutoMigration(
            from = 4,
            to = 5,
            spec = NeoFeedDb.MigrationRemoveFeedArticle::class,
        ),
        AutoMigration(
            from = 5,
            to = 6,
            spec = NeoFeedDb.ReplaceThreetenInstances::class,
        ),
        AutoMigration(
            from = 6,
            to = 7,
            spec = NeoFeedDb.RemoveLegacyPubDate::class,
        ),
        AutoMigration(
            from = 7,
            to = 8,
        ),
    ],
    views = [
        ArticleIdWithLink::class,
    ],
)
@TypeConverters(Converters::class)
abstract class NeoFeedDb : RoomDatabase() {
    abstract fun feedSourceDao(): FeedSourceDao

    abstract fun feedArticleDao(): FeedArticleDao

    companion object {
        @Volatile
        private var instance: NeoFeedDb? = null

        fun getInstance(context: Context): NeoFeedDb =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }

        private fun buildDatabase(context: Context): NeoFeedDb =
            Room
                .databaseBuilder(context, NeoFeedDb::class.java, "NeoFeed")
                .addMigrations(*allMigrations)
                .build()
    }

    class MigrationMoveToArticle : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            super.onPostMigrate(db)
            val cursor = db.query("SELECT * FROM FeedArticle")
            val insertStmt =
                db.compileStatement(
                    """
                    INSERT INTO Article (
                        uuid, guid, title, plainTitle, imageUrl, enclosureLink,
                        plainSnippet, description, author,
                        pubDate, link, feedId, firstSyncedTime, primarySortTime,
                        categories, pinned, bookmarked
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                )

            cursor.use {
                while (it.moveToNext()) {
                    val guid = it.getString(it.getColumnIndexOrThrow("guid"))
                    val title = it.getString(it.getColumnIndexOrThrow("title"))
                    val plainTitle = it.getString(it.getColumnIndexOrThrow("plainTitle"))
                    val imageUrl = it.getString(it.getColumnIndexOrThrow("imageUrl"))
                    val enclosureLink = it.getString(it.getColumnIndexOrThrow("enclosureLink"))
                    val plainSnippet = it.getString(it.getColumnIndexOrThrow("plainSnippet"))
                    val description = it.getString(it.getColumnIndexOrThrow("description"))
                    val author = it.getString(it.getColumnIndexOrThrow("author"))
                    val pubDate = it.getLong(it.getColumnIndexOrThrow("pubDate"))
                    val link = it.getString(it.getColumnIndexOrThrow("link"))
                    val feedId = it.getLong(it.getColumnIndexOrThrow("feedId"))
                    val firstSyncedTime = it.getLong(it.getColumnIndexOrThrow("firstSyncedTime"))
                    val primarySortTime = it.getLong(it.getColumnIndexOrThrow("primarySortTime"))
                    val categoriesString = it.getString(it.getColumnIndexOrThrow("categories"))
                    val pinned = it.getInt(it.getColumnIndexOrThrow("pinned"))
                    val bookmarked = it.getInt(it.getColumnIndexOrThrow("bookmarked"))

                    val uuid = UUID.randomUUID().toString()

                    insertStmt.bindString(1, uuid)
                    insertStmt.bindString(2, guid)
                    insertStmt.bindString(3, title)
                    insertStmt.bindString(4, plainTitle)

                    if (imageUrl != null) {
                        insertStmt.bindString(5, imageUrl)
                    } else {
                        insertStmt.bindNull(5)
                    }

                    if (enclosureLink != null) {
                        insertStmt.bindString(6, enclosureLink)
                    } else {
                        insertStmt.bindNull(6)
                    }

                    insertStmt.bindString(7, plainSnippet)
                    insertStmt.bindString(8, description)

                    if (author != null) {
                        insertStmt.bindString(9, author)
                    } else {
                        insertStmt.bindNull(9)
                    }

                    if (pubDate != 0L) {
                        insertStmt.bindLong(10, pubDate)
                    } else {
                        insertStmt.bindNull(10)
                    }

                    if (link != null) {
                        insertStmt.bindString(11, link)
                    } else {
                        insertStmt.bindNull(11)
                    }

                    insertStmt.bindLong(12, feedId)
                    insertStmt.bindLong(13, firstSyncedTime)
                    insertStmt.bindLong(14, primarySortTime)
                    insertStmt.bindString(15, categoriesString)
                    insertStmt.bindLong(16, pinned.toLong())
                    insertStmt.bindLong(17, bookmarked.toLong())

                    insertStmt.executeInsert()
                }
            }
        }
    }

    @DeleteTable(tableName = "FeedArticle")
    class MigrationRemoveFeedArticle : AutoMigrationSpec

    class ReplaceThreetenInstances : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            val cursor = db.query("SELECT uuid, pubDate FROM article")
            val stmt = db.compileStatement("UPDATE article SET pubDateV2 = ? WHERE uuid = ?")
            cursor.use {
                while (it.moveToNext()) {
                    val uuid = it.getString(0)
                    val pubDateStr = it.getString(1)
                    val pubDateMillis = convertZDT2Millis(pubDateStr)

                    stmt.bindLong(1, pubDateMillis)
                    stmt.bindString(2, uuid)
                    stmt.executeUpdateDelete()
                }
            }
        }

        // TODO remove in two releases
        private fun convertZDT2Millis(pubDateStr: String?): Long {
            if (pubDateStr == null) return 0L
            return try {
                val zdt = ZonedDateTime.parse(pubDateStr)
                zdt.toInstant().toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }

    @DeleteColumn(tableName = "Article", columnName = "pubDate")
    class RemoveLegacyPubDate : AutoMigrationSpec
}

val allMigrations = arrayOf(Migration1To2, Migration2To3)

object Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE Feeds ADD COLUMN fullTextByDefault INTEGER NOT NULL DEFAULT 0
            """.trimIndent(),
        )
    }
}

object Migration2To3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE Feeds ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1
            """.trimIndent(),
        )
    }
}
