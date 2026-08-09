# Clear, unclaimed project-name research

Research date: 2026-08-08

## Question

Which two-familiar-word English names make a recipient-facing last-mile Delivery tracker understandable on first contact, without suggesting fleet management, route optimisation, or a general logistics suite, and without an exact conflicting software name discoverable in a practical public search?

## Result

Six names survived the practical collision screen. The strongest decision set is **Delivery Glance**, **Delivery Ahead**, and **Delivery Peek**:

- **Delivery Glance** is the clearest visibility-oriented project name.
- **Delivery Ahead** is the most natural recipient/arrival promise.
- **Delivery Peek** has the cleanest search footprint, at the cost of a more playful tone.

The remaining three are usable, but each describes only part of the promise or introduces a metaphor. This is a ranked research shortlist, not the final naming decision.

## Method and limits

For each surviving name I checked:

1. GitHub's public repository search for the hyphenated words with `in:name`, then compared every returned repository name after lowercasing and removing punctuation. This catches `delivery-peek`, `Delivery_Peek`, and `DeliveryPeek` as the same exact name while keeping `Delivery at a Glance` distinct.
2. Exact unscoped package lookups on npm and PyPI, in both hyphenated and compact forms.
3. General-web exact-phrase searches combined with software-, app-, and tracking-oriented modifiers, followed by direct checks of product or brand pages when a potentially conflicting name surfaced.

No exact conflicting software product or project was discovered for the six shortlisted names under that procedure. That means “no collision found in this bounded search,” not that a name is reserved or legally cleared. This was not a comprehensive national/international trademark search; app-store internal indexes, domains, company registries, social handles, and future registrations can also change the answer.

## Ranked shortlist

### 1. Delivery Glance

**Why it fits:** Both words are common, `Delivery` fixes the domain immediately, and `Glance` promises one place to understand status, location, ETA, and what happens next. It sounds like a visibility product rather than a dispatch, fleet, or routing product.

**Trade-off:** “Glance” suggests a concise current view more strongly than continuous live tracking. GitHub returned two loose word matches and no punctuation-normalised exact match; one is the old, similarly phrased [`Delivery-at-a-Glance`](https://github.com/danielgruesso/Delivery-at-a-Glance) repository. That is a discoverable similarity, so this has slightly more search noise than Delivery Ahead or Delivery Peek, even though it is not the same name.

**Checks:** [GitHub query](https://api.github.com/search/repositories?q=delivery-glance+in%3Aname&per_page=100) · npm [hyphenated](https://registry.npmjs.org/delivery-glance) / [compact](https://registry.npmjs.org/deliveryglance) · PyPI [hyphenated](https://pypi.org/pypi/delivery-glance/json) / [compact](https://pypi.org/pypi/deliveryglance/json). Both package forms returned HTTP 404 in both registries.

### 2. Delivery Ahead

**Why it fits:** This is the most natural phrase from a Recipient's point of view: a Delivery is ahead, and the product helps them prepare for it. It foregrounds arrival without making any route-planning or fleet claim.

**Trade-off:** It communicates “on the way” more strongly than “track it here.” It also appears descriptively in ordinary prose, so the exact product name is clear but not highly ownable as a general phrase.

**Checks:** GitHub returned one long, unrelated repository name containing the search words and no punctuation-normalised exact match ([query](https://api.github.com/search/repositories?q=delivery-ahead+in%3Aname&per_page=100)) · npm [hyphenated](https://registry.npmjs.org/delivery-ahead) / [compact](https://registry.npmjs.org/deliveryahead) · PyPI [hyphenated](https://pypi.org/pypi/delivery-ahead/json) / [compact](https://pypi.org/pypi/deliveryahead/json). Both package forms returned HTTP 404 in both registries.

### 3. Delivery Peek

**Why it fits:** It reads as “peek at my Delivery,” which is strongly recipient-facing and maps cleanly to opening a Tracking Link. Its public-code search footprint was the cleanest of the set.

**Trade-off:** “Peek” is friendly and memorable but less serious than “Glance”; it may undersell the Dispatcher's and Courier's supporting workflows in a portfolio description.

**Checks:** GitHub returned zero repository-name results ([query](https://api.github.com/search/repositories?q=delivery-peek+in%3Aname&per_page=100)) · npm [hyphenated](https://registry.npmjs.org/delivery-peek) / [compact](https://registry.npmjs.org/deliverypeek) · PyPI [hyphenated](https://pypi.org/pypi/delivery-peek/json) / [compact](https://pypi.org/pypi/deliverypeek/json). Both package forms returned HTTP 404 in both registries.

### 4. Delivery Beacon

**Why it fits:** A beacon continually signals location and presence, which supports the product's status, current-location, and freshness promise. It avoids the operational implications of `Fleet`, `Route`, and `Logistics`.

**Trade-off:** This is a metaphor, not a literal tracker label. Some viewers may expect Bluetooth/GPS hardware or a notification service rather than a web application.

**Checks:** GitHub returned three loose word matches and no punctuation-normalised exact match ([query](https://api.github.com/search/repositories?q=delivery-beacon+in%3Aname&per_page=100)) · npm [hyphenated](https://registry.npmjs.org/delivery-beacon) / [compact](https://registry.npmjs.org/deliverybeacon) · PyPI [hyphenated](https://pypi.org/pypi/delivery-beacon/json) / [compact](https://pypi.org/pypi/deliverybeacon/json). Both package forms returned HTTP 404 in both registries.

### 5. Delivery Nearby

**Why it fits:** It is concrete, recipient-first, and immediately evokes the moment when a Courier is approaching the handoff address.

**Trade-off:** The product covers the full Delivery lifecycle, while this name describes only the late “nearby” phase. It may also read as a search for nearby delivery services rather than a page for following one active Delivery.

**Checks:** GitHub returned five broader nearby/delivery names and no punctuation-normalised exact match ([query](https://api.github.com/search/repositories?q=delivery-nearby+in%3Aname&per_page=100)) · npm [hyphenated](https://registry.npmjs.org/delivery-nearby) / [compact](https://registry.npmjs.org/deliverynearby) · PyPI [hyphenated](https://pypi.org/pypi/delivery-nearby/json) / [compact](https://pypi.org/pypi/deliverynearby/json). Both package forms returned HTTP 404 in both registries.

### 6. Delivery Snapshot

**Why it fits:** It promises a comprehensible summary of the Delivery's current state and is more formal than Delivery Peek.

**Trade-off:** “Snapshot” sounds static, which works against the real-time position and Location Freshness story. The phrase also appears as a section heading in an existing [Mercury Delivery manual](https://floristwiki.ftdi.com/images/8/8d/Chapter_14_-_Mercury_Delivery.pdf), although it is not presented there as the software's name.

**Checks:** GitHub returned five loose word matches and no punctuation-normalised exact match ([query](https://api.github.com/search/repositories?q=delivery-snapshot+in%3Aname&per_page=100)) · npm [hyphenated](https://registry.npmjs.org/delivery-snapshot) / [compact](https://registry.npmjs.org/deliverysnapshot) · PyPI [hyphenated](https://pypi.org/pypi/delivery-snapshot/json) / [compact](https://pypi.org/pypi/deliverysnapshot/json). Both package forms returned HTTP 404 in both registries.

## Candidates deliberately not advanced

- **Delivery Lookout:** no exact combined-name project or package was found, but `LOOKOUT®` is an active registered software/security brand according to [Lookout's own legal footer](https://www.lookout.com/). That is an avoidable brand association for another software product.
- **Delivery Progress:** GitHub's name search returns punctuation-normalised exact repositories named `Delivery-Progress` and `delivery_progress`, so it fails the requested repository-name screen ([query](https://api.github.com/search/repositories?q=delivery-progress+in%3Aname&per_page=100)).
- **Delivery Lens:** the phrase is already used within Digital.ai's named “Software Delivery Lens” offering in its [product brief](https://platform.softwareone.com/files/product-media-files/PCP-2400-6863/c5aff21a5f80c0b27a0f876de70e69676b8187592400201f36c410d5a6608250.pdf). It is not the same delivery domain, but software-search ambiguity is unnecessary.
- **Delivery Horizon:** GitHub already surfaces adjacent names such as `Horizon-Delivery-Manager` and `horizon-delivery-client` ([query](https://api.github.com/search/repositories?q=delivery-horizon+in%3Aname&per_page=100)), while “Horizon” does less to explain recipient tracking than the surviving words.
- **Arrival Signal:** package and exact repository checks were clean, but the phrase is strongly shared with scientific, transport, and airport-arrival contexts; removing `Delivery` makes first-contact comprehension worse.
- **Delivery Tracker**, **Delivery Status**, **Delivery Watch**, and **Delivery View:** these are literal but heavily generic feature/category phrases. They do not meet the goal of a search-distinct project identity, even if a particular punctuation variant might still be registerable.

## Naming-decision guidance

The human decision should compare the top three aloud in the same portfolio sentence:

> “I built **[name]**, a recipient-first last-mile Delivery tracking web application that shows status, courier location freshness, ETA, and what happens next.”

Choose **Delivery Glance** if immediate product comprehension matters most, **Delivery Ahead** if the arrival promise and professional tone matter most, or **Delivery Peek** if clean searchability and memorability matter most. Whichever wins should receive a final same-day recheck immediately before creating the public repository; availability is time-sensitive.
