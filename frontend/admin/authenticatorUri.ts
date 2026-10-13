/** Standard TOTP provisioning; kept in memory and rendered locally. */
export function authenticatorUri(secret: string): string {
  if (!/^[A-Z2-7]{32}$/.test(secret)) throw new Error('Invalid authenticator setup key');
  const params = new URLSearchParams({secret, issuer: 'Splynt', algorithm: 'SHA1', digits: '6', period: '30'});
  return `otpauth://totp/Splynt:Platform%20Admin?${params}`;
}
