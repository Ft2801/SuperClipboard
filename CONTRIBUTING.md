
# Contributing to SuperClipboard

Thank you for your interest in contributing to SuperClipboard. This document outlines the process for submitting contributions and the standards expected for code quality.

## Getting Started

1. Fork the repository
2. Clone your fork locally
3. Create a feature branch from `main`
4. Make your changes
5. Test on a physical device (accessibility services do not work reliably on emulators)
6. Submit a pull request

## Branch Naming

Use the following conventions:

- `feature/description` for new features
- `fix/description` for bug fixes
- `docs/description` for documentation changes
- `refactor/description` for code restructuring

## Commit Messages

Follow conventional commit format:

```
type(scope): description

body (optional)
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `perf`

Examples:

```
feat(injection): add retry logic for failed chunks
fix(import): handle BOM in UTF-8 files
docs(readme): update build instructions for AGP 8.5
```

## Code Standards

- Kotlin style follows the official Kotlin coding conventions
- All public functions and classes must have KDoc comments
- Database operations must use coroutines; never block the main thread
- UI state must flow through the ViewModel via StateFlow
- No hardcoded strings in Compose UI; use string resources
- Floating overlay code (WindowManager) must use XML layouts, not Compose

## Testing

- Test file import with files of varying sizes: 1KB, 1MB, 10MB, 50MB
- Test injection into at least three different target apps (e.g., Chrome, WhatsApp, Samsung Notes)
- Verify that `TransactionTooLargeException` does not occur at any stage
- Test the floating button drag behavior and panel dismissal
- Test on both stock Android and at least one OEM skin (Samsung, Xiaomi)

## Pull Request Requirements

- Description of the change and its motivation
- List of devices and Android versions tested
- Screenshots or screen recordings for UI changes
- No merge conflicts with `main`

## Reporting Issues

Use the issue templates provided. Include:

- Device model and Android version
- Steps to reproduce
- Expected vs actual behavior
- Logcat output if applicable (filter by `SuperClipboard` tag)

## Code of Conduct

All contributors are expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).