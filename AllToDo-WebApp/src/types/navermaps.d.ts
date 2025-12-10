declare namespace naver {
    namespace maps {
        class LatLng {
            constructor(lat: number, lng: number);
            lat(): number;
            lng(): number;
        }
        class Point {
            constructor(x: number, y: number);
        }
        class Map {
            setCenter(latlng: LatLng): void;
            setZoom(level: number): void;
            getZoom(): number;
            panTo(latlng: LatLng): void;
        }
    }
}
