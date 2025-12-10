import SwiftUI
import MapKit

struct PathHistoryView: View {
    var log: UserLog
    var onClose: () -> Void
    
    @State private var region: MKCoordinateRegion
    @State private var pathCoordinates: [CLLocationCoordinate2D] = []
    
    init(log: UserLog, onClose: @escaping () -> Void) {
        self.log = log
        self.onClose = onClose
        
        // Initial Region around midpoint
        self._region = State(initialValue: MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: log.latitude, longitude: log.longitude),
            span: MKCoordinateSpan(latitudeDelta: 0.01, longitudeDelta: 0.01)
        ))
    }
    
    var body: some View {
        ZStack(alignment: .topTrailing) {
            PathMapView(coordinates: pathCoordinates, region: $region)
                .ignoresSafeArea()
            
            // Close Button
            Button(action: onClose) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 30))
                    .foregroundColor(.white)
                    .shadow(radius: 2)
                    .padding()
            }
        }
        .onAppear {
            decodePath()
        }
    }
    
    private func decodePath() {
        guard let data = log.pathData else { return }
        
        do {
            let locations = try JSONDecoder().decode([LocationData].self, from: data)
            self.pathCoordinates = locations.map { 
                CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude) 
            }
            
            // Calculate bounding box
            if !pathCoordinates.isEmpty {
                let lats = pathCoordinates.map { $0.latitude }
                let lons = pathCoordinates.map { $0.longitude }
                
                let minLat = lats.min()!
                let maxLat = lats.max()!
                let minLon = lons.min()!
                let maxLon = lons.max()!
                
                let center = CLLocationCoordinate2D(
                    latitude: (minLat + maxLat) / 2,
                    longitude: (minLon + maxLon) / 2
                )
                let span = MKCoordinateSpan(
                    latitudeDelta: (maxLat - minLat) * 1.5, // Padding
                    longitudeDelta: (maxLon - minLon) * 1.5
                )
                
                self.region = MKCoordinateRegion(center: center, span: span)
            }
        } catch {
            print("Failed to decode path: \(error)")
        }
    }
}

struct PathMapView: UIViewRepresentable {
    var coordinates: [CLLocationCoordinate2D]
    @Binding var region: MKCoordinateRegion
    
    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.isPitchEnabled = false
        return mapView
    }
    
    func updateUIView(_ uiView: MKMapView, context: Context) {
        uiView.setRegion(region, animated: true)
        
        // Remove old overlays
        uiView.removeOverlays(uiView.overlays)
        
        // Add Polyline
        if !coordinates.isEmpty {
            let polyline = MKPolyline(coordinates: coordinates, count: coordinates.count)
            uiView.addOverlay(polyline)
        }
        
        // Add Start/End Annotations
        uiView.removeAnnotations(uiView.annotations)
        if let first = coordinates.first {
            let start = MKPointAnnotation()
            start.coordinate = first
            start.title = "Start"
            uiView.addAnnotation(start)
        }
        if let last = coordinates.last {
            let end = MKPointAnnotation()
            end.coordinate = last
            end.title = "End"
            uiView.addAnnotation(end)
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }
    
    class Coordinator: NSObject, MKMapViewDelegate {
        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let polyline = overlay as? MKPolyline {
                let renderer = MKPolylineRenderer(polyline: polyline)
                renderer.strokeColor = .blue
                renderer.lineWidth = 4
                return renderer
            }
            return MKOverlayRenderer(overlay: overlay)
        }
    }
}
