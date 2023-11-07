package com.github.onlaait.gallerranking

import kotlin.system.exitProcess

object DefaultExceptionHandler : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread?, e: Throwable?) {
        println(e?.stackTraceToString())
        exitProcess(999)
    }
}