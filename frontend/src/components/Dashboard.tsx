import {
    useCallback,
    useEffect,
    useMemo,
    useState
} from "react";

import {
    InventoryDialog
} from "./InventoryDialog";

import type {
    InventoryOperation
} from "./InventoryDialog";

import {
    ProductDialog
} from "./ProductDialog";

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

interface InventorySelection {
    product: Product;
    operation: InventoryOperation;
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

    const [products, setProducts] =
        useState<Product[]>([]);

    const [lowStockProducts, setLowStockProducts] =
        useState<Product[]>([]);

    const [archivedProducts, setArchivedProducts] =
        useState<Product[]>([]);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");

    const [productDialogOpen, setProductDialogOpen] =
        useState(false);

    const [inventorySelection, setInventorySelection] =
        useState<InventorySelection | null>(null);

    const [archivingProductId, setArchivingProductId] =
        useState<number | null>(null);

    const [restoringProductId, setRestoringProductId] =
        useState<number | null>(null);

    const selectedStore =
        storeAccesses.find(
            store => store.id === selectedStoreId
        ) ?? null;

    const loadInventory = useCallback(async () => {
        if (selectedStoreId === null) {
            return;
        }

        setLoading(true);
        setError("");

        try {
            const [
                allProducts,
                lowStock,
                archived
            ] = await Promise.all([
                productApi.getAll(selectedStoreId),
                productApi.getLowStock(selectedStoreId),
                productApi.getArchived(selectedStoreId)
            ]);

            setProducts(allProducts);
            setLowStockProducts(lowStock);
            setArchivedProducts(archived);
        } catch (requestError) {
            setError(getErrorMessage(requestError));
        } finally {
            setLoading(false);
        }
    }, [selectedStoreId]);

    useEffect(() => {
        if (selectedStoreId === null) {
            return;
        }

        sessionStorage.setItem(
            "splynt.storeId",
            String(selectedStoreId)
        );

        /*
         * Loading data when the selected store changes is an
         * intentional synchronization with the backend API.
         */
        // oxlint-disable-next-line react/set-state-in-effect
        void loadInventory();
    }, [selectedStoreId, loadInventory]);

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

    function openInventoryDialog(
        product: Product,
        operation: InventoryOperation
    ) {
        setInventorySelection({
            product,
            operation
        });
    }

    async function archiveProduct(product: Product) {
        if (selectedStoreId === null) {
            return;
        }

        const confirmed = window.confirm(
            `Archive "${product.name}"?\n\n`
            + "It will disappear from the active catalog, "
            + "but its inventory history will be preserved."
        );

        if (!confirmed) {
            return;
        }

        setArchivingProductId(product.id);
        setError("");

        try {
            await productApi.archive(
                selectedStoreId,
                product.id
            );

            await loadInventory();
        } catch (requestError) {
            setError(getErrorMessage(requestError));
        } finally {
            setArchivingProductId(null);
        }
    }

    async function restoreProduct(product: Product) {
        if (selectedStoreId === null) {
            return;
        }

        const confirmed = window.confirm(
            `Restore "${product.name}"?\n\n`
            + "The product will return to the active catalog "
            + "with its previous stock information."
        );

        if (!confirmed) {
            return;
        }

        setRestoringProductId(product.id);
        setError("");

        try {
            await productApi.restore(
                selectedStoreId,
                product.id
            );

            await loadInventory();
        } catch (requestError) {
            setError(getErrorMessage(requestError));
        } finally {
            setRestoringProductId(null);
        }
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
                            onClick={() =>
                                window.scrollTo({
                                    top: 0,
                                    behavior: "smooth"
                                })
                            }
                        >
                            <span>▦</span>
                            Overview
                        </button>

                        <button
                            className="navigation-item"
                            type="button"
                            onClick={() =>
                                scrollToSection(
                                    "low-stock-section"
                                )
                            }
                        >
                            <span>!</span>
                            Low stock
                        </button>

                        <button
                            className="navigation-item"
                            type="button"
                            onClick={() =>
                                scrollToSection(
                                    "archived-products-section"
                                )
                            }
                        >
                            <span>↺</span>
                            Archived
                            {archivedProducts.length > 0 && (
                                <span className="navigation-count">
                                    {archivedProducts.length}
                                </span>
                            )}
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
                                selectedStoreId === null
                                || loading
                            }
                            onClick={() =>
                                void loadInventory()
                            }
                        >
                            {loading
                                ? "Refreshing..."
                                : "Refresh"}
                        </button>

                        <button
                            className="button primary"
                            type="button"
                            disabled={selectedStoreId === null}
                            onClick={() =>
                                setProductDialogOpen(true)
                            }
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
                        value={
                            lowStockProducts.length.toLocaleString()
                        }
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

                            <h2>
                                Low-stock recommendations
                            </h2>
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
                            <div className="empty-icon">
                                ✓
                            </div>

                            <h3>
                                Stock levels look healthy
                            </h3>

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

                        <span className="badge">
                            {products.length} active
                        </span>
                    </div>

                    {loading ? (
                        <div className="empty-state">
                            Loading products…
                        </div>
                    ) : products.length === 0 ? (
                        <div className="empty-state">
                            <div className="empty-icon">
                                □
                            </div>

                            <h3>No active products</h3>

                            <p>
                                Add a product or restore one from
                                the archived-products section.
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
                                        <th>Actions</th>
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

                                            <td>
                                                {product.quantity}
                                            </td>

                                            <td>
                                                {
                                                    product
                                                        .reorderLevel
                                                }
                                            </td>

                                            <td>
                                                {product.unitCost ===
                                                null
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

                                            <td>
                                                <div className="table-actions">
                                                    <button
                                                        className="button secondary compact"
                                                        type="button"
                                                        disabled={
                                                            product.quantity
                                                            <= 0
                                                        }
                                                        onClick={() =>
                                                            openInventoryDialog(
                                                                product,
                                                                "sale"
                                                            )
                                                        }
                                                    >
                                                        Sale
                                                    </button>

                                                    <button
                                                        className="button secondary compact"
                                                        type="button"
                                                        onClick={() =>
                                                            openInventoryDialog(
                                                                product,
                                                                "restock"
                                                            )
                                                        }
                                                    >
                                                        Restock
                                                    </button>

                                                    <button
                                                        className="button danger compact"
                                                        type="button"
                                                        disabled={
                                                            archivingProductId
                                                            === product.id
                                                        }
                                                        onClick={() =>
                                                            void archiveProduct(
                                                                product
                                                            )
                                                        }
                                                    >
                                                        {
                                                            archivingProductId
                                                            === product.id
                                                                ? "Archiving..."
                                                                : "Archive"
                                                        }
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </section>

                <section
                    id="archived-products-section"
                    className="content-card"
                >
                    <div className="section-heading">
                        <div>
                            <span className="section-kicker">
                                Product history
                            </span>

                            <h2>Archived products</h2>

                            <p>
                                Restore products without losing
                                their inventory history.
                            </p>
                        </div>

                        <span className="badge">
                            {archivedProducts.length} archived
                        </span>
                    </div>

                    {loading ? (
                        <div className="empty-state">
                            Loading archived products…
                        </div>
                    ) : archivedProducts.length === 0 ? (
                        <div className="empty-state">
                            <div className="empty-icon">
                                ✓
                            </div>

                            <h3>No archived products</h3>

                            <p>
                                Archived products will appear here
                                and can be restored later.
                            </p>
                        </div>
                    ) : (
                        <div className="table-wrapper">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Product</th>
                                        <th>Category</th>
                                        <th>Last stock</th>
                                        <th>Barcode</th>
                                        <th>Status</th>
                                        <th>Action</th>
                                    </tr>
                                </thead>

                                <tbody>
                                    {archivedProducts.map(product => (
                                        <tr key={product.id}>
                                            <td>
                                                <strong>
                                                    {product.name}
                                                </strong>

                                                <span>
                                                    {product.brand
                                                        ?? "No brand"}
                                                </span>
                                            </td>

                                            <td>
                                                {product.category
                                                    ?? "Uncategorized"}
                                            </td>

                                            <td>
                                                {product.quantity}
                                            </td>

                                            <td>
                                                {product.barcode}
                                            </td>

                                            <td>
                                                <span className="stock-status archived">
                                                    Archived
                                                </span>
                                            </td>

                                            <td>
                                                <button
                                                    className="button secondary compact"
                                                    type="button"
                                                    disabled={
                                                        restoringProductId
                                                        === product.id
                                                    }
                                                    onClick={() =>
                                                        void restoreProduct(
                                                            product
                                                        )
                                                    }
                                                >
                                                    {
                                                        restoringProductId
                                                        === product.id
                                                            ? "Restoring..."
                                                            : "Restore"
                                                    }
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </section>
            </main>

            {productDialogOpen
                && selectedStoreId !== null && (
                <ProductDialog
                    storeId={selectedStoreId}
                    onClose={() =>
                        setProductDialogOpen(false)
                    }
                    onSaved={async () => {
                        await loadInventory();
                    }}
                />
            )}

            {inventorySelection
                && selectedStoreId !== null && (
                <InventoryDialog
                    storeId={selectedStoreId}
                    product={inventorySelection.product}
                    operation={
                        inventorySelection.operation
                    }
                    onClose={() =>
                        setInventorySelection(null)
                    }
                    onSaved={async () => {
                        await loadInventory();
                    }}
                />
            )}
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

function scrollToSection(sectionId: string) {
    document
        .getElementById(sectionId)
        ?.scrollIntoView({
            behavior: "smooth"
        });
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