package dev.poddispatcher.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.poddispatcher.Graph
import dev.poddispatcher.R
import dev.poddispatcher.engine.Dispatcher
import dev.poddispatcher.engine.LinkUrl
import kotlinx.coroutines.launch

/**
 * Invisible entry point for podcast links, reached two ways: intercepted VIEW
 * intents for hosts baked into the manifest, and SEND intents from the share
 * sheet (which is not host-restricted, so it also covers sources added OTA
 * before their hosts ship in a release). Resolves the link via the schema
 * engine, forwards it to the preferred app, and finishes.
 */
class DispatchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = intent?.action == Intent.ACTION_SEND
        val url =
            if (shared) LinkUrl.findInText(intent.getStringExtra(Intent.EXTRA_TEXT))
            else intent?.dataString
        if (url == null) {
            if (shared) toast(getString(R.string.error_no_link_in_share))
            finish()
            return
        }
        // Loop guard: if a deep link we emitted resolves back to ourselves
        // (source host == target host and the target schema has no package
        // pin), bail out instead of re-dispatching forever.
        if (intent.getBooleanExtra(EXTRA_DISPATCHED, false)) {
            toast(getString(R.string.error_dispatch_loop))
            finish()
            return
        }
        lifecycleScope.launch {
            when (val result = Graph.dispatcher(this@DispatchActivity).dispatch(url)) {
                is Dispatcher.Result.Launch -> launchFirstWorking(result, url, shared)
                is Dispatcher.Result.Failure -> {
                    toast(result.reason)
                    // Intercepted links must never strand the user; a shared
                    // link just returns to the app it was shared from, so
                    // bouncing it to a browser would only be noise.
                    if (!shared) openElsewhere(url)
                }
            }
            finish()
        }
    }

    private fun launchFirstWorking(
        result: Dispatcher.Result.Launch,
        originalUrl: String,
        shared: Boolean,
    ) {
        for (candidate in result.candidates) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(candidate)).apply {
                result.packageName?.let { setPackage(it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_DISPATCHED, true)
            }
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // try the next candidate
            }
        }
        toast(getString(R.string.error_target_not_installed))
        if (!shared) openElsewhere(originalUrl)
    }

    /**
     * Last resort: hand the original link to whatever would have opened it if
     * we weren't installed (the source platform's own app, or a browser), so
     * intercepting a link we can't resolve never leaves the user stranded.
     */
    private fun openElsewhere(url: String) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val handler = packageManager
            .queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY)
            .firstOrNull { it.activityInfo.packageName != packageName }
            ?: return
        runCatching {
            startActivity(view.apply {
                setPackage(handler.activityInfo.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val EXTRA_DISPATCHED = "dev.poddispatcher.DISPATCHED"
    }
}
