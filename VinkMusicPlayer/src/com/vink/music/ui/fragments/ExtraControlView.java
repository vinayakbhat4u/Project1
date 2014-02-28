package com.vink.music.ui.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.vink.music.R;
import com.vink.music.interfaces.NotifyMainLibrary;
import com.vink.music.support.MediaPlaybackService;
import com.vink.music.util.Constants;
import com.vink.music.util.MusicUtils;

public class ExtraControlView extends Fragment{
	private static final String TAG = "ExtraControlView";
	private ImageButton mRepeatButton;
	private ImageButton mShuffleButton;
	private ImageButton mQueueButton;
	private Toast mToast;
	private NotifyMainLibrary mActivityCommunicator;


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.extra_control_view, container,
				false);
		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		initializeViews();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mActivityCommunicator = (NotifyMainLibrary) getActivity();
	}

	@Override
	public void onStart() {
		super.onStart();
		setButtonViews();
	}

	private void initializeViews() {
		View view = getView();
		mQueueButton = (ImageButton) view.findViewById(R.id.curplaylist);
		mQueueButton.setOnClickListener(mQueueListener);
		mShuffleButton = ((ImageButton) view.findViewById(R.id.shuffle));
		mShuffleButton.setOnClickListener(mShuffleListener);
		mRepeatButton = ((ImageButton) view.findViewById(R.id.repeat));
		mRepeatButton.setOnClickListener(mRepeatListener);

	}

	private View.OnClickListener mQueueListener = new View.OnClickListener() {
		public void onClick(View v) {
			 Bundle bundle = new Bundle();
			 bundle.putInt(Constants.LAUNCH_TYPE, Constants.TRACK_BROWSER);
			 bundle.putString(Constants.PLAYLIST_EXTRA, "nowplaying");
			 bundle.putBoolean(Constants.EDIT_PLAYLIST, true);
				mActivityCommunicator.passDataToActivity(bundle);
		}
	};

	private View.OnClickListener mShuffleListener = new View.OnClickListener() {
		public void onClick(View v) {
			toggleShuffle();
		}
	};

	private View.OnClickListener mRepeatListener = new View.OnClickListener() {
		public void onClick(View v) {
			cycleRepeat();
		}
	};

	private void setButtonViews() {
		try {
			if (MusicUtils.sService.getAudioId() >= 0 || MusicUtils.sService.isPlaying()
					|| MusicUtils.sService.getPath() != null) {
				setRepeatButtonImage();
				setShuffleButtonImage();

			}
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void setShuffleButtonImage() {

		if (MusicUtils.sService == null)
			return;
		try {
			switch (MusicUtils.sService.getShuffleMode()) {
			case MediaPlaybackService.SHUFFLE_NONE:
				mShuffleButton
						.setImageResource(R.drawable.ic_mp_shuffle_off_btn);
				break;
			case MediaPlaybackService.SHUFFLE_AUTO:
				mShuffleButton
						.setImageResource(R.drawable.ic_mp_partyshuffle_on_btn);
				break;
			default:
				mShuffleButton
						.setImageResource(R.drawable.ic_mp_shuffle_on_btn);
				break;
			}
		} catch (RemoteException ex) {
		}

	}

	public void setRepeatButtonImage() {

		if (MusicUtils.sService == null)
			return;
		try {
			switch (MusicUtils.sService.getRepeatMode()) {
			case MediaPlaybackService.REPEAT_ALL:
				mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_all_btn);
				break;
			case MediaPlaybackService.REPEAT_CURRENT:
				mRepeatButton
						.setImageResource(R.drawable.ic_mp_repeat_once_btn);
				break;
			default:
				mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_off_btn);
				break;
			}
		} catch (RemoteException ex) {
		}

	}

	private void toggleShuffle() {
		if (MusicUtils.sService == null) {
			return;
		}
		try {
			int shuffle = MusicUtils.sService.getShuffleMode();
			if (shuffle == MediaPlaybackService.SHUFFLE_NONE) {
				MusicUtils.sService.setShuffleMode(MediaPlaybackService.SHUFFLE_NORMAL);
				if (MusicUtils.sService.getRepeatMode() == MediaPlaybackService.REPEAT_CURRENT) {
					MusicUtils.sService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
					setRepeatButtonImage();
				}
				showToast(R.string.shuffle_on_notif);
			} else if (shuffle == MediaPlaybackService.SHUFFLE_NORMAL
					|| shuffle == MediaPlaybackService.SHUFFLE_AUTO) {
				MusicUtils.sService.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE);
				showToast(R.string.shuffle_off_notif);
			} else {
				Log.e("MediaPlaybackActivity", "Invalid shuffle mode: "
						+ shuffle);
			}
			setShuffleButtonImage();
		} catch (RemoteException ex) {
		}
	}

	private void cycleRepeat() {
		if (MusicUtils.sService == null) {
			return;
		}
		try {
			int mode = MusicUtils.sService.getRepeatMode();
			if (mode == MediaPlaybackService.REPEAT_NONE) {
				MusicUtils.sService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
				showToast(R.string.repeat_all_notif);
			} else if (mode == MediaPlaybackService.REPEAT_ALL) {
				MusicUtils.sService.setRepeatMode(MediaPlaybackService.REPEAT_CURRENT);
				if (MusicUtils.sService.getShuffleMode() != MediaPlaybackService.SHUFFLE_NONE) {
					MusicUtils.sService.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE);
					setShuffleButtonImage();
				}
				showToast(R.string.repeat_current_notif);
			} else {
				MusicUtils.sService.setRepeatMode(MediaPlaybackService.REPEAT_NONE);
				showToast(R.string.repeat_off_notif);
			}
			setRepeatButtonImage();
		} catch (RemoteException ex) {
		}

	}

	private void showToast(int resid) {
		if (mToast == null) {
			mToast = Toast.makeText(this.getActivity(), "", Toast.LENGTH_SHORT);
		}
		mToast.setText(resid);
		mToast.show();
	}


	@Override
	public void onDestroyView() {
		super.onDestroyView();
	}

}
