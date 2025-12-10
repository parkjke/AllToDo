import React, { useState, useRef, useEffect } from 'react';
import { useTodos } from '../context/TodoContext';
import { ListTodo, MapPin, Grip } from 'lucide-react';
import { GlassPanel } from './GlassPanel';

interface TopLeftTodoProps {
    onOpen: () => void;
}

export const TopLeftTodo: React.FC<TopLeftTodoProps> = ({ onOpen }) => {
    const { todos, focusOnMap } = useTodos();
    const activeTodos = todos.filter(t => !t.completed).slice(0, 3);
    const todosWithLocation = todos.filter(t => t.location && !t.completed);

    // Cycle state
    const [cycleIndex, setCycleIndex] = useState(0);

    // Resizing State
    const [size, setSize] = useState({ width: 260, height: 'auto' as number | 'auto' });
    const resizingRef = useRef(false);

    // Stats calculation
    const redCount = todos.filter(t => t.source === 'external' && !t.completed).length;
    // Blue = Local source AND has location
    const blueCount = todos.filter(t => t.source === 'local' && t.location && !t.completed).length;
    // Black = Local source AND no location
    const blackCount = todos.filter(t => t.source === 'local' && !t.location && !t.completed).length;

    // Handle Resize (Mouse & Touch)
    useEffect(() => {
        const handleMove = (clientX: number, clientY: number) => {
            if (!resizingRef.current) return;
            const newWidth = Math.max(200, Math.min(600, clientX - 16));
            const newHeight = Math.max(100, Math.min(800, clientY - 16));
            setSize({ width: newWidth, height: newHeight });
        };

        const handleMouseMove = (e: MouseEvent) => handleMove(e.clientX, e.clientY);
        const handleTouchMove = (e: TouchEvent) => {
            if (e.touches.length > 0) {
                // Prevent scrolling while resizing
                if (resizingRef.current) {
                    // e.preventDefault(); // Caution with passive listeners
                }
                handleMove(e.touches[0].clientX, e.touches[0].clientY);
            }
        };

        const handleEnd = () => {
            resizingRef.current = false;
            document.body.style.cursor = 'default';
        };

        window.addEventListener('mousemove', handleMouseMove);
        window.addEventListener('mouseup', handleEnd);
        window.addEventListener('touchmove', handleTouchMove);
        window.addEventListener('touchend', handleEnd);

        return () => {
            window.removeEventListener('mousemove', handleMouseMove);
            window.removeEventListener('mouseup', handleEnd);
            window.removeEventListener('touchmove', handleTouchMove);
            window.removeEventListener('touchend', handleEnd);
        };
    }, []);

    const startResizing = (e: React.MouseEvent | React.TouchEvent) => {
        // Stop bubbling to prevent opening the drawer
        e.stopPropagation();
        resizingRef.current = true;
        document.body.style.cursor = 'se-resize';
    };

    const handleMapCycle = (e: React.MouseEvent) => {
        e.stopPropagation();
        if (todosWithLocation.length === 0) return;

        const nextIndex = (cycleIndex + 1) > todosWithLocation.length ? 0 : cycleIndex + 1;
        setCycleIndex(nextIndex);

        if (nextIndex === 0) {
            focusOnMap(todosWithLocation.map(t => t.location!));
        } else {
            const target = todosWithLocation[nextIndex - 1];
            if (target && target.location) {
                focusOnMap([target.location]);
            }
        }
    };

    return (
        <div
            className="absolute top-4 left-4 z-[1000] will-change-transform flex flex-col"
            style={{
                width: `${size.width}px`,
                height: size.height === 'auto' ? 'auto' : `${size.height}px`,
                transition: resizingRef.current ? 'none' : 'width 0.2s, height 0.2s'
            }}
        >
            <GlassPanel
                className="relative p-3 cursor-pointer !bg-green-700/80 backdrop-blur-md hover:!bg-green-700 transition-colors group text-black shadow-lg shadow-black/5 pr-6 h-full flex flex-col overflow-hidden"
                onClick={onOpen}
            >
                {/* Resize Handle (Bottom Right Corner) - Enhanced Touch Area */}
                <div
                    className="absolute right-0 bottom-0 w-12 h-12 cursor-se-resize flex items-center justify-center hover:bg-black/5 transition-colors z-20 group/handle rounded-tl-3xl active:bg-black/10 touch-none"
                    onMouseDown={startResizing}
                    onTouchStart={startResizing}
                    onClick={(e) => e.stopPropagation()}
                >
                    <Grip size={18} className="text-black/30 group-hover/handle:text-black/60 transition-colors" />
                </div>

                <div className="flex items-center gap-2 mb-2 shrink-0 relative">
                    <ListTodo size={22} className="text-green-800" />
                    <span className="font-bold text-base text-green-800">To Do</span>

                    {/* Map Cycle Button */}
                    <button
                        onClick={handleMapCycle}
                        className="ml-2 w-7 h-7 flex items-center justify-center bg-black/5 hover:bg-black/10 text-green-800 rounded-full transition-colors"
                        title="Cycle Map Locations"
                    >
                        <MapPin size={16} className="fill-current" />
                    </button>

                    {/* Stats - Moved to Right */}
                    <div className="ml-auto flex items-center gap-1.5">
                        <div className="w-5 h-5 rounded-full bg-red-100 border border-red-200 flex items-center justify-center shadow-sm">
                            <span className="text-[10px] font-bold text-red-600 leading-none">{redCount}</span>
                        </div>
                        <div className="w-5 h-5 rounded-full bg-blue-100 border border-blue-200 flex items-center justify-center shadow-sm">
                            <span className="text-[10px] font-bold text-blue-600 leading-none">{blueCount}</span>
                        </div>
                        <div className="w-5 h-5 rounded-full bg-white border border-black/10 flex items-center justify-center shadow-sm">
                            <span className="text-[10px] font-bold text-black leading-none">{blackCount}</span>
                        </div>
                    </div>
                </div>

                <div className="space-y-1.5 overflow-hidden flex-1">
                    {activeTodos.length === 0 ? (
                        <p className="text-xs text-black/60 italic">No active tasks</p>
                    ) : (
                        activeTodos.map(todo => (
                            <div key={todo.id} className="flex items-center justify-between gap-2 group/item">
                                <div className="flex items-center gap-2 overflow-hidden">
                                    <div className={`w-1.5 h-1.5 rounded-full shrink-0 ${todo.source === 'external' ? 'bg-red-500' :
                                        (todo.location ? 'bg-blue-500' : 'bg-black')
                                        }`} />
                                    <p className={`text-xs truncate font-medium ${todo.completed ? 'text-neutral-500 line-through' : 'text-black'}`}>
                                        {todo.text}
                                    </p>
                                </div>
                                {todo.location && (
                                    <button
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            focusOnMap([todo.location!]);
                                        }}
                                        className="p-1 hover:bg-black/10 rounded transition-colors"
                                    >
                                        <MapPin size={14} className="text-black" />
                                    </button>
                                )}
                            </div>
                        ))
                    )}
                    {todos.filter(t => !t.completed).length > 3 && (
                        <p className="text-[10px] text-black/60 pl-3.5">
                            + {todos.filter(t => !t.completed).length - 3} more
                        </p>
                    )}
                </div>
            </GlassPanel>
        </div>
    );
};
