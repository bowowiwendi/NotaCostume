package com.notacostume.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.notacostume.app.databinding.ActivityEditBinding

class EditNotaActivity : AppCompatActivity() {

    private lateinit var b: ActivityEditBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityEditBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbar.setNavigationOnClickListener { finish() }

        val id = intent.getLongExtra("id", 0L)
        if (id <= 0) {
            finish()
            return
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, FormFragment.forEdit(id))
            .commit()
    }
}
