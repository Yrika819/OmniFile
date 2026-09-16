# First-Class Cloud Storage Research

Status: COMPLETE — API/OAuth research only; no credentials registered  
Last verified: 2026-09-17

## Classification legend

**FACT** documented behavior; **INFERENCE** derived consequence; **RECOMMENDATION** proposed direction; **UNRESOLVED** requires prototype/provider-account evidence.

## Executive finding

Google Drive, Microsoft OneDrive and Dropbox should be modeled as first-class providers when advanced behavior matters. SAF/document-provider access remains valuable as a generic fallback, but it cannot reliably expose provider-specific search, change feeds, resumable upload sessions, conflict/version controls, shared-drive/team semantics or server-side operations.

No OAuth client IDs, secrets or production registrations were created during this research.

# Google Drive

## API

**FACT:** Google Drive API v3 is the current REST API for files, folders, shared drives, permissions and metadata.

Sources:
- https://developers.google.com/workspace/drive/api/guides/about-sdk
- https://developers.google.com/workspace/drive/api/reference/rest/v3
- https://developers.google.com/workspace/drive/api/reference/rest/v3/files

## OAuth/scopes

**FACT:** Google documents `drive.file` as a recommended non-sensitive per-file scope for many use cases. Broad `drive` and `drive.readonly` access are restricted scopes and can trigger substantially greater verification obligations for a public app.

**FACT:** Restricted Drive scopes are limited to qualifying use cases and can require restricted-scope verification; storing/transmitting restricted-scope data can add security-assessment obligations.

Source:
- https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification

**INFERENCE:** A full first-class file manager that lets users browse arbitrary Drive content may need broader access than `drive.file`, depending on UX. That decision has distribution/verification consequences and must not be made casually.

**RECOMMENDATION:** Prototype the narrowest viable scope strategy and evaluate Google Picker/per-file access before requesting broad Drive scopes.

## Core capabilities

Drive API provides:
- list/search with query/pagination;
- metadata IDs and parent relationships;
- download/export for appropriate file types;
- resumable uploads;
- metadata rename/move by updating name/parents;
- server-side file copy;
- trash/delete;
- shared-file/shared-drive support with API-specific flags;
- change tracking APIs.

Sources:
- list/search: https://developers.google.com/workspace/drive/api/guides/search-files
- download: https://developers.google.com/workspace/drive/api/guides/manage-downloads
- uploads: https://developers.google.com/workspace/drive/api/guides/manage-uploads
- copy: https://developers.google.com/workspace/drive/api/reference/rest/v3/files/copy
- shared drives: https://developers.google.com/workspace/drive/api/guides/manage-shareddrives
- changes: https://developers.google.com/workspace/drive/api/guides/manage-changes

## Quotas

**FACT:** Drive API quota rules changed for new projects starting 2026-05-01 and use quota units. Exact project quota must be read from the active Cloud project rather than hard-coded into architecture.

Source:
- https://developers.google.com/workspace/drive/api/guides/limits

**RECOMMENDATION:** Provider layer must classify rate-limit/quota responses and use server-recommended retry/backoff behavior rather than fixed sleeps.

# Microsoft OneDrive

## API/authentication

**FACT:** OneDrive is exposed through Microsoft Graph. Android interactive authentication should use Microsoft Authentication Library (MSAL), not legacy ADAL. Mobile applications are public clients and should not embed a client secret.

Sources:
- https://learn.microsoft.com/en-us/entra/msal/android/
- https://learn.microsoft.com/en-us/entra/msal/overview
- https://learn.microsoft.com/en-us/graph/auth/

## Permissions

**FACT:** Delegated scopes include `Files.Read`, `Files.ReadWrite`, broader `.All` variants, and special app-folder/selected models with account-type limitations. Least privilege should be used.

Sources:
- https://learn.microsoft.com/en-us/onedrive/developer/rest-api/concepts/permissions_reference
- https://learn.microsoft.com/en-us/graph/permissions-reference

## Core capabilities

Microsoft Graph DriveItem APIs provide:
- list children/search;
- metadata and IDs;
- file download;
- byte-range download via the actual download URL when supported, including HTTP 206 behavior;
- resumable upload sessions;
- rename/move;
- copy APIs;
- delete/trash semantics depending endpoint/account;
- sharing metadata;
- delta queries for incremental change tracking.

Sources:
- DriveItem: https://learn.microsoft.com/en-us/graph/api/resources/driveitem
- content download: https://learn.microsoft.com/en-us/graph/api/driveitem-get-content
- upload sessions: https://learn.microsoft.com/en-us/graph/api/driveitem-createuploadsession
- move: https://learn.microsoft.com/en-us/graph/api/driveitem-move
- copy: https://learn.microsoft.com/en-us/graph/api/driveitem-copy
- delta: https://learn.microsoft.com/en-us/graph/api/driveitem-delta

**FACT:** Some operations have drive-boundary limitations; for example the documented DriveItem move endpoint does not provide arbitrary cross-Drive move as a simple atomic operation.

**RECOMMENDATION:** Preserve Drive ID/item ID as identity and model cross-drive moves as copy+verify+delete when no provider-native operation applies.

# Dropbox

## OAuth

**FACT:** Dropbox OAuth supports scoped access. Native/mobile apps should use Authorization Code + PKCE rather than embedding a secret. Short-lived access tokens and refresh tokens are used for long-lived/background access when authorized.

Source:
- https://developers.dropbox.com/oauth-guide

## Core file API

Dropbox exposes endpoints for:
- list folder + continuation cursor;
- metadata/search;
- download;
- upload;
- move/copy;
- delete;
- revisions/conflict metadata;
- shared resources according to scopes/account context.

Source:
- https://developers.dropbox.com/dbx-file-access-guide
- https://www.dropbox.com/developers/documentation/http/documentation

## Resumable upload

**FACT:** Dropbox upload sessions support files larger than a single request. Current API docs state that an individual request should not exceed 150 MiB, upload sessions can handle very large files, and a session has a finite lifetime (currently documented as seven days).

Source:
- https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/files/upload-session-start

**RECOMMENDATION:** Persist session identifiers/checkpoints only in protected operation state and be prepared for session expiration, after which restart/reconciliation is required.

# Common provider concerns

## Listing and pagination

All first-class APIs paginate large collections. UI/storage abstraction must therefore support incremental listing rather than forcing each provider to materialize entire directories before displaying the first row.

## Search

Provider-native search can outperform recursive client traversal and can cover metadata unavailable through generic file APIs. Search capabilities should be provider-advertised rather than faked by downloading/listing everything.

## Range/random download

**RECOMMENDATION:** Expose range/random read only where the provider endpoint or temporary download URL demonstrably supports it. It is especially important for media seeking and archive browsing.

## Resumable upload

Google Drive, OneDrive and Dropbox each have provider-specific resumable mechanisms. Their session tokens, chunk requirements, expiration and conflict rules differ.

**RECOMMENDATION:** Operation Manager should use a generic resumable-upload state concept while provider implementations persist their opaque provider token/state.

## Server-side copy/move

Do not implement same-provider copy by downloading then re-uploading when a provider-native operation exists. At the same time, do not assume server-side copy/move is available across accounts/drives/shared-drive boundaries.

## Metadata and modification conflicts

Useful remote version concepts include:
- etag/revision/generation-like values;
- modification time;
- provider-specific revision IDs.

**RECOMMENDATION:** Before resuming or overwriting, compare the strongest available remote version token. A changed source should pause/reconcile rather than quietly continue from an old byte offset.

## Offline cache

Separate:
- evictable metadata cache;
- evictable byte/block stream cache;
- user-requested offline copies/pins;
- durable operation partials.

A “clear cache” command must not delete user-requested offline files or operation recovery checkpoints unless explicitly selected.

## Rate limiting/backoff

Provider throttling should be a structured error with optional retry-after/server hints. Operation Manager can retry transient throttling with persisted state, but UI should surface prolonged quota/rate-limit blocks.

# First-class API vs SAF/document provider

| Capability | First-class cloud API | SAF/document provider wrapper |
|---|---|---|
| provider object ID | Yes | opaque document ID, provider-defined |
| provider-native global search | Usually | not universal |
| full pagination control | Yes | hidden/provider-specific |
| resumable upload sessions | Yes | not exposed generically |
| server-side copy/move | Often | only if provider advertises generic flags |
| conflict/version tokens | Richer | limited/generic metadata |
| shared drives/team spaces | Provider-native | representation varies |
| change/delta feed | Often | no universal SAF feed |
| range download | API-specific | FD/pipe/provider dependent |
| OAuth scope control | App owns it | document provider owns auth |
| implementation effort | High | Low |
| interoperability | Provider-specific | Broad generic access |

## What SAF-only loses

**INFERENCE:** Depending exclusively on SAF would sacrifice or obscure:
- reliable resumable upload;
- remote version/conflict controls;
- server-side operations;
- provider-native search;
- detailed quota/throttle information;
- shared/team-drive semantics;
- change feeds useful for indexing/offline state;
- direct control over range requests for playback/archive access.

## Recommended hybrid strategy

1. Support SAF as a generic interoperable source.
2. Add first-class Drive/OneDrive/Dropbox providers where their advanced features justify the auth/maintenance cost.
3. Keep identities/provider credentials isolated; do not automatically merge an SAF Drive document and first-class Drive object merely because names match.

## OAuth/token security

- Use system/browser-based authorization flows, not password collection.
- PKCE for native public-client flows where provider requires/supports it.
- Never embed client secrets in the APK as if they were confidential.
- Store refresh/token material through protected credential storage.
- Do not write tokens or signed download URLs to logs.
- Clear account credential references when the user disconnects provider.
- Validate redirect/deep-link inputs.

References:
- Google authorization: https://developers.google.com/identity/authorization/overview
- MSAL Android: https://learn.microsoft.com/en-us/entra/msal/android/
- Dropbox OAuth: https://developers.dropbox.com/oauth-guide

## Distribution implications

- Google broad Drive scopes may require restricted-scope verification/security assessment depending use.
- Microsoft permissions require user/admin consent according to account/tenant policy.
- Dropbox production apps must comply with its app/scopes/review policies.

**RECOMMENDATION:** Design least-privilege account linking before public distribution and document exactly why each scope is required.

## Prototype-required questions

1. Google Drive scope strategy: can desired full file-manager UX remain within narrower user-selected file access, or is restricted all-Drive scope truly required?
2. OneDrive personal vs work/school scope differences and shared resources.
3. Dropbox range download behavior and remote media seeking.
4. Resumable 100 GB upload interruption/restart for all three providers.
5. Rate-limit behavior under large recursive listing/indexing.
6. Conflict handling when remote source changes mid-download.
7. Media3 seek adapters using provider range APIs.
8. Mapping provider change feeds into optional local indexing.

## Recommendation summary

**Strong:** first-class providers + SAF fallback, rather than SAF-only.  
**Strong:** capability model for range, resumable upload, server copy/move, versioning and search.  
**Strong:** least-privilege OAuth and protected token storage.  
**Postpone:** exact OAuth scopes/client registrations, SDK vs raw REST choice, account-sync behavior and provider order until prototypes and distribution plan.
