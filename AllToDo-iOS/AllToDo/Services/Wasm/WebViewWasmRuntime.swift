import Foundation
import WebKit

import WebKit

@MainActor
final class WebViewWasmRuntime: NSObject, WasmRuntime, WKNavigationDelegate {
    private let webView: WKWebView
    private var pageLoadedContinuation: CheckedContinuation<Void, Error>?
    
    override init() {
        let config = WKWebViewConfiguration()
        // Allow access to local file resources if needed (not strictly needed for injected HTML but good practice)
        config.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")
        
        self.webView = WKWebView(frame: .zero, configuration: config)
        super.init()
        
        self.webView.navigationDelegate = self
        
        // Load Glue Code
        var glueScript = ""
        if let glueURL = Bundle.main.url(forResource: "wasm_glue", withExtension: "js"),
           let glueContent = try? String(contentsOf: glueURL) {
            glueScript = glueContent
        } else {
            print("[WASM_RUNTIME] ⚠️ Failed to find wasm_glue.js")
        }
        
        // Patch Glue for Global Scope
        // 1. Remove strict 'let' declaration to prevent shadowing/scoping issues.
        // Regex to match "let wasm_bindgen;" with possible whitespace
        if let regex = try? NSRegularExpression(pattern: "let\\s+wasm_bindgen\\s*;", options: []) {
            let range = NSRange(location: 0, length: glueScript.utf16.count)
            glueScript = regex.stringByReplacingMatches(in: glueScript, options: [], range: range, withTemplate: "// removed let declaration")
        }
        
        let html = """
        <html>
        <head></head>
        <body>
        <h1>WASM Host</h1>
        <script>
        // 0. Error Handler
        window.LAST_ERROR = null;
        window.onerror = function(message, source, lineno, colno, error) {
            window.LAST_ERROR = message + " at " + lineno + ":" + colno;
        };
        
        // 1. Declare global var explicitly
        var wasm_bindgen;
        </script>
        
        <script>
        // 2. Inject Glue
        \(glueScript)
        
        // 3. Explicitly attach to window (just in case)
        window.wasm_bindgen = wasm_bindgen;
        </script>
        
        <script>
        // Initializer
        async function loadWasm(base64Data) {
            if (window.LAST_ERROR) {
                return "LOAD_ERROR: " + window.LAST_ERROR;
            }
            if (typeof wasm_bindgen === 'undefined' || typeof wasm_bindgen !== 'function') {
                return "SETUP_ERROR: wasm_bindgen is missing (" + typeof wasm_bindgen + ")";
            }
            try {
                // Decode Base64
                var binaryString = window.atob(base64Data);
                var len = binaryString.length;
                var bytes = new Uint8Array(len);
                for (var i = 0; i < len; i++) {
                    bytes[i] = binaryString.charCodeAt(i);
                }
                
                await wasm_bindgen(bytes);
                return "OK";
            } catch (e) {
                return "ERROR: " + e.toString();
            }
        }
        
        function compress(points, minDist, angleThresh) {
            try {
                var int32Array = new Int32Array(points);
                var result = wasm_bindgen.compress_trajectory(int32Array, minDist, angleThresh);
                return Array.from(result);
            } catch (e) {
                return null;
            }
        }
        
        function cluster(points, cellSizeMeters) {
            try {
                var int32Array = new Int32Array(points);
                var result = wasm_bindgen.cluster_points(int32Array, cellSizeMeters);
                return Array.from(result);
            } catch (e) {
                return null;
            }
        }
        </script>
        </body>
        </html>
        """
        
        self.webView.loadHTMLString(html, baseURL: Bundle.main.bundleURL)
    }
    
    // MARK: - WKNavigationDelegate
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        pageLoadedContinuation?.resume(returning: ())
        pageLoadedContinuation = nil
        print("[WASM_RUNTIME] WebView Loaded")
    }
    
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        pageLoadedContinuation?.resume(throwing: error)
        pageLoadedContinuation = nil
        print("[WASM_RUNTIME] WebView Load Failed: \(error)")
    }
    
    private func ensurePageLoaded() async throws {
        if !webView.isLoading { return }
        try await withCheckedThrowingContinuation { continuation in
            self.pageLoadedContinuation = continuation
        }
    }

    func loadModule(_ wasmBytes: Data) async throws {
        // Ensure HTML is loaded first
        if webView.isLoading {
             print("[WASM_RUNTIME] Waiting for WebView...")
             var attempts = 0
             while webView.isLoading && attempts < 20 {
                 try await Task.sleep(nanoseconds: 100_000_000) // 100ms
                 attempts += 1
             }
        }
        
        // Debug: Check JS Error status first
        let debugScript = "return window.LAST_ERROR || (typeof wasm_bindgen);"
        if let debugRes = try? await webView.callAsyncJavaScript(debugScript, arguments: [:], in: nil, contentWorld: .page) as? String {
             print("[WASM_RUNTIME] JS Debug: \(debugRes)")
        }

        let base64Str = wasmBytes.base64EncodedString()
        
        // Use callAsyncJavaScript to handle the Promise returned by loadWasm
        let script = "return await loadWasm(base64Data);"
        let result = try await webView.callAsyncJavaScript(script, arguments: ["base64Data": base64Str], in: nil, contentWorld: .page)
        
        if let resStr = result as? String {
            if resStr.starts(with: "ERROR") || resStr.starts(with: "LOAD_ERROR") || resStr.starts(with: "SETUP_ERROR") {
                throw NSError(domain: "WebViewWasm", code: -1, userInfo: [NSLocalizedDescriptionKey: resStr])
            }
        }
        print("[WASM_RUNTIME] Init Success: \(result ?? "OK")")
    }
    
    func callFunction(_ name: String, params: [Int32]) async throws -> Int32 {
        return 0
    }
    
    func compressTrajectory(_ points: [Int32], minDistMeters: Double, angleThreshDeg: Double) async throws -> [Int32] {
        // Assume ready if called (managed by WasmManager)
        
        let pointsJson = points.description
        let js = "compress(\(pointsJson), \(minDistMeters), \(angleThreshDeg))"
        
        // evaluateJavaScript returns generic types (NSNumber for numbers)
        let rawResult = try await webView.evaluateJavaScript(js)
        
        if let array = rawResult as? [NSNumber] {
            return array.map { $0.int32Value }
        } else if let array = rawResult as? [Int] {
            return array.map { Int32($0) }
        } else if let array = rawResult as? [Double] {
             return array.map { Int32($0) }
        } else {
             print("[WASM_RUNTIME] Invalid Result Type: \(type(of: rawResult)) - \(rawResult ?? "nil")")
             throw NSError(domain: "WebViewWasm", code: -3, userInfo: [NSLocalizedDescriptionKey: "Invalid result format"])
        }
    }

    
    func clusterPoints(_ points: [Int32], cellSizeMeters: Double) async throws -> [Int32] {
        let pointsJson = points.description
        // Ensure 'cluster' function exists in JS. We need to add it to HTML.
        // Assuming we update HTML below or in this same edit.
        // Let's rely on the updated HTML string which I will modify now.
        
        let js = "cluster(\(pointsJson), \(cellSizeMeters))"
        let rawResult = try await webView.evaluateJavaScript(js)
        
        if let array = rawResult as? [NSNumber] {
             return array.map { $0.int32Value }
        } else if let array = rawResult as? [Int] {
             return array.map { Int32($0) }
        } else if let array = rawResult as? [Double] {
             return array.map { Int32($0) }
        } else {
             print("[WASM_RUNTIME] Invalid Cluster Result Type")
             return []
        }
    }
}
