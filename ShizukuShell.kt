package dev.garen.deviceboost

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Runs shell commands with Shizuku (shell-level) privileges, the same thing `rish` does in Termux. */
object ShizukuShell {
    data class Result(val code: Int, val output: String)

    fun running(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun authorized(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    /** Blocks until the command ends, so call it off the main thread. */
    fun exec(cmd: String): Result = try {
        // newProcess is private in Shizuku API 13+, so it is called by reflection.
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        var err = ""
        val t = Thread { err = p.errorStream.bufferedReader().readText() }
        t.start()
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        t.join()
        Result(code, (out + err).trim())
    } catch (e: Throwable) {
        val c = e.cause ?: e
        Result(-1, "Error: " + (c.message ?: c.javaClass.simpleName))
    }
}
