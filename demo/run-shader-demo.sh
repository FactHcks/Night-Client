#!/bin/sh
# Runs the OpenGL gradient pattern demo using the LWJGL jars Minecraft already downloaded.
DIR="$(cd "$(dirname "$0")" && pwd)"
LWJGL="$HOME/.gradle/caches/modules-2/files-2.1/org.lwjgl"
VER=3.3.3

CP=""
for mod in lwjgl lwjgl-glfw lwjgl-opengl lwjgl-stb; do
    for jar in $(find "$LWJGL/$mod/$VER" -name "$mod-$VER.jar" -o -name "$mod-$VER-natives-linux.jar" 2>/dev/null); do
        CP="$CP:$jar"
    done
done

if [ -z "$CP" ]; then
    echo "LWJGL $VER not found in the Gradle cache. Run ./gradlew build once first." >&2
    exit 1
fi

exec java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -Ddemo.dir="$DIR" -cp "${CP#:}" "$DIR/ShaderDemo.java" "$@"
