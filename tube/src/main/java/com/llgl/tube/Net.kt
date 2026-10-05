package com.llgl.tube

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object Net {
    private fun caps(context: Context): NetworkCapabilities? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = cm.activeNetwork ?: return null
        return cm.getNetworkCapabilities(network)
    }

    fun online(context: Context): Boolean = caps(context)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    /** Wi-Fi or ethernet: where streaming video all day is not a bill. */
    fun onWifi(context: Context): Boolean {
        val c = caps(context) ?: return false
        return c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
