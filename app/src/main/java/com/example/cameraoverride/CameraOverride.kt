package com.example.cameraoverride

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class CameraOverride : IXposedHookLoadPackage {

    companion object {
        private const val TARGET_PACKAGE = "com.instagram.android"
        private const val KEPT_BACK_CAMERA_ID = "3"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        val cameraManagerClass = XposedHelpers.findClass(
            "android.hardware.camera2.CameraManager",
            lpparam.classLoader
        )

        XposedBridge.hookAllMethods(
            cameraManagerClass,
            "getCameraIdList",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val ids = param.result as? Array<*> ?: return
                    val manager = param.thisObject as? CameraManager ?: return
