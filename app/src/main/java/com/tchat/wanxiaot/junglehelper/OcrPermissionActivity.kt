package com.tchat.wanxiaot.junglehelper

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class OcrPermissionActivity : ComponentActivity() {

    private var startOcrAfterPermission: Boolean = false

    private val requestProjection =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val serviceIntent = Intent(this, JungleHelperService::class.java).apply {
                action = JungleHelperService.ACTION_MEDIA_PROJECTION_RESULT
                putExtra(JungleHelperService.EXTRA_MEDIA_PROJECTION_RESULT_CODE, result.resultCode)
                putExtra(JungleHelperService.EXTRA_MEDIA_PROJECTION_DATA, result.data)
                putExtra(JungleHelperService.EXTRA_START_OCR_AFTER_PERMISSION, startOcrAfterPermission)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startOcrAfterPermission =
            intent.getBooleanExtra(JungleHelperService.EXTRA_START_OCR_AFTER_PERMISSION, false)

        val mgr = getSystemService(MediaProjectionManager::class.java)
        if (mgr == null) {
            val serviceIntent = Intent(this, JungleHelperService::class.java).apply {
                action = JungleHelperService.ACTION_MEDIA_PROJECTION_RESULT
                putExtra(JungleHelperService.EXTRA_MEDIA_PROJECTION_RESULT_CODE, RESULT_CANCELED)
                putExtra(JungleHelperService.EXTRA_MEDIA_PROJECTION_DATA, null as Intent?)
                putExtra(JungleHelperService.EXTRA_START_OCR_AFTER_PERMISSION, startOcrAfterPermission)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            finish()
            return
        }

        requestProjection.launch(mgr.createScreenCaptureIntent())
    }
}
