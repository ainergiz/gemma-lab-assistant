package com.example.mediapipeapp.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class WifiPayload(
    val ssid: String,
    val password: String,
    val hostIp: String,
    val port: Int = 8000
)

sealed class WifiConnectionState {
    object Idle : WifiConnectionState()
    object Connecting : WifiConnectionState()
    data class Connected(val network: Network, val hostIp: String) : WifiConnectionState()
    data class Failed(val error: String) : WifiConnectionState()
}

@RequiresApi(Build.VERSION_CODES.Q)
class WifiPairingManager(private val context: Context) {
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var currentCallback: ConnectivityManager.NetworkCallback? = null
    private var boundNetwork: Network? = null
    
    companion object {
        private const val TAG = "WifiPairingManager"
    }
    
    /**
     * Parse QR code content to extract Wi-Fi credentials
     * Expected format: WIFI:T:WPA;S:MetaMaker_Desk;P:fieldlab2025;I:192.168.137.1;;
     */
    fun parseWifiQr(content: String): WifiPayload? {
        return try {
            if (!content.startsWith("WIFI:")) {
                Log.w(TAG, "Invalid QR format: $content")
                return null
            }
            
            val parts = content.removePrefix("WIFI:").split(';')
            val params = mutableMapOf<String, String>()
            
            parts.forEach { part ->
                if (part.contains(':') && part.length >= 3) {
                    val key = part[0].toString()
                    val value = part.substring(2)
                    params[key] = value
                }
            }
            
            val ssid = params["S"] ?: return null
            val password = params["P"] ?: return null
            val hostIp = params["I"] ?: return null
            
            WifiPayload(ssid, password, hostIp)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse QR code: $content", e)
            null
        }
    }
    
    /**
     * Connect to Wi-Fi network using WifiNetworkSpecifier
     * Returns a Flow that emits connection state updates
     */
    fun connectToWifi(payload: WifiPayload): Flow<WifiConnectionState> = callbackFlow {
        Log.i(TAG, "Attempting to connect to ${payload.ssid}")
        
        trySend(WifiConnectionState.Connecting)
        
        try {
            // Disconnect from any previous network
            disconnect()
            
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(payload.ssid)
                .setWpa2Passphrase(payload.password)
                .build()
            
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) // Local network only
                .setNetworkSpecifier(specifier)
                .build()
            
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "Wi-Fi network available: ${payload.ssid}")
                    
                    try {
                        // Bind this process to use the new network for all connections
                        connectivityManager.bindProcessToNetwork(network)
                        boundNetwork = network
                        
                        Log.i(TAG, "Successfully bound to network: ${payload.ssid}")
                        trySend(WifiConnectionState.Connected(network, payload.hostIp))
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to bind to network", e)
                        trySend(WifiConnectionState.Failed("Failed to bind to network: ${e.message}"))
                    }
                }
                
                override fun onUnavailable() {
                    Log.w(TAG, "Wi-Fi network unavailable: ${payload.ssid}")
                    trySend(WifiConnectionState.Failed("Cannot connect to ${payload.ssid}. Make sure the hotspot is running."))
                }
                
                override fun onLost(network: Network) {
                    Log.w(TAG, "Wi-Fi network lost: ${payload.ssid}")
                    trySend(WifiConnectionState.Failed("Connection to ${payload.ssid} was lost"))
                }
                
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    Log.d(TAG, "Network capabilities changed for ${payload.ssid}")
                }
            }
            
            currentCallback = callback
            connectivityManager.requestNetwork(request, callback)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request network", e)
            trySend(WifiConnectionState.Failed("Failed to connect: ${e.message}"))
        }
        
        awaitClose {
            Log.i(TAG, "Cleaning up Wi-Fi connection flow")
            disconnect()
        }
    }
    
    /**
     * Disconnect from the current Wi-Fi network and restore default routing
     */
    fun disconnect() {
        try {
            currentCallback?.let { callback ->
                connectivityManager.unregisterNetworkCallback(callback)
                currentCallback = null
                Log.i(TAG, "Unregistered network callback")
            }
            
            if (boundNetwork != null) {
                connectivityManager.bindProcessToNetwork(null) // Restore default network
                boundNetwork = null
                Log.i(TAG, "Restored default network routing")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
        }
    }
    
    /**
     * Check if currently connected to a specific Wi-Fi network
     */
    fun isConnectedToNetwork(ssid: String): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            activeNetwork != null && 
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
            boundNetwork != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network connection", e)
            false
        }
    }
    
    /**
     * Get the server URL for the connected network
     */
    fun getServerUrl(payload: WifiPayload): String {
        return "http://${payload.hostIp}:${payload.port}"
    }
}