package app.morphe.extension.music.patches.downloads;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;

import app.morphe.extension.music.settings.Settings;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.ui.CustomDialog;

public final class LocalDownloadsUIState {
    public static final int SEEK_BAR_HEIGHT_DP = 4;
    public static final int SEEK_BAR_TOUCH_DP = 32;
    public static final int SEEK_THUMB_DP = 14;
    public static final int SEARCH_BAR_HEIGHT_DP = 46;
    public static final int ROW_CORNER_DP = 12;
    public static final int ROW_ARTWORK_PIXELS = 192;
    public static final int FULL_ARTWORK_PIXELS = 720;
    public static final int ARTWORK_CORNER_DP = 8;
    public static final int FULL_ARTWORK_CORNER_DP = 16;
    public static final int MENU_WIDTH_DP = 250;
    public static final int MENU_CORNER_DP = 16;
    public static final int ICON_BUTTON_DP = 40;

    public static final int ICON_CHEVRON_DOWN = 1;
    public static final int ICON_SHUFFLE = 2;
    public static final int ICON_REPEAT = 3;
    public static final int ICON_PLAY = 4;
    public static final int ICON_PAUSE = 5;
    public static final int ICON_SKIP_PREV = 6;
    public static final int ICON_SKIP_NEXT = 7;
    public static final int ICON_PALETTE = 8;
    public static final int ICON_GRID = 9;
    public static final int ICON_LIST = 10;
    public static final int ICON_DOWNLOAD_MANAGER = 12;
    public static final int ICON_CHECKLIST = 13;
    public static final int ICON_SETTINGS = 14;
    public static final int ICON_BACK = 15;
    public static final int ICON_CLOSE = 16;
    public static final int ICON_FILTER = 17;
    public static final int ICON_DOWNLOAD = 18;
    public static final int ICON_MORE_VERT = 19;

    public static final int REQ_PICK_PLAYLIST_COVER = 9101;
    public static final int REQ_PICK_THEME_WALLPAPER = 9102;
    public static final int REQ_PICK_TRACK_COVER = 9103;

    public static final String PREF_NAME = "morphe_ui_theme_prefs";
    public static final String PREF_THEME_KEY = "selected_theme_palette";
    public static final String PREF_GRID_MODE_KEY = "playlist_grid_mode";
    public static final String PREF_WALLPAPER_ACTIVE_KEY = "custom_wallpaper_active";
    public static final String PREF_SWIPE_TO_PLAY_NEXT_KEY = "swipe_to_play_next_enabled";
    public static final String CUSTOM_WALLPAPER_FILE = "custom_theme_wallpaper.jpg";

    // GÖREV 2: Oynatıcı Seçimi (0 = Normal, 1 = Faye)
    public static final String PREF_PLAYER_TYPE_KEY = "selected_player_type";

    // GÖREV 3: Faye özel renk kalıcılığı (renk seçici diyalogları için)
    public static final String PREF_FAYE_BG_TOP_KEY = "faye_custom_bg_top";
    public static final String PREF_FAYE_ACCENT_PRIMARY_KEY = "faye_custom_accent_primary";
    public static final String PREF_FAYE_ACCENT_SECONDARY_KEY = "faye_custom_accent_secondary";

    public static final String PREF_EFFECTS_MASTER_KEY = "player_effects_master_enabled";
    public static final String PREF_ANIMATION_SPEED_KEY = "player_animation_speed_scale";
    public static final String PREF_ANIMATION_INTENSITY_KEY = "player_animation_intensity";
    public static final String PREF_HALO_ENABLED_KEY = "player_halo_enabled";
    public static final String PREF_VISUALIZER_ENABLED_KEY = "player_neon_visualizer_enabled";
    public static final String PREF_FOCUS_MODE_KEY = "player_focus_mode_enabled";
    public static final String PREF_LOW_POWER_MODE_KEY = "player_low_power_mode_enabled";
    public static final String PREF_EFFECT_PROFILES_KEY = "player_effect_profiles_json";

    public static final String PREF_BG_COLOR_MODE_KEY = "player_bg_color_mode";
    public static final String PREF_CUSTOM_COLOR1_KEY = "player_custom_gradient_color1";
    public static final String PREF_CUSTOM_COLOR2_KEY = "player_custom_gradient_color2";
    public static final String PREF_VIBRANCY_KEY = "player_gradient_vibrancy";

    public static final String PREF_CROSSFADE_ENABLED = "player_crossfade_enabled";
    public static final String PREF_CROSSFADE_SECONDS = "player_crossfade_seconds";
    public static final String PREF_SLEEP_FADE_ENABLED = "player_sleep_fade_enabled";

    public static final float DEFAULT_ANIMATION_SPEED = 1.0f;
    public static final float DEFAULT_ANIMATION_INTENSITY = 0.7f;
    public static final float MIN_ANIMATION_SPEED = 0.4f;
    public static final float MAX_ANIMATION_SPEED = 2.0f;
    public static final float MIN_ANIMATION_INTENSITY = 0.1f;
    public static final float MAX_ANIMATION_INTENSITY = 1.0f;

    public static final float DEFAULT_VIBRANCY = 1.0f;
    public static final float MIN_VIBRANCY = 0.4f;
    public static final float MAX_VIBRANCY = 1.8f;

    public static final int DEFAULT_CUSTOM_COLOR1 = Color.parseColor("#C45BDF");
    public static final int DEFAULT_CUSTOM_COLOR2 = Color.parseColor("#FFD34E");

    public static final LruCache<String, Bitmap> ARTWORK_CACHE = new LruCache<>(90);
    public static final Map<String, CachedTrackEntry> TRACK_CACHE = new ConcurrentHashMap<>();

    // GÖREV 7: Sanatçı listesini her seferinde hesaplamak yerine önbelleğe alıyoruz (Performans)
    private static List<String> cachedArtists = null;

    public record CachedTrackEntry(long lastModified, long length,
                                   long metadataLastModified, long metadataLength,
                                   OfflineTrack track) {}

    public enum ThemeAnimation {
        STANDARD_PULSE, GRIFFITH_FLOW, FAYE_FLOW, EVANGELION_PULSE,
        EMERALD_GLOW, SAPPHIRE_FLOW, CRIMSON_WAVE, AMETHYST_SHIMMER
    }

    public enum BackgroundColorMode {
        THEME("Tema Renkleri"), COVER("Albüm Kapağı Renkleri"), CUSTOM("Serbest Renkler");
        public final String label;
        BackgroundColorMode(String label) { this.label = label; }
    }

    public record EffectProfile(
            String name, String themeName, boolean dynamicColors, boolean breathing,
            boolean halo, boolean visualizer, float speedScale, float intensity,
            boolean focusMode, boolean lowPower, BackgroundColorMode colorMode,
            int customColor1, int customColor2, float vibrancy
    ) {}

    public enum FilterMode { ALL, UNASSIGNED, BROKEN_OR_INCOMPLETE }
    public enum SmartFilterMode { NONE, MOST_PLAYED, RECENTLY_ADDED, NIGHT_PLAYS, NEVER_PLAYED }

    public enum SortOrder {
        DATE_DESC, ARTIST, TITLE, SIZE;
        public static SortOrder saved() {
            try { return valueOf(Settings.DOWNLOADS_SORT.get()); }
            catch (Exception ignored) { return DATE_DESC; }
        }
    }

    /**
     * Tema paletleri.
     *
     * <p><b>ÖNEMLİ:</b> Enum sabitleri normalde değiştirilemez ({@code final} alanlar).
     * Ancak Faye teması için renk seçici diyaloğundan renkleri runtime'da değiştirebilmek
     * istiyoruz. Bu yüzden renk alanlarını {@code final} olmaktan çıkardık. Enum sabiti
     * kimliği (referans) hâlâ tekil kalır; sadece o sabitin <i>içindeki</i> renk int'leri
     * mutate edilebilir. Java'da {@code new ThemePalette(...)} çağrısı <b>derleme hatası</b>
     * verdiği için mutasyon yaklaşımı zorunludur.</p>
     */
    public enum ThemePalette {
        YT_MUSIC_PINK("YT Müzik Pembe Tema", Color.parseColor("#24071C"), Color.parseColor("#140410"), Color.parseColor("#090208"), Color.parseColor("#FF2A70"), Color.parseColor("#FF6E9E"), Color.argb(42, 255, 180, 215), Color.argb(75, 255, 80, 145), Color.parseColor("#FFFFFF")),
        AMOLED_NIGHT("AMOLED Gece", Color.parseColor("#0A0A0C"), Color.parseColor("#040406"), Color.parseColor("#000000"), Color.parseColor("#FF2A70"), Color.parseColor("#00E5FF"), Color.argb(36, 255, 255, 255), Color.argb(60, 255, 255, 255), Color.parseColor("#FFFFFF")),
        EMERALD_DEPTH("Zümrüt Derinliği", Color.parseColor("#07261E"), Color.parseColor("#041511"), Color.parseColor("#020A08"), Color.parseColor("#10B981"), Color.parseColor("#34D399"), Color.argb(40, 16, 185, 129), Color.argb(75, 52, 211, 153), Color.parseColor("#041511")),
        SAPPHIRE_OCEAN("Safir Okyanus", Color.parseColor("#0A1931"), Color.parseColor("#060E1E"), Color.parseColor("#030710"), Color.parseColor("#3B82F6"), Color.parseColor("#60A5FA"), Color.argb(40, 59, 130, 246), Color.argb(75, 96, 165, 250), Color.parseColor("#FFFFFF")),
        CRIMSON_VELVET("Kızıl Kadife", Color.parseColor("#3B0711"), Color.parseColor("#1C0408"), Color.parseColor("#0A0203"), Color.parseColor("#F43F5E"), Color.parseColor("#FB7185"), Color.argb(40, 244, 63, 94), Color.argb(75, 251, 113, 133), Color.parseColor("#FFFFFF")),
        ROYAL_AMETHYST("Asil Ametist", Color.parseColor("#240E3B"), Color.parseColor("#130721"), Color.parseColor("#08030F"), Color.parseColor("#A855F7"), Color.parseColor("#C084FC"), Color.argb(40, 168, 85, 247), Color.argb(75, 192, 132, 252), Color.parseColor("#FFFFFF")),
        EVANGELION("Evangelion • Neon Genesis", Color.parseColor("#17102D"), Color.parseColor("#090B19"), Color.parseColor("#03050B"), Color.parseColor("#A855F7"), Color.parseColor("#B6FF00"), Color.argb(42, 168, 85, 247), Color.argb(85, 182, 255, 0), Color.parseColor("#FFFFFF")),
        GRIFFITH("Berserk • Griffith", Color.parseColor("#292B36"), Color.parseColor("#12131A"), Color.parseColor("#07070B"), Color.parseColor("#E6E8F0"), Color.parseColor("#8E2945"), Color.argb(38, 230, 232, 240), Color.argb(75, 142, 41, 69), Color.parseColor("#101116")),
        FAYE("Cowboy Bebop • Faye", Color.parseColor("#35164A"), Color.parseColor("#1B0D2B"), Color.parseColor("#090511"), Color.parseColor("#C77DFF"), Color.parseColor("#FFD166"), Color.argb(46, 199, 125, 255), Color.argb(92, 255, 209, 102), Color.parseColor("#21112E"));

        public final String label;
        // DİKKAT: Faye renk seçicisi için bu alanlar artık final DEĞİL (mutasyon için).
        public int varBgTop, varBgMid, varBgBottom, varAccentPrimary, varAccentSecondary,
                   varGlassSurface, varGlassBorder, varOnAccent;

        ThemePalette(String label, int varBgTop, int varBgMid, int varBgBottom, int varAccentPrimary, int varAccentSecondary, int varGlassSurface, int varGlassBorder, int varOnAccent) {
            this.label = label; this.varBgTop = varBgTop; this.varBgMid = varBgMid; this.varBgBottom = varBgBottom;
            this.varAccentPrimary = varAccentPrimary; this.varAccentSecondary = varAccentSecondary;
            this.varGlassSurface = varGlassSurface; this.varGlassBorder = varGlassBorder; this.varOnAccent = varOnAccent;
        }

        /**
         * Faye paletini fabrika değerlerine döndürür. Renk seçici diyaloğundaki
         * "Varsayılan Faye Ayarlarına Dön" butonundan çağrılır.
         */
        public static void resetFayeDefaults() {
            FAYE.varBgTop          = Color.parseColor("#35164A");
            FAYE.varBgMid          = Color.parseColor("#1B0D2B");
            FAYE.varBgBottom       = Color.parseColor("#090511");
            FAYE.varAccentPrimary  = Color.parseColor("#C77DFF");
            FAYE.varAccentSecondary= Color.parseColor("#FFD166");
            FAYE.varGlassSurface   = Color.argb(46, 199, 125, 255);
            FAYE.varGlassBorder    = Color.argb(92, 255, 209, 102);
            FAYE.varOnAccent       = Color.parseColor("#21112E");
        }
    }

    public static ThemePalette currentTheme = ThemePalette.YT_MUSIC_PINK;
    public static boolean customWallpaperActive = false;
    public static boolean playlistGridMode = true;
    public static boolean swipeToPlayNext = true;
    public static boolean showingPlaylistsOverview = true;
    public static boolean showingArtists = false;

    @Nullable public static String selectedArtist = null;
    public static SmartFilterMode currentSmartFilter = SmartFilterMode.NONE;
    public static FilterMode currentFilterMode = FilterMode.ALL;
    public static boolean selectionMode = false;
    public static boolean diskReloadNeeded = true;
    public static boolean animateNextSongList = true;
    public static long lyricsOffsetMs = 0;

    public static List<OfflineTrack> tracks = new ArrayList<>();
    public static List<OfflineStorage.Playlist> playlists = new ArrayList<>();
    public static final Set<String> selectedVideoIds = new LinkedHashSet<>();
    public static final List<OfflineTrack> currentDisplayedTracks = new ArrayList<>();
    public static final List<View> rowViews = new ArrayList<>();
    public static List<String> queuePaths = new ArrayList<>();

    @Nullable public static String selectedPlaylist = null;
    @Nullable public static String pendingCoverPlaylistName = null;
    @Nullable public static String pendingCoverTrackId = null;
    @Nullable public static Bitmap customWallpaperBitmap = null;
    public static String playingPath = "";
    public static String query = "";

    @Nullable public static LinearLayout rootLayout;
    @Nullable public static LinearLayout headerLayout;
    @Nullable public static LinearLayout bottomNavLayout;
    @Nullable public static TextView fabPlusButton;
    @Nullable public static LinearLayout songsList;
    @Nullable public static LinearLayout miniPlayer;
    @Nullable public static LinearLayout downloadBanner;
    @Nullable public static LinearLayout batchActionBar;
    @Nullable public static LinearLayout queueDialogListContainer;

    @Nullable public static TextView downloadBannerTitle;
    @Nullable public static TextView downloadBannerSub;
    @Nullable public static TextView downloadBannerPauseBtn;
    @Nullable public static TextView batchCountText;
    @Nullable public static TextView queueDialogSummaryText;
    @Nullable public static TextView queueDialogPauseBtn;
    @Nullable public static TextView backToLibraryBtn;
    @Nullable public static TextView summary;
    @Nullable public static ProgressBar downloadBannerProgress;
    @Nullable public static EditText searchBar;

    @Nullable public static ImageButton viewModeBtn;
    @Nullable public static ImageButton selectModeBtn;
    @Nullable public static ImageButton headerFilterBtn;
    @Nullable public static ImageButton headerSettingsBtn;
    @Nullable public static AlertDialog queueManagerDialog;

    public static final Map<View, Integer> hiddenHostViews = new HashMap<>();
    public static boolean hostActionBarStateCaptured = false;
    public static boolean hostActionBarWasShowing = false;
    public static boolean hostWindowStateCaptured = false;
    public static boolean hostWindowWasFullscreen = false;

    private LocalDownloadsUIState() {}

    public static void loadSavedPreferences(Context context, File musicRoot) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String saved = prefs.getString(PREF_THEME_KEY, ThemePalette.YT_MUSIC_PINK.name());
            currentTheme = ThemePalette.valueOf(saved);
            playlistGridMode = prefs.getBoolean(PREF_GRID_MODE_KEY, true);
            customWallpaperActive = prefs.getBoolean(PREF_WALLPAPER_ACTIVE_KEY, false);
            swipeToPlayNext = prefs.getBoolean(PREF_SWIPE_TO_PLAY_NEXT_KEY, true);
        } catch (Exception ignored) {
            currentTheme = ThemePalette.YT_MUSIC_PINK;
            playlistGridMode = true;
            customWallpaperActive = false;
            swipeToPlayNext = true;
        }
        // Faye özel renk kalıcılığını geri yükle (varsa)
        loadFayeCustomColors(context);
        reloadCustomWallpaper(musicRoot);
    }

    /**
     * Faye teması için kullanıcının özelleştirdiği renkleri prefs'ten yükler.
     * Uygulama süreci her başladığında enum sabitleri fabrika değerine döner;
     * bu metot en son kaydedilen özel renkleri geri uygular.
     */
    public static void loadFayeCustomColors(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            if (prefs.contains(PREF_FAYE_BG_TOP_KEY)) {
                ThemePalette.FAYE.varBgTop = prefs.getInt(PREF_FAYE_BG_TOP_KEY, ThemePalette.FAYE.varBgTop);
            }
            if (prefs.contains(PREF_FAYE_ACCENT_PRIMARY_KEY)) {
                ThemePalette.FAYE.varAccentPrimary = prefs.getInt(PREF_FAYE_ACCENT_PRIMARY_KEY, ThemePalette.FAYE.varAccentPrimary);
            }
            if (prefs.contains(PREF_FAYE_ACCENT_SECONDARY_KEY)) {
                ThemePalette.FAYE.varAccentSecondary = prefs.getInt(PREF_FAYE_ACCENT_SECONDARY_KEY, ThemePalette.FAYE.varAccentSecondary);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Renk seçici diyaloğundan çağrılır. Faye enum alanını mutate eder ve
     * değeri kalıcı hale getirir.
     *
     * @param type 0 = bgTop, 1 = accentPrimary, 2 = accentSecondary
     */
    public static void saveFayeCustomColor(Context context, int type, int color) {
        switch (type) {
            case 0 -> ThemePalette.FAYE.varBgTop = color;
            case 1 -> ThemePalette.FAYE.varAccentPrimary = color;
            case 2 -> ThemePalette.FAYE.varAccentSecondary = color;
            default -> { return; }
        }
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String key = type == 0 ? PREF_FAYE_BG_TOP_KEY
                    : type == 1 ? PREF_FAYE_ACCENT_PRIMARY_KEY
                    : PREF_FAYE_ACCENT_SECONDARY_KEY;
            prefs.edit().putInt(key, color).apply();
        } catch (Exception ignored) {}
    }

    /** Faye özel renklerini prefs'ten siler (varsayılana dön için). */
    public static void clearFayeCustomColors(Context context) {
        if (context == null) return;
        try {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                    .remove(PREF_FAYE_BG_TOP_KEY)
                    .remove(PREF_FAYE_ACCENT_PRIMARY_KEY)
                    .remove(PREF_FAYE_ACCENT_SECONDARY_KEY)
                    .apply();
        } catch (Exception ignored) {}
    }

    public static void reloadCustomWallpaper(File musicRoot) {
        if (!customWallpaperActive || musicRoot == null) {
            customWallpaperBitmap = null;
            return;
        }
        File f = new File(musicRoot, CUSTOM_WALLPAPER_FILE);
        customWallpaperBitmap = f.isFile() ? OfflineStorage.decodeArtwork(f, 1080) : null;
        if (customWallpaperBitmap == null) customWallpaperActive = false;
    }

    public static void savePreferences(Context context) {
        try {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_THEME_KEY, currentTheme.name())
                    .putBoolean(PREF_GRID_MODE_KEY, playlistGridMode)
                    .putBoolean(PREF_WALLPAPER_ACTIVE_KEY, customWallpaperActive)
                    .putBoolean(PREF_SWIPE_TO_PLAY_NEXT_KEY, swipeToPlayNext)
                    .apply();
        } catch (Exception ignored) {}
    }

    public static void resetCustomWallpaper(Context context, File musicRoot, LocalDownloadsFragment fragment) {
        customWallpaperActive = false;
        customWallpaperBitmap = null;
        OfflineStorage.delete(new File(musicRoot, CUSTOM_WALLPAPER_FILE));
        savePreferences(context);
        refreshAllThemeVisuals(fragment);
        Utils.showToastShort("Özel duvar kağıdı sıfırlandı");
    }

    public static boolean isPlayer2FayeSelected(Context ctx) {
        if (ctx == null) return false;
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(PREF_PLAYER_TYPE_KEY, 0) == 1;
    }

    public static void setPlayer2FayeSelected(Context ctx, boolean selected) {
        if (ctx == null) return;
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(PREF_PLAYER_TYPE_KEY, selected ? 1 : 0).apply();
    }

    public static void refreshAllThemeVisuals(LocalDownloadsFragment fragment) {
        Context ctx = fragment != null ? fragment.getActivity() : Utils.getContext();
        applyListBackground(rootLayout);

        if (headerLayout != null) {
            LocalDownloadsAnimator.animateBackgroundColorChange(headerLayout, currentTheme.varBgTop);
        }
        if (summary != null) summary.setTextColor(primaryTextColor());
        if (headerFilterBtn != null && ctx != null) {
            headerFilterBtn.setImageDrawable(createVectorIconDrawable(ctx, ICON_FILTER, activeAccentColor(), 0));
            headerFilterBtn.setBackground(iconRippleDrawable());
        }
        if (headerSettingsBtn != null && ctx != null) {
            headerSettingsBtn.setImageDrawable(createVectorIconDrawable(ctx, ICON_SETTINGS, activeAccentColor(), 0));
            headerSettingsBtn.setBackground(iconRippleDrawable());
        }
        if (selectModeBtn != null && ctx != null) {
            selectModeBtn.setImageDrawable(createVectorIconDrawable(ctx, ICON_CHECKLIST,
                    selectionMode ? activeAccentColor() : primaryTextColor(), 0));
            selectModeBtn.setBackground(iconRippleDrawable());
        }
        if (viewModeBtn != null && ctx != null) {
            viewModeBtn.setImageDrawable(createVectorIconDrawable(ctx,
                    playlistGridMode ? ICON_GRID : ICON_LIST, primaryTextColor(), 0));
            viewModeBtn.setBackground(iconRippleDrawable());
        }
        if (backToLibraryBtn != null && ctx != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(ctx, 18));
            bg.setColor(currentTheme.varGlassSurface);
            bg.setStroke(dp(ctx, 1), currentTheme.varGlassBorder);
            backToLibraryBtn.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor()), bg, bg));
        }
        if (fabPlusButton != null) {
            fabPlusButton.setTextColor(currentTheme.varOnAccent);
            GradientDrawable fabBg = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{activeAccentColor(), activeSecondaryAccent()});
            fabBg.setShape(GradientDrawable.OVAL);
            fabPlusButton.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(Color.argb(70, 255, 255, 255)), fabBg, fabBg));
        }
        if (downloadBanner != null && ctx != null) {
            downloadBanner.setBackground(glassCardDrawable(ctx, dp(ctx, 12), true));
            if (downloadBannerProgress != null) {
                downloadBannerProgress.setProgressDrawable(createThemeProgressDrawable(dp(ctx, 4), true));
            }
        }
        if (batchActionBar != null && ctx != null) {
            batchActionBar.setBackground(glassCardDrawable(ctx, dp(ctx, 12), true));
        }
        if (bottomNavLayout != null && ctx != null) {
            LocalDownloadsHeader.refreshBottomNavigationTheme(ctx);
        }
        if (fragment != null) fragment.showTracks();

        LocalDownloadsPlayer.updateDynamicPlayerColors(ctx);
    }

    public static int primaryTextColor() {
        return currentTheme == ThemePalette.FAYE ? Color.parseColor("#FFE7A3") : Color.WHITE;
    }

    public static int activeAccentColor() { return currentTheme.varAccentPrimary; }
    public static int activeSecondaryAccent() { return currentTheme.varAccentSecondary; }

    public static int secondary() {
        return currentTheme == ThemePalette.FAYE ? Color.parseColor("#CDBBD9") : Color.argb(180, 235, 230, 240);
    }

    public static int dp(Context ctx, int val) {
        return (int) (val * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static float dp(Context ctx, float val) {
        return val * ctx.getResources().getDisplayMetrics().density;
    }

    public static int rippleColor() {
        int accent = activeAccentColor();
        return Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent));
    }

    public static Drawable iconRippleDrawable() {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor()), null, new ShapeDrawable(new OvalShape()));
    }

    public static Drawable createCircleRipple() {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(65, 255, 255, 255)), null, new ShapeDrawable(new OvalShape()));
    }

    public static Drawable glassCardDrawable(Context ctx, int cornerRadiusPx, boolean highlighted) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(cornerRadiusPx);
        gd.setColor(currentTheme.varGlassSurface);
        gd.setStroke(dp(ctx, 1), highlighted ? activeAccentColor() : currentTheme.varGlassBorder);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor()), gd, gd);
    }

    public static Drawable bottomSheetSurfaceDrawable(Context ctx) {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{currentTheme.varBgTop, currentTheme.varBgMid, currentTheme.varBgBottom});
        float r = dp(ctx, 22);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke(dp(ctx, 1), currentTheme.varGlassBorder);
        return bg;
    }

    public static Drawable menuBackground(Context ctx) {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{currentTheme.varBgMid, currentTheme.varBgBottom});
        bg.setCornerRadius(dp(ctx, MENU_CORNER_DP));
        bg.setStroke(dp(ctx, 1), currentTheme.varGlassBorder);
        return bg;
    }

    public static Drawable rowBackground(Context ctx, boolean playing) {
        GradientDrawable card = null;
        if (playing) {
            int accent = activeAccentColor();
            card = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{
                            Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent)),
                            Color.argb(22, Color.red(accent), Color.green(accent), Color.blue(accent))
                    });
            card.setCornerRadius(dp(ctx, ROW_CORNER_DP));
            card.setStroke(dp(ctx, 1), currentTheme.varGlassBorder);
        }
        return new RippleDrawable(ColorStateList.valueOf(rippleColor()), card,
                CustomDialog.createRoundedBackground(ROW_CORNER_DP, Color.WHITE));
    }

    public static void applyListBackground(@Nullable View root) {
        if (root == null) return;
        root.setBackground(buildSceneBackgroundDrawable(null, false));
    }

    public static Drawable buildSceneBackgroundDrawable(@Nullable OfflineTrack track, boolean isFullPlayer) {
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(@NonNull Canvas canvas) {
                Rect b = getBounds();
                int w = b.width(), h = b.height();
                if (w <= 0 || h <= 0) return;

                if (customWallpaperActive && customWallpaperBitmap != null) {
                    drawCenterCropBitmap(canvas, customWallpaperBitmap, w, h);
                    paint.setShader(new LinearGradient(0, 0, 0, h,
                            new int[]{
                                    Color.argb(145, Color.red(currentTheme.varBgTop), Color.green(currentTheme.varBgTop), Color.blue(currentTheme.varBgTop)),
                                    Color.argb(200, Color.red(currentTheme.varBgMid), Color.green(currentTheme.varBgMid), Color.blue(currentTheme.varBgMid)),
                                    Color.argb(242, Color.red(currentTheme.varBgBottom), Color.green(currentTheme.varBgBottom), Color.blue(currentTheme.varBgBottom))
                            },
                            null, Shader.TileMode.CLAMP));
                    canvas.drawRect(0, 0, w, h, paint);
                    paint.setShader(null);
                    return;
                }

                int top = currentTheme.varBgTop;
                int mid = currentTheme.varBgMid;
                int bot = currentTheme.varBgBottom;
                if (track != null && isFullPlayer) {
                    top = blendColors(top, track.extractDominantColor(top), 0.32f);
                }

                paint.setShader(new LinearGradient(0, 0, 0, h, new int[]{top, mid, bot}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(null);
            }

            private void drawCenterCropBitmap(Canvas canvas, Bitmap bmp, int viewW, int viewH) {
                int bw = bmp.getWidth(), bh = bmp.getHeight();
                float scale = Math.max((float) viewW / bw, (float) viewH / bh);
                float sw = viewW / scale, sh = viewH / scale;
                int srcX = Math.max(0, (int) ((bw - sw) / 2f));
                int srcY = Math.max(0, (int) ((bh - sh) / 2f));
                Rect src = new Rect(srcX, srcY, Math.min(bw, (int) (srcX + sw)), Math.min(bh, (int) (srcY + sh)));
                Rect dst = new Rect(0, 0, viewW, viewH);
                canvas.drawBitmap(bmp, src, dst, paint);
            }

            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(@Nullable ColorFilter cf) { paint.setColorFilter(cf); }
            @Override public int getOpacity() { return PixelFormat.OPAQUE; }
        };
    }

    public static int blendColors(int c1, int c2, float ratio) {
        float inv = 1f - ratio;
        int r = (int) (Color.red(c1) * inv + Color.red(c2) * ratio);
        int g = (int) (Color.green(c1) * inv + Color.green(c2) * ratio);
        int b = (int) (Color.blue(c1) * inv + Color.blue(c2) * ratio);
        return Color.rgb(r, g, b);
    }

    public static Drawable createThemeProgressDrawable(final int barHeightPx, final boolean rounded) {
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected boolean onLevelChange(int level) {
                invalidateSelf();
                return true;
            }

            @Override
            public void draw(@NonNull Canvas canvas) {
                Rect b = getBounds();
                if (b.width() <= 0 || b.height() <= 0) return;

                float cy = b.exactCenterY();
                float halfH = barHeightPx / 2f;
                float radius = rounded ? halfH : 0f;

                paint.setShader(null);
                paint.setColor(Color.argb(85, 255, 255, 255));
                RectF trackRect = new RectF(b.left, cy - halfH, b.right, cy + halfH);
                canvas.drawRoundRect(trackRect, radius, radius, paint);

                float ratio = Math.max(0f, Math.min(1f, getLevel() / 10000f));
                float playedRight = b.left + b.width() * ratio;
                if (playedRight > b.left) {
                    RectF playedRect = new RectF(b.left, cy - halfH, playedRight, cy + halfH);
                    paint.setShader(new LinearGradient(
                            b.left, cy, b.right, cy,
                            new int[]{activeAccentColor(), activeSecondaryAccent()},
                            null, Shader.TileMode.CLAMP));
                    canvas.drawRoundRect(playedRect, radius, radius, paint);
                }
            }

            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(@Nullable ColorFilter cf) { paint.setColorFilter(cf); }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }

    public static Drawable createVectorIconDrawable(Context ctx, int type, int color, int mode) {
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            public void draw(@NonNull Canvas canvas) {
                float cx = getBounds().width() / 2f, cy = getBounds().height() / 2f;
                paint.setColor(color);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);

                if (type == ICON_PLAY) {
                    paint.setStyle(Paint.Style.FILL);
                    float s = dp(ctx, 10f);
                    Path p = new Path();
                    p.moveTo(cx - s * 0.65f, cy - s);
                    p.lineTo(cx + s * 0.95f, cy);
                    p.lineTo(cx - s * 0.65f, cy + s);
                    p.close();
                    canvas.drawPath(p, paint);
                } else if (type == ICON_PAUSE) {
                    paint.setStyle(Paint.Style.FILL);
                    float w = dp(ctx, 3.5f), h = dp(ctx, 10f), gap = dp(ctx, 4.5f);
                    canvas.drawRoundRect(new RectF(cx - gap - w, cy - h, cx - gap + w, cy + h), dp(ctx, 2f), dp(ctx, 2f), paint);
                    canvas.drawRoundRect(new RectF(cx + gap - w, cy - h, cx + gap + w, cy + h), dp(ctx, 2f), dp(ctx, 2f), paint);
                } else if (type == ICON_SKIP_PREV) {
                    paint.setStyle(Paint.Style.FILL);
                    float s = dp(ctx, 8f);
                    canvas.drawRoundRect(new RectF(cx - s, cy - s, cx - s + dp(ctx, 2.5f), cy + s), dp(ctx, 1f), dp(ctx, 1f), paint);
                    Path p = new Path();
                    p.moveTo(cx + s, cy - s);
                    p.lineTo(cx - s + dp(ctx, 3f), cy);
                    p.lineTo(cx + s, cy + s);
                    p.close();
                    canvas.drawPath(p, paint);
                } else if (type == ICON_SKIP_NEXT) {
                    paint.setStyle(Paint.Style.FILL);
                    float s = dp(ctx, 8f);
                    canvas.drawRoundRect(new RectF(cx + s - dp(ctx, 2.5f), cy - s, cx + s, cy + s), dp(ctx, 1f), dp(ctx, 1f), paint);
                    Path p = new Path();
                    p.moveTo(cx - s, cy - s);
                    p.lineTo(cx + s - dp(ctx, 3f), cy);
                    p.lineTo(cx - s, cy + s);
                    p.close();
                    canvas.drawPath(p, paint);
                } else if (type == ICON_PALETTE || type == ICON_SETTINGS) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(ctx, 2f));
                    canvas.drawCircle(cx, cy, dp(ctx, 5.5f), paint);
                    for (int a = 0; a < 360; a += 45) {
                        double rad = Math.toRadians(a);
                        float x1 = cx + (float) Math.cos(rad) * dp(ctx, 6f);
                        float y1 = cy + (float) Math.sin(rad) * dp(ctx, 6f);
                        float x2 = cx + (float) Math.cos(rad) * dp(ctx, 8.5f);
                        float y2 = cy + (float) Math.sin(rad) * dp(ctx, 8.5f);
                        canvas.drawLine(x1, y1, x2, y2, paint);
                    }
                } else if (type == ICON_FILTER) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(ctx, 2f));
                    Path p = new Path();
                    p.moveTo(cx - dp(ctx, 8f), cy - dp(ctx, 6f));
                    p.lineTo(cx + dp(ctx, 8f), cy - dp(ctx, 6f));
                    p.lineTo(cx + dp(ctx, 3f), cy - dp(ctx, 1f));
                    p.lineTo(cx + dp(ctx, 3f), cy + dp(ctx, 6f));
                    p.lineTo(cx - dp(ctx, 2f), cy + dp(ctx, 8f));
                    p.lineTo(cx - dp(ctx, 2f), cy - dp(ctx, 1f));
                    p.close();
                    canvas.drawPath(p, paint);
                } else if (type == ICON_CLOSE) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(ctx, 2.2f));
                    float s = dp(ctx, 6f);
                    canvas.drawLine(cx - s, cy - s, cx + s, cy + s, paint);
                    canvas.drawLine(cx + s, cy - s, cx - s, cy + s, paint);
                } else if (type == ICON_BACK) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(ctx, 2.2f));
                    float s = dp(ctx, 6.5f);
                    canvas.drawLine(cx - s, cy, cx + s, cy, paint);
                    canvas.drawLine(cx - s, cy, cx - dp(ctx, 1f), cy - dp(ctx, 5.5f), paint);
                    canvas.drawLine(cx - s, cy, cx - dp(ctx, 1f), cy + dp(ctx, 5.5f), paint);
                } else if (type == ICON_GRID) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(ctx, 1.8f));
                    float g = dp(ctx, 6.5f), gap = dp(ctx, 1.5f);
                    canvas.drawRoundRect(new RectF(cx - g, cy - g, cx - gap, cy - gap), dp(ctx, 1.5f), dp(ctx, 1.5f), paint);
                    canvas.drawRoundRect(new RectF(cx + gap, cy - g, cx + g, cy - gap), dp(ctx, 1.5f), dp(ctx, 1.5f), paint);
                    canvas.drawRoundRect(new RectF(cx - g, cy + gap, cx - gap, cy + g), dp(ctx, 1.5f), dp(ctx, 1.5f), paint);
                    canvas.drawRoundRect(new RectF(cx + gap, cy + gap, cx + g, cy + g), dp(ctx, 1.5f), dp(ctx, 1.5f), paint);
                } else if (type == ICON_LIST) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(ctx, 2f));
                    float w = dp(ctx, 7f);
                    canvas.drawLine(cx - w, cy - dp(ctx, 5f), cx + w, cy - dp(ctx, 5f), paint);
                    canvas.drawLine(cx - w, cy, cx + w, cy, paint);
                    canvas.drawLine(cx - w, cy + dp(ctx, 5f), cx + w, cy + dp(ctx, 5f), paint);
                } else if (type == ICON_DOWNLOAD_MANAGER || type == ICON_DOWNLOAD) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(ctx, 2f));
                    canvas.drawLine(cx, cy - dp(ctx, 7f), cx, cy + dp(ctx, 3f), paint);
                    Path p = new Path();
                    p.moveTo(cx - dp(ctx, 4.5f), cy - dp(ctx, 1f));
                    p.lineTo(cx, cy + dp(ctx, 3.5f));
                    p.lineTo(cx + dp(ctx, 4.5f), cy - dp(ctx, 1f));
                    canvas.drawPath(p, paint);
                    canvas.drawLine(cx - dp(ctx, 6f), cy + dp(ctx, 7f), cx + dp(ctx, 6f), cy + dp(ctx, 7f), paint);
                } else if (type == ICON_MORE_VERT) {
                    paint.setStyle(Paint.Style.FILL);
                    float r = dp(ctx, 2f);
                    canvas.drawCircle(cx, cy - dp(ctx, 6f), r, paint);
                    canvas.drawCircle(cx, cy, r, paint);
                    canvas.drawCircle(cx, cy + dp(ctx, 6f), r, paint);
                } else if (type == ICON_CHECKLIST) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(ctx, 2f));
                    canvas.drawRoundRect(new RectF(cx - dp(ctx, 7f), cy - dp(ctx, 7f), cx + dp(ctx, 7f), cy + dp(ctx, 7f)), dp(ctx, 2.5f), dp(ctx, 2.5f), paint);
                    Path p = new Path();
                    p.moveTo(cx - dp(ctx, 3.5f), cy);
                    p.lineTo(cx - dp(ctx, 1f), cy + dp(ctx, 2.8f));
                    p.lineTo(cx + dp(ctx, 4f), cy - dp(ctx, 2.8f));
                    canvas.drawPath(p, paint);
                } else if (type == ICON_CHEVRON_DOWN) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(ctx, 2.2f));
                    float s = dp(ctx, 7f);
                    Path p = new Path();
                    p.moveTo(cx - s, cy - s / 2f);
                    p.lineTo(cx, cy + s / 2f);
                    p.lineTo(cx + s, cy - s / 2f);
                    canvas.drawPath(p, paint);
                } else if (type == ICON_SHUFFLE) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(ctx, 2f));
                    float s = dp(ctx, 7f);
                    Path p = new Path();
                    p.moveTo(cx - s, cy - s * 0.6f);
                    p.lineTo(cx + s, cy + s * 0.6f);
                    p.moveTo(cx - s, cy + s * 0.6f);
                    p.lineTo(cx + s, cy - s * 0.6f);
                    canvas.drawPath(p, paint);
                    if (mode != 0) {
                        paint.setStyle(Paint.Style.FILL);
                        canvas.drawCircle(cx, cy + dp(ctx, 11f), dp(ctx, 2.2f), paint);
                    }
                } else if (type == ICON_REPEAT) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(ctx, 2f));
                    float r = dp(ctx, 7f);
                    canvas.drawRoundRect(new RectF(cx - r, cy - r * 0.7f, cx + r, cy + r * 0.7f), dp(ctx, 4f), dp(ctx, 4f), paint);
                    if (mode == OfflinePlaybackService.REPEAT_ONE) {
                        paint.setStyle(Paint.Style.FILL);
                        paint.setTextSize(dp(ctx, 9f));
                        paint.setTypeface(Typeface.DEFAULT_BOLD);
                        paint.setTextAlign(Paint.Align.CENTER);
                        canvas.drawText("1", cx, cy + dp(ctx, 3f), paint);
                    }
                    if (mode != OfflinePlaybackService.REPEAT_OFF) {
                        paint.setStyle(Paint.Style.FILL);
                        canvas.drawCircle(cx, cy + dp(ctx, 11f), dp(ctx, 2.2f), paint);
                    }
                }
            }
            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(@Nullable ColorFilter cf) { paint.setColorFilter(cf); }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }

    public static TextView text(Context ctx, String val, int sp, int color) {
        TextView v = new TextView(ctx);
        v.setText(val);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    public static ImageButton icon(Context ctx, String drawable, String desc) {
        ImageButton b = icon(ctx, ResourceUtils.getDrawable(drawable), desc);
        b.setColorFilter(Color.WHITE);
        return b;
    }

    public static ImageButton icon(Context ctx, Drawable drawable, String desc) {
        ImageButton b = new ImageButton(ctx);
        b.setImageDrawable(drawable);
        b.setContentDescription(desc);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8));
        b.setBackground(iconRippleDrawable());
        return b;
    }

    public static TextView createPillButton(Context ctx, String label, boolean active) {
        int accent = activeAccentColor();
        TextView view = text(ctx, label, 12, active ? currentTheme.varOnAccent : Color.WHITE);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(ctx, 12), dp(ctx, 7), dp(ctx, 12), dp(ctx, 7));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(ctx, 18));
        if (active) {
            bg.setColors(new int[]{accent, activeSecondaryAccent()});
            bg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        } else {
            bg.setColor(currentTheme.varGlassSurface);
            bg.setStroke(dp(ctx, 1), currentTheme.varGlassBorder);
        }
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor()), bg, bg));
        return view;
    }

    public static ImageView artworkView(Context ctx, @Nullable Bitmap bmp, int cornerDp) {
        ImageView v = new ImageView(ctx);
        v.setBackgroundColor(Color.argb(45, 255, 255, 255));
        roundCorners(ctx, v, cornerDp);
        if (bmp != null) {
            v.setImageBitmap(bmp);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            v.setImageDrawable(createVectorIconDrawable(ctx, ICON_PLAY, activeAccentColor(), 0));
            v.setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12));
        }
        return v;
    }

    public static void roundCorners(Context ctx, View v, int cornerDp) {
        final float r = dp(ctx, (float) cornerDp);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    public static void loadArtworkAsync(OfflineTrack track, int maxSize, ImageView target) {
        if (track == null || target == null) return;
        String cacheKey = track.videoId() + "_" + maxSize;
        target.setTag(cacheKey);
        Bitmap cached = ARTWORK_CACHE.get(cacheKey);
        if (cached != null) {
            target.clearColorFilter();
            target.setPadding(0, 0, 0, 0);
            target.setImageBitmap(cached);
            target.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return;
        }
        Utils.submitOnBackgroundThread(() -> {
            Bitmap decoded = track.artwork(maxSize);
            if (decoded != null) {
                ARTWORK_CACHE.put(cacheKey, decoded);
                Utils.runOnMainThread(() -> {
                    if (cacheKey.equals(target.getTag())) {
                        target.clearColorFilter();
                        target.setPadding(0, 0, 0, 0);
                        target.setImageBitmap(decoded);
                        target.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    }
                });
            }
            return null;
        });
    }

    public static void showMenu(Context ctx, View anchor, String[] items, int selected, IntConsumer onPick) {
        ListPopupWindow popup = new ListPopupWindow(ctx);
        popup.setAnchorView(anchor);
        popup.setDropDownGravity(Gravity.END);
        popup.setWidth(dp(ctx, MENU_WIDTH_DP));
        popup.setModal(true);
        popup.setBackgroundDrawable(menuBackground(ctx));
        popup.setListSelector(new RippleDrawable(ColorStateList.valueOf(rippleColor()), null,
                CustomDialog.createRoundedBackground(MENU_CORNER_DP, Color.WHITE)));
        popup.setAdapter(new ArrayAdapter<>(ctx, 0, items) {
            @NonNull @Override
            public View getView(int pos, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView v = text(ctx, getItem(pos), 14, selected < 0 || pos == selected ? Color.WHITE : secondary());
                v.setPadding(dp(ctx, 18), dp(ctx, 13), dp(ctx, 18), dp(ctx, 13));
                return v;
            }
        });
        popup.setOnItemClickListener((p, v, pos, id) -> { popup.dismiss(); onPick.accept(pos); });
        popup.show();
    }

    public static void finishSearchInput(Context ctx) {
        EditText field = searchBar;
        if (field == null || ctx == null) return;
        InputMethodManager keyboard = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            try { keyboard.hideSoftInputFromWindow(field.getWindowToken(), 0); }
            catch (RuntimeException ignored) {}
        }
        field.clearFocus();
        if (rootLayout != null) rootLayout.requestFocus();
    }

    public static void toggleSearch(Context ctx) {
        EditText field = searchBar;
        if (field == null || ctx == null) return;
        boolean opening = field.getVisibility() != View.VISIBLE;
        InputMethodManager kb = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (opening) {
            field.setVisibility(View.VISIBLE);
            LocalDownloadsAnimator.slideViewHeight(field, dp(ctx, SEARCH_BAR_HEIGHT_DP));
            field.animate().cancel();
            field.animate().alpha(1f).setDuration(LocalDownloadsAnimator.SEARCH_ANIM_MS).start();
            field.requestFocus();
            if (kb != null) kb.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
            return;
        }
        if (kb != null) kb.hideSoftInputFromWindow(field.getWindowToken(), 0);
        field.setText("");
        field.clearFocus();
        if (rootLayout != null) rootLayout.requestFocus();
        LocalDownloadsAnimator.slideViewHeight(field, 0);
        field.animate().cancel();
        field.animate().alpha(0f).setDuration(LocalDownloadsAnimator.SEARCH_ANIM_MS)
                .withEndAction(() -> {
                    if (searchBar == field) field.setVisibility(View.GONE);
                }).start();
    }

    public static void loadTracksIfNeeded(File musicRoot) {
        if (!diskReloadNeeded && !tracks.isEmpty()) {
            tracks.sort(comparator());
            return;
        }
        diskReloadNeeded = false;
        tracks = new ArrayList<>();
        File[] files = musicRoot.listFiles(f -> f.isFile() && (f.getName().endsWith(".webm") || f.getName().endsWith(".m4a")));
        if (files != null) {
            for (File file : files) {
                String key = file.getAbsolutePath();
                long lm = file.lastModified();
                long len = file.length();
                File metadataFile = new File(file.getParentFile(), file.getName().substring(0, file.getName().lastIndexOf('.')) + ".json");
                long metaLm = metadataFile.isFile() ? metadataFile.lastModified() : 0L;
                long metaLen = metadataFile.isFile() ? metadataFile.length() : 0L;
                CachedTrackEntry cached = TRACK_CACHE.get(key);
                if (cached != null && cached.lastModified() == lm && cached.length() == len
                        && cached.metadataLastModified() == metaLm && cached.metadataLength() == metaLen) {
                    tracks.add(cached.track());
                } else {
                    OfflineTrack loaded = OfflineTrack.load(file);
                    TRACK_CACHE.put(key, new CachedTrackEntry(lm, len, metaLm, metaLen, loaded));
                    tracks.add(loaded);
                }
            }
            tracks.sort(comparator());
        }
        playlists = OfflineStorage.loadPlaylists(musicRoot);
        if (selectedPlaylist != null && findPlaylist(selectedPlaylist) == null) selectedPlaylist = null;

        // Sanatçı önbelleğini geçersiz kıl (yeniden yükleme yapıldı)
        invalidateArtistCache();
    }

    @Nullable
    public static OfflineStorage.Playlist findPlaylist(@Nullable String name) {
        if (name == null) return null;
        for (OfflineStorage.Playlist primary : OfflineStorage.buildPrimaryLibraryGrid(tracks, playlists)) {
            if (primary.name().equalsIgnoreCase(name)) return primary;
        }
        for (OfflineStorage.Playlist p : playlists) {
            if (p.name().equalsIgnoreCase(name)) return p;
        }
        for (OfflineStorage.Playlist smart : OfflineStorage.buildSmartPlaylists(tracks)) {
            if (smart.name().equalsIgnoreCase(name)) return smart;
        }
        for (OfflineStorage.Playlist artistCard : OfflineStorage.buildArtistCards(tracks)) {
            if (artistCard.name().equalsIgnoreCase(name)) return artistCard;
        }
        return null;
    }

    public static Comparator<OfflineTrack> comparator() {
        Comparator<OfflineTrack> byTitle = Comparator.comparing(t -> t.displayTitle().toLowerCase(Locale.ROOT));
        return switch (SortOrder.saved()) {
            case DATE_DESC -> Comparator.comparingLong((OfflineTrack t) -> t.audioFile().lastModified()).reversed();
            case TITLE -> byTitle;
            case SIZE -> Comparator.comparingLong((OfflineTrack t) -> t.audioFile().length()).reversed();
            default -> Comparator.comparing((OfflineTrack t) -> t.displayArtist().toLowerCase(Locale.ROOT)).thenComparing(byTitle);
        };
    }

    public static boolean matchesQuery(OfflineTrack t) {
        return query.isEmpty() || t.displayTitle().toLowerCase(Locale.ROOT).contains(query)
                || t.displayArtist().toLowerCase(Locale.ROOT).contains(query)
                || t.videoId().toLowerCase(Locale.ROOT).contains(query);
    }

    @Nullable
    public static OfflineTrack findByPath(String path) {
        if (path == null || path.isEmpty()) return null;
        for (OfflineTrack t : tracks) {
            if (t.audioFile().getAbsolutePath().equals(path)) return t;
        }
        return null;
    }

    @Nullable
    public static OfflineTrack findById(String videoId) {
        for (OfflineTrack t : tracks) {
            if (t.videoId().equals(videoId)) return t;
        }
        return null;
    }

    public static Set<String> getAllPlaylistTrackIds() {
        Set<String> set = new HashSet<>();
        for (OfflineStorage.Playlist p : playlists) {
            set.addAll(p.videoIds());
        }
        return set;
    }

    public static void invalidateArtistCache() {
        cachedArtists = null;
    }

    // GÖREV 7: Sanatçı Rafı için performans optimizasyonu (Önbellekli Liste)
    public static List<String> getAllArtists() {
        if (cachedArtists != null) return cachedArtists;

        Set<String> artists = new HashSet<>();
        for (OfflineTrack t : tracks) {
            String artist = t.displayArtist();
            if (artist != null && !artist.isBlank() && !artist.equalsIgnoreCase("bilinmeyen sanatçı") && !artist.equalsIgnoreCase("unknown artist")) {
                artists.add(artist);
            }
        }
        List<String> sortedArtists = new ArrayList<>(artists);
        sortedArtists.sort(String::compareToIgnoreCase);
        cachedArtists = sortedArtists;
        return cachedArtists;
    }

    public static boolean isBrokenOrIncompleteTrack(OfflineTrack t) {
        return t.durationSeconds() <= 0 || t.title().isBlank() || t.title().equals(t.videoId()) || t.audioFile().length() < 32 * 1024;
    }

    public static void markPlayingRow(Context ctx) {
        if (ctx == null) return;
        int idx = queuePaths.indexOf(playingPath);
        for (int i = 0; i < rowViews.size(); i++) {
            boolean isSel = i < currentDisplayedTracks.size() && selectedVideoIds.contains(currentDisplayedTracks.get(i).videoId());
            rowViews.get(i).setBackground(rowBackground(ctx, i == idx || isSel));
        }
    }

    public static String formatDuration(int s) {
        if (s <= 0) return "0:00";
        return s < 3600 ? String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    public static String formatSize(long b) {
        return String.format(Locale.ROOT, "%.1f", b / 1048576.0);
    }

    public static String formatSpeed(long bytesPerSec) {
        if (bytesPerSec <= 0) return "0 KB/sn";
        if (bytesPerSec >= 1048576L) return String.format(Locale.ROOT, "%.2f MB/sn", bytesPerSec / 1048576.0);
        return String.format(Locale.ROOT, "%d KB/sn", Math.max(1L, bytesPerSec / 1024L));
    }

    public static void hideHostActionBar(Activity act) {
        if (act == null) return;
        try {
            ActionBar actionBar = act.getActionBar();
            if (actionBar != null) {
                if (!hostActionBarStateCaptured) {
                    hostActionBarWasShowing = actionBar.isShowing();
                    hostActionBarStateCaptured = true;
                }
                actionBar.hide();
            }
        } catch (Exception ignored) {}
    }

    public static void restoreHostHeader(Activity act) {
        for (Map.Entry<View, Integer> entry : new HashMap<>(hiddenHostViews).entrySet()) {
            View view = entry.getKey();
            if (view != null) view.setVisibility(entry.getValue());
        }
        hiddenHostViews.clear();
        if (act != null) {
            Window window = act.getWindow();
            if (window != null && hostWindowStateCaptured) {
                if (hostWindowWasFullscreen) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                else window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
            if (hostActionBarStateCaptured) {
                ActionBar actionBar = act.getActionBar();
                if (actionBar != null) {
                    if (hostActionBarWasShowing) actionBar.show();
                    else actionBar.hide();
                }
            }
        }
        hostActionBarStateCaptured = false;
        hostWindowStateCaptured = false;
    }

    public static ThemeAnimation getAnimationForTheme(ThemePalette theme) {
        if (theme == null) return ThemeAnimation.STANDARD_PULSE;
        switch (theme) {
            case GRIFFITH: return ThemeAnimation.GRIFFITH_FLOW;
            case FAYE: return ThemeAnimation.FAYE_FLOW;
            case EVANGELION: return ThemeAnimation.EVANGELION_PULSE;
            case EMERALD_DEPTH: return ThemeAnimation.EMERALD_GLOW;
            case SAPPHIRE_OCEAN: return ThemeAnimation.SAPPHIRE_FLOW;
            case CRIMSON_VELVET: return ThemeAnimation.CRIMSON_WAVE;
            case ROYAL_AMETHYST: return ThemeAnimation.AMETHYST_SHIMMER;
            default: return ThemeAnimation.STANDARD_PULSE;
        }
    }

    public static ThemeAnimation getCurrentAnimation() { return getAnimationForTheme(currentTheme); }

    private static SharedPreferences effectsPrefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEffectsMasterEnabled(Context ctx) {
        try { return effectsPrefs(ctx).getBoolean(PREF_EFFECTS_MASTER_KEY, true); }
        catch (Exception ignored) { return true; }
    }

    public static void setEffectsMasterEnabled(Context ctx, boolean enabled) {
        try { effectsPrefs(ctx).edit().putBoolean(PREF_EFFECTS_MASTER_KEY, enabled).apply(); }
        catch (Exception ignored) {}
    }

    public static boolean isLowPowerMode(Context ctx) {
        try { return effectsPrefs(ctx).getBoolean(PREF_LOW_POWER_MODE_KEY, false); }
        catch (Exception ignored) { return false; }
    }

    public static void setLowPowerMode(Context ctx, boolean enabled) {
        try { effectsPrefs(ctx).edit().putBoolean(PREF_LOW_POWER_MODE_KEY, enabled).apply(); }
        catch (Exception ignored) {}
    }

    public static boolean isFocusMode(Context ctx) {
        try { return effectsPrefs(ctx).getBoolean(PREF_FOCUS_MODE_KEY, false); }
        catch (Exception ignored) { return false; }
    }

    public static void setFocusMode(Context ctx, boolean enabled) {
        try { effectsPrefs(ctx).edit().putBoolean(PREF_FOCUS_MODE_KEY, enabled).apply(); }
        catch (Exception ignored) {}
    }

    public static boolean isHaloEnabled(Context ctx) {
        try { return effectsPrefs(ctx).getBoolean(PREF_HALO_ENABLED_KEY, true); }
        catch (Exception ignored) { return true; }
    }

    public static void setHaloEnabled(Context ctx, boolean enabled) {
        try { effectsPrefs(ctx).edit().putBoolean(PREF_HALO_ENABLED_KEY, enabled).apply(); }
        catch (Exception ignored) {}
    }

    public static boolean isVisualizerEnabled(Context ctx) {
        try { return effectsPrefs(ctx).getBoolean(PREF_VISUALIZER_ENABLED_KEY, false); }
        catch (Exception ignored) { return false; }
    }

    public static void setVisualizerEnabled(Context ctx, boolean enabled) {
        try { effectsPrefs(ctx).edit().putBoolean(PREF_VISUALIZER_ENABLED_KEY, enabled).apply(); }
        catch (Exception ignored) {}
    }

    public static boolean isHaloEffectivelyEnabled(Context ctx) {
        return isEffectsMasterEnabled(ctx) && !isLowPowerMode(ctx) && isHaloEnabled(ctx);
    }

    public static float getAnimationSpeedScale(Context ctx) {
        try {
            float v = effectsPrefs(ctx).getFloat(PREF_ANIMATION_SPEED_KEY, DEFAULT_ANIMATION_SPEED);
            return Math.max(MIN_ANIMATION_SPEED, Math.min(MAX_ANIMATION_SPEED, v));
        } catch (Exception ignored) { return DEFAULT_ANIMATION_SPEED; }
    }

    public static void setAnimationSpeedScale(Context ctx, float v) {
        try {
            float clamped = Math.max(MIN_ANIMATION_SPEED, Math.min(MAX_ANIMATION_SPEED, v));
            effectsPrefs(ctx).edit().putFloat(PREF_ANIMATION_SPEED_KEY, clamped).apply();
        } catch (Exception ignored) {}
    }

    public static float getAnimationIntensity(Context ctx) {
        try {
            float v = effectsPrefs(ctx).getFloat(PREF_ANIMATION_INTENSITY_KEY, DEFAULT_ANIMATION_INTENSITY);
            return Math.max(MIN_ANIMATION_INTENSITY, Math.min(MAX_ANIMATION_INTENSITY, v));
        } catch (Exception ignored) { return DEFAULT_ANIMATION_INTENSITY; }
    }

    public static void setAnimationIntensity(Context ctx, float v) {
        try {
            float clamped = Math.max(MIN_ANIMATION_INTENSITY, Math.min(MAX_ANIMATION_INTENSITY, v));
            effectsPrefs(ctx).edit().putFloat(PREF_ANIMATION_INTENSITY_KEY, clamped).apply();
        } catch (Exception ignored) {}
    }

    public static long scaleDuration(Context ctx, long baseMs) {
        float scale = getAnimationSpeedScale(ctx);
        if (scale <= 0f) scale = 1f;
        long scaled = Math.round(baseMs / scale);
        return Math.max(40L, scaled);
    }

    public static boolean isPlayerCoverBreathingEnabled(Context ctx) {
        try { return effectsPrefs(ctx).getBoolean("player_cover_breathing", true); }
        catch (Exception ignored) { return true; }
    }

    public static boolean isPlayerDynamicColorsEnabled(Context ctx) {
        try { return effectsPrefs(ctx).getBoolean("player_dynamic_artwork_colors", true); }
        catch (Exception ignored) { return true; }
    }

    public static boolean isPlayerCinematicTransitionsEnabled(Context ctx) {
        try { return effectsPrefs(ctx).getBoolean("player_cinematic_transitions", true); }
        catch (Exception ignored) { return true; }
    }

    public static boolean isCrossfadeEnabled(Context ctx) {
        try { return effectsPrefs(ctx).getBoolean(PREF_CROSSFADE_ENABLED, false); }
        catch (Exception ignored) { return false; }
    }

    public static void setCrossfadeEnabled(Context ctx, boolean enabled) {
        try { effectsPrefs(ctx).edit().putBoolean(PREF_CROSSFADE_ENABLED, enabled).apply(); }
        catch (Exception ignored) {}
    }

    public static int getCrossfadeSeconds(Context ctx) {
        try { return effectsPrefs(ctx).getInt(PREF_CROSSFADE_SECONDS, 5); }
        catch (Exception ignored) { return 5; }
    }

    public static void setCrossfadeSeconds(Context ctx, int seconds) {
        try { effectsPrefs(ctx).edit().putInt(PREF_CROSSFADE_SECONDS, seconds).apply(); }
        catch (Exception ignored) {}
    }

    public static boolean isSleepFadeEnabled(Context ctx) {
        try { return effectsPrefs(ctx).getBoolean(PREF_SLEEP_FADE_ENABLED, true); }
        catch (Exception ignored) { return true; }
    }

    public static void setSleepFadeEnabled(Context ctx, boolean enabled) {
        try { effectsPrefs(ctx).edit().putBoolean(PREF_SLEEP_FADE_ENABLED, enabled).apply(); }
        catch (Exception ignored) {}
    }

    public static BackgroundColorMode getBackgroundColorMode(Context ctx) {
        try {
            String raw = effectsPrefs(ctx).getString(PREF_BG_COLOR_MODE_KEY, BackgroundColorMode.THEME.name());
            return BackgroundColorMode.valueOf(raw);
        } catch (Exception ignored) { return BackgroundColorMode.THEME; }
    }

    public static void setBackgroundColorMode(Context ctx, BackgroundColorMode mode) {
        try {
            effectsPrefs(ctx).edit().putString(PREF_BG_COLOR_MODE_KEY, (mode == null ? BackgroundColorMode.THEME : mode).name()).apply();
        } catch (Exception ignored) {}
    }

    public static int getCustomGradientColor1(Context ctx) {
        try { return effectsPrefs(ctx).getInt(PREF_CUSTOM_COLOR1_KEY, DEFAULT_CUSTOM_COLOR1); }
        catch (Exception ignored) { return DEFAULT_CUSTOM_COLOR1; }
    }

    public static void setCustomGradientColor1(Context ctx, int color) {
        try { effectsPrefs(ctx).edit().putInt(PREF_CUSTOM_COLOR1_KEY, color).apply(); }
        catch (Exception ignored) {}
    }

    public static int getCustomGradientColor2(Context ctx) {
        try { return effectsPrefs(ctx).getInt(PREF_CUSTOM_COLOR2_KEY, DEFAULT_CUSTOM_COLOR2); }
        catch (Exception ignored) { return DEFAULT_CUSTOM_COLOR2; }
    }

    public static void setCustomGradientColor2(Context ctx, int color) {
        try { effectsPrefs(ctx).edit().putInt(PREF_CUSTOM_COLOR2_KEY, color).apply(); }
        catch (Exception ignored) {}
    }

    public static float getGradientVibrancy(Context ctx) {
        try {
            float v = effectsPrefs(ctx).getFloat(PREF_VIBRANCY_KEY, DEFAULT_VIBRANCY);
            return Math.max(MIN_VIBRANCY, Math.min(MAX_VIBRANCY, v));
        } catch (Exception ignored) { return DEFAULT_VIBRANCY; }
    }

    public static void setGradientVibrancy(Context ctx, float v) {
        try {
            float clamped = Math.max(MIN_VIBRANCY, Math.min(MAX_VIBRANCY, v));
            effectsPrefs(ctx).edit().putFloat(PREF_VIBRANCY_KEY, clamped).apply();
        } catch (Exception ignored) {}
    }

    public static int applyVibrancy(int color, float vibrancy) {
        if (vibrancy <= 0f) vibrancy = 1f;
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0f, Math.min(1f, hsv[1] * vibrancy));
        hsv[2] = Math.max(0f, Math.min(1f, hsv[2] * (0.96f + (vibrancy - 1f) * 0.08f)));
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    public static int[] buildCustomGradient(int c1, int c2) {
        int mid = blendColors(c1, c2, 0.5f);
        return new int[]{c1, mid, c2, mid, c1};
    }

    public static int[] resolveActiveGradientPalette(Context ctx, @Nullable OfflineTrack track) {
        if (ctx == null) return new int[]{Color.BLACK, Color.WHITE};
        BackgroundColorMode mode = getBackgroundColorMode(ctx);
        float vibrancy = getGradientVibrancy(ctx);
        int[] base;
        switch (mode) {
            case COVER: {
                int dominant = track != null ? track.extractDominantColor(currentTheme.varAccentPrimary) : currentTheme.varAccentPrimary;
                int complement = complementOf(dominant);
                base = buildCustomGradient(dominant, complement);
                break;
            }
            case CUSTOM: base = buildCustomGradient(getCustomGradientColor1(ctx), getCustomGradientColor2(ctx)); break;
            case THEME: default: base = LocalDownloadsAnimator.getThemeGradientPalette(currentTheme); break;
        }
        int[] out = new int[base.length];
        for (int i = 0; i < base.length; i++) out[i] = applyVibrancy(base[i], vibrancy);
        return out;
    }

    public static int[] resolveActiveHaloColors(Context ctx, @Nullable OfflineTrack track) {
        if (ctx == null) return new int[]{currentTheme.varAccentPrimary, currentTheme.varAccentSecondary};
        BackgroundColorMode mode = getBackgroundColorMode(ctx);
        float vibrancy = getGradientVibrancy(ctx);
        int c1, c2;
        switch (mode) {
            case COVER: {
                c1 = track != null ? track.extractDominantColor(currentTheme.varAccentPrimary) : currentTheme.varAccentPrimary;
                c2 = complementOf(c1);
                break;
            }
            case CUSTOM: { c1 = getCustomGradientColor1(ctx); c2 = getCustomGradientColor2(ctx); break; }
            case THEME: default: { int[] t = LocalDownloadsAnimator.getHaloColors(currentTheme); c1 = t[0]; c2 = t[1]; break; }
        }
        return new int[]{applyVibrancy(c1, vibrancy), applyVibrancy(c2, vibrancy)};
    }

    public static int complementOf(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + 180f) % 360f;
        hsv[1] = Math.min(1f, hsv[1] * 0.85f + 0.15f);
        hsv[2] = Math.max(0.35f, Math.min(0.95f, hsv[2] * 1.05f));
        return Color.HSVToColor(hsv);
    }

    public static List<EffectProfile> getSavedEffectProfiles(Context ctx) {
        List<EffectProfile> out = new ArrayList<>();
        try {
            String json = effectsPrefs(ctx).getString(PREF_EFFECT_PROFILES_KEY, "");
            if (json == null || json.isEmpty()) return out;
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                try {
                    BackgroundColorMode mode;
                    try { mode = BackgroundColorMode.valueOf(o.optString("colorMode", BackgroundColorMode.THEME.name())); }
                    catch (Exception e) { mode = BackgroundColorMode.THEME; }

                    out.add(new EffectProfile(
                            o.optString("name", ""), o.optString("themeName", ThemePalette.YT_MUSIC_PINK.name()),
                            o.optBoolean("dynamicColors", true), o.optBoolean("breathing", true),
                            o.optBoolean("halo", true), o.optBoolean("visualizer", false),
                            (float) o.optDouble("speedScale", DEFAULT_ANIMATION_SPEED), (float) o.optDouble("intensity", DEFAULT_ANIMATION_INTENSITY),
                            o.optBoolean("focusMode", false), o.optBoolean("lowPower", false),
                            mode, o.optInt("customColor1", DEFAULT_CUSTOM_COLOR1),
                            o.optInt("customColor2", DEFAULT_CUSTOM_COLOR2), (float) o.optDouble("vibrancy", DEFAULT_VIBRANCY)
                    ));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static boolean saveEffectProfile(Context ctx, String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String trimmed = name.trim();
        List<EffectProfile> profiles = getSavedEffectProfiles(ctx);
        profiles.removeIf(p -> p.name().equalsIgnoreCase(trimmed));
        profiles.add(new EffectProfile(
                trimmed, currentTheme.name(), isPlayerDynamicColorsEnabled(ctx), isPlayerCoverBreathingEnabled(ctx),
                isHaloEnabled(ctx), isVisualizerEnabled(ctx), getAnimationSpeedScale(ctx), getAnimationIntensity(ctx),
                isFocusMode(ctx), isLowPowerMode(ctx), getBackgroundColorMode(ctx), getCustomGradientColor1(ctx),
                getCustomGradientColor2(ctx), getGradientVibrancy(ctx)
        ));
        writeProfiles(ctx, profiles);
        return true;
    }

    public static boolean deleteEffectProfile(Context ctx, String name) {
        if (name == null || name.trim().isEmpty()) return false;
        List<EffectProfile> profiles = getSavedEffectProfiles(ctx);
        boolean removed = profiles.removeIf(p -> p.name().equalsIgnoreCase(name.trim()));
        if (removed) writeProfiles(ctx, profiles);
        return removed;
    }

    public static boolean applyEffectProfile(Context ctx, EffectProfile profile) {
        if (profile == null || ctx == null) return false;
        try {
            ThemePalette theme;
            try { theme = ThemePalette.valueOf(profile.themeName()); }
            catch (Exception ignored) { theme = currentTheme; }
            currentTheme = theme;
            SharedPreferences.Editor e = effectsPrefs(ctx).edit();
            e.putString(PREF_THEME_KEY, currentTheme.name());
            e.putBoolean("player_dynamic_artwork_colors", profile.dynamicColors());
            e.putBoolean("player_cover_breathing", profile.breathing());
            e.putBoolean(PREF_HALO_ENABLED_KEY, profile.halo());
            e.putBoolean(PREF_VISUALIZER_ENABLED_KEY, profile.visualizer());
            e.putFloat(PREF_ANIMATION_SPEED_KEY, Math.max(MIN_ANIMATION_SPEED, Math.min(MAX_ANIMATION_SPEED, profile.speedScale())));
            e.putFloat(PREF_ANIMATION_INTENSITY_KEY, Math.max(MIN_ANIMATION_INTENSITY, Math.min(MAX_ANIMATION_INTENSITY, profile.intensity())));
            e.putBoolean(PREF_FOCUS_MODE_KEY, profile.focusMode());
            e.putBoolean(PREF_LOW_POWER_MODE_KEY, profile.lowPower());
            e.putString(PREF_BG_COLOR_MODE_KEY, (profile.colorMode() == null ? BackgroundColorMode.THEME : profile.colorMode()).name());
            e.putInt(PREF_CUSTOM_COLOR1_KEY, profile.customColor1());
            e.putInt(PREF_CUSTOM_COLOR2_KEY, profile.customColor2());
            e.putFloat(PREF_VIBRANCY_KEY, Math.max(MIN_VIBRANCY, Math.min(MAX_VIBRANCY, profile.vibrancy())));
            e.apply();
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static void writeProfiles(Context ctx, List<EffectProfile> profiles) {
        try {
            JSONArray arr = new JSONArray();
            for (EffectProfile p : profiles) {
                JSONObject o = new JSONObject();
                o.put("name", p.name());
                o.put("themeName", p.themeName());
                o.put("dynamicColors", p.dynamicColors());
                o.put("breathing", p.breathing());
                o.put("halo", p.halo());
                o.put("visualizer", p.visualizer());
                o.put("speedScale", (double) p.speedScale());
                o.put("intensity", (double) p.intensity());
                o.put("focusMode", p.focusMode());
                o.put("lowPower", p.lowPower());
                o.put("colorMode", (p.colorMode() == null ? BackgroundColorMode.THEME : p.colorMode()).name());
                o.put("customColor1", p.customColor1());
                o.put("customColor2", p.customColor2());
                o.put("vibrancy", (double) p.vibrancy());
                arr.put(o);
            }
            effectsPrefs(ctx).edit().putString(PREF_EFFECT_PROFILES_KEY, arr.toString()).apply();
        } catch (JSONException ignored) {} catch (Exception ignored) {}
    }
}