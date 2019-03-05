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

