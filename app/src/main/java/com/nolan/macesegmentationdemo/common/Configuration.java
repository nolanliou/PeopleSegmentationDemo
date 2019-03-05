package com.nolan.macesegmentationdemo.common;

import com.nolan.macesegmentationdemo.segmentation.MaskProcessor;


public class Configuration {
    private AppConfig appConfig;
    private ModelConfig modelConfig;
    private MaskProcessor maskProcessor;

    public Configuration(String storageDir, ModelConfig modelConfig, MaskProcessor maskProcessor) {
        this.appConfig = new AppConfig(storageDir);
        this.modelConfig = modelConfig;
        this.maskProcessor = maskProcessor;
    }

    public AppConfig getAppConfig() {
        return appConfig;
    }

    public ModelConfig getModelConfig() {
        return modelConfig;
    }

    public MaskProcessor getMaskProcessor() {
        return maskProcessor;
    }

}
