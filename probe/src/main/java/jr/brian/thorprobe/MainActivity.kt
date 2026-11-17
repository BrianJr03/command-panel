package jr.brian.thorprobe

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var output: TextView
    private val sb = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val run = Button(this).apply { text = "Run probes" }
        val scroll = ScrollView(this)
        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
        }
        scroll.addView(output)
        root.addView(run)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        run.setOnClickListener { run.isEnabled = false; runProbes { run.isEnabled = true } }
    }

    private fun runProbes(done: () -> Unit) {
        sb.setLength(0)
        appendLine("Thor Probe — ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("=".repeat(40))
        output.text = sb.toString()

        Thread {
            val results = Probes.runAll(applicationContext) { r ->
                runOnUiThread {
                    val mark = when (r.verdict) {
                        Probes.Verdict.PASS -> "[PASS]"
                        Probes.Verdict.FAIL -> "[FAIL]"
                        Probes.Verdict.PARTIAL -> "[~~~~]"
                        Probes.Verdict.INFO -> "[info]"
                    }
                    Log.i("THORPROBE", "$mark ${r.name} :: ${r.detail}")
                    appendLine("$mark ${r.name}")
                    appendLine("        ${r.detail}")
                    output.text = sb.toString()
                }
            }
            val report = buildReport(results)
            val file = File(getExternalFilesDir(null), "thor-probe-report.txt")
            try { file.writeText(report) } catch (_: Throwable) {}
            runOnUiThread {
                appendLine("=".repeat(40))
                appendLine("Report written to:")
                appendLine(file.absolutePath)
                output.text = sb.toString()
                done()
            }
        }.start()
    }

    private fun buildReport(results: List<Probes.Result>): String {
        val b = StringBuilder()
        b.appendLine("Thor Probe report ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        b.appendLine("model=${android.os.Build.MODEL} fw=${android.os.Build.DISPLAY}")
        b.appendLine()
        for (r in results) {
            b.appendLine("${r.verdict}\t${r.name}")
            b.appendLine("\t${r.detail}")
        }
        return b.toString()
    }

    private fun appendLine(s: String) { sb.append(s).append('\n') }
}
