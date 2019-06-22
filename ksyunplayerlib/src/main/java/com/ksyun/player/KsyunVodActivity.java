package com.ksyun.player;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ksyun.media.player.IMediaPlayer;
import com.ksyun.media.player.KSYMediaPlayer;
import com.ksyun.media.player.KSYTextureView;
import com.ksyun.media.player.misc.KSYProbeMediaInfo;

import java.io.IOException;
import java.lang.ref.WeakReference;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class KsyunVodActivity extends Activity {
    private KSYTextureView mVideoView = null;
    private View mLoadView = null;
    private ImageView v_thumb;
    private TextView loadingText;
    private boolean mIsSystemCallPause;
    private boolean mIsNeedUpdateUIProgress;
    private Handler mainUIHandler = null;

    private SeekBar seekBar;
    private TextView tv_title;
    private TextView tv_position;
    private TextView tv_duration;
    private TextView tv_info;

    private ImageView v_back;
    private ImageView v_play;
    private ImageView v_rotate;
    private boolean mIsTouchingSeekbar = false;


    private int videoScalingMode = KSYMediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT;

    private String url;
    private String title;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ksyun_vod);
        Log.e("info", "onCreate");
        initView();
        initBatteryReceiver();
        url = getIntent().getStringExtra("url");
        title = getIntent().getStringExtra("title");
        videoScalingMode = getIntent().getIntExtra("scale", 1);
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
        seekBar = (SeekBar) findViewById(R.id.seekbar);
        tv_title = (TextView) findViewById(R.id.v_title);
        tv_position = (TextView) findViewById(R.id.tv_position);
        tv_duration = (TextView) findViewById(R.id.tv_duration);
        tv_info = (TextView) findViewById(R.id.tv_info);
        v_back = (ImageView) findViewById(R.id.v_back);
        v_play = (ImageView) findViewById(R.id.v_play);
        v_rotate = (ImageView) findViewById(R.id.v_rotate);
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
        mVideoView.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(IMediaPlayer iMediaPlayer) {
                Log.e("info", "onPrepared");
                mLoadView.setVisibility(View.GONE);
                mVideoView.setVideoScalingMode(videoScalingMode);
                mVideoView.start();
                v_play.setImageResource(R.drawable.v_play_pause);
                startUIUpdateThread();
            }
        });

        mVideoView.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(IMediaPlayer iMediaPlayer, int what, int extra) {
                showError(what);
                Log.e("info", "OnErrorListener, Error:" + what + ",extra:" + extra);
                return false;
            }
        });

        mVideoView.setOnBufferingUpdateListener(new IMediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(IMediaPlayer iMediaPlayer, int i) {
                if (!mIsTouchingSeekbar) {
                    loadingText.setText("缓冲: " + i + "%");
                    mLoadView.setVisibility(i == 100 ? View.GONE : View.VISIBLE);
                }
            }
        });

        mVideoView.setOnInfoListener(new IMediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(IMediaPlayer iMediaPlayer, int i, int i1) {
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
            }
        });

        mVideoView.setOnMessageListener(new IMediaPlayer.OnMessageListener() {
            @Override
            public void onMessage(IMediaPlayer iMediaPlayer, Bundle bundle) {
                Log.e("info", "name:" + bundle.toString());
            }
        });

        mVideoView.setOnCompletionListener(new IMediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(IMediaPlayer iMediaPlayer) {
                finishPlay();
                finish();
            }
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

        v_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishPlay();
                finish();
            }
        });


        v_play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mVideoView.isPlaying()) {
                    v_play.setImageResource(R.drawable.v_play_arrow);
                    mVideoView.pause();
                } else {
                    v_play.setImageResource(R.drawable.v_play_pause);
                    mVideoView.start();
                }
            }
        });

        v_rotate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Configuration mConfiguration = getResources().getConfiguration();  /* 获取设置的配置信息 */
                int ori = mConfiguration.orientation;                           /* 获取屏幕方向 */
                if (ori == 2) {
                    /* 横屏 -> 竖屏 */
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);            /* 强制为竖屏 */
                } else if (ori == 1) {
                    /* 竖屏 -> 横屏 */
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);    /* 强制为横屏 */
                }
            }
        });

        mainUIHandler = new MyHandler(this);
    }

    private void userSeekPlayProgress(int seekPostionMs, int max) {
        int currentPlayPos = (int) mVideoView.getCurrentPosition();
        boolean isChangeOverSeekGate = isOverSeekGate(seekPostionMs * 1000, currentPlayPos);
        if (!isChangeOverSeekGate) {
            /* 避免拖动粒度过细，拖动时频繁定位影响体验 */
            return;
        }
        mIsTouchingSeekbar = true;
        stopUIUpdateThread();
        seekBar.setProgress(seekPostionMs);
        tv_info.setText(formatTimeText(seekPostionMs) + "/" + formatTimeText(max));
        tv_info.setVisibility(VISIBLE);
        mVideoView.seekTo(seekPostionMs * 1000);
    }

    private boolean isOverSeekGate(int seekBarPositionMs, int currentPlayPosMs) {
        final int SEEK_MIN_GATE_MS = 1000;
        boolean isChangeOverSeekGate = Math.abs(currentPlayPosMs - seekBarPositionMs) > SEEK_MIN_GATE_MS;
        return (isChangeOverSeekGate);
    }


    private static String formatTimeText(int i) {
        int i2 = (i % 3600) / 60;
        int i3 = i % 60;
        if (i / 3600 != 0) {
            return (String.format("%02d:%02d:%02d", new Object[]{Integer.valueOf(i / 3600), Integer.valueOf(i2), Integer.valueOf(i3)}));
        }
        return (String.format("%02d:%02d", new Object[]{Integer.valueOf(i2), Integer.valueOf(i3)}));
    }

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
                    Message msg = mainUIHandler.obtainMessage(1, (int) currentPlayTime / 1000, (int) durationTime / 1000);
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
            mActivity = new WeakReference<KsyunVodActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            KsyunVodActivity activity = mActivity.get();
            if (msg.what == 1) {
                activity.tv_duration.setText(formatTimeText(msg.arg2));
                activity.tv_position.setText(formatTimeText(msg.arg1));
                /* setTimeTextView(mTextViewDurationTime, durationTimeMs); */
                if (msg.arg1 > 0 && msg.arg2 >= 0) {
                    activity.seekBar.setMax(msg.arg2);
                    activity.seekBar.setProgress(msg.arg1);
                } else {
                    activity.seekBar.setProgress(0);
                }

            }
        }
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
    protected void onStop() {
        super.onStop();
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
            mVideoView.release();
            mVideoView = null;
        }
        setResult(38438);
    }

    /**
     * 隐藏虚拟按键，并且全屏
     */
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
                errorMsg = "MEDIA_ERROR_ACCESSS_FORBIDDEN";
                break;
            case IMediaPlayer.MEDIA_ERROR_TARGET_NOT_FOUND:
                errorMsg = "MEDIA_ERROR_TARGET_NOT_FOUND";
                break;
            case IMediaPlayer.MEDIA_ERROR_OTHER_ERROR_CODE:
                errorMsg = "MEDIA_ERROR_OTHER_ERROR_CODE";
                break;
            case IMediaPlayer.MEDIA_ERROR_SERVER_EXCEPTION:
                errorMsg = "MEDIA_ERROR_SERVER_EXCEPTION";
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
                errorMsg = "MEDIA_ERROR_3XX_OVERFLOW";
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


    private BatteryReceiver batteryReceiver;

    private TextView dianliangTextView;
    private TextView dianliangTextView2;
    private LinearLayout dianliangTextView3;

    public void initBatteryReceiver() {
        this.dianliangTextView = (TextView) findViewById(R.id.dianliang);
        this.dianliangTextView2 = (TextView) findViewById(R.id.dianliang2);
        this.dianliangTextView3 = (LinearLayout) findViewById(R.id.dianliang3);

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
            this.dianliangTextView2.setBackgroundColor(Color.parseColor("#ffffff"));
            this.dianliangTextView3.setBackgroundResource(R.drawable.play_ctrl_battery);
        } else {
            this.dianliangTextView2.setBackgroundColor(Color.parseColor("#FF0000"));
            this.dianliangTextView3.setBackgroundResource(R.drawable.play_ctrl_battery1);
        }
        if (i == 2) {
            this.dianliangTextView3.setBackgroundResource(R.drawable.play_ctrl_battery2);
            i2 = 1;
        }
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -1);
        layoutParams.weight = getBatteryWeight(i2);
        this.dianliangTextView.setLayoutParams(layoutParams);
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
}