#!/usr/bin/env bash

# ==============================================================================
# WaveEQ - Script de Automatización de Despliegue Nativo (Android 14+ / API 34)
# 100% Jetpack Compose • Kotlin • DynamicsProcessing • Visualizer 60 FPS
# ==============================================================================

set -eo pipefail

# Colores para salida de terminal
RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
PURPLE='\033[0;35m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Configuración por defecto
BUILD_MODE="debug"
CLEAN_CACHE=false
LAUNCH_APP=true
SHOW_LOGS=false
TARGET_DEVICE=""
PACKAGE_NAME="com.audiowaveeq"

print_banner() {
    echo -e "${CYAN}${BOLD}"
    echo "================================================================================"
    echo "   ██╗    ██╗ █████╗ ██╗   ██╗███████╗███████╗ ██████╗ "
    echo "   ██║    ██║██╔══██╗██║   ██║██╔════╝██╔════╝██╔═══██╗"
    echo "   ██║ █╗ ██║███████║██║   ██║█████╗  █████╗  ██║   ██║"
    echo "   ██║███╗██║██╔══██║╚██╗ ██╔╝██╔══╝  ██╔══╝  ██║▄▄ ██║"
    echo "   ╚███╔███╔╝██║  ██║ ╚████╔╝ ███████╗███████╗╚██████╔╝"
    echo "    ╚══╝╚══╝ ╚═╝  ╚═╝  ╚═══╝  ╚══════╝╚══════╝ ╚══▀▀═╝ "
    echo "   Motor Nativo 100% Jetpack Compose • 32 Bandas PEQ • Android 14+ (API 34)"
    echo "================================================================================"
    echo -e "${NC}"
}

print_help() {
    echo -e "${BOLD}Uso:${NC} ./deploy.sh [OPCIONES]"
    echo ""
    echo -e "${BOLD}Opciones de Compilación:${NC}"
    echo "  --debug             Compila en modo Debug con Jetpack Compose (por defecto)."
    echo "  --release           Compila APK optimizado en modo Release."
    echo "  --clean             Limpia cachés de Gradle y carpetas build/."
    echo "  --no-launch         No inicia automáticamente la aplicación tras la instalación."
    echo "  --zip               Genera el archivo ZIP 100% puro para GitHub / Android Studio."
    echo "  --logs              Inicia logcat filtrando por etiquetas WaveEQ tras el despliegue."
    echo "  --device <ID>       Especifica el número de serie del dispositivo ADB destino."
    echo "  --help, -h          Muestra este menú de ayuda."
    echo ""
    echo -e "${BOLD}Ejemplos:${NC}"
    echo "  ./deploy.sh --clean --debug"
    echo "  ./deploy.sh --zip"
    echo "  ./deploy.sh --release --no-launch"
    echo "  ./deploy.sh --debug --logs"
    exit 0
}

# Parseo de argumentos
while [[ $# -gt 0 ]]; do
    case "$1" in
        --zip)
            echo -e "${CYAN}Generando paquete ZIP nativo Android puro...${NC}"
            python3 scripts/create_zip.py
            exit 0
            ;;
        --debug)
            BUILD_MODE="debug"
            shift
            ;;
        --release)
            BUILD_MODE="release"
            shift
            ;;
        --clean)
            CLEAN_CACHE=true
            shift
            ;;
        --no-launch)
            LAUNCH_APP=false
            shift
            ;;
        --logs)
            SHOW_LOGS=true
            shift
            ;;
        --device)
            TARGET_DEVICE="$2"
            shift 2
            ;;
        --help|-h)
            print_help
            ;;
        *)
            echo -e "${RED}[ERROR] Opción desconocida: $1${NC}"
            print_help
            ;;
    esac
done

print_banner

echo -e "${BLUE}[INFO]${NC} Modo de compilación seleccionado: ${BOLD}${BUILD_MODE^^}${NC}"

# 1. Verificación de Entorno y Prerrequisitos
echo -e "\n${PURPLE}==> 1. Verificando Entorno Java y Android SDK...${NC}"

# Java / JDK
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_EXEC="$JAVA_HOME/bin/java"
elif command -v java &> /dev/null; then
    JAVA_EXEC="java"
else
    echo -e "${YELLOW}[ALERTA] JAVA_HOME no configurado. Verificando ejecutable 'java'...${NC}"
    JAVA_EXEC=""
fi

if [ -n "$JAVA_EXEC" ]; then
    JAVA_VER=$("$JAVA_EXEC" -version 2>&1 | head -n 1)
    echo -e "${GREEN}[OK]${NC} Java JDK detectado: ${JAVA_VER}"
else
    echo -e "${RED}[ERROR] No se encontró Java JDK 17+. Asegúrate de tener Android Studio o OpenJDK 17 instalado.${NC}"
fi

# Android SDK
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
        echo -e "${GREEN}[OK]${NC} ANDROID_HOME detectado automáticamente en: $ANDROID_HOME"
    fi
fi

# 2. Limpieza de Caché si se especificó --clean
if [ "$CLEAN_CACHE" = true ]; then
    echo -e "\n${PURPLE}==> 2. Limpieza Profunda de Caché y Compilaciones Previas...${NC}"
    (
        cd android
        ./gradlew clean --no-daemon || true
    )
    echo -e "${GREEN}[OK]${NC} Limpieza completada exitosamente."
fi

# 3. Compilación Nativa con Gradle
echo -e "\n${PURPLE}==> 3. Compilando APK Nativo (Jetpack Compose)...${NC}"
(
    cd android
    chmod +x gradlew
    if [ "$BUILD_MODE" == "release" ]; then
        ./gradlew assembleRelease --no-daemon --stacktrace
    else
        ./gradlew assembleDebug --no-daemon --stacktrace
    fi
)

# 4. Verificación de APK Resultante
echo -e "\n${PURPLE}==> 4. Verificando Artefacto APK...${NC}"
if [ "$BUILD_MODE" == "release" ]; then
    APK_PATH="android/app/build/outputs/apk/release/app-release-unsigned.apk"
    if [ ! -f "$APK_PATH" ]; then
        APK_PATH="android/app/build/outputs/apk/release/app-release.apk"
    fi
else
    APK_PATH="android/app/build/outputs/apk/debug/app-debug.apk"
fi

if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
    echo -e "${GREEN}[EXITO]${NC} APK generado correctamente: ${BOLD}$APK_PATH${NC} (${APK_SIZE})"
else
    echo -e "${YELLOW}[AVISO]${NC} El archivo no se encuentra en la ruta esperada ($APK_PATH)."
fi

# 5. Despliegue con ADB si hay dispositivo conectado
if command -v adb &> /dev/null; then
    echo -e "\n${PURPLE}==> 5. Desplegando en Dispositivo Android...${NC}"
    if [ -f "$APK_PATH" ]; then
        adb install -r "$APK_PATH"
        if [ "$LAUNCH_APP" = true ]; then
            echo -e "${CYAN}Iniciando WaveEQ (Jetpack Compose)...${NC}"
            adb shell am start -n "$PACKAGE_NAME/.MainActivity"
        fi
        if [ "$SHOW_LOGS" = true ]; then
            echo -e "${CYAN}Iniciando visualización de Logcat (Filtro: WaveEQ)...${NC}"
            adb logcat -v time -s WaveEQ_AudioEQService:V WaveEQ_MainActivity:V WaveEQ_App:V
        fi
    fi
fi

echo -e "\n${GREEN}${BOLD}¡Despliegue y compilación finalizados con éxito!${NC}\n"
