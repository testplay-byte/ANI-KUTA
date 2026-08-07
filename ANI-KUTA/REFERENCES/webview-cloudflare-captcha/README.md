# WebView, Cloudflare, and Captcha Management Research

This folder is for researching and documenting:
- WebView integration for extension sources that require JavaScript rendering
- Cloudflare bypass/challenge handling
- Captcha management systems
- Cookie/session management for authenticated sources

## Key Questions
- How does the old project handle WebView-based sources?
- What Cloudflare bypass mechanisms are available?
- How are captchas presented to the user and solved?
- How are cookies persisted across WebView sessions?

## Old Project References
- Check `core/source-api/.../network/` for WebView helpers
- Check extension sources that use WebView (e.g. AniKotoS uses WebViewFetcher)
- Check for CloudflareInterceptor in the old project's network layer

## TODO
- [ ] Research old project's WebViewFetcher implementation
- [ ] Document Cloudflare bypass approach
- [ ] Document captcha management approach
- [ ] Plan integration into the new project's :core:network module

