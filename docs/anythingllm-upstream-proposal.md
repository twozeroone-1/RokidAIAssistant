# AnythingLLM Upstream Proposal Draft

## Suggested Issue Title

`Feature proposal: add AnythingLLM as an optional docs provider`

## Suggested Issue Body

```md
Hi, thank you for building this app.

I'm still a beginner, so I may be missing some context, but after studying the code and trying the app on real devices, I wanted to say that I was genuinely impressed by how well this project is structured and how complete it already feels. The phone-side runtime/provider design in particular feels very thoughtfully built.

I'm a Korean developer working on a document-grounded assistant workflow for Rokid glasses, and while building my own prototype I kept thinking that your current structure looks like a strong fit for one small addition: AnythingLLM as an optional docs provider.

I am proposing a deliberately narrow scope:

- add an AnythingLLM entry in docs settings
- add server URL / API key / workspace slug settings
- add a connection test / health check
- relay a simple text docs query
- map basic source or citation previews when available

What I am not proposing in this first step:

- no overall product-flow redesign
- no answer-mode architecture changes
- no history metadata or badge changes
- no photo-to-docs routing changes
- no large UI restructuring

The reason I think this could fit well is that your current phone-side structure already separates settings, validation, routing, and adapters cleanly. It seems possible to add this as a small, reviewable provider-shaped feature instead of a broad redesign.

If this direction sounds reasonable, I’d be happy to prepare a small PR with that limited scope.
```

## Suggested PR Scope

Use a clean branch from upstream `main` and keep the diff limited to:

- `phone-app` settings model updates for AnythingLLM fields
- settings persistence for server URL, API key, and workspace slug
- health check and simple query adapter
- minimal docs settings UI
- tests for settings persistence, health check, and query routing

## Explicit Non-Goals

- no `General AI / Docs` product-mode redesign
- no `Fast / Slow / Auto` networking policy
- no history schema or badge changes
- no photo-based docs pipeline
- no TTS or CXR changes
