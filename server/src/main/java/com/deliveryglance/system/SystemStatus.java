package com.deliveryglance.system;

/**
 * The public probe. It carries no Delivery, account or secret — only what the application is and
 * whether an optional, deployment-configured surface is available, so a client can decide what to
 * offer before anyone signs in.
 *
 * @param proofCaptureEnabled whether proof of delivery is configured on this deployment. False when
 * no bucket is set, and the frontend then offers no capture rather than a control that cannot work.
 */
record SystemStatus(String application, String status, boolean proofCaptureEnabled) {
}
