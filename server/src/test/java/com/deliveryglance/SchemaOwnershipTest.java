package com.deliveryglance;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway is the only thing allowed to create schema. If Boot, Spring Session or anything else ever
 * generates a table at runtime, the table list stops matching the migrations and this fails.
 */
@IntegrationTest
class SchemaOwnershipTest {

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void containsOnlyTheTablesTheMigrationsCreate() {
		List<String> tables = this.jdbcClient
			.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name")
			.query(String.class)
			.list();

		assertThat(tables).containsExactly("assignment", "courier", "courier_location_sharing", "delivery",
				"delivery_transition", "flyway_schema_history", "internal_account", "spring_session",
				"spring_session_attributes");
	}

	@Test
	void appliedEveryMigrationSuccessfully() {
		List<String> versions = this.jdbcClient
			.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
			.query(String.class)
			.list();

		assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6");
	}

	@Test
	void databaseArbitratesOneActiveAssignmentPerCourierAndDelivery() {
		List<String> partialUniqueIndexes = this.jdbcClient.sql("""
				SELECT indexname FROM pg_indexes
				WHERE schemaname = 'public' AND tablename = 'assignment'
				  AND indexdef LIKE 'CREATE UNIQUE INDEX%WHERE (ended_at IS NULL)'
				ORDER BY indexname
				""").query(String.class).list();

		assertThat(partialUniqueIndexes).containsExactly("assignment_one_active_courier_idx",
				"assignment_one_active_delivery_idx");
	}

	@Test
	void seedsBothPreProvisionedInternalAccountsWithEncodedPasswords() {
		List<String> accounts = this.jdbcClient
			.sql("SELECT email || ' ' || role FROM internal_account WHERE enabled AND password_hash LIKE '{bcrypt}$%' ORDER BY role")
			.query(String.class)
			.list();

		assertThat(accounts).containsExactly(DemoAccounts.COURIER_EMAIL + " COURIER",
				DemoAccounts.DISPATCHER_EMAIL + " DISPATCHER");
	}

}
