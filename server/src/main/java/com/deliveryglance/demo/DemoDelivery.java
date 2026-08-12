package com.deliveryglance.demo;

import java.util.List;

import com.deliveryglance.delivery.DeliveryProvisioning;

/**
 * The fictional Deliveries a reset puts back, and the whole of what the demo starts from.
 *
 * <p>Every value here is invented, and deliberately reads that way. A Handoff Address is somebody's
 * doorstep in a real deployment, so a public portfolio repository must not carry one that could be
 * mistaken for a real person's, and a reader should be able to tell that at a glance rather than by
 * checking. The coordinates match the fictional place {@code web/e2e/support/team.ts} works in, so
 * the journeys, the screenshots and the recorded demo are all about the same two streets.
 *
 * <p>Both start Awaiting Courier, which is the only state a Delivery is ever legitimately created
 * in. Nothing here writes a Delivery straight into Assigned or Delivered: the demo reaches those
 * states by being driven, so what a viewer sees is the product's own lifecycle rather than rows
 * arranged to look like one. Two of them rather than one, because the second is what the
 * walkthrough cancels — a Delivery cannot be both carried to handoff and shown as cancelled.
 */
final class DemoDelivery {

	static final List<DeliveryProvisioning.NewDelivery> CATALOGUE = List.of(
			new DeliveryProvisioning.NewDelivery("DEMO-1001",
					new DeliveryProvisioning.NewAddress("Glance Depot, 1 Fictional Way", 51.5, -0.12),
					new DeliveryProvisioning.NewAddress("14 Invented Crescent, Apartment 3", 51.51, -0.13)),
			new DeliveryProvisioning.NewDelivery("DEMO-1002",
					new DeliveryProvisioning.NewAddress("Glance Depot, 1 Fictional Way", 51.5, -0.12),
					new DeliveryProvisioning.NewAddress("2 Imaginary Row, Unit B", 51.494, -0.113)));

	private DemoDelivery() {
	}

}
