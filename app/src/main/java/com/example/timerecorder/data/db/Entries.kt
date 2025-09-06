package com.example.timerecorder.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * タスクを1テーブルで階層管理：
 * 親は parentId=null、子は親の id を持つ（孫以降も同様に可能）
 */
@Entity(
    tableName = "tasks",
    indices = [Index("parentId")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val parentId: Long? = null,      // 親なら null
    val colorInt: Int? = null,       // 親カテゴリ色（子にも入れておくとUIで便利）
    val sortOrder: Int = 0
)

/** 親 → 直下の子 のリレーション */
data class ParentWithChildren(
    @Embedded val parent: TaskEntity,
    @Relation(
        entity = TaskEntity::class,
        parentColumn = "id",
        entityColumn = "parentId"
    )
    val children: List<TaskEntity>
)

/** 現在アクティブなタスク（常に0 or 1件） */
@Entity(tableName = "active_task_singleton")
data class ActiveTaskEntity(
    @PrimaryKey val singletonId: Int = 0,   // 常に 0 の固定ID
    val taskId: Long,
    val startedAtEpochMs: Long              // 押下開始時刻
)

/** 押下履歴（タスク種別・押下時刻・期間） */
@Entity(
    tableName = "task_events",
    indices = [Index("taskId")],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TaskEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    val pressedAtEpochMs: Long,   // 押下開始
    val durationMillis: Long      // 期間（ミリ秒）
)

/** active と task の結合表示用（Repository / ViewModel が参照） */
data class ActiveWithTask(
    @Embedded val active: ActiveTaskEntity,
    @Relation(
        parentColumn = "taskId",
        entityColumn = "id"
    )
    val task: TaskEntity
)
