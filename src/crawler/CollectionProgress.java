package crawler;

import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class CollectionProgress {
    private static final long PUBLISH_INTERVAL_NANOS = 100_000_000L;
    private static final AtomicInteger posts = new AtomicInteger();
    private static final AtomicInteger comments = new AtomicInteger();
    private static final Set<String> authorKeys = ConcurrentHashMap.newKeySet();
    private static final AtomicLong lastPublishedAt = new AtomicLong();
    private static volatile Consumer<Snapshot> listener = snapshot -> {};

    private CollectionProgress() {
    }

    public static void setListener(Consumer<Snapshot> progressListener) {
        listener = progressListener == null ? snapshot -> {} : progressListener;
    }

    public static void reset() {
        posts.set(0);
        comments.set(0);
        authorKeys.clear();
        lastPublishedAt.set(0L);
        publishNow();
    }

    public static void recordPost(String name, String uid, String ip) {
        posts.incrementAndGet();
        addAuthor(name, uid, ip);
        publishThrottled();
    }

    public static void recordComment(String name, String uid, String ip) {
        comments.incrementAndGet();
        addAuthor(name, uid, ip);
        publishThrottled();
    }

    public static void publishNow() {
        Snapshot snapshot = snapshot();
        try {
            listener.accept(snapshot);
        } catch (RuntimeException ignored) {
            // Collection must continue even if a UI listener has already closed.
        }
    }

    public static Snapshot snapshot() {
        return new Snapshot(posts.get(), comments.get(), authorKeys.size());
    }

    private static void publishThrottled() {
        long now = System.nanoTime();
        long previous = lastPublishedAt.get();
        if (now - previous >= PUBLISH_INTERVAL_NANOS && lastPublishedAt.compareAndSet(previous, now)) {
            publishNow();
        }
    }

    private static void addAuthor(String name, String uid, String ip) {
        String key = authorKey(name, uid, ip);
        if (key != null) {
            authorKeys.add(key);
        }
    }

    private static String authorKey(String name, String uid, String ip) {
        String normalizedUid = normalize(uid);
        if (!normalizedUid.isEmpty()) {
            return "uid:" + normalizedUid;
        }

        String normalizedIp = normalize(ip);
        if (!normalizedIp.isEmpty()) {
            return "ip:" + normalizedIp;
        }

        String normalizedName = normalize(name);
        return normalizedName.isEmpty() ? null : "name:" + normalizedName;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record Snapshot(int posts, int comments, int uniqueAuthors) {
    }
}
