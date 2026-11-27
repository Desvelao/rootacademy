# Content packs

All game content lives in `content/` as JSON and is shipped in the APK as assets. Logic goes in `*.json`, text in
`strings.en.json` and `strings.es.json` (both files must have exactly the same keys). Run `make packcheck` after every
change: it validates the packs and replays every reference solution. `make check` includes it.

## Layout

| Folder | Pack file | Extra files |
|---|---|---|
| `content/games/<id>/` | `game.json` (a sub-game: engine, content dir) | `strings.<lang>.json`: `title`, `subtitle`, `progress` |
| `content/campaigns/<id>/` | `campaign.json` (a story across games) | `strings.<lang>.json`: `title`, `intro`, `outro`, one key per step |
| `content/missions/<id>/` | `mission.json` (terminal mission) | `solution.txt`: one command per line, ending in `submit <flag>` |
| `content/puzzles/<id>/` | `puzzle.json` (CTF puzzle) | |
| `content/lessons/<id>/` | `lesson.json` (codex entry) | |
| `content/soc/` | `soc.json` (templates and story days) | |
| `content/cipher/<id>/` | `cipher.json` (Cipher Quest level) | `solution.txt`: the key |
| `content/defense/<id>/` | `map.json` (tower defense map) | `solution.txt`: `wave N: defense@node ...` per wave |

Every pack has `"schema": 1`, a `type` and a unique `id` (the folder name). Most activities share `points`, `skills`
(skill id to XP), `unlocks.codex` (lesson ids unlocked on first completion) and `flag_sha256`; lessons have none of these.

| Pack | Fields beyond `schema`, `type`, `id` |
|---|---|
| mission | `order`, `difficulty`, `points`, `skills`, `unlocks`, `startHost`, `startUser`, `traceLimit`, `hosts`, `objectives`, `commands` (chips), `flag_sha256` |
| puzzle | `category` (`web`, `crypto`, `reverse`, `forensics`), `difficulty`, `points`, `skills`, `unlocks`, `tools`, `flag_sha256` |
| lesson | `skill` (the skill it belongs to) |
| soc days | `order`, `difficulty`, `count`, `seed`, `points`, `skills`, `unlocks`, `requiresMission` (optional gate) |
| cipher level | `order`, `chapter` (`rome`, `renaissance`, `wwii`, `today`), `cipher` (`caesar`, `vigenere`, `enigma`, `rsa`), `ciphertext`, `tool` (optional: `freq`, `caesar`, `vigenere`, `keylen`, `enigma`, `rsa`), `points`, `skills`, `unlocks`, `flag_sha256` |
| defense map | `order`, `integrity`, `startMoney`, `income`, `nodes`, `links`, `spawn`, `target`, `waves`, `points`, `skills`, `unlocks` |

Puzzle `tools` are the helpers the puzzle screen offers: `base64`, `rot13`, `hex`, `url`, `md5`, `sha256`, `caesar`.
Mission `commands` are the chips shown above the input and must be real terminal commands. Cipher levels and defense
maps have no `difficulty` field: their `order` is the difficulty progression (earlier is easier).

## Flags

Flags are stored only as a SHA-256 hash, never in clear text:

```bash
docker compose run --rm dev ./gradlew :tools:packcheck:run --args="--hash 'flag{example}'"
```

For cipher levels the hash is of the normalised plaintext answer (see `Ciphers.answerHash`).

## Strings

Required keys: missions `title, brief, debrief, walkthrough, hint.1..3, objective.<id>`; puzzles and cipher levels
`title, prompt, walkthrough, hint.1..3`; lessons `title, body`; defense maps `title, brief, debrief, walkthrough,
hint.1..3, node.<id>`; the SOC pack has its own keys (`t.<template>.body`, `clue.<id>`, `day.<id>.title/brief/debrief`).
packcheck reports missing, extra and empty keys for each language.

## Games

The hub lists whatever `content/games/<id>/game.json` declares; there is no per-game code to register.

```json
{ "schema": 1, "id": "cipher", "engine": "cipher", "order": 3, "contentDir": "cipher", "icon": "lock" }
```

`engine` picks the play mechanic and where the packs are read from (`contentDir`, relative to `content/`):

| engine | reads | screens |
|---|---|---|
| `terminal` | `<contentDir>/missions`, `puzzles`, `lessons` (empty `contentDir` = the content root) | terminal missions and CTF puzzles |
| `soc` | `<contentDir>/soc.json` (only one soc game) | SOC shifts |
| `cipher` | `<contentDir>/<id>/cipher.json` | Cipher Quest levels |
| `defense` | `<contentDir>/<id>/map.json` | tower defense maps |

A new game on an existing engine is only data: add `games/<id>/` with its strings and put its packs in its own
`contentDir` (ids stay unique across all games). A new *mechanic* needs Kotlin: an `Engine` entry in `core`, loading in
`PackLoader`, validation in `PackValidator`, and a hub screen registered in `app/.../hub/GameHub.kt`.

## Campaigns

A campaign links activities (missions, puzzles, cipher levels, defense maps, SOC days) of any games into one story.

```json
{ "schema": 1, "id": "halcyon", "order": 1,
  "steps": [
    { "id": "s1", "activity": "m09-phishing-kit", "story": "s1.story" },
    { "id": "s2", "activity": "soc-day-4", "requires": ["s1"], "story": "s2.story" } ] }
```

A step's activity stays locked until every step in `requires` (earlier steps of the same campaign) is completed;
activities outside campaigns are never locked. Strings: `title`, `intro`, `outro` and each step's `story` key.
`rewards.codex` may list lessons. packcheck rejects unknown activities, duplicate steps, forward references and gates
that form a cycle across campaigns. The three `halcyon-*` campaigns are the worked example: they thread the Halcyon story through
terminal missions, SOC shifts, puzzles, a cipher level and defense maps. `history-of-secrets` (the cipher levels) and
`web-trail` (the web puzzles) show a story that stays inside one kind of activity. Anything not in a campaign is simply
free play from the sub-game lists on Home. Progress is derived from completions, so no extra storage is needed.

## Unlock links between games

Campaigns are the general mechanism. The older SOC-only link still works:

A SOC day can require a terminal mission with `"requiresMission": "<mission id>"` in `content/soc/soc.json` (day 4 requires
`m09-phishing-kit`); the app locks the day until that mission is completed and packcheck rejects unknown mission ids.
The link is declared on the SOC side, so missions need no extra field.

## Reference solutions

`solution.txt` files are the proof that a pack is solvable. packcheck replays them; the `syncContent` Gradle task
excludes them from the APK so they never ship.

## Adding a game or a campaign

1. Game: create `content/games/<id>/game.json` and `strings.en.json` / `strings.es.json`, then the packs under its
   `contentDir`. Campaign: create `content/campaigns/<id>/campaign.json` plus strings.
2. `make packcheck`.

## Adding a mission

1. Copy an existing `content/missions/mNN-*` folder and edit `mission.json` (hosts, files, objectives, `startHost`).
2. Write both strings files and `solution.txt`.
3. Hash the flag and put it in `flag_sha256`.
4. Make a lesson unlock via `unlocks.codex` only if the lesson exists under `content/lessons/`.
5. `make packcheck`. The `all-flags` badge threshold (`Badges` in `core/.../progress/Progress.kt`) must equal the
   number of missions plus puzzles; packcheck fails when it does not.
