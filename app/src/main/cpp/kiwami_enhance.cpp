// JNI bridge between NcnnTileModel (Kotlin) and ncnn: runs the Real-ESRGAN x4 model on one
// tile at a time, on the GPU through Vulkan or on ncnn's CPU code.
//
// Tiles cross the boundary as planar RGB floats in 0..1 (3 * h * w), the same layout the
// ONNX Runtime path uses, so PageEnhancer does not care which engine runs.

#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <mutex>
#include <string>

#include "datareader.h"
#include "gpu.h"
#include "net.h"

#define LOG_TAG "KiwamiEnhance"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

std::once_flag gpu_once;

void ensure_gpu_instance() {
    // Creating the Vulkan instance is process-wide and must happen once.
    std::call_once(gpu_once, [] { ncnn::create_gpu_instance(); });
}

struct Model {
    ncnn::Net net;
};

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_ani_dantotsu_download_manga_enhance_NcnnTileModel_nativeGpuCount(JNIEnv *, jclass) {
    ensure_gpu_instance();
    return ncnn::get_gpu_count();
}

JNIEXPORT jlong JNICALL
Java_ani_dantotsu_download_manga_enhance_NcnnTileModel_nativeCreate(
        JNIEnv *env, jclass, jbyteArray param, jbyteArray bin, jboolean gpu) {
    auto *model = new Model();
    ncnn::Option &opt = model->net.opt;
    opt.use_vulkan_compute = gpu;
    // fp16 storage and maths: what the official realesrgan-ncnn-vulkan uses, and the weights
    // are stored as fp16 anyway. ncnn falls back where the hardware lacks it.
    opt.use_fp16_packed = true;
    opt.use_fp16_storage = true;
    opt.use_fp16_arithmetic = true;
    if (gpu) {
        ensure_gpu_instance();
        if (ncnn::get_gpu_count() == 0) {
            delete model;
            return 0;
        }
        model->net.set_vulkan_device(ncnn::get_default_gpu_index());
    }

    // load_param_mem wants a NUL-terminated string.
    jsize param_len = env->GetArrayLength(param);
    std::string param_text(static_cast<size_t>(param_len), '\0');
    env->GetByteArrayRegion(param, 0, param_len, reinterpret_cast<jbyte *>(&param_text[0]));
    if (model->net.load_param_mem(param_text.c_str()) != 0) {
        LOGW("load_param_mem failed");
        delete model;
        return 0;
    }

    // DataReaderFromMemory copies the weights, so the Java array can go after this call.
    jbyte *bin_bytes = env->GetByteArrayElements(bin, nullptr);
    const unsigned char *cursor = reinterpret_cast<const unsigned char *>(bin_bytes);
    ncnn::DataReaderFromMemory reader(cursor);
    int loaded = model->net.load_model(reader);
    env->ReleaseByteArrayElements(bin, bin_bytes, JNI_ABORT);
    if (loaded != 0) {
        LOGW("load_model failed");
        delete model;
        return 0;
    }
    return reinterpret_cast<jlong>(model);
}

JNIEXPORT jfloatArray JNICALL
Java_ani_dantotsu_download_manga_enhance_NcnnTileModel_nativeRun(
        JNIEnv *env, jclass, jlong handle, jfloatArray tile, jint w, jint h) {
    auto *model = reinterpret_cast<Model *>(handle);
    const size_t plane = static_cast<size_t>(w) * h;

    ncnn::Mat in(w, h, 3);
    jfloat *src = env->GetFloatArrayElements(tile, nullptr);
    for (int c = 0; c < 3; c++) {
        // Mat channels may be padded (cstep), so copy plane by plane.
        std::memcpy(in.channel(c), src + c * plane, plane * sizeof(float));
    }
    env->ReleaseFloatArrayElements(tile, src, JNI_ABORT);

    ncnn::Mat out;
    {
        ncnn::Extractor ex = model->net.create_extractor();
        if (ex.input("data", in) != 0 || ex.extract("output", out) != 0) {
            LOGW("extract failed for a %dx%d tile", w, h);
            return nullptr;
        }
    }
    if (out.c != 3) return nullptr;

    const size_t out_plane = static_cast<size_t>(out.w) * out.h;
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(3 * out_plane));
    if (result == nullptr) return nullptr;  // OutOfMemoryError is pending
    for (int c = 0; c < 3; c++) {
        env->SetFloatArrayRegion(result, static_cast<jsize>(c * out_plane),
                                 static_cast<jsize>(out_plane), out.channel(c));
    }
    return result;
}

JNIEXPORT void JNICALL
Java_ani_dantotsu_download_manga_enhance_NcnnTileModel_nativeDestroy(JNIEnv *, jclass, jlong handle) {
    delete reinterpret_cast<Model *>(handle);
}

}  // extern "C"
