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
        /// 창 전체 배경색 (0.9 투명도 적용)
        @Composable
        fun background(isDark: Boolean): Color = 
            if (isDark) Black.copy(alpha = 0.9f) else White.copy(alpha = 0.9f)
            
        /// '할 일 만들기' 헤더 텍스트 색상
        fun headerText(isDark: Boolean): Color = if (isDark) White else Gray8
        
        /// 입력 필드 상단 레이블(이름, 날짜 등) 텍스트 색상
        fun labelText(isDark: Boolean): Color = if (isDark) Gray5 else Gray8
        
        /// 입력창 내부 배경색
        fun inputBackground(isDark: Boolean): Color = 
            if (isDark) Color(0xFF1A1A1A) else Gray1
            
        /// 입력창 플레이스홀더 텍스트 색상
        fun placeholderText(isDark: Boolean): Color = if (isDark) Gray7 else Gray6
        
        /// 입력창 실제 입력 텍스트 및 기본 텍스트 색상
        fun primaryText(isDark: Boolean): Color = if (isDark) White else Gray8
    }
    
    // MARK: - 할 일 목록 레이어 시맨틱 (TodoList)
    object TodoList {
        /// 목록 내부 보조 아이콘(인원수 등)의 틴트 컬러
        fun iconTint(isDark: Boolean): Color = if (isDark) Gray5 else Gray5
        
        /// 목록 항목의 주요 이름 및 시간 텍스트 색상
        fun primaryText(isDark: Boolean): Color = if (isDark) Gray3 else Gray7
        
        /// 목록 항목 카드 뒤의 배경색
        fun background(isDark: Boolean): Color = if (isDark) Gray8 else Gray2
    }
    
    // MARK: - 장소 검색 오버레이 시맨틱 (Search)
    object Search {
        /// 검색창 전체 배경색 (할 일 레이어와 공유)
        @Composable
        fun background(isDark: Boolean): Color = TodoLayer.background(isDark)
        
        /// 검색바 내부 구성 요소(주요 아이콘, 텍스트)의 색상
        fun searchBarTint(isDark: Boolean): Color = if (isDark) Gray4 else Gray8 

        /// 검색바 힌트(찾을 곳) 텍스트 색상
        fun searchBarPlaceholder(isDark: Boolean): Color = if (isDark) Gray7 else Gray7
            
        /// 검색 결과 목록의 장소 명칭 색상
        fun resultName(isDark: Boolean): Color = if (isDark) Gray6 else Gray8
        
        /// 검색 결과 목록의 상세 주소 색상
        fun resultAddress(isDark: Boolean): Color = if (isDark) Gray7 else Gray7
        
        /// 검색 결과 목록의 거리 표시 색상 (브랜드 컬러 활용)
        fun distance(isDark: Boolean): Color = if (isDark) AllToDoDarkGreen else AllToDoGreen
        
        /// 검색 결과 항목 간 구분선 색상
        fun divider(isDark: Boolean): Color = 
            if (isDark) White.copy(alpha = 0.1f) else Black.copy(alpha = 0.1f)
            
        /// 검색 창 상단 테두리 그라데이션 시작 색상
        fun borderGradientTop(isDark: Boolean): Color = 
            if (isDark) White.copy(alpha = 0.4f) else White.copy(alpha = 0.6f)

        /// 클릭 시 발생하는 시각적 피드백(Ripple) 색상
        fun ripple(isDark: Boolean): Color = 
            if (isDark) AllToDoLightGreen else AllToDoDarkGreen
    }

    // MARK: - 지도 테마 시맨틱 (Map)
    object Map {
        /// 구글 맵 스타일 리소스 ID (다크: R.raw.google_map_dark_style, 라이트: null)
        fun googleStyleRes(isDark: Boolean): Int? = 
            if (isDark) kr.alltodo.R.raw.google_map_dark_style else null
    }

    // MARK: - 내 정보 창 시맨틱 (UserProfile)
    object UserProfile {
        /// 창 전체 배경색
        fun background(isDark: Boolean): Color = if (isDark) Color(0xFF1E1E1E) else Gray2
        
        /// 주요 텍스트 및 기본 콘텐츠 색상
        fun content(isDark: Boolean): Color = if (isDark) White else Gray8
        
        /// 항목 간 경계 구분선 색상
        fun divider(isDark: Boolean): Color = content(isDark).copy(alpha = 0.2f)
        
        /// 상단 보조 버튼(핀 보관함, 경로추적)의 배경색
        fun subButtonBackground(isDark: Boolean): Color = if (isDark) Gray7.copy(alpha = 0.3f) else White.copy(alpha = 0.5f)
        
        /// 상단 보조 버튼 내부 아이콘 및 텍스트 색상
        fun subButtonContent(isDark: Boolean): Color = if (isDark) White else Gray8
        
        /// 프로필 기본 아이콘의 원형 배경색
        fun profileIconBackground(isDark: Boolean): Color = if (isDark) Gray7.copy(alpha = 0.2f) else Gray3.copy(alpha = 0.3f)
        
        /// '설정', '지도 서비스' 등 섹션 헤더 텍스트 색상
        fun settingHeader(isDark: Boolean): Color = if (isDark) Gray5 else Gray8
        
        /// 선택된 칩(글꼴 크기 등)의 배경색
        fun chipSelectedContainer(isDark: Boolean): Color = if (isDark) Gray6 else Gray5
        
        /// 선택되지 않은 칩의 배경색
        fun chipUnselectedContainer(isDark: Boolean): Color = if (isDark) Gray8 else Gray3.copy(alpha = 0.2f)
        
        /// 칩 내부의 레이블 텍스트 및 아이콘 색상
        fun chipText(isDark: Boolean): Color = if (isDark) White else Gray8
    }

    // MARK: - 캘린더 창 시맨틱 (Calendar)
    object Calendar {
        /// 창 전체 배경색
        @Composable
        fun background(isDark: Boolean): Color = TodoLayer.background(isDark)

        /// 헤더(연/월) 텍스트 색상
        fun headerText(isDark: Boolean): Color = if (isDark) White else Gray8

        /// 요일 및 날짜 기본 텍스트 색상
        fun primaryText(isDark: Boolean): Color = if (isDark) White else Gray8

        /// 보조 텍스트 (다른 달의 날짜 등)
        fun secondaryText(isDark: Boolean): Color = Gray6

        /// 구분선 색상
        fun divider(isDark: Boolean): Color = 
            if (isDark) White.copy(alpha = 0.2f) else Black.copy(alpha = 0.1f)

        /// 오늘 날짜 강조 테두리 색상
        fun todayBorder(isDark: Boolean): Color = AllToDoGreen

        /// 선택된 날짜 배경/강조 색상
        fun selectedBackground(isDark: Boolean): Color = 
            if (isDark) AllToDoDarkGreen.copy(alpha = 0.3f) else AllToDoLightGreen.copy(alpha = 0.3f)
            
        // 핀 컬러 (기존 브랜드 컬러 활용)
        val pinBlue = AllToDoBlue
        val pinGreen = AllToDoGreen
        val pinRed = AllToDoRed
    }
}

// 명칭 단축을 위한 별칭 (Optional)
private val White = AllToDoWhite
private val Black = AllToDoBlack
