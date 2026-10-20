package app.morphe.extension.music.patches.downloads;

import static app.morphe.extension.shared.StringRef.str;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.shared.Utils;

public final class LocalDownloadsListRenderer {

    private static final int PAGE_SIZE = 40;
    private static final int BOTTOM_TRIGGER_DP = 600;
    
    private static final Map<ScrollView, Boolean> LAZY_LISTENERS_INSTALLED = new HashMap<>();

    private LocalDownloadsListRenderer() {}

    public static void renderTracksAndPlaylists(Context ctx, File musicRoot, LocalDownloadsFragment fragment) {
        if (ctx == null || LocalDownloadsUIState.songsList == null) return;

        LocalDownloadsUIState.loadTracksIfNeeded(musicRoot);
        LocalDownloadsUIState.songsList.removeAllViews();
        LocalDownloadsUIState.rowViews.clear();
        LocalDownloadsUIState.queuePaths = new ArrayList<>();
        LocalDownloadsUIState.currentDisplayedTracks.clear();

        if (LocalDownloadsUIState.batchActionBar != null) {
            LocalDownloadsUIState.batchActionBar.setVisibility(LocalDownloadsUIState.selectionMode ? View.VISIBLE : View.GONE);
            if (LocalDownloadsUIState.selectionMode && LocalDownloadsUIState.batchCountText != null) {
                LocalDownloadsUIState.batchCountText.setText(LocalDownloadsUIState.selectedVideoIds.size() + " şarkı seçildi");
            }
        }

        if (LocalDownloadsUIState.selectModeBtn != null) {
            LocalDownloadsUIState.selectModeBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(ctx, LocalDownloadsUIState.ICON_CHECKLIST,
                    LocalDownloadsUIState.selectionMode ? LocalDownloadsUIState.activeAccentColor() : LocalDownloadsUIState.primaryTextColor(), 0));
        }

        if (LocalDownloadsUIState.showingArtists && LocalDownloadsUIState.selectedArtist == null) {
            if (LocalDownloadsUIState.backToLibraryBtn != null) LocalDownloadsUIState.backToLibraryBtn.setVisibility(View.GONE);
            if (LocalDownloadsUIState.summary != null) {
                List<String> artists = LocalDownloadsUIState.getAllArtists();
                LocalDownloadsUIState.summary.setText("Sanatçılar • " + artists.size() + " Sanatçı");
            }
            renderArtistsSection(ctx, musicRoot, fragment);
            return;
        }

        boolean inRootGrid = LocalDownloadsUIState.showingPlaylistsOverview && LocalDownloadsUIState.selectedPlaylist == null
                && LocalDownloadsUIState.currentSmartFilter == LocalDownloadsUIState.SmartFilterMode.NONE
                && !LocalDownloadsUIState.showingArtists && LocalDownloadsUIState.currentFilterMode == LocalDownloadsUIState.FilterMode.ALL
                && LocalDownloadsUIState.query.isEmpty();

        if (LocalDownloadsUIState.backToLibraryBtn != null) LocalDownloadsUIState.backToLibraryBtn.setVisibility(inRootGrid ? View.GONE : View.VISIBLE);

        if (inRootGrid) {
            if (LocalDownloadsUIState.summary != null) {
                LocalDownloadsUIState.summary.setText("Kitaplık • " + (LocalDownloadsUIState.playlists.size() + 2) + " Liste • " + LocalDownloadsUIState.tracks.size() + " Parça");
            }
            renderPlaylistsSection(ctx, musicRoot, fragment);
            return;
        }

        List<OfflineTrack> displayed = new ArrayList<>();
        OfflineStorage.Playlist activePlaylist = LocalDownloadsUIState.findPlaylist(LocalDownloadsUIState.selectedPlaylist);
        int pending = 0;

        if (LocalDownloadsUIState.showingArtists && LocalDownloadsUIState.selectedArtist != null) {
            for (OfflineTrack t : LocalDownloadsUIState.tracks) {
                if (t.displayArtist().equalsIgnoreCase(LocalDownloadsUIState.selectedArtist)) displayed.add(t);
            }
            LocalDownloadsUIState.songsList.addView(createArtistHeader(ctx, LocalDownloadsUIState.selectedArtist, displayed.size(), fragment));
        } else if (LocalDownloadsUIState.currentSmartFilter != LocalDownloadsUIState.SmartFilterMode.NONE) {
            String smartId = "";
            switch (LocalDownloadsUIState.currentSmartFilter) {
                case MOST_PLAYED: smartId = OfflineStorage.SMART_MOST_PLAYED; break;
                case RECENTLY_ADDED: smartId = OfflineStorage.SMART_RECENTLY_ADDED; break;
                case NIGHT_PLAYS: smartId = OfflineStorage.SMART_NIGHT_PLAYS; break;
                case NEVER_PLAYED: smartId = OfflineStorage.SMART_NEVER_PLAYED; break;
                default: break;
            }
            OfflineStorage.Playlist smart = null;
            for (OfflineStorage.Playlist pl : OfflineStorage.buildSmartPlaylists(LocalDownloadsUIState.tracks)) {
                if (smartId.equals(pl.remoteId())) { smart = pl; break; }
            }
            if (smart != null) {
                Map<String, OfflineTrack> byId = new HashMap<>();
                for (OfflineTrack t : LocalDownloadsUIState.tracks) byId.put(t.videoId(), t);
                for (String id : smart.videoIds()) {
                    OfflineTrack t = byId.get(id);
                    if (t != null) displayed.add(t);
                }
            }
        } else if (activePlaylist != null) {
            if (OfflineStorage.SPECIAL_ALL_SONGS.equalsIgnoreCase(activePlaylist.name())) {
                displayed.addAll(LocalDownloadsUIState.tracks);
            } else {
                Map<String, OfflineTrack> byId = new HashMap<>();
                for (OfflineTrack t : LocalDownloadsUIState.tracks) byId.put(t.videoId(), t);
                for (String id : activePlaylist.videoIds()) {
                    OfflineTrack t = byId.get(id);
                    if (t != null) displayed.add(t);
                    else pending++;
                }
            }
            LocalDownloadsUIState.songsList.addView(createYtMusicPlaylistHeader(ctx, musicRoot, activePlaylist, displayed, pending, fragment));
        } else if (LocalDownloadsUIState.currentFilterMode == LocalDownloadsUIState.FilterMode.UNASSIGNED) {
            Set<String> inPlaylists = LocalDownloadsUIState.getAllPlaylistTrackIds();
            for (OfflineTrack t : LocalDownloadsUIState.tracks) if (!inPlaylists.contains(t.videoId())) displayed.add(t);
        } else if (LocalDownloadsUIState.currentFilterMode == LocalDownloadsUIState.FilterMode.BROKEN_OR_INCOMPLETE) {
            for (OfflineTrack t : LocalDownloadsUIState.tracks) if (LocalDownloadsUIState.isBrokenOrIncompleteTrack(t)) displayed.add(t);
        } else {
            displayed.addAll(LocalDownloadsUIState.tracks);
        }

        long totalBytes = 0;
        for (OfflineTrack t : displayed) totalBytes += t.audioFile().length();

        String sum;
        if (LocalDownloadsUIState.showingArtists && LocalDownloadsUIState.selectedArtist != null) {
            sum = "Sanatçı: " + LocalDownloadsUIState.selectedArtist + " • " + displayed.size() + " parça";
        } else {
            String listTitle = smartFilterLabel();
            sum = !listTitle.isEmpty() ? listTitle + " • " + displayed.size() + " parça"
                    : (activePlaylist != null ? activePlaylist.name() + " • " : "") + str("morphe_music_downloads_summary", displayed.size(), LocalDownloadsUIState.formatSize(totalBytes));
            if (LocalDownloadsUIState.currentFilterMode == LocalDownloadsUIState.FilterMode.UNASSIGNED) sum = "Sahipsiz • " + displayed.size() + " parça";
            else if (LocalDownloadsUIState.currentFilterMode == LocalDownloadsUIState.FilterMode.BROKEN_OR_INCOMPLETE) sum = "Hatalı/Eksik • " + displayed.size() + " parça";
        }

        if (pending > 0) sum += " • " + pending + " eksik";
        if (LocalDownloadsUIState.summary != null) LocalDownloadsUIState.summary.setText(sum);

        final List<OfflineTrack> filtered = new ArrayList<>();
        for (OfflineTrack t : displayed) {
            if (LocalDownloadsUIState.matchesQuery(t)) filtered.add(t);
        }

        LocalDownloadsUIState.currentDisplayedTracks.addAll(filtered);
        for (OfflineTrack t : filtered) {
            LocalDownloadsUIState.queuePaths.add(t.audioFile().getAbsolutePath());
        }

        if (filtered.isEmpty()) {
            String msg = (LocalDownloadsUIState.showingArtists && LocalDownloadsUIState.selectedArtist != null)
                    ? "Bu sanatçıya ait indirilmiş şarkı bulunamadı."
                    : (LocalDownloadsUIState.currentSmartFilter != LocalDownloadsUIState.SmartFilterMode.NONE
                    ? "Bu akıllı filtreye uygun şarkı bulunamadı ✨"
                    : (activePlaylist != null
                    ? (pending > 0 ? pending + " şarkı henüz indirilmemiş." : "Bu listede henüz indirilmiş şarkı yok.")
                    : (LocalDownloadsUIState.currentFilterMode != LocalDownloadsUIState.FilterMode.ALL
                    ? "Bu filtreye uyan şarkı bulunamadı ✨"
                    : str(LocalDownloadsUIState.tracks.isEmpty() ? "morphe_music_downloads_empty" : "morphe_music_downloads_nothing_found"))));
            TextView empty = LocalDownloadsUIState.text(ctx, msg, 15, LocalDownloadsUIState.secondary());
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(LocalDownloadsUIState.dp(ctx, 20), LocalDownloadsUIState.dp(ctx, 50), LocalDownloadsUIState.dp(ctx, 20), 0);
            LocalDownloadsUIState.songsList.addView(empty);
            return;
        }

        boolean animateRows = LocalDownloadsUIState.animateNextSongList;
        LocalDownloadsUIState.animateNextSongList = false;

        final int[] loadedCount = {0};
        final int totalSize = filtered.size();
        final int initial = Math.min(PAGE_SIZE, totalSize);

        for (int i = 0; i < initial; i++) {
            OfflineTrack t = filtered.get(i);
            View row = songRow(ctx, t, fragment);
            LocalDownloadsUIState.songsList.addView(row);
            LocalDownloadsUIState.rowViews.add(row);
            if (animateRows && i < 8) {
                LocalDownloadsAnimator.animateListItemIn(ctx, row, i, LocalDownloadsUIState.dp(ctx, 10));
            }
        }
        loadedCount[0] = initial;
        LocalDownloadsUIState.markPlayingRow(ctx);

        if (totalSize > initial) {
            attachLazyScrollListener(ctx, fragment, filtered, loadedCount, totalSize);
        }
    }

    private static void attachLazyScrollListener(final Context ctx, final LocalDownloadsFragment fragment,
                                                 final List<OfflineTrack> filtered,
                                                 final int[] loadedCount, final int totalSize) {
        final ScrollView scroll = findParentScrollView(LocalDownloadsUIState.songsList);
        if (scroll == null) return;

        if (LAZY_LISTENERS_INSTALLED.containsKey(scroll)) return;
        LAZY_LISTENERS_INSTALLED.put(scroll, Boolean.TRUE);

        final int triggerPx = LocalDownloadsUIState.dp(ctx, BOTTOM_TRIGGER_DP);

        scroll.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (loadedCount[0] >= totalSize) return false;
                    View child = scroll.getChildAt(0);
                    if (child == null) return false;
                    int distanceFromBottom = child.getHeight() - (scroll.getScrollY() + scroll.getHeight());
                    if (distanceFromBottom <= triggerPx) {
                        int next = Math.min(totalSize, loadedCount[0] + PAGE_SIZE);
                        for (int i = loadedCount[0]; i < next; i++) {
                            OfflineTrack t = filtered.get(i);
                            View row = songRow(ctx, t, fragment);
                            LocalDownloadsUIState.songsList.addView(row);
                            LocalDownloadsUIState.rowViews.add(row);
                        }
                        loadedCount[0] = next;
                        LocalDownloadsUIState.markPlayingRow(ctx);
                    }
                }
                return false;
            }
        });
    }

    private static ScrollView findParentScrollView(View v) {
        android.view.ViewParent p = v.getParent();
        while (p != null) {
            if (p instanceof ScrollView) return (ScrollView) p;
            p = p.getParent();
        }
        return null;
    }

    private static String smartFilterLabel() {
        switch (LocalDownloadsUIState.currentSmartFilter) {
            case MOST_PLAYED: return "En Çok Dinlenenler";
            case RECENTLY_ADDED: return "Son İndirilenler";
            case NIGHT_PLAYS: return "Gece Dinlenenler";
            case NEVER_PLAYED: return "Hiç Dinlenmeyenler";
            default: return "";
        }
    }

    private static void renderArtistsSection(Context ctx, File musicRoot, LocalDownloadsFragment fragment) {
        List<String> artists = LocalDownloadsUIState.getAllArtists();
        boolean animateCards = LocalDownloadsUIState.animateNextSongList;
        LocalDownloadsUIState.animateNextSongList = false;

        if (artists.isEmpty()) {
            TextView empty = LocalDownloadsUIState.text(ctx, "Henüz indirilmiş bir sanatçı bulunmuyor.", 15, LocalDownloadsUIState.secondary());
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(LocalDownloadsUIState.dp(ctx, 20), LocalDownloadsUIState.dp(ctx, 50), LocalDownloadsUIState.dp(ctx, 20), 0);
            LocalDownloadsUIState.songsList.addView(empty);
            return;
        }

        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        int cardW = (screenW - LocalDownloadsUIState.dp(ctx, 36)) / 2;
        LinearLayout currentRow = null;

        for (int i = 0; i < artists.size(); i++) {
            if (i % 2 == 0) {
                currentRow = new LinearLayout(ctx);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setPadding(0, 0, 0, LocalDownloadsUIState.dp(ctx, 14));
                LocalDownloadsUIState.songsList.addView(currentRow, new LinearLayout.LayoutParams(-1, -2));
            }
            String artistName = artists.get(i);
            View card = createArtistCard(ctx, artistName, cardW, fragment);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, -2);
            if (i % 2 == 0) lp.setMargins(0, 0, LocalDownloadsUIState.dp(ctx, 12), 0);
            card.setLayoutParams(lp);
            currentRow.addView(card);
            if (animateCards && i < 8) {
                LocalDownloadsAnimator.animateListItemIn(ctx, card, i, LocalDownloadsUIState.dp(ctx, 10));
            }
        }
    }

    private static View createArtistCard(Context ctx, String artistName, int widthPx, LocalDownloadsFragment fragment) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(LocalDownloadsUIState.dp(ctx, 8), LocalDownloadsUIState.dp(ctx, 12), LocalDownloadsUIState.dp(ctx, 8), LocalDownloadsUIState.dp(ctx, 12));
        card.setBackground(LocalDownloadsUIState.glassCardDrawable(ctx, LocalDownloadsUIState.dp(ctx, 14), false));

        int imgSize = widthPx - LocalDownloadsUIState.dp(ctx, 48);
        ImageView avatar = new ImageView(ctx);
        avatar.setBackgroundColor(Color.argb(45, 255, 255, 255));
        avatar.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) { outline.setOval(0, 0, view.getWidth(), view.getHeight()); }
        });
        avatar.setClipToOutline(true);

        OfflineTrack firstTrack = null;
        for (OfflineTrack t : LocalDownloadsUIState.tracks) {
            if (t.displayArtist().equalsIgnoreCase(artistName)) { firstTrack = t; break; }
        }
        if (firstTrack != null) LocalDownloadsUIState.loadArtworkAsync(firstTrack, imgSize, avatar);
        else {
            avatar.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(ctx, LocalDownloadsUIState.ICON_PLAY, LocalDownloadsUIState.activeAccentColor(), 0));
            avatar.setPadding(LocalDownloadsUIState.dp(ctx, 20), LocalDownloadsUIState.dp(ctx, 20), LocalDownloadsUIState.dp(ctx, 20), LocalDownloadsUIState.dp(ctx, 20));
        }

        card.addView(avatar, new LinearLayout.LayoutParams(imgSize, imgSize));

        TextView title = LocalDownloadsUIState.text(ctx, artistName, 15, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, LocalDownloadsUIState.dp(ctx, 12), 0, LocalDownloadsUIState.dp(ctx, 2));
        card.addView(title);

        int trackCount = 0;
        for (OfflineTrack t : LocalDownloadsUIState.tracks) if (t.displayArtist().equalsIgnoreCase(artistName)) trackCount++;

        TextView count = LocalDownloadsUIState.text(ctx, trackCount + " şarkı", 12, LocalDownloadsUIState.secondary());
        count.setSingleLine(true);
        count.setGravity(Gravity.CENTER);
        card.addView(count);

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                LocalDownloadsUIState.selectedArtist = artistName;
                LocalDownloadsUIState.animateNextSongList = true;
                fragment.showTracks();
            }
        });
        return card;
    }

    private static View createArtistHeader(Context ctx, final String artistName, int trackCount, final LocalDownloadsFragment fragment) {
        LinearLayout headerCard = new LinearLayout(ctx);
        headerCard.setOrientation(LinearLayout.VERTICAL);
        headerCard.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = LocalDownloadsUIState.dp(ctx, 16);
        headerCard.setPadding(pad, pad, pad, pad);
        headerCard.setBackground(LocalDownloadsUIState.glassCardDrawable(ctx, LocalDownloadsUIState.dp(ctx, 16), false));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(LocalDownloadsUIState.dp(ctx, 4), LocalDownloadsUIState.dp(ctx, 4), LocalDownloadsUIState.dp(ctx, 4), LocalDownloadsUIState.dp(ctx, 10));
        headerCard.setLayoutParams(cardLp);

        int coverSize = LocalDownloadsUIState.dp(ctx, 130);
        ImageView coverView = new ImageView(ctx);
        coverView.setBackgroundColor(Color.argb(45, 255, 255, 255));
        coverView.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) { outline.setOval(0, 0, view.getWidth(), view.getHeight()); }
        });
        coverView.setClipToOutline(true);
        coverView.setElevation(LocalDownloadsUIState.dp(ctx, 8f));

        OfflineTrack firstTrack = null;
        for (OfflineTrack t : LocalDownloadsUIState.tracks) {
            if (t.displayArtist().equalsIgnoreCase(artistName)) { firstTrack = t; break; }
        }
        if (firstTrack != null) LocalDownloadsUIState.loadArtworkAsync(firstTrack, coverSize, coverView);

        FrameLayout coverWrap = new FrameLayout(ctx);
        coverWrap.addView(coverView, new FrameLayout.LayoutParams(coverSize, coverSize, Gravity.CENTER));
        headerCard.addView(coverWrap, new LinearLayout.LayoutParams(-2, -2));

        TextView title = LocalDownloadsUIState.text(ctx, artistName, 22, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, LocalDownloadsUIState.dp(ctx, 14), 0, LocalDownloadsUIState.dp(ctx, 4));
        headerCard.addView(title);

        TextView sub = LocalDownloadsUIState.text(ctx, trackCount + " indirilmiş şarkı", 13, LocalDownloadsUIState.secondary());
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, LocalDownloadsUIState.dp(ctx, 16));
        headerCard.addView(sub);

        LinearLayout actionsBar = new LinearLayout(ctx);
        actionsBar.setOrientation(LinearLayout.HORIZONTAL);
        actionsBar.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton playBtn = new ImageButton(ctx);
        android.graphics.drawable.GradientDrawable playCircle = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{LocalDownloadsUIState.activeAccentColor(), LocalDownloadsUIState.activeSecondaryAccent()});
        playCircle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        playBtn.setBackground(playCircle);
        playBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(ctx, LocalDownloadsUIState.ICON_PLAY, LocalDownloadsUIState.currentTheme.varOnAccent, 0));
        playBtn.setContentDescription("Sanatçıyı Çal");

        final OfflineTrack finalFirstTrack = firstTrack;
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (finalFirstTrack != null) {
                    List<OfflineTrack> artistTracks = new ArrayList<>();
                    for (OfflineTrack t : LocalDownloadsUIState.tracks) if (t.displayArtist().equalsIgnoreCase(artistName)) artistTracks.add(t);
                    ArrayList<String> queue = new ArrayList<>();
                    for (OfflineTrack t : artistTracks) queue.add(t.audioFile().getAbsolutePath());
                    LocalDownloadsPlayer.playWithCustomQueue(ctx, finalFirstTrack, queue, 0);
                    fragment.showFullScreenPlayer();
                }
            }
        });
        actionsBar.addView(playBtn, new LinearLayout.LayoutParams(LocalDownloadsUIState.dp(ctx, 64), LocalDownloadsUIState.dp(ctx, 64)));

        LinearLayout actionsBarWrap = new LinearLayout(ctx);
        actionsBarWrap.setOrientation(LinearLayout.HORIZONTAL);
        actionsBarWrap.setGravity(Gravity.CENTER);
        actionsBarWrap.addView(actionsBar);
        headerCard.addView(actionsBarWrap, new LinearLayout.LayoutParams(-1, -2));

        TextView backBtn = LocalDownloadsUIState.createPillButton(ctx, "← Tüm Sanatçılar", false);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                LocalDownloadsUIState.selectedArtist = null;
                LocalDownloadsUIState.animateNextSongList = true;
                fragment.showTracks();
            }
        });
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(-2, -2);
        backLp.gravity = Gravity.CENTER_HORIZONTAL;
        backLp.setMargins(0, LocalDownloadsUIState.dp(ctx, 10), 0, 0);
        headerCard.addView(backBtn, backLp);

        return headerCard;
    }

    public static void renderPlaylistsSection(Context ctx, File musicRoot, LocalDownloadsFragment fragment) {
        List<OfflineStorage.Playlist> primaryGridItems = OfflineStorage.buildPrimaryLibraryGrid(LocalDownloadsUIState.tracks, LocalDownloadsUIState.playlists);

        boolean animateCards = LocalDownloadsUIState.animateNextSongList;
        LocalDownloadsUIState.animateNextSongList = false;

        if (LocalDownloadsUIState.playlistGridMode) {
            int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
            int cardW = (screenW - LocalDownloadsUIState.dp(ctx, 36)) / 2;
            LinearLayout currentRow = null;
            for (int i = 0; i < primaryGridItems.size(); i++) {
                if (i % 2 == 0) {
                    currentRow = new LinearLayout(ctx);
                    currentRow.setOrientation(LinearLayout.HORIZONTAL);
                    currentRow.setPadding(0, 0, 0, LocalDownloadsUIState.dp(ctx, 14));
                    LocalDownloadsUIState.songsList.addView(currentRow, new LinearLayout.LayoutParams(-1, -2));
                }
                View card = createYtMusicGridCard(ctx, musicRoot, primaryGridItems.get(i), cardW, fragment);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, -2);
                if (i % 2 == 0) lp.setMargins(0, 0, LocalDownloadsUIState.dp(ctx, 12), 0);
                card.setLayoutParams(lp);
                currentRow.addView(card);
                if (animateCards && i < 8) {
                    LocalDownloadsAnimator.animateListItemIn(ctx, card, i, LocalDownloadsUIState.dp(ctx, 10));
                }
            }
        } else {
            for (int i = 0; i < primaryGridItems.size(); i++) {
                View row = createListPlaylistRow(ctx, musicRoot, primaryGridItems.get(i), fragment);
                LocalDownloadsUIState.songsList.addView(row);
                if (animateCards && i < 8) {
                    LocalDownloadsAnimator.animateListItemIn(ctx, row, i, LocalDownloadsUIState.dp(ctx, 10));
                }
            }
        }
    }

    private static int computePlaylistTotalSeconds(OfflineStorage.Playlist pl) {
        int total = 0;
        for (String id : pl.videoIds()) {
            OfflineTrack t = LocalDownloadsUIState.findById(id);
            if (t != null && t.durationSeconds() > 0) total += t.durationSeconds();
        }
        return total;
    }

    private static String formatLongDuration(int seconds) {
        if (seconds <= 0) return "0 dk";
        int h = seconds / 3600, m = (seconds % 3600) / 60;
        if (h > 0) return h + " sa " + m + " dk";
        return Math.max(1, m) + " dk";
    }

    public static View createYtMusicGridCard(Context ctx, File musicRoot, final OfflineStorage.Playlist pl, int widthPx, final LocalDownloadsFragment fragment) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.START);
        card.setPadding(LocalDownloadsUIState.dp(ctx, 8), LocalDownloadsUIState.dp(ctx, 8), LocalDownloadsUIState.dp(ctx, 8), LocalDownloadsUIState.dp(ctx, 10));
        card.setBackground(LocalDownloadsUIState.glassCardDrawable(ctx, LocalDownloadsUIState.dp(ctx, 14), false));

        int imgSize = widthPx - LocalDownloadsUIState.dp(ctx, 16);
        ImageView cover = buildPlaylistCoverImageView(ctx, pl, imgSize, 10);
        card.addView(cover, new LinearLayout.LayoutParams(imgSize, imgSize));

        String prefix = OfflineStorage.SPECIAL_ALL_SONGS.equalsIgnoreCase(pl.name()) ? "🎵 "
                : (OfflineStorage.SPECIAL_LIKED_SONGS.equalsIgnoreCase(pl.name()) ? "❤️ " : "");
        TextView title = LocalDownloadsUIState.text(ctx, prefix + pl.name(), 14, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(LocalDownloadsUIState.dp(ctx, 2), LocalDownloadsUIState.dp(ctx, 8), LocalDownloadsUIState.dp(ctx, 2), LocalDownloadsUIState.dp(ctx, 2));
        card.addView(title);

        int totalSec = computePlaylistTotalSeconds(pl);
        String durationStr = totalSec > 0 ? formatLongDuration(totalSec) : null;
        String subLabel = (pl.isSmart() ? "Sabit Koleksiyon" : "Oynatma listesi")
                + " • " + pl.videoIds().size() + " parça"
                + (durationStr != null ? " • " + durationStr : "");
        TextView count = LocalDownloadsUIState.text(ctx, subLabel, 12, LocalDownloadsUIState.secondary());
        count.setSingleLine(true);
        count.setPadding(LocalDownloadsUIState.dp(ctx, 2), 0, LocalDownloadsUIState.dp(ctx, 2), 0);
        card.addView(count);

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                LocalDownloadsUIState.showingArtists = false;
                LocalDownloadsUIState.selectedArtist = null;
                LocalDownloadsUIState.currentSmartFilter = LocalDownloadsUIState.SmartFilterMode.NONE;
                LocalDownloadsUIState.selectedPlaylist = pl.name();
                LocalDownloadsUIState.showingPlaylistsOverview = false;
                LocalDownloadsUIState.animateNextSongList = true;
                fragment.showTracks();
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                if (!OfflineStorage.SPECIAL_ALL_SONGS.equalsIgnoreCase(pl.name())) fragment.showPlaylistActionsMenu(v, pl);
                return true;
            }
        });
        return card;
    }

    public static View createListPlaylistRow(Context ctx, File musicRoot, final OfflineStorage.Playlist pl, final LocalDownloadsFragment fragment) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(LocalDownloadsUIState.dp(ctx, 10), LocalDownloadsUIState.dp(ctx, 8), LocalDownloadsUIState.dp(ctx, 10), LocalDownloadsUIState.dp(ctx, 8));
        row.setBackground(LocalDownloadsUIState.glassCardDrawable(ctx, LocalDownloadsUIState.dp(ctx, 12), false));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(ctx, 8));
        row.setLayoutParams(rowLp);

        int size = LocalDownloadsUIState.dp(ctx, 56);
        ImageView cover = buildPlaylistCoverImageView(ctx, pl, size, 8);
        row.addView(cover, new LinearLayout.LayoutParams(size, size));

        LinearLayout info = new LinearLayout(ctx);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(LocalDownloadsUIState.dp(ctx, 14), 0, LocalDownloadsUIState.dp(ctx, 8), 0);
        TextView name = LocalDownloadsUIState.text(ctx, pl.name(), 15, LocalDownloadsUIState.primaryTextColor());
        name.setTypeface(Typeface.DEFAULT_BOLD);

        int totalSec = computePlaylistTotalSeconds(pl);
        String durationStr = totalSec > 0 ? formatLongDuration(totalSec) : null;
        String subLabel = (pl.isSmart() ? "Sabit Koleksiyon" : "Oynatma listesi")
                + " • " + pl.videoIds().size() + " parça"
                + (durationStr != null ? " • " + durationStr : "");
        TextView sub = LocalDownloadsUIState.text(ctx, subLabel, 12, LocalDownloadsUIState.secondary());
        info.addView(name);
        info.addView(sub);
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

        if (!OfflineStorage.SPECIAL_ALL_SONGS.equalsIgnoreCase(pl.name())) {
            ImageButton menu = LocalDownloadsUIState.icon(ctx, "yt_outline_experimental_overflow_vertical_vd_theme_24", "Liste Menüsü");
            menu.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { fragment.showPlaylistActionsMenu(v, pl); }
            });
            row.addView(menu, new LinearLayout.LayoutParams(LocalDownloadsUIState.dp(ctx, 40), LocalDownloadsUIState.dp(ctx, 40)));
        }

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                LocalDownloadsUIState.showingArtists = false;
                LocalDownloadsUIState.selectedArtist = null;
                LocalDownloadsUIState.selectedPlaylist = pl.name();
                LocalDownloadsUIState.showingPlaylistsOverview = false;
                LocalDownloadsUIState.animateNextSongList = true;
                fragment.showTracks();
            }
        });
        return row;
    }

    public static View createYtMusicPlaylistHeader(Context ctx, File musicRoot, final OfflineStorage.Playlist pl, final List<OfflineTrack> loadedTracks, int pendingCount, final LocalDownloadsFragment fragment) {
        LinearLayout headerCard = new LinearLayout(ctx);
        headerCard.setOrientation(LinearLayout.VERTICAL);
        headerCard.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = LocalDownloadsUIState.dp(ctx, 16);
        headerCard.setPadding(pad, pad, pad, pad);
        headerCard.setBackground(LocalDownloadsUIState.glassCardDrawable(ctx, LocalDownloadsUIState.dp(ctx, 16), false));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(LocalDownloadsUIState.dp(ctx, 4), LocalDownloadsUIState.dp(ctx, 4), LocalDownloadsUIState.dp(ctx, 4), LocalDownloadsUIState.dp(ctx, 10));
        headerCard.setLayoutParams(cardLp);

        int coverSize = LocalDownloadsUIState.dp(ctx, 152);
        FrameLayout coverWrap = new FrameLayout(ctx);
        ImageView coverView = buildPlaylistCoverImageView(ctx, pl, coverSize, 12);
        coverView.setElevation(LocalDownloadsUIState.dp(ctx, 8f));
        coverWrap.addView(coverView, new FrameLayout.LayoutParams(coverSize, coverSize, Gravity.CENTER));
        if (!OfflineStorage.SPECIAL_ALL_SONGS.equalsIgnoreCase(pl.name())) {
            coverWrap.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { fragment.showChangePlaylistCoverDialog(v, pl, loadedTracks); }
            });
        }
        headerCard.addView(coverWrap, new LinearLayout.LayoutParams(-2, -2));

        TextView title = LocalDownloadsUIState.text(ctx, pl.name(), 20, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, LocalDownloadsUIState.dp(ctx, 10), 0, LocalDownloadsUIState.dp(ctx, 2));
        headerCard.addView(title);

        int totalSec = 0;
        for (OfflineTrack t : loadedTracks) totalSec += t.durationSeconds();
        String meta = loadedTracks.size() + " parça • " + LocalDownloadsUIState.formatDuration(totalSec);
        if (pendingCount > 0) meta += " (" + pendingCount + " bekliyor)";
        TextView sub = LocalDownloadsUIState.text(ctx, meta, 12, LocalDownloadsUIState.secondary());
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, LocalDownloadsUIState.dp(ctx, 16));
        headerCard.addView(sub);

        LinearLayout actionsBar = new LinearLayout(ctx);
        actionsBar.setOrientation(LinearLayout.HORIZONTAL);
        actionsBar.setGravity(Gravity.CENTER_VERTICAL);
        actionsBar.setPadding(LocalDownloadsUIState.dp(ctx, 4), LocalDownloadsUIState.dp(ctx, 6), LocalDownloadsUIState.dp(ctx, 4), LocalDownloadsUIState.dp(ctx, 6));

        ImageButton downloadBtn = new ImageButton(ctx);
        downloadBtn.setBackground(LocalDownloadsUIState.createCircleRipple());
        downloadBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(ctx, LocalDownloadsUIState.ICON_DOWNLOAD, LocalDownloadsUIState.primaryTextColor(), 0));
        downloadBtn.setContentDescription("Listeyi İndir / Senkronize Et");
        downloadBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (OfflineStorage.SPECIAL_ALL_SONGS.equalsIgnoreCase(pl.name())) Utils.showToastShort("Tüm şarkılar zaten cihazda");
                else LocalDownloadManager.syncPlaylists(musicRoot, pl.name(), true);
            }
        });
        actionsBar.addView(downloadBtn, new LinearLayout.LayoutParams(LocalDownloadsUIState.dp(ctx, 48), LocalDownloadsUIState.dp(ctx, 48)));

        ImageButton playBtn = new ImageButton(ctx);
        android.graphics.drawable.GradientDrawable playCircle = new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{LocalDownloadsUIState.activeAccentColor(), LocalDownloadsUIState.activeSecondaryAccent()});
        playCircle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        playBtn.setBackground(playCircle);
        playBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(ctx, LocalDownloadsUIState.ICON_PLAY, LocalDownloadsUIState.currentTheme.varOnAccent, 0));
        playBtn.setContentDescription("Listeyi Oynat");
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!loadedTracks.isEmpty()) { LocalDownloadsPlayer.play(ctx, loadedTracks.get(0)); fragment.showFullScreenPlayer(); }
            }
        });
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(LocalDownloadsUIState.dp(ctx, 68), LocalDownloadsUIState.dp(ctx, 68));
        playLp.setMargins(LocalDownloadsUIState.dp(ctx, 16), 0, LocalDownloadsUIState.dp(ctx, 16), 0);
        actionsBar.addView(playBtn, playLp);

        ImageButton moreBtn = new ImageButton(ctx);
        moreBtn.setBackground(LocalDownloadsUIState.createCircleRipple());
        moreBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(ctx, LocalDownloadsUIState.ICON_MORE_VERT, LocalDownloadsUIState.primaryTextColor(), 0));
        moreBtn.setContentDescription("Diğer Seçenekler");
        moreBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { fragment.showPlaylistActionsMenu(v, pl); }
        });
        actionsBar.addView(moreBtn, new LinearLayout.LayoutParams(LocalDownloadsUIState.dp(ctx, 48), LocalDownloadsUIState.dp(ctx, 48)));

        LinearLayout actionsBarWrap = new LinearLayout(ctx);
        actionsBarWrap.setOrientation(LinearLayout.HORIZONTAL);
        actionsBarWrap.setGravity(Gravity.CENTER);
        actionsBarWrap.addView(actionsBar);
        headerCard.addView(actionsBarWrap, new LinearLayout.LayoutParams(-1, -2));

        TextView backGridBtn = LocalDownloadsUIState.createPillButton(ctx, "← Kütüphaneye Dön", false);
        backGridBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                LocalDownloadsUIState.selectedPlaylist = null;
                LocalDownloadsUIState.showingPlaylistsOverview = true;
                LocalDownloadsUIState.animateNextSongList = true;
                fragment.showTracks();
            }
        });
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(-2, -2);
        backLp.gravity = Gravity.CENTER_HORIZONTAL;
        backLp.setMargins(0, LocalDownloadsUIState.dp(ctx, 6), 0, 0);
        headerCard.addView(backGridBtn, backLp);

        return headerCard;
    }

    public static ImageView buildPlaylistCoverImageView(Context ctx, OfflineStorage.Playlist pl, int sizePx, int cornerDp) {
        final ImageView iv = LocalDownloadsUIState.artworkView(ctx, null, cornerDp);
        final String cacheKey = "pl_cover_" + pl.name() + "_" + pl.coverPath() + "_" + pl.videoIds().size() + "_" + sizePx + "_" + cornerDp;
        iv.setTag(cacheKey);

        Bitmap cached = LocalDownloadsUIState.ARTWORK_CACHE.get(cacheKey);
        if (cached != null) {
            iv.clearColorFilter();
            iv.setPadding(0, 0, 0, 0);
            iv.setImageBitmap(cached);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return iv;
        }

        final OfflineStorage.Playlist fPl = pl;
        final int fSize = sizePx;

        // DÜZELTME: submitOnBackgroundThread bir değer döndüren lambda (Callable) beklediği için
        // geleneksel Runnable yerine lambda kullanıldı ve sonuna return null; eklendi.
        Utils.submitOnBackgroundThread(() -> {
            Bitmap result = null;
            if (fPl.coverPath() != null && !fPl.coverPath().isEmpty()) {
                File coverFile = new File(fPl.coverPath());
                if (coverFile.isFile()) result = OfflineStorage.decodeArtwork(coverFile, fSize);
            }
            if (result == null) {
                List<Bitmap> tiles = new ArrayList<>(4);
                for (String id : fPl.videoIds()) {
                    OfflineTrack t = LocalDownloadsUIState.findById(id);
                    if (t != null) {
                        Bitmap b = t.artwork(fSize / 2 + 32);
                        if (b != null) tiles.add(b);
                    }
                    if (tiles.size() >= 4) break;
                }
                if (tiles.size() >= 4) {
                    result = Bitmap.createBitmap(fSize, fSize, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(result);
                    int half = fSize / 2;
                    Rect[] dsts = { new Rect(0, 0, half, half), new Rect(half, 0, fSize, half), new Rect(0, half, half, fSize), new Rect(half, half, fSize, fSize) };
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    for (int i = 0; i < 4; i++) c.drawBitmap(tiles.get(i), null, dsts[i], p);
                } else if (!tiles.isEmpty()) {
                    result = tiles.get(0);
                }
            }
            if (result != null) {
                final Bitmap finalBmp = result;
                LocalDownloadsUIState.ARTWORK_CACHE.put(cacheKey, finalBmp);
                Utils.runOnMainThread(() -> {
                    if (cacheKey.equals(iv.getTag())) {
                        iv.clearColorFilter();
                        iv.setPadding(0, 0, 0, 0);
                        iv.setImageBitmap(finalBmp);
                        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    }
                });
            }
            return null;
        });
        return iv;
    }

    public static View songRow(final Context ctx, final OfflineTrack track, final LocalDownloadsFragment fragment) {
        final LinearLayout row = new LinearLayout(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(LocalDownloadsUIState.dp(ctx, 8), LocalDownloadsUIState.dp(ctx, 7), LocalDownloadsUIState.dp(ctx, 8), LocalDownloadsUIState.dp(ctx, 7));

        final boolean isSelected = LocalDownloadsUIState.selectedVideoIds.contains(track.videoId());

        if (LocalDownloadsUIState.selectionMode) {
            TextView check = LocalDownloadsUIState.text(ctx, isSelected ? "☑️" : "⬜", 18, isSelected ? LocalDownloadsUIState.activeAccentColor() : LocalDownloadsUIState.secondary());
            check.setPadding(LocalDownloadsUIState.dp(ctx, 4), 0, LocalDownloadsUIState.dp(ctx, 10), 0);
            row.addView(check);
        }

        ImageView artView = LocalDownloadsUIState.artworkView(ctx, null, LocalDownloadsUIState.ARTWORK_CORNER_DP);
        LocalDownloadsUIState.loadArtworkAsync(track, LocalDownloadsUIState.ROW_ARTWORK_PIXELS, artView);
        artView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (LocalDownloadsUIState.selectionMode) toggleTrackSelection(track, fragment);
                else { LocalDownloadsPlayer.play(ctx, track); fragment.showFullScreenPlayer(); }
            }
        });
        row.addView(artView, new LinearLayout.LayoutParams(LocalDownloadsUIState.dp(ctx, 50), LocalDownloadsUIState.dp(ctx, 50)));

        LinearLayout labels = new LinearLayout(ctx);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(LocalDownloadsUIState.dp(ctx, 12), 0, LocalDownloadsUIState.dp(ctx, 8), 0);

        TextView name = LocalDownloadsUIState.text(ctx, track.displayTitle(), 15, LocalDownloadsUIState.primaryTextColor());
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView detail = LocalDownloadsUIState.text(
                ctx,
                track.displayArtist() + " • " + track.formattedDuration() + " • " + LocalDownloadsUIState.formatSize(track.audioFile().length()) + " MB",
                12,
                LocalDownloadsUIState.secondary()
        );
        detail.setSingleLine(true);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);

        labels.addView(name);
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageButton menu = LocalDownloadsUIState.icon(ctx, "yt_outline_experimental_overflow_vertical_vd_theme_24", "Şarkı seçenekleri");
        menu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { fragment.showTrackBottomSheet(track); }
        });
        row.addView(menu, new LinearLayout.LayoutParams(LocalDownloadsUIState.dp(ctx, LocalDownloadsUIState.ICON_BUTTON_DP), LocalDownloadsUIState.dp(ctx, LocalDownloadsUIState.ICON_BUTTON_DP)));

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (LocalDownloadsUIState.selectionMode) toggleTrackSelection(track, fragment);
                else fragment.showTrackBottomSheet(track);
            }
        });

        GestureDetector swipeDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            private boolean handledSwipe;
            @Override public boolean onDown(MotionEvent e) { handledSwipe = false; return true; }
            @Override public boolean onSingleTapUp(MotionEvent e) { if (!handledSwipe) row.performClick(); return true; }
            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (handledSwipe || e1 == null || e2 == null || !LocalDownloadsUIState.swipeToPlayNext || LocalDownloadsUIState.selectionMode) return false;
                float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                if (dx >= LocalDownloadsUIState.dp(ctx, 26) && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                    handledSwipe = true;
                    if (row.getParent() != null) row.getParent().requestDisallowInterceptTouchEvent(true);
                    LocalDownloadsPlayer.insertTrackIntoLiveQueue(ctx, track, true);
                    LocalDownloadsAnimator.animateSwipeSuccess(ctx, row, LocalDownloadsUIState.dp(ctx, 18));
                    return true;
                }
                return false;
            }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (handledSwipe || e1 == null || e2 == null || !LocalDownloadsUIState.swipeToPlayNext || LocalDownloadsUIState.selectionMode) return false;
                float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                if (dx > LocalDownloadsUIState.dp(ctx, 22) && Math.abs(dx) > Math.abs(dy)) {
                    handledSwipe = true;
                    LocalDownloadsPlayer.insertTrackIntoLiveQueue(ctx, track, true);
                    LocalDownloadsAnimator.animateSwipeSuccess(ctx, row, LocalDownloadsUIState.dp(ctx, 18));
                    return true;
                }
                return false;
            }
        });

        row.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (!LocalDownloadsUIState.swipeToPlayNext || LocalDownloadsUIState.selectionMode) return false;
                return swipeDetector.onTouchEvent(event);
            }
        });
        return row;
    }

    private static void toggleTrackSelection(OfflineTrack track, LocalDownloadsFragment fragment) {
        if (!LocalDownloadsUIState.selectedVideoIds.remove(track.videoId())) LocalDownloadsUIState.selectedVideoIds.add(track.videoId());
        fragment.showTracks();
    }
}