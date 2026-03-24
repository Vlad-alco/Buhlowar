package ru.alcodist.buhlowar

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private var pollingJob: Job? = null

    // Views
    private lateinit var tvUrl: TextView
    private lateinit var tvConnection: TextView
    private lateinit var layoutSafety: LinearLayout
    private lateinit var tvSafety: TextView
    private lateinit var tvSafetyDetail: TextView
    private lateinit var tvStage: TextView
    private lateinit var tvTsa: TextView
    private lateinit var tvTsar: TextView
    private lateinit var tvAqua: TextView
    private lateinit var tvTank: TextView
    private lateinit var tvStrBak: TextView
    private lateinit var tvStrOut: TextView
    private lateinit var tvPressure: TextView
    private lateinit var tvBoxTemp: TextView
    private lateinit var tvStageTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvHeads: TextView
    private lateinit var tvBody: TextView
    private lateinit var tvTargetVol: TextView
    private lateinit var tvHeater: TextView
    private lateinit var tvMixer: TextView
    private lateinit var tvWater: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var swNotifications: SwitchMaterial

    private val POLL_INTERVAL = 3000L
    private var lastCommandTime = 0L

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) enableNotifications()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadSettings()
        checkPermissions()
        startPolling()
    }

    private fun initViews() {
        tvUrl = findViewById(R.id.tvUrl)
        tvConnection = findViewById(R.id.tvConnection)
        layoutSafety = findViewById(R.id.layoutSafety)
        tvSafety = findViewById(R.id.tvSafety)
        tvSafetyDetail = findViewById(R.id.tvSafetyDetail)
        tvStage = findViewById(R.id.tvStage)
        tvTsa = findViewById(R.id.tvTsa)
        tvTsar = findViewById(R.id.tvTsar)
        tvAqua = findViewById(R.id.tvAqua)
        tvTank = findViewById(R.id.tvTank)
        tvStrBak = findViewById(R.id.tvStrBak)
        tvStrOut = findViewById(R.id.tvStrOut)
        tvPressure = findViewById(R.id.tvPressure)
        tvBoxTemp = findViewById(R.id.tvBoxTemp)
        tvStageTime = findViewById(R.id.tvStageTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        tvHeads = findViewById(R.id.tvHeads)
        tvBody = findViewById(R.id.tvBody)
        tvTargetVol = findViewById(R.id.tvTargetVol)
        tvHeater = findViewById(R.id.tvHeater)
        tvMixer = findViewById(R.id.tvMixer)
        tvWater = findViewById(R.id.tvWater)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        swNotifications = findViewById(R.id.swNotifications)

        findViewById<View>(R.id.btnSettings).setOnClickListener { showSettingsDialog() }
        findViewById<View>(R.id.btnRefresh).setOnClickListener { refreshData() }

        // Control buttons
        findViewById<Button>(R.id.btnStartDist).setOnClickListener { sendCommand("START_DIST") }
        findViewById<Button>(R.id.btnStartRect).setOnClickListener { sendCommand("START_RECT") }
        findViewById<Button>(R.id.btnStop).setOnClickListener { sendCommand("STOP") }
        findViewById<Button>(R.id.btnNext).setOnClickListener { sendCommand("NEXT_STAGE") }

        swNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("notifications_enabled", isChecked) }
        }
    }

    private fun loadSettings() {
        val apiUrl = prefs.getString("api_url", "") ?: ""
        tvUrl.text = if (apiUrl.isEmpty()) "Нажмите ⚙ для настройки" else apiUrl
        swNotifications.isChecked = prefs.getBoolean("notifications_enabled", true)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        enableNotifications()
    }

    private fun enableNotifications() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("FCM", "Token: ${task.result}")
            }
        }
    }

    private fun showSettingsDialog() {
        val currentUrl = prefs.getString("api_url", "") ?: ""

        val input = EditText(this).apply {
            hint = "https://control.alcodist.ru/api/api.php"
            setText(currentUrl)
        }

        AlertDialog.Builder(this)
            .setTitle("Настройки подключения")
            .setMessage("URL сервера:")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val url = input.text.toString().trim()
                prefs.edit { putString("api_url", url) }
                loadSettings()
                refreshData()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun startPolling() {
        pollingJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                refreshData()
                delay(POLL_INTERVAL)
            }
        }
    }

    private fun refreshData() {
        val apiUrl = prefs.getString("api_url", "") ?: ""
        if (apiUrl.isEmpty()) {
            tvConnection.text = "⚙️"
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "$apiUrl?status=1"
                val response = URL(url).readText()
                val json = JSONObject(response)

                withContext(Dispatchers.Main) {
                    updateUI(json)
                }
            } catch (e: Exception) {
                Log.e("API", "Error", e)
                withContext(Dispatchers.Main) {
                    tvConnection.text = "❌"
                    tvSafety.text = "Нет связи"
                    tvSafety.setTextColor(0xFFFF5555.toInt())
                }
            }
        }
    }

    private fun updateUI(json: JSONObject) {
        val connected = json.optBoolean("connected", false)
        tvConnection.text = if (connected) "🟢" else "🔴"
        tvLastUpdate.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val data = json.optJSONObject("data") ?: return

        // Safety - поддержка обоих вариантов ключа
        val safetyCode = if (data.has("safety_code")) data.optInt("safety_code", 0)
                         else data.optInt("safety", 0)
        when (safetyCode) {
            0 -> {
                tvSafety.text = "✅ НОРМА"
                tvSafety.setTextColor(0xFF55FF55.toInt())
                layoutSafety.setBackgroundColor(0xFF0f3460.toInt())
            }
            1 -> {
                tvSafety.text = "⚠️ ВНИМАНИЕ"
                tvSafety.setTextColor(0xFFFFFF55.toInt())
                layoutSafety.setBackgroundColor(0xFF4a4000.toInt())
            }
            else -> {
                tvSafety.text = "🚨 АВАРИЯ!"
                tvSafety.setTextColor(0xFFFF5555.toInt())
                layoutSafety.setBackgroundColor(0xFF4a0000.toInt())
            }
        }

        // Stage
        tvStage.text = data.optString("stage", "-")

        // Temperatures
        tvTsa.text = "${data.optDouble("tsa", 0.0).format1()}°C"
        tvTsar.text = "${data.optDouble("tsar", 0.0).format1()}°C"
        tvAqua.text = "${data.optDouble("aqua", 0.0).format1()}°C"
        tvTank.text = "${data.optDouble("tank", 0.0).format1()}°C"

        // Strength
        tvStrBak.text = if (data.optBoolean("str_bak_valid")) 
            "${data.optDouble("str_bak", 0.0).format1()}%"
        else "--%"
        tvStrOut.text = if (data.optBoolean("str_out_valid")) 
            "${data.optDouble("str_out", 0.0).format1()}%"
        else "--%"

        // Pressure & Box
        tvPressure.text = "${data.optDouble("pressure", 0.0).format1()} мм"
        tvBoxTemp.text = "${data.optDouble("box_temp", 0.0).format1()}°C"

        // Time
        tvStageTime.text = formatTime(data.optInt("stageTimeSec", 0))
        tvTotalTime.text = formatTime(data.optInt("time", 0))

        // Volumes
        tvHeads.text = "${data.optInt("headsVolDone", 0)}/${data.optInt("headsVolTarget", 0)} мл"
        tvBody.text = "${data.optDouble("bodyVolDone", 0.0).format1()} мл"
        
        // Target volume
        val targetVol = data.optInt("rectVolumeTarget", 0)
        tvTargetVol.text = if (targetVol > 0) "→ ${targetVol} мл" else ""

        // Outputs
        val heaterOn = data.optBoolean("heaterOn", false)
        val mixerOn = data.optBoolean("mixerOn", false)
        val waterOpen = data.optBoolean("waterValveOpen", false)

        tvHeater.text = if (heaterOn) "🔌 НАГРЕВ: ВКЛ" else "🔌 НАГРЕВ: ВЫКЛ"
        tvHeater.setTextColor(if (heaterOn) 0xFFFFAA00.toInt() else 0xFF888888.toInt())

        tvMixer.text = if (mixerOn) "🔄 МИКСЕР: ВКЛ" else "🔄 МИКСЕР: ВЫКЛ"
        tvMixer.setTextColor(if (mixerOn) 0xFFFFAA00.toInt() else 0xFF888888.toInt())

        tvWater.text = if (waterOpen) "💧 КЛАПАН: ВКЛ" else "💧 КЛАПАН: ВЫКЛ"
        tvWater.setTextColor(if (waterOpen) 0xFF55aaff.toInt() else 0xFF888888.toInt())
    }

    private fun formatTime(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    private fun Double.format1(): String = "%.1f".format(this)

    private fun sendCommand(cmd: String) {
        val now = System.currentTimeMillis()
        if (now - lastCommandTime < 1000) {
            Toast.makeText(this, "Подождите...", Toast.LENGTH_SHORT).show()
            return
        }
        lastCommandTime = now

        val apiUrl = prefs.getString("api_url", "") ?: ""
        if (apiUrl.isEmpty()) {
            Toast.makeText(this, "Настройте URL сервера", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "$apiUrl?cmd=$cmd"
                val connection = URL(url).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val response = connection.responseCode
                withContext(Dispatchers.Main) {
                    if (response == 200) {
                        Toast.makeText(this@MainActivity, "✓ $cmd отправлено", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Ошибка: $response", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("API", "Command error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка отправки", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
    }
}
