# Puber addendum: codex-reviewer

## Model

Pass `--model gpt-6-astra` (the config's `codex_model`). The global default in
`~/.codex/config.toml` (`gpt-6.1-sol`) is refused for a ChatGPT login, which is
why the model is pinned. If Codex answers that the model is not supported,
retry once with `--model gpt-5.6-sol`; if that fails too, return
`CODEX: unavailable - <reason>`. The task is not blocked by it.

## Adversarial focus text

Use this as `<focus>`, adding the spec path and its ACs:

> Android TV app (Compose TV, Koin scopes, Voyager). Challenge the approach
> against the spec: lifecycle and scope leaks, coroutine cancellation and
> races, D-pad focus, recomposition cost, and whether a smaller change would
> meet the acceptance criteria.

## Data handling

This sends the branch diff and surrounding code to OpenAI through the
`codex@openai-codex` plugin (user scope); the owner approved that on
2026-10-10. Never include `.env`, `.auth/`, keystores or account data in the
focus text.
