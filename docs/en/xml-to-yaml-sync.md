# Syncing XML Changes Back to YAML: A Decision Guide

[Back to documentation home](README.md) · [The relationship between the three artifacts](yaml-xml-html.md)

External tools may take part in design editing, but once each write-back completes, canonical YAML remains the only authoritative source. This guide helps maintainers decide when to sync, how to review the risks, and where to stop when something goes wrong.

**The authoritative operating specification for parameters, identity matching rules, deletion authorization, reports, and rollback is the [Skill sync specification](../../skills/dec-design/references/xml-yaml-sync.md).** That specification's commands are run from the `skills/dec-design/` directory, and its input and output paths must be adjusted to the actual project; the [development and verification manual](development-and-verification.md) in this directory is run from the repository root.

## 1. First decide which kind of change you are making

| Current situation | Choice |
| --- | --- |
| Only XML exists; no canonical YAML has been set up yet | Follow the [initial import specification](../../skills/dec-design/references/xml-to-yaml.md) to establish the design source |
| YAML exists, and an external tool modified the corresponding XML | Follow the [sync specification](../../skills/dec-design/references/xml-yaml-sync.md) to merge back into the existing design |
| You are editing the business design directly inside the repository | Change canonical YAML, then derive XML, HTML, and code |
| You want YAML and XML edited independently, overwriting each other in real time | Agree on delivery versions and write-back windows first; the current tooling provides no dual-master collaboration |

Once YAML exists, a full reverse import cannot replace sync, or stable identities and YAML-only metadata may be lost. Files prepared for external editing should keep identity information per the [XML export specification](../../skills/dec-design/references/yaml-to-xml.md), and the corresponding design version should be recorded.

## 2. After receiving external changes, protect the originals first

**Save the external XML elsewhere before doing any regeneration.** Running YAML→XML generation first on already-modified files overwrites content that has not been written back yet with the old design; re-exporting "to add identity comments" carries the same risk.

Confirm which YAML the XML corresponds to, which version it was exported from, and whether the repository changed the design again after delivery. Then run the pre-checks per the operating specification and review whether the additions, updates, and deletions match the business intent. A successful report only means the tool completed its checks; it cannot replace a human confirming that these changes are the intended changes.

## 3. Common accidents and how to judge them

| Scenario | Risk and handling |
| --- | --- |
| The external side changed a rule description while the repository changed the same field in the same period | The current sync has no common historical baseline and cannot reliably identify both sides' changes; compare the delivered version, Git diffs, and the report, then decide first which business meaning to keep |
| The external tool deleted identity comments and renamed at the same time | It is impossible to reliably tell a rename from delete-old-create-new; check the original deliverable and node identities, and do not resolve the ambiguity by relaxing deletion protection |
| An entity is missing from the XML | First confirm whether it is a business deletion or the external tool failed to export it; deletions require reviewing the reference impact, then explicit authorization per the specification |
| A rename involves references in other files | Identity matching is not a whole-project refactoring; coordinate the change in an isolated branch or a full copy of the design, then validate everything together |
| You want to place the candidate YAML next to the original design directory for comparison | A candidate file with the same ID in the same directory is treated by the validator as a duplicate definition; review using an isolated full copy of the design |
| The first file syncs successfully and the second fails | A single-file rollback does not undo the previous file; inspect the whole changeset, and do not treat partial success as the whole project being synced |

For example, if the external side only changed “发起支付” into “发起支付并返回支付信息”, you can review that single description change; if the YAML in the same period had already added new payment constraints, then even if the report shows no conflict, you must still verify whether the external side's older content would overwrite those constraints.

## 4. How to judge that you can continue

After the pre-checks pass, you still need to confirm that the complete design actually written back passes directory-level validation. This applies especially to changes involving cross-file references: a successful pre-check does not mean all references are valid, still less that Runtime behavior has been verified. See the [sync specification](../../skills/dec-design/references/xml-yaml-sync.md) for the exact scope of the checks and failure handling.

If ambiguity, a validation rollback, or an exception occurs, keep the external originals, the report, and the error output, and locate the problem first; do not continue generating and overwrite the scene. Only when both the sync report and the YAML diff match expectations should you move on to further verification.

## 5. Delivery after write-back

Regenerate the affected derived artifacts from the confirmed YAML, and complete directory validation, the relevant behavior tests, and the drift and Binding evidence checks per the [development and verification manual](development-and-verification.md). Changes involving runtime design also require repackaging and rebuilding the Runtime; refreshing HTML does not make the running application pick up the new design automatically.

At delivery, state the business changes, the design version used, and the scope actually verified. A successful sync proves only that the controlled write-back completed; whether the application behaves correctly is still supported by the corresponding business tests and integration evidence.
