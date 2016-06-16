package com.mp4player.avtivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.libframework.activity.BaseActivity;
import com.libframework.annotation.ContentView;
import com.libframework.annotation.ViewInject;
import com.libframework.annotation.event.OnClick;
import com.libframework.annotation.event.OnProgressChanged;
import com.libframework.annotation.event.OnStartTrackingTouch;
import com.libframework.annotation.event.OnStopTrackingTouch;
import com.libuiwidgets.toast.CustomToast;
import com.mp4player.R;
import com.mp4player.constants.Constants;
import com.mp4player.player.MP4Player;
import com.mp4player.player.MP4Player.IMP4PlayCb;

@ContentView(R.layout.activity_main)
public class ActivityMain extends BaseActivity {
	
	private static final String WAKE_LOCK = "WAKE_LOCK";
	
	@ViewInject(R.id.iv_video_image)
	private ImageView mIvVideoImage;

	@ViewInject(R.id.tv_video_name)
	private TextView mTvVideoName;
	
	@ViewInject(R.id.tv_total_time)
	private TextView mTvTotalTime;

	@ViewInject(R.id.tv_cur_time)
	private TextView mTvCurTime;

	@ViewInject(R.id.sb_video_seekbar)
	private SeekBar mSbVideoSeekBar;

	@ViewInject(R.id.ib_footer_play)
	private ImageButton mIbFooterPlay;
	
	@ViewInject(R.id.iv_center_play)
	private ImageView mIvCenterPlay;
	
	@ViewInject(R.id.rl_header_panel)
	private RelativeLayout mRlHeaderPanel;
	
	@ViewInject(R.id.rl_footer_panel)
	private RelativeLayout mRlFooterPanel;
	
	private Animation mHeaderEnterAnim;
	private Animation mHeaderExitAnim;
	private Animation mFooterEnterAnim;
	private Animation mFooterExitAnim;
	
	private MP4Player mPlayer;
	private StringBuilder mBuilder;
	private int mDuration;
	private int mMaxPorgress;
	private boolean mNeedContinuePlay;
	private boolean mIsPanelVisible = true;
	private WakeLock mWakeLock;
	
	@SuppressWarnings("deprecation")
	@Override
	public void initData() {
		super.initData();
		mMaxPorgress = mSbVideoSeekBar.getMax();
		mPlayer = new MP4Player(new MP4PlayCb());
		mBuilder = new StringBuilder();
		mWakeLock = ((PowerManager)getSystemService(Context.POWER_SERVICE))
				.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, WAKE_LOCK);
		prepare();
		loadAnim();
	}
	
	@Override
	public void initWidget() {
		super.initWidget();
		initTotalTimeText();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mWakeLock.isHeld()) {
			mWakeLock.release();
		}
		mPlayer.pause();
		mIvCenterPlay.setVisibility(View.VISIBLE);
		mIbFooterPlay.setImageDrawable(getResources().getDrawable(R.drawable.footer_play));
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mPlayer.stop();
		mPlayer.release();
	}
	
	@OnClick({R.id.btn_header_back,
		R.id.iv_center_play,
		R.id.ib_footer_play,
		R.id.iv_video_image})
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btn_header_back:
			finish();
			break;
		case R.id.iv_center_play:
			if (!mPlayer.isPlaying()) {
				if (!mWakeLock.isHeld()) {
					mWakeLock.acquire();
				}
				mPlayer.play();
				mIvCenterPlay.setVisibility(View.GONE);
				mIbFooterPlay.setImageDrawable(getResources().getDrawable(R.drawable.footer_pause));
				toggleMenuPanel();
			}
			break;
		case R.id.ib_footer_play:
			if (!mPlayer.isPlaying()) {
				if (!mWakeLock.isHeld()) {
					mWakeLock.acquire();
				}
				mPlayer.play();
				mIvCenterPlay.setVisibility(View.GONE);
				mIbFooterPlay.setImageDrawable(getResources().getDrawable(R.drawable.footer_pause));
				toggleMenuPanel();
			} else if (mPlayer.isPausing()) {
				if (!mWakeLock.isHeld()) {
					mWakeLock.acquire();
				}
				mPlayer.resume();
				mIvCenterPlay.setVisibility(View.GONE);
				mIbFooterPlay.setImageDrawable(getResources().getDrawable(R.drawable.footer_pause));
			} else {
				if (mWakeLock.isHeld()) {
					mWakeLock.release();
				}
				mPlayer.pause();
				mIbFooterPlay.setImageDrawable(getResources().getDrawable(R.drawable.footer_play));
			}
			break;
		case R.id.iv_video_image:
			if (mPlayer.isPlaying()) {
				toggleMenuPanel();
			}
			break;
		default:
			break;
		}
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			toggleMenuPanel();
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_BACK) {
			finish();
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}
	
	@OnStartTrackingTouch(R.id.sb_video_seekbar)
	public void onStartTrackingTouch(SeekBar seekBar) {
		if (mPlayer.isPlaying()) {
			mPlayer.stop();
			mIbFooterPlay.setImageDrawable(getResources().getDrawable(R.drawable.footer_pause));
			mNeedContinuePlay = true;
		}
	}
	
	@OnStopTrackingTouch(R.id.sb_video_seekbar)
	public void onStopTrackingTouch(SeekBar seekBar) {
		int curProgress = seekBar.getProgress();
		if (curProgress >= mMaxPorgress) {
			mPlayer.stop();
			prepare();
		} else {
			final int curPosition = (int)(mDuration * curProgress / mMaxPorgress);
			mPlayer.prepare(Constants.MP4_FILE_PATH, curPosition);
			if (mNeedContinuePlay) {
				mPlayer.play();
				mIbFooterPlay.setImageDrawable(getResources().getDrawable(R.drawable.footer_play));
				mNeedContinuePlay = false;
			} else {
				setSnapshot(curPosition);
			}
		}
	}
	
	@OnProgressChanged(R.id.sb_video_seekbar)
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		setCurTimeText(mDuration * seekBar.getProgress() / mMaxPorgress);
	}
	
	private void prepare() {
		if (-1 == (mDuration = mPlayer.prepare(Constants.MP4_FILE_PATH, 0))) {
			CustomToast.makeText(ActivityMain.this, "The video is damaged.", Toast.LENGTH_SHORT);
			finish();
		}
		mTvVideoName.setText(Constants.MP4_FILE_NAME);
		setSnapshot(0);
		mTvCurTime.setText("00:00");
		mIvCenterPlay.setVisibility(View.VISIBLE);
	}
	
	private void loadAnim() {
		mHeaderEnterAnim = AnimationUtils.loadAnimation(this, R.anim.header_enter);
		mHeaderExitAnim  = AnimationUtils.loadAnimation(this, R.anim.header_exit);
		mFooterEnterAnim = AnimationUtils.loadAnimation(this, R.anim.footer_enter);
		mFooterExitAnim  = AnimationUtils.loadAnimation(this, R.anim.footer_exit);
	}
	
	private void initTotalTimeText() {
		final int min = mDuration / 60;
		final int sec = mDuration % 60;
		
		StringBuilder builder = new StringBuilder();
		mBuilder.append(min < 10 ? "0" : "");
		mBuilder.append(min);
		mBuilder.append(":");
		mBuilder.append(sec < 10 ? "0" : "");
		mBuilder.append(sec);
		mTvTotalTime.setText(builder.toString());
	}
	
	private void setCurTimeText(int curPosition) {
		final int curMin = curPosition / 60;
		final int curSec = curPosition % 60;
		
		mBuilder.delete(0, mBuilder.length());
		mBuilder.append(curMin < 10 ? "0" : "");
		mBuilder.append(curMin);
		mBuilder.append(":");
		mBuilder.append(curSec < 10 ? "0" : "");
		mBuilder.append(curSec);
		mTvCurTime.setText(mBuilder.toString());
	}
	
	private void setSnapshot(int startTime) {
		Bitmap snapshot = mPlayer.snapshot(Constants.MP4_FILE_PATH, startTime);
		if (null != snapshot) {
			mIvVideoImage.setImageBitmap(snapshot);
		}
	}

	private void toggleMenuPanel() {
		if (mIsPanelVisible) {
			mRlHeaderPanel.startAnimation(mHeaderExitAnim);
			mRlFooterPanel.startAnimation(mFooterExitAnim);
			mRlHeaderPanel.setVisibility(View.GONE);
			mRlFooterPanel.setVisibility(View.GONE);
			mIsPanelVisible = false;
		} else {
			mRlHeaderPanel.startAnimation(mHeaderEnterAnim);
			mRlFooterPanel.startAnimation(mFooterEnterAnim);
			mRlHeaderPanel.setVisibility(View.VISIBLE);
			mRlFooterPanel.setVisibility(View.VISIBLE);
			mIsPanelVisible = true;
		}
	}
	
	class MP4PlayCb implements IMP4PlayCb {

		@Override
		public void onProcess(double percent) {
			final double curPercent = percent;
			final int curPosition = (int)(mDuration * percent);
			final int curPorgress = (int)(mMaxPorgress * percent);
			
			runOnUiThread(new Runnable() {
				
				@Override
				public void run() {
					setCurTimeText(curPosition);
					
					if (!mPlayer.isPausing()) {
						mSbVideoSeekBar.setProgress(curPorgress);
					}
					if (curPercent >= 1.0) {
						mPlayer.stop();
						prepare();
					}
				}
			});
		}

		@Override
		public void onDisplay(Bitmap bitmap) {
			final Bitmap curBitmap = bitmap;
			
			runOnUiThread(new Runnable() {
				
				@Override
				public void run() {
					if (null != curBitmap) {
						mIvVideoImage.setImageBitmap(curBitmap);						
					}
				}
			});
		}
		
	}
	
}
