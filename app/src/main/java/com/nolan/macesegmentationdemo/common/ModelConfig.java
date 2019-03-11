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

public class ModelConfig {
    private ModelInfo modelInfo;
    private String deviceType;

    public ModelConfig(ModelInfo modelInfo, String deviceType) {
        this.modelInfo = modelInfo;
        this.deviceType = deviceType;
    }

    public ModelInfo getModelInfo() {
        return modelInfo;
    }

    public String getModelName() {
        return modelInfo.getName();
    }

    public int[] getModelInputShape(int idx) {
        //TODO(liuqi): check availability
        return modelInfo.getInputShapes()[idx].getDims();
    }
    public int[] getModelOutputShape(int idx) {
        //TODO(liuqi): check availability
        return modelInfo.getOutputShapes()[idx].getDims();
    }

    public String getDeviceType() {
        return deviceType;
    }
}


