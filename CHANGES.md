# 1.1.0

**Breaking changes**
- Every observable property is now a `BBObservable` instead of a `StateFlow`:
  - `value` returns the current value, as before.
  - `values` emits the current value and then new values. Use it if you need replay of the current 
     value on subscription.
  - `updates` emits only new values. Use it if you only want to subscribe to future updates.
- Removed `BBCharacteristic.notifications`, use `data.updates` instead.

**Bug fixes**
- **Scanning**: Extended advertisement packets are now returned on devices that support them.

# 1.0.3

- Add `notifications` property to `BBCharacteristic` as a pure data stream without replay.

# 1.0.2

Optimisation to the permissions activity, removed any unnecessary dependencies that would leak
into the client application.

# 1.0.1

Mainly connection reliability fixes.

**Connection lifecycle**
- Fixed a leak where a clean `disconnect()` didn't release the native GATT client.
- `disconnect()` no longer hangs indefinitely against an unresponsive peripheral.
- `BBCharacteristic.data`/`isNotifying` are now reset on disconnect.

**Error handling**
- `read()`, `write()`, `discoverServices()`, and `requestMtu()` now surface an error immediately
  when the underlying native call fails.
- Fixed a scan-throttle bug that counted an attempt even when the native scan call never started.
- A scan that fails to auto-resume is now logged in the console.

# 1.0.0

BlueBreeze's first stable release. This version focuses on correctness of the operation queue,
full public API documentation, and a fully unit-tested, mockable Bluetooth layer.
No breaking changes to the public API since the beta 0.0.x releases.
