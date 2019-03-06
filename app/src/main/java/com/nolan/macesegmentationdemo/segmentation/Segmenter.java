// Copyright 2019 The MACE Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.nolan.macesegmentationdemo.segmentation;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import com.nolan.macesegmentationdemo.common.AppConfig;
import com.nolan.macesegmentationdemo.common.Configuration;
import com.nolan.macesegmentationdemo.common.ModelConfig;
import com.nolan.macesegmentationdemo.common.ModelInfo;
import com.xiaomi.mace.MaceJni;

import java.nio.FloatBuffer;
import java.util.Arrays;

public class Segmenter {
    private static final String MODEL_GRAPH_FILE_EXTENSION = ".pb";
    private static final String MODEL_WEIGHT_FILE_EXTENSION = ".data";
    public class Segmentation {
        private Bitmap segBitmap;
        private String[] labels;
        private long preProcessTime;
        private long inferenceTime;
        private long postProcessTime;
        private boolean successful;

        public Segmentation(boolean status) {
            this.successful = status;
        }

        public Segmentation(final Bitmap segBitmap, final String[] labels,
                            final long preProcessTime, final long inferenceTime,
                            final long postProcessTime) {
            this.segBitmap = segBitmap;
            this.labels = labels;
            this.preProcessTime = preProcessTime;
            this.inferenceTime = inferenceTime;
            this.postProcessTime = postProcessTime;
            this.successful = true;
        }

        public Bitmap getSegBitmap() {
            return segBitmap;
        }

        public String[] getLabels() {
            return labels;
        }

        public long getPreProcessTime() {
            return preProcessTime;
        }

        public long getInferenceTime() {
            return inferenceTime;
        }

        public long getPostProcessTime() {
            return postProcessTime;
        }

        public boolean isSuccessful() {
            return successful;
        }

    }

    private Configuration config;
    // input float array
    private FloatBuffer inputBuffer;

    public static Segmenter instance = new Segmenter();

    private Segmenter() {}

    public int initialize(AssetManager assetManager, final Configuration config) {
        this.config = config;
        int[] inputShape = config.getModelConfig().getModelInputShape(0);
        int inputSize = 1;
        for (int s : inputShape) {
            inputSize *= s;
        }
        float[] floatValues = new float[inputSize];
        inputBuffer = FloatBuffer.wrap(floatValues, 0, inputSize);
        // create mace engine.
        ModelConfig modelConfig = config.getModelConfig();
        ModelInfo modelInfo = modelConfig.getModelInfo();
        AppConfig appConfig = config.getAppConfig();
        if (modelConfig.getDeviceType().compareTo("GPU") == 0) {
            int status = MaceJni.createGPUContext(appConfig.getStoragePath());
            if (status != 0) {
                Log.e("Segmenter Initialize", "Create GPU Context failed");
                return status;
            }
        }
        int status = MaceJni.createEngine(assetManager, modelInfo.getName(),
                appConfig.getOmpNumThreads(), appConfig.getCpuAffinityPolicy(),
                appConfig.getGpuPerfHint(), appConfig.getGpuPriorityHint(),
                modelInfo.getName() + MODEL_GRAPH_FILE_EXTENSION,
                modelInfo.getName() + MODEL_WEIGHT_FILE_EXTENSION,
                modelInfo.getInputNames(), modelInfo.getOutputNames(),
                modelConfig.getModelInputShape(0), modelConfig.getModelOutputShape(0),
                modelConfig.getDeviceType());
        return status;
    }

    public Configuration getConfig() {
        return config;
    }

    public Segmentation segment(Bitmap bitmap) {
        Log.i("Segmenter", "Segmenting...");
        int[] modelInputShape = config.getModelConfig().getModelInputShape(0);
        int[] modelOutputShape = config.getModelConfig().getModelInputShape(0);
        int modelInputHeight = modelInputShape[1];
        int modelInputWidth = modelInputShape[2];
        int inputHeight = bitmap.getHeight();
        int inputWidth = bitmap.getWidth();
        if (inputHeight > modelInputHeight || inputWidth > modelInputWidth) {
            Log.e("Segmenter", "The input size must be less than model's input size");
            return new Segmentation(false);
        }
        long startTime = SystemClock.uptimeMillis();
        preProcess(bitmap);
        long preProcTime = SystemClock.uptimeMillis() - startTime;
        // inference
        startTime = SystemClock.uptimeMillis();
        float[] segData = MaceJni.inference(config.getModelConfig().getModelName(),
                inputBuffer.array());
        long inferenceTime = SystemClock.uptimeMillis() - startTime;
        if (segData == null) {
            Log.e("Segmenter", "Segmentation failed.");
            return new Segmentation(false);
        }
        // post process
        startTime = SystemClock.uptimeMillis();
        int[] maskData = argMax(segData);
        MaskProcessor.Result segResult = config.getMaskProcessor().process(
                inputHeight, inputWidth, modelOutputShape[2], maskData);
        long postProcessTime = SystemClock.uptimeMillis() - startTime;
        String[] labels = segResult.labels.toArray(new String[segResult.labels.size()]);
        return new Segmentation(segResult.segBitmap, labels,
                preProcTime, inferenceTime, postProcessTime);
    }

    private void preProcess(Bitmap bitmap) {
        int[] modelInputShape = config.getModelConfig().getModelInputShape(0);
        int modelInputHeight = modelInputShape[1];
        int modelInputWidth = modelInputShape[2];
        int inHeight = bitmap.getHeight();
        int inWidth = bitmap.getWidth();
        // pad and normalize
        int[] colorValues = new int[inHeight * inWidth];
        bitmap.getPixels(colorValues, 0, bitmap.getWidth(), 0, 0,
                bitmap.getWidth(), bitmap.getHeight());
        inputBuffer.rewind();
        float[] zeroBuffer = new float[modelInputWidth * modelInputShape[3]];
        for (int hIdx = 0; hIdx < modelInputHeight; ++hIdx) {
            inputBuffer.put(zeroBuffer);
        }
        final float scale = 2.f / 255.f;
        for (int hIdx = 0; hIdx < inHeight; ++hIdx) {
            int colorIdx = hIdx * inWidth;
            int outIdx = hIdx * modelInputWidth * modelInputShape[3];
            for (int wIdx = 0; wIdx < inWidth; ++wIdx) {
                float normalizedValue = scale * ((colorValues[colorIdx] >> 16) & 0xFF) - 1.f;
                inputBuffer.put(outIdx, normalizedValue);
                normalizedValue = scale * ((colorValues[colorIdx] >> 8) & 0xFF) - 1.f;
                inputBuffer.put(outIdx + 1, normalizedValue);
                normalizedValue = scale * (colorValues[colorIdx] & 0xFF) - 1.f;
                inputBuffer.put(outIdx + 2, normalizedValue);
                outIdx += 3;
                colorIdx += 1;
            }
        }
    }

    private int[] argMax(float[] rawSegData) {
        int[] modelOutputShape = config.getModelConfig().getModelOutputShape(0);
        if (rawSegData.length != (modelOutputShape[1] * modelOutputShape[2] * modelOutputShape[3])) {
            Log.e("Segmenter",
                    "Output size of MACE is not match with model's output. "
                            + Arrays.toString(modelOutputShape) + " vs " +
                    Integer.toString(rawSegData.length));
            return null;
        }
        int imageSize = modelOutputShape[1] * modelOutputShape[2];
        int[] segMask = new int[imageSize];
        int rawSegDataIdx = 0;
        int maskIdx = 0;
        for (int pIdx = 0; pIdx < imageSize; ++pIdx) {
            int maxIdx = 0;
            float maxValue = Float.MIN_VALUE;
            for (int cIdx = 0; cIdx < modelOutputShape[3]; ++cIdx) {
                if (rawSegData[rawSegDataIdx] > maxValue) {
                    maxIdx = cIdx;
                    maxValue = rawSegData[rawSegDataIdx];
                }
                rawSegDataIdx += 1;
            }
            segMask[maskIdx] = maxIdx;
            maskIdx += 1;
        }
        return segMask;
    }
}
