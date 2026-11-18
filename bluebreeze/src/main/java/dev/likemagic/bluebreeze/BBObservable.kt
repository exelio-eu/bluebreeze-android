//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import kotlinx.coroutines.flow.Flow

/**
 * An observable value, used for every piece of state BlueBreeze exposes (`BBManager.state`,
 * `BBDevice.connectionStatus`, `BBCharacteristic.data`, and so on).
 *
 * ```kotlin
 * characteristic.data.value                 // the current value
 * characteristic.data.values.collect { }     // the current value, then new values
 * characteristic.data.updates.collect { }    // only new values
 * ```
 */
interface BBObservable<T> {
    /** The current value. */
    val value: T

    /** A stream of the current [value], followed by every value published from now on. */
    val values: Flow<T>

    /** A stream of only the values published from now on, without the current [value]. */
    val updates: Flow<T>
}
