# Security

## Reporting a vulnerability

Please don't open a public issue. Report it privately via [GitHub's vulnerability reporting](https://github.com/mc51/Clipster-Android/security/advisories/new) instead.

## How Clipster protects your clips

- **Encryption on the device:** clips are encrypted with [Fernet](https://github.com/fernet/spec) (AES-128-CBC with HMAC-SHA256) before they are sent. The server only stores encrypted clips, the key never leaves your devices.
- **Keys from your password:** Clipster derives two PBKDF2-SHA256 hashes from your password. One (20,000 iterations) is your login for the server. The other (10,000 iterations) is the encryption key. Your password itself is never sent or stored.
- **On the device:** the two hashes are stored in the app's private storage and excluded from backups and device transfers.
- **In transit:** all connections use HTTPS with the certificates Android trusts. A self-signed certificate is only accepted if you enabled it and confirmed its fingerprint, and then only that exact certificate.

## Known limitations

- The salt is derived from your username and password, and the iteration counts are low. Both are needed for compatibility with the other Clipster clients. Whoever runs the server receives your login hash and could try to guess your password offline, which would also reveal the encryption key. **Use a strong, unique password**, or run your own server.
- The server can see metadata: your username, when you share, the size and format (text or image) of clips, and the device name.
