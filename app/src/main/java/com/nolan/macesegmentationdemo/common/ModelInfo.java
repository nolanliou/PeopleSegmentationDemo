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


public class ModelInfo {
    private String name;
    private String[] inputNames;
    private Shape[] inputShapes;
    private String[] outputNames;
    private Shape[] outputShapes;
    private int basedCPUExecTime;
    private boolean quantized8CPU;
    private boolean quantized8DSP;

    public ModelInfo(String name, String[] inputNames, Shape[] inputShapes,
                     String[] outputNames, Shape[] outputShapes, int basedCPUExecTime,
                     boolean quantized8CPU, boolean quantized8DSP) {
        this.name = name;
        this.inputNames = inputNames;
        this.inputShapes = inputShapes;
        this.outputNames = outputNames;
        this.outputShapes = outputShapes;
        this.basedCPUExecTime = basedCPUExecTime;
        this.quantized8CPU = quantized8CPU;
        this.quantized8DSP = quantized8DSP;
    }

    public String getName() {
        return name;
    }

    public String[] getInputNames() {
        return inputNames;
    }

    public Shape[] getInputShapes() {
        return inputShapes;
    }

    public String[] getOutputNames() {
        return outputNames;
    }

    public Shape[] getOutputShapes() {
        return outputShapes;
    }

    public int getBasedCPUExecTime() {
        return basedCPUExecTime;
    }

    public boolean isQuantized8CPU() {
        return quantized8CPU;
    }

    public boolean isQuantized8DSP() {
        return quantized8DSP;
    }
}
