package com.intent.launcher.domain

import com.intent.launcher.engine.ErrandCapture
import com.intent.launcher.store.IntentionFlowStore

/**
 * CASE-01 fixture: “Buy milk tonight” as a quick to-do / reminder —
 * not an exploring Intention.
 */
object ErrandScenario {
    const val TODO_ID = "errand-buy-milk-tonight"
    const val RAW_TEXT = "Buy milk tonight"
    const val EXPECTED_FIRST_ACTION = "Buy milk"
    const val EXPECTED_REMINDER = "Tonight"

    fun captureBuyMilkTonight(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        todoId: String = TODO_ID
    ): ErrandCapture.Result =
        ErrandCapture.capture(
            store = store,
            text = RAW_TEXT,
            nowEpochMs = nowEpochMs,
            todoId = todoId
        )

    fun complete(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        todoId: String = TODO_ID
    ): Boolean = ErrandCapture.complete(store, todoId, nowEpochMs)
}
