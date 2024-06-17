package dev.fabik.bluetoothhid.utils

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.javascriptengine.JavaScriptSandbox
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class JsEngineService : Service() {
    companion object {
        const val TAG: String = "JsEngine"
    }

    private val binder = LocalBinder()
    private var jsSandbox: JavaScriptSandbox? = null
    private var isInitialized = false

    override fun onCreate() {
        Log.d(TAG, "Initializing js sandbox...")

        if (isInitialized) {
            Log.w(TAG, "Already initialized (skipping)")
            return
        }

        if (!JavaScriptSandbox.isSupported()) {
            Log.w(TAG, "JsSandbox is not supported!")
            return
        }

        runCatching {
            val jsSandboxFuture = JavaScriptSandbox.createConnectedInstanceAsync(applicationContext)

            jsSandboxFuture.addListener({
                jsSandbox = jsSandboxFuture.get()
                isInitialized = true
            }, Executors.newSingleThreadExecutor())
        }.onFailure {
            Log.e(TAG, "Failed to initialize js sandbox", it)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Start command received")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "OnDestroy called")
        isInitialized = false
        jsSandbox?.close()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    @SuppressLint("RequiresFeature")
    suspend fun evaluate(code: String, onOutput: ((String) -> Unit)? = null): String {
        var result = "Error: Unable to create isolate!"

        jsSandbox?.createIsolate()?.let {
            if (jsSandbox?.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_CONSOLE_MESSAGING) == true) {
                it.setConsoleCallback(Executors.newSingleThreadExecutor()) { message ->
                    Log.d(TAG, message.toString())
                    onOutput?.invoke(message.toString())
                }
            }

            it.addOnTerminatedCallback(Executors.newSingleThreadExecutor()) { info ->
                Log.d(TAG, "Isolate terminated with $info")
                onOutput?.invoke("Isolate terminated with $info")
            }

            onOutput?.invoke("--- Execution started ---")

            val start = System.currentTimeMillis()
            val future = it.evaluateJavaScriptAsync(code)

            result = suspendCoroutine {
                future.addListener({
                    runCatching {
                        it.resume(future.get())
                    }.onFailure { err ->
                        Log.e(TAG, "Failed to evaluate code", err)
                        it.resume(err.cause?.message ?: err.message ?: err.toString())
                    }
                }, Executors.newSingleThreadExecutor())
            }

            onOutput?.invoke("--- Execution finished (${System.currentTimeMillis() - start}ms) ---")
            onOutput?.invoke(result)

            it.close()
        }

        return result
    }

    inner class LocalBinder : Binder() {
        suspend fun evaluate(code: String, onOutput: ((String) -> Unit)? = null) =
            this@JsEngineService.evaluate(code, onOutput)

        suspend fun evaluateTemplate(
            code: String,
            value: String,
            type: String,
            onOutput: ((String) -> Unit)? = null
        ): String {
            val template = """
            const format = "$type";
            const code = "$value";
            $code""".trimIndent()

            return evaluate(template, onOutput)
        }
    }

}

@Composable
fun rememberJsEngineService(context: Context): JsEngineService.LocalBinder? {
    val serviceBinder = remember { mutableStateOf<JsEngineService.LocalBinder?>(null) }

    DisposableEffect(Unit) {
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                serviceBinder.value = service as JsEngineService.LocalBinder?
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceBinder.value = null
            }
        }

        Intent(context, JsEngineService::class.java).also {
            context.startService(it)
            context.bindService(it, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        onDispose {
            context.unbindService(serviceConnection)
        }
    }

    return serviceBinder.value
}