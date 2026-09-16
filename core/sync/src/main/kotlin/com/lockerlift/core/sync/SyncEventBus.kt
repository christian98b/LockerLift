package com.lockerlift.core.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lightweight in-process reactive event bus for Wearable Data Layer sync signals,
 * such as queue flush completion notifications.
 */
object SyncEventBus {

    private val _flushCompletedEvents = MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 1)
    val flushCompletedEvents: SharedFlow<Int> = _flushCompletedEvents.asSharedFlow()

    fun notifyFlushCompleted(count: Int) {
        _flushCompletedEvents.tryEmit(count)
    }

    fun reset() {
        _flushCompletedEvents.resetReplayCache()
    }
}
