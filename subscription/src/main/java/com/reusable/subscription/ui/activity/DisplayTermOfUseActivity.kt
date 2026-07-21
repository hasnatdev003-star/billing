package com.reusable.subscription.ui.activity

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.reusable.subscription.domain.config.SubscriptionPolicyProvider
import com.reusable.subscription.R

/**
 * Displays subscription terms (English-only HTML) in a WebView.
 * Gets [trialInfo] from intent extra "trialInfo" (HashMap) and passes it to
 * [SubscriptionPolicyProvider.getSubscriptionPolicyHtml].
 */
class DisplayTermOfUseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_term_of_use)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val toolbarTitle = findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle?.text = getString(R.string.subscription_terms)
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        @Suppress("UNCHECKED_CAST")
        val trialInfo = (intent.getSerializableExtra("trialInfo") as? HashMap<String, String>) ?: emptyMap()
        val html = SubscriptionPolicyProvider.getSubscriptionPolicyHtml(trialInfo)

        val webView = findViewById<WebView>(R.id.web_view)
        webView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
        }
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.loadDataWithBaseURL(
            null,
            wrapThemeAwareHtml(html),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun wrapThemeAwareHtml(contentHtml: String): String {
        val background = String.format("#%06X", (0xFFFFFF and getAttrColor("colorBackground", android.R.attr.colorBackground, Color.WHITE)))
        val textPrimary = String.format("#%06X", (0xFFFFFF and getAttrColor("colorOnSurface", 0, Color.BLACK)))
        val textSecondary = String.format("#%06X", (0xFFFFFF and getAttrColor("colorOnSurfaceVariant", 0, Color.DKGRAY)))
        val accent = String.format("#%06X", (0xFFFFFF and getAttrColor("colorPrimary", 0, Color.BLUE)))
        val divider = String.format("#%06X", (0xFFFFFF and getAttrColor("colorOutline", 0, Color.LTGRAY)))

        return """
            <!doctype html>
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <style>
                    body {
                        margin: 0;
                        padding: 16px;
                        background: $background;
                        color: $textPrimary;
                        font-family: sans-serif;
                        line-height: 1.5;
                    }
                    h2, h3 { color: $accent; margin: 12px 0 8px 0; }
                    p, li { color: $textSecondary; }
                    strong, b { color: $textPrimary; }
                    ul { padding-left: 18px; }
                    hr { border: 0; border-top: 1px solid $divider; }
                </style>
            </head>
            <body>
                $contentHtml
            </body>
            </html>
        """.trimIndent()
    }

    private fun getAttrColor(attrName: String, fallbackAttr: Int, fallbackColor: Int): Int {
        val typedValue = android.util.TypedValue()
        var attrId = resources.getIdentifier(attrName, "attr", packageName)
        
        // If the attribute is not found in the app's package, check standard android attrs
        if (attrId == 0) attrId = fallbackAttr
        
        if (attrId != 0 && theme.resolveAttribute(attrId, typedValue, true)) {
            return typedValue.data
        }
        return fallbackColor
    }
}

