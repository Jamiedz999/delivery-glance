package com.deliveryglance.trackinglink;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Copy has to hand out the same link twice without anything having stored it, which is only true if
 * the capability is a pure function of the link's identity, its generation and a deployment key.
 * These tests pin that function: same inputs, same token; any input different, different token.
 */
class TrackingCapabilitiesTest {

	private static final UUID LINK = UUID.fromString("6f1e3c2a-0000-4000-8000-000000000001");

	private static final UUID OTHER_LINK = UUID.fromString("6f1e3c2a-0000-4000-8000-000000000002");

	private static final byte[] KEY_ONE = "the-first-deployment-key-material".getBytes(java.nio.charset.StandardCharsets.UTF_8);

	private static final byte[] KEY_TWO = "a-completely-different-key-value!".getBytes(java.nio.charset.StandardCharsets.UTF_8);

	private final TrackingCapabilities capabilities = new TrackingCapabilities(Map.of(1, KEY_ONE, 2, KEY_TWO), 2);

	@Test
	void derivesTheSameTokenEveryTimeForOneLinkGenerationAndKey() {
		String first = this.capabilities.derive(LINK, 1, 1);
		String second = this.capabilities.derive(LINK, 1, 1);

		assertThat(first).isEqualTo(second);
	}

	@Test
	void derivesADifferentTokenForADifferentLinkIdentity() {
		assertThat(this.capabilities.derive(LINK, 1, 1)).isNotEqualTo(this.capabilities.derive(OTHER_LINK, 1, 1));
	}

	@Test
	void derivesADifferentTokenForADifferentGeneration() {
		assertThat(this.capabilities.derive(LINK, 1, 1)).isNotEqualTo(this.capabilities.derive(LINK, 2, 1));
	}

	@Test
	void derivesADifferentTokenForADifferentKeyVersion() {
		assertThat(this.capabilities.derive(LINK, 1, 1)).isNotEqualTo(this.capabilities.derive(LINK, 1, 2));
	}

	/**
	 * A short token would be guessable however careful everything downstream is, so the width is
	 * asserted rather than left to whichever digest the implementation happens to use.
	 */
	@Test
	void derivesAFullTwoHundredAndFiftySixBitToken() {
		byte[] decoded = Base64.getUrlDecoder().decode(this.capabilities.derive(LINK, 1, 1));

		assertThat(decoded).hasSize(32);
	}

	@Test
	void producesUrlSafeUnpaddedTokens() {
		assertThat(this.capabilities.derive(LINK, 1, 1)).matches("^[A-Za-z0-9_-]{43}$");
	}

	@Test
	void usesTheConfiguredCurrentKeyForNewLinks() {
		assertThat(this.capabilities.currentKeyVersion()).isEqualTo(2);
	}

	/**
	 * A link issued under a key that is no longer configured cannot be rederived. Failing loudly is
	 * the only honest option: silently deriving under the current key would mint a token that the
	 * stored verifier rejects, and report it as an unavailable link.
	 */
	@Test
	void refusesToDeriveUnderAKeyVersionItDoesNotHold() {
		assertThatIllegalStateException().isThrownBy(() -> this.capabilities.derive(LINK, 1, 9))
			.withMessageContaining("9");
	}

	@Test
	void verifiesATokenAgainstItsOwnVerifier() {
		String token = this.capabilities.derive(LINK, 1, 1);

		assertThat(TrackingCapabilities.matches(token, TrackingCapabilities.verifierOf(token))).isTrue();
	}

	@Test
	void rejectsATokenAgainstAnotherLinksVerifier() {
		String token = this.capabilities.derive(LINK, 1, 1);
		String otherVerifier = TrackingCapabilities.verifierOf(this.capabilities.derive(OTHER_LINK, 1, 1));

		assertThat(TrackingCapabilities.matches(token, otherVerifier)).isFalse();
	}

	@Test
	void storesAVerifierThatCannotBeReadBackAsAToken() {
		String token = this.capabilities.derive(LINK, 1, 1);

		assertThat(TrackingCapabilities.verifierOf(token)).doesNotContain(token).matches("^[0-9a-f]{64}$");
	}

}
