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

                    val filtered = ids
                        .filterIsInstance<String>()
                        .filter { id -> shouldKeepCamera(manager, id) }
                        .toTypedArray()

                    param.result = filtered
                    XposedBridge.log(
                        "[CameraOverride] getCameraIdList -> ${filtered.joinToString()}"
                    )
                }
            }
        )

        XposedBridge.hookAllMethods(
            cameraManagerClass,
            "openCamera",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val cameraId = param.args.getOrNull(0) as? String ?: return
                    val manager = param.thisObject as? CameraManager ?: return

                    if (!shouldKeepCamera(manager, cameraId)) {
                        XposedBridge.log(
                            "[CameraOverride] Блокирую openCamera($cameraId) для $TARGET_PACKAGE"
                        )
                        param.throwable = CameraAccessException(
                            CameraAccessException.CAMERA_DISCONNECTED,
                            "Camera $cameraId скрыта модулем CameraOverride"
                        )
                    }
                }
            }
        )

        XposedBridge.log(
            "[CameraOverride] Хуки установлены для $TARGET_PACKAGE, " +
                "показываю только заднюю камеру $KEPT_BACK_CAMERA_ID"
        )
    }

    private fun shouldKeepCamera(manager: CameraManager, id: String): Boolean {
        if (id == KEPT_BACK_CAMERA_ID) return true

        val facing = try {
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
        } catch (e: Exception) {
            null
        } ?: return true

        return facing != CameraCharacteristics.LENS_FACING_BACK
    }
}
