/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlinx.coroutines.internal.*
import org.w3c.dom.*
import kotlin.js.*

internal class ScheduledMessageQueue(private val dispatcher: SetTimeoutBasedDispatcher) : MessageQueue() {
    val processQueue: () -> Unit = ::process

    override fun schedule() {
        dispatcher.scheduleQueueProcessing()
    }

    override fun reschedule() {
        setTimeout(processQueue, 0)
    }
}

internal class NodeDispatcher(private val process: JsProcess) : SetTimeoutBasedDispatcher() {
    override fun scheduleQueueProcessing() {
        process.nextTick(messageQueue.processQueue)
    }
}

@Suppress("UNUSED_PARAMETER")
private fun subscribeToWindowMessages(window: Window, process: () -> Unit): Unit = js("""{
    const handler = (event) => {
        if (event.source == window && event.data == 'dispatchCoroutine') {
            event.stopPropagation();
            process();
        }
    }
    window.addEventListener('message', handler, true);
}""")

@Suppress("UNUSED_PARAMETER")
private fun createRescheduleMessagePoster(window: Window): () -> Unit =
    js("() => window.postMessage('dispatchCoroutine', '*')")

@Suppress("UNUSED_PARAMETER")
private fun createScheduleMessagePoster(process: () -> Unit): () -> Unit =
    js("() => Promise.resolve(0).then(process)")

internal class WindowMessageQueue(window: Window) : MessageQueue() {
    private val scheduleMessagePoster = createScheduleMessagePoster(::process)
    private val rescheduleMessagePoster = createRescheduleMessagePoster(window)
    init {
        subscribeToWindowMessages(window, ::process)
    }

    override fun schedule() {
        scheduleMessagePoster()
    }

    override fun reschedule() {
        rescheduleMessagePoster()
    }
}

// We need to reference global setTimeout and clearTimeout so that it works on Node.JS as opposed to
// using them via "window" (which only works in browser)
internal external fun setTimeout(handler: () -> Unit, timeout: Int): Int

// d8 doesn't have clearTimeout
@Suppress("UNUSED_PARAMETER")
internal fun clearTimeout(handle: Int): Unit =
    js("{ if (typeof clearTimeout !== 'undefined') clearTimeout(handle); }")
@Suppress("UNUSED_PARAMETER")
internal fun setTimeout(window: WindowOrWorkerGlobalScope, handler: () -> Unit, timeout: Int): Int =
    js("window.setTimeout(handler, timeout)")