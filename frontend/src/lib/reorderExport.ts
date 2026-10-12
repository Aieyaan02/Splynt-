import type { Product } from "../types.ts";

// Quote every cell and neutralize spreadsheet formulas in text fields.
function cell(value: string | number): string {
    const text = String(value);
    const safe = typeof value === "string" && /^[\s\uFEFF]*[=+@-]/u.test(text) ? `'${text}` : text;
    return `"${safe.replaceAll('"', '""')}"`;
}
export function reorderCsv(products: Product[], storeId: number, storeName: string): string {
    const rows: (string | number)[][] = [["Store", "Product", "Barcode", "Unit", "Current stock", "Minimum", "Target", "Suggested quantity", "Stock updated at"]];
    for (const product of products) {
        if (product.storeId !== storeId || !product.active || !product.stockKnown || product.quantity === null || !product.lowStock || product.suggestedReorderQuantity <= 0) continue;
        rows.push([storeName, product.name, product.barcode, product.cloverDetails?.unitName ?? "units", product.quantity, product.reorderLevel, product.targetStock, product.suggestedReorderQuantity, product.updatedAt]);
    }
    return '\uFEFF' + rows.map(row => row.map(cell).join(',')).join('\r\n') + '\r\n';
}
export function downloadReorderCsv(products: Product[], storeId: number, storeName: string): void {
    const url = URL.createObjectURL(new Blob([reorderCsv(products, storeId, storeName)], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = `splynt-reorder-${storeId}-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.append(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
