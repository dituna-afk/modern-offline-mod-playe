package app.morphe.extension.music.patches.downloads;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public final class LocalDownloadsPlayer {

    private static final class LyricPair {
        final long timeMs;
        final String original;
        String translated;

        LyricPair(long timeMs, String original, String translated) {
            this.timeMs = timeMs;
            this.original = original;
            this.translated = translated;
        }
    }

    @Nullable private static Dialog fullPlayerDialog;
    @Nullable private static LinearLayout fullPlayerRoot;
    @Nullable private static LinearLayout fullPlayerTopBar;
    @Nullable private static HorizontalScrollView fullPlayerActionsScroll;
    @Nullable private static LinearLayout fullPlayerActionsRow;
    @Nullable private static LinearLayout fullPlayerControlsRow;
    @Nullable private static TextView fullPlayerEffectsPill;
    @Nullable private static ProgressBar miniProgressBar;
    @Nullable private static ImageView miniArtwork, fullArtwork;
    @Nullable private static TextView miniTitle, miniArtist, fullTitle, fullArtist, fullCurrentTime, fullTotalTime;
    @Nullable private static ImageButton miniPlay, fullPlayBtn, fullPrevBtn, fullNextBtn, fullShuffleBtn, fullRepeatBtn;
    @Nullable private static SeekBar fullSeek;

    @Nullable private static LocalDownloadsAnimator.HaloGlowDrawable fullPlayerHaloDrawable;
    @Nullable private static LocalDownloadsAnimator.FlowingGradientDrawable fullPlayerGradientDrawable;
    @Nullable private static View fullPlayerHaloView;

    private static boolean showingPause = false;
    private static boolean userSeeking = false;
    private static boolean likedCurrentTrack = false;
    private static boolean breathingActive = false;
    private static boolean openingFullPlayer = false;
    private static boolean dismissingWithAnimation = false;
    private static boolean focusModeActiveLocal = false;

    private static boolean isFayeModeActive = false;
    @Nullable private static LocalDownloadsAnimator.FayeFlowingPaintDrawable fayeBgDrawable;

    private LocalDownloadsPlayer() {}

    public static boolean isFullPlayerShowing() {
        return fullPlayerDialog != null && fullPlayerDialog.isShowing();
    }

    public static void dismissFullPlayerIfShowing() {
        if (fullPlayerDialog != null && fullPlayerDialog.isShowing()) {
            dismissWithAnimation();
        }
    }

    private static void dismissWithAnimation() {
        if (dismissingWithAnimation) return;

        final Dialog dialogToDismiss = fullPlayerDialog;
        final LinearLayout rootToDismiss = fullPlayerRoot;
        final ImageView artworkToDismiss = fullArtwork;
        if (dialogToDismiss == null || !dialogToDismiss.isShowing()) {
            dismissingWithAnimation = false;
            return;
        }

        dismissingWithAnimation = true;
        LocalDownloadsAnimator.animatePlayerMinimize(rootToDismiss, artworkToDismiss, () -> {
            try {
                if (dialogToDismiss.isShowing()) {
                    dialogToDismiss.dismiss();
                }
            } catch (Exception ignored) {
            } finally {
                if (fullPlayerDialog != dialogToDismiss) {
                    dismissingWithAnimation = false;
                }
            }
        });
    }

    public static LinearLayout createOriginalYtMusicMiniPlayer(Context ctx, LocalDownloadsFragment fragment) {
        LinearLayout outer = new LinearLayout(ctx);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(LocalDownloadsUIState.currentTheme.varBgMid);
        outer.setClickable(true);
        outer.setFocusable(true);
        outer.setOnClickListener(v -> openFullPlayerSafely(fragment));

        miniProgressBar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        miniProgressBar.setMax(1000);
        miniProgressBar.setProgressDrawable(
                LocalDownloadsUIState.createThemeProgressDrawable(LocalDownloadsUIState.dp(ctx, 2), false));
        outer.addView(miniProgressBar, new LinearLayout.LayoutParams(-1, LocalDownloadsUIState.dp(ctx, 2)));

        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(
                LocalDownloadsUIState.dp(ctx, 10),
                LocalDownloadsUIState.dp(ctx, 7),
                LocalDownloadsUIState.dp(ctx, 6),
                LocalDownloadsUIState.dp(ctx, 7)
        );

        miniArtwork = LocalDownloadsUIState.artworkView(ctx, null, 6);
        bar.addView(miniArtwork, new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(ctx, 44), LocalDownloadsUIState.dp(ctx, 44)));

        LinearLayout labels = new LinearLayout(ctx);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(LocalDownloadsUIState.dp(ctx, 10), 0, LocalDownloadsUIState.dp(ctx, 6), 0);

        miniTitle = LocalDownloadsUIState.text(ctx, "", 14, Color.WHITE);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(TextUtils.TruncateAt.END);
        miniTitle.setTypeface(Typeface.DEFAULT_BOLD);

        miniArtist = LocalDownloadsUIState.text(ctx, "", 12, Color.argb(175, 255, 255, 255));
        miniArtist.setSingleLine(true);
        miniArtist.setEllipsize(TextUtils.TruncateAt.END);

        labels.addView(miniTitle);
        labels.addView(miniArtist);
        bar.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageButton miniPrev = new ImageButton(ctx);
        miniPrev.setBackground(LocalDownloadsUIState.createCircleRipple());
        miniPrev.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(
                ctx, LocalDownloadsUIState.ICON_SKIP_PREV, Color.WHITE, 0));
        miniPrev.setOnClickListener(v -> OfflinePlaybackService.skipPrevious(ctx));
        bar.addView(miniPrev, new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(ctx, 40), LocalDownloadsUIState.dp(ctx, 40)));

        miniPlay = new ImageButton(ctx);
        miniPlay.setBackground(LocalDownloadsUIState.createCircleRipple());
        miniPlay.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(
                ctx, LocalDownloadsUIState.ICON_PLAY, Color.WHITE, 0));
        miniPlay.setOnClickListener(v -> OfflinePlaybackService.toggle(ctx));
        bar.addView(miniPlay, new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(ctx, 42), LocalDownloadsUIState.dp(ctx, 42)));

        ImageButton miniNext = new ImageButton(ctx);
        miniNext.setBackground(LocalDownloadsUIState.createCircleRipple());
        miniNext.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(
                ctx, LocalDownloadsUIState.ICON_SKIP_NEXT, Color.WHITE, 0));
        miniNext.setOnClickListener(v -> OfflinePlaybackService.skipNext(ctx));
        bar.addView(miniNext, new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(ctx, 40), LocalDownloadsUIState.dp(ctx, 40)));

        outer.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        return outer;
    }

    private static void openFullPlayerSafely(LocalDownloadsFragment fragment) {
        if (fragment == null) return;
        Activity activity = fragment.getActivity();
        if (activity == null || activity.isFinishing()) return;
        if (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed()) return;
        showFullScreenPlayer(activity, fragment);
    }

    public static void showFullScreenPlayer(Activity activity, LocalDownloadsFragment fragment) {
        if (activity == null || fragment == null || activity.isFinishing()) return;
        if (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed()) return;

        if (openingFullPlayer && (fullPlayerDialog == null || !fullPlayerDialog.isShowing())) {
            openingFullPlayer = false;
        }
        if (openingFullPlayer || (fullPlayerDialog != null && fullPlayerDialog.isShowing())) return;
        openingFullPlayer = true;

        try {
            if (LocalDownloadsUIState.currentTheme == LocalDownloadsUIState.ThemePalette.FAYE) {
                showFayeFullScreenPlayer(activity, fragment);
                return;
            }

            fullPlayerDialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            fullPlayerRoot = new LinearLayout(activity);
            fullPlayerRoot.setOrientation(LinearLayout.VERTICAL);
            fullPlayerRoot.setGravity(Gravity.CENTER_HORIZONTAL);
            fullPlayerRoot.setElevation(LocalDownloadsUIState.dp(activity, 40f));
            fullPlayerRoot.setPadding(
                    LocalDownloadsUIState.dp(activity, 24),
                    LocalDownloadsUIState.dp(activity, 18),
                    LocalDownloadsUIState.dp(activity, 24),
                    LocalDownloadsUIState.dp(activity, 22)
            );

            LinearLayout topBar = new LinearLayout(activity);
            topBar.setOrientation(LinearLayout.HORIZONTAL);
            topBar.setGravity(Gravity.CENTER_VERTICAL);
            topBar.setPadding(0, LocalDownloadsUIState.dp(activity, 2), 0, LocalDownloadsUIState.dp(activity, 10));
            fullPlayerTopBar = topBar;

            ImageButton collapseBtn = new ImageButton(activity);
            collapseBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(
                    activity, LocalDownloadsUIState.ICON_CHEVRON_DOWN, Color.WHITE, 0));
            collapseBtn.setBackground(LocalDownloadsUIState.createCircleRipple());
            collapseBtn.setOnClickListener(v -> dismissWithAnimation());
            topBar.addView(collapseBtn, new LinearLayout.LayoutParams(
                    LocalDownloadsUIState.dp(activity, 44), LocalDownloadsUIState.dp(activity, 44)));

            FrameLayout centerToggleWrap = new FrameLayout(activity);
            LinearLayout songVideoPill = new LinearLayout(activity);
            songVideoPill.setOrientation(LinearLayout.HORIZONTAL);
            songVideoPill.setBackground(LocalDownloadsUIState.glassCardDrawable(
                    activity, LocalDownloadsUIState.dp(activity, 18), false));

            TextView songTab = LocalDownloadsUIState.text(activity, "Şarkı", 13, Color.WHITE);
            songTab.setTypeface(Typeface.DEFAULT_BOLD);
            songTab.setPadding(
                    LocalDownloadsUIState.dp(activity, 18),
                    LocalDownloadsUIState.dp(activity, 6),
                    LocalDownloadsUIState.dp(activity, 18),
                    LocalDownloadsUIState.dp(activity, 6)
            );
            songTab.setBackground(LocalDownloadsUIState.glassCardDrawable(
                    activity, LocalDownloadsUIState.dp(activity, 18), true));

            TextView queueTab = LocalDownloadsUIState.text(activity, "Sıradaki", 13, LocalDownloadsUIState.secondary());
            queueTab.setPadding(
                    LocalDownloadsUIState.dp(activity, 16),
                    LocalDownloadsUIState.dp(activity, 6),
                    LocalDownloadsUIState.dp(activity, 16),
                    LocalDownloadsUIState.dp(activity, 6)
            );
            queueTab.setOnClickListener(v -> showUpNextQueueDialog(activity));

            songVideoPill.addView(songTab);
            songVideoPill.addView(queueTab);
            centerToggleWrap.addView(songVideoPill, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            topBar.addView(centerToggleWrap, new LinearLayout.LayoutParams(0, -2, 1f));

            ImageButton moreBtn = LocalDownloadsUIState.icon(
                    activity, "yt_outline_experimental_overflow_vertical_vd_theme_24", "Menü");
            moreBtn.setColorFilter(Color.WHITE);
            moreBtn.setOnClickListener(v -> {
                OfflineTrack cur = LocalDownloadsUIState.findByPath(LocalDownloadsUIState.playingPath);
                if (cur != null) fragment.showTrackBottomSheet(cur);
            });
            topBar.addView(moreBtn, new LinearLayout.LayoutParams(
                    LocalDownloadsUIState.dp(activity, 44), LocalDownloadsUIState.dp(activity, 44)));
            fullPlayerRoot.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

            FrameLayout artWrap = new FrameLayout(activity);
            int artSize = Math.min(
                    LocalDownloadsUIState.dp(activity, 320),
                    activity.getResources().getDisplayMetrics().widthPixels - LocalDownloadsUIState.dp(activity, 52)
            );

            fullPlayerHaloView = new View(activity);
            fullPlayerHaloDrawable = LocalDownloadsAnimator.createHaloDrawable(activity);
            fullPlayerHaloView.setBackground(fullPlayerHaloDrawable);
            int haloSize = artSize + LocalDownloadsUIState.dp(activity, 110);
            artWrap.addView(fullPlayerHaloView, new FrameLayout.LayoutParams(
                    haloSize, haloSize, Gravity.CENTER));

            fullArtwork = LocalDownloadsUIState.artworkView(activity, null, LocalDownloadsUIState.FULL_ARTWORK_CORNER_DP);
            fullArtwork.setElevation(LocalDownloadsUIState.dp(activity, 16f));
            artWrap.addView(fullArtwork, new FrameLayout.LayoutParams(artSize, artSize, Gravity.CENTER));

            GestureDetector playerGestureDetector = new GestureDetector(activity,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override public boolean onDown(MotionEvent e) { return true; }
                        @Override
                        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                            if (e1 == null || e2 == null) return false;
                            float dx = e2.getX() - e1.getX();
                            float dy = e2.getY() - e1.getY();

                            if (dy < -LocalDownloadsUIState.dp(activity, 70) && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                                showUpNextQueueDialog(activity);
                                return true;
                            }
                            if (dy > LocalDownloadsUIState.dp(activity, 75) && dy > Math.abs(dx) * 1.2f) {
                                dismissWithAnimation();
                                return true;
                            }
                            if (Math.abs(dx) > LocalDownloadsUIState.dp(activity, 60) && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                                boolean next = dx < 0;
                                LocalDownloadsAnimator.animateArtworkSwipe(artWrap, next, LocalDownloadsUIState.dp(activity, 16));
                                if (next) OfflinePlaybackService.skipNext(activity);
                                else OfflinePlaybackService.skipPrevious(activity);
                                return true;
                            }
                            return false;
                        }
                    });
            artWrap.setOnTouchListener((v, event) -> playerGestureDetector.onTouchEvent(event));
            fullPlayerRoot.addView(artWrap, new LinearLayout.LayoutParams(-1, 0, 1f));

            LinearLayout metaCol = new LinearLayout(activity);
            metaCol.setOrientation(LinearLayout.VERTICAL);
            metaCol.setPadding(
                    LocalDownloadsUIState.dp(activity, 4),
                    LocalDownloadsUIState.dp(activity, 12),
                    LocalDownloadsUIState.dp(activity, 4),
                    LocalDownloadsUIState.dp(activity, 10)
            );

            fullTitle = LocalDownloadsUIState.text(activity, "", 22, Color.WHITE);
            fullTitle.setTypeface(Typeface.DEFAULT_BOLD);
            fullTitle.setSingleLine(true);
            fullTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            fullTitle.setMarqueeRepeatLimit(-1);
            fullTitle.setSelected(true);

            fullArtist = LocalDownloadsUIState.text(activity, "", 15, Color.argb(190, 255, 255, 255));
            fullArtist.setSingleLine(true);
            fullArtist.setEllipsize(TextUtils.TruncateAt.END);
            fullArtist.setPadding(0, LocalDownloadsUIState.dp(activity, 3), 0, 0);

            metaCol.addView(fullTitle);
            metaCol.addView(fullArtist);
            fullPlayerRoot.addView(metaCol, new LinearLayout.LayoutParams(-1, -2));

            HorizontalScrollView actionsScroll = new HorizontalScrollView(activity);
            actionsScroll.setHorizontalScrollBarEnabled(false);
            actionsScroll.setPadding(0, LocalDownloadsUIState.dp(activity, 2), 0, LocalDownloadsUIState.dp(activity, 12));
            fullPlayerActionsScroll = actionsScroll;

            LinearLayout actionsRow = new LinearLayout(activity);
            actionsRow.setOrientation(LinearLayout.HORIZONTAL);
            actionsRow.setGravity(Gravity.CENTER_VERTICAL);
            fullPlayerActionsRow = actionsRow;

            TextView likePill = createYtActionPill(activity, "👍 Beğen  |  👎");
            likePill.setOnClickListener(v -> {
                likedCurrentTrack = !likedCurrentTrack;
                likePill.setText(likedCurrentTrack ? "❤️ Beğenildi  |  👎" : "👍 Beğen  |  👎");
                OfflineTrack cur = LocalDownloadsUIState.findByPath(LocalDownloadsUIState.playingPath);
                if (cur != null && likedCurrentTrack) {
                    OfflineStorage.createPlaylist(fragment.getMusicRoot(), OfflineStorage.SPECIAL_LIKED_SONGS, "LM");
                    OfflineStorage.addTrackToPlaylist(fragment.getMusicRoot(), OfflineStorage.SPECIAL_LIKED_SONGS, cur.videoId());
                    LocalDownloadsUIState.diskReloadNeeded = true;
                    Utils.showToastShort("Beğenilenler listesine eklendi");
                }
            });
            actionsRow.addView(likePill);

            TextView lyricsPill = createYtActionPill(activity, "🎤 Sözler / Şiir (LRC)");
            lyricsPill.setOnClickListener(v -> showSyncedLyricsDialog(activity, fragment));
            actionsRow.addView(lyricsPill);

            TextView sleepPill = createYtActionPill(activity, "🌙 Uyku Zamanlayıcısı");
            sleepPill.setOnClickListener(v -> showSleepTimerDialog(activity, sleepPill));
            actionsRow.addView(sleepPill);

            TextView preampPill = createYtActionPill(activity, "🔊 Ses Yükseltici");
            preampPill.setOnClickListener(v -> showPreampBoosterDialog(activity));
            actionsRow.addView(preampPill);

            TextView queuePill = createYtActionPill(activity, "🎶 Sıradaki Listesi");
            queuePill.setOnClickListener(v -> showUpNextQueueDialog(activity));
            actionsRow.addView(queuePill);

            TextView sharePill = createYtActionPill(activity, "↗ Paylaş");
            sharePill.setOnClickListener(v -> {
                OfflineTrack current = LocalDownloadsUIState.findByPath(LocalDownloadsUIState.playingPath);
                if (current != null) fragment.shareTrackLink(current);
            });
            actionsRow.addView(sharePill);

            TextView effectsPill = createYtActionPill(activity, buildEffectsPillLabel(activity));
            fullPlayerEffectsPill = effectsPill;
            effectsPill.setOnClickListener(v -> LocalDownloadsDialogs.showPlayerEffectsDialog(activity, fragment));
            actionsRow.addView(effectsPill);

            TextView focusPill = createYtActionPill(activity,
                    LocalDownloadsUIState.isFocusMode(activity) ? "🌞 Normal Görünüm" : "🌙 Odak Modu");
            focusPill.setOnClickListener(v -> {
                boolean newState = !LocalDownloadsUIState.isFocusMode(activity);
                LocalDownloadsUIState.setFocusMode(activity, newState);
                focusPill.setText(newState ? "🌞 Normal Görünüm" : "🌙 Odak Modu");
                applyFocusModeVisibility(activity);
            });
            actionsRow.addView(focusPill);

            TextView lowPowerPill = createYtActionPill(activity,
                    LocalDownloadsUIState.isLowPowerMode(activity) ? "🔋 Düşük Güç: AÇIK" : "🔋 Düşük Güç: KAPALI");
            lowPowerPill.setOnClickListener(v -> {
                boolean newState = !LocalDownloadsUIState.isLowPowerMode(activity);
                LocalDownloadsUIState.setLowPowerMode(activity, newState);
                lowPowerPill.setText(newState ? "🔋 Düşük Güç: AÇIK" : "🔋 Düşük Güç: KAPALI");
                applyFullPlayerBackground(activity);
                applyEffectsVisibility(activity);
                if (fullPlayerEffectsPill != null) {
                    fullPlayerEffectsPill.setText(buildEffectsPillLabel(activity));
                }
            });
            actionsRow.addView(lowPowerPill);

            actionsScroll.addView(actionsRow);
            fullPlayerRoot.addView(actionsScroll, new LinearLayout.LayoutParams(-1, -2));

            fullSeek = createFullPlayerSeekBar(activity);
            fullPlayerRoot.addView(fullSeek, new LinearLayout.LayoutParams(
                    -1, LocalDownloadsUIState.dp(activity, LocalDownloadsUIState.SEEK_BAR_TOUCH_DP)));

            LinearLayout timeRow = new LinearLayout(activity);
            timeRow.setOrientation(LinearLayout.HORIZONTAL);
            timeRow.setPadding(
                    LocalDownloadsUIState.dp(activity, 4),
                    0,
                    LocalDownloadsUIState.dp(activity, 4),
                    LocalDownloadsUIState.dp(activity, 12)
            );
            fullCurrentTime = LocalDownloadsUIState.text(activity, "0:00", 12, Color.argb(195, 255, 255, 255));
            fullTotalTime = LocalDownloadsUIState.text(activity, "0:00", 12, Color.argb(195, 255, 255, 255));
            fullTotalTime.setGravity(Gravity.END);
            timeRow.addView(fullCurrentTime, new LinearLayout.LayoutParams(0, -2, 1f));
            timeRow.addView(fullTotalTime, new LinearLayout.LayoutParams(0, -2, 1f));
            fullPlayerRoot.addView(timeRow, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout controlsRow = new LinearLayout(activity);
            controlsRow.setOrientation(LinearLayout.HORIZONTAL);
            controlsRow.setGravity(Gravity.CENTER_VERTICAL);
            controlsRow.setPadding(0, LocalDownloadsUIState.dp(activity, 4), 0, LocalDownloadsUIState.dp(activity, 14));
            fullPlayerControlsRow = controlsRow;

            fullShuffleBtn = new ImageButton(activity);
            fullShuffleBtn.setBackground(LocalDownloadsUIState.createCircleRipple());
            fullShuffleBtn.setOnClickListener(v -> {
                boolean en = OfflinePlaybackService.toggleShuffle();
                updateFullShuffleRepeatVisuals(activity);
                Utils.showToastShort(en ? "Karışık çalma açık" : "Karışık çalma kapalı");
            });
            controlsRow.addView(fullShuffleBtn, new LinearLayout.LayoutParams(
                    LocalDownloadsUIState.dp(activity, 48), LocalDownloadsUIState.dp(activity, 48)));
            controlsRow.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));

            fullPrevBtn = new ImageButton(activity);
            fullPrevBtn.setBackground(LocalDownloadsUIState.createCircleRipple());
            fullPrevBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(
                    activity, LocalDownloadsUIState.ICON_SKIP_PREV, Color.WHITE, 0));
            fullPrevBtn.setOnClickListener(v -> OfflinePlaybackService.skipPrevious(activity));
            controlsRow.addView(fullPrevBtn, new LinearLayout.LayoutParams(
                    LocalDownloadsUIState.dp(activity, 54), LocalDownloadsUIState.dp(activity, 54)));
            controlsRow.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));

            fullPlayBtn = new ImageButton(activity);
            fullPlayBtn.setElevation(LocalDownloadsUIState.dp(activity, 10f));
            fullPlayBtn.setOnClickListener(v -> OfflinePlaybackService.toggle(activity));
            controlsRow.addView(fullPlayBtn, new LinearLayout.LayoutParams(
                    LocalDownloadsUIState.dp(activity, 72), LocalDownloadsUIState.dp(activity, 72)));
            controlsRow.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));

            fullNextBtn = new ImageButton(activity);
            fullNextBtn.setBackground(LocalDownloadsUIState.createCircleRipple());
            fullNextBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(
                    activity, LocalDownloadsUIState.ICON_SKIP_NEXT, Color.WHITE, 0));
            fullNextBtn.setOnClickListener(v -> OfflinePlaybackService.skipNext(activity));
            controlsRow.addView(fullNextBtn, new LinearLayout.LayoutParams(
                    LocalDownloadsUIState.dp(activity, 54), LocalDownloadsUIState.dp(activity, 54)));
            controlsRow.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));

            fullRepeatBtn = new ImageButton(activity);
            fullRepeatBtn.setBackground(LocalDownloadsUIState.createCircleRipple());
            fullRepeatBtn.setOnClickListener(v -> {
                OfflinePlaybackService.cycleRepeatMode();
                updateFullShuffleRepeatVisuals(activity);
            });
            controlsRow.addView(fullRepeatBtn, new LinearLayout.LayoutParams(
                    LocalDownloadsUIState.dp(activity, 48), LocalDownloadsUIState.dp(activity, 48)));
            fullPlayerRoot.addView(controlsRow, new LinearLayout.LayoutParams(-1, -2));

            fullPlayerDialog.setContentView(fullPlayerRoot);
            setupDialogDismissListener(activity);

            Window window = fullPlayerDialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
                window.setNavigationBarColor(LocalDownloadsUIState.currentTheme.varBgBottom);
                OfflineHeaderBlocker.applyImmersiveFullscreen(window, true);
            }

            OfflineTrack current = LocalDownloadsUIState.findByPath(LocalDownloadsUIState.playingPath);
            if (current != null) applyTrackVisuals(activity, current);
            applyFullPlayerBackground(activity);
            applyFocusModeVisibility(activity);
            applyEffectsVisibility(activity);
            updatePlayIcon(activity, showingPause);

            isFayeModeActive = false;
            fullPlayerDialog.show();
            openingFullPlayer = false;

            fullPlayerRoot.setAlpha(0f);
            fullPlayerRoot.setTranslationY(LocalDownloadsUIState.dp(activity, 18));
            long enterDuration = LocalDownloadsUIState.isPlayerCinematicTransitionsEnabled(activity) ? 280L : 120L;
            enterDuration = LocalDownloadsUIState.scaleDuration(activity, enterDuration);
            fullPlayerRoot.animate().alpha(1f).translationY(0f).setDuration(enterDuration)
                    .setInterpolator(new DecelerateInterpolator(1.35f)).start();

        } catch (RuntimeException error) {
            handlePlayerCreationError(error);
        }
    }

    private static void showFayeFullScreenPlayer(Activity activity, LocalDownloadsFragment fragment) {
        try {
            isFayeModeActive = true;
            fullPlayerDialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

            FrameLayout fayeRoot = new FrameLayout(activity);

            int c1 = LocalDownloadsUIState.currentTheme.varAccentPrimary;
            int c2 = LocalDownloadsUIState.currentTheme.varAccentSecondary;
            int c3 = LocalDownloadsUIState.currentTheme.varBgMid;

            fayeBgDrawable = LocalDownloadsAnimator.createFayeBackground(activity, c1, c2, c3);
            fayeRoot.setBackground(fayeBgDrawable);
            fayeBgDrawable.start();

            fullPlayerRoot = new LinearLayout(activity);
            fullPlayerRoot.setOrientation(LinearLayout.VERTICAL);
            fullPlayerRoot.setGravity(Gravity.CENTER_HORIZONTAL);
            fullPlayerRoot.setPadding(
                    LocalDownloadsUIState.dp(activity, 24),
                    LocalDownloadsUIState.dp(activity, 40),
                    LocalDownloadsUIState.dp(activity, 24),
                    LocalDownloadsUIState.dp(activity, 30)
            );

            ImageButton closeBtn = new ImageButton(activity);
            closeBtn.setBackground(LocalDownloadsUIState.createCircleRipple());
            closeBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(activity, LocalDownloadsUIState.ICON_CLOSE, Color.WHITE, 0));
            closeBtn.setOnClickListener(v -> dismissWithAnimation());
            FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(LocalDownloadsUIState.dp(activity, 48), LocalDownloadsUIState.dp(activity, 48));
            closeLp.gravity = Gravity.TOP | Gravity.START;
            closeLp.setMargins(LocalDownloadsUIState.dp(activity, 16), LocalDownloadsUIState.dp(activity, 16), 0, 0);
            fayeRoot.addView(closeBtn, closeLp);

            FrameLayout artWrap = new FrameLayout(activity);
            int artSize = Math.min(
                    LocalDownloadsUIState.dp(activity, 360),
                    activity.getResources().getDisplayMetrics().widthPixels - LocalDownloadsUIState.dp(activity, 60)
            );

            fullPlayerHaloView = new View(activity);
            fullPlayerHaloDrawable = LocalDownloadsAnimator.createHaloDrawable(activity);
            fullPlayerHaloView.setBackground(fullPlayerHaloDrawable);
            int haloSize = artSize + LocalDownloadsUIState.dp(activity, 150);
            artWrap.addView(fullPlayerHaloView, new FrameLayout.LayoutParams(haloSize, haloSize, Gravity.CENTER));

            fullArtwork = LocalDownloadsUIState.artworkView(activity, null, LocalDownloadsUIState.FULL_ARTWORK_CORNER_DP);
            fullArtwork.setElevation(LocalDownloadsUIState.dp(activity, 20f));
            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(LocalDownloadsUIState.dp(activity, LocalDownloadsUIState.FULL_ARTWORK_CORNER_DP));
            border.setStroke(LocalDownloadsUIState.dp(activity, 2), c2);
            fullArtwork.setBackground(border);

            artWrap.addView(fullArtwork, new FrameLayout.LayoutParams(artSize, artSize, Gravity.CENTER));

            GestureDetector fayeGestureDetector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }
                @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                    if (e1 == null || e2 == null) return false;
                    float dx = e2.getX() - e1.getX();
                    if (Math.abs(dx) > LocalDownloadsUIState.dp(activity, 80) && Math.abs(dx) > Math.abs(vY) * 1.2f) {
                        boolean next = dx < 0;
                        LocalDownloadsAnimator.animateArtworkSwipe(artWrap, next, LocalDownloadsUIState.dp(activity, 20));
                        if (next) OfflinePlaybackService.skipNext(activity);
                        else OfflinePlaybackService.skipPrevious(activity);
                        return true;
                    }
                    return false;
                }
            });
            artWrap.setOnTouchListener((v, event) -> fayeGestureDetector.onTouchEvent(event));

            fullPlayerRoot.addView(artWrap, new LinearLayout.LayoutParams(-1, 0, 1f));

            fullTitle = LocalDownloadsUIState.text(activity, "", 26, Color.WHITE);
            fullTitle.setTypeface(Typeface.DEFAULT_BOLD);
            fullTitle.setSingleLine(true);
            fullTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            fullTitle.setGravity(Gravity.CENTER);
            fullTitle.setMarqueeRepeatLimit(-1);
            fullTitle.setSelected(true);
            fullTitle.setShadowLayer(8f, 0f, 2f, Color.BLACK);

            fullArtist = LocalDownloadsUIState.text(activity, "", 16, c2);
            fullArtist.setTypeface(Typeface.DEFAULT_BOLD);
            fullArtist.setSingleLine(true);
            fullArtist.setGravity(Gravity.CENTER);
            fullArtist.setPadding(0, LocalDownloadsUIState.dp(activity, 4), 0, LocalDownloadsUIState.dp(activity, 24));

            fullPlayerRoot.addView(fullTitle, new LinearLayout.LayoutParams(-1, -2));
            fullPlayerRoot.addView(fullArtist, new LinearLayout.LayoutParams(-1, -2));

            fullSeek = createFullPlayerSeekBar(activity);
            fullPlayerRoot.addView(fullSeek, new LinearLayout.LayoutParams(
                    -1, LocalDownloadsUIState.dp(activity, LocalDownloadsUIState.SEEK_BAR_TOUCH_DP)));

            LinearLayout timeRow = new LinearLayout(activity);
            timeRow.setOrientation(LinearLayout.HORIZONTAL);
            timeRow.setPadding(
                    LocalDownloadsUIState.dp(activity, 8), 0,
                    LocalDownloadsUIState.dp(activity, 8), LocalDownloadsUIState.dp(activity, 16)
            );
            fullCurrentTime = LocalDownloadsUIState.text(activity, "0:00", 13, Color.WHITE);
            fullTotalTime = LocalDownloadsUIState.text(activity, "0:00", 13, Color.WHITE);
            fullTotalTime.setGravity(Gravity.END);
            timeRow.addView(fullCurrentTime, new LinearLayout.LayoutParams(0, -2, 1f));
            timeRow.addView(fullTotalTime, new LinearLayout.LayoutParams(0, -2, 1f));
            fullPlayerRoot.addView(timeRow, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout controlsRow = new LinearLayout(activity);
            controlsRow.setOrientation(LinearLayout.HORIZONTAL);
            controlsRow.setGravity(Gravity.CENTER_VERTICAL);
            controlsRow.setPadding(0, 0, 0, LocalDownloadsUIState.dp(activity, 20));

            fullShuffleBtn = new ImageButton(activity);
            fullShuffleBtn.setBackground(LocalDownloadsUIState.createCircleRipple());
            fullShuffleBtn.setOnClickListener(v -> {
                OfflinePlaybackService.toggleShuffle();
                updateFullShuffleRepeatVisuals(activity);
            });
            controlsRow.addView(fullShuffleBtn, new LinearLayout.LayoutParams(
                    LocalDownloadsUIState.dp(activity, 48), LocalDownloadsUIState.dp(activity, 48)));
            controlsRow.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));

            fullPrevBtn = new ImageButton(activity);
            fullPrevBtn.setBackground(LocalDownloadsUIState.createCircleRipple());
            fullPrevBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(
                    activity, LocalDownloadsUIState.ICON_SKIP_PREV, Color.WHITE, 0));
            fullPrevBtn.setOnClickListener(v -> OfflinePlaybackService.skipPrevious(activity));
            controlsRow.addView(fullPrevBtn, new LinearLayout.LayoutParams(
                    LocalDownloadsUIState.dp(activity, 60), LocalDownloadsUIState.dp(activity, 60)));
            controlsRow.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));

            fullPlayBtn = new ImageButton(activity);
            GradientDrawable fayePlayCircle = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, new int[]{c2, c1});
            fayePlayCircle.setShape(GradientDrawable.OVAL);
            fullPlayBtn.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(90, 255, 255, 255)), fayePlayCircle, fayePlayCircle));
            fullPlayBtn.setElevation(LocalDownloadsUIState.dp(activity, 15f));
            fullPlayBtn.setOnClickListener(v -> OfflinePlaybackService.toggle(activity));
            controlsRow.addView(fullPlayBtn, new LinearLayout.LayoutParams(
                    LocalDownloadsUIState.dp(activity, 84), LocalDownloadsUIState.dp(activity, 84)));
            controlsRow.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));

            fullNextBtn = new ImageButton(activity);
            fullNextBtn.setBackground(LocalDownloadsUIState.createCircleRipple());
            fullNextBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(
                    activity, LocalDownloadsUIState.ICON_SKIP_NEXT, Color.WHITE, 0));
            fullNextBtn.setOnClickListener(v -> OfflinePlaybackService.skipNext(activity));
            controlsRow.addView(fullNextBtn, new LinearLayout.LayoutParams(
                    LocalDownloadsUIState.dp(activity, 60), LocalDownloadsUIState.dp(activity, 60)));
            controlsRow.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));

            fullRepeatBtn = new ImageButton(activity);
            fullRepeatBtn.setBackground(LocalDownloadsUIState.createCircleRipple());
            fullRepeatBtn.setOnClickListener(v -> {
                OfflinePlaybackService.cycleRepeatMode();
                updateFullShuffleRepeatVisuals(activity);
            });
            controlsRow.addView(fullRepeatBtn, new LinearLayout.LayoutParams(
                    LocalDownloadsUIState.dp(activity, 48), LocalDownloadsUIState.dp(activity, 48)));
            fullPlayerRoot.addView(controlsRow, new LinearLayout.LayoutParams(-1, -2));

            HorizontalScrollView fayeActionsScroll = new HorizontalScrollView(activity);
            fayeActionsScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout fayeActionsRow = new LinearLayout(activity);
            fayeActionsRow.setOrientation(LinearLayout.HORIZONTAL);
            fayeActionsRow.setGravity(Gravity.CENTER);

            TextView fayeLyricsBtn = createYtActionPill(activity, "🎤 Sözler");
            fayeLyricsBtn.setOnClickListener(v -> showSyncedLyricsDialog(activity, fragment));
            fayeActionsRow.addView(fayeLyricsBtn);

            TextView fayeQueueBtn = createYtActionPill(activity, "🎶 Sıradaki");
            fayeQueueBtn.setOnClickListener(v -> showUpNextQueueDialog(activity));
            fayeActionsRow.addView(fayeQueueBtn);

            TextView fayeSleepBtn = createYtActionPill(activity, "🌙 Uyku");
            fayeSleepBtn.setOnClickListener(v -> showSleepTimerDialog(activity, fayeSleepBtn));
            fayeActionsRow.addView(fayeSleepBtn);

            fayeActionsScroll.addView(fayeActionsRow);
            fullPlayerRoot.addView(fayeActionsScroll, new LinearLayout.LayoutParams(-1, -2));

            fayeRoot.addView(fullPlayerRoot, new FrameLayout.LayoutParams(-1, -1));
            fullPlayerDialog.setContentView(fayeRoot);

            setupDialogDismissListener(activity);

            Window window = fullPlayerDialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                OfflineHeaderBlocker.applyImmersiveFullscreen(window, true);
            }

            OfflineTrack current = LocalDownloadsUIState.findByPath(LocalDownloadsUIState.playingPath);
            if (current != null) applyTrackVisuals(activity, current);
            updatePlayIcon(activity, showingPause);

            fullPlayerDialog.show();
            openingFullPlayer = false;

            fullPlayerRoot.setAlpha(0f);
            fullPlayerRoot.setTranslationY(LocalDownloadsUIState.dp(activity, 30));
            fullPlayerRoot.animate().alpha(1f).translationY(0f).setDuration(400L)
                    .setInterpolator(new DecelerateInterpolator(1.5f)).start();

        } catch (RuntimeException error) {
            handlePlayerCreationError(error);
        }
    }

    private static void setupDialogDismissListener(Activity activity) {
        final Dialog dialogInstance = fullPlayerDialog;
        final LinearLayout rootInstance = fullPlayerRoot;
        final ImageView artworkInstance = fullArtwork;
        final LocalDownloadsAnimator.HaloGlowDrawable haloInstance = fullPlayerHaloDrawable;
        final LocalDownloadsAnimator.FlowingGradientDrawable gradientInstance = fullPlayerGradientDrawable;
        final LocalDownloadsAnimator.FayeFlowingPaintDrawable fayeBgInstance = fayeBgDrawable;

        dialogInstance.setOnDismissListener(d -> {
            openingFullPlayer = false;
            dismissingWithAnimation = false;
            breathingActive = false;

            LocalDownloadsAnimator.stopAllPlayerAnimations(rootInstance);

            if (haloInstance != null) { try { haloInstance.stop(); } catch (Exception ignored) {} }
            if (gradientInstance != null) { try { gradientInstance.stop(); } catch (Exception ignored) {} }
            if (fayeBgInstance != null) { try { fayeBgInstance.stop(); } catch (Exception ignored) {} }

            if (rootInstance != null) {
                rootInstance.animate().cancel();
                rootInstance.setTranslationY(0f);
                rootInstance.setAlpha(1f);
                rootInstance.setBackground(null);
            }
            if (artworkInstance != null) {
                artworkInstance.animate().cancel();
                artworkInstance.setScaleX(1f);
                artworkInstance.setScaleY(1f);
                artworkInstance.setTranslationX(0f);
                artworkInstance.setTranslationY(0f);
                artworkInstance.setAlpha(1f);
            }

            if (fullPlayerDialog == dialogInstance) {
                fullPlayerDialog = null;
                fullPlayerRoot = null;
                fullArtwork = null;
                fullPlayerHaloDrawable = null;
                fullPlayerGradientDrawable = null;
                fullPlayerHaloView = null;
                fullPlayerTopBar = null;
                fullPlayerActionsScroll = null;
                fullPlayerActionsRow = null;
                fullPlayerControlsRow = null;
                fullPlayerEffectsPill = null;
                fullTitle = null;
                fullArtist = null;
                fullCurrentTime = null;
                fullTotalTime = null;
                fullPlayBtn = null;
                fullPrevBtn = null;
                fullNextBtn = null;
                fullShuffleBtn = null;
                fullRepeatBtn = null;
                fullSeek = null;
                fayeBgDrawable = null;
            }

            try {
                if (activity.getWindow() != null) {
                    OfflineHeaderBlocker.applyImmersiveFullscreen(activity.getWindow(), true);
                }
            } catch (Exception ignored) {}
        });
    }

    private static void handlePlayerCreationError(RuntimeException error) {
        Dialog failedDialog = fullPlayerDialog;
        LinearLayout failedRoot = fullPlayerRoot;
        if (failedRoot != null) {
            LocalDownloadsAnimator.stopAllPlayerAnimations(failedRoot);
            failedRoot.animate().cancel();
        }
        if (failedDialog != null) {
            try { if (failedDialog.isShowing()) failedDialog.dismiss(); } catch (Exception ignored) {}
        }
        if (fullPlayerDialog == failedDialog) {
            openingFullPlayer = false;
            dismissingWithAnimation = false;
            breathingActive = false;
            fullPlayerDialog = null;
            fullPlayerRoot = null;
            fullArtwork = null;
            fullPlayerHaloView = null;
            if (fullPlayerHaloDrawable != null) { try { fullPlayerHaloDrawable.stop(); } catch (Exception ignored) {} fullPlayerHaloDrawable = null; }
            if (fullPlayerGradientDrawable != null) { try { fullPlayerGradientDrawable.stop(); } catch (Exception ignored) {} fullPlayerGradientDrawable = null; }
            if (fayeBgDrawable != null) { try { fayeBgDrawable.stop(); } catch (Exception ignored) {} fayeBgDrawable = null; }
        }
        Logger.printException(() -> "Failed to open local full player", error);
    }

    private static void applyFullPlayerBackground(Context ctx) {
        if (ctx == null || fullPlayerRoot == null || isFayeModeActive) return;
        boolean dynamic = LocalDownloadsUIState.isPlayerDynamicColorsEnabled(ctx);
        OfflineTrack cur = LocalDownloadsUIState.findByPath(LocalDownloadsUIState.playingPath);
        Drawable scene = LocalDownloadsUIState.buildSceneBackgroundDrawable(dynamic ? cur : null, dynamic);

        boolean gradientEnabled = LocalDownloadsUIState.isEffectsMasterEnabled(ctx) && !LocalDownloadsUIState.isLowPowerMode(ctx);

        if (gradientEnabled) {
            if (fullPlayerGradientDrawable != null) { try { fullPlayerGradientDrawable.stop(); } catch (Exception ignored) {} }
            fullPlayerGradientDrawable = LocalDownloadsAnimator.createBackgroundGradient(ctx);
            fullPlayerGradientDrawable.setAlpha(70);
            LayerDrawable layers = new LayerDrawable(new Drawable[]{scene, fullPlayerGradientDrawable});
            fullPlayerRoot.setBackground(layers);
            if (!fullPlayerGradientDrawable.isRunning()) fullPlayerGradientDrawable.start();
        } else {
            if (fullPlayerGradientDrawable != null) { try { fullPlayerGradientDrawable.stop(); } catch (Exception ignored) {} fullPlayerGradientDrawable = null; }
            fullPlayerRoot.setBackground(scene);
        }
    }

    private static void applyFocusModeVisibility(Context ctx) {
        if (ctx == null || isFayeModeActive) return;
        boolean focus = LocalDownloadsUIState.isFocusMode(ctx);
        boolean uiChanged = (focus != focusModeActiveLocal);
        focusModeActiveLocal = focus;

        if (fullPlayerActionsRow != null) {
            for (int i = 0; i < fullPlayerActionsRow.getChildCount(); i++) {
                View child = fullPlayerActionsRow.getChildAt(i);
                boolean isFocusPill = false;
                if (child instanceof TextView) {
                    CharSequence t = ((TextView) child).getText();
                    if (t != null && (t.toString().contains("Odak Modu") || t.toString().contains("Normal Görünüm"))) {
                        isFocusPill = true;
                    }
                }
                child.setVisibility((focus && !isFocusPill) ? View.GONE : View.VISIBLE);
            }
        }

        if (uiChanged && fullArtwork != null) {
            LocalDownloadsAnimator.animateFocusModeTransition(fullArtwork, null, focus);
        }
    }

    private static void applyEffectsVisibility(Context ctx) {
        if (ctx == null || fullPlayerHaloDrawable == null || fullPlayerHaloView == null) return;
        boolean halo = LocalDownloadsUIState.isHaloEffectivelyEnabled(ctx);
        if (halo) {
            fullPlayerHaloView.setVisibility(View.VISIBLE);
            if (!fullPlayerHaloDrawable.isRunning()) fullPlayerHaloDrawable.start();
        } else {
            try { fullPlayerHaloDrawable.stop(); } catch (Exception ignored) {}
            fullPlayerHaloView.setVisibility(View.GONE);
        }
    }

    private static String buildEffectsPillLabel(Context ctx) {
        boolean master = LocalDownloadsUIState.isEffectsMasterEnabled(ctx);
        boolean lowPower = LocalDownloadsUIState.isLowPowerMode(ctx);
        if (!master) return "🎨 Efektler: KAPALI";
        if (lowPower) return "🎨 Efektler: DÜŞÜK GÜÇ";
        return "🎨 Efektler: AÇIK";
    }

    public static void showSyncedLyricsDialog(Activity activity, LocalDownloadsFragment fragment) {
        OfflineTrack cur = LocalDownloadsUIState.findByPath(LocalDownloadsUIState.playingPath);
        if (cur == null || activity == null) return;

        File musicRoot = fragment.getMusicRoot();
        File lrcFile = new File(musicRoot, cur.videoId() + ".lrc");
        if (lrcFile.isFile()) {
            try {
                String rawCheck = OfflineStorage.readText(lrcFile);
                if (LocalDownloadManager.isGibberishOrCorruptedLyrics(rawCheck)) {
                    OfflineStorage.delete(lrcFile);
                    activity.getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE)
                            .edit().remove("lyrics_lookup_failed_" + cur.videoId()).apply();
                }
            } catch (Exception ignored) {}
        }

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout rootLayout = new FrameLayout(activity);

        ImageView blurBg = new ImageView(activity);
        blurBg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap art = cur.artwork(1080);
        if (art != null) {
            blurBg.setImageBitmap(art);
            blurBg.setAlpha(0.3f);
        } else {
            blurBg.setBackgroundColor(LocalDownloadsUIState.currentTheme.varBgBottom);
        }
        rootLayout.addView(blurBg, new FrameLayout.LayoutParams(-1, -1));

        View overlay = new View(activity);
        GradientDrawable overlayGradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(240, 10, 10, 15), Color.argb(200, 10, 10, 15), Color.argb(250, 5, 5, 8)});
        overlay.setBackground(overlayGradient);
        rootLayout.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(
                LocalDownloadsUIState.dp(activity, 16),
                LocalDownloadsUIState.dp(activity, 40),
                LocalDownloadsUIState.dp(activity, 16),
                LocalDownloadsUIState.dp(activity, 20)
        );

        LinearLayout topBar = new LinearLayout(activity);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton closeBtn = new ImageButton(activity);
        closeBtn.setBackground(LocalDownloadsUIState.createCircleRipple());
        closeBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(activity, LocalDownloadsUIState.ICON_CLOSE, Color.WHITE, 0));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        topBar.addView(closeBtn, new LinearLayout.LayoutParams(LocalDownloadsUIState.dp(activity, 44), LocalDownloadsUIState.dp(activity, 44)));

        TextView header = LocalDownloadsUIState.text(activity, "🎤 " + cur.displayTitle(), 18, Color.WHITE);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setSingleLine(true);
        header.setEllipsize(TextUtils.TruncateAt.END);
        header.setPadding(LocalDownloadsUIState.dp(activity, 12), 0, 0, 0);
        topBar.addView(header, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout syncRow = new LinearLayout(activity);
        syncRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView offsetMinusBtn = LocalDownloadsUIState.createPillButton(activity, "-0.5s", false);
        offsetMinusBtn.setOnClickListener(v -> {
            LocalDownloadsUIState.lyricsOffsetMs -= 500;
            Utils.showToastShort("Senkron: " + (LocalDownloadsUIState.lyricsOffsetMs / 1000.0) + "s");
        });
        syncRow.addView(offsetMinusBtn);

        TextView offsetPlusBtn = LocalDownloadsUIState.createPillButton(activity, "+0.5s", false);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-2, -2);
        pLp.setMargins(LocalDownloadsUIState.dp(activity, 6), 0, 0, 0);
        offsetPlusBtn.setLayoutParams(pLp);
        offsetPlusBtn.setOnClickListener(v -> {
            LocalDownloadsUIState.lyricsOffsetMs += 500;
            Utils.showToastShort("Senkron: " + (LocalDownloadsUIState.lyricsOffsetMs / 1000.0) + "s");
        });
        syncRow.addView(offsetPlusBtn);

        topBar.addView(syncRow);

        container.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        View separator = new View(activity);
        separator.setBackgroundColor(Color.argb(60, 255, 255, 255));
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(-1, LocalDownloadsUIState.dp(activity, 1));
        sepLp.setMargins(0, LocalDownloadsUIState.dp(activity, 16), 0, LocalDownloadsUIState.dp(activity, 16));
        container.addView(separator, sepLp);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        LinearLayout lineContainer = new LinearLayout(activity);
        lineContainer.setOrientation(LinearLayout.VERTICAL);
        lineContainer.setPadding(0, LocalDownloadsUIState.dp(activity, 100), 0, LocalDownloadsUIState.dp(activity, 150));
        scroll.addView(lineContainer);
        container.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        rootLayout.addView(container, new FrameLayout.LayoutParams(-1, -1));

        final List<OfflineTrack.LyricLine> lyrics = new ArrayList<>(cur.loadSynchronizedLyrics());
        final List<LyricPair> pairs = new ArrayList<>();
        final List<TextView> originalViews = new ArrayList<>();
        final List<View> lyricRows = new ArrayList<>();
        final int[] previousActiveLyric = {-2};
        final boolean[] userIsScrolling = {false};

        scroll.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                userIsScrolling[0] = true;
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                scroll.postDelayed(() -> userIsScrolling[0] = false, 3000);
            }
            return false;
        });

        Runnable renderLines = () -> {
            lineContainer.removeAllViews();
            pairs.clear();
            originalViews.clear();
            lyricRows.clear();
            previousActiveLyric[0] = -2;

            for (OfflineTrack.LyricLine line : lyrics) {
                String raw = line.text() == null ? "" : line.text().trim();
                if (raw.isEmpty() || raw.equals("[TR_NATIVE_POEM_OR_SONG]")) continue;

                String translatedPrefix = "└ 🇹🇷";
                if (raw.startsWith(translatedPrefix)) {
                    String translated = raw.substring(translatedPrefix.length()).trim();
                    if (!pairs.isEmpty()) pairs.get(pairs.size() - 1).translated = translated;
                    continue;
                }
                if (raw.startsWith("└ TR:")) {
                    String translated = raw.substring("└ TR:".length()).trim();
                    if (!pairs.isEmpty()) pairs.get(pairs.size() - 1).translated = translated;
                    continue;
                }

                pairs.add(new LyricPair(line.timeMs(), raw, ""));
            }

            if (pairs.isEmpty()) {
                TextView empty = LocalDownloadsUIState.text(activity, "Söz bulunamadı.", 16, Color.argb(150, 255, 255, 255));
                empty.setGravity(Gravity.CENTER);
                lineContainer.addView(empty);
                return;
            }

            for (LyricPair pair : pairs) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setGravity(Gravity.CENTER);
                row.setPadding(0, LocalDownloadsUIState.dp(activity, 12), 0, LocalDownloadsUIState.dp(activity, 12));

                TextView original = LocalDownloadsUIState.text(activity, pair.original, 20, Color.argb(120, 255, 255, 255));
                original.setTypeface(Typeface.DEFAULT_BOLD);
                original.setGravity(Gravity.CENTER);
                original.setOnClickListener(v -> OfflinePlaybackService.seekTo((int) pair.timeMs));
                row.addView(original);
                originalViews.add(original);

                if (pair.translated != null && !pair.translated.isEmpty()) {
                    TextView translated = LocalDownloadsUIState.text(activity, pair.translated, 16, Color.argb(90, 255, 255, 255));
                    translated.setGravity(Gravity.CENTER);
                    translated.setPadding(0, LocalDownloadsUIState.dp(activity, 4), 0, 0);
                    translated.setOnClickListener(v -> OfflinePlaybackService.seekTo((int) pair.timeMs));
                    row.addView(translated);
                }

                lineContainer.addView(row);
                lyricRows.add(row);
            }
        };

        renderLines.run();

        OfflinePlaybackService.PlaybackListener lrcUpdater = (path, title, playing, position, duration) -> {
            if (pairs.isEmpty() || originalViews.isEmpty()) return;
            activity.runOnUiThread(() -> {
                int activeIdx = -1;
                long adjustedPos = position + LocalDownloadsUIState.lyricsOffsetMs;

                for (int i = 0; i < pairs.size(); i++) {
                    if (adjustedPos >= pairs.get(i).timeMs) activeIdx = i;
                    else break;
                }

                for (int i = 0; i < originalViews.size(); i++) {
                    boolean active = i == activeIdx;
                    TextView original = originalViews.get(i);
                    original.setTextColor(active ? Color.WHITE : Color.argb(120, 255, 255, 255));
                    original.setTextSize(active ? 24 : 20);
                    if (active) original.setTypeface(Typeface.DEFAULT_BOLD);
                    else original.setTypeface(Typeface.DEFAULT);

                    LinearLayout row = (LinearLayout) original.getParent();
                    if (row.getChildCount() > 1) {
                        TextView translated = (TextView) row.getChildAt(1);
                        translated.setTextColor(active ? LocalDownloadsUIState.activeAccentColor() : Color.argb(90, 255, 255, 255));
                        translated.setTextSize(active ? 18 : 16);
                    }

                    if (active && !userIsScrolling[0]) {
                        if (previousActiveLyric[0] != activeIdx) {
                            View lyricRow = lyricRows.get(i);
                            int scrollY = lyricRow.getTop() - (scroll.getHeight() / 2) + (lyricRow.getHeight() / 2);
                            scroll.smoothScrollTo(0, Math.max(0, scrollY));

                            lyricRow.setAlpha(0.8f);
                            lyricRow.animate().alpha(1f).setDuration(200).start();
                        }
                    }
                }
                previousActiveLyric[0] = activeIdx;
            });
        };
        OfflinePlaybackService.addListener(lrcUpdater);
        dialog.setOnDismissListener(d -> OfflinePlaybackService.removeListener(lrcUpdater));

        dialog.setContentView(rootLayout);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            OfflineHeaderBlocker.applyImmersiveFullscreen(dialog.getWindow(), true);
        }
        dialog.show();

        SharedPreferences lyricPrefs = activity.getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE);
        boolean lyricLookupFailedBefore = lyricPrefs.getBoolean("lyrics_lookup_failed_" + cur.videoId(), false);
        if (lyrics.isEmpty() && !lyricLookupFailedBefore) {
            Utils.submitOnBackgroundThread(() -> {
                boolean found = LocalDownloadManager.fetchAndSaveSyncedLyrics(
                        musicRoot, cur.videoId(), cur.displayTitle(), cur.displayArtist(), cur.durationSeconds());
                if (!found) lyricPrefs.edit().putBoolean("lyrics_lookup_failed_" + cur.videoId(), true).apply();
                List<OfflineTrack.LyricLine> loaded = cur.loadSynchronizedLyrics();
                Utils.runOnMainThread(() -> {
                    if (!dialog.isShowing()) return;
                    lyrics.clear();
                    lyrics.addAll(loaded);
                    renderLines.run();
                });
                return null;
            });
        }
    }

    private static void showSleepTimerDialog(Context ctx, View anchor) {
        long rem = OfflinePlaybackService.getRemainingSleepTimerMinutes();
        String status = rem > 0 ? " (Aktif: " + rem + " dk kaldı)"
                : (OfflinePlaybackService.isStopAtTrackEnd() ? " (Parça sonunda duracak)" : "");
        String[] items = {
                "🌙 15 Dakika",
                "🌙 30 Dakika",
                "🌙 45 Dakika",
                "🌙 1 Saat (60 dk)",
                "🎵 Bu parça bitince durdur",
                "✕ Zamanlayıcıyı Kapat" + status
        };
        LocalDownloadsUIState.showMenu(ctx, anchor, items, -1, pos -> {
            switch (pos) {
                case 0 -> { OfflinePlaybackService.setSleepTimer(15, false); Utils.showToastShort("Uyku zamanlayıcısı: 15 dk"); }
                case 1 -> { OfflinePlaybackService.setSleepTimer(30, false); Utils.showToastShort("Uyku zamanlayıcısı: 30 dk"); }
                case 2 -> { OfflinePlaybackService.setSleepTimer(45, false); Utils.showToastShort("Uyku zamanlayıcısı: 45 dk"); }
                case 3 -> { OfflinePlaybackService.setSleepTimer(60, false); Utils.showToastShort("Uyku zamanlayıcısı: 1 saat"); }
                case 4 -> { OfflinePlaybackService.setSleepTimer(0, true); Utils.showToastShort("Bu parça bitince müzik duracak"); }
                case 5 -> { OfflinePlaybackService.setSleepTimer(0, false); Utils.showToastShort("Uyku zamanlayıcısı kapatıldı"); }
            }
        });
    }

    private static void showPreampBoosterDialog(Context ctx) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(
                LocalDownloadsUIState.dp(ctx, 20),
                LocalDownloadsUIState.dp(ctx, 18),
                LocalDownloadsUIState.dp(ctx, 20),
                LocalDownloadsUIState.dp(ctx, 18)
        );
        box.setBackground(LocalDownloadsUIState.menuBackground(ctx));

        int current = OfflinePlaybackService.getPreampPercent();
        TextView title = LocalDownloadsUIState.text(ctx, "🔊 Ses Yükseltici (Pre-amp): %" + current, 16, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);

        TextView sub = LocalDownloadsUIState.text(
                ctx, "Cihazın sistem sesini bozmadan uygulama içi kazancı %100 ile %150 arasında artırır.",
                12, LocalDownloadsUIState.secondary());
        sub.setPadding(0, LocalDownloadsUIState.dp(ctx, 4), 0, LocalDownloadsUIState.dp(ctx, 14));
        box.addView(sub);

        SeekBar slider = new SeekBar(ctx);
        slider.setMax(50);
        slider.setProgress(current - 100);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int pct = 100 + progress;
                title.setText("🔊 Ses Yükseltici (Pre-amp): %" + pct);
                OfflinePlaybackService.setPreampPercent(pct);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        box.addView(slider, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(ctx).setView(box).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }

    /**
     * GÖREV 6: Sıradaki şarkılar kuyruğu.
     * - ≡ ikonuna basıp sürükleyerek sıralamayı değiştirebilirsin (dikey drag).
     * - Satıra basıp sağa/sola kaydırarak şarkıyı kuyruktan silebilirsin (yatay swipe).
     * - Normal tıklama şarkıyı çalar.
     */
    public static void showUpNextQueueDialog(Context ctx) {
        List<String> queue = new ArrayList<>(OfflinePlaybackService.getCurrentQueue());
        if (queue.isEmpty()) {
            Utils.showToastShort("Sırada şarkı yok");
            return;
        }

        final int swipeThreshold = LocalDownloadsUIState.dp(ctx, 140);
        final int touchSlop = LocalDownloadsUIState.dp(ctx, 12);
        final int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;

        LinearLayout sheet = new LinearLayout(ctx);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(
                LocalDownloadsUIState.dp(ctx, 16),
                LocalDownloadsUIState.dp(ctx, 18),
                LocalDownloadsUIState.dp(ctx, 16),
                LocalDownloadsUIState.dp(ctx, 16)
        );
        sheet.setBackground(LocalDownloadsUIState.bottomSheetSurfaceDrawable(ctx));

        TextView header = LocalDownloadsUIState.text(
                ctx,
                "🎶 Sıradaki Şarkılar • ≡ Sürükle, ↔ Kaydır-Sil (" + queue.size() + ")",
                16, Color.WHITE);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(
                LocalDownloadsUIState.dp(ctx, 8), 0,
                LocalDownloadsUIState.dp(ctx, 8),
                LocalDownloadsUIState.dp(ctx, 6));

        TextView hint = LocalDownloadsUIState.text(
                ctx,
                "İpucu: Bir satırı sağa/sola kaydırarak kuyruktan silebilirsin.",
                11, LocalDownloadsUIState.secondary());
        hint.setPadding(
                LocalDownloadsUIState.dp(ctx, 8), 0,
                LocalDownloadsUIState.dp(ctx, 8),
                LocalDownloadsUIState.dp(ctx, 12));

        sheet.addView(header, new LinearLayout.LayoutParams(-1, -2));
        sheet.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(ctx);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        AlertDialog dialog = new AlertDialog.Builder(ctx).setView(sheet).create();

        final int[] draggedIndex = {-1};
        final Runnable[] rebuildRows = new Runnable[1];

        rebuildRows[0] = () -> {
            list.removeAllViews();
            List<String> liveQ = OfflinePlaybackService.getCurrentQueue();
            int activeIdx = OfflinePlaybackService.getCurrentQueueIndex();

            if (liveQ.isEmpty()) {
                dialog.dismiss();
                Utils.showToastShort("Sıra temizlendi");
                return;
            }

            for (int i = 0; i < liveQ.size(); i++) {
                final int index = i;
                OfflineTrack t = LocalDownloadsUIState.findByPath(liveQ.get(i));
                if (t == null) t = OfflineTrack.load(new File(liveQ.get(i)));
                final OfflineTrack track = t;

                // ==== SWIPE-TO-REMOVE KATMANI ====
                FrameLayout rowWrapper = new FrameLayout(ctx);
                LinearLayout.LayoutParams wrapperLp = new LinearLayout.LayoutParams(-1, -2);
                wrapperLp.setMargins(0, LocalDownloadsUIState.dp(ctx, 2), 0, LocalDownloadsUIState.dp(ctx, 2));
                rowWrapper.setLayoutParams(wrapperLp);
                // Yatay kaydırırken ScrollView'ın araya girmesini engellemek için:
                rowWrapper.setClipChildren(false);
                rowWrapper.setClipToPadding(false);

                // Silme Arka Planı (satır kaydırılınca görünür)
                LinearLayout deleteBg = new LinearLayout(ctx);
                deleteBg.setGravity(Gravity.CENTER_VERTICAL);
                deleteBg.setPadding(
                        LocalDownloadsUIState.dp(ctx, 20), 0,
                        LocalDownloadsUIState.dp(ctx, 20), 0);

                GradientDrawable delDrawable = new GradientDrawable();
                delDrawable.setColor(Color.parseColor("#E5322D"));
                delDrawable.setCornerRadius(LocalDownloadsUIState.dp(ctx, 12));
                deleteBg.setBackground(delDrawable);

                TextView delIconLeft = LocalDownloadsUIState.text(ctx, "🗑️ SİL", 14, Color.WHITE);
                delIconLeft.setTypeface(Typeface.DEFAULT_BOLD);
                deleteBg.addView(delIconLeft);

                View delSpacer = new View(ctx);
                deleteBg.addView(delSpacer, new LinearLayout.LayoutParams(0, 1, 1f));

                TextView delIconRight = LocalDownloadsUIState.text(ctx, "SİL 🗑️", 14, Color.WHITE);
                delIconRight.setTypeface(Typeface.DEFAULT_BOLD);
                deleteBg.addView(delIconRight);

                rowWrapper.addView(deleteBg, new FrameLayout.LayoutParams(-1, -1));

                // Gerçek Satır (swipe edilen katman)
                LinearLayout item = new LinearLayout(ctx);
                item.setGravity(Gravity.CENTER_VERTICAL);
                int p = LocalDownloadsUIState.dp(ctx, 8);
                item.setPadding(p, p, p, p);
                boolean isDragged = (index == draggedIndex[0]);
                item.setBackground(isDragged
                        ? LocalDownloadsUIState.glassCardDrawable(ctx, LocalDownloadsUIState.dp(ctx, 12), true)
                        : LocalDownloadsUIState.rowBackground(ctx, i == activeIdx));
                if (isDragged) {
                    LocalDownloadsAnimator.animateQueueItemDrag(item, true);
                }
                rowWrapper.addView(item, new FrameLayout.LayoutParams(-1, -2));

                // Sürükleme Tutamacı (≡) — dikey drag
                TextView dragHandle = LocalDownloadsUIState.text(
                        ctx, "≡", 22,
                        isDragged ? LocalDownloadsUIState.activeAccentColor() : LocalDownloadsUIState.secondary());
                dragHandle.setPadding(
                        LocalDownloadsUIState.dp(ctx, 6), 0,
                        LocalDownloadsUIState.dp(ctx, 12), 0);
                dragHandle.setOnTouchListener(new View.OnTouchListener() {
                    private float startY;
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getActionMasked()) {
                            case MotionEvent.ACTION_DOWN -> {
                                startY = event.getRawY();
                                draggedIndex[0] = index;
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                                item.setBackground(LocalDownloadsUIState.glassCardDrawable(
                                        ctx, LocalDownloadsUIState.dp(ctx, 12), true));
                                LocalDownloadsAnimator.animateQueueItemDrag(item, true);
                                return true;
                            }
                            case MotionEvent.ACTION_MOVE -> {
                                float dy = event.getRawY() - startY;
                                item.setTranslationY(dy * 0.35f);
                                if (Math.abs(dy) > LocalDownloadsUIState.dp(ctx, 42)) {
                                    int targetIdx = dy > 0 ? index + 1 : index - 1;
                                    if (targetIdx >= 0 && targetIdx < liveQ.size()) {
                                        OfflinePlaybackService.moveQueueItem(index, targetIdx);
                                        draggedIndex[0] = targetIdx;
                                        startY = event.getRawY();
                                        final int pendingTarget = targetIdx;
                                        list.post(() -> {
                                            rebuildRows[0].run();
                                            if (pendingTarget < list.getChildCount()) {
                                                View movedView = list.getChildAt(pendingTarget);
                                                if (movedView != null) {
                                                    LocalDownloadsAnimator.animateListItemIn(
                                                            ctx, movedView, 0,
                                                            dy > 0 ? -LocalDownloadsUIState.dp(ctx, 10) : LocalDownloadsUIState.dp(ctx, 10));
                                                }
                                            }
                                        });
                                    }
                                }
                                return true;
                            }
                            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                draggedIndex[0] = -1;
                                LocalDownloadsAnimator.animateQueueItemDrop(item);
                                list.post(rebuildRows[0]);
                                return true;
                            }
                        }
                        return false;
                    }
                });
                item.addView(dragHandle);

                // Kapak resmi
                ImageView art = LocalDownloadsUIState.artworkView(ctx, null, 4);
                LocalDownloadsUIState.loadArtworkAsync(track, 128, art);
                item.addView(art, new LinearLayout.LayoutParams(
                        LocalDownloadsUIState.dp(ctx, 42), LocalDownloadsUIState.dp(ctx, 42)));

                // Başlık/Alt başlık
                LinearLayout col = new LinearLayout(ctx);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(
                        LocalDownloadsUIState.dp(ctx, 12), 0,
                        LocalDownloadsUIState.dp(ctx, 8), 0);

                TextView title = LocalDownloadsUIState.text(ctx, track.displayTitle(), 14, Color.WHITE);
                if (i == activeIdx) title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setSingleLine(true);

                TextView sub = LocalDownloadsUIState.text(
                        ctx,
                        track.displayArtist() + " • " + LocalDownloadsUIState.formatDuration(track.durationSeconds()),
                        12, LocalDownloadsUIState.secondary());
                sub.setSingleLine(true);

                col.addView(title);
                col.addView(sub);
                item.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

                // ==== YATAY SWIPE (ACTION_MOVE tabanlı) ====
                final float[] touchStartX = {0f};
                final float[] touchStartY = {0f};
                final boolean[] isSwiping = {false};

                item.setOnTouchListener((v, event) -> {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN: {
                            touchStartX[0] = event.getRawX();
                            touchStartY[0] = event.getRawY();
                            isSwiping[0] = false;
                            return false; // View.onTouchEvent click için çalışsın
                        }
                        case MotionEvent.ACTION_MOVE: {
                            float dx = event.getRawX() - touchStartX[0];
                            float dy = event.getRawY() - touchStartY[0];
                            if (!isSwiping[0]
                                    && Math.abs(dx) > touchSlop
                                    && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                                isSwiping[0] = true;
                                if (v.getParent() != null) {
                                    v.getParent().requestDisallowInterceptTouchEvent(true);
                                }
                            }
                            if (isSwiping[0]) {
                                item.setTranslationX(dx);
                                float alpha = 1f - Math.min(0.55f,
                                        Math.abs(dx) / (float) (swipeThreshold * 2));
                                item.setAlpha(alpha);
                                return true;
                            }
                            return false;
                        }
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL: {
                            if (isSwiping[0]) {
                                float dx = event.getRawX() - touchStartX[0];
                                if (Math.abs(dx) > swipeThreshold) {
                                    // Eşiği aştı → uçur ve sil
                                    float targetX = dx > 0 ? screenWidth : -screenWidth;
                                    item.animate()
                                            .translationX(targetX)
                                            .alpha(0f)
                                            .setDuration(220)
                                            .setInterpolator(new AccelerateInterpolator())
                                            .withEndAction(() -> {
                                                OfflinePlaybackService.removeQueueItem(index);
                                                rebuildRows[0].run();
                                                Utils.showToastShort("Sıradan kaldırıldı: " + track.displayTitle());
                                            })
                                            .start();
                                } else {
                                    // Eşiği aşmadı → yerine geri dön
                                    item.animate()
                                            .translationX(0f)
                                            .alpha(1f)
                                            .setDuration(180)
                                            .setInterpolator(new OvershootInterpolator(1.5f))
                                            .start();
                                }
                                isSwiping[0] = false;
                                return true;
                            }
                            return false;
                        }
                    }
                    return false;
                });

                // Tıklama → şarkıyı çal (sadece swipe olmadıysa)
                item.setOnClickListener(v -> {
                    if (isSwiping[0]) return;
                    OfflinePlaybackService.playAtQueueIndex(index);
                    dialog.dismiss();
                });

                list.addView(rowWrapper);
            }
        };
        rebuildRows[0].run();

        scroll.addView(list);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, LocalDownloadsUIState.dp(ctx, 380)));
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }

    public static void play(Context ctx, OfflineTrack track) {
        ArrayList<String> queue = new ArrayList<>(LocalDownloadsUIState.queuePaths);
        if (queue.isEmpty()) queue.add(track.audioFile().getAbsolutePath());
        playWithCustomQueue(ctx, track, queue, Math.max(0, queue.indexOf(track.audioFile().getAbsolutePath())));
    }

    public static void playWithCustomQueue(Context ctx, OfflineTrack track, ArrayList<String> queue, int queueIndex) {
        if (ctx == null || track == null) return;
        try {
            Intent intent = new Intent(ctx, OfflinePlaybackService.class)
                    .setAction(OfflinePlaybackService.ACTION_PLAY_FILE)
                    .putExtra(OfflinePlaybackService.EXTRA_PATH, track.audioFile().getAbsolutePath())
                    .putExtra(OfflinePlaybackService.EXTRA_TITLE, track.displayTitle())
                    .putExtra(OfflinePlaybackService.EXTRA_ARTIST, track.displayArtist())
                    .putExtra(OfflinePlaybackService.EXTRA_ARTWORK_PATH, track.artworkFile().getAbsolutePath())
                    .putStringArrayListExtra(OfflinePlaybackService.EXTRA_QUEUE, queue)
                    .putExtra(OfflinePlaybackService.EXTRA_QUEUE_INDEX, queueIndex);
            ctx.startForegroundService(intent);
            applyTrackVisuals(ctx, track);
        } catch (Exception ex) {
            Logger.printException(() -> "Offline playback failed: " + track.audioFile(), ex);
            Utils.showToastShort(str("morphe_music_downloads_play_failed"));
        }
    }

    public static void insertTrackIntoLiveQueue(Context ctx, OfflineTrack track, boolean playNext) {
        if (ctx == null || track == null) return;
        String targetPath = track.audioFile().getAbsolutePath();
        List<String> currentQ = OfflinePlaybackService.getCurrentQueue();
        if (currentQ.isEmpty() || LocalDownloadsUIState.playingPath.isEmpty()) {
            play(ctx, track);
            Utils.showToastShort("Oynatılıyor: " + track.displayTitle());
            return;
        }
        if (OfflinePlaybackService.enqueueTrack(targetPath, playNext)) {
            Utils.showToastShort(playNext
                    ? "⏭️ Bundan sonra oynatılacak: " + track.displayTitle()
                    : "🎶 Sıraya eklendi: " + track.displayTitle());
        } else {
            play(ctx, track);
        }
    }

    public static void handlePlaybackChanged(Activity activity, String path, String title,
                                             boolean playing, int position, int duration) {
        if (activity == null || LocalDownloadsUIState.miniPlayer == null) return;
        LocalDownloadsUIState.miniPlayer.setVisibility(title.isEmpty() ? View.GONE : View.VISIBLE);

        if (!path.equals(LocalDownloadsUIState.playingPath)) {
            LocalDownloadsUIState.diskReloadNeeded = true;
            LocalDownloadsUIState.playingPath = path;
            likedCurrentTrack = false;
            OfflineTrack t = LocalDownloadsUIState.findByPath(path);
            if (t != null) {
                if (isFullPlayerShowing() && fullArtwork != null) {
                    LocalDownloadsAnimator.animateCoverSwap(fullArtwork, true, () -> applyTrackVisuals(activity, t));
                } else {
                    applyTrackVisuals(activity, t);
                }
            } else {
                if (miniTitle != null) miniTitle.setText(title);
                if (fullTitle != null) fullTitle.setText(title);
            }
            LocalDownloadsUIState.markPlayingRow(activity);
        }
        updateFullShuffleRepeatVisuals(activity);
        updatePlayIcon(activity, playing);

        int safeDur = Math.max(1, duration);
        if (miniProgressBar != null) {
            miniProgressBar.setMax(safeDur);
            miniProgressBar.setProgress(position);
        }
        if (fullSeek != null && !userSeeking) {
            fullSeek.setMax(safeDur);
            fullSeek.setProgress(position, true);
            if (fullCurrentTime != null) fullCurrentTime.setText(LocalDownloadsUIState.formatDuration(position / 1000));
            if (fullTotalTime != null) fullTotalTime.setText(LocalDownloadsUIState.formatDuration(duration / 1000));
        }

        if (fullPlayerHaloDrawable != null) {
            fullPlayerHaloDrawable.setPlaying(playing);
        }
    }

    public static void updateDynamicPlayerColors(Context ctx) {
        if (ctx == null) return;

        if (LocalDownloadsUIState.miniPlayer != null) {
            LocalDownloadsUIState.miniPlayer.setBackgroundColor(LocalDownloadsUIState.currentTheme.varBgMid);
        }
        if (miniProgressBar != null) {
            miniProgressBar.setProgressDrawable(
                    LocalDownloadsUIState.createThemeProgressDrawable(LocalDownloadsUIState.dp(ctx, 2), false));
        }

        if (fullPlayerRoot != null && !isFayeModeActive) {
            applyFullPlayerBackground(ctx);

            if (fullPlayerHaloDrawable != null) {
                try { fullPlayerHaloDrawable.stop(); } catch (Exception ignored) {}
            }
            fullPlayerHaloDrawable = LocalDownloadsAnimator.createHaloDrawable(ctx);
            if (fullPlayerHaloView != null) {
                fullPlayerHaloView.setBackground(fullPlayerHaloDrawable);
            }
            applyEffectsVisibility(ctx);
        }

        if (fullSeek != null) {
            fullSeek.setProgressDrawable(LocalDownloadsUIState.createThemeProgressDrawable(
                    LocalDownloadsUIState.dp(ctx, LocalDownloadsUIState.SEEK_BAR_HEIGHT_DP), true));
            fullSeek.invalidate();
        }

        updatePlayIcon(ctx, showingPause);
        updateFullShuffleRepeatVisuals(ctx);
    }

    private static void updatePlayIcon(Context ctx, boolean playing) {
        showingPause = playing;
        int iconType = playing ? LocalDownloadsUIState.ICON_PAUSE : LocalDownloadsUIState.ICON_PLAY;

        if (fullPlayBtn != null) {
            if (isFayeModeActive) {
                fullPlayBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(
                        ctx, iconType, Color.WHITE, 0));
            } else {
                GradientDrawable playCircle = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{LocalDownloadsUIState.activeAccentColor(), LocalDownloadsUIState.activeSecondaryAccent()});
                playCircle.setShape(GradientDrawable.OVAL);
                fullPlayBtn.setBackground(new RippleDrawable(
                        ColorStateList.valueOf(Color.argb(90, 255, 255, 255)), playCircle, playCircle));
                fullPlayBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(
                        ctx, iconType, LocalDownloadsUIState.currentTheme.varOnAccent, 0));
            }
        }
        if (miniPlay != null) {
            miniPlay.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(
                    ctx, iconType, Color.WHITE, 0));
        }

        if (fullArtwork != null && isFullPlayerShowing()) {
            boolean breathingEnabled = LocalDownloadsUIState.isPlayerCoverBreathingEnabled(ctx)
                    && LocalDownloadsUIState.isEffectsMasterEnabled(ctx)
                    && !LocalDownloadsUIState.isLowPowerMode(ctx);

            if (playing && breathingEnabled && !breathingActive) {
                breathingActive = true;
                LocalDownloadsAnimator.applyArtworkBreathingEffect(fullArtwork);
            } else if ((!playing || !breathingEnabled) && breathingActive) {
                breathingActive = false;
                LocalDownloadsAnimator.stopArtworkBreathingEffect(fullArtwork);
                if (!dismissingWithAnimation) {
                    fullArtwork.animate().cancel();
                    fullArtwork.animate().scaleX(1f).scaleY(1f).setDuration(200L).start();
                }
            }
        }

        if (fullPlayerHaloDrawable != null) {
            fullPlayerHaloDrawable.setPlaying(playing);
        }
    }

    private static void updateFullShuffleRepeatVisuals(Context ctx) {
        int accent = LocalDownloadsUIState.activeAccentColor();
        if (fullShuffleBtn != null) {
            int c = OfflinePlaybackService.isShuffleEnabled() ? accent : Color.argb(135, 255, 255, 255);
            fullShuffleBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(
                    ctx, LocalDownloadsUIState.ICON_SHUFFLE, c,
                    OfflinePlaybackService.isShuffleEnabled() ? 1 : 0));
        }
        if (fullRepeatBtn != null) {
            int r = OfflinePlaybackService.getRepeatMode();
            int c = r != OfflinePlaybackService.REPEAT_OFF ? accent : Color.argb(135, 255, 255, 255);
            fullRepeatBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(
                    ctx, LocalDownloadsUIState.ICON_REPEAT, c, r));
        }
    }

    public static void applyTrackVisuals(Context ctx, OfflineTrack track) {
        if (miniTitle != null) miniTitle.setText(track.displayTitle());
        if (miniArtist != null) miniArtist.setText(track.displayArtist());
        if (miniArtwork != null) {
            LocalDownloadsUIState.loadArtworkAsync(track, LocalDownloadsUIState.ROW_ARTWORK_PIXELS, miniArtwork);
        }
        if (fullTitle != null) fullTitle.setText(track.displayTitle());
        if (fullArtist != null) fullArtist.setText(track.displayArtist());
        if (fullArtwork != null) {
            LocalDownloadsUIState.loadArtworkAsync(track, LocalDownloadsUIState.FULL_ARTWORK_PIXELS, fullArtwork);
        }

        updateDynamicPlayerColors(ctx);
    }

    private static TextView createYtActionPill(Context ctx, String label) {
        TextView pill = LocalDownloadsUIState.text(ctx, label, 13, Color.WHITE);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setPadding(
                LocalDownloadsUIState.dp(ctx, 16),
                LocalDownloadsUIState.dp(ctx, 9),
                LocalDownloadsUIState.dp(ctx, 16),
                LocalDownloadsUIState.dp(ctx, 9)
        );
        pill.setBackground(LocalDownloadsUIState.glassCardDrawable(ctx, LocalDownloadsUIState.dp(ctx, 20), false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, LocalDownloadsUIState.dp(ctx, 10), 0);
        pill.setLayoutParams(lp);
        return pill;
    }

    private static SeekBar createFullPlayerSeekBar(Context ctx) {
        SeekBar seek = new SeekBar(ctx);
        seek.setPadding(LocalDownloadsUIState.dp(ctx, 10), 0, LocalDownloadsUIState.dp(ctx, 10), 0);
        seek.setProgressDrawable(LocalDownloadsUIState.createThemeProgressDrawable(
                LocalDownloadsUIState.dp(ctx, LocalDownloadsUIState.SEEK_BAR_HEIGHT_DP), true));

        Drawable thumb = new Drawable() {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            public void draw(@NonNull Canvas canvas) {
                float cx = getBounds().exactCenterX();
                float cy = getBounds().exactCenterY();
                p.setColor(isFayeModeActive ? LocalDownloadsUIState.currentTheme.varAccentSecondary : LocalDownloadsUIState.activeAccentColor());
                canvas.drawCircle(cx, cy, LocalDownloadsUIState.dp(ctx, LocalDownloadsUIState.SEEK_THUMB_DP) / 2f, p);
                p.setColor(Color.WHITE);
                canvas.drawCircle(cx, cy, LocalDownloadsUIState.dp(ctx, LocalDownloadsUIState.SEEK_THUMB_DP) / 4f, p);
            }
            @Override public int getIntrinsicWidth() { return LocalDownloadsUIState.dp(ctx, LocalDownloadsUIState.SEEK_THUMB_DP); }
            @Override public int getIntrinsicHeight() { return LocalDownloadsUIState.dp(ctx, LocalDownloadsUIState.SEEK_THUMB_DP); }
            @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
            @Override public void setColorFilter(@Nullable ColorFilter cf) { p.setColorFilter(cf); }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
        seek.setThumb(thumb);
        seek.setThumbOffset(LocalDownloadsUIState.dp(ctx, LocalDownloadsUIState.SEEK_THUMB_DP) / 2);
        seek.setBackground(null);
        seek.setSplitTrack(false);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                OfflinePlaybackService.seekTo(bar.getProgress());
            }
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser && fullCurrentTime != null) {
                    fullCurrentTime.setText(LocalDownloadsUIState.formatDuration(value / 1000));
                }
            }
        });
        return seek;
    }
}