/*
 * Copyright (C) 2018 Mobile Application Lab
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.mobileapplab.android.app.simplerawaudio

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat.*
import android.media.MediaRecorder.AudioSource.MIC
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import net.mobileapplab.simplerawaudio.SimpleRawAudioPlayer
import net.mobileapplab.simplerawaudio.SimpleRawAudioRecorder
import java.io.File

class MainActivity : AppCompatActivity() {
    companion object {
        private const val AUDIO_REQUEST_CODE = 0
    }

    private var recorder: SimpleRawAudioRecorder? = null
    private var player: SimpleRawAudioPlayer? = null

    private lateinit var path: String
    private val sampleRate = 44100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        path = "$filesDir${File.separator}tmp.pcm"

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO),
                    AUDIO_REQUEST_CODE)
        } else {
            initPlayer()
            initRecorder()
        }

        start.setOnClickListener {
            recorder?.startRecording()
        }

        stop.setOnClickListener {
            recorder?.stopRecording()
        }

        fab.setOnClickListener { _ ->
            player?.let {
                if (it.isPlaying) {
                    it.stop()
                } else {
                    it.play()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            AUDIO_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initPlayer()
                    initRecorder()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        recorder?.release()
        player?.release()
    }

    private fun initRecorder() {
        recorder = SimpleRawAudioRecorder.Builder()
                .sampleRate(sampleRate)
                .audioSource(MIC)
                .channel(CHANNEL_IN_MONO)
                .encoding(ENCODING_PCM_16BIT)
                .path(path)
                .listener {
                    val db = SimpleRawAudioRecorder.calculateDecibel(it)
                    updateVolumeText(db)
                }
                .build()
    }

    private fun initPlayer() {
        val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        player = SimpleRawAudioPlayer.Builder(path)
                .audioAttribute(attributes)
                .sampleRate(sampleRate)
                .channel(CHANNEL_OUT_MONO)
                .encoding(ENCODING_PCM_16BIT)
                .listener(object : SimpleRawAudioPlayer.StateListener {
                    override fun onPlay() {
                        runOnUiThread {
                            fab.setImageDrawable(getDrawable(android.R.drawable.ic_media_pause))
                        }
                    }

                    override fun onStop() {
                        runOnUiThread {
                            fab.setImageDrawable(getDrawable(android.R.drawable.ic_media_play))
                        }
                    }
                })
                .build()
    }

    private fun updateVolumeText(volumeDb: Double) {
        runOnUiThread {
            db.text = String.format("%1$2.1f", volumeDb)
        }
    }
}
