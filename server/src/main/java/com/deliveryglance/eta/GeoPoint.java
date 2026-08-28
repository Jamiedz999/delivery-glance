package com.deliveryglance.eta;

/**
 * A WGS84 coordinate the module hands to a travel-time provider. It is eta-local and carries no
 * accuracy, timestamp or provenance — those belong to location's freshness rules, which have
 * already been applied by the time a point reaches here. A point in this module means "a place to
 * route from or to", nothing more.
 */
record GeoPoint(double latitude, double longitude) {
}
