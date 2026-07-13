package com.rawpulse.hr

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rawpulse.hr.data.ConnectionState
import com.rawpulse.hr.data.HrRepository
import com.rawpulse.hr.data.MetricsSnapshot
import com.rawpulse.hr.data.Settings
import com.rawpulse.hr.databinding.ActivityMainBinding
import com.rawpulse.hr.service.HrService
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

        // Android 15 forces edge-to-edge, so content would otherwise draw under the status
        // bar (the title slid up behind the clock). Pad the scroll view by the system-bar
        // insets instead — this behaves correctly on every Android version.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        settings = Settings(this)
        binding.demoSwitch.isChecked = settings.demoMode
        binding.ageInput.setText(settings.age.toString())

        binding.connectButton.setOnClickListener { onConnectClicked() }
        binding.infoButton.setOnClickListener { showInfoDialog() }

        // Toggling demo/live while streaming should take effect immediately. Persist the
        // new choice and, if we're already streaming, tell the service to switch its data
        // source live — no need to stop and restart by hand.
        binding.demoSwitch.setOnCheckedChangeListener { _, isChecked ->
            settings.demoMode = isChecked
            if (HrRepository.current().state != ConnectionState.DISCONNECTED) {
                HrService.start(this)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                HrRepository.flow.collect { render(it) }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Persist whatever the user typed even if they never tapped "Start streaming",
        // so the age (and the zones derived from it) survive closing the app.
        persistSettings()
    }

    /** Info tooltip: what each widget is, plus a note from the developer. */
    private fun showInfoDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_info, null)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        // Transparent window so the rounded dialog_bg (and its hairline) is what shows.
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        // The about text carries <a> links (email + X profile); without a movement
        // method the URLSpans render but never receive taps.
        view.findViewById<TextView>(R.id.info_about_body).movementMethod =
            LinkMovementMethod.getInstance()
        view.findViewById<View>(R.id.info_close).setOnClickListener { dialog.dismiss() }
        dialog.show()
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
        val streaming = snap.state != ConnectionState.DISCONNECTED
        // "Live" means connected AND receiving fresh readings (demo counts). Show "--"
        // otherwise, so the number doesn't freeze on stop or when the band drops.
        val live = snap.hasLiveReading
        binding.bpmValue.text =
            if (live) snap.bpm?.toString() ?: getString(R.string.dash)
            else getString(R.string.dash)
        val statusText = when {
            snap.demo && live -> getString(R.string.status_demo)
            live -> getString(R.string.status_connected)
            snap.state == ConnectionState.CONNECTED -> getString(R.string.status_connecting)
            snap.state == ConnectionState.SCANNING -> getString(R.string.status_scanning)
            snap.state == ConnectionState.CONNECTING -> getString(R.string.status_connecting)
            else -> getString(R.string.status_disconnected)
        }
        styleStatusPill(live, statusText)

        binding.connectButton.setText(if (streaming) R.string.disconnect else R.string.connect)
        styleActionButton(streaming)
    }

    /** Red pill with a leading dot when live; quiet neutral pill otherwise. */
    private fun styleStatusPill(live: Boolean, text: String) {
        binding.status.text = if (live) "●  $text" else text
        binding.status.setBackgroundResource(
            if (live) R.drawable.status_pill_live else R.drawable.status_pill_off
        )
        binding.status.setTextColor(
            ContextCompat.getColor(this, if (live) R.color.app_accent else R.color.app_muted)
        )
    }

    /**
     * Start is the loud action (filled red); Stop is deliberately quiet (red ghost),
     * so the destructive control doesn't shout.
     */
    private fun styleActionButton(streaming: Boolean) {
        val btn = binding.connectButton
        if (streaming) {
            val accent = ContextCompat.getColor(this, R.color.app_accent)
            btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            btn.strokeColor = ColorStateList.valueOf(accent)
            btn.strokeWidth = (resources.displayMetrics.density).toInt()
            btn.setTextColor(accent)
        } else {
            // Deeper red + light text: reads as the primary action without the glare
            // of a full-saturation fill.
            btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.app_btn))
            btn.strokeWidth = 0
            btn.setTextColor(ContextCompat.getColor(this, R.color.app_text))
        }
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
