import Foundation

struct AppConfig {
    static var logUrl: URL {
        #if DEBUG
        return URL(string: "http://175.194.163.56:8003/dev/logs")!
        #else
        // TODO: Replace with production URL when ready
        return URL(string: "http://175.194.163.56:8003/dev/logs")!
        #endif
    }
}
