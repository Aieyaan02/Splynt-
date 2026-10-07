import { passwordEncodingError } from "../lib/passwordValidation";
import { Brand } from "./Brand";
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
    notice?: string;
    initialMode?: "login" | "register";
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
    onAuthenticated, initialMode = "login", notice
}: AuthPageProps) {
    const [mode, setMode] =
        useState<"login" | "register">(initialMode);

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

        const encodingError = passwordEncodingError(loginPassword);
        if (encodingError) { setError(encodingError); return; }
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

        const encodingError = passwordEncodingError(registration.password);
        if (encodingError) { setError(encodingError); return; }
        setBusy(true);
        setError("");
        setSuccess("");

        const uniqueSuffix =
            crypto.randomUUID().slice(0, 8);

        const registrationRequest: RegisterRequest = {
            ...registration,

            organizationName:
                registration.organizationName.trim(),

            storeName:
                registration.storeName.trim(),

            organizationSlug:
                `${createSlug(
                    registration.organizationName,
                    "business"
                )}-${uniqueSuffix}`,

            storeSlug:
                createSlug(
                    registration.storeName,
                    "store"
                )
        };

        try {
            await authApi.register(registrationRequest);

            try {
                const session = await authApi.login({ email: registration.email.trim(), password: registration.password });
                setAccessToken(session.accessToken);
                onAuthenticated(await accountApi.getCurrent());
            } catch {
                setLoginEmail(registration.email.trim());
                setLoginPassword("");
                setMode("login");
                setSuccess("Your account is ready. Sign in to continue.");
            }
            setRegistration(initialRegistration);
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
                <Brand light />

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
                            <strong>Your own workspace</strong>

                            <span>
                                Keep every store’s inventory organized
                            </span>
                        </article>

                        <article>
                            <strong>Stay connected</strong>

                            <span>
                                Bring your Clover inventory into view
                            </span>
                        </article>

                        <article>
                            <strong>Know your next move</strong>

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
                    <a href="#/" className="auth-back">← Back to Splynt</a>
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

                    {notice && <p className="message" role="status">{notice}</p>}
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
                                {busy
                                    ? "Signing in..."
                                    : "Sign in"}
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
                                    Set up your business and first store.
                                </p>
                            </div>

                            <div className="form-grid two-columns">
                                <label>
                                    First name

                                    <input
                                        value={
                                            registration.firstName
                                        }
                                        autoComplete="given-name"
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
                                        value={
                                            registration.lastName
                                        }
                                        autoComplete="family-name"
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

                                <small>
                                    Use at least 10 characters. Emoji and some letters count toward the limit faster.
                                </small>
                            </label>

                            <label>
                                Business name

                                <input
                                    value={
                                        registration.organizationName
                                    }
                                    autoComplete="organization"
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
                                Store name

                                <input
                                    value={registration.storeName}
                                    onChange={event =>
                                        updateRegistration(
                                            "storeName",
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

function createSlug(
    value: string,
    fallback: string
): string {
    const normalizedValue = value
        .normalize("NFKD")
        .replace(/[\u0300-\u036f]/g, "")
        .toLowerCase()
        .trim()
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-+|-+$/g, "")
        .slice(0, 80)
        .replace(/-+$/g, "");

    return normalizedValue || fallback;
}

function getErrorMessage(error: unknown): string {
    if (error instanceof Error) {
        return error.message;
    }

    return "Something went wrong. Please try again.";
}