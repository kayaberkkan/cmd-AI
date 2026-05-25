@echo off
echo ==========================================
echo    CMD ASISTAN - WINDOWS PAKETLEME
echo ==========================================
echo.

echo [1/5] Java kontrol ediliyor...
java -version 2>nul
if errorlevel 1 (
    echo HATA: Java bulunamadi! JDK 17+ kurun.
    pause
    exit /b 1
)

echo.
echo [2/5] Java derlemesi yapiliyor...
if not exist build mkdir build
javac -encoding UTF-8 -source 11 -target 11 -cp "lib/*" -d build src/*.java
if errorlevel 1 (
    echo HATA: Derleme basarisiz!
    pause
    exit /b 1
)

if not exist dist mkdir dist
jar cfm dist/TerminalAsistani.jar src/Manifest.txt -C build .
echo Derleme tamamlandi!
echo.
echo [3/5] Fat JAR olusturuluyor...
if exist temp_jar rmdir /s /q temp_jar
mkdir temp_jar
cd temp_jar
jar xf ..\dist\TerminalAsistani.jar
jar xf ..\lib\jline-3.26.1.jar
mkdir META-INF 2>nul
echo Manifest-Version: 1.0 > META-INF\MANIFEST.MF
echo Main-Class: Main >> META-INF\MANIFEST.MF
echo. >> META-INF\MANIFEST.MF
jar cfm ..\dist\TerminalAsistani-fat.jar META-INF\MANIFEST.MF .
cd ..
rmdir /s /q temp_jar
echo Fat JAR olusturuldu!

echo.
echo [4/5] Paketleme hazirligi...
if exist temp_package rmdir /s /q temp_package
mkdir temp_package
copy dist\TerminalAsistani-fat.jar temp_package\TerminalAsistani.jar >nul
copy dist\config.properties temp_package\ >nul 2>nul
copy resources\icon.png temp_package\ >nul 2>nul

echo.
echo [5/5] jpackage ile Windows uygulamasi olusturuluyor...
if exist cmdAI rmdir /s /q cmdAI

jpackage ^
    --input temp_package ^
    --main-jar TerminalAsistani.jar ^
    --main-class Main ^
    --name "cmdAI" ^
    --type app-image ^
    --app-version "1.0" ^
    --vendor "Berkkan Kaya" ^
    --copyright "2026 Berkkan Kaya" ^
    --description "Yapay Zeka Destekli Terminal Asistani" ^
    --java-options "-Xmx256m -Dhttps.protocols=TLSv1.2,TLSv1.3 -Djdk.tls.client.protocols=TLSv1.2,TLSv1.3" ^
    --icon resources\icon.ico ^
    --win-console ^
    --add-modules java.base,java.desktop,java.net.http,java.logging,jdk.crypto.cryptoki,jdk.crypto.ec

echo.
echo [5.5/5] Ikon ve yapilandirma dosyalari gizleniyor...
attrib +h cmdAI\app\icon.png >nul 2>nul
attrib +h cmdAI\app\cmdAI.cfg >nul 2>nul
attrib +h cmdAI\app\TerminalAsistani.jar >nul 2>nul
attrib +h cmdAI\cmdAI.ico >nul 2>nul

rmdir /s /q temp_package 2>nul

echo.
echo ==========================================
echo PAKETLEME TAMAMLANDI!
echo.
echo Uygulama: .\cmdAI\cmdAI.exe
echo ==========================================
if not "%CI%"=="true" pause
