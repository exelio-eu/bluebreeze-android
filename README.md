# BlueBreeze Android

[![Maven Central](https://img.shields.io/maven-central/v/dev.likemagic/bluebreeze.svg)](https://central.sonatype.com/artifact/dev.likemagic/bluebreeze)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-lightgrey.svg)](build.gradle.kts)

BlueBreeze is a modern Bluetooth LE library for Android, built on Kotlin coroutines and
`Flow` for all data streams. It wraps Android's callback-based
`BluetoothGatt`/`BluetoothLeScanner` APIs with `suspend` operations and serial per-device request
queuing, so you don't have to hand-write connection state machines or worry about overlapping BLE
requests.

- **Coroutines-first** -- every state (adapter power, authorization, discovered devices, connection
  status, characteristic data, ...) is a `BBObservable`: read its current `value`, collect its
  `values` (the current value, then new ones), or collect only its `updates`.
- **`suspend` operations** -- connect, discover services, read, write, and subscribe/unsubscribe
  are all `suspend` calls with a built-in timeout, GATT callbacks are abstracted.
- **Automatic per-device request queuing** -- operations on the same device are serialized in call
  order. You can fire off several suspend calls without worrying about `BluetoothGatt`'s limitation
  of one request at a time.
- **Runtime permission handling built in** -- [`BBManager`](bluebreeze/src/main/java/dev/likemagic/bluebreeze/BBManager.kt) requests and tracks the Bluetooth permissions it needs across 
  every Android version
- **Bluetooth SIG assigned numbers built in** -- known company IDs, service UUIDs, and
  characteristic UUIDs are bundled and looked up automatically.

## Installation

BlueBreeze is published to Maven Central. Add it to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.likemagic:bluebreeze:1.1.0")
}
```

## Requirements

- Android API 21+ (minSdk), compiled against API 36
- Declare the Bluetooth permissions BlueBreeze needs in your app's `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

## Quick start

```kotlin
import dev.likemagic.bluebreeze.BBManager
import dev.likemagic.bluebreeze.BBState
import dev.likemagic.bluebreeze.BBAuthorization
import dev.likemagic.bluebreeze.BBCharacteristicProperty

val manager = BBManager(context)

// Request permissions if needed, then start scanning once the adapter is on
if (manager.authorizationStatus.value != BBAuthorization.authorized) {
    manager.authorizationRequest(activity)
}

lifecycleScope.launch {
    manager.state.values.collect { state ->
        if (state == BBState.poweredOn) {
            manager.scanStart(context)
        }
    }
}

// Observe scan results
lifecycleScope.launch {
    manager.scanResults.collect { result ->
        println("${result.name ?: "Unknown device"} ${result.rssi}")
    }
}

// Connect, discover, and talk to a device
lifecycleScope.launch {
    val device = manager.devices.value.values.firstOrNull() ?: return@launch

    device.connect()
    device.discoverServices()
    device.requestMtu(255)

    for (service in device.services.value) {
        for (characteristic in service.characteristics) {
            if (BBCharacteristicProperty.read in characteristic.properties) {
                val data = characteristic.read()
                println("${characteristic.uuid} $data")
            }
        }
    }

    device.disconnect()
}
```

### Observing state

Every piece of state is a `BBObservable`, which you can query in three ways:

```kotlin
characteristic.data.value                       // the current value
characteristic.data.values.collect { data -> }  // the current value, then new values
characteristic.data.updates.collect { data -> } // only values that arrive from now on
```

For example, to receive notifications:

```kotlin
lifecycleScope.launch {
    characteristic.data.updates.collect { data ->
        println("${characteristic.uuid} $data")
    }
}
characteristic.subscribe()
```

In Jetpack Compose, use
`observable.values.collectAsStateWithLifecycle(initialValue = observable.value)`.

Call `manager.close()` when you're done with it (e.g. in `onDestroy`) to unregister the broadcast
receivers `BBManager` registers for its lifetime.

See [`example`](example) for a full Jetpack Compose app built on top of BlueBreeze.

## Background updates

If you need to stay connected or keep receiving data while your app isn't visible, run BlueBreeze with a
[foreground service](https://developer.android.com/develop/background-work/services/fgs) of type
`connectedDevice`. The service keeps the process alive and shows the user a persistent
notification.

### Manifest

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<!-- Android 14+: required for the connectedDevice service type -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<!-- Android 13+: required for the service's notification to be shown -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<application>
    <service
        android:name=".BluetoothService"
        android:exported="false"
        android:foregroundServiceType="connectedDevice" />
</application>
```

### Service

Share a single `BBManager` between your UI and the service, for example by creating it in your
`Application` class, so that both see the same devices and connections. The service then collects
data for as long as it runs. This example uses `LifecycleService` from
`androidx.lifecycle:lifecycle-service`:

```kotlin
class BluetoothService : LifecycleService() {
    override fun onCreate() {
        super.onCreate()

        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Bluetooth")
                .build()
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Connected to your sensor")
            .setSmallIcon(R.drawable.ic_bluetooth)
            .build()

        // The type argument is ignored below Android 10
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )

        val characteristic = (application as MyApp).sensorCharacteristic
        lifecycleScope.launch {
            characteristic.data.updates.collect { data ->
                // Store or process data while the app is in the background
            }
        }
    }
}
```

Keep these in mind:

- **Start the service while your app is in the foreground**, e.g. right after connecting. Since
  Android 12, apps can't start a foreground service from the background, with few exceptions.
  Use `ContextCompat.startForegroundService(context, Intent(context, BluetoothService::class.java))`.
- **Get the Bluetooth permissions first.** On Android 14+, starting a `connectedDevice` service
  throws a `SecurityException` unless `BLUETOOTH_CONNECT` (or another Bluetooth permission) is
  already granted, so check `manager.authorizationStatus.value` before starting it.
- **Stop the service** with `stopSelf()` or `stopService(...)` once you disconnect, so that the
  notification goes away. Only call `manager.close()` when the `BBManager` itself is no longer
  needed, not when the UI goes away.
- **Scanning in the background** needs a filter: pass `serviceUUIDs` to `scanStart`. Android
  pauses unfiltered scans while the screen is off. On Android 10 and 11, where BLE scanning is
  tied to location access, scanning from the background also needs `ACCESS_BACKGROUND_LOCATION`.

## Documentation

The public API is documented with KDoc directly in the source under
[`bluebreeze/src/main/java/dev/likemagic/bluebreeze`](bluebreeze/src/main/java/dev/likemagic/bluebreeze).

## License

BlueBreeze is available under the MIT license. See [LICENSE](LICENSE) for details.
