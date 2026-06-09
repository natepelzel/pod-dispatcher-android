package dev.poddispatcher.ui

import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dev.poddispatcher.Graph
import dev.poddispatcher.Prefs
import dev.poddispatcher.R
import dev.poddispatcher.model.PodSchema
import kotlinx.coroutines.launch

/**
 * Settings screen: a Material-style list of target apps (installed ones
 * first, active one highlighted; missing ones grayed out with an Install
 * link), pull-to-refresh for OTA schema updates, and a pinned button for
 * Android's link handling settings.
 */
class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var listColumn: LinearLayout
    private var pad = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val repository = Graph.repository(this)
        pad = (16 * resources.displayMetrics.density).toInt()

        listColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val swipe = SwipeRefreshLayout(this).apply {
            addView(ScrollView(this@MainActivity).apply {
                isFillViewport = true
                addView(listColumn)
            })
            setOnRefreshListener {
                lifecycleScope.launch {
                    val ok = repository.refresh()
                    isRefreshing = false
                    Toast.makeText(
                        this@MainActivity,
                        getString(if (ok) R.string.refresh_ok else R.string.refresh_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                    if (ok) renderList()
                }
            }
        }

        val linkSettingsButton = Button(this).apply {
            text = getString(R.string.open_by_default_settings)
            setOnClickListener { openLinkHandlingSettings() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(swipe, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
            ))
            addView(linkSettingsButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(pad, pad / 2, pad, pad / 2) })
        }
        // targetSdk 35 enforces edge-to-edge: keep content out of the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        renderList()
    }

    private fun renderList() {
        listColumn.removeAllViews()
        listColumn.addView(TextView(this).apply {
            text = getString(R.string.choose_target)
            textSize = 18f
            setPadding(0, 0, 0, pad / 2)
        })

        // Installed apps first; the rest grayed out with an Install link.
        val (installed, missing) = Graph.repository(this)
            .androidTargets()
            .partition { isInstalled(it.target!!.android!!.packageName) }

        if (installed.isEmpty() && missing.isEmpty()) {
            listColumn.addView(TextView(this).apply {
                text = getString(R.string.no_targets)
            })
            return
        }

        val selectedId = prefs.preferredTargetId ?: installed.firstOrNull()?.id
        installed.forEach { listColumn.addView(installedRow(it, it.id == selectedId)) }
        missing.forEach { listColumn.addView(missingRow(it)) }
    }

    private fun installedRow(schema: PodSchema, selected: Boolean): LinearLayout {
        val packageName = schema.target!!.android!!.packageName
        return row().apply {
            if (selected) {
                background = GradientDrawable().apply {
                    cornerRadius = 16 * resources.displayMetrics.density
                    setColor(HIGHLIGHT_COLOR)
                }
            } else {
                val ripple = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                setBackgroundResource(ripple.resourceId)
            }
            addView(appIcon(packageManager.getApplicationIcon(packageName), grayed = false))
            addView(TextView(this@MainActivity).apply {
                text = schema.name
                textSize = 16f
                if (selected) setTypeface(typeface, Typeface.BOLD)
                setPadding(pad / 2, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
                )
            })
            if (selected) {
                addView(TextView(this@MainActivity).apply {
                    text = "✓"
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(ACCENT_COLOR)
                    setPadding(0, 0, pad / 2, 0)
                })
            }
            isClickable = true
            setOnClickListener {
                prefs.preferredTargetId = schema.id
                renderList()
            }
        }
    }

    private fun missingRow(schema: PodSchema): LinearLayout {
        val packageName = schema.target!!.android!!.packageName
        return row().apply {
            addView(appIcon(getDrawable(android.R.drawable.sym_def_app_icon)!!, grayed = true))
            addView(TextView(this@MainActivity).apply {
                text = schema.name
                textSize = 16f
                alpha = 0.45f
                setPadding(pad / 2, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
                )
            })
            addView(Button(this@MainActivity, null, android.R.attr.borderlessButtonStyle).apply {
                text = getString(R.string.install)
                setTextColor(ACCENT_COLOR)
                setOnClickListener { openPlayStore(packageName) }
            })
        }
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = (56 * resources.displayMetrics.density).toInt()
        setPadding(pad / 2, pad / 4, pad / 2, pad / 4)
    }

    private fun appIcon(drawable: Drawable, grayed: Boolean) =
        ImageView(this).apply {
            val size = (40 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            setImageDrawable(drawable)
            if (grayed) {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                alpha = 0.4f
            }
        }

    private fun isInstalled(packageName: String): Boolean =
        runCatching { packageManager.getPackageInfo(packageName, 0) }.isSuccess

    private fun openPlayStore(packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        )
        runCatching { startActivity(market) }
            .recoverCatching { startActivity(web) }
    }

    /**
     * On Android 12+ link interception for domains we don't own must be
     * enabled manually by the user; send them straight to that settings page.
     */
    private fun openLinkHandlingSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                Uri.parse("package:$packageName"),
            )
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            )
        }
        runCatching { startActivity(intent) }
    }

    private companion object {
        /** M3 light secondary-container; fine on the plain Material light theme. */
        const val HIGHLIGHT_COLOR = 0xFFE8DEF8.toInt()

        /** M3 primary. */
        const val ACCENT_COLOR = 0xFF6750A4.toInt()
    }
}
