import React from 'react';
import { Bell, User, Navigation, Plus, Minus } from 'lucide-react';

interface MapControlsProps {
    onOpenMyInfo: () => void;
    map: kakao.maps.Map | undefined;
}

export const MapControls: React.FC<MapControlsProps> = ({ onOpenMyInfo, map }) => {

    const handleLocationClick = () => {
        if (navigator.geolocation) {
            navigator.geolocation.getCurrentPosition((position) => {
                if (!map) return;
                const lat = position.coords.latitude;
                const lng = position.coords.longitude;
                const newPos = new kakao.maps.LatLng(lat, lng);
                map.setCenter(newPos);
                map.setLevel(3); // Zoom level 3 is close
            });
        }
    };

    const handleZoomIn = () => {
        if (!map) return;
        map.setLevel(map.getLevel() - 1);
    };

    const handleZoomOut = () => {
        if (!map) return;
        map.setLevel(map.getLevel() + 1);
    };

    return (
        <div className="absolute top-4 right-4 z-[1000] flex flex-col items-end gap-4">
            {/* Top Row: Notifications & User */}
            <div className="flex items-center gap-3">
                <button
                    className="p-3 cursor-pointer bg-primary hover:bg-primary-hover transition-colors text-white shadow-lg shadow-primary/30 rounded-2xl"
                >
                    <Bell size={24} className="text-black" />
                </button>
                <button
                    className="p-3 cursor-pointer bg-primary hover:bg-primary-hover transition-colors text-white shadow-lg shadow-primary/30 rounded-2xl"
                    onClick={onOpenMyInfo}
                >
                    <User size={24} className="text-black" />
                </button>
            </div>

            {/* Column: Location & Zoom (Below User Icon) */}
            <div className="flex flex-col gap-3">
                <button
                    className="p-3 cursor-pointer bg-primary hover:bg-primary-hover transition-colors text-white shadow-lg shadow-primary/30 rounded-2xl"
                    onClick={handleLocationClick}
                >
                    <Navigation size={24} className="text-black" />
                </button>

                <div className="flex flex-col gap-1 shadow-lg shadow-primary/30 rounded-2xl overflow-hidden">
                    <button
                        className="p-3 cursor-pointer bg-primary hover:bg-primary-hover transition-colors text-white rounded-b-none"
                        onClick={handleZoomIn}
                    >
                        <Plus size={24} className="text-black" />
                    </button>
                    <div className="h-[1px] bg-black/20 w-full" />
                    <button
                        className="p-3 cursor-pointer bg-primary hover:bg-primary-hover transition-colors text-white rounded-t-none"
                        onClick={handleZoomOut}
                    >
                        <Minus size={24} className="text-black" />
                    </button>
                </div>
            </div>
        </div>
    );
};
