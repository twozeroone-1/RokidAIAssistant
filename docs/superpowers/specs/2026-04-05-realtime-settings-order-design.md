# Realtime Settings Order Design

## Goal

Make the `Realtime conversation` section easier to scan without changing the visual style, adding new controls, or changing behavior.

## Scope

Only reorder items inside the existing `Realtime conversation` section in `SettingsScreen`.

In scope:
- Keep the current card-based Material layout
- Keep existing rows, dialogs, switches, and settings values
- Reorder rows so the most frequently used session controls appear first
- Keep conditional visibility rules such as `live mic profile` and `camera interval`

Out of scope:
- New card styles or hero headers
- Accordion or collapsible advanced sections
- New settings or behavior changes
- Changes to non-live settings sections

## Recommended Order

1. Realtime conversation
2. Live input source
3. Live output target
4. Play live answer audio
5. Interrupt assistant while speaking
6. Live voice
7. Use Google Search
8. Live RAG
9. Thinking level
10. Keep long sessions alive
11. Live minimal UI
12. Experimental live mic tuning
13. Live mic profile
14. Show thought summaries
15. Live camera mode
16. Live camera interval

## Rationale

The current issue is not missing functionality. It is that controls with very different importance are presented as if they all matter equally. Reordering fixes the scanning problem with minimal product risk.

The first half of the section should answer:
- Is realtime conversation on?
- Where does audio come from?
- Where does audio go?
- What kind of reply do I get?

The lower half should contain options that are less frequently changed, experimental, or debugging-oriented.

## Implementation Notes

- Preserve current component primitives such as `SettingsSection`, `SettingsRow`, `SettingsRowWithSwitch`, `SettingsSliderRow`, and the existing dialogs.
- Extract ordering into a small testable helper so the row sequence can be verified without Compose UI tests.
- Keep the existing conditional rules:
  - `Live mic profile` only shows when experimental tuning is enabled
  - `Camera interval` only shows when camera mode is `INTERVAL`
  - `Live output target` remains disabled when live answer audio playback is off

## Verification

- Add unit tests for the helper that produces the visible row order for representative settings states
- Run targeted `phone-app` unit tests
- Run `:phone-app:assembleDebug` to verify the Compose screen still builds
