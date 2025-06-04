package com.example.serverapp.fragments

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.serverapp.R
import com.example.serverapp.RetrofitClient
import com.google.android.material.textview.MaterialTextView

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var tvServerIp: TextView
    private lateinit var btnConfigureIp: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ссылки на элементы
        tvServerIp      = view.findViewById(R.id.tvServerIp)
        btnConfigureIp  = view.findViewById(R.id.btnConfigureIp)

        // Загружаем сохранённый IP из SharedPreferences
        val prefs = requireContext()
            .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedIp = prefs.getString("server_ip", "")
        tvServerIp.text = if (savedIp.isNullOrBlank()) {
            getString(R.string.settings_ip_not_set)
        } else {
            savedIp
        }

        btnConfigureIp.setOnClickListener {
            showIpInputDialog(prefs)
        }
    }

    private fun showIpInputDialog(prefs: android.content.SharedPreferences) {
        val currentIp = prefs.getString("server_ip", "") ?: ""
        val edit = android.widget.EditText(requireContext()).apply {
            hint = "192.168.0.10"
            inputType = InputType.TYPE_CLASS_PHONE
            setText(currentIp)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Введите IP-адрес")
            .setView(edit)
            .setPositiveButton("OK") { dlg, _ ->
                val ip = edit.text.toString().trim()
                if (ip.isNotEmpty()) {
                    prefs.edit().putString("server_ip", ip).apply()
                    RetrofitClient.setServerIp(ip)
                    tvServerIp.text = ip
                }
                dlg.dismiss()
            }
            .setNegativeButton("Отмена") { dlg, _ ->
                dlg.dismiss()
            }
            .show()
    }
}
