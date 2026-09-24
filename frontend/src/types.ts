export type MembershipRole =
    | "OWNER"
    | "ADMIN"
    | "MANAGER"
    | "EMPLOYEE";

export type SubscriptionPlan =
    | "FREE"
    | "STARTER"
    | "PROFESSIONAL"
    | "ENTERPRISE";

export type SubscriptionStatus =
    | "TRIALING"
    | "ACTIVE"
    | "PAST_DUE"
    | "CANCELED";

export interface LoginRequest {
    email: string;
    password: string;
}

export interface LoginResponse {
    accessToken: string;
    tokenType: "Bearer";
    expiresInSeconds: number;
    expiresAt: string;
    userId: number;
    email: string;
    firstName: string;
    lastName: string;
}

export interface RegisterRequest {
    email: string;
    password: string;
    firstName: string;
    lastName: string;
    organizationName: string;
    organizationSlug: string;
    storeName: string;
    storeSlug: string;
}

export interface RegisterResponse {
    userId: number;
    email: string;
    firstName: string;
    lastName: string;
    organizationId: number;
    organizationName: string;
    organizationSlug: string;
    storeId: number;
    storeName: string;
    storeSlug: string;
    role: MembershipRole;
}

export interface StoreSummary {
    id: number;
    name: string;
    slug: string;
    timezone: string;
    currencyCode: string;
    countryCode: string;
}

export interface OrganizationAccess {
    id: number;
    name: string;
    slug: string;
    role: MembershipRole;
    subscriptionPlan: SubscriptionPlan;
    subscriptionStatus: SubscriptionStatus;
    trialEndsAt: string | null;
    stores: StoreSummary[];
}

export interface Account {
    id: number;
    email: string;
    firstName: string;
    lastName: string;
    fullName: string;
    emailVerified: boolean;
    lastLoginAt: string | null;
    organizations: OrganizationAccess[];
}

export type ProductSource =
    | "MANUAL"
    | "CLOVER"
    | "SYSTEM";

export interface Product {
    id: number;
    storeId: number;
    barcode: string;
    name: string;
    brand: string | null;
    category: string | null;
    quantity: number;
    reorderLevel: number;
    targetStock: number;
    unitCost: number | null;
    cloverItemId: string | null;
    source: ProductSource;
    active: boolean;
    lowStock: boolean;
    suggestedReorderQuantity: number;
    createdAt: string;
    updatedAt: string;
}

export interface CreateProductRequest {
    barcode: string;
    name: string;
    brand: string | null;
    category: string | null;
    quantity: number;
    reorderLevel: number;
    targetStock: number;
    unitCost: number | null;
}

export interface InventoryChangeRequest {
    quantity: number;
    note: string | null;
}

export interface ApiError {
    timestamp?: string;
    status?: number;
    error?: string;
    message?: string;
    path?: string;
    validationErrors?: Record<string, string>;
}