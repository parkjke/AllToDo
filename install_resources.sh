#!/bin/bash
set -e

# 1. Define Paths
SRC_DIR="/Users/thinking/.gemini/antigravity/brain/0a9fea0f-667e-48c7-847e-efc2cc026277"
ANDROID_DIR="/Volumes/Work/AllToDo/AllToDo-Android/app/src/main/res/drawable"
IOS_BASE="/Volumes/Work/AllToDo/AllToDo-iOS/AllToDo/Assets.xcassets"

# 2. Source Files
PIN_TODO="$SRC_DIR/pin_todo_1765434839843.png"
PIN_HIST="$SRC_DIR/pin_history_1765434856553.png"
PIN_SERV="$SRC_DIR/pin_server_1765434871469.png"

echo "Checking source files..."
ls -l "$PIN_TODO"
ls -l "$PIN_HIST"
ls -l "$PIN_SERV"

# 3. Android Installation
echo "Installing for Android..."
cp "$PIN_TODO" "$ANDROID_DIR/ic_pin_todo.png"
cp "$PIN_HIST" "$ANDROID_DIR/ic_pin_history.png"
cp "$PIN_SERV" "$ANDROID_DIR/ic_pin_server.png"

# 4. iOS Installation
echo "Creating iOS Directories..."
mkdir -p "$IOS_BASE/PinTodo.imageset"
mkdir -p "$IOS_BASE/PinHistory.imageset"
mkdir -p "$IOS_BASE/PinServer.imageset"

echo "Copying images to iOS..."
cp "$PIN_TODO" "$IOS_BASE/PinTodo.imageset/pin_todo.png"
cp "$PIN_HIST" "$IOS_BASE/PinHistory.imageset/pin_history.png"
cp "$PIN_SERV" "$IOS_BASE/PinServer.imageset/pin_server.png"

# 5. iOS JSON Config
echo "Generating JSON..."
JSON_CONTENT='{
  "images" : [
    { "idiom" : "universal", "scale" : "1x" },
    { "idiom" : "universal", "scale" : "2x" },
    { "filename" : "FILENAME", "idiom" : "universal", "scale" : "3x" }
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}'

echo "$JSON_CONTENT" | sed "s/FILENAME/pin_todo.png/" > "$IOS_BASE/PinTodo.imageset/Contents.json"
echo "$JSON_CONTENT" | sed "s/FILENAME/pin_history.png/" > "$IOS_BASE/PinHistory.imageset/Contents.json"
echo "$JSON_CONTENT" | sed "s/FILENAME/pin_server.png/" > "$IOS_BASE/PinServer.imageset/Contents.json"

echo "All Done!"
