# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/). Add every user-visible change under `Unreleased`; the release
workflow publishes the matching section as the GitHub Release notes.

## [Unreleased]

## [0.1.0] - 2026-11-28

First release.

### Added
- App icon: a root shell prompt (`#`) with a block cursor, also used as the themed (monochrome) icon.
- `make dev` builds the debug APK into `dist/<app>-<version>-<yyyymmddhhmm>.apk`.
- Terminal output can be selected and copied by long-press, and a Copy button copies all of it.
- Sub-games are now defined as data (`content/games/`), so new games on the existing engines need no code.
- Campaigns: stories that link missions, SOC shifts, ciphers and defense maps of different games, with ordered, locked steps and their own Campaigns tab.
- The Halcyon story is now three campaigns (pentest, phishing wave, incident and defense) covering missions, SOC shifts, puzzles, a new cipher level and the defense maps; Home leads with your current campaign and the sub-games are free play.
- New Cipher Quest level "The attacker's note", part of the phishing campaign.
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
