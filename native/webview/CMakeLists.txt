cmake_minimum_required(VERSION 3.20)
project(apricityui_webview LANGUAGES CXX)

if(NOT WIN32)
    message(FATAL_ERROR "The offscreen WebView2 host is Windows-only.")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreadedDLL")

# NuGet layout of Microsoft.Web.WebView2: <root>/build/native/{include,x64}
set(WEBVIEW2_SDK_DIR "" CACHE PATH "Path to the extracted Microsoft.Web.WebView2 package")
if(NOT WEBVIEW2_SDK_DIR)
    message(FATAL_ERROR "Pass -DWEBVIEW2_SDK_DIR=<extracted nuget package dir>")
endif()
set(WEBVIEW2_INCLUDE "${WEBVIEW2_SDK_DIR}/build/native/include")
set(WEBVIEW2_LIB_DIR "${WEBVIEW2_SDK_DIR}/build/native/x64")
if(NOT EXISTS "${WEBVIEW2_INCLUDE}/WebView2.h")
    message(FATAL_ERROR "WebView2.h not found under ${WEBVIEW2_INCLUDE}")
endif()

find_package(JNI REQUIRED)

# C++/WinRT projection headers for Windows.Graphics.Capture. vcvars sets these, and the
# packaging script always configures from a developer prompt.
file(TO_CMAKE_PATH "$ENV{WindowsSdkDir}Include/$ENV{WindowsSDKVersion}" WINSDK_INCLUDE_DIR)
if(NOT EXISTS "${WINSDK_INCLUDE_DIR}/cppwinrt/winrt/base.h")
    message(FATAL_ERROR "C++/WinRT headers not found under ${WINSDK_INCLUDE_DIR}")
endif()

add_library(apricityui_webview SHARED
        src/webview_host.cpp
        src/frame_stream.cpp
        src/jni_bridge.cpp)

target_include_directories(apricityui_webview PRIVATE
        "${WEBVIEW2_INCLUDE}"
        "${JAVA_INCLUDE_PATH}"
        "${JAVA_INCLUDE_PATH2}"
        "${WINSDK_INCLUDE_DIR}/cppwinrt"
        "${WINSDK_INCLUDE_DIR}/winrt"
        "${CMAKE_CURRENT_SOURCE_DIR}/src")

target_compile_definitions(apricityui_webview PRIVATE UNICODE _UNICODE NOMINMAX
        WINVER=0x0A00 _WIN32_WINNT=0x0A00)

# The sources carry UTF-8 comments; without this MSVC reads them in the system code page
# and warns (C4819), and a mis-decoded multi-byte sequence can swallow the next line.
if(MSVC)
    # /utf-8: the sources carry UTF-8 comments.
    # /bigobj: C++/WinRT projections instantiate a lot of templates.
    target_compile_options(apricityui_webview PRIVATE /utf-8 /bigobj)
endif()

target_link_libraries(apricityui_webview PRIVATE
        "${WEBVIEW2_LIB_DIR}/WebView2LoaderStatic.lib"
        windowscodecs.lib
        dcomp.lib
        ole32.lib
        oleaut32.lib
        user32.lib
        gdi32.lib
        shell32.lib
        shlwapi.lib
        advapi32.lib
        version.lib
        d3d11.lib
        dxgi.lib
        windowsapp.lib)

set_target_properties(apricityui_webview PROPERTIES
        PREFIX ""
        OUTPUT_NAME "apricityui_webview"
        RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/out")

# Multi-config generators (Visual Studio, Xcode) append the configuration name;
# pin every configuration to the same directory so the packaging script has one
# stable path.
foreach(config DEBUG RELEASE RELWITHDEBINFO MINSIZEREL)
    set_target_properties(apricityui_webview PROPERTIES
            RUNTIME_OUTPUT_DIRECTORY_${config} "${CMAKE_BINARY_DIR}/out")
endforeach()
