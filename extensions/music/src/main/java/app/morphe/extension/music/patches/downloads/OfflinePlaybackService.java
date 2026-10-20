package app.morphe.extension.music.patches.downloads;

import static app.morphe.extension.shared.StringRef.str;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.LoudnessEnhancer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.view.KeyEvent;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import app.morphe.extension.shared.Logger;

/** Foreground offline player with Sleep Timer, Drag-Drop Queue Reorder, Pre-amp Booster & Smart Stats. */
public final class OfflinePlaybackService extends Service {
    public interface PlaybackListener {
        void onPlaybackChanged(String path, String title, boolean playing, int position, int duration);
    }

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static final Set<PlaybackListener> listeners = new CopyOnWriteArraySet<>();
    private static volatile String currentPath = "";
    private static volatile String currentTitle = "";
    private static volatile boolean currentPlaying;
    private static volatile int currentPosition;
    private static volatile int currentDuration;
    private static volatile int currentRepeatMode = REPEAT_OFF;
    private static volatile boolean shuffleEnabled = false;

    // Pre-amp (%100 - %150)
    private static volatile int preampPercent = 100;

    // Sleep Timer
    private static volatile long sleepTimerEndRealtimeMs = 0L;
    private static volatile boolean stopAtTrackEnd = false;

    private static volatile OfflinePlaybackService instance;

    public static void addListener(PlaybackListener listener) {
        listeners.add(listener);
        listener.onPlaybackChanged(currentPath, currentTitle, currentPlaying, currentPosition, currentDuration);
    }

    public static void removeListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    public static void toggle(Context context) {
        context.startService(new Intent(context, OfflinePlaybackService.class).setAction(ACTION_TOGGLE));
    }

    public static void stop(Context context) {
        context.startService(new Intent(context, OfflinePlaybackService.class).setAction(ACTION_STOP));
    }

    public static void skipNext(Context context) {
        context.startService(new Intent(context, OfflinePlaybackService.class).setAction(ACTION_NEXT));
    }

    public static void skipPrevious(Context context) {
        context.startService(new Intent(context, OfflinePlaybackService.class).setAction(ACTION_PREVIOUS));
    }

    public static void seekTo(int position) {
        OfflinePlaybackService service = instance;
        if (service != null && service.playerReady()) {
            service.player.seekTo(Math.max(0, Math.min(position, service.player.getDuration())));
            service.publishState();
        }
    }

    public static int getRepeatMode() {
        return currentRepeatMode;
    }

    public static int cycleRepeatMode() {
        currentRepeatMode = (currentRepeatMode + 1) % 3;
        OfflinePlaybackService service = instance;
        if (service != null) service.publishState();
        return currentRepeatMode;
    }

    public static boolean isShuffleEnabled() {
        return shuffleEnabled;
    }

    public static boolean toggleShuffle() {
        shuffleEnabled = !shuffleEnabled;
        OfflinePlaybackService service = instance;
        if (service != null) {
            service.applyShuffleState();
            service.publishState();
        }
        return shuffleEnabled;
    }

    public static synchronized void moveQueueItem(int fromIndex, int toIndex) {
        OfflinePlaybackService service = instance;
        if (service == null) return;
        if (fromIndex < 0 || fromIndex >= service.queue.size()
                || toIndex < 0 || toIndex >= service.queue.size()
                || fromIndex == toIndex) {
            return;
        }
        String moved = service.queue.remove(fromIndex);
        service.queue.add(toIndex, moved);

        if (service.queueIndex == fromIndex) {
            service.queueIndex = toIndex;
        } else if (fromIndex < service.queueIndex && toIndex >= service.queueIndex) {
            service.queueIndex--;
        } else if (fromIndex > service.queueIndex && toIndex <= service.queueIndex) {
            service.queueIndex++;
        }
    }

    public static synchronized void removeQueueItem(int index) {
        OfflinePlaybackService service = instance;
        if (service == null || index < 0 || index >= service.queue.size()) return;
        
        String removed = service.queue.remove(index);
        if (service.originalQueue.contains(removed)) {
            service.originalQueue.remove(removed);
        }
        
        if (index < service.queueIndex) {
            service.queueIndex--;
        } else if (index == service.queueIndex) {
            // Çalan şarkıyı sildiyse, sıradakine geç
            if (service.queue.isEmpty()) {
                service.stopPlayback();
            } else {
                service.queueIndex = Math.min(service.queueIndex, service.queue.size() - 1);
                OfflineTrack nextTrack = OfflineTrack.load(new File(service.queue.get(service.queueIndex)));
                service.playFile(nextTrack.audioFile().getAbsolutePath(), nextTrack.displayTitle(), 
                        nextTrack.displayArtist(), nextTrack.artworkFile().getAbsolutePath());
            }
        }
    }

    public static synchronized boolean enqueueTrack(String targetPath, boolean playNext) {
        OfflinePlaybackService service = instance;
        if (service == null || targetPath == null || targetPath.isEmpty()) return false;
        if (service.queue.isEmpty()) {
            service.queue.add(targetPath);
            service.originalQueue.add(targetPath);
            service.queueIndex = 0;
        } else if (playNext) {
            int insertAt = Math.min(service.queue.size(), Math.max(0, service.queueIndex + 1));
            service.queue.add(insertAt, targetPath);
            service.originalQueue.add(Math.min(service.originalQueue.size(), insertAt), targetPath);
        } else {
            service.queue.add(targetPath);
            service.originalQueue.add(targetPath);
        }
        service.publishState();
        service.notifyChanged();
        return true;
    }

    public static void setSleepTimer(int minutes, boolean stopWhenCurrentTrackEnds) {
        stopAtTrackEnd = stopWhenCurrentTrackEnds;
        OfflinePlaybackService service = instance;
        if (minutes > 0) {
            sleepTimerEndRealtimeMs = System.currentTimeMillis() + minutes * 60_000L;
            if (service != null) {
                service.progressHandler.removeCallbacks(service.sleepTimerRunnable);
                service.progressHandler.postDelayed(service.sleepTimerRunnable, minutes * 60_000L);
            }
        } else {
            sleepTimerEndRealtimeMs = 0L;
            if (service != null) {
                service.progressHandler.removeCallbacks(service.sleepTimerRunnable);
            }
        }
    }

    public static boolean isStopAtTrackEnd() {
        return stopAtTrackEnd;
    }

    public static long getRemainingSleepTimerMinutes() {
        if (sleepTimerEndRealtimeMs <= 0L) return 0L;
        long diff = sleepTimerEndRealtimeMs - System.currentTimeMillis();
        return diff > 0 ? (diff + 59_999L) / 60_000L : 0L;
    }

    public static int getPreampPercent() {
        return preampPercent;
    }

    public static void setPreampPercent(int percent) {
        preampPercent = Math.max(100, Math.min(150, percent));
        OfflinePlaybackService service = instance;
        if (service != null) {
            service.applyPreampGain();
        }
    }

    public static List<String> getCurrentQueue() {
        OfflinePlaybackService service = instance;
        return service == null ? Collections.emptyList() : new ArrayList<>(service.queue);
    }

    public static int getCurrentQueueIndex() {
        OfflinePlaybackService service = instance;
        return service == null ? -1 : service.queueIndex;
    }

    public static void playAtQueueIndex(int index) {
        OfflinePlaybackService service = instance;
        if (service != null && index >= 0 && index < service.queue.size()) {
            service.queueIndex = index;
            OfflineTrack track = OfflineTrack.load(new File(service.queue.get(index)));
            service.playFile(track.audioFile().getAbsolutePath(), track.displayTitle(),
                    track.displayArtist(), track.artworkFile().getAbsolutePath());
        }
    }

    public static final String ACTION_PLAY_FILE = "app.morphe.action.PLAY_OFFLINE_FILE";
    public static final String ACTION_TOGGLE = "app.morphe.action.TOGGLE_OFFLINE_PLAYBACK";
    public static final String ACTION_STOP = "app.morphe.action.STOP_OFFLINE_PLAYBACK";
    public static final String ACTION_NEXT = "app.morphe.action.NEXT_OFFLINE_TRACK";
    public static final String ACTION_PREVIOUS = "app.morphe.action.PREVIOUS_OFFLINE_TRACK";
    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_QUEUE = "queue";
    public static final String EXTRA_QUEUE_INDEX = "queue_index";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_ARTWORK_PATH = "artwork_path";

    private static final String CHANNEL_ID = "morphe_offline_playback";
    private static final int NOTIFICATION_ID = 8841;

    private MediaPlayer player;
    private MediaPlayer nextPlayer; // Crossfade için
    private boolean isCrossfading = false;
    
    private LoudnessEnhancer loudnessEnhancer;
    private boolean prepared;
    private MediaSession session;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private BroadcastReceiver audioNoisyReceiver;
    private int audioEffectSessionId;
    private String path = "";
    private String publishedMetadataPath = "";
    private String title = "YouTube Music";
    private String artist = "YouTube Music";
    private Bitmap artwork;
    private ArrayList<String> originalQueue = new ArrayList<>();
    private ArrayList<String> queue = new ArrayList<>();
    private int queueIndex = -1;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());

    // Customizable wired/Bluetooth headset button handling.
    private static final long HEADSET_DOUBLE_PRESS_TIMEOUT_MS = 350L;
    private static final long HEADSET_LONG_PRESS_TIMEOUT_MS = 600L;
    private boolean headsetButtonDown = false;
    private boolean headsetLongPressTriggered = false;
    private Runnable pendingHeadsetSinglePress;
    private Runnable pendingHeadsetLongPress;

    private final Runnable sleepTimerRunnable = () -> {
        sleepTimerEndRealtimeMs = 0L;
        if (playerReady()) player.setVolume(1f, 1f); // Reset volume
        pause();
    };

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            publishState();
            if (playerReady() && player.isPlaying()) {
                checkSleepFade();
                checkCrossfade();
                progressHandler.postDelayed(this, 400);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        createFocusRequest();
        createChannel();
        
        // Kulaklık veya Bluetooth bağlantısı koptuğunda müziği durduran sistem
        audioNoisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                    pause(); 
                }
            }
        };
        registerReceiver(audioNoisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

        session = new MediaSession(this, "MorpheOfflinePlayback");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { playQueueOffset(1); }
            @Override public void onSkipToPrevious() { playQueueOffset(-1); }
            @Override public void onStop() { stopPlayback(); }
            @Override public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                KeyEvent event = mediaButtonIntent == null ? null
                        : mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event == null) return super.onMediaButtonEvent(mediaButtonIntent);
                int keyCode = event.getKeyCode();
                if (keyCode != KeyEvent.KEYCODE_HEADSETHOOK
                        && keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    return super.onMediaButtonEvent(mediaButtonIntent);
                }
                handleCustomHeadsetButton(event);
                return true;
            }
            @Override public void onSeekTo(long pos) {
                if (playerReady()) player.seekTo((int) Math.min(Integer.MAX_VALUE, pos));
                publishState();
            }
        });
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch != null) session.setSessionActivity(PendingIntent.getActivity(this, 3, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        session.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_PLAY_FILE.equals(action)) {
            ArrayList<String> suppliedQueue = intent.getStringArrayListExtra(EXTRA_QUEUE);
            if (suppliedQueue != null) {
                originalQueue = new ArrayList<>(suppliedQueue);
                queue = new ArrayList<>(suppliedQueue);
                queueIndex = intent.getIntExtra(EXTRA_QUEUE_INDEX, -1);
                if (shuffleEnabled) applyShuffleState();
            }
            playFile(intent.getStringExtra(EXTRA_PATH), intent.getStringExtra(EXTRA_TITLE),
                    intent.getStringExtra(EXTRA_ARTIST), intent.getStringExtra(EXTRA_ARTWORK_PATH));
        } else if (ACTION_TOGGLE.equals(action)) {
            if (playerReady() && player.isPlaying()) pause();
            else resume();
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback();
        } else if (ACTION_NEXT.equals(action)) {
            playQueueOffset(1);
        } else if (ACTION_PREVIOUS.equals(action)) {
            playQueueOffset(-1);
        }
        return START_NOT_STICKY;
    }

    private void handleCustomHeadsetButton(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() != 0 || headsetButtonDown) return;
            headsetButtonDown = true;
            headsetLongPressTriggered = false;
            if (pendingHeadsetLongPress != null) {
                progressHandler.removeCallbacks(pendingHeadsetLongPress);
            }
            pendingHeadsetLongPress = () -> {
                if (!headsetButtonDown || headsetLongPressTriggered) return;
                headsetLongPressTriggered = true;
                if (pendingHeadsetSinglePress != null) {
                    progressHandler.removeCallbacks(pendingHeadsetSinglePress);
                    pendingHeadsetSinglePress = null;
                }
                performHeadsetAction("headset_long_action", "PREVIOUS");
            };
            progressHandler.postDelayed(pendingHeadsetLongPress, HEADSET_LONG_PRESS_TIMEOUT_MS);
            return;
        }

        if (event.getAction() != KeyEvent.ACTION_UP || !headsetButtonDown) return;
        headsetButtonDown = false;
        if (pendingHeadsetLongPress != null) {
            progressHandler.removeCallbacks(pendingHeadsetLongPress);
            pendingHeadsetLongPress = null;
        }
        if (headsetLongPressTriggered) {
            headsetLongPressTriggered = false;
            return;
        }

        // Delay the single press briefly so a second press can be recognized.
        if (pendingHeadsetSinglePress != null) {
            progressHandler.removeCallbacks(pendingHeadsetSinglePress);
            pendingHeadsetSinglePress = null;
            performHeadsetAction("headset_double_action", "NEXT");
        } else {
            pendingHeadsetSinglePress = () -> {
                pendingHeadsetSinglePress = null;
                performHeadsetAction("headset_single_action", "TOGGLE");
            };
            progressHandler.postDelayed(pendingHeadsetSinglePress, HEADSET_DOUBLE_PRESS_TIMEOUT_MS);
        }
    }

    private void performHeadsetAction(String preferenceKey, String defaultAction) {
        SharedPreferences prefs = getSharedPreferences(LocalDownloadsUIState.PREF_NAME, MODE_PRIVATE);
        String action = prefs.getString(preferenceKey, defaultAction);
        if (action == null) action = defaultAction;
        switch (action) {
            case "TOGGLE":
                if (playerReady() && player.isPlaying()) pause();
                else resume();
                break;
            case "NEXT":
                playQueueOffset(1);
                break;
            case "PREVIOUS":
                playQueueOffset(-1);
                break;
            case "VOLUME_UP":
                if (audioManager != null) audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
                break;
            case "VOLUME_DOWN":
                if (audioManager != null) audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
                break;
            case "NONE":
            default:
                break;
        }
    }

    private void applyShuffleState() {
        if (originalQueue.isEmpty()) return;
        String activeTrack = (queueIndex >= 0 && queueIndex < queue.size()) ? queue.get(queueIndex) : path;
        if (shuffleEnabled) {
            ArrayList<String> shuffled = new ArrayList<>(originalQueue);
            shuffled.remove(activeTrack);
            Collections.shuffle(shuffled);
            if (activeTrack != null && !activeTrack.isEmpty() && originalQueue.contains(activeTrack)) {
                shuffled.add(0, activeTrack);
                queueIndex = 0;
            }
            queue = shuffled;
        } else {
            queue = new ArrayList<>(originalQueue);
            queueIndex = Math.max(0, queue.indexOf(activeTrack));
        }
    }

    private void playFile(@Nullable String path, @Nullable String requestedTitle,
                          @Nullable String requestedArtist, @Nullable String artworkPath) {
        if (path == null) return;
        File audioFile = new File(path);
        if (!audioFile.isFile()) return;

        this.path = path;
        title = requestedTitle == null ? audioFile.getName() : requestedTitle;
        artist = requestedArtist == null || requestedArtist.isBlank() ? "YouTube Music" : requestedArtist;
        File artworkFile = artworkPath == null ? null : new File(artworkPath);
        artwork = artworkFile != null && artworkFile.isFile()
                ? BitmapFactory.decodeFile(artworkPath)
                : null;
        
        releasePlayer();
        isCrossfading = false;
        
        try {
            pauseOtherMedia();
            if (audioFocusDenied()) return;
            player = new MediaPlayer();
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(path);
            player.setVolume(1f, 1f); // Reset volume for normal play
            
            setupPlayerListeners(player);
            
            player.setOnPreparedListener(mp -> {
                if (mp != player) return;
                prepared = true;
                openAudioEffectSession(mp);
                setupLoudnessEnhancer(mp.getAudioSessionId());
                mp.start();

                int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                boolean isNight = (hour >= 22 || hour < 5);
                OfflineTrack.recordPlayEvent(audioFile, isNight);

                publishState();
                startForeground(NOTIFICATION_ID, notification());
                progressHandler.removeCallbacks(progressTicker);
                progressHandler.post(progressTicker);
            });
            
            player.prepareAsync();
            startForeground(NOTIFICATION_ID, notification());
        } catch (Exception ex) {
            Logger.printException(() -> "Could not start offline playback", ex);
            stopPlayback();
        }
    }

    private void setupPlayerListeners(MediaPlayer mp) {
        mp.setOnCompletionListener(m -> {
            if (m != player) return;
            if (stopAtTrackEnd) {
                stopAtTrackEnd = false;
                pause();
                return;
            }
            if (currentRepeatMode == REPEAT_ONE) {
                m.seekTo(0);
                m.setVolume(1f, 1f);
                m.start();
                publishState();
            } else if (queueIndex >= 0 && queueIndex + 1 < queue.size()) {
                playQueueOffset(1);
            } else if (currentRepeatMode == REPEAT_ALL && !queue.isEmpty()) {
                queueIndex = -1;
                playQueueOffset(1);
            } else {
                stopPlayback();
            }
        });
        mp.setOnErrorListener((m, what, extra) -> {
            if (m != player) return true;
            Logger.printException(() -> "Offline player error: " + what + "/" + extra);
            stopPlayback();
            return true;
        });
    }

    private void setupLoudnessEnhancer(int sessionId) {
        if (sessionId <= 0) return;
        try {
            loudnessEnhancer = new LoudnessEnhancer(sessionId);
            applyPreampGain();
        } catch (Exception ex) {
            Logger.printDebug(() -> "LoudnessEnhancer unavailable", ex);
        }
    }

    private void applyPreampGain() {
        if (loudnessEnhancer == null) return;
        try {
            int gainMb = (preampPercent - 100) * 30; // %150 -> +1500 mB (+15 dB)
            loudnessEnhancer.setTargetGain(gainMb);
            loudnessEnhancer.setEnabled(gainMb > 0);
        } catch (Exception ignored) {
        }
    }

    // GÖREV 6: Uyku Modu Fade-Out
    private void checkSleepFade() {
        if (sleepTimerEndRealtimeMs <= 0 || !LocalDownloadsUIState.isSleepFadeEnabled(this)) return;
        long remaining = sleepTimerEndRealtimeMs - System.currentTimeMillis();
        
        if (remaining > 0 && remaining <= 30_000L) {
            // Son 30 saniye kala sesi yavaşça kıs
            float fraction = remaining / 30_000f;
            if (playerReady()) {
                player.setVolume(fraction, fraction);
            }
        }
    }

    // GÖREV 6: Crossfade (Sonraki şarkıya yumuşak geçiş)
    private void checkCrossfade() {
        if (isCrossfading || !LocalDownloadsUIState.isCrossfadeEnabled(this)) return;
        if (!playerReady() || player.getDuration() <= 0) return;
        if (currentRepeatMode == REPEAT_ONE || stopAtTrackEnd) return; // Aynı şarkı döngüsünde veya uyku modunda crossfade yapma

        int duration = player.getDuration();
        int position = player.getCurrentPosition();
        int crossfadeMs = LocalDownloadsUIState.getCrossfadeSeconds(this) * 1000;

        if (crossfadeMs <= 0 || duration <= crossfadeMs) return;
        
        if (duration - position <= crossfadeMs) {
            int nextIndex = queueIndex + 1;
            if (nextIndex >= queue.size()) {
                if (currentRepeatMode == REPEAT_ALL) {
                    nextIndex = 0;
                } else {
                    return; // Kuyruk sonu
                }
            }
            startCrossfade(queue.get(nextIndex), nextIndex);
        }
    }

    private void startCrossfade(String nextPath, int nextIndex) {
        isCrossfading = true;
        try {
            nextPlayer = new MediaPlayer();
            nextPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            nextPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            nextPlayer.setDataSource(nextPath);
            nextPlayer.setVolume(0f, 0f); // 0 ses ile başlat
            
            nextPlayer.setOnPreparedListener(mp -> {
                if (mp != nextPlayer) return;
                mp.start();
                int fadeMs = LocalDownloadsUIState.getCrossfadeSeconds(this) * 1000;
                
                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(fadeMs);
                animator.addUpdateListener(animation -> {
                    float fraction = animation.getAnimatedFraction();
                    if (playerReady()) player.setVolume(1f - fraction, 1f - fraction);
                    if (nextPlayer != null) nextPlayer.setVolume(fraction, fraction);
                });
                
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        MediaPlayer oldPlayer = player;
                        player = nextPlayer;
                        nextPlayer = null;
                        
                        if (oldPlayer != null) {
                            try {
                                oldPlayer.reset();
                                oldPlayer.release();
                            } catch (Exception ignored) {}
                        }
                        
                        prepared = true;
                        isCrossfading = false;
                        queueIndex = nextIndex;
                        path = nextPath;
                        
                        OfflineTrack track = OfflineTrack.load(new File(path));
                        title = track.displayTitle();
                        artist = track.displayArtist();
                        File artFile = track.artworkFile();
                        artwork = (artFile != null && artFile.isFile()) ? BitmapFactory.decodeFile(artFile.getAbsolutePath()) : null;

                        // Yeni player için listener'ları kur ve efektleri bağla
                        setupPlayerListeners(player);
                        openAudioEffectSession(player);
                        setupLoudnessEnhancer(player.getAudioSessionId());
                        applyPreampGain();

                        publishState();
                        notifyChanged();
                    }
                });
                animator.start();
            });
            nextPlayer.prepareAsync();
        } catch (Exception e) {
            isCrossfading = false;
            Logger.printException(() -> "Crossfade failed", e);
        }
    }

    private void playQueueOffset(int offset) {
        if (queue.isEmpty()) return;

        if (offset < 0 && playerReady() && player.getCurrentPosition() > 3000) {
            player.seekTo(0);
            publishState();
            return;
        }

        int target = queueIndex + offset;
        if (target < 0) {
            if (currentRepeatMode == REPEAT_ALL) target = queue.size() - 1;
            else if (playerReady()) { player.seekTo(0); publishState(); return; }
            else return;
        } else if (target >= queue.size()) {
            if (currentRepeatMode == REPEAT_ALL) target = 0;
            else return;
        }

        queueIndex = target;
        OfflineTrack track = OfflineTrack.load(new File(queue.get(target)));
        playFile(track.audioFile().getAbsolutePath(), track.displayTitle(), track.displayArtist(),
                track.artworkFile().getAbsolutePath());
    }

    private void resume() {
        if (!playerReady()) return;
        if (audioFocusDenied()) return;
        player.start();
        progressHandler.removeCallbacks(progressTicker);
        progressHandler.post(progressTicker);
        publishState();
        notifyChanged();
    }

    private void pause() {
        if (!playerReady() || !player.isPlaying()) return;
        player.pause();
        progressHandler.removeCallbacks(progressTicker);
        publishState();
        notifyChanged();
    }

    private void stopPlayback() {
        releasePlayer();
        progressHandler.removeCallbacks(progressTicker);
        progressHandler.removeCallbacks(sleepTimerRunnable);
        sleepTimerEndRealtimeMs = 0L;
        stopAtTrackEnd = false;
        isCrossfading = false;
        
        if (audioManager != null && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
        publishedMetadataPath = "";
        path = "";
        currentPath = "";
        currentTitle = "";
        currentPlaying = false;
        currentPosition = 0;
        currentDuration = 0;
        if (session != null) {
            session.setPlaybackState(new PlaybackState.Builder().setState(
                    PlaybackState.STATE_STOPPED, 0, 0).build());
            session.setMetadata(new MediaMetadata.Builder().build());
        }
        for (PlaybackListener listener : listeners) {
            listener.onPlaybackChanged("", "", false, 0, 0);
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private boolean playerReady() {
        return prepared && player != null;
    }

    private void openAudioEffectSession(MediaPlayer mediaPlayer) {
        int sessionId = mediaPlayer.getAudioSessionId();
        if (sessionId <= 0) return;

        audioEffectSessionId = sessionId;
        sendBroadcast(new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName())
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC));
    }

    private void closeAudioEffectSession() {
        int sessionId = audioEffectSessionId;
        if (sessionId <= 0) return;

        audioEffectSessionId = 0;
        sendBroadcast(new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName()));
    }

    private void releasePlayer() {
        prepared = false;
        progressHandler.removeCallbacks(progressTicker);
        closeAudioEffectSession();

        if (loudnessEnhancer != null) {
            try { loudnessEnhancer.release(); } catch (Exception ignored) {}
            loudnessEnhancer = null;
        }

        if (nextPlayer != null) {
            try {
                nextPlayer.reset();
                nextPlayer.release();
            } catch (Exception ignored) {}
            nextPlayer = null;
        }

        MediaPlayer released = player;
        if (released == null) return;

        player = null;
        released.setOnPreparedListener(null);
        released.setOnCompletionListener(null);
        released.setOnErrorListener(null);
        released.reset();
        released.release();
    }

    private void createFocusRequest() {
        if (audioManager == null) return;
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS ||
                            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                        pause();
                    }
                })
                .build();
    }

    private boolean audioFocusDenied() {
        return audioManager == null || focusRequest == null
                || audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void pauseOtherMedia() {
        if (audioManager == null || !audioManager.isMusicActive()) return;
        KeyEvent down = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE);
        KeyEvent up = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE);
        audioManager.dispatchMediaKeyEvent(down);
        audioManager.dispatchMediaKeyEvent(up);
    }

    private void publishState() {
        MediaSession currentSession = session;
        if (currentSession == null) return;

        final boolean playing = playerReady() && player.isPlaying();
        final long position = playerReady() ? player.getCurrentPosition() : 0;
        currentSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
                        PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP |
                        PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_SKIP_TO_NEXT |
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        position, playing ? 1f : 0f)
                .build());
        currentPath = path == null ? "" : path;
        currentTitle = title;
        currentPlaying = playing;
        currentPosition = (int) position;
        currentDuration = playerReady() ? player.getDuration() : 0;
        for (PlaybackListener listener : listeners) {
            listener.onPlaybackChanged(currentPath, currentTitle, currentPlaying, currentPosition, currentDuration);
        }
        if (!currentPath.equals(publishedMetadataPath)) {
            publishedMetadataPath = currentPath;
            MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, currentDuration);
            if (artwork != null) {
                metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
                        .putBitmap(MediaMetadata.METADATA_KEY_ART, artwork)
                        .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, artwork);
            }
            currentSession.setMetadata(metadata.build());
        }
    }

    @SuppressLint("WrongConstant")
    private Notification notification() {
        final boolean playing = playerReady() && player.isPlaying();
        PendingIntent toggle = serviceIntent(ACTION_TOGGLE, 1);
        PendingIntent stop = serviceIntent(ACTION_STOP, 2);
        PendingIntent previous = serviceIntent(ACTION_PREVIOUS, 4);
        PendingIntent next = serviceIntent(ACTION_NEXT, 5);
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent content = PendingIntent.getActivity(this, 3, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                .setContentTitle(title)
                .setContentText(artist)
                .setLargeIcon(artwork)
                .setContentIntent(content)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(true)
                .addAction(action(android.R.drawable.ic_media_previous,
                        str("morphe_music_downloads_previous"), previous))
                .addAction(action(
                        playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        str(playing ? "morphe_music_downloads_pause" : "morphe_music_downloads_play"),
                        toggle))
                .addAction(action(android.R.drawable.ic_media_next,
                        str("morphe_music_downloads_next"), next))
                .addAction(action(android.R.drawable.ic_menu_close_clear_cancel,
                        str("morphe_music_downloads_close"), stop))
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .build();
    }

    private Notification.Action action(int iconResource, String title, PendingIntent intent) {
        return new Notification.Action.Builder(
                Icon.createWithResource(this, iconResource), title, intent).build();
    }

    private PendingIntent serviceIntent(String action, int code) {
        Intent intent = new Intent(this, OfflinePlaybackService.class).setAction(action);
        return PendingIntent.getService(this, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressLint("NotificationPermission")
    private void notifyChanged() {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, notification());
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                str("morphe_music_downloads_playback_channel"), NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopPlayback();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        progressHandler.removeCallbacks(progressTicker);
        progressHandler.removeCallbacks(sleepTimerRunnable);
        if (pendingHeadsetSinglePress != null) progressHandler.removeCallbacks(pendingHeadsetSinglePress);
        if (pendingHeadsetLongPress != null) progressHandler.removeCallbacks(pendingHeadsetLongPress);
        pendingHeadsetSinglePress = null;
        pendingHeadsetLongPress = null;
        headsetButtonDown = false;
        headsetLongPressTriggered = false;
        
        if (audioNoisyReceiver != null) {
            try {
                unregisterReceiver(audioNoisyReceiver);
            } catch (Exception ignored) {
            }
            audioNoisyReceiver = null;
        }
        
        releasePlayer();
        artwork = null;
        if (audioManager != null && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
        if (session != null) {
            session.release();
            session = null;
        }
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}