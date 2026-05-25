#!/bin/bash
set -e

echo "=========================================="
echo "   CMD ASISTAN - PAKETLEME SÜRECİ"
echo "=========================================="

echo ""
echo "[1/4] Java derlemesi yapılıyor..."
./build.sh

echo ""
echo "[2/4] Fat JAR oluşturuluyor (JLine dahil ediliyor)..."
rm -rf temp_jar
mkdir -p temp_jar

cd temp_jar
jar xf ../dist/TerminalAsistani.jar

jar xf ../lib/jline-3.26.1.jar
mkdir -p META-INF
echo "Manifest-Version: 1.0" > META-INF/MANIFEST.MF
echo "Main-Class: Main" >> META-INF/MANIFEST.MF
echo "" >> META-INF/MANIFEST.MF

jar cfm ../dist/TerminalAsistani-fat.jar META-INF/MANIFEST.MF .
cd ..
rm -rf temp_jar
echo "✅ Fat JAR oluşturuldu!"
echo ""
echo "[3/4] Paketleme hazırlığı..."
rm -rf temp_package
mkdir -p temp_package
cp dist/TerminalAsistani-fat.jar temp_package/TerminalAsistani.jar
cp resources/cmdai.icns temp_package/ 2>/dev/null || true
cp resources/icon.png temp_package/ 2>/dev/null || true

echo ""
OS_NAME=$(uname -s)

if [ "$OS_NAME" = "Darwin" ]; then
    echo "[4/4] jpackage ile macOS uygulaması oluşturuluyor..."
    rm -rf "cmdAI.app"

    jpackage \
        --input temp_package \
        --main-jar TerminalAsistani.jar \
        --main-class Main \
        --name "cmdAI" \
        --type app-image \
        --app-version "1.1" \
        --vendor "Berkkan Kaya" \
        --copyright "2026 Berkkan Kaya" \
        --description "Yapay Zeka Destekli Terminal Asistanı" \
        --java-options "-Xmx256m" \
        --icon resources/cmdai.icns \
        --add-modules java.base,java.desktop,java.net.http,java.logging || true

    if [ -d "cmdAI.app" ]; then
        echo "✅ jpackage basariyla tamamlandi (cleanup hatasi gormezden gelindi)."
    else
        echo "❌ HATA: cmdAI.app olusturulamadi!"
        exit 1
    fi

    rm -rf temp_package

    echo ""
    echo "=========================================="
    echo "✅ PAKETLEME TAMAMLANDI!"
    echo ""
    echo "Uygulama: ./cmdAI.app"
    echo "Çalıştırmak için: open 'cmdAI.app'"
    echo "=========================================="

elif [ "$OS_NAME" = "Linux" ]; then
    echo "[4/4] jpackage ile Linux uygulaması oluşturuluyor..."
    rm -rf "cmdAI"
    rm -f cmdAI-linux-*.tar.gz

    jpackage \
        --input temp_package \
        --main-jar TerminalAsistani.jar \
        --main-class Main \
        --name "cmdAI" \
        --type app-image \
        --app-version "1.1" \
        --vendor "Berkkan Kaya" \
        --copyright "2026 Berkkan Kaya" \
        --description "Yapay Zeka Destekli Terminal Asistanı" \
        --java-options "-Xmx256m" \
        --icon resources/icon.png \
        --add-modules java.base,java.desktop,java.net.http,java.logging

    if [ -d "cmdAI" ]; then
        echo "✅ jpackage basariyla tamamlandi."
        ARCH=$(uname -m)
        if [ "$ARCH" = "x86_64" ]; then
            ARCH_NAME="amd64"
        elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
            ARCH_NAME="arm64"
        else
            ARCH_NAME="$ARCH"
        fi
        tar -czf "cmdAI-linux-${ARCH_NAME}.tar.gz" cmdAI
        echo "✅ Arşiv oluşturuldu: cmdAI-linux-${ARCH_NAME}.tar.gz"
    else
        echo "❌ HATA: cmdAI olusturulamadi!"
        exit 1
    fi

    rm -rf temp_package

    echo ""
    echo "=========================================="
    echo "✅ PAKETLEME TAMAMLANDI!"
    echo ""
    echo "Dizin: ./cmdAI"
    echo "Çalıştırılabilir dosya: ./cmdAI/bin/cmdAI"
    echo "Arşiv: ./cmdAI-linux-${ARCH_NAME}.tar.gz"
    echo "=========================================="
else
    echo "Bilinmeyen OS: $OS_NAME"
    exit 1
fi
