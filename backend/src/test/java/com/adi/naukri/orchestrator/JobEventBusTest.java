package com.adi.naukri.orchestrator;

import com.adi.naukri.report.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for JobEventBus.
 *
 * Author: Adikarthik Gupta C B
 */
class JobEventBusTest {

    private JobEventBus bus;

    @BeforeEach
    void setUp() {
        bus = new JobEventBus();
    }

    // -----------------------------------------------------------------------
    // TC-1: Late subscriber gets replay of buffered events for that jobId
    // -----------------------------------------------------------------------
    @Test
    void lateSubscriberReceivesReplayOfBufferedEvents() throws InterruptedException {
        String jobId = "job-replay";

        // Publish 3 events BEFORE subscribing
        bus.publish(new JobEvent.RunStarted(jobId, Instant.now(), 3));
        bus.publish(new JobEvent.AccountStarted(jobId, Instant.now(), "a@a.com", 0));
        bus.publish(new JobEvent.StepStarted(jobId, Instant.now(), "a@a.com", "LOGIN"));

        // Now subscribe LATE
        List<JobEvent> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);
        JobEventBus.Registration reg = bus.subscribe(jobId, e -> {
            received.add(e);
            latch.countDown();
        });

        // Late subscriber should receive all 3 buffered events via replay
        assertTrue(latch.await(2, TimeUnit.SECONDS),
                "Expected 3 replayed events, got " + received.size());
        assertEquals(3, received.size());
        assertInstanceOf(JobEvent.RunStarted.class, received.get(0));
        assertInstanceOf(JobEvent.AccountStarted.class, received.get(1));
        assertInstanceOf(JobEvent.StepStarted.class, received.get(2));

        reg.close();
    }

    // -----------------------------------------------------------------------
    // TC-2: Events for other jobIds are not delivered to this subscriber
    // -----------------------------------------------------------------------
    @Test
    void eventsForOtherJobIdsAreIgnored() throws InterruptedException {
        String myJob    = "job-mine";
        String otherJob = "job-other";

        List<JobEvent> received = new CopyOnWriteArrayList<>();
        JobEventBus.Registration reg = bus.subscribe(myJob, received::add);

        // Publish to BOTH jobs
        bus.publish(new JobEvent.RunStarted(myJob,    Instant.now(), 1));
        bus.publish(new JobEvent.RunStarted(otherJob, Instant.now(), 1));
        bus.publish(new JobEvent.RunStopped(otherJob, Instant.now()));

        // Small sleep to allow any spurious delivery
        Thread.sleep(100);

        assertEquals(1, received.size(),
                "Should only receive events for myJob, got: " + received.size());
        assertEquals(myJob, received.get(0).jobId());

        reg.close();
    }

    // -----------------------------------------------------------------------
    // TC-3: Unsubscribe stops further deliveries
    // -----------------------------------------------------------------------
    @Test
    void unsubscribeStopsFurtherDeliveries() throws InterruptedException {
        String jobId = "job-unsub";

        List<JobEvent> received = new CopyOnWriteArrayList<>();
        JobEventBus.Registration reg = bus.subscribe(jobId, received::add);

        // Publish one event, receives it
        bus.publish(new JobEvent.RunStarted(jobId, Instant.now(), 2));
        Thread.sleep(50);
        assertEquals(1, received.size());

        // Close registration
        reg.close();

        // Publish more events — should NOT be received
        bus.publish(new JobEvent.AccountStarted(jobId, Instant.now(), "b@b.com", 0));
        bus.publish(new JobEvent.RunStopped(jobId, Instant.now()));
        Thread.sleep(100);

        assertEquals(1, received.size(),
                "After close(), no more events should be received");
    }

    // -----------------------------------------------------------------------
    // TC-4: Ring-buffer caps at 100 events per jobId
    // -----------------------------------------------------------------------
    @Test
    void ringBufferCapsAt100EventsPerJob() {
        String jobId = "job-cap";

        // Publish 150 events
        for (int i = 0; i < 150; i++) {
            bus.publish(new JobEvent.StepStarted(jobId, Instant.now(), "x@x.com", "step-" + i));
        }

        // Late subscriber should only get 100 (the last 100)
        List<JobEvent> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(100);
        JobEventBus.Registration reg = bus.subscribe(jobId, e -> {
            received.add(e);
            latch.countDown();
        });

        try {
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(100, received.size());
        } catch (InterruptedException e) {
            fail("Interrupted");
        } finally {
            reg.close();
        }
    }

    // -----------------------------------------------------------------------
    // TC-5: Multiple subscribers all receive the same event
    // -----------------------------------------------------------------------
    @Test
    void multipleSubscribersAllReceiveEvent() throws InterruptedException {
        String jobId = "job-multi";

        List<JobEvent> recv1 = new CopyOnWriteArrayList<>();
        List<JobEvent> recv2 = new CopyOnWriteArrayList<>();

        JobEventBus.Registration r1 = bus.subscribe(jobId, recv1::add);
        JobEventBus.Registration r2 = bus.subscribe(jobId, recv2::add);

        bus.publish(new JobEvent.RunStarted(jobId, Instant.now(), 1));
        Thread.sleep(100);

        assertEquals(1, recv1.size());
        assertEquals(1, recv2.size());

        r1.close();
        r2.close();
    }

    // -----------------------------------------------------------------------
    // TC-6: AccountCompleted event carries status, resumeOldName, resumeNewName
    // -----------------------------------------------------------------------
    @Test
    void accountCompletedEventCarriesFields() {
        String jobId = "job-ac";
        Instant ts = Instant.now();
        JobEvent.AccountCompleted ev = new JobEvent.AccountCompleted(
                jobId, ts, "u@u.com", AccountStatus.OK, "old.pdf", "new.pdf");

        assertEquals(jobId,          ev.jobId());
        assertEquals(ts,             ev.timestamp());
        assertEquals("u@u.com",      ev.email());
        assertEquals(AccountStatus.OK, ev.status());
        assertEquals("old.pdf",      ev.resumeOldName());
        assertEquals("new.pdf",      ev.resumeNewName());
    }

    // -----------------------------------------------------------------------
    // TC-7: RunCompleted carries a Summary
    // -----------------------------------------------------------------------
    @Test
    void runCompletedCarriesSummary() {
        String jobId = "job-rc";
        JobEvent.Summary summary = new JobEvent.Summary(3, 0, 0, 0, 0);
        JobEvent.RunCompleted ev = new JobEvent.RunCompleted(jobId, Instant.now(), summary);

        assertEquals(3, ev.summary().ok());
        assertEquals(0, ev.summary().failed());
    }
}
