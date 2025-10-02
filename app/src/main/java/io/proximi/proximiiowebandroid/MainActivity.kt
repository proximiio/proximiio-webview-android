package io.proximi.proximiiowebandroid

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.proximi.proximiiolibrary.ProximiioAPI
import io.proximi.proximiiolibrary.ProximiioFloor
import io.proximi.proximiiolibrary.ProximiioListener
import io.proximi.proximiiolibrary.ProximiioOptions
import io.proximi.proximiiowebandroid.ui.theme.ProximiioWebAndroidTheme
import org.json.JSONObject
import kotlin.math.abs


class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var proximiioAPI: ProximiioAPI
    private val PERMISSION_REQUEST_CODE = 1001
    private var webView: WebView? = null

    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null

    private var currentHeading: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = getRequiredPermissions()

        if (hasPermissions(permissions)) {
            initializeProximiioAPI()
        } else {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
        }

        enableEdgeToEdge()
        setContent {
            ProximiioWebAndroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ProximiioWebView(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )

        // API 34+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(android.Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            permissions.addAll(listOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ))
        } else {
            // Android 11 and below
            permissions.addAll(listOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN
            ))
        }

        return permissions.toTypedArray()
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        proximiioAPI.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = mutableListOf<String>()
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permissions[i])
                }
            }

            if (deniedPermissions.isEmpty()) {
                initializeProximiioAPI()
            } else {
                if (deniedPermissions.any { it.contains("BLUETOOTH") }) {
                    Log.w("MainActivity", "Bluetooth permissions denied - beacon detection will not work")
                }
            }
        }

        if (::proximiioAPI.isInitialized) {
            proximiioAPI.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun initializeProximiioAPI() {
        val token = "INSERT_PROXIMIIO_APPLICATION_TOKEN"
        val options = ProximiioOptions()
        var isMoving = false
        var level = 0

        proximiioAPI = ProximiioAPI("ProximiioAPI", applicationContext, options)

        proximiioAPI.setListener(object : ProximiioListener() {
            override fun position(location: Location) {
                injectLocationUpdate(location.latitude, location.longitude, currentHeading.toDouble(), level, isMoving, proximiioAPI.isPdrSupported(applicationContext))
                super.position(location)
            }

            override fun loggedIn(online: Boolean, auth: String?) {
                checkBluetoothStatus()
                checkBeaconPermissions()
                super.loggedIn(online, auth)
            }

            override fun deviceStill() {
                super.deviceStill()
                isMoving = false
            }

            override fun deviceMoving() {
                super.deviceMoving()
                isMoving = true
            }

            override fun changedFloor(floor: ProximiioFloor?) {
                super.changedFloor(floor)
                if (floor != null && floor.floorNumber != null) {
                    level = floor.floorNumber!!
                }
            }
        })

        proximiioAPI.setAuth(token, true)
        proximiioAPI.setActivity(this)
        proximiioAPI.setForceBluetooth(true)
        proximiioAPI.pdrEnabled(true)
        proximiioAPI.pdrCorrectionThreshold(3.0)
        proximiioAPI.onStart()
    }

    private fun checkBeaconPermissions() {
        val requiredPermissions = getRequiredPermissions()
        val deniedPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (deniedPermissions.isNotEmpty()) {
            Log.e("MainActivity", "Missing permissions for beacon detection: ${deniedPermissions.joinToString(", ")}")
        } else {
            Log.d("MainActivity", "All beacon permissions granted!")
        }
    }

    private fun checkBluetoothStatus() {
        try {
            val bluetoothManager = getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter

            if (bluetoothAdapter == null) {
                Log.e("MainActivity", "Bluetooth adapter not available")
            } else {
                Log.d("MainActivity", "Bluetooth status - Enabled: ${bluetoothAdapter.isEnabled}, " +
                        "LE supported: ${packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE)}")

                if (!bluetoothAdapter.isEnabled) {
                    Log.w("MainActivity", "Bluetooth is disabled - beacon detection will not work")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking Bluetooth status", e)
        }
    }

    private fun injectLocationUpdate(latitude: Double, longitude: Double, heading: Double, level: Int, isMoving: Boolean, accelAvailable: Boolean) {
        webView?.let { webView ->
            runOnUiThread {
                try {
                    val update = JSONObject().apply {
                        put("type", "SET_LOCATION")
                        put("latitude", latitude)
                        put("longitude", longitude)
                        put("level", level)
                        put("heading", heading)
                        put("accelAvailable", accelAvailable)
                        put("isInMovement", isMoving)
                    }

                    val javascript = "window.postMessage('${update.toString().replace("'", "\\'")}');"
                    webView.evaluateJavascript(javascript, ValueCallback {})
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error injecting location update", e)
                }
            }
        }
    }

    fun setWebView(webView: WebView) {
        this.webView = webView
    }

    override fun onResume() {
        super.onResume()
        if (::proximiioAPI.isInitialized) {
            proximiioAPI.onStart()
            Log.d("MainActivity", "ProximiioAPI resumed")
        }

        // Register sensor listener for rotation vector sensor
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::proximiioAPI.isInitialized) {
            proximiioAPI.onStop()
        }

        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::proximiioAPI.isInitialized) {
            proximiioAPI.destroy()
            Log.d("MainActivity", "ProximiioAPI destroyed")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            // Convert rotation vector to rotation matrix
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            // Get orientation angles from rotation matrix
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            // Azimuth is the first value in the orientation array (in radians)
            val azimuthRadians = orientation[0]
            var azimuthDegrees = Math.toDegrees(azimuthRadians.toDouble()).toFloat()

            // Normalize to 0-360 degrees
            if (azimuthDegrees < 0) {
                azimuthDegrees += 360f
            }

            // Update current heading (only if it changed significantly to reduce noise)
            val headingDiff = abs(azimuthDegrees - currentHeading)
            if (headingDiff > 2.0f || headingDiff < -2.0f) { // 2-degree threshold
                currentHeading = azimuthDegrees
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ProximiioWebView(modifier: Modifier = Modifier) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    loadUrl("INSERT_MAP_URL")

                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    (context as? MainActivity)?.setWebView(this)
                }
            },
            modifier = modifier.fillMaxSize()
        )
    }
}
