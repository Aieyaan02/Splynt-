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

    /*
     * Keep number inputs as strings while the user edits them.
     * This allows the field to be completely empty instead of
     * immediately changing an empty value back to zero.
     */
    const [quantity, setQuantity] = useState("0");
    const [reorderLevel, setReorderLevel] = useState("5");
    const [targetStock, setTargetStock] = useState("20");
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

        const parsedQuantity =
            parseNonNegativeInteger(quantity);

        const parsedReorderLevel =
            parseNonNegativeInteger(reorderLevel);

        const parsedTargetStock =
            parseNonNegativeInteger(targetStock);

        const parsedUnitCost =
            unitCost.trim() === ""
                ? null
                : Number(unitCost);

        const fieldErrors: Record<string, string> = {};

        if (parsedQuantity === null) {
            fieldErrors.quantity =
                "Starting quantity must be a whole number of zero or greater";
        }

        if (parsedReorderLevel === null) {
            fieldErrors.reorderLevel =
                "Reorder level must be a whole number of zero or greater";
        }

        if (parsedTargetStock === null) {
            fieldErrors.targetStock =
                "Target stock must be a whole number of zero or greater";
        }

        if (
            parsedUnitCost !== null
            && (
                !Number.isFinite(parsedUnitCost)
                || parsedUnitCost < 0
            )
        ) {
            fieldErrors.unitCost =
                "Unit cost must be zero or greater";
        }

        if (Object.keys(fieldErrors).length > 0) {
            setValidationErrors(fieldErrors);
            return;
        }

        /*
         * These values cannot be null here because the validation
         * above would have returned from the function.
         */
        const validQuantity = parsedQuantity as number;
        const validReorderLevel =
            parsedReorderLevel as number;
        const validTargetStock =
            parsedTargetStock as number;

        if (validTargetStock < validReorderLevel) {
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
            quantity: validQuantity,
            reorderLevel: validReorderLevel,
            targetStock: validTargetStock,
            unitCost: parsedUnitCost
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
                            step="1"
                            inputMode="numeric"
                            value={quantity}
                            onFocus={event =>
                                event.currentTarget.select()
                            }
                            onChange={event =>
                                setQuantity(
                                    normalizeWholeNumberInput(
                                        event.target.value
                                    )
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
                            step="1"
                            inputMode="numeric"
                            value={reorderLevel}
                            onFocus={event =>
                                event.currentTarget.select()
                            }
                            onChange={event =>
                                setReorderLevel(
                                    normalizeWholeNumberInput(
                                        event.target.value
                                    )
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
                            step="1"
                            inputMode="numeric"
                            value={targetStock}
                            onFocus={event =>
                                event.currentTarget.select()
                            }
                            onChange={event =>
                                setTargetStock(
                                    normalizeWholeNumberInput(
                                        event.target.value
                                    )
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
                            inputMode="decimal"
                            value={unitCost}
                            onFocus={event =>
                                event.currentTarget.select()
                            }
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
                        {busy
                            ? "Adding product..."
                            : "Add product"}
                    </button>
                </footer>
            </form>
        </dialog>
    );
}

function normalizeWholeNumberInput(
    value: string
): string {
    if (value === "") {
        return "";
    }

    /*
     * Convert values such as 010 into 10 while still permitting
     * a single zero.
     */
    return value.replace(/^0+(?=\d)/, "");
}

function parseNonNegativeInteger(
    value: string
): number | null {
    if (value.trim() === "") {
        return null;
    }

    const parsedValue = Number(value);

    if (
        !Number.isInteger(parsedValue)
        || parsedValue < 0
    ) {
        return null;
    }

    return parsedValue;
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