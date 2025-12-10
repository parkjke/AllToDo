import React, { createContext, useContext, useState, type ReactNode } from 'react';

export type PinSize = 'small' | 'medium' | 'large';

interface SettingsContextType {
    pinSize: PinSize;
    setPinSize: (size: PinSize) => void;
}

const SettingsContext = createContext<SettingsContextType | undefined>(undefined);

export const SettingsProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
    // Load from local storage or default to 'medium'
    const [pinSize, setPinSizeState] = useState<PinSize>(() => {
        const saved = localStorage.getItem('alltodo-settings-pinsize');
        return (saved as PinSize) || 'medium';
    });

    const setPinSize = (size: PinSize) => {
        setPinSizeState(size);
        localStorage.setItem('alltodo-settings-pinsize', size);
    };

    return (
        <SettingsContext.Provider value={{ pinSize, setPinSize }}>
            {children}
        </SettingsContext.Provider>
    );
};

export const useSettings = () => {
    const context = useContext(SettingsContext);
    if (!context) {
        throw new Error('useSettings must be used within a SettingsProvider');
    }
    return context;
};
