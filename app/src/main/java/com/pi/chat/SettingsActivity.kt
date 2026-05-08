package com.pi.chat

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.apply {
            title = "Settings"
            setDisplayHomeAsUpEnabled(true)
        }

        val editUrl = findViewById<TextInputEditText>(R.id.editServerUrl)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)

        editUrl.setText(Prefs.getServerUrl(this))

        btnSave.setOnClickListener {
            val url = editUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                Prefs.setServerUrl(this, url)
                Toast.makeText(this, "Saved. Reconnect to apply.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
