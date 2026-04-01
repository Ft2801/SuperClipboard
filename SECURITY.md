# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.x     | Yes       |

## Reporting a Vulnerability

If you discover a security vulnerability in SuperClipboard, do not open a public issue.

Instead, contact the maintainer directly with:

1. A description of the vulnerability
2. Steps to reproduce
3. Potential impact assessment

You will receive acknowledgment within 48 hours and a detailed response within 7 days.

## Security Considerations

### Accessibility Service

The `PasteAccessibilityService` has `canRetrieveWindowContent="true"`, which grants it the ability to read content from any window. The service:

- Does NOT log, store, or transmit content from other applications
- Only reads its own database content for injection purposes
- Only accesses `AccessibilityNodeInfo` to find editable fields

### Data Storage

- All user text is stored locally in an encrypted-capable SQLite database via Room
- No data is transmitted to external servers
- Temporary cache files created for sharing are cleaned up on subsequent share operations

### Overlay Permission

The `SYSTEM_ALERT_WINDOW` permission allows drawing over other apps. The overlay:

- Displays only the floating button and text selection panel
- Does not capture screenshots, record input, or intercept touches outside its own views