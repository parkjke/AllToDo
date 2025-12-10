import SwiftUI
import MapKit
import CoreLocation

struct AppleMapView: UIViewRepresentable {
    @Binding var action: MapAction
    @Binding var rotation: Double
    @ObservedObject var locationManager: AppLocationManager
    var todoItems: [ToDoItem]
    var userLogs: [UserLog]
    @Binding var selectedItem: ToDoItem?
    @Binding var selectedClusterItems: [UnifiedMapItem]?
    var onLongTap: ((CLLocationCoordinate2D) -> Void)?
    var onUserLocationTap: (() -> Void)?
    var onDelete: ((ToDoItem) -> Void)?
    var onDeleteLog: ((UserLog) -> Void)?
    var onSelectLog: ((UserLog) -> Void)?
    
    func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.showsUserLocation = false // We will draw our own Red Pin
        mapView.showsCompass = false // [FIX] Hide System Compass
        mapView.isRotateEnabled = true
        mapView.isPitchEnabled = false
        
        // Long Press Gesture
        let longPress = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleLongPress(_:)))
        longPress.minimumPressDuration = 0.3 // Make it snappier (default is 0.5)
        mapView.addGestureRecognizer(longPress)
        
        return mapView
    }
    
    func updateUIView(_ uiView: MKMapView, context: Context) {
        context.coordinator.parent = self
        uiView.showsUserLocation = false // Force disable System Blue Dot
        
        // Handle Map Actions
        if action != .none {
            context.coordinator.handleAction(action, mapView: uiView)
            DispatchQueue.main.async {
                action = .none
            }
        }
        
        // Update Annotations
        context.coordinator.updateAnnotations(mapView: uiView, items: todoItems, userLocation: locationManager.currentLocation)
        
        // Launch Animation
        if context.coordinator.firstRender {
            context.coordinator.performLaunchAnimation(mapView: uiView, userLocation: locationManager.currentLocation)
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, MKMapViewDelegate {
        var parent: AppleMapView
        var firstRender = true
        var userAnnotation: UnifiedAnnotation?
        var lastItemIDs: Set<UUID> = []
        var lastLogIDs: Set<UUID> = []
        
        init(_ parent: AppleMapView) {
            self.parent = parent
        }
        
        // MARK: - Actions
        func handleAction(_ action: MapAction, mapView: MKMapView) {
            switch action {
            case .zoomIn:
                var region = mapView.region
                region.span.latitudeDelta /= 2.0
                region.span.longitudeDelta /= 2.0
                mapView.setRegion(region, animated: true)
            case .zoomOut:
                var region = mapView.region
                region.span.latitudeDelta *= 2.0
                region.span.longitudeDelta *= 2.0
                mapView.setRegion(region, animated: true)
            case .currentLocation:
                if let loc = parent.locationManager.currentLocation {
                    let region = MKCoordinateRegion(center: loc.coordinate, span: MKCoordinateSpan(latitudeDelta: 0.01, longitudeDelta: 0.01))
                    mapView.setRegion(region, animated: true)
                } else {
                    parent.locationManager.requestPermission()
                }
            case .rotateNorth:
                let camera = mapView.camera
                camera.heading = 0
                mapView.setCamera(camera, animated: true)
            case .none:
                break
            case .zoomToFit:
                mapView.showAnnotations(mapView.annotations, animated: true)
            }
        }
        
        @objc func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
            if gesture.state == .began {
                let mapView = gesture.view as! MKMapView
                let point = gesture.location(in: mapView)
                let coord = mapView.convert(point, toCoordinateFrom: mapView)
                
                // Feedback
                let generator = UIImpactFeedbackGenerator(style: .medium)
                generator.impactOccurred()
                
                parent.onLongTap?(coord)
            }
        }
        
        // Custom Button to hold data Independently
        class MapPinButton: UIButton {
            var items: [UnifiedMapItem] = []
            
            // Keep debug log
            override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
                print("DEBUG: MapPinButton Touched!")
                super.touchesBegan(touches, with: event)
            }
            
            // [FIX] Button itself must accept touches in the expanded area
            override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
                let largerBounds = self.bounds.insetBy(dx: -20, dy: -20)
                return largerBounds.contains(point)
            }
        }
        
        // [NEW] Custom Annotation View to enforce HitTest
        class TouchableAnnotationView: MKAnnotationView {
            override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
                // [FIX] Force-return the button if the touch is within our expanded bounds
                // This guarantees the button receives the touch event
                if self.point(inside: point, with: event) {
                    return self.subviews.first { $0 is UIButton } ?? super.hitTest(point, with: event)
                }
                return super.hitTest(point, with: event)
            }
            override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
                // [FIX] Expand hit area significantly to catch touches easily
                let largerBounds = self.bounds.insetBy(dx: -20, dy: -20)
                return largerBounds.contains(point)
            }
        }

        @objc func handlePinButtonTap(_ sender: UIButton) {
            // [FIX] Read data directly from Button, ignoring MapView
            guard let btn = sender as? MapPinButton else { return }
            print("DEBUG: ----- handlePinButtonTap \(btn.touchesBegan)")

            // Impact Feedback
            let generator = UIImpactFeedbackGenerator(style: .medium)
            generator.impactOccurred()
            
            let items = btn.items
            if items.isEmpty { return }
            
            // Debug Logs (User Requirement)
            if items.count > 1 {
                print("DEBUG: Button Tap Cluster (\(items.count) items)")
            } else if let first = items.first {
                switch first {
                case .todo(let todo): print("DEBUG: Button Tap ToDo: \(todo.title)")
                case .history: print("DEBUG: Button Tap History")
                default: break
                }
            }
            
            // Update State
            DispatchQueue.main.async {
                self.parent.selectedClusterItems = items
                self.parent.selectedItem = nil
            }
        }

        func mapViewDidChangeVisibleRegion(_ mapView: MKMapView) {
             let heading = mapView.camera.heading
             DispatchQueue.main.async {
                 self.parent.rotation = heading
             }
        }

        func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
             print("DEBUG: Raw didSelect called with annotation: \(String(describing: view.annotation))")
             // [User Request] Removed forced deselect to allow natural selection flow
             
             // 1. Cluster -> Show List
             if let cluster = view.annotation as? MKClusterAnnotation {
                 var items: [UnifiedMapItem] = []
                 for member in cluster.memberAnnotations {
                     if let unified = member as? UnifiedAnnotation, let item = unified.item {
                         items.append(item)
                     }
                 }
                 parent.selectedClusterItems = items
                 // Keep map position stable
                 return
             }
             
             // 2. Items
             if let unified = view.annotation as? UnifiedAnnotation, let item = unified.item {
                 switch item {
                 case .todo(let todo):
                     // Treat single ToDo as cluster item (Callout) to maintain uniform UI (Map, Time, Trash)
                     print("DEBUG: didSelect ToDo: \(todo.title)")
                     DispatchQueue.main.async {
                         self.parent.selectedClusterItems = [.todo(todo)]
                         self.parent.selectedItem = nil
                     }
                 case .history(let log):
                     // Show Callout (treat as cluster of 1) instead of immediate jump
                     print("DEBUG: didSelect History: \(log.startTime)")
                     DispatchQueue.main.async {
                         self.parent.selectedClusterItems = [.history(log)]
                         self.parent.selectedItem = nil
                     }
                 default:
                     break
                 }
             }
        }
        
        // ... (Update Annotations / Launch / ViewFor - Unchanged) ...


        
        func mapView(_ mapView: MKMapView, didDeselect view: MKAnnotationView) {
            // [FIX] Force reset clustering properties to ensure re-clustering works
            if view.annotation is UnifiedAnnotation {
                view.displayPriority = .defaultHigh
                view.clusteringIdentifier = "unified"
                view.layer.zPosition = 10
            }
        }
        
        // MARK: - Annotation Management
        func updateAnnotations(mapView: MKMapView, items: [ToDoItem], userLocation: CLLocation?) {
            // 1. Update User Location Pin (Red)
            if let userLoc = userLocation {
                if userAnnotation == nil {
                    userAnnotation = UnifiedAnnotation()
                    userAnnotation?.item = .userLocation
                    mapView.addAnnotation(userAnnotation!)
                }
                userAnnotation?.coordinate = userLoc.coordinate
            }
            
            // 2. Diffing Guard
            let currentItemIDs = Set(items.map { $0.id })
            let currentLogIDs = Set(parent.userLogs.map { $0.id })
            
            // Debug: Check Frequency
            // print("DEBUG: updateAnnotations Check - Items: \(currentItemIDs.count)")
            
            if currentItemIDs == lastItemIDs && currentLogIDs == lastLogIDs {
                return // No changes
            }
            
            lastItemIDs = currentItemIDs
            lastLogIDs = currentLogIDs
            
            // 3. Native Clustering - Just Add All Annotations
            // Remove old unified annotations (keep UserLocation)
            let oldAnnotations = mapView.annotations.filter { 
                $0 !== userAnnotation && !($0 is MKUserLocation) 
            }
            mapView.removeAnnotations(oldAnnotations)
            
            var newAnnotations: [MKAnnotation] = []
            
            // Add ToDos
            for item in items {
                let annotation = UnifiedAnnotation()
                annotation.item = .todo(item)
                annotation.coordinate = CLLocationCoordinate2D(latitude: item.location?.latitude ?? 0, longitude: item.location?.longitude ?? 0)
                // annotation.title = "Item" // Removed title
                newAnnotations.append(annotation)
            }
            
            // Add Logs
            for log in parent.userLogs {
                let annotation = UnifiedAnnotation()
                annotation.item = .history(log)
                annotation.coordinate = CLLocationCoordinate2D(latitude: log.latitude, longitude: log.longitude)
                // annotation.title = "History" // Removed title
                newAnnotations.append(annotation)
            }
            
            
            mapView.addAnnotations(newAnnotations)
        }
        
        // MARK: - Animation
        func performLaunchAnimation(mapView: MKMapView, userLocation: CLLocation?) {
            guard let userLoc = userLocation else { return }
            firstRender = false
            
            // 1. Start High (Zoomed Out)
            let startRegion = MKCoordinateRegion(
                center: userLoc.coordinate,
                span: MKCoordinateSpan(latitudeDelta: 0.5, longitudeDelta: 0.5)
            )
            mapView.setRegion(startRegion, animated: false)
            
            // 2. Animate to Close (Zoomed In)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                let endRegion = MKCoordinateRegion(
                    center: userLoc.coordinate,
                    span: MKCoordinateSpan(latitudeDelta: 0.01, longitudeDelta: 0.01)
                )
                MKMapView.animate(withDuration: 1.5, delay: 0, usingSpringWithDamping: 0.8, initialSpringVelocity: 0, options: .curveEaseInOut) {
                    mapView.setRegion(endRegion, animated: true)
                    
                    // Tilt
                    let camera = mapView.camera
                    camera.centerCoordinate = userLoc.coordinate
                    camera.pitch = 45
                    camera.heading = 0
                    camera.altitude = 1000
                    mapView.setCamera(camera, animated: true)
                }
            }
        }
        
        // MARK: - Delegate Methods
        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            if annotation is MKUserLocation { return nil }
            
            let isCluster = annotation is MKClusterAnnotation
            let identifier = isCluster ? "UnifiedCluster" : "UnifiedPin"
            
            var view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) as? TouchableAnnotationView
            if view == nil {
                view = TouchableAnnotationView(annotation: annotation, reuseIdentifier: identifier)
                view?.canShowCallout = false
                view?.displayPriority = isCluster ? .required : .defaultHigh
                view?.collisionMode = .circle
            }
            
            // [FIX] Only single items should cluster together
            if !isCluster {
                if view?.clusteringIdentifier != "unified" { view?.clusteringIdentifier = "unified" }
            } else {
                if view?.clusteringIdentifier != nil { view?.clusteringIdentifier = nil }
            }
            
            view?.annotation = annotation
            view?.layer.zPosition = isCluster ? 100 : 10
            
            configurePinView(view: view!, annotation: annotation)

            // Special handling for UserLocation callout (Nearby History)
            if annotation === userAnnotation {
                let centerInt = IntCoordinate.from(annotation.coordinate)
                let nearbyLogs = parent.userLogs.filter {
                    let locInt = IntCoordinate(lat: Int($0.latitude * 100_000), lng: Int($0.longitude * 100_000))
                    return centerInt.distance(to: locInt) < 1000
                }
                let items = nearbyLogs.map { UnifiedMapItem.history($0) }
                let historyView = ClusterListCallout(
                    items: items.sorted(by: { $0.date > $1.date }),
                    isCluster: true,
                    onDeleteToDo: { [weak self] item in self?.parent.onDelete?(item) },
                    onDeleteLog: { [weak self] log in self?.parent.onDeleteLog?(log) },
                    onSelectLog: { [weak self] log in self?.parent.onSelectLog?(log) }
                )
                
                let countVal = CGFloat(items.count)
                let height: CGFloat = countVal >= 4 ? 240 : (countVal * 60 + 10)
                
                injectSwiftUI(view: view!, swiftUIView: historyView, height: height)
            }
            
            return view
        }
        
        private func configurePinView(view: MKAnnotationView, annotation: MKAnnotation) {
            // 1. Reset
            view.subviews.forEach { $0.removeFromSuperview() }
            
            // 2. Setup Container & Placeholder
            let width: CGFloat = 40
            let height: CGFloat = 50 // Unified Height
            let size = CGSize(width: width, height: height)
            view.frame = CGRect(origin: .zero, size: size)
            view.centerOffset = CGPoint(x: 0, y: -height / 2) // [FIX] Restore Alignment
            view.isUserInteractionEnabled = true
            
            // [FIX] Assign transparent image to ensure MapKit respects frame/touches
            view.image = UIGraphicsImageRenderer(size: size).image { _ in
                UIColor.white.withAlphaComponent(0.05).setFill()
                UIRectFill(CGRect(origin: .zero, size: size))
            }
            
            // 3. MAIN BUTTON (Container)
            let btn = MapPinButton(type: .custom)
            btn.frame = CGRect(origin: .zero, size: size)
            btn.backgroundColor = .clear
            btn.tag = 999
            
            // 3a. Visuals INSIDE Button
            // Layer A: Base Image
            let imageView = UIImageView()
            imageView.frame = btn.bounds
            imageView.contentMode = .scaleAspectFit
            imageView.isUserInteractionEnabled = false 
            btn.addSubview(imageView)
            
            // Layer B: Content
            let label = UILabel()
            label.textAlignment = .center
            label.font = UIFont.systemFont(ofSize: 10, weight: .bold)
            label.textColor = .white
            label.isUserInteractionEnabled = false
            
            let contentFrame = (annotation is MKClusterAnnotation) ? btn.bounds : CGRect(x: 0, y: 0, width: width, height: height * 0.4)
            label.frame = contentFrame
            btn.addSubview(label)
            
            // Data Binding
            if let cluster = annotation as? MKClusterAnnotation {
                var items: [UnifiedMapItem] = []
                for member in cluster.memberAnnotations {
                    if let unified = member as? UnifiedAnnotation, let item = unified.item {
                        items.append(item)
                    }
                }
                btn.items = items // Inject Data
                
                var t=0, l=0, m=0
                for mem in cluster.memberAnnotations {
                    if let u = mem as? UnifiedAnnotation, let i = u.item {
                        switch i {
                        case .todo: t+=1
                        case .history,.userLocation: l+=1
                        case .serverMessage: m+=1
                        }
                    }
                }
                imageView.image = createPiePinImage(todos: t, logs: l, msgs: m)
                
            } else if let unified = annotation as? UnifiedAnnotation, let item = unified.item {
                btn.items = [item] // Inject Data
                
                switch item {
                case .todo(let todo):
                    let color = UIColor(red: 0.2, green: 0.8, blue: 0.2, alpha: 1.0)
                    imageView.image = createDiamondPinImage(color: color, iconName: nil, text: nil) 
                    print("DEBUG: !!!!! TODO \(todo.title ?? "None") location: \(todo.location)")

                    if let date = todo.dueDate {
                        let f = DateFormatter(); f.dateFormat = "H:mm"
                        label.text = f.string(from: date)
                    } else {
                        let config = UIImage.SymbolConfiguration(pointSize: 14, weight: .bold)
                        let icon = UIImage(systemName: "checkmark", withConfiguration: config)?.withTintColor(.white, renderingMode: .alwaysOriginal)
                        let iconView = UIImageView(image: icon)
                        iconView.frame = contentFrame
                        iconView.contentMode = .center
                        iconView.isUserInteractionEnabled = false
                        btn.addSubview(iconView)
                    }
                    
                case .history(let log):
                    let f = DateFormatter(); f.dateFormat = "H:mm"
                    label.text = f.string(from: log.startTime)
                    imageView.image = createDiamondPinImage(color: .red, iconName: nil, text: nil)

                case .serverMessage:
                    imageView.image = createDiamondPinImage(color: .blue, iconName: nil, text: nil)
                    let config = UIImage.SymbolConfiguration(pointSize: 14, weight: .bold)
                    let icon = UIImage(systemName: "envelope.fill", withConfiguration: config)?.withTintColor(.white, renderingMode: .alwaysOriginal)
                    let iconView = UIImageView(image: icon)
                    iconView.frame = contentFrame
                    iconView.contentMode = .center
                    iconView.isUserInteractionEnabled = false
                    btn.addSubview(iconView)
                    
                case .userLocation:
                     imageView.image = createDiamondPinImage(color: .red, iconName: nil, text: nil)
                     let config = UIImage.SymbolConfiguration(pointSize: 14, weight: .bold)
                     let icon = UIImage(systemName: "person.fill", withConfiguration: config)?.withTintColor(.white, renderingMode: .alwaysOriginal)
                     let iconView = UIImageView(image: icon)
                     iconView.frame = contentFrame
                     iconView.contentMode = .center
                     iconView.isUserInteractionEnabled = false
                }
            }
            
            btn.addTarget(self, action: #selector(handlePinButtonTap(_:)), for: .touchUpInside)
            view.addSubview(btn)
            
            print("DEBUG: !!!!! Pin Configured. Button Frame: \(btn.frame)")
        }

        // Helper: Pie Chart Pin Image Generator (Now with Pin Shape)
        private func createPiePinImage(todos: Int, logs: Int, msgs: Int) -> UIImage {
            let total = CGFloat(todos + logs + msgs)
            // No early return for 0, draw empty pin
            
            let width: CGFloat = 40
            let height: CGFloat = 50 // Unified Height
            let size = CGSize(width: width, height: height)
            
            return UIGraphicsImageRenderer(size: size).image { context in
                // 1. Draw Pin Shape (Background) - White Diamond
                let path = UIBezierPath()
                path.move(to: CGPoint(x: width / 2, y: 0))
                path.addLine(to: CGPoint(x: width, y: height * 0.4))
                path.addLine(to: CGPoint(x: width / 2, y: height))
                path.addLine(to: CGPoint(x: 0, y: height * 0.4))
                path.close()
                UIColor.white.setFill() 
                path.fill()
                // Optional: Thin border for definition
                UIColor(white: 0.9, alpha: 1.0).setStroke()
                path.lineWidth = 1
                path.stroke()

                // 2. Draw Pie Chart (Inside Head)
                // Center roughly at (20, 20) which is 40% height of 50.
                let center = CGPoint(x: width / 2, y: height * 0.4)
                let radius: CGFloat = 14 // Fit inside diamond head
                
                var startAngle: CGFloat = -CGFloat.pi / 2
                let colors: [(count: Int, color: UIColor)] = [
                    (todos, UIColor(red: 0.2, green: 0.8, blue: 0.2, alpha: 1.0)),
                    (logs, .red),
                    (msgs, .blue)
                ]
                
                // If Total > 0, draw slices
                if total > 0 {
                    for item in colors {
                        guard item.count > 0 else { continue }
                        let ratio = CGFloat(item.count) / total
                        let endAngle = startAngle + ratio * 2 * CGFloat.pi
                        
                        let slice = UIBezierPath()
                        slice.move(to: center)
                        slice.addArc(withCenter: center, radius: radius, startAngle: startAngle, endAngle: endAngle, clockwise: true)
                        slice.close()
                        item.color.setFill()
                        slice.fill()
                        
                        startAngle = endAngle
                    }
                } else {
                    // Empty grey circle? or just white pin?
                    // Draw grey circle placeholder
                    let circle = UIBezierPath(arcCenter: center, radius: radius, startAngle: 0, endAngle: 2 * .pi, clockwise: true)
                    UIColor(white: 0.9, alpha: 1).setFill()
                    circle.fill()
                }
                
                // 3. White inner circle for text overlap
                let innerRadius: CGFloat = 9
                let innerPath = UIBezierPath(ovalIn: CGRect(x: center.x - innerRadius, y: center.y - innerRadius, width: innerRadius * 2, height: innerRadius * 2))
                UIColor.white.setFill()
                innerPath.fill()
                
                // 4. Count Text
                let countText = Int(total) > 9 ? "9+" : "\(Int(total))"
                let attributes: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: 11, weight: .bold),
                    .foregroundColor: UIColor.black
                ]
                let string = NSString(string: countText)
                let textSize = string.size(withAttributes: attributes)
                string.draw(at: CGPoint(x: center.x - textSize.width/2, y: center.y - textSize.height/2), withAttributes: attributes)
            }
        }

        // Helper: Diamond Pin Image Generator
        private func createDiamondPinImage(color: UIColor, iconName: String? = nil, text: String? = nil, count: Int? = nil) -> UIImage {
            let width: CGFloat = 40
            let height: CGFloat = 50
            let size = CGSize(width: width, height: height)
            
            return UIGraphicsImageRenderer(size: size).image { context in
                // 1. Shape (Kite/Diamond)
                let path = UIBezierPath()
                path.move(to: CGPoint(x: width / 2, y: 0))
                path.addLine(to: CGPoint(x: width, y: height * 0.4))
                path.addLine(to: CGPoint(x: width / 2, y: height))
                path.addLine(to: CGPoint(x: 0, y: height * 0.4))
                path.close()
                color.setFill()
                path.fill()
                
                let contentCenter = CGPoint(x: width / 2, y: height * 0.4)
                
                // 2. Cluster Mode: White Circle + Count
                if let count = count {
                    // White Circle
                    let circleSize: CGFloat = 24
                    let circleRect = CGRect(x: contentCenter.x - circleSize/2, y: contentCenter.y - circleSize/2, width: circleSize, height: circleSize)
                    UIColor.white.setFill()
                    UIBezierPath(ovalIn: circleRect).fill()
                    
                    // Count Text
                    let countText = count > 9 ? "9+" : "\(count)"
                    let attributes: [NSAttributedString.Key: Any] = [
                        .font: UIFont.systemFont(ofSize: 12, weight: .bold),
                        .foregroundColor: color
                    ]
                    let string = NSString(string: countText)
                    let textSize = string.size(withAttributes: attributes)
                    let textRect = CGRect(
                        x: contentCenter.x - textSize.width / 2,
                        y: contentCenter.y - textSize.height / 2,
                        width: textSize.width,
                        height: textSize.height
                    )
                    string.draw(in: textRect, withAttributes: attributes)
                    
                } else {
                    // 3. Single Mode: Icon or Text
                    if let text = text {
                         // ... (unchanged)
                         let fontSize: CGFloat = text.count > 2 ? 10 : 13
                         let attributes: [NSAttributedString.Key: Any] = [
                             .font: UIFont.systemFont(ofSize: fontSize, weight: .bold),
                             .foregroundColor: UIColor.white
                         ]
                         let string = NSString(string: text)
                         let textSize = string.size(withAttributes: attributes)
                         string.draw(at: CGPoint(x: contentCenter.x - textSize.width/2, y: contentCenter.y - textSize.height/2), withAttributes: attributes)
                    } else if let iconName = iconName {
                         let config = UIImage.SymbolConfiguration(pointSize: 16, weight: .bold)
                         if let icon = UIImage(systemName: iconName, withConfiguration: config)?.withTintColor(.white, renderingMode: .alwaysOriginal) {
                             icon.draw(at: CGPoint(x: contentCenter.x - icon.size.width/2, y: contentCenter.y - icon.size.height/2))
                         }
                    }
                }
            }
        }

        // ... performLaunchAnimation ... UNCHANGED (omitted for brevity in replacement if possible, but context requires care)
        // I will copy existing performLaunchAnimation body or target replace better.
        // Actually, replacing from Line 282 is safer.
        // Wait, I need to update injectSwiftUI definition too (Line 416).
        // And callsites at 196, 242.
        
        func mapView(_ mapView: MKMapView, annotationView view: MKAnnotationView, calloutAccessoryControlTapped control: UIControl) {
            // Handled in SwiftUI
        }
        
        // didSelect logic is implemented above
        
        // Helper to inject SwiftUI into Callout
        private func injectSwiftUI<T: View>(view: MKAnnotationView, swiftUIView: T, height: CGFloat, width: CGFloat = 260) {
            view.detailCalloutAccessoryView = nil
            
            let controller = UIHostingController(rootView: swiftUIView)
            controller.view.translatesAutoresizingMaskIntoConstraints = false
            controller.view.backgroundColor = .clear 
            
            let containerView = UIView()
            containerView.translatesAutoresizingMaskIntoConstraints = false
            containerView.backgroundColor = .clear 
            containerView.addSubview(controller.view)
            
            NSLayoutConstraint.activate([
                controller.view.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
                controller.view.trailingAnchor.constraint(equalTo: containerView.trailingAnchor),
                controller.view.topAnchor.constraint(equalTo: containerView.topAnchor),
                controller.view.bottomAnchor.constraint(equalTo: containerView.bottomAnchor),
                
                containerView.widthAnchor.constraint(equalToConstant: width),
                containerView.heightAnchor.constraint(equalToConstant: height)
            ])
            
            view.detailCalloutAccessoryView = containerView
        }
    } 
} 

    // ... Classes (ToDoAnnotation, UnifiedMapItem) remain UNCHANGED ...

struct ClusterListCallout: View {
    var items: [UnifiedMapItem]
    var isCluster: Bool
    @AppStorage("popupFontSize") private var popupFontSize = 1
    
    var fontSize: CGFloat {
        switch popupFontSize {
        case 0: return 12
        case 1: return 15 // Default
        case 2: return 18
        default: return 15
        }
    }

    var onDeleteToDo: (ToDoItem) -> Void
    var onDeleteLog: (UserLog) -> Void
    var onSelectLog: (UserLog) -> Void
    
    var body: some View {
        VStack(spacing: 0) {
            if items.count == 1 {
                // Single Item - No Scroll
                itemRow(items[0], isSingle: true)
            } else {
                // Multiple - Scroll
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(items) { item in
                            itemRow(item, isSingle: false)
                            Divider()
                        }
                    }
                }
            }
        }
        .background(Color.clear)
    }
    
    // Constant widths for alignment
    private let iconWidth: CGFloat = 40
    
    @ViewBuilder
    func itemRow(_ item: UnifiedMapItem, isSingle: Bool) -> some View {
        HStack(spacing: 0) {
            // [Col 1] Map Icon / Spacer
            Group {
                if case .history(let log) = item, log.pathData != nil {
                    Button(action: {
                        onSelectLog(log)
                    }) {
                        Image(systemName: "map.fill")
                            .font(.system(size: fontSize))
                            .foregroundColor(isCluster ? .red : .black)
                            .frame(width: iconWidth, height: iconWidth)
                    }
                    .buttonStyle(.plain)
                } else {
                    // Placeholder Map Icon for Balance
                    Image(systemName: "map.fill")
                        .font(.system(size: fontSize))
                        .foregroundColor(.gray.opacity(0.3)) // Light Gray
                        .frame(width: iconWidth, height: iconWidth)
                }
            }
            
            Spacer()
            
            // [Col 2] Content (Time / Title)
            Group {
                switch item {
                case .todo(let todo):
                    VStack(alignment: .center, spacing: 2) {
                        Text(todo.title)
                            .font(.system(size: fontSize, weight: .bold))
                            .foregroundColor(.green)
                            .lineLimit(1)
                        if let date = todo.dueDate {
                            Text(date.formatted(date: .omitted, time: .shortened))
                                .font(.system(size: fontSize * 0.8))
                                .foregroundColor(.gray)
                        }
                    }
                case .history(let log):
                    Text(log.startTime.formatted(date: .omitted, time: .shortened))
                        .font(.system(size: fontSize))
                        .foregroundColor(isCluster ? .red : .black)
                case .serverMessage(let msg):
                    Text(msg)
                        .font(.system(size: fontSize))
                        .foregroundColor(isCluster ? .blue : .black)
                case .userLocation:
                    Text(Date().formatted(date: .omitted, time: .shortened))
                        .font(.system(size: fontSize, weight: .bold))
                        .foregroundColor(.red)
                }
            }
            .frame(maxWidth: .infinity) // Fill center
            
            Spacer()
            
            // [Col 3] Trash Icon
            Button(action: {
                switch item {
                case .todo(let todo): onDeleteToDo(todo)
                case .history(let log): onDeleteLog(log)
                case .serverMessage(_): break
                case .userLocation: break // No action
                }
            }) {
                if case .userLocation = item {
                    Image(systemName: "person.fill")
                        .font(.system(size: fontSize)) // Consistent size
                        .foregroundColor(.red)
                } else {
                    Image(systemName: "trash.fill")
                        .font(.system(size: fontSize))
                        .foregroundColor(.red)
                }
            }
            .frame(width: iconWidth, height: iconWidth)
            .buttonStyle(.plain)
        }
        .padding(.vertical, isSingle ? 0 : 4)
        .padding(.horizontal, 8)
        .frame(height: isSingle ? 50 : nil)
    }
}

