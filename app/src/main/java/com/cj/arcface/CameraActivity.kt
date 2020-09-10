package com.cj.arcface

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Outline
import android.media.FaceDetector
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewOutlineProvider
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.cj.arcface.util.toBitmap
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_camera.*
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


/**
 * ━━━━━━神兽出没━━━━━━
 * 　　　┏┓　　　┏┓
 * 　　┏┛┻━━━┛┻┓
 * 　　┃　　　　　　　┃
 * 　　┃　　　━　　　┃
 * 　　┃　┳┛　┗┳　┃
 * 　　┃　　　　　　　┃
 * 　　┃　　　┻　　　┃
 * 　　┃　　　　　　　┃
 * 　　┗━┓　　　┏━┛Code is far away from bug with the animal protecting
 * 　　　　┃　　　┃    神兽保佑,代码无bug
 * 　　　　┃　　　┃
 * 　　　　┃　　　┗━━━┓
 * 　　　　┃　　　　　　　┣┓
 * 　　　　┃　　　　　　　┏┛
 * 　　　　┗┓┓┏━┳┓┏┛
 * 　　　　　┃┫┫　┃┫┫
 * 　　　　　┗┻┛　┗┻┛
 * ━━━━━━感觉萌萌哒━━━━━━
 *
 *
 * Created by zengyi on 2020/8/28.
 */
class CameraActivity : AppCompatActivity() {

    companion object {
        val TAG = "CameraActivity"
    }

    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService

    private var bitmap: Bitmap? = null

    private var isOne = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        iv_scan.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(p0: View, p1: Outline) {
                p1.setOval(0, 0, p0.width, p0.height)
            }
        }
        iv_scan.clipToOutline = true

        cameraExecutor = Executors.newSingleThreadExecutor()

        startCamera()
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(preview_view.createSurfaceProvider()) }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)//缩短照片拍摄的延迟时间
                //.setCaptureMode(CAPTURE_MODE_MAXIMIZE_QUALITY)//优化照片质量
                //.setTargetRotation(preview_view.display.rotation)//设置输出图像的所需旋转度。
                .build()

            val orientationEventListener = object : OrientationEventListener(this as Context) {
                override fun onOrientationChanged(orientation: Int) {
                    // Monitors orientation values to determine the target rotation value
                    val rotation: Int = when (orientation) {
                        in 45..134 -> Surface.ROTATION_270
                        in 135..224 -> Surface.ROTATION_180
                        in 225..314 -> Surface.ROTATION_90
                        else -> Surface.ROTATION_0
                    }

                    imageCapture?.targetRotation = rotation
                }
            }
            orientationEventListener.enable()


            // Select back camera as a default
            var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA


            //图片分析
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(
                    Size(
                        resources.displayMetrics.widthPixels,
                        resources.displayMetrics.heightPixels
                    )
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                //.setTargetRotation(preview_view.display.rotation)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { image ->
                bitmap = image.image?.toBitmap()
                bitmap?.apply {
                    rotaingImageView(270, bitmap = this)?.let { cropBitmap(it) }
                    //cropBitmap(this)
                }
                image.close()
            })

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    imageAnalysis,
                    preview,
                    imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed$exc")
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    // Unbind use cases before rebinding
                    cameraProvider.unbindAll()
                    // Bind use cases to camera
                    cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        imageAnalysis,
                        preview,
                        imageCapture
                    )
                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed$exc")
                }

            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResume() {
        super.onResume()
        isOne = true
    }

    private fun cropBitmap(bitmap: Bitmap) {
        if (isOne) {
            val file = File(filesDir, "head_tmp.png")
            val create = Observable.create<File> { emitter ->
                val intArray = IntArray(2)
                iv_scan.getLocationInWindow(intArray)
                val createBitmap = Bitmap.createBitmap(
                    bitmap, intArray[0], intArray[1], iv_scan.width, iv_scan.height
                )
                //必须是565才能识别
                val bitmap1: Bitmap = createBitmap.copy(Bitmap.Config.RGB_565, true)
                val faceDetector = FaceDetector(bitmap1.width, bitmap1.height, 1)
                val array = arrayOfNulls<FaceDetector.Face>(1)
                val faces = faceDetector.findFaces(bitmap1, array)
                if (faces > 0) {
                    Log.e(TAG, "检测到脸")
                    val fos = FileOutputStream(file.path)
                    createBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    fos.flush()
                    fos.close()
                    emitter.onNext(file)
                } else {
                    Log.e(TAG, "未检测到脸")
                    emitter.onError(Throwable("未检测到脸"))
                }

            }
            var disposable: Disposable? = null
            val observer = object : Observer<File> {
                override fun onNext(t: File) {
                    disposable?.dispose()
                    isOne = false
                    setResult(Activity.RESULT_OK)
                    finish()
                }

                override fun onError(e: Throwable) {
                    isOne = true
                }

                override fun onComplete() {

                }

                override fun onSubscribe(d: Disposable) {
                    disposable = d
                }
            }
            create.subscribeOn(Schedulers.computation())//指定被观察者线程
                .observeOn(AndroidSchedulers.mainThread())//指定观察者线程
                .subscribe(observer)
        }

    }

    fun rotaingImageView(angle: Int, bitmap: Bitmap): Bitmap? {
        //旋转图片 动作   
        val matrix = Matrix()
        matrix.postRotate(angle.toFloat())
        // 创建新的图片   
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    /**拍照*/
    private fun takePhoto() {
        // Create time-stamped output file to hold the image
        val photoFile = File(filesDir, "head_tmp.jpg")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(photoFile)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture?.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}")
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    Log.e(TAG, "Photo capture succeeded: $savedUri")
                }
            })
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        bitmap?.recycle()
    }
}