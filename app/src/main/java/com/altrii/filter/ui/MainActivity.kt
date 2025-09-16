package com.altrii.filter.ui

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.altrii.filter.R
import com.altrii.filter.data.FilterPreferences
import com.altrii.filter.databinding.ActivityMainBinding
import com.altrii.filter.vpn.AltriiVpnService
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val preferences by lazy { FilterPreferences(this) }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                startFilterService()
            } else {
                Toast.makeText(this, R.string.filter_stopped, Toast.LENGTH_SHORT).show()
            }
        }

    private val blockBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val domain = intent?.getStringExtra(AltriiVpnService.EXTRA_DOMAIN) ?: return
            val readableCategory = intent.getStringExtra(AltriiVpnService.EXTRA_CATEGORY)
            val message = if (!readableCategory.isNullOrEmpty()) {
                getString(R.string.domain_blocked_snackbar_with_reason, domain, readableCategory)
            } else {
                getString(R.string.domain_blocked_snackbar, domain)
            }
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    private var broadcastRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupUi()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (!broadcastRegistered) {
            ContextCompat.registerReceiver(
                this,
                blockBroadcastReceiver,
                IntentFilter(AltriiVpnService.ACTION_DOMAIN_BLOCKED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            broadcastRegistered = true
        }
    }

    override fun onPause() {
        super.onPause()
        if (broadcastRegistered) {
            unregisterReceiver(blockBroadcastReceiver)
            broadcastRegistered = false
        }
    }

    private fun setupUi() {
        binding.socialSwitch.isChecked = preferences.blockSocialMedia
        binding.youtubeSwitch.isChecked = preferences.blockYoutube
        binding.gamblingSwitch.isChecked = preferences.blockGambling

        binding.socialSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.blockSocialMedia = isChecked
        }
        binding.youtubeSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.blockYoutube = isChecked
        }
        binding.gamblingSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.blockGambling = isChecked
        }

        binding.startButton.setOnClickListener {
            binding.statusText.text = getString(R.string.filter_running)
            prepareAndStartVpn()
        }

        binding.stopButton.setOnClickListener {
            binding.statusText.text = getString(R.string.filter_stopped)
            startService(Intent(this, AltriiVpnService::class.java).apply {
                action = AltriiVpnService.ACTION_STOP
            })
        }
    }

    private fun prepareAndStartVpn() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            startFilterService()
        }
    }

    private fun startFilterService() {
        val intent = Intent(this, AltriiVpnService::class.java).apply {
            action = AltriiVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATIONS
                )
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_NOTIFICATIONS = 501
    }
}
