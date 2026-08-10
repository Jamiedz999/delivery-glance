package com.deliveryglance.identityaccess;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads the pre-provisioned Internal Accounts. There is no write side: accounts arrive through the
 * Flyway migration that creates them.
 */
@Repository
class InternalAccountRepository {

	private final JdbcClient jdbcClient;

	InternalAccountRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	Optional<InternalAccountPrincipal> findByEmail(String email) {
		return this.jdbcClient
			.sql("""
					SELECT id, email, password_hash, display_name, role, enabled
					FROM internal_account
					WHERE email = :email
					""")
			.param("email", normalise(email))
			.query((rs, rowNumber) -> new InternalAccountPrincipal(rs.getObject("id", UUID.class), rs.getString("email"),
					rs.getString("password_hash"), rs.getString("display_name"),
					InternalAccountRole.valueOf(rs.getString("role")), rs.getBoolean("enabled")))
			.optional();
	}

	private static String normalise(String email) {
		return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
	}

}
