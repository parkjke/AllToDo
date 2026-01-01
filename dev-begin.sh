#!/bin/bash

# ==============================================================================
# AllToDo 통합 개발 환경 시작/종료 스크립트
# 사용법:
#   ./dev-begin.sh        : 4개의 서비스를 새 터미널 창에서 실행합니다.
#   ./dev-begin.sh -end   : 실행 중인 모든 서비스(포트 기반)를 종료합니다.
# ==============================================================================

# 프로젝트 루트 경로 (스크립트가 있는 위치)
PROJECT_ROOT=$(pwd)

# ------------------------------------------------------------------------------
# 1. 종료 모드 (-end)
# ------------------------------------------------------------------------------
if [ "$1" == "-end" ]; then
    echo "🛑  개발 서버들을 종료합니다..."
    
    # 종료할 포트 목록
    # 8000: Backend (Uvicorn)
    # 5173: WebMng (Vite Default)
    # 5175: WebApp (Vite)
    # 5177: HomePage (Custom Config)
    PORTS=(8000 5173 5175 5177)
    
    FOUND=0
    
    for PORT in "${PORTS[@]}"; do
        # 해당 포트를 사용 중인 프로세스 ID(PID) 찾기
        PID=$(lsof -t -i:$PORT)
        
        if [ -n "$PID" ]; then
            echo "   ❌ Port $PORT (PID: $PID) 종료 중..."
            kill -9 $PID
            FOUND=1
        fi
    done
    
    if [ $FOUND -eq 0 ]; then
        echo "   ℹ️ 실행 중인 개발 서버를 찾지 못했습니다."
    else
        echo "✅ 모든 서버가 종료되었습니다."
    fi
    exit 0
fi

# ------------------------------------------------------------------------------
# 2. 시작 모드 (Default)
# ------------------------------------------------------------------------------
echo "🚀  AllToDo 개발 환경을 시작합니다..."

osascript <<EOF
tell application "Terminal"
    activate
    
    -- 새 창 만들기
    do script "echo '🚀 Starting AllToDo Development Environment...'"
    set mainWindow to front window
    
    -- Tab 1: Backend
    do script "cd \"$PROJECT_ROOT/AllToDo-Backend\" && source .venv/bin/activate && uvicorn app.main:app --reload --host 0.0.0.0 --port 8000" in selected tab of mainWindow
    set custom title of selected tab of mainWindow to "Backend (8000)"
    
    -- Tab 2: WebApp
    tell mainWindow
        set newTab to do script "cd \"$PROJECT_ROOT/AllToDo-WebApp\" && npm run dev -- --port 5175"
        set custom title of newTab to "WebApp (5175)"
    end tell
    
    -- Tab 3: WebMng
    tell mainWindow
        set newTab to do script "cd \"$PROJECT_ROOT/AllToDo-WebMng\" && npm run dev -- --port 5173"
        set custom title of newTab to "WebMng (5173)"
    end tell
    
    -- Tab 4: HomePage
    tell mainWindow
        set newTab to do script "cd \"$PROJECT_ROOT/AllToDo-HomePage\" && npm run dev"
        set custom title of newTab to "HomePage (5177)"
    end tell
    
end tell
EOF

echo "✨  4개의 서비스가 새 터미널 창에서 실행되었습니다."
echo "---------------------------------------------------------"
echo "📋  실행된 서비스:"
echo "   1. 🔙 Backend  : http://localhost:8000"
echo "   2. 📱 WebApp   : http://localhost:5175"
echo "   3. 🖥️ WebMng   : http://localhost:5173"
echo "   4. 🏠 HomePage : http://localhost:5177"
echo ""
echo "🛑  종료하려면 다음 명령어를 실행하세요:"
echo "   ./dev-begin.sh -end"
echo "---------------------------------------------------------"
