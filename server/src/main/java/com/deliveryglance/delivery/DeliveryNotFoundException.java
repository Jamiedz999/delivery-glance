package com.deliveryglance.delivery;

import java.util.UUID;

class DeliveryNotFoundException extends RuntimeException {

	DeliveryNotFoundException(UUID id) {
		super("No Delivery with id %s".formatted(id));
	}

}
