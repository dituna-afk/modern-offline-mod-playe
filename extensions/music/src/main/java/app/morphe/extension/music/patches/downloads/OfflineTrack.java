package app.morphe.extension.music.patches.downloads;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.shared.Logger;

public final class OfflineTrack {

    private static final Pattern LRC_TIMESTAMP =
            Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)");

    public record LyricLine(long timeMs, String text) {}

    private final File audioFile;
    private final String videoId;

    private String title;
    private String artist;
    private String album;
    private int durationSeconds;
    private long lastPlayedAt;
    private int playCount;
    private int nightPlayCount;
    
    // YENİ: Zengin Meta Veriler
    private String releaseYear;
    private String genre;
    private String label;
    private String credits;

    private OfflineTrack(File audioFile) {
        this.audioFile = audioFile;
        String name = audioFile.getName();
        int dot = name.lastIndexOf('.');
        this.videoId = dot > 0 ? name.substring(0, dot) : name;
        this.title = videoId;
        this.artist = "";
        this.album = "";
        this.durationSeconds = 0;
        this.releaseYear = "";
        this.genre = "";
        this.label = "";
        this.credits = "";
    }

    public static OfflineTrack load(File audioFile) {
        OfflineTrack track = new OfflineTrack(audioFile);
        track.readMetadata();
        return track;
    }

    private File metadataFile() {
        return new File(audioFile.getParentFile(), videoId + ".json");
    }

    private void readMetadata() {
        try {
            File f = metadataFile();
            if (!f.isFile()) return;
            String raw = OfflineStorage.readText(f);
            if (raw == null || raw.isBlank()) return;
            JSONObject json = new JSONObject(raw);
            title = json.optString("title", videoId);
            artist = json.optString("artist", "");
            album = json.optString("album", "");
            durationSeconds = json.optInt("duration", 0);
            lastPlayedAt = json.optLong("lastPlayedAt", 0L);
            playCount = json.optInt("playCount", 0);
            nightPlayCount = json.optInt("nightPlayCount", 0);
            
            // Yeni alanlar
            releaseYear = json.optString("releaseYear", "");
            genre = json.optString("genre", "");
            label = json.optString("label", "");
            credits = json.optString("credits", "");
        } catch (Exception ex) {
            Logger.printDebug(() -> "Could not read track metadata: " + videoId, ex);
        }
    }

    private void writeMetadata() {
        try {
            JSONObject json = new JSONObject();
            json.put("videoId", videoId);
            json.put("title", title == null ? videoId : title);
            json.put("artist", artist == null ? "" : artist);
            json.put("album", album == null ? "" : album);
            json.put("duration", durationSeconds);
            json.put("lastPlayedAt", lastPlayedAt);
            json.put("playCount", playCount);
            json.put("nightPlayCount", nightPlayCount);
            
            // Yeni alanlar
            json.put("releaseYear", releaseYear == null ? "" : releaseYear);
            json.put("genre", genre == null ? "" : genre);
            json.put("label", label == null ? "" : label);
            json.put("credits", credits == null ? "" : credits);
            
            OfflineStorage.writeText(metadataFile(), json.toString());
        } catch (Exception ex) {
            Logger.printException(() -> "Could not write track metadata: " + videoId, ex);
        }
    }

    public static void save(File directory, String videoId, String title, String artist,
                            String album, int duration, @Nullable Bitmap artwork) {
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) return;
            File audio = null;
            for (String ext : new String[]{".webm", ".m4a"}) {
                File candidate = new File(directory, videoId + ext);
                if (candidate.isFile()) {
                    audio = candidate;
                    break;
                }
            }
            if (audio == null) return;

            OfflineTrack track = new OfflineTrack(audio);
            track.title = title == null ? videoId : title;
            track.artist = artist == null ? "" : artist;
            track.album = album == null ? "" : album;
            track.durationSeconds = duration;
            track.writeMetadata();

            if (artwork != null) {
                File artFile = new File(directory, videoId + ".jpg");
                OfflineStorage.writeJpeg(artFile, artwork);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Could not save track metadata: " + videoId, ex);
        }
    }

    // YENİ: YT Music'ten çekilen zengin meta verileri kaydeder
    public void setExtendedMetadata(String year, String genre, String label, String credits) {
        this.releaseYear = year == null ? "" : year;
        this.genre = genre == null ? "" : genre;
        this.label = label == null ? "" : label;
        this.credits = credits == null ? "" : credits;
        writeMetadata();
    }

    public static void recordPlayEvent(File audioFile, boolean isNight) {
        try {
            if (audioFile == null || !audioFile.isFile()) return;
            OfflineTrack track = OfflineTrack.load(audioFile);
            track.playCount++;
            if (isNight) track.nightPlayCount++;
            track.lastPlayedAt = System.currentTimeMillis();
            track.writeMetadata();
        } catch (Exception ignored) {}
    }

    public boolean updateMetadata(File musicRoot, String newTitle, String newArtist, String newAlbum) {
        try {
            this.title = newTitle == null || newTitle.isBlank() ? videoId : newTitle;
            this.artist = newArtist == null ? "" : newArtist;
            this.album = newAlbum == null ? "" : newAlbum;
            writeMetadata();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public File audioFile() { return audioFile; }
    public String videoId() { return videoId; }
    public String title() { return title == null || title.isBlank() ? videoId : title; }
    public String displayTitle() { return title(); }
    public String artist() { return artist == null ? "" : artist; }
    public String displayArtist() { return artist == null || artist.isBlank() ? "Bilinmeyen Sanatçı" : artist; }
    public String album() { return album == null ? "" : album; }
    public String displayAlbum() { return album == null || album.isBlank() ? "Tekli" : album; }
    public int durationSeconds() { return durationSeconds; }
    public long lastPlayedAt() { return lastPlayedAt; }
    public int playCount() { return playCount; }
    public int nightPlayCount() { return nightPlayCount; }
    
    // Yeni alanların getter'ları
    public String releaseYear() { return releaseYear == null || releaseYear.isBlank() ? "Bilinmiyor" : releaseYear; }
    public String genre() { return genre == null || genre.isBlank() ? "Bilinmiyor" : genre; }
    public String label() { return label == null || label.isBlank() ? "Bilinmiyor" : label; }
    public String credits() { return credits == null || credits.isBlank() ? "Bilinmiyor" : credits; }

    public String formattedDuration() {
        if (durationSeconds <= 0) return "--:--";
        return String.format(Locale.ROOT, "%d:%02d", durationSeconds / 60, durationSeconds % 60);
    }

    public File artworkFile() {
        File custom = new File(audioFile.getParentFile(), videoId + "_custom.jpg");
        if (custom.isFile()) return custom;
        return new File(audioFile.getParentFile(), videoId + ".jpg");
    }

    public File lrcFile() {
        return new File(audioFile.getParentFile(), videoId + ".lrc");
    }

    @Nullable
    public Bitmap artwork(int maxSize) {
        File f = artworkFile();
        if (!f.isFile()) return null;
        return OfflineStorage.decodeArtwork(f, maxSize);
    }

    public int extractDominantColor(int fallback) {
        try {
            File f = artworkFile();
            if (!f.isFile()) return fallback;
            Bitmap bmp = OfflineStorage.decodeArtwork(f, 64);
            if (bmp == null) return fallback;
            int w = bmp.getWidth(), h = bmp.getHeight();
            if (w <= 0 || h <= 0) return fallback;
            long r = 0, g = 0, b = 0, count = 0;
            int stepX = Math.max(1, w / 8);
            int stepY = Math.max(1, h / 8);
            for (int y = 0; y < h; y += stepY) {
                for (int x = 0; x < w; x += stepX) {
                    int p = bmp.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    r += Color.red(p);
                    g += Color.green(p);
                    b += Color.blue(p);
                    count++;
                }
            }
            if (count == 0) return fallback;
            return Color.rgb((int) (r / count), (int) (g / count), (int) (b / count));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public List<LyricLine> loadSynchronizedLyrics() {
        List<LyricLine> out = new ArrayList<>();
        try {
            File f = lrcFile();
            if (!f.isFile()) return out;
            String raw = OfflineStorage.readText(f);
            if (raw == null || raw.isBlank()) return out;

            long fallbackTime = 0L;
            for (String line : raw.split("\\r?\\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.equals("[TR_NATIVE_POEM_OR_SONG]")) continue;

                Matcher m = LRC_TIMESTAMP.matcher(trimmed);
                if (m.matches()) {
                    int minutes = Integer.parseInt(m.group(1));
                    int seconds = Integer.parseInt(m.group(2));
                    String frac = m.group(3);
                    int millis = frac.length() == 2 ? Integer.parseInt(frac) * 10 : Integer.parseInt(frac);
                    long timeMs = minutes * 60_000L + seconds * 1_000L + millis;
                    String text = m.group(4).trim();
                    if (!text.isEmpty()) {
                        out.add(new LyricLine(timeMs, text));
                    }
                } else if (!trimmed.startsWith("[")) {
                    fallbackTime += 4000L;
                    out.add(new LyricLine(fallbackTime, trimmed));
                }
            }
        } catch (Exception ex) {
            Logger.printDebug(() -> "Could not load lyrics for: " + videoId, ex);
        }
        return out;
    }

    public String displaySize() {
        long len = audioFile.length();
        return String.format(Locale.ROOT, "%.1f MB", len / 1_048_576.0);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof OfflineTrack other)) return false;
        return audioFile.getAbsolutePath().equals(other.audioFile.getAbsolutePath());
    }

    @Override
    public int hashCode() {
        return audioFile.getAbsolutePath().hashCode();
    }
}