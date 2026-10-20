package app.morphe.extension.music.patches.downloads;

import static app.morphe.extension.shared.StringRef.str;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.text.Html;
import android.webkit.CookieManager;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.music.patches.scrobbling.ScrobbleManager;
import app.morphe.extension.music.patches.spoof.SpoofVideoStreamsPatch;
import app.morphe.extension.music.settings.Settings;
import app.morphe.extension.music.shared.VideoInformation;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.innertube.PlayerResponseOuterClass.Format;
import app.morphe.extension.shared.innertube.PlayerResponseOuterClass.PlayerResponse;
import app.morphe.extension.shared.innertube.PlayerResponseOuterClass.ThumbnailEntry;
import app.morphe.extension.shared.innertube.PlayerResponseOuterClass.VideoDetails;
import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.shared.spoof.requests.StreamingDataRequest;

public final class LocalDownloadManager {

    public interface DownloadListener {
        void onDownloadStateChanged(String videoId, boolean completed);
    }

    public enum LyricsSource {
        AUTO_ALL("Otomatik (YT Music + 3. Parti)"),
        YOUTUBE_MUSIC("YouTube Music (Resmi Sözler)"),
        GENIUS("Genius (Geniş Arşiv)"),
        LRCLIB("LRCLIB (Senkronize Söz)"),
        MUSIXMATCH("Musixmatch (Doğrulanmış)"),
        DEEZER("Deezer (Halka Açık API)"),
        NETEASE("NetEase (Asya / Global)"),
        LYRICS_OVH("Lyrics.ovh"),
        LYRIST("Lyrist");

        public final String label;
        LyricsSource(String label) { this.label = label; }
    }

    public enum TranslationEngine {
        AI_LLM("Yapay Zeka (Bağlam Duyarlı)"),
        DEEPL("DeepL Çeviri (Gelişmiş)"),
        GOOGLE_GTX("Google Çeviri (Hızlı)");

        public final String label;
        TranslationEngine(String label) { this.label = label; }
    }

    public record QueueItem(
            String videoId,
            String title,
            String playlistName,
            int progress,
            boolean active,
            long downloadedBytes,
            long totalBytes,
            long speedBytesPerSec,
            boolean failed
    ) {
        public QueueItem(String videoId, String title, String playlistName, int progress, boolean active) {
            this(videoId, title, playlistName, progress, active, 0L, 0L, 0L, false);
        }
    }

    private static final Set<DownloadListener> LISTENERS = new CopyOnWriteArraySet<>();
    private static final ExecutorService PLAYLIST_QUEUE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final ExecutorService SINGLE_DOWNLOAD_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final ExecutorService SEGMENT_POOL = Executors.newFixedThreadPool(8);

    private static final String DOWNLOAD_CHANNEL = "morphe_audio_downloads";
    private static final String USER_AGENT = "com.google.android.youtube/19.47.53 (Linux; U; Android 14) gzip";
    private static final String WEB_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final int SEGMENT_COUNT = 4;
    private static final int BUFFER_SIZE = 64 * 1024;

    private static final long NOTIFICATION_INTERVAL_MILLISECONDS = 850;
    private static final AtomicLong lastNotificationTime = new AtomicLong();

    private static final int CONNECT_TIMEOUT_MILLISECONDS = 15_000;
    private static final int READ_TIMEOUT_MILLISECONDS = 30_000;
    private static final int MAX_STALLED_ATTEMPTS = 4;
    private static final int MAX_CONTINUATION_PAGES = 35;

    private static final String QUEUE_PREFS = "morphe_download_queue_prefs";
    private static final String QUEUE_KEY = "pending_queue_json";

    private static final long RATE_CHECK_INTERVAL_MS = 300L;

    private static final long BASE_BACKOFF_MS = 500L;
    private static final long MAX_BACKOFF_MS = 30_000L;

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{1,20}");
    private static final Pattern PLAYLIST_PARAM = Pattern.compile("[?&]list=([A-Za-z0-9_-]+)");
    private static final Pattern BROWSE_PLAYLIST = Pattern.compile("(?:/playlist/|/browse/)(VL[A-Za-z0-9_-]+|PL[A-Za-z0-9_-]+|OLAK[A-Za-z0-9_-]+|LM|LL)");
    private static final Pattern JSON_VIDEO_ID = Pattern.compile("\"videoId\"\\s*:\\s*\"([A-Za-z0-9_-]{11})\"");
    private static final Pattern CONTINUATION_TOKEN = Pattern.compile("\"continuation\"\\s*:\\s*\"([A-Za-z0-9_%=-]{16,})\"");
    private static final Pattern DIRECT_VIDEO_ID = Pattern.compile("(?:v=|youtu\\.be/|/watch\\?v=|/shorts/)([A-Za-z0-9_-]{11})");
    private static final Pattern SAPISID_PATTERN = Pattern.compile("(?:^|;\\s*)(?:__Secure-3PAPISID|SAPISID)=([^;]+)");
    private static final Pattern NON_QU_Q_PATTERN = Pattern.compile("(?i)q(?!u)");

    private static final Pattern GENIUS_LYRICS_CONTAINER = Pattern.compile(
            "data-lyrics-container=\"true\"[^>]*>(.*?)</div>", Pattern.DOTALL);
    private static final Pattern GENIUS_LEGACY_LYRICS = Pattern.compile(
            "class=\"lyrics\"[^>]*>(.*?)</p>", Pattern.DOTALL);

    private static final String THUMBNAIL_URL = "https://i.ytimg.com/vi/";

    private static final Set<String> ACTIVE_DOWNLOADS = ConcurrentHashMap.newKeySet();
    private static final Map<String, QueueItem> QUEUE_MAP = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<String, QueueItem> FAILED_MAP = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Set<String> CANCELLED_IDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> ACTIVE_PLAYLIST_JOBS = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger CANCEL_EPOCH = new AtomicInteger(0);
    private static final AtomicBoolean PAUSED = new AtomicBoolean(false);
    private static final AtomicInteger COMPLETED_SESSION_COUNT = new AtomicInteger(0);

    @Nullable private static volatile String cachedMusixmatchToken = null;

    private LocalDownloadManager() {}

    public static void addListener(DownloadListener listener) { LISTENERS.add(listener); }
    public static void removeListener(DownloadListener listener) { LISTENERS.remove(listener); }
    public static boolean isDownloading(String videoId) { return ACTIVE_DOWNLOADS.contains(videoId); }
    public static int getActiveDownloadCount() { return ACTIVE_DOWNLOADS.size(); }
    public static int getTotalQueueCount() { return QUEUE_MAP.size(); }
    public static int getFailedDownloadCount() { return FAILED_MAP.size(); }
    public static int getCompletedSessionCount() { return COMPLETED_SESSION_COUNT.get(); }
    public static boolean isPaused() { return PAUSED.get(); }

    public static void setPaused(boolean paused) {
        PAUSED.set(paused);
        notifyListeners("", false);
    }

    public static List<QueueItem> getQueueSnapshot() {
        synchronized (QUEUE_MAP) { return new ArrayList<>(QUEUE_MAP.values()); }
    }

    public static List<QueueItem> getFailedSnapshot() {
        synchronized (FAILED_MAP) { return new ArrayList<>(FAILED_MAP.values()); }
    }

    public static long getTotalActiveSpeedBytesPerSec() {
        long total = 0L;
        for (QueueItem item : getQueueSnapshot()) if (item.active()) total += item.speedBytesPerSec();
        return total;
    }

    public static void prioritizeInQueue(String videoId) {
        if (videoId == null) return;
        synchronized (QUEUE_MAP) {
            QueueItem target = QUEUE_MAP.remove(videoId);
            if (target == null) return;
            LinkedHashMap<String, QueueItem> reordered = new LinkedHashMap<>();
            reordered.put(videoId, target);
            reordered.putAll(QUEUE_MAP);
            QUEUE_MAP.clear();
            QUEUE_MAP.putAll(reordered);
        }
        saveQueuePersist();
        notifyListeners(videoId, false);
    }

    public static int retryFailedDownloads() {
        List<QueueItem> failedList = getFailedSnapshot();
        FAILED_MAP.clear();
        int count = 0;
        for (QueueItem item : failedList) {
            enqueue(item.videoId(), item.playlistName(), false);
            count++;
        }
        saveQueuePersist();
        notifyListeners("", false);
        return count;
    }

    public static void clearFailedDownloads() {
        FAILED_MAP.clear();
        saveQueuePersist();
        notifyListeners("", false);
    }

    public static void removeFromQueue(String videoId) {
        if (videoId == null || videoId.isEmpty()) return;
        CANCELLED_IDS.add(videoId);
        QUEUE_MAP.remove(videoId);
        FAILED_MAP.remove(videoId);
        ACTIVE_DOWNLOADS.remove(videoId);
        saveQueuePersist();
        notifyListeners(videoId, false);
    }

    public static void cancelAllDownloads(@Nullable File musicRoot) {
        CANCEL_EPOCH.incrementAndGet();
        PAUSED.set(false);
        QUEUE_MAP.clear();
        FAILED_MAP.clear();
        ACTIVE_DOWNLOADS.clear();
        ACTIVE_PLAYLIST_JOBS.clear();
        CANCELLED_IDS.clear();
        clearPersistedQueue();

        if (musicRoot != null && musicRoot.isDirectory()) {
            File[] partFiles = musicRoot.listFiles(f -> f.isFile() && f.getName().endsWith(".part"));
            if (partFiles != null) {
                for (File f : partFiles) OfflineStorage.delete(f);
            }
        }

        try {
            Context context = Utils.getContext();
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancelAll();
        } catch (Exception ignored) {}

        notifyListeners("", true);
    }

    private static void saveQueuePersist() {
        try {
            Context ctx = Utils.getContext();
            if (ctx == null) return;
            JSONArray arr = new JSONArray();
            synchronized (QUEUE_MAP) {
                for (QueueItem item : QUEUE_MAP.values()) {
                    JSONObject o = new JSONObject();
                    o.put("videoId", item.videoId());
                    o.put("title", item.title());
                    o.put("playlist", item.playlistName());
                    arr.put(o);
                }
            }
            ctx.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE)
                    .edit().putString(QUEUE_KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static void clearPersistedQueue() {
        try {
            Context ctx = Utils.getContext();
            if (ctx == null) return;
            ctx.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE)
                    .edit().remove(QUEUE_KEY).apply();
        } catch (Exception ignored) {}
    }

    public static void restorePersistedQueue() {
        try {
            Context ctx = Utils.getContext();
            if (ctx == null) return;
            SharedPreferences sp = ctx.getSharedPreferences(QUEUE_PREFS, Context.MODE_PRIVATE);
            String json = sp.getString(QUEUE_KEY, "");
            if (json == null || json.isBlank()) return;
            JSONArray arr = new JSONArray(json);
            final int epoch = CANCEL_EPOCH.get();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                final String vid = o.optString("videoId", "");
                final String title = o.optString("title", vid);
                final String playlist = o.optString("playlist", "Tekli İndirme");
                if (vid.isEmpty()) continue;
                if (ACTIVE_DOWNLOADS.contains(vid) || QUEUE_MAP.containsKey(vid)) continue;
                File musicRoot = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Morphe");
                if (OfflineStorage.isTrackDownloaded(musicRoot, vid)) continue;
                putQueueItem(vid, title, playlist, 0, false, 0L, 0L, 0L);
                
                SINGLE_DOWNLOAD_EXECUTOR.submit(new Runnable() {
                    @Override public void run() {
                        downloadBlocking(epoch, vid, title, null, null, 0, null, playlist, false);
                    }
                });
            }
            sp.edit().remove(QUEUE_KEY).apply();
            notifyListeners("", false);
        } catch (Exception ignored) {}
    }

    private static void putQueueItem(String videoId, String title, @Nullable String playlist,
                                     int progress, boolean active, long downloadedBytes,
                                     long totalBytes, long speedBytesPerSec) {
        QueueItem existing = QUEUE_MAP.get(videoId);
        String resolvedTitle = (title != null && !title.isBlank()) ? title
                : (existing != null ? existing.title() : videoId);
        String resolvedPl = (playlist != null && !playlist.isBlank()) ? playlist
                : (existing != null ? existing.playlistName() : "Tekli İndirme");
        QUEUE_MAP.put(videoId, new QueueItem(videoId, resolvedTitle, resolvedPl,
                progress, active, downloadedBytes, totalBytes, speedBytesPerSec, false));
    }

    private static void awaitIfPaused(int epoch, String videoId) throws InterruptedException {
        while (PAUSED.get()) {
            if (CANCEL_EPOCH.get() != epoch || CANCELLED_IDS.contains(videoId)) {
                throw new InterruptedException("Cancelled while paused");
            }
            Thread.sleep(350);
        }
    }

    private static void notifyListeners(String videoId, boolean completed) {
        for (DownloadListener listener : LISTENERS) {
            try {
                listener.onDownloadStateChanged(videoId, completed);
            } catch (RuntimeException error) {
                Logger.printException(() -> "Local download listener failed", error);
            }
        }
    }

    public static void enqueue(String videoId) {
        enqueue(videoId, null, true);
    }

    public static void enqueue(String videoId, @Nullable String targetPlaylistName, boolean showToasts) {
        if (videoId == null || !VIDEO_ID.matcher(videoId).matches()) {
            if (showToasts) Utils.showToastShort(str("morphe_music_downloads_unknown_track"));
            return;
        }

        final int epoch = CANCEL_EPOCH.get();
        boolean alreadyQueued;
        synchronized (QUEUE_MAP) {
            if (CANCEL_EPOCH.get() != epoch) return;
            alreadyQueued = ACTIVE_DOWNLOADS.contains(videoId) || QUEUE_MAP.containsKey(videoId);
            if (!alreadyQueued) {
                CANCELLED_IDS.remove(videoId);
                FAILED_MAP.remove(videoId);
                QUEUE_MAP.put(videoId, new QueueItem(videoId, videoId,
                        targetPlaylistName == null || targetPlaylistName.isBlank() ? "Tekli İndirme" : targetPlaylistName,
                        0, false, 0L, 0L, 0L, false));
            }
        }
        if (alreadyQueued) {
            if (showToasts) Utils.showToastShort("Bu şarkı zaten indirme kuyruğunda!");
            return;
        }

        saveQueuePersist();

        final boolean isPlayingTrack = videoId.equals(VideoInformation.getVideoId());
        ScrobbleManager metadata = ScrobbleManager.getInstance();

        String title = isPlayingTrack ? metadata.getCurrentTitle() : null;
        String artist = isPlayingTrack ? metadata.getCurrentArtist() : null;
        String album = isPlayingTrack ? metadata.getCurrentAlbum() : null;
        int duration = isPlayingTrack ? metadata.getCurrentDurationSeconds() : 0;
        Bitmap artwork = isPlayingTrack ? metadata.getCurrentArtwork() : null;

        synchronized (QUEUE_MAP) {
            if (CANCEL_EPOCH.get() != epoch || CANCELLED_IDS.contains(videoId)
                    || !QUEUE_MAP.containsKey(videoId)) {
                return;
            }
            putQueueItem(videoId, title != null ? title : videoId, targetPlaylistName, 0, false, 0L, 0L, 0L);
        }
        notifyListeners(videoId, false);

        final String fTitle = title;
        final String fArtist = artist;
        final String fAlbum = album;
        final int fDuration = duration;
        final Bitmap fArtwork = artwork;
        final String fVideoId = videoId;
        final String fTargetPlaylistName = targetPlaylistName;
        final boolean fShowToasts = showToasts;

        SINGLE_DOWNLOAD_EXECUTOR.submit(new Runnable() {
            @Override public void run() {
                downloadBlocking(epoch, fVideoId, fTitle, fArtist, fAlbum, fDuration, fArtwork, fTargetPlaylistName, fShowToasts);
            }
        });
    }

    public static LyricsSource getSavedLyricsSource() {
        try {
            SharedPreferences sp = Utils.getContext().getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE);
            return LyricsSource.valueOf(sp.getString("preferred_lyrics_source", LyricsSource.AUTO_ALL.name()));
        } catch (Exception ignored) { return LyricsSource.AUTO_ALL; }
    }

    public static void setSavedLyricsSource(LyricsSource source) {
        try {
            Utils.getContext().getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putString("preferred_lyrics_source", source.name()).apply();
        } catch (Exception ignored) {}
    }

    public static TranslationEngine getSavedTranslationEngine() {
        try {
            SharedPreferences sp = Utils.getContext().getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE);
            return TranslationEngine.valueOf(sp.getString("preferred_translation_engine", TranslationEngine.AI_LLM.name()));
        } catch (Exception ignored) { return TranslationEngine.AI_LLM; }
    }

    public static void setSavedTranslationEngine(TranslationEngine engine) {
        try {
            Utils.getContext().getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putString("preferred_translation_engine", engine.name()).apply();
        } catch (Exception ignored) {}
    }

    public static String getSavedDeeplApiKey() {
        try {
            SharedPreferences sp = Utils.getContext().getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE);
            return sp.getString("deepl_api_key", "").trim();
        } catch (Exception ignored) { return ""; }
    }

    public static void setSavedDeeplApiKey(String key) {
        try {
            Utils.getContext().getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putString("deepl_api_key", key == null ? "" : key.trim()).apply();
        } catch (Exception ignored) {}
    }

    public static boolean isGibberishOrCorruptedLyrics(@Nullable String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("nucico vudeb") || lower.contains("wob gopini")
                || lower.contains("quqoy jol") || lower.contains("feloh piveh")
                || lower.contains("rij bofigu") || lower.contains("gac rutibo")) {
            return true;
        }
        int nonQuCount = 0;
        Matcher m = NON_QU_Q_PATTERN.matcher(lower);
        while (m.find()) nonQuCount++;
        if (nonQuCount >= 3) return true;

        String[] lines = lower.split("\\r?\\n");
        int checkedLines = 0, threeWordWeirdLines = 0;
        for (String rawLine : lines) {
            if (rawLine.contains("└")) continue;
            String clean = rawLine.replaceAll("\\[\\d{2}:\\d{2}\\.\\d{2,3}\\]", "").trim();
            if (clean.isEmpty() || clean.startsWith("[")) continue;
            checkedLines++;
            String[] words = clean.split("\\s+");
            if (words.length >= 2 && words.length <= 4) {
                boolean hasWeirdChar = clean.contains("q") || clean.contains("x") || clean.contains("w");
                if (hasWeirdChar) threeWordWeirdLines++;
            }
        }
        return checkedLines >= 6 && (threeWordWeirdLines * 100 / checkedLines) >= 45;
    }

    public static int purgeCorruptedLyrics(File directory) {
        if (directory == null || !directory.isDirectory()) return 0;
        File[] lrcFiles = directory.listFiles(f -> f.isFile() && f.getName().endsWith(".lrc"));
        if (lrcFiles == null) return 0;

        int purged = 0;
        SharedPreferences prefs = null;
        try {
            prefs = Utils.getContext().getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE);
        } catch (Exception ignored) {}

        for (File f : lrcFiles) {
            try {
                String content = OfflineStorage.readText(f);
                if (isGibberishOrCorruptedLyrics(content)) {
                    String vid = f.getName().substring(0, f.getName().length() - 4);
                    OfflineStorage.delete(f);
                    if (prefs != null) prefs.edit().remove("lyrics_lookup_failed_" + vid).apply();
                    purged++;
                }
            } catch (Exception ignored) {}
        }
        return purged;
    }

    public static boolean isAlreadyTurkishText(@Nullable String text) {
        if (text == null || text.isBlank()) return false;
        String clean = text.replaceAll("\\[\\d{2}:\\d{2}\\.\\d{2,3}\\]", "").replaceAll("└.*", "");
        int trSpecificChars = 0;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if ("ğĞşŞıİçÇöÖüÜ".indexOf(c) >= 0) trSpecificChars++;
        }
        if (trSpecificChars >= 4) return true;
        String lower = " " + clean.toLowerCase(Locale.ROOT).replaceAll("[^a-zğüşıöç\\s]", " ") + " ";
        String[] trWords = {" bir ", " ve ", " bu ", " ne ", " için ", " gibi ", " kadar ", " daha ", " çok ", " sen ", " ben ", " bana ", " sana ", " beni ", " seni ", " gönül ", " gözlerin ", " kalbim ", " geceler ", " yalnız ", " sevda ", " artık "};
        int hits = 0;
        for (String w : trWords) if (lower.contains(w)) hits++;
        return hits >= 3;
    }

    public static boolean isTrackLyricsNativeTurkish(File directory, String videoId) {
        if (directory == null || videoId == null || videoId.isBlank()) return false;
        File file = new File(directory, videoId + ".lrc");
        if (!file.isFile()) return false;
        try {
            String value = OfflineStorage.readText(file);
            if (isGibberishOrCorruptedLyrics(value)) { OfflineStorage.delete(file); return false; }
            if (value.contains("[TR_NATIVE_POEM_OR_SONG]")) return true;
            String stripped = stripExistingTranslations(value);
            return !value.contains("└ 🇹🇷") && isAlreadyTurkishText(stripped);
        } catch (Exception ignored) { return false; }
    }

    public static boolean hasTurkishTranslation(File directory, String videoId) {
        if (directory == null || videoId == null || videoId.isBlank()) return false;
        File file = new File(directory, videoId + ".lrc");
        if (!file.isFile()) return false;
        try {
            String value = OfflineStorage.readText(file);
            if (isGibberishOrCorruptedLyrics(value)) { OfflineStorage.delete(file); return false; }
            return value.contains("└ 🇹🇷") || value.contains("└ TR:") || value.contains("[TR_NATIVE_POEM_OR_SONG]") || isAlreadyTurkishText(value);
        } catch (Exception ignored) { return false; }
    }

    public static boolean fetchAndSaveSyncedLyrics(File directory, String videoId, String title, String artist, int durationSeconds) {
        return fetchAndSaveSyncedLyricsCustom(directory, videoId, title, artist, durationSeconds,
                getSavedLyricsSource(), getSavedTranslationEngine(), getSavedDeeplApiKey(), false);
    }

    public static boolean fetchAndSaveSyncedLyricsCustom(File directory, String videoId, String title, String artist, int durationSeconds, LyricsSource source, TranslationEngine engine, @Nullable String deeplKey, boolean forceOverwrite) {
        if (title == null || title.isBlank()) return false;
        File lrcFile = new File(directory, videoId + ".lrc");
        if (!directory.isDirectory() && !directory.mkdirs()) return false;

        if (lrcFile.isFile()) {
            try {
                String currentText = OfflineStorage.readText(lrcFile);
                if (isGibberishOrCorruptedLyrics(currentText)) OfflineStorage.delete(lrcFile);
            } catch (Exception ignored) {}
        }

        if (!forceOverwrite && hasTurkishTranslation(directory, videoId)) return true;

        String cleanTitle = cleanTrackMetadata(title);
        String cleanArtist = artist == null ? "" : cleanTrackMetadata(artist);
        String finalLrcContent = null;

        if (!forceOverwrite && lrcFile.isFile() && lrcFile.length() > 16) {
            try {
                String existing = OfflineStorage.readText(lrcFile);
                if (!existing.isBlank() && !isGibberishOrCorruptedLyrics(existing)) {
                    finalLrcContent = stripExistingTranslations(existing);
                }
            } catch (Exception ignored) {}
        }

        if (finalLrcContent == null || finalLrcContent.isBlank()) {
            finalLrcContent = fetchFromSelectedSource(source, videoId, cleanTitle, cleanArtist, durationSeconds);
        }

        if (finalLrcContent == null || finalLrcContent.isBlank() || isGibberishOrCorruptedLyrics(finalLrcContent)) return false;

        if (isAlreadyTurkishText(finalLrcContent)) {
            try {
                String nativeTrContent = "[TR_NATIVE_POEM_OR_SONG]\n" + finalLrcContent.trim();
                OfflineStorage.writeText(lrcFile, nativeTrContent);
                return true;
            } catch (Exception ignored) { return false; }
        }

        try { OfflineStorage.writeText(lrcFile, finalLrcContent); } catch (Exception ignored) {}

        try {
            List<String> timestamps = new ArrayList<>();
            List<String> originalTexts = new ArrayList<>();
            Pattern p = Pattern.compile("(\\[\\d{2}:\\d{2}\\.\\d{2,3}\\])(.*)");
            boolean isSynced = false;

            for (String line : finalLrcContent.split("\\r?\\n")) {
                if (line.contains("└ 🇹🇷") || line.contains("└ TR:") || line.contains("[TR_NATIVE_POEM_OR_SONG]")) continue;
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    isSynced = true;
                    timestamps.add(m.group(1));
                    originalTexts.add(m.group(2).trim());
                } else if (!line.startsWith("[")) {
                    originalTexts.add(line.trim());
                }
            }

            String[] translatedLines = translateLyricsRobust(originalTexts, engine, deeplKey);
            StringBuilder dualLyrics = new StringBuilder();

            if (isSynced) {
                for (int i = 0; i < timestamps.size(); i++) {
                    String ts = timestamps.get(i);
                    String orig = originalTexts.get(i);
                    String trans = (i < translatedLines.length && translatedLines[i] != null) ? translatedLines[i] : "";
                    dualLyrics.append(ts).append(orig).append("\n");
                    if (!orig.isEmpty() && !trans.isEmpty() && !orig.equalsIgnoreCase(trans)) {
                        dualLyrics.append(ts).append(" └ 🇹🇷 ").append(trans).append("\n");
                    }
                }
            } else {
                for (int i = 0; i < originalTexts.size(); i++) {
                    String orig = originalTexts.get(i);
                    String trans = (i < translatedLines.length && translatedLines[i] != null) ? translatedLines[i] : "";
                    dualLyrics.append(orig).append("\n");
                    if (!orig.isEmpty() && !trans.isEmpty() && !orig.equalsIgnoreCase(trans)) {
                        dualLyrics.append(" └ 🇹🇷 ").append(trans).append("\n");
                    }
                }
            }
            OfflineStorage.writeText(lrcFile, dualLyrics.toString());
        } catch (Exception e) {
            Logger.printDebug(() -> "Lyrics translation failed: " + videoId, e);
        }
        return true;
    }

    public static boolean saveManualLyrics(File directory, String videoId, String title, String artist, String rawLyrics) {
        if (directory == null || videoId == null || videoId.isBlank() || rawLyrics == null || rawLyrics.isBlank()) return false;
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) return false;
            File lrcFile = new File(directory, videoId + ".lrc");
            String trimmed = rawLyrics.trim();
            if (isGibberishOrCorruptedLyrics(trimmed)) return false;
            String content = isAlreadyTurkishText(trimmed) ? "[TR_NATIVE_POEM_OR_SONG]\n" + trimmed : trimmed;
            OfflineStorage.writeText(lrcFile, content);
            try {
                SharedPreferences sp = Utils.getContext().getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE);
                sp.edit().remove("lyrics_lookup_failed_" + videoId).apply();
            } catch (Exception ignored) {}
            return true;
        } catch (Exception ignored) { return false; }
    }

    public static boolean exportLyricsToDownloads(Context ctx, File lrcFile, String trackTitle) {
        if (lrcFile == null || !lrcFile.exists() || ctx == null) return false;
        try {
            String safeName = trackTitle.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
            if (safeName.isEmpty()) safeName = "lyrics_" + System.currentTimeMillis();
            File destDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MorpheLyrics");
            if (!destDir.exists() && !destDir.mkdirs()) {
                destDir = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "MorpheLyrics");
                if (!destDir.exists()) destDir.mkdirs();
            }
            File destFile = new File(destDir, safeName + ".lrc");
            try (InputStream in = new FileInputStream(lrcFile); OutputStream out = new FileOutputStream(destFile)) {
                byte[] buf = new byte[8192]; int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }
            return true;
        } catch (Exception e) {
            Logger.printException(() -> "Export lyrics failed", e);
            return false;
        }
    }

    private static String stripExistingTranslations(String rawLrc) {
        StringBuilder sb = new StringBuilder();
        for (String line : rawLrc.split("\\r?\\n")) {
            if (line.contains("└ 🇹🇷") || line.contains("└ TR:") || line.contains("[TR_NATIVE_POEM_OR_SONG]")) continue;
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    @Nullable
    private static String fetchFromSelectedSource(LyricsSource source, String videoId, String cleanTitle, String cleanArtist, int durationSeconds) {
        String content = null;

        if (source == LyricsSource.YOUTUBE_MUSIC || source == LyricsSource.AUTO_ALL) {
            try { content = tryFetchYtMusicLyrics(videoId); } catch (Exception ignored) {}
            if (content != null && !content.isBlank() && !isGibberishOrCorruptedLyrics(content)) return content;
            content = null;
            if (source == LyricsSource.YOUTUBE_MUSIC) return null;
        }

        if (source == LyricsSource.LRCLIB || source == LyricsSource.AUTO_ALL) {
            try { content = tryFetchLrclibExact(cleanTitle, cleanArtist, durationSeconds); } catch (Exception ignored) {}
            if (content == null || content.isBlank()) {
                try { content = tryFetchLrclibSearchStrict(cleanTitle, cleanArtist); } catch (Exception ignored) {}
            }
            if (content != null && !content.isBlank() && !isGibberishOrCorruptedLyrics(content)) return content;
            content = null;
            if (source == LyricsSource.LRCLIB) return null;
        }

        if (source == LyricsSource.MUSIXMATCH || source == LyricsSource.AUTO_ALL) {
            try { content = tryFetchMusixmatchStrict(cleanTitle, cleanArtist, durationSeconds); } catch (Exception ignored) {}
            if (content != null && !content.isBlank() && !isGibberishOrCorruptedLyrics(content)) return content;
            content = null;
            if (source == LyricsSource.MUSIXMATCH) return null;
        }

        if (source == LyricsSource.DEEZER || source == LyricsSource.AUTO_ALL) {
            try { content = tryFetchDeezer(cleanTitle, cleanArtist); } catch (Exception ignored) {}
            if (content != null && !content.isBlank() && !isGibberishOrCorruptedLyrics(content)) return content;
            content = null;
            if (source == LyricsSource.DEEZER) return null;
        }

        if (source == LyricsSource.NETEASE || source == LyricsSource.AUTO_ALL) {
            try { content = tryFetchNetEase(cleanTitle, cleanArtist); } catch (Exception ignored) {}
            if (content != null && !content.isBlank() && !isGibberishOrCorruptedLyrics(content)) return content;
            content = null;
            if (source == LyricsSource.NETEASE) return null;
        }

        if (source == LyricsSource.GENIUS || source == LyricsSource.AUTO_ALL) {
            try { content = tryFetchGenius(cleanTitle, cleanArtist); } catch (Exception ignored) {}
            if (content != null && !content.isBlank() && !isGibberishOrCorruptedLyrics(content)) return content;
            content = null;
            if (source == LyricsSource.GENIUS) return null;
        }

        if (source == LyricsSource.LYRICS_OVH || source == LyricsSource.AUTO_ALL) {
            if (!cleanArtist.isBlank()) {
                try { content = tryFetchLyricsOvh(cleanArtist, cleanTitle); } catch (Exception ignored) {}
            }
            if (content != null && !content.isBlank() && !isGibberishOrCorruptedLyrics(content)) return content;
            content = null;
            if (source == LyricsSource.LYRICS_OVH) return null;
        }

        if (source == LyricsSource.LYRIST || source == LyricsSource.AUTO_ALL) {
            try { content = tryFetchLyrist(cleanTitle, cleanArtist); } catch (Exception ignored) {}
            if (content != null && !content.isBlank() && !isGibberishOrCorruptedLyrics(content)) return content;
        }

        return (content != null && !isGibberishOrCorruptedLyrics(content)) ? content : null;
    }

    @Nullable
    private static String tryFetchYtMusicLyrics(String videoId) {
        if (videoId == null || videoId.isBlank()) return null;
        try {
            String endpoint = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false";
            String payload = "{\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"1.20240101.01.00\",\"hl\":\"tr\",\"gl\":\"TR\"}},\"videoId\":\"" + videoId + "\"}";
            String response = postInnerTubeJson(endpoint, payload);
            if (response == null || response.isEmpty()) return null;

            JSONObject root = new JSONObject(response);
            String lyrics = deepSearchLyrics(root);
            if (lyrics != null && !lyrics.isBlank()) return lyrics;

            String continuation = findFirstContinuationForLyrics(root);
            if (continuation != null) {
                String contPayload = "{\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"1.20240101.01.00\",\"hl\":\"tr\",\"gl\":\"TR\"}},\"continuation\":\"" + continuation + "\"}";
                String contResponse = postInnerTubeJson("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false", contPayload);
                if (contResponse != null) {
                    String contLyrics = deepSearchLyrics(new JSONObject(contResponse));
                    if (contLyrics != null && !contLyrics.isBlank()) return contLyrics;
                }
            }

            String browseId = findBrowseIdForLyrics(root);
            if (browseId != null) {
                String browsePayload = "{\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"1.20240101.01.00\",\"hl\":\"tr\",\"gl\":\"TR\"}},\"browseId\":\"" + browseId + "\"}";
                String browseResponse = postInnerTubeJson("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false", browsePayload);
                if (browseResponse != null) {
                    String browseLyrics = deepSearchLyrics(new JSONObject(browseResponse));
                    if (browseLyrics != null && !browseLyrics.isBlank()) return browseLyrics;
                }
            }
            return null;
        } catch (Exception e) {
            Logger.printDebug(() -> "YT Music lyrics fetch failed for " + videoId, e);
            return null;
        }
    }

    /**
     * YENİ: YT Music üzerinden Albüm, Yıl, Tür, Şirket ve Credits çeker.
     */
    @Nullable
    private static JSONObject tryFetchYtMusicMetadata(String videoId) {
        try {
            String endpoint = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false";
            String payload = "{\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"1.20240101.01.00\",\"hl\":\"tr\",\"gl\":\"TR\"}},\"videoId\":\"" + videoId + "\"}";
            String response = postInnerTubeJson(endpoint, payload);
            if (response == null || response.isEmpty()) return null;

            JSONObject root = new JSONObject(response);
            String jsonStr = root.toString();

            String year = "", genre = "", label = "", credits = "";
            
            // Basit regex tabanlı ayıklama (Karmaşık ağaç yapısında daha hızlı)
            Matcher mYear = Pattern.compile("\"(\\d{4})\"\\s*,?\\s*\"musicInlineBadgeRenderer\"").matcher(jsonStr);
            if (mYear.find()) year = mYear.group(1);

            Matcher mLabel = Pattern.compile("\"recordLabel\"\\s*:\\s*\"([^\"]+)\"").matcher(jsonStr);
            if (mLabel.find()) label = mLabel.group(1);

            Matcher mGenre = Pattern.compile("\"genre\"\\s*:\\s*\"([^\"]+)\"").matcher(jsonStr);
            if (mGenre.find()) genre = mGenre.group(1);
            
            Matcher mCredits = Pattern.compile("\"credits\"\\s*:\\s*\\[\\s*\\{\\s*\"text\"\\s*:\\s*\"([^\"]+)\"").matcher(jsonStr);
            if (mCredits.find()) credits = mCredits.group(1);

            JSONObject meta = new JSONObject();
            meta.put("year", year);
            meta.put("genre", genre);
            meta.put("label", label);
            meta.put("credits", credits);
            return meta;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String deepSearchLyrics(JSONObject obj) {
        if (obj == null) return null;
        if (obj.has("musicDescriptionShelfRenderer")) {
            JSONObject shelf = obj.optJSONObject("musicDescriptionShelfRenderer");
            if (shelf != null) {
                JSONObject description = shelf.optJSONObject("description");
                if (description != null) {
                    String text = description.optString("simpleText", "");
                    if (!text.isEmpty()) return text.trim();
                    JSONArray runs = description.optJSONArray("runs");
                    if (runs != null) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < runs.length(); i++) {
                            JSONObject run = runs.optJSONObject(i);
                            if (run != null) sb.append(run.optString("text", ""));
                        }
                        if (sb.length() > 0) return sb.toString().trim();
                    }
                }
            }
        }
        java.util.Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object val = obj.opt(key);
            if (val instanceof JSONObject) {
                String res = deepSearchLyrics((JSONObject) val);
                if (res != null && !res.isBlank()) return res;
            } else if (val instanceof JSONArray) {
                JSONArray arr = (JSONArray) val;
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (item instanceof JSONObject) {
                        String res = deepSearchLyrics((JSONObject) item);
                        if (res != null && !res.isBlank()) return res;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static String findFirstContinuationForLyrics(JSONObject obj) {
        if (obj == null) return null;
        if (obj.has("continuationCommand")) {
            JSONObject cmd = obj.optJSONObject("continuationCommand");
            if (cmd != null) {
                String token = cmd.optString("token", "");
                if (!token.isEmpty()) return token;
            }
        }
        java.util.Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object val = obj.opt(key);
            if (val instanceof JSONObject) {
                String res = findFirstContinuationForLyrics((JSONObject) val);
                if (res != null) return res;
            } else if (val instanceof JSONArray) {
                JSONArray arr = (JSONArray) val;
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (item instanceof JSONObject) {
                        String res = findFirstContinuationForLyrics((JSONObject) item);
                        if (res != null) return res;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static String findBrowseIdForLyrics(JSONObject obj) {
        if (obj == null) return null;
        if (obj.has("browseEndpoint")) {
            JSONObject ep = obj.optJSONObject("browseEndpoint");
            if (ep != null) {
                String id = ep.optString("browseId", "");
                if (id.startsWith("MPLYt") || id.startsWith("VL")) return id;
            }
        }
        java.util.Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object val = obj.opt(key);
            if (val instanceof JSONObject) {
                String res = findBrowseIdForLyrics((JSONObject) val);
                if (res != null) return res;
            } else if (val instanceof JSONArray) {
                JSONArray arr = (JSONArray) val;
                for (int i = 0; i < arr.length(); i++) {
                    Object item = arr.opt(i);
                    if (item instanceof JSONObject) {
                        String res = findBrowseIdForLyrics((JSONObject) item);
                        if (res != null) return res;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static String tryFetchDeezer(String title, String artist) throws Exception {
        if (title == null || title.isBlank()) return null;
        String q = urlEncode(title + (artist != null && !artist.isBlank() ? " " + artist : ""));
        String searchUrl = "https://api.deezer.com/search?q=" + q;
        String res = httpGetText(searchUrl, "https://www.deezer.com/");
        if (res == null) return null;
        JSONObject json = new JSONObject(res);
        JSONArray data = json.optJSONArray("data");
        if (data == null || data.length() == 0) return null;
        JSONObject track = data.optJSONObject(0);
        if (track == null) return null;
        String titleFound = track.optString("title", "");
        if (!isLooseMetadataMatch(title, titleFound) && !isStrictMetadataMatch(title, artist, titleFound, "")) return null;
        long id = track.optLong("id");
        String lyricUrl = "https://api.deezer.com/track/" + id + "/lyrics";
        String lyrRes = httpGetText(lyricUrl, "https://www.deezer.com/");
        if (lyrRes == null) return null;
        JSONObject lyrJson = new JSONObject(lyrRes);
        String lyrics = lyrJson.optString("lyrics", "");
        if (lyrics.isBlank()) return null;
        return lyrics.replaceAll("\\*+\\s*Bu şarkı sözleri.*", "").replaceAll("\\*+\\s*This lyrics.*", "").trim();
    }

    @Nullable
    private static String tryFetchNetEase(String title, String artist) throws Exception {
        if (title == null || title.isBlank()) return null;
        String q = urlEncode(title + (artist != null && !artist.isBlank() ? " " + artist : ""));
        String searchUrl = "http://music.163.com/api/search/get/web?s=" + q + "&type=1&limit=1";
        String res = httpGetText(searchUrl, "http://music.163.com/");
        if (res == null) return null;
        JSONObject json = new JSONObject(res);
        JSONObject result = json.optJSONObject("result");
        if (result == null) return null;
        JSONArray songs = result.optJSONArray("songs");
        if (songs == null || songs.length() == 0) return null;
        JSONObject song = songs.optJSONObject(0);
        if (song == null) return null;
        String titleFound = song.optString("name", "");
        if (!isLooseMetadataMatch(title, titleFound) && !isStrictMetadataMatch(title, artist, titleFound, "")) return null;
        long id = song.optLong("id");
        String lyricUrl = "http://music.163.com/api/song/lyric?id=" + id + "&lv=1&kv=1&tv=-1";
        String lyrRes = httpGetText(lyricUrl, "http://music.163.com/");
        if (lyrRes == null) return null;
        JSONObject lyrJson = new JSONObject(lyrRes);
        JSONObject lrc = lyrJson.optJSONObject("lrc");
        if (lrc == null) return null;
        String lyrics = lrc.optString("lyric", "");
        if (lyrics.isBlank()) return null;
        return lyrics;
    }

    @Nullable
    private static String tryFetchMusixmatchStrict(String title, String artist, int durationSeconds) throws Exception {
        if (title == null || title.isBlank()) return null;
        if (cachedMusixmatchToken == null || cachedMusixmatchToken.isBlank()) {
            String tokenUrl = "https://apic-desktop.musixmatch.com/ws/1.1/token.get?app_id=web-desktop-app-v1.0";
            String tokenBody = httpGetText(tokenUrl, "https://www.musixmatch.com/");
            if (tokenBody != null) {
                JSONObject root = new JSONObject(tokenBody);
                JSONObject body = root.optJSONObject("message") != null ? root.optJSONObject("message").optJSONObject("body") : null;
                if (body != null) cachedMusixmatchToken = body.optString("user_token", null);
            }
        }
        if (cachedMusixmatchToken == null || cachedMusixmatchToken.isBlank()) return null;

        StringBuilder url = new StringBuilder("https://apic-desktop.musixmatch.com/ws/1.1/macro.subtitles.get")
                .append("?format=json&namespace=lyrics_richsynched&subtitle_format=lrc&app_id=web-desktop-app-v1.0")
                .append("&q_track=").append(urlEncode(title))
                .append("&q_artist=").append(urlEncode(artist == null ? "" : artist))
                .append("&usertoken=").append(urlEncode(cachedMusixmatchToken));
        if (durationSeconds > 0) url.append("&q_duration=").append(durationSeconds);

        String response = httpGetText(url.toString(), "https://www.musixmatch.com/");
        if (response == null || response.isBlank()) return null;

        JSONObject root = new JSONObject(response);
        JSONObject message = root.optJSONObject("message");
        if (message == null) return null;
        JSONObject body = message.optJSONObject("body");
        if (body == null) return null;
        JSONObject macroCalls = body.optJSONObject("macro_calls");
        if (macroCalls == null) return null;

        JSONObject matcherTrack = macroCalls.optJSONObject("matcher.track.get");
        if (matcherTrack != null) {
            JSONObject mtMsg = matcherTrack.optJSONObject("message");
            JSONObject mtBody = mtMsg != null ? mtMsg.optJSONObject("body") : null;
            JSONObject trackObj = mtBody != null ? mtBody.optJSONObject("track") : null;
            if (trackObj != null) {
                String foundTrackName = trackObj.optString("track_name", "");
                String foundArtistName = trackObj.optString("artist_name", "");
                if (!isStrictMetadataMatch(title, artist, foundTrackName, foundArtistName) && !isLooseMetadataMatch(title, foundTrackName)) return null;
            }
        }

        JSONObject subGet = macroCalls.optJSONObject("track.subtitles.get");
        if (subGet != null) {
            JSONObject subMsg = subGet.optJSONObject("message");
            JSONObject subBody = subMsg != null ? subMsg.optJSONObject("body") : null;
            JSONArray subList = subBody != null ? subBody.optJSONArray("subtitle_list") : null;
            if (subList != null && subList.length() > 0) {
                JSONObject subObj = subList.optJSONObject(0).optJSONObject("subtitle");
                if (subObj != null) {
                    String lrc = subObj.optString("subtitle_body", "").trim();
                    if (!lrc.isBlank() && !isGibberishOrCorruptedLyrics(lrc)) return lrc;
                }
            }
        }

        JSONObject lyrGet = macroCalls.optJSONObject("track.lyrics.get");
        if (lyrGet != null) {
            JSONObject lyrMsg = lyrGet.optJSONObject("message");
            JSONObject lyrBody = lyrMsg != null ? lyrMsg.optJSONObject("body") : null;
            JSONObject lyrObj = lyrBody != null ? lyrBody.optJSONObject("lyrics") : null;
            if (lyrObj != null) {
                String plain = lyrObj.optString("lyrics_body", "");
                if (!plain.isBlank()) {
                    String cleaned = plain.replaceAll("\\*+\\s*This Lyrics is NOT for Commercial use.*", "").trim();
                    if (!isGibberishOrCorruptedLyrics(cleaned)) return cleaned;
                }
            }
        }
        return null;
    }

    @Nullable
    private static String tryFetchGenius(String title, String artist) {
        if (title == null || title.isBlank()) return null;
        try {
            String query = title + (artist != null && !artist.isBlank() ? " " + artist : "");
            String searchUrl = "https://genius.com/api/search/multi?per_page=5&q=" + urlEncode(query);
            String searchBody = httpGetText(searchUrl, "https://genius.com/");
            if (searchBody == null || searchBody.isBlank()) return null;

            JSONObject root = new JSONObject(searchBody);
            JSONObject response = root.optJSONObject("response");
            if (response == null) return null;
            JSONArray sections = response.optJSONArray("sections");
            if (sections == null) return null;

            String bestUrl = null;
            for (int i = 0; i < sections.length(); i++) {
                JSONObject section = sections.optJSONObject(i);
                if (section == null || !"song".equals(section.optString("type", ""))) continue;
                JSONArray hits = section.optJSONArray("hits");
                if (hits == null) continue;

                for (int j = 0; j < hits.length(); j++) {
                    JSONObject hit = hits.optJSONObject(j);
                    if (hit == null) continue;
                    JSONObject result = hit.optJSONObject("result");
                    if (result == null) continue;

                    String resultTitle = result.optString("title", "");
                    String resultArtist = "";
                    JSONObject primaryArtist = result.optJSONObject("primary_artist");
                    if (primaryArtist != null) resultArtist = primaryArtist.optString("name", "");
                    String url = result.optString("url", "");
                    if (url.isEmpty()) continue;

                    if (isStrictMetadataMatch(title, artist, resultTitle, resultArtist) || isLooseMetadataMatch(title, resultTitle)) {
                        bestUrl = url;
                        break;
                    }
                }
                if (bestUrl != null) break;
            }

            if (bestUrl == null) return null;
            String pageHtml = httpGetText(bestUrl, "https://genius.com/");
            if (pageHtml == null || pageHtml.isBlank()) return null;
            return extractGeniusLyricsFromHtml(pageHtml);
        } catch (Exception ignored) { return null; }
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static String extractGeniusLyricsFromHtml(String html) {
        StringBuilder sb = new StringBuilder();
        Matcher modern = GENIUS_LYRICS_CONTAINER.matcher(html);
        while (modern.find()) {
            String chunk = modern.group(1);
            if (chunk == null) continue;
            String cleaned = chunk.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</p>", "\n").replaceAll("(?is)<script.*?</script>", "").replaceAll("(?is)<style.*?</style>", "");
            sb.append(Html.fromHtml(cleaned).toString()).append("\n");
        }

        if (sb.length() == 0) {
            Matcher legacy = GENIUS_LEGACY_LYRICS.matcher(html);
            while (legacy.find()) {
                String chunk = legacy.group(1);
                if (chunk == null) continue;
                sb.append(Html.fromHtml(chunk.replaceAll("(?i)<br\\s*/?>", "\n")).toString()).append("\n");
            }
        }

        String result = sb.toString().trim();
        if (result.isEmpty()) return null;
        result = result.replaceAll("(?m)^\\[(?:Verse|Chorus|Bridge|Intro|Outro|Pre-Chorus|Hook)[^\\]]*\\]\\s*$", "").trim();
        result = result.replaceAll("\\n{3,}", "\n\n");
        return result.isBlank() ? null : result;
    }

    private static boolean isStrictMetadataMatch(String queryTitle, String queryArtist, String resultTitle, String resultArtist) {
        if (resultTitle == null || resultTitle.isBlank()) return false;
        String qt = normalizeForMatch(queryTitle), rt = normalizeForMatch(resultTitle);
        if (qt.isEmpty() || rt.isEmpty()) return false;
        if (!(qt.equals(rt) || qt.contains(rt) || rt.contains(qt))) return false;
        if (queryArtist != null && !queryArtist.isBlank()) {
            String qa = normalizeForMatch(queryArtist), ra = normalizeForMatch(resultArtist == null ? "" : resultArtist);
            if (!qa.isEmpty() && !ra.isEmpty() && !qa.equals("youtubemusic")) {
                if (qt.contains(ra) || qa.contains(rt)) return true;
                return qa.equals(ra) || qa.contains(ra) || ra.contains(qa);
            }
        }
        return true;
    }

    private static boolean isLooseMetadataMatch(String queryTitle, String resultTitle) {
        if (queryTitle == null || resultTitle == null) return false;
        String qt = normalizeForMatch(queryTitle), rt = normalizeForMatch(resultTitle);
        if (qt.isEmpty() || rt.isEmpty()) return false;
        if (qt.length() < 4 || rt.length() < 4) return qt.equals(rt);
        return qt.equals(rt) || rt.contains(qt) || qt.contains(rt);
    }

    private static String normalizeForMatch(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replace('ğ', 'g').replace('ü', 'u').replace('ş', 's').replace('ı', 'i').replace('ö', 'o').replace('ç', 'c')
                .replaceAll("[^a-z0-9\\u3040-\\u30ff\\u4e00-\\u9faf\\u0400-\\u04ff]", "");
    }

    private static String cleanTrackMetadata(String value) {
        if (value == null) return "";
        String cleaned = value.trim();
        cleaned = cleaned.replaceAll("(?i)\\s*[-–—]\\s*topic\\s*$", "");
        cleaned = cleaned.replaceAll("(?i)\\s*\\((official\\s+)?(music\\s+)?video\\)\\s*$", "");
        cleaned = cleaned.replaceAll("(?i)\\s*\\[(official\\s+)?(music\\s+)?video\\]\\s*$", "");
        cleaned = cleaned.replaceAll("(?i)\\s*\\((lyrics?|audio|visuali[sz]er|official audio|lyric video|slowed|reverb)\\)\\s*$", "");
        cleaned = cleaned.replaceAll("(?i)\\s*\\[(lyrics?|audio|visuali[sz]er|official audio|lyric video|slowed|reverb)\\]\\s*$", "");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private static String tryFetchLrclibExact(String title, String artist, int durationSeconds) throws IOException, org.json.JSONException {
        if (title == null || title.isBlank()) return null;
        StringBuilder url = new StringBuilder("https://lrclib.net/api/get?track_name=").append(urlEncode(title)).append("&artist_name=").append(urlEncode(artist == null ? "" : artist));
        if (durationSeconds > 0) url.append("&duration=").append(durationSeconds);
        String body = httpGetText(url.toString(), "https://lrclib.net/");
        if (body == null || body.isBlank()) return null;
        JSONObject json = new JSONObject(body);
        String synced = json.optString("syncedLyrics", "");
        if (!synced.isBlank()) return synced;
        String plain = json.optString("plainLyrics", "");
        return plain.isBlank() ? null : plain;
    }

    private static String tryFetchLrclibSearchStrict(String title, String artist) throws IOException, org.json.JSONException {
        if (title == null || title.isBlank()) return null;
        StringBuilder url = new StringBuilder("https://lrclib.net/api/search?q=").append(urlEncode(title + (artist != null && !artist.isBlank() ? " " + artist : "")));
        String body = httpGetText(url.toString(), "https://lrclib.net/");
        if (body == null || body.isBlank()) return null;
        JSONArray results = new JSONArray(body);
        if (results.length() == 0) return null;
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.optJSONObject(i);
            if (item == null) continue;
            String synced = item.optString("syncedLyrics", ""), plain = item.optString("plainLyrics", "");
            if (synced.isBlank() && plain.isBlank()) continue;
            if (isStrictMetadataMatch(title, artist, item.optString("trackName", ""), item.optString("artistName", ""))) {
                return !synced.isBlank() ? synced : plain;
            }
        }
        return null;
    }

    private static String tryFetchLyricsOvh(String artist, String title) throws IOException, org.json.JSONException {
        if (artist == null || artist.isBlank() || title == null || title.isBlank()) return null;
        String url = "https://api.lyrics.ovh/v1/" + urlEncodePath(artist) + "/" + urlEncodePath(title);
        String body = httpGetText(url, "https://lyrics.ovh/");
        if (body == null || body.isBlank()) return null;
        JSONObject json = new JSONObject(body);
        String lyrics = json.optString("lyrics", "");
        return lyrics.isBlank() ? null : lyrics.trim();
    }

    private static String tryFetchLyrist(String title, String artist) throws IOException, org.json.JSONException {
        if (title == null || title.isBlank()) return null;
        String path = "https://lyrist.vercel.app/api/" + urlEncodePath(title);
        if (artist != null && !artist.isBlank()) path += "/" + urlEncodePath(artist);
        String body = httpGetText(path, "https://lyrist.vercel.app/");
        if (body == null || body.isBlank()) return null;
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            JSONObject json = new JSONObject(trimmed);
            String lyrics = json.optString("lyrics", "");
            if (lyrics.isBlank()) lyrics = json.optString("plainLyrics", "");
            if (lyrics.isBlank()) lyrics = json.optString("syncedLyrics", "");
            return lyrics.isBlank() ? null : lyrics.trim();
        }
        return trimmed.startsWith("[") ? null : trimmed;
    }

    private static String urlEncode(String value) {
        try { return URLEncoder.encode(value == null ? "" : value, "UTF-8"); } catch (Exception ignored) { return value == null ? "" : value; }
    }

    private static String urlEncodePath(String value) {
        return urlEncode(value).replace("+", "%20");
    }

    private static String httpGetText(String address, String referer) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new java.net.URL(address).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLISECONDS);
            connection.setReadTimeout(READ_TIMEOUT_MILLISECONDS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", WEB_USER_AGENT);
            connection.setRequestProperty("Accept", "application/json, text/plain, text/html, */*");
            if (referer != null && !referer.isBlank()) connection.setRequestProperty("Referer", referer);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return null;
            InputStream stream = connection.getInputStream();
            return stream == null ? null : readStreamToString(stream);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String[] translateLyricsRobust(List<String> originalLines, TranslationEngine engine, @Nullable String deeplKey) {
        String[] result = new String[originalLines.size()];
        if (originalLines.isEmpty()) return result;
        StringBuilder batch = new StringBuilder();
        for (String line : originalLines) batch.append(line == null ? "" : line).append("\n");
        String translatedBatch = "";
        
        if (engine == TranslationEngine.AI_LLM) {
            translatedBatch = doTranslateLlm(batch.toString());
        } else if (engine == TranslationEngine.DEEPL) {
            translatedBatch = doTranslateDeepL(batch.toString(), deeplKey);
        }
        
        if (translatedBatch.isBlank()) translatedBatch = doTranslateGoogleGtx(batch.toString());
        
        String[] batchLines = translatedBatch.split("\\r?\\n");
        if (!translatedBatch.isBlank() && batchLines.length == originalLines.size()) {
            for (int i = 0; i < batchLines.length; i++) result[i] = batchLines[i].trim();
            return result;
        }
        for (int i = 0; i < originalLines.size(); i++) {
            String line = originalLines.get(i);
            if (line == null || line.isBlank()) {
                result[i] = "";
            } else {
                String tr = "";
                if (engine == TranslationEngine.AI_LLM) tr = doTranslateLlm(line.trim());
                else if (engine == TranslationEngine.DEEPL) tr = doTranslateDeepL(line.trim(), deeplKey);
                
                if (tr.isBlank()) tr = doTranslateGoogleGtx(line.trim());
                result[i] = tr;
            }
        }
        return result;
    }

    /**
     * YENİ: Yapay Zeka (Llama 3) ile tüm sözleri bağlamıyla beraber çevirir.
     */
    private static String doTranslateLlm(String text) {
        if (text == null || text.isBlank()) return "";
        HttpURLConnection conn = null;
        try {
            // Ücretsiz Pollinations AI API'si
            conn = (HttpURLConnection) new java.net.URL("https://text.pollinations.ai/").openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            
            JSONObject body = new JSONObject();
            body.put("model", "openai");
            JSONArray messages = new JSONArray();
            
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", "Sen profesyonel bir şarkı sözü çevirmenisin. Sana verilen şarkı sözlerini İngilizceden Türkçeye çevireceksin. KURALLAR: 1. Şarkının ruhunu, bağlamını ve duygusunu koru. 2. Satır sayısını birebir koru (her satır bir satıra denk gelsin). 3. Eğer sözlerin başında zaman damgası [00:12.00] gibi ibareler varsa bunları aynen koru. 4. Sadece çevrilmiş metni döndür, başına veya sonuna hiçbir açıklama ekleme.");
            messages.put(sys);
            
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", text);
            messages.put(user);
            
            body.put("messages", messages);
            
            try (OutputStream os = conn.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String resp = readStreamToString(conn.getInputStream());
                if (resp.trim().startsWith("{")) {
                    try {
                        JSONObject root = new JSONObject(resp);
                        JSONArray choices = root.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            return choices.optJSONObject(0).optJSONObject("message").optString("content", "").trim();
                        }
                    } catch (Exception ignored) {}
                }
                return resp.trim();
            }
        } catch (Exception e) {
            Logger.printDebug(() -> "AI Translation failed", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return "";
    }

    private static String doTranslateDeepL(String text, @Nullable String apiKey) {
        if (text == null || text.isBlank()) return "";
        if (apiKey != null && !apiKey.isBlank()) {
            HttpURLConnection conn = null;
            try {
                boolean isFreeKey = apiKey.endsWith(":fx");
                String host = isFreeKey ? "https://api-free.deepl.com/v2/translate" : "https://api.deepl.com/v2/translate";
                conn = (HttpURLConnection) new java.net.URL(host).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Authorization", "DeepL-Auth-Key " + apiKey.trim());
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                String body = "text=" + urlEncode(text) + "&target_lang=TR";
                try (OutputStream os = conn.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    String resp = readStreamToString(conn.getInputStream());
                    JSONObject json = new JSONObject(resp);
                    JSONArray arr = json.optJSONArray("translations");
                    if (arr != null && arr.length() > 0) {
                        String tr = arr.optJSONObject(0).optString("text", "").trim();
                        if (!tr.isBlank()) return tr;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        HttpURLConnection rpcConn = null;
        try {
            long id = (System.currentTimeMillis() % 900000L) + 100000L;
            JSONObject payload = new JSONObject();
            payload.put("jsonrpc", "2.0");
            payload.put("method", "LMT_handle_jobs");
            payload.put("id", id);
            JSONObject params = new JSONObject();
            JSONArray jobs = new JSONArray();
            JSONObject job = new JSONObject();
            job.put("kind", "default");
            JSONArray sentences = new JSONArray();
            JSONObject sentence = new JSONObject();
            sentence.put("text", text);
            sentence.put("id", 1);
            sentence.put("prefix", "");
            sentences.put(sentence);
            job.put("sentences", sentences);
            job.put("preferred_num_beams", 1);
            jobs.put(job);
            params.put("jobs", jobs);
            JSONObject lang = new JSONObject();
            lang.put("source_lang_computed", "auto");
            lang.put("target_lang", "TR");
            params.put("lang", lang);
            params.put("priority", 1);
            params.put("timestamp", System.currentTimeMillis());
            payload.put("params", params);

            String jsonStr = payload.toString();
            rpcConn = (HttpURLConnection) new java.net.URL("https://www2.deepl.com/jsonrpc").openConnection();
            rpcConn.setRequestMethod("POST");
            rpcConn.setDoOutput(true);
            rpcConn.setConnectTimeout(6000);
            rpcConn.setReadTimeout(7500);
            rpcConn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            rpcConn.setRequestProperty("User-Agent", WEB_USER_AGENT);
            rpcConn.setRequestProperty("Origin", "https://www.deepl.com");
            rpcConn.setRequestProperty("Referer", "https://www.deepl.com/");
            try (OutputStream os = rpcConn.getOutputStream()) { os.write(jsonStr.getBytes(StandardCharsets.UTF_8)); }
            if (rpcConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String resp = readStreamToString(rpcConn.getInputStream());
                JSONObject root = new JSONObject(resp);
                JSONObject resObj = root.optJSONObject("result");
                JSONArray transArr = resObj != null ? resObj.optJSONArray("translations") : null;
                if (transArr != null && transArr.length() > 0) {
                    JSONArray beams = transArr.optJSONObject(0).optJSONArray("beams");
                    if (beams != null && beams.length() > 0) {
                        JSONArray sents = beams.optJSONObject(0).optJSONArray("sentences");
                        if (sents != null && sents.length() > 0) {
                            String out = sents.optJSONObject(0).optString("text", "").trim();
                            if (!out.isBlank()) return out;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (rpcConn != null) rpcConn.disconnect();
        }
        return "";
    }

    private static String doTranslateGoogleGtx(String text) {
        if (text == null || text.isBlank()) return "";
        HttpURLConnection conn = null;
        try {
            String address = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=tr&dt=t&q=" + urlEncode(text);
            conn = (HttpURLConnection) new java.net.URL(address).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(6000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", WEB_USER_AGENT);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return "";
            String response = readStreamToString(conn.getInputStream());
            JSONArray root = new JSONArray(response);
            JSONArray segments = root.optJSONArray(0);
            if (segments == null) return "";
            StringBuilder translated = new StringBuilder();
            for (int i = 0; i < segments.length(); i++) {
                JSONArray part = segments.optJSONArray(i);
                if (part != null && part.length() > 0) translated.append(part.optString(0, ""));
            }
            return translated.toString().trim();
        } catch (Exception ignored) { return ""; }
        finally { if (conn != null) conn.disconnect(); }
    }

    public static void syncPlaylists(File musicRoot, @Nullable String playlistName, boolean notifyStart) {
        syncPlaylists(musicRoot, playlistName, 0, 0, notifyStart);
    }

    public static void syncPlaylists(File musicRoot, @Nullable String playlistName, int startOneBased, int endOneBased, boolean notifyStart) {
        final String jobKey = "sync_" + (playlistName == null ? "ALL" : playlistName.toLowerCase(Locale.ROOT));
        if (!ACTIVE_PLAYLIST_JOBS.add(jobKey)) {
            if (notifyStart) Utils.runOnMainThread(() -> Utils.showToastShort("Bu liste zaten indiriliyor!"));
            return;
        }
        final int epoch = CANCEL_EPOCH.get();
        final File fMusicRoot = musicRoot;
        final String fPlaylistName = playlistName;
        final int fStart = startOneBased;
        final int fEnd = endOneBased;
        final boolean fNotify = notifyStart;

        PLAYLIST_QUEUE_EXECUTOR.submit(new Runnable() {
            @Override public void run() {
                try {
                    if (CANCEL_EPOCH.get() != epoch) return;
                    List<String> missing = OfflineStorage.getMissingVideoIds(fMusicRoot, fPlaylistName, fStart, fEnd);
                    missing.removeIf(id -> ACTIVE_DOWNLOADS.contains(id) || QUEUE_MAP.containsKey(id));
                    if (missing.isEmpty()) {
                        if (fNotify) Utils.runOnMainThread(() -> Utils.showToastShort("Seçilen aralıktaki şarkılar zaten indirilmiş"));
                        return;
                    }
                    for (final String id : missing) {
                        CANCELLED_IDS.remove(id);
                        putQueueItem(id, id, fPlaylistName != null ? fPlaylistName : "Otomatik Senkron", 0, false, 0L, 0L, 0L);
                    }
                    saveQueuePersist();
                    notifyListeners("", false);
                    if (fNotify) Utils.runOnMainThread(() -> Utils.showToastShort(missing.size() + " şarkı kuyruğa eklendi"));
                    for (final String videoId : missing) {
                        if (CANCEL_EPOCH.get() != epoch) return;
                        if (CANCELLED_IDS.remove(videoId)) { QUEUE_MAP.remove(videoId); continue; }
                        awaitIfPaused(epoch, videoId);
                        downloadBlocking(epoch, videoId, null, null, null, 0, null, fPlaylistName, false);
                    }
                    if (fNotify && CANCEL_EPOCH.get() == epoch) Utils.runOnMainThread(() -> Utils.showToastShort("Oynatma listesi indirmesi tamamlandı"));
                } catch (InterruptedException ignored) {
                } finally {
                    ACTIVE_PLAYLIST_JOBS.remove(jobKey);
                    notifyListeners("", true);
                }
            }
        });
    }

    /**
     * YENİ: Kütüphanedeki (Beğenilenler, listeler, geçmiş) tüm şarkıları topluca çeker.
     */
    public static void fetchAndDownloadLibrary(File musicRoot) {
        final String jobKey = "library_sync";
        if (!ACTIVE_PLAYLIST_JOBS.add(jobKey)) {
            Utils.runOnMainThread(() -> Utils.showToastShort("Kütüphane zaten taranıyor!"));
            return;
        }
        final int epoch = CANCEL_EPOCH.get();
        final File fMusicRoot = musicRoot;

        PLAYLIST_QUEUE_EXECUTOR.submit(new Runnable() {
            @Override public void run() {
                try {
                    if (CANCEL_EPOCH.get() != epoch) return;
                    Utils.runOnMainThread(() -> Utils.showToastShort("Kütüphane taranıyor, lütfen bekleyin..."));
                    
                    Set<String> allIds = new LinkedHashSet<>();
                    // 1. Beğenilen Şarkılar
                    fetchInnerTubeBrowseIds("VLLM", allIds, Integer.MAX_VALUE);
                    if (allIds.isEmpty()) fetchInnerTubeBrowseIds("FEmusic_liked_videos", allIds, Integer.MAX_VALUE);
                    // 2. Kullanıcının Çalma Listeleri (Eğer giriş yapıldıysa)
                    Set<String> playlistIds = new LinkedHashSet<>();
                    fetchInnerTubeBrowseIds("FEmusic_liked_playlists", playlistIds, 50); 
                    for (String plId : playlistIds) {
                        fetchInnerTubeBrowseIds(plId, allIds, Integer.MAX_VALUE);
                    }

                    if (allIds.isEmpty()) {
                        Utils.runOnMainThread(() -> Utils.showToastShort("Kütüphanenizde şarkı bulunamadı. Giriş yaptığınızdan emin olun."));
                        return;
                    }

                    List<String> toDownload = new ArrayList<>();
                    for (String id : allIds) {
                        if (!OfflineStorage.isTrackDownloaded(fMusicRoot, id) && !QUEUE_MAP.containsKey(id)) {
                            CANCELLED_IDS.remove(id);
                            putQueueItem(id, id, "Kütüphane Toplu", 0, false, 0L, 0L, 0L);
                            toDownload.add(id);
                        }
                    }
                    saveQueuePersist();
                    notifyListeners("", false);
                    
                    final int totalCount = toDownload.size();
                    if (totalCount == 0) {
                        Utils.runOnMainThread(() -> Utils.showToastShort("Kütüphanenizdeki tüm şarkılar zaten indirilmiş!"));
                        return;
                    }

                    Utils.runOnMainThread(() -> Utils.showToastShort("Kütüphaneden " + totalCount + " yeni şarkı indiriliyor..."));
                    
                    int count = 0;
                    for (final String id : toDownload) {
                        if (CANCEL_EPOCH.get() != epoch) return;
                        if (CANCELLED_IDS.remove(id)) { QUEUE_MAP.remove(id); continue; }
                        awaitIfPaused(epoch, id);
                        count++;
                        final int index = count;
                        showDownloadNotification("library_sync", "Kütüphane İndiriliyor", (index * 100) / totalCount, true, index + "/" + totalCount);
                        downloadBlocking(epoch, id, null, null, null, 0, null, "Kütüphane Toplu", false);
                    }
                    
                    if (CANCEL_EPOCH.get() == epoch) {
                        Utils.runOnMainThread(() -> Utils.showToastShort("Kütüphane indirmesi tamamlandı! (" + totalCount + " şarkı)"));
                    }
                } catch (Exception ex) {
                    Logger.printException(() -> "Library download failed", ex);
                    Utils.runOnMainThread(() -> Utils.showToastShort("Kütüphane indirilirken hata oluştu."));
                } finally {
                    ACTIVE_PLAYLIST_JOBS.remove(jobKey);
                    notifyListeners("", true);
                }
            }
        });
    }

    public static void fetchAndDownloadPlaylist(File musicRoot, String playlistName, String rawInput) {
        fetchAndDownloadPlaylist(musicRoot, playlistName, rawInput, 0, 0);
    }

    public static void fetchAndDownloadPlaylist(File musicRoot, String playlistName, String rawInput, int startOneBased, int endOneBased) {
        final String jobKey = "fetch_" + playlistName.toLowerCase(Locale.ROOT) + "_" + startOneBased + "_" + endOneBased;
        if (!ACTIVE_PLAYLIST_JOBS.add(jobKey)) {
            Utils.runOnMainThread(() -> Utils.showToastShort("\"" + playlistName + "\" zaten kuyrukta!"));
            return;
        }
        final int epoch = CANCEL_EPOCH.get();
        final File fMusicRoot = musicRoot;
        final String fPlaylistName = playlistName;
        final String fRawInput = rawInput;
        final int fStart = startOneBased;
        final int fEnd = endOneBased;

        PLAYLIST_QUEUE_EXECUTOR.submit(new Runnable() {
            @Override public void run() {
                try {
                    if (CANCEL_EPOCH.get() != epoch) return;
                    int maxNeeded = fEnd > 0 ? fEnd : Integer.MAX_VALUE;
                    List<String> allResolved = resolvePlaylistOrTrackIds(fRawInput, maxNeeded);
                    if (CANCEL_EPOCH.get() != epoch) return;
                    if (allResolved.isEmpty()) {
                        Utils.runOnMainThread(() -> Utils.showToastShort("Şarkı bulunamadı. Gizli listeler için oturum açık olmalı."));
                        return;
                    }
                    int fromIndex = fStart > 0 ? Math.max(0, fStart - 1) : 0;
                    int toIndex = fEnd > 0 ? Math.min(allResolved.size(), fEnd) : allResolved.size();
                    if (fromIndex >= allResolved.size() || fromIndex >= toIndex) {
                        fromIndex = 0;
                        toIndex = allResolved.size();
                    }
                    final int uiFromIndex = fromIndex;
                    final int uiToIndex = toIndex;
                    List<String> resolvedIds = new ArrayList<>(allResolved.subList(fromIndex, toIndex));
                    OfflineStorage.createPlaylist(fMusicRoot, fPlaylistName, fRawInput);
                    OfflineStorage.addTracksToPlaylist(fMusicRoot, fPlaylistName, resolvedIds);
                    List<String> toDownload = new ArrayList<>();
                    for (final String id : resolvedIds) {
                        if (!OfflineStorage.isTrackDownloaded(fMusicRoot, id) && !QUEUE_MAP.containsKey(id)) {
                            CANCELLED_IDS.remove(id);
                            putQueueItem(id, id, fPlaylistName, 0, false, 0L, 0L, 0L);
                            toDownload.add(id);
                        }
                    }
                    saveQueuePersist();
                    notifyListeners("", false);
                    if (toDownload.isEmpty()) {
                        Utils.runOnMainThread(() -> Utils.showToastShort("\"" + fPlaylistName + "\" listesindeki seçili şarkılar zaten indirilmiş!"));
                        return;
                    }
                    final String rangeInfo = (fStart > 0 || fEnd > 0)
                            ? " (" + (uiFromIndex + 1) + "-" + uiToIndex + ". aralıkta " + toDownload.size() + " yeni şarkı)"
                            : " (" + toDownload.size() + " şarkı)";
                    Utils.runOnMainThread(() -> Utils.showToastShort("\"" + fPlaylistName + "\"" + rangeInfo + " kuyruğa alındı..."));
                    int count = 0;
                    for (final String id : toDownload) {
                        if (CANCEL_EPOCH.get() != epoch) return;
                        if (CANCELLED_IDS.remove(id)) { QUEUE_MAP.remove(id); continue; }
                        awaitIfPaused(epoch, id);
                        count++;
                        final int index = count;
                        final int total = toDownload.size();
                        showDownloadNotification("playlist_sync_" + fPlaylistName, fPlaylistName + " (" + index + "/" + total + ")",
                                (index * 100) / total, true, "Oynatma listesi indiriliyor...");
                        downloadBlocking(epoch, id, null, null, null, 0, null, fPlaylistName, false);
                    }
                    if (CANCEL_EPOCH.get() == epoch) {
                        showDownloadNotification("playlist_sync_" + fPlaylistName, fPlaylistName, 100, false, toDownload.size() + " şarkı başarıyla indirildi");
                        Utils.runOnMainThread(() -> Utils.showToastShort("\"" + fPlaylistName + "\" listesindeki " + toDownload.size() + " şarkı indirildi!"));
                    }
                } catch (InterruptedException ignored) {
                } catch (Exception ex) {
                    Logger.printException(() -> "Playlist auto-download failed: " + fRawInput, ex);
                    Utils.runOnMainThread(() -> Utils.showToastShort("Playlist indirilirken hata oluştu"));
                } finally {
                    ACTIVE_PLAYLIST_JOBS.remove(jobKey);
                    notifyListeners("", true);
                }
            }
        });
    }

    private static List<String> resolvePlaylistOrTrackIds(String input, int maxLimit) {
        Set<String> ids = new LinkedHashSet<>();
        if (input == null || input.isBlank()) return new ArrayList<>();
        String trimmed = input.trim();
        Matcher directMatcher = DIRECT_VIDEO_ID.matcher(trimmed);
        while (directMatcher.find()) ids.add(directMatcher.group(1));
        String listId = extractPlaylistId(trimmed);
        if (listId != null) {
            boolean isLikedList = listId.equalsIgnoreCase("LM") || listId.equalsIgnoreCase("VLLM") || listId.equalsIgnoreCase("LL") || listId.equalsIgnoreCase("VLLL") || listId.equalsIgnoreCase("FEmusic_liked_videos");
            if (isLikedList) {
                fetchInnerTubeBrowseIds("VLLM", ids, maxLimit);
                if (ids.size() < maxLimit) fetchInnerTubeBrowseIds("FEmusic_liked_videos", ids, maxLimit);
                if (ids.size() < maxLimit) fetchInnerTubeBrowseIds("VLLL", ids, maxLimit);
                if (ids.isEmpty()) fetchRemotePlaylistWebPage("LL", true, ids);
                if (ids.isEmpty()) fetchRemotePlaylistWebPage("LM", true, ids);
            } else {
                String browseId = listId.startsWith("VL") ? listId : "VL" + listId;
                fetchInnerTubeBrowseIds(browseId, ids, maxLimit);
                if (ids.isEmpty()) fetchRemotePlaylistWebPage(listId, false, ids);
            }
            if (ids.isEmpty() && isLikedList) {
                String activeId = VideoInformation.getVideoId();
                if (activeId != null && activeId.matches("[A-Za-z0-9_-]{11}")) ids.add(activeId);
            }
        }
        if (ids.isEmpty()) {
            String[] tokens = trimmed.split("[\\s,;]+");
            for (String token : tokens) if (token.matches("[A-Za-z0-9_-]{11}")) ids.add(token);
        }
        return new ArrayList<>(ids);
    }

    @Nullable
    private static String extractPlaylistId(String trimmed) {
        Matcher listMatcher = PLAYLIST_PARAM.matcher(trimmed);
        if (listMatcher.find()) {
            String raw = listMatcher.group(1);
            return raw != null && raw.startsWith("VL") && raw.length() > 4 ? raw.substring(2) : raw;
        }
        Matcher browseMatcher = BROWSE_PLAYLIST.matcher(trimmed);
        if (browseMatcher.find()) {
            String raw = browseMatcher.group(1);
            return raw != null && raw.startsWith("VL") && raw.length() > 4 ? raw.substring(2) : raw;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.equals("LM") || upper.equals("VLLM") || upper.equals("LL") || upper.equals("VLLL") || upper.equals("FEMUSIC_LIKED_VIDEOS")) return upper;
        if (trimmed.startsWith("VL") && trimmed.length() > 4 && !trimmed.contains(" ")) return trimmed.substring(2);
        if ((trimmed.startsWith("PL") || trimmed.startsWith("OLAK") || trimmed.startsWith("RD") || trimmed.startsWith("UU") || trimmed.startsWith("FL")) && !trimmed.contains(" ") && trimmed.length() >= 6) return trimmed;
        return null;
    }

    private static void fetchInnerTubeBrowseIds(String browseId, Set<String> outIds, int maxLimit) {
        try {
            String continuation = null;
            for (int page = 0; page < MAX_CONTINUATION_PAGES; page++) {
                if (outIds.size() >= maxLimit) break;
                String endpoint = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false";
                String payload = (page == 0)
                        ? "{\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"1.20260101.01.00\",\"hl\":\"tr\",\"gl\":\"TR\"}},\"browseId\":\"" + browseId + "\"}"
                        : "{\"context\":{\"client\":{\"clientName\":\"WEB_REMIX\",\"clientVersion\":\"1.20260101.01.00\",\"hl\":\"tr\",\"gl\":\"TR\"}},\"continuation\":\"" + continuation + "\"}";
                String response = postInnerTubeJson(endpoint, payload);
                if (response == null || response.isEmpty()) break;
                int beforeCount = outIds.size();
                Matcher m = JSON_VIDEO_ID.matcher(response);
                while (m.find()) outIds.add(m.group(1));
                if (outIds.size() >= maxLimit) break;
                Matcher contMatcher = CONTINUATION_TOKEN.matcher(response);
                String nextToken = null;
                while (contMatcher.find()) {
                    String candidate = contMatcher.group(1);
                    if (!candidate.equals(continuation)) { nextToken = candidate; break; }
                }
                if (nextToken == null || outIds.size() == beforeCount) break;
                continuation = nextToken;
            }
        } catch (Exception ex) {
            Logger.printDebug(() -> "InnerTube browse resolution fallback: " + browseId, ex);
        }
    }

    @Nullable
    private static String postInnerTubeJson(String url, String jsonPayload) {
        HttpURLConnection conn = null;
        try {
            conn = Requester.openConnection(url);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MILLISECONDS);
            conn.setReadTimeout(READ_TIMEOUT_MILLISECONDS);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", WEB_USER_AGENT);
            conn.setRequestProperty("Origin", "https://music.youtube.com");
            conn.setRequestProperty("Referer", "https://music.youtube.com/");
            conn.setRequestProperty("X-YouTube-Client-Name", "67");
            conn.setRequestProperty("X-YouTube-Client-Version", "1.20260101.01.00");
            applySessionCookiesAndAuth(conn, "https://music.youtube.com");
            try (OutputStream os = conn.getOutputStream()) { os.write(jsonPayload.getBytes(StandardCharsets.UTF_8)); }
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            return readStreamToString(conn.getInputStream());
        } catch (Exception ex) {
            Logger.printDebug(() -> "postInnerTubeJson failed", ex);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void fetchRemotePlaylistWebPage(String listId, boolean musicDomain, Set<String> ids) {
        HttpURLConnection conn = null;
        try {
            String base = musicDomain ? "https://music.youtube.com/playlist?list=" : "https://www.youtube.com/playlist?list=";
            conn = Requester.openConnection(base + listId + "&hl=en");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MILLISECONDS);
            conn.setReadTimeout(READ_TIMEOUT_MILLISECONDS);
            conn.setRequestProperty("User-Agent", WEB_USER_AGENT);
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            applySessionCookiesAndAuth(conn, musicDomain ? "https://music.youtube.com" : "https://www.youtube.com");
            String html = readStreamToString(conn.getInputStream());
            Matcher m = JSON_VIDEO_ID.matcher(html);
            while (m.find()) ids.add(m.group(1));
        } catch (Exception ex) {
            Logger.printDebug(() -> "Could not fetch remote playlist page: " + listId, ex);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void applySessionCookiesAndAuth(HttpURLConnection conn, String origin) {
        try {
            CookieManager cm = CookieManager.getInstance();
            if (cm == null) return;
            String cookies = cm.getCookie(origin);
            if (cookies == null || cookies.isBlank()) cookies = cm.getCookie("https://www.youtube.com");
            if (cookies != null && !cookies.isBlank()) {
                conn.setRequestProperty("Cookie", cookies);
                Matcher sapisid = SAPISID_PATTERN.matcher(cookies);
                if (sapisid.find()) {
                    long timestamp = System.currentTimeMillis() / 1000L;
                    String raw = timestamp + " " + sapisid.group(1) + " " + origin;
                    MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                    byte[] digest = sha1.digest(raw.getBytes(StandardCharsets.UTF_8));
                    StringBuilder hex = new StringBuilder();
                    for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b));
                    conn.setRequestProperty("Authorization", "SAPISIDHASH " + timestamp + "_" + hex);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static String readStreamToString(InputStream in) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream buffer = new ByteArrayOutputStream(32768)) {
            byte[] chunk = new byte[16384];
            int read;
            while ((read = input.read(chunk)) != -1) buffer.write(chunk, 0, read);
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void downloadBlocking(int epoch, String videoId, @Nullable String title, @Nullable String artist,
                                         @Nullable String album, int duration, @Nullable Bitmap artwork,
                                         @Nullable String targetPlaylistName, boolean showToasts) {
        if (CANCEL_EPOCH.get() != epoch || CANCELLED_IDS.remove(videoId)) {
            QUEUE_MAP.remove(videoId);
            saveQueuePersist();
            notifyListeners(videoId, false);
            return;
        }

        Context context = Utils.getContext();
        File directory = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Morphe");

        if (targetPlaylistName != null && !targetPlaylistName.isBlank()) OfflineStorage.addTrackToPlaylist(directory, targetPlaylistName, videoId);
        if (OfflineStorage.isTrackDownloaded(directory, videoId)) {
            QUEUE_MAP.remove(videoId);
            saveQueuePersist();
            if (showToasts) Utils.showToastShort(str("morphe_music_downloads_already_saved"));
            notifyListeners(videoId, true);
            return;
        }

        if (!ACTIVE_DOWNLOADS.add(videoId)) {
            if (showToasts) Utils.showToastShort(str("morphe_music_downloads_already_running"));
            return;
        }

        putQueueItem(videoId, title, targetPlaylistName, 1, true, 0L, 0L, 0L);
        notifyListeners(videoId, false);
        File temporary = null;
        String finalTitle = title;
        String finalArtist = artist;

        try {
            awaitIfPaused(epoch, videoId);
            ResolvedStream resolved = resolveStream(videoId);
            if (resolved == null || resolved.format().getUrl().isBlank()) throw new IllegalStateException("Audio stream unavailable");

            Format format = resolved.format();
            VideoDetails details = resolved.details();
            finalTitle = firstNotBlank(finalTitle, details.getTitle(), videoId);
            finalArtist = firstNotBlank(finalArtist, stripTopic(details.getOwnerChannelName()), "");
            if (duration <= 0) duration = details.getLengthSeconds();
            if (artwork == null) artwork = resolveArtwork(videoId, details);
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Could not create directory");

            putQueueItem(videoId, finalTitle, targetPlaylistName, 5, true, 0L, format.getContentLength(), 0L);
            notifyListeners(videoId, false);

            String extension = format.getMimeType().contains("mp4") ? ".m4a" : ".webm";
            File destination = new File(directory, videoId + extension);
            temporary = new File(directory, destination.getName() + ".part");

            if (temporary.exists() && !temporary.delete()) throw new IllegalStateException("Could not reset partial");
            downloadWithResume(epoch, videoId, finalTitle, targetPlaylistName, format.getUrl(), temporary, format.getContentLength(), showToasts);

            if (CANCEL_EPOCH.get() != epoch || CANCELLED_IDS.contains(videoId)) {
                OfflineStorage.delete(temporary);
                return;
            }
            if (destination.exists() && !destination.delete()) throw new IllegalStateException("Could not replace audio");
            if (!temporary.renameTo(destination)) throw new IllegalStateException("Could not finish audio");

            OfflineTrack.save(directory, videoId, finalTitle, finalArtist, album, duration, artwork);
            
            // YENİ: Meta verileri YT Music'ten çek ve OfflineTrack'e kaydet
            JSONObject ytMeta = tryFetchYtMusicMetadata(videoId);
            if (ytMeta != null) {
                OfflineTrack tr = OfflineTrack.load(destination);
                tr.setExtendedMetadata(
                        ytMeta.optString("year", ""),
                        ytMeta.optString("genre", ""),
                        ytMeta.optString("label", ""),
                        ytMeta.optString("credits", "")
                );
            }

            fetchAndSaveSyncedLyrics(directory, videoId, finalTitle, finalArtist, duration);
            COMPLETED_SESSION_COUNT.incrementAndGet();
            FAILED_MAP.remove(videoId);
            if (showToasts) Utils.showToastShort(str("morphe_music_downloads_complete"));
        } catch (InterruptedException cancelledEx) {
            if (temporary != null) OfflineStorage.delete(temporary);
        } catch (Exception ex) {
            if (temporary != null) OfflineStorage.delete(temporary);
            if (CANCEL_EPOCH.get() == epoch && !CANCELLED_IDS.contains(videoId)) {
                String failedTitle = finalTitle != null && !finalTitle.isBlank() ? finalTitle : videoId;
                FAILED_MAP.put(videoId, new QueueItem(videoId, failedTitle, targetPlaylistName != null ? targetPlaylistName : "Tekli İndirme", 0, false, 0L, 0L, 0L, true));
                Logger.printException(() -> "Local audio download failed: " + videoId, ex);
                awaitNotificationSlot();
                showDownloadNotification(videoId, finalTitle, 0, false, str("morphe_music_downloads_failed"));
                if (showToasts) Utils.showToastShort(str("morphe_music_downloads_failed"));
            }
        } finally {
            ACTIVE_DOWNLOADS.remove(videoId);
            QUEUE_MAP.remove(videoId);
            CANCELLED_IDS.remove(videoId);
            saveQueuePersist();
            notifyListeners(videoId, true);
        }
    }

    private record ResolvedStream(Format format, VideoDetails details) {}

    private static String firstNotBlank(@Nullable String value, String fallback, String lastResort) {
        if (value != null && !value.isBlank()) return value;
        if (fallback != null && !fallback.isBlank()) return fallback;
        return lastResort;
    }

    private static String stripTopic(String channelName) {
        if (channelName == null) return "";
        return channelName.endsWith(" - Topic") ? channelName.substring(0, channelName.length() - " - Topic".length()) : channelName;
    }

    private static boolean canPostProgress() {
        long now = System.currentTimeMillis();
        long previous = lastNotificationTime.get();
        return now - previous >= NOTIFICATION_INTERVAL_MILLISECONDS && lastNotificationTime.compareAndSet(previous, now);
    }

    private static void awaitNotificationSlot() {
        while (true) {
            long now = System.currentTimeMillis();
            long previous = lastNotificationTime.get();
            long remaining = NOTIFICATION_INTERVAL_MILLISECONDS - (now - previous);
            if (remaining <= 0) {
                if (lastNotificationTime.compareAndSet(previous, now)) return;
                continue;
            }
            try { Thread.sleep(remaining); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
        }
    }

    @Nullable
    private static Bitmap resolveArtwork(String videoId, VideoDetails details) {
        Bitmap artwork = downloadArtwork(largestThumbnail(details));
        if (artwork != null) return artwork;
        artwork = downloadArtwork(THUMBNAIL_URL + videoId + "/maxresdefault.jpg");
        return artwork != null ? artwork : downloadArtwork(THUMBNAIL_URL + videoId + "/hqdefault.jpg");
    }

    @Nullable
    private static String largestThumbnail(VideoDetails details) {
        String url = null; int widest = 0;
        for (ThumbnailEntry entry : details.getThumbnail().getThumbnailsList()) {
            if (entry.getWidth() >= widest && !entry.getUrl().isBlank()) {
                widest = entry.getWidth(); url = entry.getUrl();
            }
        }
        return url;
    }

    @Nullable
    private static Bitmap downloadArtwork(@Nullable String url) {
        if (url == null || url.isBlank()) return null;
        try (InputStream input = Requester.openConnection(url).getInputStream()) {
            return BitmapFactory.decodeStream(input);
        } catch (Exception ex) {
            Logger.printDebug(() -> "Could not fetch artwork: " + url, ex);
            return null;
        }
    }

    private static void downloadWithResume(int epoch, String videoId, String title, @Nullable String playlistName,
                                           String url, File temporary, long expectedLength, boolean showToasts) throws Exception {
        if (showToasts) Utils.showToastShort(str("morphe_music_downloads_started"));
        awaitNotificationSlot();
        showDownloadNotification(videoId, title, 0, true, str("morphe_music_downloads_starting"));

        if (expectedLength <= 0) expectedLength = probeContentLength(url);
        final long totalLength = expectedLength;

        if (totalLength <= 0) {
            downloadUnknownLength(epoch, videoId, url, temporary);
        } else {
            downloadInParallel(epoch, videoId, title, playlistName, url, temporary, totalLength);
        }

        if (CANCEL_EPOCH.get() != epoch || CANCELLED_IDS.contains(videoId)) throw new InterruptedException("Download cancelled");
        if (totalLength > 0 && temporary.length() != totalLength) throw new IllegalStateException("Incomplete audio file");

        awaitNotificationSlot();
        showDownloadNotification(videoId, title, 100, false, str("morphe_music_downloads_complete"));
    }

    private static void downloadInParallel(final int epoch, final String videoId, final String title, @Nullable final String playlistName,
                                           final String url, final File temporary, final long totalLength) throws Exception {
        try (RandomAccessFile file = new RandomAccessFile(temporary, "rw")) { file.setLength(totalLength); }
        final long startTimeMs = System.currentTimeMillis();
        final AtomicLong downloaded = new AtomicLong();
        long segmentSize = (totalLength + SEGMENT_COUNT - 1) / SEGMENT_COUNT;

        List<Future<?>> futures = new ArrayList<>(SEGMENT_COUNT);
        for (int index = 0; index < SEGMENT_COUNT; index++) {
            final long start = index * segmentSize;
            final long end = Math.min(totalLength - 1, start + segmentSize - 1);
            if (start > end) continue;

            futures.add(SEGMENT_POOL.submit(new Runnable() {
                @Override public void run() {
                    try {
                        downloadRange(epoch, videoId, url, temporary, start, end, downloaded, new ProgressCallback() {
                            @Override public void update(long bytes) {
                                int progress = (int) Math.min(99, bytes * 100 / totalLength);
                                long elapsedMs = Math.max(1L, System.currentTimeMillis() - startTimeMs);
                                long speedBps = (bytes * 1000L) / elapsedMs;
                                putQueueItem(videoId, title, playlistName, progress, true, bytes, totalLength, speedBps);
                                if (!canPostProgress()) return;
                                notifyListeners(videoId, false);
                                showDownloadNotification(videoId, title, progress, true, str("morphe_music_downloads_progress", progress));
                            }
                        });
                    } catch (Exception ignored) {}
                }
            }));
        }

        for (Future<?> future : futures) future.get();
    }

    private interface ProgressCallback { void update(long bytes); }

    private static void downloadRange(int epoch, String videoId, String url, File destination, long start, long end,
                                      AtomicLong downloaded, ProgressCallback progress) throws Exception {
        long position = start;
        int failures = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        Context ctx = Utils.getContext();
        int rateLimitKbps = ctx != null ? LocalDownloadsUIState.getRateLimitKbps(ctx) : 0;
        long rateLimitBytesPerSec = rateLimitKbps > 0 ? (rateLimitKbps * 1024L) / SEGMENT_COUNT : 0L;
        long lastRateCheck = System.currentTimeMillis();
        long bytesInWindow = 0L;

        try (RandomAccessFile output = new RandomAccessFile(destination, "rw")) {
            while (position <= end) {
                if (CANCEL_EPOCH.get() != epoch || CANCELLED_IDS.contains(videoId) || Thread.currentThread().isInterrupted())
                    throw new InterruptedException("Download cancelled");
                awaitIfPaused(epoch, videoId);
                long before = position;
                HttpURLConnection connection = openAudioConnection(url, position, end);

                try {
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_PARTIAL)
                        throw new IllegalStateException("Range request returned HTTP " + connection.getResponseCode());
                    try (InputStream input = connection.getInputStream()) {
                        output.seek(position);
                        while (position <= end) {
                            if (CANCEL_EPOCH.get() != epoch || CANCELLED_IDS.contains(videoId))
                                throw new InterruptedException("Download cancelled");
                            if (PAUSED.get()) break;
                            int read = input.read(buffer, 0, (int) Math.min(buffer.length, end - position + 1));
                            if (read < 0) break;
                            output.write(buffer, 0, read);
                            position += read;
                            bytesInWindow += read;

                            if (rateLimitBytesPerSec > 0) {
                                long now = System.currentTimeMillis();
                                long elapsed = now - lastRateCheck;
                                if (elapsed >= RATE_CHECK_INTERVAL_MS) {
                                    long currentBps = bytesInWindow * 1000L / elapsed;
                                    if (currentBps > rateLimitBytesPerSec) {
                                        long sleepMs = (bytesInWindow * 1000L / rateLimitBytesPerSec) - elapsed;
                                        if (sleepMs > 0 && sleepMs < 2000L) {
                                            try { Thread.sleep(sleepMs); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                                        }
                                    }
                                    lastRateCheck = System.currentTimeMillis();
                                    bytesInWindow = 0L;
                                }
                            }

                            progress.update(downloaded.addAndGet(read));
                        }
                    }
                } catch (IOException ex) {
                    if (CANCEL_EPOCH.get() != epoch || CANCELLED_IDS.contains(videoId))
                        throw new InterruptedException("Download cancelled");
                } finally {
                    connection.disconnect();
                }

                if (position > before) {
                    failures = 0;
                } else {
                    failures++;
                    if (failures < MAX_STALLED_ATTEMPTS) {
                        long backoff = Math.min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * (1L << Math.min(6, failures - 1)));
                        try { Thread.sleep(backoff); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    }
                }
                if (failures >= MAX_STALLED_ATTEMPTS) throw new IllegalStateException("Download range stalled at " + position);
            }
        }
    }

    private static long probeContentLength(String url) {
        HttpURLConnection connection = null;
        try {
            connection = openAudioConnection(url, 0, 0);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_PARTIAL) return -1;
            String contentRange = connection.getHeaderField("Content-Range");
            if (contentRange == null) return -1;
            int slash = contentRange.lastIndexOf('/');
            return slash < 0 ? -1 : Long.parseLong(contentRange.substring(slash + 1));
        } catch (Exception ex) {
            Logger.printDebug(() -> "Could not probe audio length", ex);
            return -1;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static HttpURLConnection openAudioConnection(String url, long start, long end) throws Exception {
        HttpURLConnection connection = Requester.openConnection(url);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLISECONDS);
        connection.setReadTimeout(READ_TIMEOUT_MILLISECONDS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Referer", "https://music.youtube.com/");
        connection.setRequestProperty("Range", "bytes=" + start + "-" + end);
        return connection;
    }

    private static void downloadUnknownLength(int epoch, String videoId, String url, File destination) throws Exception {
        HttpURLConnection connection = Requester.openConnection(url);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLISECONDS);
        connection.setReadTimeout(READ_TIMEOUT_MILLISECONDS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IllegalStateException("HTTP " + connection.getResponseCode());
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (CANCEL_EPOCH.get() != epoch || CANCELLED_IDS.contains(videoId)) throw new InterruptedException("Download cancelled");
                    awaitIfPaused(epoch, videoId);
                    output.write(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    static void showDownloadNotification(String key, String title, int progress, boolean ongoing, String text) {
        Utils.runOnMainThread(() -> postDownloadNotification(key, title, progress, ongoing, text));
    }

    @SuppressLint("NotificationPermission")
    private static void postDownloadNotification(String key, String title, int progress, boolean ongoing, String text) {
        Context context = Utils.getContext();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(DOWNLOAD_CHANNEL, str("morphe_music_downloads_notification_channel"), NotificationManager.IMPORTANCE_LOW));
        Notification.Builder builder = new Notification.Builder(context, DOWNLOAD_CHANNEL);
        builder.setSmallIcon(ongoing ? android.R.drawable.stat_sys_download : android.R.drawable.stat_sys_download_done)
                .setContentTitle(title == null || title.isBlank() ? "YouTube Music" : title)
                .setContentText(text)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true);
        if (ongoing) builder.setProgress(100, Math.max(0, progress), progress < 0);
        manager.notify(key.hashCode(), builder.build());
    }

    @Nullable
    private static ResolvedStream resolveStream(String videoId) {
        StreamingDataRequest request = StreamingDataRequest.getRequestForVideoId(videoId);
        if (request == null) {
            Logger.printDebug(() -> "No cached streaming request for download, resolving: " + videoId);
            request = StreamingDataRequest.fetchRequestForDownload(videoId, SpoofVideoStreamsPatch.getAvailableClients(), Settings.SPOOF_VIDEO_STREAMS_CLIENT_TYPE.get());
        }
        StreamingDataRequest.StreamData stream = request.getStream();
        if (stream == null) return null;
        try {
            PlayerResponse response = PlayerResponse.parseFrom(stream.streamingData());
            Format best = response.getStreamingData().getAdaptiveFormatsList().stream()
                    .filter(format -> format.getMimeType().startsWith("audio/") && !format.getUrl().isBlank())
                    .max(Comparator.comparingInt(Format::getBitrate)).orElse(null);
            return best == null ? null : new ResolvedStream(best, response.getVideoDetails());
        } catch (Exception ex) {
            Logger.printException(() -> "Could not parse audio formats: " + videoId, ex);
            return null;
        }
    }
}