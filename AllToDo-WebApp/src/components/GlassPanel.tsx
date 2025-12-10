import React from 'react';

interface GlassPanelProps {
    children: React.ReactNode;
    className?: string;
    onClick?: () => void;
}

export const GlassPanel: React.FC<GlassPanelProps> = ({ children, className = '', onClick }) => {
    return (
        <div
            className={`backdrop-blur-md bg-white/60 border border-white/40 shadow-lg rounded-2xl ${className}`}
            onClick={onClick}
        >
            {children}
        </div>
    );
};
