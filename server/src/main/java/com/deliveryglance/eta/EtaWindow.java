package com.deliveryglance.eta;

import java.time.Instant;

/**
 * A published ETA Window: the earliest and latest a Delivery is estimated to reach its handoff.
 * Both endpoints sit on five-minute boundaries because the window is always rounded outward before
 * it is stored, so a Recipient is never shown a false-precision minute.
 *
 * <p>The window is a range rather than a single time on purpose — ADR 05 forbids presenting an
 * approximate estimate as a live fact, and a two-ended window is the honest shape of "around then".
 * Whether the window has been passed — "running later than expected" — is decided in the browser
 * against its own clock, the same place Location Freshness is aged, so the server stores only the two
 * endpoints and never a derived late flag.
 */
record EtaWindow(Instant start, Instant end) {
}
