cmake_minimum_required(VERSION 3.22.1)
project(racing_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(native_app_glue STATIC
    ${CMAKE_ANDROID_NDK}/sources/android/native_app_glue/android_native_app_glue.c
)

target_include_directories(native_app_glue PUBLIC
    ${CMAKE_ANDROID_NDK}/sources/android/native_app_glue
)

add_library(racing_native SHARED
    native_main.cpp
)

target_include_directories(racing_native PRIVATE
    ${CMAKE_ANDROID_NDK}/sources/android/native_app_glue
)

target_link_options(racing_native PRIVATE "-u" "ANativeActivity_onCreate")

find_library(android-lib android)
find_library(log-lib log)
find_library(egl-lib EGL)
find_library(glesv3-lib GLESv3)

target_link_libraries(racing_native PRIVATE
    native_app_glue
    ${android-lib}
    ${log-lib}
    ${egl-lib}
    ${glesv3-lib}
)
