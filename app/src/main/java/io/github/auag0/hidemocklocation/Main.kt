package io.github.auag0.hidemocklocation

import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.os.Bundle
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.getStaticIntField
import de.robv.android.xposed.XposedHelpers.setIntField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.auag0.hidemocklocation.XposedUtils.invokeOriginalMethod

class Main : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("[HideMockLocation] handleLoadPackage: 包名 = ${lpparam.packageName}")
        try {
            XposedBridge.log("[HideMockLocation] handleLoadPackage: 开始Hook Location方法")
            hookLocationMethods(lpparam.classLoader)
            XposedBridge.log("[HideMockLocation] handleLoadPackage: Location方法Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[HideMockLocation] handleLoadPackage: Location方法Hook失败: ${e.message}")
            XposedBridge.log("[HideMockLocation] handleLoadPackage: 堆栈: ${e.stackTraceToString()}")
        }
        try {
            XposedBridge.log("[HideMockLocation] handleLoadPackage: 开始Hook Settings方法")
            hookSettingsMethods(lpparam.classLoader)
            XposedBridge.log("[HideMockLocation] handleLoadPackage: Settings方法Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[HideMockLocation] handleLoadPackage: Settings方法Hook失败: ${e.message}")
            XposedBridge.log("[HideMockLocation] handleLoadPackage: 堆栈: ${e.stackTraceToString()}")
        }
        try {
            XposedBridge.log("[HideMockLocation] handleLoadPackage: 开始Hook WiFi方法")
            hookWifiMethods(lpparam.classLoader)
            XposedBridge.log("[HideMockLocation] handleLoadPackage: WiFi方法Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[HideMockLocation] handleLoadPackage: WiFi方法Hook失败: ${e.message}")
            XposedBridge.log("[HideMockLocation] handleLoadPackage: 堆栈: ${e.stackTraceToString()}")
        }
        try {
            XposedBridge.log("[HideMockLocation] handleLoadPackage: 开始Hook Telephony方法")
            hookTelephonyMethods(lpparam.classLoader)
            XposedBridge.log("[HideMockLocation] handleLoadPackage: Telephony方法Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[HideMockLocation] handleLoadPackage: Telephony方法Hook失败: ${e.message}")
            XposedBridge.log("[HideMockLocation] handleLoadPackage: 堆栈: ${e.stackTraceToString()}")
        }
        XposedBridge.log("[HideMockLocation] handleLoadPackage: 所有Hook完成")
    }

    private fun hookLocationMethods(classLoader: ClassLoader) {
        val locationClass = findClass(
            "android.location.Location",
            classLoader
        )
        // Hooked android.location.Location isFromMockProvider()
        hookAllMethods(locationClass, "isFromMockProvider", returnConstant(false))
        // Hooked android.location.Location isMock()
        hookAllMethods(locationClass, "isMock", returnConstant(false))
        // Hooked android.location.Location setIsFromMockProvider()
        hookAllMethods(locationClass, "setIsFromMockProvider", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val isFromMockProvider = param.args[0] as Boolean?
                if (isFromMockProvider == true) {
                    param.args[0] = false
                }
            }
        })
        // Hooked android.location.Location setMock()
        hookAllMethods(locationClass, "setMock", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val mock = param.args[0] as Boolean?
                if (mock == true) {
                    param.args[0] = false
                }
            }
        })
        // Hooked android.location.Location getExtras()
        hookAllMethods(locationClass, "getExtras", object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam): Bundle? {
                var extras: Bundle? = param.invokeOriginalMethod() as Bundle?
                extras = getPatchedBundle(extras)
                return extras
            }
        })
        // Hooked android.location.Location setExtras()
        hookAllMethods(locationClass, "setExtras", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val extras = param.args[0] as Bundle?
                param.args[0] = getPatchedBundle(extras)
            }
        })
        // Hooked android.location.Location set()
        try {
            val HAS_MOCK_PROVIDER_MASK = getStaticIntField(locationClass, "HAS_MOCK_PROVIDER_MASK")
            hookAllMethods(locationClass, "set", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        var mFieldsMask = getIntField(param.thisObject, "mFieldsMask")
                        mFieldsMask = mFieldsMask and HAS_MOCK_PROVIDER_MASK.inv()
                        setIntField(param.thisObject, "mFieldsMask", mFieldsMask)

                        var mExtras = getObjectField(param.thisObject, "mExtras") as Bundle?
                        mExtras = getPatchedBundle(mExtras)
                        setObjectField(param.thisObject, "mExtras", mExtras)
                    } catch (e: Throwable) {
                        XposedBridge.log("[HideMockLocation] Location.set Hook执行失败: ${e.message}")
                    }
                }
            })
        } catch (e: Throwable) {
            XposedBridge.log("[HideMockLocation] Location.set Hook设置失败 (可能因为HAS_MOCK_PROVIDER_MASK访问被拒绝): ${e.message}")
        }
    }

    private fun hookSettingsMethods(classLoader: ClassLoader) {
        // Hooked android.provider.Settings.* getStringForUser()
        val settingsClassNames = arrayOf(
            "android.provider.Settings.Secure",
            "android.provider.Settings.System",
            "android.provider.Settings.Global",
            "android.provider.Settings.NameValueCache"
        )
        settingsClassNames.forEach {
            val clazz = findClass(it, classLoader)
            hookAllMethods(clazz, "getStringForUser", object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    val name: String? = param.args[1] as? String?
                    return when (name) {
                        "mock_location" -> "0"
                        else -> try {
                            param.invokeOriginalMethod()
                        } catch (e: Throwable) {
                            param.throwable = e
                            null
                        }
                    }
                }
            })
        }
    }

    private fun hookWifiMethods(classLoader: ClassLoader) {
        try {
            XposedBridge.log("[HideMockLocation] hookWifiMethods: 开始Hook WiFi方法")
            val wifiManagerClass = findClass(
                "android.net.wifi.WifiManager",
                classLoader
            )
            XposedBridge.log("[HideMockLocation] hookWifiMethods: 找到WifiManager类")
            
            // Hooked android.net.wifi.WifiManager getScanResults()
            hookAllMethods(wifiManagerClass, "getScanResults", object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    XposedBridge.log("[HideMockLocation] getScanResults: 方法被调用!")
                    try {
                        // 获取原始扫描结果（仅用于日志记录）
                        val originalResults = param.invokeOriginalMethod() as? List<*> ?: run {
                            XposedBridge.log("[HideMockLocation] getScanResults: 原始结果为空或null")
                            XposedBridge.log("[HideMockLocation] getScanResults: 返回空列表，隐藏WiFi信息")
                            return emptyList<ScanResult>()
                        }
                        XposedBridge.log("[HideMockLocation] getScanResults: 原始结果数量 = ${originalResults.size}")
                        
                        // 返回空列表，隐藏所有WiFi信息
                        XposedBridge.log("[HideMockLocation] getScanResults: 返回空列表，隐藏WiFi信息")
                        return emptyList<ScanResult>()
                    } catch (e: Throwable) {
                        XposedBridge.log("[HideMockLocation] getScanResults: 异常发生: ${e.message}")
                        XposedBridge.log("[HideMockLocation] getScanResults: 堆栈跟踪: ${e.stackTraceToString()}")
                        // 如果出现异常，返回空列表
                        return emptyList<ScanResult>()
                    }
                }
            })
            XposedBridge.log("[HideMockLocation] hookWifiMethods: getScanResults方法Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[HideMockLocation] hookWifiMethods: Hook失败: ${e.message}")
            XposedBridge.log("[HideMockLocation] hookWifiMethods: 堆栈跟踪: ${e.stackTraceToString()}")
        }
    }

    private fun hookTelephonyMethods(classLoader: ClassLoader) {
        try {
            XposedBridge.log("[HideMockLocation] hookTelephonyMethods: 开始Hook Telephony方法")
            val telephonyManagerClass = findClass(
                "android.telephony.TelephonyManager",
                classLoader
            )
            XposedBridge.log("[HideMockLocation] hookTelephonyMethods: 找到TelephonyManager类")
            
            // Hooked android.telephony.TelephonyManager getAllCellInfo()
            hookAllMethods(telephonyManagerClass, "getAllCellInfo", object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    XposedBridge.log("[HideMockLocation] getAllCellInfo: 方法被调用!")
                    try {
                        // 获取原始基站信息
                        val originalCellInfo = param.invokeOriginalMethod() as? List<*> ?: run {
                            XposedBridge.log("[HideMockLocation] getAllCellInfo: 原始结果为空或null")
                            return emptyList<Any>()
                        }
                        XposedBridge.log("[HideMockLocation] getAllCellInfo: 原始基站数量 = ${originalCellInfo.size}")
                        
                        // 返回空列表，隐藏所有基站信息
                        XposedBridge.log("[HideMockLocation] getAllCellInfo: 返回空列表，隐藏基站信息")
                        return emptyList<Any>()
                    } catch (e: Throwable) {
                        XposedBridge.log("[HideMockLocation] getAllCellInfo: 异常发生: ${e.message}")
                        XposedBridge.log("[HideMockLocation] getAllCellInfo: 堆栈跟踪: ${e.stackTraceToString()}")
                        return emptyList<Any>()
                    }
                }
            })
            
            // Hooked android.telephony.TelephonyManager getCellLocation() (已废弃但可能仍在使用)
            try {
                hookAllMethods(telephonyManagerClass, "getCellLocation", object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        XposedBridge.log("[HideMockLocation] getCellLocation: 方法被调用!")
                        try {
                            val originalLocation = param.invokeOriginalMethod()
                            XposedBridge.log("[HideMockLocation] getCellLocation: 原始结果 = $originalLocation")
                            // 返回null，隐藏基站位置信息
                            XposedBridge.log("[HideMockLocation] getCellLocation: 返回null，隐藏基站位置")
                            return null
                        } catch (e: Throwable) {
                            XposedBridge.log("[HideMockLocation] getCellLocation: 异常发生: ${e.message}")
                            return null
                        }
                    }
                })
                XposedBridge.log("[HideMockLocation] hookTelephonyMethods: getCellLocation方法Hook完成")
            } catch (e: Throwable) {
                XposedBridge.log("[HideMockLocation] hookTelephonyMethods: getCellLocation方法Hook失败 (可能不存在): ${e.message}")
            }
            
            // Hooked android.telephony.TelephonyManager getNeighboringCellInfo() (已废弃但可能仍在使用)
            try {
                hookAllMethods(telephonyManagerClass, "getNeighboringCellInfo", object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any? {
                        XposedBridge.log("[HideMockLocation] getNeighboringCellInfo: 方法被调用!")
                        try {
                            val originalNeighbors = param.invokeOriginalMethod() as? List<*> ?: run {
                                XposedBridge.log("[HideMockLocation] getNeighboringCellInfo: 原始结果为空或null")
                                return emptyList<Any>()
                            }
                            XposedBridge.log("[HideMockLocation] getNeighboringCellInfo: 原始相邻基站数量 = ${originalNeighbors.size}")
                            // 返回空列表，隐藏相邻基站信息
                            XposedBridge.log("[HideMockLocation] getNeighboringCellInfo: 返回空列表，隐藏相邻基站信息")
                            return emptyList<Any>()
                        } catch (e: Throwable) {
                            XposedBridge.log("[HideMockLocation] getNeighboringCellInfo: 异常发生: ${e.message}")
                            return emptyList<Any>()
                        }
                    }
                })
                XposedBridge.log("[HideMockLocation] hookTelephonyMethods: getNeighboringCellInfo方法Hook完成")
            } catch (e: Throwable) {
                XposedBridge.log("[HideMockLocation] hookTelephonyMethods: getNeighboringCellInfo方法Hook失败 (可能不存在): ${e.message}")
            }
            
            XposedBridge.log("[HideMockLocation] hookTelephonyMethods: getAllCellInfo方法Hook完成")
        } catch (e: Throwable) {
            XposedBridge.log("[HideMockLocation] hookTelephonyMethods: Hook失败: ${e.message}")
            XposedBridge.log("[HideMockLocation] hookTelephonyMethods: 堆栈跟踪: ${e.stackTraceToString()}")
        }
    }

    /**
     * if "mockLocation" containsKey in the given bundle, set it to false
     *
     * @param origBundle original Bundle object
     * @return Bundle with "mockLocation" set to false
     */
    private fun getPatchedBundle(origBundle: Bundle?): Bundle? {
        if (origBundle?.containsKey("mockLocation") == true) {
            origBundle.putBoolean("mockLocation", false)
        }
        return origBundle
    }
}