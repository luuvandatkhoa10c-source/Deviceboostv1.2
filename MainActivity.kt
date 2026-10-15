package dev.garen.deviceboost

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import rikka.shizuku.Shizuku
import java.util.Locale

class MainActivity : Activity() {

    private data class Task(val title: String, val desc: String, val cmd: String, val undo: String? = null)

    // Taken from the user's own phone cheat sheet (S25 Ultra, One UI 9).
    private val tasks = listOf(
        Task(
            "Main fix: smoother UI",
            "Animations 0.5x, recompiles SystemUI and the launcher, then restarts SystemUI " +
                "(the screen flickers for a second). Undo resets animations to 1x.",
            "for k in window_animation_scale transition_animation_scale animator_duration_scale; " +
                "do settings put global \$k 0.5; done; " +
                "cmd package compile -m speed-profile -f com.android.systemui com.sec.android.app.launcher; " +
                "am crash com.android.systemui",
            "for k in window_animation_scale transition_animation_scale animator_duration_scale; " +
                "do settings put global \$k 1.0; done"
        ),
        Task(
            "Keep background apps alive (untested on One UI 9)",
            "Turns off phantom-process monitoring, raises the phantom process limit and " +
                "enables fixed performance mode. Undo restores the defaults.",
            "settings put global settings_enable_monitor_phantom_procs false; " +
                "device_config put activity_manager max_phantom_processes 2147483647; " +
                "cmd power set-fixed-performance-mode-enabled true",
            "settings put global settings_enable_monitor_phantom_procs true; " +
                "cmd power set-fixed-performance-mode-enabled false; " +
                "settings put global adaptive_battery_management_enabled 1"
        ),
        Task(
            "Turn off adaptive battery (costs battery)",
            "Optional extra for keeping apps alive. Undo turns it back on.",
            "settings put global adaptive_battery_management_enabled 0",
            "settings put global adaptive_battery_management_enabled 1"
        ),
        Task(
            "Clear cached background apps",
            "Kills cached background processes to free RAM.",
            "am kill-all"
        ),
        Task(
            "Full app recompile (slow, charge first)",
            "Compiles every app with speed-profile. Can take a long time.",
            "cmd package compile -m speed-profile -a"
        )
    )

    private lateinit var statusText: TextView
    private lateinit var statusBtn: Button
    private lateinit var logView: TextView
    private lateinit var grantBox: LinearLayout
    private lateinit var current: TextView
    private lateinit var animControls: LinearLayout
    private lateinit var seekLabel: TextView
    private lateinit var seek: SeekBar
    private lateinit var input: EditText
    private lateinit var savedBox: LinearLayout

    private val logBuf = StringBuilder()
    private var busy = false
    private var grantTried = false
    private val prefs by lazy { getSharedPreferences("deviceboost", MODE_PRIVATE) }

    private val binderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { refresh() } }
    private val deadListener = Shizuku.OnBinderDeadListener { runOnUiThread { refresh() } }
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        runOnUiThread {
            grantTried = false
            refresh()
        }
    }

    private val grantCommand: String
        get() = "pm grant $packageName ${AnimSettings.PERMISSION}"

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun LinearLayout.add(v: View, top: Int = 8) {
        addView(
            v,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(top) }
        )
    }

    private fun weighted() =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun label(text: String, sp: Float = 14f, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = sp
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun button(text: String, onClick: () -> Unit) =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            setOnClickListener { onClick() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            addView(root)
            // Targeting SDK 35 draws edge-to-edge, so keep content clear of the bars.
            setOnApplyWindowInsetsListener { v, insets ->
                val b = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(b.left, b.top, b.right, b.bottom)
                insets
            }
        }
        setContentView(scroll)

        root.add(label("Device Boost", 28f, bold = true), 0)
        root.add(label("Your phone cheat sheet as one-tap buttons. No Termux needed."), 4)

        // ---- Shizuku status ----
        statusText = label("", 14f)
        root.add(statusText, 16)
        statusBtn = Button(this).apply { isAllCaps = false }
        root.add(statusBtn, 6)

        // Fallback card, shown only when Shizuku is not connected and the permission is missing.
        grantBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        grantBox.add(
            label(
                "Without Shizuku you can still use animation speed after running this once " +
                    "(Termux or ADB):"
            ),
            0
        )
        grantBox.add(
            label("rish -c \"$grantCommand\"").apply {
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
            },
            6
        )
        grantBox.add(button("Copy command") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("grant", grantCommand))
            toast("Copied")
        }, 6)
        root.add(grantBox, 12)

        // ---- Output log ----
        root.add(label("Output", 18f, bold = true), 20)
        logView = label("Nothing run yet.", 12f).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        root.add(logView, 6)
        val logRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        logRow.addView(button("Copy log") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("log", logBuf.toString()))
            toast("Log copied")
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        logRow.addView(button("Clear log") {
            logBuf.setLength(0)
            logView.text = "Nothing run yet."
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.add(logRow, 4)

        // ---- Optimize tasks ----
        root.add(label("Optimize", 18f, bold = true), 24)
        tasks.forEach { t ->
            root.add(label(t.title, 15f, bold = true), 16)
            root.add(label(t.desc, 12f), 2)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(button("Run") { runShell(t.title, t.cmd) }, weighted())
            val u = t.undo
            if (u != null) row.addView(button("Undo") { runShell("Undo: " + t.title, u) }, weighted())
            root.add(row, 4)
        }

        // ---- Animation speed ----
        root.add(label("Animation speed", 18f, bold = true), 28)
        current = label("", 14f)
        root.add(current, 6)
        animControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf(
            "Default (1.00x)" to 1f,
            "Smooth (0.50x)" to 0.5f,
            "Snappy (0.30x)" to 0.3f,
            "Off (0x)" to 0f
        ).forEach { (name, value) ->
            animControls.add(button(name) { applyAnim(value) }, 6)
        }
        seekLabel = label("Custom: 1.00x", 14f)
        animControls.add(seekLabel, 16)
        seek = SeekBar(this).apply {
            max = 40 // 0.00x .. 2.00x in 0.05 steps
            progress = 20
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    seekLabel.text = "Custom: " + AnimSettings.format(p * 0.05f)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        animControls.add(seek, 4)
        animControls.add(button("Apply custom") { applyAnim(seek.progress * 0.05f) }, 8)
        root.add(animControls, 8)

        // ---- Your own commands ----
        root.add(label("Your commands", 18f, bold = true), 28)
        root.add(
            label("Paste any shell command you normally run in Termux. Save it to keep a one-tap button.", 12f),
            4
        )
        input = EditText(this).apply {
            hint = "e.g. settings put global ..."
            typeface = Typeface.MONOSPACE
            textSize = 13f
            minLines = 2
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        root.add(input, 6)
        val cmdRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cmdRow.addView(button("Run") {
            val c = input.text.toString().trim()
            if (c.isEmpty()) toast("Type a command first") else runShell("Custom command", c)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        cmdRow.addView(button("Save") {
            val c = input.text.toString().trim()
            if (c.isEmpty()) {
                toast("Type a command first")
            } else {
                val l = loadSaved()
                if (!l.contains(c)) l.add(c)
                storeSaved(l)
                renderSaved()
                toast("Saved")
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.add(cmdRow, 4)
        root.add(label("Saved: tap to run, long-press to remove", 12f), 12)
        savedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.add(savedBox, 4)
        renderSaved()

        root.add(label("Also in Settings (biggest effect)", 18f, bold = true), 28)
        root.add(
            label(
                "- Accessibility > Visibility enhancements > Reduce transparency and blur\n" +
                    "- Battery > Background usage limits > turn off \"Put unused apps to sleep\"\n" +
                    "- Keep the phone cool: heat throttling beats any tweak",
                13f
            ),
            6
        )

        refresh()

        try {
            Shizuku.addBinderReceivedListenerSticky(binderListener)
            Shizuku.addBinderDeadListener(deadListener)
            Shizuku.addRequestPermissionResultListener(permListener)
        } catch (e: Throwable) {
            // Shizuku missing: the app still works for animation speed.
        }
    }

    override fun onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderListener)
            Shizuku.removeBinderDeadListener(deadListener)
            Shizuku.removeRequestPermissionResultListener(permListener)
        } catch (e: Throwable) {
            // ignore
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ---------------------------------------------------------------- actions

    private fun runShell(title: String, cmd: String) {
        if (!ShizukuShell.authorized()) {
            toast("Start Shizuku and allow access first")
            return
        }
        if (busy) {
            toast("Another task is still running")
            return
        }
        busy = true
        log("> $title\n$ $cmd\n(running...)")
        Thread {
            val r = ShizukuShell.exec(cmd)
            runOnUiThread {
                busy = false
                val body = if (r.output.isBlank()) "(no output)" else r.output
                log("$body\n[exit ${r.code}]")
                refresh()
            }
        }.start()
    }

    private fun applyAnim(v: Float) {
        val s = String.format(Locale.US, "%.2f", v)
        if (AnimSettings.hasPermission(this)) {
            if (AnimSettings.setAll(this, v)) {
                toast("Animations set to " + AnimSettings.format(v))
            } else {
                toast("Android refused the change")
            }
            refresh()
        } else if (ShizukuShell.authorized()) {
            runShell(
                "Animations " + AnimSettings.format(v),
                "settings put global window_animation_scale $s; " +
                    "settings put global transition_animation_scale $s; " +
                    "settings put global animator_duration_scale $s"
            )
        } else {
            toast("Connect Shizuku first")
        }
    }

    private fun refresh() {
        val running = ShizukuShell.running()
        val authorized = ShizukuShell.authorized()

        when {
            authorized -> {
                statusText.text = "Shizuku connected. All tasks are ready."
                statusBtn.visibility = View.GONE
            }
            running -> {
                statusText.text = "Shizuku is running. Allow Device Boost to use it."
                statusBtn.text = "Allow access"
                statusBtn.setOnClickListener { requestShizuku() }
                statusBtn.visibility = View.VISIBLE
            }
            else -> {
                statusText.text = "Shizuku is not running. Open Shizuku and start it " +
                    "(Wireless debugging), then come back here."
                statusBtn.text = "Open Shizuku"
                statusBtn.setOnClickListener { openShizuku() }
                statusBtn.visibility = View.VISIBLE
            }
        }

        val hasPerm = AnimSettings.hasPermission(this)
        grantBox.visibility = if (hasPerm || authorized) View.GONE else View.VISIBLE
        animControls.alpha = if (hasPerm || authorized) 1f else 0.4f

        val (w, t, a) = AnimSettings.read(this)
        current.text = "Now: window ${AnimSettings.format(w)} - " +
            "transition ${AnimSettings.format(t)} - animator ${AnimSettings.format(a)}"

        // With Shizuku connected, grant the secure-settings permission automatically (once),
        // so the Quick Settings tile works without Shizuku later.
        if (authorized && !hasPerm && !grantTried) {
            grantTried = true
            Thread {
                ShizukuShell.exec(grantCommand)
                runOnUiThread { refresh() }
            }.start()
        }
    }

    private fun requestShizuku() {
        try {
            if (Shizuku.isPreV11()) {
                toast("Please update Shizuku")
            } else {
                Shizuku.requestPermission(1001)
            }
        } catch (e: Throwable) {
            toast("Could not ask Shizuku: " + (e.message ?: "unknown error"))
        }
    }

    private fun openShizuku() {
        val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (i == null) toast("Shizuku isn't installed") else startActivity(i)
    }

    // ---------------------------------------------------------------- saved commands

    private fun loadSaved(): MutableList<String> {
        val out = mutableListOf<String>()
        try {
            val a = JSONArray(prefs.getString("saved", "[]"))
            for (i in 0 until a.length()) out.add(a.getString(i))
        } catch (e: Exception) {
            // start empty
        }
        return out
    }

    private fun storeSaved(l: List<String>) {
        prefs.edit().putString("saved", JSONArray(l).toString()).apply()
    }

    private fun renderSaved() {
        savedBox.removeAllViews()
        val l = loadSaved()
        if (l.isEmpty()) {
            savedBox.add(label("Nothing saved yet.", 12f), 4)
            return
        }
        l.forEach { c ->
            val b = Button(this).apply {
                text = c.lines().first().take(70)
                isAllCaps = false
                typeface = Typeface.MONOSPACE
                textSize = 12f
                setOnClickListener { runShell("Saved command", c) }
                setOnLongClickListener {
                    val cur = loadSaved()
                    cur.remove(c)
                    storeSaved(cur)
                    renderSaved()
                    toast("Removed")
                    true
                }
            }
            savedBox.add(b, 4)
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun log(s: String) {
        logBuf.append(s).append("\n\n")
        if (logBuf.length > 20000) logBuf.delete(0, logBuf.length - 15000)
        logView.text = logBuf.toString().trimEnd().lines().takeLast(16).joinToString("\n")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
