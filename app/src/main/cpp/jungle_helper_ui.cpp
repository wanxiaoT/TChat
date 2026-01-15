// jungle_helper_ui.cpp - 打野助手自定义ImGui界面
#include "jungle_helper_ui.h"
#include <atomic>
#include "imgui/imgui.h"

// 窗口状态
static bool g_ShowMainWindow = true;
static float g_WindowAlpha = 0.95f;
static std::atomic<int> g_MainWindowWidthPx{0};
static std::atomic<int> g_MainWindowHeightPx{0};
static std::atomic<bool> g_OcrRequested{false};
static std::atomic<bool> g_PermissionRequested{false};

void JungleHelper_Init() {
    // 初始化
    g_ShowMainWindow = true;
    g_MainWindowWidthPx.store(0, std::memory_order_relaxed);
    g_MainWindowHeightPx.store(0, std::memory_order_relaxed);
}

void JungleHelper_Shutdown() {
    // 清理资源
}

void JungleHelper_SetVisible(bool visible) {
    g_ShowMainWindow = visible;
}

bool JungleHelper_IsVisible() {
    return g_ShowMainWindow;
}

void JungleHelper_Render(float deltaTime) {
    if (!g_ShowMainWindow) {
        return;
    }

    (void)deltaTime; // 未使用

    const ImGuiIO& io = ImGui::GetIO();

    // 设置窗口样式
    ImGui::SetNextWindowBgAlpha(g_WindowAlpha);
    ImGui::SetNextWindowPos(ImVec2(0.0f, 0.0f), ImGuiCond_Once);
    ImVec2 initialSize(575.0f, 450.0f);
    ImGui::SetNextWindowSize(initialSize, ImGuiCond_Once);

    ImGuiWindowFlags windowFlags =
        ImGuiWindowFlags_NoMove;

    // 主窗口（移除 NoCollapse，标题栏自带折叠按钮）
    if (ImGui::Begin(u8"打野助手", &g_ShowMainWindow, windowFlags)) {
        // 透明度滑块
        ImGui::SliderFloat(u8"透明度", &g_WindowAlpha, 0.3f, 1.0f);

        ImGui::Separator();
        const ImVec2 windowSize = ImGui::GetWindowSize();
        ImGui::Text(u8"窗口: %.0f x %.0f px", windowSize.x, windowSize.y);
        ImGui::Text(u8"Surface: %.0f x %.0f px", io.DisplaySize.x, io.DisplaySize.y);
        ImGui::Separator();

        if (ImGui::Button(u8"申请权限")) {
            g_PermissionRequested.store(true, std::memory_order_release);
        }
        ImGui::SameLine();
        if (ImGui::Button(u8"OCR框选")) {
            g_OcrRequested.store(true, std::memory_order_release);
        }
    }

    // 记录窗口像素尺寸（无论是否折叠都要记录）
    const ImVec2 windowSize = ImGui::GetWindowSize();
    g_MainWindowWidthPx.store((int)(windowSize.x + 0.5f), std::memory_order_relaxed);
    g_MainWindowHeightPx.store((int)(windowSize.y + 0.5f), std::memory_order_relaxed);

    ImGui::End();
}

void JungleHelper_GetMainWindowSize(int* outWidthPx, int* outHeightPx) {
    if (outWidthPx) {
        *outWidthPx = g_MainWindowWidthPx.load(std::memory_order_relaxed);
    }
    if (outHeightPx) {
        *outHeightPx = g_MainWindowHeightPx.load(std::memory_order_relaxed);
    }
}

bool JungleHelper_ConsumeOcrRequest() {
    return g_OcrRequested.exchange(false, std::memory_order_acq_rel);
}

bool JungleHelper_ConsumePermissionRequest() {
    return g_PermissionRequested.exchange(false, std::memory_order_acq_rel);
}
