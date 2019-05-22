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
        this.colors = new int[2];
        int alpha = 0;
        this.colors[0] = Color.argb(255, 0, 0, 0);
        this.colors[1] = Color.argb(alpha, 128, 0, 0);

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
