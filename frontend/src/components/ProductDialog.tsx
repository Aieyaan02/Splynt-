import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";

import {
    ApiRequestError,
    productApi
} from "../lib/api";

import type {
    CreateProductRequest,
    Product
} from "../types";

interface ProductDialogProps {
    storeId: number;
    onClose: () => void;
    onSaved: (product: Product) => void | Promise<void>;
}

export function ProductDialog({
    storeId,
    onClose,
    onSaved
}: ProductDialogProps) {
    const dialogReference =
        useRef<HTMLDialogElement>(null);

    const [barcode, setBarcode] = useState("");
    const [name, setName] = useState("");
    const [brand, setBrand] = useState("");
    const [category, setCategory] = useState("");
    const [quantity, setQuantity] = useState(0);
    const [reorderLevel, setReorderLevel] = useState(5);
    const [targetStock, setTargetStock] = useState(20);
    const [unitCost, setUnitCost] = useState("");

    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [validationErrors, setValidationErrors] =
        useState<Record<string, string>>({});

    useEffect(() => {
        const dialog = dialogReference.current;

        if (dialog && !dialog.open) {
            dialog.showModal();
        }
    }, []);

    async function handleSubmit(event: FormEvent) {
        event.preventDefault();

        setError("");
        setValidationErrors({});

        if (targetStock < reorderLevel) {
            setValidationErrors({
                targetStock:
                    "Target stock must be at least the reorder level"
            });
            return;
        }

        const request: CreateProductRequest = {
            barcode: barcode.trim(),
            name: name.trim(),
            brand: brand.trim() || null,
            category: category.trim() || null,
            quantity,
            reorderLevel,
            targetStock,
            unitCost:
                unitCost.trim() === ""
                    ? null
                    : Number(unitCost)
        };

        setBusy(true);

        try {
            const product = await productApi.create(
                storeId,
                request
            );

            await onSaved(product);
            onClose();
        } catch (requestError) {
            if (requestError instanceof ApiRequestError) {
                setError(requestError.message);
                setValidationErrors(
                    requestError.validationErrors
                );
            } else {
                setError(
                    requestError instanceof Error
                        ? requestError.message
                        : "Unable to create product"
                );
            }
        } finally {
            setBusy(false);
        }
    }

    return (
        <dialog
            ref={dialogReference}
            className="modal"
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
                            New catalog item
                        </span>
                        <h2>Add product</h2>
                        <p>
                            Add a product and define its stock targets.
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

                <div className="form-grid two-columns">
                    <label>
                        Product name
                        <input
                            value={name}
                            onChange={event =>
                                setName(event.target.value)
                            }
                            required
                        />
                        <FieldError
                            message={validationErrors.name}
                        />
                    </label>

                    <label>
                        Barcode
                        <input
                            value={barcode}
                            onChange={event =>
                                setBarcode(event.target.value)
                            }
                            required
                        />
                        <FieldError
                            message={validationErrors.barcode}
                        />
                    </label>

                    <label>
                        Brand
                        <input
                            value={brand}
                            onChange={event =>
                                setBrand(event.target.value)
                            }
                        />
                    </label>

                    <label>
                        Category
                        <input
                            value={category}
                            onChange={event =>
                                setCategory(event.target.value)
                            }
                        />
                    </label>

                    <label>
                        Starting quantity
                        <input
                            type="number"
                            min="0"
                            value={quantity}
                            onChange={event =>
                                setQuantity(
                                    Number(event.target.value)
                                )
                            }
                            required
                        />
                        <FieldError
                            message={validationErrors.quantity}
                        />
                    </label>

                    <label>
                        Reorder level
                        <input
                            type="number"
                            min="0"
                            value={reorderLevel}
                            onChange={event =>
                                setReorderLevel(
                                    Number(event.target.value)
                                )
                            }
                            required
                        />
                        <FieldError
                            message={
                                validationErrors.reorderLevel
                            }
                        />
                    </label>

                    <label>
                        Target stock
                        <input
                            type="number"
                            min="0"
                            value={targetStock}
                            onChange={event =>
                                setTargetStock(
                                    Number(event.target.value)
                                )
                            }
                            required
                        />
                        <FieldError
                            message={
                                validationErrors.targetStock
                            }
                        />
                    </label>

                    <label>
                        Unit cost
                        <input
                            type="number"
                            min="0"
                            step="0.01"
                            value={unitCost}
                            onChange={event =>
                                setUnitCost(event.target.value)
                            }
                            placeholder="0.00"
                        />
                        <FieldError
                            message={validationErrors.unitCost}
                        />
                    </label>
                </div>

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
                        {busy ? "Adding product..." : "Add product"}
                    </button>
                </footer>
            </form>
        </dialog>
    );
}

interface FieldErrorProps {
    message?: string;
}

function FieldError({
    message
}: FieldErrorProps) {
    if (!message) {
        return null;
    }

    return (
        <span className="field-error">
            {message}
        </span>
    );
}