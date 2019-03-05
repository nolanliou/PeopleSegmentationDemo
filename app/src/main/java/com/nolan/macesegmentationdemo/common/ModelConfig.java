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


