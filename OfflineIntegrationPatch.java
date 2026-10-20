package app.morphe.extension.music.patches.downloads;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import app.morphe.extension.music.shared.VideoInformation;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public final class OfflineIntegrationPatch {
    private static final String OFFLINE_TAB_TAG = "morphe_pivot_offline_tab";
    public static final String OFFLINE_FRAGMENT_TAG = "morphe_offline_downloads";
    public static final String ANALYZER_PREFS = "morphe_online_play_analyzer";
    private static final float Z_INDEX_PIVOT_TAB = 16f;

    public static final String PREF_AUTO_DL_ENABLED = "auto_wifi_download_frequent";
    public static final String PREF_AUTO_DL_THRESHOLD = "auto_wifi_download_threshold";
    public static final String PREF_AUTO_DL_MIN_SECONDS = "auto_wifi_download_min_seconds";
    public static final String PREF_AUTO_DL_WIFI_ONLY = "auto_wifi_download_wifi_only";
    public static final String PREF_AUTO_DL_TARGET_LIST = "auto_wifi_download_target_list";

    private static Activity guardedActivity;
    private static Object backDispatcher;
    private static Object backCallback;

    private static boolean analyzerStarted = false;
    private static String lastObservedOnlineVideoId = "";
    private static long lastObservedStartTimeMs = 0L;
    private static boolean currentTrackCounted = false;
    private static final Handler ANALYZER_HANDLER = new Handler(Looper.getMainLooper());

    private OfflineIntegrationPatch() {}

    public static void injectOfflinePivotTab(ViewGroup pivotBarLayout) {
        if (pivotBarLayout == null) return;
        try {
            Context context = pivotBarLayout.getContext();
            Activity act = Utils.getActivity();
            if (act == null && context instanceof Activity a) act = a;
            if (act != null) {
                OfflineHeaderBlocker.installOnlineDownloadsInterceptor(act);
            }
            startOnlinePlayAnalyzer(context);

            if (pivotBarLayout.findViewWithTag(OFFLINE_TAB_TAG) != null) return;

            LinearLayout tab = new LinearLayout(context);
            tab.setTag(OFFLINE_TAB_TAG);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setElevation(Z_INDEX_PIVOT_TAB);
            tab.setPadding(0, dp(context, 4), 0, dp(context, 4));

            TextView icon = new TextView(context);
            icon.setText("⬇️");
            icon.setTextSize(15);
            icon.setTextColor(LocalDownloadsUIState.activeAccentColor());
            icon.setGravity(Gravity.CENTER);

            TextView label = new TextView(context);
            label.setText("Çevrimdışı");
            label.setTextSize(10);
            label.setTextColor(LocalDownloadsUIState.primaryTextColor());
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER);

            tab.addView(icon);
            tab.addView(label);
            tab.setOnClickListener(v -> openOfflineFragment(context));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            pivotBarLayout.addView(tab, lp);
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to inject Offline PivotBar tab", ex);
        }
    }

    /**
     * Özelleştirilebilir Online Dinleme Analizcisi.
     * İlk başlatmada önceki oturumdan kalan indirme kuyruğunu da geri yükler.
     */
    public static synchronized void startOnlinePlayAnalyzer(Context context) {
        if (analyzerStarted || context == null) return;
        analyzerStarted = true;
        final Context appCtx = context.getApplicationContext();

        // Önceki oturumdan kalan indirme kuyruğunu geri yükle (kalıcı kuyruk)
        try {
            LocalDownloadManager.restorePersistedQueue();
        } catch (Exception ex) {
            Logger.printDebug(() -> "restorePersistedQueue failed", ex);
        }

        ANALYZER_HANDLER.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    checkOnlinePlayingTrack(appCtx);
                } catch (Exception ignored) {}
                ANALYZER_HANDLER.postDelayed(this, 3000L);
            }
        }, 3000L);
    }

    private static void checkOnlinePlayingTrack(Context ctx) {
        SharedPreferences uiPrefs = ctx.getSharedPreferences(LocalDownloadsUIState.PREF_NAME, Context.MODE_PRIVATE);
        boolean autoDownloadEnabled = uiPrefs.getBoolean(PREF_AUTO_DL_ENABLED, true);
        if (!autoDownloadEnabled) return;

        int threshold = uiPrefs.getInt(PREF_AUTO_DL_THRESHOLD, 3);
        int minSeconds = uiPrefs.getInt(PREF_AUTO_DL_MIN_SECONDS, 15);
        boolean wifiOnly = uiPrefs.getBoolean(PREF_AUTO_DL_WIFI_ONLY, true);
        String targetPlaylist = uiPrefs.getString(PREF_AUTO_DL_TARGET_LIST, "Sık Dinlenenler (Oto)");

        String activeVideoId = VideoInformation.getVideoId();
        if (activeVideoId == null || activeVideoId.isBlank() || !activeVideoId.matches("[A-Za-z0-9_-]{11}")) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!activeVideoId.equals(lastObservedOnlineVideoId)) {
            lastObservedOnlineVideoId = activeVideoId;
            lastObservedStartTimeMs = now;
            currentTrackCounted = false;
            return;
        }

        if (!currentTrackCounted && (now - lastObservedStartTimeMs) >= (minSeconds * 1000L)) {
            currentTrackCounted = true;
            SharedPreferences stats = ctx.getSharedPreferences(ANALYZER_PREFS, Context.MODE_PRIVATE);
            int plays = stats.getInt("plays_" + activeVideoId, 0) + 1;
            stats.edit().putInt("plays_" + activeVideoId, plays).apply();

            boolean networkAllowed = !wifiOnly || isConnectedToWifi(ctx);
            if (plays >= threshold && networkAllowed) {
                File musicRoot = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Morphe");
                if (!OfflineStorage.isTrackDownloaded(musicRoot, activeVideoId)
                        && !LocalDownloadManager.isDownloading(activeVideoId)) {
                    String resolvedTarget = (targetPlaylist == null || targetPlaylist.isBlank())
                            ? null : targetPlaylist;
                    if (resolvedTarget != null) {
                        OfflineStorage.createPlaylist(musicRoot, resolvedTarget, "");
                    }
                    LocalDownloadManager.enqueue(activeVideoId, resolvedTarget, false);
                    Utils.runOnMainThread(() ->
                            Utils.showToastShort("⚡ Sık dinlediğin şarkı (" + plays + ". kez) otomatik indiriliyor"));
                }
            }
        }
    }

    public static void clearOnlinePlayStats(Context ctx) {
        if (ctx == null) return;
        ctx.getSharedPreferences(ANALYZER_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public static boolean isConnectedToWifi(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network active = cm.getActiveNetwork();
            if (active == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void openOfflineFragment(Context context) {
        Activity act = Utils.getActivity();
        if (act == null && context instanceof Activity a) act = a;
        if (act == null) return;

        try {
            FragmentManager fm = act.getFragmentManager();
            if (fm.findFragmentByTag(OFFLINE_FRAGMENT_TAG) != null) {
                guardBackButton(act);
                return;
            }

            fm.beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .add(android.R.id.content, new LocalDownloadsFragment(), OFFLINE_FRAGMENT_TAG)
                    .commitAllowingStateLoss();
            guardBackButton(act);
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to open Offline Fragment", ex);
        }
    }

    public static void dispatchGuardedBack(Activity activity) {
        if (activity == null) return;
        android.app.Fragment fragment = activity.getFragmentManager().findFragmentByTag(OFFLINE_FRAGMENT_TAG);
        if (fragment instanceof LocalDownloadsFragment ldf) {
            ldf.handleGuardedBack();
        }
    }

    public static synchronized void guardBackButton(Activity activity) {
        if (activity == null) return;
        if (guardedActivity == activity && backDispatcher != null && backCallback != null) return;
        releaseBackGuard(guardedActivity);
        try {
            Class<?> callbackClass = Class.forName("android.window.OnBackInvokedCallback");
            Method getter = Activity.class.getMethod("getOnBackInvokedDispatcher");
            Object dispatcher = getter.invoke(activity);
            Object callback = Proxy.newProxyInstance(callbackClass.getClassLoader(),
                    new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                        if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                        if ("equals".equals(method.getName())) return args != null && args.length == 1 && proxy == args[0];
                        if ("toString".equals(method.getName())) return "OfflineScreenBackGuard";
                        if ("onBackInvoked".equals(method.getName())) {
                            dispatchGuardedBack(activity);
                        }
                        return null;
                    });
            Class<?> dispatcherClass = Class.forName("android.window.OnBackInvokedDispatcher");
            Method register = dispatcherClass.getMethod("registerOnBackInvokedCallback", int.class, callbackClass);
            register.invoke(dispatcher, 1_000_000, callback);
            guardedActivity = activity;
            backDispatcher = dispatcher;
            backCallback = callback;
        } catch (Throwable ignored) {}
    }

    public static synchronized void releaseBackGuard(Activity activity) {
        if (guardedActivity == null || (activity != null && guardedActivity != activity)) return;
        try {
            Class<?> callbackClass = Class.forName("android.window.OnBackInvokedCallback");
            Class<?> dispatcherClass = Class.forName("android.window.OnBackInvokedDispatcher");
            Method unregister = dispatcherClass.getMethod("unregisterOnBackInvokedCallback", callbackClass);
            unregister.invoke(backDispatcher, backCallback);
        } catch (Throwable ignored) {}
        guardedActivity = null;
        backDispatcher = null;
        backCallback = null;
    }

    public static void exitOfflineFragment(Activity activity) {
        if (activity == null) return;
        releaseBackGuard(activity);
        OfflineHeaderBlocker.detach(activity);
        try {
            FragmentManager fm = activity.getFragmentManager();
            android.app.Fragment fragment = fm.findFragmentByTag(OFFLINE_FRAGMENT_TAG);
            if (fragment != null) {
                fm.beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .remove(fragment)
                        .commitAllowingStateLoss();
                fm.executePendingTransactions();
            }
            if (fm.getBackStackEntryCount() > 0) {
                fm.popBackStackImmediate(OFFLINE_FRAGMENT_TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            }
            OfflineHeaderBlocker.resetOnlineChipAndNavigateHome(activity);
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to exit Offline Fragment", ex);
        }
    }

    private static int dp(Context ctx, int val) {
        return (int) (val * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}