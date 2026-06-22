package com.goodwy.ftpmanager.fragments

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import com.goodwy.ftpmanager.R
import com.goodwy.ftpmanager.activities.MainActivity
import com.goodwy.ftpmanager.databinding.FragmentNetworkBinding
import com.goodwy.ftpmanager.FtpServerService

// SMT Architecture: Inherits MyViewPagerFragment (which is a RelativeLayout ViewGroup)
class NetworkFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {

    private var binding: FragmentNetworkBinding? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentNetworkBinding.bind(this)
        setupFtpTab()
    }

    private fun setupFtpTab() {
        val masterActivity = (context as? MainActivity) ?: return
        val prefs = context.getSharedPreferences("ftp_prefs", Context.MODE_PRIVATE)

        binding?.apply {
            // 1. Populate static username
            editUsername.setText(prefs.getString("username", "goodwy"))
            
            // 2. Live SharedPreferences writer
            editUsername.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    prefs.edit().putString("username", s.toString().trim()).apply()
                }
            })

            // 3. Hand off click event to MainActivity's permission gatekeeper
            btnStartStop.setOnClickListener {
                if (masterActivity.checkAndRequestFtpPermissions()) {
                    val isStopping = btnStartStop.text.contains("STOP")
                    masterActivity.toggleFtpEngine(context, !isStopping)
                }
            }
        }
    }

    // Shouted directly into this instance by MainActivity's BroadcastReceiver
    fun updateFtpUiState(isRunning: Boolean, url: String, pass: String) {
        binding?.apply {
            if (isRunning) {
                statusDot.setBackgroundResource(android.R.drawable.presence_online)
                textStatus.text = "FTP Server is running"
                textStatus.setTextColor(Color.parseColor("#137333"))
                textUrl.text = url
                textPassword.text = pass
                editUsername.isEnabled = false

                btnStartStop.text = "STOP FTP SERVER"
                btnStartStop.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D93025"))
            } else {
                statusDot.setBackgroundResource(android.R.drawable.presence_offline)
                textStatus.text = "FTP Service Stopped"
                textStatus.setTextColor(Color.parseColor("#5F6368"))
                textUrl.text = "ftp://--.--.--.--:----"
                textPassword.text = "------"
                editUsername.isEnabled = true

                btnStartStop.text = "START FTP SERVER"
                btnStartStop.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1A73E8"))
            }
        }
    }

    // --- Goodwy abstract View boilerplate overrides ---
    override fun setupColors(textColor: Int, primaryColor: Int) {
        binding?.textStatus?.setTextColor(textColor)
    }

    override fun searchQueryChanged(text: String) {
        // Network tab ignores top search bar queries
    }

    override fun refreshFragment() {
        // Triggered when user swipes back onto the tab; forces a live state re-broadcast
        (context as? MainActivity)?.let {
            if (it.isFtpServiceRunning(it)) {
                it.startService(Intent(it, FtpServerService::class.java))
            }
        }
    }
}
