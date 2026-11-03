#!/usr/bin/env bash
# Builds a self-contained runtime image of Peint, with its own Java runtime,
# into target/peint, and archives it as dist/<name>.zip on Windows or
# dist/<name>.tar.gz elsewhere.
#
# Usage: build-image.sh <archive name>
#   e.g. build-image.sh peint-2.1.0-windows-x64
#
# jlink is called directly rather than through javafx-maven-plugin's
# javafx:jlink goal, which is no longer maintained and always passes the
# deprecated "--compress 0" syntax.
set -euo pipefail

name="$1"

# The JDK the build uses, rather than whichever is first on the PATH.
bin="${JAVA_HOME:+$JAVA_HOME/bin/}"

# Cleaned first, so no jar left over from an earlier build or version ends up
# on the module path.
./mvnw -B -q clean package -DskipTests
./mvnw -B -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/modules

# Each JavaFX module comes as an empty placeholder jar (javafx-base-25.0.5.jar)
# plus a jar for this platform (javafx-base-25.0.5-win.jar, or -linux, -mac,
# -mac-aarch64). jlink only accepts real modules, so the placeholders, the
# ones without a platform, are left out.
for jar in target/modules/javafx-*.jar; do
    if [[ "$(basename "$jar")" =~ ^javafx-[a-z]+-[0-9.]+\.jar$ ]]; then
        rm "$jar"
    fi
done

case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*) separator=";" ;;
    *) separator=":" ;;
esac

# The image is left uncompressed (zip-0): the archive it ships in compresses
# it far better as a whole than jlink does file by file (33 MB against 43 MB
# for the Windows zip).
rm -rf target/peint
"${bin}jlink" \
    --module-path "target/modules${separator}$(ls target/Peint-*.jar)" \
    --add-modules se.pbt.peint \
    --launcher peint=se.pbt.peint/se.pbt.peint.PeintApp \
    --add-options=--enable-native-access=javafx.graphics \
    --compress zip-0 \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --output target/peint

mkdir -p dist
case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*)
        # jar writes zip files; --no-manifest leaves out the manifest it would otherwise add.
        "${bin}jar" --create --no-manifest --file "dist/${name}.zip" -C target peint
        ;;
    *)
        # tar keeps the launcher script executable, which zip doesn't.
        tar -czf "dist/${name}.tar.gz" -C target peint
        ;;
esac
