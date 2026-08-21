package com.example.cameraoverride

import android.hardware.camera2.CameraAccessException
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackageParam

/**
 * Прячет сломанную 1x-камеру от Instagram, оставляя доступной только 0.5x (ultrawide).
 *
 * Как это работает:
 *  1) Хукаем CameraManager.getCameraIdList() — из результата убираем ID сломанной камеры.
 *     Instagram сам ищет "заднюю камеру" перебором списка ID через CameraCharacteristics,
 *     поэтому если в списке останется только ultrawide — он станет "основной" задней камерой.
 *  2) На случай, если где-то в коде Instagram ID захардкожен напрямую (без похода в список) —
 *     дополнительно блокируем openCamera() именно для этого ID.
 *
 * ВАЖНО: перед сборкой проверь фактический ID сломанной камеры через приложение
 * "Camera2 API Probe" (Google Play) — на большинстве Xiaomi/Poco устройств это:
 *   0 = основная (1x), 1 = фронтальная, 2 = ultrawide (0.5x), 3 = макро.
 * Но точный номер лучше свериться на своём устройстве и прошивке (crDroid), т.к. нумерация
 * камер задаётся вендорским camera HAL и может отличаться.
 */
class CameraOverride : IXposedHookLoadPackage {

    companion object {
        // Пакет, для которого действует хук
        private const val TARGET_PACKAGE = "com.instagram.android"

        // ID сломанной камеры (1x). ИЗМЕНИ при необходимости под свой девайс.
        private const val BROKEN_CAMERA_ID = "0"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        val cameraManagerClass = XposedHelpers.findClass(
            "android.hardware.camera2.CameraManager",
            lpparam.classLoader
        )

        // --- 1. Прячем ID сломанной камеры из списка доступных ---
        XposedBridge.hookAllMethods(
            cameraManagerClass,
            "getCameraIdList",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val ids = param.result as? Array<*> ?: return
                    val filtered = ids
                        .filterIsInstance<String>()
                        .filter { it != BROKEN_CAMERA_ID }
                        .toTypedArray()
                    param.result = filtered
                    XposedBridge.log(
                        "[CameraOverride] getCameraIdList -> ${filtered.joinToString()}"
                    )
                }
            }
        )

        // --- 2. Страховка: блокируем прямое открытие сломанной камеры ---
        XposedBridge.hookAllMethods(
            cameraManagerClass,
            "openCamera",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val cameraId = param.args.getOrNull(0) as? String
                    if (cameraId == BROKEN_CAMERA_ID) {
                        XposedBridge.log(
                            "[CameraOverride] Блокирую openCamera($BROKEN_CAMERA_ID) для $TARGET_PACKAGE"
                        )
                        param.throwable = CameraAccessException(
                            CameraAccessException.CAMERA_DISCONNECTED,
                            "Camera $BROKEN_CAMERA_ID скрыта модулем CameraOverride"
                        )
                    }
                }
            }
        )

        XposedBridge.log(
            "[CameraOverride] Хуки установлены для $TARGET_PACKAGE, скрываю камеру $BROKEN_CAMERA_ID"
        )
    }
}
