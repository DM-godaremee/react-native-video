package com.brentvatne.exoplayer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.accessibility.CaptioningManager;
import android.widget.FrameLayout;
import android.support.v4.media.session.MediaSessionCompat;

import com.brentvatne.react.R;
import com.brentvatne.receiver.AudioBecomingNoisyReceiver;
import com.brentvatne.receiver.BecomingNoisyListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Dynamic;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Promise;
import com.facebook.react.uimanager.ThemedReactContext;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.drm.DefaultDrmSessionEventListener;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DefaultDrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.twentyfouri.media.offline.ExoPlayerDownloadService;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@SuppressLint("ViewConstructor")
@SuppressWarnings("unchecked")
class ReactExoplayerView extends FrameLayout implements
        LifecycleEventListener,
        Player.EventListener,
        BandwidthMeter.EventListener,
        BecomingNoisyListener,
        AudioManager.OnAudioFocusChangeListener,
        MetadataOutput,
        DefaultDrmSessionEventListener {

    private static final String TAG = "ReactExoplayerView";

    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private static final CookieManager DEFAULT_COOKIE_MANAGER;
    private static final int SHOW_PROGRESS = 1;

    static {
        DEFAULT_COOKIE_MANAGER = new CookieManager();
        DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    }

    private final MediaSessionCompat mediaSession = new MediaSessionCompat(getContext(), "tag");
    private final BroadcastReceiver pipReceiver;
    private final BroadcastReceiver leaveReceiver;
    private final VideoEventEmitter eventEmitter;
    private PlayerControlView playerControlView;
    private View playPauseControlContainer;
    private Player.EventListener stateEventListener;
    private Player.EventListener seekEventListener;

    private Handler mainHandler;
    private ExoPlayerView exoPlayerView;

    private DataSource.Factory mediaDataSourceFactory;
    private SimpleExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private boolean playerNeedsSource;
    private Timeline.Period period;

    private int resumeWindow;
    private long positionThreshold = 0;
    private long resumePosition;
    private boolean loadVideoStarted;
    private boolean isFullscreen;
    private boolean isInBackground;
    private boolean isPaused;
    private boolean isBuffering;
    private boolean isInPictureInPictureMode;
    private boolean muted = false;
    private float rate = 1f;
    private float audioVolume = 1f;
    private int minLoadRetryCount = 3;
    private int maxBitRate = 0;
    private long seekTime = C.TIME_UNSET;

    private int minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS;
    private int maxBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS;
    private int bufferForPlaybackMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS;
    private int bufferForPlaybackAfterRebufferMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;

    private Handler mainHandler;

    // Props from React
    private Uri srcUri;
    private String extension;
    private boolean repeat;
    private String audioTrackType;
    private Dynamic audioTrackValue;
    private String videoTrackType;
    private Dynamic videoTrackValue;
    private String textTrackType;
    private Dynamic textTrackValue;
    private ReadableArray textTracks;
    private boolean disableFocus;
    private float mProgressUpdateInterval = 250.0f;
    private boolean playInBackground = false;
    private Map<String, String> requestHeaders;
    private boolean mReportBandwidth = false;
    private UUID drmUUID = null;
    private String drmLicenseUrl = null;
    private String[] drmLicenseHeader = null;
    private boolean controls;
    private boolean showPictureInPictureOnLeave;
    private boolean offline;
    // \ End props

    // React
    private final ThemedReactContext themedReactContext;
    private final AudioManager audioManager;
    private final AudioBecomingNoisyReceiver audioBecomingNoisyReceiver;

    private final Handler progressHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW_PROGRESS:
                    if (player != null
                            && player.getPlaybackState() == Player.STATE_READY
                            && player.getPlayWhenReady()
                            ) {
                        long pos = player.getCurrentPosition();
                        long fullPos = pos;
                        long bufferedDuration = player.getBufferedPercentage() * player.getDuration() / 100;

                        Timeline currentTimeline = player.getCurrentTimeline();
                        if (!currentTimeline.isEmpty()) {
                            fullPos -= currentTimeline.getPeriod(player.getCurrentPeriodIndex(), period)
                                    .getPositionInWindowMs() - positionThreshold;
                        }

                        eventEmitter.progressChanged(pos, fullPos, bufferedDuration, player.getDuration());
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, Math.round(mProgressUpdateInterval));
                    }
                    break;
            }
        }
    };

    public ReactExoplayerView(ThemedReactContext context) {
        super(context);
        this.themedReactContext = context;
        this.eventEmitter = new VideoEventEmitter(context);

        createViews();

        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        themedReactContext.addLifecycleEventListener(this);
        audioBecomingNoisyReceiver = new AudioBecomingNoisyReceiver(themedReactContext);

        ReactExoplayerView self = this;

        pipReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean isInPictureInPictureMode = intent.getBooleanExtra("isInPictureInPictureMode", false);
                self.onPictureInPictureModeChanged(isInPictureInPictureMode);
            }
        };

        leaveReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (showPictureInPictureOnLeave) {
                    self.setPictureInPicture(true);
                }
            }
        };

        Activity activity = themedReactContext.getCurrentActivity();
        activity.registerReceiver(pipReceiver, new IntentFilter("onPictureInPictureModeChanged"));
        activity.registerReceiver(leaveReceiver, new IntentFilter("onUserLeaveHint"));

    }

    @Override
    public void setId(int id) {
        super.setId(id);
        eventEmitter.setViewId(id);
    }

    private void createViews() {
        clearResumePosition();
        mediaDataSourceFactory = buildDataSourceFactory(true);
        if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
            CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
        }

        LayoutParams layoutParams = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        exoPlayerView = new ExoPlayerView(getContext());
        exoPlayerView.setLayoutParams(layoutParams);

        addView(exoPlayerView, 0, layoutParams);

        mainHandler = new Handler();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        initializePlayer();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        /* We want to be able to continue playing audio when switching tabs.
         * Leave this here in case it causes issues.
         */
        // stopPlayback();
    }

    // LifecycleEventListener implementation

    @Override
    public void onHostResume() {
        if (!playInBackground || !isInBackground) {
            setPlayWhenReady(!isPaused);
        }
        isInBackground = false;
    }

    @Override
    public void onHostPause() {
        isInBackground = true;
        if (playInBackground || showPictureInPictureOnLeave) {
            return;
        }
        setPlayWhenReady(false);
    }

    @Override
    public void onHostDestroy() {
        stopPlayback();

        Activity activity = themedReactContext.getCurrentActivity();
        if (activity == null) return;
        try {
            activity.unregisterReceiver(pipReceiver);
            activity.unregisterReceiver(leaveReceiver);
        } catch (Exception ignore) {
            // ignore if already unregistered
        }
    }

    public void cleanUpResources() {
        stopPlayback();
    }

    //BandwidthMeter.EventListener implementation
    @Override
    public void onBandwidthSample(int elapsedMs, long bytes, long bitrate) {
        if (mReportBandwidth) {
            eventEmitter.bandwidthReport(bitrate);
        }
    }

    // Internal methods

    /**
     * Toggling the visibility of the player control view
     */
    private void togglePlayerControlVisibility() {
        if(player == null) return;
        reLayout(playerControlView);
        if (playerControlView.isVisible()) {
            playerControlView.hide();
        } else {
            playerControlView.show();
        }
    }

    /**
     * Initializing Player control
     */
    private void initializePlayerControl() {
        if (playerControlView == null) {
            playerControlView = new PlayerControlView(getContext());
        }

        // Setting the player for the playerControlView
        playerControlView.setPlayer(player);
        playerControlView.show();
        playPauseControlContainer = playerControlView.findViewById(R.id.exo_play_pause_container);

        // Invoking onClick event for exoplayerView
        exoPlayerView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayerControlVisibility();
            }
        });

        // Invoking onPlayerStateChanged event for Player
        stateEventListener = new Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                reLayout(playPauseControlContainer);
                //Remove this eventListener once its executed. since UI will work fine once after the reLayout is done
                player.removeListener(stateEventListener);
            }
        };

        // Invoking onPlayerStateChanged event for Player
        seekEventListener = new Player.EventListener() {
            @Override
            public void onSeekProcessed() {
                Timeline currentTimeline = player.getCurrentTimeline();
                if (!currentTimeline.isEmpty()) {
                   positionThreshold = currentTimeline.getPeriod(player.getCurrentPeriodIndex(), period)
                            .getPositionInWindowMs();
                }
            }
        };
        player.addListener(stateEventListener);
        player.addListener(seekEventListener);
    }

    /**
     * Adding Player control to the frame layout
     */
    private void addPlayerControl() {
        if(player == null) return;
        LayoutParams layoutParams = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        playerControlView.setLayoutParams(layoutParams);
        int indexOfPC = indexOfChild(playerControlView);
        if (indexOfPC != -1) {
            removeViewAt(indexOfPC);
        }
        addView(playerControlView, 1, layoutParams);
    }

    /**
     * Update the layout
     * @param view  view needs to update layout
     *
     * This is a workaround for the open bug in react-native: https://github.com/facebook/react-native/issues/17968
     */
    private void reLayout(View view) {
        if (view == null) return;
        view.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
        view.layout(view.getLeft(), view.getTop(), view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private DrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManager(UUID uuid,
                                                                           String licenseUrl, String[] keyRequestPropertiesArray) throws UnsupportedDrmException {
        if (Util.SDK_INT < 18) {
            return null;
        }
        HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(licenseUrl,
                buildHttpDataSourceFactory(false));
        if (keyRequestPropertiesArray != null) {
            for (int i = 0; i < keyRequestPropertiesArray.length - 1; i += 2) {
                drmCallback.setKeyRequestProperty(keyRequestPropertiesArray[i],
                        keyRequestPropertiesArray[i + 1]);
            }
        }
        DefaultDrmSessionManager DDSM =  new DefaultDrmSessionManager<>(uuid, FrameworkMediaDrm.newInstance(uuid), drmCallback, null);
        DDSM.addListener(mainHandler, this);

        return DDSM;
    }

    /**
     * Returns a new HttpDataSource factory.
     *
     * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
     *     DataSource factory.
     * @return A new HttpDataSource factory.
     */
    private HttpDataSource.Factory buildHttpDataSourceFactory(boolean useBandwidthMeter) {
        return new DefaultHttpDataSourceFactory("sctv", useBandwidthMeter ? BANDWIDTH_METER : null);
    }


    private void initializePlayer() {
        ReactExoplayerView self = this;
        // This ensures all props have been settled, to avoid async racing conditions.
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (player == null) {
                    TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory();
                    trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
                    trackSelector.setParameters(trackSelector.buildUponParameters()
                                    .setMaxVideoBitrate(maxBitRate == 0 ? Integer.MAX_VALUE : maxBitRate));

                    DefaultAllocator allocator = new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
                    DefaultLoadControl.Builder defaultLoadControlBuilder = new DefaultLoadControl.Builder();
                    defaultLoadControlBuilder.setAllocator(allocator);
                    defaultLoadControlBuilder.setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs);
                    defaultLoadControlBuilder.setTargetBufferBytes(-1);
                    defaultLoadControlBuilder.setPrioritizeTimeOverSizeThresholds(true);
                    DefaultLoadControl defaultLoadControl = defaultLoadControlBuilder.createDefaultLoadControl();
                    DefaultRenderersFactory renderersFactory =
                            new DefaultRenderersFactory(getContext())
                                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
                    // DRM
                    DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;
                    if (self.drmUUID != null) {
                        try {
                            drmSessionManager = buildDrmSessionManager(self.drmUUID, self.drmLicenseUrl,
                                    self.drmLicenseHeader);
                        } catch (UnsupportedDrmException e) {
                            int errorStringId = Util.SDK_INT < 18 ? R.string.error_drm_not_supported
                                    : (e.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
                                    ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown);
                            eventEmitter.error(getResources().getString(errorStringId), e);
                            return;
                        }
                    }
                    // End DRM
                    player = ExoPlayerFactory.newSimpleInstance(getContext(), renderersFactory,
                            trackSelector, defaultLoadControl, drmSessionManager, bandwidthMeter);
                    player.addListener(self);
                    player.addMetadataOutput(self);
                    exoPlayerView.setPlayer(player);
                    audioBecomingNoisyReceiver.setListener(self);
                    bandwidthMeter.addEventListener(new Handler(), self);
                    setPlayWhenReady(!isPaused);
                    playerNeedsSource = true;
                    positionThreshold = 0;

                    PlaybackParameters params = new PlaybackParameters(rate, 1f);
                    player.setPlaybackParameters(params);
                }
                if (playerNeedsSource && srcUri != null) {
                    ArrayList<MediaSource> mediaSourceList = buildTextSources();
                    MediaSource videoSource = buildMediaSource(srcUri, extension);
                    MediaSource mediaSource;
                    if (mediaSourceList.size() == 0) {
                        mediaSource = videoSource;
                    } else {
                        mediaSourceList.add(0, videoSource);
                        MediaSource[] textSourceArray = mediaSourceList.toArray(
                                new MediaSource[mediaSourceList.size()]
                        );
                        mediaSource = new MergingMediaSource(textSourceArray);
                    }

                    boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;
                    if (haveResumePosition) {
                        player.seekTo(resumeWindow, resumePosition);
                    }
                    player.prepare(mediaSource, !haveResumePosition, false);
                    playerNeedsSource = false;

                    eventEmitter.loadStart();
                    loadVideoStarted = true;
                }

                // Initializing the playerControlView
                initializePlayerControl();
                setControls(controls);
                applyModifiers();
                period = new Timeline.Period();

                //Use Media Session Connector from the ExoPlayer library to enable MediaSession Controls in PIP.
                MediaSessionConnector mediaSessionConnector = new MediaSessionConnector(mediaSession);
                mediaSessionConnector.setPlayer(player, null);
                mediaSession.setActive(true);
            }
        }, 1);
    }

    private DrmSessionManager<FrameworkMediaCrypto> buildDrmSessionManager(UUID uuid,
                                                                           String licenseUrl, String[] keyRequestPropertiesArray) throws UnsupportedDrmException {
        if (Util.SDK_INT < 18) {
            return null;
        }
        HttpMediaDrmCallback drmCallback = new HttpMediaDrmCallback(licenseUrl,
                buildHttpDataSourceFactory(false));
        if (keyRequestPropertiesArray != null) {
            for (int i = 0; i < keyRequestPropertiesArray.length - 1; i += 2) {
                drmCallback.setKeyRequestProperty(keyRequestPropertiesArray[i],
                        keyRequestPropertiesArray[i + 1]);
            }
        }
        return new DefaultDrmSessionManager<>(uuid,
                FrameworkMediaDrm.newInstance(uuid), drmCallback, null, false, 3);
    }

    private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
        int type = Util.inferContentType(!TextUtils.isEmpty(overrideExtension) ? "." + overrideExtension
                : uri.getLastPathSegment());
        DataSource.Factory dataSourceFactory = mediaDataSourceFactory;
        if (offline) {
            Cache downloadCache = com.twentyfouri.media.offline.ExoPlayerDownloadService.getDownloadCache();
            if (downloadCache != null) {
                dataSourceFactory = new CacheDataSourceFactory(
                        downloadCache,
                        dataSourceFactory
                );
            }
            MediaSource mediaSource = buildOfflineMediaSource(getDownloadHelper(uri, null), dataSourceFactory, uri);
            if (mediaSource != null) {
                return mediaSource;
            }
        }
        switch (type) {
            case C.TYPE_SS:
                return new SsMediaSource.Factory(
                        new DefaultSsChunkSource.Factory(dataSourceFactory),
                        dataSourceFactory
                ).setLoadErrorHandlingPolicy(
                        new DefaultLoadErrorHandlingPolicy(minLoadRetryCount)
                ).createMediaSource(uri);
            case C.TYPE_DASH:
                return new DashMediaSource.Factory(
                        new DefaultDashChunkSource.Factory(dataSourceFactory),
                        dataSourceFactory
                ).setLoadErrorHandlingPolicy(
                    new DefaultLoadErrorHandlingPolicy(minLoadRetryCount)
                ).createMediaSource(uri);
            case C.TYPE_HLS:
                return new HlsMediaSource.Factory(
                        dataSourceFactory
                ).setLoadErrorHandlingPolicy(
                        new DefaultLoadErrorHandlingPolicy(minLoadRetryCount)
                ).createMediaSource(uri);
            case C.TYPE_OTHER:
                return new ExtractorMediaSource.Factory(
                        dataSourceFactory
                ).setLoadErrorHandlingPolicy(
                        new DefaultLoadErrorHandlingPolicy(minLoadRetryCount)
                ).createMediaSource(uri);
            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
    }

    private ArrayList<MediaSource> buildTextSources() {
        ArrayList<MediaSource> textSources = new ArrayList<>();
        if (textTracks == null) {
            return textSources;
        }

        for (int i = 0; i < textTracks.size(); ++i) {
            ReadableMap textTrack = textTracks.getMap(i);
            String language = textTrack.getString("language");
            String title = textTrack.hasKey("title")
                    ? textTrack.getString("title") : language + " " + i;
            Uri uri = Uri.parse(textTrack.getString("uri"));
            MediaSource textSource = buildTextSource(title, uri, textTrack.getString("type"),
                    language);
            if (textSource != null) {
                textSources.add(textSource);
            }
        }
        return textSources;
    }

    private MediaSource buildTextSource(String title, Uri uri, String mimeType, String language) {
        Format textFormat = Format.createTextSampleFormat(title, mimeType, Format.NO_VALUE, language);
        return new SingleSampleMediaSource.Factory(mediaDataSourceFactory)
                .createMediaSource(uri, textFormat, C.TIME_UNSET);
    }

    private void releasePlayer() {
        if (player != null) {
            updateResumePosition();
            player.release();
            player.removeListener(seekEventListener);
            player.removeMetadataOutput(this);
            trackSelector = null;
            player = null;
        }
        progressHandler.removeMessages(SHOW_PROGRESS);
        themedReactContext.removeLifecycleEventListener(this);
        audioBecomingNoisyReceiver.removeListener();
        BANDWIDTH_METER.removeEventListener(this);
        mediaSession.release();
    }

    private boolean requestAudioFocus() {
        if (disableFocus || srcUri == null) {
            return true;
        }
        int result = audioManager.requestAudioFocus(this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void setPlayWhenReady(boolean playWhenReady) {
        if (player == null) {
            return;
        }

        if (playWhenReady) {
            boolean hasAudioFocus = requestAudioFocus();
            if (hasAudioFocus) {
                player.setPlayWhenReady(true);
            }
        } else {
            player.setPlayWhenReady(false);
        }
    }

    private void startPlayback() {
        if (player != null) {
            switch (player.getPlaybackState()) {
                case Player.STATE_IDLE:
                case Player.STATE_ENDED:
                    initializePlayer();
                    break;
                case Player.STATE_BUFFERING:
                case Player.STATE_READY:
                    if (!player.getPlayWhenReady()) {
                        setPlayWhenReady(true);
                    }
                    break;
                default:
                    break;
            }

        } else {
            initializePlayer();
        }
        if (!disableFocus) {
            setKeepScreenOn(true);
        }
    }

    private void pausePlayback() {
        if (player != null) {
            if (player.getPlayWhenReady()) {
                setPlayWhenReady(false);
            }
        }
        setKeepScreenOn(false);
    }

    private void stopPlayback() {
        onStopPlayback();
        releasePlayer();
    }

    private void onStopPlayback() {
        if (isFullscreen) {
            setFullscreen(false);
        }
        setKeepScreenOn(false);
        audioManager.abandonAudioFocus(this);
    }

    private void updateResumePosition() {
        resumeWindow = player.getCurrentWindowIndex();
        resumePosition = player.isCurrentWindowSeekable() ? Math.max(0, player.getCurrentPosition())
                : C.TIME_UNSET;
    }

    private void clearResumePosition() {
        resumeWindow = C.INDEX_UNSET;
        resumePosition = C.TIME_UNSET;
    }

    /**
     * Returns a new DataSource factory.
     *
     * @param useBandwidthMeter Whether to set {@link #bandwidthMeter} as a listener to the new
     *                          DataSource factory.
     * @return A new DataSource factory.
     */
    private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
        return DataSourceUtil.getDefaultDataSourceFactory(this.themedReactContext,
                useBandwidthMeter ? BANDWIDTH_METER : null, requestHeaders);
    }

    /**
     * Returns a new HttpDataSource factory.
     *
     * @param useBandwidthMeter Whether to set {@link #bandwidthMeter} as a listener to the new
     *     DataSource factory.
     * @return A new HttpDataSource factory.
     */
    private HttpDataSource.Factory buildHttpDataSourceFactory(boolean useBandwidthMeter) {
        return DataSourceUtil.getDefaultHttpDataSourceFactory(this.themedReactContext, useBandwidthMeter ? bandwidthMeter : null, requestHeaders);
    }


    // AudioManager.OnAudioFocusChangeListener implementation

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                eventEmitter.audioFocusChanged(false);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                eventEmitter.audioFocusChanged(true);
                break;
            default:
                break;
        }

        if (player != null) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                // Lower the volume
                if (!muted) {
                    player.setVolume(audioVolume * 0.8f);
                }
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                // Raise it back to normal
                if (!muted) {
                    player.setVolume(audioVolume * 1);
                }
            }
        }
    }

    // AudioBecomingNoisyListener implementation

    @Override
    public void onAudioBecomingNoisy() {
        eventEmitter.audioBecomingNoisy();
    }

    // Player.EventListener implementation

    @Override
    public void onLoadingChanged(boolean isLoading) {
        // Do nothing.
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        String text = "onStateChanged: playWhenReady=" + playWhenReady + ", playbackState=";
        switch (playbackState) {
            case Player.STATE_IDLE:
                text += "idle";
                eventEmitter.idle();
                clearProgressMessageHandler();
                break;
            case Player.STATE_BUFFERING:
                text += "buffering";
                onBuffering(true);
                clearProgressMessageHandler();
                break;
            case Player.STATE_READY:
                text += "ready";
                eventEmitter.ready();
                onBuffering(false);
                startProgressHandler();
                videoLoaded();
                // Setting the visibility for the playerControlView
                if (playerControlView != null) {
                    playerControlView.show();
                }

                /*
                 * If play is in playing state, but PAUSED prop is TRUE, report
                 * external play/pause state change.
                 */
                if (playWhenReady == isPaused) {
                   eventEmitter.externalPauseToggled(playWhenReady);
                }
                break;
            case Player.STATE_ENDED:
                text += "ended";
                eventEmitter.end();
                onStopPlayback();
                break;
            default:
                text += "unknown";
                break;
        }
        Log.d(TAG, text);
    }

    private void startProgressHandler() {
        progressHandler.sendEmptyMessage(SHOW_PROGRESS);
    }

    /*
        The progress message handler will duplicate recursions of the onProgressMessage handler
        on change of player state from any state to STATE_READY with playWhenReady is true (when
        the video is not paused). This clears all existing messages.
     */
    private void clearProgressMessageHandler() {
         progressHandler.removeMessages(SHOW_PROGRESS);
    }

    private void videoLoaded() {
        if (loadVideoStarted) {
            loadVideoStarted = false;
            setSelectedAudioTrack(audioTrackType, audioTrackValue);
            setSelectedVideoTrack(videoTrackType, videoTrackValue);
            setSelectedTextTrack(textTrackType, textTrackValue);
            Format videoFormat = player.getVideoFormat();
            int width = videoFormat != null ? videoFormat.width : 0;
            int height = videoFormat != null ? videoFormat.height : 0;
            eventEmitter.load(player.getDuration(), player.getCurrentPosition(), width, height,
                    getAudioTrackInfo(), getTextTrackInfo(), getVideoTrackInfo());
        }
    }

    private WritableArray getAudioTrackInfo() {
        WritableArray audioTracks = Arguments.createArray();

        MappingTrackSelector.MappedTrackInfo info = trackSelector.getCurrentMappedTrackInfo();
        int index = getTrackRendererIndex(C.TRACK_TYPE_AUDIO);
        if (info == null || index == C.INDEX_UNSET) {
            return audioTracks;
        }

        TrackGroupArray groups = info.getTrackGroups(index);
        for (int i = 0; i < groups.length; ++i) {
            Format format = groups.get(i).getFormat(0);
            WritableMap audioTrack = Arguments.createMap();
            audioTrack.putInt("index", i);
            audioTrack.putString("title", format.id != null ? format.id : "");
            audioTrack.putString("type", format.sampleMimeType);
            audioTrack.putString("language", format.language != null ? format.language : "");
            audioTrack.putString("bitrate", format.bitrate == Format.NO_VALUE ? ""
                                    : String.format(Locale.US, "%.2fMbps", format.bitrate / 1000000f));
            audioTracks.pushMap(audioTrack);
        }
        return audioTracks;
    }
    private WritableArray getVideoTrackInfo() {
        WritableArray videoTracks = Arguments.createArray();

        MappingTrackSelector.MappedTrackInfo info = trackSelector.getCurrentMappedTrackInfo();
        int index = getTrackRendererIndex(C.TRACK_TYPE_VIDEO);
        if (info == null || index == C.INDEX_UNSET) {
            return videoTracks;
        }

        TrackGroupArray groups = info.getTrackGroups(index);
        for (int i = 0; i < groups.length; ++i) {
            TrackGroup group = groups.get(i);

            for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                Format format = group.getFormat(trackIndex);
                WritableMap videoTrack = Arguments.createMap();
                videoTrack.putInt("width", format.width == Format.NO_VALUE ? 0 : format.width);
                videoTrack.putInt("height",format.height == Format.NO_VALUE ? 0 : format.height);
                videoTrack.putInt("bitrate", format.bitrate == Format.NO_VALUE ? 0 : format.bitrate);
                videoTrack.putString("codecs", format.codecs != null ? format.codecs : "");
                videoTrack.putString("trackId",
                        format.id == null ? String.valueOf(trackIndex) : format.id);
                videoTracks.pushMap(videoTrack);
            }
        }
        return videoTracks;
    }

    private WritableArray getTextTrackInfo() {
        WritableArray textTracks = Arguments.createArray();

        MappingTrackSelector.MappedTrackInfo info = trackSelector.getCurrentMappedTrackInfo();
        int index = getTrackRendererIndex(C.TRACK_TYPE_TEXT);
        if (info == null || index == C.INDEX_UNSET) {
            return textTracks;
        }

        TrackGroupArray groups = info.getTrackGroups(index);
        for (int i = 0; i < groups.length; ++i) {
             Format format = groups.get(i).getFormat(0);
             WritableMap textTrack = Arguments.createMap();
             textTrack.putInt("index", i);
             textTrack.putString("title", format.id != null ? format.id : "");
             textTrack.putString("type", format.sampleMimeType);
             textTrack.putString("language", format.language != null ? format.language : "");
             textTracks.pushMap(textTrack);
        }
        return textTracks;
    }

    private void onBuffering(boolean buffering) {
        if (isBuffering == buffering) {
            return;
        }

        isBuffering = buffering;
        if (buffering) {
            eventEmitter.buffering(true);
        } else {
            eventEmitter.buffering(false);
        }
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        if (playerNeedsSource) {
            // This will only occur if the user has performed a seek whilst in the error state. Update the
            // resume position so that if the user then retries, playback will resume from the position to
            // which they seeked.
            updateResumePosition();
        }
        // When repeat is turned on, reaching the end of the video will not cause a state change
        // so we need to explicitly detect it.
        if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION
                && player.getRepeatMode() == Player.REPEAT_MODE_ONE) {
            eventEmitter.end();
        }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
        // Do nothing.
    }

    @Override
    public void onSeekProcessed() {
        eventEmitter.seek(player.getCurrentPosition(), seekTime);
        seekTime = C.TIME_UNSET;
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
        // Do nothing.
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        // Do nothing.
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        // Do Nothing.
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters params) {
        eventEmitter.playbackRateChange(params.speed);
    }

    @Override
    public void onPlayerError(ExoPlaybackException e) {
        String errorString = null;
        Exception ex = e;
        if (e.type == ExoPlaybackException.TYPE_RENDERER) {
            Exception cause = e.getRendererException();
            if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
                // Special case for decoder initialization failures.
                MediaCodecRenderer.DecoderInitializationException decoderInitializationException =
                        (MediaCodecRenderer.DecoderInitializationException) cause;
                if (decoderInitializationException.decoderName == null) {
                    if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                        errorString = getResources().getString(R.string.error_querying_decoders);
                    } else if (decoderInitializationException.secureDecoderRequired) {
                        errorString = getResources().getString(R.string.error_no_secure_decoder,
                                decoderInitializationException.mimeType);
                    } else {
                        errorString = getResources().getString(R.string.error_no_decoder,
                                decoderInitializationException.mimeType);
                    }
                } else {
                    errorString = getResources().getString(R.string.error_instantiating_decoder,
                            decoderInitializationException.decoderName);
                }
            }
        }
        else if (e.type == ExoPlaybackException.TYPE_SOURCE) {
            ex = e.getSourceException();
            errorString = getResources().getString(R.string.unrecognized_media_format);
        }
        if (errorString != null) {
            eventEmitter.error(errorString, ex);
        }
        playerNeedsSource = true;
        if (isBehindLiveWindow(e)) {
            clearResumePosition();
            initializePlayer();
        } else {
            updateResumePosition();
        }
    }

    private static boolean isBehindLiveWindow(ExoPlaybackException e) {
        if (e.type != ExoPlaybackException.TYPE_SOURCE) {
            return false;
        }
        Throwable cause = e.getSourceException();
        while (cause != null) {
            if (cause instanceof BehindLiveWindowException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    public int getTrackRendererIndex(int trackType) {
        if (player != null) {
            int rendererCount = player.getRendererCount();
            for (int rendererIndex = 0; rendererIndex < rendererCount; rendererIndex++) {
                if (player.getRendererType(rendererIndex) == trackType) {
                    return rendererIndex;
                }
            }
        }
        return C.INDEX_UNSET;
    }

    @Override
    public void onMetadata(Metadata metadata) {
        eventEmitter.timedMetadata(metadata);
    }

    // ReactExoplayerViewManager public api

    public void setSrc(final Uri uri, final String extension, Map<String, String> headers) {
        if (uri != null) {
            boolean isOriginalSourceNull = srcUri == null;
            boolean isSourceEqual = uri.equals(srcUri);

            this.srcUri = uri;
            this.extension = extension;
            this.requestHeaders = headers;
            this.mediaDataSourceFactory =
                    DataSourceUtil.getDefaultDataSourceFactory(this.themedReactContext, BANDWIDTH_METER,
                            this.requestHeaders);

            if (!isOriginalSourceNull && !isSourceEqual) {
                reloadSource();
            }
        }
    }

    public void setProgressUpdateInterval(final float progressUpdateInterval) {
        mProgressUpdateInterval = progressUpdateInterval;
    }

    public void setReportBandwidth(boolean reportBandwidth) {
        mReportBandwidth = reportBandwidth;
    }

    public void setRawSrc(final Uri uri, final String extension) {
        if (uri != null) {
            boolean isOriginalSourceNull = srcUri == null;
            boolean isSourceEqual = uri.equals(srcUri);

            this.srcUri = uri;
            this.extension = extension;
            this.mediaDataSourceFactory = buildDataSourceFactory(true);

            if (!isOriginalSourceNull && !isSourceEqual) {
                reloadSource();
            }
        }
    }

    public void setTextTracks(ReadableArray textTracks) {
        this.textTracks = textTracks;
        reloadSource();
    }

    private void reloadSource() {
        playerNeedsSource = true;
        initializePlayer();
    }

    public void setResizeModeModifier(@ResizeMode.Mode int resizeMode) {
        exoPlayerView.setResizeMode(resizeMode);
    }

    private void applyModifiers() {
        setRepeatModifier(repeat);
        setMutedModifier(muted);
    }

    public void setRepeatModifier(boolean repeat) {
        if (player != null) {
            if (repeat) {
                player.setRepeatMode(Player.REPEAT_MODE_ONE);
            } else {
                player.setRepeatMode(Player.REPEAT_MODE_OFF);
            }
        }
        this.repeat = repeat;
    }

    public void setSelectedTrack(int trackType, String type, Dynamic value) {
        if (player == null) return;
        int rendererIndex = getTrackRendererIndex(trackType);
        if (rendererIndex == C.INDEX_UNSET) {
            return;
        }
        MappingTrackSelector.MappedTrackInfo info = trackSelector.getCurrentMappedTrackInfo();
        if (info == null) {
            return;
        }

        TrackGroupArray groups = info.getTrackGroups(rendererIndex);
        int groupIndex = C.INDEX_UNSET;
        int[] tracks = {0} ;

        if (TextUtils.isEmpty(type)) {
            type = "default";
        }

        DefaultTrackSelector.Parameters disableParameters = trackSelector.getParameters()
                .buildUpon()
                .setRendererDisabled(rendererIndex, true)
                .build();

        if (type.equals("disabled")) {
            trackSelector.setParameters(disableParameters);
            return;
        } else if (type.equals("language")) {
            for (int i = 0; i < groups.length; ++i) {
                Format format = groups.get(i).getFormat(0);
                if (format.language != null && format.language.equals(value.asString())) {
                    groupIndex = i;
                    break;
                }
            }
        } else if (type.equals("title")) {
            for (int i = 0; i < groups.length; ++i) {
                Format format = groups.get(i).getFormat(0);
                if (format.id != null && format.id.equals(value.asString())) {
                    groupIndex = i;
                    break;
                }
            }
        } else if (type.equals("index")) {
            if (value.asInt() < groups.length) {
                groupIndex = value.asInt();
            }
        } else if (type.equals("resolution")) {
            int height = value.asInt();
            for (int i = 0; i < groups.length; ++i) { // Search for the exact height
                TrackGroup group = groups.get(i);
                for (int j = 0; j < group.length; j++) {
                    Format format = group.getFormat(j);
                    if (format.height == height) {
                        groupIndex = i;
                        tracks[0] = j;
                        break;
                    }
                }
            }
        } else if (rendererIndex == C.TRACK_TYPE_TEXT && Util.SDK_INT > 18) { // Text default
            // Use system settings if possible
            CaptioningManager captioningManager
                    = (CaptioningManager)themedReactContext.getSystemService(Context.CAPTIONING_SERVICE);
            if (captioningManager != null && captioningManager.isEnabled()) {
                groupIndex = getGroupIndexForDefaultLocale(groups);
            }
        } else if (rendererIndex == C.TRACK_TYPE_AUDIO) { // Audio default
            groupIndex = getGroupIndexForDefaultLocale(groups);
        }

        if (groupIndex == C.INDEX_UNSET && trackType == C.TRACK_TYPE_VIDEO && groups.length != 0) { // Video auto
            // Add all tracks as valid options for ABR to choose from
            TrackGroup group = groups.get(0);
            tracks = new int[group.length];
            groupIndex = 0;
            for (int j = 0; j < group.length; j++) {
                tracks[j] = j;
            }
        }

        if (groupIndex == C.INDEX_UNSET) {
            trackSelector.setParameters(disableParameters);
            return;
        }

        DefaultTrackSelector.Parameters selectionParameters = trackSelector.getParameters()
                .buildUpon()
                .setRendererDisabled(rendererIndex, false)
                .setSelectionOverride(rendererIndex, groups,
                        new DefaultTrackSelector.SelectionOverride(groupIndex, tracks))
                .build();
        trackSelector.setParameters(selectionParameters);
    }

    private int getGroupIndexForDefaultLocale(TrackGroupArray groups) {
        if (groups.length == 0){
            return C.INDEX_UNSET;
        }

        int groupIndex = 0; // default if no match
        String locale2 = Locale.getDefault().getLanguage(); // 2 letter code
        String locale3 = Locale.getDefault().getISO3Language(); // 3 letter code
        for (int i = 0; i < groups.length; ++i) {
            Format format = groups.get(i).getFormat(0);
            String language = format.language;
            if (language != null && (language.equals(locale2) || language.equals(locale3))) {
                groupIndex = i;
                break;
            }
        }
        return groupIndex;
    }

    public void setSelectedVideoTrack(String type, Dynamic value) {
        videoTrackType = type;
        videoTrackValue = value;
        setSelectedTrack(C.TRACK_TYPE_VIDEO, videoTrackType, videoTrackValue);
    }

    public void setSelectedAudioTrack(String type, Dynamic value) {
        audioTrackType = type;
        audioTrackValue = value;
        setSelectedTrack(C.TRACK_TYPE_AUDIO, audioTrackType, audioTrackValue);
    }

    public void setSelectedTextTrack(String type, Dynamic value) {
        textTrackType = type;
        textTrackValue = value;
        setSelectedTrack(C.TRACK_TYPE_TEXT, textTrackType, textTrackValue);
    }

    public void setPausedModifier(boolean paused) {
        isPaused = paused;
        if (player != null) {
            if (!paused) {
                startPlayback();
            } else {
                pausePlayback();
            }
        }
    }

    public void setMutedModifier(boolean muted) {
        this.muted = muted;
        audioVolume = muted ? 0.f : 1.f;
        if (player != null) {
            player.setVolume(audioVolume);
        }
    }


    public void setVolumeModifier(float volume) {
        audioVolume = volume;
        if (player != null) {
            player.setVolume(audioVolume);
        }
    }

    public void seekTo(long positionMs) {
        if (player != null) {
            seekTime = positionMs;
            player.seekTo(positionMs);
        }
    }

    public void setRateModifier(float newRate) {
        rate = newRate;

        if (player != null) {
            PlaybackParameters params = new PlaybackParameters(rate, 1f);
            player.setPlaybackParameters(params);
        }
    }

    public void setMaxBitRateModifier(int newMaxBitRate) {
        maxBitRate = newMaxBitRate;
        if (player != null) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setMaxVideoBitrate(maxBitRate == 0 ? Integer.MAX_VALUE : maxBitRate));
        }
    }

    public void setMinLoadRetryCountModifier(int newMinLoadRetryCount) {
        minLoadRetryCount = newMinLoadRetryCount;
        releasePlayer();
        initializePlayer();
    }

    public void setPlayInBackground(boolean playInBackground) {
        this.playInBackground = playInBackground;
    }

    public void setDisableFocus(boolean disableFocus) {
        this.disableFocus = disableFocus;
    }

    public void setFullscreen(boolean fullscreen) {
        if (fullscreen == isFullscreen) {
            return; // Avoid generating events when nothing is changing
        }
        isFullscreen = fullscreen;

        Activity activity = themedReactContext.getCurrentActivity();
        if (activity == null) {
            return;
        }
        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        int uiOptions;
        if (isFullscreen) {
            if (Util.SDK_INT >= 19) { // 4.4+
                uiOptions = SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | SYSTEM_UI_FLAG_FULLSCREEN;
            } else {
                uiOptions = SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | SYSTEM_UI_FLAG_FULLSCREEN;
            }
            eventEmitter.fullscreenWillPresent();
            decorView.setSystemUiVisibility(uiOptions);
            eventEmitter.fullscreenDidPresent();
        } else {
            uiOptions = View.SYSTEM_UI_FLAG_VISIBLE;
            eventEmitter.fullscreenWillDismiss();
            decorView.setSystemUiVisibility(uiOptions);
            eventEmitter.fullscreenDidDismiss();
        }
    }

    public void setUseTextureView(boolean useTextureView) {
        boolean finallyUseTextureView = useTextureView && this.drmUUID == null;
        exoPlayerView.setUseTextureView(finallyUseTextureView);
    }

    public void setHideShutterView(boolean hideShutterView) {
        exoPlayerView.setHideShutterView(hideShutterView);
    }

    public void setBufferConfig(int newMinBufferMs, int newMaxBufferMs, int newBufferForPlaybackMs, int newBufferForPlaybackAfterRebufferMs) {
        minBufferMs = newMinBufferMs;
        maxBufferMs = newMaxBufferMs;
        bufferForPlaybackMs = newBufferForPlaybackMs;
        bufferForPlaybackAfterRebufferMs = newBufferForPlaybackAfterRebufferMs;
        releasePlayer();
        initializePlayer();
    }

    public void setDrmType(UUID drmType) {
        this.drmUUID = drmType;
    }

    public void setDrmLicenseUrl(String licenseUrl){
        this.drmLicenseUrl = licenseUrl;
    }

    public void setDrmLicenseHeader(String[] header){
        this.drmLicenseHeader = header;
    }

    public void setOffline(Boolean offline) { this.offline = offline; }


    @Override
    public void onDrmKeysLoaded() {
        Log.d("DRM Info", "onDrmKeysLoaded");
    }

    @Override
    public void onDrmSessionManagerError(Exception e) {
        Log.d("DRM Info", "onDrmSessionManagerError");
        eventEmitter.error("onDrmSessionManagerError", e);
    }

    @Override
    public void onDrmKeysRestored() {
        Log.d("DRM Info", "onDrmKeysRestored");
    }

    @Override
    public void onDrmKeysRemoved() {
        Log.d("DRM Info", "onDrmKeysRemoved");
    }

    /**
     * Handling controls prop
     *
     * @param controls  Controls prop, if true enable controls, if false disable them
     */
    public void setControls(boolean controls) {
        this.controls = controls;
        if (player == null || exoPlayerView == null) return;
        if (controls) {
            addPlayerControl();
        } else {
            int indexOfPC = indexOfChild(playerControlView);
            if (indexOfPC != -1) {
                removeViewAt(indexOfPC);
            }
        }
    }

    /**
     * Handling showPictureInPictureOnLeave prop.
     *
     * @param showPictureInPictureOnLeaveProp If true, enter pip mode when pressing home or recent HW button.
     */
    public void setShowPictureInPictureOnLeave(boolean showPictureInPictureOnLeaveProp) {
        showPictureInPictureOnLeave = showPictureInPictureOnLeaveProp;
    }

    /**
     * Handling pip prop.
     *
     * @param pictureInPicture  Pip prop, if true, enter PIP mode.
     */
    public void setPictureInPicture(boolean pictureInPicture) {
        if (!isInPictureInPictureMode && pictureInPicture) {
            this.enterPictureInPictureMode();
        }
        isInPictureInPictureMode = pictureInPicture;
    }

    /**
     * PIP handled, for N devices that support it, not "officially".
     */
    public void enterPictureInPictureMode() {
        PackageManager packageManager = themedReactContext.getPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && packageManager
                .hasSystemFeature(
                        PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            long videoPosition = player.getCurrentPosition();
            Activity activity = themedReactContext.getCurrentActivity();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PictureInPictureParams.Builder params = new PictureInPictureParams.Builder();
                activity.enterPictureInPictureMode(params.build());
            } else {
                activity.enterPictureInPictureMode();
            }
        }
    }

    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        eventEmitter.pictureInPictureModeChanged(isInPictureInPictureMode);
    }

    public void setSubtitleForcedMiddle(final boolean forced) {
        if (exoPlayerView != null) {
            exoPlayerView.setSubtitleForcedMiddle(forced);
        }        
    }

    public void save(ReadableMap options, Promise promise) {
        promise.resolve("ok");
    }

    private DownloadHelper getDownloadHelper(Uri uri, String typeOverride) throws IllegalStateException {
        if (typeOverride != null && typeOverride.isEmpty()) {
            typeOverride = null;
        }
        int type = Util.inferContentType(uri, typeOverride);
        RenderersFactory renderersFactory =
                new DefaultRenderersFactory(getContext())
                        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
        DataSource.Factory dataSourceFactory = buildDataSourceFactory(false);
        DownloadHelper downloadHelper;
        switch (type) {
            case C.TYPE_DASH:
                downloadHelper = DownloadHelper.forDash(uri, dataSourceFactory, renderersFactory);
                break;
            case C.TYPE_SS:
                downloadHelper = DownloadHelper.forSmoothStreaming(uri, dataSourceFactory, renderersFactory);
                break;
            case C.TYPE_HLS:
                downloadHelper = DownloadHelper.forHls(uri, dataSourceFactory, renderersFactory);
                break;
            case C.TYPE_OTHER:
                downloadHelper = DownloadHelper.forProgressive(uri);
                break;
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
        return downloadHelper;
    }

    private MediaSource buildOfflineMediaSource(DownloadHelper helper, DataSource.Factory dataFactory, Uri uri) {
        DownloadRequest request = (DownloadRequest)ExoPlayerDownloadService.downloadRequests.get(uri.toString());
        return helper.createMediaSource(request, dataFactory);
    }
}
