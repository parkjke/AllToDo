package kr.alltodo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

// MARK: - 기본 테마 컬러 (Material3 Default)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// MARK: - AllToDo 브랜드 컬러 (Brand Colors)
/// 앱의 정체성을 나타내는 주요 색상들입니다.
val AllToDoGreen = Color(0xFF209933)      // 기본 브랜드 그린 (iOS와 동일한 20%, 60%, 20% 근사치)
val AllToDoDarkGreen = Color(0xFF1A661A)  // 다크 모드용 진한 그린 (가독성 확보)
val AllToDoLightGreen = Color(0xFF99FF99) // 강조/파동 효과용 연한 그린
val AllToDoRed = Color(0xFFDB143D)        // 삭제/경고용 레드
val AllToDoBlue = Color(0xFF1F8FFF)       // 알림/정보용 블루
val AllToDoBlack = Color(0xFF000000)
val AllToDoWhite = Color(0xFFFFFFFF)

// MARK: - 그레이 스케일 (Gray Scale Palette)
/// UI 요소의 계층 구조를 표현하기 위한 9단계 그레이 스케일입니다.
/// iOS 디자인 시스템과 동일한 명도를 기준으로 정의되었습니다.
val Gray9 = Color(0xFF212121) // 가장 어두운 회색
val Gray8 = Color(0xFF333333) // 강한 텍스트용
val Gray7 = Color(0xFF616161) // 중간 텍스트용
val Gray6 = Color(0xFF808080) // 보조 텍스트용
val Gray5 = Color(0xFF9E9E9E) // 플레이스홀더/비활성용
val Gray4 = Color(0xFFBDBDBD) // 약한 구분선용
val Gray3 = Color(0xFFCCCCCC) // 매우 약한 요소용
val Gray2 = Color(0xFFE0E0E0) // 배경 위 요소용
val Gray1 = Color(0xFFF2F2F2) // 가장 밝은 회색

/**
 * AllToDo 시맨틱 디자인 시스템
 * 각 컴포넌트 및 상황별 의미(Semantic)에 맞는 색상을 제공합니다.
 */
object AppColors {
    
    // MARK: - 할 일 만들기 레이어 시맨틱 (TodoLayer)
    object TodoLayer {
        @Composable
        fun background(isDark: Boolean): Color = 
            if (isDark) Black.copy(alpha = 0.9f) else White.copy(alpha = 0.9f)
            
        fun headerText(isDark: Boolean): Color = if (isDark) White else Gray8 // [FIX] Stronger visibility
        
        fun labelText(isDark: Boolean): Color = if (isDark) Gray5 else Gray8
        
        fun inputBackground(isDark: Boolean): Color = 
            if (isDark) Color(0xFF1A1A1A) else Gray1
            
        fun placeholderText(isDark: Boolean): Color = if (isDark) Gray7 else Gray6
        
        fun primaryText(isDark: Boolean): Color = if (isDark) White else Gray8
    }
    
    // MARK: - 장소 검색 오버레이 시맨틱 (Search)
    object Search {
        @Composable
        fun background(isDark: Boolean): Color = TodoLayer.background(isDark)
        
        /// 검색바 내부의 구성 요소(아이콘, 텍스트, 지우기 버튼) 통합 색상 (다크: Gray3, 라이트: Gray7)
        fun searchBarTint(isDark: Boolean): Color = if (isDark) Gray4 else Gray8 // [FIX] Strong visibility

        fun searchBarPlaceholder(isDark: Boolean): Color = if (isDark) Gray7 else Gray7
            
        fun resultName(isDark: Boolean): Color = if (isDark) Gray6 else Gray8
        
        fun resultAddress(isDark: Boolean): Color = if (isDark) Gray7 else Gray7
        
        fun distance(isDark: Boolean): Color = if (isDark) AllToDoDarkGreen else AllToDoGreen
        
        /// 검색 결과 리스트 및 항목 간 구분선 통합 색상 (두께로 구분 권장)
        fun divider(isDark: Boolean): Color = 
            if (isDark) White.copy(alpha = 0.1f) else Black.copy(alpha = 0.1f)
            
        fun borderGradientTop(isDark: Boolean): Color = 
            if (isDark) White.copy(alpha = 0.4f) else White.copy(alpha = 0.6f)

        /// 검색 결과 이동 시 표시되는 물결(Ripple) 색상
        fun ripple(isDark: Boolean): Color = 
            if (isDark) AllToDoLightGreen else AllToDoDarkGreen
    }
}

// 명칭 단축을 위한 별칭 (Optional)
private val White = AllToDoWhite
private val Black = AllToDoBlack
