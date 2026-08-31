## End to End Tests

This folder contains our e2e test suite, written with playwright.
The playwright docs are pretty good so use them as your reference: https://playwright.dev/docs/intro

## Quickstart:

- If node not installed install it with nvm: https://github.com/nvm-sh/nvm
- Run `npm install`
- To run the tests run `npx playwright test` or `npx playwright test --ui` for the UI.
- Highly recommend to install the VSCode extension: https://playwright.dev/docs/getting-started-vscode (makes a lot of things easier, like running individual tests or recording your own)

## README screenshots

The screenshots in the top-level `images/` folder (used by the README) are generated
automatically by `tests/screenshots/screenshots.spec.ts`. The spec seeds realistic demo
data through the REST API (users, connections, an approved & executed request, API keys,
a temp-access request with a running proxy) and captures every shot in light and dark mode.

```bash
# 1. Start the e2e stack with a CLEAN database (fresh timestamps in the shots)
docker compose -p kviklet-e2e -f ../e2e-compose.yml down -v
docker compose -p kviklet-e2e -f ../e2e-compose.yml up -d --build

# 2. Generate the screenshots into screenshots-output/
npm run screenshots

# 3. Compare them against the committed images/ (writes visual diffs, exits 1 on change)
npm run screenshots:diff

# 4. Adopt the new screenshots into images/
npm run screenshots:update
```

Notes:

- The API key and proxy screenshots need an enterprise test license. The spec looks for
  `../../license-script/test_license_key.json` by default; override with
  `KVIKLET_TEST_LICENSE_PATH=/path/to/license.json`. The test license allows exactly
  2 users, which is all the seed data uses.
- Seeding is idempotent, so the spec can re-run against a running stack. The one shot
  that accumulates content across re-runs is the audit log (the live session tests
  execute real statements through the UI), so always regenerate from a fresh stack
  before updating `images/` — that also keeps the relative timestamps small.
- The screenshots project only exists when `SCREENSHOTS=1` is set, so a plain
  `npx playwright test` (and CI) never runs it.
