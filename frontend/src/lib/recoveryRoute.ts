export type RecoveryPath = "/forgot-password" | "/reset-password" | "/verify-email";
export function recoveryRoute(route: string): { path: RecoveryPath; token: string | null } | null {
    const separator = route.indexOf("?");
    const path = separator < 0 ? route : route.slice(0, separator);
    if (path !== "/forgot-password" && path !== "/reset-password" && path !== "/verify-email") return null;
    const values = new URLSearchParams(separator < 0 ? "" : route.slice(separator + 1)).getAll("token");
    const token = values.length === 1 && /^[A-Za-z0-9_-]{43}$/.test(values[0]) ? values[0] : null;
    return { path, token };
}
