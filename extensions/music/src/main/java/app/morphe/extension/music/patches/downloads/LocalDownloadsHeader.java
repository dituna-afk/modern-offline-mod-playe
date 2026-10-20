package app.morphe.extension.music.patches.downloads;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.music.settings.Settings;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.search.BaseSearchViewController;
import app.morphe.extension.shared.ui.CustomDialog;

public final class LocalDownloadsHeader {
    private static final List<TextView> BOTTOM_TAB_ICONS = new ArrayList<>();
    private static final List<TextView> BOTTOM_TAB_LABELS = new ArrayList<>();

    private LocalDownloadsHeader() {}

    public static LinearLayout createHeader(Context ctx, LocalDownloadsFragment fragment) {
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(LocalDownloadsUIState.currentTheme.varBgTop);
        header.setClickable(true);

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(-1, -2);
        headerLp.topMargin = 0;
        header.setLayoutParams(headerLp);
        header.setPadding(LocalDownloadsUIState.dp(ctx, 14), LocalDownloadsUIState.dp(ctx, 8), LocalDownloadsUIState.dp(ctx, 14), LocalDownloadsUIState.dp(ctx, 6));
        LocalDownloadsUIState.headerLayout = header;

        LocalDownloadsUIState.searchBar = CustomDialog.createSearchBar(ctx, str("morphe_music_downloads_search_hint"), text -> {
            LocalDownloadsUIState.query = text.trim().toLowerCase(Locale.ROOT);
            fragment.showTracks();
        });
        LocalDownloadsUIState.searchBar.setSingleLine(true);
        LocalDownloadsUIState.searchBar.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LocalDownloadsUIState.searchBar.setOnEditorActionListener((view, actionId, event) -> {
            boolean searchAction = actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP);
            if (!searchAction) return false;
            LocalDownloadsUIState.query = view.getText().toString().trim().toLowerCase(Locale.ROOT);
            fragment.showTracks();
            LocalDownloadsUIState.finishSearchInput(ctx);
            return true;
        });
        LocalDownloadsUIState.searchBar.setVisibility(View.GONE);
        LocalDownloadsUIState.searchBar.setAlpha(0f);
        header.addView(LocalDownloadsUIState.searchBar, new LinearLayout.LayoutParams(-1, 0));

        LinearLayout line = new LinearLayout(ctx);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, LocalDownloadsUIState.dp(ctx, 4), 0, LocalDownloadsUIState.dp(ctx, 4));

        LocalDownloadsUIState.backToLibraryBtn = LocalDownloadsUIState.createPillButton(ctx, "← Geri", false);
        LocalDownloadsUIState.backToLibraryBtn.setVisibility(View.GONE);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(-2, -2);
        backLp.setMargins(0, 0, LocalDownloadsUIState.dp(ctx, 10), 0);
        LocalDownloadsUIState.backToLibraryBtn.setLayoutParams(backLp);
        LocalDownloadsUIState.backToLibraryBtn.setOnClickListener(v -> {
            LocalDownloadsUIState.selectedPlaylist = null;
            LocalDownloadsUIState.selectedArtist = null;
            LocalDownloadsUIState.showingArtists = false;
            LocalDownloadsUIState.showingPlaylistsOverview = true;
            LocalDownloadsUIState.currentSmartFilter = LocalDownloadsUIState.SmartFilterMode.NONE;
            LocalDownloadsUIState.currentFilterMode = LocalDownloadsUIState.FilterMode.ALL;
            LocalDownloadsUIState.selectionMode = false;
            LocalDownloadsUIState.selectedVideoIds.clear();
            LocalDownloadsUIState.animateNextSongList = true;
            fragment.showTracks();
        });
        line.addView(LocalDownloadsUIState.backToLibraryBtn);

        LocalDownloadsUIState.summary = LocalDownloadsUIState.text(ctx, "", 14, LocalDownloadsUIState.primaryTextColor());
        LocalDownloadsUIState.summary.setTypeface(Typeface.DEFAULT_BOLD);
        LocalDownloadsUIState.summary.setSingleLine(true);
        LocalDownloadsUIState.summary.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(LocalDownloadsUIState.summary, new LinearLayout.LayoutParams(0, -2, 1f));

        LocalDownloadsUIState.selectModeBtn = LocalDownloadsUIState.icon(ctx, LocalDownloadsUIState.createVectorIconDrawable(ctx, LocalDownloadsUIState.ICON_CHECKLIST, LocalDownloadsUIState.primaryTextColor(), 0), "Toplu Seçim");
        LocalDownloadsUIState.selectModeBtn.setOnClickListener(v -> {
            LocalDownloadsUIState.selectionMode = !LocalDownloadsUIState.selectionMode;
            if (!LocalDownloadsUIState.selectionMode) LocalDownloadsUIState.selectedVideoIds.clear();
            fragment.showTracks();
        });
        line.addView(LocalDownloadsUIState.selectModeBtn, new LinearLayout.LayoutParams(LocalDownloadsUIState.dp(ctx, 38), LocalDownloadsUIState.dp(ctx, 38)));

        LocalDownloadsUIState.viewModeBtn = LocalDownloadsUIState.icon(ctx, LocalDownloadsUIState.createVectorIconDrawable(ctx, LocalDownloadsUIState.playlistGridMode ? LocalDownloadsUIState.ICON_GRID : LocalDownloadsUIState.ICON_LIST, LocalDownloadsUIState.primaryTextColor(), 0), "Görünüm Değiştir");
        LocalDownloadsUIState.viewModeBtn.setOnClickListener(v -> {
            LocalDownloadsUIState.playlistGridMode = !LocalDownloadsUIState.playlistGridMode;
            LocalDownloadsUIState.savePreferences(ctx);
            LocalDownloadsUIState.viewModeBtn.setImageDrawable(LocalDownloadsUIState.createVectorIconDrawable(ctx, LocalDownloadsUIState.playlistGridMode ? LocalDownloadsUIState.ICON_GRID : LocalDownloadsUIState.ICON_LIST, LocalDownloadsUIState.primaryTextColor(), 0));
            Utils.showToastShort(LocalDownloadsUIState.playlistGridMode ? "Izgara (Grid) Görünümü" : "Liste Görünümü");
            fragment.showTracks();
        });
        line.addView(LocalDownloadsUIState.viewModeBtn, new LinearLayout.LayoutParams(LocalDownloadsUIState.dp(ctx, 38), LocalDownloadsUIState.dp(ctx, 38)));

        ImageButton search = LocalDownloadsUIState.icon(ctx, BaseSearchViewController.getSearchIconDrawable(), str("morphe_music_downloads_search_hint"));
        search.setOnClickListener(v -> LocalDownloadsUIState.toggleSearch(ctx));
        line.addView(search, new LinearLayout.LayoutParams(LocalDownloadsUIState.dp(ctx, 38), LocalDownloadsUIState.dp(ctx, 38)));

        LocalDownloadsUIState.headerFilterBtn = LocalDownloadsUIState.icon(ctx, LocalDownloadsUIState.createVectorIconDrawable(ctx, LocalDownloadsUIState.ICON_FILTER, LocalDownloadsUIState.activeAccentColor(), 0), "Akıllı Otomatik Filtreler");
        LocalDownloadsUIState.headerFilterBtn.setOnClickListener(v -> showSmartFilterMenu(ctx, LocalDownloadsUIState.headerFilterBtn, fragment));
        line.addView(LocalDownloadsUIState.headerFilterBtn, new LinearLayout.LayoutParams(LocalDownloadsUIState.dp(ctx, 38), LocalDownloadsUIState.dp(ctx, 38)));

        LocalDownloadsUIState.headerSettingsBtn = LocalDownloadsUIState.icon(ctx, LocalDownloadsUIState.createVectorIconDrawable(ctx, LocalDownloadsUIState.ICON_SETTINGS, LocalDownloadsUIState.activeAccentColor(), 0), "Ayarlar");
        LocalDownloadsUIState.headerSettingsBtn.setOnClickListener(v -> fragment.showSettingsDialog());
        line.addView(LocalDownloadsUIState.headerSettingsBtn, new LinearLayout.LayoutParams(LocalDownloadsUIState.dp(ctx, 38), LocalDownloadsUIState.dp(ctx, 38)));

        header.addView(line, new LinearLayout.LayoutParams(-1, -2));
        return header;
    }

    public static void showSmartFilterMenu(Context ctx, View anchor, LocalDownloadsFragment fragment) {
        String[] items = {"⚡ En Çok Dinlenenler", "🕘 Son İndirilenler", "🌙 Gece Dinlenenler", "◌ Hiç Dinlenmeyenler", "🎵 Tüm Şarkılar", "↕ Sırala…"};
        LocalDownloadsUIState.showMenu(ctx, anchor, items, -1, pos -> {
            LocalDownloadsUIState.showingArtists = false;
            LocalDownloadsUIState.selectedPlaylist = null;
            LocalDownloadsUIState.selectedArtist = null;
            LocalDownloadsUIState.showingPlaylistsOverview = false;
            LocalDownloadsUIState.currentFilterMode = LocalDownloadsUIState.FilterMode.ALL;
            LocalDownloadsUIState.diskReloadNeeded = true;
            LocalDownloadsUIState.query = "";
            if (LocalDownloadsUIState.searchBar != null) LocalDownloadsUIState.searchBar.setText("");
            switch (pos) {
                case 0 -> LocalDownloadsUIState.currentSmartFilter = LocalDownloadsUIState.SmartFilterMode.MOST_PLAYED;
                case 1 -> LocalDownloadsUIState.currentSmartFilter = LocalDownloadsUIState.SmartFilterMode.RECENTLY_ADDED;
                case 2 -> LocalDownloadsUIState.currentSmartFilter = LocalDownloadsUIState.SmartFilterMode.NIGHT_PLAYS;
                case 3 -> LocalDownloadsUIState.currentSmartFilter = LocalDownloadsUIState.SmartFilterMode.NEVER_PLAYED;
                case 4 -> { LocalDownloadsUIState.currentSmartFilter = LocalDownloadsUIState.SmartFilterMode.NONE; LocalDownloadsUIState.showingPlaylistsOverview = false; }
                case 5 -> { showSortMenu(ctx, anchor, fragment); return; }
            }
            LocalDownloadsUIState.animateNextSongList = true;
            fragment.showTracks();
            refreshBottomNavigationTheme(ctx);
        });
    }

    public static void showSortMenu(Context ctx, View anchor, LocalDownloadsFragment fragment) {
        String[] items = {"📅 Son İndirilenler (En Yeni)", str("morphe_music_downloads_sort_artist"), str("morphe_music_downloads_sort_title"), str("morphe_music_downloads_sort_size")};
        LocalDownloadsUIState.showMenu(ctx, anchor, items, LocalDownloadsUIState.SortOrder.saved().ordinal(), pos -> {
            Settings.DOWNLOADS_SORT.save(LocalDownloadsUIState.SortOrder.values()[pos].name());
            fragment.showTracks();
        });
    }

    public static View createBottomNavigationBar(Context ctx, LocalDownloadsFragment fragment) {
        LinearLayout nav = new LinearLayout(ctx);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(LocalDownloadsUIState.currentTheme.varBgBottom);
        LocalDownloadsUIState.bottomNavLayout = nav;

        BOTTOM_TAB_ICONS.clear();
        BOTTOM_TAB_LABELS.clear();

        String[] labels = {"Ana Sayfa", "Ara", "Kitaplık", "Sanatçılar", "Ayarlar"};
        String[] icons = {"🏠", "🔍", "📚", "🎤", "⚙️"};

        for (int i = 0; i < labels.length; i++) {
            final int tabIndex = i;
            boolean active = (i == 2 && !LocalDownloadsUIState.showingArtists) || (i == 3 && LocalDownloadsUIState.showingArtists);

            LinearLayout tab = new LinearLayout(ctx);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, LocalDownloadsUIState.dp(ctx, 6), 0, LocalDownloadsUIState.dp(ctx, 6));
            tab.setBackground(LocalDownloadsUIState.createCircleRipple());

            TextView ic = LocalDownloadsUIState.text(ctx, icons[i], 16, active ? LocalDownloadsUIState.activeAccentColor() : LocalDownloadsUIState.secondary());
            ic.setGravity(Gravity.CENTER);

            TextView lb = LocalDownloadsUIState.text(ctx, labels[i], 11, active ? LocalDownloadsUIState.primaryTextColor() : LocalDownloadsUIState.secondary());
            lb.setGravity(Gravity.CENTER);
            if (active) lb.setTypeface(Typeface.DEFAULT_BOLD);

            BOTTOM_TAB_ICONS.add(ic);
            BOTTOM_TAB_LABELS.add(lb);

            tab.addView(ic);
            tab.addView(lb);
            tab.setOnClickListener(v -> {
                if (tabIndex == 0) {
                    Activity activity = fragment.getActivity();
                    if (activity == null && ctx instanceof Activity a) activity = a;
                    if (activity != null) { OfflinePlaybackService.stop(activity); OfflineIntegrationPatch.exitOfflineFragment(activity); }
                } else if (tabIndex == 1) {
                    LocalDownloadsUIState.toggleSearch(ctx);
                } else if (tabIndex == 2) {
                    LocalDownloadsUIState.showingArtists = false;
                    LocalDownloadsUIState.currentSmartFilter = LocalDownloadsUIState.SmartFilterMode.NONE;
                    LocalDownloadsUIState.selectedPlaylist = null;
                    LocalDownloadsUIState.selectedArtist = null;
                    LocalDownloadsUIState.showingPlaylistsOverview = true;
                    LocalDownloadsUIState.currentFilterMode = LocalDownloadsUIState.FilterMode.ALL;
                    LocalDownloadsUIState.animateNextSongList = true;
                    fragment.showTracks();
                    refreshBottomNavigationTheme(ctx);
                } else if (tabIndex == 3) {
                    LocalDownloadsUIState.showingArtists = true;
                    LocalDownloadsUIState.currentSmartFilter = LocalDownloadsUIState.SmartFilterMode.NONE;
                    LocalDownloadsUIState.selectedPlaylist = null;
                    LocalDownloadsUIState.selectedArtist = null;
                    LocalDownloadsUIState.showingPlaylistsOverview = false;
                    LocalDownloadsUIState.currentFilterMode = LocalDownloadsUIState.FilterMode.ALL;
                    LocalDownloadsUIState.query = "";
                    if (LocalDownloadsUIState.searchBar != null) LocalDownloadsUIState.searchBar.setText("");
                    LocalDownloadsUIState.diskReloadNeeded = true;
                    LocalDownloadsUIState.TRACK_CACHE.clear();
                    LocalDownloadsUIState.animateNextSongList = true;
                    fragment.showTracks();
                    refreshBottomNavigationTheme(ctx);
                } else {
                    fragment.showSettingsDialog();
                }
            });
            nav.addView(tab, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        return nav;
    }

    public static void refreshBottomNavigationTheme(Context ctx) {
        if (LocalDownloadsUIState.bottomNavLayout != null) {
            LocalDownloadsAnimator.animateBackgroundColorChange(LocalDownloadsUIState.bottomNavLayout, LocalDownloadsUIState.currentTheme.varBgBottom);
        }
        for (int i = 0; i < BOTTOM_TAB_ICONS.size() && i < BOTTOM_TAB_LABELS.size(); i++) {
            boolean active = (i == 2 && !LocalDownloadsUIState.showingArtists) || (i == 3 && LocalDownloadsUIState.showingArtists);
            BOTTOM_TAB_ICONS.get(i).setTextColor(active ? LocalDownloadsUIState.activeAccentColor() : LocalDownloadsUIState.secondary());
            BOTTOM_TAB_LABELS.get(i).setTextColor(active ? LocalDownloadsUIState.primaryTextColor() : LocalDownloadsUIState.secondary());
            BOTTOM_TAB_LABELS.get(i).setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }
}