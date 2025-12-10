import { v4 as uuidv4 } from 'uuid';

const UUID_KEY = 'alltodo_user_uuid';

export const getOrCreateUUID = (): string => {
    let uuid = localStorage.getItem(UUID_KEY);
    if (!uuid) {
        uuid = uuidv4();
        localStorage.setItem(UUID_KEY, uuid);
        // TODO: Sync with server here
        console.log('New UUID generated:', uuid);
    } else {
        console.log('Existing UUID found:', uuid);
    }
    return uuid;
};

export const getUserUUID = (): string | null => {
    return localStorage.getItem(UUID_KEY);
};
