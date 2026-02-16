# README.md Creation — What & Why

This document explains the creation of the project's `README.md` file.

---

## What Was Done

A comprehensive `README.md` was added to the repository root. It serves as the primary
entry point for anyone discovering the project.

### Sections Included

| Section | Purpose |
|---|---|
| **Overview** | High-level description of what the app does and what it demonstrates |
| **Features** | Table summarizing key capabilities (monitoring, alerts, settings, reboot survival) |
| **Tech Stack** | Exact versions of Kotlin, Compose, Gradle, AGP, SDKs, and testing frameworks — sourced directly from `libs.versions.toml` and `build.gradle.kts` |
| **Requirements** | What you need installed to build and run the project |
| **Setup & Run** | Step-by-step terminal commands from clone to launch |
| **Scripts / Gradle Tasks** | Table of every useful `./gradlew` command |
| **Environment Variables** | Notes that none are required; documents `local.properties` |
| **Tests** | Test file inventory with counts, Robolectric requirements, and run commands |
| **Project Structure** | Annotated directory tree explaining every key file |
| **Documentation** | Links to the existing `docs/` files |
| **License** | TODO placeholder — no license file exists yet |

---

## How Information Was Gathered

Every fact in the README was extracted from actual project files, not invented:

- **Language & versions**: `gradle/libs.versions.toml` (Kotlin 2.2.10, AGP 9.0.1, etc.)
- **Gradle version**: `gradle/wrapper/gradle-wrapper.properties` (Gradle 9.2.1)
- **SDK levels**: `app/build.gradle.kts` (minSdk 28, compileSdk/targetSdk 36)
- **Entry point**: `AndroidManifest.xml` (`.MainActivity` with `MAIN`/`LAUNCHER` filter)
- **Components**: `AndroidManifest.xml` (service, receiver declarations)
- **Test inventory**: Actual test files in `app/src/test/` and `app/src/androidTest/`
- **Test details**: `docs/testing.md` (counts, Robolectric usage, naming conventions)
- **Features & architecture**: `docs/implementation_notes.md`, `docs/settings_screen.md`

Where information was unknown or not yet decided (e.g., license type, repository URL),
`TODO` markers or angle-bracket placeholders (`<repository-url>`) were used instead of
inventing values.

---

## Design Decisions

1. **Tables over prose**: Key information (tech stack, features, tests) uses Markdown
   tables for scannability.

2. **Annotated project tree**: The structure section includes inline comments explaining
   what each file does, so newcomers don't have to open every file to understand the
   layout.

3. **Links to existing docs**: Rather than duplicating content from
   `implementation_notes.md`, `settings_screen.md`, and `testing.md`, the README links
   to them in a Documentation section.

4. **TODO for license**: The project has no LICENSE file, so the section honestly states
   this and prompts the maintainer to add one.
