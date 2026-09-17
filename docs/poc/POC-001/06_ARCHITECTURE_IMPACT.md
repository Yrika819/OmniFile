# POC-001 — Architecture Impact

Architecture authority remains `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`. This document is evidence only.

| Architecture V1 decision | Classification | Evidence |
|---|---|---|
| Capability-based storage model | CONFIRMED | Direct files, local SAF documents and provider FIFO expose different measured capability sets. |
| Provider-scoped opaque identity | CONFIRMED | SAF URI/documentId changed on rename/move; direct path changed while observed fileKey stayed stable. |
| Path string as universal identity rejected | CONFIRMED | Direct and SAF identity/presentation changed differently. |
| Sequential / seekable / random access separated | CONFIRMED | FIFO sequential read succeeded while `lseek` failed; tested local SAF was seekable. |
| FD presence does not imply seekability | CONFIRMED | Same API surface produced REGULAR seekable FDs and FIFO non-seekable FDs. |
| Provider-specific write/name semantics | CONFIRMED | Local SAF sanitized requested names that direct storage rejected. |
| Provider lifecycle/reconnect first-class | UNAFFECTED / STILL POC_REQUIRED | Removable/cloud disconnect not tested. |
| Unknown metadata stays unknown | CONFIRMED | Synthetic pipe did not require fabricated meaningful file size. |
| Exact SAF adapter semantics | REFINED | Tree URI must be converted to document URI; tested provider changes URI/documentId on rename/move and can sanitize names. |
| Rename atomicity never inferred from method name | CONFIRMED | Success/identity was measured, atomicity not generalized. |

## Challenged / contradicted

No Architecture V1 principle was contradicted.

## Safe to carry into later freeze discussion

- capability-based/provider-scoped abstraction;
- descriptor and seekability as separate facts;
- no stable-URI assumption across SAF rename/move;
- name preservation is provider-dependent;
- persisted grants are durable state worth modeling.

## Unsafe to freeze from this PoC alone

- universal SAF seekability;
- removable/cloud identity and reconnect semantics;
- explicit grant-revocation behavior;
- universal atomic rename/move guarantees.

Production interfaces remain unfrozen.
