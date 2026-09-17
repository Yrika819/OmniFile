# POC-003 — Archive Engine Matrix — Plan

Status: COMPLETE

Architecture authority: `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`
Branch: `poc/archive-engines-v1`

This is a disposable proof of concept. All identifiers, dependency versions, scripts and harness structure are **POC-ONLY — NOT PRODUCTION AUTHORITY**.

The PoC measures Commons Compress, Zip4j and Junrar against generated archive fixtures on a host JVM and a physical Pixel 7a running Android 16 / API 36. It covers normal, large-directory, encrypted, split, solid, multipart, malformed, path-containment, link, and bounded-expansion cases. libarchive remains an alternative native candidate and was not built in this PoC.

The PoC does not freeze production dependencies, module structure, package names, SDK versions, native packaging, archive APIs, or release architecture.