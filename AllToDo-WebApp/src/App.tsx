import { useEffect, useState } from 'react';
import { MapContainer } from './components/MapContainer';
import { getOrCreateUUID } from './utils/uuid';
import { logDailyActivity } from './utils/dailyLogger';
import { TodoProvider } from './context/TodoContext';
import { TodoDrawer } from './components/TodoDrawer';
import { TopLeftTodo } from './components/TopLeftTodo';
import { MyInfoDrawer } from './components/MyInfoDrawer';
import { SettingsProvider } from './context/SettingsContext';
function App() {
  const [isTodoOpen, setIsTodoOpen] = useState(false);
  const [isMyInfoOpen, setIsMyInfoOpen] = useState(false);

  useEffect(() => {
    // 1. Initialize UUID
    getOrCreateUUID();

    // 2. Log Daily Activity (Location + Time)
    if ('geolocation' in navigator) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const { latitude, longitude } = position.coords;
          logDailyActivity(latitude, longitude);
        },
        (error) => {
          console.error('Error getting location for daily log:', error);
        }
      );
    }
  }, []);

  return (
    <SettingsProvider>
      <TodoProvider>
        <div className="relative w-full h-screen overflow-hidden bg-gray-100">
          <MapContainer onOpenMyInfo={() => setIsMyInfoOpen(true)} />

          {/* Top Left Todo Widget */}
          <TopLeftTodo onOpen={() => setIsTodoOpen(true)} />

          {/* Drawers */}
          <TodoDrawer isOpen={isTodoOpen} onClose={() => setIsTodoOpen(false)} />
          <MyInfoDrawer isOpen={isMyInfoOpen} onClose={() => setIsMyInfoOpen(false)} />
        </div>
      </TodoProvider>
    </SettingsProvider>
  );
}

export default App;
