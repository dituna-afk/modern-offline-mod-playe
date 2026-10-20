package app.morphe.extension.music.patches.downloads;

import android.app.ActionBar;
import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OfflineHeaderBlocker {

    private static final Map<View, SavedViewState> HIDDEN_VIEWS = new IdentityHashMap<>();
    private static final Map<View, Boolean> HOOKED_DOWNLOAD_TABS = new IdentityHashMap<>();

    private static ViewTreeObserver.OnGlobalLayoutListener offlineLayoutListener;
    private static ViewTreeObserver.OnGlobalLayoutListener onlineInterceptorListener;
    private static View observedDecor;
    private static View observedOnlineDecor;
    private static Window.Callback originalWindowCallback;
    private static Window hookedWindow;
    private static boolean offlineScreenActive = false;
    private static View offlineRootLayout;
    private static Runnable immediateSweepRunnable;
    private static Runnable delayedSweep120Runnable;
    private static Runnable delayedSweep320Runnable;

    private static final class SavedViewState {
        final int visibility;
        final int height;
        final int topMargin;
        final int bottomMargin;
        final int paddingTop;
        final int paddingBottom;

        SavedViewState(int visibility, int height, int topMargin, int bottomMargin, int paddingTop, int paddingBottom) {
            this.visibility = visibility;
            this.height = height;
            this.topMargin = topMargin;
            this.bottomMargin = bottomMargin;
            this.paddingTop = paddingTop;
            this.paddingBottom = paddingBottom;
        }
    }

    private OfflineHeaderBlocker() {}

    /**
     * Online moddayken "İndirilenler / Downloads" çipine basıldığında
     * YT Music'in o çipi seçili bırakmasını engeller ve doğrudan Offline oynatıcıyı açar.
     */
    public static void installOnlineDownloadsInterceptor(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        View decor = activity.getWindow().getDecorView();
        if (decor == null) return;

        if (observedOnlineDecor != decor) {
            if (observedOnlineDecor != null && onlineInterceptorListener != null) {
                try {
                    observedOnlineDecor.getViewTreeObserver().removeOnGlobalLayoutListener(onlineInterceptorListener);
                } catch (Exception ignored) {}
            }
            observedOnlineDecor = decor;
            onlineInterceptorListener = () -> {
                if (!offlineScreenActive && decor instanceof ViewGroup dg) {
                    hookOnlineDownloadsButtons(activity, dg);
                }
            };
            decor.getViewTreeObserver().addOnGlobalLayoutListener(onlineInterceptorListener);
        }
        if (decor instanceof ViewGroup dg) {
            hookOnlineDownloadsButtons(activity, dg);
        }
    }

    private static void hookOnlineDownloadsButtons(Activity activity, ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;

            if ("morphe_pivot_offline_tab".equals(child.getTag())) continue;

            if (child instanceof TextView tv && isDownloadsCategoryText(tv.getText())) {
                View clickableParent = findClickableContainer(tv);
                if (clickableParent != null && !HOOKED_DOWNLOAD_TABS.containsKey(clickableParent)) {
                    HOOKED_DOWNLOAD_TABS.put(clickableParent, Boolean.TRUE);
                    clickableParent.setOnTouchListener((v, event) -> {
                        if (offlineScreenActive) return false;
                        int action = event.getActionMasked();
                        if (action == MotionEvent.ACTION_DOWN) {
                            if (v.getParent() != null) {
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            v.setPressed(false);
                            v.setSelected(false);
                            return true;
                        }
                        if (action == MotionEvent.ACTION_UP) {
                            v.setPressed(false);
                            v.setSelected(false);
                            clearOnlineDownloadsChipSelection(activity);
                            OfflineIntegrationPatch.openOfflineFragment(activity);
                            return true;
                        }
                        return true;
                    });
                }
            } else if (child instanceof ViewGroup vg) {
                hookOnlineDownloadsButtons(activity, vg);
            }
        }
    }

    /**
     * Çevrimdışı moddan Ana Sayfa düğmesiyle çıkıldığında:
     * 1. Kitaplık'taki "İndirilenler" çipinin solundaki 'X' (kapat) butonuna basarak seçimi kaldırır.
     * 2. YT Music'in alt barındaki ilk sekmeye (Ana Sayfa) tıklayarak gerçek Ana Sayfa'ya döner.
     */
    public static void resetOnlineChipAndNavigateHome(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup decorGroup)) return;

        decor.post(() -> {
            try {
                clearOnlineDownloadsChipSelection(activity);
                selectHostHomeTab(decorGroup);
            } catch (Exception ignored) {}
        });
    }

    private static void clearOnlineDownloadsChipSelection(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup decorGroup)) return;

        List<TextView> downloadChipTexts = new ArrayList<>();
        findTextViewsByText(decorGroup, downloadChipTexts);

        for (TextView tv : downloadChipTexts) {
            tv.setSelected(false);
            tv.setActivated(false);
            View chipContainer = findClickableContainer(tv);
            if (chipContainer != null) {
                chipContainer.setSelected(false);
                chipContainer.setActivated(false);
                chipContainer.setPressed(false);

                // Çip şeridinde "İndirilenler"in hemen solundaki 'X' (Filtreyi temizle) butonunu bul ve tıkla
                ViewParent stripParent = chipContainer.getParent();
                if (stripParent instanceof ViewGroup chipStrip) {
                    for (int i = 0; i < chipStrip.getChildCount(); i++) {
                        View sibling = chipStrip.getChildAt(i);
                        if (sibling != null && sibling != chipContainer && sibling.getVisibility() == View.VISIBLE) {
                            if (sibling.getWidth() > 0 && sibling.getWidth() <= dp(activity, 56)) {
                                sibling.performClick();
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private static void findTextViewsByText(ViewGroup group, List<TextView> out) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == null) continue;
            if (child instanceof TextView tv && isDownloadsCategoryText(tv.getText())) {
                out.add(tv);
            } else if (child instanceof ViewGroup vg) {
                findTextViewsByText(vg, out);
            }
        }
    }

    private static void selectHostHomeTab(ViewGroup decorGroup) {
        ViewGroup pivotBar = findHostPivotBar(decorGroup);
        if (pivotBar != null && pivotBar.getChildCount() > 0) {
            for (int i = 0; i < pivotBar.getChildCount(); i++) {
                View tab = pivotBar.getChildAt(i);
                if (tab != null && !"morphe_pivot_offline_tab".equals(tab.getTag()) && tab.isClickable()) {
                    tab.performClick();
                    return;
                }
            }
            View first = pivotBar.getChildAt(0);
            if (first != null) first.performClick();
        }
    }

    private static ViewGroup findHostPivotBar(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == null) continue;
            String idName = getResourceName(child);
            String cls = child.getClass().getName().toLowerCase(Locale.ROOT);
            if ((idName.contains("pivot_bar") || cls.contains("pivotbar")) && child instanceof ViewGroup vg) {
                return vg;
            }
            if (child instanceof ViewGroup vg) {
                ViewGroup found = findHostPivotBar(vg);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean isDownloadsCategoryText(CharSequence cs) {
        if (cs == null) return false;
        String text = cs.toString().trim().toLowerCase(Locale.ROOT);
        return text.equals("indirilenler") || text.equals("downloads") || text.equals("indirmeler");
    }

    private static View findClickableContainer(View view) {
        View current = view;
        int depth = 0;
        while (current != null && depth < 5) {
            if (current.isClickable() || current.hasOnClickListeners()) {
                return current;
            }
            ViewParent p = current.getParent();
            current = (p instanceof View v) ? v : null;
            depth++;
        }
        return view;
    }

    /**
     * Bildirim çubuğunu (Status Bar) gizler veya geri açar.
     * Kullanıcı ekranın en üstünden aşağı kaydırırsa geçici olarak görünür.
     */
    @SuppressWarnings("deprecation")
    public static void applyImmersiveFullscreen(Window window, boolean fullscreen) {
        if (window == null) return;
        try {
            if (fullscreen) {
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowInsetsController controller = window.getInsetsController();
                    if (controller != null) {
                        controller.hide(WindowInsets.Type.statusBars());
                        controller.setSystemBarsBehavior(
                                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    }
                }
                View decor = window.getDecorView();
                if (decor != null) {
                    decor.setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    );
                }
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowInsetsController controller = window.getInsetsController();
                    if (controller != null) {
                        controller.show(WindowInsets.Type.statusBars());
                    }
                }
                View decor = window.getDecorView();
                if (decor != null) {
                    decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                }
            }
        } catch (Exception ignored) {}
    }

    public static void attach(Activity activity, View customRootLayout) {
        if (activity == null || customRootLayout == null) return;
        offlineScreenActive = true;
        if (offlineRootLayout != customRootLayout) {
            removePendingSweeps();
            offlineRootLayout = customRootLayout;
        } else {
            removePendingSweeps();
        }

        applyImmersiveFullscreen(activity.getWindow(), true);

        customRootLayout.setVisibility(View.VISIBLE);
        customRootLayout.setAlpha(1f);
        customRootLayout.setElevation(100f);
        customRootLayout.setTranslationZ(100f);

        // Tam ekran modunda bildirim paneli gizlendiği için üst ve alt boşlukları tamamen sıfırla
        customRootLayout.setFitsSystemWindows(false);
        customRootLayout.setPadding(0, 0, 0, 0);
        customRootLayout.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, 0, 0, 0);
            return insets;
        });

        installHardBackButtonLock(activity);

        runSweep(activity, customRootLayout);
        immediateSweepRunnable = () -> {
            if (isCurrentOfflineRoot(activity, customRootLayout)) {
                runSweep(activity, customRootLayout);
            }
        };
        delayedSweep120Runnable = () -> {
            if (isCurrentOfflineRoot(activity, customRootLayout)) {
                runSweep(activity, customRootLayout);
            }
        };
        delayedSweep320Runnable = () -> {
            if (isCurrentOfflineRoot(activity, customRootLayout)) {
                runSweep(activity, customRootLayout);
            }
        };
        customRootLayout.post(immediateSweepRunnable);
        customRootLayout.postDelayed(delayedSweep120Runnable, 120);
        customRootLayout.postDelayed(delayedSweep320Runnable, 320);

        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (decor != null && observedDecor != decor) {
            detachOfflineListenerOnly();
            observedDecor = decor;
            offlineLayoutListener = () -> {
                if (offlineScreenActive && customRootLayout.isAttachedToWindow()) {
                    if (customRootLayout.getVisibility() != View.VISIBLE) {
                        customRootLayout.setVisibility(View.VISIBLE);
                    }
                    applyImmersiveFullscreen(activity.getWindow(), true);
                    runSweep(activity, customRootLayout);
                }
            };
            decor.getViewTreeObserver().addOnGlobalLayoutListener(offlineLayoutListener);
        }
    }

    public static void detach(Activity activity) {
        offlineScreenActive = false;
        removePendingSweeps();
        offlineRootLayout = null;
        detachOfflineListenerOnly();
        removeHardBackButtonLock();

        if (activity != null) {
            applyImmersiveFullscreen(activity.getWindow(), false);
        }

        for (Map.Entry<View, SavedViewState> entry : new IdentityHashMap<>(HIDDEN_VIEWS).entrySet()) {
            View v = entry.getKey();
            SavedViewState st = entry.getValue();
            if (v == null || st == null) continue;
            try {
                v.setVisibility(st.visibility);
                v.setPadding(v.getPaddingLeft(), st.paddingTop, v.getPaddingRight(), st.paddingBottom);
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                if (lp != null) {
                    lp.height = st.height;
                    if (lp instanceof ViewGroup.MarginLayoutParams mlp) {
                        mlp.topMargin = st.topMargin;
                        mlp.bottomMargin = st.bottomMargin;
                    }
                    v.setLayoutParams(lp);
                }
            } catch (Exception ignored) {}
        }
        HIDDEN_VIEWS.clear();
    }

    private static void installHardBackButtonLock(Activity activity) {
        Window window = activity.getWindow();
        if (window == null || hookedWindow == window) return;
        removeHardBackButtonLock();

        Window.Callback current = window.getCallback();
        if (current == null) return;
        final Window.Callback delegate = current;
        originalWindowCallback = delegate;
        hookedWindow = window;

        window.setCallback(new Window.Callback() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (offlineScreenActive && event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        OfflineIntegrationPatch.dispatchGuardedBack(activity);
                    }
                    return true;
                }
                return delegate.dispatchKeyEvent(event);
            }

            @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) { return delegate.dispatchKeyShortcutEvent(event); }
            @Override public boolean dispatchTouchEvent(MotionEvent event) { return delegate.dispatchTouchEvent(event); }
            @Override public boolean dispatchTrackballEvent(MotionEvent event) { return delegate.dispatchTrackballEvent(event); }
            @Override public boolean dispatchGenericMotionEvent(MotionEvent event) { return delegate.dispatchGenericMotionEvent(event); }
            @Override public boolean dispatchPopulateAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) { return delegate.dispatchPopulateAccessibilityEvent(event); }
            @Override public View onCreatePanelView(int featureId) { return delegate.onCreatePanelView(featureId); }
            @Override public boolean onCreatePanelMenu(int featureId, android.view.Menu menu) { return delegate.onCreatePanelMenu(featureId, menu); }
            @Override public boolean onPreparePanel(int featureId, View view, android.view.Menu menu) { return delegate.onPreparePanel(featureId, view, menu); }
            @Override public boolean onMenuOpened(int featureId, android.view.Menu menu) { return delegate.onMenuOpened(featureId, menu); }
            @Override public boolean onMenuItemSelected(int featureId, android.view.MenuItem item) { return delegate.onMenuItemSelected(featureId, item); }
            @Override public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) { delegate.onWindowAttributesChanged(attrs); }
            @Override public void onContentChanged() { delegate.onContentChanged(); }
            @Override
            public void onWindowFocusChanged(boolean hasFocus) {
                if (offlineScreenActive && hasFocus) {
                    applyImmersiveFullscreen(window, true);
                }
                delegate.onWindowFocusChanged(hasFocus);
            }
            @Override public void onAttachedToWindow() { delegate.onAttachedToWindow(); }
            @Override public void onDetachedFromWindow() { delegate.onDetachedFromWindow(); }
            @Override public void onPanelClosed(int featureId, android.view.Menu menu) { delegate.onPanelClosed(featureId, menu); }
            @Override public boolean onSearchRequested() { return delegate.onSearchRequested(); }
            @Override public boolean onSearchRequested(android.view.SearchEvent searchEvent) { return delegate.onSearchRequested(searchEvent); }
            @Override public android.view.ActionMode onWindowStartingActionMode(android.view.ActionMode.Callback callback) { return delegate.onWindowStartingActionMode(callback); }
            @Override public android.view.ActionMode onWindowStartingActionMode(android.view.ActionMode.Callback callback, int type) { return delegate.onWindowStartingActionMode(callback, type); }
            @Override public void onActionModeStarted(android.view.ActionMode mode) { delegate.onActionModeStarted(mode); }
            @Override public void onActionModeFinished(android.view.ActionMode mode) { delegate.onActionModeFinished(mode); }
        });
    }

    private static void removeHardBackButtonLock() {
        if (hookedWindow != null && originalWindowCallback != null) {
            try {
                hookedWindow.setCallback(originalWindowCallback);
            } catch (Exception ignored) {}
        }
        hookedWindow = null;
        originalWindowCallback = null;
    }

    private static boolean isCurrentOfflineRoot(Activity activity, View root) {
        return offlineScreenActive
                && offlineRootLayout == root
                && activity != null
                && !activity.isFinishing()
                && root != null
                && root.isAttachedToWindow();
    }

    private static void removePendingSweeps() {
        View root = offlineRootLayout;
        if (root != null) {
            if (immediateSweepRunnable != null) root.removeCallbacks(immediateSweepRunnable);
            if (delayedSweep120Runnable != null) root.removeCallbacks(delayedSweep120Runnable);
            if (delayedSweep320Runnable != null) root.removeCallbacks(delayedSweep320Runnable);
        }
        immediateSweepRunnable = null;
        delayedSweep120Runnable = null;
        delayedSweep320Runnable = null;
    }

    private static void detachOfflineListenerOnly() {
        if (observedDecor != null && offlineLayoutListener != null) {
            try {
                observedDecor.getViewTreeObserver().removeOnGlobalLayoutListener(offlineLayoutListener);
            } catch (Exception ignored) {}
        }
        observedDecor = null;
        offlineLayoutListener = null;
    }

    private static void runSweep(Activity activity, View customRoot) {
        try {
            ActionBar ab = activity.getActionBar();
            if (ab != null && ab.isShowing()) ab.hide();

            View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
            if (!(decor instanceof ViewGroup decorGroup)) return;

            View content = activity.findViewById(android.R.id.content);
            if (content != null) {
                if (content.getPaddingBottom() != 0 || content.getPaddingTop() != 0) {
                    saveStateIfNeeded(content);
                    content.setPadding(0, 0, 0, 0);
                }
                ViewGroup.LayoutParams clp = content.getLayoutParams();
                if (clp instanceof ViewGroup.MarginLayoutParams cmlp) {
                    if (cmlp.topMargin != 0 || cmlp.bottomMargin != 0) {
                        saveStateIfNeeded(content);
                        cmlp.topMargin = 0;
                        cmlp.bottomMargin = 0;
                        content.setLayoutParams(cmlp);
                    }
                }
            }

            int maxHeaderBottomY = dp(activity, 145);
            int screenH = activity.getResources().getDisplayMetrics().heightPixels;

            List<View> toCollapse = new ArrayList<>();
            scanHierarchyForHostBars(decorGroup, customRoot, maxHeaderBottomY, screenH, toCollapse);

            for (View target : toCollapse) {
                if (target == customRoot || isAncestorOf(target, customRoot)) continue;
                collapseSingleView(target);
            }
        } catch (Exception ignored) {}
    }

    private static void scanHierarchyForHostBars(ViewGroup group, View customRoot,
                                                 int maxHeaderBottomY, int screenH, List<View> out) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == null || child == customRoot || isDescendantOf(child, customRoot)) continue;
            if (isSystemBar(child)) continue;

            if (isAncestorOf(child, customRoot)) {
                if (child.getPaddingBottom() > 0 || child.getPaddingTop() > 0) {
                    saveStateIfNeeded(child);
                    child.setPadding(child.getPaddingLeft(), 0, child.getPaddingRight(), 0);
                }
                ViewGroup.LayoutParams alp = child.getLayoutParams();
                if (alp instanceof ViewGroup.MarginLayoutParams amlp) {
                    if (amlp.topMargin > 0 || amlp.bottomMargin > 0) {
                        saveStateIfNeeded(child);
                        amlp.topMargin = 0;
                        amlp.bottomMargin = 0;
                        child.setLayoutParams(amlp);
                    }
                }
                if (child instanceof ViewGroup vg) {
                    scanHierarchyForHostBars(vg, customRoot, maxHeaderBottomY, screenH, out);
                }
                continue;
            }

            String cls = child.getClass().getName().toLowerCase(Locale.ROOT);
            String idName = getResourceName(child);

            boolean isHostHeaderOrBottomBar = cls.contains("toolbar")
                    || cls.contains("appbarlayout")
                    || cls.contains("collapsingtoolbar")
                    || cls.contains("actionbar")
                    || cls.contains("pivotbar")
                    || cls.contains("bottomnavigation")
                    || idName.contains("toolbar")
                    || idName.contains("action_bar")
                    || idName.contains("header")
                    || idName.contains("title_bar")
                    || idName.contains("masthead")
                    || idName.contains("pivot_bar")
                    || idName.contains("bottom_bar")
                    || idName.contains("bottom_sheet");

            if (isHostHeaderOrBottomBar) {
                out.add(child);
                continue;
            }

            // Doğrudan "İndirilenler" başlık metnini içeren üst bar kapsayıcısını yakala ve gizle
            if (child instanceof TextView tv && isDownloadsCategoryText(tv.getText())) {
                View headerContainer = findHeaderBarContainer(tv, customRoot);
                out.add(headerContainer != null ? headerContainer : tv);
                continue;
            }

            if (child.getVisibility() == View.VISIBLE && child.getHeight() > 0) {
                Rect r = new Rect();
                if (child.getGlobalVisibleRect(r)) {
                    // Ekranın en üst şeridinde yer alan tüm yabancı host barlarını (<- İndirilenler dahil) gizle
                    if (r.top <= dp(child, 60) && r.bottom <= maxHeaderBottomY && child.getHeight() <= dp(child, 95)) {
                        if (containsOfflineTitleText(child) || hasBackNavigationIcon(child)) {
                            out.add(child);
                            continue;
                        }
                    }
                    // Ekranın en altında yer kaplayan YT Music alt barını gizle
                    if (r.top >= screenH - dp(child, 110) && child.getHeight() <= dp(child, 90)) {
                        out.add(child);
                        continue;
                    }
                }
            }

            if (child instanceof ViewGroup vg) {
                scanHierarchyForHostBars(vg, customRoot, maxHeaderBottomY, screenH, out);
            }
        }
    }

    private static View findHeaderBarContainer(View textView, View customRoot) {
        View current = textView;
        int depth = 0;
        while (current != null && depth < 4) {
            ViewParent p = current.getParent();
            if (!(p instanceof View pv) || pv == customRoot || isAncestorOf(pv, customRoot)) {
                break;
            }
            if (pv.getHeight() > 0 && pv.getHeight() <= dp(textView, 110)) {
                current = pv;
            } else {
                break;
            }
            depth++;
        }
        return current;
    }

    private static boolean hasBackNavigationIcon(View v) {
        if (v instanceof ImageView iv && iv.getVisibility() == View.VISIBLE) {
            CharSequence desc = iv.getContentDescription();
            if (desc != null) {
                String d = desc.toString().toLowerCase(Locale.ROOT);
                if (d.contains("geri") || d.contains("back") || d.contains("navigate") || d.contains("yukarı")) {
                    return true;
                }
            }
        } else if (v instanceof ViewGroup vg) {
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (hasBackNavigationIcon(vg.getChildAt(i))) return true;
            }
        }
        return false;
    }

    private static boolean containsOfflineTitleText(View v) {
        if (v instanceof TextView tv) {
            return isDownloadsCategoryText(tv.getText());
        } else if (v instanceof ViewGroup vg) {
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (containsOfflineTitleText(vg.getChildAt(i))) return true;
            }
        }
        return false;
    }

    private static void saveStateIfNeeded(View v) {
        if (HIDDEN_VIEWS.containsKey(v)) return;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        int h = lp != null ? lp.height : -2;
        int tm = (lp instanceof ViewGroup.MarginLayoutParams mlp) ? mlp.topMargin : 0;
        int bm = (lp instanceof ViewGroup.MarginLayoutParams mlp) ? mlp.bottomMargin : 0;
        HIDDEN_VIEWS.put(v, new SavedViewState(v.getVisibility(), h, tm, bm, v.getPaddingTop(), v.getPaddingBottom()));
    }

    private static void collapseSingleView(View v) {
        saveStateIfNeeded(v);
        v.setVisibility(View.GONE);
        v.setMinimumHeight(0);
        v.setPadding(0, 0, 0, 0);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null) {
            lp.height = 0;
            if (lp instanceof ViewGroup.MarginLayoutParams mlp) {
                mlp.topMargin = 0;
                mlp.bottomMargin = 0;
            }
            v.setLayoutParams(lp);
        }
    }

    private static boolean isSystemBar(View v) {
        String id = getResourceName(v);
        return id.contains("status_bar") || id.contains("navigation_bar") || id.contains("system_bar");
    }

    private static String getResourceName(View v) {
        try {
            if (v.getId() != View.NO_ID && v.getResources() != null) {
                return v.getResources().getResourceEntryName(v.getId()).toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static boolean isAncestorOf(View potentialAncestor, View target) {
        ViewParent p = target.getParent();
        while (p != null) {
            if (p == potentialAncestor) return true;
            p = p.getParent();
        }
        return false;
    }

    private static boolean isDescendantOf(View target, View potentialAncestor) {
        return isAncestorOf(potentialAncestor, target);
    }

    private static int dp(Activity act, int val) {
        return (int) (val * act.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int dp(View v, int val) {
        return (int) (val * v.getResources().getDisplayMetrics().density + 0.5f);
    }
}
