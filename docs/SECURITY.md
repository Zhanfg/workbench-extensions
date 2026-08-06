# Extension Distribution Security

## Trust model

This repository is a public transport layer for Workbench extension metadata and binary packages. It is not the source of truth for private implementation code or signing secrets.

The Windows client must reject a package when any of the following checks fail:

1. catalog schema and extension identity;
2. allowed HTTPS host and repository path;
3. declared byte size;
4. SHA-256 digest;
5. ZIP central directory, file paths, compression method and CRC32;
6. package `integrity.json` entries;
7. supported host API and runtime type.

## Publication boundary

- No plugin source code is published here.
- No private key, Android signing key or release credential is stored here.
- Updates must retain immutable versioned package paths.
- Existing versioned package bytes must never be replaced. Publish a new version instead.

## Reporting

Do not publish exploitable details or secrets in a public issue. Contact the repository owner privately for security-sensitive reports.
