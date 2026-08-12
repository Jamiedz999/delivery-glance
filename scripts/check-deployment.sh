#!/usr/bin/env bash
#
# Checks a running Delivery Glance deployment from the outside, with no credentials and no secrets.
#
# Everything below is asserted against the deployment's own responses, so this is the command behind
# the deployment claims in README.md and docs/deployment.md rather than a description of them. It
# makes no authenticated request on purpose: a check that needs the demo password could not be run
# against a deployment whose password you do not have, which is most of them.
#
#   scripts/check-deployment.sh https://your-host.example
#
# Exit status is 0 only if every REQUIRED check passed. Checks marked NOTE report a fact and never
# fail the run — whether the map is configured, and whether the demo reset is switched on, are
# deployment decisions rather than defects.

set -uo pipefail

BASE_URL="${1:-}"
if [[ -z "$BASE_URL" ]]; then
	echo "usage: $0 <base-url>   e.g. $0 https://your-host.example" >&2
	exit 2
fi
BASE_URL="${BASE_URL%/}"

failures=0
pass() { printf '  \033[32mok\033[0m    %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; failures=$((failures + 1)); }
note() { printf '  note  %s\n' "$1"; }

# One request per URL, kept on disk so several assertions can read the same response rather than
# asking the deployment the same question five times.
workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

fetch() { # fetch <name> <url> [curl args...]
	local name="$1" url="$2"
	shift 2
	curl --silent --show-error --location --max-time 20 \
		--dump-header "$workdir/$name.headers" --output "$workdir/$name.body" \
		--write-out '%{http_code}' "$@" "$url" > "$workdir/$name.status" 2> "$workdir/$name.err"
}

status_of() { cat "$workdir/$1.status" 2>/dev/null || echo 000; }

header_of() { # header_of <name> <header>
	grep -i "^$2:" "$workdir/$1.headers" 2>/dev/null | tail -1 | cut -d: -f2- | tr -d '\r' | sed 's/^ *//'
}

expect_header_contains() { # expect_header_contains <name> <header> <substring> <description>
	local value
	value="$(header_of "$1" "$2")"
	if [[ "$value" == *"$3"* ]]; then
		pass "$4"
	else
		fail "$4 — $2 was '${value:-absent}'"
	fi
}

echo
echo "Delivery Glance deployment check — $BASE_URL"

# ---------------------------------------------------------------------------
echo
echo "Transport"

if [[ "$BASE_URL" == https://* ]]; then
	pass "the base URL is HTTPS"
elif [[ "$BASE_URL" == http://localhost* || "$BASE_URL" == http://127.0.0.1* ]]; then
	note "plain HTTP against localhost; the cookie checks below are expected to be skipped"
else
	fail "the base URL is not HTTPS — a Tracking Link is a public bearer capability and must not travel in clear"
fi

# ---------------------------------------------------------------------------
echo
echo "The application is answering"

fetch health "$BASE_URL/actuator/health"
if [[ "$(status_of health)" == "200" ]] && grep -q '"status":"UP"' "$workdir/health.body"; then
	pass "/actuator/health is UP"
else
	fail "/actuator/health returned $(status_of health): $(head -c 200 "$workdir/health.body")"
fi

fetch system "$BASE_URL/api/system"
if [[ "$(status_of system)" == "200" ]]; then
	pass "/api/system answers ($(head -c 160 "$workdir/system.body"))"
else
	fail "/api/system returned $(status_of system)"
fi

# The whole point of Flyway owning the schema: a deployment that started but could not migrate is
# not a deployment. Health covers the datasource; a Delivery route proves the tables are there.
fetch deliveries "$BASE_URL/api/deliveries"
if [[ "$(status_of deliveries)" == "401" ]]; then
	pass "/api/deliveries refuses an anonymous caller with 401"
else
	fail "/api/deliveries returned $(status_of deliveries), expected 401"
fi

# ---------------------------------------------------------------------------
echo
echo "Cookies"

if [[ "$BASE_URL" == https://* ]]; then
	csrf_cookie="$(grep -i '^set-cookie: *XSRF-TOKEN' "$workdir/system.headers" | tr -d '\r')"
	if [[ -z "$csrf_cookie" ]]; then
		fail "/api/system issued no XSRF-TOKEN cookie"
	else
		[[ "$csrf_cookie" == *[Ss]ecure* ]] && pass "the CSRF cookie is Secure" ||
			fail "the CSRF cookie is not Secure — set SESSION_COOKIE_SECURE=true"
		[[ "$csrf_cookie" == *SameSite=Strict* ]] && pass "the CSRF cookie is SameSite=Strict" ||
			fail "the CSRF cookie is not SameSite=Strict"
	fi
else
	note "skipping the cookie checks: Secure cookies are not expected over plain HTTP"
fi

# ---------------------------------------------------------------------------
echo
echo "The Recipient tracking route"

# No token, so this is the bootstrap page every Link Holder is served before anything is decided.
# Its headers are the ones a Delivery address travels under, and they are set by a filter in front
# of the security chain precisely so that a refused request carries them too.
fetch track "$BASE_URL/track"
if [[ "$(status_of track)" == "200" ]]; then
	pass "/track is served"
else
	fail "/track returned $(status_of track)"
fi

expect_header_contains track "Cache-Control" "no-store" "/track is no-store"
expect_header_contains track "Referrer-Policy" "no-referrer" "/track sends no referrer"
expect_header_contains track "X-Content-Type-Options" "nosniff" "/track is nosniff"
expect_header_contains track "X-Robots-Tag" "noindex" "/track asks not to be indexed"
expect_header_contains track "Content-Security-Policy" "frame-ancestors 'none'" "/track cannot be framed"

# The generic refusal. A tampered token, an unknown link and an expired one must be indistinguishable,
# which is what stops the route becoming a way to ask whether a Delivery exists.
#
# The exchange is CSRF-protected like every other unsafe route, so the token has to be collected
# first — otherwise this would be checking the CSRF filter and reporting it as a link refusal.
csrf_jar="$workdir/jar"
curl --silent --output /dev/null --cookie-jar "$csrf_jar" --max-time 20 "$BASE_URL/api/system"
csrf_token="$(awk '/XSRF-TOKEN/ {print $7}' "$csrf_jar" 2>/dev/null | tail -1)"
if [[ -z "$csrf_token" ]]; then
	fail "could not obtain a CSRF token from /api/system"
fi

fetch exchange "$BASE_URL/api/tracking-session" \
	--request POST --header 'Content-Type: application/json' \
	--header "X-XSRF-TOKEN: ${csrf_token:-none}" --cookie "$csrf_jar" \
	--data '{"token":"deployment-check-not-a-real-token"}'
if [[ "$(status_of exchange)" == "404" ]]; then
	pass "an invalid Tracking Link is refused with the generic 404"
else
	fail "/api/tracking-session returned $(status_of exchange) for a nonsense token, expected 404"
fi
expect_header_contains exchange "Cache-Control" "no-store" "the refusal is no-store"
expect_header_contains exchange "Referrer-Policy" "no-referrer" "the refusal sends no referrer"

# Looking for leaked data, not for the word "delivery" — the error type is a `urn:delivery-glance:`
# URN and always contains it.
if grep -qiE '"(reference|state|handoffAddress|pickupAddress|courier|courierDisplayName|latitude|longitude)"' \
	"$workdir/exchange.body"; then
	fail "the refusal body carries Delivery fields: $(head -c 200 "$workdir/exchange.body")"
else
	pass "the refusal carries no fact about any Delivery"
fi

# ---------------------------------------------------------------------------
echo
echo "Deployment inputs"

# The bootstrap page carries the configured style in a meta tag, so this is readable without a link.
map_style="$(grep -oE 'name="delivery-glance-map-style" content="[^"]*"' "$workdir/track.body" |
	head -1 | sed 's/.*content="//; s/"$//')"
if [[ -z "$map_style" ]]; then
	note "TRACKING_MAP_STYLE_URL is unset — the Recipient view will show its map-unavailable state"
else
	note "a map style is configured: $map_style"
	if [[ "$map_style" == *"delivery"* || "$map_style" == *"track"* ]]; then
		fail "the map style URL looks like it carries application state; it must carry no Delivery or Tracking token"
	fi
fi

fetch demo "$BASE_URL/api/demo/reset" --request POST
case "$(status_of demo)" in
	401 | 403)
		note "the demo reset is refused for an anonymous caller ($(status_of demo)); whether it is switched on at all cannot be told from outside, which is intended"
		;;
	200)
		fail "POST /api/demo/reset succeeded WITHOUT AUTHENTICATION — this deployment just wiped its own data"
		;;
	*)
		note "POST /api/demo/reset returned $(status_of demo)"
		;;
esac

# ---------------------------------------------------------------------------
echo
if [[ "$failures" -eq 0 ]]; then
	printf '\033[32mAll required checks passed.\033[0m\n\n'
	exit 0
fi
printf '\033[31m%d required check(s) failed.\033[0m\n\n' "$failures"
exit 1
