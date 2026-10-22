// BumBoard: a small JNI bridge to librime (BSD-3-Clause) for app.bumboard.rime.Rime.

#include <jni.h>
#include <android/log.h>

#include <string>
#include <vector>

#include <rime_api.h>

#define LOG_TAG "BumRime"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

RimeApi *rime() {
    static RimeApi *api = rime_get_api();
    return api;
}

std::string toString(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

// NewStringUTF wants modified UTF-8, which differs from Rime's UTF-8 for characters outside
// the BMP (many rare Chinese characters), so build Java strings from UTF-16 instead.
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

JNIEXPORT void JNICALL
Java_app_bumboard_rime_Rime_nativeStart(JNIEnv *env, jclass, jstring sharedDir, jstring userDir,
                                        jboolean fullCheck) {
    static std::string shared, user;
    shared = toString(env, sharedDir);
    user = toString(env, userDir);
    RIME_STRUCT(RimeTraits, traits);
    traits.shared_data_dir = shared.c_str();
    traits.user_data_dir = user.c_str();
    traits.distribution_name = "BumBoard";
    traits.distribution_code_name = "bumboard";
    traits.distribution_version = "1";
    traits.app_name = "rime.bumboard";
    traits.min_log_level = 2;
    traits.log_dir = "";
    rime()->setup(&traits);
    rime()->initialize(&traits);
    if (rime()->start_maintenance(fullCheck ? True : False)) {
        rime()->join_maintenance_thread();
    }
    LOGI("Rime started (shared=%s, user=%s)", shared.c_str(), user.c_str());
}

JNIEXPORT void JNICALL
Java_app_bumboard_rime_Rime_nativeStop(JNIEnv *, jclass) {
    rime()->finalize();
}

JNIEXPORT jlong JNICALL
Java_app_bumboard_rime_Rime_nativeCreateSession(JNIEnv *, jclass) {
    return static_cast<jlong>(rime()->create_session());
}

JNIEXPORT void JNICALL
Java_app_bumboard_rime_Rime_nativeDestroySession(JNIEnv *, jclass, jlong session) {
    rime()->destroy_session(static_cast<RimeSessionId>(session));
}

JNIEXPORT jboolean JNICALL
Java_app_bumboard_rime_Rime_nativeSelectSchema(JNIEnv *env, jclass, jlong session, jstring schema) {
    return rime()->select_schema(static_cast<RimeSessionId>(session), toString(env, schema).c_str())
           ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_app_bumboard_rime_Rime_nativeSetOption(JNIEnv *env, jclass, jlong session, jstring option,
                                            jboolean value) {
    rime()->set_option(static_cast<RimeSessionId>(session), toString(env, option).c_str(),
                       value ? True : False);
}

JNIEXPORT jboolean JNICALL
Java_app_bumboard_rime_Rime_nativeProcessKey(JNIEnv *, jclass, jlong session, jint keycode,
                                             jint mask) {
    return rime()->process_key(static_cast<RimeSessionId>(session), keycode, mask)
           ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_app_bumboard_rime_Rime_nativeClearComposition(JNIEnv *, jclass, jlong session) {
    rime()->clear_composition(static_cast<RimeSessionId>(session));
}

JNIEXPORT jboolean JNICALL
Java_app_bumboard_rime_Rime_nativeCommitComposition(JNIEnv *, jclass, jlong session) {
    return rime()->commit_composition(static_cast<RimeSessionId>(session)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_app_bumboard_rime_Rime_nativeSelectCandidate(JNIEnv *, jclass, jlong session, jint index) {
    return rime()->select_candidate(static_cast<RimeSessionId>(session), static_cast<size_t>(index))
           ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_app_bumboard_rime_Rime_nativeGetCommit(JNIEnv *env, jclass, jlong session) {
    RIME_STRUCT(RimeCommit, commit);
    if (!rime()->get_commit(static_cast<RimeSessionId>(session), &commit)) return nullptr;
    jstring text = toJava(env, commit.text);
    rime()->free_commit(&commit);
    return text;
}

JNIEXPORT jstring JNICALL
Java_app_bumboard_rime_Rime_nativeGetInput(JNIEnv *env, jclass, jlong session) {
    return toJava(env, rime()->get_input(static_cast<RimeSessionId>(session)));
}

// Returns [preedit, cursor, highlighted, text0, comment0, text1, comment1, ...] with up to
// `limit` candidates, or null when nothing is being composed.
JNIEXPORT jobjectArray JNICALL
Java_app_bumboard_rime_Rime_nativeGetContext(JNIEnv *env, jclass, jlong session, jint limit) {
    const auto id = static_cast<RimeSessionId>(session);
    RIME_STRUCT(RimeContext, ctx);
    if (!rime()->get_context(id, &ctx)) return nullptr;
    std::string preedit = ctx.composition.preedit ? ctx.composition.preedit : "";
    const int cursor = ctx.composition.cursor_pos;
    const int highlighted = ctx.menu.page_no * ctx.menu.page_size + ctx.menu.highlighted_candidate_index;
    rime()->free_context(&ctx);
    if (preedit.empty()) return nullptr;

    std::vector<std::pair<std::string, std::string>> candidates;
    RimeCandidateListIterator it = {};
    if (rime()->candidate_list_from_index(id, &it, 0)) {
        while (static_cast<int>(candidates.size()) < limit && rime()->candidate_list_next(&it)) {
            candidates.emplace_back(it.candidate.text ? it.candidate.text : "",
                                    it.candidate.comment ? it.candidate.comment : "");
        }
        rime()->candidate_list_end(&it);
    }

    jclass stringClass = env->FindClass("java/lang/String");
    auto out = env->NewObjectArray(static_cast<jsize>(3 + 2 * candidates.size()), stringClass, nullptr);
    env->SetObjectArrayElement(out, 0, toJava(env, preedit.c_str()));
    env->SetObjectArrayElement(out, 1, env->NewStringUTF(std::to_string(cursor).c_str()));
    env->SetObjectArrayElement(out, 2, env->NewStringUTF(std::to_string(highlighted).c_str()));
    for (size_t i = 0; i < candidates.size(); ++i) {
        jstring text = toJava(env, candidates[i].first.c_str());
        jstring comment = toJava(env, candidates[i].second.c_str());
        env->SetObjectArrayElement(out, static_cast<jsize>(3 + 2 * i), text);
        env->SetObjectArrayElement(out, static_cast<jsize>(4 + 2 * i), comment);
        env->DeleteLocalRef(text);
        env->DeleteLocalRef(comment);
    }
    return out;
}

}  // extern "C"
