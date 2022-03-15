package com.example.multiimageview

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.PermissionChecker
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import android.provider.MediaStore

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.drawToBitmap
import com.example.multiimageview.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

typealias LumaListener = (luma: Double) -> Unit

lateinit var ld: LayerDrawable
private var myBitmap: Bitmap? = null
private var myBitmap2: Bitmap? = null


class MainActivity : AppCompatActivity() {

    //akash
    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        ld = ContextCompat.getDrawable(this, R.drawable.meterlayer) as LayerDrawable

        val notclicked = BitmapFactory.decodeResource(this.resources, R.drawable.notclicked)
        val randomclick = BitmapFactory.decodeResource(this.resources, R.drawable.kw)

         val mDrawable = BitmapDrawable(resources, notclicked )
       /*  val randomDrawable = BitmapDrawable(resources, randomclick )
         //ld.setDrawableByLayerId(R.id.kwImage, randomDrawable)
         ld.setDrawableByLayerId(R.id.kwImage, mDrawable)
         ld.setDrawableByLayerId(R.id.kwhImage, mDrawable)
         ld.setDrawableByLayerId(R.id.kvahImage, mDrawable)*/
         viewBinding.meterIv.setImageDrawable(ld);



        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        //viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }
        viewBinding.save.setOnClickListener { saveFromImageView() }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun saveFromImageView() {
        //var finalImage = viewBinding.meterIv.drawable.toBitmap()
        var finalBitmap = viewBinding.meterIv.drawToBitmap()

        val bitmapFile = getPhotoFileUri("")
        var savedUri = Uri.fromFile(bitmapFile)

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "ComboImage")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + "UPCLSmartBilling"
            )
            //put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val resolver = this.contentResolver

        //Inserting the contentValues to contentResolver and getting the Uri
        var savedUrilocal = resolver!!.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        //Opening an outputstream with the Uri that we got
        val fos2 = resolver.openOutputStream(Objects.requireNonNull(savedUrilocal)!!) as FileOutputStream
        //compress images, //Finally writing the bitmap to the output stream that we opened
        finalBitmap?.compress(Bitmap.CompressFormat.JPEG, 70, fos2)
        Objects.requireNonNull(fos2)

        //Compress image
        //Finally writing the bitmap to the output stream that we opened

        val bytearrayoutputstream = ByteArrayOutputStream()
        finalBitmap?.compress(
            Bitmap.CompressFormat.WEBP,
            40,
            bytearrayoutputstream
        )

        //viewBinding.meterIv.setImageBitmap(finalImage)
    }

    private fun captureVideo() {}

    fun getPhotoFileUri(fileName: String): File? {
        // Get safe storage directory for photos
        val mediaStorageDir = File(
            this.getExternalFilesDir(Environment.DIRECTORY_PICTURES + File.separator + "ComboImage"),
            "BillingFragment"
        )

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
            Log.d("BillingFragment", "failed to create directory")
        }

        // Return the file target for the photo based on filename
        return File(mediaStorageDir.path + File.separator.toString() + "Akash Jha")
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                        Log.d(TAG, "Average luminosity: $luma")
                    })
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }
   /* private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }*/

    private fun takePhoto() {
        viewBinding.loader.show()
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)

                    val savedUri = output.savedUri

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        myBitmap = ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(
                                this@MainActivity.contentResolver,
                                savedUri!!
                            )
                        )
                    }

                    myBitmap2 = myBitmap!!.copy(Bitmap.Config.ARGB_8888, true)

                    //Compress image
                    val bytearrayoutputstream = ByteArrayOutputStream()
                    myBitmap2?.compress(
                        Bitmap.CompressFormat.WEBP,
                        40,
                        bytearrayoutputstream
                    )
                    val BYTE = bytearrayoutputstream.toByteArray()
                    myBitmap2 = BitmapFactory.decodeByteArray(BYTE, 0, BYTE.size)
                    val mDrawable = BitmapDrawable(resources, myBitmap2)

                    val imagenum = viewBinding.picNum.text

                    val randomclick = BitmapFactory.decodeResource(resources, R.drawable.kw)
                    val randomDrawable = BitmapDrawable(resources, randomclick )

                    if(imagenum == "Pic 1"){
                        viewBinding.loader.hide()

                        viewBinding.picNum.text = "Pic 2"

                        ld.setDrawableByLayerId(R.id.kwImage, mDrawable)
                        viewBinding.meterIv.setImageDrawable(ld);
                    }
                    if(imagenum == "Pic 2"){
                        viewBinding.loader.hide()

                        viewBinding.picNum.text = "Pic 3"

                        ld.setDrawableByLayerId(R.id.kwhImage, mDrawable)
                        viewBinding.meterIv.setImageDrawable(ld);
                    }
                    if(imagenum == "Pic 3"){
                        viewBinding.loader.hide()

                        viewBinding.picNum.text = "Pic 3"

                        ld.setDrawableByLayerId(R.id.kvahImage, mDrawable)
                        viewBinding.meterIv.setImageDrawable(ld);
                    }


                        viewBinding.meterIv.setImageDrawable(ld);
                        viewBinding.meterIv.requestLayout()
                        viewBinding.meterIv.requestFocus()
                        //photoFile.delete()
                }
            }
        )
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}