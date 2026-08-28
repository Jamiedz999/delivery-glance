package com.deliveryglance.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import com.deliveryglance.DemoAccounts;
import com.deliveryglance.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two sides of the one question the probe answers, decided against real internal_account rows:
 * the factory seed that V1 lays down, and a row whose password hash a deployment replaced.
 */
@IntegrationTest
class DemoAccountsProbeTest {

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void reportsUnchangedForTheFactorySeededRows() {
		assertThat(new DemoAccountsProbe(this.jdbcClient).demoAccountsUnchanged()).isTrue();
	}

	@Test
	@Transactional
	void reportsChangedWhenAnAccountHashWasReplaced() {
		this.jdbcClient
			.sql("UPDATE internal_account SET password_hash = '{bcrypt}$2a$12$replacedreplacedreplacedreplacedreplacedreplacedreb' "
					+ "WHERE email = :email")
			.param("email", DemoAccounts.COURIER_EMAIL)
			.update();

		assertThat(new DemoAccountsProbe(this.jdbcClient).demoAccountsUnchanged()).isFalse();
	}

}
