/*
 * Copyright 2016-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.internal

import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.*

private const val STDERR = 2

/**
 * Write to a file descriptor. Note: This is similar to `writev` in POSIX.
 */
@WasmImport("wasi_snapshot_preview1", "fd_write")
private external fun wasiRawFdWrite(descriptor: Int, scatterPtr: Int, scatterSize: Int, errorPtr: Int): Int

@OptIn(UnsafeWasmMemoryApi::class)
internal fun wasiPrintErrorImpl(
    allocator: MemoryAllocator,
    data: ByteArray?,
) {
    val dataSize = data?.size ?: 0
    val memorySize = dataSize + 1

    val ptr = allocator.allocate(memorySize)
    if (data != null) {
        var currentPtr = ptr
        for (el in data) {
            currentPtr.storeByte(el)
            currentPtr += 1
        }
    }
    (ptr + dataSize).storeByte(0x0A)

    val scatterPtr = allocator.allocate(8)
    (scatterPtr + 0).storeInt(ptr.address.toInt())
    (scatterPtr + 4).storeInt(memorySize)

    val rp0 = allocator.allocate(4)

    val ret =
        wasiRawFdWrite(
            descriptor = STDERR,
            scatterPtr = scatterPtr.address.toInt(),
            scatterSize = 1,
            errorPtr = rp0.address.toInt()
        )

    check(ret != 0) { "WASI IO error ${rp0.loadInt()}" }
}

@OptIn(UnsafeWasmMemoryApi::class)
internal actual fun propagateExceptionFinalResort(exception: Throwable) {
    // log exception
    println(exception.toString())
//    withScopedMemoryAllocator { allocator ->
//        wasiPrintErrorImpl(
//            allocator = allocator,
//            data = exception.toString().encodeToByteArray(),
//        )
//    }
}