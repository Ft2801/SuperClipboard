# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-03-30

### Added

- Initial release
- Room Database storage for massive text entries
- Chunked text injection via Accessibility Service
- Floating overlay button and text selection panel via WindowManager
- In-app notes manager with CRUD operations
- File import via ACTION_SEND intent filter with buffered stream reading
- File export via Storage Access Framework with 20 supported extensions
- Sharing via plain Intent with automatic FileProvider fallback for large texts
- Onboarding screen with guided permission setup
- Search functionality for saved notes
- Injection progress notification and in-panel progress bar
- Material Design 3 UI with dynamic color support
- Fallback clipboard paste injection for incompatible target views