# Pirámide – envoltorio Android

La APK (`../piramide.apk`) es un WebView nativo que carga el juego desde
`assets/www/` (copia de `../app/`).

Compilación usada:

1. `javac --release 8` de `src/MainActivity.java` contra stubs de la API de Android.
2. `dx --dex --min-sdk-version=21` → `classes.dex`.
3. Proyecto apktool: `AndroidManifest.xml`, `apktool.yml`, `res/` (+ íconos), `assets/www/`, `classes.dex`.
4. `apktool b` (aapt2) → `uber-apk-signer` (zipalign + firma v1/v2/v3).

Paquete: `com.blackstudio.piramide` · minSdk 21 · targetSdk 34.
