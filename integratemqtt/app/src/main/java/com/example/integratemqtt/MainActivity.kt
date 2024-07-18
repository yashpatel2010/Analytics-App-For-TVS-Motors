package com.example.integratemqtt


import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.integratemqtt.ui.theme.IntegratemqttTheme
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream




//firebase ni files add krvi ama



import MqttClient
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
//import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.integratemqtt.ui.theme.IntegratemqttTheme
//import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.delay
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
//import org.json.JSONArray
//import org.json.JSONObject
import java.io.*
//import java.text.SimpleDateFormat
import java.util.*
//import java.util.zip.ZipEntry
//import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    private var isLogging = mutableStateOf(false)
    private var logJob: Job? = null
    private val storage = Firebase.storage
    private val database = Firebase.database
    private lateinit var logFile: File
    private lateinit var logFileName: String
    private lateinit var zipFile: File
    private lateinit var zipFileName: String
    private val logList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IntegratemqttTheme {
                MqttMessageDisplay()
            }
        }
    }

    @Composable
    fun MqttMessageDisplay() {
        var message by remember { mutableStateOf("Waiting for message...") }
        val context = LocalContext.current

        // Initialize MQTT Client
        val mqttClient = remember { MqttClient(context, "ws://10.123.177.101:1883", "androidClientId") }



        LaunchedEffect(Unit) {
            try {
                mqttClient.connect("test/topic") { receivedMessage ->
                    message = receivedMessage
                    Log.d("MqttMessageDisplay", "Received message: $receivedMessage")

                    handleIncomingMessage(receivedMessage, isLogging)

                }
            } catch (e: Exception) {
                Log.e("MqttMessageDisplay", "Connection failed", e)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(text = message)
        }
    }

    private fun handleIncomingMessage(payload: String, isLogging: MutableState<Boolean>) {
        // Extract the time and VIN NO from the payload
        val parts = payload.split(", ")
        var time = parts[0].split(": ")[1].toIntOrNull()
        val vinNO = parts[1].split(": ")[1]

        // Log the incoming message
        Log.d("MqttMessageDisplay", "Time: $time, VIN NO: $vinNO")

        // Add logic to generate logs for the specific VIN and time
        if (!isLogging.value && vinNO == "HR5") {

            if (time != null) {
                startLogging(isLogging, vinNO, time)
            }

        } else {
            stopLoggingAndUpload(isLogging)
        }
    }

    private fun startLogging(isLogging: MutableState<Boolean>, vinNO: String, time: Int) {
        logList.clear()
        var delayy=time
        createNewLogFile(vinNO)
        this.isLogging.value = true
        logJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val process = ProcessBuilder().command("logcat").redirectErrorStream(true).start()
                val inputStream = process.inputStream

                val reader = BufferedReader(InputStreamReader(inputStream))
                var line: String? = null

                while (this@MainActivity.isLogging.value && reader.readLine().also { line = it } != null) {
                    logList.add(line!!)

                        writeLogsToJsonFile()


                    delay(1000L)
                        delayy--
                        if(delayy <= 0)
                        {
                            isLogging.value = false // Stop the logging
                        }

                }
                compressJsonToZip()

                inputStream.close()

                stopLoggingAndUpload(isLogging)
            } catch (e: Exception) {
                Log.e(TAG, "Logging failed", e)
            }
        }
    }


    private fun stopLoggingAndUpload(isLogging: MutableState<Boolean>) {
        this.isLogging.value = false
        logJob?.cancel()
        logJob = null
        try {

            uploadZipFile()
        } catch (e: Exception) {
            Log.e(TAG, "Stop logging or upload failed", e)
        }
    }

    private fun createNewLogFile(vinNO: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        logFileName = "${vinNO},logcat.txt"

        logFile = File(getExternalFilesDir(null), logFileName)
        zipFileName = "${vinNO}_$timestamp.zip"

        zipFile = File(getExternalFilesDir(null), zipFileName)
    }

    private fun uploadZipFile() {
        val storageRef = storage.reference.child("logs/$zipFileName")
        val fileUri = zipFile.toUri()

        storageRef.putFile(fileUri)
            .addOnSuccessListener {
                Log.i(TAG, "Zip file uploaded successfully")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Zip file upload failed", exception)
                retryUpload(fileUri)
            }
    }

    private fun retryUpload(fileUri: Uri, retries: Int = 3) {
        if (retries > 0) {
            storage.reference.child("logs/$zipFileName").putFile(fileUri)
                .addOnSuccessListener {
                    Log.i(TAG, "Zip file uploaded successfully after retry")
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Retry upload failed", exception)
                    retryUpload(fileUri, retries - 1)
                }
        }
        else {
            Log.e(TAG, "Exhausted all retries for upload")
        }
    }

    private fun compressJsonToZip() {
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                FileInputStream(logFile).use { fis ->
                    val zipEntry = ZipEntry(logFileName)
                    zipOut.putNextEntry(zipEntry)
                    fis.copyTo(zipOut, 1024)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed", e)
        }
    }

    private fun writeLogsToJsonFile() {
        try {
            val jsonArray = JSONArray()
            logList.forEach { log ->
                val jsonObject = JSONObject().apply { put("log", log) }
                jsonArray.put(jsonObject)
            }

            FileOutputStream(logFile, false).use { outputStream ->
                outputStream.write(jsonArray.toString().toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Writing logs to JSON file failed", e)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}


