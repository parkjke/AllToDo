package kr.alltodo.utils

/**
 * 한글 초성 추출 및 검색 관련 유틸리티
 */
object KoreanUtils {
    private val CHOSEONG = listOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )

    /**
     * 문자열에서 초성만 추출합니다.
     * 한글이 아닌 문자는 그대로 유지합니다.
     */
    fun extractChoseong(text: String): String {
        val sb = StringBuilder()
        for (char in text) {
            if (char in '\uAC00'..'\uD7A3') {
                val index = (char.toInt() - 0xAC00) / (21 * 28)
                sb.append(CHOSEONG[index])
            } else {
                sb.append(char)
            }
        }
        return sb.toString()
    }

    /**
     * 텍스트가 쿼리(초성 또는 일반 문자열)를 포함하는지 확인합니다.
     * 1. 일반 검색 (명칭 포함 여부)
     * 2. 초성 검색 (초성 일치 여부)
     */
    fun match(text: String, query: String): Boolean {
        if (query.isEmpty()) return true
        
        // 1. 일반 포함 검색
        if (text.contains(query, ignoreCase = true)) return true
        
        // 2. 초성 추출 및 검색
        val textChoseong = extractChoseong(text)
        val queryChoseong = extractChoseong(query)
        
        return textChoseong.contains(queryChoseong, ignoreCase = true)
    }
}
