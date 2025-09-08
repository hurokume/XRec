package com.example.timerecorder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * タスク実行セッション（task_sessions）用の DAO。
 *
 * カラム:
 * - id: Long (PK, autoGenerate)
 * - taskId: Long (FK -> tasks.id)
 * - startedAt: Long (EpochMillis)
 * - endedAt: Long? (null = 実行中)
 */
@Dao
interface SessionDao {

    // -----------------------------
    // 読み取り系
    // -----------------------------

    /**
     * 現在実行中（endedAt = NULL）のセッションを1件取得。
     * 実行中は多くても1件という前提。
     */
    @Query("SELECT * FROM task_sessions WHERE endedAt IS NULL LIMIT 1")
    suspend fun getActive(): TaskSessionEntity?

    /**
     * 実行中セッションを監視（必要に応じて利用）。
     * UIでアクティブ表示をリアルタイム反映したい場合に使えます。
     */
    @Query("SELECT * FROM task_sessions WHERE endedAt IS NULL LIMIT 1")
    fun observeActive(): Flow<TaskSessionEntity?>

    /**
     * 指定した時刻より前に終了しているセッションのうち、直近の1件を取得。
     * （短時間セッションを“前のセッションに吸収”する設計で使うことがあります）
     */
    @Query(
        """
        SELECT * FROM task_sessions
        WHERE endedAt IS NOT NULL AND endedAt <= :startExclusive
        ORDER BY endedAt DESC
        LIMIT 1
    """
    )
    suspend fun getLastFinishedBefore(startExclusive: Long): TaskSessionEntity?

    // -----------------------------
    // 更新系
    // -----------------------------

    /**
     * セッションの終了時刻を確定・延長する。
     */
    @Query("UPDATE task_sessions SET endedAt = :newEnd WHERE id = :sessionId")
    suspend fun updateEnd(sessionId: Long, newEnd: Long)

    /**
     * セッションを削除（誤タップなど短時間セッションの破棄に使用）。
     */
    @Query("DELETE FROM task_sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: Long)

    /**
     * 新しいセッションを開始（endedAt = NULL を前提）。
     */
    @Insert
    suspend fun insert(entity: TaskSessionEntity): Long
}
