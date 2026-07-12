package com.pulsetile.hr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pulsetile.hr.data.ConnectionState
import com.pulsetile.hr.data.HrRepository
import com.pulsetile.hr.data.MetricsSnapshot
import com.pulsetile.hr.data.Settings
import com.pulsetile.hr.databinding.ActivityMainBinding
import com.pulsetile.hr.service.HrService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val bleGranted = result[Manifest.permission.BLUETOOTH_SCAN] != false &&
            result[Manifest.permission.BLUETOOTH_CONNECT] != false
        if (bleGranted && hasBlePermissions()) {
            startStreaming()
        } else {
            Toast.makeText(this, R.string.perm_needed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = Settings(this)
        binding.demoSwitch.isChecked = settings.demoMode
        binding.ageInput.setText(settings.age.toString())

        binding.connectButton.setOnClickListener { onConnectClicked() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                HrRepository.flow.collect { render(it) }
            }
        }
    }

    private fun onConnectClicked() {
        persistSettings()
        val streaming = HrRepository.current().state != ConnectionState.DISCONNECTED
        if (streaming) {
            HrService.stop(this)
            return
        }
        // Even demo mode needs BLUETOOTH_CONNECT granted: the connectedDevice
        // foreground-service type requires it on Android 14+.
        if (hasBlePermissions()) {
            startStreaming()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun startStreaming() {
        persistSettings()
        HrService.start(this)
    }

    private fun persistSettings() {
        settings.demoMode = binding.demoSwitch.isChecked
        val age = binding.ageInput.text?.toString()?.toIntOrNull()
        if (age != null) settings.age = age
        HrRepository.setMaxHr(settings.effectiveMaxHr())
    }

    private fun render(snap: MetricsSnapshot) {
        binding.bpmValue.text = snap.bpm?.toString() ?: getString(R.string.dash)
        binding.status.text = when {
            snap.demo && snap.state == ConnectionState.CONNECTED -> getString(R.string.demo_mode)
            snap.state == ConnectionState.CONNECTED && !snap.stale -> getString(R.string.status_connected)
            snap.state == ConnectionState.CONNECTED -> getString(R.string.status_connecting)
            snap.state == ConnectionState.SCANNING -> getString(R.string.status_scanning)
            snap.state == ConnectionState.CONNECTING -> getString(R.string.status_connecting)
            else -> getString(R.string.status_disconnected)
        }
        val streaming = snap.state != ConnectionState.DISCONNECTED
        binding.connectButton.setText(if (streaming) R.string.disconnect else R.string.connect)
    }

    private fun hasBlePermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.toTypedArray()
    }
}
