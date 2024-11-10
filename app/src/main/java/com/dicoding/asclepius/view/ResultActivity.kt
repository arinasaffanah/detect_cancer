package com.dicoding.asclepius.view


import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import com.dicoding.asclepius.databinding.ActivityResultBinding

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val result = intent.getStringExtra("RESULT") ?: "No result"
        val imageUriString = intent.getStringExtra("IMAGE_URI")
        val imageUri = Uri.parse(imageUriString)

        binding.resultText.text = result

        // Menampilkan gambar yang dianalisis
        imageUri?.let {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
            binding.resultImage.setImageBitmap(bitmap)
        }
    }
}