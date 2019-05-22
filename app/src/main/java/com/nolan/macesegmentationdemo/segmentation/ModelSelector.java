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

import android.util.Log;

import com.nolan.macesegmentationdemo.common.ModelConfig;
import com.nolan.macesegmentationdemo.common.ModelInfo;
import com.nolan.macesegmentationdemo.common.Shape;
import com.xiaomi.mace.MaceJni;

public class ModelSelector {
    // Expected run time for your model
    private static int EXPECTED_EXEC_TIME = 1000; // (ms)
    // CPU run time of your test device (Get from MACE run)
    private static int BASE_CPU_EXEC_TIME = 31; // (ms)

    private static String CPU_DEVICE = "CPU";
    private static String GPU_DEVICE = "GPU";
    private static String DSP_DEVICE = "DSP";

    // The array is ordered by model priority
    public static ModelInfo[] modelInfos = new ModelInfo[] {
            new ModelInfo("deeplab_v3_plus_mobilenet_v2_quant",
                    new String[]{"Input"},
                    new Shape[]{new Shape(new int[]{1, 513, 513, 3})},
                    new String[]{"ResizeBilinear_1"},
                    new Shape[]{new Shape(new int[]{1, 513, 513, 2})},
                    465, true, false),
            new ModelInfo("deeplab_v3_plus_mobilenet_v2",
                    new String[]{"Input"},
                    new Shape[]{new Shape(new int[]{1, 513, 513, 3})},
                    new String[]{"ResizeBilinear_1"},
                    new Shape[]{new Shape(new int[]{1, 513, 513, 2})},
                    465, false, false),
    };

    // Select the device and model for the phone.
    // Estimate the run time of model based on the run time of test phone.
    // Priority: Quality(High->Low), Device(DSP -> GPU -> CPU(quantized8))
    public ModelConfig select() {
        int modelSize = modelInfos.length;
        float[] gpuPerf = null;
        float[] cpuPerf = null;
        ModelInfo selectedModelInfo = null;
        String deviceType = null;
        for (int i = 0; i < modelSize; ++i) {
            if (modelInfos[i].isQuantized8DSP()) {
                // TODO(liuqi): support dsp model
            } else if (modelInfos[i].isQuantized8CPU()) {
                // cpu quantization model
                if (cpuPerf == null) {
                    cpuPerf = MaceJni.getDeviceCapability(CPU_DEVICE, 1.f);
                }
                float estimatedTime = estimatedExecTime(modelInfos[i].getBasedCPUExecTime(),
                        cpuPerf[1]);
                if (estimatedTime < EXPECTED_EXEC_TIME) {
                    selectedModelInfo = modelInfos[i];
                    deviceType = CPU_DEVICE;
                    break;
                }
            } else {
                // model(float) support cpu and gpu
                if (gpuPerf == null) {
                    gpuPerf = MaceJni.getDeviceCapability(GPU_DEVICE, BASE_CPU_EXEC_TIME);
                    Log.i("Segmentation", "GPU performance " + Float.toString(gpuPerf[0]));
                }
                if (gpuPerf[0] > 0.f) {
                    float estimatedTime = modelInfos[i].getBasedCPUExecTime() * gpuPerf[0];
                    Log.i("Segmentation", "GPU estimated time " + Float.toString(estimatedTime));
                    if (estimatedTime < EXPECTED_EXEC_TIME) {
                        selectedModelInfo = modelInfos[i];
                        deviceType = GPU_DEVICE;
                        break;
                    }
                }
                if (cpuPerf == null) {
                    cpuPerf = MaceJni.getDeviceCapability(CPU_DEVICE, 1.f);
                }
                float estimatedTime = estimatedExecTime(modelInfos[i].getBasedCPUExecTime(),
                        cpuPerf[0]);
                Log.i("Segmentation", "CPU estimated time " + Float.toString(estimatedTime));
                if (estimatedTime < EXPECTED_EXEC_TIME) {
                    selectedModelInfo = modelInfos[i];
                    deviceType = CPU_DEVICE;
                    break;
                }
            }
        }
        if (selectedModelInfo != null) {
            Log.i("Segmentation", "Use model " + selectedModelInfo.getName()
                    + " with device " + deviceType);
            return new ModelConfig(selectedModelInfo, deviceType);
        }
        return null;
    }

    private float estimatedExecTime(final float modelBaseRunTime, final float targetTestRunTime) {
        return modelBaseRunTime * (targetTestRunTime / (float)BASE_CPU_EXEC_TIME);
    }
}
