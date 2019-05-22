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

#include "src/main/cpp/mace_jni.h"

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <fstream>
#include <functional>
#include <map>
#include <memory>
#include <string>
#include <vector>
#include <numeric>

#include "src/main/cpp/include/mace/public/mace.h"
#include "mace/public/mace.h"

namespace {

struct ModelInfo {
  mace::DeviceType device_type;
  std::vector<std::string> input_names;
  std::vector<std::string> output_names;
  std::vector<std::vector<int64_t>> input_shapes;
  std::vector<std::vector<int64_t>> output_shapes;
};

struct MaceEngineEntity {
  std::shared_ptr<mace::MaceEngine> engine;
  std::vector<unsigned char> weight_data;
  ModelInfo model_info;
};

mace::DeviceType ParseDeviceType(const std::string &device) {
  if (device.compare("CPU") == 0) {
    return mace::DeviceType::CPU;
  } else if (device.compare("GPU") == 0) {
    return mace::DeviceType::GPU;
  } else if (device.compare("HEXAGON") == 0) {
    return mace::DeviceType::HEXAGON;
  } else {
    return mace::DeviceType::CPU;
  }
}

class MaceContext {
 public:
  static MaceContext *Instance() {
    static MaceContext context;
    return &context;
  }
  void CreateGPUContext(const std::string &storage_path);
  std::shared_ptr<mace::GPUContext> GetGPUContext();
  MaceEngineEntity *CreateEntity(std::string engine_name);
  MaceEngineEntity *GetEntity(std::string engine_name) const;

 private:
  MaceContext() = default;
  ~MaceContext() = default;
  MaceContext(const MaceContext &context) = delete;
  MaceContext(MaceContext &&context) = delete;
  MaceContext &operator=(const MaceContext &context) = delete;
  MaceContext &operator=(const MaceContext &&context) = delete;

private:
  std::shared_ptr<mace::GPUContext> gpu_context_;
  std::map<std::string, std::unique_ptr<MaceEngineEntity>> entities_;
};

void MaceContext::CreateGPUContext(const std::string &storage_path) {
  if (gpu_context_ == nullptr) {
    gpu_context_ = mace::GPUContextBuilder()
            .SetStoragePath(storage_path)
            .Finalize();
  }
}

std::shared_ptr<mace::GPUContext> MaceContext::GetGPUContext() {
  return this->gpu_context_;
}

MaceEngineEntity* MaceContext::CreateEntity(std::string entity_name) {
  entities_[entity_name] = std::unique_ptr<MaceEngineEntity>(new MaceEngineEntity);
  return entities_.at(entity_name).get();
}

MaceEngineEntity* MaceContext::GetEntity(std::string entity_name) const {
  if(entities_.count(entity_name) == 0) {
    return nullptr;
  }
  return entities_.at(entity_name).get();
}

jint ParseModelInfo(
    JNIEnv *env,
    std::string entity_name,
    jobjectArray raw_input_names, jobjectArray raw_output_names,
    jintArray raw_input_shapes, jintArray raw_output_shapes) {
  MaceEngineEntity *mace_engine_entity = MaceContext::Instance()->GetEntity(entity_name);

  int input_count = env->GetArrayLength(raw_input_names);
  int output_count = env->GetArrayLength(raw_output_names);
  mace_engine_entity->model_info.input_names.resize(input_count);
  mace_engine_entity->model_info.input_shapes.resize(input_count);
  mace_engine_entity->model_info.output_names.resize(output_count);
  mace_engine_entity->model_info.output_shapes.resize(output_count);

  jint *input_shapes_ptr = env->GetIntArrayElements(raw_input_shapes, 0);
  int input_shapes_length = env->GetArrayLength(raw_input_shapes);
  int shape_idx = 0;
  for (int i = 0; i < input_count; i++) {
      jstring name = (jstring) (env->GetObjectArrayElement(raw_input_names, i));
      const char *name_ptr = env->GetStringUTFChars(name, 0);
      if (name_ptr == nullptr) return JNI_ERR;
      mace_engine_entity->model_info.input_names[i] = std::string(name_ptr);
      while(shape_idx < input_shapes_length && input_shapes_ptr[shape_idx] != 0) {
          mace_engine_entity->model_info.input_shapes[i].push_back(input_shapes_ptr[shape_idx]);
          shape_idx++;
      }
      shape_idx++;
      env->ReleaseStringUTFChars(name, name_ptr);
  }
  env->ReleaseIntArrayElements(raw_input_shapes, input_shapes_ptr, 0);

  shape_idx = 0;
  jint *output_shapes_ptr = env->GetIntArrayElements(raw_output_shapes, 0);
  int output_shapes_length = env->GetArrayLength(raw_output_shapes);
  for (int i = 0; i < output_count; i++) {
      jstring name = (jstring) (env->GetObjectArrayElement(raw_output_names, i));
      const char *name_ptr = env->GetStringUTFChars(name, 0);
      // Don't forget to call `ReleaseStringUTFChars` when you're done.
      if (name_ptr == nullptr) return JNI_ERR;
      mace_engine_entity->model_info.output_names[i] = std::string(name_ptr);
      while(shape_idx < output_shapes_length && output_shapes_ptr[shape_idx] != 0) {
          mace_engine_entity->model_info.output_shapes[i].push_back(output_shapes_ptr[shape_idx]);
          shape_idx++;
      }
      shape_idx++;
      env->ReleaseStringUTFChars(name, name_ptr);
  }
  env->ReleaseIntArrayElements(raw_output_shapes, output_shapes_ptr, 0);
  return JNI_OK;
}

}  // namespace


JNIEXPORT jfloatArray JNICALL
Java_com_xiaomi_mace_MaceJni_getDeviceCapability(JNIEnv *env,
                                                      jclass thisObj,
                                                      jstring device,
                                                      jfloat base_cpu_exec_time) {
  setenv("MACE_CPP_MIN_VLOG_LEVEL", "0", 1);
  // get device
  const char *device_ptr = env->GetStringUTFChars(device, nullptr);
  if (device_ptr == nullptr) return nullptr;
  mace::DeviceType device_type = ParseDeviceType(device_ptr);
  env->ReleaseStringUTFChars(device, device_ptr);

  mace::Capability capability = mace::GetCapability(device_type, base_cpu_exec_time);

  // transform output
  std::vector<float> output(2);
  if (capability.supported) {
    output[0] = capability.float32_performance.exec_time;
    output[1] = capability.quantized8_performance.exec_time;
  } else {
    output[0] = output[1] = -1.f;
  }
  jfloatArray jOutputData = env->NewFloatArray(2);  // allocate
  if (jOutputData == nullptr) return nullptr;
  env->SetFloatArrayRegion(jOutputData, 0, 2, output.data());  // copy

  return jOutputData;
}

JNIEXPORT jint JNICALL
Java_com_xiaomi_mace_MaceJni_createGPUContext(
    JNIEnv *env, jclass thisObj, jstring storage_path) {
  // DO NOT USE tmp directory.
  // Please use APP's own directory and make sure the directory exists.
  const char *storage_path_ptr = env->GetStringUTFChars(storage_path, nullptr);
  if (storage_path_ptr == nullptr) return JNI_ERR;
  const std::string storage_file_path(storage_path_ptr);
  env->ReleaseStringUTFChars(storage_path, storage_path_ptr);
  MaceContext::Instance()->CreateGPUContext(storage_file_path);
  return JNI_OK;
}

JNIEXPORT jint JNICALL
Java_com_xiaomi_mace_MaceJni_createEngine(
    JNIEnv *env, jclass thisObj, jobject assetManager, jstring raw_model_name,
    jint omp_num_threads, jint cpu_affinity_policy,
    jint gpu_perf_hint, jint gpu_priority_hint,
    jstring model_graph_file_name, jstring model_weight_file_name,
    jobjectArray raw_input_names, jobjectArray raw_output_names,
    jintArray raw_input_shapes, jintArray raw_output_shapes,
    jstring device) {
  const char *model_name_ptr = env->GetStringUTFChars(raw_model_name, nullptr);
  if (model_name_ptr == nullptr) return JNI_ERR;
  std::string model_name(model_name_ptr);
  MaceEngineEntity *mace_engine_entity = MaceContext::Instance()->CreateEntity(model_name);
  if (mace_engine_entity == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR,
                        "MACE JNI",
                        "Create engine entity failed");
    return JNI_ERR;
  }
  env->ReleaseStringUTFChars(raw_model_name, model_name_ptr);

  // get device
  const char *device_ptr = env->GetStringUTFChars(device, nullptr);
  if (device_ptr == nullptr) return JNI_ERR;
  mace_engine_entity->model_info.device_type = ParseDeviceType(device_ptr);
  env->ReleaseStringUTFChars(device, device_ptr);

  // create MaceEngineConfig
  mace::MaceStatus status;
  mace::MaceEngineConfig config(mace_engine_entity->model_info.device_type);
  status = config.SetCPUThreadPolicy(
      omp_num_threads,
      static_cast<mace::CPUAffinityPolicy>(cpu_affinity_policy));
  if (status != mace::MaceStatus::MACE_SUCCESS) {
    __android_log_print(ANDROID_LOG_ERROR,
                        "MACE JNI",
                        "Set CPU thread policy failed. openmp result: %s, threads: %d, cpu: %d",
                        status.information().c_str(), omp_num_threads,
                        cpu_affinity_policy);
  }
  if (mace_engine_entity->model_info.device_type == mace::DeviceType::GPU) {
    config.SetGPUContext(MaceContext::Instance()->GetGPUContext());
    config.SetGPUHints(
        static_cast<mace::GPUPerfHint>(gpu_perf_hint),
        static_cast<mace::GPUPriorityHint>(gpu_priority_hint));
    __android_log_print(ANDROID_LOG_INFO,
                        "MACE JNI",
                        "gpu perf: %d, priority: %d",
                        gpu_perf_hint, gpu_priority_hint);
  }

  __android_log_print(ANDROID_LOG_INFO,
                      "MACE JNI",
                      "Create engine on device: %d",
                      mace_engine_entity->model_info.device_type);

  AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
  if (mgr == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR,
                        "MACE JNI",
                        "AAssetManager is NULL");
    return JNI_ERR;
  }
  //  parse model graph file name
  const char *model_graph_file_ptr = env->GetStringUTFChars(model_graph_file_name, nullptr);
  if (model_graph_file_ptr == nullptr) return JNI_ERR;
  AAsset* model_graph_asset = AAssetManager_open(mgr, model_graph_file_ptr, AASSET_MODE_UNKNOWN);
  if (model_graph_asset == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR, "MACE JNI", "_ASSET_NOT_FOUND_");
    return JNI_ERR;
  }
  env->ReleaseStringUTFChars(model_graph_file_name, model_graph_file_ptr);
  size_t model_graph_size = static_cast<size_t>(AAsset_getLength(model_graph_asset));
  std::vector<unsigned char> model_graph_data(model_graph_size);
  AAsset_read(model_graph_asset, model_graph_data.data(), model_graph_size);
  AAsset_close(model_graph_asset);
  //  parse model data file name
  const char *model_weight_file_ptr = env->GetStringUTFChars(model_weight_file_name, nullptr);
  if (model_weight_file_ptr == nullptr) return JNI_ERR;
  AAsset* model_weight_asset = AAssetManager_open(mgr, model_weight_file_ptr, AASSET_MODE_UNKNOWN);
  if (model_weight_asset == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR, "MACE JNI", "_ASSET_NOT_FOUND_");
    return JNI_ERR;
  }
  env->ReleaseStringUTFChars(model_weight_file_name, model_weight_file_ptr);
  size_t model_weight_size = static_cast<size_t>(AAsset_getLength(model_weight_asset));
  mace_engine_entity->weight_data.resize(model_weight_size);
  AAsset_read(model_weight_asset, mace_engine_entity->weight_data.data(), model_weight_size);
  AAsset_close(model_weight_asset);

  //  load model input and output name
  jint parse_status = ParseModelInfo(env, model_name, raw_input_names, raw_output_names,
                                     raw_input_shapes, raw_output_shapes);
  if (parse_status != JNI_OK) {
    return parse_status;
  }

  mace::MaceStatus create_engine_status =
      CreateMaceEngineFromProto(model_graph_data.data(),
                                model_graph_size,
                                mace_engine_entity->weight_data.data(),
                                model_weight_size,
                                mace_engine_entity->model_info.input_names,
                                mace_engine_entity->model_info.output_names,
                                config,
                                &mace_engine_entity->engine);

  __android_log_print(ANDROID_LOG_INFO,
                      "MACE JNI",
                      "Engine creation result: %s",
                      create_engine_status.information().c_str());
  if (mace_engine_entity->model_info.device_type != mace::DeviceType::CPU) {
    mace_engine_entity->weight_data.clear();
  }

  return create_engine_status == mace::MaceStatus::MACE_SUCCESS ?
         JNI_OK : JNI_ERR;
}

JNIEXPORT jfloatArray JNICALL
Java_com_xiaomi_mace_MaceJni_inference(
    JNIEnv *env, jclass thisObj, jstring raw_model_name, jfloatArray input_data) {
  const char *model_name_ptr = env->GetStringUTFChars(raw_model_name, nullptr);
  if (model_name_ptr == nullptr) return nullptr;
  MaceEngineEntity *mace_engine_entity = MaceContext::Instance()->GetEntity(model_name_ptr);
  env->ReleaseStringUTFChars(raw_model_name, model_name_ptr);
  //  prepare input and output
  const ModelInfo &model_info = mace_engine_entity->model_info;
  const std::string input_name = model_info.input_names[0];
  auto &input_shape = model_info.input_shapes[0];
  const int64_t input_size = std::accumulate(input_shape.begin(), input_shape.end(),
                                             1, std::multiplies<int64_t>());
  const std::string output_name = model_info.output_names[0];
  auto &output_shape = model_info.output_shapes[0];
  const int64_t output_size = std::accumulate(output_shape.begin(), output_shape.end(),
                                              1, std::multiplies<int64_t>());

  //  load input
  jfloat *input_data_ptr = env->GetFloatArrayElements(input_data, nullptr);
  if (input_data_ptr == nullptr) return nullptr;
  jsize length = env->GetArrayLength(input_data);
  if (length != input_size) return nullptr;

  std::map<std::string, mace::MaceTensor> inputs;
  std::map<std::string, mace::MaceTensor> outputs;
  // construct input
  auto buffer_in = std::shared_ptr<float>(new float[input_size],
                                          std::default_delete<float[]>());
  std::copy_n(input_data_ptr, input_size, buffer_in.get());
  env->ReleaseFloatArrayElements(input_data, input_data_ptr, 0);
  inputs[input_name] = mace::MaceTensor(input_shape, buffer_in);

  // construct output
  auto buffer_out = std::shared_ptr<float>(new float[output_size],
                                           std::default_delete<float[]>());
  outputs[output_name] = mace::MaceTensor(output_shape, buffer_out);

  // run model
  mace_engine_entity->engine->Run(inputs, &outputs);

  // transform output
  jfloatArray jOutputData = env->NewFloatArray(output_size);  // allocate
  if (jOutputData == nullptr) return nullptr;
  env->SetFloatArrayRegion(jOutputData, 0, output_size,
                           outputs[output_name].data().get());  // copy

  return jOutputData;
}
