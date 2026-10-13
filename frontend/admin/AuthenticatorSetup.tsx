import {QRCodeSVG} from 'qrcode.react';
import {authenticatorUri} from './authenticatorUri';

export function AuthenticatorSetup({secret}: {secret: string}) {
  let uri: string;
  try { uri = authenticatorUri(secret); }
  catch { return <p role="alert">The setup key format is invalid. Sign in again to restart setup.</p>; }
  return <section aria-label="Authenticator setup">
    <p>In Duo Mobile, tap Add, then Use QR code. Scan this code with your phone.</p>
    <div style={{textAlign: 'center', margin: '16px 0'}}>
      <QRCodeSVG value={uri} size={240} marginSize={4} level="M"
        bgColor="#ffffff" fgColor="#000000" role="img"
        title="Private Splynt Admin authenticator setup QR code"
        style={{maxWidth: '100%', height: 'auto'}}/>
    </div>
    <p>Enter the six-digit code from Duo below to finish setup. Complete this within five minutes of signing in.</p>
    <details><summary>Cannot scan? Use the manual setup key</summary>
      <p>Add a time-based account named Splynt Admin.</p>
      <p style={{overflowWrap: 'anywhere'}}><code>{secret}</code></p>
    </details>
    <p>Keep this QR code and setup key private. The QR code is generated on this device.</p>
  </section>;
}
