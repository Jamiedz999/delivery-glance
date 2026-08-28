package com.deliveryglance.system;

/**
 * The public probe. It carries no Delivery, account or secret — only what the application is and
 * whether an optional, deployment-configured surface is available, so a client can decide what to
 * offer before anyone signs in.
 *
 * @param proofCaptureEnabled whether proof of delivery is configured on this deployment. False when
 * no bucket is set, and the frontend then offers no capture rather than a control that cannot work.
 * @param etaEnabled whether external travel-time ETA is configured on this deployment. False when no
 * provider base URL is set, and the Recipient view then shows no arrival window rather than a
 * control that can never populate.
 * @param demoAccountsUnchanged whether both Internal Account rows still hold the fictional factory
 * password hashes the first migration seeds. True lets the Sign-in page publish the two demo
 * credentials; false when a deployment reseeded them, in which case the page cannot and does not.
 */
record SystemStatus(String application, String status, boolean proofCaptureEnabled, boolean etaEnabled,
		boolean demoAccountsUnchanged) {
}
