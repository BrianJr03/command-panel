package jr.brian.commandpannel.privileged.helper

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.util.Log
import jr.brian.commandpannel.privileged.PcmMixer
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * Root-side audio sources for the Replay Buffer. Game audio is an `AudioPolicy` loopback mix of what apps play
 * (media, game and unknown usages) with `ROUTE_FLAG_LOOP_BACK_RENDER`, so it keeps playing on the speakers while a
 * copy is recorded; this is Android 13+'s hidden API, reached reflectively, the same technique as scrcpy's
 * `--audio-source=playback --audio-dup`. The mic is a plain `AudioRecord`. Both are 48 kHz 16-bit stereo.
 *
 * [start] runs one capture session: it reads 20 ms frames from each source, mixes them and writes little-endian PCM
 * to the app's pipe until [stop] or the app closes its end.
 */
internal object AudioCapture {
    private const val TAG = "AudioCapture"
    const val SAMPLE_RATE = 48_000
    const val CHANNELS = 2
    /** Samples (both channels) in one 20 ms frame. */
    private const val FRAME_SAMPLES = SAMPLE_RATE / 50 * CHANNELS

    @Volatile private var session: Thread? = null
    @Volatile private var running = false

    /** Starts streaming the requested sources into [out]. False (and [out] closed) if none of them opened. */
    @Synchronized
    fun start(out: ParcelFileDescriptor, game: Boolean, mic: Boolean): Boolean {
        stop()
        // The context needs a Looper (it builds an ActivityThread), and binder calls arrive on binder threads.
        val (loop, micRecord) = onMainLooper { open(game, mic) } ?: (null to null)
        if (loop == null && micRecord == null) {
            runCatching { out.close() }
            return false
        }
        loop?.record?.startRecording()
        micRecord?.startRecording()
        running = true
        session = thread(name = "replay-audio") { pump(out, loop, micRecord) }
        Log.i(TAG, "audio capture started: game=${loop != null} mic=${micRecord != null}")
        return true
    }

    /** Opens the requested sources, trying each package identity this uid may use until they all open. */
    private fun open(game: Boolean, mic: Boolean): Pair<Loopback?, AudioRecord?> {
        var loop: Loopback? = null
        var micRecord: AudioRecord? = null
        for (pkg in FakeContext.candidatePackages()) {
            val ctx = runCatching { FakeContext.get(pkg) }.onFailure { Log.w(TAG, "no context for $pkg", it) }.getOrNull() ?: continue
            if (game && loop == null) loop = openLoopback(ctx)
            if (mic && micRecord == null) micRecord = openMic(ctx)
            if ((!game || loop != null) && (!mic || micRecord != null)) break
        }
        return loop to micRecord
    }

    /** Runs [block] on the helper's main looper and waits for it (up to 5 s); `null` if it timed out or threw. */
    private fun <T> onMainLooper(block: () -> T): T? {
        val looper = android.os.Looper.getMainLooper()
        if (android.os.Looper.myLooper() == looper) return runCatching(block).getOrNull()
        val result = java.util.concurrent.atomic.AtomicReference<T?>()
        val done = java.util.concurrent.CountDownLatch(1)
        android.os.Handler(looper).post {
            runCatching(block).onSuccess { result.set(it) }.onFailure { Log.w(TAG, "open failed", it) }
            done.countDown()
        }
        return if (done.await(5, java.util.concurrent.TimeUnit.SECONDS)) result.get() else null
    }

    @Synchronized
    fun stop() {
        running = false
        session?.let { runCatching { it.join(500) } }
        session = null
    }

    private fun pump(out: ParcelFileDescriptor, loop: Loopback?, mic: AudioRecord?) {
        val a = ShortArray(FRAME_SAMPLES)
        val b = ShortArray(FRAME_SAMPLES)
        val bytes = ByteArray(FRAME_SAMPLES * 2)
        try {
            FileOutputStream(out.fileDescriptor).use { stream ->
                while (running) {
                    // The loopback paces the loop when game audio is on (it delivers silence when nothing plays).
                    val primary = loop?.record ?: mic!!
                    if (!readFull(primary, a)) break
                    if (loop != null && mic != null) {
                        if (!readFull(mic, b)) break
                        PcmMixer.mixInto(a, b, FRAME_SAMPLES)
                    }
                    PcmMixer.toBytes(a, FRAME_SAMPLES, bytes)
                    stream.write(bytes)
                }
            }
        } catch (t: Throwable) {
            // The app closed its end (disarm) or the pipe broke; either way the session is over.
            Log.i(TAG, "audio stream ended: ${t.javaClass.simpleName}")
        } finally {
            loop?.release()
            mic?.let { runCatching { it.stop() }; runCatching { it.release() } }
            runCatching { out.close() }
            running = false
        }
    }

    private fun readFull(record: AudioRecord, buf: ShortArray): Boolean {
        var n = 0
        while (n < buf.size && running) {
            val r = record.read(buf, n, buf.size - n)
            if (r <= 0) return false
            n += r
        }
        return n == buf.size
    }

    private val format: AudioFormat = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(SAMPLE_RATE)
        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
        .build()

    /** A recorder of what apps play, plus the policy to unregister when done. */
    class Loopback(val record: AudioRecord, private val policy: Any) {
        fun release() {
            runCatching { record.stop() }
            runCatching { record.release() }
            runCatching {
                val policyClass = Class.forName("android.media.audiopolicy.AudioPolicy")
                AudioManager::class.java.getDeclaredMethod("unregisterAudioPolicyAsyncStatic", policyClass)
                    .apply { isAccessible = true }.invoke(null, policy)
            }.onFailure { Log.w(TAG, "unregister policy failed", it) }
        }
    }

    fun openLoopback(context: Context): Loopback? = runCatching {
        val ruleClass = Class.forName("android.media.audiopolicy.AudioMixingRule")
        val ruleBuilderClass = Class.forName("android.media.audiopolicy.AudioMixingRule\$Builder")
        val ruleBuilder = ruleBuilderClass.getConstructor().newInstance()
        ruleBuilderClass.getMethod("setTargetMixRole", Int::class.javaPrimitiveType)
            .invoke(ruleBuilder, ruleClass.getField("MIX_ROLE_PLAYERS").getInt(null))
        val matchUsage = ruleClass.getField("RULE_MATCH_ATTRIBUTE_USAGE").getInt(null)
        val addMixRule = ruleBuilderClass.getMethod("addMixRule", Int::class.javaPrimitiveType, Any::class.java)
        for (usage in listOf(AudioAttributes.USAGE_MEDIA, AudioAttributes.USAGE_GAME, AudioAttributes.USAGE_UNKNOWN)) {
            addMixRule.invoke(ruleBuilder, matchUsage, AudioAttributes.Builder().setUsage(usage).build())
        }
        val rule = ruleBuilderClass.getMethod("build").invoke(ruleBuilder)

        val mixClass = Class.forName("android.media.audiopolicy.AudioMix")
        val mixBuilderClass = Class.forName("android.media.audiopolicy.AudioMix\$Builder")
        val mixBuilder = mixBuilderClass.getConstructor(ruleClass).newInstance(rule)
        mixBuilderClass.getMethod("setFormat", AudioFormat::class.java).invoke(mixBuilder, format)
        mixBuilderClass.getMethod("setRouteFlags", Int::class.javaPrimitiveType)
            .invoke(mixBuilder, mixClass.getField("ROUTE_FLAG_LOOP_BACK_RENDER").getInt(null))
        val mix = mixBuilderClass.getMethod("build").invoke(mixBuilder)

        val policyClass = Class.forName("android.media.audiopolicy.AudioPolicy")
        val policyBuilderClass = Class.forName("android.media.audiopolicy.AudioPolicy\$Builder")
        val policyBuilder = policyBuilderClass.getConstructor(Context::class.java).newInstance(context)
        policyBuilderClass.getMethod("addMix", mixClass).invoke(policyBuilder, mix)
        val policy = policyBuilderClass.getMethod("build").invoke(policyBuilder)

        val registered = AudioManager::class.java.getDeclaredMethod("registerAudioPolicyStatic", policyClass)
            .apply { isAccessible = true }.invoke(null, policy) as Int
        check(registered == 0) { "registerAudioPolicyStatic returned $registered" }
        val record = policyClass.getMethod("createAudioRecordSink", mixClass).invoke(policy, mix) as AudioRecord
        check(record.state == AudioRecord.STATE_INITIALIZED) { "loopback AudioRecord not initialized" }
        Loopback(record, policy)
    }.onFailure { Log.w(TAG, "game audio loopback unavailable", it) }.getOrNull()

    @android.annotation.SuppressLint("MissingPermission")
    fun openMic(context: Context): AudioRecord? = runCatching {
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        AudioRecord.Builder()
            .setContext(context)
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(min, SAMPLE_RATE * CHANNELS * 2 / 5))
            .build()
            .also { check(it.state == AudioRecord.STATE_INITIALIZED) { "mic AudioRecord not initialized" } }
    }.onFailure { Log.w(TAG, "mic unavailable", it) }.getOrNull()
}
