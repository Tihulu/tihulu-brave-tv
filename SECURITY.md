# Security Policy

## Supported versions

This project is pre-release. Only the latest `main` branch is intended for testing.

## Browser-specific security policy

Do not solve TV compatibility by disabling Chromium/Brave sandboxing, Site Isolation, origin security, certificate validation, Safe Browsing/Brave protections or renderer process separation.

A change that causes renderer/browser-process crashes, repeatable ANRs, unsafe intent exposure, or unexpected permission broadening is a release blocker.

## Reporting

For vulnerabilities introduced by this TV layer, open a private GitHub security advisory when repository security advisories are available. Do not publish proof-of-concept exploit details in a public issue before a fix is available.

For vulnerabilities that reproduce in unmodified Brave or Chromium, report them through the appropriate upstream security process.
