package app.morphe.extension.music.patches.downloads;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceFragment;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.CustomDialog;

@SuppressWarnings("deprecation")
public final class LocalDownloadsFragment extends PreferenceFragment
        implements OfflinePlaybackService.PlaybackListener, LocalDownloadManager.DownloadListener {

    private static final float Z_INDEX_CONTENT = 2f;
    private static final float Z_INDEX_FAB = 12f;
    private static final float Z_INDEX_MINI_PLAYER = 20f;
    private static final float Z_INDEX_BOTTOM_NAV = 24f;

    private File musicRoot;
    private LinearLayout rootLayout;
    @Nullable private ScrollView mainScroll;

    private static boolean animateNextTransition = false;
    private static boolean transitionFromRight = true;

    public static void requestLibraryTransition(boolean fromRight) {
        animateNextTransition = true;
        transitionFromRight = fromRight;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        Activity activity = getActivity();
        musicRoot = new File(activity.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Morphe");
        LocalDownloadsUIState.loadSavedPreferences(activity, musicRoot);
        LocalDownloadsUIState.hideHostActionBar(activity);

        rootLayout = new LinearLayout(activity);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setClickable(true);
        rootLayout.setFocusableInTouchMode(true);
        rootLayout.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) handleGuardedBack();
            return true;
        });
        rootLayout.requestFocus();
        LocalDownloadsUIState.rootLayout = rootLayout;

        OfflineIntegrationPatch.guardBackButton(activity);
        LocalDownloadsUIState.applyListBackground(rootLayout);

        rootLayout.addView(LocalDownloadsHeader.createHeader(activity, this),
                new LinearLayout.LayoutParams(-1, -2));

        LocalDownloadsUIState.downloadBanner = createLiveDownloadBanner(activity);
        rootLayout.addView(LocalDownloadsUIState.downloadBanner,
                new LinearLayout.LayoutParams(-1, -2));

        LocalDownloadsUIState.batchActionBar = createBatchSelectionBar(activity);
        rootLayout.addView(LocalDownloadsUIState.batchActionBar,
                new LinearLayout.LayoutParams(-1, -2));

        FrameLayout contentFrame = new FrameLayout(activity);
        contentFrame.setElevation(LocalDownloadsUIState.dp(activity, Z_INDEX_CONTENT));
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        mainScroll = scroll;
        
        LocalDownloadsUIState.songsList = new LinearLayout(activity);
        LocalDownloadsUIState.songsList.setOrientation(LinearLayout.VERTICAL);
        LocalDownloadsUIState.songsList.setPadding(
                LocalDownloadsUIState.dp(activity, 12),
                LocalDownloadsUIState.dp(activity, 6),
                LocalDownloadsUIState.dp(activity, 12),
                LocalDownloadsUIState.dp(activity, 88)
        );
        scroll.addView(LocalDownloadsUIState.songsList);
        contentFrame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        TextView fabPlus = new TextView(activity);
        fabPlus.setText("+");
        fabPlus.setTextSize(28);
        fabPlus.setTypeface(Typeface.DEFAULT_BOLD);
        fabPlus.setTextColor(LocalDownloadsUIState.currentTheme.varOnAccent);
        fabPlus.setGravity(Gravity.CENTER);
        fabPlus.setElevation(LocalDownloadsUIState.dp(activity, Z_INDEX_FAB));
        GradientDrawable fabBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{LocalDownloadsUIState.activeAccentColor(), LocalDownloadsUIState.activeSecondaryAccent()});
        fabBg.setShape(GradientDrawable.OVAL);
        fabPlus.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(70, 255, 255, 255)), fabBg, fabBg));
        fabPlus.setOnClickListener(v -> showCreateOrAutoDownloadPlaylistDialog(null, null));
        LocalDownloadsUIState.fabPlusButton = fabPlus;

        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(
                LocalDownloadsUIState.dp(activity, 56),
                LocalDownloadsUIState.dp(activity, 56),
                Gravity.BOTTOM | Gravity.END
        );
        fabLp.setMargins(
                LocalDownloadsUIState.dp(activity, 16),
                LocalDownloadsUIState.dp(activity, 16),
                LocalDownloadsUIState.dp(activity, 18),
                LocalDownloadsUIState.dp(activity, 18)
        );
        contentFrame.addView(fabPlus, fabLp);

        rootLayout.addView(contentFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        LocalDownloadsUIState.miniPlayer = LocalDownloadsPlayer.createOriginalYtMusicMiniPlayer(activity, this);
        LocalDownloadsUIState.miniPlayer.setElevation(LocalDownloadsUIState.dp(activity, Z_INDEX_MINI_PLAYER));
        LocalDownloadsUIState.miniPlayer.setVisibility(View.GONE);
        rootLayout.addView(LocalDownloadsUIState.miniPlayer, new LinearLayout.LayoutParams(-1, -2));

        View bottomNav = LocalDownloadsHeader.createBottomNavigationBar(activity, this);
        bottomNav.setElevation(LocalDownloadsUIState.dp(activity, Z_INDEX_BOTTOM_NAV));
        rootLayout.addView(bottomNav, new LinearLayout.LayoutParams(-1, LocalDownloadsUIState.dp(activity, 58)));

        OfflineHeaderBlocker.attach(activity, rootLayout);

        LocalDownloadsUIState.diskReloadNeeded = true;
        showTracks();
        refreshDownloadStatusUI();

        // Subtle entrance motion for the offline library; respect the shared
        // animation-duration setting so reduced/short animations stay consistent.
        final LinearLayout entranceRoot = rootLayout;
        entranceRoot.setAlpha(0f);
        entranceRoot.setTranslationY(LocalDownloadsUIState.dp(activity, 8));
        entranceRoot.post(() -> {
            if (entranceRoot.getParent() == null) return;
            entranceRoot.animate().cancel();
            entranceRoot.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(LocalDownloadsUIState.scaleDuration(activity, 220L))
                    .start();
        });
        return rootLayout;
    }

    @Override
    public void onStart() {
        super.onStart();
        Activity act = getActivity();
        OfflineIntegrationPatch.guardBackButton(act);
        LocalDownloadsUIState.hideHostActionBar(act);
        OfflineHeaderBlocker.attach(act, rootLayout);
        OfflinePlaybackService.addListener(this);
        LocalDownloadManager.addListener(this);
        refreshDownloadStatusUI();
    }

    @Override
    public void onStop() {
        OfflinePlaybackService.removeListener(this);
        LocalDownloadManager.removeListener(this);
        Activity act = getActivity();
        LocalDownloadsUIState.restoreHostHeader(act);
        OfflineHeaderBlocker.detach(act);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        Activity act = getActivity();
        // Stop view-owned motion before the fragment hierarchy is released.
        if (rootLayout != null) {
            try {
                rootLayout.animate().cancel();
                rootLayout.clearAnimation();
                LocalDownloadsAnimator.stopAllPlayerAnimations(rootLayout);
            } catch (Exception ignored) {}
        }
        LocalDownloadsUIState.restoreHostHeader(act);
        OfflineHeaderBlocker.detach(act);
        rootLayout = null;
        super.onDestroyView();
    }

    public void handleGuardedBack() {
        Activity act = getActivity();
        if (LocalDownloadsPlayer.isFullPlayerShowing()) {
            LocalDownloadsPlayer.dismissFullPlayerIfShowing();
            return;
        }
        if (LocalDownloadsUIState.searchBar != null
                && LocalDownloadsUIState.searchBar.getVisibility() == View.VISIBLE) {
            LocalDownloadsUIState.toggleSearch(act);
            return;
        }
        if (LocalDownloadsUIState.selectionMode) {
            LocalDownloadsUIState.selectionMode = false;
            LocalDownloadsUIState.selectedVideoIds.clear();
            showTracks();
            return;
        }
        if (!LocalDownloadsUIState.showingPlaylistsOverview
                || LocalDownloadsUIState.selectedPlaylist != null
                || LocalDownloadsUIState.showingArtists
                || LocalDownloadsUIState.currentSmartFilter != LocalDownloadsUIState.SmartFilterMode.NONE
                || LocalDownloadsUIState.currentFilterMode != LocalDownloadsUIState.FilterMode.ALL) {
            
            LocalDownloadsFragment.requestLibraryTransition(false); // Geriye dönerken soldan gelsin
            LocalDownloadsUIState.selectedPlaylist = null;
            LocalDownloadsUIState.selectedArtist = null;
            LocalDownloadsUIState.showingArtists = false;
            LocalDownloadsUIState.showingPlaylistsOverview = true;
            LocalDownloadsUIState.currentSmartFilter = LocalDownloadsUIState.SmartFilterMode.NONE;
            LocalDownloadsUIState.currentFilterMode = LocalDownloadsUIState.FilterMode.ALL;
            
            // Liste giriş animasyonlarını sıfırla ki üst üste binmesin
            LocalDownloadsUIState.animateNextSongList = false;
            showTracks();
            if (act != null) LocalDownloadsHeader.refreshBottomNavigationTheme(act);
        }
    }

    public File getMusicRoot() { return musicRoot; }

    public void showTracks() {
        Activity act = getActivity();
        if (act == null || LocalDownloadsUIState.songsList == null) return;

        if (animateNextTransition) {
            animateNextTransition = false; // Tek seferlik tüket
            View target = mainScroll != null ? mainScroll : LocalDownloadsUIState.songsList;
            LocalDownloadsAnimator.animateLibraryTransition(target, transitionFromRight,
                    () -> LocalDownloadsListRenderer.renderTracksAndPlaylists(act, musicRoot, this));
        } else {
            LocalDownloadsListRenderer.renderTracksAndPlaylists(act, musicRoot, this);
        }
    }

    public void showFullScreenPlayer() {
        Activity act = getActivity();
        if (act == null) return;
        LocalDownloadsPlayer.showFullScreenPlayer(act, this);
    }

    public void showSettingsDialog() {
        LocalDownloadsDialogs.showSettingsDialog(getActivity(), this);
    }

    public void showDownloadManagerDialog() {
        LocalDownloadsDialogs.showDownloadManagerDialog(getActivity(), this);
    }

    public void showLyricsLibraryDialog() {
        LocalDownloadsDialogs.showLyricsLibraryDialog(getActivity(), this);
    }

    public void showManualLyricsSearchDialog(OfflineTrack track, @Nullable Runnable onComplete) {
        LocalDownloadsDialogs.showManualLyricsSearchDialog(getActivity(), track, onComplete, this);
    }

    public void showTrackBottomSheet(OfflineTrack track) {
        LocalDownloadsDialogs.showTrackBottomSheet(getActivity(), track, this);
    }

    public void showCreateOrAutoDownloadPlaylistDialog(@Nullable OfflineTrack trackToAdd,
                                                       @Nullable OfflineStorage.Playlist existingPlaylist) {
        LocalDownloadsDialogs.showCreateOrAutoDownloadPlaylistDialog(
                getActivity(), musicRoot, trackToAdd, existingPlaylist, this);
    }

    public void showChangePlaylistCoverDialog(View anchor, OfflineStorage.Playlist pl, List<OfflineTrack> plTracks) {
        LocalDownloadsDialogs.showChangePlaylistCoverDialog(getActivity(), anchor, pl, plTracks, this);
    }

    public void showPlaylistActionsMenu(View anchor, OfflineStorage.Playlist pl) {
        LocalDownloadsDialogs.showPlaylistActionsMenu(getActivity(), anchor, pl, this);
    }

    @Override
    public void onDownloadStateChanged(String videoId, boolean completed) {
        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            refreshDownloadStatusUI();
            if (completed) {
                LocalDownloadsUIState.diskReloadNeeded = true;
                showTracks();
            }
        });
    }

    @Override
    public void onPlaybackChanged(String path, String title, boolean playing, int position, int duration) {
        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() ->
                LocalDownloadsPlayer.handlePlaybackChanged(activity, path, title, playing, position, duration));
    }

    private LinearLayout createLiveDownloadBanner(Context ctx) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(
                LocalDownloadsUIState.dp(ctx, 12),
                LocalDownloadsUIState.dp(ctx, 8),
                LocalDownloadsUIState.dp(ctx, 12),
                LocalDownloadsUIState.dp(ctx, 8)
        );
        box.setBackground(LocalDownloadsUIState.glassCardDrawable(ctx, LocalDownloadsUIState.dp(ctx, 12), true));

        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(-1, -2);
        boxLp.setMargins(
                LocalDownloadsUIState.dp(ctx, 12),
                LocalDownloadsUIState.dp(ctx, 2),
                LocalDownloadsUIState.dp(ctx, 12),
                LocalDownloadsUIState.dp(ctx, 6)
        );
        box.setLayoutParams(boxLp);

        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LocalDownloadsUIState.downloadBannerTitle = LocalDownloadsUIState.text(ctx, "İndiriliyor...", 13, Color.WHITE);
        LocalDownloadsUIState.downloadBannerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LocalDownloadsUIState.downloadBannerTitle.setSingleLine(true);
        LocalDownloadsUIState.downloadBannerTitle.setEllipsize(TextUtils.TruncateAt.END);

        LocalDownloadsUIState.downloadBannerSub = LocalDownloadsUIState.text(ctx, "", 11, LocalDownloadsUIState.secondary());
        LocalDownloadsUIState.downloadBannerSub.setSingleLine(true);
        textCol.addView(LocalDownloadsUIState.downloadBannerTitle);
        textCol.addView(LocalDownloadsUIState.downloadBannerSub);
        topRow.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1f));

        LocalDownloadsUIState.downloadBannerPauseBtn = LocalDownloadsUIState.createPillButton(ctx, "⏸ Duraklat", false);
        LocalDownloadsUIState.downloadBannerPauseBtn.setOnClickListener(v -> {
            boolean paused = !LocalDownloadManager.isPaused();
            LocalDownloadManager.setPaused(paused);
            Utils.showToastShort(paused ? "İndirmeler duraklatıldı" : "İndirmeler devam ediyor");
            refreshDownloadStatusUI();
        });
        topRow.addView(LocalDownloadsUIState.downloadBannerPauseBtn);

        TextView stopAllBtn = LocalDownloadsUIState.createPillButton(ctx, "✕ İptal", false);
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(-2, -2);
        stopLp.setMargins(LocalDownloadsUIState.dp(ctx, 6), 0, LocalDownloadsUIState.dp(ctx, 6), 0);
        stopAllBtn.setLayoutParams(stopLp);
        stopAllBtn.setOnClickListener(v -> confirmCancelAllDownloads());
        topRow.addView(stopAllBtn);

        TextView manageBtn = LocalDownloadsUIState.createPillButton(ctx, "Kuyruk", true);
        manageBtn.setOnClickListener(v -> showDownloadManagerDialog());
        topRow.addView(manageBtn);

        box.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        LocalDownloadsUIState.downloadBannerProgress = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        LocalDownloadsUIState.downloadBannerProgress.setMax(100);
        LocalDownloadsUIState.downloadBannerProgress.setProgressDrawable(
                LocalDownloadsUIState.createThemeProgressDrawable(LocalDownloadsUIState.dp(ctx, 4), true));
        LinearLayout.LayoutParams progLp = new LinearLayout.LayoutParams(-1, LocalDownloadsUIState.dp(ctx, 5));
        progLp.setMargins(0, LocalDownloadsUIState.dp(ctx, 6), 0, 0);
        box.addView(LocalDownloadsUIState.downloadBannerProgress, progLp);

        box.setOnClickListener(v -> showDownloadManagerDialog());
        box.setVisibility(View.GONE);
        return box;
    }

    private LinearLayout createBatchSelectionBar(Context ctx) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(
                LocalDownloadsUIState.dp(ctx, 12),
                LocalDownloadsUIState.dp(ctx, 8),
                LocalDownloadsUIState.dp(ctx, 12),
                LocalDownloadsUIState.dp(ctx, 8)
        );
        bar.setBackground(LocalDownloadsUIState.glassCardDrawable(ctx, LocalDownloadsUIState.dp(ctx, 12), true));

        LinearLayout top = new LinearLayout(ctx);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LocalDownloadsUIState.batchCountText = LocalDownloadsUIState.text(ctx, "0 şarkı seçildi", 13, Color.WHITE);
        LocalDownloadsUIState.batchCountText.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(LocalDownloadsUIState.batchCountText, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView deleteSelectedBtn = LocalDownloadsUIState.createPillButton(ctx, "🗑️ Seçilenleri Sil", true);
        deleteSelectedBtn.setOnClickListener(v -> confirmDeleteSelectedTracks());
        top.addView(deleteSelectedBtn);

        TextView addToPlBtn = LocalDownloadsUIState.createPillButton(ctx, "📁 Listeye Ekle", false);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-2, -2);
        addLp.setMargins(LocalDownloadsUIState.dp(ctx, 6), 0, 0, 0);
        addToPlBtn.setLayoutParams(addLp);
        addToPlBtn.setOnClickListener(v -> LocalDownloadsDialogs.showBatchAddToPlaylistMenu(ctx, addToPlBtn, this));
        top.addView(addToPlBtn);

        bar.addView(top, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView quickScroll = new HorizontalScrollView(ctx);
        quickScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout quickRow = new LinearLayout(ctx);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.setPadding(0, LocalDownloadsUIState.dp(ctx, 6), 0, 0);

        TextView selectAllBtn = LocalDownloadsUIState.createPillButton(ctx, "Tümünü Seç", false);
        selectAllBtn.setOnClickListener(v -> {
            for (OfflineTrack t : LocalDownloadsUIState.currentDisplayedTracks) {
                LocalDownloadsUIState.selectedVideoIds.add(t.videoId());
            }
            showTracks();
        });
        quickRow.addView(selectAllBtn);

        LinearLayout.LayoutParams oLp = new LinearLayout.LayoutParams(-2, -2);
        oLp.setMargins(LocalDownloadsUIState.dp(ctx, 6), 0, 0, 0);

        TextView selectOrphansBtn = LocalDownloadsUIState.createPillButton(ctx, "Sahipsizleri Seç", false);
        selectOrphansBtn.setLayoutParams(oLp);
        selectOrphansBtn.setOnClickListener(v -> {
            Set<String> inPlaylists = LocalDownloadsUIState.getAllPlaylistTrackIds();
            for (OfflineTrack t : LocalDownloadsUIState.tracks) {
                if (!inPlaylists.contains(t.videoId())) LocalDownloadsUIState.selectedVideoIds.add(t.videoId());
            }
            showTracks();
        });
        quickRow.addView(selectOrphansBtn);

        TextView selectBrokenBtn = LocalDownloadsUIState.createPillButton(ctx, "Hatalıları Seç", false);
        selectBrokenBtn.setLayoutParams(oLp);
        selectBrokenBtn.setOnClickListener(v -> {
            for (OfflineTrack t : LocalDownloadsUIState.tracks) {
                if (LocalDownloadsUIState.isBrokenOrIncompleteTrack(t)) {
                    LocalDownloadsUIState.selectedVideoIds.add(t.videoId());
                }
            }
            showTracks();
        });
        quickRow.addView(selectBrokenBtn);

        TextView clearSelBtn = LocalDownloadsUIState.createPillButton(ctx, "Seçimi Temizle", false);
        clearSelBtn.setLayoutParams(oLp);
        clearSelBtn.setOnClickListener(v -> {
            LocalDownloadsUIState.selectedVideoIds.clear();
            showTracks();
        });
        quickRow.addView(clearSelBtn);

        TextView closeSelBtn = LocalDownloadsUIState.createPillButton(ctx, "✕ Kapat", false);
        closeSelBtn.setLayoutParams(oLp);
        closeSelBtn.setOnClickListener(v -> {
            LocalDownloadsUIState.selectionMode = false;
            LocalDownloadsUIState.selectedVideoIds.clear();
            LocalDownloadsUIState.currentFilterMode = LocalDownloadsUIState.FilterMode.ALL;
            showTracks();
        });
        quickRow.addView(closeSelBtn);

        quickScroll.addView(quickRow);
        bar.addView(quickScroll, new LinearLayout.LayoutParams(-1, -2));
        bar.setVisibility(View.GONE);
        return bar;
    }

    public void refreshDownloadStatusUI() {
        List<LocalDownloadManager.QueueItem> queue = LocalDownloadManager.getQueueSnapshot();
        boolean paused = LocalDownloadManager.isPaused();

        if (LocalDownloadsUIState.downloadBanner != null) {
            if (queue.isEmpty()) {
                LocalDownloadsUIState.downloadBanner.setVisibility(View.GONE);
            } else {
                LocalDownloadsUIState.downloadBanner.setVisibility(View.VISIBLE);
                LocalDownloadManager.QueueItem activeItem = null;
                for (LocalDownloadManager.QueueItem item : queue) {
                    if (item.active()) {
                        activeItem = item;
                        break;
                    }
                }
                if (activeItem == null) activeItem = queue.get(0);

                String statusPrefix = paused ? "⏸ Duraklatıldı: " : "⬇️ İndiriliyor: ";
                String speedStr = activeItem.speedBytesPerSec() > 0
                        ? " • " + LocalDownloadsUIState.formatSpeed(activeItem.speedBytesPerSec())
                        : "";
                LocalDownloadsUIState.downloadBannerTitle.setText(statusPrefix + activeItem.title());
                LocalDownloadsUIState.downloadBannerSub.setText("Kuyrukta " + queue.size()
                        + " şarkı • " + activeItem.playlistName()
                        + " • %" + activeItem.progress() + speedStr);
                LocalDownloadsAnimator.animateProgress(LocalDownloadsUIState.downloadBannerProgress, Math.max(2, Math.min(100, activeItem.progress())));
                LocalDownloadsUIState.downloadBannerPauseBtn.setText(paused ? "▶ Devam" : "⏸ Duraklat");
            }
        }

        if (LocalDownloadsUIState.queueManagerDialog != null
                && LocalDownloadsUIState.queueManagerDialog.isShowing()
                && LocalDownloadsUIState.queueDialogListContainer != null) {
            LocalDownloadsDialogs.populateQueueDialogItems(getActivity(), queue, paused, this);
        }
    }

    public void shareTrackLink(OfflineTrack track) {
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, track.displayTitle() + " - " + track.displayArtist()
                    + "\nhttps://music.youtube.com/watch?v=" + track.videoId());
            startActivity(Intent.createChooser(share, "Şarkıyı Paylaş"));
        } catch (Exception ignored) {}
    }

    public void pickImageFromGallery(int requestCode) {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Görsel Seçin"), requestCode);
        } catch (Exception ex) {
            Utils.showToastShort("Galeri açılamadı");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        Activity act = getActivity();
        if (act == null) return;

        Utils.submitOnBackgroundThread(() -> {
            try (InputStream in = act.getContentResolver().openInputStream(uri)) {
                Bitmap raw = BitmapFactory.decodeStream(in);
                if (raw == null) return null;
                Bitmap scaled = scaleDownBitmap(raw, 1080);

                if (requestCode == LocalDownloadsUIState.REQ_PICK_PLAYLIST_COVER
                        && LocalDownloadsUIState.pendingCoverPlaylistName != null) {
                    OfflineStorage.setPlaylistCoverBitmap(musicRoot, LocalDownloadsUIState.pendingCoverPlaylistName, scaled);
                    LocalDownloadsUIState.ARTWORK_CACHE.evictAll();
                    LocalDownloadsUIState.diskReloadNeeded = true;
                    Utils.runOnMainThread(() -> {
                        Utils.showToastShort("Oynatma listesi kapağı güncellendi");
                        showTracks();
                    });
                } else if (requestCode == LocalDownloadsUIState.REQ_PICK_THEME_WALLPAPER) {
                    if (!musicRoot.isDirectory()) musicRoot.mkdirs();
                    OfflineStorage.writeJpeg(new File(musicRoot, LocalDownloadsUIState.CUSTOM_WALLPAPER_FILE), scaled);
                    LocalDownloadsUIState.customWallpaperActive = true;
                    LocalDownloadsUIState.savePreferences(act);
                    LocalDownloadsUIState.reloadCustomWallpaper(musicRoot);
                    Utils.runOnMainThread(() -> {
                        Utils.showToastShort("Özel duvar kağıdı kaydedildi");
                        LocalDownloadsUIState.refreshAllThemeVisuals(this);
                    });
                } else if (requestCode == LocalDownloadsUIState.REQ_PICK_TRACK_COVER) {
                    // GÖREV 5: Tekil şarkı kapağını güncelle
                    if (LocalDownloadsUIState.pendingCoverTrackId != null) {
                        String vid = LocalDownloadsUIState.pendingCoverTrackId;
                        File customCoverFile = new File(musicRoot, vid + "_custom.jpg");
                        OfflineStorage.writeJpeg(customCoverFile, scaled);
                        
                        LocalDownloadsUIState.ARTWORK_CACHE.evictAll();
                        LocalDownloadsUIState.TRACK_CACHE.clear();
                        LocalDownloadsUIState.diskReloadNeeded = true;
                        
                        Utils.runOnMainThread(() -> {
                            Utils.showToastShort("✓ Şarkı kapağı başarıyla güncellendi");
                            showTracks();
                            
                            // Eğer çalan şarkıysa oynatıcıyı da güncelle
                            OfflineTrack updatedTrack = LocalDownloadsUIState.findById(vid);
                            if (updatedTrack != null && LocalDownloadsUIState.playingPath.equals(updatedTrack.audioFile().getAbsolutePath())) {
                                LocalDownloadsPlayer.applyTrackVisuals(act, updatedTrack);
                            }
                        });
                        LocalDownloadsUIState.pendingCoverTrackId = null;
                    }
                }
            } catch (Exception ex) {
                Logger.printException(() -> "Image selection failed", ex);
                Utils.runOnMainThread(() -> Utils.showToastShort("Görsel yüklenemedi"));
            }
            return null;
        });
    }

    private static Bitmap scaleDownBitmap(Bitmap src, int maxDimension) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxDimension && h <= maxDimension) return src;
        float ratio = Math.min((float) maxDimension / w, (float) maxDimension / h);
        return Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w * ratio)), Math.max(1, Math.round(h * ratio)), true);
    }

    public void confirmCancelAllDownloads() {
        confirm("İndirmeleri Durdur",
                "Bekleyen ve aktif olan tüm indirmeler iptal edilip yarım kalan (.part) dosyalar silinsin mi?",
                () -> {
                    LocalDownloadManager.cancelAllDownloads(musicRoot);
                    cleanPartFiles();
                    refreshDownloadStatusUI();
                    Utils.showToastShort("Tüm indirmeler durduruldu");
                });
    }

    public void confirmDeleteSelectedTracks() {
        if (LocalDownloadsUIState.selectedVideoIds.isEmpty()) {
            Utils.showToastShort("Önce silinecek şarkıları seçin");
            return;
        }
        long bytes = 0;
        List<OfflineTrack> toDelete = new ArrayList<>();
        for (OfflineTrack t : LocalDownloadsUIState.tracks) {
            if (LocalDownloadsUIState.selectedVideoIds.contains(t.videoId())) {
                toDelete.add(t);
                bytes += t.audioFile().length();
            }
        }
        confirm("Seçilenleri Topluca Sil",
                toDelete.size() + " şarkı (" + LocalDownloadsUIState.formatSize(bytes) + " MB) cihazdan kalıcı olarak silinsin mi?",
                () -> {
                    for (OfflineTrack t : toDelete) {
                        OfflineStorage.delete(t.audioFile());
                        deleteSidecars(t.videoId());
                        OfflineStorage.removeTrackFromAllPlaylists(musicRoot, t.videoId());
                        LocalDownloadsUIState.TRACK_CACHE.remove(t.audioFile().getAbsolutePath());
                    }
                    LocalDownloadsUIState.selectedVideoIds.clear();
                    LocalDownloadsUIState.diskReloadNeeded = true;
                    showTracks();
                    Utils.showToastShort(toDelete.size() + " şarkı silindi");
                });
    }

    public void confirmDeletePlaylist(String playlistName) {
        confirm("Listeyi Sil", "\"" + playlistName + "\" oynatma listesi silinsin mi?", () -> {
            OfflineStorage.deletePlaylist(musicRoot, playlistName);
            if (playlistName.equalsIgnoreCase(LocalDownloadsUIState.selectedPlaylist)) {
                LocalDownloadsUIState.selectedPlaylist = null;
            }
            LocalDownloadsUIState.diskReloadNeeded = true;
            showTracks();
            Utils.showToastShort("Liste silindi");
        });
    }

    public void confirmDelete(OfflineTrack track) {
        confirm(str("morphe_music_downloads_delete_track_title"),
                str("morphe_music_downloads_delete_track_message", track.displayTitle()), () -> deleteTrack(track));
    }

    public void confirmDeleteAll() {
        if (!LocalDownloadsUIState.tracks.isEmpty()) {
            confirm(str("morphe_music_downloads_delete_all_title"),
                    str("morphe_music_downloads_delete_all_message"), this::deleteAll);
        }
    }

    public void confirm(String title, String message, Runnable onConfirm) {
        CustomDialog.create(getActivity(), title, message, null, str("morphe_music_downloads_delete"),
                onConfirm, () -> {}, null, null, false).first.show();
    }

    public void deleteAll() {
        LocalDownloadManager.cancelAllDownloads(musicRoot);
        for (OfflineTrack t : new ArrayList<>(LocalDownloadsUIState.tracks)) {
            OfflineStorage.delete(t.audioFile());
            deleteSidecars(t.videoId());
            OfflineStorage.removeTrackFromAllPlaylists(musicRoot, t.videoId());
        }
        LocalDownloadsUIState.ARTWORK_CACHE.evictAll();
        LocalDownloadsUIState.TRACK_CACHE.clear();
        LocalDownloadsUIState.selectedVideoIds.clear();
        LocalDownloadsUIState.diskReloadNeeded = true;
        showTracks();
        Utils.showToastShort(str("morphe_music_downloads_all_deleted"));
    }

    public void deleteTrack(OfflineTrack t) {
        boolean deleted = t.audioFile().delete();
        deleteSidecars(t.videoId());
        OfflineStorage.removeTrackFromAllPlaylists(musicRoot, t.videoId());
        LocalDownloadsUIState.TRACK_CACHE.remove(t.audioFile().getAbsolutePath());
        LocalDownloadsUIState.selectedVideoIds.remove(t.videoId());
        if (!deleted) {
            Utils.showToastShort(str("morphe_music_downloads_track_delete_failed"));
            return;
        }
        LocalDownloadsUIState.diskReloadNeeded = true;
        showTracks();
        Utils.showToastShort(str("morphe_music_downloads_track_deleted"));
    }

    public void deleteSidecars(String id) {
        OfflineStorage.delete(new File(musicRoot, id + ".json"));
        OfflineStorage.delete(new File(musicRoot, id + ".jpg"));
        OfflineStorage.delete(new File(musicRoot, id + "_custom.jpg")); // GÖREV 5: Özel kapağı da sil
        OfflineStorage.delete(new File(musicRoot, id + ".lrc"));
        OfflineStorage.delete(new File(musicRoot, id + ".webm.part"));
        OfflineStorage.delete(new File(musicRoot, id + ".m4a.part"));
    }

    public int cleanPartFiles() {
        int count = 0;
        File[] partFiles = musicRoot.listFiles(f -> f.isFile() && f.getName().endsWith(".part"));
        if (partFiles != null) {
            for (File f : partFiles) {
                OfflineStorage.delete(f);
                count++;
            }
        }
        return count;
    }
}