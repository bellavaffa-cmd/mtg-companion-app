package com.mtgcompanion.app.data

import retrofit2.HttpException
import java.io.IOException

/**
 * Whether [e] indicates the request failed because the device is offline: a connectivity error
 * (IOException — unknown host, no route, timeout) or a 504 from OkHttp's only-if-cached path when
 * offline and nothing suitable is in the cache.
 */
fun isOffline(e: Throwable): Boolean =
    e is IOException || (e as? HttpException)?.code() == 504
