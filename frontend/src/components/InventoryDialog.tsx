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

        if (quantity <= 0) {
            setError(
                "Quantity must be greater than zero"
            );
            return;
        }

        if (isSale && quantity > product.quantity) {
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

            if (isSale) {
                await inventoryApi.recordSale(
                    storeId,
                    product.id,
                    request
                );
            } else {
                await inventoryApi.recordRestock(
                    storeId,
                    product.id,
                    request
                );
            }

            await onSaved();
            onClose();
        } catch (requestError) {
            if (requestError instanceof ApiRequestError) {
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

                        <h2>
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
                    <div className="message error-message">
                        {error}
                    </div>
                )}

                <label>
                    Quantity
                    <input
                        type="number"
                        min="1"
                        max={
                            isSale
                                ? product.quantity
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
                        Cancel
                    </button>

                    <button
                        className="button primary"
                        type="submit"
                        disabled={busy}
                    >
                        {busy
                            ? "Saving..."
                            : isSale
                                ? "Record sale"
                                : "Record restock"}
                    </button>
                </footer>
            </form>
        </dialog>
    );
}