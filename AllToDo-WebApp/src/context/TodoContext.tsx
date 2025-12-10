import React, { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import { v4 as uuidv4 } from 'uuid';

export interface Todo {
    id: string;
    text: string;
    completed: boolean;
    createdAt: number;
    location?: {
        lat: number;
        lng: number;
    };
    source?: 'local' | 'external';
}

interface TodoContextType {
    todos: Todo[];
    addTodo: (text: string, location?: { lat: number; lng: number }) => void;
    toggleTodo: (id: string) => void;
    deleteTodo: (id: string) => void;
    mapFocus: { center?: { lat: number; lng: number }; bounds?: { lat: number; lng: number }[] } | null;
    focusOnMap: (locations: { lat: number; lng: number }[]) => void;
}

const TodoContext = createContext<TodoContextType | undefined>(undefined);

export const TodoProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
    const [todos, setTodos] = useState<Todo[]>(() => {
        const saved = localStorage.getItem('alltodo-data');
        if (saved) {
            const parsed = JSON.parse(saved);
            // Only return if there is data, otherwise load mock
            if (parsed.length > 0) return parsed;
        }

        // Initial Mock Data (Force load if empty or requested restoration)
        return [
            // 5 Location Tasks
            { id: '1', text: 'Grocery Run @ Market', completed: false, createdAt: Date.now() - 100000, source: 'local', location: { lat: 37.5665, lng: 126.9780 } },
            { id: '2', text: 'Client Meeting', completed: false, createdAt: Date.now() - 80000, source: 'external', location: { lat: 37.5700, lng: 126.9800 } },
            { id: '3', text: 'Gym Session', completed: true, createdAt: Date.now() - 60000, source: 'local', location: { lat: 37.5640, lng: 126.9750 } },
            { id: '4', text: 'Coffee with Friend', completed: false, createdAt: Date.now() - 40000, source: 'local', location: { lat: 37.5600, lng: 126.9700 } },
            { id: '5', text: 'Pick up Dry Cleaning', completed: false, createdAt: Date.now() - 20000, source: 'local', location: { lat: 37.5580, lng: 126.9680 } },

            // 3 Non-Location Tasks
            { id: '6', text: 'Read Documentation', completed: false, createdAt: Date.now() - 15000, source: 'local' },
            { id: '7', text: 'Update System OS', completed: false, createdAt: Date.now() - 10000, source: 'local' },
            { id: '8', text: 'Review PRs', completed: false, createdAt: Date.now() - 5000, source: 'external' },
        ];
    });

    const [mapFocus, setMapFocus] = useState<{ center?: { lat: number; lng: number }; bounds?: { lat: number; lng: number }[] } | null>(null);

    useEffect(() => {
        localStorage.setItem('alltodo-data', JSON.stringify(todos));
    }, [todos]);

    const addTodo = (text: string, location?: { lat: number; lng: number }) => {
        const newTodo: Todo = {
            id: uuidv4(),
            text,
            completed: false,
            createdAt: Date.now(),
            location,
            source: 'local'
        };
        setTodos(prev => [newTodo, ...prev]);
    };

    const toggleTodo = (id: string) => {
        setTodos(prev => prev.map(t => t.id === id ? { ...t, completed: !t.completed } : t));
    };

    const deleteTodo = (id: string) => {
        setTodos(prev => prev.filter(t => t.id !== id));
    };

    const focusOnMap = (locations: { lat: number; lng: number }[]) => {
        if (locations.length === 0) return;

        if (locations.length === 1) {
            setMapFocus({ center: locations[0] });
        } else {
            setMapFocus({ bounds: locations });
        }
    };

    return (
        <TodoContext.Provider value={{ todos, addTodo, toggleTodo, deleteTodo, mapFocus, focusOnMap }}>
            {children}
        </TodoContext.Provider>
    );
};

export const useTodos = () => {
    const context = useContext(TodoContext);
    if (!context) {
        throw new Error('useTodos must be used within a TodoProvider');
    }
    return context;
};
