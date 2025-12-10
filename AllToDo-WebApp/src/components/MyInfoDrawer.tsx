import React from 'react';
import { X, User, Settings, LogOut } from 'lucide-react';
import { useSettings, type PinSize } from '../context/SettingsContext';

interface MyInfoDrawerProps {
    isOpen: boolean;
    onClose: () => void;
}

export const MyInfoDrawer: React.FC<MyInfoDrawerProps> = ({ isOpen, onClose }) => {
    const { pinSize, setPinSize } = useSettings();

    const sizes: { label: string; value: PinSize }[] = [
        { label: 'Small', value: 'small' },
        { label: 'Medium', value: 'medium' },
        { label: 'Large', value: 'large' },
    ];

    return (
        <div
            className={`fixed inset-y-0 right-0 z-[2000] !bg-primary/80 backdrop-blur-md shadow-[-4px_0_20px_rgba(0,0,0,0.1)] transition-transform duration-300 ease-in-out transform ${isOpen ? 'translate-x-0' : 'translate-x-full'}`}
            style={{ width: '85%', maxWidth: '320px' }}
        >
            <div className="h-full flex flex-col p-6">
                <div className="flex justify-between items-center mb-8">
                    <h2 className="text-2xl font-bold text-gray-800">My Info</h2>
                    <button onClick={onClose} className="p-2 hover:bg-gray-100 rounded-full">
                        <X size={24} className="text-gray-500" />
                    </button>
                </div>

                <div className="flex flex-col items-center mb-8">
                    <div className="w-24 h-24 bg-primary/20 rounded-full flex items-center justify-center mb-4">
                        <User size={48} className="text-primary" />
                    </div>
                    <h3 className="text-xl font-bold text-gray-800">User Name</h3>
                    <p className="text-gray-500">user@example.com</p>
                </div>

                <div className="mb-8">
                    <div className="flex items-center gap-2 mb-4 text-gray-700 font-medium">
                        <Settings size={20} />
                        <span>Map Settings</span>
                    </div>

                    <div className="bg-gray-50 p-4 rounded-xl">
                        <p className="text-sm text-gray-500 mb-3">Location Pin Size</p>
                        <div className="grid grid-cols-3 gap-2">
                            {sizes.map((size) => (
                                <button
                                    key={size.value}
                                    onClick={() => setPinSize(size.value)}
                                    className={`py-2 px-1 text-sm rounded-lg border transition-all ${pinSize === size.value
                                        ? 'bg-primary text-white border-primary shadow-sm'
                                        : 'bg-white text-gray-600 border-gray-200 hover:border-primary/50'
                                        }`}
                                >
                                    {size.label}
                                </button>
                            ))}
                        </div>
                    </div>
                </div>

                <div className="mt-auto">
                    <button className="w-full flex items-center gap-4 p-4 hover:bg-red-50 rounded-xl transition-colors text-left text-red-600">
                        <LogOut size={20} />
                        <span className="font-medium">Sign Out</span>
                    </button>
                </div>
            </div>
        </div>
    );
};
