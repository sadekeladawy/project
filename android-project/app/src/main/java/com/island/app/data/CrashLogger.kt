package com.island.app.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val TAG = "IslandCrash"
    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_ENTRIES = 5
    private const val MAX_ENTRY_CHARS = 4000
    private const val ENTRY_DELIMITER = "\n--- ISLAND LOG ENTRY ---\n"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                prependLog(
                    context = appContext,
                    severity = "FATAL",
                    header = "Uncaught exception on thread ${thread.name}",
                    throwable = throwable
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun recordNonFatal(context: Context, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
        runCatching {
            prependLog(
                context = context.applicationContext,
                severity = "NON-FATAL",
                header = message,
                throwable = throwable
            )
        }
    }

    fun readLastCrash(context: Context): String? {
        return runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
        }.getOrNull()
    }

    fun clearLastCrash(context: Context) {
        runCatching {
            File(context.filesDir, FILE_NAME).delete()
        }
    }

    @Synchronized
    private fun prependLog(
        context: Context,
        severity: String,
        header: String,
        throwable: Throwable?
    ) {
        val trace = if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            "\n${sw.toString()}"
        } else {
            ""
        }
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val newEntry = "[$severity] $timestamp — $header$trace".take(MAX_ENTRY_CHARS)

        val file = File(context.filesDir, FILE_NAME)
        val existingEntries = if (file.exists()) {
            file.readText()
                .split(ENTRY_DELIMITER)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        val updatedEntries = (listOf(newEntry) + existingEntries)
            .take(MAX_ENTRIES)
            .map { it.take(MAX_ENTRY_CHARS) }

        file.writeText(updatedEntries.joinToString(ENTRY_DELIMITER))
    }
}
