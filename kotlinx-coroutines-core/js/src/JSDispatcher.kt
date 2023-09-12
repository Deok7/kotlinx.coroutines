/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import org.w3c.dom.*
import kotlin.js.Promise

internal class ScheduledMessageQueue(private val dispatcher: SetTimeoutBasedDispatcher) : MessageQueue() {
    val processQueue: dynamic = { process() }

    override fun schedule() {
        dispatcher.scheduleQueueProcessing()
    }

    override fun reschedule() {
        setTimeout(processQueue, 0)
    }
}

internal object NodeDispatcher : SetTimeoutBasedDispatcher() {
    override fun scheduleQueueProcessing() {
        process.nextTick(messageQueue.processQueue)
    }
}

internal class WindowMessageQueue(private val window: Window) : MessageQueue() {
    private val messageName = "dispatchCoroutine"

    init {
        window.addEventListener("message", { event: dynamic ->
            if (event.source == window && event.data == messageName) {
                event.stopPropagation()
                process()
            }
        }, true)
    }

    override fun schedule() {
        Promise.resolve(Unit).then({ process() })
    }

    override fun reschedule() {
        window.postMessage(messageName, "*")
    }
}

// We need to reference global setTimeout and clearTimeout so that it works on Node.JS as opposed to
// using them via "window" (which only works in browser)
internal external fun setTimeout(handler: dynamic, timeout: Int = definedExternally): Int
internal external fun clearTimeout(handle: Int = definedExternally)
internal fun setTimeout(window: WindowOrWorkerGlobalScope, handler: () -> Unit, timeout: Int): Int =
    window.setTimeout(handler, timeout)