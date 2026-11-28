package com.aistudio.testbaza.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        QuizBankEntity::class,
        QuizQuestionEntity::class,
        ExamAttemptHistoryEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(QuizTypeConverters::class)
abstract class QuizDatabase : RoomDatabase() {
    abstract fun quizDao(): QuizDao

    companion object {
        @Volatile
        private var INSTANCE: QuizDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quiz_questions ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE quiz_questions ADD COLUMN correctCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE quiz_questions ADD COLUMN totalTimeSeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE quiz_questions ADD COLUMN lastTimeSeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE quiz_questions ADD COLUMN timedCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exam_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bankId` INTEGER NOT NULL,
                        `bankTitle` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `isTimedExam` INTEGER NOT NULL,
                        `totalQuestions` INTEGER NOT NULL,
                        `correctCount` INTEGER NOT NULL,
                        `wrongCount` INTEGER NOT NULL,
                        `unansweredCount` INTEGER NOT NULL,
                        `totalElapsedSeconds` INTEGER NOT NULL,
                        `durationLimitMinutes` INTEGER NOT NULL,
                        `timedOut` INTEGER NOT NULL,
                        `wrongDetailsJson` TEXT NOT NULL,
                        `questionTimingsJson` TEXT NOT NULL,
                        FOREIGN KEY(`bankId`) REFERENCES `quiz_banks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_history_bankId` ON `exam_history` (`bankId`)")
            }
        }

        fun getInstance(context: Context): QuizDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuizDatabase::class.java,
                    "testbaza_quiz.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
