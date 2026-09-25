import {
    useCallback,
    useEffect,
    useRef,
    useState
} from "react";

import { getAccessToken } from "../lib/api";

interface CloverAutoSyncProps {
    storeId: number | null;
    onSynchronized: () => Promise<void>;
}

interface ApiErrorResponse {
    message?: string;
    error?: string;
}

const SYNC_INTERVAL_MILLISECONDS = 30_000;

export function CloverAutoSync({
    storeId,
    onSynchronized
}: CloverAutoSyncProps) {
    const synchronizationInProgress = useRef(false);

    const [synchronizing, setSynchronizing] = useState(false);
    const [lastSynchronization, setLastSynchronization] =
        useState<Date | null>(null);
    const [synchronizationError, setSynchronizationError] =
        useState<string | null>(null);

    const synchronize = useCallback(async () => {
        const accessToken = getAccessToken();

        if (
            storeId === null
            || accessToken === null
            || synchronizationInProgress.current
        ) {
            return;
        }

        synchronizationInProgress.current = true;
        setSynchronizing(true);
        setSynchronizationError(null);

        try {
            const response = await fetch(
                `/api/stores/${storeId}`
                    + "/integrations/clover/sync",
                {
                    method: "POST",
                    headers: {
                        Accept: "application/json",
                        Authorization: `Bearer ${accessToken}`
                    }
                }
            );

            if (!response.ok) {
                let message =
                    `Clover synchronization failed (${response.status})`;

                try {
                    const body =
                        await response.json() as ApiErrorResponse;

                    message =
                        body.message
                        ?? body.error
                        ?? message;
                } catch {
                    // Keep the HTTP status message.
                }

                throw new Error(message);
            }

            await onSynchronized();
            setLastSynchronization(new Date());
        } catch (error) {
            setSynchronizationError(
                error instanceof Error
                    ? error.message
                    : "Clover synchronization failed"
            );
        } finally {
            synchronizationInProgress.current = false;
            setSynchronizing(false);
        }
    }, [storeId, onSynchronized]);

    useEffect(() => {
        const initialSynchronization = window.setTimeout(
            () => void synchronize(),
            1_000
        );

        const synchronizationInterval = window.setInterval(
            () => void synchronize(),
            SYNC_INTERVAL_MILLISECONDS
        );

        return () => {
            window.clearTimeout(initialSynchronization);
            window.clearInterval(synchronizationInterval);
        };
    }, [synchronize]);

    let status = "● Clover auto-sync enabled";

    if (synchronizing) {
        status = "● Synchronizing Clover inventory…";
    } else if (synchronizationError !== null) {
        status = `● Clover sync unavailable: ${synchronizationError}`;
    } else if (lastSynchronization !== null) {
        status =
            "● Clover auto-sync · Updated "
            + lastSynchronization.toLocaleTimeString(
                [],
                {
                    hour: "numeric",
                    minute: "2-digit",
                    second: "2-digit"
                }
            );
    }

    return (
        <p className="clover-sync-status">
            {status}
        </p>
    );
}
