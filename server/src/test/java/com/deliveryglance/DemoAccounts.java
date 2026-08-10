package com.deliveryglance;

/**
 * The fictional Internal Accounts that {@code V1__internal_account.sql} seeds with the default
 * Flyway placeholders. They are also the credentials README.md documents for the demo.
 */
public final class DemoAccounts {

	public static final String DISPATCHER_EMAIL = "dispatcher@delivery-glance.example";

	public static final String DISPATCHER_DISPLAY_NAME = "Dana the Dispatcher";

	public static final String DISPATCHER_PASSWORD = "Dispatcher-Demo-2026!";

	public static final String COURIER_EMAIL = "courier@delivery-glance.example";

	public static final String COURIER_DISPLAY_NAME = "Cory the Courier";

	public static final String COURIER_PASSWORD = "Courier-Demo-2026!";

	private DemoAccounts() {
	}

}
