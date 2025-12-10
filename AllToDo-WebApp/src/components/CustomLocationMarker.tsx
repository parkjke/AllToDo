import React, { useEffect, useState } from 'react';
import { MapPin } from 'lucide-react';
import { CustomOverlayMap, useMap } from 'react-kakao-maps-sdk';
import { useSettings, type PinSize } from '../context/SettingsContext';

interface Location {
    lat: number;
    lng: number;
}

export const CustomLocationMarker = () => {
    const map = useMap();
    const { pinSize } = useSettings();
    const [position, setPosition] = useState<Location | null>(null);

    useEffect(() => {
        if (!map) return;

        if (navigator.geolocation) {
            // Initial fetch
            navigator.geolocation.getCurrentPosition((pos) => {
                const lat = pos.coords.latitude;
                const lng = pos.coords.longitude;
                setPosition({ lat, lng });
                map.setCenter(new kakao.maps.LatLng(lat, lng));
            });

            // Watch for updates
            const watchId = navigator.geolocation.watchPosition(
                (pos) => {
                    const lat = pos.coords.latitude;
                    const lng = pos.coords.longitude;
                    setPosition({ lat, lng });
                },
                (err) => console.error(err),
                { enableHighAccuracy: true, maximumAge: 0, timeout: 5000 }
            );

            return () => navigator.geolocation.clearWatch(watchId);
        }
    }, [map]);

    if (!position) return null;

    // Size configuration based on settings
    const getSizeEndpoint = (size: PinSize) => {
        switch (size) {
            case 'small': return 'w-4 h-4';
            case 'medium': return 'w-8 h-8';
            case 'large': return 'w-16 h-16'; // Very large as requested "Sang"
            default: return 'w-8 h-8';
        }
    };

    // Core circle size
    const sizeClasses = getSizeEndpoint(pinSize);

    return (
        <CustomOverlayMap position={position} yAnchor={1} zIndex={100}>
            <div className={`relative ${sizeClasses} transform -translate-y-1/2 drop-shadow-lg cursor-pointer`}>
                {/* Pulse for current location */}
                <div className="absolute inset-x-0 bottom-0 top-1 bg-green-500 rounded-full animate-ping opacity-30 z-0"></div>

                {/* Main Teardrop Marker */}
                <MapPin className="relative w-full h-full text-green-500 fill-green-500 z-10 drop-shadow-md" fill="currentColor" strokeWidth={1.5} />

                {/* Optional: Center dot */}
                <div className="absolute top-[35%] left-1/2 -translate-x-1/2 -translate-y-1/2 w-1.5 h-1.5 bg-white rounded-full z-20" />
            </div>
        </CustomOverlayMap>
    );
};
