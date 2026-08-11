package com.deliveryglance.recipientview;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;

import com.deliveryglance.trackinglink.LinkHolderAuthorization.HeldGrant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Every Recipient page currently listening, and the only thing this application pushes to one.
 *
 * <p>What goes down a stream is a hint and a number: something about this Delivery changed, and here
 * is the version it changed to. There is no coordinate, no address, no Courier and no state in it,
 * which is what lets the stream be treated as untrusted — a page that believed a hint would still
 * have to fetch the snapshot to learn anything, so it may as well only fetch. A hint that is lost,
 * duplicated or delivered late therefore costs a redundant fetch and never a wrong page.
 *
 * <p>The version exists so a page can skip work it has already done, not so it can detect a gap.
 * Nothing here is replayable: the counter lives with the connections and is discarded with the last
 * of them, and a restart drops both. That is deliberate — reconnecting rereads an authorized
 * snapshot, which is a shorter path to current truth than an event log would be, and one that
 * cannot itself go stale.
 *
 * <p>Two bounds keep this from being a memory or thread lever. {@link #MAX_STREAMS} caps how many
 * connections exist at once, and the sender below has a fixed pool and a finite queue: when either
 * overflows the affected stream is closed rather than buffered, because a closed stream reconnects
 * and refetches while a buffered one goes quietly stale.
 */
@Component
class RecipientStreams {

	/** The one event name a Recipient page listens for. */
	static final String SNAPSHOT_CHANGED = "snapshot-changed";

	/**
	 * How long a connection lives before the browser is made to reopen it. Short enough that a
	 * registration nothing cleaned up cannot outlive an afternoon, long enough that reconnects are
	 * rare; a reconnect rechecks the grant from scratch and costs one snapshot read.
	 */
	static final Duration STREAM_LIFETIME = Duration.ofMinutes(10);

	/**
	 * The whole application's connection budget. Core's acceptance is a hundred Recipient pages;
	 * this is that with room to spare and still a number, so an endpoint open to anyone holding a
	 * link cannot be turned into unbounded server memory by opening connections.
	 */
	static final int MAX_STREAMS = 200;

	private final ConcurrentMap<UUID, DeliveryStreams> byDelivery = new ConcurrentHashMap<>();

	private final AtomicInteger openStreams = new AtomicInteger();

	/**
	 * Writing to a stream is a blocking write, so it does not happen on the thread that has just
	 * committed a Delivery transition or on the one running the heartbeat. Two threads and a finite
	 * queue: enough that one unresponsive phone does not hold up everybody else, small enough that
	 * the cost of a fan-out stays visible rather than absorbed.
	 */
	private final ThreadPoolExecutor sender = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(1000), (runnable, executor) -> {
				throw new RejectedExecutionException("The Recipient stream sender is saturated");
			});

	/**
	 * Registers a page and hands back the connection it will read.
	 *
	 * <p>The first frame goes out before this returns, which is not cosmetic: it commits the
	 * response, so the browser's {@code open} event — and the snapshot fetch that follows it —
	 * happen strictly after this registration. Without it, a change landing between that fetch and
	 * the registration would reach nobody, leaving a page holding a stale snapshot over a healthy
	 * connection, which is the one failure a hint stream must not have.
	 *
	 * @return the connection, or empty when the application is already at {@link #MAX_STREAMS} or
	 * the browser was gone before the first frame could be written
	 */
	Optional<SseEmitter> open(HeldGrant grant) {
		if (this.openStreams.incrementAndGet() > MAX_STREAMS) {
			this.openStreams.decrementAndGet();
			return Optional.empty();
		}

		SseEmitter emitter = new SseEmitter(STREAM_LIFETIME.toMillis());
		Stream stream = new Stream(emitter, grant);
		register(stream);

		// All three endings are wired up because they are three different endings and only
		// completion is guaranteed to follow the others. Unregistering is idempotent.
		emitter.onCompletion(() -> unregister(stream));
		emitter.onTimeout(() -> close(stream));
		emitter.onError((error) -> close(stream));

		try {
			emitter.send(SseEmitter.event().comment("open"));
		}
		catch (IOException | RuntimeException ex) {
			close(stream);
			return Optional.empty();
		}
		return Optional.of(emitter);
	}

	/**
	 * Tells every page watching this Delivery that its snapshot is out of date.
	 *
	 * <p>Nothing happens when nobody is watching, which is what lets the version counter be
	 * discarded with the last connection: a version only has to increase within one connection's
	 * lifetime, and a page that reconnects starts again from whatever it is then told.
	 */
	void hintChanged(UUID deliveryId) {
		DeliveryStreams watching = this.byDelivery.get(deliveryId);
		if (watching == null) {
			return;
		}
		long version = watching.version.incrementAndGet();
		String data = "{\"version\":" + version + "}";
		for (Stream stream : watching.currentStreams()) {
			submit(stream, () -> stream.emitter().send(SseEmitter.event().name(SNAPSHOT_CHANGED).data(data)));
		}
	}

	/**
	 * The keep-alive, and the recheck that rides on it, at the interval the SSE specification
	 * suggests. Both halves matter. A Servlet container does not reliably tell an application that
	 * the far end has gone, so a write that fails is how a dropped connection is noticed at all; and
	 * a stream opened while a grant was valid must not outlive it merely because nothing else
	 * happened to that Delivery.
	 */
	@Scheduled(fixedDelayString = "PT15S")
	void heartbeat() {
		for (DeliveryStreams watching : this.byDelivery.values()) {
			for (Stream stream : watching.currentStreams()) {
				// Closed with no reason given and no distinguishable one: an expired link, an
				// unknown grant and a Delivery whose grace period ran out all end the same silence.
				if (!stream.grant().stillAuthorizes()) {
					close(stream);
					continue;
				}
				submit(stream, () -> stream.emitter().send(SseEmitter.event().comment("keep-alive")));
			}
		}
	}

	/** How many connections are registered. Exists so a test can prove cleanup leaves nothing. */
	int openStreamCount() {
		return this.openStreams.get();
	}

	/** How many Deliveries hold a registry entry, version counter and all. */
	int watchedDeliveryCount() {
		return this.byDelivery.size();
	}

	@PreDestroy
	void shutDown() {
		this.byDelivery.values()
			.forEach((watching) -> watching.currentStreams().forEach((stream) -> stream.emitter().complete()));
		this.sender.shutdownNow();
	}

	/**
	 * Runs a write on the sender, and closes the stream if it cannot be run or fails.
	 *
	 * <p>Failing closed is the whole error policy. A write that throws means the far end is gone or
	 * the queue is full; either way the page's next correct move is to reconnect and reread its
	 * snapshot, and leaving the connection open would deny it that.
	 */
	private void submit(Stream stream, Write write) {
		try {
			this.sender.execute(() -> {
				try {
					write.run();
				}
				catch (IOException | RuntimeException ex) {
					close(stream);
				}
			});
		}
		catch (RejectedExecutionException ex) {
			close(stream);
		}
	}

	private void close(Stream stream) {
		// complete() fires the completion callback, which is what normally unregisters. Calling it
		// on an emitter that has already finished is harmless, and unregistering twice is a no-op.
		try {
			stream.emitter().complete();
		}
		finally {
			unregister(stream);
		}
	}

	private void register(Stream stream) {
		this.byDelivery.compute(stream.grant().deliveryId(), (deliveryId, watching) -> {
			DeliveryStreams existing = (watching != null) ? watching : new DeliveryStreams();
			existing.streams.add(stream);
			return existing;
		});
	}

	/**
	 * Removes the stream and, with the last one for a Delivery, the entry itself. Dropping the entry
	 * is what stops a long-running process accumulating one counter per Delivery ever tracked.
	 *
	 * <p>Adding and removing both happen inside the registry map's per-key lock, so "this was the
	 * last stream, drop the entry" cannot race with a connection arriving for the same Delivery.
	 */
	private void unregister(Stream stream) {
		boolean[] removed = { false };
		this.byDelivery.computeIfPresent(stream.grant().deliveryId(), (deliveryId, watching) -> {
			removed[0] = watching.streams.remove(stream);
			return watching.streams.isEmpty() ? null : watching;
		});
		if (removed[0]) {
			this.openStreams.decrementAndGet();
		}
	}

	@FunctionalInterface
	private interface Write {

		void run() throws IOException;

	}

	/** One Recipient page: the connection it reads, and the grant that has to keep authorizing it. */
	private record Stream(SseEmitter emitter, HeldGrant grant) {
	}

	/** The connections watching one Delivery, and the version counter they share. */
	private static final class DeliveryStreams {

		private final AtomicLong version = new AtomicLong();

		private final Set<Stream> streams = ConcurrentHashMap.newKeySet();

		/** A copy, so a fan-out that closes streams is not iterating the set it is mutating. */
		private Set<Stream> currentStreams() {
			return Set.copyOf(this.streams);
		}

	}

}
