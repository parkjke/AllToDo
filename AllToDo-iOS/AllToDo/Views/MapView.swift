import SwiftUI
import MapKit
import SwiftData

struct MapView: View {
    @State private var locationManager = LocationManager()
    @State private var pulse: CGFloat = 1.0
    
    @Query private var tasks: [ToDoItem]
    
    public init() {}
    
    var body: some View {
        Map(coordinateRegion: $locationManager.region, showsUserLocation: true, annotationItems: tasks.filter { $0.location != nil }) { task in
            MapAnnotation(coordinate: CLLocationCoordinate2D(latitude: task.location!.latitude, longitude: task.location!.longitude)) {
                VStack {
                    Image(systemName: "mappin.circle.fill")
                        .foregroundColor(.red)
                        .font(.title)
                    Text(task.title)
                        .font(.caption)
                        .padding(4)
                        .background(.ultraThinMaterial)
                        .cornerRadius(4)
                }
            }
        }
            .ignoresSafeArea()
            .overlay(
                // Pulsing Circle Animation for Current Location
                ZStack {
                    if locationManager.location != nil {
                        Circle()
                            .stroke(Color.blue.opacity(0.5), lineWidth: 2)
                            .scaleEffect(pulse)
                            .opacity(2 - pulse)
                            .animation(
                                Animation.easeOut(duration: 1.5)
                                    .repeatForever(autoreverses: false),
                                value: pulse
                            )
                            .frame(width: 100, height: 100)
                            .onAppear {
                                pulse = 2.0
                            }
                    }
                }
            )
    }
}

#Preview {
    MapView()
}
