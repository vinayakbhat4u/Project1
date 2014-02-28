package com.vink.music.ui.fragments;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.vink.music.R;
import com.vink.music.interfaces.NotifyMainLibrary;
import com.vink.music.interfaces.MainActivityActions;
import com.vink.music.ui.MainLibrary;
import com.vink.music.util.MusicUtils;

public class AlbumArtView extends Fragment implements MainActivityActions {
	private ImageView mAlbum;
	private TextView albumTitle;
	private TextView artist;
	private TextView title;
	private ImageView playIndicator;

	private AlbumArtHandler mAlbumArtHandler;
	private Worker mAlbumArtWorker;
	private static final int GET_ALBUM_ART = 3;
	private static final int ALBUM_ART_DECODED = 4;
	private static final String TAG = AlbumArtView.class.getName();
	private NotifyMainLibrary mActivityCommunicator;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.album_art_view, container, false);
		return view;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mActivityCommunicator = (NotifyMainLibrary) getActivity();
		mActivityCommunicator.setMainActivityActionsListener(this);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		View view = getView();
		mAlbum = (ImageView) view.findViewById(R.id.album);
		title = (TextView) view.findViewById(R.id.title);
		albumTitle = (TextView) view.findViewById(R.id.album_title);
		artist = (TextView) view.findViewById(R.id.artist);
		playIndicator = (ImageView) view.findViewById(R.id.play_indicator);
		mAlbumArtWorker = new Worker("album art worker");
		mAlbumArtHandler = new AlbumArtHandler(mAlbumArtWorker.getLooper());
	}

	private static class AlbumSongIdWrapper {
		public long albumid;
		public long songid;

		AlbumSongIdWrapper(long aid, long sid) {
			albumid = aid;
			songid = sid;
		}
	}

	public class AlbumArtHandler extends Handler {
		private long mAlbumId = -1;

		public AlbumArtHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			long albumid = ((AlbumSongIdWrapper) msg.obj).albumid;
			long songid = ((AlbumSongIdWrapper) msg.obj).songid;
			if (msg.what == GET_ALBUM_ART
					&& (mAlbumId != albumid || albumid < 0)) {
				// while decoding the new image, show the default album art
				Message numsg = mHandler.obtainMessage(ALBUM_ART_DECODED, null);
				mHandler.removeMessages(ALBUM_ART_DECODED);
				mHandler.sendMessageDelayed(numsg, 300);
				// Don't allow default artwork here, because we want to fall
				// back to song-specific
				// album art if we can't find anything for the album.
				Bitmap bm = MusicUtils
						.getArtwork(AlbumArtView.this.getActivity(), songid,
								albumid, false);
				if (bm == null) {
					bm = MusicUtils.getArtwork(AlbumArtView.this.getActivity(),
							songid, -1);
					albumid = -1;
				}
				if (bm != null) {
					numsg = mHandler.obtainMessage(ALBUM_ART_DECODED, bm);
					mHandler.removeMessages(ALBUM_ART_DECODED);
					mHandler.sendMessage(numsg);
				}
				mAlbumId = albumid;
			}
		}
	}

	private static class Worker implements Runnable {
		private final Object mLock = new Object();
		private Looper mLooper;

		/**
		 * Creates a worker thread with the given name. The thread then runs a
		 * {@link android.os.Looper}.
		 * 
		 * @param name
		 *            A name for the new thread
		 */
		Worker(String name) {
			Thread t = new Thread(null, this, name);
			t.setPriority(Thread.MIN_PRIORITY);
			t.start();
			synchronized (mLock) {
				while (mLooper == null) {
					try {
						mLock.wait();
					} catch (InterruptedException ex) {
					}
				}
			}
		}

		public Looper getLooper() {
			return mLooper;
		}

		public void run() {
			synchronized (mLock) {
				Looper.prepare();
				mLooper = Looper.myLooper();
				mLock.notifyAll();
			}
			Looper.loop();
		}

		public void quit() {
			mLooper.quit();
		}
	}

	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case ALBUM_ART_DECODED:
				Log.i(TAG, "Inside ALBUM_ART_DECODED");
				mAlbum.setImageBitmap((Bitmap) msg.obj);
				mAlbum.getDrawable().setDither(true);
				break;

			default:
				break;
			}
		}
	};

	@Override
	public void passDataToFragment(Intent bundle) {
		// TODO Auto-generated method stub

	}

	@Override
	public void toggleShuffle() {
		// TODO Auto-generated method stub

	}

	@Override
	public void cycleRepeat() {
		// TODO Auto-generated method stub

	}

	@Override
	public void updateTrackInfo() {
		Log.i(TAG, "updateTrackInfo");
		try {
			title.setText(MusicUtils.sService.getTrackName());
			albumTitle.setText(MusicUtils.sService.getAlbumName());
			artist.setText(MusicUtils.sService.getArtistName());
			long songid = MusicUtils.sService.getAudioId();
			String path;

			path = MusicUtils.sService.getPath();
			
			if(path==null){
				return;
			}

			if (songid < 0 && path.toLowerCase().startsWith("http://")) {
				// mAlbum.setVisibility(View.GONE);
				mAlbumArtHandler.removeMessages(GET_ALBUM_ART);
				mAlbumArtHandler.obtainMessage(GET_ALBUM_ART,
						new AlbumSongIdWrapper(-1, -1)).sendToTarget();
			} else {
				long albumid = MusicUtils.sService.getAlbumId();
				String albumName = MusicUtils.sService.getAlbumName();
				if (MediaStore.UNKNOWN_STRING.equals(albumName)) {
					albumName = getString(R.string.unknown_album_name);
					albumid = -1;
				}
				mAlbumArtHandler.removeMessages(GET_ALBUM_ART);
				mAlbumArtHandler.obtainMessage(GET_ALBUM_ART,
						new AlbumSongIdWrapper(albumid, songid)).sendToTarget();
				mAlbum.setVisibility(View.VISIBLE);
			}
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public void onDestroyView() {
		mAlbumArtWorker.quit();
		super.onDestroyView();
	}

	@Override
	public void isPlaying(boolean isPlaying) {
		if (isPlaying) {
			playIndicator.setVisibility(View.VISIBLE);
		} else {
			playIndicator.setVisibility(View.GONE);
		}
	}

}
