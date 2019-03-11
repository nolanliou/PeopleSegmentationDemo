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

package com.xiaomi.mace;

import android.content.res.AssetManager;

public class MaceJni {

    static {
        System.loadLibrary("mace_jni");
    }

    /**
     * Get device capability
     * @param device device type
     * @param base_cpu_exec_time CPU execution time on test device.
     * @return float array with 2 elements: the former for float32 performance, the latter for quantized8 performance
     */
    public static native float[] getDeviceCapability(String device, float base_cpu_exec_time);

    /**
     * Same with MACE definition.
     * @param storagePath storage path
     * @return status
     */
    public static native int createGPUContext(String storagePath);

    /**
     * create MaceEngine for specific model
     *
     * @param manager asset manager to get model files
     * @param modelName model name(should be unique for one App)
     * @param ompNumThreads number of openmp threads
     * @param cpuAffinityPolicy cpu affinity policy
     * @param gpuPerfHint gpu performance hint
     * @param gpuPriorityHint gpu priority hint(only work for Qualcomm Adreno GPU)
     * @param model_graph_file model graph file name
     * @param model_weight_file model weight file name
     * @param input_names input names
     * @param output_names output names
     * @param input_shapes input shapes separated by 0
     * @param output_shapes output shapes separated by 0
     * @param device device type
     * @return
     */
    public static native int createEngine(AssetManager manager, String modelName,
                                          int ompNumThreads, int cpuAffinityPolicy,
                                          int gpuPerfHint, int gpuPriorityHint,
                                          String model_graph_file, String model_weight_file,
                                          String[] input_names, String[] output_names,
                                          int[] input_shapes, int[] output_shapes,
                                          String device);

    /**
     * inference the model
     * @param modelName model name(same with createEngine)
     * @param input input array
     * @return output of the model.
     */
    public static native float[] inference(String modelName, float[] input);

}
