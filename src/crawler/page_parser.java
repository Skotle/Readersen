package crawler;

import org.jsoup.Jsoup;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class page_parser {
    private static final int BLOCK_COOLDOWN_BASE_MS = 5000;
    private static final DateTimeFormatter OUTPUT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static volatile long globalCooldownUntil = 0L;

    public static CrawlerResult Crawler(String ID, String Gall, int start, int end, int concurrency) throws InterruptedException {
        GalleryConfig galleryConfig = resolveGalleryConfig(ID, Gall);
        return crawlResolvedRange(ID, Gall, galleryConfig, start, end, concurrency, null);
    }

    public static CrawlerResult CrawlerByDate(String ID, String Gall, LocalDate startDate, LocalDate endDate, int concurrency) throws InterruptedException {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Date range is required.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be the same as or after start date.");
        }

        GalleryConfig galleryConfig = resolveGalleryConfig(ID, Gall);
        Map<Integer, Optional<PageDateSpan>> dateSpanCache = new HashMap<>();
        DateRange dateRange = resolveAvailableDateRange(ID, galleryConfig, startDate, endDate, dateSpanCache);
        DatePageRange pageRange = findPageRangeByDate(ID, galleryConfig, dateRange, dateSpanCache);
        System.out.println("Date page search: page " + pageRange.startPage() + " ~ " + pageRange.endPage());
        return crawlResolvedRange(ID, Gall, galleryConfig, pageRange.startPage(), pageRange.endPage(), concurrency, dateRange);
    }

    public static CrawlerResult CrawlerAll(String ID, String Gall, int concurrency) throws InterruptedException {
        GalleryConfig galleryConfig = resolveGalleryConfig(ID, Gall);
        int lastPage = resolveLastPage(ID, galleryConfig);
        System.out.println("All page search: page 1 ~ " + lastPage);
        return crawlResolvedRange(ID, Gall, galleryConfig, 1, lastPage, concurrency, null);
    }

    private static int resolveLastPage(String id, GalleryConfig config) throws InterruptedException {
        OptionalInt pageEnd = fetchLastPageNumber(id, config);
        if (pageEnd.isPresent()) {
            return Math.max(1, pageEnd.getAsInt());
        }

        if (!fetchPageHasArticlesWithRetry(id, config, 1)) {
            return 1;
        }

        int low = 1;
        int high = 2;
        while (true) {
            if (!fetchPageHasArticlesWithRetry(id, config, high)) {
                return findLastExistingPageByFetch(id, config, low, high);
            }
            low = high;
            int nextHigh = safeDoublePage(high);
            if (nextHigh == high) {
                return high;
            }
            high = nextHigh;
        }
    }

    private static int findLastExistingPageByFetch(String id, GalleryConfig config, int knownExistingPage,
                                                   int missingPage) throws InterruptedException {
        int low = knownExistingPage;
        int high = missingPage;
        while (low + 1 < high) {
            int mid = low + (high - low) / 2;
            if (fetchPageHasArticlesWithRetry(id, config, mid)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private static boolean fetchPageHasArticlesWithRetry(String id, GalleryConfig config, int page) throws InterruptedException {
        int retries = 3;
        while (retries-- > 0) {
            try {
                waitGlobalCooldown();
                Document doc = fetchListDocument(config.baseUrl() + "&page=" + page, config.type(), id);
                return hasNormalArticleRows(doc, config.subjectSelector());
            } catch (Exception e) {
                if (isNotFound(e)) {
                    return false;
                }
                if (retries == 0) {
                    throw new IllegalArgumentException("Failed to probe page " + page + ": " + e.getMessage(), e);
                }
                if (isBlockingResponse(e)) {
                    applyGlobalCooldown(BLOCK_COOLDOWN_BASE_MS * (4 - retries));
                }
                Thread.sleep(ThreadLocalRandom.current().nextInt(500, 1201));
            }
        }
        return false;
    }

    private static boolean hasNormalArticleRows(Document doc, String subjectSelector) {
        for (Element tag : doc.select("tr.ub-content")) {
            if (isNormalArticleRow(tag, subjectSelector)) {
                return true;
            }
        }
        return false;
    }

    private static CrawlerResult crawlResolvedRange(String ID, String Gall, GalleryConfig galleryConfig,
                                                    int start, int end, int concurrency, DateRange dateRange) throws InterruptedException {
        ArrayList<String> skipSubjects = new ArrayList<>(Arrays.asList("고정", "공지", "설문", "AD"));
        ArrayList<String> skipAuthor = new ArrayList<>(Arrays.asList("운영자","김유식"));

        // Thread-safe 데이터 저장소
        List<String> AuthorBox = Collections.synchronizedList(new ArrayList<>());
        List<String> IpBox = Collections.synchronizedList(new ArrayList<>());
        List<String> IDBox = Collections.synchronizedList(new ArrayList<>());
        List<Integer> ViewBox = Collections.synchronizedList(new ArrayList<>());
        List<Integer> RecomBox = Collections.synchronizedList(new ArrayList<>());
        List<Integer> RepleBox = Collections.synchronizedList(new ArrayList<>());
        List<String> RepleTrueBox = Collections.synchronizedList(new ArrayList<>());
        List<String> days = Collections.synchronizedList(new ArrayList<>());

        int total_geul = 0;

        int effectiveConcurrency = Math.max(1, concurrency);
        ExecutorService executor = Executors.newFixedThreadPool(effectiveConcurrency);
        List<Future<Void>> futures = new ArrayList<>();
        AtomicInteger completedPages = new AtomicInteger(0);

        // 쿨다운 상태 관리 변수
        AtomicBoolean isPaused = new AtomicBoolean(false);

        String baseurl = galleryConfig.baseUrl();
        String subtag = galleryConfig.subjectSelector();
        String actualGallType = galleryConfig.type();

        if (!actualGallType.equals(Gall)) {
            System.out.println("갤러리 타입 자동 보정: " + Gall + " -> " + actualGallType);
        }

        for (int page = start; page <= end; page++) {
            final int currentPage = page;
            futures.add(executor.submit(() -> {
                int retries = 3;
                while (retries-- > 0) {
                    try {
                        waitGlobalCooldown();

                        // 스레드들이 같은 순간에 몰리지 않게 요청 전 지터를 둔다.
                        Thread.sleep(ThreadLocalRandom.current().nextInt(300, 901));

                        // 다른 스레드에 의해 글로벌 쿨다운(에러 발생 등) 중인지 체크
                        while (isPaused.get()) {
                            Thread.sleep(100);
                        }

                        String key = baseurl + "&page=" + currentPage;
                        Document doc = fetchListDocument(key, actualGallType, ID);

                        Elements trList = doc.select("tr.ub-content");

                        for (Element tag : trList) {
                            Element subjectTd = tag.selectFirst(subtag);
                            if (isNormalArticleRow(tag, subtag) && !skipSubjects.contains(subjectTd.text().trim())) {

                                Element writerTd = tag.selectFirst("td.gall_writer");

                                String imgSrc = "";
                                Element img = writerTd.selectFirst("a.writer_nikcon img");
                                if (img != null) imgSrc = img.attr("src");

                                String subnik = determineSubnik(imgSrc);
                                String uid = writerTd.attr("data-uid");
                                String nick = writerTd.attr("data-nick");
                                if (skipAuthor.contains((nick))) continue;
                                String ip = writerTd.attr("data-ip");
                                String displayName = subnik + nick;

                                Element view = tag.selectFirst("td.gall_count");
                                Element recommend = tag.selectFirst("td.gall_recommend");
                                Element reple = tag.selectFirst("span.reply_num");
                                Element geulnum = tag.selectFirst("td.gall_num");
                                String geulnumText = geulnum != null ? geulnum.text().trim() : "";

                                Element day = tag.selectFirst("td.gall_date");
                                LocalDateTime postDate = parsePostDate(day);
                                if (dateRange != null && (postDate == null || !dateRange.includes(postDate))) {
                                    continue;
                                }
                                String postDateText = postDate != null
                                        ? postDate.format(OUTPUT_DATE_FORMATTER)
                                        : (day != null ? day.attr("title") : "");

                                AuthorBox.add(displayName);
                                IpBox.add(ip);
                                IDBox.add(uid);
                                days.add(postDateText);
                                ViewBox.add(parseSafeInt(view != null ? view.text() : ""));
                                RecomBox.add(parseSafeInt(recommend != null ? recommend.text() : ""));

                                if (reple != null) {
                                    RepleTrueBox.add(geulnumText);
                                    String ra = reple.text();
                                    String repl = ra.contains("/") ? ra.substring(1, ra.length() - 1).split("/")[0] : ra.replaceAll("[\\[\\]]", "");
                                    RepleBox.add(parseSafeInt(repl));
                                } else {
                                    RepleBox.add(0);
                                }
                            }
                        }

                        int done = completedPages.incrementAndGet();
                        System.out.print("\r페이지 진행: " + done + "/" + (end - start + 1));
                        break;

                    } catch (Exception e) {
                        if (isNotFound(e)) {
                            System.out.println("\n[건너뜀] 페이지 " + currentPage + " 없음(404)");
                            completedPages.incrementAndGet();
                            break;
                        }
                        if (retries == 0) {
                            System.err.println("\n[오류] 페이지 " + currentPage + " 실패: " + e.getMessage());
                        } else {
                            if (isBlockingResponse(e)) {
                                applyGlobalCooldown(BLOCK_COOLDOWN_BASE_MS * (4 - retries));
                            }

                            // 에러 발생 시 전체 스레드 흐름을 제어 (쿨다운 게이트)
                            if (isPaused.compareAndSet(false, true)) {
                                Thread.sleep(ThreadLocalRandom.current().nextInt(1500, 3501));
                                isPaused.set(false);
                            }
                        }
                    }
                }
                return null;
            }));
        }

        for (Future<Void> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }
        executor.shutdown();
        System.out.println("\n크롤링 완료.");

        return new CrawlerResult(
                new ArrayList<>(AuthorBox), new ArrayList<>(IDBox), new ArrayList<>(IpBox),
                new ArrayList<>(ViewBox), new ArrayList<>(RecomBox), new ArrayList<>(RepleBox),
                new ArrayList<>(RepleTrueBox), new ArrayList<>(days), total_geul, actualGallType, start, end
        );
    }

    private static DateRange resolveAvailableDateRange(String id, GalleryConfig config, LocalDate requestedStart,
                                                       LocalDate requestedEnd, Map<Integer, Optional<PageDateSpan>> cache)
            throws InterruptedException {
        LocalDate resolvedStart = resolveStartBoundaryDate(id, config, requestedStart, cache);
        LocalDate resolvedEnd = resolveEndBoundaryDate(id, config, requestedEnd, cache);

        if (resolvedStart == null) {
            resolvedStart = resolveDateFromCachedSpans(cache, requestedStart, true);
        }
        if (resolvedEnd == null) {
            resolvedEnd = resolveDateFromCachedSpans(cache, requestedEnd, false);
        }
        if (resolvedStart == null || resolvedEnd == null) {
            throw new IllegalArgumentException("No dated posts were found after probing pages.");
        }

        if (resolvedStart.isAfter(resolvedEnd)) {
            LocalDate closest = findClosestPostedDateToRange(id, config, requestedStart, requestedEnd, cache);
            resolvedStart = closest;
            resolvedEnd = closest;
        }

        if (!resolvedStart.equals(requestedStart) || !resolvedEnd.equals(requestedEnd)) {
            System.out.println("Date boundary adjusted: " + requestedStart + " ~ " + requestedEnd +
                    " -> " + resolvedStart + " ~ " + resolvedEnd);
        }

        return new DateRange(resolvedStart.atStartOfDay(), resolvedEnd.atTime(LocalTime.MAX));
    }

    private static LocalDate resolveDateFromCachedSpans(Map<Integer, Optional<PageDateSpan>> cache,
                                                        LocalDate targetDate, boolean preferAfter) {
        LocalDate preferred = null;
        LocalDate closest = null;
        long closestDistance = Long.MAX_VALUE;

        for (Optional<PageDateSpan> optionalSpan : cache.values()) {
            if (optionalSpan.isEmpty()) {
                continue;
            }

            PageDateSpan span = optionalSpan.get();
            LocalDate[] candidates = {
                    span.newest().toLocalDate(),
                    span.oldest().toLocalDate()
            };
            for (LocalDate candidate : candidates) {
                if (preferAfter) {
                    if (!candidate.isBefore(targetDate) && (preferred == null || candidate.isBefore(preferred))) {
                        preferred = candidate;
                    }
                } else if (!candidate.isAfter(targetDate) && (preferred == null || candidate.isAfter(preferred))) {
                    preferred = candidate;
                }

                long distance = Math.abs(ChronoUnit.DAYS.between(targetDate, candidate));
                if (closest == null || distance < closestDistance) {
                    closest = candidate;
                    closestDistance = distance;
                }
            }
        }

        return preferred != null ? preferred : closest;
    }

    private static LocalDate resolveStartBoundaryDate(String id, GalleryConfig config, LocalDate targetDate,
                                                      Map<Integer, Optional<PageDateSpan>> cache) throws InterruptedException {
        NeighborPostedDates neighbors = findNeighborPostedDates(id, config, targetDate, cache);
        return neighbors.afterOrSame() != null ? neighbors.afterOrSame() : neighbors.beforeOrSame();
    }

    private static LocalDate resolveEndBoundaryDate(String id, GalleryConfig config, LocalDate targetDate,
                                                    Map<Integer, Optional<PageDateSpan>> cache) throws InterruptedException {
        NeighborPostedDates neighbors = findNeighborPostedDates(id, config, targetDate, cache);
        return neighbors.beforeOrSame() != null ? neighbors.beforeOrSame() : neighbors.afterOrSame();
    }

    private static LocalDate findClosestPostedDateToRange(String id, GalleryConfig config, LocalDate requestedStart,
                                                          LocalDate requestedEnd, Map<Integer, Optional<PageDateSpan>> cache)
            throws InterruptedException {
        Set<LocalDate> candidates = new LinkedHashSet<>();
        NeighborPostedDates startNeighbors = findNeighborPostedDates(id, config, requestedStart, cache);
        NeighborPostedDates endNeighbors = findNeighborPostedDates(id, config, requestedEnd, cache);
        addDateCandidate(candidates, startNeighbors.beforeOrSame());
        addDateCandidate(candidates, startNeighbors.afterOrSame());
        addDateCandidate(candidates, endNeighbors.beforeOrSame());
        addDateCandidate(candidates, endNeighbors.afterOrSame());

        LocalDate best = null;
        long bestDistance = Long.MAX_VALUE;
        for (LocalDate candidate : candidates) {
            long distance = distanceToDateRange(candidate, requestedStart, requestedEnd);
            if (best == null || distance < bestDistance || (distance == bestDistance && candidate.isAfter(best))) {
                best = candidate;
                bestDistance = distance;
            }
        }

        if (best == null) {
            throw new IllegalArgumentException("No dated posts were found.");
        }
        return best;
    }

    private static long distanceToDateRange(LocalDate date, LocalDate start, LocalDate end) {
        if (date.isBefore(start)) {
            return ChronoUnit.DAYS.between(date, start);
        }
        if (date.isAfter(end)) {
            return ChronoUnit.DAYS.between(end, date);
        }
        return 0;
    }

    private static void addDateCandidate(Set<LocalDate> candidates, LocalDate date) {
        if (date != null) {
            candidates.add(date);
        }
    }

    private static NeighborPostedDates findNeighborPostedDates(String id, GalleryConfig config, LocalDate targetDate,
                                                               Map<Integer, Optional<PageDateSpan>> cache)
            throws InterruptedException {
        PageDateSpan firstSpan = getCachedDateSpan(id, config, 1, cache).orElseThrow(
                () -> new IllegalArgumentException("No dated posts were found on page 1.")
        );
        OptionalInt knownLastPage = OptionalInt.empty();

        Set<Integer> candidatePages = new LinkedHashSet<>();
        if (targetDate.atStartOfDay().isAfter(firstSpan.newest())) {
            addPageCandidate(candidatePages, 1);
            addPageCandidate(candidatePages, 2);
            return buildNeighborPostedDates(id, config, targetDate, candidatePages, cache);
        }

        LocalDateTime targetEnd = targetDate.atTime(LocalTime.MAX);
        if (knownLastPage.isPresent()) {
            int lastPage = knownLastPage.getAsInt();
            PageDateSpan lastSpan = getCachedDateSpan(id, config, lastPage, cache).orElse(firstSpan);
            if (targetEnd.isBefore(lastSpan.oldest())) {
                addPageCandidate(candidatePages, lastPage - 1);
                addPageCandidate(candidatePages, lastPage);
                return buildNeighborPostedDates(id, config, targetDate, candidatePages, cache);
            }
        }

        int low = 1;
        int high = 2;
        while (true) {
            if (knownLastPage.isPresent() && high >= knownLastPage.getAsInt()) {
                high = knownLastPage.getAsInt();
                Optional<PageDateSpan> span = getCachedDateSpan(id, config, high, cache);
                if (span.isPresent() && !span.get().oldest().isAfter(targetEnd)) {
                    break;
                }
                addPageCandidate(candidatePages, high - 1);
                addPageCandidate(candidatePages, high);
                return buildNeighborPostedDates(id, config, targetDate, candidatePages, cache);
            }

            Optional<PageDateSpan> span = getCachedDateSpan(id, config, high, cache);
            if (span.isEmpty()) {
                int lastPage = findLastExistingPage(id, config, low, high, cache);
                Optional<PageDateSpan> lastSpan = getCachedDateSpan(id, config, lastPage, cache);
                if (lastSpan.isPresent() && !lastSpan.get().oldest().isAfter(targetEnd)) {
                    high = lastPage;
                    break;
                }
                addPageCandidate(candidatePages, lastPage - 1);
                addPageCandidate(candidatePages, lastPage);
                return buildNeighborPostedDates(id, config, targetDate, candidatePages, cache);
            }
            if (!span.get().oldest().isAfter(targetEnd)) {
                break;
            }
            low = high;
            int nextHigh = safeDoublePage(high);
            if (nextHigh == high) {
                addPageCandidate(candidatePages, high - 1);
                addPageCandidate(candidatePages, high);
                return buildNeighborPostedDates(id, config, targetDate, candidatePages, cache);
            }
            high = nextHigh;
        }

        while (low + 1 < high) {
            int mid = low + (high - low) / 2;
            Optional<PageDateSpan> span = getCachedDateSpan(id, config, mid, cache);
            if (span.isPresent() && !span.get().oldest().isAfter(targetEnd)) {
                high = mid;
            } else {
                low = mid;
            }
        }

        addPageCandidate(candidatePages, high - 1);
        addPageCandidate(candidatePages, high);
        addPageCandidate(candidatePages, high + 1);
        return buildNeighborPostedDates(id, config, targetDate, candidatePages, cache);
    }

    private static void addPageCandidate(Set<Integer> candidatePages, int page) {
        if (page > 0) {
            candidatePages.add(page);
        }
    }

    private static NeighborPostedDates buildNeighborPostedDates(String id, GalleryConfig config, LocalDate targetDate,
                                                                Set<Integer> candidatePages,
                                                                Map<Integer, Optional<PageDateSpan>> cache)
            throws InterruptedException {
        LocalDate beforeOrSame = null;
        LocalDate afterOrSame = null;

        for (int page : candidatePages) {
            Optional<PageDateSpan> span = getCachedDateSpan(id, config, page, cache);
            if (span.isPresent()) {
                LocalDate newest = span.get().newest().toLocalDate();
                LocalDate oldest = span.get().oldest().toLocalDate();
                if (!newest.isAfter(targetDate) && (beforeOrSame == null || newest.isAfter(beforeOrSame))) {
                    beforeOrSame = newest;
                }
                if (!oldest.isAfter(targetDate) && (beforeOrSame == null || oldest.isAfter(beforeOrSame))) {
                    beforeOrSame = oldest;
                }
                if (!newest.isBefore(targetDate) && (afterOrSame == null || newest.isBefore(afterOrSame))) {
                    afterOrSame = newest;
                }
                if (!oldest.isBefore(targetDate) && (afterOrSame == null || oldest.isBefore(afterOrSame))) {
                    afterOrSame = oldest;
                }
            }

            for (LocalDate postDate : fetchPagePostDatesWithRetry(id, config, page)) {
                if (!postDate.isAfter(targetDate) && (beforeOrSame == null || postDate.isAfter(beforeOrSame))) {
                    beforeOrSame = postDate;
                }
                if (!postDate.isBefore(targetDate) && (afterOrSame == null || postDate.isBefore(afterOrSame))) {
                    afterOrSame = postDate;
                }
            }
        }

        return new NeighborPostedDates(beforeOrSame, afterOrSame);
    }

    private static Set<LocalDate> fetchPagePostDatesWithRetry(String id, GalleryConfig config, int page) throws InterruptedException {
        int retries = 3;
        while (retries-- > 0) {
            try {
                waitGlobalCooldown();
                Document doc = fetchListDocument(config.baseUrl() + "&page=" + page, config.type(), id);
                return extractPagePostDates(doc, config.subjectSelector());
            } catch (Exception e) {
                if (isNotFound(e)) {
                    return Set.of();
                }
                if (retries == 0) {
                    throw new IllegalArgumentException("Failed to inspect page " + page + ": " + e.getMessage(), e);
                }
                if (isBlockingResponse(e)) {
                    applyGlobalCooldown(BLOCK_COOLDOWN_BASE_MS * (4 - retries));
                }
                Thread.sleep(ThreadLocalRandom.current().nextInt(500, 1201));
            }
        }
        return Set.of();
    }

    private static Set<LocalDate> extractPagePostDates(Document doc, String subjectSelector) {
        Set<LocalDate> postDates = new LinkedHashSet<>();
        for (Element tag : doc.select("tr.ub-content")) {
            Element geulnum = tag.selectFirst("td.gall_num");
            String geulnumText = geulnum != null ? geulnum.text().trim() : "";
            if (!isNormalArticleRow(tag, subjectSelector)) {
                continue;
            }

            LocalDateTime postDate = parsePostDate(tag.selectFirst("td.gall_date"));
            if (postDate != null) {
                postDates.add(postDate.toLocalDate());
            }
        }
        return postDates;
    }

    private static OptionalInt fetchLastPageNumber(String id, GalleryConfig config) throws InterruptedException {
        int retries = 3;
        while (retries-- > 0) {
            try {
                waitGlobalCooldown();
                Document doc = fetchListDocument(config.baseUrl() + "&page=1", config.type(), id);
                return extractLastPageNumber(doc);
            } catch (Exception e) {
                if (isNotFound(e)) {
                    return OptionalInt.empty();
                }
                if (retries == 0) {
                    System.out.println("Last page number lookup failed: " + e.getMessage());
                    return OptionalInt.empty();
                }
                if (isBlockingResponse(e)) {
                    applyGlobalCooldown(BLOCK_COOLDOWN_BASE_MS * (4 - retries));
                }
                Thread.sleep(ThreadLocalRandom.current().nextInt(500, 1201));
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt extractLastPageNumber(Document doc) {
        Element pageEnd = doc.selectFirst("a.page_end[href], a[class*=page_end][href]");
        OptionalInt pageEndNumber = pageEnd == null
                ? OptionalInt.empty()
                : parsePageNumberFromHref(pageEnd.attr("href"));
        if (pageEndNumber.isPresent()) {
            return pageEndNumber;
        }

        return OptionalInt.empty();
    }

    private static OptionalInt parsePageNumberFromHref(String href) {
        if (href == null) {
            return OptionalInt.empty();
        }

        int index = href.indexOf("page=");
        if (index < 0) {
            return OptionalInt.empty();
        }

        int start = index + "page=".length();
        int end = start;
        while (end < href.length() && Character.isDigit(href.charAt(end))) {
            end++;
        }
        if (end == start) {
            return OptionalInt.empty();
        }

        try {
            return OptionalInt.of(Integer.parseInt(href.substring(start, end)));
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    private static DatePageRange findPageRangeByDate(String id, GalleryConfig config, DateRange dateRange,
                                                     Map<Integer, Optional<PageDateSpan>> cache) throws InterruptedException {
        PageDateSpan firstSpan = getCachedDateSpan(id, config, 1, cache).orElseThrow(
                () -> new IllegalArgumentException("No dated posts were found on page 1.")
        );
        OptionalInt knownLastPage = OptionalInt.empty();

        if (firstSpan.newest().isBefore(dateRange.start())) {
            throw new IllegalArgumentException("No posts exist in the selected date range.");
        }

        int startPage;
        if (!firstSpan.oldest().isAfter(dateRange.end())) {
            startPage = 1;
        } else {
            int low = 1;
            int high = 2;
            while (true) {
                if (knownLastPage.isPresent() && high >= knownLastPage.getAsInt()) {
                    high = knownLastPage.getAsInt();
                }
                Optional<PageDateSpan> span = getCachedDateSpan(id, config, high, cache);
                if (span.isEmpty()) {
                    int lastPage = findLastExistingPage(id, config, low, high, cache);
                    Optional<PageDateSpan> lastSpan = getCachedDateSpan(id, config, lastPage, cache);
                    if (lastSpan.isPresent() && !lastSpan.get().oldest().isAfter(dateRange.end())) {
                        high = lastPage;
                        break;
                    }
                    throw new IllegalArgumentException("No posts exist before or on the selected end date.");
                }
                if (!span.get().oldest().isAfter(dateRange.end())) {
                    break;
                }
                if (knownLastPage.isPresent() && high >= knownLastPage.getAsInt()) {
                    throw new IllegalArgumentException("No posts exist before or on the selected end date.");
                }
                low = high;
                int nextHigh = safeDoublePage(high);
                if (nextHigh == high) {
                    throw new IllegalArgumentException("Date page search exceeded the maximum page range.");
                }
                high = nextHigh;
            }

            while (low + 1 < high) {
                int mid = low + (high - low) / 2;
                Optional<PageDateSpan> span = getCachedDateSpan(id, config, mid, cache);
                if (span.isPresent() && !span.get().oldest().isAfter(dateRange.end())) {
                    high = mid;
                } else {
                    low = mid;
                }
            }
            startPage = high;
        }

        PageDateSpan startSpan = getCachedDateSpan(id, config, startPage, cache).orElseThrow(
                () -> new IllegalArgumentException("No dated posts were found on the detected start page.")
        );
        if (startSpan.newest().isBefore(dateRange.start())) {
            throw new IllegalArgumentException("No posts exist in the selected date range.");
        }

        int low = startPage;
        int high = safeDoublePage(startPage);
        while (true) {
            if (knownLastPage.isPresent() && high >= knownLastPage.getAsInt()) {
                int lastPage = knownLastPage.getAsInt();
                PageDateSpan lastSpan = getCachedDateSpan(id, config, lastPage, cache).orElse(startSpan);
                if (!lastSpan.newest().isBefore(dateRange.start())) {
                    System.out.println("Start date boundary was not found. Using gallery last page: " + lastPage);
                    return new DatePageRange(startPage, lastPage);
                }
                high = lastPage;
                break;
            }
            Optional<PageDateSpan> span = getCachedDateSpan(id, config, high, cache);
            if (span.isEmpty()) {
                int lastPage = findLastExistingPage(id, config, low, high, cache);
                PageDateSpan lastSpan = getCachedDateSpan(id, config, lastPage, cache).orElse(startSpan);
                if (!lastSpan.newest().isBefore(dateRange.start())) {
                    System.out.println("Start date boundary was not found. Using gallery last page: " + lastPage);
                    return new DatePageRange(startPage, lastPage);
                }
                high = lastPage;
                break;
            }
            if (span.get().newest().isBefore(dateRange.start())) {
                break;
            }
            low = high;
            int nextHigh = safeDoublePage(high);
            if (nextHigh == high) {
                System.out.println("Start date boundary was not found before maximum page. Using page: " + high);
                return new DatePageRange(startPage, high);
            }
            high = nextHigh;
        }

        while (low + 1 < high) {
            int mid = low + (high - low) / 2;
            Optional<PageDateSpan> span = getCachedDateSpan(id, config, mid, cache);
            if (span.isPresent() && !span.get().newest().isBefore(dateRange.start())) {
                low = mid;
            } else {
                high = mid;
            }
        }

        return new DatePageRange(startPage, low);
    }

    private static int findLastExistingPage(String id, GalleryConfig config, int knownExistingPage, int missingPage,
                                            Map<Integer, Optional<PageDateSpan>> cache) throws InterruptedException {
        int low = knownExistingPage;
        int high = missingPage;
        while (low + 1 < high) {
            int mid = low + (high - low) / 2;
            Optional<PageDateSpan> span = getCachedDateSpan(id, config, mid, cache);
            if (span.isPresent()) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private static int safeDoublePage(int page) {
        if (page >= Integer.MAX_VALUE / 2) {
            return Integer.MAX_VALUE;
        }
        return Math.max(page + 1, page * 2);
    }

    private static Optional<PageDateSpan> getCachedDateSpan(String id, GalleryConfig config, int page,
                                                           Map<Integer, Optional<PageDateSpan>> cache) throws InterruptedException {
        Optional<PageDateSpan> cached = cache.get(page);
        if (cached != null) {
            return cached;
        }

        Optional<PageDateSpan> span = fetchPageDateSpanWithRetry(id, config, page);
        cache.put(page, span);
        span.ifPresent(value -> System.out.println(
                "Date probe page " + page + ": " +
                        value.newest().format(OUTPUT_DATE_FORMATTER) + " ~ " +
                        value.oldest().format(OUTPUT_DATE_FORMATTER)
        ));
        return span;
    }

    private static Optional<PageDateSpan> fetchPageDateSpanWithRetry(String id, GalleryConfig config, int page) throws InterruptedException {
        int retries = 3;
        while (retries-- > 0) {
            try {
                waitGlobalCooldown();
                Document doc = fetchListDocument(config.baseUrl() + "&page=" + page, config.type(), id);
                PageDateSpan span = extractPageDateSpan(doc, config.subjectSelector());
                return span == null ? Optional.empty() : Optional.of(span);
            } catch (Exception e) {
                if (isNotFound(e)) {
                    return Optional.empty();
                }
                if (retries == 0) {
                    throw new IllegalArgumentException("Failed to probe page " + page + ": " + e.getMessage(), e);
                }
                if (isBlockingResponse(e)) {
                    applyGlobalCooldown(BLOCK_COOLDOWN_BASE_MS * (4 - retries));
                }
                Thread.sleep(ThreadLocalRandom.current().nextInt(500, 1201));
            }
        }
        return Optional.empty();
    }

    private static PageDateSpan extractPageDateSpan(Document doc, String subjectSelector) {
        LocalDateTime newest = null;
        LocalDateTime oldest = null;
        for (Element tag : doc.select("tr.ub-content")) {
            Element geulnum = tag.selectFirst("td.gall_num");
            String geulnumText = geulnum != null ? geulnum.text().trim() : "";
            if (!isNormalArticleRow(tag, subjectSelector)) {
                continue;
            }

            LocalDateTime postDate = parsePostDate(tag.selectFirst("td.gall_date"));
            if (postDate == null) {
                continue;
            }
            if (newest == null || postDate.isAfter(newest)) {
                newest = postDate;
            }
            if (oldest == null || postDate.isBefore(oldest)) {
                oldest = postDate;
            }
        }

        if (newest == null || oldest == null) {
            return null;
        }
        return new PageDateSpan(newest, oldest);
    }

    private static LocalDateTime parsePostDate(Element day) {
        if (day == null) {
            return null;
        }

        LocalDateTime parsed = parsePostDateText(day.attr("title"));
        if (parsed != null) {
            return parsed;
        }
        return parsePostDateText(day.text());
    }

    private static LocalDateTime parsePostDateText(String rawText) {
        if (rawText == null) {
            return null;
        }

        String text = rawText.trim();
        if (text.isEmpty()) {
            return null;
        }

        String normalized = text.replace('.', '-').replace('/', '-');
        String[] dateTimePatterns = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss"
        };
        for (String pattern : dateTimePatterns) {
            try {
                return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
            }
        }

        String[] datePatterns = {"yyyy-MM-dd"};
        for (String pattern : datePatterns) {
            try {
                return LocalDate.parse(normalized, DateTimeFormatter.ofPattern(pattern)).atStartOfDay();
            } catch (DateTimeParseException ignored) {
            }
        }

        if (normalized.matches("\\d{2}-\\d{2} \\d{2}:\\d{2}(:\\d{2})?")) {
            LocalDate now = LocalDate.now();
            int month = Integer.parseInt(normalized.substring(0, 2));
            int year = month > now.getMonthValue() ? now.getYear() - 1 : now.getYear();
            String value = year + "-" + normalized;
            if (value.length() == 16) {
                value += ":00";
            }
            try {
                return LocalDateTime.parse(value, OUTPUT_DATE_FORMATTER);
            } catch (DateTimeParseException ignored) {
            }
        }

        if (normalized.matches("\\d{2}-\\d{2}")) {
            LocalDate now = LocalDate.now();
            int month = Integer.parseInt(normalized.substring(0, 2));
            int year = month > now.getMonthValue() ? now.getYear() - 1 : now.getYear();
            try {
                return LocalDate.parse(year + "-" + normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
            } catch (DateTimeParseException ignored) {
            }
        }

        if (normalized.matches("\\d{2}:\\d{2}(:\\d{2})?")) {
            String value = normalized.length() == 5 ? normalized + ":00" : normalized;
            try {
                return LocalDateTime.of(LocalDate.now(), LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm:ss")));
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    private static GalleryConfig resolveGalleryConfig(String id, String preferredType) throws InterruptedException {
        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, preferredType);
        addCandidate(candidates, "m");
        addCandidate(candidates, "main");
        addCandidate(candidates, "mini");

        Exception lastException = null;
        for (String type : candidates) {
            GalleryConfig config = galleryConfigFor(type, id);
            try {
                fetchListDocument(config.baseUrl() + "&page=1", config.type(), id);
                return config;
            } catch (Exception e) {
                lastException = e;
                if (isBlockingResponse(e)) {
                    applyGlobalCooldown(BLOCK_COOLDOWN_BASE_MS);
                    waitGlobalCooldown();
                }
            }
        }

        String message = lastException == null ? "알 수 없는 오류" : lastException.getMessage();
        throw new IllegalArgumentException("갤러리 주소를 찾지 못했습니다. TYPE과 갤러리 ID를 확인하세요. (" + message + ")");
    }

    private static void addCandidate(List<String> candidates, String type) {
        String normalized = normalizeType(type);
        if (!candidates.contains(normalized)) {
            candidates.add(normalized);
        }
    }

    private static String normalizeType(String type) {
        if ("mini".equals(type)) return "mini";
        if ("m".equals(type)) return "m";
        return "main";
    }

    private static GalleryConfig galleryConfigFor(String type, String id) {
        String normalized = normalizeType(type);
        if ("mini".equals(normalized)) {
            return new GalleryConfig("mini", "https://gall.dcinside.com/mini/board/lists/?id=" + id, "td.gall_subject");
        }
        if ("m".equals(normalized)) {
            return new GalleryConfig("m", "https://gall.dcinside.com/mgallery/board/lists/?id=" + id, "td.gall_subject");
        }
        return new GalleryConfig("main", "https://gall.dcinside.com/board/lists/?id=" + id, "td.gall_num");
    }

    private static Document fetchListDocument(String url, String gallType, String id) throws java.io.IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Cache-Control", "no-cache")
                .header("Referer", refererFor(gallType, id))
                .timeout(15000)
                .get();
    }

    private static String determineSubnik(String imgSrc) {
        if (imgSrc.contains("fix_nik.gif") || imgSrc.contains("fix_sub_managernik.gif") ||
                imgSrc.contains("fix_managernik.gif") || imgSrc.contains("sub_managernik.gif")) {
            return "고정";
        } else if (imgSrc.contains("nik.gif") || imgSrc.contains("managernik.gif")) {
            return "비고정";
        }
        return "유동";
    }

    private static String refererFor(String gallType, String id) {
        if ("mini".equals(gallType)) {
            return "https://gall.dcinside.com/mini/board/lists/?id=" + id;
        }
        if ("m".equals(gallType)) {
            return "https://gall.dcinside.com/mgallery/board/lists/?id=" + id;
        }
        return "https://gall.dcinside.com/board/lists/?id=" + id;
    }

    private record GalleryConfig(String type, String baseUrl, String subjectSelector) {
    }

    private record DateRange(LocalDateTime start, LocalDateTime end) {
        private boolean includes(LocalDateTime dateTime) {
            return !dateTime.isBefore(start) && !dateTime.isAfter(end);
        }
    }

    private record DatePageRange(int startPage, int endPage) {
    }

    private record PageDateSpan(LocalDateTime newest, LocalDateTime oldest) {
    }

    private record NeighborPostedDates(LocalDate beforeOrSame, LocalDate afterOrSame) {
    }

    private static int parseSafeInt(String text) {
        try {
            return Integer.parseInt(text.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean isArticleNumber(String text) {
        return text != null && text.trim().matches("\\d+");
    }

    private static boolean isNormalArticleRow(Element tag, String subjectSelector) {
        Element number = tag.selectFirst("td.gall_num");
        if (number == null || !isArticleNumber(number.text())) {
            return false;
        }

        String rowClass = tag.className() == null ? "" : tag.className().toLowerCase(Locale.ROOT);
        if (rowClass.contains("notice") || rowClass.contains("ad")) {
            return false;
        }

        Element subject = tag.selectFirst(subjectSelector);
        if (subject == null) {
            return false;
        }
        if (isExcludedListLabel(subject.text())) {
            return false;
        }

        Element category = tag.selectFirst("td.gall_subject");
        return category == null || !isExcludedListLabel(category.text());
    }

    private static boolean isExcludedListLabel(String text) {
        if (text == null) {
            return false;
        }

        String normalized = text.trim();
        return normalized.equals("AD")
                || normalized.equals("\uace0\uc815")
                || normalized.equals("\uacf5\uc9c0")
                || normalized.equals("\uc124\ubb38");
    }

    private static boolean isBlockingResponse(Exception e) {
        if (e instanceof HttpStatusException statusException) {
            int status = statusException.getStatusCode();
            return status == 403 || status == 429 || status == 503;
        }
        return false;
    }

    private static boolean isNotFound(Exception e) {
        return e instanceof HttpStatusException statusException && statusException.getStatusCode() == 404;
    }

    private static void applyGlobalCooldown(long millis) {
        long jitter = ThreadLocalRandom.current().nextLong(1000, 4001);
        long until = System.currentTimeMillis() + millis + jitter;
        globalCooldownUntil = Math.max(globalCooldownUntil, until);
        System.err.println("\n[차단 방지] 페이지 요청 쿨다운 " + ((millis + jitter) / 1000) + "초");
    }

    private static void waitGlobalCooldown() throws InterruptedException {
        long wait = globalCooldownUntil - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
    }
}
