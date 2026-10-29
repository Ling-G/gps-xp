# GPS XP

Android Xposed / LSPosed 固定定位模块，最低 Android 8.0（API 26）。使用传统 Xposed API 82，需要框架支持 legacy 模块。

[下载 APK](https://github.com/Ling-G/gps-xp/releases/latest) · [版本记录](https://github.com/Ling-G/gps-xp/releases)

默认坐标严格按用户输入保存，不进行坐标系转换：

| 项目 | 默认值 |
| --- | --- |
| 经度 longitude | 121.42404117992659 |
| 纬度 latitude | 29.66665962827546 |

## 安装使用

1. 安装 `gps-xp-debug.apk`。这是开发测试签名版本。
2. 在 LSPosed 管理器中启用 GPS XP，作用域勾选需要测试的目标应用。不要勾选系统框架。
3. 打开 GPS XP，保持默认坐标或修改坐标，打开“启用模拟定位”并保存。
4. 在系统设置中授予目标应用定位权限；如需小数点精度，请允许精确位置。
5. 彻底结束目标应用，再重新启动，然后进入其定位功能。
6. 修改坐标、关闭模拟定位、调整作用域后，都需要重新启动目标应用。关闭后会恢复原始定位接口。

设置界面能打开不表示框架已加载模块。可在 LSPosed 日志查找 `GPS XP: settings loaded for 包名, enabled=true`。若出现 `settings unavailable`，模块会保持不生效，不会擅自套用默认坐标。

## 实现范围

- `Location.getLatitude()` / `getLongitude()`：替换作用域应用进程内读取的经纬度，包括已有 Location 对象。历史轨迹中的 Location 对象也会受影响。
- `LocationManager.getLastKnownLocation()`：直接生成固定位置，无须等待真实 GPS 首次定位。
- `requestLocationUpdates()` / `requestSingleUpdate()` 的 LocationListener 重载：生成回调，支持 Handler / Looper / Executor；固定位置更新最短 1 秒，尊重更长的请求间隔、正的距离阈值、LocationRequest 的最大次数和有效时长。
- `removeUpdates(LocationListener)`：停止并清理相应监听，已排队但尚未开始的回调也会跳过。
- Android 11+ `getCurrentLocation()`：通过应用传入的 Executor 回调，尊重 CancellationSignal。
- 生成的位置含当前系统时间、单调时钟、5 米精度、0 海拔、0 速度和模拟位置标记。不修改 `isMock()` / `isFromMockProvider()`，不隐藏框架。
- 不拦截 PendingIntent 定位注册，不模拟卫星/NMEA/地理围栏、Wi-Fi、基站或 IP；Google Play Services、百度、高德等 SDK 的独立定位实现不保证覆盖。如果它们返回 Android Location，并在当前进程读取上述 getter，经纬度替换可能有效，但不能保证它们主动产生回调。
- 不修改系统定位服务、定位开关或权限。仅在目标应用已经拥有定位权限时启用替换。合成回调运行于目标应用进程，未提供后台定位调度保证。

配置由私有 SharedPreferences 保存，通过只读 ContentProvider 跨进程读取；其他应用可以读取模拟坐标和开关，不能通过 Provider 改写。`forceQueryable` 使 Android 11+ 的目标应用能够发现模块 Provider。每个目标进程启动时读取一次，以避免定位回调频繁执行跨进程查询。安装在其他 Android 用户或工作资料时应在相同资料下安装、启用。

输入坐标的原始坐标系未指定，因此模块原样使用。Android 原生位置通常按 WGS84 解释；地图显示若采用 GCJ-02 / BD-09，可能存在偏移。

## 构建

安装 JDK 17 和 Android SDK（platform android-36，build-tools 36.1.0）。官方 Xposed API 82 jar 已作为仅编译依赖放入 `app/libs`，不会打包进 APK。

Windows 不依赖 Gradle 的构建方式：

```powershell
$env:JAVA_HOME = '你的 JDK 17 路径'
.\build-apk.ps1 -Sdk '你的 Android SDK 路径'
```

输出为 `build/manual/gps-xp-debug.apk`。需要不同 SDK build-tools 时传入 `-BuildTools` 参数。

Android Studio 可直接打开本项目。或使用 Gradle 8.13：

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

首次 Gradle 构建需要下载 Android Gradle Plugin 8.13.2 等构建依赖，配置本机 SDK 路径或设置 ANDROID_HOME。Gradle 调试 APK 默认输出至 `app/build/outputs/apk/debug/`。正式发布时请自行管理发布签名密钥。

## 官方接口资料

- [Xposed 模块开发与入口配置](https://github.com/rovo89/XposedBridge/wiki/Development-tutorial)
- [XposedBridge API](https://api.xposed.info/reference/de/robv/android/xposed/XposedBridge.html)
- [Android LocationManager](https://developer.android.com/reference/android/location/LocationManager)
- [Android application 清单属性](https://developer.android.com/guide/topics/manifest/application-element)
- [官方 Xposed API 82 编译依赖](https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar)

## 本次验证（2026-10-29）

- 使用本机 JDK 17、Android API 36、build-tools 36.1.0，通过 `build-apk.ps1` 完成 Java 编译、DEX 转换、APK 封装和签名。
- APK 的 v2 / v3 签名验证通过，包名、启动页面、最低系统版本及 `assets/xposed_init` 已检查。
- `tests/CoordinatesCheck.java` 的 17 项检查通过：包括默认值的双精度保留、经纬度顺序、边界、NaN 与无穷大。
- `tests/EntryLoadCheck.java` 通过：Xposed 入口在没有 Android Looper 的环境中可以完成静态初始化。
- Gradle wrapper 已生成；本机 Gradle 的 Android 插件依赖下载未完成，因此本次未运行 Gradle lint，交付 APK 来自上述独立 SDK 构建脚本。

已在小米 8 / Android 10 / LSPosed 1.9.2（7024）上完成真机验证：模块识别及加载、配置读取、GPS/网络最后位置、单次定位、连续定位、取消监听，以及关闭开关并重启后的原始接口恢复均通过。返回的默认经纬度与输入完全一致，生成的位置保留 `mock=true` 标记。

验证使用独立测试应用。Android 11+ getCurrentLocation、其他系统版本和第三方地图 SDK 尚未实测。
