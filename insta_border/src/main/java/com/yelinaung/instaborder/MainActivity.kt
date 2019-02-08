package com.yelinaung.instaborder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.media.ExifInterface
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference


class MainActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {
    val MY_PERMISSIONS_REQUEST_READ_CONTACTS = 2
    var fileName: String = "default_file_name"

    private fun getCameraPhotoOrientation(context: Context, imageUri: Uri, imagePath: String): Int {
        var rotate = 0
        try {
            context.contentResolver.notifyChange(imageUri, null)
            val imageFile = File(imagePath)

            val exif = ExifInterface(imageFile.absolutePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_270 -> rotate = 270
                ExifInterface.ORIENTATION_ROTATE_180 -> rotate = 180
                ExifInterface.ORIENTATION_ROTATE_90 -> rotate = 90
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return rotate
    }

    private fun openImage() {
        val intent = Intent(Intent.ACTION_PICK);
        intent.type = "image/*"
        startActivityForResult(intent, 22)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide()
        this.checkPermission()

        export.isEnabled = false

        borderValue.text = "0"
        seekBar.max = 120
        seekBar.incrementProgressBy(1)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seeBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress == 0) {
                    borderValue.text = "0"
                } else {
                    borderValue.text = getString(R.string.border_value, progress.div(10.0).toString())
                }

                val newProgress = progress * 2
                photoView.setPadding(newProgress, newProgress, newProgress, newProgress)
                photoView.requestLayout()
            }
        })

        export.setOnClickListener {
            SaveImage(this).execute()
        }

        openNewImage.setOnClickListener {
            this.openImage()
        }
        openFile.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                this.checkPermission()
            } else {
                this.openImage()
            }
        }
    }

    private class SaveImage internal constructor(context: MainActivity) : AsyncTask<String, Void, String>() {
        private val activityReference: WeakReference<MainActivity> = WeakReference(context)

        override fun doInBackground(vararg p0: String?): String {
            try {
                val activity = activityReference.get()
                activity?.saveImage()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            return "Done"
        }

        override fun onPostExecute(result: String?) {
            val activity = activityReference.get()
            if (activity == null || activity.isFinishing) return
        }
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            ) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                val builder = AlertDialog.Builder(this)
                builder
                    .setMessage("To save pictures, the app needs permission to read/write image files")
                    .setPositiveButton(android.R.string.yes) { _, _ ->
                        // continue with delete
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            MY_PERMISSIONS_REQUEST_READ_CONTACTS
                        )
                    }
                    .show()
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    MY_PERMISSIONS_REQUEST_READ_CONTACTS
                )
            }
        } else {
            println("already has permission")
        }
    }

    private fun saveImage() {
        photoViewWrapper.isDrawingCacheEnabled = true
        photoViewWrapper.buildDrawingCache()
        val cache = photoViewWrapper.drawingCache
        val extension = fileName.substring(fileName.lastIndexOf("."))
        val split = fileName.split(extension)
        val newFileName = "${split[0]}_with_border$extension"
        try {
            val file = File(Environment.getExternalStorageDirectory(), newFileName)
            val output = FileOutputStream(file)
            cache.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.flush()
            output.close()
            MediaStore.Images.Media.insertImage(contentResolver, file.absolutePath, file.name, file.name)
            runOnUiThread {
                Toast.makeText(this, "Saved new image at ${file.absolutePath}!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            photoViewWrapper.destroyDrawingCache()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_READ_CONTACTS -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    openFile.visibility = View.VISIBLE
                    openFile.text = "Tap to open image"
                } else {
                    openFile.visibility = View.VISIBLE
                    openFile.text = "Please sir. I need permission"
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == 22) {
            val selectedImage = data?.data
            val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)

            if (selectedImage != null) {
                val cursor = contentResolver.query(selectedImage, filePathColumn, null, null, null)
                cursor!!.moveToFirst()

                val columnIndex = cursor.getColumnIndex(filePathColumn[0])
                val filePath = cursor.getString(columnIndex)
                cursor.close()

                // HACKISH AF
                this.fileName = filePath.substring(filePath.lastIndexOf("/") + 1)

                val orientation = this.getCameraPhotoOrientation(this, selectedImage, filePath)
                val matrix = Matrix()
                matrix.postRotate(orientation.toFloat())
                val original = BitmapFactory.decodeFile(filePath)
                val rotatedBitmap =
                    Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                photoView.setImageBitmap(rotatedBitmap)
                photoView.visibility = View.VISIBLE
                openFile.visibility = View.GONE
                export.isEnabled = true
                openNewImage.visibility = View.VISIBLE
            } else {
                photoView.visibility = View.GONE
                openFile.visibility = View.VISIBLE
                openFile.text = "Oh no! Something went wrong"
            }
        }
    }
}
