import { defineConfig } from 'vite'

export default defineConfig({
    server: {
        port: 5177, // 여기서 포트를 변경할 수 있습니다.
        host: true  // 네트워크 호스트 허용 (선택 사항)
    }
})
