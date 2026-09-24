import { useEffect, useMemo, useState } from "react";

import {
    clearAccessToken,
    productApi
} from "../lib/api";

import type {
    Account,
    MembershipRole,
    Product,
    StoreSummary
} from "../types";

interface DashboardProps {
    account: Account;
    onLogout: () => void;
}

interface StoreAccess extends StoreSummary {
    organizationName: string;
    role: MembershipRole;
}

export function Dashboard({
    account,
    onLogout
}: DashboardProps) {
    const storeAccesses = useMemo<StoreAccess[]>(
        () =>
            account.organizations.flatMap(organization =>
                organization.stores.map(store => ({
                    ...store,
                    organizationName: organization.name,
                    role: organization.role
                }))
            ),
        [account]
    );

    const rememberedStoreId = Number(
        sessionStorage.getItem("splynt.storeId")
    );

    const initialStore =
        storeAccesses.find(
            store => store.id === rememberedStoreId
        ) ?? storeAccesses[0] ?? null;

    const [selectedStoreId, setSelectedStoreId] =
        useState<number | null>(initialStore?.id ?? null);

    const [products, setProducts] = useState<Product[]>([]);
    const [lowStockProducts, setLowStockProducts] =
        useState<Product[]>([]);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    const selectedStore =
        storeAccesses.find(
            store => store.id === selectedStoreId
        ) ?? null;

    useEffect(() => {
        if (selectedStoreId === null) {
            return;
        }

        let cancelled = false;

        async function loadInventory() {
            setLoading(true);
            setError("");

            try {
                const [allProducts, lowStock] =
                    await Promise.all([
                        productApi.getAll(selectedStoreId!),
                        productApi.getLowStock(selectedStoreId!)
                    ]);

                if (!cancelled) {
                    setProducts(allProducts);
                    setLowStockProducts(lowStock);
                }
            } catch (requestError) {
                if (!cancelled) {
                    setError(getErrorMessage(requestError));
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        }

        sessionStorage.setItem(
            "splynt.storeId",
            String(selectedStoreId)
        );

        void loadInventory();

        return () => {
            cancelled = true;
        };
    }, [selectedStoreId]);

    const totalUnits = products.reduce(
        (sum, product) => sum + product.quantity,
        0
    );

    const inventoryValue = products.reduce(
        (sum, product) =>
            sum
            + product.quantity * (product.unitCost ?? 0),
        0
    );

    function logout() {
        clearAccessToken();
        sessionStorage.removeItem("splynt.storeId");
        onLogout();
    }

    return (
        <div className="dashboard-layout">
            <aside className="sidebar">
                <div>
                    <div className="brand">
                        <span className="brand-mark">S</span>
                        <span>Splynt</span>
                    </div>

                    <div className="workspace-label">
                        Workspace
                    </div>

                    <label className="store-picker">
                        Active store
                        <select
                            value={selectedStoreId ?? ""}
                            disabled={storeAccesses.length === 0}
                            onChange={event =>
                                setSelectedStoreId(
                                    Number(event.target.value)
                                )
                            }
                        >
                            {storeAccesses.map(store => (
                                <option
                                    key={store.id}
                                    value={store.id}
                                >
                                    {store.name}
                                </option>
                            ))}
                        </select>
                    </label>

                    <nav className="sidebar-navigation">
                        <button
                            className="navigation-item active"
                            type="button"
                        >
                            <span>▦</span>
                            Overview
                        </button>

                        <button
                            className="navigation-item"
                            type="button"
                            onClick={() =>
                                document
                                    .querySelector(
                                        "#low-stock-section"
                                    )
                                    ?.scrollIntoView({
                                        behavior: "smooth"
                                    })
                            }
                        >
                            <span>!</span>
                            Low stock
                        </button>
                    </nav>
                </div>

                <div className="sidebar-account">
                    <div className="avatar">
                        {getInitials(account)}
                    </div>

                    <div className="account-copy">
                        <strong>{account.fullName}</strong>
                        <span>
                            {selectedStore?.role ?? "NO STORE"}
                        </span>
                    </div>

                    <button
                        className="logout-button"
                        type="button"
                        title="Sign out"
                        onClick={logout}
                    >
                        ↗
                    </button>
                </div>
            </aside>

            <main className="dashboard-content">
                <header className="dashboard-header">
                    <div>
                        <span className="eyebrow dark">
                            Inventory overview
                        </span>

                        <h1>
                            {selectedStore?.name
                                ?? "No active store"}
                        </h1>

                        <p>
                            {selectedStore
                                ? `${selectedStore.organizationName} · `
                                    + `${selectedStore.role}`
                                : "Create a store to begin."}
                        </p>
                    </div>

                    <div className="header-actions">
                        <button
                            className="button secondary"
                            type="button"
                            disabled={
                                selectedStoreId === null || loading
                            }
                            onClick={() =>
                                setSelectedStoreId(current => {
                                    if (current === null) {
                                        return null;
                                    }

                                    setSelectedStoreId(null);

                                    window.setTimeout(() => {
                                        setSelectedStoreId(current);
                                    });

                                    return null;
                                })
                            }
                        >
                            {loading ? "Refreshing..." : "Refresh"}
                        </button>

                        <button
                            className="button primary"
                            type="button"
                            disabled={selectedStoreId === null}
                        >
                            + Add product
                        </button>
                    </div>
                </header>

                {error && (
                    <div className="message error-message">
                        {error}
                    </div>
                )}

                <section className="statistics-grid">
                    <StatCard
                        label="Total products"
                        value={products.length.toLocaleString()}
                        description="Active catalog items"
                    />

                    <StatCard
                        label="Low-stock products"
                        value={lowStockProducts.length.toLocaleString()}
                        description="Require attention"
                        warning
                    />

                    <StatCard
                        label="Inventory units"
                        value={totalUnits.toLocaleString()}
                        description="Across active products"
                    />

                    <StatCard
                        label="Inventory value"
                        value={formatCurrency(
                            inventoryValue,
                            selectedStore?.currencyCode
                        )}
                        description="Based on unit cost"
                    />
                </section>

                <section
                    id="low-stock-section"
                    className="content-card"
                >
                    <div className="section-heading">
                        <div>
                            <span className="section-kicker">
                                Attention needed
                            </span>
                            <h2>Low-stock recommendations</h2>
                        </div>

                        <span className="badge warning-badge">
                            {lowStockProducts.length}{" "}
                            {lowStockProducts.length === 1
                                ? "item"
                                : "items"}
                        </span>
                    </div>

                    {loading ? (
                        <div className="empty-state">
                            Loading inventory…
                        </div>
                    ) : lowStockProducts.length === 0 ? (
                        <div className="empty-state">
                            <div className="empty-icon">✓</div>
                            <h3>Stock levels look healthy</h3>
                            <p>
                                No products currently require
                                reordering.
                            </p>
                        </div>
                    ) : (
                        <div className="low-stock-grid">
                            {lowStockProducts.map(product => (
                                <article
                                    className="low-stock-card"
                                    key={product.id}
                                >
                                    <h3>{product.name}</h3>

                                    <p>
                                        {product.category
                                            ?? "Uncategorized"}
                                        {" · "}
                                        {product.barcode}
                                    </p>

                                    <div className="stock-values">
                                        <div>
                                            <strong>
                                                {product.quantity}
                                            </strong>
                                            <span>
                                                units remaining
                                            </span>
                                        </div>

                                        <div>
                                            <strong>
                                                {
                                                    product
                                                        .suggestedReorderQuantity
                                                }
                                            </strong>
                                            <span>
                                                suggested reorder
                                            </span>
                                        </div>
                                    </div>
                                </article>
                            ))}
                        </div>
                    )}
                </section>

                <section className="content-card">
                    <div className="section-heading">
                        <div>
                            <span className="section-kicker">
                                Store catalog
                            </span>
                            <h2>Products</h2>
                        </div>
                    </div>

                    {loading ? (
                        <div className="empty-state">
                            Loading products…
                        </div>
                    ) : products.length === 0 ? (
                        <div className="empty-state">
                            <div className="empty-icon">□</div>
                            <h3>No products yet</h3>
                            <p>
                                Add your first product to begin
                                tracking inventory.
                            </p>
                        </div>
                    ) : (
                        <div className="table-wrapper">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Product</th>
                                        <th>Category</th>
                                        <th>Stock</th>
                                        <th>Reorder at</th>
                                        <th>Unit cost</th>
                                        <th>Status</th>
                                    </tr>
                                </thead>

                                <tbody>
                                    {products.map(product => (
                                        <tr key={product.id}>
                                            <td>
                                                <strong>
                                                    {product.name}
                                                </strong>
                                                <span>
                                                    {product.brand
                                                        ?? "No brand"}
                                                    {" · "}
                                                    {product.barcode}
                                                </span>
                                            </td>

                                            <td>
                                                {product.category
                                                    ?? "Uncategorized"}
                                            </td>

                                            <td>{product.quantity}</td>

                                            <td>
                                                {product.reorderLevel}
                                            </td>

                                            <td>
                                                {product.unitCost === null
                                                    ? "—"
                                                    : formatCurrency(
                                                        product.unitCost,
                                                        selectedStore
                                                            ?.currencyCode
                                                    )}
                                            </td>

                                            <td>
                                                <span
                                                    className={
                                                        product.lowStock
                                                            ? "stock-status low"
                                                            : "stock-status healthy"
                                                    }
                                                >
                                                    {product.lowStock
                                                        ? "Low stock"
                                                        : "Healthy"}
                                                </span>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </section>
            </main>
        </div>
    );
}

interface StatCardProps {
    label: string;
    value: string;
    description: string;
    warning?: boolean;
}

function StatCard({
    label,
    value,
    description,
    warning = false
}: StatCardProps) {
    return (
        <article
            className={
                warning
                    ? "statistic-card warning-card"
                    : "statistic-card"
            }
        >
            <span>{label}</span>
            <strong>{value}</strong>
            <small>{description}</small>
        </article>
    );
}

function getInitials(account: Account): string {
    return (
        `${account.firstName.charAt(0)}`
        + `${account.lastName.charAt(0)}`
    ).toUpperCase();
}

function formatCurrency(
    value: number,
    currencyCode = "USD"
): string {
    return new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: currencyCode
    }).format(value);
}

function getErrorMessage(error: unknown): string {
    if (error instanceof Error) {
        return error.message;
    }

    return "Unable to load inventory.";
}