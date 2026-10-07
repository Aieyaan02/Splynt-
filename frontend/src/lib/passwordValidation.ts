export function passwordEncodingError(password: string): string | null {
    return new TextEncoder().encode(password).length > 72
        ? "This password is too long. Emoji and some letters take extra space; use a shorter password (at most 72 UTF-8 bytes)."
        : null;
}
