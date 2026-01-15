// jungle_helper_ui.h - 打野助手UI头文件
#ifndef JUNGLE_HELPER_UI_H
#define JUNGLE_HELPER_UI_H

// 初始化打野助手UI
void JungleHelper_Init();

// 关闭打野助手UI
void JungleHelper_Shutdown();

// 设置可见性
void JungleHelper_SetVisible(bool visible);

// 获取可见性
bool JungleHelper_IsVisible();

// 渲染UI (deltaTime: 距离上一帧的时间，单位秒)
void JungleHelper_Render(float deltaTime);

// 获取主窗口像素尺寸（用于Android悬浮窗自适应尺寸）
// 若尚未渲染过，将返回 (0, 0)。
void JungleHelper_GetMainWindowSize(int* outWidthPx, int* outHeightPx);

// 检测并消费一次 OCR 触发请求（来自 ImGui 按钮）
// 返回 true 表示需要进入 OCR 框选流程。
bool JungleHelper_ConsumeOcrRequest();

// 检测并消费一次权限申请请求（来自 ImGui 按钮）
// 返回 true 表示需要申请录屏权限。
bool JungleHelper_ConsumePermissionRequest();

#endif // JUNGLE_HELPER_UI_H
