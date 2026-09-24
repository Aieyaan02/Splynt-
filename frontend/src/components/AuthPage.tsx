import { useState } from "react";
import type { FormEvent } from "react";

import {
    accountApi,
    authApi,
    setAccessToken
} from "../lib/api";

import type {
    Account,
    RegisterRequest
} from "../types";

interface AuthPageProps {
    onAuthenticated: (account: Account) => void;
}

const initialRegistration: RegisterRequest = {
    email: "",
    password: "",
    firstName: "",
    lastName: "",
    organizationName: "",
    organizationSlug: "",
    storeName: "",
    storeSlug: ""
};

export function AuthPage({
    onAuthenticated
}: AuthPageProps) {
    const [mode, setMode] =
        useState<"login" | "register">("login");

    const [loginEmail, setLoginEmail] = useState("");
    const [loginPassword, setLoginPassword] = useState("");

    const [registration, setRegistration] =
        useState<RegisterRequest>(initialRegistration);

    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState("");

    function changeMode(nextMode: "login" | "register") {
        setMode(nextMode);
        setError("");
        setSuccess("");
    }

    async function handleLogin(event: FormEvent) {
        event.preventDefault();

        setBusy(true);
        setError("");
        setSuccess("");

        try {
            const loginResponse = await authApi.login({
                email: loginEmail,
                password: loginPassword
            });

            setAccessToken(loginResponse.accessToken);

            const account = await accountApi.getCurrent();

            onAuthenticated(account);
        } catch (requestError) {
            setError(getErrorMessage(requestError));
        } finally {
            setBusy(false);
        }
    }

    async function handleRegistration(event: FormEvent) {
        event.preventDefault();

        setBusy(true);
        setError("");
        setSuccess("");

        try {
            await authApi.register(registration);

            setLoginEmail(registration.email);
            setLoginPassword("");
            setRegistration(initialRegistration);
            setMode("login");

            setSuccess(
                "Account created successfully. Sign in to continue."
            );
        } catch (requestError) {
            setError(getErrorMessage(requestError));
        } finally {
            setBusy(false);
        }
    }

    function updateRegistration(
        field: keyof RegisterRequest,
        value: string
    ) {
        setRegistration(current => ({
            ...current,
            [field]: value
        }));
    }

    return (
        <main className="auth-layout">
            <section className="auth-visual">
                <div className="brand brand-light">
                    <span className="brand-mark">S</span>
                    <span>Splynt</span>
                </div>

                <div className="hero-content">
                    <span className="eyebrow">
                        Inventory intelligence
                    </span>

                    <h1>
                        Know what is running low before it runs out.
                    </h1>

                    <p>
                        Secure inventory management, low-stock
                        recommendations, and store operations in one
                        focused workspace.
                    </p>

                    <div className="hero-features">
                        <article>
                            <strong>Multi-tenant</strong>
                            <span>
                                Secure organization and store isolation
                            </span>
                        </article>

                        <article>
                            <strong>Real-time ready</strong>
                            <span>
                                Designed for Clover synchronization
                            </span>
                        </article>

                        <article>
                            <strong>Actionable</strong>
                            <span>
                                Stock alerts with suggested reorder amounts
                            </span>
                        </article>
                    </div>
                </div>

                <p className="hero-footer">
                    Built for independent retailers.
                </p>
            </section>

            <section className="auth-form-side">
                <div className="auth-card">
                    <div className="mobile-brand brand">
                        <span className="brand-mark">S</span>
                        <span>Splynt</span>
                    </div>

                    <div className="auth-tabs">
                        <button
                            className={
                                mode === "login"
                                    ? "auth-tab active"
                                    : "auth-tab"
                            }
                            type="button"
                            onClick={() => changeMode("login")}
                        >
                            Sign in
                        </button>

                        <button
                            className={
                                mode === "register"
                                    ? "auth-tab active"
                                    : "auth-tab"
                            }
                            type="button"
                            onClick={() => changeMode("register")}
                        >
                            Create account
                        </button>
                    </div>

                    {error && (
                        <div className="message error-message">
                            {error}
                        </div>
                    )}

                    {success && (
                        <div className="message success-message">
                            {success}
                        </div>
                    )}

                    {mode === "login" ? (
                        <form
                            className="form-stack"
                            onSubmit={handleLogin}
                        >
                            <div className="form-heading">
                                <h2>Welcome back</h2>
                                <p>
                                    Sign in to manage your inventory.
                                </p>
                            </div>

                            <label>
                                Email address
                                <input
                                    type="email"
                                    value={loginEmail}
                                    autoComplete="email"
                                    onChange={event =>
                                        setLoginEmail(
                                            event.target.value
                                        )
                                    }
                                    required
                                />
                            </label>

                            <label>
                                Password
                                <input
                                    type="password"
                                    value={loginPassword}
                                    autoComplete="current-password"
                                    onChange={event =>
                                        setLoginPassword(
                                            event.target.value
                                        )
                                    }
                                    required
                                />
                            </label>

                            <button
                                className="button primary wide"
                                type="submit"
                                disabled={busy}
                            >
                                {busy ? "Signing in..." : "Sign in"}
                            </button>
                        </form>
                    ) : (
                        <form
                            className="form-stack"
                            onSubmit={handleRegistration}
                        >
                            <div className="form-heading">
                                <h2>Create your workspace</h2>
                                <p>
                                    Set up your organization and first
                                    store.
                                </p>
                            </div>

                            <div className="form-grid two-columns">
                                <label>
                                    First name
                                    <input
                                        value={registration.firstName}
                                        onChange={event =>
                                            updateRegistration(
                                                "firstName",
                                                event.target.value
                                            )
                                        }
                                        required
                                    />
                                </label>

                                <label>
                                    Last name
                                    <input
                                        value={registration.lastName}
                                        onChange={event =>
                                            updateRegistration(
                                                "lastName",
                                                event.target.value
                                            )
                                        }
                                        required
                                    />
                                </label>
                            </div>

                            <label>
                                Email address
                                <input
                                    type="email"
                                    value={registration.email}
                                    autoComplete="email"
                                    onChange={event =>
                                        updateRegistration(
                                            "email",
                                            event.target.value
                                        )
                                    }
                                    required
                                />
                            </label>

                            <label>
                                Password
                                <input
                                    type="password"
                                    value={registration.password}
                                    minLength={10}
                                    maxLength={72}
                                    autoComplete="new-password"
                                    onChange={event =>
                                        updateRegistration(
                                            "password",
                                            event.target.value
                                        )
                                    }
                                    required
                                />
                                <small>Use 10–72 characters.</small>
                            </label>

                            <label>
                                Organization name
                                <input
                                    value={
                                        registration.organizationName
                                    }
                                    placeholder="Grab n' GO"
                                    onChange={event =>
                                        updateRegistration(
                                            "organizationName",
                                            event.target.value
                                        )
                                    }
                                    required
                                />
                            </label>

                            <label>
                                Organization slug
                                <input
                                    value={
                                        registration.organizationSlug
                                    }
                                    placeholder="grab-n-go"
                                    onChange={event =>
                                        updateRegistration(
                                            "organizationSlug",
                                            event.target.value
                                        )
                                    }
                                    required
                                />
                            </label>

                            <div className="form-grid two-columns">
                                <label>
                                    Store name
                                    <input
                                        value={registration.storeName}
                                        placeholder="Miami Beach"
                                        onChange={event =>
                                            updateRegistration(
                                                "storeName",
                                                event.target.value
                                            )
                                        }
                                        required
                                    />
                                </label>

                                <label>
                                    Store slug
                                    <input
                                        value={registration.storeSlug}
                                        placeholder="miami-beach"
                                        onChange={event =>
                                            updateRegistration(
                                                "storeSlug",
                                                event.target.value
                                            )
                                        }
                                        required
                                    />
                                </label>
                            </div>

                            <button
                                className="button primary wide"
                                type="submit"
                                disabled={busy}
                            >
                                {busy
                                    ? "Creating account..."
                                    : "Create account"}
                            </button>
                        </form>
                    )}
                </div>
            </section>
        </main>
    );
}

function getErrorMessage(error: unknown): string {
    if (error instanceof Error) {
        return error.message;
    }

    return "Something went wrong. Please try again.";
}