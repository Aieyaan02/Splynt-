import { useEffect, useState } from "react";

import { AuthPage } from "./components/AuthPage";
import { Dashboard } from "./components/Dashboard";

import {
    accountApi,
    clearAccessToken,
    getAccessToken
} from "./lib/api";

import type { Account } from "./types";

export default function App() {
    const [account, setAccount] =
        useState<Account | null>(null);

    const [checkingSession, setCheckingSession] =
        useState(true);

    useEffect(() => {
        let cancelled = false;

        async function restoreSession() {
            if (!getAccessToken()) {
                setCheckingSession(false);
                return;
            }

            try {
                const currentAccount =
                    await accountApi.getCurrent();

                if (!cancelled) {
                    setAccount(currentAccount);
                }
            } catch {
                clearAccessToken();
            } finally {
                if (!cancelled) {
                    setCheckingSession(false);
                }
            }
        }

        void restoreSession();

        return () => {
            cancelled = true;
        };
    }, []);

    if (checkingSession) {
        return (
            <main className="loading-screen">
                <div className="brand">
                    <span className="brand-mark">S</span>
                    <span>Splynt</span>
                </div>
                <p>Restoring your workspace…</p>
            </main>
        );
    }

    if (!account) {
        return (
            <AuthPage
                onAuthenticated={setAccount}
            />
        );
    }

    return (
        <Dashboard
            account={account}
            onLogout={() => setAccount(null)}
        />
    );
}