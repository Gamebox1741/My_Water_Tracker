package com.example.mywatertracker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.example.mywatertracker.ui.theme.MyWaterTrackerTheme
import kotlinx.coroutines.delay

class WaterTrackerViewModel : ViewModel() {
    private val _waterLevel = mutableStateOf(0.0)
    private val _serviceRunning = mutableStateOf(false)
    private val _hasNotificationPermission = mutableStateOf(false)
    private val _permissionRequested = mutableStateOf(false)

    val waterLevel: Double get() = _waterLevel.value
    val serviceRunning: Boolean get() = _serviceRunning.value
    val hasNotificationPermission: Boolean get() = _hasNotificationPermission.value
    val permissionRequested: Boolean get() = _permissionRequested.value

    fun updateWaterLevel(level: Double) {
        _waterLevel.value = level
    }

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun setNotificationPermission(granted: Boolean) {
        _hasNotificationPermission.value = granted
    }

    fun setPermissionRequested(requested: Boolean) {
        _permissionRequested.value = requested
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: WaterTrackerViewModel

    // Notification permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setNotificationPermission(isGranted)
        viewModel.setPermissionRequested(true)

        if (isGranted) {
            // Start service ONLY after permission is granted
            startWaterTrackingService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = WaterTrackerViewModel()

        setContent {
            MyWaterTrackerTheme {
                WaterTrackerApp(
                    viewModel = viewModel,
                    onAddWater = { addGlassOfWater() },
                    onStartService = { startWaterTrackingService() },
                    onRequestPermission = { requestNotificationPermission() }
                )
            }
        }

        // Check notification permission status
        checkNotificationPermissionStatus()
    }

    private fun checkNotificationPermissionStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires notification permission
            // Don't start service yet - wait for permission
            viewModel.setNotificationPermission(false)
        } else {
            // Older versions don't need explicit permission
            // Start service immediately
            viewModel.setNotificationPermission(true)
            startWaterTrackingService()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            viewModel.setPermissionRequested(true)
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Older versions - permission is granted by default
            viewModel.setNotificationPermission(true)
            startWaterTrackingService()
        }
    }

    private fun startWaterTrackingService() {
        try {
            val serviceIntent = Intent(this, WaterTrackingService::class.java)
            startService(serviceIntent)
            viewModel.setServiceRunning(true)
        } catch (e: Exception) {
            e.printStackTrace()
            viewModel.setServiceRunning(false)
        }
    }

    private fun addGlassOfWater() {
        try {
            // Only allow adding water if service is running
            if (viewModel.serviceRunning) {
                val intent = Intent(this, WaterTrackingService::class.java).apply {
                    action = WaterTrackingService.ACTION_ADD_WATER
                    putExtra(WaterTrackingService.EXTRA_WATER_AMOUNT, WaterTrackingService.GLASS_OF_WATER_ML.toDouble())
                }
                startService(intent)

                // Update UI optimistically
                viewModel.updateWaterLevel(viewModel.waterLevel + WaterTrackingService.GLASS_OF_WATER_ML)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: update UI locally if service communication fails
            viewModel.updateWaterLevel(viewModel.waterLevel + WaterTrackingService.GLASS_OF_WATER_ML)
        }
    }
}

@Composable
fun WaterTrackerApp(
    viewModel: WaterTrackerViewModel,
    onAddWater: () -> Unit,
    onStartService: () -> Unit,
    onRequestPermission: () -> Unit
) {
    var currentWaterLevel by remember { mutableStateOf(viewModel.waterLevel) }
    val context = LocalContext.current

    // Update local state when ViewModel changes
    LaunchedEffect(viewModel.waterLevel) {
        currentWaterLevel = viewModel.waterLevel
    }

    // Simulate background water decrease (0.144 ml every 5 seconds)
    // This runs regardless of service status to demonstrate the concept
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000) // Every 5 seconds
            if (viewModel.serviceRunning) {
                // If service is running, simulate the actual service behavior
                val newLevel = currentWaterLevel - 0.144
                currentWaterLevel = if (newLevel < 0) 0.0 else newLevel
                viewModel.updateWaterLevel(currentWaterLevel)
            } else if (viewModel.hasNotificationPermission && !viewModel.serviceRunning) {
                // If permission is granted but service isn't running yet
                val newLevel = currentWaterLevel - 0.144
                currentWaterLevel = if (newLevel < 0) 0.0 else newLevel
                viewModel.updateWaterLevel(currentWaterLevel)
            }
        }
    }

    WaterTrackerScreen(
        waterLevel = currentWaterLevel,
        serviceRunning = viewModel.serviceRunning,
        hasNotificationPermission = viewModel.hasNotificationPermission,
        permissionRequested = viewModel.permissionRequested,
        onAddWater = onAddWater,
        onStartService = onStartService,
        onRequestPermission = onRequestPermission
    )
}

@Composable
fun WaterTrackerScreen(
    waterLevel: Double,
    serviceRunning: Boolean,
    hasNotificationPermission: Boolean,
    permissionRequested: Boolean,
    onAddWater: () -> Unit,
    onStartService: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "My Water Tracker",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Current Water Level",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Text(
                text = "${String.format("%.1f", waterLevel)} ml",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Permission and service status flow
            when {
                // Case 1: Android 13+ and permission not yet requested
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !hasNotificationPermission &&
                        !permissionRequested -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Notification Permission Required",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            text = "This app needs notification permission to track your water levels in the background",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        Button(onClick = onRequestPermission) {
                            Text("Grant Notification Permission")
                        }
                    }
                }

                // Case 2: Permission was requested but denied
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !hasNotificationPermission &&
                        permissionRequested -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Permission Denied",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Text(
                            text = "The app will work with limited functionality",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(onClick = onRequestPermission) {
                            Text("Request Permission Again")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onStartService,
                            enabled = !serviceRunning
                        ) {
                            Text("Start Without Notifications")
                        }
                    }
                }

                // Case 3: Permission granted or older Android version
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Main action button
                        Button(
                            onClick = onAddWater,
                            modifier = Modifier.padding(16.dp),
                            enabled = serviceRunning
                        ) {
                            Text(
                                text = "Drank a Glass of Water",
                                fontSize = 18.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        Text(
                            text = "+250 ml",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Service control for edge cases
                        if (!serviceRunning && hasNotificationPermission) {
                            Button(onClick = onStartService) {
                                Text("Start Water Tracking Service")
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Status information
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission && !permissionRequested ->
                            "Status: Waiting for notification permission"
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission && permissionRequested ->
                            "Status: Permission denied - limited functionality"
                        !serviceRunning && hasNotificationPermission ->
                            "Status: Ready to start service"
                        serviceRunning ->
                            "Status: Active - Water tracking enabled"
                        else ->
                            "Status: Setting up..."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(8.dp))

            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WaterTrackerPreview() {
    MyWaterTrackerTheme {
        WaterTrackerScreen(
            waterLevel = 1250.5,
            serviceRunning = true,
            hasNotificationPermission = true,
            permissionRequested = true,
            onAddWater = {},
            onStartService = {},
            onRequestPermission = {}
        )
    }
}