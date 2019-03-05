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
#ifndef MACESEGMENTATIONDEMO_MACE_LIBRARY_SRC_MAIN_CPP_MACE_UTILS_H_
#define MACESEGMENTATIONDEMO_MACE_LIBRARY_SRC_MAIN_CPP_MACE_UTILS_H_

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_xiaomi_mace_JniMaceUtils
 * Method:    getDeviceCapability
 * Signature: (Ljava/lang/String;IIIILjava/lang/String;)I
 * Return: array with 2 elements: [0] for float32 performance, [1] for quantized8 performance
 */
JNIEXPORT jfloatArray JNICALL
Java_com_xiaomi_mace_MaceJni_getDeviceCapability(JNIEnv *,
                                                 jclass,
                                                 jstring,
                                                 jfloat);
/*
 * Class:     com_xiaomi_mace_JniMaceUtils
 * Method:    createGPUContext
 * Signature: (Ljava/lang/String;IIIILjava/lang/String;)I
 */
JNIEXPORT jint JNICALL
Java_com_xiaomi_mace_MaceJni_createGPUContext(JNIEnv *,
                                              jclass,
                                              jstring);

/*
 * Class:     com_xiaomi_mace_JniMaceUtils
 * Method:    createEngine
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL
Java_com_xiaomi_mace_MaceJni_createEngine(
    JNIEnv *, jclass, jobject, jstring, jint, jint, jint, jint,
    jstring, jstring, jobjectArray, jobjectArray,
    jintArray, jintArray, jstring);

/*
 * Class:     com_xiaomi_mace_JniMaceUtils
 * Method:    segmentImage
 * Signature: ([F)[F
 */
JNIEXPORT jfloatArray JNICALL
Java_com_xiaomi_mace_MaceJni_inference
  (JNIEnv *, jclass, jstring, jfloatArray);

#ifdef __cplusplus
}
#endif

#endif  // MACESEGMENTATIONDEMO_MACE_LIBRARY_SRC_MAIN_CPP_MACE_UTILS_H_
