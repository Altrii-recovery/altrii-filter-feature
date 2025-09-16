package com.altrii.filter.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.altrii.filter.R
import com.altrii.filter.databinding.ActivityBlockedBinding

class BlockedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val domain = intent?.getStringExtra(EXTRA_DOMAIN).orEmpty()
        val category = intent?.getStringExtra(EXTRA_CATEGORY)
        binding.blockedTitle.text = if (!category.isNullOrEmpty()) {
            getString(R.string.block_screen_title_with_reason, category)
        } else {
            getString(R.string.block_screen_title)
        }
        binding.blockedMessage.text = getString(R.string.blocked_message, domain.ifEmpty { getString(R.string.app_name) })

        binding.closeButton.setOnClickListener {
            finish()
        }
    }

    companion object {
        const val EXTRA_DOMAIN = "blocked_domain"
        const val EXTRA_CATEGORY = "blocked_category"
    }
}
