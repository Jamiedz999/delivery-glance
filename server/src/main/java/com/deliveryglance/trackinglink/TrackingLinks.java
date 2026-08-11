package com.deliveryglance.trackinglink;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import com.deliveryglance.delivery.NewDeliveryLinks;
import com.deliveryglance.identityaccess.CurrentActor;
import com.deliveryglance.identityaccess.CurrentActorProvider;
import com.deliveryglance.shared.Secrets;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Tracking Link use cases: create one with a Delivery, hand the Dispatcher the same link as
 * often as they ask, and turn a presented capability into a scoped grant.
 *
 * <p>Every failure on the holder-facing paths raises the same {@link UnavailableLinkException}, and
 * they are written so the refusals cost roughly the same work: a token that decodes to nothing and a
 * token whose link expired yesterday both do a verifier lookup and then stop.
 */
@Service
class TrackingLinks implements NewDeliveryLinks {

	/** ADR 06's absolute cap, counted from issue and never extended by viewing or Copy. */
	static final Duration LIFETIME = Duration.ofDays(7);

	/** Exactly one 256-bit base64url capability, and nothing else. */
	private static final Pattern WELL_FORMED_TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

	private static final int FIRST_GENERATION = 1;

	private final TrackingLinkRepository repository;

	private final TrackedDeliveries deliveries;

	private final TrackingCapabilities capabilities;

	private final CurrentActorProvider currentActorProvider;

	private final Clock clock;

	TrackingLinks(TrackingLinkRepository repository, TrackedDeliveries deliveries, TrackingCapabilities capabilities,
			CurrentActorProvider currentActorProvider, Clock clock) {
		this.repository = repository;
		this.deliveries = deliveries;
		this.capabilities = capabilities;
		this.currentActorProvider = currentActorProvider;
		this.clock = clock;
	}

	@Override
	public void createFor(UUID deliveryId, Instant createdAt) {
		UUID linkId = UUID.randomUUID();
		int keyVersion = this.capabilities.currentKeyVersion();
		String token = this.capabilities.derive(linkId, FIRST_GENERATION, keyVersion);
		// The token is derived here only so its verifier can be stored. It is not returned, not
		// logged, and goes out of scope with this method.
		this.repository.insertLink(deliveryId, linkId, FIRST_GENERATION, keyVersion,
				Secrets.verifierOf(token), createdAt, createdAt.plus(LIFETIME));
	}

	/**
	 * Rederives the Delivery's current capability and checks it against the stored verifier before
	 * handing it out. The check is not ceremony: if a key is misconfigured, the derivation produces
	 * a token that no holder could ever redeem, and failing here says so instead of distributing a
	 * link that reports itself unavailable to the Recipient.
	 */
	@Transactional
	TrackingLinkViews.CopiedLink copyFor(UUID deliveryId) {
		TrackingLinkRepository.StoredLink link = this.repository.findByDelivery(deliveryId)
			.orElseThrow(TrackingLinkNotFoundException::new);
		String token = this.capabilities.derive(link.linkId(), link.generation(), link.keyVersion());
		if (!Secrets.matches(token, link.tokenVerifier())) {
			throw new IllegalStateException(
					"Rederived capability for link " + link.linkId() + " does not match its stored verifier");
		}

		Instant now = this.clock.instant();
		CurrentActor actor = this.currentActorProvider.requireCurrentActor();
		// Actor and time only. ADR 06's full Tracking Link History is Future Work 14, and this table
		// has no column that could hold the token or the URL built from it.
		this.repository.insertCopy(link.linkId(), actor.accountId(), now);

		return new TrackingLinkViews.CopiedLink("/track#t=" + token, effectiveExpiryOf(link));
	}

	/**
	 * Exchanges a presented capability for a grant, returning the secret the cookie will carry.
	 *
	 * @throws UnavailableLinkException for a malformed, unknown or expired capability, with nothing
	 * in the exception or its absence to tell those three apart
	 */
	@Transactional
	GrantIssued exchange(String token) {
		if (token == null || !WELL_FORMED_TOKEN.matcher(token).matches()) {
			throw new UnavailableLinkException();
		}

		TrackingLinkRepository.StoredLink link = this.repository
			.findByTokenVerifier(Secrets.verifierOf(token))
			.orElseThrow(UnavailableLinkException::new);
		// The lookup above found a candidate by an indexed equality match on a digest; this is the
		// comparison that actually authorizes, and it does not stop early on the first wrong byte.
		if (!Secrets.matches(token, link.tokenVerifier())) {
			throw new UnavailableLinkException();
		}

		Instant now = this.clock.instant();
		Instant expiry = effectiveExpiryOf(link);
		if (!TrackingLinkExpiry.isValidAt(expiry, now)) {
			throw new UnavailableLinkException();
		}

		String secret = Secrets.issue();
		this.repository.insertGrant(UUID.randomUUID(), link.linkId(), link.generation(),
				Secrets.verifierOf(secret), now, expiry);
		return new GrantIssued(secret, now, expiry);
	}

	/**
	 * The minimal authorised snapshot DG-024 owes: enough to prove the grant resolves to one
	 * Delivery and no more. DG-025 replaces it with the real Recipient projection.
	 */
	@Transactional(readOnly = true)
	TrackingLinkViews.Snapshot snapshotFor(String grantSecret) {
		TrackingLinkRepository.StoredGrant grant = this.repository
			.findGrantByVerifier(Secrets.verifierOf(grantSecret))
			.orElseThrow(UnavailableLinkException::new);
		// A grant is scoped to the generation it was established through, so a rotation invalidates
		// derived access without having to find every grant it produced. Core never rotates; the
		// check is here because the grant table would otherwise outlive the rule that justifies it.
		if (grant.generation() != grant.linkGeneration()) {
			throw new UnavailableLinkException();
		}

		Instant now = this.clock.instant();
		if (!TrackingLinkExpiry.isValidAt(grant.expiresAt(), now)) {
			throw new UnavailableLinkException();
		}

		TrackedDeliveries.TrackedDelivery delivery = this.deliveries.find(grant.deliveryId())
			.orElseThrow(UnavailableLinkException::new);
		// The grant's own bound was fixed at exchange. A Delivery that has reached a terminal state
		// since then has a shorter one, and the link is the authority on that, not the cookie.
		if (!TrackingLinkExpiry.isValidAt(
				TrackingLinkExpiry.effective(grant.linkExpiresAt(), delivery.terminalAt()), now)) {
			throw new UnavailableLinkException();
		}

		return new TrackingLinkViews.Snapshot(delivery.reference());
	}

	private Instant effectiveExpiryOf(TrackingLinkRepository.StoredLink link) {
		TrackedDeliveries.TrackedDelivery delivery = this.deliveries.find(link.deliveryId())
			.orElseThrow(UnavailableLinkException::new);
		return TrackingLinkExpiry.effective(link.expiresAt(), delivery.terminalAt());
	}

	/**
	 * @param secret the value the cookie carries; only its verifier was stored
	 * @param establishedAt returned so the caller sizing the cookie's max-age uses the instant the
	 * grant was actually written, rather than reading the clock a second time
	 */
	record GrantIssued(String secret, Instant establishedAt, Instant expiresAt) {
	}

}
