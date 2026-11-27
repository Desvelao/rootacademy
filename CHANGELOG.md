# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/). Add every user-visible change under `Unreleased`; the release
workflow publishes the matching section as the GitHub Release notes.

## [Unreleased]

### Added
- `make debug` copies the debug APK to `dist/<app>-<yyyymmdd-hhmm>.apk`.
- Terminal output can be selected and copied by long-press, and a Copy button copies all of it.
- Sub-games are now defined as data (`content/games/`), so new games on the existing engines need no code.
- Campaigns: stories that link missions, SOC shifts, ciphers and defense maps of different games, with ordered, locked steps and their own Campaigns tab.
- The Halcyon story is now three campaigns (pentest, phishing wave, incident and defense) covering missions, SOC shifts, puzzles, a new cipher level and the defense maps; Home leads with your current campaign and the sub-games are free play.
- New Cipher Quest level "The attacker's note", part of the phishing campaign.
- Continuity fixes in the story: the file server address in "Hidden in plain sight" and the attacker address in the headers puzzle now match the rest of the story; the incident log is in time order.
- Game hub with Home, Skills, Codex and Profile tabs and a tile for each sub-game.
- Terminal missions: a simulated Linux terminal with about 25 commands, pipes, tab completion, history, command chips and a trace meter.
- 12 story missions (Halcyon Corp) with objectives, debriefs and three hints plus a walkthrough each.
- 23 standalone CTF puzzles (Crypto, Web, Forensics, Reverse) with built-in helper tools, and a seeded daily challenge.
- Skill tree with six skills, a codex of 19 offline lessons, points, streak and badges.
- SOC analyst: 10 story shifts plus a seeded daily shift. Mark emails, login alerts and logs as legit, phishing or escalate, and learn the clues. Day 4 unlocks after the phishing terminal mission.
- Cipher Quest: 12 levels in 4 chapters (Rome, Renaissance, WWII, today) with Caesar, Vigenère, a toy Enigma and toy RSA, and tools that unlock chapter by chapter (frequency analysis, shift dial, key length helper, Enigma, RSA helper).
- Tower defense: 3 network maps, 12 waves, 6 defenses and 5 attack types on a deterministic simulation drawn on a canvas.
- Progress export and import as a JSON file.
- English and Spanish, language, theme, text size, terminal font size, reduce motion and high contrast settings, About dialog.
- Content packs (`content/`) validated by `tools/packcheck`, which replays every mission solution in `make check`.
- Three harder puzzles (Vigenère, toy RSA, layered metadata), three codex lessons (segmentation, ransomware and backups, brute force and MFA) and the XSS lesson is now unlockable.
- Screen reader announcements for terminal output, trace meter, Tower defense wave status and attacks.
- First-run intro (four short screens, skippable) explaining the app, the four sub-games, points, skills and streak; "Show the intro again" in Settings.
- A "?" button in every sub-game hub and in the SOC and Tower defense screens opens a how-to-play dialog, and first-time tips introduce the terminal, Cipher Quest levels, SOC shifts and Tower defense maps.
- Glossary of 24 technical terms (SOC, phishing, flag, trace, ciphertext, DDoS, MFA, integrity, ...) from the Codex tab and Settings, with "?" buttons next to Trace, Ciphertext and Plaintext.
- Terminal `help` lists every command with a one-line description, `help <command>` shows usage and an example, and long-pressing a command chip shows its help.
- Tower defense shows the attacks of the next wave, marks the recommended defense, describes each attack in the legend and has a short how-it-works note on the first map.
- Skills: tap a skill to see its level, the XP for the next level and every activity that awards it. Codex: locked lessons name the activity that unlocks them and link to it.
- Profile lists every badge with what it takes; Home explains points and streak, and shows a "Start here" / "Next up" mission card.
- SOC shows a running correct count and a short definition of Legit, Phishing and Escalate; Cipher Quest tools have a description and each level can preselect its tool.
- Mission cards show a difficulty level and skill tags.
- Profile > Your data has a confirmed "Reset progress" action that erases points, streak, skills and the codex (settings are kept).
- Two more campaigns for content that was free play only: "A history of secrets" (the twelve cipher levels) and "The web trail" (five web puzzles).
- Lessons in the codex now end with a "how to spot it" paragraph in English and Spanish.
- Terminal `head` and `tail` take several files (with headers), `grep` accepts `-e`, `-E`, `-w`, options after the pattern and `-r` without a path.
- Tower defense maps and cipher levels are ordered by `order`; the content docs now list every pack field.

### Changed
- The app id is now `io.github.desvelao.rootacademy`, so builds install as a new app beside older ones (export and import progress to move it), and the About dialog links to the project page.
- SOC story shifts no longer carry "Day N" in their title, so they read well inside campaigns; "After the test" and "The attacker returns" now fit the story order, and the final SOC shift moved to the incident campaign.
- Missions now ramp in difficulty: points 100 to 250, and later missions have a tighter trace limit.
- Terminal commands: `head -5`, `grep -v/-c/-l`, `find -type/-iname`, `wc -l/-w/-c` and clearer errors for unknown options; `cat`, `tail` and `wc` no longer count a phantom last line; `scan <ip>` lists only that host.
- Terminal and Tower defense progress now survives Android killing the app in the background.
- Retrying a SOC story shift gives a fresh queue (the daily shift stays the same for everyone).
- Terminal history has a "next command" button and responds to the hardware Up and Down keys.
- Badge and SOC item counts in the UI follow the content instead of being typed into the text.

### Fixed
- Screens no longer leave a blank band under the status bar (and above the bottom bars): system bar space is applied once.
- Importing progress is now all-or-nothing and keeps SOC, Cipher Quest and Tower defense completions; export/import also keeps hint spending per puzzle and rejects negative or unknown values with a clearer message for files from a newer app version.
- A failed export (no output stream) is reported instead of shown as saved.
- Cipher Quest and SOC/defense text follow the language setting without restarting the app.
- Toy RSA tools no longer freeze or overflow on huge numbers; Vigenère with an empty key no longer shows the ciphertext as the answer.
- Light theme no longer flashes a dark window at start-up.
- The Caesar tools no longer start on the answer shift (they start at 0), so the first Cipher Quest level is no longer solved by selecting the tool.
- Mission, level and daily-challenge descriptions are cut at a word boundary instead of mid-word with a stray ellipsis.
- The terminal input bar is split in two rows with visible labels for Tab, previous and next, and the command chips show a fade and arrow when they scroll.
- The terminal command field is focused when a mission opens and after every dialog closes, so a hardware keyboard types straight away, the Enter key runs the command even when the keyboard does not turn it into a Send action, and the Tab key completes it instead of moving focus away; Enter also submits the puzzle flag and the cipher answer.
- `head -n -3`, `tail -n -3` and huge counts no longer crash the terminal; `find` and Tab completion no longer reveal files inside directories you may not open; errors from one pipe stage no longer feed the next; `wc` counts bytes and splits words on any whitespace.
- A terminal mission finished just before Android killed the app is credited when the app reopens; SOC shift progress now survives that too.
- The SOC fail message shows the real requirement (for example 5 of 6 correct); verdict buttons wrap at large text sizes and in Spanish; the SOC result is announced to screen readers.
- The terminal prompt line and command chips follow the terminal font size, and the top bar no longer clips its buttons at large text sizes.
- Exporting progress over an existing file no longer leaves old text after the new data; a failed import shows a message instead of crashing and no longer reports success first.
- Import ignores hint and codex entries for content that does not exist and limits hint counts and costs to what the game can produce.
- The daily challenge and the streak update at midnight while the app is open; Home no longer offers a mission or the daily puzzle that a campaign still locks.
- The app waits for your saved settings before drawing, so dark-theme and large-text users no longer see default styling flash at start-up.
- The Skills detail dialog no longer fails when an activity kind has no group; toy RSA rejects moduli that are not a product of two primes; Enigma keeps letter case; export files with version 0 or below are reported as invalid, not as "newer app".
- Tower defense: starting a wave after the game is over, or finishing the wrong wave, can no longer crash; maps with links to unknown nodes no longer crash the validator.
- Spanish: the "Hidden in plain sight" hint and the XSS lesson title are no longer English.

## [0.1.0] - 2026-11-20

First release.

### Added
- Project scaffolded from the `android-compose` template.
