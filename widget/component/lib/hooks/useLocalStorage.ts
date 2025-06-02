import {useState} from "react";

function useLocalStorage<S>(
    key: string,
    initialValue: S | ((v: S) => S)
): [S, (value: S) => void]  {
    const [storedValue, setStoredValue] = useState<S>(() => {
        try {
            const item = window.localStorage.getItem(key);
            return item !== null ? JSON.parse(item) : initialValue;
        } catch (error) {
            console.error(error);
            return initialValue;
        }
    });

    const setValue = (value: S) => {
        try {
            const valueToStore =
                value instanceof Function ? value(storedValue) : value;
            setStoredValue(valueToStore);
            window.localStorage.setItem(key, JSON.stringify(valueToStore));
        } catch (error) {
            console.error(error);
        }
    };

    return [storedValue, setValue];
}

export default useLocalStorage;