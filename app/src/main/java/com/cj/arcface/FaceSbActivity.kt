package com.cj.arcface

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.hardware.Camera
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.ViewTreeObserver
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.arcsoft.face.*
import com.arcsoft.face.enums.DetectMode
import com.arcsoft.imageutil.ArcSoftImageFormat
import com.cj.arcface.camera.CameraHelper
import com.cj.arcface.camera.CameraListener
import com.cj.arcface.util.CommUtils
import com.cj.arcface.util.ConfigUtil
import com.cj.arcface.util.RecognizeColor

import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_sb_face.*
import kotlinx.android.synthetic.main.activity_sb_face.iv_scan
import kotlinx.android.synthetic.main.activity_sb_face.textureView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

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
 * Created by yi on 2020/8/12.
 */
class FaceSbActivity : AppCompatActivity(), ViewTreeObserver.OnGlobalLayoutListener {

    companion object {
        val TAG = "FaceSbActivity"
    }

    private var cameraHelper: CameraHelper? = null
    private lateinit var drawHelper: DrawHelper
    private var previewSize: Camera.Size? = null
    private var rgbCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT
    private lateinit var faceEngine: FaceEngine
    private var afCode = -1
    private var processMask =
        FaceEngine.ASF_AGE or FaceEngine.ASF_FACE3DANGLE or FaceEngine.ASF_GENDER or FaceEngine.ASF_LIVENESS
    var headBmp: Bitmap? = null
    var isOne = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sb_face)
        when (FaceEngine.activeOnline(this, Constants.APP_ID, Constants.SDK_KEY)) {
            ErrorInfo.MOK -> {
                Log.e(TAG, "active_success")
            }
            ErrorInfo.MERR_ASF_ALREADY_ACTIVATED -> {
                Log.e(TAG, "already_activated")
            }
            else -> {
                Log.e(TAG, "active_failed")
            }
        }


        textureView.viewTreeObserver.addOnGlobalLayoutListener(this)

    }

    private fun initEngine() {
        faceEngine = FaceEngine()
        val afCode = faceEngine.init(
            this,
            DetectMode.ASF_DETECT_MODE_VIDEO,
            ConfigUtil.getFtOrient(this),
            16,
            1,
            FaceEngine.ASF_FACE_DETECT
                    or FaceEngine.ASF_AGE
                    or FaceEngine.ASF_FACE3DANGLE
                    or FaceEngine.ASF_GENDER
                    or FaceEngine.ASF_LIVENESS
        )
        Log.e(TAG, "initEngine:  init: $afCode")
        if (afCode != ErrorInfo.MOK) {
            Log.e(TAG, "init_failed:  $afCode")
        }

    }

    private fun unInitEngine() {
        if (afCode == 0) {
            afCode = faceEngine.unInit()
            Log.e(TAG, "unInitEngine: $afCode")
        }
    }

    override fun onGlobalLayout() {
        textureView.viewTreeObserver.removeOnGlobalLayoutListener(this)
        initEngine()
        initCamera()
    }

    private fun initCamera() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val cameraListener: CameraListener = object : CameraListener {
            override fun onCameraOpened(
                camera: Camera,
                cameraId: Int,
                displayOrientation: Int,
                isMirror: Boolean
            ) {
                Log.e(TAG, "onCameraOpened: $cameraId  $displayOrientation $isMirror")
                previewSize = camera.parameters.previewSize
                Log.e(TAG, "previewSize: " + previewSize!!.width + "x" + previewSize!!.height)
                drawHelper = DrawHelper(
                    previewSize!!.width,
                    previewSize!!.height,
                    textureView.width,
                    textureView.height,
                    displayOrientation,
                    cameraId,
                    isMirror,
                    false,
                    false
                )
            }

            override fun onPreview(nv21: ByteArray?, camera: Camera?) {
                val faceInfoList: List<FaceInfo> = ArrayList()
                var code = faceEngine.detectFaces(
                    nv21,
                    previewSize!!.width,
                    previewSize!!.height,
                    FaceEngine.CP_PAF_NV21,
                    faceInfoList
                )
                if (code == ErrorInfo.MOK && faceInfoList.isNotEmpty()) {
                    code = faceEngine.process(
                        nv21,
                        previewSize!!.width,
                        previewSize!!.height,
                        FaceEngine.CP_PAF_NV21,
                        faceInfoList,
                        processMask
                    )
                    if (code != ErrorInfo.MOK) {
                        return
                    }
                } else {
                    return
                }
                val ageInfoList: List<AgeInfo> = ArrayList()
                val genderInfoList: List<GenderInfo> = ArrayList()
                val face3DAngleList: List<Face3DAngle> = ArrayList()
                val faceLivenessInfoList: List<LivenessInfo> = ArrayList()
                val ageCode = faceEngine.getAge(ageInfoList)
                val genderCode = faceEngine.getGender(genderInfoList)
                val face3DAngleCode = faceEngine.getFace3DAngle(face3DAngleList)
                val livenessCode = faceEngine.getLiveness(faceLivenessInfoList)
                // 有其中一个的错误码不为ErrorInfo.MOK，return
                if (ageCode or genderCode or face3DAngleCode or livenessCode != ErrorInfo.MOK) {
                    return
                }
                val drawInfoList: MutableList<DrawInfo> = ArrayList<DrawInfo>()
                for (i in faceInfoList.indices) {
                    drawInfoList.add(
                        DrawInfo(
                            drawHelper.adjustRect(faceInfoList[i].rect),
                            genderInfoList[i].gender,
                            ageInfoList[i].age,
                            faceLivenessInfoList[i].liveness,
                            RecognizeColor.COLOR_UNKNOWN, null
                        )
                    )
                }
                if (drawInfoList.size > 0) {
                    for (i in drawInfoList.indices) {
                        val rect: Rect = drawInfoList[i].rect
                        val rect1 = Rect()
                        iv_scan.getGlobalVisibleRect(rect1)
                        if (rect1.contains(rect)) {
                            //为了美观，扩大rect截取注册图
                            val cropRect: Rect =
                                CommUtils.getBestRect(
                                    previewSize!!.width, previewSize!!.height, faceInfoList[i].rect
                                )
                            cropRect.left = cropRect.left and 3.inv()
                            cropRect.top = cropRect.top and 3.inv()
                            cropRect.right = cropRect.right and 3.inv()
                            cropRect.bottom = cropRect.bottom and 3.inv()

                            headBmp = CommUtils.getHeadImage(
                                nv21,
                                previewSize!!.width,
                                previewSize!!.height,
                                faceInfoList[i].orient,
                                cropRect,
                                ArcSoftImageFormat.NV21
                            )
                            headBmp?.apply {
                                cropBitmap(this)
                            }
                            break
                        }
                    }
                }

            }

            override fun onCameraClosed() {
                Log.e(TAG, "onCameraClosed: ")
            }

            override fun onCameraError(e: Exception) {
                Log.e(TAG, "onCameraError: " + e.message)
            }

            override fun onCameraConfigurationChanged(cameraID: Int, displayOrientation: Int) {
                drawHelper.cameraDisplayOrientation = displayOrientation
                Log.e(TAG, "onCameraConfigurationChanged: $cameraID  $displayOrientation")
            }
        }

        cameraHelper = CameraHelper.Builder()
            .previewViewSize(Point(textureView.measuredWidth, textureView.measuredHeight))
            .rotation(windowManager.defaultDisplay.rotation)
            .specificCameraId(rgbCameraId)
            .isMirror(false)
            .previewOn(textureView)
            .cameraListener(cameraListener)
            .build()
        cameraHelper!!.init()
        cameraHelper!!.start()
    }

    override fun onResume() {
        super.onResume()
        isOne = true
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraRecycle()
    }

    private fun cameraRecycle() {
        cameraHelper?.release()
        unInitEngine()
        headBmp?.recycle()

    }


    private fun cropBitmap(bitmap: Bitmap) {
        if (isOne) {
            isOne = false
            Observable.create<File> { emitter ->
                val file = File(filesDir, "head_tmp.png")
                try {
                    val out = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                    out.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                emitter.onNext(file)
            }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : Observer<File?> {
                    override fun onSubscribe(d: Disposable) {

                    }

                    override fun onError(e: Throwable) {
                        e.printStackTrace()
                        isOne = false
                    }

                    override fun onComplete() {

                    }

                    override fun onNext(t: File) {
                        isOne = false
                        setResult(Activity.RESULT_OK)
                        finish()
                    }

                })
        }
    }
}