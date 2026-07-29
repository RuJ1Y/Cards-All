package com.example.cards.community

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.cards.community.databinding.ActivityMainBinding
import androidx.lifecycle.lifecycleScope
import com.example.cards.community.network.ServerApi
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            try {
                binding.emptyMessage.text = ServerApi.ping()
            } catch (e: Exception) {
                binding.emptyMessage.text = "连接失败：${e.message}"
            }
        }
    }
}
