import React, { useState } from 'react';
import { useTodos } from '../context/TodoContext';
import { X, Plus, MapPin, Clock, Users, Calendar } from 'lucide-react';

interface TodoDrawerProps {
    isOpen: boolean;
    onClose: () => void;
}

// Simulated Add Task Modal Component
const AddTaskModal = ({ onClose, onAdd }: { onClose: () => void; onAdd: (text: string, location?: any) => void }) => {
    const [text, setText] = useState('');
    const [participant, setParticipant] = useState('');
    const [locationSearch, setLocationSearch] = useState('');
    const [time, setTime] = useState('');

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (!text.trim()) return;

        // Mock logic for location
        const mockLocation = locationSearch ? { lat: 37.5665, lng: 126.9780 } : undefined;

        onAdd(text, mockLocation);
        onClose();
    };

    return (
        <div className="absolute inset-0 z-[2001] bg-black/20 backdrop-blur-sm flex items-end sm:items-center justify-center p-4 animate-in fade-in duration-200">
            <div className="bg-white/90 backdrop-blur-md w-full max-w-sm rounded-3xl p-5 shadow-2xl space-y-4 border border-white/40">
                <div className="flex justify-between items-center bg-black/5 p-1 rounded-full px-4 py-2">
                    <h3 className="font-bold text-black text-sm">New Task</h3>
                    <button onClick={onClose}><X size={18} className="text-black/50 hover:text-black" /></button>
                </div>

                <form onSubmit={handleSubmit} className="space-y-3">
                    <input
                        className="w-full bg-white/60 p-3 rounded-xl border border-black/10 focus:border-black/30 outline-none text-black placeholder:text-black/40 font-medium"
                        placeholder="What needs to be done?"
                        value={text}
                        onChange={e => setText(e.target.value)}
                        autoFocus
                    />

                    <div className="relative">
                        <Users size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-black/40" />
                        <input
                            className="w-full bg-white/60 p-2 pl-9 rounded-xl border border-black/10 outline-none text-sm text-black placeholder:text-black/40"
                            placeholder="Add Participant (Address Book)"
                            value={participant}
                            onChange={e => setParticipant(e.target.value)}
                        />
                    </div>

                    <div className="relative">
                        <MapPin size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-black/40" />
                        <input
                            className="w-full bg-white/60 p-2 pl-9 rounded-xl border border-black/10 outline-none text-sm text-black placeholder:text-black/40"
                            placeholder="Add Location (Map Search)"
                            value={locationSearch}
                            onChange={e => setLocationSearch(e.target.value)}
                        />
                    </div>

                    <div className="relative">
                        <Calendar size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-black/40" />
                        <input
                            type="datetime-local"
                            className="w-full bg-white/60 p-2 pl-9 rounded-xl border border-black/10 outline-none text-sm text-black placeholder:text-black/40"
                            value={time}
                            onChange={e => setTime(e.target.value)}
                        />
                    </div>

                    <button className="w-full bg-black text-white p-3 rounded-xl font-bold hover:bg-black/80 transition-colors mt-2">
                        Add to List
                    </button>
                </form>
            </div>
        </div>
    );
};

export const TodoDrawer: React.FC<TodoDrawerProps> = ({ isOpen, onClose }) => {
    const { todos, addTodo, toggleTodo, focusOnMap } = useTodos();
    const [showModal, setShowModal] = useState(false);
    const [sortMode, setSortMode] = useState<'time' | 'group'>('time');

    const formatTime = (timestamp: number) => {
        return new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    };

    const sortedTodos = [...todos].sort((a, b) => b.createdAt - a.createdAt);
    const locationTodos = sortedTodos.filter(t => t.location);
    const noLocationTodos = sortedTodos.filter(t => !t.location);

    const renderTodoItem = (todo: any) => (
        <div
            key={todo.id}
            className="flex items-center gap-3 p-4 hover:bg-black/5 transition-colors group relative bg-transparent"
        >
            <div className="relative flex items-center justify-center">
                <input
                    type="checkbox"
                    checked={todo.completed}
                    onChange={() => toggleTodo(todo.id)}
                    className="peer appearance-none w-6 h-6 rounded border-2 border-black/30 cursor-pointer checked:bg-black checked:border-black transition-all bg-white"
                    style={{
                        borderColor: todo.source === 'external' ? '#ef4444' : undefined,
                    }}
                />
                {/* Custom Checkmark implementation to ensure visibility on Android */}
                <svg
                    className="absolute w-4 h-4 text-white pointer-events-none opacity-0 peer-checked:opacity-100 transition-opacity"
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="3"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                >
                    <polyline points="20 6 9 17 4 12" />
                </svg>
            </div>

            <div className="flex-1 min-w-0 pr-24 flex items-center">
                <p className={`font-medium truncate text-lg transition-colors ${todo.completed ? '!text-gray-800 line-through' : '!text-black'}`}>
                    {todo.text}
                </p>
            </div>

            <div className={`absolute right-4 flex items-center gap-4 transition-colors ${todo.completed ? 'opacity-60' : ''}`}>
                {todo.location && (
                    <button
                        onClick={() => focusOnMap([todo.location!])}
                        className="p-1 hover:bg-black/10 rounded-full transition-colors"
                        disabled={todo.completed}
                    >
                        <MapPin size={28} className={todo.completed ? 'text-gray-800' : 'text-black hover:scale-110 transition-transform'} />
                    </button>
                )}
                <span className={`text-sm font-bold font-mono ${todo.completed ? 'text-gray-700' : 'text-black/50'}`}>
                    {formatTime(todo.createdAt)}
                </span>
            </div>
        </div>
    );

    return (
        <>
            {showModal && <AddTaskModal onClose={() => setShowModal(false)} onAdd={addTodo} />}

            <div
                className={`fixed inset-x-0 top-0 z-[2000] !bg-green-700/80 backdrop-blur-md rounded-b-3xl shadow-[0_4px_20px_rgba(0,0,0,0.1)] transition-transform duration-300 ease-in-out transform flex flex-col ${isOpen ? 'translate-y-0' : '-translate-y-full'}`}
                style={{ height: '70vh' }}
            >
                <div className="px-6 flex flex-col pt-6 shrink-0 z-10">
                    <div className="flex justify-between items-center mb-0 pb-4 border-b border-green-800/10">
                        <div className="flex items-center gap-3">
                            <h2 className="text-3xl font-extrabold !text-green-900 tracking-tight">To Do List</h2>
                            <button
                                onClick={() => setShowModal(true)}
                                className="p-2 bg-green-800 text-white rounded-full hover:scale-105 active:scale-95 transition-all shadow-lg"
                            >
                                <Plus size={24} />
                            </button>
                        </div>

                        <div className="flex items-center gap-2">
                            <button
                                onClick={() => setSortMode(prev => prev === 'time' ? 'group' : 'time')}
                                className="p-2 hover:bg-black/10 rounded-full transition-colors relative"
                            >
                                <Clock size={28} className={sortMode === 'time' ? 'text-green-800' : 'text-green-800/40'} />
                                {sortMode === 'group' && (
                                    <div className="absolute top-2 right-2 w-1.5 h-1.5 bg-green-800 rounded-full" />
                                )}
                            </button>
                            <button onClick={onClose} className="p-2 hover:bg-black/10 rounded-full transition-colors">
                                <X size={28} className="text-green-800" />
                            </button>
                        </div>
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto pb-8 relative">
                    {/* Added bg-white/40 to content area to ensure contrast */}
                    {todos.length === 0 ? (
                        <div className="text-center !text-black/50 mt-20 leading-relaxed font-medium">
                            <p>No tasks yet.</p>
                            <p className="text-sm">Click [+] to add one!</p>
                        </div>
                    ) : (
                        sortMode === 'time' ? (
                            sortedTodos.map(renderTodoItem)
                        ) : (
                            <div className="">
                                {locationTodos.length > 0 && (
                                    <div className="">
                                        {locationTodos.map(renderTodoItem)}
                                    </div>
                                )}
                                {locationTodos.length > 0 && noLocationTodos.length > 0 && (
                                    <div className="h-0" />
                                )}
                                {noLocationTodos.length > 0 && (
                                    <div className="">
                                        {noLocationTodos.map(renderTodoItem)}
                                    </div>
                                )}
                            </div>
                        )
                    )}
                </div>

                {/* Drag handle indicator */}
                <div className="w-16 h-1.5 bg-green-900/20 rounded-full mx-auto mb-4 shrink-0 absolute bottom-0 left-1/2 -translate-x-1/2 pointer-events-none" />
            </div>
        </>
    );
};
