import Foundation
import CoreLocation

class KakaoSearchService {
    private let REST_API_KEY = "622cc25924dcce684064c5794fbfe384"
    private let BASE_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"
    
    func searchKeyword(
        query: String,
        latitude: Double?,
        longitude: Double?,
        completion: @escaping ([SearchResult]?, Int) -> Void
    ) {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else {
            completion([], 0)
            return
        }
        
        var urlComponents = URLComponents(string: BASE_URL)!
        var queryItems = [
            URLQueryItem(name: "query", value: query),
            URLQueryItem(name: "size", value: "10")
        ]
        
        if let lat = latitude, let lng = longitude {
            queryItems.append(URLQueryItem(name: "y", value: "\(lat)"))
            queryItems.append(URLQueryItem(name: "x", value: "\(lng)"))
            queryItems.append(URLQueryItem(name: "radius", value: "20000")) // 20km radius
        }
        
        urlComponents.queryItems = queryItems
        
        var request = URLRequest(url: urlComponents.url!)
        request.httpMethod = "GET"
        request.setValue("KakaoAK \(REST_API_KEY)", forHTTPHeaderField: "Authorization")
        
        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                print(">>> [KakaoSearchService] Error: \(error.localizedDescription)")
                completion(nil, -1)
                return
            }
            
            let httpResponse = response as? HTTPURLResponse
            let statusCode = httpResponse?.statusCode ?? -1
            
            if statusCode != 200 {
                print(">>> [KakaoSearchService] Failed with status: \(statusCode)")
                completion(nil, statusCode)
                return
            }
            
            guard let data = data else {
                completion(nil, -1)
                return
            }
            
            do {
                // Kakao API returns "x" and "y" as strings.
                // Our SearchResult expects Doubles. We need to handle this.
                // Re-modeling SearchResult to handle string-to-double or using a decoder.
                
                let decoder = JSONDecoder()
                // Let's use a temporary struct for decoding if needed, 
                // but usually Kakao API returns coords as strings in keyword search.
                
                let decoderResponse = try decoder.decode(KakaoRawResponse.self, from: data)
                let results = decoderResponse.documents.map { raw in
                    SearchResult(
                        id: raw.id,
                        name: raw.place_name,
                        address: raw.address_name,
                        latitude: Double(raw.y) ?? 0.0,
                        longitude: Double(raw.x) ?? 0.0,
                        distance: raw.distance
                    )
                }
                completion(results, 200)
            } catch {
                print(">>> [KakaoSearchService] Decode Error: \(error)")
                completion(nil, -2)
            }
        }
        task.resume()
    }
}

// Intermediate struct to handle Kakao's string-based coordinates
struct KakaoRawResult: Codable {
    let id: String
    let place_name: String
    let address_name: String
    let x: String
    let y: String
    let distance: String?
}

struct KakaoRawResponse: Codable {
    let documents: [KakaoRawResult]
}
