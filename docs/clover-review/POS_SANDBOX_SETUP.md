# POS sandbox connection acceptance

The pilot imports inventory read-only. No real store or paid deployment is required for development.

## Configuration

Use the sandbox app and merchant only. Configure client ID, client secret, encryption key and redirect URI through private environment variables, never Git. Use a single browser-facing origin for the frontend and /api proxy so the OAuth binding cookie returns to the callback.

- Alternate Launch Path: `/api/integrations/clover/launch`
- Callback: `/api/integrations/clover/connect`
- Site URL: the sandbox review deployment origin approved/configured in Clover.

Launch redirects to sign-in/registration. Choose the correct Splynt store and explicitly click Connect your POS system. Incoming merchant IDs, authorization codes and redirect URLs on the launch endpoint are not trusted or used to connect a store.

## Acceptance checklist

1. Launch from the sandbox merchant dashboard; sign in as a store owner/admin.
2. Select the intended store, connect, and approve only the required read permissions.
3. Check connection status; Sync now and compare representative names, stock quantities, archived items and unknown stock against Clover.
4. Try a second organization account and an employee: neither may manage this store connection.
5. Disconnect: confirm tokens are cleared, future sync cannot access Clover, and the saved catalog remains visible as stale data.
6. A previously opened authorization flow must fail after disconnect. Reconnect the same merchant with a new flow and sync again.
7. Attempt another merchant against that store: reject to avoid mixing retained catalogs. Use a separate Splynt store for another merchant.

Disconnect retains the merchant association and inventory/history. It is not account deletion, remote token revocation or Clover app uninstall. A sync already running finishes before disconnect acquires the store lock; no new inventory sync can fetch after disconnect completes. Provider-side uninstall notifications and revoked-access behavior still need implementation/acceptance before release.

Automated tests use a mock provider and synthetic credentials. They do not prove live Clover consent or app approval.
