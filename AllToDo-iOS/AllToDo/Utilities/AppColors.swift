import SwiftUI

// MARK: - AppColors 디자인 시스템
/// AllToDo 앱에서 공용으로 사용하는 색상 체계를 정의하는 확장입니다.
/// 브랜드 컬러, 공용 그레이 스케일, 그리고 컴포넌트별 시맨틱 컬러를 포함합니다.
extension Color {
    
    // MARK: - 브랜드 컬러 (Brand Colors)
    /// 앱의 정체성을 나타내는 주요 색상들입니다.
    static let allToDoGreen = Color(red: 0.20, green: 0.60, blue: 0.20)      // 기본 브랜드 그린
    static let allToDoDarkGreen = Color(red: 0.1, green: 0.4, blue: 0.1)     // 다크 모드용 진한 그린 (가독성 확보)
    static let allToDoLightGreen = Color(red: 0.6, green: 1.0, blue: 0.6)    // 강조/파동 효과용 연한 그린
    static let allToDoRed = Color(red: 0.86, green: 0.08, blue: 0.24)        // 삭제/경고용 레드
    static let allToDoBlue = Color(red: 0.12, green: 0.56, blue: 1.00)       // 알림/정보용 블루
    
    // MARK: - 그레이 스케일 (Gray Scale Palette)
    /// UI 요소의 계층 구조를 표현하기 위한 9단계 그레이 스케일입니다.
    /// 보편적인 명도(White 0.0~1.0)를 기준으로 정의되었습니다.
    static let gray9 = Color(white: 0.13) // #212121 (가장 어두운 회색)
    static let gray8 = Color(white: 0.20) // #333333 (강한 텍스트용)
    static let gray7 = Color(white: 0.38) // #616161 (중간 텍스트용)
    static let gray6 = Color(white: 0.50) // #808080 (보조 텍스트용)
    static let gray5 = Color(white: 0.62) // #9E9E9E (플레이스홀더/비활성용)
    static let gray4 = Color(white: 0.74) // #BDBDBD (약한 구분선용)
    static let gray3 = Color(white: 0.80) // #CCCCCC (매우 약한 요소용)
    static let gray2 = Color(white: 0.88) // #E0E0E0 (배경 위 요소용)
    static let gray1 = Color(white: 0.95) // #F2F2F2 (가장 밝은 회색)

    // MARK: - 할 일 만들기 레이어 시맨틱 컬러 (TodoLayer)
    /// '할 일 만들기' 창(CreateTodoLayer)에서 사용하는 전용 색상 규격입니다.
    struct TodoLayer {
        /// 창의 전체 배경색 (다크: 블랙 90%, 라이트: 시스템 배경 90%)
        static func background(isDark: Bool) -> Color {
            isDark ? Color.black.opacity(0.9) : Color.white.opacity(0.9)
        }
        
        /// 상단 헤더 타이틀 텍스트 색상
        static func headerText(isDark: Bool) -> Color {
            isDark ? .white : .black
        }
        
        /// 입력 필드 상단의 소제목(Label) 텍스트 색상
        static func labelText(isDark: Bool) -> Color {
            isDark ? .gray7 : .gray7
        }
        
        /// 입력창 내부의 박스 배경색 (다크: 짙은 회색, 라이트: 시스템 보조 배경색)
        static func inputBackground(isDark: Bool) -> Color {
            isDark ? Color(white: 0.1) : Color(UIColor.secondarySystemBackground)
        }
        
        /// 입력창 내부의 안내 문구(Placeholder) 색상
        static func placeholderText(isDark: Bool) -> Color {
            isDark ? .gray5 : .gray5
        }

        /// 일반적인 중요 텍스트 및 버튼 아이콘 색상
        static func primaryText(isDark: Bool) -> Color {
            isDark ? .white : .black
        }
    }
    
    // MARK: - 할 일 목록 레이어 시맨틱 컬러 (TodoList)
    struct TodoList {
        static func background(isDark: Bool) -> Color {
            isDark ? Color.black : Color.white // Non-Transparent as per Android [FIX]
        }
        
        static func primaryText(isDark: Bool) -> Color {
            isDark ? .white : .black
        }
        
        static func secondaryText(isDark: Bool) -> Color {
            isDark ? .gray6 : .gray7
        }
        
        static func iconTint(isDark: Bool) -> Color {
            isDark ? .gray4 : .gray8
        }
        
        static func itemBackground(color: Color, isDark: Bool) -> Color {
            isDark ? color.opacity(0.2) : color.opacity(0.1)
        }
    }
    
    // MARK: - 장소 검색 오버레이 시맨틱 컬러 (Search)
    /// 장소 검색창(SearchOverlay) 및 결과 리스트에서 사용하는 전용 색상 규격입니다.
    struct Search {
        /// 검색창 오버레이의 전체 배경색 (할 일 만들기 창과 동일한 투명도 유지)
        static func background(isDark: Bool) -> Color {
            TodoLayer.background(isDark: isDark)
        }
        
        /// 검색바 내부의 구성 요소(아이콘, 텍스트, 지우기 버튼) 통합 색상
        static func searchBarTint(isDark: Bool) -> Color {
            isDark ? .gray4 : .gray7
        }

        /// 검색어 미입력 시 표시되는 안내 문구 색상
        static func searchBarPlaceholder(isDark: Bool) -> Color {
            isDark ? .gray7 : .gray6
        }
        
        /// 검색 결과 리스트의 장소명(Bold) 색상
        static func resultName(isDark: Bool) -> Color {
            isDark ? .gray6 : .gray7
        }
        
        /// 검색 결과 리스트의 주소 텍스트 색상
        static func resultAddress(isDark: Bool) -> Color {
            isDark ? .gray7 : .gray6
        }
        
        /// 현재 위치로부터의 거리 표시 색상 (다크: 다크그린, 라이트: 브랜드그린)
        static func distance(isDark: Bool) -> Color {
            isDark ? .allToDoDarkGreen : .allToDoDarkGreen
        }
        
        /// 검색 결과 리스트 및 항목 간 구분선 통합 색상 (두께로 구분 권장)
        static func divider(isDark: Bool) -> Color {
            isDark ? Color.white.opacity(0.1) : Color.black.opacity(0.1)
        }

        /// 오버레이 최외곽 프리미엄 테두리 상단부 그라데이션 색상
        static func borderGradientTop(isDark: Bool) -> Color {
            isDark ? .white.opacity(0.4) : .white.opacity(0.6)
        }

        /// 검색 결과 이동 시 표시되는 물결(Ripple) 색상
        static func ripple(isDark: Bool) -> Color {
            isDark ? .allToDoLightGreen : .allToDoDarkGreen
        }
    }
    
    // MARK: - 캘린더 시맨틱 컬러 (Calendar)
    struct Calendar {
        static func background(isDark: Bool) -> Color {
            isDark ? Color.black : Color.white
        }
        
        static func primaryText(isDark: Bool) -> Color {
            isDark ? .white : .black
        }
        
        static func secondaryText(isDark: Bool) -> Color {
            isDark ? .gray6 : .gray7
        }
        
        static let pinBlue = Color.allToDoBlue
        static let pinGreen = Color.allToDoGreen
        static let pinRed = Color.allToDoRed
        
        static func dayCellBackground(isDark: Bool) -> Color {
            isDark ? Color.white.opacity(0.05) : Color.black.opacity(0.02)
        }
        
        static func todayBorder(isDark: Bool) -> Color {
            .allToDoGreen
        }
        
        static func selectedBackground(isDark: Bool) -> Color {
            .allToDoGreen.opacity(0.2)
        }
        
        static func divider(isDark: Bool) -> Color {
            isDark ? Color.white.opacity(0.1) : Color.black.opacity(0.1)
        }
    }
}

// MARK: - UIKit 호환성 확장
/// UIKit 기반 컴포넌트에서도 AllToDo 브랜드 컬러를 사용할 수 있도록 확장합니다.
extension UIColor {
    static let allToDoGreen = UIColor(red: 0.20, green: 0.60, blue: 0.20, alpha: 1.0)
    static let allToDoRed = UIColor(red: 0.86, green: 0.08, blue: 0.24, alpha: 1.0)
    static let allToDoBlue = UIColor(red: 0.12, green: 0.56, blue: 1.00, alpha: 1.0)
}
