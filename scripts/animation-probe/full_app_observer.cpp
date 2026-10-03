#include <jni.h>
#include <jvmti.h>
#include <cstdio>
#include <string>

// Test-only JVMTI loader: the packaged runtime deliberately omits java.instrument.
// options: /absolute/observer.jar[:/absolute/java-atk-wrapper.jar],/absolute/output-directory
static std::string output;
static jvmtiEnv* environment;
static jmethodID redrawMethod;
static int traceCount;
static void JNICALL redrawBreakpoint(jvmtiEnv* jvmti, JNIEnv*, jthread thread, jmethodID, jlocation) {
    if (++traceCount > 30) return;
    jvmtiFrameInfo frames[24]; jint count = 0;
    jvmti->GetStackTrace(thread, 0, 24, frames, &count);
    std::fprintf(stderr, "REDRAW_TRACE %d\n", traceCount);
    for (int i = 0; i < count; i++) {
        char *name = nullptr, *signature = nullptr; jclass type;
        jvmti->GetMethodName(frames[i].method, &name, nullptr, nullptr);
        jvmti->GetMethodDeclaringClass(frames[i].method, &type);
        jvmti->GetClassSignature(type, &signature, nullptr);
        std::fprintf(stderr, "%s.%s\n", signature ? signature : "?", name ? name : "?");
        if (name) jvmti->Deallocate(reinterpret_cast<unsigned char*>(name));
        if (signature) jvmti->Deallocate(reinterpret_cast<unsigned char*>(signature));
    }
}
static void JNICALL traceRedraw(JNIEnv* env, jclass, jboolean enabled) {
    if (!redrawMethod) redrawMethod = env->GetMethodID(env->FindClass("org/jetbrains/skiko/SkiaLayer"), "needRender", "(Z)V");
    traceCount = 0;
    if (enabled) std::fprintf(stderr, "TRACE_BREAKPOINT result=%d\n", environment->SetBreakpoint(redrawMethod, 0));
    else environment->ClearBreakpoint(redrawMethod, 0);
}
static jint JNICALL tagCore(jlong, jlong, jlong* tag, jint, void*) { *tag = 0x153199; return 0; }
static jobjectArray JNICALL instances(JNIEnv* env, jclass, jclass type) {
    jvmtiHeapCallbacks callbacks{};
    callbacks.heap_iteration_callback = tagCore;
    if (environment->IterateThroughHeap(0, type, &callbacks, nullptr) != JVMTI_ERROR_NONE) return nullptr;
    const jlong tag = 0x153199;
    jint count = 0;
    jobject* objects = nullptr;
    jlong* tags = nullptr;
    if (environment->GetObjectsWithTags(1, &tag, &count, &objects, &tags) != JVMTI_ERROR_NONE) return nullptr;
    jobjectArray result = env->NewObjectArray(count, env->FindClass("java/lang/Object"), nullptr);
    for (int i = 0; i < count; i++) { env->SetObjectArrayElement(result, i, objects[i]); environment->SetTag(objects[i], 0); }
    if (objects) environment->Deallocate(reinterpret_cast<unsigned char*>(objects));
    if (tags) environment->Deallocate(reinterpret_cast<unsigned char*>(tags));
    return result;
}
static void JNICALL start(JNIEnv* env) {
    jclass observer = env->FindClass("FullAppObserver");
    if (observer) {
        JNINativeMethod nativeMethod{const_cast<char*>("instances"), const_cast<char*>("(Ljava/lang/Class;)[Ljava/lang/Object;"), reinterpret_cast<void*>(instances)};
        env->RegisterNatives(observer, &nativeMethod, 1);
        JNINativeMethod traceMethod{const_cast<char*>("traceRedraw"), const_cast<char*>("(Z)V"), reinterpret_cast<void*>(traceRedraw)};
        env->RegisterNatives(observer, &traceMethod, 1);
        jmethodID method = env->GetStaticMethodID(observer, "start", "(Ljava/lang/String;)V");
        if (method) env->CallStaticVoidMethod(observer, method, env->NewStringUTF(output.c_str()));
    }
    if (env->ExceptionCheck()) env->ExceptionDescribe();
}
static void JNICALL vmInit(jvmtiEnv*, JNIEnv* env, jthread) { start(env); }
extern "C" JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* vm, char* options, void*) {
    if (!options) return JNI_ERR;
    std::string value(options);
    auto delimiter = value.find(',');
    if (delimiter == std::string::npos) return JNI_ERR;
    output = value.substr(delimiter + 1);
    jvmtiEnv* jvmti = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&jvmti), JVMTI_VERSION_1_2) != JNI_OK) return JNI_ERR;
    environment = jvmti;
    jvmtiCapabilities capabilities{};
    capabilities.can_tag_objects = 1;
    capabilities.can_generate_breakpoint_events = 1;
    if (jvmti->AddCapabilities(&capabilities) != JVMTI_ERROR_NONE) return JNI_ERR;
    const std::string jars = value.substr(0, delimiter);
    for (std::size_t begin = 0; begin < jars.size();) {
        auto end = jars.find(':', begin);
        if (end == std::string::npos) end = jars.size();
        if (jvmti->AddToSystemClassLoaderSearch(jars.substr(begin, end - begin).c_str()) != JVMTI_ERROR_NONE) return JNI_ERR;
        begin = end + 1;
    }
    jvmtiEventCallbacks callbacks{};
    callbacks.VMInit = vmInit;
    callbacks.Breakpoint = redrawBreakpoint;
    if (jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks)) != JVMTI_ERROR_NONE) return JNI_ERR;
    jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_BREAKPOINT, nullptr);
    return jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, nullptr) == JVMTI_ERROR_NONE ? JNI_OK : JNI_ERR;
}
