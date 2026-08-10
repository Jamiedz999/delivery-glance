# “Delivery Tracker” focused name check

Research date: 2026-08-09

## Decision-relevant finding

Under the revised rule, ordinary exact-name reuse is not the key issue. The relevant question is whether an exact same-name public project also has substantially the same recipient delivery-tracking function and a similar Java/Spring Boot + React/TypeScript-style stack.

**Yes, materially close projects exist, although no inspected repository is a complete end-to-end twin of the proposed product.** Two normalised-exact repositories reproduce its central live-courier-location capability on a Spring Boot backend, and a third uses almost the same full-stack skeleton for a simpler delivery-status application. That creates a real “same name, same technical neighbourhood” signal; the commercial products and generic-name volume below are separate considerations, not the basis of that finding.

Here, “normalised exact” means lowercasing a repository name and removing punctuation, so `Delivery-Tracker`, `delivery_tracker`, and `DeliveryTracker` all compare equal.

## Closest exact-name repositories

| Repository | Function overlap | Stack overlap | Assessment against the stated red line |
|---|---|---|---|
| [`wasdfg/DeliveryTracker`](https://github.com/wasdfg/DeliveryTracker) | Its README says it links a courier's location so consumers can see it easily—the proposed product's central Recipient-facing map promise. | Its [`build.gradle`](https://github.com/wasdfg/DeliveryTracker/blob/main/build.gradle) declares Java 17, Spring Boot 3.2, Security/JWT, WebSocket, Redis, Kafka, JPA, and MySQL. No React/TypeScript client was found in this repository. | **High functional and backend-stack overlap.** It lacks the inspected frontend and the proposed Tracking Link/ETA/freshness details, but it is already the same-name, Spring-based “consumer watches courier location” idea. |
| [`gdntts/delivery_tracker`](https://github.com/gdntts/delivery_tracker) | Its README documents per-order courier GPS ingestion, persisted current/history positions, and instant WebSocket delivery to watching clients. | The same [README](https://github.com/gdntts/delivery_tracker/blob/main/README.md) and [`pom.xml`](https://github.com/gdntts/delivery_tracker/blob/main/pom.xml) specify Java 21, Spring Boot 4.1, PostgreSQL, JPA, Flyway, and WebSocket/STOMP. It is backend-only. | **High overlap in the project's hardest backend slice.** It does not show the Recipient UI, Dispatcher/Courier workflows, secure Tracking Link, or ETA, but the name, real-time location function, language, framework, database family, and event delivery are all close. |
| [`HigorRobertoDev/delivery-tracker`](https://github.com/HigorRobertoDev/delivery-tracker) | A simplified delivery-order tracker with authentication, order creation/listing, and a delivery status lifecycle. It does not document live courier position, ETA, or a no-login Recipient link. | Its [README](https://github.com/HigorRobertoDev/delivery-tracker/blob/main/README.md), backend [`pom.xml`](https://github.com/HigorRobertoDev/delivery-tracker/blob/main/backend/pom.xml), and frontend [`package.json`](https://github.com/HigorRobertoDev/delivery-tracker/blob/main/frontend/package.json) show Java 21, Spring Boot 3, Spring Security/JWT, JPA, React 18, and Vite. | **Near-full stack match, but only moderate function match.** This is the closest inspected full-stack skeleton, yet its product is order CRUD/status management rather than recipient-first live tracking. |
| [`leticiafdepaula/Delivery_tracker`](https://github.com/leticiafdepaula/Delivery_tracker) | Delivery-order creation, status transitions, and status history; no live location is documented. | Its [README](https://github.com/leticiafdepaula/Delivery_tracker/blob/main/README.md) and [`pom.xml`](https://github.com/leticiafdepaula/Delivery_tracker/blob/main/pom.xml) show Java 21, Spring Boot 4, Security/JWT, JPA, and SQLite, and link a separate frontend repository. | **Lower than the three above:** similar domain and backend conventions, but not the defining recipient live-location experience in the inspected code. |

The proposed product remains distinguishable by its complete combination of Dispatcher-confirmed Courier Recommendation, Courier lifecycle updates, expiring no-account Tracking Link, location freshness, Recipient ETA/next-step communication, and staged architecture. The risk comes from the exact title plus close public implementations of individual core slices, not evidence that the whole specification already exists unchanged.

## Breadth of GitHub reuse

On 2026-08-09, GitHub's public repository-name query returned **1,010** relevant results. After normalising names as above, **92 of the first 100 results were exact `deliverytracker` matches** ([reproducible API query](https://api.github.com/search/repositories?q=delivery-tracker+in%3Aname&per_page=100)). Examples include an actively maintained TypeScript delivery/shipping service, [`shlee322/delivery-tracker`](https://github.com/shlee322/delivery-tracker), and the same-name Node tracking library, [`egg-/delivery-tracker`](https://github.com/egg-/delivery-tracker).

That volume establishes that the phrase behaves like a category label. Under the revised criterion this is not automatically disqualifying, but an owner-qualified search will be needed to find a new repository reliably.

## Package registries

- npm's exact hyphenated name is occupied by public package [`delivery-tracker`](https://www.npmjs.com/package/delivery-tracker), a Node.js courier-tracking library at version 2.8.3. This is a technical namespace collision, but its Node library shape differs materially from the planned application.
- The compact npm name [`deliverytracker`](https://registry.npmjs.org/deliverytracker), plus PyPI's [`delivery-tracker`](https://pypi.org/pypi/delivery-tracker/json) and [`deliverytracker`](https://pypi.org/pypi/deliverytracker/json), returned HTTP 404 in the same-day checks.

## Exact commercial/product names (reported separately)

These are exact public product-name collisions, but they do not by themselves imply a code clone because their implementation stacks are not established here:

- Joywide operates an exact-name [Delivery Tracker](https://tracker.delivery/en/) cloud/self-hosted shipment API with webhooks and a Tracking Link.
- Metapack calls its consumer-facing branded tracking portal [Delivery Tracker](https://www.metapack.com/blog/introducing-delivery-tracker/); it displays current delivery status to ecommerce recipients.
- TRUX markets a [Delivery Tracker](https://www.truxnow.com/products-delivery-tracker) module with customer-visible progress, real-time vehicle location, and ETA in the construction-materials domain.
- Appelio's [`deliverytracker.app`](https://deliverytracker.app/) uses “Delivery Tracker” as its site heading, although its linked current Apple listing is titled [Package Tracker & Track Parcel](https://apps.apple.com/us/app/package-tracker-track-parcel/id6746402409).

## Bounds and caveat

The deep code inspection was deliberately bounded to four high-risk normalised-exact repositories selected from the first 100 general results and the first 100 Java, TypeScript, and JavaScript language-filtered results. Many exact-name repositories remain uninspected and could contain a closer match. Counts and repository contents can also change.

This report establishes search and similarity evidence only. It is not trademark advice, legal clearance, a reservation of any name, or the final naming decision.
