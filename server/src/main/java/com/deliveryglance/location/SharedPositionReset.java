package com.deliveryglance.location;

/**
 * Forgetting every Courier's coordinates at once.
 *
 * <p>This is the one operation on Current Location that is not about a particular Courier, and it
 * exists for the demo reset. Emptying {@code courier_location_sharing} would end every Location
 * Sharing Session but would not remove the positions those sessions produced, because positions are
 * never in the database — so without this a "reset" demo would keep showing a Courier on a map for
 * up to two more minutes, which is exactly the sort of stale claim this product is about not making.
 *
 * <p>Deliberately narrow, and deliberately not on {@link LocationFacts}: that port is what peers read
 * positions through, and a destructive operation does not belong beside a read. Nothing durable is
 * lost here. A forgotten position is the same Unavailable a restart produces, and the next report
 * fixes it.
 */
public interface SharedPositionReset {

	void forgetEveryPosition();

}
