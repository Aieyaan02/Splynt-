import type {
    Account,
    InventoryMovement,
    ProductSettings,
    ApiError,
    CreateProductRequest,
    InventoryChangeRequest,
    LoginRequest,
    LoginResponse,
    Product,
    RegisterRequest,
    RegisterResponse
} from "../types";

export const SESSION_EXPIRED_EVENT = "splynt:session-expired";

const TOKEN_KEY = "splynt.accessToken";

export class ApiRequestError extends Error {
    readonly status: number;
    readonly validationErrors: Record<string, string>;

    constructor(
        message: string,
        status: number,
        validationErrors: Record<string, string> = {}
    ) {
        super(message);

        this.name = "ApiRequestError";
        this.status = status;
        this.validationErrors = validationErrors;
    }
}

export function getAccessToken(): string | null {
    return sessionStorage.getItem(TOKEN_KEY);
}

export function setAccessToken(token: string): void {
    sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearAccessToken(): void {
    sessionStorage.removeItem(TOKEN_KEY);
}

async function request<T>(
    path: string,
    options: RequestInit = {},
    authenticated = true
): Promise<T> {
    const headers = new Headers(options.headers);

    headers.set("Accept", "application/json");

    if (options.body !== undefined) {
        headers.set("Content-Type", "application/json");
    }

    const requestToken = authenticated ? getAccessToken() : null;
    if (authenticated) {
        const token = requestToken;

        if (token) {
            headers.set(
                "Authorization",
                `Bearer ${token}`
            );
        }
    }

    const response = await fetch(path, {
        ...options,
        headers
    });

    // A late failure from an older session must not clear a newer login.
    if (response.status === 401 && authenticated && requestToken
        && getAccessToken() === requestToken) {
        clearAccessToken();
        window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    }

    const contentType =
        response.headers.get("content-type") ?? "";

    let responseBody: unknown = null;

    if (contentType.includes("application/json")) {
        try {
            responseBody = await response.json();
        } catch {
            // Keep HTTP status semantics even when a proxy sends malformed JSON.
            if (response.ok) throw new ApiRequestError("The server returned an unreadable response. Try again.", response.status);
        }
    } else if (response.status !== 204) {
        const text = await response.text();
        responseBody = text || null;
    }

    if (!response.ok) {
        const apiError = isApiError(responseBody)
            ? responseBody
            : null;

        throw new ApiRequestError(
            apiError?.message
                ?? `Request failed with status ${response.status}`,
            response.status,
            apiError?.validationErrors ?? {}
        );
    }

    return responseBody as T;
}

function isApiError(value: unknown): value is ApiError {
    return (
        typeof value === "object"
        && value !== null
        && (
            "message" in value
            || "status" in value
            || "validationErrors" in value
        )
    );
}

export const authApi = {
    login(requestBody: LoginRequest): Promise<LoginResponse> {
        return request<LoginResponse>(
            "/api/auth/login",
            {
                method: "POST",
                body: JSON.stringify(requestBody)
            },
            false
        );
    },

    register(
        requestBody: RegisterRequest
    ): Promise<RegisterResponse> {
        return request<RegisterResponse>(
            "/api/auth/register",
            {
                method: "POST",
                body: JSON.stringify(requestBody)
            },
            false
        );
    }
};

export const accountApi = {
    getCurrent(): Promise<Account> {
        return request<Account>("/api/me");
    }
};

export const productApi = {
    settings(storeId: number, productId: number, body: ProductSettings): Promise<Product> {
        return request(`/api/stores/${storeId}/products/${productId}/settings`, { method: "PATCH", body: JSON.stringify(body) });
    },
    getAll(storeId: number): Promise<Product[]> {
        return request<Product[]>(
            `/api/stores/${storeId}/products`
        );
    },

    getArchived(storeId: number): Promise<Product[]> {
        return request<Product[]>(
            `/api/stores/${storeId}/products/archived`
        );
    },

    getLowStock(storeId: number): Promise<Product[]> {
        return request<Product[]>(
            `/api/stores/${storeId}/products/low-stock`
        );
    },

    create(
        storeId: number,
        requestBody: CreateProductRequest
    ): Promise<Product> {
        return request<Product>(
            `/api/stores/${storeId}/products`,
            {
                method: "POST",
                body: JSON.stringify(requestBody)
            }
        );
    },

    archive(
        storeId: number,
        productId: number
    ): Promise<void> {
        return request<void>(
            `/api/stores/${storeId}/products/${productId}`,
            {
                method: "DELETE"
            }
        );
    },

    restore(
        storeId: number,
        productId: number
    ): Promise<Product> {
        return request<Product>(
            `/api/stores/${storeId}`
                + `/products/${productId}/restore`,
            {
                method: "POST"
            }
        );
    }
};

export const inventoryApi = {
    history(storeId: number, productId: number): Promise<InventoryMovement[]> {
        return request(`/api/stores/${storeId}/products/${productId}/inventory/movements`);
    },
    recordSale(
        storeId: number,
        productId: number,
        requestBody: InventoryChangeRequest
    ): Promise<unknown> {
        return request<unknown>(
            `/api/stores/${storeId}`
                + `/products/${productId}/inventory/sales`,
            {
                method: "POST",
                body: JSON.stringify(requestBody)
            }
        );
    },

    recordRestock(
        storeId: number,
        productId: number,
        requestBody: InventoryChangeRequest
    ): Promise<unknown> {
        return request<unknown>(
            `/api/stores/${storeId}`
                + `/products/${productId}/inventory/restocks`,
            {
                method: "POST",
                body: JSON.stringify(requestBody)
            }
        );
    }
};
export interface CloverConnection {
    connected: boolean;
    merchantId: string | null;
    lastSyncedAt: string | null;
    lastSyncError: string | null;
    issueCount: number;
    issues: { itemId: string | null; name: string | null; barcode: string | null; reason: string; nextStep: string }[];
}

export const cloverApi = {
    status(storeId: number): Promise<CloverConnection> {
        return request(`/api/stores/${storeId}/integrations/clover`);
    },
    connect(storeId: number): Promise<{ authorizationUrl: string }> {
        return request(`/api/stores/${storeId}/integrations/clover/connect`, { method: "POST" });
    },
    sync(storeId: number): Promise<unknown> {
        return request(`/api/stores/${storeId}/integrations/clover/sync`, { method: "POST" });
    }
};

export interface ContactInquiry { id: number; name: string; email: string; business: string | null; message: string; createdAt: string; }
export const operationsApi = {
    inquiries(page: number): Promise<{ content: ContactInquiry[]; totalPages: number; number: number }> {
        return request(`/api/operations/inquiries?page=${page}`);
    }
};

export interface SalesInsights {
    storeName: string; location: string; timezone: string; lastSyncedAt: string | null; syncError: string | null;
    from: string; untilExclusive: string; methodology: string; seasonalStatus: string;
    history: { metric?: string; completeMonths: number; sufficientForRecurringPatterns: boolean; status: string; methodology: string;
        months: { month: string; complete: boolean; days: number; orders: number | null; ordersPerDay?: number | null; units?: number | null; unitsPerDay?: number | null }[];
        comparisons: { month: string; previousMonth: string; sufficient: boolean; dailyOrdersChangePercent?: number | null; dailyUnitsChangePercent?: number | null }[];
        recurringPatterns: { calendarMonth: number; direction: string; earlierIndex: number; latestIndex: number }[];
        productComparisons: { unit?: string; productId: number; name: string; month: string; previousMonth: string; units: number | null; previousUnits: number | null; orders: number; previousOrders: number; sufficient: boolean; dailyUnitsChangePercent: number | null }[];
    };
    summary: { completeDays: number; orders: number; units: number; sufficientForVelocity: boolean;
        topProducts: { productId: number; name: string; units: number | null; unitsPerDay: number | null; estimatedDaysRemaining: number | null; currentStock: number | null; orders: number; unit: string }[];
        hourlyUnits: number[]; weekdayUnits: number[]; hourlyOrders: number[]; weekdayOrders: number[]; };
}
export const insightsApi = {
    read(storeId: number): Promise<SalesInsights> { return request(`/api/stores/${storeId}/insights`); }
};

export interface StoreSettings {
    id?: number; version?: number; name: string; city: string | null; state: string | null;
    countryCode: string; timezone: string; currencyCode: string;
}
export const storeApi = {
    read(id: number): Promise<StoreSettings> { return request(`/api/stores/${id}/settings`); },
    update(id: number, data: StoreSettings): Promise<StoreSettings> { return request(`/api/stores/${id}/settings`, { method: "PATCH", body: JSON.stringify(data) }); },
    create(organizationId: number, data: StoreSettings): Promise<StoreSettings> { return request(`/api/organizations/${organizationId}/stores`, { method: "POST", body: JSON.stringify(data) }); }
};

export interface AdviceView {
    configured: boolean; canGenerate: boolean; message: string | null; generatedAt: string | null;
    nextGenerationAt: string | null; model: string | null; error: string | null;
    report: { summary: string; actions: { title: string; rationale: string; nextStep: string; evidenceIds: string[] }[];
        productExperiments: { productIdea: string; hypothesis: string; smallTest: string; measure: string; evidenceIds: string[] }[]; limitations: string[] } | null;
    evidence: Record<string, unknown> | null;
}
export const adviceApi = {
    read(storeId: number): Promise<AdviceView> { return request(`/api/stores/${storeId}/advice`); },
    generate(storeId: number): Promise<AdviceView> { return request(`/api/stores/${storeId}/advice`, { method: "POST" }); }
};
