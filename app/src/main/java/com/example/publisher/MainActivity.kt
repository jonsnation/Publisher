package com.example.publisher

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.*
import com.google.android.material.textfield.TextInputLayout
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.gson.Gson

class MainActivity : AppCompatActivity() {

    private lateinit var clientLocation: FusedLocationProviderClient
    private lateinit var mqttClient: Mqtt5BlockingClient
    private lateinit var studentID: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private var isPublishing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val textInputLayout = findViewById<TextInputLayout>(R.id.textInputLayout)
        studentID = textInputLayout.editText!!
        startButton = findViewById(R.id.button)
        stopButton = findViewById(R.id.button2)


        clientLocation = LocationServices.getFusedLocationProviderClient(this)

        mqttClient = MqttClient.builder()
            .useMqttVersion5()
            .identifier(UUID.randomUUID().toString())
            .serverHost("broker-816035483.sundaebytestt.com")
            .serverPort(1883)
            .buildBlocking()

        // Attempt to connect to MQTT broker.
        try {
            mqttClient.connect()
            Toast.makeText(this, "Connected to MQTT broker", Toast.LENGTH_SHORT).show()
            Log.d("MQTT", "Connected to MQTT broker")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to connect to MQTT broker", Toast.LENGTH_SHORT).show()
            Log.e("MQTT", "Failed to connect to MQTT broker", e)
        }

        // Start publishing location updates on button click.
        startButton.setOnClickListener {
            val studentIdText = studentID.text.toString()
            if (studentIdText.isNotEmpty()) {
                if (!isPublishing) {
                    startLocationUpdates()
                    isPublishing = true
                    Toast.makeText(this, "Started publishing", Toast.LENGTH_SHORT).show()
                    Log.d("PUBLISH", "Started publishing")
                }
            } else {
                Toast.makeText(this, "Please enter your student ID", Toast.LENGTH_SHORT).show()
            }
        }

        // Stop publishing location updates on button click.
        stopButton.setOnClickListener {
            if (isPublishing) {
                stopLocationUpdates()
                isPublishing = false
                Toast.makeText(this, "Stopped publishing", Toast.LENGTH_SHORT).show()
                Log.d("PUBLISH", "Stopped publishing")
            }
        }
    }

    // Callback to receive location updates.
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                publishLocation(location)
            }
        }
    }

    // Start location updates if permission is granted.
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()
        clientLocation.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    // Stop location updates.
    private fun stopLocationUpdates() {
        clientLocation.removeLocationUpdates(locationCallback)
    }

    @SuppressLint("DefaultLocale")
    private fun publishLocation(location: Location) {
        val studentId = studentID.text.toString()
        val timestamp = SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSZ", Locale.getDefault()).format(Date())
        val speedKmph = location.speed * 3.6 // Convert m/s to km/h
        val speedKmphRounded = String.format("%.2f", speedKmph).toDouble() // Convert to Double

        val locationData = LocationData(
            studentId = studentId,
            latitude = location.latitude,
            longitude = location.longitude,
            speed = speedKmphRounded,
            timestamp = timestamp
        )

        val gson = Gson()
        val payload = gson.toJson(locationData)

        try {
            mqttClient.publishWith()
                .topic("assignment/location")
                .payload(payload.toByteArray())
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
            Toast.makeText(this, "Location published", Toast.LENGTH_SHORT).show()
            Log.d("MQTT", "Location published: $payload")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to publish location", Toast.LENGTH_SHORT).show()
            Log.e("MQTT", "Failed to publish location", e)
        }
    }

    // Handle location permission request result.
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
                Log.d("PERMISSION", "Location permission denied")
            }
        }
}