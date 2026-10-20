package app.morphe.extension.music.patches.downloads;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.shared.Logger;

/**
 * Dosya sistemi, playlist yönetimi, akıllı listeler ve sanatçı kartları için
 * merkezi depolama sınıfı.
 *
 * <p>Playlistler {@code musicRoot/playlists.json} içinde saklanır. Akıllı
 * listeler ({@link #buildSmartPlaylists}) hiçbir zaman diske yazılmaz; her
 * çağrıldığında mevcut {@link OfflineTrack} verilerinden dinamik olarak
 * hesaplanır. "Tüm Şarkılar" da aynı şekilde dinamik üretilir.</p>
 */
public final class OfflineStorage {

    public static final String SPECIAL_ALL_SONGS = "Tüm Şarkılar";
    public static final String SPECIAL_LIKED_SONGS = "Beğenilenler";

    public static final String SMART_MOST_PLAYED = "smart_most_played";
    public static final String SMART_RECENTLY_ADDED = "smart_recently_added";
    public static final String SMART_NIGHT_PLAYS = "smart_night_plays";
    public static final String SMART_NEVER_PLAYED = "smart_never_played";

    private static final String PLAYLISTS_FILE = "playlists.json";
    private static final String JSON_PLAYLISTS_ARRAY = "playlists";
    private static final String JSON_NAME = "name";
    private static final String JSON_REMOTE_ID = "remoteId";
    private static final String JSON_COVER_PATH = "coverPath";
    private static final String JSON_VIDEO_IDS = "videoIds";

    /**
     * @param name       Görünen isim ("Tüm Şarkılar", "Beğenilenler", kullanıcı listeleri...)
     * @param remoteId   Uzak liste ID'si (YouTube Music/YT playlist id), boş olabilir
     * @param videoIds   Listedeki video ID'leri, sırayla
     * @param coverPath  Özel kapak dosyası yolu, boşsa varsayılan 4'lü kolaj kullanılır
     * @param isSmart    true ise bu dinamik hesaplanmış bir liste (diske yazılmaz)
     */
    public record Playlist(
            String name,
            String remoteId,
            List<String> videoIds,
            String coverPath,
            boolean isSmart
    ) {}

    private OfflineStorage() {}

    // =====================================================================================
    // DOSYA OKUMA / YAZMA / SİLME
    // =====================================================================================

    /** UTF-8 dosyayı okur. Dosya yoksa veya hata olursa null döner. */
    @Nullable
    public static String readText(File file) {
        if (file == null || !file.isFile()) return null;
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream(16384)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    /** UTF-8 metni dosyaya yazar. Hata durumunda sessizce loglar. */
    public static void writeText(File file, String text) {
        if (file == null || text == null) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(text.getBytes("UTF-8"));
            }
        } catch (Exception e) {
            Logger.printException(() -> "Could not write text: " + file, e);
        }
    }

    /** Bitmap'i JPEG olarak diske yazar (kalite %92). */
    public static void writeJpeg(File file, Bitmap bitmap) {
        if (file == null || bitmap == null) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out);
            }
        } catch (Exception e) {
            Logger.printException(() -> "Could not write JPEG: " + file, e);
        }
    }

    /** Belirtilen dosyayı siler. Dosya değilse veya yoksa sessizce geçer. */
    public static void delete(@Nullable File file) {
        if (file == null) return;
        try {
            if (file.isFile()) file.delete();
        } catch (Exception ignored) {}
    }

    /**
     * JPEG/PNG dosyasını örnekleme (inSampleSize) kullanarak yükler.
     * Bellek dostudur; büyük kapaklar için uygundur. {@code maxSize}'ı aşmayacak
     * şekilde 2'nin kuvveti ile küçültme yapar.
     */
    @Nullable
    public static Bitmap decodeArtwork(@Nullable File file, int maxSize) {
        if (file == null || !file.isFile() || maxSize <= 0) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int w = bounds.outWidth, h = bounds.outHeight;
            if (w <= 0 || h <= 0) return null;

            int sample = 1;
            while ((w / sample) > maxSize && (h / sample) > maxSize) {
                sample *= 2;
            }

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Exception e) {
            return null;
        }
    }

    // =====================================================================================
    // PLAYLIST YÖNETİMİ (JSON KALICILIĞI)
    // =====================================================================================

    /**
     * Diskteki tüm kullanıcı playlistlerini yükler. "Tüm Şarkılar" ve akıllı
     * listeler burada dönmez; onlar {@link #buildPrimaryLibraryGrid} ve
     * {@link #buildSmartPlaylists} ile dinamik üretilir.
     */
    public static List<Playlist> loadPlaylists(@Nullable File musicRoot) {
        List<Playlist> result = new ArrayList<>();
        if (musicRoot == null) return result;
        File file = new File(musicRoot, PLAYLISTS_FILE);
        if (!file.isFile()) return result;

        try {
            String raw = readText(file);
            if (raw == null || raw.isBlank()) return result;
            JSONObject root = new JSONObject(raw);
            JSONArray arr = root.optJSONArray(JSON_PLAYLISTS_ARRAY);
            if (arr == null) return result;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;

                String name = o.optString(JSON_NAME, "");
                if (name.isBlank()) continue;
                String remoteId = o.optString(JSON_REMOTE_ID, "");
                String coverPath = o.optString(JSON_COVER_PATH, "");

                List<String> ids = new ArrayList<>();
                JSONArray idsArr = o.optJSONArray(JSON_VIDEO_IDS);
                if (idsArr != null) {
                    for (int j = 0; j < idsArr.length(); j++) {
                        String id = idsArr.optString(j, "");
                        if (!id.isBlank()) ids.add(id);
                    }
                }
                result.add(new Playlist(name, remoteId, ids, coverPath, false));
            }
        } catch (Exception e) {
            Logger.printException(() -> "Could not parse playlists.json", e);
        }
        return result;
    }

    /** Playlist listesini JSON'a yazar. Akıllı listeler atlanır. */
    private static void savePlaylists(@Nullable File musicRoot, List<Playlist> playlists) {
        if (musicRoot == null) return;
        try {
            if (!musicRoot.isDirectory() && !musicRoot.mkdirs()) return;
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (Playlist p : playlists) {
                if (p.isSmart()) continue;
                JSONObject o = new JSONObject();
                o.put(JSON_NAME, p.name());
                o.put(JSON_REMOTE_ID, p.remoteId() == null ? "" : p.remoteId());
                o.put(JSON_COVER_PATH, p.coverPath() == null ? "" : p.coverPath());
                JSONArray ids = new JSONArray();
                for (String id : p.videoIds()) ids.put(id);
                o.put(JSON_VIDEO_IDS, ids);
                arr.put(o);
            }
            root.put(JSON_PLAYLISTS_ARRAY, arr);
            writeText(new File(musicRoot, PLAYLISTS_FILE), root.toString());
        } catch (Exception e) {
            Logger.printException(() -> "Could not save playlists.json", e);
        }
    }

    /**
     * Listeyi oluşturur; zaten varsa {@code remoteId}'yi günceller. Liste
     * adları büyük/küçük harf duyarsızdır.
     */
    public static void createPlaylist(@Nullable File musicRoot, String name, String remoteId) {
        if (musicRoot == null || name == null || name.isBlank()) return;
        List<Playlist> all = loadPlaylists(musicRoot);

        for (int i = 0; i < all.size(); i++) {
            Playlist p = all.get(i);
            if (p.name().equalsIgnoreCase(name)) {
                String newRemote = remoteId == null ? "" : remoteId;
                if (!newRemote.equals(p.remoteId())) {
                    all.set(i, new Playlist(p.name(), newRemote, p.videoIds(), p.coverPath(), false));
                    savePlaylists(musicRoot, all);
                }
                return;
            }
        }

        all.add(new Playlist(name, remoteId == null ? "" : remoteId, new ArrayList<>(), "", false));
        savePlaylists(musicRoot, all);
    }

    /** Tek bir şarkıyı listeye ekler. Zaten varsa false döner. */
    public static boolean addTrackToPlaylist(@Nullable File musicRoot, String name, String videoId) {
        if (videoId == null || videoId.isBlank()) return false;
        List<String> wrapper = new ArrayList<>();
        wrapper.add(videoId);
        return addTracksToPlaylist(musicRoot, name, wrapper) > 0;
    }

    /** Birden çok şarkıyı ekler; eklenen yeni şarkı sayısını döner. */
    public static int addTracksToPlaylist(@Nullable File musicRoot, String name, List<String> videoIds) {
        if (musicRoot == null || name == null || name.isBlank()
                || videoIds == null || videoIds.isEmpty()) return 0;

        List<Playlist> all = loadPlaylists(musicRoot);
        int idx = -1;
        Playlist target = null;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).name().equalsIgnoreCase(name)) {
                target = all.get(i);
                idx = i;
                break;
            }
        }
        if (target == null) {
            target = new Playlist(name, "", new ArrayList<>(), "", false);
            all.add(target);
            idx = all.size() - 1;
        }

        List<String> existing = new ArrayList<>(target.videoIds());
        int added = 0;
        for (String id : videoIds) {
            if (id == null || id.isBlank()) continue;
            if (!existing.contains(id)) {
                existing.add(id);
                added++;
            }
        }
        if (added == 0) return 0;

        all.set(idx, new Playlist(target.name(), target.remoteId(), existing, target.coverPath(), false));
        savePlaylists(musicRoot, all);
        return added;
    }

    /** Şarkıyı listeden çıkarır. Listede yoksa false döner. */
    public static boolean removeTrackFromPlaylist(@Nullable File musicRoot, String name, String videoId) {
        if (musicRoot == null || name == null || videoId == null) return false;
        List<Playlist> all = loadPlaylists(musicRoot);
        for (int i = 0; i < all.size(); i++) {
            Playlist p = all.get(i);
            if (!p.name().equalsIgnoreCase(name)) continue;
            if (!p.videoIds().contains(videoId)) return false;

            List<String> ids = new ArrayList<>(p.videoIds());
            ids.remove(videoId);
            all.set(i, new Playlist(p.name(), p.remoteId(), ids, p.coverPath(), false));
            savePlaylists(musicRoot, all);
            return true;
        }
        return false;
    }

    /** Şarkıyı tüm kullanıcı listelerinden siler (dosya silme işlemi yapmaz). */
    public static void removeTrackFromAllPlaylists(@Nullable File musicRoot, String videoId) {
        if (musicRoot == null || videoId == null) return;
        List<Playlist> all = loadPlaylists(musicRoot);
        boolean changed = false;
        for (int i = 0; i < all.size(); i++) {
            Playlist p = all.get(i);
            if (!p.videoIds().contains(videoId)) continue;
            List<String> ids = new ArrayList<>(p.videoIds());
            ids.remove(videoId);
            all.set(i, new Playlist(p.name(), p.remoteId(), ids, p.coverPath(), false));
            changed = true;
        }
        if (changed) savePlaylists(musicRoot, all);
    }

    /** Listeyi ve varsa özel kapak dosyasını siler. */
    public static void deletePlaylist(@Nullable File musicRoot, String name) {
        if (musicRoot == null || name == null) return;
        List<Playlist> all = loadPlaylists(musicRoot);
        Playlist target = null;
        for (Playlist p : all) {
            if (p.name().equalsIgnoreCase(name)) {
                target = p;
                break;
            }
        }
        if (target == null) return;

        String cover = target.coverPath();
        if (cover != null && !cover.isEmpty()) {
            File f = new File(cover);
            if (f.isFile() && f.getName().startsWith("playlist_cover_")) {
                delete(f);
            }
        }
        all.remove(target);
        savePlaylists(musicRoot, all);
    }

    /**
     * Kapak yolunu günceller. {@code path} boşsa varsayılan 4'lü kolaj
     * kullanılır. Eski kapak bizim ürettiğimiz {@code playlist_cover_*.jpg}
     * ise temizlenir; başka bir şarkının kapağıysa dokunulmaz.
     */
    public static void setPlaylistCoverPath(@Nullable File musicRoot, String name, String path) {
        if (musicRoot == null || name == null) return;
        List<Playlist> all = loadPlaylists(musicRoot);
        String newPath = path == null ? "" : path;

        for (int i = 0; i < all.size(); i++) {
            Playlist p = all.get(i);
            if (!p.name().equalsIgnoreCase(name)) continue;

            String oldPath = p.coverPath();
            if (oldPath != null && !oldPath.isEmpty() && !oldPath.equals(newPath)) {
                File old = new File(oldPath);
                if (old.isFile() && old.getName().startsWith("playlist_cover_")) {
                    delete(old);
                }
            }
            all.set(i, new Playlist(p.name(), p.remoteId(), p.videoIds(), newPath, false));
            savePlaylists(musicRoot, all);
            return;
        }

        // Liste yoksa oluştur ve kapağı ata
        all.add(new Playlist(name, "", new ArrayList<>(), newPath, false));
        savePlaylists(musicRoot, all);
    }

    /**
     * Bir Bitmap'i {@code playlist_cover_<safeName>.jpg} olarak kaydedip
     * playlist kapağı olarak atar.
     */
    public static void setPlaylistCoverBitmap(@Nullable File musicRoot, String name, @Nullable Bitmap bmp) {
        if (musicRoot == null || name == null || bmp == null) return;
        String safeName = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (safeName.isEmpty()) safeName = "default";
        File coverFile = new File(musicRoot, "playlist_cover_" + safeName + ".jpg");
        writeJpeg(coverFile, bmp);
        setPlaylistCoverPath(musicRoot, name, coverFile.getAbsolutePath());
    }

    // =====================================================================================
    // ŞARKI VAR MI / EKSİK ID'LER
    // =====================================================================================

    /** Video ID için audio dosyası (webm veya m4a) cihazda var mı? */
    public static boolean isTrackDownloaded(@Nullable File musicRoot, @Nullable String videoId) {
        if (musicRoot == null || videoId == null || videoId.isBlank()) return false;
        return new File(musicRoot, videoId + ".webm").isFile()
                || new File(musicRoot, videoId + ".m4a").isFile();
    }

    /**
     * Belirtilen listedeki (1-tabanlı start/end aralığında) henüz indirilmemiş
     * şarkıların video ID'lerini döner. {@code end=0} ise sonuna kadar.
     */
    public static List<String> getMissingVideoIds(@Nullable File musicRoot, @Nullable String name,
                                                  int startOneBased, int endOneBased) {
        List<String> result = new ArrayList<>();
        if (musicRoot == null || name == null) return result;

        Playlist target = null;
        for (Playlist p : loadPlaylists(musicRoot)) {
            if (p.name().equalsIgnoreCase(name)) {
                target = p;
                break;
            }
        }
        // "Tüm Şarkılar" dinamik; yeni şarkı indirilecekse burada iş yok.
        if (target == null) return result;

        List<String> ids = target.videoIds();
        int from = startOneBased > 0 ? Math.max(0, startOneBased - 1) : 0;
        int to = endOneBased > 0 ? Math.min(ids.size(), endOneBased) : ids.size();
        if (from >= to) {
            from = 0;
            to = ids.size();
        }

        for (int i = from; i < to; i++) {
            String id = ids.get(i);
            if (!isTrackDownloaded(musicRoot, id)) result.add(id);
        }
        return result;
    }

    // =====================================================================================
    // AGGREGATORS (KİTAPLIK / AKILLI / SANATÇI)
    // =====================================================================================

    /**
     * Kitaplıktaki ana ızgara: [Tüm Şarkılar] + [Beğenilenler (varsa)] + kullanıcı listeleri.
     */
    public static List<Playlist> buildPrimaryLibraryGrid(List<OfflineTrack> tracks,
                                                         @Nullable List<Playlist> userPlaylists) {
        List<Playlist> result = new ArrayList<>();

        List<String> allIds = new ArrayList<>();
        if (tracks != null) {
            for (OfflineTrack t : tracks) allIds.add(t.videoId());
        }
        result.add(new Playlist(SPECIAL_ALL_SONGS, "", allIds, "", true));

        Playlist liked = null;
        if (userPlaylists != null) {
            for (Playlist p : userPlaylists) {
                if (p.isSmart()) continue;
                if (SPECIAL_LIKED_SONGS.equalsIgnoreCase(p.name())) {
                    liked = p;
                } else {
                    result.add(p);
                }
            }
        }
        if (liked != null) {
            result.add(1, liked);
        }
        return result;
    }

    /**
     * 4 akıllı koleksiyon: En Çok Dinlenenler, Son İndirilenler, Gece Dinlenenler,
     * Hiç Dinlenmeyenler. Diske yazılmaz.
     */
    public static List<Playlist> buildSmartPlaylists(@Nullable List<OfflineTrack> tracks) {
        List<Playlist> result = new ArrayList<>();
        if (tracks == null) tracks = new ArrayList<>();

        // En Çok Dinlenenler
        List<OfflineTrack> byPlay = new ArrayList<>(tracks);
        byPlay.sort((a, b) -> Integer.compare(b.playCount(), a.playCount()));
        List<String> mostPlayed = new ArrayList<>();
        for (int i = 0; i < Math.min(25, byPlay.size()); i++) {
            OfflineTrack t = byPlay.get(i);
            if (t.playCount() > 0) mostPlayed.add(t.videoId());
        }
        result.add(new Playlist("En Çok Dinlenenler", SMART_MOST_PLAYED, mostPlayed, "", true));

        // Son İndirilenler
        List<OfflineTrack> byDate = new ArrayList<>(tracks);
        byDate.sort((a, b) -> Long.compare(b.audioFile().lastModified(), a.audioFile().lastModified()));
        List<String> recent = new ArrayList<>();
        for (int i = 0; i < Math.min(25, byDate.size()); i++) {
            recent.add(byDate.get(i).videoId());
        }
        result.add(new Playlist("Son İndirilenler", SMART_RECENTLY_ADDED, recent, "", true));

        // Gece Dinlenenler
        List<OfflineTrack> nightTracks = new ArrayList<>();
        for (OfflineTrack t : tracks) {
            if (t.nightPlayCount() > 0) nightTracks.add(t);
        }
        nightTracks.sort((a, b) -> Integer.compare(b.nightPlayCount(), a.nightPlayCount()));
        List<String> night = new ArrayList<>();
        for (int i = 0; i < Math.min(25, nightTracks.size()); i++) {
            night.add(nightTracks.get(i).videoId());
        }
        result.add(new Playlist("Gece Dinlenenler", SMART_NIGHT_PLAYS, night, "", true));

        // Hiç Dinlenmeyenler
        List<String> never = new ArrayList<>();
        for (OfflineTrack t : tracks) {
            if (t.playCount() == 0) never.add(t.videoId());
        }
        result.add(new Playlist("Hiç Dinlenmeyenler", SMART_NEVER_PLAYED, never, "", true));

        return result;
    }

    /**
     * Sanatçı adına göre gruplanmış listeler. Her sanatçı için bir tane.
     * Sanatçı adı boş olanlar atlanır. Sıralama Türkçe harflere duyarlı değildir.
     */
    public static List<Playlist> buildArtistCards(@Nullable List<OfflineTrack> tracks) {
        List<Playlist> result = new ArrayList<>();
        if (tracks == null) return result;

        Map<String, List<String>> byArtist = new LinkedHashMap<>();
        for (OfflineTrack t : tracks) {
            String artist = t.displayArtist();
            if (artist == null || artist.isBlank()
                    || artist.equalsIgnoreCase("bilinmeyen sanatçı")
                    || artist.equalsIgnoreCase("unknown artist")) continue;
            byArtist.computeIfAbsent(artist, k -> new ArrayList<>()).add(t.videoId());
        }

        // Alfabetik sıralama (küçük/büyük harf duyarsız)
        List<Map.Entry<String, List<String>>> entries = new ArrayList<>(byArtist.entrySet());
        entries.sort((a, b) -> a.getKey().toLowerCase(Locale.ROOT)
                .compareTo(b.getKey().toLowerCase(Locale.ROOT)));

        for (Map.Entry<String, List<String>> e : entries) {
            result.add(new Playlist(e.getKey(), "artist", e.getValue(), "", true));
        }
        return result;
    }

    // =====================================================================================
    // YARDIMCILAR
    // =====================================================================================

    /**
     * Playlist içindeki benzersiz video ID'lerini döner (sırayı korur).
     * Savunma amaçlı: JSON bozulması olsa bile tekrarları temizler.
     */
    public static List<String> uniqueIds(Playlist p) {
        if (p == null) return new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>(p.videoIds());
        return new ArrayList<>(seen);
    }
}