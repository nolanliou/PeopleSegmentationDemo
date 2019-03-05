package com.nolan.macesegmentationdemo.segmentation;


import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

public class MaskProcessor {
    public class Result {
        public Bitmap segBitmap;
        public Set<String> labels;
        Result() {
            this.labels = new HashSet<>();
        }
    }
    private int colors[];
    private Vector<String> labels;

    public MaskProcessor(final AssetManager assetManager,
                         final String labelFilename) throws IOException {
        // 21 class for pascal_voc_2012
        this.colors = new int[21];
        int alpha = 150;
        this.colors[0] = Color.argb(255, 0, 0, 0);
        this.colors[1] = Color.argb(alpha, 128, 0, 0);
        this.colors[2] = Color.argb(alpha, 0, 128, 0);
        this.colors[3] = Color.argb(alpha, 128, 128, 0);
        this.colors[4] = Color.argb(alpha, 0, 0, 128);
        this.colors[5] = Color.argb(alpha, 128, 0, 128);
        this.colors[6] = Color.argb(alpha, 0, 128, 128);
        this.colors[7] = Color.argb(alpha, 128, 128, 128);
        this.colors[8] = Color.argb(alpha, 64, 0, 0);
        this.colors[9] = Color.argb(alpha, 192, 0, 0);
        this.colors[10] = Color.argb(alpha, 64, 128, 0);
        this.colors[11] = Color.argb(alpha, 192, 128, 0);
        this.colors[12] = Color.argb(alpha, 64, 0, 128);
        this.colors[13] = Color.argb(alpha, 192, 0, 128);
        this.colors[14] = Color.argb(alpha, 64, 128, 128);
        this.colors[15] = Color.argb(alpha, 192, 128, 128);
        this.colors[16] = Color.argb(alpha, 0, 64, 0);
        this.colors[17] = Color.argb(alpha, 128, 64, 0);
        this.colors[18] = Color.argb(alpha, 0, 192, 0);
        this.colors[19] = Color.argb(alpha, 128, 192, 0);
        this.colors[20] = Color.argb(alpha, 0, 64, 128);

        labels = new Vector<>();
        InputStream labelsInput;
        String actualFilename = labelFilename.split("file:///android_asset/")[1];
        labelsInput = assetManager.open(actualFilename);
        BufferedReader reader;
        reader = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = reader.readLine()) != null) {
            labels.add(line);
        }
    }

    public Result process(int inputHeight, int inputWidth,
                          int outWidth,
                          int[] segData) {
        // crop
        Result result = new Result();
        int[] pixels = new int[inputHeight * inputWidth];
        for (int hIdx = 0; hIdx < inputHeight; ++hIdx) {
            for (int wIdx = 0; wIdx < inputWidth; ++wIdx) {
                int classValue = segData[hIdx * outWidth + wIdx];
                pixels[hIdx * inputWidth + wIdx] = this.colors[classValue];
                result.labels.add(this.labels.get(classValue));
            }
        }
        result.segBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888);
        result.segBitmap.setPixels(pixels, 0, inputWidth,
                0, 0, inputWidth, inputHeight);

        return result;
    }
}
