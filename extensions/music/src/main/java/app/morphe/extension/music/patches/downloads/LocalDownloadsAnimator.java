package app.morphe.extension.music.patches.downloads;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

public final class LocalDownloadsAnimator {
    public static final long SEARCH_ANIM_MS = 180L;
    public static final long BOTTOM_SHEET_ANIM_MS = 250L;

    public enum AnimationProfile {
        SPRING("Yaylanan & Canlı (Overshoot)"),
        CYBER("Siber & Keskin Kayma"),
        SMOOTH("Sinematik & Yumuşak Fade"),
        MINIMAL("Minimal & Hızlı");

        public final String label;
        AnimationProfile(String label) { this.label = label; }
    }

    private static final Map<View, ObjectAnimator> BREATHING_ANIMATORS =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ProgressBar, ValueAnimator> PROGRESS_ANIMATORS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LocalDownloadsAnimator() {}

    public static AnimationProfile getSavedAnimationProfile(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE);
            return AnimationProfile.valueOf(sp.getString("preferred_anim_profile", AnimationProfile.SPRING.name()));
        } catch (Exception ignored) {
            return AnimationProfile.SPRING;
        }
    }

    public static void setSavedAnimationProfile(Context ctx, AnimationProfile profile) {
        try {
            ctx.getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putString("preferred_anim_profile", profile.name()).apply();
        } catch (Exception ignored) {}
    }

    public static void animateListItemIn(Context ctx, View item, int index, int dpOffset) {
        if (item == null) return;
        AnimationProfile profile = getSavedAnimationProfile(ctx);

        item.setAlpha(0f);
        item.setTranslationY(dpOffset);

        long delay = Math.min(index, 10) * (profile == AnimationProfile.MINIMAL ? 10L : 18L);
        long duration = switch (profile) {
            case MINIMAL -> 140L;
            case CYBER -> 180L;
            case SMOOTH -> 280L;
            default -> 220L;
        };

        android.view.animation.Interpolator interpolator = switch (profile) {
            case SPRING -> new OvershootInterpolator(1.35f);
            case CYBER -> new AccelerateDecelerateInterpolator();
            case SMOOTH -> new DecelerateInterpolator(1.8f);
            default -> new DecelerateInterpolator();
        };

        item.setScaleX(profile == AnimationProfile.SPRING ? 0.96f : 1f);
        item.setScaleY(profile == AnimationProfile.SPRING ? 0.96f : 1f);

        item.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delay)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .start();
    }

    public static void animateSwipeSuccess(Context ctx, View row, float shiftPx) {
        if (row == null) return;
        AnimationProfile profile = getSavedAnimationProfile(ctx);
        row.animate().cancel();

        row.animate()
                .translationX(shiftPx)
                .alpha(0.85f)
                .scaleX(1.02f)
                .scaleY(1.02f)
                .setDuration(profile == AnimationProfile.MINIMAL ? 70L : 95L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> row.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(profile == AnimationProfile.MINIMAL ? 140L : 220L)
                        .setInterpolator(new OvershootInterpolator(1.5f))
                        .start())
                .start();
    }

    public static void animateQueueItemDrag(View row, boolean dragging) {
        if (row == null) return;
        row.animate().cancel();
        float scale = dragging ? 1.035f : 1f;
        float elev = dragging ? 16f : 2f;
        row.animate()
                .scaleX(scale)
                .scaleY(scale)
                .translationZ(elev)
                .setDuration(120L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    public static void animateQueueItemDrop(View row) {
        if (row == null) return;
        Context ctx = row.getContext();
        row.animate().cancel();
        row.animate()
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(LocalDownloadsUIState.scaleDuration(ctx, 200L))
                .setInterpolator(new OvershootInterpolator(1.3f))
                .start();
    }

    public static void animateQueueMakeRoom(View row, float fromY, float toY) {
        if (row == null) return;
        Context ctx = row.getContext();
        row.animate().cancel();
        row.setTranslationY(fromY);
        row.animate()
                .translationY(toY)
                .setDuration(LocalDownloadsUIState.scaleDuration(ctx, 180L))
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
    }

    public static void applyArtworkBreathingEffect(View artworkView) {
        if (artworkView == null) return;
        stopArtworkBreathingEffect(artworkView);

        long duration = 3500L;
        try {
            Context ctx = artworkView.getContext();
            duration = LocalDownloadsUIState.scaleDuration(ctx, 3500L);
            if (LocalDownloadsUIState.isLowPowerMode(ctx)) {
                duration *= 2L;
            }
        } catch (Exception ignored) {}

        PropertyValuesHolder pvhX = PropertyValuesHolder.ofFloat("scaleX", 1f, 1.03f, 1f);
        PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat("scaleY", 1f, 1.03f, 1f);
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(artworkView, pvhX, pvhY);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setDuration(duration);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();

        BREATHING_ANIMATORS.put(artworkView, animator);
    }

    public static void stopArtworkBreathingEffect(View artworkView) {
        if (artworkView == null) return;
        Animator animator = BREATHING_ANIMATORS.remove(artworkView);
        if (animator != null) {
            try { animator.cancel(); } catch (Exception ignored) {}
        }
    }

    public static void animateBottomSheetShow(View sheet, View overlay, int screenHeight) {
        if (sheet == null || overlay == null) return;
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(BOTTOM_SHEET_ANIM_MS).start();

        float startY = screenHeight * 0.62f;
        sheet.setTranslationY(startY);
        ObjectAnimator slideUp = ObjectAnimator.ofFloat(sheet, "translationY", startY, 0f);
        slideUp.setDuration(BOTTOM_SHEET_ANIM_MS);
        slideUp.setInterpolator(new DecelerateInterpolator(1.65f));
        slideUp.start();
    }

    public static void animateBottomSheetDismiss(View sheet, View overlay, Dialog dialog,
                                                 int screenHeight, @Nullable Runnable afterDismiss) {
        if (sheet == null || overlay == null) {
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
            if (afterDismiss != null) afterDismiss.run();
            return;
        }
        overlay.animate().alpha(0f).setDuration(BOTTOM_SHEET_ANIM_MS - 40L).start();
        ObjectAnimator slideDown = ObjectAnimator.ofFloat(
                sheet, "translationY", sheet.getTranslationY(), screenHeight * 0.68f);
        slideDown.setDuration(BOTTOM_SHEET_ANIM_MS - 40L);
        slideDown.setInterpolator(new DecelerateInterpolator());
        slideDown.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                try {
                    if (dialog != null && dialog.isShowing()) dialog.dismiss();
                } catch (Exception ignored) {}
                if (afterDismiss != null) afterDismiss.run();
            }
        });
        slideDown.start();
    }

    public static void slideViewHeight(View view, int targetHeight) {
        if (view == null) return;
        ValueAnimator anim = ValueAnimator.ofInt(view.getHeight(), targetHeight);
        anim.setDuration(SEARCH_ANIM_MS);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(v -> {
            view.getLayoutParams().height = (int) v.getAnimatedValue();
            view.requestLayout();
        });
        anim.start();
    }

    public static void animateArtworkSwipe(View artworkWrap, boolean nextTrack, int shiftPx) {
        if (artworkWrap == null) return;
        artworkWrap.animate().cancel();
        artworkWrap.setTranslationX(nextTrack ? -shiftPx : shiftPx);
        artworkWrap.setScaleX(0.95f);
        artworkWrap.setScaleY(0.95f);
        artworkWrap.animate()
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(210L)
                .setInterpolator(new OvershootInterpolator(1.25f))
                .start();
    }

    public static void animateBackgroundColorChange(View view, int toColor) {
        if (view == null) return;
        int fromColor = toColor;
        if (view.getBackground() instanceof ColorDrawable cd) {
            fromColor = cd.getColor();
        }
        ValueAnimator colorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        colorAnim.setDuration(240L);
        colorAnim.addUpdateListener(animator -> view.setBackgroundColor((int) animator.getAnimatedValue()));
        colorAnim.start();
    }

    public static void animatePlayerMinimize(@Nullable View fullRoot, @Nullable View artwork,
                                             @Nullable Runnable onEnd) {
        if (fullRoot == null) {
            if (onEnd != null) onEnd.run();
            return;
        }
        Context ctx = fullRoot.getContext();
        long duration = LocalDownloadsUIState.scaleDuration(ctx, 260L);

        fullRoot.animate().cancel();
        if (artwork != null) {
            artwork.animate().cancel();
            artwork.animate()
                    .scaleX(0.3f)
                    .scaleY(0.3f)
                    .translationY(LocalDownloadsUIState.dp(ctx, 180))
                    .alpha(0.15f)
                    .setDuration(duration)
                    .setInterpolator(new AccelerateInterpolator(1.6f))
                    .start();
        }
        fullRoot.animate()
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(new AccelerateInterpolator(1.4f))
                .withEndAction(() -> {
                    if (artwork != null) {
                        artwork.setScaleX(1f);
                        artwork.setScaleY(1f);
                        artwork.setTranslationY(0f);
                        artwork.setAlpha(1f);
                    }
                    fullRoot.setAlpha(1f);
                    if (onEnd != null) onEnd.run();
                })
                .start();
    }

    public static void animateCoverSwap(@Nullable View artwork, boolean forward,
                                        @Nullable Runnable swapAction) {
        if (artwork == null) {
            if (swapAction != null) swapAction.run();
            return;
        }
        Context ctx = artwork.getContext();
        long outDuration = LocalDownloadsUIState.scaleDuration(ctx, 150L);
        long inDuration = LocalDownloadsUIState.scaleDuration(ctx, 230L);
        float shift = LocalDownloadsUIState.dp(ctx, 32) * (forward ? -1f : 1f);

        artwork.animate().cancel();
        artwork.animate()
                .translationX(shift)
                .alpha(0f)
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(outDuration)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    if (swapAction != null) swapAction.run();
                    artwork.setTranslationX(-shift);
                    artwork.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(inDuration)
                            .setInterpolator(new OvershootInterpolator(1.1f))
                            .start();
                })
                .start();
    }

    public static void animateFocusModeTransition(@Nullable View artwork, @Nullable View actionsRow,
                                                  boolean entering) {
        Context ctx = (artwork != null ? artwork : actionsRow) != null
                ? (artwork != null ? artwork.getContext() : actionsRow.getContext())
                : null;
        if (ctx == null) return;
        long d = LocalDownloadsUIState.scaleDuration(ctx, 280L);

        if (artwork != null) {
            artwork.animate().cancel();
            float target = entering ? 1.08f : 1f;
            artwork.animate()
                    .scaleX(target)
                    .scaleY(target)
                    .setDuration(d)
                    .setInterpolator(new OvershootInterpolator(1.05f))
                    .start();
        }
        if (actionsRow != null) {
            actionsRow.animate().cancel();
            actionsRow.animate()
                    .alpha(entering ? 0f : 1f)
                    .translationY(entering ? LocalDownloadsUIState.dp(ctx, 40) : 0f)
                    .setDuration(d)
                    .setInterpolator(new DecelerateInterpolator(1.4f))
                    .start();
        }
    }

    public static void animateTabBounce(@Nullable View icon) {
        if (icon == null) return;
        Context ctx = icon.getContext();
        icon.animate().cancel();
        icon.setScaleX(0.88f);
        icon.setScaleY(0.88f);
        icon.animate()
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(LocalDownloadsUIState.scaleDuration(ctx, 120L))
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> icon.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(LocalDownloadsUIState.scaleDuration(ctx, 160L))
                        .setInterpolator(new OvershootInterpolator(1.5f))
                        .start())
                .start();
    }

    public static void animateLibraryTransition(@Nullable View content, boolean enterFromRight,
                                                @Nullable Runnable swapAction) {
        if (content == null) {
            if (swapAction != null) swapAction.run();
            return;
        }
        Context ctx = content.getContext();
        long outD = LocalDownloadsUIState.scaleDuration(ctx, 140L);
        long inD = LocalDownloadsUIState.scaleDuration(ctx, 230L);
        float shift = LocalDownloadsUIState.dp(ctx, 26) * (enterFromRight ? -1f : 1f);

        content.animate().cancel();
        content.animate()
                .alpha(0.35f)
                .translationX(shift)
                .setDuration(outD)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    if (swapAction != null) swapAction.run();
                    content.setTranslationX(-shift);
                    content.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(inD)
                            .setInterpolator(new DecelerateInterpolator(1.6f))
                            .start();
                })
                .start();
    }

    public static int[] getThemeGradientPalette(LocalDownloadsUIState.ThemePalette theme) {
        if (theme == null) theme = LocalDownloadsUIState.ThemePalette.YT_MUSIC_PINK;
        switch (theme) {
            case GRIFFITH:
                return new int[]{
                        Color.parseColor("#DCE6FF"),
                        Color.parseColor("#4A7BC8"),
                        Color.parseColor("#0F2A5C"),
                        Color.parseColor("#4A7BC8"),
                        Color.parseColor("#DCE6FF")
                };
            case FAYE:
                return new int[]{
                        Color.parseColor("#1B0A28"),
                        Color.parseColor("#C45BDF"),
                        Color.parseColor("#FF6B4A"),
                        Color.parseColor("#FFD34E"),
                        Color.parseColor("#C45BDF"),
                        Color.parseColor("#1B0A28")
                };
            case EVANGELION:
                return new int[]{
                        Color.parseColor("#A855F7"),
                        Color.parseColor("#B6FF00"),
                        Color.parseColor("#A855F7"),
                        Color.parseColor("#B6FF00"),
                        Color.parseColor("#A855F7")
                };
            default:
                return new int[]{
                        theme.varBgMid,
                        theme.varAccentPrimary,
                        theme.varBgMid,
                        theme.varAccentSecondary,
                        theme.varBgMid
                };
        }
    }

    public static int[] getHaloColors(LocalDownloadsUIState.ThemePalette theme) {
        if (theme == null) theme = LocalDownloadsUIState.ThemePalette.YT_MUSIC_PINK;
        switch (theme) {
            case GRIFFITH:
                return new int[]{Color.parseColor("#A5C0E8"), Color.parseColor("#3A6BB5")};
            case FAYE:
                return new int[]{Color.parseColor("#C45BDF"), Color.parseColor("#FFD34E")};
            case EVANGELION:
                return new int[]{Color.parseColor("#A855F7"), Color.parseColor("#B6FF00")};
            default:
                return new int[]{theme.varAccentPrimary, theme.varAccentSecondary};
        }
    }

    public static HaloGlowDrawable createHaloDrawable(Context ctx) {
        OfflineTrack track = LocalDownloadsUIState.findByPath(LocalDownloadsUIState.playingPath);
        int[] colors = LocalDownloadsUIState.resolveActiveHaloColors(ctx, track);
        LocalDownloadsUIState.ThemeAnimation style = LocalDownloadsUIState.getCurrentAnimation();
        return new HaloGlowDrawable(ctx, colors[0], colors[1], style);
    }

    public static FlowingGradientDrawable createBackgroundGradient(Context ctx) {
        OfflineTrack track = LocalDownloadsUIState.findByPath(LocalDownloadsUIState.playingPath);
        return new FlowingGradientDrawable(ctx,
                LocalDownloadsUIState.resolveActiveGradientPalette(ctx, track));
    }

    // GÖREV 2: Faye Mode için 3 renkli boya akıntısı ve yüzen şekiller
    public static FayeFlowingPaintDrawable createFayeBackground(Context ctx, int color1, int color2, int color3) {
        return new FayeFlowingPaintDrawable(ctx, color1, color2, color3);
    }

    public static void animateProgress(@Nullable ProgressBar progressBar, int targetProgress) {
        if (progressBar == null) return;

        int target = Math.max(0, Math.min(progressBar.getMax(), targetProgress));
        ValueAnimator previous;
        synchronized (PROGRESS_ANIMATORS) {
            previous = PROGRESS_ANIMATORS.remove(progressBar);
        }
        if (previous != null) {
            try { previous.cancel(); } catch (Exception ignored) {}
        }

        int start = progressBar.getProgress();
        if (start == target || !progressBar.isAttachedToWindow()) {
            progressBar.setProgress(target);
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(start, target);
        animator.setDuration(220L);
        animator.setInterpolator(new DecelerateInterpolator());
        synchronized (PROGRESS_ANIMATORS) {
            PROGRESS_ANIMATORS.put(progressBar, animator);
        }
        animator.addUpdateListener(valueAnimator -> {
            synchronized (PROGRESS_ANIMATORS) {
                if (PROGRESS_ANIMATORS.get(progressBar) != valueAnimator) return;
            }
            progressBar.setProgress((Integer) valueAnimator.getAnimatedValue());
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator endedAnimator) {
                synchronized (PROGRESS_ANIMATORS) {
                    if (PROGRESS_ANIMATORS.get(progressBar) == endedAnimator) {
                        PROGRESS_ANIMATORS.remove(progressBar);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator canceledAnimator) {
                synchronized (PROGRESS_ANIMATORS) {
                    if (PROGRESS_ANIMATORS.get(progressBar) == canceledAnimator) {
                        PROGRESS_ANIMATORS.remove(progressBar);
                    }
                }
            }
        });
        animator.start();
    }

    private static void cancelProgressAnimation(@Nullable ProgressBar progressBar) {
        if (progressBar == null) return;
        ValueAnimator animator;
        synchronized (PROGRESS_ANIMATORS) {
            animator = PROGRESS_ANIMATORS.remove(progressBar);
        }
        if (animator != null) {
            try { animator.cancel(); } catch (Exception ignored) {}
        }
    }

    public static void stopAllPlayerAnimations(@Nullable View root) {
        if (root == null) return;

        try {
            root.animate().cancel();
            root.clearAnimation();
        } catch (Exception ignored) {}

        stopArtworkBreathingEffect(root);
        if (root instanceof ProgressBar progressBar) {
            cancelProgressAnimation(progressBar);
        }

        Drawable bg = root.getBackground();
        if (bg instanceof Animatable anim) {
            try {
                if (anim.isRunning()) anim.stop();
            } catch (Exception ignored) {}
        }

        Drawable foreground = null;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            try { foreground = root.getForeground(); } catch (Exception ignored) {}
        }
        if (foreground instanceof Animatable anim) {
            try {
                if (anim.isRunning()) anim.stop();
            } catch (Exception ignored) {}
        }

        if (root instanceof ViewGroup vg) {
            for (int i = 0; i < vg.getChildCount(); i++) {
                stopAllPlayerAnimations(vg.getChildAt(i));
            }
        }
    }

    public static final class HaloGlowDrawable extends Drawable implements Animatable {
        private final Context ctx;
        private final int colorPrimary;
        private final int colorSecondary;
        private final LocalDownloadsUIState.ThemeAnimation style;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private RadialGradient primaryShader;
        private RadialGradient secondaryShader;
        private SweepGradient cdSparkle;
        private int cachedW = 0, cachedH = 0;

        private float phase = 0f;
        private float sparkleAngle = 0f;
        private boolean running = false;
        private boolean playing = true;
        private int viewAlpha = 255;
        private long lastInvalidateMs = 0L;
        private long lastLowPowerCheck = 0L;
        private boolean lowPowerCache = false;
        private ValueAnimator animator;

        HaloGlowDrawable(Context ctx, int colorPrimary, int colorSecondary,
                         LocalDownloadsUIState.ThemeAnimation style) {
            this.ctx = ctx.getApplicationContext();
            this.colorPrimary = colorPrimary;
            this.colorSecondary = colorSecondary;
            this.style = style == null ? LocalDownloadsUIState.ThemeAnimation.STANDARD_PULSE : style;
        }

        public void setPlaying(boolean playing) {
            this.playing = playing;
        }

        @Override
        protected void onBoundsChange(@NonNull Rect bounds) {
            super.onBoundsChange(bounds);
            rebuildShaders();
        }

        private void rebuildShaders() {
            Rect b = getBounds();
            int w = b.width(), h = b.height();
            if (w <= 0 || h <= 0) return;
            if (primaryShader != null && w == cachedW && h == cachedH) return;
            cachedW = w;
            cachedH = h;

            float cx = b.exactCenterX(), cy = b.exactCenterY();
            float radius = Math.min(w, h) * 0.5f;

            primaryShader = buildRadial(cx, cy, radius, colorPrimary);
            secondaryShader = buildRadial(cx, cy, radius, colorSecondary);

            if (style == LocalDownloadsUIState.ThemeAnimation.FAYE_FLOW) {
                cdSparkle = new SweepGradient(0f, 0f,
                        new int[]{
                                0x00FFFFFF,
                                0x70FFFFFF,
                                0x00FFFFFF,
                                0x00FFFFFF,
                                0x00FFFFFF,
                                0x40FFFFFF,
                                0x00FFFFFF,
                                0x00FFFFFF
                        },
                        null);
            } else {
                cdSparkle = null;
            }
        }

        private RadialGradient buildRadial(float cx, float cy, float radius, int color) {
            return new RadialGradient(cx, cy, radius,
                    new int[]{
                            (color & 0x00FFFFFF) | 0xC0000000,
                            (color & 0x00FFFFFF) | 0x40000000,
                            0x00000000
                    },
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
        }

        private boolean isLowPowerCached() {
            long now = System.currentTimeMillis();
            if (now - lastLowPowerCheck > 1000L) {
                lowPowerCache = LocalDownloadsUIState.isLowPowerMode(ctx);
                lastLowPowerCheck = now;
            }
            return lowPowerCache;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            if (primaryShader == null) rebuildShaders();
            if (primaryShader == null) return;

            float pulse = (float) (Math.sin(phase * Math.PI * 2.0) * 0.5 + 0.5);
            float intensity = LocalDownloadsUIState.getAnimationIntensity(ctx);
            float playFactor = playing ? 1f : 0.4f;

            float cx = b.exactCenterX(), cy = b.exactCenterY();
            float r = Math.min(b.width(), b.height()) * 0.5f;

            int baseAlpha = (int) ((70f + pulse * 90f) * intensity * playFactor);
            int secAlpha = (int) ((60f + (1f - pulse) * 80f) * intensity * playFactor);

            int a1 = clamp(baseAlpha * viewAlpha / 255);
            int a2 = clamp(secAlpha * viewAlpha / 255);

            paint.setShader(primaryShader);
            paint.setAlpha(a1);
            canvas.drawCircle(cx, cy, r, paint);

            paint.setShader(secondaryShader);
            paint.setAlpha(a2);
            canvas.drawCircle(cx, cy, r * (1.05f + pulse * 0.1f), paint);

            switch (style) {
                case FAYE_FLOW -> drawFayeEffects(canvas, cx, cy, r, pulse, intensity, playFactor);
                case GRIFFITH_FLOW -> drawGriffithEffects(canvas, cx, cy, r, pulse, intensity, playFactor);
                case EVANGELION_PULSE -> drawEvangelionEffects(canvas, cx, cy, r, pulse, intensity, playFactor);
                default -> { /* standart: sadece temel hale */ }
            }
        }

        private void drawFayeEffects(Canvas canvas, float cx, float cy, float r,
                                     float pulse, float intensity, float playFactor) {
            if (cdSparkle != null) {
                Matrix m = new Matrix();
                m.postTranslate(cx, cy);
                m.postRotate(sparkleAngle, cx, cy);
                cdSparkle.setLocalMatrix(m);

                int sparkleAlpha = (int) ((50f + pulse * 50f) * intensity * playFactor);
                sparkleAlpha = clamp(sparkleAlpha * viewAlpha / 255);
                paint.setShader(cdSparkle);
                paint.setAlpha(sparkleAlpha);
                canvas.drawCircle(cx, cy, r * 1.02f, paint);
                paint.setShader(null);
            }

            float t = (float) (Math.sin(phase * Math.PI * 1.2) * 0.5 + 0.5);
            int ringColor = blend(colorPrimary, colorSecondary, t);
            int ringAlpha = (int) ((60f + pulse * 60f) * intensity * playFactor);
            ringAlpha = clamp(ringAlpha * viewAlpha / 255);
            paint.setColor((ringColor & 0x00FFFFFF) | (ringAlpha << 24));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(LocalDownloadsUIState.dp(ctx, 2f));
            canvas.drawCircle(cx, cy, r * 0.94f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawGriffithEffects(Canvas canvas, float cx, float cy, float r,
                                         float pulse, float intensity, float playFactor) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(LocalDownloadsUIState.dp(ctx, 1.5f));
            int alpha = (int) ((30f + pulse * 40f) * intensity * playFactor);
            alpha = clamp(alpha * viewAlpha / 255);
            paint.setColor((colorPrimary & 0x00FFFFFF) | (alpha << 24));
            for (int i = 0; i < 2; i++) {
                float wr = r * (1.10f + i * 0.08f);
                RectF oval = new RectF(cx - wr, cy - wr, cx + wr, cy + wr);
                canvas.drawArc(oval, 155, 32, false, paint);
                canvas.drawArc(oval, -7, 32, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawEvangelionEffects(Canvas canvas, float cx, float cy, float r,
                                           float pulse, float intensity, float playFactor) {
            Rect b = getBounds();

            int innerAlpha = (int) ((60f + pulse * 60f) * intensity * playFactor);
            innerAlpha = clamp(innerAlpha * viewAlpha / 255);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(LocalDownloadsUIState.dp(ctx, 2.5f));
            paint.setColor((colorSecondary & 0x00FFFFFF) | (innerAlpha << 24));
            canvas.drawCircle(cx, cy, r * 0.86f, paint);

            int stripeAlpha = clamp((int) (25f * intensity * playFactor) * viewAlpha / 255);
            paint.setStrokeWidth(LocalDownloadsUIState.dp(ctx, 2f));
            paint.setColor((0xFFD700 & 0x00FFFFFF) | (stripeAlpha << 24));
            float stripeW = LocalDownloadsUIState.dp(ctx, 14);
            float spacing = LocalDownloadsUIState.dp(ctx, 10);
            float y0 = b.top + LocalDownloadsUIState.dp(ctx, 4);
            float y1 = y0 + LocalDownloadsUIState.dp(ctx, 10);
            for (float x = b.left - stripeW; x < b.right; x += spacing) {
                canvas.drawLine(x, y1, x + stripeW, y0, paint);
            }
            y0 = b.bottom - LocalDownloadsUIState.dp(ctx, 4);
            y1 = y0 - LocalDownloadsUIState.dp(ctx, 10);
            for (float x = b.left - stripeW; x < b.right; x += spacing) {
                canvas.drawLine(x, y0, x + stripeW, y1, paint);
            }

            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void start() {
            if (running) return;
            running = true;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.RESTART);
            long base = LocalDownloadsUIState.scaleDuration(ctx, 4200L);
            animator.setDuration(Math.max(1200L, base));
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                if (isLowPowerCached()) {
                    long now = System.currentTimeMillis();
                    if (now - lastInvalidateMs < 66L) return;
                    lastInvalidateMs = now;
                }
                phase = (float) a.getAnimatedValue();
                sparkleAngle = phase * 40f;
                invalidateSelf();
            });
            animator.start();
        }

        @Override
        public void stop() {
            running = false;
            if (animator != null) {
                try { animator.cancel(); } catch (Exception ignored) {}
                animator = null;
            }
        }

        @Override public boolean isRunning() { return running; }

        @Override
        public void setAlpha(int alpha) {
            viewAlpha = alpha;
            invalidateSelf();
        }

        @Override public void setColorFilter(@Nullable ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    public static final class FlowingGradientDrawable extends Drawable implements Animatable {
        private final Context ctx;
        private final int[] colors;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private LinearGradient shader;
        private int cachedW = 0, cachedH = 0;

        private float phase = 0f;
        private boolean running = false;
        private int viewAlpha = 255;
        private long lastInvalidateMs = 0L;
        private long lastLowPowerCheck = 0L;
        private boolean lowPowerCache = false;
        private ValueAnimator animator;

        FlowingGradientDrawable(Context ctx, int[] colors) {
            this.ctx = ctx.getApplicationContext();
            this.colors = colors == null || colors.length < 2
                    ? new int[]{Color.BLACK, Color.WHITE}
                    : colors;
        }

        @Override
        protected void onBoundsChange(@NonNull Rect bounds) {
            super.onBoundsChange(bounds);
            cachedW = 0;
            cachedH = 0;
            ensureShader();
        }

        private void ensureShader() {
            Rect b = getBounds();
            int w = b.width(), h = b.height();
            if (w <= 0 || h <= 0) return;
            if (shader != null && w == cachedW && h == cachedH) return;
            cachedW = w;
            cachedH = h;
            shader = new LinearGradient(
                    b.left, b.top,
                    b.left + w, b.bottom,
                    colors, null, Shader.TileMode.MIRROR);
        }

        private boolean isLowPowerCached() {
            long now = System.currentTimeMillis();
            if (now - lastLowPowerCheck > 1000L) {
                lowPowerCache = LocalDownloadsUIState.isLowPowerMode(ctx);
                lastLowPowerCheck = now;
            }
            return lowPowerCache;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            if (shader == null) ensureShader();
            if (shader == null) return;

            paint.setShader(shader);
            paint.setAlpha(viewAlpha);

            float w = b.width();
            canvas.save();
            canvas.translate(-phase * 2f * w, 0f);
            canvas.drawRect(-w, b.top, b.right + 2f * w, b.bottom, paint);
            canvas.restore();
        }

        @Override
        public void start() {
            if (running) return;
            running = true;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.RESTART);
            long base = LocalDownloadsUIState.scaleDuration(ctx, 12000L);
            animator.setDuration(Math.max(3000L, base));
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                if (isLowPowerCached()) {
                    long now = System.currentTimeMillis();
                    if (now - lastInvalidateMs < 66L) return;
                    lastInvalidateMs = now;
                }
                phase = (float) a.getAnimatedValue();
                invalidateSelf();
            });
            animator.start();
        }

        @Override
        public void stop() {
            running = false;
            if (animator != null) {
                try { animator.cancel(); } catch (Exception ignored) {}
                animator = null;
            }
        }

        @Override public boolean isRunning() { return running; }

        @Override
        public void setAlpha(int alpha) {
            viewAlpha = alpha;
            invalidateSelf();
        }

        @Override public void setColorFilter(@Nullable ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // GÖREV 2: Faye Mode için özel tasarlanmış arka plan drawable'ı.
    // 3 renkli akan boya sütunları ve süzülen soyut şekiller içerir.
    public static final class FayeFlowingPaintDrawable extends Drawable implements Animatable {
        private final Context ctx;
        private final int color1, color2, color3;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        private final Path path1 = new Path();
        private final Path path2 = new Path();
        private final Path path3 = new Path();

        private final List<FloatingShape> shapes = new ArrayList<>();
        private final Random random = new Random();

        private float phase = 0f;
        private boolean running = false;
        private int viewAlpha = 255;
        private long lastInvalidateMs = 0L;
        private long lastLowPowerCheck = 0L;
        private boolean lowPowerCache = false;
        private ValueAnimator animator;
        private int cachedW = 0, cachedH = 0;

        FayeFlowingPaintDrawable(Context ctx, int c1, int c2, int c3) {
            this.ctx = ctx.getApplicationContext();
            this.color1 = c1;
            this.color2 = c2;
            this.color3 = c3;
            shapePaint.setStyle(Paint.Style.FILL_AND_STROKE);
            shapePaint.setStrokeWidth(LocalDownloadsUIState.dp(ctx, 1.5f));
        }

        @Override
        protected void onBoundsChange(@NonNull Rect bounds) {
            super.onBoundsChange(bounds);
            int w = bounds.width(), h = bounds.height();
            if (w <= 0 || h <= 0) return;
            if (cachedW != w || cachedH != h) {
                cachedW = w;
                cachedH = h;
                initShapes(w, h);
            }
        }

        private void initShapes(int w, int h) {
            shapes.clear();
            int numShapes = 18; // Ekranda süzülecek şekil sayısı
            for (int i = 0; i < numShapes; i++) {
                shapes.add(new FloatingShape(
                        random.nextFloat() * w,
                        random.nextFloat() * h,
                        LocalDownloadsUIState.dp(ctx, 10 + random.nextInt(30)),
                        (random.nextFloat() - 0.5f) * 0.5f, // X hızı
                        -0.2f - (random.nextFloat() * 0.6f), // Y hızı (yukarı doğru)
                        random.nextInt(3), // Tip: 0=daire, 1=üçgen, 2=kare
                        (random.nextFloat() - 0.5f) * 90f, // Rotasyon
                        random.nextFloat() * 0.5f // açısal hız
                ));
            }
        }

        private boolean isLowPowerCached() {
            long now = System.currentTimeMillis();
            if (now - lastLowPowerCheck > 1000L) {
                lowPowerCache = LocalDownloadsUIState.isLowPowerMode(ctx);
                lastLowPowerCheck = now;
            }
            return lowPowerCache;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            int w = b.width(), h = b.height();
            if (w <= 0 || h <= 0) return;

            // Arka planı temel renkle doldur
            canvas.drawColor(color1);

            // Akan boya sütunlarını çiz (Bezier eğrileri ile dalgalı)
            drawFlowingColumns(canvas, w, h);

            // Süzülen soyut şekilleri çiz
            drawFloatingShapes(canvas, w, h);
        }

        private void drawFlowingColumns(Canvas canvas, int w, int h) {
            int columns = 3;
            float colWidth = w / (float) columns;

            for (int i = 0; i < columns; i++) {
                Path p = i == 0 ? path1 : (i == 1 ? path2 : path3);
                p.reset();

                int color = i == 0 ? color1 : (i == 1 ? color2 : color3);
                paint.setColor(color);
                paint.setAlpha(viewAlpha);

                // Aşağı/yukarı dalgalanan bir şerit oluştur
                float xOffset = i * colWidth;
                float waveAmplitude = colWidth * 0.4f;
                float waveSpeed = 1.5f + i * 0.5f;
                
                p.moveTo(xOffset, 0);
                
                for (float y = 0; y <= h; y += 20) {
                    float phaseOffset = phase * (float) (Math.PI * 2) * waveSpeed + (i * 2.0f);
                    float x = xOffset + (float) Math.sin(y / (h * 0.2f) + phaseOffset) * waveAmplitude;
                    p.lineTo(x, y);
                }
                
                p.lineTo(xOffset + colWidth, h);
                for (float y = h; y >= 0; y -= 20) {
                    float phaseOffset = phase * (float) (Math.PI * 2) * waveSpeed + (i * 2.0f);
                    float x = xOffset + colWidth + (float) Math.sin(y / (h * 0.2f) + phaseOffset) * waveAmplitude;
                    p.lineTo(x, y);
                }
                
                p.close();

                // Hafif şeffaf bir boya efekti
                paint.setStyle(Paint.Style.FILL);
                paint.setAlpha((int) (viewAlpha * 0.6f)); // Arka planın görünmesi için
                canvas.drawPath(p, paint);
            }
        }

        private void drawFloatingShapes(Canvas canvas, int w, int h) {
            if (shapes.isEmpty()) initShapes(w, h);

            for (FloatingShape shape : shapes) {
                canvas.save();
                canvas.translate(shape.x, shape.y);
                canvas.rotate(shape.rotation);

                // Şekil rengini rastgele ata (3 renkten biri)
                int c = shape.type == 0 ? color1 : (shape.type == 1 ? color2 : color3);
                shapePaint.setColor(c);
                shapePaint.setAlpha((int) (viewAlpha * 0.25f)); // Çok hafif şeffaf

                if (shape.type == 0) {
                    canvas.drawCircle(0, 0, shape.size, shapePaint);
                } else if (shape.type == 1) {
                    Path tri = new Path();
                    tri.moveTo(0, -shape.size);
                    tri.lineTo(shape.size * 0.866f, shape.size * 0.5f);
                    tri.lineTo(-shape.size * 0.866f, shape.size * 0.5f);
                    tri.close();
                    canvas.drawPath(tri, shapePaint);
                } else {
                    canvas.drawRect(-shape.size/2, -shape.size/2, shape.size/2, shape.size/2, shapePaint);
                }
                canvas.restore();
            }
        }

        @Override
        public void start() {
            if (running) return;
            running = true;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.RESTART);
            long base = LocalDownloadsUIState.scaleDuration(ctx, 15000L); // Yavaş akış
            animator.setDuration(Math.max(3000L, base));
            animator.setInterpolator(new LinearInterpolator());
            
            animator.addUpdateListener(a -> {
                if (isLowPowerCached()) {
                    long now = System.currentTimeMillis();
                    if (now - lastInvalidateMs < 66L) return;
                    lastInvalidateMs = now;
                }
                phase = (float) a.getAnimatedValue();

                // Şekillerin pozisyonlarını güncelle
                for (FloatingShape shape : shapes) {
                    shape.x += shape.vx;
                    shape.y += shape.vy;
                    shape.rotation += shape.rotationSpeed;

                    // Ekran dışına çıkanları yukarıdan/aşağıdan geri döndür
                    if (shape.y < -shape.size * 2) {
                        shape.y = cachedH + shape.size * 2;
                        shape.x = random.nextFloat() * cachedW;
                    }
                    if (shape.x < -shape.size * 2) shape.x = cachedW + shape.size;
                    if (shape.x > cachedW + shape.size * 2) shape.x = -shape.size;
                }

                invalidateSelf();
            });
            animator.start();
        }

        @Override
        public void stop() {
            running = false;
            if (animator != null) {
                try { animator.cancel(); } catch (Exception ignored) {}
                animator = null;
            }
        }

        @Override public boolean isRunning() { return running; }

        @Override
        public void setAlpha(int alpha) {
            viewAlpha = alpha;
            invalidateSelf();
        }

        @Override public void setColorFilter(@Nullable ColorFilter cf) { paint.setColorFilter(cf); shapePaint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final class FloatingShape {
        float x, y, size;
        float vx, vy;
        int type; // 0=daire, 1=üçgen, 2=kare
        float rotation;
        float rotationSpeed;

        FloatingShape(float x, float y, float size, float vx, float vy, int type, float rotation, float rotationSpeed) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.vx = vx;
            this.vy = vy;
            this.type = type;
            this.rotation = rotation;
            this.rotationSpeed = rotationSpeed;
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int blend(int c1, int c2, float t) {
        float inv = 1f - t;
        int r = (int) (Color.red(c1) * inv + Color.red(c2) * t);
        int g = (int) (Color.green(c1) * inv + Color.green(c2) * t);
        int b = (int) (Color.blue(c1) * inv + Color.blue(c2) * t);
        return Color.rgb(r, g, b);
    }
}