export interface ReadinessInput {
    lastSyncedAt: string | null;
    syncError: string | null;
    summary: { completeDays: number; orders: number; sufficientForVelocity: boolean; topProducts: unknown[] };
}
export function intelligenceReadiness(data: ReadinessInput, now = Date.now()) {
    const synced = data.lastSyncedAt === null ? NaN : Date.parse(data.lastSyncedAt);
    return [
        { label: "Recent, successful sales import", ready: Number.isFinite(synced) && synced <= now && now - synced <= 86_400_000 && !data.syncError, detail: "A successful import within the last 24 hours." },
        { label: "At least 14 complete days", ready: data.summary.completeDays >= 14, detail: `${data.summary.completeDays} complete days available.` },
        { label: "At least 30 included orders", ready: data.summary.orders >= 30, detail: `${data.summary.orders} included orders available.` },
        { label: "Recognized product sales", ready: data.summary.topProducts.length > 0 && data.summary.sufficientForVelocity, detail: "Sales must match catalog products before recommendations can use them." }
    ];
}
