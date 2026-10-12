/** Neutral wording for provider-generated system messages, never customer content. */
export function providerCopy(message: string): string {
    return message.replace(/\bClover\b/g, "POS provider");
}
