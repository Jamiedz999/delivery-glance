package com.deliveryglance.identityaccess;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated Internal Account as Spring Security holds it. It is serialized into the JDBC
 * session store, so every field has to stay small and serializable.
 */
final class InternalAccountPrincipal implements UserDetails, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final UUID accountId;

	private final String email;

	private final String passwordHash;

	private final String displayName;

	private final InternalAccountRole role;

	private final boolean enabled;

	InternalAccountPrincipal(UUID accountId, String email, String passwordHash, String displayName,
			InternalAccountRole role, boolean enabled) {
		this.accountId = accountId;
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.role = role;
		this.enabled = enabled;
	}

	CurrentActor toCurrentActor() {
		return new CurrentActor(this.accountId, this.displayName, this.role);
	}

	String displayName() {
		return this.displayName;
	}

	InternalAccountRole role() {
		return this.role;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority(this.role.authority()));
	}

	@Override
	public String getPassword() {
		return this.passwordHash;
	}

	@Override
	public String getUsername() {
		return this.email;
	}

	@Override
	public boolean isEnabled() {
		return this.enabled;
	}

}
