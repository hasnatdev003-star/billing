package com.app.googleplaybillingutils

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.reusable.subscription.data.local.BillingDataStore
import com.reusable.subscription.ui.activity.SubscriptionActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var billingDataStore: BillingDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        billingDataStore = BillingDataStore(this)

        val tvStatus = findViewById<TextView>(R.id.tv_status)
        val btnPremium = findViewById<Button>(R.id.btn_premium)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Observe Premium Status
        lifecycleScope.launch {
            billingDataStore.isPremium.collectLatest { isPremium ->
                if (isPremium) {
                    tvStatus.text = "Status: PREMIUM 👑"
                    btnPremium.text = "Manage Subscription"
                } else {
                    tvStatus.text = "Status: FREE USER"
                    btnPremium.text = "Go Premium"
                }
            }
        }

        btnPremium.setOnClickListener {
            // Launch the Subscription Module
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }
    }
}
