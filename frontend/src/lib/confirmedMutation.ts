/** Retry a failed view refresh without repeating an already confirmed write. */
export function confirmedMutation() {
    let saved = false;
    let pending: Promise<void> | null = null;
    return {
        get saved() { return saved; },
        run(mutate: () => Promise<unknown>, refresh: () => void | Promise<void>, onSaved: () => void = () => {}) {
            if (pending) return pending;
            pending = (async () => {
                if (!saved) {
                    await mutate();
                    saved = true;
                    onSaved();
                }
                await refresh();
            })().finally(() => { pending = null; });
            return pending;
        }
    };
}
