import { confirmedMutation } from "../lib/confirmedMutation";
import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";

import {
    ApiRequestError,
    inventoryApi
} from "../lib/api";

import type { Product } from "../types";

export type InventoryOperation =
    | "sale"
    | "restock";

interface InventoryDialogProps {
    storeId: number;
    product: Product;
    operation: InventoryOperation;
    onClose: () => void;
    onSaved: () => void | Promise<void>;
}

export function InventoryDialog({
    storeId,
    product,
    operation,
    onClose,
    onSaved
}: InventoryDialogProps) {
    const dialogReference =
        useRef<HTMLDialogElement>(null);

    const operationState = useRef(confirmedMutation());
    const [saved, setSaved] = useState(false);
    const [quantity, setQuantity] = useState(1);
    const [note, setNote] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");

    const isSale = operation === "sale";

    useEffect(() => {
        const dialog = dialogReference.current;

        if (dialog && !dialog.open) {
            dialog.showModal();
        }
    }, []);

    async function handleSubmit(event: FormEvent) {
        event.preventDefault();

        setError("");

        if (!saved && quantity <= 0) {
            setError(
                "Quantity must be greater than zero"
            );
            return;
        }

        if (!saved && (product.quantity === null || (isSale && quantity > product.quantity))) {
            setError(
                `Only ${product.quantity} units are available`
            );
            return;
        }

        setBusy(true);

        try {
            const request = {
                quantity,
                note: note.trim() || null
            };

            await operationState.current.run(
                () => isSale ? inventoryApi.recordSale(storeId, product.id, request)
                    : inventoryApi.recordRestock(storeId, product.id, request),
                onSaved,
                () => setSaved(true)
            );
            onClose();
        } catch (requestError) {
            if (operationState.current.saved) {
                setError("Your inventory change was saved, but the dashboard could not refresh. Retry the refresh; your change will not be submitted again.");
            } else if (requestError instanceof ApiRequestError) {
                setError(requestError.message);
            } else {
                setError(
                    requestError instanceof Error
                        ? requestError.message
                        : "Unable to update inventory"
                );
            }
        } finally {
            setBusy(false);
        }
    }

    return (
        <dialog
            ref={dialogReference}
            className="modal compact-modal"
            aria-labelledby="inventory-operation-title"
            onCancel={event => {
                event.preventDefault();

                if (!busy) {
                    onClose();
                }
            }}
        >
            <form
                className="modal-form"
                onSubmit={handleSubmit}
            >
                <header className="modal-header">
                    <div>
                        <span className="section-kicker">
                            Inventory movement
                        </span>

                        <h2 id="inventory-operation-title">
                            {isSale
                                ? "Record sale"
                                : "Record restock"}
                        </h2>

                        <p>
                            {product.name} · {product.quantity} units
                            currently available
                        </p>
                    </div>

                    <button
                        className="modal-close"
                        type="button"
                        disabled={busy}
                        onClick={onClose}
                        aria-label="Close"
                    >
                        ×
                    </button>
                </header>

                {error && (
                    <div className="message error-message" role="alert">
                        {error}
                    </div>
                )}

                <label>
                    Quantity
                    <input
                        disabled={busy || saved}
                        type="number"
                        min="0.000001"
                        step="0.000001"
                        max={
                            isSale
                                ? product.quantity ?? undefined
                                : undefined
                        }
                        value={quantity}
                        onChange={event =>
                            setQuantity(
                                Number(event.target.value)
                            )
                        }
                        required
                    />
                </label>

                <label>
                    Note
                    <textarea
                        disabled={busy || saved}
                        rows={4}
                        value={note}
                        onChange={event =>
                            setNote(event.target.value)
                        }
                        placeholder={
                            isSale
                                ? "Optional sale note"
                                : "Optional supplier or delivery note"
                        }
                    />
                </label>

                <footer className="modal-actions">
                    <button
                        className="button secondary"
                        type="button"
                        disabled={busy}
                        onClick={onClose}
                    >
                        {saved ? "Close" : "Cancel"}
                    </button>

                    <button
                        className="button primary"
                        type="submit"
                        disabled={busy}
                    >
                        {busy
                            ? saved ? "Refreshing…" : "Saving…"
                            : saved ? "Refresh dashboard" : isSale
                                ? "Record sale"
                                : "Record restock"}
                    </button>
                </footer>
            </form>
        </dialog>
    );
}