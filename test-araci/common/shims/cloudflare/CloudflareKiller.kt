package com.lagradost.cloudstream3.network

import okhttp3.Interceptor
import okhttp3.Response

class CloudflareKiller : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
