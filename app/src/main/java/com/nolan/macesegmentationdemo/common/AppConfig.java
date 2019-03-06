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
package com.nolan.macesegmentationdemo.common;

import android.os.Environment;

import java.io.File;

public class AppConfig {
    private int ompNumThreads;
    private int cpuAffinityPolicy;
    private int gpuPerfHint;
    private int gpuPriorityHint;
    private String storagePath = "";

    public AppConfig(String storageDir) {
        ompNumThreads = -1;
        cpuAffinityPolicy = 1;
        gpuPerfHint = 3;
        gpuPriorityHint = 1;
        storagePath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + File.separator + storageDir;
        File file = new File(storagePath);
        if (!file.exists()) {
            file.mkdir();
        }
    }

    public int getOmpNumThreads() {
        return ompNumThreads;
    }

    public int getCpuAffinityPolicy() {
        return cpuAffinityPolicy;
    }

    public int getGpuPerfHint() {
        return gpuPerfHint;
    }

    public int getGpuPriorityHint() {
        return gpuPriorityHint;
    }

    public String getStoragePath() {
        return storagePath;
    }
}

