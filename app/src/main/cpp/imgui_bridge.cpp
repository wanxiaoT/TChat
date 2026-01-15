// imgui_bridge.cpp - JNI桥接层，连接Kotlin和ImGui
#include <jni.h>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <cstdlib>

#include "imgui/imgui.h"
#include "imgui/imgui_impl_opengl3.h"
#include "imgui/My_font/zh_Font.h"
#include "jungle_helper_ui.h"

#define LOG_TAG "ImGuiBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 全局状态
static bool g_Initialized = false;
static int g_DisplayWidth = 0;
static int g_DisplayHeight = 0;
static float g_DisplayDensity = 1.0f;
static std::chrono::steady_clock::time_point g_LastFrameTime;
static std::chrono::steady_clock::time_point g_LastResizeLogTime;
static AAssetManager* g_AssetManager = nullptr;

// 清屏颜色（SurfaceView为透明时需要清成全透明，否则会出现大块半透明背景）
static ImVec4 g_ClearColor = ImVec4(0.0f, 0.0f, 0.0f, 0.0f);

extern "C" {

JNIEXPORT void JNICALL
Java_com_tchat_wanxiaot_junglehelper_ImGuiBridge_nativeSetAssetManager(
    JNIEnv* env, jobject thiz, jobject asset_manager) {

    (void)thiz;
    g_AssetManager = asset_manager ? AAssetManager_fromJava(env, asset_manager) : nullptr;
}

JNIEXPORT void JNICALL
Java_com_tchat_wanxiaot_junglehelper_ImGuiBridge_nativeInit(
    JNIEnv* env, jobject thiz, jint width, jint height, jfloat density) {

    if (g_Initialized) {
        LOGI("ImGui already initialized, skipping");
        return;
    }

    LOGI("Initializing ImGui: %dx%d, density=%.2f", width, height, density);

    g_DisplayWidth = width;
    g_DisplayHeight = height;
    g_DisplayDensity = density;

    // 创建ImGui上下文
    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();

    // 禁用ini文件保存
    io.IniFilename = nullptr;

    // 设置显示大小
    io.DisplaySize = ImVec2((float)width, (float)height);
    io.DisplayFramebufferScale = ImVec2(1.0f, 1.0f);

    // 设置字体大小（根据屏幕密度调整）
    ImFontConfig fontConfig;
    fontConfig.SizePixels = 20.0f * density;
    fontConfig.OversampleH = 2;
    fontConfig.OversampleV = 2;
    const ImWchar* glyphRanges = io.Fonts->GetGlyphRangesChineseSimplifiedCommon();

    // 优先加载内置中文字体（避免依赖assets/system字体导致部分机型闪退）
    ImFont* mainFont = nullptr;
    {
        ImFontConfig cfg = fontConfig;
        cfg.FontDataOwnedByAtlas = false; // 字体数据是静态常量，不能由Atlas释放
        cfg.FontNo = 0;
        cfg.Flags = (ImFontFlags)(cfg.Flags | ImFontFlags_NoLoadError);
        mainFont = io.Fonts->AddFontFromMemoryTTF(
            const_cast<void*>(static_cast<const void*>(OPPOSans_H)),
            (int)OPPOSans_H_size,
            cfg.SizePixels,
            &cfg,
            glyphRanges);
        if (mainFont) {
            LOGI("Loaded embedded CJK font (OPPOSans_H, %u bytes)", OPPOSans_H_size);
        }
    }

    // 再尝试加载assets内的字体（最稳定，避免机型/system字体差异）
    if (!mainFont && g_AssetManager) {
        const char* assetCandidates[] = {
            "fonts/cjk.ttf",
            "fonts/chinese.ttf",
            "fonts/NotoSansSC-Regular.ttf",
            "fonts/NotoSansCJK-Regular.ttc",
        };

        for (const char* assetPath : assetCandidates) {
            AAsset* asset = AAssetManager_open(g_AssetManager, assetPath, AASSET_MODE_BUFFER);
            if (!asset) {
                continue;
            }

            const size_t size = (size_t)AAsset_getLength(asset);
            const void* src = AAsset_getBuffer(asset);
            if (!src || size == 0) {
                AAsset_close(asset);
                continue;
            }

            void* ownedCopy = std::malloc(size);
            if (!ownedCopy) {
                AAsset_close(asset);
                continue;
            }
            std::memcpy(ownedCopy, src, size);
            AAsset_close(asset);

            ImFontConfig cfg = fontConfig;
            cfg.FontDataOwnedByAtlas = true;
            cfg.FontNo = 0;
            mainFont = io.Fonts->AddFontFromMemoryTTF(ownedCopy, (int)size, cfg.SizePixels, &cfg, glyphRanges);
            if (mainFont) {
                LOGI("Loaded CJK font from asset '%s' (%zu bytes)", assetPath, size);
                break;
            }

            std::free(ownedCopy);
        }
    }

    // 再尝试加载系统中文字体（避免在APK内打包大字体文件）
    struct FontCandidate {
        const char* path;
    };
    const FontCandidate candidates[] = {
        // 老机型常见（通常包含中日韩字符）
        {"/system/fonts/DroidSansFallback.ttf"},
        // 部分系统存在的Noto CJK字体集合
        {"/system/fonts/NotoSansCJK-Regular.ttc"},
        {"/system/fonts/NotoSansCJK.ttc"},
    };

    for (const auto& candidate : candidates) {
        if (mainFont) {
            break;
        }
        ImFontConfig cfg = fontConfig;
        // 避免系统字体缺失时触发ImGui断言导致直接崩溃
        cfg.Flags = (ImFontFlags)(cfg.Flags | ImFontFlags_NoLoadError);
        cfg.FontNo = 0;
        mainFont = io.Fonts->AddFontFromFileTTF(candidate.path, cfg.SizePixels, &cfg, glyphRanges);
        if (mainFont) {
            LOGI("Loaded CJK font from '%s'", candidate.path);
            break;
        }
    }

    // 回退：默认字体（不支持中文）
    if (!mainFont) {
        mainFont = io.Fonts->AddFontDefault(&fontConfig);
        LOGI("Falling back to default font (CJK may not render)");
    }
    io.FontDefault = mainFont;

    // 设置样式（亮色模式）
    ImGui::StyleColorsLight();
    ImGuiStyle& style = ImGui::GetStyle();
    style.ScaleAllSizes(density);

    // 调整窗口圆角和边距
    style.WindowRounding = 8.0f;
    style.FrameRounding = 4.0f;
    style.GrabRounding = 4.0f;
    style.WindowPadding = ImVec2(12.0f, 12.0f);

    // 初始化OpenGL3后端
    ImGui_ImplOpenGL3_Init("#version 300 es");

    // 初始化打野助手UI
    JungleHelper_Init();

    g_LastFrameTime = std::chrono::steady_clock::now();
    g_LastResizeLogTime = g_LastFrameTime;
    g_Initialized = true;

    LOGI("ImGui initialized successfully");
}

JNIEXPORT void JNICALL
Java_com_tchat_wanxiaot_junglehelper_ImGuiBridge_nativeShutdown(
    JNIEnv* env, jobject thiz) {

    if (!g_Initialized) {
        return;
    }

    LOGI("Shutting down ImGui");

    JungleHelper_Shutdown();
    ImGui_ImplOpenGL3_Shutdown();
    ImGui::DestroyContext();

    g_Initialized = false;
}

JNIEXPORT void JNICALL
Java_com_tchat_wanxiaot_junglehelper_ImGuiBridge_nativeResize(
    JNIEnv* env, jobject thiz, jint width, jint height) {

    if (!g_Initialized) {
        return;
    }

    g_DisplayWidth = width;
    g_DisplayHeight = height;

    ImGuiIO& io = ImGui::GetIO();
    io.DisplaySize = ImVec2((float)width, (float)height);

    auto now = std::chrono::steady_clock::now();
    if (now - g_LastResizeLogTime >= std::chrono::seconds(1)) {
        LOGI("Resized to %dx%d", width, height);
        g_LastResizeLogTime = now;
    }
}

JNIEXPORT void JNICALL
Java_com_tchat_wanxiaot_junglehelper_ImGuiBridge_nativeRender(
    JNIEnv* env, jobject thiz) {

    if (!g_Initialized) {
        return;
    }

    // 计算deltaTime
    auto currentTime = std::chrono::steady_clock::now();
    float deltaTime = std::chrono::duration<float>(currentTime - g_LastFrameTime).count();
    g_LastFrameTime = currentTime;

    ImGuiIO& io = ImGui::GetIO();
    io.DeltaTime = deltaTime > 0.0f ? deltaTime : (1.0f / 60.0f);

    // 开始新帧
    ImGui_ImplOpenGL3_NewFrame();
    ImGui::NewFrame();

    // 渲染打野助手UI
    JungleHelper_Render(deltaTime);

    // 渲染ImGui
    ImGui::Render();

    // 清屏（半透明背景）
    glViewport(0, 0, g_DisplayWidth, g_DisplayHeight);
    glClearColor(g_ClearColor.x, g_ClearColor.y, g_ClearColor.z, g_ClearColor.w);
    glClear(GL_COLOR_BUFFER_BIT);

    // 绘制ImGui
    ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
}

JNIEXPORT void JNICALL
Java_com_tchat_wanxiaot_junglehelper_ImGuiBridge_nativeOnTouch(
    JNIEnv* env, jobject thiz, jint action, jfloat x, jfloat y, jint pointerId) {

    if (!g_Initialized) {
        return;
    }

    ImGuiIO& io = ImGui::GetIO();

    // ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MOVE = 2
    // ACTION_POINTER_DOWN = 5, ACTION_POINTER_UP = 6
    switch (action) {
        case 0: // ACTION_DOWN
        case 5: // ACTION_POINTER_DOWN
            io.AddMousePosEvent(x, y);
            io.AddMouseButtonEvent(0, true);
            break;

        case 1: // ACTION_UP
        case 6: // ACTION_POINTER_UP
            io.AddMousePosEvent(x, y);
            io.AddMouseButtonEvent(0, false);
            break;

        case 2: // ACTION_MOVE
            io.AddMousePosEvent(x, y);
            break;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_tchat_wanxiaot_junglehelper_ImGuiBridge_nativeWantCaptureMouse(
    JNIEnv* env, jobject thiz) {

    if (!g_Initialized) {
        return JNI_FALSE;
    }

    return ImGui::GetIO().WantCaptureMouse ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_tchat_wanxiaot_junglehelper_ImGuiBridge_nativeConsumeOcrRequest(
    JNIEnv* env, jobject thiz) {

    (void)env;
    (void)thiz;
    return JungleHelper_ConsumeOcrRequest() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_tchat_wanxiaot_junglehelper_ImGuiBridge_nativeConsumePermissionRequest(
    JNIEnv* env, jobject thiz) {

    (void)env;
    (void)thiz;
    return JungleHelper_ConsumePermissionRequest() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_tchat_wanxiaot_junglehelper_ImGuiBridge_nativeSetVisible(
    JNIEnv* env, jobject thiz, jboolean visible) {

    JungleHelper_SetVisible(visible);
}

JNIEXPORT jboolean JNICALL
Java_com_tchat_wanxiaot_junglehelper_ImGuiBridge_nativeIsVisible(
    JNIEnv* env, jobject thiz) {

    return JungleHelper_IsVisible() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_tchat_wanxiaot_junglehelper_ImGuiBridge_nativeGetMainWindowSize(
    JNIEnv* env, jobject thiz) {

    if (!g_Initialized) {
        return 0;
    }

    int widthPx = 0;
    int heightPx = 0;
    JungleHelper_GetMainWindowSize(&widthPx, &heightPx);

    const uint64_t packed =
        (static_cast<uint64_t>(static_cast<uint32_t>(widthPx)) << 32) |
        static_cast<uint32_t>(heightPx);
    return static_cast<jlong>(packed);
}

} // extern "C"
