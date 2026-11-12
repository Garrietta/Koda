package com.ivor.ivormusic.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads an update's APK and hands it to Android's package installer,
 * which shows its own confirmation. Android only lets an app install an
 * update signed with the same key as the copy already installed, and only
 * once the person has let this app install apps (Settings, Install unknown
 * apps); [canInstall] and [openInstallPermission] cover the second.
 *
 * One install runs at a time; [state] says where it is, for the screen
 * that started it and for any other that is watching.
 */
object ApkInstaller {

    sealed interface State {
        data object Idle : State

        /** [fraction] is null when the server did not say how big the file is. */
        data class Downloading(val fraction: Float?) : State

        /** Handed to Android; its own dialog takes it from here. */
        data object Installing : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system page where the person lets this app install updates. */
    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Downloads [url] and asks Android to install it. Returns when the installer has it, or on failure. */
    suspend fun downloadAndInstall(context: Context, url: String) {
        if (_state.value is State.Downloading || _state.value is State.Installing) return
        val app = context.applicationContext
        _state.value = State.Downloading(null)
        val apk = try {
            download(app, url)
        } catch (e: kotlinx.coroutines.CancellationException) {
            _state.value = State.Idle
            throw e
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "The download failed.")
            return
        }
        try {
            install(app, apk)
            _state.value = State.Installing
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "Android would not start the install.")
        }
    }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)

    /** Starts [downloadAndInstall] in a scope of its own, so leaving the screen does not stop it. */
    fun start(context: Context, url: String) {
        scope.launch { downloadAndInstall(context, url) }
    }

    fun reset() {
        _state.value = State.Idle
    }

    internal fun report(state: State) {
        _state.value = state
    }

    private suspend fun download(context: Context, url: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "update.apk")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("The download answered ${response.code}.")
            val body = response.body ?: error("The download was empty.")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReported = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total != null) {
                            val percent = (read * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                _state.value = State.Downloading(read.toFloat() / total)
                            }
                        }
                    }
                }
            }
        }
        file
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("update.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateInstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }
}

/**
 * Hears back from the package installer: shows its confirmation when it
 * asks for one, and turns a refusal into words.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            }
            PackageInstaller.STATUS_SUCCESS -> ApkInstaller.report(ApkInstaller.State.Idle)
            else -> ApkInstaller.report(ApkInstaller.State.Failed(installFailure(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))))
        }
    }
}

/** What a package installer status means, in words a person can act on. */
internal fun installFailure(status: Int, detail: String?): String = when (status) {
    PackageInstaller.STATUS_FAILURE_ABORTED -> "The install was cancelled."
    PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
        "This build is signed differently from the copy installed, so Android will not update it. Uninstall this copy, then install the new one."
    PackageInstaller.STATUS_FAILURE_STORAGE -> "There is not enough space to install the update."
    PackageInstaller.STATUS_FAILURE_INVALID -> "The downloaded file is not a valid app."
    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the install."
    else -> detail?.takeIf { it.isNotBlank() } ?: "The install failed."
}
