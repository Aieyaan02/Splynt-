import { PasswordDialog } from "./PasswordDialog";
import { AdvicePanel } from "./AdvicePanel";
import { StoreSettingsDialog } from "./StoreSettingsDialog";
import { InsightsPanel } from "./InsightsPanel";
import { ProductDetailsDialog } from "./ProductDetailsDialog";
import { Brand } from "./Brand";
import { CloverAutoSync } from "./CloverAutoSync";
import {
    useCallback,
    useEffect,
    useMemo,
    useState,
    useRef
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
    accountApi,
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
    onAccountChanged: (account: Account) => void;
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
    onAccountChanged,
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

    const [passwordDialogOpen, setPasswordDialogOpen] = useState(false);
    const [storeDialog, setStoreDialog] = useState<{ id: number | null } | null>(null);
    const [storeRevision, setStoreRevision] = useState(0);

    const [connectionResult, setConnectionResult] = useState(() =>
        new URLSearchParams(window.location.search).get("clover"));
    const rememberedStoreId = Number(
        new URLSearchParams(window.location.search).get("store")
            ?? sessionStorage.getItem("splynt.storeId")
    );
    useEffect(() => {
        const url = new URL(window.location.href);
        if (url.searchParams.has("clover")) {
            url.searchParams.delete("clover");
            url.searchParams.delete("store");
            window.history.replaceState(null, "", url);
        }
    }, []);

    const initialStore =
        storeAccesses.find(
            store => store.id === rememberedStoreId
        ) ?? storeAccesses[0] ?? null;

    const [selectedStoreId, setSelectedStoreId] =
        useState<number | null>(initialStore?.id ?? null);

    const activeStoreId = useRef(selectedStoreId);

    const [products, setProducts] =
        useState<Product[]>([]);

    // Keep summary and table on the same server snapshot during background imports.
    const lowStockProducts = products.filter(product => product.lowStock);

    const [archivedProducts, setArchivedProducts] =
        useState<Product[]>([]);

    const [detailProduct, setDetailProduct] = useState<Product | null>(null);
    const [search, setSearch] = useState("");
    const [stockFilter, setStockFilter] = useState("all");
    const [categoryFilter, setCategoryFilter] = useState("");
    const requestGeneration = useRef(0);
    const visibleProducts = products.filter(product => {
        const term = search.toLowerCase().trim();
        return (!term || [product.name, product.barcode, product.brand, product.category, product.cloverDetails?.sku, product.cloverDetails?.alternateName, ...(product.cloverDetails?.categories ?? [])]
            .some(value => value?.toLowerCase().includes(term)))
            && (stockFilter === "all" || (stockFilter === "low" ? product.lowStock : (stockFilter === "unknown" ? !product.stockKnown : product.quantity !== null && (product.quantity ?? 0) <= 0)))
            && (!categoryFilter || product.category === categoryFilter || product.cloverDetails?.categories?.includes(categoryFilter));
    });
    const categories = [...new Set(products.flatMap(product => [product.category, ...(product.cloverDetails?.categories ?? [])]).filter(Boolean))] as string[];

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

    const loadInventory = useCallback(async (reportFailure = false) => {
        if (selectedStoreId === null || activeStoreId.current !== selectedStoreId) {
            return;
        }

        const generation = ++requestGeneration.current;
        setLoading(true);
        setError("");

        try {
            const [
                allProducts,
                archived
            ] = await Promise.all([
                productApi.getAll(selectedStoreId),
                productApi.getArchived(selectedStoreId)
            ]);

            if (generation !== requestGeneration.current) return;
            setProducts(allProducts);
            setArchivedProducts(archived);
        } catch (requestError) {
            if (generation === requestGeneration.current) setError(getErrorMessage(requestError));
            if (reportFailure) throw requestError;
        } finally {
            if (generation === requestGeneration.current) setLoading(false);
        }
    }, [selectedStoreId, setArchivedProducts]);

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

    const stockedProducts = products.filter(product => product.stockKnown && product.quantity !== null && product.quantity > 0).length;
    const unknownStock = products.filter(product => !product.stockKnown).length;

    const inventoryValue = products.reduce(
        (sum, product) =>
            sum
            + Math.max(0, product.quantity ?? 0) * (product.unitCost ?? 0),
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
            {passwordDialogOpen && <PasswordDialog onClose={() => setPasswordDialogOpen(false)} onChanged={onLogout} />}
            <aside className="sidebar">
                <div>
                    <Brand light />

                    <div className="workspace-label">
                        Workspace
                    </div>

                    <label className="store-picker">
                        Active store

                        <select
                            value={selectedStoreId ?? ""}
                            disabled={storeAccesses.length === 0}
                            onChange={event => {
                                requestGeneration.current++;
                                setProducts([]); setArchivedProducts([]);
                                setInventorySelection(null); setProductDialogOpen(false); setDetailProduct(null);
                                setSearch(""); setCategoryFilter(""); setStockFilter("all");
                                activeStoreId.current = Number(event.target.value);
                                setSelectedStoreId(Number(event.target.value));
                            }}
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

                    <div className="store-management">
                        {(selectedStore?.role === "OWNER" || selectedStore?.role === "ADMIN") && <button className="navigation-item" onClick={() => setStoreDialog({ id: selectedStoreId })}>Store settings</button>}
                        {account.organizations.some(org => org.role === "OWNER" || org.role === "ADMIN") && <button className="navigation-item" onClick={() => setStoreDialog({ id: null })}>+ Add store</button>}
                    </div>
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
                    <button className="navigation-item" type="button" onClick={() => setPasswordDialogOpen(true)}>
                        <span>⚙</span> Change password
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
                <section className="connection-panel" aria-label="Store integration">
                    <div className="connection-panel-label"><span className="connection-icon">⇄</span><div><strong>Your store, connected.</strong><p>Bring your Clover inventory into focus.</p></div></div>
                    <CloverAutoSync
                        key={selectedStoreId}
                        canManage={selectedStore?.role === "OWNER" || selectedStore?.role === "ADMIN"}
                        storeId={selectedStoreId}
                        onSynchronized={() => loadInventory(true)}
                    />

                </section>

                {connectionResult && (
                    <div className={connectionResult === "connected" ? "message" : "message error-message"} role="status">
                        {connectionResult === "connected"
                            ? "Clover is connected. Your inventory will appear after the first sync; you can also choose Sync now."
                            : "Clover wasn't connected. Try again from this browser and approve store access. If it keeps failing, contact support."}
                        <button type="button" className="button secondary compact" onClick={() => setConnectionResult(null)}>Dismiss</button>
                    </div>
                )}
                {error && (
                    <div className="message error-message">
                        {error}
                    </div>
                )}

                {unknownStock > 0 && <p className="message error-message" role="status">Stock is unknown for {unknownStock} products. They are excluded from low-stock alerts and inventory value. Check stock tracking in Clover and sync again.</p>}
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
                        label="Products in stock"
                        value={stockedProducts.toLocaleString()}
                        description={unknownStock ? `${unknownStock} products have unknown stock` : "Known positive balances"}
                    />

                    <StatCard
                        label="Inventory value"
                        value={formatCurrency(
                            inventoryValue,
                            selectedStore?.currencyCode
                        )}
                        description={products.some(product => product.unitCost === null || !product.stockKnown || (product.quantity ?? 0) < 0) ? "Partial value · missing costs, unknown or negative stock" : "Based on unit cost"}
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
                                {unknownStock ? "?" : "✓"}
                            </div>

                            <h3>
                                {unknownStock ? "Some stock levels are unknown" : "Known stock levels look healthy"}
                            </h3>

                            <p>
                                {unknownStock ? "Resolve unknown stock before deciding whether every product is sufficiently stocked." : "No products with known stock currently meet their reorder thresholds."}
                            </p>
                        </div>
                    ) : (
                        <div className="low-stock-grid">
                            {lowStockProducts.map(product => (
                                <article
                                    className="low-stock-card"
                                    key={product.id}
                                >
                                    <h3><button type="button" className="product-name-button" onClick={() => setDetailProduct(product)}>{product.name} ↗</button></h3>

                                    <p>
                                        {product.category
                                            ?? "Uncategorized"}
                                        {" · "}
                                        {product.barcode}
                                    </p>

                                    <div className="stock-values">
                                        <div>
                                            <strong>
                                                {product.quantity ?? "Unknown"}
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

                    <div className="catalog-toolbar">
                        <label className="catalog-search"><span>Search products</span><input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Search name, barcode, or brand…" /></label>
                        <label><span>Stock status</span><select value={stockFilter} onChange={event => setStockFilter(event.target.value)}><option value="all">All stock levels</option><option value="low">Low stock</option><option value="out">Out of stock</option><option value="unknown">Unknown stock</option></select></label>
                        <label><span>Category</span><select value={categoryFilter} onChange={event => setCategoryFilter(event.target.value)}><option value="">All categories</option>{categories.sort().map(category => <option key={category}>{category}</option>)}</select></label>
                    </div>
                    {products.length > 0 && <p className="catalog-count" aria-live="polite">Showing {visibleProducts.length} of {products.length} products</p>}
                    {!loading && products.length > 0 && visibleProducts.length === 0 && <div className="empty-state"><h3>No matching products</h3><p>Try another name, barcode, or stock filter.</p><button className="button secondary" onClick={() => { setSearch(""); setStockFilter("all"); setCategoryFilter(""); }}>Clear filters</button></div>}
                    {loading ? (
                        <div className="empty-state">
                            Loading products…
                        </div>
                    ) : products.length === 0 ? (
                        <div className="empty-state">
                            <div className="empty-icon">
                                □
                            </div>

                            <h3>Your shelves start here.</h3>

                            <p>
                                Connect your Clover store above to import inventory,
                                or add your first product manually.
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
                                    {visibleProducts.map(product => (
                                        <tr key={product.id}>
                                            <td>
                                                <button type="button" className="product-name-button" onClick={() => setDetailProduct(product)}>{product.name} <span aria-hidden="true">↗</span></button>

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
                                                {product.quantity ?? "Unknown"}
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
                                                        !product.stockKnown ? "stock-status unknown" : product.lowStock
                                                            ? "stock-status low"
                                                            : "stock-status healthy"
                                                    }
                                                >
                                                    {!product.stockKnown ? "Unknown stock" : product.lowStock
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
                                                            product.source === "CLOVER" || (product.quantity ?? 0) <= 0
                                                        }
                                                        title={product.source === "CLOVER" ? "Manage stock in Clover, then sync Splynt" : undefined}
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
                                                        disabled={product.source === "CLOVER"}
                                                        title={product.source === "CLOVER" ? "Manage stock in Clover, then sync Splynt" : undefined}
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

                {selectedStoreId !== null && <AdvicePanel key={`advice-${selectedStoreId}-${storeRevision}`} storeId={selectedStoreId} canManage={selectedStore?.role === "OWNER" || selectedStore?.role === "ADMIN"} />}
                {selectedStoreId !== null && <InsightsPanel key={`${selectedStoreId}-${storeRevision}`} storeId={selectedStoreId} />}
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
                                {unknownStock ? "?" : "✓"}
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
                                                <button type="button" className="product-name-button" onClick={() => setDetailProduct(product)}>{product.name} <span aria-hidden="true">↗</span></button>

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
                                                {product.quantity ?? "Unknown"}
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

            {storeDialog && <StoreSettingsDialog storeId={storeDialog.id} organizations={account.organizations}
                onClose={() => setStoreDialog(null)} onSaved={async id => {
                    const refreshed = await accountApi.getCurrent();
                    onAccountChanged(refreshed);
                    requestGeneration.current++;
                    if (id !== selectedStoreId) { setProducts([]); setArchivedProducts([]); }
                    setInventorySelection(null); setProductDialogOpen(false); setDetailProduct(null);
                    activeStoreId.current = id; setSelectedStoreId(id); setStoreRevision(current => current + 1);
                }} />}
            {detailProduct && selectedStoreId !== null && <ProductDetailsDialog
                key={`${selectedStoreId}-${detailProduct.id}`}
                product={detailProduct} storeId={selectedStoreId}
                timezone={selectedStore?.timezone ?? "UTC"}
                canManage={selectedStore?.role === "OWNER" || selectedStore?.role === "ADMIN"}
                onClose={() => setDetailProduct(null)} onSaved={loadInventory} />}
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
                        await loadInventory(true);
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