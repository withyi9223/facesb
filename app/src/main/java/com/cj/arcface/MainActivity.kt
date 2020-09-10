package com.cj.arcface

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Message
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cj.arcface.util.goToActForResult
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File


class MainActivity : AppCompatActivity() {


    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        RxPermissions(this).request(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_PHONE_STATE
        ).subscribe { t: Boolean ->
            if (t) {
                initView()
            } else {
                finish()
            }
        }


    }

    private fun initView() {
        textView.setOnClickListener {
            goToActForResult<CameraActivity>(101)
        }
        textView1.setOnClickListener {
            goToActForResult<FaceSbActivity>(101)
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            101 -> {
                if (resultCode == Activity.RESULT_OK) {
                    val file = File(filesDir.toString(), "head_tmp.png")
                    if (file.exists() && file.isFile) {
                        Glide.with(this).load(file)
                            .skipMemoryCache(true) // 不使用内存缓存  
                            .diskCacheStrategy(DiskCacheStrategy.NONE) // 不使用磁盘缓存 
                            .into(imageView)
                    }
                }
            }
            else -> {

            }
        }
    }

    
}