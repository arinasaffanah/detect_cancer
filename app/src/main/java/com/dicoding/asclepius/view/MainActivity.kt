package com.dicoding.asclepius.view


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dicoding.asclepius.databinding.ActivityMainBinding
import com.dicoding.asclepius.helper.ImageClassifierHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentImageUri: Uri? = null
    private lateinit var imageClassifierHelper: ImageClassifierHelper

    companion object {
        private const val REQUEST_CODE_PERMISSION = 100
    }

    private val galleryResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                currentImageUri = uri
                showImage()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupImageClassifier()
        setupButtons()
        checkAndRequestPermissions()
    }

    private fun setupImageClassifier() {
        imageClassifierHelper = ImageClassifierHelper(this)
    }

    private fun setupButtons() {
        with(binding) {
            // Tombol untuk memilih gambar dari galeri
            galleryButton.setOnClickListener {
                checkPermissionAndOpenGallery()
            }

            // Tombol untuk menganalisis gambar yang telah dipilih
            analyzeButton.setOnClickListener {
                if (currentImageUri != null) {
                    analyzeImage()
                } else {
                    showToast("Please select an image first")
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermission(permission)
        }
    }

    private fun checkPermissionAndOpenGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                startGallery()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission) -> {
                showPermissionRationaleDialog(permission)
            }
            else -> {
                requestPermission(permission)
            }
        }
    }

    private fun requestPermission(permission: String) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(permission),
            REQUEST_CODE_PERMISSION
        )
    }

    private fun showPermissionRationaleDialog(permission: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs access to your gallery to select images for analysis.")
            .setPositiveButton("Grant Permission") { _, _ ->
                requestPermission(permission)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                showToast("Feature unavailable without permission")
            }
            .create()
            .show()
    }

    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This feature requires gallery access. Please enable it in app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                showToast("Feature unavailable without permission")
            }
            .create()
            .show()
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            startActivity(this)
        }
    }

    private fun startGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryResult.launch(intent)
    }

    private fun showImage() {
        currentImageUri?.let {
            try {
                binding.previewImageView.setImageURI(it)
                binding.analyzeButton.isEnabled = true
            } catch (e: Exception) {
                showToast("Error loading image: ${e.localizedMessage}")
                binding.analyzeButton.isEnabled = false
            }
        }
    }

    private fun analyzeImage() {
        currentImageUri?.let { uri ->
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                val result = imageClassifierHelper.classifyStaticImage(uri)
                showResult(result)
            } catch (e: Exception) {
                showToast("Error analyzing image: ${e.localizedMessage}")
            }
        }
    }

    private fun showResult(result: String) {
        Intent(this, ResultActivity::class.java).apply {
            putExtra("RESULT", result)
            putExtra("IMAGE_URI", currentImageUri.toString())
            startActivity(this)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            when {
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED -> {
                    showToast("Permission granted")
                    startGallery()
                }
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    permissions[0]
                ) -> {
                    // User checked "Don't ask again"
                    showPermissionSettingsDialog()
                }
                else -> {
                    showToast("Permission denied")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        imageClassifierHelper.close()
    }
}