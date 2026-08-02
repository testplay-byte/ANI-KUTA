package com.confused.anikuta.core.common

import kotlinx.coroutines.Dispatchers

/**
 * Injectable dispatcher provider for testability.
 * All network/DB operations go through IO; UI through Main.
 */
interface DispatcherProvider {
    val main: kotlinx.coroutines.CoroutineDispatcher
    val io: kotlinx.coroutines.CoroutineDispatcher
    val default: kotlinx.coroutines.CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val main = Dispatchers.Main
    override val io = Dispatchers.IO
    override val default = Dispatchers.Default
}
