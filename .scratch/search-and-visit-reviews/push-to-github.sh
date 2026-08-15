#!/usr/bin/env bash
# Publishes the staged tickets to GitHub as issues, in dependency order.
#
# Local ticket numbers (#01..#08) in the "Blocked by" sections are rewritten to
# the real issue numbers as each one is created, which is why this has to run
# top to bottom rather than in parallel.
#
# Safe to read before running: it creates one label and eight issues, and
# touches nothing else. It does not close or modify any existing issue.

set -euo pipefail

REPO="${REPO:-VHemanth45/Mumbai}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/issues"

command -v gh >/dev/null || { echo "gh is not installed. brew install gh"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh is not authenticated. gh auth login"; exit 1; }

# Idempotent: --force updates the label if it already exists.
gh label create ready-for-agent \
  --repo "$REPO" \
  --color 0E8A16 \
  --description "Scoped tightly enough for an agent to pick up unattended" \
  --force

declare -A NUM   # local ticket number -> real issue number

publish() {
  local slug="$1" title="$2"
  local file="$DIR/$slug.md"
  local body
  body="$(cat "$file")"

  # Rewrite #01..#08 to the issue numbers already created this run.
  local n
  for n in "${!NUM[@]}"; do
    body="${body//\#$n/#${NUM[$n]}}"
  done

  local url
  url="$(gh issue create \
    --repo "$REPO" \
    --title "$title" \
    --body "$body" \
    --label ready-for-agent)"

  NUM["${slug:0:2}"]="${url##*/}"
  echo "  ${slug:0:2} -> $url"
}

echo "Publishing to $REPO ..."
publish 01-commit-visit-and-verdict-in-one-transaction "Commit a visit and its verdict in one transaction"
publish 02-finish-search-coverage                      "Finish the search half of the Explore view-model tests"
publish 03-finish-review-coverage                      "Finish the review half of the Explore view-model tests"
publish 04-review-survives-restart                     "A review survives closing and updating the app"
publish 05-pin-where-the-flight-lands                  "Pin where the flight lands"
publish 06-on-screen-hint-names-the-search-box         "The on-screen hint names the search box"
publish 07-collapse-selected-and-focused-place         "Collapse the selected and focused place parameters"
publish 08-refresh-the-prd                             "Refresh the PRD to match what shipped"
echo "Done."
