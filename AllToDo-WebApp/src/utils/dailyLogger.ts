interface DailyLog {
    date: string; // YYYY-MM-DD
    location: { lat: number; lng: number };
    timestamp: number;
}

const LOG_KEY = 'alltodo_daily_logs';

export const logDailyActivity = (lat: number, lng: number) => {
    const today = new Date().toISOString().split('T')[0];
    const logsStr = localStorage.getItem(LOG_KEY);
    let logs: DailyLog[] = logsStr ? JSON.parse(logsStr) : [];

    // Check if already logged today
    const alreadyLogged = logs.some(log => log.date === today);

    if (!alreadyLogged) {
        const newLog: DailyLog = {
            date: today,
            location: { lat, lng },
            timestamp: Date.now(),
        };
        logs.push(newLog);
        localStorage.setItem(LOG_KEY, JSON.stringify(logs));
        console.log('Daily activity logged:', newLog);
    } else {
        console.log('Activity already logged for today.');
    }
};

export const getDailyLogs = (): DailyLog[] => {
    const logsStr = localStorage.getItem(LOG_KEY);
    return logsStr ? JSON.parse(logsStr) : [];
};
