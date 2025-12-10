import React, { useState, useEffect } from 'react';
import { Map, CustomOverlayMap } from 'react-kakao-maps-sdk';
import { useTodos } from '../context/TodoContext';
import { MapControls } from './MapControls';
import { CustomLocationMarker } from './CustomLocationMarker';
import { MapPin } from 'lucide-react';
import { useSettings, type PinSize } from '../context/SettingsContext';

const TodoMarkers = ({ map }: { map?: kakao.maps.Map }) => {
    const { todos, mapFocus } = useTodos();
    const { pinSize } = useSettings();

    // Effect to handle map focus changes (Center or Bounds)
    useEffect(() => {
        if (!map || !mapFocus) return;

        if (mapFocus.bounds) {
            const bounds = new kakao.maps.LatLngBounds();
            mapFocus.bounds.forEach(loc => bounds.extend(new kakao.maps.LatLng(loc.lat, loc.lng)));
            map.setBounds(bounds);
        } else if (mapFocus.center) {
            map.setCenter(new kakao.maps.LatLng(mapFocus.center.lat, mapFocus.center.lng));
            map.setLevel(3); // Zoom in for single item
        }
    }, [map, mapFocus]);

    // Size configuration
    const getSizeEndpoint = (size: PinSize) => {
        switch (size) {
            case 'small': return 'w-6 h-6';
            case 'medium': return 'w-8 h-8';
            case 'large': return 'w-12 h-12';
            default: return 'w-8 h-8';
        }
    };
    const sizeClasses = getSizeEndpoint(pinSize);

    return (
        <>
            {todos.filter(t => t.location).map(todo => {
                let colorClass = 'text-green-500 fill-green-500'; // Default Incomplete
                if (todo.source === 'external') colorClass = 'text-red-500 fill-red-500';
                else if (todo.completed) colorClass = 'text-blue-500 fill-blue-500';

                return (
                    <CustomOverlayMap
                        key={todo.id}
                        position={{ lat: todo.location!.lat, lng: todo.location!.lng }}
                        yAnchor={1} // Bottom anchor for tear drop
                        zIndex={1}
                    >
                        <div className={`relative ${sizeClasses} transform -translate-y-1/2 drop-shadow-md hover:scale-110 transition-transform cursor-pointer group`}
                            onClick={() => alert(todo.text)}
                        >
                            <MapPin className={`w-full h-full ${colorClass}`} fill="currentColor" />
                            {/* Label on hover */}
                            <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-1 px-2 py-1 bg-black/80 text-white text-xs rounded opacity-0 group-hover:opacity-100 whitespace-nowrap pointer-events-none">
                                {todo.text}
                            </div>
                        </div>
                    </CustomOverlayMap>
                );
            })}
        </>
    );
};

interface MapContainerProps {
    onOpenMyInfo: () => void;
}

export const MapContainer: React.FC<MapContainerProps> = ({ onOpenMyInfo }) => {
    // Default center (Seoul)
    const defaultCenter = { lat: 37.5665, lng: 126.9780 };
    const [map, setMap] = useState<kakao.maps.Map>();

    return (
        <div className="w-full h-screen absolute top-0 left-0 z-0">
            <Map
                center={defaultCenter}
                style={{ width: '100%', height: '100%' }}
                level={3}
                onCreate={setMap}
            >
                <CustomLocationMarker />
                <TodoMarkers map={map} />
            </Map>
            <MapControls onOpenMyInfo={onOpenMyInfo} map={map} />
        </div>
    );
};
