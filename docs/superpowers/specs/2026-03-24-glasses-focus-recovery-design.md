# Glasses Focus Recovery Design

**Goal:** Restore touchpad tap and enter-key interaction by keeping the glasses app in the focused foreground task when Rokid launcher steals focus.

**Problem:** On real devices, `MainActivity` can remain resumed while input focus moves to `com.rokid.os.sprite.launcher`. In that state, `KEYCODE_ENTER` and touchpad events no longer reach the app, so the existing input handlers do not run.

**Recommended Approach:** Add a small focus recovery policy in the glasses app. `MainActivity` will observe loss of window focus while the activity is resumed. If the activity is still supposed to be active and a short cooldown has elapsed, it will relaunch itself with `NEW_TASK | CLEAR_TOP | SINGLE_TOP` using a dedicated recovery intent extra. This restores app focus without changing the Rokid-specific function button path.

**Scope:**
- Recover tap/enter input by restoring activity focus
- Keep `SPRITE_FUNCTION` button handling out of scope for this change
- Avoid aggressive loops by applying a cooldown and canceling recovery when focus returns

**Verification:**
- Unit-test the recovery policy decisions
- Compile the glasses app
- On device, confirm `mFocusedApp` returns to `com.example.rokidglasses/.MainActivity`
- Confirm touchpad tap or enter key reaches `MainActivity` again
