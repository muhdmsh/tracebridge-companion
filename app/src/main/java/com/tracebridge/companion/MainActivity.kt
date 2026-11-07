package com.tracebridge.companion

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var urlInput: EditText
    private lateinit var methodInput: Spinner
    private lateinit var headersInput: EditText
    private lateinit var bodyInput: EditText
    private lateinit var responseView: TextView
    private lateinit var sendButton: Button
    private lateinit var clockView: TextView

    private val clockTask = object : Runnable {
        override fun run() {
            val now = LocalTime.now()
            val start = LocalTime.of(19, 0)
            val end = LocalTime.of(19, 1)

            val active = !now.isBefore(start) && now.isBefore(end)

            clockView.text = if (active) {
                "7:00 PM replay window is ACTIVE"
            } else {
                "Timed replay activates daily at 7:00 PM"
            }

            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val scroll = ScrollView(this)
        scroll.addView(root)

        val title = TextView(this).apply {
            text = "TraceBridge"
            textSize = 28f
        }
        root.addView(title)

        root.addView(label("API Request URL"))

        urlInput = EditText(this).apply {
            hint = "https://example.com/api"
            isSingleLine = true
        }
        root.addView(urlInput, matchWidth())

        root.addView(label("HTTP Method"))

        methodInput = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("GET", "POST", "PUT", "PATCH", "DELETE")
            )
        }
        root.addView(methodInput)

        root.addView(label("Headers (one per line: Name: Value)"))

        headersInput = EditText(this).apply {
            hint = "Authorization: Bearer YOUR_TOKEN"
            minLines = 3
            gravity = android.view.Gravity.TOP
        }
        root.addView(headersInput, matchWidth())

        root.addView(label("Request Body"))

        bodyInput = EditText(this).apply {
            hint = """{"key":"value"}"""
            minLines = 4
            gravity = android.view.Gravity.TOP
        }
        root.addView(bodyInput, matchWidth())

        sendButton = Button(this).apply {
            text = "Send API Request"
            setOnClickListener { sendRequest() }
        }
        root.addView(sendButton)

        clockView = TextView(this).apply {
            textSize = 16f
            setPadding(0, 20, 0, 12)
        }
        root.addView(clockView)

        root.addView(label("API Response"))

        responseView = TextView(this).apply {
            text = "Your response will appear here."
            textSize = 14f
            setPadding(12, 12, 12, 12)
        }
        root.addView(responseView)

        setContentView(scroll)
        handler.post(clockTask)
    }

    private fun label(value: String): TextView {
        return TextView(this).apply {
            text = value
            textSize = 16f
            setPadding(0, 16, 0, 6)
        }
    }

    private fun matchWidth(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun sendRequest() {
        val address = urlInput.text.toString().trim()

        if (!address.startsWith("https://")) {
            responseView.text = "Please enter a valid HTTPS URL."
            return
        }

        val method = methodInput.selectedItem.toString()
        val headers = headersInput.text.toString()
        val body = bodyInput.text.toString()

        sendButton.isEnabled = false
        responseView.text = "Sending request..."

        executor.execute {
            val result = try {
                val connection = URL(address).openConnection()
                    as HttpURLConnection

                connection.requestMethod = method
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = false

                headers.lines().forEach { line ->
                    val separator = line.indexOf(':')
                    if (separator > 0) {
                        val name = line.substring(0, separator).trim()
                        val value = line.substring(separator + 1).trim()
                        if (name.isNotEmpty()) {
                            connection.setRequestProperty(name, value)
                        }
                    }
                }

                if (method in listOf("POST", "PUT", "PATCH")) {
                    connection.doOutput = true
                    connection.setRequestProperty(
                        "Content-Type",
                        "application/json"
                    )
                    connection.outputStream.use {
                        it.write(body.toByteArray(Charsets.UTF_8))
                    }
                }

                val status = connection.responseCode
                val stream = if (status in 200..399) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val response = stream?.bufferedReader()?.use {
                    it.readText()
                } ?: "(Empty response)"

                connection.disconnect()
                "HTTP $status\n\n$response"
            } catch (e: Exception) {
                "Request failed: ${e.message}"
            }

            runOnUiThread {
                responseView.text = result
                sendButton.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockTask)
        executor.shutdownNow()
        super.onDestroy()
    }
}
