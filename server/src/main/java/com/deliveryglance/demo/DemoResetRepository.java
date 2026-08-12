package com.deliveryglance.demo;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Empties every table the demo is allowed to own, in the order the foreign keys require.
 *
 * <p>This is the one place in the application that writes another module's tables, and it is a
 * deliberate trade rather than an oversight. The alternative is a {@code deleteEverything()} on the
 * delivery, dispatch, trackinglink, courier and location modules — five destructive methods living
 * permanently in production code, reachable by any caller in the process, to serve one fixture. One
 * clearly named class that exists only when the demo switch is on, and that names every table it
 * empties, is the smaller hole.
 *
 * <p>What it never touches is as much of the contract as what it does. {@code internal_account} is
 * seeded by {@code V1__internal_account.sql} and is the only reason the demo can be signed into, so
 * a reset that dropped it would need password hashes at runtime and would become a second source of
 * truth for them. Spring Session's tables are left alone too, so the Dispatcher who pressed reset is
 * still signed in to look at the result.
 */
class DemoResetRepository {

	/**
	 * Children before parents, and coordinates before the Couriers they belong to. Written out one
	 * statement at a time rather than as a {@code TRUNCATE ... CASCADE}, because CASCADE would follow
	 * a foreign key into {@code internal_account} if one were ever added and silently delete the
	 * accounts this method is careful to keep.
	 *
	 * <p>A table added by a later migration and forgotten here would leave rows behind that a reset
	 * claims to have removed. {@code SchemaOwnershipTest.containsOnlyTheTablesTheMigrationsCreate}
	 * is what notices: it names every table in the schema, so a new one fails there first.
	 */
	private static final String[] TABLES_IN_DEPENDENCY_ORDER = { "tracking_grant", "tracking_link_copy",
			"tracking_link", "assignment", "delivery_transition", "delivery", "courier_location_sharing", "courier" };

	private final JdbcClient jdbcClient;

	DemoResetRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	void deleteEveryDeliveryAndCourierFact() {
		for (String table : TABLES_IN_DEPENDENCY_ORDER) {
			this.jdbcClient.sql("DELETE FROM " + table).update();
		}
	}

}
