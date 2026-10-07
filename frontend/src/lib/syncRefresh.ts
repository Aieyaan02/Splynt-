// Track only snapshots successfully shown in the dashboard. Failed reads stay retryable.
export function syncRefresh() {
    let displayed: string | null = null;
    let pending: Promise<void> | null = null;
    return {
        run(timestamp: string | null, refresh: () => Promise<void>): Promise<void> {
            if (pending) return pending;
            if (!timestamp || timestamp === displayed) return Promise.resolve();
            pending = Promise.resolve().then(refresh).then(() => { displayed = timestamp; })
                .finally(() => { pending = null; });
            return pending;
        }
    };
}
