package com.deliveryglance.identityaccess;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Resolves a sign-in attempt against the pre-provisioned Internal Accounts. Every failure reason
 * reaches the caller as the same authentication failure, so an unknown email cannot be told apart
 * from a wrong password.
 */
@Service
class InternalAccountUserDetailsService implements UserDetailsService {

	private final InternalAccountRepository accounts;

	InternalAccountUserDetailsService(InternalAccountRepository accounts) {
		this.accounts = accounts;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return this.accounts.findByEmail(email)
			.orElseThrow(() -> new UsernameNotFoundException("No Internal Account for the supplied email"));
	}

}
