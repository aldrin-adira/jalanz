# Contributing to Jalanz

Thanks for your interest. Jalanz is in early testing (see `spec/10-build-phases.md`).

## Before you start
1. Read `spec/00-README.md` for what the app is and the decisions behind it.
2. Follow `spec/09-git-workflow.md` for every change:
   - branch from `develop` using a typed name, e.g. `fix/FR-06-toll-rounding`;
   - commit after each completed unit of work with the detailed message format (What / Why / How / Testing / Refs);
   - open a pull request into `develop` using the template in 09 §9.
3. Enable the commit checks after cloning:
   ```sh
   git config commit.template .gitmessage
   git config core.hooksPath .githooks
   ```

## Rules
- No secrets, API keys, signing keys or `.env` files in commits.
- No crawled toll datasets, real drive logs or real people's locations.
- Keep privacy guarantees intact (no analytics SDKs, no background location, sharing stays end-to-end encrypted).
- Be respectful in issues and reviews.

## Licence
By contributing, you agree that your contributions are licensed under the Apache License 2.0, the same licence as
the project (see `LICENSE`).
