cmake_minimum_required(VERSION 3.22.1)

project(junglehelper)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ImGui source files
set(IMGUI_DIR ${CMAKE_CURRENT_SOURCE_DIR}/imgui)

add_library(${CMAKE_PROJECT_NAME} SHARED
    # JNI bridge
    imgui_bridge.cpp
    jungle_helper_ui.cpp

    # ImGui core
    ${IMGUI_DIR}/imgui.cpp
    ${IMGUI_DIR}/imgui_demo.cpp
    ${IMGUI_DIR}/imgui_draw.cpp
    ${IMGUI_DIR}/imgui_tables.cpp
    ${IMGUI_DIR}/imgui_widgets.cpp

    # ImGui backends
    ${IMGUI_DIR}/imgui_impl_android.cpp
    ${IMGUI_DIR}/imgui_impl_opengl3.cpp
)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
    IMGUI_IMPL_OPENGL_ES3
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${IMGUI_DIR}
)

target_link_libraries(${CMAKE_PROJECT_NAME} PRIVATE
    android
    EGL
    GLESv3
    log
)

# Android 16KB page size compatibility (ELF segment alignment).
# Safe for 4KB-page devices as well.
if (ANDROID)
    target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
        "-Wl,-z,common-page-size=16384"
        "-Wl,-z,max-page-size=16384"
    )
endif()
