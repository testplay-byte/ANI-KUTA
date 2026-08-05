package com.confused.anikuta.error

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnikutaCrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        Log.e(TAG, "Uncaught exception on thread ${t.name}", e)
        try {
            File(context.filesDir, CRASH_FILE).writeText(buildReport(t, e))
        } catch (ioe: Exception) {
            Log.e(TAG, "Failed to write crash report", ioe)
        }
        try {
            val intent = Intent(context, ErrorActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
        } catch (ie: Exception) {
            Log.e(TAG, "Failed to launch ErrorActivity", ie)
        }
        Process.killProcess(Process.myPid())
        System.exit(10)
    }

    private fun buildReport(t: Thread, e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        return buildString {
            appendLine("=== ANIKUTA Crash Report ===")
            appendLine("Time: $time")
            appendLine("Thread: ${t.name} (id=${t.id})")
            appendLine("Process PID: ${Process.myPid()}")
            appendLine("Android API: ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.VERSION.RELEASE})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine()
            appendLine("Exception: ${e.javaClass.name}")
            appendLine("Message: ${e.message}")
            appendLine()
            appendLine("Stack trace:")
            appendLine(sw.toString())
        }
    }

    companion object {
        private const val TAG = "AnikutaCrash"
        const val CRASH_FILE = "last_crash.txt"

        fun getLastCrash(context: Context): String? = try {
            val file = File(context.filesDir, CRASH_FILE)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) { null }

        fun clearLastCrash(context: Context) {
            try { File(context.filesDir, CRASH_FILE).delete() }
            catch (e: Exception) { Log.w(TAG, "Failed to delete crash report", e) }
        }
    }
}
