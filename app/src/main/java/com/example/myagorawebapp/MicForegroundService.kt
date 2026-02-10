package com.example.myagorawebapp

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import androidx.core.app.NotificationCompat
import java.lang.Exception

class MicForegroundService : Service() {

    private var recordingThread: Thread? = null
    @Volatile private var isRecording = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val CHANNEL_ID = "mic_service_channel"
    private val NOTIF_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        acquireWakeLock()
        startRecording()
        // START_STICKY: if killed, system will try to recreate
        return START_STICKY
    }

    override fun onDestroy() {
        stopRecording()
        releaseWakeLock()
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "麦克风后台服务", NotificationManager.IMPORTANCE_LOW)
            channel.setSound(null, null)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val title = "演播智联"
        val text = "麦克风正在后台运行"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        // 点击通知回到应用
        val intent = Intent(this, MainActivity::class.java)
        val pIntent = PendingIntent.getActivity(this, 0, intent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        builder.setContentIntent(pIntent)
        return builder.build()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            // PARTIAL_WAKE_LOCK 保持 CPU 在息屏时继续运行
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "演播智联:MicWakeLock")
            if (wakeLock?.isHeld == false) {
                // 不设置超时，直到服务被停止再释放
                wakeLock?.acquire()
            }
        } catch (e: Exception) {
            // 记录或忽略
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun startRecording() {
        if (isRecording) return
        isRecording = true

        recordingThread = Thread {
            var recorder: AudioRecord? = null
            try {
                val sampleRate = 44100
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                var bufferSize = minBuf
                if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    bufferSize = sampleRate * 2
                }
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    // 无法初始化时终止
                    return@Thread
                }

                recorder.startRecording()

                val buffer = ByteArray(bufferSize)
                while (isRecording) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read <= 0) {
                        // 读取异常或停止
                        Thread.sleep(10)
                    }
                    // 我们故意不保存数据 — 只维持麦克风打开
                }
            } catch (t: Throwable) {
                // log if needed
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (ignored: Exception) {}
            }
        }
        recordingThread?.start()
    }

    private fun stopRecording() {
        isRecording = false
        try {
            recordingThread?.join(500)
        } catch (e: InterruptedException) {
            // ignore
        }
        recordingThread = null
    }
}