# ADR 06 — A Tracking Link is a capability, not an account

## The question

How does a Recipient with no account read exactly one Delivery, without that link becoming a way to
discover other Deliveries or a permanent key to somebody's address?

## What we decided

One Delivery has at most one working Tracking Link, created when the Delivery is created. Holding it
authorises reading that one Delivery. It proves nothing about who is holding it, and there is no PIN
and no Recipient account.

**Opening it changes nothing.** No first-open activation, no timer started, no business state
touched — so link previews, scanners and crawlers cannot consume it.

**It expires on its own**, at the earlier of seven days after it was made or twenty-four hours after
the Delivery finished. Opening it never extends either limit.

**A link that does not work says nothing about why.** Unknown, tampered with, expired and revoked all
produce one identical page:

> This tracking link is no longer available. Contact the delivery team that shared it.

**A Dispatcher can switch it off.** Revoke Link kills the link and every Tracking Session on it at
once. Replace Link does the same and issues one replacement, keeping the original expiry so it cannot
be used to extend access. Reissue Link makes a fresh link after the old one expired or was revoked,
while the Delivery is still running. Each needs a Link Change Reason and records who did it and when.

After the Delivery finishes, a still-valid link shows a reduced page: `DELIVERED` keeps the Delivery
Number, Delivery Address, result and time; `CANCELLED` keeps the Delivery Number, a plain result, the
time and the Support Contact — and **drops the address**.

## Why

The link is a bearer capability, so the whole design is about limiting what possession is worth. It
defends against guessing, against leaking through logs, referrers, previews and caches, and against
access outliving its purpose. It does not defend against somebody forwarding the link deliberately or
taking a screenshot, and it does not pretend to.

The identical dead-link page is the part that is easy to get wrong. A page that said "this link has
expired" would confirm that a Delivery exists, which turns the route into a way to ask questions
about strangers' deliveries.

The cancelled page keeps the Delivery Number but drops the address, which looks backwards until you
see what each one is. The page tells the Recipient to phone somebody; withholding the only identifier
that conversation can start from would be asking them to describe a delivery they cannot name. The
Number identifies a Delivery to the team that already owns it. The address identifies where a person
lives. Only one of those is worth protecting on a page that no longer needs it.

## What is built

Creation, Copy Link, expiry, the identical dead-link page, and the fragment-to-Tracking-Session
exchange. The raw capability is never stored — only a SHA-256 verifier — so a database copy cannot be
turned back into a working link, and Copy Link re-derives the same link rather than reading one back.

Revoke Link is built ([#59](https://github.com/Jamiedz999/delivery-glance/issues/59)). Replace Link
and Reissue Link are [#60](https://github.com/Jamiedz999/delivery-glance/issues/60); cutting off a
page that is already open is [#61](https://github.com/Jamiedz999/delivery-glance/issues/61); the
Dispatcher-visible Link History is [#62](https://github.com/Jamiedz999/delivery-glance/issues/62).

How the capability is generated, carried and exchanged is [ADR 10](10-choose-core-technical-architecture.md).
