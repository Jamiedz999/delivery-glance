# DG-021 · Add Internal Account sign-in and the persisted Delivery slice

Type: implementation
Sprint: 1
Area: full-stack
Blocked by: DG-020
Estimate: 4–5 focused hours

## Outcome

A pre-provisioned Dispatcher can sign in, create a Delivery, list Deliveries, reopen a persisted detail after restart and cancel it while it is still awaiting a Courier. This completes the Sprint 1 vertical slice.

## Read first

- [Core technical baseline](../implementation/TECHNICAL-BASELINE.md)
- [Ticket 12: Sprint 1 scope](../12-rescope-to-resume-ready-core.md#sprint-1--walking-skeleton--810-hours)
- [Lifecycle decision, using its Core scope update](../../adr/03-define-delivery-lifecycle.md)
- [Domain language](../../../CONTEXT.md)

## In scope

Introduce only `identityaccess` and `delivery`.

### Internal Account contract

- Flyway creates pre-provisioned `DISPATCHER` and `COURIER` Internal Accounts with unique normalised email, strong one-way password hashes, display name, enabled flag and role.
- Fictional demo credentials are configurable and documented. There is no registration, invitation, password reset or account administration.
- Use a same-origin opaque Spring Security session persisted with Spring Session JDBC. Keep CSRF enabled; React reads the CSRF cookie and sends the header on unsafe requests.
- `POST /api/session/login` accepts form-encoded email/password and returns `204` or the common authentication failure.
- `GET /api/session` returns only current display name and role; `DELETE /api/session` signs out.
- Dispatcher Delivery routes return `403` to a signed-in Courier and `401` without an Internal Account session.

### Delivery contract

Create durable `delivery` and `delivery_transition` tables using explicit SQL and constraints. A Delivery has a server-generated ID, unique non-sensitive Delivery Reference, readable pickup/handoff addresses, validated WGS84 points, state, optimistic version and timestamps.

The Core slice exposes:

- `POST /api/deliveries`
- `GET /api/deliveries`
- `GET /api/deliveries/{id}`
- `POST /api/deliveries/{id}/cancel`

Creation accepts the reference plus pickup/handoff address labels and coordinates. It creates `AWAITING_COURIER` and its first Delivery Transition in one transaction. Cancel requires the expected version, is idempotent by command ID, and succeeds only from `AWAITING_COURIER` in this Issue. Invalid transitions return a stable conflict response instead of overwriting state.

Build the minimum Dispatcher UI: sign-in, Delivery list, create form, detail and Cancel action. Use server-built DTOs and accessible loading, empty, validation, authorization and conflict states.

## Ordered work

1. Add Flyway migrations, constraints and explicit JdbcClient repositories inside the two modules.
2. Add pre-provisioned authentication/session/CSRF behaviour and focused security tests.
3. Add guarded Delivery use cases and API contracts with transaction and state-transition tests.
4. Add the Dispatcher routes and forms, keeping API state in TanStack Query rather than a new global store.
5. Verify persistence across application restart through Compose and update the README demo path.

## Acceptance criteria

- The DG-020 commands stay green.
- Backend tests prove unauthenticated, wrong-role, valid Dispatcher and bad-CSRF behaviour.
- Backend tests prove create/list/detail, duplicate reference rejection, invalid coordinate rejection, optimistic conflict, idempotent retry and guarded cancellation.
- The database schema is created only by Flyway; Boot starts with schema validation and no runtime DDL.
- In the Compose app, a Dispatcher creates a fictional Delivery, restarts the application container and reopens the same detail.
- The same UI cannot expose a Dispatcher route to a Courier, and direct API access is also denied server-side.
- No Tracking Link, assignment, Courier availability, ETA or location feature is present.

## Non-goals

- Self-service identity flows, Recipient accounts or account management.
- Search, dashboards, pagination polish, bulk operations or real addresses/geocoding.
- Assignment, Matching Round, Service Zones, Tracking Links or realtime updates.
- A generic CRUD/repository framework shared between modules.

## PR evidence

Include backend/frontend check output and a short recording or screenshots showing login, create, restart and reopen. After merge, close DG-021, run the Sprint 1 release gate, and promote only DG-022 to `ready`.
