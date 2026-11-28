package com.mtx.rdp.connector.simulation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mtx.rdp.connector.simulation.InMemoryRawMessageStore.Found;
import com.mtx.rdp.connector.simulation.InMemoryRawMessageStore.NotFound;
import com.mtx.rdp.connector.simulation.InMemoryRawMessageStore.Unavailable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InMemoryRawMessageStoreTest {

    @Test
    void readsBackTheSameCompleteBytes() {
        InMemoryRawMessageStore store = new InMemoryRawMessageStore();
        byte[] raw = {1, 2, 3, 4};

        String reference = store.put(raw);

        assertArrayEquals(raw, found(store, reference));
    }

    @Test
    void laterMutationOfTheInputDoesNotChangeStoredBytes() {
        InMemoryRawMessageStore store = new InMemoryRawMessageStore();
        byte[] raw = {1, 2, 3};

        String reference = store.put(raw);
        raw[0] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, found(store, reference));
    }

    @Test
    void laterMutationOfTheReadBytesDoesNotChangeTheNextRead() {
        InMemoryRawMessageStore store = new InMemoryRawMessageStore();
        String reference = store.put(new byte[] {4, 5, 6});

        byte[] first = found(store, reference);
        first[0] = 0;

        assertArrayEquals(new byte[] {4, 5, 6}, found(store, reference));
    }

    @Test
    void unknownReferenceIsNotFoundWhileTheStoreIsOpen() {
        InMemoryRawMessageStore store = new InMemoryRawMessageStore();
        store.put(new byte[] {1});

        assertInstanceOf(NotFound.class, store.read("mem-missing"));
        assertInstanceOf(NotFound.class, store.read(null));
    }

    @Test
    void closedStoreMakesExistingAndUnknownReferencesUnavailable() {
        InMemoryRawMessageStore store = new InMemoryRawMessageStore();
        String reference = store.put(new byte[] {7, 8});

        store.close();

        assertInstanceOf(Unavailable.class, store.read(reference));
        assertInstanceOf(Unavailable.class, store.read("mem-missing"));
        assertInstanceOf(Unavailable.class, store.read(null));
    }

    @Test
    void rejectsWriteAfterCloseAndAllowsRepeatedClose() {
        InMemoryRawMessageStore store = new InMemoryRawMessageStore();
        store.close();
        store.close();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> store.put(new byte[] {1}));
        assertInstanceOf(Unavailable.class, store.read("mem-1"));
        assertEquals("进程内原文存储已关闭，不能写入", exception.getMessage());
    }

    @Test
    void distinctWritesKeepDistinctReferences() {
        InMemoryRawMessageStore store = new InMemoryRawMessageStore();
        String first = store.put(new byte[] {1, 1});
        String second = store.put(new byte[] {2, 2});

        assertNotEquals(first, second);
        assertArrayEquals(new byte[] {1, 1}, found(store, first));
        assertArrayEquals(new byte[] {2, 2}, found(store, second));
    }

    @Test
    void concurrentWritesRemainReadableUntilCloseClearsThem() throws Exception {
        InMemoryRawMessageStore store = new InMemoryRawMessageStore();
        int writers = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<AtomicReference<String>> references = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < writers; index++) {
            byte[] raw = {(byte) index, (byte) (index + 1)};
            AtomicReference<String> reference = new AtomicReference<>();
            references.add(reference);
            threads.add(Thread.ofPlatform().start(() -> {
                await(start);
                reference.set(store.put(raw));
            }));
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        for (int index = 0; index < writers; index++) {
            assertArrayEquals(
                    new byte[] {(byte) index, (byte) (index + 1)},
                    found(store, references.get(index).get()));
        }
        store.close();
        for (AtomicReference<String> reference : references) {
            assertInstanceOf(Unavailable.class, store.read(reference.get()));
        }
    }

    private static byte[] found(InMemoryRawMessageStore store, String reference) {
        Found found = assertInstanceOf(Found.class, store.read(reference));
        return found.body();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
