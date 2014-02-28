package com.vink.music.ui.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.vink.music.R;
import com.vink.music.interfaces.NotifyMainLibrary;
import com.vink.music.support.MediaPlaybackService;
import com.vink.music.support.RepeatingImageButton;
import com.vink.music.ui.MainLibrary;
import com.vink.music.util.MusicUtils;

public class BasicControlView extends Fragment {
	private boolean mSeeking = false;
	private boolean mDeviceHasDpad;
	private long mStartSeekPos = 0;
	private long mLastSeekEventTime;
	private RepeatingImageButton mPrevButton;
	private ImageButton mPauseButton;
	private RepeatingImageButton mNextButton;
	private ImageButton mRepeatButton;
	private ImageButton mShuffleButton;
	private ImageButton mQueueButton;
	private Toast mToast;
	private int mTouchSlop;
	private TextView mCurrentTime;
	private TextView mTotalTime;
	private TextView mTrackName;
	private ProgressBar mProgress;
	private long mPosOverride = -1;
	private long mDuration;
	private boolean mFromTouch = false;

	private boolean paused;
	private MainLibrary mainLib;
	private Uri mUri;
	private NotifyMainLibrary mActivityCommunicator;

	private static final int REFRESH = 1;
	private static final int QUIT = 2;
	private static final int GET_ALBUM_ART = 3;
	private static final int ALBUM_ART_DECODED = 4;
	private static final String TAG = "BasicControlView";

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.playback_control, container,
				false);
		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		initializeViews();
		startPlayback();

	}

	@Override
	public void onAttach(Activity activity) {
		// TODO Auto-generated method stub
		super.onAttach(activity);
		mActivityCommunicator = (NotifyMainLibrary) getActivity();
		mainLib = (MainLibrary) activity;
		mUri = mainLib.getIntentDataUri();
	}

	@Override
	public void onResume() {
		super.onResume();
		updateTrackInfo();
		setPauseButtonImage();
	}

	@Override
	public void onStop() {
		paused = true;
		mHandler.removeMessages(REFRESH);
		getActivity().unregisterReceiver(mStatusListener);
		super.onStop();
	}

	@Override
	public void onStart() {
		super.onStart();

		paused = false;

		initBasicControlView();
		if (MusicUtils.sService == null) {
			// something went wrong
			mHandler.sendEmptyMessage(QUIT);
		}

		IntentFilter f = new IntentFilter();
		f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
		f.addAction(MediaPlaybackService.META_CHANGED);
		getActivity().registerReceiver(mStatusListener, new IntentFilter(f));
		updateTrackInfo();
		long next = refreshNow();
		queueNextRefresh(next);

	}

	private void updateTrackInfo() {
		mActivityCommunicator.updateTrackInfo();
		if (MusicUtils.sService == null) {
			return;
		}

		try {
			mTrackName.setText(MusicUtils.sService.getTrackName());

			mDuration = MusicUtils.sService.duration();
			mTotalTime.setText(MusicUtils.makeTimeString(this.getActivity(),
					mDuration / 1000));
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void queueNextRefresh(long delay) {
		if (!paused) {
			Message msg = mHandler.obtainMessage(REFRESH);
			mHandler.removeMessages(REFRESH);
			mHandler.sendMessageDelayed(msg, delay);
		}
	}

	private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(MediaPlaybackService.META_CHANGED)) {
				// redraw the artist/title info and
				// set new max for progress bar
				updateTrackInfo();
				setPauseButtonImage();
				// queueNextRefresh(1);
			} else if (action.equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
				setPauseButtonImage();
			}
		}
	};

	private void initializeViews() {
		View view = getView();
		mPrevButton = (RepeatingImageButton) view.findViewById(R.id.prev);
		mPrevButton.setOnClickListener(mPrevListener);
		mPrevButton.setRepeatListener(mRewListener, 260);
		mPauseButton = (ImageButton) view.findViewById(R.id.pause);
		mPauseButton.requestFocus();
		mPauseButton.setOnClickListener(mPauseListener);
		mNextButton = (RepeatingImageButton) view.findViewById(R.id.next);
		mNextButton.setOnClickListener(mNextListener);
		mNextButton.setRepeatListener(mFfwdListener, 260);
		mCurrentTime = (TextView) view.findViewById(R.id.currenttime);
		mTotalTime = (TextView) view.findViewById(R.id.totaltime);
		mProgress = (ProgressBar) view.findViewById(android.R.id.progress);
		mTrackName = (TextView) view.findViewById(R.id.track_title);
	}

	private void initializeSeekBar() {
		if (mProgress instanceof SeekBar) {
			SeekBar seeker = (SeekBar) mProgress;
			seeker.setOnSeekBarChangeListener(mSeekListener);
		}
		mProgress.setMax(1000);
	}

	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			// case ALBUM_ART_DECODED:
			// mAlbum.setImageBitmap((Bitmap)msg.obj);
			// mAlbum.getDrawable().setDither(true);
			// break;

			case REFRESH:
				long next = refreshNow();
				queueNextRefresh(next);
				break;

			case QUIT:
				// This can be moved back to onCreate once the bug that prevents
				// Dialogs from being started from onCreate/onResume is fixed.
				new AlertDialog.Builder(BasicControlView.this.getActivity())
						.setTitle(R.string.service_start_error_title)
						.setMessage(R.string.service_start_error_msg)
						.setPositiveButton(R.string.service_start_error_button,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int whichButton) {
										getActivity().finish();
									}
								}).setCancelable(false).show();
				break;

			default:
				break;
			}
		}
	};

	private void startPlayback() {

		if (MusicUtils.sService == null)
			return;
		String filename = "";
		if (mUri != null && mUri.toString().length() > 0) {
			// If this is a file:// URI, just use the path directly instead
			// of going through the open-from-filedescriptor codepath.
			String scheme = mUri.getScheme();
			if ("file".equals(scheme)) {
				filename = mUri.getPath();
			} else {
				filename = mUri.toString();
			}
			try {
				MusicUtils.sService.stop();
				MusicUtils.sService.openFile(filename);
				MusicUtils.sService.play();
				mainLib.setIntent(new Intent());
			} catch (Exception ex) {
				Log.d("MediaPlaybackActivity", "couldn't start playback: " + ex);
			}
		}

		updateTrackInfo();
		long next = refreshNow();
		queueNextRefresh(next);
	}

	private View.OnClickListener mPauseListener = new View.OnClickListener() {
		public void onClick(View v) {
			doPauseResume();
		}
	};

	private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
		public void onStartTrackingTouch(SeekBar bar) {
			mLastSeekEventTime = 0;
			mFromTouch = true;
		}

		public void onProgressChanged(SeekBar bar, int progress,
				boolean fromuser) {
			if (!fromuser || (MusicUtils.sService == null)) {
				return;
			}
			long now = SystemClock.elapsedRealtime();
			if ((now - mLastSeekEventTime) > 250) {
				mLastSeekEventTime = now;
				mPosOverride = mDuration * progress / 1000;
				try {
					MusicUtils.sService.seek(mPosOverride);
				} catch (RemoteException ex) {
				}

				// trackball event, allow progress updates
				if (!mFromTouch) {
					refreshNow();
					mPosOverride = -1;
				}
			}
		}

		public void onStopTrackingTouch(SeekBar bar) {
			mPosOverride = -1;
			mFromTouch = false;
		}
	};

	private View.OnClickListener mPrevListener = new View.OnClickListener() {
		public void onClick(View v) {
			if (MusicUtils.sService == null)
				return;
			try {
				if (MusicUtils.sService.position() < 2000) {
					MusicUtils.sService.prev();
				} else {
					MusicUtils.sService.seek(0);
					MusicUtils.sService.play();
				}
			} catch (RemoteException ex) {
			}
		}
	};

	private View.OnClickListener mNextListener = new View.OnClickListener() {
		public void onClick(View v) {
			if (MusicUtils.sService == null)
				return;
			try {
				MusicUtils.sService.next();
			} catch (RemoteException ex) {
			}
		}
	};

	private RepeatingImageButton.RepeatListener mRewListener = new RepeatingImageButton.RepeatListener() {
		public void onRepeat(View v, long howlong, int repcnt) {
			scanBackward(repcnt, howlong);
		}
	};

	private RepeatingImageButton.RepeatListener mFfwdListener = new RepeatingImageButton.RepeatListener() {
		public void onRepeat(View v, long howlong, int repcnt) {
			scanForward(repcnt, howlong);
		}
	};

	private void scanBackward(int repcnt, long delta) {
		if (MusicUtils.sService == null)
			return;
		try {
			if (repcnt == 0) {
				mStartSeekPos = MusicUtils.sService.position();
				mLastSeekEventTime = 0;
				mSeeking = false;
			} else {
				mSeeking = true;
				if (delta < 5000) {
					// seek at 10x speed for the first 5 seconds
					delta = delta * 10;
				} else {
					// seek at 40x after that
					delta = 50000 + (delta - 5000) * 40;
				}
				long newpos = mStartSeekPos - delta;
				if (newpos < 0) {
					// move to previous track
					MusicUtils.sService.prev();
					long duration = MusicUtils.sService.duration();
					mStartSeekPos += duration;
					newpos += duration;
				}
				if (((delta - mLastSeekEventTime) > 250) || repcnt < 0) {
					MusicUtils.sService.seek(newpos);
					mLastSeekEventTime = delta;
				}
				if (repcnt >= 0) {
					mPosOverride = newpos;
				} else {
					mPosOverride = -1;
				}
				refreshNow();
			}
		} catch (RemoteException ex) {
		}
	}

	private void scanForward(int repcnt, long delta) {
		if (MusicUtils.sService == null)
			return;
		try {
			if (repcnt == 0) {
				mStartSeekPos = MusicUtils.sService.position();
				mLastSeekEventTime = 0;
				mSeeking = false;
			} else {
				mSeeking = true;
				if (delta < 5000) {
					// seek at 10x speed for the first 5 seconds
					delta = delta * 10;
				} else {
					// seek at 40x after that
					delta = 50000 + (delta - 5000) * 40;
				}
				long newpos = mStartSeekPos + delta;
				long duration = MusicUtils.sService.duration();
				if (newpos >= duration) {
					// move to next track
					MusicUtils.sService.next();
					mStartSeekPos -= duration; // is OK to go negative
					newpos -= duration;
				}
				if (((delta - mLastSeekEventTime) > 250) || repcnt < 0) {
					MusicUtils.sService.seek(newpos);
					mLastSeekEventTime = delta;
				}
				if (repcnt >= 0) {
					mPosOverride = newpos;
				} else {
					mPosOverride = -1;
				}
				refreshNow();
			}
		} catch (RemoteException ex) {
		}
	}

	private void doPauseResume() {
		try {
			if (MusicUtils.sService != null) {
				if (MusicUtils.sService.isPlaying()) {
					MusicUtils.sService.pause();
				} else {
					MusicUtils.sService.play();
				}
				refreshNow();
				setPauseButtonImage();
			}
		} catch (RemoteException ex) {
		}
	}

	private void setPauseButtonImage() {
		try {
			if (MusicUtils.sService != null && MusicUtils.sService.isPlaying()) {
				mPauseButton
						.setImageResource(android.R.drawable.ic_media_pause);
				mActivityCommunicator.isPlaying(true);
			} else {
				mPauseButton.setImageResource(android.R.drawable.ic_media_play);
				mActivityCommunicator.isPlaying(false);
			}
		} catch (RemoteException ex) {
		}
	}

	private long refreshNow() {
		if (MusicUtils.sService == null)
			return 500;
		try {
			long pos = mPosOverride < 0 ? MusicUtils.sService.position()
					: mPosOverride;
			if ((pos >= 0) && (mDuration > 0)) {
				mCurrentTime.setText(MusicUtils.makeTimeString(
						this.getActivity(), pos / 1000));
				int progress = (int) (1000 * pos / mDuration);
				mProgress.setProgress(progress);

				if (MusicUtils.sService.isPlaying()) {
					mCurrentTime.setVisibility(View.VISIBLE);
				} else {
					// blink the counter
					int vis = mCurrentTime.getVisibility();
					mCurrentTime
							.setVisibility(vis == View.INVISIBLE ? View.VISIBLE
									: View.INVISIBLE);
					return 500;
				}
			} else {
				mCurrentTime.setText("--:--");
				mProgress.setProgress(1000);
			}
			// calculate the number of milliseconds until the next full second,
			// so
			// the counter can be updated at just the right time
			long remaining = 1000 - (pos % 1000);

			// approximate how often we would need to refresh the slider to
			// move it smoothly
			int width = mProgress.getWidth();
			if (width == 0)
				width = 320;
			long smoothrefreshtime = mDuration / width;

			if (smoothrefreshtime > remaining)
				return remaining;
			if (smoothrefreshtime < 20)
				return 20;
			return smoothrefreshtime;
		} catch (RemoteException ex) {
		}
		return 500;
	}

	private void initBasicControlView() {
		initializeSeekBar();
		// startPlayback();
		try {
			// Assume something is playing when the service says it is,
			// but also if the audio ID is valid but the service is paused.
			if (MusicUtils.sService.getAudioId() >= 0
					|| MusicUtils.sService.isPlaying()
					|| MusicUtils.sService.getPath() != null) {
				// something is playing now, we're done
				setPauseButtonImage();
				return;
			}
		} catch (RemoteException ex) {
		}

	}

}
