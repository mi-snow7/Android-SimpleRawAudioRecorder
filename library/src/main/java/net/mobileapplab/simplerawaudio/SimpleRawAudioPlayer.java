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
package net.mobileapplab.simplerawaudio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static android.media.AudioFormat.CHANNEL_OUT_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.media.AudioTrack.MODE_STREAM;

public class SimpleRawAudioPlayer {

    private static final int DEFAULT_SAMPLE_RATE_IN_HZ = 44100;

    private final String mRecordDataPath;

    private final AudioTrack mAudioPlayer;

    private Thread mThread;

    private StateListener mListener;

    public interface StateListener {
        void onPlay();

        void onStop();
    }

    private SimpleRawAudioPlayer(@NonNull AudioAttributes audioAttributes, @NonNull AudioFormat audioFormat, @NonNull String path, @Nullable StateListener listener) {
        int bufferSize = AudioTrack.getMinBufferSize(audioFormat.getSampleRate(), audioFormat.getChannelMask(), audioFormat.getEncoding());
        if (bufferSize == AudioTrack.ERROR_BAD_VALUE || bufferSize == AudioTrack.ERROR) {
            throw new IllegalStateException("Failed to get buffer size.");
        }

        mAudioPlayer = new AudioTrack(audioAttributes, audioFormat, bufferSize, MODE_STREAM, 0);
        if (!isInitialized()) {
            throw new IllegalStateException("Failed to initialize SimpleRawAudioPlayer");
        }
        mRecordDataPath = path;
        mListener = listener;
    }

    public static final class Builder {
        private AudioAttributes mAudioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        private int mAudioEncoding = ENCODING_PCM_16BIT;

        private int mSampleRate = DEFAULT_SAMPLE_RATE_IN_HZ;

        private int mChannel = CHANNEL_OUT_MONO;

        private final String mPath;

        private StateListener mListener;

        /**
         * Create Builder object.
         *
         * @param pathToRawAudio file path to raw audio file must not be empty.
         * @throws IllegalArgumentException thrown when given path is empty.
         */
        public Builder(@NonNull String pathToRawAudio) {
            if (TextUtils.isEmpty(pathToRawAudio)) {
                throw new IllegalArgumentException();
            }

            mPath = pathToRawAudio;
        }

        /**
         * Set the AudioAttributes instance.
         *
         * @param audioAttributes a non-null {@link AudioAttributes} instance.
         * @return this Builder object.
         */
        public Builder audioAttribute(AudioAttributes audioAttributes) {
            mAudioAttributes = audioAttributes;
            return this;
        }

        /**
         * Sets the data encoding format.
         *
         * @param encoding the encoding format must be defined in {@link AudioFormat}.
         * @return this Builder object.
         */
        public Builder encoding(int encoding) {
            mAudioEncoding = encoding;
            return this;
        }

        /**
         * Set the sample rate expressed in Hertz.
         *
         * @param sampleRate the sample rate expressed in Hertz.
         * @return this Builder object.
         */
        public Builder sampleRate(final int sampleRate) {
            mSampleRate = sampleRate;
            return this;
        }

        /**
         * Set the configuration of the audio channels.
         *
         * @param channel configuration of the audio channels like a
         *                {@link AudioFormat#CHANNEL_OUT_MONO} and {@link AudioFormat#CHANNEL_OUT_STEREO}
         * @return this Builder object.
         */
        public Builder channel(int channel) {
            mChannel = channel;
            return this;
        }

        /**
         * Set the listener.
         *
         * @param listener to receive callbacks.
         * @return this Builder object.
         */
        public Builder listener(@Nullable StateListener listener) {
            mListener = listener;
            return this;
        }

        /**
         * Create SimpleRawAudioPlayer instance.
         *
         * @return Returns SimpleRawAudioPlayer instance.
         * @throws IllegalStateException Thrown when failed to initialize SimpleRawAudioPlayer.
         */
        public SimpleRawAudioPlayer build() {
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(mAudioEncoding)
                    .setSampleRate(mSampleRate)
                    .setChannelMask(mChannel)
                    .build();
            return new SimpleRawAudioPlayer(mAudioAttributes, format, mPath, mListener);
        }
    }

    synchronized public void play() {
        if (!isInitialized() || isPlaying()) {
            return;
        }

        mAudioPlayer.play();
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                File file = new File(mRecordDataPath);
                if (!file.exists()) {
                    stop();
                    return;
                }

                byte[] bytes = new byte[(int) file.length()];

                try (FileInputStream fis = new FileInputStream(file)) {
                    if (fis.read(bytes) >= 0) {
                        mAudioPlayer.write(bytes, 0, bytes.length);
                    }
                } catch (IOException e) {
                    // NOP.
                } finally {
                    stop();
                }
            }
        };

        mThread = new Thread(task);

        if (mListener != null) {
            mListener.onPlay();
        }
        mThread.start();
    }

    synchronized public void stop() {
        if (!isInitialized() || isStopped()) {
            return;
        }

        mAudioPlayer.stop();
        if (mListener != null) {
            mListener.onStop();
        }
    }

    /**
     * Release the instance.
     * Note: The object can no longer be used after a call to release()
     */
    synchronized public void release() {
        if (isPlaying()) {
            stop();
        }

        if (isInitialized()) {
            mAudioPlayer.release();
        }
        mThread = null;
    }

    public boolean isPlaying() {
        return mAudioPlayer.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
    }

    public boolean isStopped() {
        return mAudioPlayer.getPlayState() == AudioTrack.PLAYSTATE_STOPPED;
    }

    public boolean isInitialized() {
        return mAudioPlayer.getState() == AudioTrack.STATE_INITIALIZED;
    }
}
