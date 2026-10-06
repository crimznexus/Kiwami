cmake_minimum_required(VERSION 3.22.1)
project(kiwami_enhance)

# Prebuilt ncnn with Vulkan (https://github.com/Tencent/ncnn/releases, 20260526), fetched into
# third_party/ by tools/enhance/fetch_ncnn.sh and kept out of git.
set(NCNN_VERSION 20260526)
set(ncnn_DIR ${CMAKE_SOURCE_DIR}/../../../../third_party/ncnn-${NCNN_VERSION}-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

add_library(kiwami_enhance SHARED kiwami_enhance.cpp)
target_link_libraries(kiwami_enhance ncnn android log)
