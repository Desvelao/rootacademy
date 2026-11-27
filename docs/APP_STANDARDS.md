# App standards

Rules every screen of this app follows. Keep them minimal and consistent.

## Settings: how inputs are done

Settings is one scrollable screen. Each group has a small section label and, below it, a panel (rounded,
`surfaceContainer` fill, 1dp `outlineVariant` border). Rows inside a panel are separated by `HorizontalDivider`.

| Input | Use for | How |
|---|---|---|
| Pills | 2-3 short options (theme) | One row of equal-width selectable pills, min height 52dp, 16dp corners. Selected = `secondaryContainer` fill, `primary` border and text; unselected = `outlineVariant` border. One tap applies it. |
| Radio rows | 4+ options or long labels (language) | Whole row is `selectable(role = Role.RadioButton)`, min height 64dp, title + optional subtitle, `RadioButton(onClick = null)` at the end. One tap applies it. |
| Switch rows | on/off | Title, subtitle, `Switch` at the end; whole row toggles. |
| Link/action rows | open a dialog or action | Title + subtitle, min height 48dp. |

- Changes apply immediately; there is no Save button.
- Touch targets are at least 48dp. Selection is never shown by colour alone (border, radio or check too).
- All text comes from string resources.

## Language selector

- Section "Language" in Settings, radio rows: **System default** first, then every shipped language.
- Each language is shown in its own name (English, Español, ...), not translated.
- Default is **System default**: the app uses the system language if it ships it, otherwise English.
  English lives in `values/`; each extra language is a `values-xx/` folder.
- Implementation: list the shipped languages in `res/xml/locales_config.xml`, reference it with
  `android:localeConfig` on `<application>`, and apply the choice with
  `AppCompatDelegate.setApplicationLocales(...)` (`LocaleListCompat.getEmptyLocaleList()` = follow system).
  The platform persists the choice, so it is not stored in the app preferences.

## Theme selector

- Section "Theme" in Settings, three pills: **System**, **Light**, **Dark**.
- Default is **System**. Persist the mode (enum `ThemeMode { SYSTEM, LIGHT, DARK }`, default `SYSTEM`).
- `AppTheme(mode)` in `ui/theme/Theme.kt` picks dark when `mode == DARK`, or when `mode == SYSTEM` and
  `isSystemInDarkTheme()`.

## Help and first-time tips

- Every sub-game hub has a "?" button in its top bar (min 48dp, `contentDescription` "How to play") that opens a
  how-to-play `HelpDialog`; it has a Glossary button. Screens with a complex first look (terminal mission, cipher
  level, SOC shift, defense map) also show a `FirstTimeTip` once.
- Technical terms live in `ui/design/Help.kt` (`Term`): add the label and definition strings (`term_<id>`,
  `term_<id>_def`) in every language. A `TermHelpButton` next to a label opens the definition.
- Tips and the intro are remembered in the settings (`seenTips`). Settings > Help > "Show the intro again" forgets them.
- Keep help text in string resources, in every shipped language.

## Hardware keyboard

- In the terminal, Enter runs the command, Tab completes it, Up and Down browse the history. Enter also submits the
  puzzle flag and the cipher answer. Do not rely on the on-screen keyboard's Send action alone: handle Enter with
  `terminalKeyAction` (terminal) or `Modifier.onEnterKey` (`ui/design/Keys.kt`).
- Emulator: the AVD needs `hw.keyboard=yes` (the default) and the emulator window must have focus. To test a real
  physical-keyboard path, use a userdebug image: `adb root`, then `sendevent /dev/input/eventN 1 <scancode> 1/0` on the
  "AT Translated Set 2 keyboard" node (`adb shell getevent -i` lists it; scancode 28 = Enter, 15 = Tab). `adb shell input
  keyevent` is a virtual device and `adb emu event send` did not deliver keys to the app, so neither proves anything
  about a PC keyboard. Gboard sees hardware keys before the app: numpad Enter is swallowed by it on the API 36 image.

## Version and About

- Last item of Settings: a centred text button (min height 48dp), `bodySmall`, `onSurfaceVariant`, reading
  `<app name> v<version name>`.
- Tapping it opens an `AlertDialog` titled **About** with, stacked:
  1. app name (`titleMedium`)
  2. `Version <name> (build <code>)`
  3. the project URL in `primary` colour
- Buttons: **Open link** (confirm; `ACTION_VIEW`, toast if no browser is found), **Copy link** (copies the URL and
  closes), **Cancel**.
- Values come from `R.string.app_name` and `BuildConfig.VERSION_NAME`, `VERSION_CODE`, `APP_URL`; never hard-code them.

## Suggested string keys

`settings_title`, `settings_section_language`, `settings_section_theme`, `language_system`, `theme_system`,
`theme_light`, `theme_dark`, `settings_version` (`%1$s v%2$s`), `about_title`, `about_version`
(`Version %1$s (build %2$d)`), `about_open`, `about_copy`, `about_no_browser`, `action_cancel`.
