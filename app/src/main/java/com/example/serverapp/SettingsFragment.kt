package com.example.serverapp.fragments

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.serverapp.R
import com.example.serverapp.RetrofitClient
import com.google.android.material.textfield.TextInputEditText

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var itemServerIp: TextView
    private lateinit var tvServerIp: TextView

    private lateinit var itemMaxLength: TextView
    private lateinit var tvMaxLength: TextView

    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_IP = "server_ip"
        private const val KEY_MAX_LENGTH = "video_max_length"

        private const val DEFAULT_IP = "127.0.0.1"
        private const val DEFAULT_MAX_LENGTH = 60
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        itemServerIp   = view.findViewById(R.id.item_server_ip)
        tvServerIp     = view.findViewById(R.id.ip_subtitle)

        itemMaxLength  = view.findViewById(R.id.item_max_length)
        tvMaxLength    = view.findViewById(R.id.length_subtitle)

        updateDisplayedIp()
        updateDisplayedMaxLength()

        itemServerIp.setOnClickListener {
            showIpInputDialog()
        }
        tvServerIp.setOnClickListener {
            showIpInputDialog()
        }
        itemMaxLength.setOnClickListener {
            showMaxLengthDialog()
        }
        tvMaxLength.setOnClickListener {
            showMaxLengthDialog()
        }
    }

    private fun updateDisplayedIp() {
        val ip = prefs.getString(KEY_IP, DEFAULT_IP) ?: DEFAULT_IP
        if (ip.isBlank() || ip == DEFAULT_IP) {
            tvServerIp.text = getString(R.string.settings_ip_not_set)
        } else {
            tvServerIp.text = ip
        }
    }

    private fun showIpInputDialog() {
        val current = prefs.getString(KEY_IP, DEFAULT_IP)!!
        val edit = TextInputEditText(requireContext()).apply {
            hint = DEFAULT_IP
            inputType = InputType.TYPE_CLASS_PHONE
            setText(if (current != DEFAULT_IP) current else "")
            setSelection(text?.length ?: 0)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Введите IP-адрес")
            .setView(edit)
            .setPositiveButton("OK") { dialog, _ ->
                val input = edit.text.toString().trim()
                val ip = if (input.isBlank()) DEFAULT_IP else input
                prefs.edit().putString(KEY_IP, ip).apply()
                RetrofitClient.setServerIp(ip)
                updateDisplayedIp()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun updateDisplayedMaxLength() {
        val len = prefs.getInt(KEY_MAX_LENGTH, DEFAULT_MAX_LENGTH)
        tvMaxLength.text = "$len сек."
    }

    private fun showMaxLengthDialog() {
        val current = prefs.getInt(KEY_MAX_LENGTH, DEFAULT_MAX_LENGTH)
        val edit = TextInputEditText(requireContext()).apply {
            hint = DEFAULT_MAX_LENGTH.toString()
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.toString())
            setSelection(text?.length ?: 0)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Максимальная длина видео (сек)")
            .setView(edit)
            .setPositiveButton("OK") { dialog, _ ->
                val input = edit.text.toString().toIntOrNull()
                val len = input?.takeIf { it > 0 } ?: DEFAULT_MAX_LENGTH
                prefs.edit().putInt(KEY_MAX_LENGTH, len).apply()
                updateDisplayedMaxLength()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
