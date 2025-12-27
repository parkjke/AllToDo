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
    
    static var backgroundReentryThreshold: TimeInterval {
        #if DEBUG
        return 5.0
        #else
        return 540.0 // 9 minutes
        #endif
    }
    
    static var launchAnimationDelay: TimeInterval {
        #if DEBUG
        return 3.0 // User Request: 3 seconds for Dev
        #else
        return 1.0 // User Request: 1 second for Release
        #endif
    }
}
