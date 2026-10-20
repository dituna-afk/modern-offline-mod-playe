package app.morphe.extension.music.patches.downloads;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.CustomDialog;

public final class LocalDownloadsDialogs {

    private LocalDownloadsDialogs() {}

    public static void showSettingsDialog(Activity activity, LocalDownloadsFragment fragment) {
        if (activity == null || fragment == null) return;
        File musicRoot = fragment.getMusicRoot();

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout overlay = new FrameLayout(activity);
        overlay.setBackgroundColor(Color.argb(150, 0, 0, 0));

        ScrollView sheetScroll = new ScrollView(activity);
        sheetScroll.setFillViewport(true);

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(
                LocalDownloadsUIState.dp(activity, 20),
                LocalDownloadsUIState.dp(activity, 16),
                LocalDownloadsUIState.dp(activity, 20),
                LocalDownloadsUIState.dp(activity, 28)
        );
        sheet.setBackground(LocalDownloadsUIState.bottomSheetSurfaceDrawable(activity));
        sheet.setClickable(true);

        View handle = new View(activity);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setCornerRadius(LocalDownloadsUIState.dp(activity, 3));
        handleBg.setColor(Color.argb(90, 255, 255, 255));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(activity, 42), LocalDownloadsUIState.dp(activity, 4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(activity, 14));
        sheet.addView(handle, handleLp);

        int screenH = activity.getResources().getDisplayMetrics().heightPixels;

        LinearLayout topRow = new LinearLayout(activity);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = LocalDownloadsUIState.text(activity, "⚙️ Ayarlar & Özelleştirme", 19, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        topRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageButton closeBtn = LocalDownloadsUIState.icon(
                activity,
                LocalDownloadsUIState.createVectorIconDrawable(activity, LocalDownloadsUIState.ICON_CLOSE, LocalDownloadsUIState.primaryTextColor(), 0),
                "Kapat"
        );
        closeBtn.setOnClickListener(v ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, null));
        topRow.addView(closeBtn, new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(activity, 36), LocalDownloadsUIState.dp(activity, 36)));
        sheet.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        // 1. Tema Paletleri
        TextView secTheme = LocalDownloadsUIState.text(
                activity, "🎨 Tema Paleti (CSS Variables)", 14, LocalDownloadsUIState.activeAccentColor());
        secTheme.setTypeface(Typeface.DEFAULT_BOLD);
        secTheme.setPadding(0, LocalDownloadsUIState.dp(activity, 14), 0, LocalDownloadsUIState.dp(activity, 8));
        sheet.addView(secTheme);

        for (LocalDownloadsUIState.ThemePalette palette : LocalDownloadsUIState.ThemePalette.values()) {
            boolean isSelected = (palette == LocalDownloadsUIState.currentTheme);
            LinearLayout themeRow = new LinearLayout(activity);
            themeRow.setGravity(Gravity.CENTER_VERTICAL);
            themeRow.setPadding(
                    LocalDownloadsUIState.dp(activity, 12),
                    LocalDownloadsUIState.dp(activity, 10),
                    LocalDownloadsUIState.dp(activity, 12),
                    LocalDownloadsUIState.dp(activity, 10)
            );
            themeRow.setBackground(LocalDownloadsUIState.glassCardDrawable(
                    activity, LocalDownloadsUIState.dp(activity, 10), isSelected));
            LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(-1, -2);
            trLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(activity, 6));
            themeRow.setLayoutParams(trLp);

            View swatch = new View(activity);
            GradientDrawable swBg = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{palette.varAccentPrimary, palette.varAccentSecondary});
            swBg.setShape(GradientDrawable.OVAL);
            swatch.setBackground(swBg);
            themeRow.addView(swatch, new LinearLayout.LayoutParams(
                    LocalDownloadsUIState.dp(activity, 22), LocalDownloadsUIState.dp(activity, 22)));

            TextView tName = LocalDownloadsUIState.text(activity, "   " + palette.label, 14, LocalDownloadsUIState.primaryTextColor());
            if (isSelected) tName.setTypeface(Typeface.DEFAULT_BOLD);
            themeRow.addView(tName, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView badge = LocalDownloadsUIState.text(
                    activity, isSelected ? "Aktif ✓" : "Seç", 12,
                    isSelected ? LocalDownloadsUIState.activeAccentColor() : LocalDownloadsUIState.secondary());
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            themeRow.addView(badge);

            themeRow.setOnClickListener(v -> {
                LocalDownloadsUIState.currentTheme = palette;
                LocalDownloadsUIState.savePreferences(activity);
                LocalDownloadsUIState.refreshAllThemeVisuals(fragment);
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> Utils.showToastShort("Tema uygulandı: " + palette.label));
            });
            sheet.addView(themeRow);
        }

        // 2. Özel Duvar Kağıdı & Animasyon Profili
        TextView secWall = LocalDownloadsUIState.text(
                activity, "🖼️ Duvar Kağıdı & Animasyon Stili", 14, LocalDownloadsUIState.activeAccentColor());
        secWall.setTypeface(Typeface.DEFAULT_BOLD);
        secWall.setPadding(0, LocalDownloadsUIState.dp(activity, 14), 0, LocalDownloadsUIState.dp(activity, 8));
        sheet.addView(secWall);

        LinearLayout wallRow = new LinearLayout(activity);
        wallRow.setOrientation(LinearLayout.HORIZONTAL);
        wallRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView uploadWallBtn = LocalDownloadsUIState.createPillButton(activity, "📂 Duvar Kağıdı Yükle", true);
        uploadWallBtn.setOnClickListener(v ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> fragment.pickImageFromGallery(LocalDownloadsUIState.REQ_PICK_THEME_WALLPAPER)));
        wallRow.addView(uploadWallBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        if (LocalDownloadsUIState.customWallpaperActive && LocalDownloadsUIState.customWallpaperBitmap != null) {
            TextView resetWallBtn = LocalDownloadsUIState.createPillButton(activity, "🗑️ Sıfırla", false);
            LinearLayout.LayoutParams rwLp = new LinearLayout.LayoutParams(-2, -2);
            rwLp.setMargins(LocalDownloadsUIState.dp(activity, 8), 0, 0, 0);
            resetWallBtn.setLayoutParams(rwLp);
            resetWallBtn.setOnClickListener(v ->
                    LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                            () -> LocalDownloadsUIState.resetCustomWallpaper(activity, musicRoot, fragment)));
            wallRow.addView(resetWallBtn);
        }
        sheet.addView(wallRow, new LinearLayout.LayoutParams(-1, -2));

        TextView animProfileRow = LocalDownloadsUIState.text(
                activity,
                "✨ Animasyon Stili: " + LocalDownloadsAnimator.getSavedAnimationProfile(activity).label,
                14, LocalDownloadsUIState.primaryTextColor());
        animProfileRow.setTypeface(Typeface.DEFAULT_BOLD);
        animProfileRow.setPadding(
                LocalDownloadsUIState.dp(activity, 14),
                LocalDownloadsUIState.dp(activity, 12),
                LocalDownloadsUIState.dp(activity, 14),
                LocalDownloadsUIState.dp(activity, 12)
        );
        animProfileRow.setBackground(LocalDownloadsUIState.glassCardDrawable(
                activity, LocalDownloadsUIState.dp(activity, 10), false));
        animProfileRow.setOnClickListener(v -> {
            LocalDownloadsAnimator.AnimationProfile[] profiles = LocalDownloadsAnimator.AnimationProfile.values();
            String[] names = new String[profiles.length];
            for (int i = 0; i < profiles.length; i++) names[i] = profiles[i].label;
            LocalDownloadsUIState.showMenu(activity, animProfileRow, names,
                    LocalDownloadsAnimator.getSavedAnimationProfile(activity).ordinal(), pos -> {
                        LocalDownloadsAnimator.setSavedAnimationProfile(activity, profiles[pos]);
                        animProfileRow.setText("✨ Animasyon Stili: " + profiles[pos].label);
                        Utils.showToastShort("Animasyon stili: " + profiles[pos].label);
                    });
        });
        LinearLayout.LayoutParams apLp = new LinearLayout.LayoutParams(-1, -2);
        apLp.setMargins(0, LocalDownloadsUIState.dp(activity, 8), 0, 0);
        sheet.addView(animProfileRow, apLp);

        // Oynatıcı görsel efektleri & profiller
        TextView playerEffectsHeader = LocalDownloadsUIState.text(activity,
                "🎬 Oynatıcı Efektleri & Profiller", 14, LocalDownloadsUIState.activeAccentColor());
        playerEffectsHeader.setTypeface(Typeface.DEFAULT_BOLD);
        playerEffectsHeader.setPadding(0, LocalDownloadsUIState.dp(activity, 16), 0, LocalDownloadsUIState.dp(activity, 8));
        sheet.addView(playerEffectsHeader);

        addSettingsActionItem(activity, sheet, "🎬 Gelişmiş Efektler, Animasyonlar & Profiller", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> showPlayerEffectsDialog(activity, fragment)));

        addSettingsActionItem(activity, sheet, "🎧 Kulaklık Kontrollerini Özelleştir", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> showHeadphoneControlsDialog(activity)));

        // 3. İndirme, Otomatik Analiz ve Önbellek Yönetimi
        TextView secCache = LocalDownloadsUIState.text(
                activity, "📦 Akıllı İndirme ve Önbellek Yönetimi", 14, LocalDownloadsUIState.activeAccentColor());
        secCache.setTypeface(Typeface.DEFAULT_BOLD);
        secCache.setPadding(0, LocalDownloadsUIState.dp(activity, 16), 0, LocalDownloadsUIState.dp(activity, 8));
        sheet.addView(secCache);

        Set<String> inPlaylists = LocalDownloadsUIState.getAllPlaylistTrackIds();
        int orphanCount = 0;
        int brokenCount = 0;
        long totalBytes = 0;
        for (OfflineTrack t : LocalDownloadsUIState.tracks) {
            totalBytes += t.audioFile().length();
            if (!inPlaylists.contains(t.videoId())) orphanCount++;
            if (LocalDownloadsUIState.isBrokenOrIncompleteTrack(t)) brokenCount++;
        }
        final int finalOrphanCount = orphanCount;
        final int finalBrokenCount = brokenCount;

        TextView storageInfo = LocalDownloadsUIState.text(
                activity,
                "Toplam Depolama: " + LocalDownloadsUIState.tracks.size() + " Şarkı (" + LocalDownloadsUIState.formatSize(totalBytes) + " MB)",
                12, LocalDownloadsUIState.secondary());
        storageInfo.setPadding(0, 0, 0, LocalDownloadsUIState.dp(activity, 8));
        sheet.addView(storageInfo);

        SharedPreferences prefs = activity.getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE);
        boolean autoWifi = prefs.getBoolean(OfflineIntegrationPatch.PREF_AUTO_DL_ENABLED, true);
        int threshold = prefs.getInt(OfflineIntegrationPatch.PREF_AUTO_DL_THRESHOLD, 3);
        boolean wifiOnly = prefs.getBoolean(OfflineIntegrationPatch.PREF_AUTO_DL_WIFI_ONLY, true);

        addSettingsActionItem(activity, sheet,
                "📡 Sık Dinlenenleri Otomatik İndirme Ayarları ("
                        + (autoWifi ? threshold + " Dinleme • " + (wifiOnly ? "Wi-Fi" : "Mobil+Wi-Fi") : "KAPALI") + ")",
                () -> LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> showAutoDownloadConfigDialog(activity, fragment)));

        TextView swipeNextToggle = LocalDownloadsUIState.text(
                activity,
                "➡️ Hassas Sağa Kaydırarak Sıradaki: " + (LocalDownloadsUIState.swipeToPlayNext ? "AÇIK ✓" : "KAPALI"),
                14, LocalDownloadsUIState.primaryTextColor());
        swipeNextToggle.setTypeface(Typeface.DEFAULT_BOLD);
        swipeNextToggle.setPadding(
                LocalDownloadsUIState.dp(activity, 14),
                LocalDownloadsUIState.dp(activity, 12),
                LocalDownloadsUIState.dp(activity, 14),
                LocalDownloadsUIState.dp(activity, 12)
        );
        swipeNextToggle.setBackground(LocalDownloadsUIState.glassCardDrawable(
                activity, LocalDownloadsUIState.dp(activity, 10), LocalDownloadsUIState.swipeToPlayNext));
        swipeNextToggle.setOnClickListener(v -> {
            LocalDownloadsUIState.swipeToPlayNext = !LocalDownloadsUIState.swipeToPlayNext;
            LocalDownloadsUIState.savePreferences(activity);
            swipeNextToggle.setText("➡️ Hassas Sağa Kaydırarak Sıradaki: " + (LocalDownloadsUIState.swipeToPlayNext ? "AÇIK ✓" : "KAPALI"));
            swipeNextToggle.setBackground(LocalDownloadsUIState.glassCardDrawable(
                    activity, LocalDownloadsUIState.dp(activity, 10), LocalDownloadsUIState.swipeToPlayNext));
            Utils.showToastShort(LocalDownloadsUIState.swipeToPlayNext
                    ? "Hassas sağa kaydırma aktif"
                    : "Sağa kaydırma kapatıldı");
        });
        LinearLayout.LayoutParams swipeToggleLp = new LinearLayout.LayoutParams(-1, -2);
        swipeToggleLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(activity, 6));
        sheet.addView(swipeNextToggle, swipeToggleLp);

        addSettingsActionItem(activity, sheet, "🎤 Şarkı Sözleri, Türkçe Şiir & DeepL Çeviri Merkezi", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> showLyricsLibraryDialog(activity, fragment)));

        addSettingsActionItem(activity, sheet, "⬇️ İndirme Kuyruğu Yöneticisini Aç (" + LocalDownloadManager.getTotalQueueCount() + ")", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> showDownloadManagerDialog(activity, fragment)));

        addSettingsActionItem(activity, sheet, "🧹 Görsel & Meta Veri Önbelleğini Temizle (RAM/LruCache)", () -> {
            LocalDownloadsUIState.ARTWORK_CACHE.evictAll();
            LocalDownloadsUIState.TRACK_CACHE.clear();
            LocalDownloadsUIState.diskReloadNeeded = true;
            LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, () -> {
                fragment.showTracks();
                Utils.showToastShort("Önbellek temizlendi ve yeniden yüklendi");
            });
        });

        addSettingsActionItem(activity, sheet, "🩹 Yarım Kalan (.part) İndirme Kalıntılarını Sil", () -> {
            int c = fragment.cleanPartFiles();
            Utils.showToastShort(c + " yarım (.part) dosya temizlendi");
        });

        addSettingsActionItem(activity, sheet, "🔍 Sahipsiz (Listesiz) Şarkıları Ayıkla (" + finalOrphanCount + ")", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, () -> {
                    LocalDownloadsUIState.selectedPlaylist = null;
                    LocalDownloadsUIState.showingArtists = false;
                    LocalDownloadsUIState.showingPlaylistsOverview = false;
                    LocalDownloadsUIState.currentFilterMode = LocalDownloadsUIState.FilterMode.UNASSIGNED;
                    LocalDownloadsUIState.selectionMode = true;
                    LocalDownloadsUIState.selectedVideoIds.clear();
                    for (OfflineTrack t : LocalDownloadsUIState.tracks) {
                        if (!inPlaylists.contains(t.videoId())) LocalDownloadsUIState.selectedVideoIds.add(t.videoId());
                    }
                    fragment.showTracks();
                }));

        addSettingsActionItem(activity, sheet, "⚠️ Hatalı / Eksik İnen Şarkıları Ayıkla (" + finalBrokenCount + ")", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, () -> {
                    LocalDownloadsUIState.selectedPlaylist = null;
                    LocalDownloadsUIState.showingArtists = false;
                    LocalDownloadsUIState.showingPlaylistsOverview = false;
                    LocalDownloadsUIState.currentFilterMode = LocalDownloadsUIState.FilterMode.BROKEN_OR_INCOMPLETE;
                    LocalDownloadsUIState.selectionMode = true;
                    LocalDownloadsUIState.selectedVideoIds.clear();
                    for (OfflineTrack t : LocalDownloadsUIState.tracks) {
                        if (LocalDownloadsUIState.isBrokenOrIncompleteTrack(t)) {
                            LocalDownloadsUIState.selectedVideoIds.add(t.videoId());
                        }
                    }
                    fragment.showTracks();
                }));

        addSettingsActionItem(activity, sheet, "🗑️ Tüm İndirilen Şarkıları Kalıcı Olarak Sil (" + LocalDownloadsUIState.tracks.size() + ")", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, fragment::confirmDeleteAll));

        sheetScroll.addView(sheet);
        overlay.addView(sheetScroll, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        overlay.setOnClickListener(v ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, null));

        dialog.setContentView(overlay);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            OfflineHeaderBlocker.applyImmersiveFullscreen(dialog.getWindow(), true);
        }
        dialog.show();
        LocalDownloadsAnimator.animateBottomSheetShow(sheet, overlay, screenH);
    }

    private static void showHeadphoneControlsDialog(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        SharedPreferences prefs = activity.getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = LocalDownloadsUIState.dp(activity, 20);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(LocalDownloadsUIState.bottomSheetSurfaceDrawable(activity));

        TextView title = LocalDownloadsUIState.text(activity, "🎧 Kulaklık Kontrolleri", 18, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);
        TextView note = LocalDownloadsUIState.text(activity,
                "Kulaklığının bildirdiği medya tuşlarına göre çalışır. Bazı modeller çift/uzun basışı Android'e iletmez.",
                12, LocalDownloadsUIState.secondary());
        note.setPadding(0, LocalDownloadsUIState.dp(activity, 6), 0, LocalDownloadsUIState.dp(activity, 12));
        box.addView(note);

        String[] pressKeys = {"headset_single_action", "headset_double_action", "headset_long_action"};
        String[] pressLabels = {"Tek basış", "Çift basış", "Uzun basış"};
        String[] actions = {"Oynat / Duraklat", "Sonraki şarkı", "Önceki şarkı", "Ses artır", "Ses azalt", "İşlem yok"};
        String[] actionIds = {"TOGGLE", "NEXT", "PREVIOUS", "VOLUME_UP", "VOLUME_DOWN", "NONE"};
        for (int i = 0; i < pressKeys.length; i++) {
            final String key = pressKeys[i];
            final String pressLabel = pressLabels[i];
            String saved = prefs.getString(key, i == 0 ? "TOGGLE" : (i == 1 ? "NEXT" : "PREVIOUS"));
            int selected = 0;
            for (int j = 0; j < actionIds.length; j++) if (actionIds[j].equals(saved)) selected = j;
            TextView row = LocalDownloadsUIState.text(activity, pressLabel + ": " + actions[selected], 14, LocalDownloadsUIState.primaryTextColor());
            row.setPadding(LocalDownloadsUIState.dp(activity, 12), LocalDownloadsUIState.dp(activity, 12),
                    LocalDownloadsUIState.dp(activity, 12), LocalDownloadsUIState.dp(activity, 12));
            row.setBackground(LocalDownloadsUIState.glassCardDrawable(activity, LocalDownloadsUIState.dp(activity, 10), false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(activity, 7));
            box.addView(row, lp);
            row.setOnClickListener(v -> {
                int current = 0;
                String now = prefs.getString(key, "TOGGLE");
                for (int j = 0; j < actionIds.length; j++) if (actionIds[j].equals(now)) current = j;
                LocalDownloadsUIState.showMenu(activity, row, actions, current, pos -> {
                    prefs.edit().putString(key, actionIds[pos]).apply();
                    row.setText(pressLabel + ": " + actions[pos]);
                    Utils.showToastShort(pressLabel + " eşlemesi kaydedildi");
                });
            });
        }
        new AlertDialog.Builder(activity).setView(box).setPositiveButton("Tamam", null)
                .setNeutralButton("Varsayılanlar", (d, which) -> prefs.edit()
                        .putString("headset_single_action", "TOGGLE")
                        .putString("headset_double_action", "NEXT")
                        .putString("headset_long_action", "PREVIOUS").apply())
                .show();
    }

    private static void addSettingsActionItem(Context ctx, LinearLayout parent, String label, Runnable onClick) {
        TextView item = LocalDownloadsUIState.text(ctx, label, 14, LocalDownloadsUIState.primaryTextColor());
        item.setPadding(
                LocalDownloadsUIState.dp(ctx, 14),
                LocalDownloadsUIState.dp(ctx, 12),
                LocalDownloadsUIState.dp(ctx, 14),
                LocalDownloadsUIState.dp(ctx, 12)
        );
        item.setBackground(LocalDownloadsUIState.glassCardDrawable(ctx, LocalDownloadsUIState.dp(ctx, 10), false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(ctx, 6));
        item.setLayoutParams(lp);
        item.setOnClickListener(v -> onClick.run());
        parent.addView(item);
    }

    public static void showAutoDownloadConfigDialog(Activity activity, LocalDownloadsFragment fragment) {
        if (activity == null) return;
        SharedPreferences prefs = activity.getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = LocalDownloadsUIState.dp(activity, 20);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(LocalDownloadsUIState.bottomSheetSurfaceDrawable(activity));

        TextView title = LocalDownloadsUIState.text(activity, "📡 Otomatik İndirme Özelleştirme", 17, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);

        TextView sub = LocalDownloadsUIState.text(
                activity,
                "Online modda dinlediğin şarkıları analiz eder ve belirlediğin kurallara göre otomatik indirir.",
                12, LocalDownloadsUIState.secondary());
        sub.setPadding(0, LocalDownloadsUIState.dp(activity, 4), 0, LocalDownloadsUIState.dp(activity, 12));
        box.addView(sub);

        final boolean[] enabled = {prefs.getBoolean(OfflineIntegrationPatch.PREF_AUTO_DL_ENABLED, true)};
        final int[] threshold = {prefs.getInt(OfflineIntegrationPatch.PREF_AUTO_DL_THRESHOLD, 3)};
        final int[] minSec = {prefs.getInt(OfflineIntegrationPatch.PREF_AUTO_DL_MIN_SECONDS, 15)};
        final boolean[] wifiOnly = {prefs.getBoolean(OfflineIntegrationPatch.PREF_AUTO_DL_WIFI_ONLY, true)};
        final String[] targetList = {prefs.getString(OfflineIntegrationPatch.PREF_AUTO_DL_TARGET_LIST, "Sık Dinlenenler (Oto)")};

        TextView toggleBtn = LocalDownloadsUIState.text(
                activity, "Durum: " + (enabled[0] ? "AÇIK ✓" : "KAPALI ✕"), 14, LocalDownloadsUIState.primaryTextColor());
        toggleBtn.setTypeface(Typeface.DEFAULT_BOLD);
        toggleBtn.setPadding(LocalDownloadsUIState.dp(activity, 14), LocalDownloadsUIState.dp(activity, 11), LocalDownloadsUIState.dp(activity, 14), LocalDownloadsUIState.dp(activity, 11));
        toggleBtn.setBackground(LocalDownloadsUIState.glassCardDrawable(activity, LocalDownloadsUIState.dp(activity, 10), enabled[0]));
        toggleBtn.setOnClickListener(v -> {
            enabled[0] = !enabled[0];
            toggleBtn.setText("Durum: " + (enabled[0] ? "AÇIK ✓" : "KAPALI ✕"));
            toggleBtn.setBackground(LocalDownloadsUIState.glassCardDrawable(activity, LocalDownloadsUIState.dp(activity, 10), enabled[0]));
        });
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(activity, 8));
        box.addView(toggleBtn, rowLp);

        TextView countBtn = LocalDownloadsUIState.text(
                activity, "🔢 Kaç Kez Dinlenince İndirilsin: " + threshold[0] + " Kez", 14, LocalDownloadsUIState.primaryTextColor());
        countBtn.setPadding(LocalDownloadsUIState.dp(activity, 14), LocalDownloadsUIState.dp(activity, 11), LocalDownloadsUIState.dp(activity, 14), LocalDownloadsUIState.dp(activity, 11));
        countBtn.setBackground(LocalDownloadsUIState.glassCardDrawable(activity, LocalDownloadsUIState.dp(activity, 10), false));
        countBtn.setOnClickListener(v -> {
            int[] vals = {1, 2, 3, 5, 10};
            String[] items = {"1 Kez (Anında İndir)", "2 Kez Dinlenince", "3 Kez Dinlenince (Önerilen)", "5 Kez Dinlenince", "10 Kez Dinlenince"};
            LocalDownloadsUIState.showMenu(activity, countBtn, items, -1, pos -> {
                threshold[0] = vals[pos];
                countBtn.setText("🔢 Kaç Kez Dinlenince İndirilsin: " + threshold[0] + " Kez");
            });
        });
        box.addView(countBtn, rowLp);

        TextView secBtn = LocalDownloadsUIState.text(
                activity, "⏱️ Sayım İçin Minimum Dinleme Süresi: " + minSec[0] + " Saniye", 14, LocalDownloadsUIState.primaryTextColor());
        secBtn.setPadding(LocalDownloadsUIState.dp(activity, 14), LocalDownloadsUIState.dp(activity, 11), LocalDownloadsUIState.dp(activity, 14), LocalDownloadsUIState.dp(activity, 11));
        secBtn.setBackground(LocalDownloadsUIState.glassCardDrawable(activity, LocalDownloadsUIState.dp(activity, 10), false));
        secBtn.setOnClickListener(v -> {
            int[] vals = {10, 15, 30, 45};
            String[] items = {"10 Saniye", "15 Saniye (Önerilen)", "30 Saniye", "45 Saniye"};
            LocalDownloadsUIState.showMenu(activity, secBtn, items, -1, pos -> {
                minSec[0] = vals[pos];
                secBtn.setText("⏱️ Sayım İçin Minimum Dinleme Süresi: " + minSec[0] + " Saniye");
            });
        });
        box.addView(secBtn, rowLp);

        TextView netBtn = LocalDownloadsUIState.text(
                activity, "🌐 Ağ Tercihi: " + (wifiOnly[0] ? "Sadece Wi-Fi" : "Wi-Fi + Mobil Veri"), 14, LocalDownloadsUIState.primaryTextColor());
        netBtn.setPadding(LocalDownloadsUIState.dp(activity, 14), LocalDownloadsUIState.dp(activity, 11), LocalDownloadsUIState.dp(activity, 14), LocalDownloadsUIState.dp(activity, 11));
        netBtn.setBackground(LocalDownloadsUIState.glassCardDrawable(activity, LocalDownloadsUIState.dp(activity, 10), false));
        netBtn.setOnClickListener(v -> {
            String[] items = {"Sadece Wi-Fi Bağlantısında", "Wi-Fi + Mobil Veri (Her Zaman)"};
            LocalDownloadsUIState.showMenu(activity, netBtn, items, wifiOnly[0] ? 0 : 1, pos -> {
                wifiOnly[0] = (pos == 0);
                netBtn.setText("🌐 Ağ Tercihi: " + (wifiOnly[0] ? "Sadece Wi-Fi" : "Wi-Fi + Mobil Veri"));
            });
        });
        box.addView(netBtn, rowLp);

        TextView targetBtn = LocalDownloadsUIState.text(
                activity, "📁 Hedef Liste: " + (targetList[0].isEmpty() ? "Sadece Tüm Şarkılar" : targetList[0]), 14, LocalDownloadsUIState.primaryTextColor());
        targetBtn.setPadding(LocalDownloadsUIState.dp(activity, 14), LocalDownloadsUIState.dp(activity, 11), LocalDownloadsUIState.dp(activity, 14), LocalDownloadsUIState.dp(activity, 11));
        targetBtn.setBackground(LocalDownloadsUIState.glassCardDrawable(activity, LocalDownloadsUIState.dp(activity, 10), false));
        targetBtn.setOnClickListener(v -> {
            List<String> opts = new ArrayList<>();
            opts.add("Sık Dinlenenler (Oto)");
            opts.add(OfflineStorage.SPECIAL_LIKED_SONGS);
            opts.add("Sadece Tüm Şarkılar (Listesiz)");
            for (OfflineStorage.Playlist p : LocalDownloadsUIState.playlists) {
                if (!opts.contains(p.name())) opts.add(p.name());
            }
            String[] arr = opts.toArray(new String[0]);
            LocalDownloadsUIState.showMenu(activity, targetBtn, arr, -1, pos -> {
                targetList[0] = (pos == 2) ? "" : arr[pos];
                targetBtn.setText("📁 Hedef Liste: " + (targetList[0].isEmpty() ? "Sadece Tüm Şarkılar" : targetList[0]));
            });
        });
        box.addView(targetBtn, rowLp);

        LinearLayout bottomRow = new LinearLayout(activity);
        bottomRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        bottomRow.setPadding(0, LocalDownloadsUIState.dp(activity, 6), 0, 0);

        TextView resetStatsBtn = LocalDownloadsUIState.createPillButton(activity, "🧹 Sayaçları Sıfırla", false);
        resetStatsBtn.setOnClickListener(v -> {
            OfflineIntegrationPatch.clearOnlinePlayStats(activity);
            Utils.showToastShort("Online dinleme sayaçları sıfırlandı");
        });
        bottomRow.addView(resetStatsBtn);

        TextView saveBtn = LocalDownloadsUIState.createPillButton(activity, "Kaydet", true);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-2, -2);
        sLp.setMargins(LocalDownloadsUIState.dp(activity, 8), 0, 0, 0);
        saveBtn.setLayoutParams(sLp);
        bottomRow.addView(saveBtn);
        box.addView(bottomRow);

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(box).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        saveBtn.setOnClickListener(v -> {
            prefs.edit()
                    .putBoolean(OfflineIntegrationPatch.PREF_AUTO_DL_ENABLED, enabled[0])
                    .putInt(OfflineIntegrationPatch.PREF_AUTO_DL_THRESHOLD, threshold[0])
                    .putInt(OfflineIntegrationPatch.PREF_AUTO_DL_MIN_SECONDS, minSec[0])
                    .putBoolean(OfflineIntegrationPatch.PREF_AUTO_DL_WIFI_ONLY, wifiOnly[0])
                    .putString(OfflineIntegrationPatch.PREF_AUTO_DL_TARGET_LIST, targetList[0])
                    .apply();
            dialog.dismiss();
            Utils.showToastShort("Otomatik indirme ayarları kaydedildi");
        });
        dialog.show();
    }

    public static void showDownloadManagerDialog(Activity act, LocalDownloadsFragment fragment) {
        if (act == null || fragment == null) return;
        File musicRoot = fragment.getMusicRoot();

        LinearLayout sheet = new LinearLayout(act);
        sheet.setOrientation(LinearLayout.VERTICAL);
        int pad = LocalDownloadsUIState.dp(act, 18);
        sheet.setPadding(pad, pad, pad, pad);
        sheet.setBackground(LocalDownloadsUIState.bottomSheetSurfaceDrawable(act));

        TextView title = LocalDownloadsUIState.text(act, "⬇️ Gelişmiş İndirme & Kuyruk Yöneticisi", 17, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        sheet.addView(title);

        LocalDownloadsUIState.queueDialogSummaryText = LocalDownloadsUIState.text(act, "", 12, LocalDownloadsUIState.secondary());
        LocalDownloadsUIState.queueDialogSummaryText.setPadding(0, LocalDownloadsUIState.dp(act, 4), 0, LocalDownloadsUIState.dp(act, 10));
        sheet.addView(LocalDownloadsUIState.queueDialogSummaryText);

        HorizontalScrollView actionScroll = new HorizontalScrollView(act);
        actionScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actionRow = new LinearLayout(act);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(0, 0, 0, LocalDownloadsUIState.dp(act, 10));

        LocalDownloadsUIState.queueDialogPauseBtn = LocalDownloadsUIState.createPillButton(
                act, LocalDownloadManager.isPaused() ? "▶ Devam Ettir" : "⏸ Tümünü Duraklat", false);
        LocalDownloadsUIState.queueDialogPauseBtn.setOnClickListener(v -> {
            LocalDownloadManager.setPaused(!LocalDownloadManager.isPaused());
            fragment.refreshDownloadStatusUI();
        });
        actionRow.addView(LocalDownloadsUIState.queueDialogPauseBtn);

        TextView retryFailedBtn = LocalDownloadsUIState.createPillButton(act, "🔄 Hatalıları Tekrar Dene", false);
        LinearLayout.LayoutParams rfLp = new LinearLayout.LayoutParams(-2, -2);
        rfLp.setMargins(LocalDownloadsUIState.dp(act, 8), 0, 0, 0);
        retryFailedBtn.setLayoutParams(rfLp);
        retryFailedBtn.setOnClickListener(v -> {
            int count = LocalDownloadManager.retryFailedDownloads();
            Utils.showToastShort(count > 0 ? count + " hatalı indirme yeniden başlatıldı" : "Hatalı indirme yok");
            fragment.refreshDownloadStatusUI();
        });
        actionRow.addView(retryFailedBtn);

        TextView cancelAllBtn = LocalDownloadsUIState.createPillButton(act, "🛑 Tümünü İptal & Temizle", true);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-2, -2);
        cLp.setMargins(LocalDownloadsUIState.dp(act, 8), 0, 0, 0);
        cancelAllBtn.setLayoutParams(cLp);
        cancelAllBtn.setOnClickListener(v -> {
            LocalDownloadManager.cancelAllDownloads(musicRoot);
            fragment.cleanPartFiles();
            Utils.showToastShort("Tüm indirmeler iptal edildi ve kalıntılar silindi");
            fragment.refreshDownloadStatusUI();
        });
        actionRow.addView(cancelAllBtn);
        actionScroll.addView(actionRow);
        sheet.addView(actionScroll, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(act);
        LocalDownloadsUIState.queueDialogListContainer = new LinearLayout(act);
        LocalDownloadsUIState.queueDialogListContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(LocalDownloadsUIState.queueDialogListContainer);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, LocalDownloadsUIState.dp(act, 330)));

        LinearLayout bottomRow = new LinearLayout(act);
        bottomRow.setGravity(Gravity.END);
        bottomRow.setPadding(0, LocalDownloadsUIState.dp(act, 10), 0, 0);

        TextView cleanPartsBtn = LocalDownloadsUIState.createPillButton(act, "🧹 Yarım (.part) Dosyaları Sil", false);
        cleanPartsBtn.setOnClickListener(v -> {
            int removed = fragment.cleanPartFiles();
            Utils.showToastShort(removed + " yarım kalan (.part) dosya temizlendi");
        });
        bottomRow.addView(cleanPartsBtn);

        TextView closeBtn = LocalDownloadsUIState.createPillButton(act, "Kapat", false);
        LinearLayout.LayoutParams clLp = new LinearLayout.LayoutParams(-2, -2);
        clLp.setMargins(LocalDownloadsUIState.dp(act, 8), 0, 0, 0);
        closeBtn.setLayoutParams(clLp);
        bottomRow.addView(closeBtn);
        sheet.addView(bottomRow, new LinearLayout.LayoutParams(-1, -2));

        LocalDownloadsUIState.queueManagerDialog = new AlertDialog.Builder(act).setView(sheet).create();
        if (LocalDownloadsUIState.queueManagerDialog.getWindow() != null) {
            LocalDownloadsUIState.queueManagerDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        closeBtn.setOnClickListener(v -> LocalDownloadsUIState.queueManagerDialog.dismiss());

        populateQueueDialogItems(act, LocalDownloadManager.getQueueSnapshot(), LocalDownloadManager.isPaused(), fragment);
        LocalDownloadsUIState.queueManagerDialog.show();
    }

    public static void populateQueueDialogItems(Activity act, List<LocalDownloadManager.QueueItem> queue,
                                                boolean paused, LocalDownloadsFragment fragment) {
        if (act == null || LocalDownloadsUIState.queueDialogListContainer == null) return;
        File musicRoot = fragment.getMusicRoot();
        LocalDownloadsUIState.queueDialogListContainer.removeAllViews();

        if (LocalDownloadsUIState.queueDialogPauseBtn != null) {
            LocalDownloadsUIState.queueDialogPauseBtn.setText(paused ? "▶ Devam Ettir" : "⏸ Tümünü Duraklat");
        }

        List<LocalDownloadManager.QueueItem> failedItems = LocalDownloadManager.getFailedSnapshot();
        long totalSpeed = LocalDownloadManager.getTotalActiveSpeedBytesPerSec();

        if (LocalDownloadsUIState.queueDialogSummaryText != null) {
            String stats = "Aktif: " + LocalDownloadManager.getActiveDownloadCount()
                    + " • Sırada: " + queue.size()
                    + " • Tamamlanan: " + LocalDownloadManager.getCompletedSessionCount()
                    + " • Hatalı: " + failedItems.size();
            if (totalSpeed > 0) {
                stats += "\n⚡ Anlık Toplam Hız: " + LocalDownloadsUIState.formatSpeed(totalSpeed);
            }
            LocalDownloadsUIState.queueDialogSummaryText.setText(stats);
        }

        if (queue.isEmpty() && failedItems.isEmpty()) {
            TextView empty = LocalDownloadsUIState.text(act, "İndirme kuyruğu temiz ✨", 14, LocalDownloadsUIState.secondary());
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(LocalDownloadsUIState.dp(act, 16), LocalDownloadsUIState.dp(act, 40), LocalDownloadsUIState.dp(act, 16), LocalDownloadsUIState.dp(act, 40));
            LocalDownloadsUIState.queueDialogListContainer.addView(empty);
            return;
        }

        int limit = Math.min(queue.size(), 80);
        for (int i = 0; i < limit; i++) {
            LocalDownloadManager.QueueItem item = queue.get(i);
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(LocalDownloadsUIState.dp(act, 8), LocalDownloadsUIState.dp(act, 7), LocalDownloadsUIState.dp(act, 8), LocalDownloadsUIState.dp(act, 7));
            row.setBackground(LocalDownloadsUIState.rowBackground(act, item.active()));

            LinearLayout col = new LinearLayout(act);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView t = LocalDownloadsUIState.text(act, (item.active() ? "⬇️ " : "⏳ ") + item.title(), 13, LocalDownloadsUIState.primaryTextColor());
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            if (item.active()) t.setTypeface(Typeface.DEFAULT_BOLD);

            String sizeDetail = item.totalBytes() > 0
                    ? " • " + LocalDownloadsUIState.formatSize(item.downloadedBytes()) + "/" + LocalDownloadsUIState.formatSize(item.totalBytes()) + " MB"
                    : "";
            String speedDetail = item.active() && item.speedBytesPerSec() > 0
                    ? " • " + LocalDownloadsUIState.formatSpeed(item.speedBytesPerSec())
                    : "";
            String subTxt = item.playlistName()
                    + (item.active() ? " • %" + item.progress() + sizeDetail + speedDetail : " • Sırada bekliyor");
            TextView s = LocalDownloadsUIState.text(act, subTxt, 11, LocalDownloadsUIState.secondary());
            s.setSingleLine(true);
            col.addView(t);
            col.addView(s);
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

            if (!item.active() && i > 0) {
                TextView prioBtn = LocalDownloadsUIState.createPillButton(act, "⬆️", false);
                LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-2, -2);
                pLp.setMargins(0, 0, LocalDownloadsUIState.dp(act, 6), 0);
                prioBtn.setLayoutParams(pLp);
                prioBtn.setOnClickListener(v -> {
                    LocalDownloadManager.prioritizeInQueue(item.videoId());
                    Utils.showToastShort("Kuyrukta ilk sıraya taşındı");
                    fragment.refreshDownloadStatusUI();
                });
                row.addView(prioBtn);
            }

            TextView removeBtn = LocalDownloadsUIState.createPillButton(act, "✕", false);
            removeBtn.setOnClickListener(v -> {
                LocalDownloadManager.removeFromQueue(item.videoId());
                OfflineStorage.delete(new File(musicRoot, item.videoId() + ".webm.part"));
                OfflineStorage.delete(new File(musicRoot, item.videoId() + ".m4a.part"));
                fragment.refreshDownloadStatusUI();
            });
            row.addView(removeBtn);

            LocalDownloadsUIState.queueDialogListContainer.addView(row);
        }

        for (LocalDownloadManager.QueueItem failed : failedItems) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(LocalDownloadsUIState.dp(act, 8), LocalDownloadsUIState.dp(act, 7), LocalDownloadsUIState.dp(act, 8), LocalDownloadsUIState.dp(act, 7));
            row.setBackground(LocalDownloadsUIState.rowBackground(act, false));

            LinearLayout col = new LinearLayout(act);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView t = LocalDownloadsUIState.text(act, "⚠️ " + failed.title(), 13, Color.parseColor("#FF6E9E"));
            t.setSingleLine(true);
            TextView s = LocalDownloadsUIState.text(act, failed.playlistName() + " • İndirme başarısız oldu", 11, LocalDownloadsUIState.secondary());
            col.addView(t);
            col.addView(s);
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView retryOne = LocalDownloadsUIState.createPillButton(act, "🔄 Dene", true);
            retryOne.setOnClickListener(v -> {
                LocalDownloadManager.removeFromQueue(failed.videoId());
                LocalDownloadManager.enqueue(failed.videoId(), failed.playlistName(), true);
                fragment.refreshDownloadStatusUI();
            });
            row.addView(retryOne);
            LocalDownloadsUIState.queueDialogListContainer.addView(row);
        }
    }

    public static void showLyricsLibraryDialog(Activity activity, LocalDownloadsFragment fragment) {
        if (activity == null || fragment == null) return;
        File musicRoot = fragment.getMusicRoot();
        LocalDownloadManager.purgeCorruptedLyrics(musicRoot);
        LocalDownloadsUIState.diskReloadNeeded = true;
        LocalDownloadsUIState.TRACK_CACHE.clear();
        LocalDownloadsUIState.loadTracksIfNeeded(musicRoot);

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(
                LocalDownloadsUIState.dp(activity, 16),
                LocalDownloadsUIState.dp(activity, 16),
                LocalDownloadsUIState.dp(activity, 16),
                LocalDownloadsUIState.dp(activity, 14)
        );
        panel.setBackground(LocalDownloadsUIState.bottomSheetSurfaceDrawable(activity));

        TextView title = LocalDownloadsUIState.text(activity, "🎤 Söz, Şiir & DeepL Çeviri Merkezi", 17, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title);

        TextView summaryView = LocalDownloadsUIState.text(activity, "Söz ve şiir dosyaları taranıyor…", 12, LocalDownloadsUIState.secondary());
        summaryView.setPadding(0, LocalDownloadsUIState.dp(activity, 4), 0, LocalDownloadsUIState.dp(activity, 8));
        panel.addView(summaryView);

        HorizontalScrollView configScroll = new HorizontalScrollView(activity);
        configScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout configRow = new LinearLayout(activity);
        configRow.setOrientation(LinearLayout.HORIZONTAL);
        configRow.setPadding(0, 0, 0, LocalDownloadsUIState.dp(activity, 8));

        TextView sourceBtn = LocalDownloadsUIState.createPillButton(
                activity, "🌐 Kaynak: " + LocalDownloadManager.getSavedLyricsSource().label, true);
        sourceBtn.setOnClickListener(v -> {
            LocalDownloadManager.LyricsSource[] sources = LocalDownloadManager.LyricsSource.values();
            String[] labels = new String[sources.length];
            for (int i = 0; i < sources.length; i++) labels[i] = sources[i].label;
            LocalDownloadsUIState.showMenu(activity, sourceBtn, labels,
                    LocalDownloadManager.getSavedLyricsSource().ordinal(), pos -> {
                        LocalDownloadManager.setSavedLyricsSource(sources[pos]);
                        sourceBtn.setText("🌐 Kaynak: " + sources[pos].label);
                        Utils.showToastShort("Söz/Şiir kaynağı: " + sources[pos].label);
                    });
        });
        configRow.addView(sourceBtn);

        TextView engineBtn = LocalDownloadsUIState.createPillButton(
                activity, "🇹🇷 Motor: " + LocalDownloadManager.getSavedTranslationEngine().label, false);
        LinearLayout.LayoutParams engLp = new LinearLayout.LayoutParams(-2, -2);
        engLp.setMargins(LocalDownloadsUIState.dp(activity, 6), 0, LocalDownloadsUIState.dp(activity, 6), 0);
        engineBtn.setLayoutParams(engLp);
        engineBtn.setOnClickListener(v -> {
            LocalDownloadManager.TranslationEngine[] engines = LocalDownloadManager.TranslationEngine.values();
            String[] labels = new String[engines.length];
            for (int i = 0; i < engines.length; i++) labels[i] = engines[i].label;
            LocalDownloadsUIState.showMenu(activity, engineBtn, labels,
                    LocalDownloadManager.getSavedTranslationEngine().ordinal(), pos -> {
                        LocalDownloadManager.setSavedTranslationEngine(engines[pos]);
                        engineBtn.setText("🇹🇷 Motor: " + engines[pos].label);
                        Utils.showToastShort("Çeviri motoru: " + engines[pos].label);
                    });
        });
        configRow.addView(engineBtn);

        TextView deeplKeyBtn = LocalDownloadsUIState.createPillButton(activity, "🔑 DeepL Anahtarı", false);
        deeplKeyBtn.setOnClickListener(v -> showDeeplApiKeyDialog(activity));
        configRow.addView(deeplKeyBtn);

        configScroll.addView(configRow);
        panel.addView(configScroll, new LinearLayout.LayoutParams(-1, -2));

        EditText filterInput = new EditText(activity);
        filterInput.setHint("Şarkı veya şair ara (üzerine tıklayıp manuel söz/şiir çek)...");
        filterInput.setTextColor(LocalDownloadsUIState.primaryTextColor());
        filterInput.setHintTextColor(LocalDownloadsUIState.secondary());
        filterInput.setTextSize(13);
        filterInput.setSingleLine(true);
        panel.addView(filterInput, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(activity);
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, LocalDownloadsUIState.dp(activity, 300)));

        final String[] filterText = {""};
        Runnable[] renderRef = new Runnable[1];
        renderRef[0] = () -> {
            rows.removeAllViews();
            int readyCount = 0;
            int trNativeCount = 0;
            int missingCount = 0;
            SharedPreferences prefs = activity.getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE);
            List<OfflineTrack> sorted = new ArrayList<>(LocalDownloadsUIState.tracks);
            sorted.sort(Comparator.comparing(t -> t.displayTitle().toLowerCase(Locale.ROOT)));

            for (OfflineTrack track : sorted) {
                boolean hasLrc = track.lrcFile() != null && track.lrcFile().isFile() && track.lrcFile().length() > 8;
                boolean isNativeTr = LocalDownloadManager.isTrackLyricsNativeTurkish(musicRoot, track.videoId());
                boolean hasTr = LocalDownloadManager.hasTurkishTranslation(musicRoot, track.videoId());
                if (isNativeTr) trNativeCount++;
                if (hasTr) readyCount++;
                else missingCount++;

                if (!filterText[0].isEmpty()
                        && !track.displayTitle().toLowerCase(Locale.ROOT).contains(filterText[0])
                        && !track.displayArtist().toLowerCase(Locale.ROOT).contains(filterText[0])) {
                    continue;
                }

                boolean unavailable = prefs.getBoolean("lyrics_lookup_failed_" + track.videoId(), false);
                String state = isNativeTr ? "📖 Türkçe Şiir / Orijinal Söz Hazır (Çevirisiz)"
                        : (hasTr ? "✓ Orijinal + Türkçe Çeviri Hazır (DeepL)"
                        : (hasLrc ? "◐ Söz var · Türkçe çeviri yok"
                        : (unavailable ? "— Tam eşleşme bulunamadı (Elle aramak için dokun)" : "○ Henüz indirilmedi")));

                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(
                        LocalDownloadsUIState.dp(activity, 10),
                        LocalDownloadsUIState.dp(activity, 8),
                        LocalDownloadsUIState.dp(activity, 10),
                        LocalDownloadsUIState.dp(activity, 8)
                );
                row.setBackground(LocalDownloadsUIState.glassCardDrawable(activity, LocalDownloadsUIState.dp(activity, 10), false));
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                rowLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(activity, 5));
                row.setLayoutParams(rowLp);

                LinearLayout col = new LinearLayout(activity);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView name = LocalDownloadsUIState.text(activity, track.displayTitle() + " • " + track.displayArtist(), 13, LocalDownloadsUIState.primaryTextColor());
                name.setTypeface(Typeface.DEFAULT_BOLD);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                col.addView(name);

                TextView status = LocalDownloadsUIState.text(
                        activity, state, 11, hasTr ? LocalDownloadsUIState.activeSecondaryAccent() : LocalDownloadsUIState.secondary());
                status.setPadding(0, LocalDownloadsUIState.dp(activity, 2), 0, 0);
                col.addView(status);
                row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

                TextView manualBtn = LocalDownloadsUIState.createPillButton(activity, "🔍 Elle Bul", false);
                manualBtn.setOnClickListener(v -> showManualLyricsSearchDialog(activity, track, renderRef[0], fragment));
                row.addView(manualBtn);

                TextView pasteBtn = LocalDownloadsUIState.createPillButton(activity, "📝", false);
                pasteBtn.setContentDescription("Sözü elle yapıştır / düzenle");
                LinearLayout.LayoutParams pasteLp = new LinearLayout.LayoutParams(-2, -2);
                pasteLp.setMargins(LocalDownloadsUIState.dp(activity, 4), 0, 0, 0);
                pasteBtn.setLayoutParams(pasteLp);
                pasteBtn.setOnClickListener(v ->
                        showManualLyricsPasteDialog(activity, track, renderRef[0], fragment));
                row.addView(pasteBtn);

                row.setOnClickListener(v -> showManualLyricsSearchDialog(activity, track, renderRef[0], fragment));
                rows.addView(row);
            }
            summaryView.setText("Toplam " + sorted.size() + " Parça: Hazır " + readyCount
                    + " (Türkçe Şiir/Söz: " + trNativeCount + ") • Eksik " + missingCount);
        };
        renderRef[0].run();

        filterInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterText[0] = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                renderRef[0].run();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        buttons.setPadding(0, LocalDownloadsUIState.dp(activity, 10), 0, 0);

        TextView purgeBtn = LocalDownloadsUIState.createPillButton(activity, "🧹 Bozuk Sözleri Sil", false);
        purgeBtn.setOnClickListener(v -> {
            int purged = LocalDownloadManager.purgeCorruptedLyrics(musicRoot);
            LocalDownloadsUIState.TRACK_CACHE.clear();
            LocalDownloadsUIState.diskReloadNeeded = true;
            LocalDownloadsUIState.loadTracksIfNeeded(musicRoot);
            renderRef[0].run();
            Utils.showToastShort(purged > 0
                    ? purged + " bozuk/sahte söz dosyası temizlendi!"
                    : "Bozuk söz dosyası bulunmadı ✨");
        });
        buttons.addView(purgeBtn);

        TextView close = LocalDownloadsUIState.createPillButton(activity, "Kapat", false);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-2, -2);
        cLp.setMargins(LocalDownloadsUIState.dp(activity, 6), 0, 0, 0);
        close.setLayoutParams(cLp);
        buttons.addView(close);

        TextView fetch = LocalDownloadsUIState.createPillButton(activity, "⚡ Eksikleri Bul / Şiir Çek", true);
        LinearLayout.LayoutParams fetchLp = new LinearLayout.LayoutParams(-2, -2);
        fetchLp.setMargins(LocalDownloadsUIState.dp(activity, 6), 0, 0, 0);
        fetch.setLayoutParams(fetchLp);
        buttons.addView(fetch);
        panel.addView(buttons);

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(panel).create();
        close.setOnClickListener(v -> dialog.dismiss());
        fetch.setOnClickListener(v -> {
            fetch.setEnabled(false);
            fetch.setText("Başlatılıyor…");
            final LocalDownloadManager.LyricsSource chosenSource = LocalDownloadManager.getSavedLyricsSource();
            final LocalDownloadManager.TranslationEngine chosenEngine = LocalDownloadManager.getSavedTranslationEngine();
            final String deeplKey = LocalDownloadManager.getSavedDeeplApiKey();

            Utils.submitOnBackgroundThread(() -> {
                LocalDownloadManager.purgeCorruptedLyrics(musicRoot);
                SharedPreferences prefs = activity.getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor retryEditor = prefs.edit();
                for (OfflineTrack track : new ArrayList<>(LocalDownloadsUIState.tracks)) {
                    retryEditor.remove("lyrics_lookup_failed_" + track.videoId());
                }
                retryEditor.apply();

                List<OfflineTrack> toProcess = new ArrayList<>(LocalDownloadsUIState.tracks);
                int total = toProcess.size();
                int foundCount = 0;

                for (int i = 0; i < total; i++) {
                    OfflineTrack track = toProcess.get(i);
                    final int currentIdx = i + 1;
                    Utils.runOnMainThread(() -> {
                        if (dialog.isShowing()) {
                            fetch.setText("Taranıyor (" + currentIdx + "/" + total + ")…");
                        }
                    });

                    boolean hasTr = LocalDownloadManager.hasTurkishTranslation(musicRoot, track.videoId());
                    if (hasTr) continue;

                    boolean found = false;
                    try {
                        found = LocalDownloadManager.fetchAndSaveSyncedLyricsCustom(
                                musicRoot, track.videoId(), track.displayTitle(), track.displayArtist(),
                                track.durationSeconds(), chosenSource, chosenEngine, deeplKey, false);
                    } catch (Exception ignored) {}

                    if (found) {
                        foundCount++;
                    } else {
                        prefs.edit().putBoolean("lyrics_lookup_failed_" + track.videoId(), true).apply();
                    }
                }

                LocalDownloadsUIState.TRACK_CACHE.clear();
                LocalDownloadsUIState.diskReloadNeeded = true;
                final int finalFound = foundCount;

                Utils.runOnMainThread(() -> {
                    if (!dialog.isShowing()) return;
                    LocalDownloadsUIState.loadTracksIfNeeded(musicRoot);
                    renderRef[0].run();
                    fetch.setEnabled(true);
                    fetch.setText("⚡ Eksikleri Bul / Şiir Çek");
                    Utils.showToastShort("Tamamlandı! " + finalFound + " yeni söz/şiir eklendi.");
                });
                return null;
            });
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    public static void showManualLyricsPasteDialog(Activity act, OfflineTrack track,
                                                   @Nullable Runnable onComplete,
                                                   LocalDownloadsFragment fragment) {
        if (act == null || track == null || fragment == null) return;
        File musicRoot = fragment.getMusicRoot();

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = LocalDownloadsUIState.dp(act, 20);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(LocalDownloadsUIState.menuBackground(act));

        TextView title = LocalDownloadsUIState.text(act, "📝 Söz / Şiir Yapıştır & Düzenle", 16, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);

        TextView sub = LocalDownloadsUIState.text(act,
                "Şarkının sözünü veya şiirini buraya yapıştırın. Zaman damgası ([mm:ss.00]) eklerseniz senkron çalışır. "
                        + "Mevcut çeviriler korunur.",
                12, LocalDownloadsUIState.secondary());
        sub.setPadding(0, LocalDownloadsUIState.dp(act, 4), 0, LocalDownloadsUIState.dp(act, 10));
        box.addView(sub);

        String existing = "";
        File lrcFile = new File(musicRoot, track.videoId() + ".lrc");
        if (lrcFile.isFile()) {
            try {
                String raw = OfflineStorage.readText(lrcFile);
                StringBuilder sb = new StringBuilder();
                for (String line : raw.split("\\r?\\n")) {
                    if (line.contains("└ 🇹🇷") || line.contains("└ TR:")
                            || line.contains("[TR_NATIVE_POEM_OR_SONG]")) continue;
                    sb.append(line).append("\n");
                }
                existing = sb.toString().trim();
            } catch (Exception ignored) {}
        }

        EditText lyricsInput = new EditText(act);
        lyricsInput.setHint("Sözleri veya şiiri buraya yapıştırın…");
        lyricsInput.setText(existing);
        lyricsInput.setTextColor(LocalDownloadsUIState.primaryTextColor());
        lyricsInput.setHintTextColor(LocalDownloadsUIState.secondary());
        lyricsInput.setTextSize(14);
        lyricsInput.setGravity(Gravity.TOP | Gravity.START);
        lyricsInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        lyricsInput.setMinLines(10);
        lyricsInput.setMaxLines(18);
        lyricsInput.setVerticalScrollBarEnabled(true);
        lyricsInput.setPadding(
                LocalDownloadsUIState.dp(act, 12),
                LocalDownloadsUIState.dp(act, 12),
                LocalDownloadsUIState.dp(act, 12),
                LocalDownloadsUIState.dp(act, 12));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setCornerRadius(LocalDownloadsUIState.dp(act, 10));
        inputBg.setColor(Color.argb(30, 255, 255, 255));
        inputBg.setStroke(LocalDownloadsUIState.dp(act, 1),
                LocalDownloadsUIState.currentTheme.varGlassBorder);
        lyricsInput.setBackground(inputBg);

        ScrollView inputScroll = new ScrollView(act);
        inputScroll.addView(lyricsInput, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, LocalDownloadsUIState.dp(act, 320));
        inputLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(act, 12));
        box.addView(inputScroll, inputLp);

        LinearLayout actions = new LinearLayout(act);
        actions.setGravity(Gravity.END);
        TextView cancelBtn = LocalDownloadsUIState.createPillButton(act, "İptal", false);
        TextView saveBtn = LocalDownloadsUIState.createPillButton(act, "💾 Kaydet", true);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-2, -2);
        sLp.setMargins(LocalDownloadsUIState.dp(act, 8), 0, 0, 0);
        saveBtn.setLayoutParams(sLp);
        actions.addView(cancelBtn);
        actions.addView(saveBtn);
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(act).setView(box).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }

        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        saveBtn.setOnClickListener(v -> {
            String newText = lyricsInput.getText().toString().trim();
            if (newText.isEmpty()) {
                Utils.showToastShort("Lütfen söz veya şiir metni girin");
                return;
            }
            boolean ok = LocalDownloadManager.saveManualLyrics(
                    musicRoot, track.videoId(), track.displayTitle(),
                    track.displayArtist(), newText);
            if (ok) {
                LocalDownloadsUIState.TRACK_CACHE.clear();
                LocalDownloadsUIState.diskReloadNeeded = true;
                Utils.showToastShort("✓ Söz / şiir kaydedildi");
                dialog.dismiss();
                if (onComplete != null) onComplete.run();
            } else {
                Utils.showToastShort("Kaydedilemedi (bozuk/geçersiz metin?)");
            }
        });
        dialog.show();
    }

    public static void showManualLyricsSearchDialog(Activity act, OfflineTrack track,
                                                    @Nullable Runnable onComplete,
                                                    LocalDownloadsFragment fragment) {
        if (act == null || track == null || fragment == null) return;
        File musicRoot = fragment.getMusicRoot();

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = LocalDownloadsUIState.dp(act, 20);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(LocalDownloadsUIState.menuBackground(act));

        TextView title = LocalDownloadsUIState.text(act, "🔍 Manuel Söz veya Türkçe Şiir Ara", 16, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);

        TextView sub = LocalDownloadsUIState.text(
                act,
                "Şiirler için '📖 Şiir Siteleri (Antoloji / Arşiv)' kaynağını seçip şiir ve şair adını yazabilirsin. "
                        + "Hiçbir kaynakta bulunamadıysa '📝 Yapıştır' ile sözü elle ekleyebilirsin.",
                12, LocalDownloadsUIState.secondary());
        sub.setPadding(0, LocalDownloadsUIState.dp(act, 4), 0, LocalDownloadsUIState.dp(act, 10));
        box.addView(sub);

        EditText titleInput = new EditText(act);
        titleInput.setHint("Şarkı veya Şiir Adı (Örn: Dalga - Orhan Veli)");
        titleInput.setText(track.displayTitle());
        titleInput.setTextColor(LocalDownloadsUIState.primaryTextColor());
        titleInput.setHintTextColor(LocalDownloadsUIState.secondary());
        titleInput.setSingleLine(true);
        box.addView(titleInput, new LinearLayout.LayoutParams(-1, -2));

        EditText artistInput = new EditText(act);
        artistInput.setHint("Sanatçı veya Şair Adı (İsteğe bağlı)");
        artistInput.setText(track.displayArtist());
        artistInput.setTextColor(LocalDownloadsUIState.primaryTextColor());
        artistInput.setHintTextColor(LocalDownloadsUIState.secondary());
        artistInput.setSingleLine(true);
        LinearLayout.LayoutParams arLp = new LinearLayout.LayoutParams(-1, -2);
        arLp.setMargins(0, LocalDownloadsUIState.dp(act, 6), 0, LocalDownloadsUIState.dp(act, 10));
        box.addView(artistInput, arLp);

        final LocalDownloadManager.LyricsSource[] selectedSource = {LocalDownloadManager.getSavedLyricsSource()};
        final LocalDownloadManager.TranslationEngine[] selectedEngine = {LocalDownloadManager.getSavedTranslationEngine()};

        TextView sourcePickBtn = LocalDownloadsUIState.createPillButton(
                act, "🌐 Kaynak: " + selectedSource[0].label, false);
        sourcePickBtn.setOnClickListener(v -> {
            LocalDownloadManager.LyricsSource[] all = LocalDownloadManager.LyricsSource.values();
            String[] names = new String[all.length];
            for (int i = 0; i < all.length; i++) names[i] = all[i].label;
            LocalDownloadsUIState.showMenu(act, sourcePickBtn, names, selectedSource[0].ordinal(), pos -> {
                selectedSource[0] = all[pos];
                sourcePickBtn.setText("🌐 Kaynak: " + all[pos].label);
            });
        });
        box.addView(sourcePickBtn, new LinearLayout.LayoutParams(-1, -2));

        TextView enginePickBtn = LocalDownloadsUIState.createPillButton(
                act, "🇹🇷 Çeviri (Yabancıysa): " + selectedEngine[0].label, false);
        LinearLayout.LayoutParams epLp = new LinearLayout.LayoutParams(-1, -2);
        epLp.setMargins(0, LocalDownloadsUIState.dp(act, 6), 0, LocalDownloadsUIState.dp(act, 14));
        enginePickBtn.setLayoutParams(epLp);
        enginePickBtn.setOnClickListener(v -> {
            LocalDownloadManager.TranslationEngine[] all = LocalDownloadManager.TranslationEngine.values();
            String[] names = new String[all.length];
            for (int i = 0; i < all.length; i++) names[i] = all[i].label;
            LocalDownloadsUIState.showMenu(act, enginePickBtn, names, selectedEngine[0].ordinal(), pos -> {
                selectedEngine[0] = all[pos];
                enginePickBtn.setText("🇹🇷 Çeviri (Yabancıysa): " + all[pos].label);
            });
        });
        box.addView(enginePickBtn);

        AlertDialog dialog = new AlertDialog.Builder(act).setView(box).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout actions = new LinearLayout(act);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView pasteBtn = LocalDownloadsUIState.createPillButton(act, "📝 Yapıştır", false);
        pasteBtn.setOnClickListener(v -> {
            dialog.dismiss();
            showManualLyricsPasteDialog(act, track, onComplete, fragment);
        });
        actions.addView(pasteBtn);
        actions.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView cancelBtn = LocalDownloadsUIState.createPillButton(act, "İptal", false);
        TextView searchBtn = LocalDownloadsUIState.createPillButton(act, "🔍 Ara ve Kaydet", true);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-2, -2);
        sLp.setMargins(LocalDownloadsUIState.dp(act, 8), 0, 0, 0);
        searchBtn.setLayoutParams(sLp);
        actions.addView(cancelBtn);
        actions.addView(searchBtn);
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        searchBtn.setOnClickListener(v -> {
            String qTitle = titleInput.getText().toString().trim();
            String qArtist = artistInput.getText().toString().trim();
            if (qTitle.isEmpty()) {
                Utils.showToastShort("Lütfen şarkı veya şiir adını yazın");
                return;
            }
            searchBtn.setEnabled(false);
            searchBtn.setText("Aranıyor…");

            Utils.submitOnBackgroundThread(() -> {
                boolean ok = LocalDownloadManager.fetchAndSaveSyncedLyricsCustom(
                        musicRoot, track.videoId(), qTitle, qArtist, track.durationSeconds(),
                        selectedSource[0], selectedEngine[0],
                        LocalDownloadManager.getSavedDeeplApiKey(), true);
                LocalDownloadsUIState.TRACK_CACHE.clear();
                LocalDownloadsUIState.diskReloadNeeded = true;
                Utils.runOnMainThread(() -> {
                    dialog.dismiss();
                    if (ok) {
                        act.getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE)
                                .edit().remove("lyrics_lookup_failed_" + track.videoId()).apply();
                        Utils.showToastShort("✓ Söz / Şiir başarıyla kaydedildi!");
                    } else {
                        Utils.showToastShort("Tam eşleşen söz/şiir bulunamadı. '📝 Yapıştır' ile elle ekleyebilirsin.");
                    }
                    if (onComplete != null) onComplete.run();
                });
                return null;
            });
        });
        dialog.show();
    }

    private static void showDeeplApiKeyDialog(Activity act) {
        if (act == null) return;

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = LocalDownloadsUIState.dp(act, 20);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(LocalDownloadsUIState.menuBackground(act));

        TextView title = LocalDownloadsUIState.text(act, "🔑 DeepL API Anahtarı (İsteğe Bağlı)", 16, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);

        TextView info = LocalDownloadsUIState.text(
                act,
                "Boş bırakırsan ücretsiz DeepL Web RPC kullanılır. Kendi DeepL Free (:fx) veya Pro anahtarın varsa buraya girebilirsin.",
                12, LocalDownloadsUIState.secondary());
        info.setPadding(0, LocalDownloadsUIState.dp(act, 4), 0, LocalDownloadsUIState.dp(act, 10));
        box.addView(info);

        EditText keyInput = new EditText(act);
        keyInput.setHint("Örn: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx:fx");
        keyInput.setText(LocalDownloadManager.getSavedDeeplApiKey());
        keyInput.setTextColor(LocalDownloadsUIState.primaryTextColor());
        keyInput.setHintTextColor(LocalDownloadsUIState.secondary());
        keyInput.setSingleLine(true);
        box.addView(keyInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.END);
        row.setPadding(0, LocalDownloadsUIState.dp(act, 12), 0, 0);
        TextView saveBtn = LocalDownloadsUIState.createPillButton(act, "Kaydet", true);
        row.addView(saveBtn);
        box.addView(row);

        AlertDialog dialog = new AlertDialog.Builder(act).setView(box).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        saveBtn.setOnClickListener(v -> {
            LocalDownloadManager.setSavedDeeplApiKey(keyInput.getText().toString());
            dialog.dismiss();
            Utils.showToastShort("DeepL çeviri ayarları kaydedildi");
        });
        dialog.show();
    }

    // GÖREV 5: Şarkı Meta Verisi ve Kapak Düzenleyici Diyalog
    public static void showEditTrackMetadataDialog(Activity activity, OfflineTrack track, LocalDownloadsFragment fragment) {
        if (activity == null || track == null || fragment == null) return;
        File musicRoot = fragment.getMusicRoot();

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = LocalDownloadsUIState.dp(activity, 20);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(LocalDownloadsUIState.menuBackground(activity));

        TextView title = LocalDownloadsUIState.text(activity, "✏️ Şarkı Bilgilerini Düzenle", 17, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, LocalDownloadsUIState.dp(activity, 12));
        box.addView(title);

        // Kapak Değiştirme Kısmı
        LinearLayout coverRow = new LinearLayout(activity);
        coverRow.setGravity(Gravity.CENTER_VERTICAL);
        coverRow.setPadding(0, 0, 0, LocalDownloadsUIState.dp(activity, 16));

        ImageView coverView = LocalDownloadsUIState.artworkView(activity, null, 8);
        LocalDownloadsUIState.loadArtworkAsync(track, 256, coverView);
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(activity, 80), LocalDownloadsUIState.dp(activity, 80));
        coverRow.addView(coverView, imgLp);

        TextView changeCoverBtn = LocalDownloadsUIState.createPillButton(activity, "🖼️ Kapağı Değiştir", false);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-2, -2);
        btnLp.setMargins(LocalDownloadsUIState.dp(activity, 16), 0, 0, 0);
        changeCoverBtn.setLayoutParams(btnLp);
        changeCoverBtn.setOnClickListener(v -> {
            LocalDownloadsUIState.pendingCoverTrackId = track.videoId();
            fragment.pickImageFromGallery(LocalDownloadsUIState.REQ_PICK_TRACK_COVER);
            // Dialog açık kalabilir, kullanıcı resmi seçince arka planda güncellenir
        });
        coverRow.addView(changeCoverBtn);
        box.addView(coverRow);

        // Metin Alanları
        EditText titleInput = new EditText(activity);
        titleInput.setHint("Şarkı Adı");
        titleInput.setText(track.displayTitle());
        titleInput.setTextColor(LocalDownloadsUIState.primaryTextColor());
        titleInput.setHintTextColor(LocalDownloadsUIState.secondary());
        titleInput.setSingleLine(true);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, -2);
        inputLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(activity, 8));
        box.addView(titleInput, inputLp);

        EditText artistInput = new EditText(activity);
        artistInput.setHint("Sanatçı");
        artistInput.setText(track.displayArtist());
        artistInput.setTextColor(LocalDownloadsUIState.primaryTextColor());
        artistInput.setHintTextColor(LocalDownloadsUIState.secondary());
        artistInput.setSingleLine(true);
        box.addView(artistInput, inputLp);

        EditText albumInput = new EditText(activity);
        albumInput.setHint("Albüm");
        albumInput.setText(track.displayAlbum());
        albumInput.setTextColor(LocalDownloadsUIState.primaryTextColor());
        albumInput.setHintTextColor(LocalDownloadsUIState.secondary());
        albumInput.setSingleLine(true);
        box.addView(albumInput, inputLp);

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, LocalDownloadsUIState.dp(activity, 16), 0, 0);
        
        TextView cancelBtn = LocalDownloadsUIState.createPillButton(activity, "İptal", false);
        TextView saveBtn = LocalDownloadsUIState.createPillButton(activity, "💾 Kaydet", true);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-2, -2);
        sLp.setMargins(LocalDownloadsUIState.dp(activity, 8), 0, 0, 0);
        saveBtn.setLayoutParams(sLp);
        actions.addView(cancelBtn);
        actions.addView(saveBtn);
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(box).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        saveBtn.setOnClickListener(v -> {
            String newTitle = titleInput.getText().toString().trim();
            String newArtist = artistInput.getText().toString().trim();
            String newAlbum = albumInput.getText().toString().trim();
            
            if (newTitle.isEmpty()) {
                Utils.showToastShort("Şarkı adı boş olamaz");
                return;
            }

            // OfflineTrack içindeki metadata güncelleme metodunu çağır
            // (Bu metodun OfflineTrack.java içinde olduğu varsayılmıştır)
            boolean success = track.updateMetadata(musicRoot, newTitle, newArtist, newAlbum);
            
            if (success) {
                LocalDownloadsUIState.TRACK_CACHE.clear();
                LocalDownloadsUIState.diskReloadNeeded = true;
                Utils.showToastShort("✓ Şarkı bilgileri güncellendi");
                dialog.dismiss();
                fragment.showTracks();
                
                // Eğer çalan şarkıysa oynatıcıyı da güncelle
                if (LocalDownloadsUIState.playingPath.equals(track.audioFile().getAbsolutePath())) {
                    LocalDownloadsPlayer.applyTrackVisuals(activity, track);
                }
            } else {
                Utils.showToastShort("Güncelleme başarısız oldu");
            }
        });
        dialog.show();
    }

    public static void showTrackBottomSheet(Activity activity, OfflineTrack track, LocalDownloadsFragment fragment) {
        if (activity == null || track == null || fragment == null) return;
        File musicRoot = fragment.getMusicRoot();

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout overlay = new FrameLayout(activity);
        overlay.setBackgroundColor(Color.argb(155, 0, 0, 0));

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(
                LocalDownloadsUIState.dp(activity, 18),
                LocalDownloadsUIState.dp(activity, 14),
                LocalDownloadsUIState.dp(activity, 18),
                LocalDownloadsUIState.dp(activity, 24)
        );
        sheet.setBackground(LocalDownloadsUIState.bottomSheetSurfaceDrawable(activity));
        sheet.setClickable(true);

        int screenH = activity.getResources().getDisplayMetrics().heightPixels;

        View handle = new View(activity);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setCornerRadius(LocalDownloadsUIState.dp(activity, 3));
        handleBg.setColor(Color.argb(85, 255, 255, 255));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(activity, 40), LocalDownloadsUIState.dp(activity, 4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(activity, 12));
        sheet.addView(handle, handleLp);

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(
                LocalDownloadsUIState.dp(activity, 2),
                0,
                LocalDownloadsUIState.dp(activity, 2),
                LocalDownloadsUIState.dp(activity, 12)
        );

        ImageView thumb = LocalDownloadsUIState.artworkView(activity, null, 8);
        LocalDownloadsUIState.loadArtworkAsync(track, LocalDownloadsUIState.ROW_ARTWORK_PIXELS, thumb);
        headerRow.addView(thumb, new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(activity, 48), LocalDownloadsUIState.dp(activity, 48)));

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(LocalDownloadsUIState.dp(activity, 12), 0, LocalDownloadsUIState.dp(activity, 8), 0);

        TextView titleTv = LocalDownloadsUIState.text(activity, track.displayTitle(), 16, LocalDownloadsUIState.primaryTextColor());
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);

        TextView subTv = LocalDownloadsUIState.text(
                activity,
                track.displayArtist() + " • " + LocalDownloadsUIState.formatDuration(track.durationSeconds()),
                13, LocalDownloadsUIState.secondary());
        subTv.setSingleLine(true);
        subTv.setEllipsize(TextUtils.TruncateAt.END);

        titleCol.addView(titleTv);
        titleCol.addView(subTv);
        headerRow.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView likeBtn = LocalDownloadsUIState.text(activity, "👍", 18, LocalDownloadsUIState.primaryTextColor());
        likeBtn.setGravity(Gravity.CENTER);
        likeBtn.setBackground(LocalDownloadsUIState.createCircleRipple());
        likeBtn.setOnClickListener(v -> {
            OfflineStorage.createPlaylist(musicRoot, OfflineStorage.SPECIAL_LIKED_SONGS, "LM");
            OfflineStorage.addTrackToPlaylist(musicRoot, OfflineStorage.SPECIAL_LIKED_SONGS, track.videoId());
            likeBtn.setText("❤️");
            LocalDownloadsUIState.diskReloadNeeded = true;
            Utils.showToastShort("Beğenilenler listesine eklendi");
        });
        headerRow.addView(likeBtn, new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(activity, 40), LocalDownloadsUIState.dp(activity, 40)));

        ImageButton closeBtn = LocalDownloadsUIState.icon(
                activity,
                LocalDownloadsUIState.createVectorIconDrawable(activity, LocalDownloadsUIState.ICON_CLOSE, LocalDownloadsUIState.primaryTextColor(), 0),
                "Kapat"
        );
        closeBtn.setOnClickListener(v ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, null));
        headerRow.addView(closeBtn, new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(activity, 40), LocalDownloadsUIState.dp(activity, 40)));

        sheet.addView(headerRow, new LinearLayout.LayoutParams(-1, -2));

        View divider = new View(activity);
        divider.setBackgroundColor(Color.argb(35, 255, 255, 255));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, LocalDownloadsUIState.dp(activity, 1));
        divLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(activity, 14));
        sheet.addView(divider, divLp);

        LinearLayout topCardsRow = new LinearLayout(activity);
        topCardsRow.setOrientation(LinearLayout.HORIZONTAL);
        topCardsRow.setPadding(0, 0, 0, LocalDownloadsUIState.dp(activity, 14));

        View cardPlayNext = createBottomSheetTopCard(activity, "⏭️", "Bundan sonra\noynat", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> LocalDownloadsPlayer.insertTrackIntoLiveQueue(activity, track, true)));

        View cardSavePlaylist = createBottomSheetTopCard(activity, "➕", "Oynatma listesine\nkaydet", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> showAddToPlaylistDialog(activity, track, fragment)));

        View cardShare = createBottomSheetTopCard(activity, "📤", "Paylaş", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> fragment.shareTrackLink(track)));

        LinearLayout.LayoutParams cLp1 = new LinearLayout.LayoutParams(0, -2, 1f);
        cLp1.setMargins(0, 0, LocalDownloadsUIState.dp(activity, 8), 0);
        LinearLayout.LayoutParams cLp2 = new LinearLayout.LayoutParams(0, -2, 1f);
        cLp2.setMargins(0, 0, LocalDownloadsUIState.dp(activity, 8), 0);
        LinearLayout.LayoutParams cLp3 = new LinearLayout.LayoutParams(0, -2, 1f);

        topCardsRow.addView(cardPlayNext, cLp1);
        topCardsRow.addView(cardSavePlaylist, cLp2);
        topCardsRow.addView(cardShare, cLp3);
        sheet.addView(topCardsRow, new LinearLayout.LayoutParams(-1, -2));

        addBottomSheetListRow(activity, sheet, "▶", "Şimdi oynat (Tam Ekran)", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, () -> {
                    LocalDownloadsPlayer.play(activity, track);
                    fragment.showFullScreenPlayer();
                }));

        addBottomSheetListRow(activity, sheet, "🎶", "Sıraya ekle", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> LocalDownloadsPlayer.insertTrackIntoLiveQueue(activity, track, false)));

        addBottomSheetListRow(activity, sheet, "🎤", "Şarkı sözlerini / Şiiri göster (LRC)", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, () -> {
                    if (!LocalDownloadsUIState.playingPath.equals(track.audioFile().getAbsolutePath())) {
                        LocalDownloadsPlayer.play(activity, track);
                    }
                    LocalDownloadsPlayer.showSyncedLyricsDialog(activity, fragment);
                }));
                
        // GÖREV 4: Sözleri Dışa Aktar
        addBottomSheetListRow(activity, sheet, "💾", "Sözleri Dışa Aktar (.lrc)", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, () -> {
                    File lrcFile = new File(musicRoot, track.videoId() + ".lrc");
                    if (!lrcFile.exists()) {
                        Utils.showToastShort("Dışa aktarılacak söz dosyası bulunamadı");
                        return;
                    }
                    boolean ok = LocalDownloadManager.exportLyricsToDownloads(activity, lrcFile, track.displayTitle());
                    if (ok) {
                        Utils.showToastShort("✓ Sözler 'İndirilenler/MorpheLyrics' klasörüne aktarıldı");
                    } else {
                        Utils.showToastShort("Sözler dışa aktarılamadı");
                    }
                }));
                
        // GÖREV 5: Kapak ve Meta Veri Düzenle
        addBottomSheetListRow(activity, sheet, "✏️", "Şarkı Bilgilerini / Kapağı Düzenle", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> showEditTrackMetadataDialog(activity, track, fragment)));

        addBottomSheetListRow(activity, sheet, "🔍", "Söz / Türkçe Şiir Elle Ara (Antoloji / DeepL)", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> showManualLyricsSearchDialog(activity, track, null, fragment)));

        addBottomSheetListRow(activity, sheet, "📝", "Söz / Şiir Yapıştır / Düzenle", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> showManualLyricsPasteDialog(activity, track, null, fragment)));

        if (LocalDownloadsUIState.selectedPlaylist != null
                && !OfflineStorage.SPECIAL_ALL_SONGS.equalsIgnoreCase(LocalDownloadsUIState.selectedPlaylist)) {
            addBottomSheetListRow(activity, sheet, "➖", "Oynatma listesinden kaldır", () ->
                    LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, () -> {
                        OfflineStorage.removeTrackFromPlaylist(musicRoot, LocalDownloadsUIState.selectedPlaylist, track.videoId());
                        LocalDownloadsUIState.diskReloadNeeded = true;
                        fragment.showTracks();
                        Utils.showToastShort("Listeden kaldırıldı");
                    }));
        }

        addBottomSheetListRow(activity, sheet, "☑️", "Toplu seçimde işaretle", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, () -> {
                    LocalDownloadsUIState.selectionMode = true;
                    LocalDownloadsUIState.selectedVideoIds.add(track.videoId());
                    fragment.showTracks();
                }));

        addBottomSheetListRow(activity, sheet, "ℹ️", "Şarkının jeneriğini ve bilgisini göster", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> showTrackInfoDialog(activity, track)));

        addBottomSheetListRow(activity, sheet, "🗑️", "Cihazdan kaldır (Sil)", () ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                        () -> fragment.confirmDelete(track)));

        overlay.addView(sheet, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        overlay.setOnClickListener(v ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, null));

        dialog.setContentView(overlay);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            OfflineHeaderBlocker.applyImmersiveFullscreen(dialog.getWindow(), true);
        }
        dialog.show();
        LocalDownloadsAnimator.animateBottomSheetShow(sheet, overlay, screenH);
    }

    private static View createBottomSheetTopCard(Context ctx, String emojiIcon, String label, Runnable onClick) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(
                LocalDownloadsUIState.dp(ctx, 8),
                LocalDownloadsUIState.dp(ctx, 12),
                LocalDownloadsUIState.dp(ctx, 8),
                LocalDownloadsUIState.dp(ctx, 12)
        );
        box.setBackground(LocalDownloadsUIState.glassCardDrawable(ctx, LocalDownloadsUIState.dp(ctx, 12), false));

        TextView ic = LocalDownloadsUIState.text(ctx, emojiIcon, 20, LocalDownloadsUIState.primaryTextColor());
        ic.setGravity(Gravity.CENTER);
        box.addView(ic);

        TextView lb = LocalDownloadsUIState.text(ctx, label, 11, LocalDownloadsUIState.primaryTextColor());
        lb.setGravity(Gravity.CENTER);
        lb.setPadding(0, LocalDownloadsUIState.dp(ctx, 6), 0, 0);
        lb.setLines(2);
        box.addView(lb);

        box.setOnClickListener(v -> onClick.run());
        return box;
    }

    private static void addBottomSheetListRow(Context ctx, LinearLayout parent, String iconText, String titleText, Runnable onClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                LocalDownloadsUIState.dp(ctx, 10),
                LocalDownloadsUIState.dp(ctx, 11),
                LocalDownloadsUIState.dp(ctx, 10),
                LocalDownloadsUIState.dp(ctx, 11)
        );
        row.setBackground(LocalDownloadsUIState.rowBackground(ctx, false));

        TextView ic = LocalDownloadsUIState.text(ctx, iconText, 17, LocalDownloadsUIState.primaryTextColor());
        ic.setGravity(Gravity.CENTER);
        row.addView(ic, new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(ctx, 34), LocalDownloadsUIState.dp(ctx, 28)));

        TextView tv = LocalDownloadsUIState.text(ctx, titleText, 15, LocalDownloadsUIState.primaryTextColor());
        tv.setPadding(LocalDownloadsUIState.dp(ctx, 12), 0, 0, 0);
        row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));

        row.setOnClickListener(v -> onClick.run());
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }


    public static void showAddToPlaylistDialog(Activity act, OfflineTrack track, LocalDownloadsFragment fragment) {
        if (act == null || track == null || fragment == null) return;
        File musicRoot = fragment.getMusicRoot();

        LinearLayout sheet = new LinearLayout(act);
        sheet.setOrientation(LinearLayout.VERTICAL);
        int pad = LocalDownloadsUIState.dp(act, 18);
        sheet.setPadding(pad, pad, pad, pad);
        sheet.setBackground(LocalDownloadsUIState.bottomSheetSurfaceDrawable(act));

        TextView title = LocalDownloadsUIState.text(act, "➕ Oynatma Listesine Kaydet", 17, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, LocalDownloadsUIState.dp(act, 12));
        sheet.addView(title);

        ScrollView scroll = new ScrollView(act);
        LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, LocalDownloadsUIState.dp(act, 300)));

        AlertDialog dialog = new AlertDialog.Builder(act).setView(sheet).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        String[] items = new String[LocalDownloadsUIState.playlists.size() + 2];
        items[0] = "✨ + Yeni Oynatma Listesi Oluştur";
        items[1] = "❤️ " + OfflineStorage.SPECIAL_LIKED_SONGS;
        for (int i = 0; i < LocalDownloadsUIState.playlists.size(); i++) {
            items[i + 2] = "📁 " + LocalDownloadsUIState.playlists.get(i).name();
        }

        for (int i = 0; i < items.length; i++) {
            final int which = i;
            TextView row = LocalDownloadsUIState.text(act, items[i], 14, LocalDownloadsUIState.primaryTextColor());
            row.setTypeface(i == 0 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            row.setPadding(
                    LocalDownloadsUIState.dp(act, 14),
                    LocalDownloadsUIState.dp(act, 12),
                    LocalDownloadsUIState.dp(act, 14),
                    LocalDownloadsUIState.dp(act, 12)
            );
            row.setBackground(LocalDownloadsUIState.glassCardDrawable(act, LocalDownloadsUIState.dp(act, 10), i == 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(act, 6));
            row.setLayoutParams(lp);

            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (which == 0) {
                    showCreateOrAutoDownloadPlaylistDialog(act, musicRoot, track, null, fragment);
                } else if (which == 1) {
                    OfflineStorage.createPlaylist(musicRoot, OfflineStorage.SPECIAL_LIKED_SONGS, "LM");
                    OfflineStorage.addTrackToPlaylist(musicRoot, OfflineStorage.SPECIAL_LIKED_SONGS, track.videoId());
                    LocalDownloadsUIState.diskReloadNeeded = true;
                    fragment.showTracks();
                    Utils.showToastShort("Beğenilenler listesine eklendi");
                } else {
                    String target = LocalDownloadsUIState.playlists.get(which - 2).name();
                    if (OfflineStorage.addTrackToPlaylist(musicRoot, target, track.videoId())) {
                        LocalDownloadsUIState.diskReloadNeeded = true;
                        fragment.showTracks();
                        Utils.showToastShort(target + " listesine eklendi");
                    } else {
                        Utils.showToastShort("Şarkı zaten bu listede var");
                    }
                }
            });
            list.addView(row);
        }

        dialog.show();
    }

    public static void showCreateOrAutoDownloadPlaylistDialog(Context ctx, File musicRoot,
                                                              @Nullable OfflineTrack trackToAdd,
                                                              @Nullable OfflineStorage.Playlist existingPlaylist,
                                                              LocalDownloadsFragment fragment) {
        if (ctx == null || fragment == null) return;

        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(
                LocalDownloadsUIState.dp(ctx, 20),
                LocalDownloadsUIState.dp(ctx, 18),
                LocalDownloadsUIState.dp(ctx, 20),
                LocalDownloadsUIState.dp(ctx, 14)
        );
        container.setBackground(LocalDownloadsUIState.menuBackground(ctx));

        TextView titleView = LocalDownloadsUIState.text(
                ctx,
                existingPlaylist != null
                        ? "\"" + existingPlaylist.name() + "\" Aralıklı İndir"
                        : "Playlist & Beğenilenler (LM) İndir",
                17, LocalDownloadsUIState.primaryTextColor());
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        container.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        EditText nameInput = new EditText(ctx);
        nameInput.setHint("Liste adı (Örn: Favorilerim)");
        nameInput.setTextColor(LocalDownloadsUIState.primaryTextColor());
        nameInput.setHintTextColor(LocalDownloadsUIState.secondary());
        nameInput.setSingleLine(true);
        if (existingPlaylist != null) {
            nameInput.setText(existingPlaylist.name());
            nameInput.setEnabled(false);
        }
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
        nameParams.setMargins(0, LocalDownloadsUIState.dp(ctx, 10), 0, LocalDownloadsUIState.dp(ctx, 6));
        container.addView(nameInput, nameParams);

        EditText urlInput = new EditText(ctx);
        urlInput.setHint("Playlist Linki, ID veya Beğenilenler için 'LM'");
        urlInput.setTextColor(LocalDownloadsUIState.primaryTextColor());
        urlInput.setHintTextColor(LocalDownloadsUIState.secondary());
        urlInput.setTextSize(13);
        if (existingPlaylist != null && !existingPlaylist.remoteId().isEmpty()) {
            urlInput.setText(existingPlaylist.remoteId());
        }
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(-1, -2);
        urlParams.setMargins(0, 0, 0, LocalDownloadsUIState.dp(ctx, 8));
        container.addView(urlInput, urlParams);

        TextView quickLikedBtn = LocalDownloadsUIState.createPillButton(ctx, "❤️ Beğenilenler (LM - En Sondan Başlar)", false);
        quickLikedBtn.setOnClickListener(v -> {
            if (nameInput.getText().toString().trim().isEmpty()) {
                nameInput.setText(OfflineStorage.SPECIAL_LIKED_SONGS);
            }
            urlInput.setText("LM");
        });
        LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(-1, -2);
        quickParams.setMargins(0, 0, 0, LocalDownloadsUIState.dp(ctx, 10));
        container.addView(quickLikedBtn, quickParams);

        TextView rangeLabel = LocalDownloadsUIState.text(ctx, "İndirme Aralığı (1 = En son eklenen şarkı):", 12, LocalDownloadsUIState.secondary());
        container.addView(rangeLabel, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout rangeRow = new LinearLayout(ctx);
        rangeRow.setOrientation(LinearLayout.HORIZONTAL);
        rangeRow.setGravity(Gravity.CENTER_VERTICAL);
        rangeRow.setPadding(0, LocalDownloadsUIState.dp(ctx, 4), 0, LocalDownloadsUIState.dp(ctx, 8));

        EditText startInput = new EditText(ctx);
        startInput.setHint("Başlangıç (1)");
        startInput.setText("1");
        startInput.setTextColor(LocalDownloadsUIState.primaryTextColor());
        startInput.setHintTextColor(LocalDownloadsUIState.secondary());
        startInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        startInput.setSingleLine(true);

        TextView dash = LocalDownloadsUIState.text(ctx, "  —  ", 15, LocalDownloadsUIState.primaryTextColor());

        EditText endInput = new EditText(ctx);
        endInput.setHint("Bitiş (0 = Tümü)");
        endInput.setText("50");
        endInput.setTextColor(LocalDownloadsUIState.primaryTextColor());
        endInput.setHintTextColor(LocalDownloadsUIState.secondary());
        endInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        endInput.setSingleLine(true);

        rangeRow.addView(startInput, new LinearLayout.LayoutParams(0, -2, 1f));
        rangeRow.addView(dash);
        rangeRow.addView(endInput, new LinearLayout.LayoutParams(0, -2, 1f));
        container.addView(rangeRow, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView presetScroll = new HorizontalScrollView(ctx);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetRow = new LinearLayout(ctx);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        int[][] presets = {{1, 25}, {1, 50}, {1, 100}, {51, 100}, {1, 0}};
        String[] presetLabels = {"Son 25", "Son 50", "Son 100", "51-100 Arası", "Tümü"};
        for (int i = 0; i < presets.length; i++) {
            final int s = presets[i][0], e = presets[i][1];
            TextView pBtn = LocalDownloadsUIState.createPillButton(ctx, presetLabels[i], false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, 0, LocalDownloadsUIState.dp(ctx, 6), LocalDownloadsUIState.dp(ctx, 12));
            pBtn.setLayoutParams(lp);
            pBtn.setOnClickListener(v -> {
                startInput.setText(String.valueOf(s));
                endInput.setText(e == 0 ? "" : String.valueOf(e));
            });
            presetRow.addView(pBtn);
        }
        presetScroll.addView(presetRow);
        container.addView(presetScroll, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(ctx);
        actions.setGravity(Gravity.END);
        TextView cancelBtn = LocalDownloadsUIState.createPillButton(ctx, "İptal", false);
        TextView createBtn = LocalDownloadsUIState.createPillButton(ctx, "Başlat & İndir", true);
        actions.addView(cancelBtn);
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(-2, -2);
        createParams.setMargins(LocalDownloadsUIState.dp(ctx, 10), 0, 0, 0);
        actions.addView(createBtn, createParams);
        container.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(ctx).setView(container).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        createBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String remoteInput = urlInput.getText().toString().trim();
            if (name.isEmpty()) {
                Utils.showToastShort("Lütfen bir liste adı girin");
                return;
            }

            int startIdx = parsePositiveInt(startInput.getText().toString(), 1);
            int endIdx = parsePositiveInt(endInput.getText().toString(), 0);

            OfflineStorage.createPlaylist(musicRoot, name, remoteInput);
            if (trackToAdd != null) OfflineStorage.addTrackToPlaylist(musicRoot, name, trackToAdd.videoId());
            LocalDownloadsUIState.selectedPlaylist = name;
            LocalDownloadsUIState.showingArtists = false;
            LocalDownloadsUIState.showingPlaylistsOverview = false;
            LocalDownloadsUIState.diskReloadNeeded = true;

            if (!remoteInput.isEmpty()) {
                LocalDownloadManager.fetchAndDownloadPlaylist(musicRoot, name, remoteInput, startIdx, endIdx);
            } else if (existingPlaylist != null) {
                LocalDownloadManager.syncPlaylists(musicRoot, name, startIdx, endIdx, true);
            } else {
                Utils.showToastShort(name + " listesi oluşturuldu");
            }
            dialog.dismiss();
            fragment.showTracks();
            fragment.refreshDownloadStatusUI();
        });
        dialog.show();
    }

    public static void showChangePlaylistCoverDialog(Activity act, View anchor, OfflineStorage.Playlist pl,
                                                     List<OfflineTrack> plTracks, LocalDownloadsFragment fragment) {
        if (act == null || fragment == null) return;
        File musicRoot = fragment.getMusicRoot();
        String[] options = {
                "🖼️ Galeriden Kapak Fotoğrafı Seç",
                "🎵 Listedeki Bir Şarkının Kapağını Seç",
                "🔄 Varsayılan 4'lü Kolaj Kapağa Dön"
        };
        LocalDownloadsUIState.showMenu(act, anchor, options, -1, pos -> {
            if (pos == 0) {
                LocalDownloadsUIState.pendingCoverPlaylistName = pl.name();
                fragment.pickImageFromGallery(LocalDownloadsUIState.REQ_PICK_PLAYLIST_COVER);
            } else if (pos == 1) {
                if (plTracks.isEmpty()) {
                    Utils.showToastShort("Listede henüz indirilmiş şarkı yok");
                    return;
                }
                String[] trackNames = new String[Math.min(plTracks.size(), 30)];
                for (int i = 0; i < trackNames.length; i++) {
                    trackNames[i] = plTracks.get(i).displayTitle();
                }
                LocalDownloadsUIState.showMenu(act, anchor, trackNames, -1, tIdx -> {
                    OfflineTrack chosen = plTracks.get(tIdx);
                    OfflineStorage.setPlaylistCoverPath(musicRoot, pl.name(), chosen.artworkFile().getAbsolutePath());
                    LocalDownloadsUIState.ARTWORK_CACHE.evictAll();
                    LocalDownloadsUIState.diskReloadNeeded = true;
                    fragment.showTracks();
                    Utils.showToastShort("Kapak fotoğrafı güncellendi");
                });
            } else if (pos == 2) {
                OfflineStorage.setPlaylistCoverPath(musicRoot, pl.name(), "");
                LocalDownloadsUIState.ARTWORK_CACHE.evictAll();
                LocalDownloadsUIState.diskReloadNeeded = true;
                fragment.showTracks();
                Utils.showToastShort("Varsayılan kolaj kapağına dönüldü");
            }
        });
    }

    public static void showPlaylistActionsMenu(Activity act, View anchor, OfflineStorage.Playlist pl,
                                               LocalDownloadsFragment fragment) {
        if (act == null || fragment == null) return;
        File musicRoot = fragment.getMusicRoot();
        String[] items = {
                "▶ Listeyi Oynat",
                "🖼️ Kapak Fotoğrafı Değiştir",
                "⬇️ Aralık Seçerek İndir (Başlangıç-Bitiş)",
                "⬇️ Eksik Tüm Şarkıları İndir",
                "🛑 Bu Listenin Aktif İndirmelerini Durdur",
                "🗑️ Oynatma Listesini Sil"
        };
        LocalDownloadsUIState.showMenu(act, anchor, items, -1, pos -> {
            switch (pos) {
                case 0 -> {
                    LocalDownloadsUIState.selectedPlaylist = pl.name();
                    LocalDownloadsUIState.showingArtists = false;
                    LocalDownloadsUIState.showingPlaylistsOverview = false;
                    fragment.showTracks();
                    if (!LocalDownloadsUIState.queuePaths.isEmpty()) {
                        OfflineTrack first = LocalDownloadsUIState.findByPath(LocalDownloadsUIState.queuePaths.get(0));
                        if (first != null) LocalDownloadsPlayer.play(act, first);
                    }
                }
                case 1 -> {
                    LocalDownloadsUIState.pendingCoverPlaylistName = pl.name();
                    fragment.pickImageFromGallery(LocalDownloadsUIState.REQ_PICK_PLAYLIST_COVER);
                }
                case 2 -> showCreateOrAutoDownloadPlaylistDialog(act, musicRoot, null, pl, fragment);
                case 3 -> LocalDownloadManager.syncPlaylists(musicRoot, pl.name(), true);
                case 4 -> {
                    int removed = 0;
                    for (LocalDownloadManager.QueueItem qi : LocalDownloadManager.getQueueSnapshot()) {
                        if (pl.name().equalsIgnoreCase(qi.playlistName())) {
                            LocalDownloadManager.removeFromQueue(qi.videoId());
                            removed++;
                        }
                    }
                    fragment.refreshDownloadStatusUI();
                    Utils.showToastShort(removed + " bekleyen indirme iptal edildi");
                }
                case 5 -> fragment.confirmDeletePlaylist(pl.name());
            }
        });
    }

    public static void showBatchAddToPlaylistMenu(Context ctx, View anchor, LocalDownloadsFragment fragment) {
        if (ctx == null || fragment == null) return;
        File musicRoot = fragment.getMusicRoot();
        if (LocalDownloadsUIState.selectedVideoIds.isEmpty()) {
            Utils.showToastShort("Önce şarkı seçin");
            return;
        }
        String[] items = new String[LocalDownloadsUIState.playlists.size() + 1];
        items[0] = "+ Yeni Liste Oluştur";
        for (int i = 0; i < LocalDownloadsUIState.playlists.size(); i++) {
            items[i + 1] = LocalDownloadsUIState.playlists.get(i).name();
        }
        LocalDownloadsUIState.showMenu(ctx, anchor, items, -1, pos -> {
            if (pos == 0) {
                showCreateOrAutoDownloadPlaylistDialog(ctx, musicRoot, null, null, fragment);
            } else {
                String target = LocalDownloadsUIState.playlists.get(pos - 1).name();
                int added = OfflineStorage.addTracksToPlaylist(
                        musicRoot, target, new ArrayList<>(LocalDownloadsUIState.selectedVideoIds));
                LocalDownloadsUIState.selectedVideoIds.clear();
                LocalDownloadsUIState.selectionMode = false;
                LocalDownloadsUIState.diskReloadNeeded = true;
                fragment.showTracks();
                Utils.showToastShort(added + " şarkı " + target + " listesine eklendi");
            }
        });
    }

    private static void showTrackInfoDialog(Activity act, OfflineTrack cur) {
        String info = "Başlık: " + cur.displayTitle() + "\nSanatçı: " + cur.displayArtist()
                + "\nAlbüm: " + cur.displayAlbum() + "\nSüre: " + LocalDownloadsUIState.formatDuration(cur.durationSeconds())
                + "\nBoyut: " + LocalDownloadsUIState.formatSize(cur.audioFile().length()) + " MB\nVideo ID: " + cur.videoId();
        CustomDialog.create(act, "Şarkı Bilgisi & Jenerik", info, null, "Tamam", () -> {}, null, null, null, false).first.show();
    }

    private static int parsePositiveInt(String val, int fallback) {
        try {
            int parsed = Integer.parseInt(val.trim());
            return Math.max(0, parsed);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    // =====================================================================================
    // OYNATICI EFEKT MERKEZİ
    // =====================================================================================

    private interface EffectToggleListener {
        void onChanged(boolean newValue);
    }

    private interface EffectSliderListener {
        void onChanged(float value);
    }

    private interface ColorPickListener {
        void onPicked(int color);
    }

    private static TextView addEffectToggleRow(Context ctx, LinearLayout parent, String label,
                                               boolean initial, EffectToggleListener listener) {
        final boolean[] state = {initial};
        TextView row = LocalDownloadsUIState.text(ctx,
                label + ": " + (initial ? "AÇIK ✓" : "KAPALI"),
                14, LocalDownloadsUIState.primaryTextColor());
        row.setTypeface(Typeface.DEFAULT_BOLD);
        row.setPadding(
                LocalDownloadsUIState.dp(ctx, 14),
                LocalDownloadsUIState.dp(ctx, 12),
                LocalDownloadsUIState.dp(ctx, 14),
                LocalDownloadsUIState.dp(ctx, 12));
        row.setBackground(LocalDownloadsUIState.glassCardDrawable(
                ctx, LocalDownloadsUIState.dp(ctx, 10), initial));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(ctx, 6));
        row.setLayoutParams(lp);
        row.setOnClickListener(v -> {
            state[0] = !state[0];
            row.setText(label + ": " + (state[0] ? "AÇIK ✓" : "KAPALI"));
            row.setBackground(LocalDownloadsUIState.glassCardDrawable(
                    ctx, LocalDownloadsUIState.dp(ctx, 10), state[0]));
            if (listener != null) listener.onChanged(state[0]);
        });
        parent.addView(row);
        return row;
    }

    private static void addEffectSliderRow(Context ctx, LinearLayout parent, String labelPrefix,
                                           float currentValue, float min, float max,
                                           EffectSliderListener listener) {
        LinearLayout wrapper = new LinearLayout(ctx);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(
                LocalDownloadsUIState.dp(ctx, 14),
                LocalDownloadsUIState.dp(ctx, 10),
                LocalDownloadsUIState.dp(ctx, 14),
                LocalDownloadsUIState.dp(ctx, 12));
        wrapper.setBackground(LocalDownloadsUIState.glassCardDrawable(
                ctx, LocalDownloadsUIState.dp(ctx, 10), false));
        LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(-1, -2);
        wLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(ctx, 6));
        wrapper.setLayoutParams(wLp);

        final float[] value = {currentValue};
        TextView label = LocalDownloadsUIState.text(ctx,
                labelPrefix + formatSliderValue(currentValue), 13, LocalDownloadsUIState.primaryTextColor());
        label.setTypeface(Typeface.DEFAULT_BOLD);
        wrapper.addView(label, new LinearLayout.LayoutParams(-1, -2));

        SeekBar slider = new SeekBar(ctx);
        slider.setMax(100);
        slider.setProgress(Math.round(normalize(currentValue, min, max) * 100f));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float t = progress / 100f;
                value[0] = min + t * (max - min);
                label.setText(labelPrefix + formatSliderValue(value[0]));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (listener != null) listener.onChanged(value[0]);
            }
        });
        wrapper.addView(slider, new LinearLayout.LayoutParams(-1, -2));
        parent.addView(wrapper);
    }

    private static void addEffectIntensitySliderRow(Context ctx, LinearLayout parent, String labelPrefix,
                                                    float currentValue, float min, float max,
                                                    EffectSliderListener listener) {
        LinearLayout wrapper = new LinearLayout(ctx);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(
                LocalDownloadsUIState.dp(ctx, 14),
                LocalDownloadsUIState.dp(ctx, 10),
                LocalDownloadsUIState.dp(ctx, 14),
                LocalDownloadsUIState.dp(ctx, 12));
        wrapper.setBackground(LocalDownloadsUIState.glassCardDrawable(
                ctx, LocalDownloadsUIState.dp(ctx, 10), false));
        LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(-1, -2);
        wLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(ctx, 6));
        wrapper.setLayoutParams(wLp);

        final float[] value = {currentValue};
        TextView label = LocalDownloadsUIState.text(ctx,
                labelPrefix + formatIntensityValue(currentValue), 13, LocalDownloadsUIState.primaryTextColor());
        label.setTypeface(Typeface.DEFAULT_BOLD);
        wrapper.addView(label, new LinearLayout.LayoutParams(-1, -2));

        SeekBar slider = new SeekBar(ctx);
        slider.setMax(100);
        slider.setProgress(Math.round(normalize(currentValue, min, max) * 100f));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float t = progress / 100f;
                value[0] = min + t * (max - min);
                label.setText(labelPrefix + formatIntensityValue(value[0]));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (listener != null) listener.onChanged(value[0]);
            }
        });
        wrapper.addView(slider, new LinearLayout.LayoutParams(-1, -2));
        parent.addView(wrapper);
    }

    private static float normalize(float v, float min, float max) {
        if (max <= min) return 0f;
        return Math.max(0f, Math.min(1f, (v - min) / (max - min)));
    }

    private static String formatSliderValue(float v) {
        return String.format(Locale.ROOT, " %.2f×", v);
    }

    private static String formatIntensityValue(float v) {
        return String.format(Locale.ROOT, " %d%%", Math.round(v * 100f));
    }

    private static void setPlayerBool(Context ctx, String key, boolean value) {
        try {
            ctx.getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(key, value).apply();
        } catch (Exception ignored) {}
    }

    private static void refreshOpenPlayerEffects() {
        try {
            if (!LocalDownloadsPlayer.isFullPlayerShowing()) return;
            Context ctx = Utils.getContext();
            if (ctx == null) return;
            LocalDownloadsPlayer.updateDynamicPlayerColors(ctx);
        } catch (Exception ignored) {}
    }

    private static void showColorPickerDialog(Activity activity, String title,
                                              int initialColor, ColorPickListener listener) {
        if (activity == null || listener == null) return;
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = LocalDownloadsUIState.dp(activity, 20);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(LocalDownloadsUIState.menuBackground(activity));

        TextView header = LocalDownloadsUIState.text(activity, title, 16, LocalDownloadsUIState.primaryTextColor());
        header.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(header);

        LinearLayout previewRow = new LinearLayout(activity);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        previewRow.setPadding(0, LocalDownloadsUIState.dp(activity, 10), 0, LocalDownloadsUIState.dp(activity, 6));

        final View preview = new View(activity);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setShape(GradientDrawable.OVAL);
        previewBg.setColor(initialColor);
        previewBg.setStroke(LocalDownloadsUIState.dp(activity, 2), LocalDownloadsUIState.primaryTextColor());
        preview.setBackground(previewBg);
        previewRow.addView(preview, new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(activity, 36), LocalDownloadsUIState.dp(activity, 36)));

        EditText hexInput = new EditText(activity);
        hexInput.setHint("#RRGGBB");
        hexInput.setText(String.format(Locale.ROOT, "#%06X", (initialColor & 0xFFFFFF)));
        hexInput.setTextColor(LocalDownloadsUIState.primaryTextColor());
        hexInput.setHintTextColor(LocalDownloadsUIState.secondary());
        hexInput.setSingleLine(true);
        LinearLayout.LayoutParams hexLp = new LinearLayout.LayoutParams(0, -2, 1f);
        hexLp.setMargins(LocalDownloadsUIState.dp(activity, 10), 0, 0, 0);
        previewRow.addView(hexInput, hexLp);
        box.addView(previewRow, new LinearLayout.LayoutParams(-1, -2));

        final int[] chosen = {initialColor};
        hexInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                try {
                    String raw = s == null ? "" : s.toString().trim();
                    if (!raw.startsWith("#")) raw = "#" + raw;
                    int parsed = Color.parseColor(raw);
                    chosen[0] = parsed;
                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.OVAL);
                    bg.setColor(parsed);
                    bg.setStroke(LocalDownloadsUIState.dp(activity, 2), LocalDownloadsUIState.primaryTextColor());
                    preview.setBackground(bg);
                } catch (Exception ignored) {}
            }
        });

        int[] palette = new int[]{
                Color.parseColor("#FF2A70"), Color.parseColor("#FF6B4A"),
                Color.parseColor("#FFD34E"), Color.parseColor("#B6FF00"),
                Color.parseColor("#34D399"), Color.parseColor("#00E5FF"),
                Color.parseColor("#3B82F6"), Color.parseColor("#60A5FA"),
                Color.parseColor("#A855F7"), Color.parseColor("#C084FC"),
                Color.parseColor("#E6E8F0"), Color.parseColor("#8E2945"),
                Color.parseColor("#C45BDF"), Color.parseColor("#F43F5E"),
                Color.parseColor("#10B981"), Color.parseColor("#1B0A28"),
                Color.parseColor("#0F2A5C"), Color.parseColor("#040406"),
                Color.parseColor("#7C3AED"), Color.parseColor("#EF4444"),
                Color.parseColor("#22D3EE"), Color.parseColor("#F59E0B"),
                Color.parseColor("#FFFFFF"), Color.parseColor("#000000")
        };
        LinearLayout paletteWrap = new LinearLayout(activity);
        paletteWrap.setOrientation(LinearLayout.VERTICAL);
        paletteWrap.setPadding(0, LocalDownloadsUIState.dp(activity, 10), 0, 0);
        int columns = 6;
        for (int row = 0; row < (palette.length + columns - 1) / columns; row++) {
            LinearLayout colorRow = new LinearLayout(activity);
            colorRow.setOrientation(LinearLayout.HORIZONTAL);
            colorRow.setGravity(Gravity.CENTER);
            for (int col = 0; col < columns; col++) {
                int idx = row * columns + col;
                if (idx >= palette.length) break;
                final int color = palette[idx];
                View swatch = new View(activity);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(color);
                bg.setStroke(LocalDownloadsUIState.dp(activity, 1), Color.argb(60, 255, 255, 255));
                swatch.setBackground(bg);
                LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                        LocalDownloadsUIState.dp(activity, 34), LocalDownloadsUIState.dp(activity, 34));
                swLp.setMargins(LocalDownloadsUIState.dp(activity, 4), LocalDownloadsUIState.dp(activity, 4),
                        LocalDownloadsUIState.dp(activity, 4), LocalDownloadsUIState.dp(activity, 4));
                swatch.setLayoutParams(swLp);
                swatch.setOnClickListener(v -> {
                    chosen[0] = color;
                    hexInput.setText(String.format(Locale.ROOT, "#%06X", (color & 0xFFFFFF)));
                });
                colorRow.addView(swatch);
            }
            paletteWrap.addView(colorRow, new LinearLayout.LayoutParams(-1, -2));
        }
        box.addView(paletteWrap, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, LocalDownloadsUIState.dp(activity, 14), 0, 0);
        TextView cancelBtn = LocalDownloadsUIState.createPillButton(activity, "İptal", false);
        TextView okBtn = LocalDownloadsUIState.createPillButton(activity, "Uygula", true);
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(-2, -2);
        okLp.setMargins(LocalDownloadsUIState.dp(activity, 8), 0, 0, 0);
        okBtn.setLayoutParams(okLp);
        actions.addView(cancelBtn);
        actions.addView(okBtn);
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(box).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        okBtn.setOnClickListener(v -> {
            listener.onPicked(chosen[0]);
            dialog.dismiss();
        });
        dialog.show();
    }

    public static void showPlayerEffectsDialog(Activity activity, LocalDownloadsFragment fragment) {
        if (activity == null) return;

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout overlay = new FrameLayout(activity);
        overlay.setBackgroundColor(Color.argb(150, 0, 0, 0));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(
                LocalDownloadsUIState.dp(activity, 20),
                LocalDownloadsUIState.dp(activity, 16),
                LocalDownloadsUIState.dp(activity, 20),
                LocalDownloadsUIState.dp(activity, 28));
        sheet.setBackground(LocalDownloadsUIState.bottomSheetSurfaceDrawable(activity));
        sheet.setClickable(true);

        int screenH = activity.getResources().getDisplayMetrics().heightPixels;

        View handle = new View(activity);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setCornerRadius(LocalDownloadsUIState.dp(activity, 3));
        handleBg.setColor(Color.argb(90, 255, 255, 255));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(activity, 42), LocalDownloadsUIState.dp(activity, 4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(activity, 14));
        sheet.addView(handle, handleLp);

        LinearLayout topRow = new LinearLayout(activity);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = LocalDownloadsUIState.text(activity, "🎬 Oynatıcı Efekt Merkezi", 19, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        topRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageButton closeBtn = LocalDownloadsUIState.icon(
                activity,
                LocalDownloadsUIState.createVectorIconDrawable(activity, LocalDownloadsUIState.ICON_CLOSE, LocalDownloadsUIState.primaryTextColor(), 0),
                "Kapat");
        closeBtn.setOnClickListener(v ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, null));
        topRow.addView(closeBtn, new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(activity, 36), LocalDownloadsUIState.dp(activity, 36)));
        sheet.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        TextView info = LocalDownloadsUIState.text(activity,
                "Tüm değişiklikler anında kaydedilir ve oynatıcı açıksa canlı uygulanır.",
                12, LocalDownloadsUIState.secondary());
        info.setPadding(0, LocalDownloadsUIState.dp(activity, 4), 0, LocalDownloadsUIState.dp(activity, 12));
        sheet.addView(info);

        // ---- 1. Genel Anahtarlar ----
        TextView secGeneral = LocalDownloadsUIState.text(activity,
                "⚡ Genel Anahtarlar", 14, LocalDownloadsUIState.activeAccentColor());
        secGeneral.setTypeface(Typeface.DEFAULT_BOLD);
        secGeneral.setPadding(0, 0, 0, LocalDownloadsUIState.dp(activity, 8));
        sheet.addView(secGeneral);

        addEffectToggleRow(activity, sheet,
                "🎛️ Efekt Ana Anahtarı (tüm efektler + arka plan gradyanı)",
                LocalDownloadsUIState.isEffectsMasterEnabled(activity),
                newVal -> {
                    LocalDownloadsUIState.setEffectsMasterEnabled(activity, newVal);
                    refreshOpenPlayerEffects();
                    Utils.showToastShort(newVal ? "Efektler açıldı" : "Efektler tamamen kapatıldı");
                });

        addEffectToggleRow(activity, sheet,
                "🔋 Düşük Güç Modu",
                LocalDownloadsUIState.isLowPowerMode(activity),
                newVal -> {
                    LocalDownloadsUIState.setLowPowerMode(activity, newVal);
                    refreshOpenPlayerEffects();
                    Utils.showToastShort(newVal
                            ? "Düşük güç modu açıldı: animasyonlar yavaşlatıldı"
                            : "Düşük güç modu kapatıldı");
                });

        addEffectToggleRow(activity, sheet,
                "🌙 Odak Modu (sadece kapak + temel kontroller)",
                LocalDownloadsUIState.isFocusMode(activity),
                newVal -> {
                    LocalDownloadsUIState.setFocusMode(activity, newVal);
                    refreshOpenPlayerEffects();
                    Utils.showToastShort(newVal ? "Odak modu açık" : "Odak modu kapalı");
                });

        // ---- 2. Arka Plan Rengi ----
        TextView secColor = LocalDownloadsUIState.text(activity,
                "🎨 Arka Plan Rengi", 14, LocalDownloadsUIState.activeAccentColor());
        secColor.setTypeface(Typeface.DEFAULT_BOLD);
        secColor.setPadding(0, LocalDownloadsUIState.dp(activity, 12), 0, LocalDownloadsUIState.dp(activity, 8));
        sheet.addView(secColor);

        final LocalDownloadsUIState.BackgroundColorMode[] currentMode = {
                LocalDownloadsUIState.getBackgroundColorMode(activity)
        };
        TextView modeBtn = LocalDownloadsUIState.text(activity,
                "🌈 Renk Modu: " + currentMode[0].label, 14, LocalDownloadsUIState.primaryTextColor());
        modeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        modeBtn.setPadding(LocalDownloadsUIState.dp(activity, 14), LocalDownloadsUIState.dp(activity, 12),
                LocalDownloadsUIState.dp(activity, 14), LocalDownloadsUIState.dp(activity, 12));
        modeBtn.setBackground(LocalDownloadsUIState.glassCardDrawable(
                activity, LocalDownloadsUIState.dp(activity, 10), true));
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(-1, -2);
        modeLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(activity, 8));
        modeBtn.setLayoutParams(modeLp);
        sheet.addView(modeBtn);

        LinearLayout customColorRow = new LinearLayout(activity);
        customColorRow.setOrientation(LinearLayout.HORIZONTAL);
        customColorRow.setGravity(Gravity.CENTER_VERTICAL);
        customColorRow.setPadding(0, 0, 0, LocalDownloadsUIState.dp(activity, 6));

        final View colorSwatch1 = new View(activity);
        final View colorSwatch2 = new View(activity);
        final TextView colorLabel1 = LocalDownloadsUIState.text(activity, "Renk 1", 12, LocalDownloadsUIState.secondary());
        final TextView colorLabel2 = LocalDownloadsUIState.text(activity, "Renk 2", 12, LocalDownloadsUIState.secondary());

        Runnable refreshColorSwatches = () -> {
            int c1 = LocalDownloadsUIState.getCustomGradientColor1(activity);
            int c2 = LocalDownloadsUIState.getCustomGradientColor2(activity);
            GradientDrawable bg1 = new GradientDrawable();
            bg1.setShape(GradientDrawable.OVAL);
            bg1.setColor(c1);
            bg1.setStroke(LocalDownloadsUIState.dp(activity, 1), LocalDownloadsUIState.primaryTextColor());
            colorSwatch1.setBackground(bg1);
            GradientDrawable bg2 = new GradientDrawable();
            bg2.setShape(GradientDrawable.OVAL);
            bg2.setColor(c2);
            bg2.setStroke(LocalDownloadsUIState.dp(activity, 1), LocalDownloadsUIState.primaryTextColor());
            colorSwatch2.setBackground(bg2);
        };

        colorSwatch1.setOnClickListener(v -> showColorPickerDialog(activity, "Renk 1 Seç",
                LocalDownloadsUIState.getCustomGradientColor1(activity), color -> {
                    LocalDownloadsUIState.setCustomGradientColor1(activity, color);
                    refreshColorSwatches.run();
                    refreshOpenPlayerEffects();
                }));

        colorSwatch2.setOnClickListener(v -> showColorPickerDialog(activity, "Renk 2 Seç",
                LocalDownloadsUIState.getCustomGradientColor2(activity), color -> {
                    LocalDownloadsUIState.setCustomGradientColor2(activity, color);
                    refreshColorSwatches.run();
                    refreshOpenPlayerEffects();
                }));

        LinearLayout colorBox1 = new LinearLayout(activity);
        colorBox1.setOrientation(LinearLayout.VERTICAL);
        colorBox1.setGravity(Gravity.CENTER);
        colorBox1.setPadding(
                LocalDownloadsUIState.dp(activity, 8),
                LocalDownloadsUIState.dp(activity, 8),
                LocalDownloadsUIState.dp(activity, 8),
                LocalDownloadsUIState.dp(activity, 8));
        colorBox1.setBackground(LocalDownloadsUIState.glassCardDrawable(activity, LocalDownloadsUIState.dp(activity, 10), false));
        colorBox1.addView(colorSwatch1, new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(activity, 40), LocalDownloadsUIState.dp(activity, 40)));
        colorBox1.addView(colorLabel1);
        LinearLayout.LayoutParams cbLp1 = new LinearLayout.LayoutParams(0, -2, 1f);
        cbLp1.setMargins(0, 0, LocalDownloadsUIState.dp(activity, 8), 0);
        customColorRow.addView(colorBox1, cbLp1);

        LinearLayout colorBox2 = new LinearLayout(activity);
        colorBox2.setOrientation(LinearLayout.VERTICAL);
        colorBox2.setGravity(Gravity.CENTER);
        colorBox2.setPadding(
                LocalDownloadsUIState.dp(activity, 8),
                LocalDownloadsUIState.dp(activity, 8),
                LocalDownloadsUIState.dp(activity, 8),
                LocalDownloadsUIState.dp(activity, 8));
        colorBox2.setBackground(LocalDownloadsUIState.glassCardDrawable(activity, LocalDownloadsUIState.dp(activity, 10), false));
        colorBox2.addView(colorSwatch2, new LinearLayout.LayoutParams(
                LocalDownloadsUIState.dp(activity, 40), LocalDownloadsUIState.dp(activity, 40)));
        colorBox2.addView(colorLabel2);
        customColorRow.addView(colorBox2, new LinearLayout.LayoutParams(0, -2, 1f));

        sheet.addView(customColorRow, new LinearLayout.LayoutParams(-1, -2));
        refreshColorSwatches.run();

        addEffectIntensitySliderRow(activity, sheet,
                "✨ Renk Canlılığı:",
                LocalDownloadsUIState.getGradientVibrancy(activity),
                LocalDownloadsUIState.MIN_VIBRANCY,
                LocalDownloadsUIState.MAX_VIBRANCY,
                newVal -> {
                    LocalDownloadsUIState.setGradientVibrancy(activity, newVal);
                    refreshOpenPlayerEffects();
                });

        modeBtn.setOnClickListener(v -> {
            LocalDownloadsUIState.BackgroundColorMode[] modes =
                    LocalDownloadsUIState.BackgroundColorMode.values();
            String[] names = new String[modes.length];
            for (int i = 0; i < modes.length; i++) names[i] = modes[i].label;
            LocalDownloadsUIState.showMenu(activity, modeBtn, names,
                    currentMode[0].ordinal(), pos -> {
                        currentMode[0] = modes[pos];
                        LocalDownloadsUIState.setBackgroundColorMode(activity, currentMode[0]);
                        modeBtn.setText("🌈 Renk Modu: " + currentMode[0].label);
                        customColorRow.setVisibility(
                                currentMode[0] == LocalDownloadsUIState.BackgroundColorMode.CUSTOM
                                        ? View.VISIBLE : View.GONE);
                        refreshOpenPlayerEffects();
                    });
        });
        customColorRow.setVisibility(
                currentMode[0] == LocalDownloadsUIState.BackgroundColorMode.CUSTOM
                        ? View.VISIBLE : View.GONE);

        // ---- 3. Görsel Efekt Anahtarları ----
        TextView secEffects = LocalDownloadsUIState.text(activity,
                "✨ Görsel Efektler", 14, LocalDownloadsUIState.activeAccentColor());
        secEffects.setTypeface(Typeface.DEFAULT_BOLD);
        secEffects.setPadding(0, LocalDownloadsUIState.dp(activity, 12), 0, LocalDownloadsUIState.dp(activity, 8));
        sheet.addView(secEffects);

        addEffectToggleRow(activity, sheet,
                "🎨 Dinamik Albüm Kapağı Renkleri",
                LocalDownloadsUIState.isPlayerDynamicColorsEnabled(activity),
                newVal -> {
                    setPlayerBool(activity, "player_dynamic_artwork_colors", newVal);
                    refreshOpenPlayerEffects();
                    Utils.showToastShort(newVal
                            ? "Arka plan kapak renklerine uyacak"
                            : "Statik tema arka planı kullanılacak");
                });

        addEffectToggleRow(activity, sheet,
                "💿 Kapak Nefes Alma Animasyonu",
                LocalDownloadsUIState.isPlayerCoverBreathingEnabled(activity),
                newVal -> {
                    setPlayerBool(activity, "player_cover_breathing", newVal);
                    LocalDownloadsPlayer.updateDynamicPlayerColors(activity);
                    Utils.showToastShort(newVal
                            ? "Kapak nefes alma açık"
                            : "Kapak nefes alma kapatıldı");
                });

        addEffectToggleRow(activity, sheet,
                "🌌 Sinematik Geçişler",
                LocalDownloadsUIState.isPlayerCinematicTransitionsEnabled(activity),
                newVal -> {
                    setPlayerBool(activity, "player_cinematic_transitions", newVal);
                    Utils.showToastShort(newVal
                            ? "Sinematik geçişler açık"
                            : "Sinematik geçişler kapatıldı");
                });

        addEffectToggleRow(activity, sheet,
                "🔮 Albüm Kapağı Çevresinde Hale",
                LocalDownloadsUIState.isHaloEnabled(activity),
                newVal -> {
                    LocalDownloadsUIState.setHaloEnabled(activity, newVal);
                    refreshOpenPlayerEffects();
                    Utils.showToastShort(newVal ? "Hale efekt açıldı" : "Hale efekt kapatıldı");
                });

        // ---- 4. Animasyon Hızı ve Yoğunluk ----
        TextView secSpeed = LocalDownloadsUIState.text(activity,
                "🎚️ Animasyon Hızı & Yoğunluk", 14, LocalDownloadsUIState.activeAccentColor());
        secSpeed.setTypeface(Typeface.DEFAULT_BOLD);
        secSpeed.setPadding(0, LocalDownloadsUIState.dp(activity, 12), 0, LocalDownloadsUIState.dp(activity, 8));
        sheet.addView(secSpeed);

        addEffectSliderRow(activity, sheet,
                "⏩ Animasyon Hızı:",
                LocalDownloadsUIState.getAnimationSpeedScale(activity),
                LocalDownloadsUIState.MIN_ANIMATION_SPEED,
                LocalDownloadsUIState.MAX_ANIMATION_SPEED,
                newVal -> {
                    LocalDownloadsUIState.setAnimationSpeedScale(activity, newVal);
                    refreshOpenPlayerEffects();
                });

        addEffectIntensitySliderRow(activity, sheet,
                "🔆 Animasyon Yoğunluğu:",
                LocalDownloadsUIState.getAnimationIntensity(activity),
                LocalDownloadsUIState.MIN_ANIMATION_INTENSITY,
                LocalDownloadsUIState.MAX_ANIMATION_INTENSITY,
                newVal -> {
                    LocalDownloadsUIState.setAnimationIntensity(activity, newVal);
                    refreshOpenPlayerEffects();
                });

        // ---- 5. Kişisel Efekt Profilleri ----
        TextView secProfiles = LocalDownloadsUIState.text(activity,
                "🗂️ Kişisel Efekt Profilleri", 14, LocalDownloadsUIState.activeAccentColor());
        secProfiles.setTypeface(Typeface.DEFAULT_BOLD);
        secProfiles.setPadding(0, LocalDownloadsUIState.dp(activity, 12), 0, LocalDownloadsUIState.dp(activity, 8));
        sheet.addView(secProfiles);

        TextView saveProfileBtn = LocalDownloadsUIState.createPillButton(activity,
                "💾 Mevcut Ayarları Yeni Profil Olarak Kaydet", true);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(-1, -2);
        spLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(activity, 8));
        saveProfileBtn.setLayoutParams(spLp);
        saveProfileBtn.setOnClickListener(v ->
                showSaveEffectProfileDialog(activity, dialog, fragment));
        sheet.addView(saveProfileBtn);

        List<LocalDownloadsUIState.EffectProfile> savedProfiles =
                LocalDownloadsUIState.getSavedEffectProfiles(activity);
        if (savedProfiles.isEmpty()) {
            TextView empty = LocalDownloadsUIState.text(activity,
                    "Henüz kayıtlı profil yok. Yukarıdaki buton ile ilk profilinizi oluşturun.",
                    12, LocalDownloadsUIState.secondary());
            empty.setPadding(LocalDownloadsUIState.dp(activity, 6), 0,
                    LocalDownloadsUIState.dp(activity, 6), LocalDownloadsUIState.dp(activity, 12));
            sheet.addView(empty);
        } else {
            for (LocalDownloadsUIState.EffectProfile profile : savedProfiles) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(
                        LocalDownloadsUIState.dp(activity, 12),
                        LocalDownloadsUIState.dp(activity, 10),
                        LocalDownloadsUIState.dp(activity, 12),
                        LocalDownloadsUIState.dp(activity, 10));
                row.setBackground(LocalDownloadsUIState.glassCardDrawable(
                        activity, LocalDownloadsUIState.dp(activity, 10), false));
                LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
                rLp.setMargins(0, 0, 0, LocalDownloadsUIState.dp(activity, 6));
                row.setLayoutParams(rLp);

                LinearLayout infoCol = new LinearLayout(activity);
                infoCol.setOrientation(LinearLayout.VERTICAL);
                TextView pName = LocalDownloadsUIState.text(activity, profile.name(), 14, LocalDownloadsUIState.primaryTextColor());
                pName.setTypeface(Typeface.DEFAULT_BOLD);
                pName.setSingleLine(true);
                pName.setEllipsize(TextUtils.TruncateAt.END);

                String themeLabel;
                try {
                    themeLabel = LocalDownloadsUIState.ThemePalette.valueOf(profile.themeName()).label;
                } catch (Exception ignored) {
                    themeLabel = profile.themeName();
                }
                StringBuilder summary = new StringBuilder();
                summary.append(themeLabel).append(" • ");
                summary.append(String.format(Locale.ROOT, "%.2f×", profile.speedScale())).append(" • ");
                summary.append(String.format(Locale.ROOT, "%d%%", Math.round(profile.intensity() * 100f)));
                summary.append(" • ").append(
                        (profile.colorMode() == null ? "Tema" : profile.colorMode().label));
                if (profile.lowPower()) summary.append(" • Düşük Güç");
                if (profile.focusMode()) summary.append(" • Odak");

                TextView pMeta = LocalDownloadsUIState.text(activity, summary.toString(), 11,
                        LocalDownloadsUIState.secondary());
                pMeta.setSingleLine(true);
                infoCol.addView(pName);
                infoCol.addView(pMeta);
                row.addView(infoCol, new LinearLayout.LayoutParams(0, -2, 1f));

                TextView applyBtn = LocalDownloadsUIState.createPillButton(activity, "Uygula", true);
                applyBtn.setOnClickListener(v -> {
                    if (LocalDownloadsUIState.applyEffectProfile(activity, profile)) {
                        Utils.showToastShort("Profil uygulandı: " + profile.name());
                        LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                                () -> {
                                    LocalDownloadsUIState.refreshAllThemeVisuals(fragment);
                                    showPlayerEffectsDialog(activity, fragment);
                                });
                    } else {
                        Utils.showToastShort("Profil uygulanamadı");
                    }
                });
                LinearLayout.LayoutParams apLp = new LinearLayout.LayoutParams(-2, -2);
                apLp.setMargins(0, 0, LocalDownloadsUIState.dp(activity, 6), 0);
                applyBtn.setLayoutParams(apLp);
                row.addView(applyBtn);

                TextView deleteBtn = LocalDownloadsUIState.createPillButton(activity, "🗑️", false);
                deleteBtn.setOnClickListener(v -> {
                    boolean removed = LocalDownloadsUIState.deleteEffectProfile(activity, profile.name());
                    if (removed) {
                        Utils.showToastShort("Profil silindi: " + profile.name());
                        LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH,
                                () -> showPlayerEffectsDialog(activity, fragment));
                    } else {
                        Utils.showToastShort("Profil silinemedi");
                    }
                });
                row.addView(deleteBtn);

                sheet.addView(row);
            }
        }

        // ---- 6. Alt Kapat Butonu ----
        LinearLayout bottomRow = new LinearLayout(activity);
        bottomRow.setGravity(Gravity.END);
        bottomRow.setPadding(0, LocalDownloadsUIState.dp(activity, 8), 0, 0);
        TextView doneBtn = LocalDownloadsUIState.createPillButton(activity, "Tamam", true);
        doneBtn.setOnClickListener(v ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, null));
        bottomRow.addView(doneBtn);
        sheet.addView(bottomRow, new LinearLayout.LayoutParams(-1, -2));

        scroll.addView(sheet);
        overlay.addView(scroll, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        overlay.setOnClickListener(v ->
                LocalDownloadsAnimator.animateBottomSheetDismiss(sheet, overlay, dialog, screenH, null));

        dialog.setContentView(overlay);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            OfflineHeaderBlocker.applyImmersiveFullscreen(dialog.getWindow(), true);
        }
        dialog.show();
        LocalDownloadsAnimator.animateBottomSheetShow(sheet, overlay, screenH);
    }

    private static void showSaveEffectProfileDialog(Activity activity, Dialog parentDialog,
                                                    LocalDownloadsFragment fragment) {
        if (activity == null) return;
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = LocalDownloadsUIState.dp(activity, 20);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(LocalDownloadsUIState.menuBackground(activity));

        TextView title = LocalDownloadsUIState.text(activity, "💾 Profil Adı", 16, LocalDownloadsUIState.primaryTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);

        TextView sub = LocalDownloadsUIState.text(activity,
                "Tema, renk modu, canlılık, hız, yoğunluk, hale, odak ve düşük güç ayarların tek profilde saklanır.",
                12, LocalDownloadsUIState.secondary());
        sub.setPadding(0, LocalDownloadsUIState.dp(activity, 4), 0, LocalDownloadsUIState.dp(activity, 10));
        box.addView(sub);

        EditText nameInput = new EditText(activity);
        nameInput.setHint("Örn: Sinematik Gece, Griffith Modu…");
        nameInput.setTextColor(LocalDownloadsUIState.primaryTextColor());
        nameInput.setHintTextColor(LocalDownloadsUIState.secondary());
        nameInput.setSingleLine(true);
        box.addView(nameInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, LocalDownloadsUIState.dp(activity, 12), 0, 0);
        TextView cancelBtn = LocalDownloadsUIState.createPillButton(activity, "İptal", false);
        TextView saveBtn = LocalDownloadsUIState.createPillButton(activity, "Kaydet", true);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(-2, -2);
        sLp.setMargins(LocalDownloadsUIState.dp(activity, 8), 0, 0, 0);
        saveBtn.setLayoutParams(sLp);
        actions.addView(cancelBtn);
        actions.addView(saveBtn);
        box.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(box).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        saveBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Utils.showToastShort("Lütfen profil adı girin");
                return;
            }
            boolean ok = LocalDownloadsUIState.saveEffectProfile(activity, name);
            if (ok) {
                Utils.showToastShort("Profil kaydedildi: " + name);
                dialog.dismiss();
                if (parentDialog != null) {
                    try { if (parentDialog.isShowing()) parentDialog.dismiss(); } catch (Exception ignored) {}
                }
                if (fragment != null) {
                    showPlayerEffectsDialog(activity, fragment);
                }
            } else {
                Utils.showToastShort("Profil kaydedilemedi");
            }
        });
        dialog.show();
    }
}