#!/usr/bin/env sh
# Fetches the prebuilt ncnn (Vulkan) libraries the page enhancer's native code links against
# into third_party/ (kept out of git). Run once from the repository root before building:
#     sh tools/enhance/fetch_ncnn.sh
# Must match NCNN_VERSION in app/src/main/cpp/CMakeLists.txt.
set -e
VERSION=20260526
NAME="ncnn-$VERSION-android-vulkan"
cd "$(dirname "$0")/../.."
if [ -d "third_party/$NAME" ]; then
    echo "third_party/$NAME already present"
    exit 0
fi
mkdir -p third_party
curl -fL -o "third_party/$NAME.zip" "https://github.com/Tencent/ncnn/releases/download/$VERSION/$NAME.zip"
unzip -q "third_party/$NAME.zip" -d third_party
rm "third_party/$NAME.zip"
echo "Extracted to third_party/$NAME"
