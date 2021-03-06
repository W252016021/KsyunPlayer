package com.ksyun.player;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ksyun.media.player.IMediaPlayer;
import com.ksyun.media.player.KSYMediaPlayer;
import com.ksyun.media.player.KSYTextureView;
import com.ksyun.media.player.misc.ITrackInfo;
import com.ksyun.media.player.misc.KSYTrackInfo;
import com.ksyun.player.adapter.TrackListAdapter;
import com.ksyun.player.bean.TrackEntity;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class KsyunVodActivity extends AppCompatActivity {
    private KSYTextureView mVideoView = null;
    private View mLoadView = null;
    private ImageView v_thumb;
    private TextView loadingText;
    private boolean mIsSystemCallPause;
    private boolean mIsNeedUpdateUIProgress;
    private Handler mainUIHandler = null;

    private LinearLayout header_bar;
    private LinearLayout ctrl_bar;

    private SeekBar seekBar;
    private TextView tv_title;
    private TextView tv_position;
    private TextView tv_duration;
    private TextView tv_info;

    private ImageView v_back;
    private ImageView v_play;
    private ImageView v_rotate;
    private ImageView v_lock;
    private ImageView v_track;
    private ImageView v_speed;
    private ImageView v_scaling;
    private ImageView v_timeText;

    private TextView timeText;
    private boolean mIsTouchingSeekbar = false;

    private int videoScalingMode = KSYMediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT;

    private int maxDuration;
    private int nowPosition;
    private int distanceX;
    private float clickX;
    private float clickY;
    private float moveY;
    private int distanceBritness;
    private int currentVolume;
    private int distanceVolume;
    private int dangqianliandu;
    private int clickVolume;
    private int maxVolume;
    private int action;
    private AudioManager audioManager;
    private boolean locked = false;//屏幕锁定状态
    private int width;
    private int height;

    private String url;
    private String title;
    private boolean isLive = false;

    private int playableRangeStart = -1;
    private int playableRangeEnd = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ksyun_vod);
        PlayerDataBaseHelper.openDataBase(this);//打开播放记录数据库
        Log.e("info", "onCreate");
        initView();
        initBatteryReceiver();

        url = getIntent().getStringExtra("url");
        title = getIntent().getStringExtra("title");
        videoScalingMode = getIntent().getIntExtra("scale", 1);
        isLive = getIntent().getBooleanExtra("live", false);
        playableRangeStart = getIntent().getIntExtra("playableStart", -1);
        playableRangeEnd = getIntent().getIntExtra("playableEnd", -1);
        if (playableRangeStart != -1 && playableRangeEnd != -1) {
            mVideoView.setPlayableRanges(playableRangeStart, playableRangeEnd);
        }
        openVideo(title, url);
    }

    private void initView() {
        mLoadView = (View) findViewById(R.id.mLoadView);
        loadingText = (TextView) mLoadView.findViewById(R.id.loading_text);
        v_thumb = (ImageView) findViewById(R.id.v_thumb);
        mVideoView = (KSYTextureView) findViewById(R.id.texture_view);
        mVideoView.setKeepScreenOn(true);
        mVideoView.setTimeout(120, 120);
        //mVideoView.setPlayableRanges(0, 60000);//设置试看时间

        header_bar = (LinearLayout) findViewById(R.id.header_bar);
        ctrl_bar = (LinearLayout) findViewById(R.id.ctrl_bar);

        seekBar = (SeekBar) findViewById(R.id.seekbar);
        tv_title = (TextView) findViewById(R.id.v_title);
        tv_position = (TextView) findViewById(R.id.tv_position);
        tv_duration = (TextView) findViewById(R.id.tv_duration);
        tv_info = (TextView) findViewById(R.id.tv_info);
        v_back = (ImageView) findViewById(R.id.v_back);
        v_play = (ImageView) findViewById(R.id.v_play);
        v_rotate = (ImageView) findViewById(R.id.v_rotate);
        v_lock = (ImageView) findViewById(R.id.v_player_lock);
        v_track = (ImageView) findViewById(R.id.v_track);
        v_scaling = (ImageView) findViewById(R.id.v_scaling);
        v_speed = (ImageView) findViewById(R.id.v_speed);
        v_timeText=(ImageView) findViewById(R.id.v_timeText);
        timeText = findViewById(R.id.timeText);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        this.width = displayMetrics.widthPixels;
        this.height = displayMetrics.heightPixels;

        this.audioManager = ((AudioManager) getSystemService(AUDIO_SERVICE));
        if (audioManager != null) {
            this.maxVolume = this.audioManager.getStreamMaxVolume(3);
            this.maxVolume *= 6;
        }
        float f = this.currentVolume * 6;
        try {
            int k = Settings.System.getInt(getContentResolver(), "screen_brightness");
            f = 1.0F * k / 255.0F;
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        this.distanceBritness = ((int) (f * 100.0F));
        this.dangqianliandu = ((int) (f * 100.0F));

        initVideoListener();
        hideBottomUIMenu();


    }

    private void openVideo(String title, String url) {
        try {
            tv_title.setText(TextUtils.isEmpty(title) ? url : title.trim());
            mLoadView.setVisibility(View.VISIBLE);
            mVideoView.setDataSource(url);
            mVideoView.prepareAsync();
        } catch (IOException e) {
            Toast.makeText(this, "Error: " + e.toString(), Toast.LENGTH_SHORT).show();
            finishPlay();
            finish();
        }
    }

    private void initVideoListener() {
        mVideoView.setOnPreparedListener(iMediaPlayer -> {
            Log.e("info", "onPrepared");
            mLoadView.setVisibility(View.GONE);
            mVideoView.setVideoScalingMode(videoScalingMode);
            mVideoView.start();
            if (!isLive) { //如果不是直播就跳转到上次播放位置
                long position = PlayerDataBaseHelper.getPosition(url);
                if (position > 0) {
                    mVideoView.seekTo(position);
                }
            }
            v_play.setImageResource(R.drawable.v_play_pause);
            startUIUpdateThread();
            if (playableRangeStart != -1 && playableRangeEnd != -1) {
                Log.e("info", "Math.abs: " + Math.abs(playableRangeEnd - playableRangeStart));
                Toast.makeText(this, "您可以试看: " + KsyunUtils.generateTime(Math.abs(playableRangeEnd - playableRangeStart)), Toast.LENGTH_SHORT).show();
            }
        });

        mVideoView.setOnErrorListener((iMediaPlayer, what, extra) -> {
            showError(what);
            Log.e("info", "OnErrorListener, Error:" + what + ",extra:" + extra);
            return false;
        });

        mVideoView.setOnBufferingUpdateListener((iMediaPlayer, i) -> {
            if (!mIsTouchingSeekbar) {
                loadingText.setText("缓冲: " + i + "%");
                mLoadView.setVisibility(i == 100 ? View.GONE : View.VISIBLE);
            }
        });

        mVideoView.setOnInfoListener((iMediaPlayer, i, i1) -> {
            switch (i) {
                case KSYMediaPlayer.MEDIA_INFO_BUFFERING_START:
                    Log.e("info", "开始缓冲数据");
                    break;
                case KSYMediaPlayer.MEDIA_INFO_BUFFERING_END:
                    Log.e("info", "数据缓冲完毕");
                    break;
                case KSYMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START:
                    Log.e("info", "开始播放音频");
                    break;
                case KSYMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                    Log.e("info", "开始渲染视频");
                    break;
                case KSYMediaPlayer.MEDIA_INFO_SUGGEST_RELOAD:
                    // 播放SDK有做快速开播的优化，在流的音视频数据交织并不好时，可能只找到某一个流的信息
                    // 当播放器读到另一个流的数据时会发出此消息通知
                    // 请务必调用reload接口
                    Log.e("info", "MEDIA_INFO_SUGGEST_RELOAD");
                    break;
                case KSYMediaPlayer.MEDIA_INFO_RELOADED:
                    Log.e("info", "reload成功的消息通知");
                    break;
                default:
                    Log.e("info", "OnInfo: " + i);
                    break;
            }
            return false;
        });

        mVideoView.setOnMessageListener((iMediaPlayer, bundle) ->
                Log.e("info", "name:" + bundle.toString())
        );
        mVideoView.setOnTimedTextListener((iMediaPlayer, s) -> {
            Log.e("info", "onTimedText: " + s);
            timeText.setText(s);
        });
        mVideoView.setOnCompletionListener(iMediaPlayer -> {
            finishPlay();
            finish();
        });

        /*播放器手势控制*/
        mVideoView.setOnTouchListener((v, motionEvent) -> {
            float x = motionEvent.getX();
            float y = motionEvent.getY();
            float f;
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    maxDuration = (int) mVideoView.getDuration() / 1000;
                    nowPosition = (int) mVideoView.getCurrentPosition() / 1000;
                    clickX = x;
                    clickY = y;
                    action = 1;
                    currentVolume = audioManager.getStreamVolume(3);
                    clickVolume = (currentVolume * 6);
                    moveY = y;
                    break;
                case MotionEvent.ACTION_UP:
                    new Handler().postDelayed(() -> {
                        tv_info.setVisibility(GONE);
                        switch (action) {
                            case 2:
                                if (!locked) {
                                    if (mVideoView.getDuration() <= 5) {
                                        return;
                                    }
                                    mVideoView.seekTo(distanceX * 1000);
                                    break;
                                }
                            case 3:
                                dangqianliandu = distanceBritness;
                                break;
                            case 4:
                                break;
                            default:
                                onClickEmptyArea();
                                break;
                        }
                    }, 100L);

                case MotionEvent.ACTION_MOVE:
                    if (!locked) {
                        f = Math.abs(x - clickX);/*x方向滑动的绝对距离*/
                        float abs = Math.abs(y - clickY);/*取滑动y方向的绝对距离*/
                        if (action == 1) {
                            if (f > 50.0f && abs < 50.0f) {
                                action = 2;/*快进*/
                            }
                            if (f < 50.0f && abs > 50.0f && ((double) clickX) < ((double) width) * 0.25d) {
                                action = 3;/*亮度*/
                            }
                            if (f < 50.0f && abs > 50.0f && ((double) clickX) > ((double) width) * 0.75d) {
                                action = 4;/*音量*/
                            }
                        }
                        switch (action) {
                            case 2:
                                distanceX = (int) ((float) ((((double) (((x - clickX) / ((float) width)) * ((float) maxDuration))) * 0.3d) + ((double) nowPosition)));
                                if (distanceX < 0) {
                                    distanceX = 0;
                                }
                                if (distanceX > maxDuration) {
                                    distanceX = maxDuration;
                                }
                                tv_info.setVisibility(VISIBLE);
                                tv_info.setText(formatTimeText(distanceX) + "/" + formatTimeText(maxDuration));
                                break;
                            case 3:
                                float f6 = (y - moveY) * 100.0F / height;

                                distanceBritness = (dangqianliandu - (int) f6);
                                if (distanceBritness > 100) {
                                    distanceBritness = 100;
                                }
                                if (distanceBritness < 7) {
                                    distanceBritness = 7;
                                }
                                tv_info.setVisibility(VISIBLE);
                                int j = (distanceBritness - 7) * 100 / 93;
                                tv_info.setText("亮度：" + j + "%");
                                setBrightness(distanceBritness);
                                break;
                            case 4:
                                float f7 = (y - moveY) * 100.0F / height;

                                distanceVolume = (clickVolume - (int) f7);
                                if (distanceVolume > maxVolume) {
                                    distanceVolume = maxVolume;
                                }
                                if (distanceVolume < 0) {
                                    distanceVolume = 0;
                                }
                                tv_info.setVisibility(VISIBLE);
                                int k = distanceVolume * 100 / maxVolume;
                                tv_info.setText("音量：" + k + "%");
                                int m = distanceVolume / 6;
                                audioManager.setStreamVolume(3, m, 0);
                                break;
                            default:
                                v.performClick();
                                break;
                        }
                    }
                    break;
            }
            return true;
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (null == mVideoView || !fromUser) {
                    return;
                }
                mIsTouchingSeekbar = true;
                userSeekPlayProgress(progress, seekBar.getMax());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                /* Log.e(DEBUG_TAG, "onStartTrackingTouch"); */
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                tv_info.setVisibility(GONE);
                mIsTouchingSeekbar = false;
                startUIUpdateThread();
            }
        });

        v_back.setOnClickListener(v -> {
            finishPlay();
            finish();
        });


        v_play.setOnClickListener(v -> {
            if (mVideoView.isPlaying()) {
                v_play.setImageResource(R.drawable.v_play_arrow);
                mVideoView.pause();
            } else {
                v_play.setImageResource(R.drawable.v_play_pause);
                mVideoView.start();
            }
        });

        v_rotate.setOnClickListener(v -> {
            Configuration mConfiguration = getResources().getConfiguration();  /* 获取设置的配置信息 */
            int ori = mConfiguration.orientation;                           /* 获取屏幕方向 */
            if (ori == 2) {
                /* 横屏 -> 竖屏 */
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);            /* 强制为竖屏 */
            } else if (ori == 1) {
                /* 竖屏 -> 横屏 */
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);    /* 强制为横屏 */
            }
        });

        v_lock.setOnClickListener(v -> {
            if (!locked) {
                locked = true;
                hideCtrlBar();
                v_lock.setImageResource(R.drawable.v_player_locked);
            } else {
                v_lock.setImageResource(R.drawable.v_player_unlocked);
                locked = false;
                showCtrlBar();
            }
        });

        v_track.setOnClickListener(v ->
                showTrackListWindow()
        );

        v_scaling.setOnClickListener(v ->
                showScalingTrackWindow()
        );
        v_speed.setOnClickListener(v ->
                showSpeedTrackWindow()
        );

        v_timeText.setOnClickListener(view -> showTimeTextListWindow());
        mainUIHandler = new MyHandler(this);
    }

    /**
     * 手势控制单击空白操作
     **/
    private void onClickEmptyArea() {
        if (locked) {
            if (v_lock.getVisibility() != VISIBLE) {
                v_lock.setVisibility(VISIBLE);
                Animation animation3 = AnimationUtils.loadAnimation(KsyunVodActivity.this, R.anim.anim_left_in);
                v_lock.startAnimation(animation3);
            } else {
                Animation animation3 = AnimationUtils.loadAnimation(KsyunVodActivity.this, R.anim.anim_left_out);
                v_lock.startAnimation(animation3);
                v_lock.setVisibility(GONE);
            }
        } else {
            if (header_bar.getVisibility() == GONE) {
                showCtrlBar();
            } else {
                hideBottomUIMenu();
                hideCtrlBar();
            }
        }
    }

    /**
     * 隐藏显示控制栏
     **/
    private void showCtrlBar() {
        header_bar.setVisibility(VISIBLE);
        Animation animation = AnimationUtils.loadAnimation(KsyunVodActivity.this, R.anim.anim_top_in);
        header_bar.startAnimation(animation);

        ctrl_bar.setVisibility(VISIBLE);
        Animation animation2 = AnimationUtils.loadAnimation(KsyunVodActivity.this, R.anim.anim_bottom_in);
        ctrl_bar.startAnimation(animation2);

        v_lock.setVisibility(VISIBLE);
        Animation animation3 = AnimationUtils.loadAnimation(KsyunVodActivity.this, R.anim.anim_left_in);
        v_lock.startAnimation(animation3);

        v_rotate.setVisibility(VISIBLE);
        v_rotate.startAnimation(animation2);
    }

    private void hideCtrlBar() {
        Animation animation = AnimationUtils.loadAnimation(KsyunVodActivity.this, R.anim.anim_top_out);
        header_bar.startAnimation(animation);
        header_bar.setVisibility(GONE);
        Animation animation2 = AnimationUtils.loadAnimation(KsyunVodActivity.this, R.anim.anim_bottom_out);
        ctrl_bar.startAnimation(animation2);
        ctrl_bar.setVisibility(GONE);
        Animation animation3 = AnimationUtils.loadAnimation(KsyunVodActivity.this, R.anim.anim_left_out);
        v_lock.startAnimation(animation3);
        v_lock.setVisibility(GONE);

        v_rotate.startAnimation(animation2);
        v_rotate.setVisibility(GONE);
    }


    /**
     * 避免拖动粒度过细，拖动时频繁定位影响体验
     **/
    private void userSeekPlayProgress(int seekPostionMs, int max) {
        int currentPlayPos = (int) mVideoView.getCurrentPosition();
        boolean isChangeOverSeekGate = isOverSeekGate(seekPostionMs * 1000, currentPlayPos);
        if (!isChangeOverSeekGate) {
            return;
        }
        mIsTouchingSeekbar = true;
        stopUIUpdateThread();
        seekBar.setProgress(seekPostionMs);
        tv_info.setText(formatTimeText(seekPostionMs) + "/" + formatTimeText(max));
        tv_info.setVisibility(VISIBLE);
        mVideoView.seekTo(seekPostionMs * 1000);
    }

    /**
     * 判断拖动粒度是否符合要求
     **/
    private boolean isOverSeekGate(int seekBarPositionMs, int currentPlayPosMs) {
        final int SEEK_MIN_GATE_MS = 1000;
        boolean isChangeOverSeekGate = Math.abs(currentPlayPosMs - seekBarPositionMs) > SEEK_MIN_GATE_MS;
        return (isChangeOverSeekGate);
    }


    /**
     * 格式化时间为 hh:ss:mm
     **/
    private static String formatTimeText(int i) {
        int i2 = (i % 3600) / 60;
        int i3 = i % 60;
        if (i / 3600 != 0) {
            return (String.format("%02d:%02d:%02d", new Object[]{Integer.valueOf(i / 3600), Integer.valueOf(i2), Integer.valueOf(i3)}));
        }
        return (String.format("%02d:%02d", new Object[]{Integer.valueOf(i2), Integer.valueOf(i3)}));
    }


    /**
     * UI刷新线程，包括播放进度
     **/
    private Thread mUpdateThread = null;

    private void startUIUpdateThread() {
        if (null == mUpdateThread) {
            mIsNeedUpdateUIProgress = true;
            mUpdateThread = new Thread(new UpdatePlayUIProcess());
            mUpdateThread.start();
        } else {
            Log.e("info", "null != mUpdateThread");
        }
    }

    private void stopUIUpdateThread() {
        mIsNeedUpdateUIProgress = false;
        mUpdateThread = null;
    }

    private class UpdatePlayUIProcess implements Runnable {
        private boolean mIsTouchingSeekbar;

        @Override
        public void run() {
            while (mIsNeedUpdateUIProgress) {
                if (!mIsTouchingSeekbar) {
                    long currentPlayTime = 0;
                    long durationTime = 0;
                    if (null != mVideoView) {
                        currentPlayTime = mVideoView.getCurrentPosition();
                        durationTime = mVideoView.getDuration();
                    }
                    Message msg = mainUIHandler.obtainMessage(1, (int) (currentPlayTime / 1000), (int) (durationTime / 1000));
                    mainUIHandler.sendMessage(msg);
                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Log.e("info", e.toString());
                }
            }
        }
    }

    private static class MyHandler extends Handler {
        WeakReference<KsyunVodActivity> mActivity;

        MyHandler(KsyunVodActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            KsyunVodActivity activity = mActivity.get();
            if (msg.what == 1) {
                activity.tv_duration.setText(formatTimeText(msg.arg2));
                activity.tv_position.setText(formatTimeText(msg.arg1));
                if (msg.arg1 > 0 && msg.arg2 >= 0) {
                    activity.seekBar.setMax(msg.arg2);
                    activity.seekBar.setProgress(msg.arg1);
                    if (activity.playableRangeStart != -1 && activity.playableRangeEnd != -1) {
                        activity.seekBar.setSecondaryProgress(Math.abs(activity.playableRangeEnd - activity.playableRangeStart) / 1000);
                    }
                } else {
                    activity.seekBar.setProgress(0);
                }

            }
        }
    }


    /**
     * 电池状态监听
     **/
    private BatteryReceiver batteryReceiver;
    private TextView batteryForground;
    private TextView batteryBackGround;
    private LinearLayout batteryShape;

    public void initBatteryReceiver() {
        this.batteryForground = (TextView) findViewById(R.id.dianliang);
        this.batteryBackGround = (TextView) findViewById(R.id.dianliang2);
        this.batteryShape = (LinearLayout) findViewById(R.id.dianliang3);

        this.batteryReceiver = new BatteryReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.BATTERY_CHANGED");
        intentFilter.addAction("android.intent.action.NEW_OUTGOING_CALL");
        registerReceiver(this.batteryReceiver, intentFilter);
    }

    public class BatteryReceiver extends BroadcastReceiver {
        public void onReceive(Context context, @NonNull Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                if (action.equals("android.intent.action.BATTERY_CHANGED")) {
                    batteryStateChanged(intent.getIntExtra("status", 0), intent.getIntExtra("level", 0), intent.getIntExtra("temperature", 0));
                }
            }
        }
    }

    private void batteryStateChanged(int i, int i2, int i3) {
        if (i2 > 10) {
            this.batteryBackGround.setBackgroundColor(Color.parseColor("#ffffff"));
            this.batteryShape.setBackgroundResource(R.drawable.play_ctrl_battery);
        } else {
            this.batteryBackGround.setBackgroundColor(Color.parseColor("#FF0000"));
            this.batteryShape.setBackgroundResource(R.drawable.play_ctrl_battery1);
        }
        if (i == 2) {
            this.batteryShape.setBackgroundResource(R.drawable.play_ctrl_battery2);
            i2 = 1;
        }
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -1);
        layoutParams.weight = getBatteryWeight(i2);
        this.batteryForground.setLayoutParams(layoutParams);
    }

    public float getBatteryWeight(int i) {
        double d = (double) i;
        if (i > 99) {
            d = 99.0d;
        }
        if (i < 1) {
            d = 1.0d;
        }
        d = 1.0d - (d / 100.0d);
        return ((float) ((100.0d - (100.0d * d)) / d));
    }


    /**
     * 隐藏虚拟按键，并且全屏
     **/
    protected void hideBottomUIMenu() {
        /* 隐藏虚拟按键，并且全屏 */
        if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
            View v = this.getWindow().getDecorView();
            v.setSystemUiVisibility(GONE);
        } else if (Build.VERSION.SDK_INT >= 19) {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    /**
     * 设置屏幕亮度
     **/
    public void setBrightness(int paramInt) {
        if (paramInt < 0) {
            paramInt = 0;
        }
        if (paramInt > 100) {
            paramInt = 100;
        }
        WindowManager.LayoutParams localLayoutParams = this.getWindow().getAttributes();
        localLayoutParams.screenBrightness = (1.0F * paramInt / 100.0F);
        this.getWindow().setAttributes(localLayoutParams);
        this.distanceBritness = paramInt;
    }

    private void showError(int what) {
        String errorMsg;
        switch (what) {
            case IMediaPlayer.MEDIA_ERROR_UNKNOWN:
                errorMsg = "未知错误";
                break;
            case IMediaPlayer.MEDIA_ERROR_SERVER_DIED:
                errorMsg = "服务器连接失败";
                break;
            case IMediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                errorMsg = "视频流不适用于连续播放视频的指标";
                break;
            case IMediaPlayer.MEDIA_ERROR_IO:
                errorMsg = "文件不存在或错误，或网络访问错误";
                break;
            case IMediaPlayer.MEDIA_ERROR_MALFORMED:
                errorMsg = "流不符合有关标准或文件的编码规范";
                break;
            case IMediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                errorMsg = "视频格式不支持";
                break;
            case IMediaPlayer.MEDIA_ERROR_TIMED_OUT:
                errorMsg = "文件访问连接超时";
                break;
            case IMediaPlayer.MEDIA_ERROR_UNSUPPORT_PROTOCOL:
                errorMsg = "视频协议不支持";
                break;
            case IMediaPlayer.MEDIA_ERROR_DNS_PARSE_FAILED:
                errorMsg = "DNS解析失败";
                break;
            case IMediaPlayer.MEDIA_ERROR_CREATE_SOCKET_FAILED:
                errorMsg = "Socket创建失败";
                break;
            case IMediaPlayer.MEDIA_ERROR_CONNECT_SERVER_FAILED:
                errorMsg = "连接服务失败";
                break;
            case IMediaPlayer.MEDIA_ERROR_BAD_REQUEST:
                errorMsg = "错误的请求";
                break;
            case IMediaPlayer.MEDIA_ERROR_UNAUTHORIZED_CLIENT:
                errorMsg = "未经授权的客户端";
                break;
            case IMediaPlayer.MEDIA_ERROR_ACCESSS_FORBIDDEN:
                errorMsg = "URL被禁止读取";
                break;
            case IMediaPlayer.MEDIA_ERROR_TARGET_NOT_FOUND:
                errorMsg = "没有发现目标文件";
                break;
            case IMediaPlayer.MEDIA_ERROR_OTHER_ERROR_CODE:
                errorMsg = "其他错误";
                break;
            case IMediaPlayer.MEDIA_ERROR_SERVER_EXCEPTION:
                errorMsg = "服务器异常";
                break;
            case IMediaPlayer.MEDIA_ERROR_INVALID_DATA:
                errorMsg = "视频格式不支持";
                break;
            case IMediaPlayer.MEDIA_ERROR_UNSUPPORT_VIDEO_CODEC:
                errorMsg = "不支持的视频代码";
                break;
            case IMediaPlayer.MEDIA_ERROR_UNSUPPORT_AUDIO_CODEC:
                errorMsg = "不支持的音频代码";
                break;
            case IMediaPlayer.MEDIA_ERROR_VIDEO_DECODE_FAILED:
                errorMsg = "视频解码失败";
                break;
            case IMediaPlayer.MEDIA_ERROR_AUDIO_DECODE_FAILED:
                errorMsg = "音频解码失败";
                break;
            case IMediaPlayer.MEDIA_ERROR_3XX_OVERFLOW:
                errorMsg = "3XX_OVERFLOW";
                break;
            case IMediaPlayer.MEDIA_ERROR_INVALID_URL:
                errorMsg = "无效的URL链接";
                break;
            default:
                errorMsg = Integer.toString(what);
                break;
        }
        Log.e("info", "onError: " + errorMsg);
        Toast.makeText(this, "onError: " + errorMsg, Toast.LENGTH_SHORT).show();
        finishPlay();
        finish();
    }

    @Override
    protected void onPause() {
        if (mVideoView != null) {
            if (mVideoView.isPlaying()) {
                mVideoView.pause();
                mIsSystemCallPause = true;
            }
        }
        /* 让播放进度UI更新线程退出 */
        stopUIUpdateThread();
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (mVideoView != null) {
            /* 普通播放，后台切换回来，恢复之前的播放状态 */
            if (mIsSystemCallPause) {
                hideBottomUIMenu();
                mVideoView.start();
                mIsSystemCallPause = false;
                mVideoView.setVisibility(View.VISIBLE);
                mVideoView.setComeBackFromShare(true);
                startUIUpdateThread();
            }
        }
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
        }
        finishPlay();
        super.onDestroy();
    }

    private void finishPlay() {
        if (mVideoView != null) {
            stopUIUpdateThread();
            if (url != null) {
                if (!isLive) {
                    PlayerDataBaseHelper.addPlayerInfo(url, mVideoView.getCurrentPosition());
                }
            }
            mVideoView.release();
            mVideoView = null;
        }
        setResult(38438);
    }

    /**
     * 屏幕方向变化，应修改宽高防止手势比例异常
     **/
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        this.width = displayMetrics.widthPixels;
        this.height = displayMetrics.heightPixels;
        showExtralButtons(newConfig.orientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void showExtralButtons(boolean visiable) {
        v_scaling.setVisibility(visiable ? VISIBLE : GONE);
        v_track.setVisibility(visiable ? VISIBLE : GONE);
        v_speed.setVisibility(visiable ? VISIBLE : GONE);
        v_timeText.setVisibility(visiable ? VISIBLE : GONE);
    }

    private void showTrackListWindow() {
        View contentView = LayoutInflater.from(this).inflate(R.layout.popuplayout, null);
        PopupWindow popWnd = new PopupWindow(this);
        popWnd.setContentView(contentView);
        popWnd.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        popWnd.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popWnd.setWidth(WindowManager.LayoutParams.MATCH_PARENT);
        int height = getWindowManager().getDefaultDisplay().getHeight();
        int width = getWindowManager().getDefaultDisplay().getWidth();
        popWnd.setHeight(height);
        popWnd.setWidth(width / 3);
        popWnd.setAnimationStyle(R.style.right_popwin_anim_style);
        popWnd.setOutsideTouchable(true);
        popWnd.setBackgroundDrawable(new BitmapDrawable(getResources()));

        TextView title = contentView.findViewById(R.id.title);
        title.setText("音轨选择");
        int index = mVideoView.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO);// 获取当前正在播放的音频轨道索引
        List<TrackEntity> trackEntityList = new ArrayList<>();
        int i = 0;
        for (KSYTrackInfo trackInfo : mVideoView.getTrackInfo()) {
            if (trackInfo.getTrackType() == ITrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                i = i + 1;
                trackEntityList.add(new TrackEntity(trackInfo.getLanguage().replace("und", "音轨" + i), trackInfo.getTrackIndex(), index == trackInfo.getTrackIndex()));
            }
        }
        RecyclerView recyclerView = contentView.findViewById(R.id.list);
        TrackListAdapter adapter = new TrackListAdapter(this, trackEntityList);
        recyclerView.setAdapter(adapter);
        adapter.setOnTrackSelectedListener(trackEntity -> {
            if (trackEntity.getIndex() != mVideoView.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_AUDIO)) {
                mVideoView.selectTrack(trackEntity.getIndex());
                Toast.makeText(KsyunVodActivity.this, "切换新的音轨", Toast.LENGTH_SHORT).show();
            }
        });

        popWnd.showAtLocation(mVideoView, Gravity.RIGHT, 0, 0);
    }

    private void showScalingTrackWindow() {
        View contentView = LayoutInflater.from(this).inflate(R.layout.popuplayout, null);
        PopupWindow popWnd = new PopupWindow(this);
        popWnd.setContentView(contentView);
        popWnd.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        popWnd.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popWnd.setWidth(WindowManager.LayoutParams.MATCH_PARENT);
        popWnd.setHeight(height);
        popWnd.setWidth(width / 3);
        popWnd.setAnimationStyle(R.style.right_popwin_anim_style);
        popWnd.setOutsideTouchable(true);
        popWnd.setBackgroundDrawable(new BitmapDrawable(getResources()));

        TextView title = contentView.findViewById(R.id.title);
        title.setText("缩放模式");

        List<TrackEntity> trackEntityList = new ArrayList<>();
        trackEntityList.add(new TrackEntity("填充模式", KSYMediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT, videoScalingMode == KSYMediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT));
        trackEntityList.add(new TrackEntity("裁剪模式", KSYMediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING, videoScalingMode == KSYMediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING));
        trackEntityList.add(new TrackEntity("全屏模式", KSYMediaPlayer.VIDEO_SCALING_MODE_NOSCALE_TO_FIT, videoScalingMode == KSYMediaPlayer.VIDEO_SCALING_MODE_NOSCALE_TO_FIT));

        RecyclerView recyclerView = contentView.findViewById(R.id.list);
        TrackListAdapter adapter = new TrackListAdapter(this, trackEntityList);
        recyclerView.setAdapter(adapter);
        adapter.setOnTrackSelectedListener(trackEntity -> {
            videoScalingMode = trackEntity.getIndex();
            mVideoView.setVideoScalingMode(trackEntity.getIndex());
        });

        popWnd.showAtLocation(mVideoView, Gravity.RIGHT, 0, 0);
    }

    private int speedIndex = 1;

    private void showSpeedTrackWindow() {
        View contentView = LayoutInflater.from(this).inflate(R.layout.popuplayout, null);
        PopupWindow popWnd = new PopupWindow(this);
        popWnd.setContentView(contentView);
        popWnd.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        popWnd.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popWnd.setWidth(WindowManager.LayoutParams.MATCH_PARENT);
        popWnd.setHeight(height);
        popWnd.setWidth(width / 3);
        popWnd.setAnimationStyle(R.style.right_popwin_anim_style);
        popWnd.setOutsideTouchable(true);
        popWnd.setBackgroundDrawable(new BitmapDrawable(getResources()));

        TextView title = contentView.findViewById(R.id.title);
        title.setText("倍速播放");

        List<TrackEntity> trackEntityList = new ArrayList<>();
        trackEntityList.add(new TrackEntity("0.8x", 0, speedIndex == 0));
        trackEntityList.add(new TrackEntity("1.0x", 1, speedIndex == 1));
        trackEntityList.add(new TrackEntity("1.25x", 2, speedIndex == 2));
        trackEntityList.add(new TrackEntity("1.5x", 3, speedIndex == 3));
        trackEntityList.add(new TrackEntity("2.0x", 4, speedIndex == 4));
        RecyclerView recyclerView = contentView.findViewById(R.id.list);
        TrackListAdapter adapter = new TrackListAdapter(this, trackEntityList);
        recyclerView.setAdapter(adapter);
        adapter.setOnTrackSelectedListener(trackEntity -> {
            speedIndex = trackEntity.getIndex();
            switch (trackEntity.getIndex()) {
                case 0:
                    mVideoView.setSpeed(0.8f);
                    break;
                case 1:
                    mVideoView.setSpeed(1.0f);
                    break;
                case 2:
                    mVideoView.setSpeed(1.25f);
                    break;
                case 3:
                    mVideoView.setSpeed(1.5f);
                    break;
                case 4:
                    mVideoView.setSpeed(2.0f);
                    break;
            }

        });

        popWnd.showAtLocation(mVideoView, Gravity.RIGHT, 0, 0);
    }

    private void showTimeTextListWindow() {
        View contentView = LayoutInflater.from(this).inflate(R.layout.popuplayout, null);
        PopupWindow popWnd = new PopupWindow(this);
        popWnd.setContentView(contentView);
        popWnd.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        popWnd.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popWnd.setWidth(WindowManager.LayoutParams.MATCH_PARENT);
        int height = getWindowManager().getDefaultDisplay().getHeight();
        int width = getWindowManager().getDefaultDisplay().getWidth();
        popWnd.setHeight(height);
        popWnd.setWidth(width / 3);
        popWnd.setAnimationStyle(R.style.right_popwin_anim_style);
        popWnd.setOutsideTouchable(true);
        popWnd.setBackgroundDrawable(new BitmapDrawable(getResources()));

        TextView title = contentView.findViewById(R.id.title);
        title.setText("字幕选择");
        int index = mVideoView.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT);// 获取当前正在播放的音频轨道索引
        List<TrackEntity> trackEntityList = new ArrayList<>();
        int i = 0;
        for (KSYTrackInfo trackInfo : mVideoView.getTrackInfo()) {
            if (trackInfo.getTrackType() == ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) {
                i = i + 1;
                trackEntityList.add(new TrackEntity(trackInfo.getLanguage().replace("und", "字幕" + i), trackInfo.getTrackIndex(), index == trackInfo.getTrackIndex()));
            }
        }
        RecyclerView recyclerView = contentView.findViewById(R.id.list);
        TrackListAdapter adapter = new TrackListAdapter(this, trackEntityList);
        recyclerView.setAdapter(adapter);
        adapter.setOnTrackSelectedListener(trackEntity -> {
            if (trackEntity.getIndex() != mVideoView.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT)) {
                mVideoView.deselectTrack(mVideoView.getSelectedTrack(ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT));
                mVideoView.selectTrack(trackEntity.getIndex());
                Toast.makeText(KsyunVodActivity.this, "切换新的字幕", Toast.LENGTH_SHORT).show();
            }
        });

        popWnd.showAtLocation(mVideoView, Gravity.RIGHT, 0, 0);
    }
}
