// BumBoard: a small JNI bridge to Zinnia (BSD-3-Clause) for app.bumboard.handwriting.Zinnia.
// The recogniser memory-maps its model file, so an open model costs almost no memory.

#include <jni.h>

#include <string>

#include "zinnia.h"

namespace {

// NewStringUTF wants modified UTF-8; build Java strings from UTF-16 so characters outside the
// BMP (rare CJK) survive.
jstring toJava(JNIEnv *env, const char *utf8) {
    if (utf8 == nullptr) return nullptr;
    std::u16string out;
    const auto *p = reinterpret_cast<const unsigned char *>(utf8);
    while (*p) {
        uint32_t c;
        int extra;
        if (*p < 0x80) { c = *p; extra = 0; }
        else if ((*p >> 5) == 0x6) { c = *p & 0x1f; extra = 1; }
        else if ((*p >> 4) == 0xe) { c = *p & 0x0f; extra = 2; }
        else if ((*p >> 3) == 0x1e) { c = *p & 0x07; extra = 3; }
        else { ++p; continue; }
        ++p;
        for (int i = 0; i < extra && (*p & 0xc0) == 0x80; ++i, ++p) c = (c << 6) | (*p & 0x3f);
        if (c >= 0x10000) {
            c -= 0x10000;
            out.push_back(static_cast<char16_t>(0xd800 + (c >> 10)));
            out.push_back(static_cast<char16_t>(0xdc00 + (c & 0x3ff)));
        } else {
            out.push_back(static_cast<char16_t>(c));
        }
    }
    return env->NewString(reinterpret_cast<const jchar *>(out.data()), static_cast<jsize>(out.size()));
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_app_bumboard_handwriting_Zinnia_nativeOpen(JNIEnv *env, jclass, jstring path) {
    const char *file = env->GetStringUTFChars(path, nullptr);
    zinnia_recognizer_t *recognizer = zinnia_recognizer_new();
    const int ok = recognizer != nullptr && zinnia_recognizer_open(recognizer, file);
    env->ReleaseStringUTFChars(path, file);
    if (!ok) {
        if (recognizer != nullptr) zinnia_recognizer_destroy(recognizer);
        return 0;
    }
    return reinterpret_cast<jlong>(recognizer);
}

JNIEXPORT void JNICALL
Java_app_bumboard_handwriting_Zinnia_nativeClose(JNIEnv *, jclass, jlong handle) {
    auto *recognizer = reinterpret_cast<zinnia_recognizer_t *>(handle);
    if (recognizer != nullptr) {
        zinnia_recognizer_close(recognizer);
        zinnia_recognizer_destroy(recognizer);
    }
}

// strokes: x, y pairs; a stroke ends where x is negative.
JNIEXPORT jobjectArray JNICALL
Java_app_bumboard_handwriting_Zinnia_nativeClassify(JNIEnv *env, jclass, jlong handle,
        jint width, jint height, jfloatArray strokes, jint nbest) {
    auto *recognizer = reinterpret_cast<zinnia_recognizer_t *>(handle);
    jclass stringClass = env->FindClass("java/lang/String");
    if (recognizer == nullptr || strokes == nullptr) return env->NewObjectArray(0, stringClass, nullptr);
    zinnia_character_t *character = zinnia_character_new();
    zinnia_character_clear(character);
    zinnia_character_set_width(character, static_cast<size_t>(width));
    zinnia_character_set_height(character, static_cast<size_t>(height));
    const jsize n = env->GetArrayLength(strokes);
    jfloat *points = env->GetFloatArrayElements(strokes, nullptr);
    size_t stroke = 0;
    bool any = false;
    for (jsize i = 0; i + 1 < n; i += 2) {
        if (points[i] < 0) {
            if (any) ++stroke;
            any = false;
            continue;
        }
        zinnia_character_add(character, stroke, static_cast<int>(points[i]), static_cast<int>(points[i + 1]));
        any = true;
    }
    env->ReleaseFloatArrayElements(strokes, points, JNI_ABORT);
    zinnia_result_t *result = zinnia_recognizer_classify(recognizer, character, static_cast<size_t>(nbest));
    jobjectArray out;
    if (result == nullptr) {
        out = env->NewObjectArray(0, stringClass, nullptr);
    } else {
        const size_t size = zinnia_result_size(result);
        out = env->NewObjectArray(static_cast<jsize>(size), stringClass, nullptr);
        for (size_t i = 0; i < size; ++i) {
            jstring value = toJava(env, zinnia_result_value(result, i));
            env->SetObjectArrayElement(out, static_cast<jsize>(i), value);
            env->DeleteLocalRef(value);
        }
        zinnia_result_destroy(result);
    }
    zinnia_character_destroy(character);
    return out;
}

}  // extern "C"
