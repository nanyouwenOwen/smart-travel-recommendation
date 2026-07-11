# Repository agent workflow

All non-trivial changes to this repository must follow the loop below. Do not skip a stage merely because the change appears straightforward.

1. **Plan**: inspect current code, contracts, TODO and prior reviews; write a bounded plan under `docs/plans/`. A planning agent/terminal must not implement production code.
2. **Implement**: implement the approved plan, update contracts/tests/docs together, and record material AI decisions in `docs/ai-governance/AI_CHANGE_LOG.md` or the existing `docs/ai-usage-log.md`.
3. **Independent review and test**: a separate reviewer/terminal audits the plan against the actual diff, runs proportional gates, and writes `docs/reviews/<round>-review.md`. It must report PASS/FAIL and concrete blockers without editing implementation.
4. **Correct and re-review**: the implementation terminal fixes blockers; the same independent reviewer re-runs the affected checks. Update `TODO.md` only after evidence passes.
5. **Deliver**: push ordinary code only when authorized, wait for required CI, and record the commit/run evidence. Tags, releases, deployments, production data changes and secrets require explicit human authorization.

Keep product/engineering documents in the normal `docs/` topic folders. Keep AI process, audit, human-action and model-handoff documents only in `docs/ai-governance/`; never mix secrets, raw private prompts or credentials into either area. Read `docs/ai-governance/README.md` before modifying the project.
