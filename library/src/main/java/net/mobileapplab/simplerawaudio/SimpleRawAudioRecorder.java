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

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.media.MediaRecorder.AudioSource.DEFAULT;

public class SimpleRawAudioRecorder {
    private static final int DEFAULT_SAMPLE_RATE_IN_HZ = 44100;

    private final int mBufferSize;

    private final String mRecordDataPath;

    private final AudioRecord mAudioRecord;

    private Thread mThread;

    private BufferingListener mBufferingListener;

    /**
     * Interface definition for a callback to be invoked when on new buffer received.
     */
    public interface BufferingListener {
        void onBufferingUpdate(@NonNull short[] shortArray);
    }

    private SimpleRawAudioRecorder(String path, int sampleRateInHz, int channel, int encode, int audioSource, @Nullable BufferingListener listener) {
        mBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channel, encode);
        if (mBufferSize == AudioRecord.ERROR_BAD_VALUE || mBufferSize == AudioRecord.ERROR) {
            throw new IllegalStateException("Failed to get buffer size.");
        }

        mAudioRecord = new AudioRecord(audioSource, sampleRateInHz, channel, encode, mBufferSize);
        if (!isInitialized()) {
            throw new IllegalStateException("Failed to initialize SimpleRawAudioRecorder.");
        }
        mRecordDataPath = path;
        mBufferingListener = listener;
    }

    /**
     * Builder class for SimpleRawAudioRecorder object.
     */
    public static final class Builder {

        private int mSampleRate = DEFAULT_SAMPLE_RATE_IN_HZ;

        private int mChannel = AudioFormat.CHANNEL_IN_MONO;

        private int mAudioSource = DEFAULT;

        private int mAudioEncoding = ENCODING_PCM_16BIT;

        private BufferingListener mListener;

        private String mPath;

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
         *                {@link AudioFormat#CHANNEL_IN_MONO} and {@link AudioFormat#CHANNEL_IN_STEREO}
         * @return this Builder object.
         */
        public Builder channel(int channel) {
            mChannel = channel;
            return this;
        }

        /**
         * Set the recording source.
         *
         * @param audioSource the recording source.
         *                    See {@link android.media.MediaRecorder.AudioSource} for the recording source definitions.
         * @return this Builder object.
         */
        public Builder audioSource(int audioSource) {
            mAudioSource = audioSource;
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
         * Set the listener.
         *
         * @param listener to receive callbacks.
         * @return this Builder object.
         */
        public Builder listener(@Nullable BufferingListener listener) {
            mListener = listener;
            return this;
        }

        /**
         * Set the path to save raw audio.
         *
         * @param path to save raw audio. If empty, raw audio file cannot be stored.
         * @return this Builder object.
         */
        public Builder path(@Nullable String path) {
            mPath = path;
            return this;
        }

        /**
         * Create SimpleRawAudioRecorder instance.
         *
         * @return Returns SimpleRawAudioRecorder instance.
         * @throws IllegalStateException Thrown when failed to initialize SimpleRawAudioRecorder.
         */
        public SimpleRawAudioRecorder build() {
            return new SimpleRawAudioRecorder(mPath, mSampleRate, mChannel, mAudioEncoding, mAudioSource, mListener);
        }
    }

    public static double calculateDecibel(short[] dataArray) {
        int sum = 0;
        int size = dataArray.length;
        for (short data : dataArray) {
            if (data >= 0)
                sum += Math.abs(data);
            else
                size--;
        }
        int ave = sum / size;
        double press = ave / 51805.5336;
        return 20 * Math.log10(press / 0.00002);
    }

    synchronized public final void startRecording() {
        if (!isInitialized() || isRecording()) {
            return;
        }

        final Runnable task = new Runnable() {
            @Override
            public void run() {
                final short[] buffer = new short[mBufferSize];
                mAudioRecord.startRecording();

                try (DataOutputStream dos = TextUtils.isEmpty(mRecordDataPath) ? null
                        : new DataOutputStream(new BufferedOutputStream(new FileOutputStream(mRecordDataPath)))) {
                    while (isRecording()) {
                        mAudioRecord.read(buffer, 0, buffer.length);
                        if (dos != null) {
                            for (short shortData : buffer) {
                                dos.writeByte(shortData & 0xff);
                                dos.writeByte((shortData >> 8) & 0xff);
                            }
                        }

                        if (mBufferingListener != null) {
                            mBufferingListener.onBufferingUpdate(buffer);
                        }
                    }
                } catch (IOException e) {
                    stopRecording();
                }
            }
        };

        mThread = new Thread(task);
        mThread.start();
    }

    synchronized public final void stopRecording() {
        if (!isInitialized() || !isRecording()) {
            return;
        }

        mAudioRecord.stop();
        mThread = null;
    }

    /**
     * Release the instance.
     * Note: The object can no longer be used after a call to release()
     */
    synchronized public final void release() {
        stopRecording();

        if (mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            mAudioRecord.release();
        }
    }

    public final boolean isRecording() {
        return mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING;
    }

    public boolean isInitialized() {
        return mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED;
    }
}
