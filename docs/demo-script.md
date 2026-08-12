# The three-role demo script

Status: current

A walkthrough that produces the same demo every time, for the recorded video and for anybody who
wants to see the product work without reading it first. It takes about six minutes at a talking pace.

Everything it shows is fictional: two pre-provisioned accounts, two invented addresses and a
coordinate typed into a browser's location emulator. Nothing here is anybody's doorstep, and nothing
you do while following it retains a real position.

## Before you start

**One running instance**, either the local Compose stack or the deployment:

```bash
TRACKING_MAP_STYLE_URL=<your style URL> DEMO_RESET_ENABLED=true docker compose up --build --wait
```

Both variables are optional and the walkthrough works without either.
[`TRACKING_MAP_STYLE_URL`](deployment.md#6--the-map-is-a-release-input) unset means step 8 shows the
honest map-unavailable state instead of a map — say so on camera rather than skipping it.
`DEMO_RESET_ENABLED` is only needed for the reset in step 1; locally you can use
`docker compose down -v` instead.

**Three browser contexts**, because this is three people:

| Role | Window | Why |
|---|---|---|
| Dispatcher | a desktop window | signs in with the Dispatcher account |
| Courier | a second profile or an incognito window, sized like a phone | one browser holds one session, and these are two accounts |
| Recipient | a real phone, or a third private window | has no account at all, which is the point |

**A location for the Courier.** In the Courier window open DevTools → ⋮ → More tools → Sensors, and
set Location to a custom position of **51.5005, -0.1205**. That is a few hundred metres from the
fictional pickup, so the recommendation has a real distance to show. Leave the panel open; step 7
changes it.

**The two accounts** are in [the README](../README.md#run-it).

## The script

### 1 · Reset, so the demo starts where it always starts

As the **Dispatcher**, sign in and reset:

```bash
curl -s -c jar https://<host>/api/system > /dev/null
csrf() { awk '/XSRF-TOKEN/ {print $7}' jar; }
curl -s -b jar -c jar -H "X-XSRF-TOKEN: $(csrf)" \
  -d email=dispatcher@delivery-glance.example -d 'password=Dispatcher-Demo-2026!' \
  https://<host>/api/session/login
curl -s -b jar -c jar -H "X-XSRF-TOKEN: $(csrf)" -X POST https://<host>/api/demo/reset
```

It answers `{"createdReferences":["DEMO-1001","DEMO-1002"]}`. Every earlier Delivery, Assignment and
Tracking Link is gone, the Courier is Off Duty, and no coordinates are held for anybody.

> Locally you can instead run `docker compose down -v && docker compose up --build --wait`, which
> removes the database volume. That is the stronger reset and needs no endpoint switched on.

### 2 · The Dispatcher's board

Sign in at `/` as the Dispatcher. Two Deliveries, both **Awaiting Courier**.

> Say: a Delivery is created Awaiting Courier and moves only through guarded transitions. There is no
> free-text status field anywhere in this product.

Open **DEMO-1001**. Point at **Nearest eligible Couriers**: it says *No Courier is currently
eligible*. Nobody is On Duty yet, so there is nobody to assign — and it says which fact it is missing.

### 3 · The Courier goes On Duty

In the Courier window, sign in and press **Go on duty**.

> Say: nothing has asked for a location yet, and nothing will until the Courier asks it to. On Duty is
> a durable declaration; it survives sign-out and restart.

### 4 · The Courier starts Location Sharing

Press **Start sharing**. *Only now* does the browser ask for permission. Accept it.

Within a few seconds the Courier's own panel shows **Live** and a position age that counts up.

> Say: the page reports the newest position about every ten seconds and only while it is in front of
> you. Switch to another tab and reporting pauses honestly rather than pretending to continue.
> The server keeps one snapshot per Courier, in memory, and never a trail.

### 5 · Direct Assignment

Back in the Dispatcher window on DEMO-1001, press **Refresh recommendation**.

Cory the Courier now appears with a distance in metres — around 400 m.

> Say: the Dispatcher sees a distance, never a coordinate. That derivation happens on the server; the
> Courier's position does not reach this page.

Press **Direct assign**. The Delivery becomes **Assigned**, once, and the history gains a transition
with the actor's name.

> Say: two Dispatchers pressing this at the same instant is the hardest correctness problem in the
> product. The application revalidates, but what actually decides it is a partial unique index in
> PostgreSQL — one caller gets `204`, the other `409`, and there is a test that proves it against a
> real database from a latch. See `docs/testing.md`.

### 6 · The Recipient opens their link

Copy the link for DEMO-1001 — there is no button for this yet, which is a known gap:

```bash
curl -s -b jar -c jar -H "X-XSRF-TOKEN: $(csrf)" -X POST \
  https://<host>/api/deliveries/<DEMO-1001 id>/tracking-link/copy
```

Send the returned URL to the phone and open it.

> Say: the capability is in the fragment — the part after `#` — which RFC 3986 keeps out of every HTTP
> request, so it never reaches a server log or a proxy. The page exchanges it for a short-lived cookie
> and removes it from the address bar and from history before rendering anything. Pressing Back does
> not walk into a URL that still carries the token.

Read what the page shows: the Reference, the Handoff Address, that a courier is being arranged, and
Cory's limited display name. **No map.**

> Say: a Courier heading to a pickup is not information about *this* Delivery's journey, and drawing
> it would put the Pickup Address on screen by inference. So the positions the Courier is already
> sending change nothing here. At the foot of the page: *Updating automatically*.

**Leave this page open for the rest of the walkthrough.** Nothing below asks you to reload it.

### 7 · Pickup, and the map appears

In the Courier window press **Confirm pickup**.

The Recipient's page changes by itself: the map appears with the handoff marker and the Courier's
current position, its accuracy, and **Live location**.

Now move the Courier. In the Sensors panel set the location to **51.5042, -0.1241** and watch the
marker move on the Recipient's phone.

> Say: what came down the stream was a version number saying "read again" — never a state, a name or a
> coordinate. Every fact on this screen arrived through the same authorised snapshot read the page
> does on load. The stream is a saving on polling, not a second source of truth.

### 8 · The honest part

Stop the Courier reporting: switch the Courier window to another tab, or turn its device location
off. **Do not touch the Recipient's phone.**

Watch it age itself: **Live** for thirty seconds, then **Delayed**, and at two minutes the marker
disappears and it says **Location unavailable** — while still saying it is connected.

> Say: nothing arrived from the server to cause that. The page computes freshness from the reading's
> own timestamp, so it will not claim to know where somebody is on the strength of a two-minute-old
> fix, even if it has lost its connection entirely. "Am I still hearing about changes?" and "how old
> is this reading?" are different questions and it answers both.

If you want the stronger version, turn the phone's network off for a moment: the page says
*Reconnecting for updates…* while keeping everything it was already showing, then reconnects and
fetches the current snapshot in one read.

### 9 · Handoff

Bring the Courier window back, press **Stop sharing** — the marker goes immediately rather than
ageing out — then **Confirm handoff**.

The Recipient's page keeps the Reference, the Handoff Address and the actual handoff time, and loses
Cory's name and every trace of location.

> Say: a delivered Delivery has nothing to say about where anybody is, so it stops saying it.

### 10 · The other Delivery, cancelled

In the Dispatcher window open **DEMO-1002**, press **Cancel delivery** and give a reason.

Copy that Delivery's Tracking Link the same way and open it. It shows the Reference, that it was
cancelled, when, and who to contact — but not the Handoff Address, and never the internal reason the
Dispatcher gave.

### 11 · The one that is not on screen

```bash
curl -s -X POST https://<host>/api/tracking-session \
  -H 'Content-Type: application/json' -d '{"token":"not-a-real-token"}'
```

`404`, with a body that is identical for a tampered token, an unknown link and an expired one.

> Say: that is what stops the link becoming a way to ask whether a Delivery exists.

## What to say at the end

The [resume checkpoint](planning/12-rescope-to-resume-ready-core.md#resume-checkpoint) sentence, and
then the limits: one instance, no ETA, no Reassignment or Undeliverable outcome, and no latency or
throughput number anywhere because nothing in the repository measures one.
[`docs/architecture.md`](architecture.md#known-limits) lists them; the demo is stronger for naming
them out loud than for hoping nobody asks.

## If you are recording

- Record the Recipient's phone screen for steps 6–9. That is the product; the other two windows are
  how it gets fed.
- Step 8 takes two real minutes. Cut to the Delayed and Unavailable moments rather than waiting, but
  do not fake the timing — the two minutes is a claim the product makes.
- Do not show the Sensors panel's coordinates on camera for longer than it takes to set them. They
  are fictional, and the habit is the point.
