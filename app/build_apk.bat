@echo off
rem Builds BF-Companion.apk without Gradle, using the Android SDK command-line tools.
rem Needs: ANDROID_SDK (default %LocalAppData%\Android\Sdk) with platforms\android-34 and
rem build-tools\34.0.0, a JDK (javac, keytool) and Python on PATH.
setlocal
cd /d "%~dp0"
if "%ANDROID_SDK%"=="" set ANDROID_SDK=%LocalAppData%\Android\Sdk
rem build-tools 35 (d8 that accepts JDK 21 class files); 34 only as a fallback
set BT=%ANDROID_SDK%\build-tools\35.0.0
if not exist "%BT%\aapt2.exe" set BT=%ANDROID_SDK%\build-tools\34.0.0
set JAR=%ANDROID_SDK%\platforms\android-34\android.jar
if not exist "%BT%\aapt2.exe" (echo Android build-tools not found under %ANDROID_SDK% & exit /b 1)

rmdir /s /q build 2>nul
mkdir build\gen build\classes build\dex
copy /y ..\static\icon-192.png res\mipmap-xxhdpi\ic_launcher.png >nul

"%BT%\aapt2" compile --dir res -o build\res.zip || exit /b 1
rem assets\www holds the whole unit guide (run ..\export_bundle.py first)
if not exist assets\www\index.html (echo assets\www is missing: run python export_bundle.py first & exit /b 1)
rem the app's version and build day, for its About sheet (from this manifest)
python -c "import re, json, datetime; m = open('AndroidManifest.xml', encoding='utf-8').read(); json.dump({'version': re.search(r'versionName=.([^\x22]+)', m)[1], 'built': datetime.date.today().isoformat()}, open('assets/www/data/version.json', 'w'))" || exit /b 1
"%BT%\aapt2" link -o build\base.apk -I "%JAR%" --manifest AndroidManifest.xml --java build\gen -A assets --auto-add-overlay build\res.zip || exit /b 1
javac -g:none -source 11 -target 11 -classpath "%JAR%" -d build\classes build\gen\sg\bf\companion\R.java src\sg\bf\companion\MainActivity.java || exit /b 1
jar cf build\classes.jar -C build\classes . || exit /b 1
call "%BT%\d8.bat" --min-api 24 --lib "%JAR%" --output build\dex build\classes.jar || exit /b 1
rem add the code: rewrite the zip (appending in place leaves headers zipalign complains about)
python -c "import zipfile as Z; s=Z.ZipFile('build/base.apk'); d=Z.ZipFile('build/full.apk','w'); [d.writestr(i, s.read(i.filename)) for i in s.infolist()]; d.write('build/dex/classes.dex','classes.dex',Z.ZIP_DEFLATED); d.close()" || exit /b 1
"%BT%\zipalign" -f -p 4 build\full.apk build\aligned.apk || exit /b 1
rem The signing key lives outside the project, in Documents\BF Companion signing key (or the BF_KEYDIR folder),
rem with its password stored encrypted for this Windows account. Every version must use this same key, or
rem it won't install over the previous one, so a missing key stops the build instead of making a new one.
if "%BF_KEYDIR%"=="" for /f "usebackq delims=" %%d in (`powershell -NoProfile -Command "[Environment]::GetFolderPath('MyDocuments')"`) do set "BF_KEYDIR=%%d\BF Companion signing key"
if not exist "%BF_KEYDIR%\bf-companion.keystore" (echo Signing key not found in "%BF_KEYDIR%": restore it from your backup. & exit /b 1)
for /f "usebackq delims=" %%p in (`powershell -NoProfile -Command "[Net.NetworkCredential]::new('', (Get-Content -LiteralPath (Join-Path $env:BF_KEYDIR 'password.dpapi') | ConvertTo-SecureString)).Password"`) do set "BF_KEYPASS=%%p"
if "%BF_KEYPASS%"=="" (echo Could not read the signing key password: run "Show password.bat" in "%BF_KEYDIR%". & exit /b 1)
call "%BT%\apksigner.bat" sign --ks "%BF_KEYDIR%\bf-companion.keystore" --ks-pass env:BF_KEYPASS --key-pass env:BF_KEYPASS --out BF-Companion.apk build\aligned.apk || exit /b 1
set BF_KEYPASS=
echo Built BF-Companion.apk
