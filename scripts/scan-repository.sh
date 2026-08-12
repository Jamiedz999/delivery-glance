#!/usr/bin/env bash
#
# Scans this repository — the working tree and the whole of its git history — for the four things it
# promises not to contain: a real credential, a raw Tracking token, a real personal address, and a
# raw Courier coordinate.
#
#   scripts/scan-repository.sh
#
# It is not a general secret scanner and does not pretend to be one; the value of a check that knows
# this repository is that it can also assert the *inverse* — that the credentials which are here on
# purpose are exactly the documented development ones and no others have joined them.
#
# Exit status is 0 only if every check passed.

set -uo pipefail
cd "$(dirname "$0")/.."

failures=0
pass() { printf '  \033[32mok\033[0m    %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; failures=$((failures + 1)); }
note() { printf '  note  %s\n' "$1"; }

# Every blob that has ever been committed, on any ref, concatenated once. Grepping this is what makes
# these checks about the repository's history rather than about its current checkout — a credential
# deleted in a later commit is still in the clone anybody makes.
history="$(mktemp)"
tree="$(mktemp)"
trap 'rm -f "$history" "$tree"' EXIT

git rev-list --objects --all |
	git cat-file --batch-check='%(objecttype) %(objectname) %(rest)' |
	awk '$1 == "blob" {print $2}' |
	git cat-file --batch > "$history" 2> /dev/null

# The working tree's text files: tracked, plus untracked ones that are not gitignored. The second
# half matters — a file written but not yet added is exactly the file somebody is about to commit,
# and a scan that only saw the index would pass right up until the moment it stopped being useful.
#
# Deliberately not history: the two checks below are about what this repository ships, and history
# cannot be edited without rewriting every merged pull request. The credential and token checks above
# are the ones that need history, because a leaked secret stays leaked in every clone whatever a
# later commit does.
git ls-files -z --cached --others --exclude-standard |
	xargs -0 grep -I -H -n '' 2> /dev/null > "$tree"

echo
echo "Delivery Glance repository scan"
printf '  (working tree, and %s objects across every ref)\n' "$(git rev-list --objects --all | wc -l | tr -d ' ')"

# ---------------------------------------------------------------------------
echo
echo "Credentials"

# Things that are never legitimately in a repository, whatever the project.
if grep -qE 'BEGIN (RSA |EC |OPENSSH |PGP )?PRIVATE KEY|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{36}|xox[baprs]-[A-Za-z0-9-]{10,}' "$history"; then
	fail "a private key or cloud/service credential appears in history"
else
	pass "no private key, AWS key, GitHub token or Slack token in history"
fi

# No .env has ever been added, on any branch. `.gitignore` says it should not be; this says it was
# not, which is the question that survives someone editing `.gitignore`.
if [[ -n "$(git log --all --diff-filter=A --name-only --pretty=format: -- '.env' '*/.env' | tr -d '[:space:]')" ]]; then
	fail ".env has been committed at some point in history"
else
	pass ".env has never been committed on any branch"
fi
if git check-ignore -q .env; then
	pass ".env is gitignored"
else
	fail ".env is not gitignored"
fi

# The inverse check. These three are development values, are documented as such, and are meant to be
# here; anything else that looks like a password assignment is not.
expected_development_credentials=(
	'development-only-tracking-key-do-not-deploy' # TRACKING_KEY_V1's default, named to be unusable
	'Dispatcher-Demo-2026!'                       # the two fictional demo passwords, documented in
	'Courier-Demo-2026!'                          # README.md and seeded by V1__internal_account.sql
	'delivery_glance'                             # the local Compose PostgreSQL password
)
for credential in "${expected_development_credentials[@]}"; do
	if grep -qF "$credential" "$tree"; then
		note "present on purpose: $credential"
	else
		fail "the documented development credential '$credential' is gone — has something been renamed without updating this scan, or the README?"
	fi
done

# ---------------------------------------------------------------------------
echo
echo "Tracking tokens"

# A raw capability is 32 random bytes, base64url, unpadded — 43 characters. It exists in exactly one
# place at runtime, the Copy response, and nowhere at rest: only a SHA-256 verifier is stored. So a
# 43-character base64url run in a URL fragment anywhere in this repository would mean one escaped.
if grep -qE '#t=[A-Za-z0-9_-]{43}' "$history"; then
	fail "a raw Tracking token appears in a link in history"
else
	pass "no raw Tracking token in any committed link"
fi

# The reporting secret is issued in the same shape and is returned once to the page that started a
# Location Sharing Session. Test fixtures build theirs at runtime; a literal one would be a leak.
if grep -qE '"reportingSecret"[[:space:]]*:[[:space:]]*"[A-Za-z0-9_-]{43}"' "$history"; then
	fail "a literal reporting secret appears in history"
else
	pass "no literal reporting secret in history"
fi

# ---------------------------------------------------------------------------
echo
echo "Addresses and coordinates"

# The rule is not "these exact labels are allowed" — a list like that rots the first time somebody
# adds a fixture — but "every address here has to read as invented". A new address that says nothing
# about being made up is flagged, which is the moment to notice a real one has been typed in.
#
# The street types are long and the house number is optional, both learned the hard way: the first
# version of this check demanded a number and knew nothing of quays or squares, and passed a file
# that still carried two real ones. A rule that only catches addresses in the shape you happened to
# think of is worse than none, because it gets quoted as if it caught all of them. Note that it also
# reads this file, so an example written out here would flag itself — which is why there is none.
INVENTION_MARKERS='fictional|invented|imaginary|notional|glance|depot|warehouse|riverside|pickup|handoff|example|demo|test'
STREET_TYPES='Street|Road|Lane|Avenue|Drive|Close|Court|Terrace|Gardens|Way|Crescent|Row|Yard|Quay|Square|Place|Park|Bridge|Hill|Green|Estate|Wharf|Dock|Mews|Grove|Rise|Walk|Parade'
unmarked="$(grep -ohE "\\b([0-9]{1,4}[A-Za-z]?[[:space:]]+)?[A-Z][A-Za-z]+([[:space:]]+[A-Z][A-Za-z]+)?[[:space:]]+($STREET_TYPES)\\b" "$tree" |
	sort -u |
	grep -viE "$INVENTION_MARKERS")"
if [[ -n "$unmarked" ]]; then
	fail "an address that does not read as invented:"
	while IFS= read -r line; do printf '        %s\n' "$line"; done <<< "$unmarked"
else
	pass "every address-shaped string in the tree reads as invented"
fi

# Raw Courier coordinates are the one thing this product promises never to keep. None can reach a
# file, because none is ever written down — but fixtures do carry coordinates, and those must be
# somewhere invented. Every one of them sits in one small box around the fictional place.
#
# Values outside the WGS84 ranges are skipped rather than flagged: a latitude of 91 is not a place,
# it is a validation fixture proving the range check refuses it.
coordinates_outside_the_box() { # coordinates_outside_the_box <field> <low> <high> <valid>
	grep -ohE "\"?$1\"?[[:space:]]*[:=][[:space:]]*\(?-?[0-9]+\.[0-9]+" "$tree" |
		grep -oE '\-?[0-9]+\.[0-9]+$' |
		awk -v low="$2" -v high="$3" -v valid="$4" \
			'($1 < -valid || $1 > valid) { next } ($1 > low && $1 < high) { next } { print }' |
		sort -u
}
strays="$(
	coordinates_outside_the_box latitude 51.0 52.0 90
	coordinates_outside_the_box longitude -1.0 0.0 180
)"
if [[ -n "$strays" ]]; then
	fail "a usable coordinate outside the one fictional area: $(echo "$strays" | tr '\n' ' ')"
else
	pass "every usable coordinate in the tree is inside the one fictional area"
fi

# The strongest statement available: if no table has a column that could hold a Courier position, no
# deployment can accumulate one however long it runs.
#
# `pickup_` and `handoff_` coordinates are excluded because they are the Delivery's two addresses,
# which are durable on purpose — a Delivery is a promise to carry something from one place to
# another, and that is not a fact about where anybody is. Every other latitude, longitude, position
# or route column would be. Comments are stripped first, because V5's are largely about there being
# no such column and would otherwise match themselves.
courier_position_columns="$(sed 's/--.*//' server/src/main/resources/db/migration/*.sql |
	grep -ioE '[a-z_]*(latitude|longitude|position|coordinate|route_history)[a-z_]*' |
	grep -viE '(pickup|handoff)_(latitude|longitude)' |
	sort -u)"
if [[ -n "$courier_position_columns" ]]; then
	fail "a migration declares something that could hold a Courier position: $(echo "$courier_position_columns" | tr '\n' ' ')"
else
	pass "no migration creates anywhere to store a Courier coordinate"
fi

# ---------------------------------------------------------------------------
echo
if [[ "$failures" -eq 0 ]]; then
	printf '\033[32mAll checks passed.\033[0m\n\n'
	exit 0
fi
printf '\033[31m%d check(s) failed.\033[0m\n\n' "$failures"
exit 1
