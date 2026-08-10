package com.deliveryglance.identityaccess;

/**
 * The two pre-provisioned roles in the single Delivery Team. A Recipient never has an Internal
 * Account, so there is no role for one.
 */
public enum InternalAccountRole {

	DISPATCHER,
	COURIER;

	String authority() {
		return "ROLE_" + name();
	}

}
