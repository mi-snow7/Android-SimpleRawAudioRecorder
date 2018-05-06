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

import android.media.AudioAttributes
import android.media.AudioFormat.*
import android.media.MediaRecorder.AudioSource.MIC
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_main.*
import net.mobileapplab.simplerawaudio.SimpleRawAudioPlayer
import net.mobileapplab.simplerawaudio.SimpleRawAudioRecorder
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var recorder: SimpleRawAudioRecorder
    private lateinit var player: SimpleRawAudioPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val path = "$filesDir${File.separator}tmp.pcm"
        val sampleRate = 44100

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

        findViewById<View>(R.id.start).setOnClickListener {
            recorder.startRecording()
        }

        findViewById<View>(R.id.stop).setOnClickListener {
            recorder.stopRecording()
        }

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

        fab.setOnClickListener {
            if (player.isPlaying) {
                player.stop()
            } else {
                player.play()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        recorder.release()
        player.release()
    }

    private fun updateVolumeText(db: Double) {
        runOnUiThread {
            val textView = findViewById<TextView>(R.id.db)
            textView.text = String.format("%1$2.1f", db)
        }
    }
}
