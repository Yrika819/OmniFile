# P05-004 Final Blocker Closure and Technology-Freeze Decision Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Keep the skeptical reviewer and final review-agent capacity reserved until the end.

**Goal:** Reconcile the published P05-004 evidence at `516308346d36c1c958a2bfa85a5fb1a2412379d9`, close only blockers supported by fresh evidence, and return an explicit `YES`, `YES_WITH_EXPLICIT_GATES`, or `NO` decision for CORE_V1 technology-family readiness without initializing production OmniFile.

**Scope:** Documentation and disposable evidence only on `poc/core-readiness-archive-v1`, followed by a documentation-only synthesis update from `4373d2d6b724d7f65442d0e2da0a839995dbb148` if the archive result is final. No production module, dependency declaration, SDK/Kotlin/AGP/JDK freeze, release signing, or Technology Freeze execution.

## Protected authority and invariants

- Preserve architecture `6ec9e1037d0fd86afebdec6bd1a5be67b008ccbb`, original Architecture V1 `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`, research `b03a2ea99f24206f847f513fa4106e90268f3fc4`, main `793d151f9608a684c1d0d4be2e58e5b49d26f823`, and P05-001/002/003 SHAs.
- Preserve `tools/p05-004/fixtures/duplicate.tsv` and SHA `664940182390f957ad28d5b10985a45584e172c8eafc0588b22480cc3bfa54be`.
- Keep `UNKNOWN`, `NOT_TESTED`, `NOT_CLOSED`, and `NO-GO` explicit; do not infer legal approval, provider neutrality, parser security, or 16 KiB runtime acceptance.
- Use the one installed API 36 `google_apis_ps16k/x86_64` image and isolated AVD only; do not install another image or mutate the physical device.

## Bounded work

1. Recheck the 16 KiB AVD identity and, once only, attempt the exact aligned APK. Record package-manager/system-server instability as an environment blocker if the app cannot be launched with trustworthy evidence.
2. Re-audit Junrar 8.1.1 against the architecture RAR read/extract requirement. Distinguish `FAMILY_FREEZE_BLOCKING` from `RAR_FEATURE_FREEZE_BLOCKING_ONLY` using authority, not preference; retain `EXTERNAL_LICENSE_REVIEW_REQUIRED` unless formal legal review exists.
3. Reconcile zstd-jni 1.5.7-17 artifact/source/build provenance, native inventory, APK alignment, 4 KiB Pixel evidence, and 16 KiB AVD results. Do not treat static ELF alignment as runtime proof.
4. State the narrowest retained format/provider stack and classify spooling, seekability, containment, cancellation, cleanup, lifecycle, normalization, security, and human-acceptance gaps as family blockers, implementation gates, or release gates.
5. Preserve the libarchive machine guard (`UNRESOLVED`) and the separate final disposition (`NOT_JUSTIFIED_FOR_CORE_V1`) unless a concrete Java-first CORE_V1 gap is demonstrated; do not weaken the guard to manufacture a pass.
6. Integrate only verified findings into the existing P05-004 documents, run the complete harness and fresh diff/reference checks, obtain one skeptical review and one final review-agent result while reserving capacity for both, then publish once normally and verify local/remote equality.
7. If P05 is `NO`, update only the three synthesis readiness documents and stop. Never create a Technology-Freeze branch or production scaffold.

## Required completion record

The final report must cover the 56 requested fields: start/final SHAs, commits, skills, subagents and reserved reviewers, Junrar and RAR severity, zstd source/build/image/runtime/APK/native/parser evidence, per-format/provider/spool/security/lifecycle gates, libarchive result, retained stack, P05/synthesis readiness and exact blocker, publication equality, protected refs, duplicate SHA, and unchanged OmniFile/com.omnifile production boundary.

## Verification commands

```sh
git status --short --branch
./tools/p05-004/test_archive_evidence_matrix.sh
git diff --check
git ls-remote origin refs/heads/poc/core-readiness-archive-v1 refs/heads/poc/p05-core-readiness-synthesis
```

