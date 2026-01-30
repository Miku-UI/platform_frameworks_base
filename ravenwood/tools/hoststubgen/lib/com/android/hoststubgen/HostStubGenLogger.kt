/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.hoststubgen

import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.Writer
import java.util.Collections

val log: HostStubGenLogger
    get() {
        var log = threadLocalLogger.get()
        if (log == null) {
            log = BufferedLogger(mainLogger)
            threadLocalLogger.set(log)
            allLoggers.add(log)
        }
        return log
    }
val logOptions = HostStubGenLoggerOptions().setConsoleLogLevel(LogLevel.Info)

private val mainLogger = HostStubGenLogger(logOptions)
private val threadLocalLogger = ThreadLocal<HostStubGenLogger>().apply { set(mainLogger) }
val allLoggers: MutableList<HostStubGenLogger> =
    Collections.synchronizedList(ArrayList<HostStubGenLogger>()).apply { add(mainLogger) }

/** Logging level */
enum class LogLevel {
    None,
    Error,
    Warn,
    Info,
    Verbose,
    Debug,
}

/**
 * Simple logging class.
 *
 * By default, it has no printers set. Use [setConsoleLogLevel] or [addFilePrinter] to actually
 * write log.
 */
open class HostStubGenLogger(val options: HostStubGenLoggerOptions) {
    protected var indentLevel: Int = 0

    constructor(other: HostStubGenLogger) : this(other.options) {
        indentLevel = other.indentLevel
    }

    protected inline fun forPrinters(callback: (LogPrinter) -> Unit) {
        synchronized(options.printers) {
            options.printers.forEach {
                callback(it)
            }
        }
    }

    /** Flush all the printers */
    open fun flush() {
        forPrinters { it.flush() }
    }

    fun indent() {
        indentLevel++
    }

    fun unindent() {
        if (indentLevel <= 0) {
            throw IllegalStateException("Unbalanced unindent() call.")
        }
        indentLevel--
    }

    inline fun <T> withIndent(block: () -> T): T {
        try {
            indent()
            return block()
        } finally {
            unindent()
        }
    }

    open fun println(level: LogLevel, message: String) {
        if (message.isEmpty()) {
            return // Don't print an empty message.
        }
        forPrinters {
            if (it.logLevel.ordinal >= level.ordinal) {
                it.println(indentLevel, message)
            }
        }
    }

    fun println(level: LogLevel, format: String, vararg args: Any?) {
        if (options.isEnabled(level)) {
            println(level, format.format(*args))
        }
    }

    /** Log an error. */
    fun e(message: String) {
        println(LogLevel.Error, message)
    }

    /** Log an error. */
    fun e(format: String, vararg args: Any?) {
        println(LogLevel.Error, format, *args)
    }

    /** Log a warning. */
    fun w(message: String) {
        println(LogLevel.Warn, message)
    }

    /** Log a warning. */
    fun w(format: String, vararg args: Any?) {
        println(LogLevel.Warn, format, *args)
    }

    /** Log an info message. */
    fun i(message: String) {
        println(LogLevel.Info, message)
    }

    /** Log an info message. */
    fun i(format: String, vararg args: Any?) {
        println(LogLevel.Info, format, *args)
    }

    /** Log a verbose message. */
    fun v(message: String) {
        println(LogLevel.Verbose, message)
    }

    /** Log a verbose message. */
    fun v(format: String, vararg args: Any?) {
        println(LogLevel.Verbose, format, *args)
    }

    /** Log a debug message. */
    fun d(message: String) {
        println(LogLevel.Debug, message)
    }

    /** Log a debug message. */
    fun d(format: String, vararg args: Any?) {
        println(LogLevel.Debug, format, *args)
    }

    inline fun <T> logTime(level: LogLevel, message: String, block: () -> T): Double {
        var ret: Double = -1.0
        val start = System.currentTimeMillis()
        try {
            block()
        } finally {
            val end = System.currentTimeMillis()
            ret = (end - start) / 1000.0
            if (options.isEnabled(level)) {
                println(level, "%s: took %.1f second(s).".format(message, (end - start) / 1000.0))
            }
        }
        return ret
    }

    /** Do an "i" log with how long it took. */
    inline fun <T> iTime(message: String, block: () -> T): Double {
        return logTime(LogLevel.Info, message, block)
    }

    /** Do a "v" log with how long it took. */
    inline fun <T> vTime(message: String, block: () -> T): Double {
        return logTime(LogLevel.Verbose, message, block)
    }

    /** Do a "d" log with how long it took. */
    inline fun <T> dTime(message: String, block: () -> T): Double {
        return logTime(LogLevel.Debug, message, block)
    }

    /**
     * Similar to the other "xTime" methods, but the message is not supposed to be printed.
     * It's only used to measure the duration with the same interface as other log methods.
     */
    inline fun <T> nTime(block: () -> T): Double {
        return logTime(LogLevel.Debug, "", block)
    }

    inline fun forVerbose(block: () -> Unit) {
        if (options.isEnabled(LogLevel.Verbose)) {
            block()
        }
    }

    inline fun forDebug(block: () -> Unit) {
        if (options.isEnabled(LogLevel.Debug)) {
            block()
        }
    }

    /** Return a Writer for a given log level. */
    fun getWriter(level: LogLevel): Writer {
        return MultiplexingWriter(level)
    }

    private inner class MultiplexingWriter(private val level: LogLevel) : Writer() {
        override fun close() {
            flush()
        }

        override fun flush() {
            this@HostStubGenLogger.flush()
        }

        override fun write(cbuf: CharArray, off: Int, len: Int) {
            String(cbuf, off, len).lines().forEach {
                println(level, it)
            }
        }
    }
}

/**
 * A logging class that only write outputs when [flush] is called.
 */
private class BufferedLogger(base: HostStubGenLogger) : HostStubGenLogger(base) {
    val output = mutableListOf<Triple<Int, LogLevel, String>>()

    override fun flush() {
        forPrinters {
            output.forEach { (indent, level, message) ->
                if (it.logLevel.ordinal >= level.ordinal) {
                    it.println(indent, message)
                }
            }
            it.flush()
        }
        output.clear()
    }

    override fun println(level: LogLevel, message: String) {
        if (message.isEmpty()) {
            return // Don't print an empty message.
        }
        output.add(Triple(indentLevel, level, message))
    }
}

class HostStubGenLoggerOptions {
    val printers = mutableListOf<LogPrinter>()

    private var consolePrinter: LogPrinter? = null

    private var maxLogLevel = LogLevel.None

    private fun updateMaxLogLevel() {
        maxLogLevel = LogLevel.None

        printers.forEach {
            if (maxLogLevel < it.logLevel) {
                maxLogLevel = it.logLevel
            }
        }
    }

    private fun addPrinter(printer: LogPrinter) {
        printers.add(printer)
        updateMaxLogLevel()
    }

    private fun removePrinter(printer: LogPrinter) {
        printers.remove(printer)
        updateMaxLogLevel()
    }

    fun setConsoleLogLevel(level: LogLevel): HostStubGenLoggerOptions {
        // If there's already a console log printer set, remove it, and then add a new one
        consolePrinter?.let {
            removePrinter(it)
        }
        val cp = StreamPrinter(level, PrintWriter(System.out))
        addPrinter(cp)
        consolePrinter = cp

        return this
    }

    fun addFilePrinter(level: LogLevel, logFilename: String): HostStubGenLoggerOptions {
        addPrinter(StreamPrinter(level, PrintWriter(BufferedOutputStream(
            FileOutputStream(logFilename)))))

        log.i("Log file set: $logFilename for $level")

        return this
    }

    fun isEnabled(level: LogLevel): Boolean {
        return level.ordinal <= maxLogLevel.ordinal
    }

    /**
     * Handle log-related command line arguments.
     */
    fun maybeHandleCommandLineArg(currentArg: String, nextArgProvider: () -> String): Boolean {
        when (currentArg) {
            "-v", "--verbose" -> setConsoleLogLevel(LogLevel.Verbose)
            "-d", "--debug" -> setConsoleLogLevel(LogLevel.Debug)
            "-q", "--quiet" -> setConsoleLogLevel(LogLevel.None)
            "--verbose-log" -> addFilePrinter(LogLevel.Verbose, nextArgProvider())
            "--debug-log" -> addFilePrinter(LogLevel.Debug, nextArgProvider())
            else -> return false
        }
        return true
    }
}

interface LogPrinter {
    val logLevel: LogLevel

    fun println(indent: Int, message: String)

    fun write(cbuf: CharArray, off: Int, len: Int)

    fun flush()
}

private class StreamPrinter(
    override val logLevel: LogLevel,
    val out: PrintWriter,
) : LogPrinter {
    override fun println(indent: Int, message: String) {
        out.print("  ".repeat(indent))
        out.println(message)
    }

    override fun write(cbuf: CharArray, off: Int, len: Int) {
        out.write(cbuf, off, len)
    }

    override fun flush() {
        out.flush()
    }
}
