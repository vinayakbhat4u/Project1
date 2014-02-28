package com.vink.music.ui.fragments;

import java.text.Collator;
import java.util.ArrayList;

import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteException;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.vink.music.R;
import com.vink.music.interfaces.NotifyMainLibrary;
import com.vink.music.util.Constants;
import com.vink.music.util.MusicUtils;
import com.vink.music.util.MusicUtils.ServiceToken;

public class PlaylistBrowser extends ListFragment implements MusicUtils.Defs {

	private static final String TAG = "PlaylistBrowserActivity";
	private static final int DELETE_PLAYLIST = CHILD_MENU_BASE + 1;
	private static final int EDIT_PLAYLIST = CHILD_MENU_BASE + 2;
	private static final int RENAME_PLAYLIST = CHILD_MENU_BASE + 3;
	private static final int CHANGE_WEEKS = CHILD_MENU_BASE + 4;
	private static final long RECENTLY_ADDED_PLAYLIST = -1;
	private static final long ALL_SONGS_PLAYLIST = -2;
	private static final long PODCASTS_PLAYLIST = -3;
	private PlaylistListAdapter mAdapter;
	boolean mAdapterSent;
	private static int mLastListPosCourse = -1;
	private static int mLastListPosFine = -1;

	private boolean mCreateShortcut;
	private NotifyMainLibrary mActivityCommunicator;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.media_picker_activity, container,
				false);
		return view;

	}

	@Override
	public void onResume() {
		super.onResume();
		MusicUtils.setSpinnerState(this.getActivity());
		MusicUtils.updateNowPlaying(PlaylistBrowser.this.getActivity());
	}

	@Override
	public void onPause() {
		mReScanHandler.removeCallbacksAndMessages(null);
		super.onPause();
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		if (mCreateShortcut) {
			// final Intent shortcut = new Intent();
			// shortcut.setAction(Intent.ACTION_VIEW);
			// shortcut.setDataAndType(Uri.EMPTY,
			// "vnd.android.cursor.dir/playlist");
			// shortcut.putExtra("playlist", String.valueOf(id));
			//
			// final Intent intent = new Intent();
			// intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcut);
			// intent.putExtra(Intent.EXTRA_SHORTCUT_NAME,
			// ((TextView) v.findViewById(R.id.line1)).getText());
			// intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
			// Intent.ShortcutIconResource.fromContext(this,
			// R.drawable.ic_launcher_shortcut_music_playlist));
			//
			// setResult(RESULT_OK, intent);
			// finish();
			// return;
		}
		Bundle bundle = new Bundle();
		if (id == RECENTLY_ADDED_PLAYLIST) {
			bundle.putString(Constants.PLAYLIST_EXTRA, "recentlyadded");
			bundle.putInt(Constants.LAUNCH_TYPE, Constants.TRACK_BROWSER);
			mActivityCommunicator.passDataToActivity(bundle);
		} else if (id == PODCASTS_PLAYLIST) {
			bundle.putString(Constants.PLAYLIST_EXTRA, "podcasts");
			bundle.putInt(Constants.LAUNCH_TYPE, Constants.TRACK_BROWSER);
			mActivityCommunicator.passDataToActivity(bundle);

		} else {
			// Intent intent = new Intent(Intent.ACTION_EDIT);
			// intent.setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/track");
			// intent.putExtra("playlist", Long.valueOf(id).toString());
			// startActivity(intent);
			bundle.putString(Constants.PLAYLIST_EXTRA, Long.valueOf(id)
					.toString());
			bundle.putInt(Constants.LAUNCH_TYPE, Constants.TRACK_BROWSER);
			mActivityCommunicator.passDataToActivity(bundle);
		}
	}

	private void playRecentlyAdded() {
		// do a query for all songs added in the last X weeks
		int X = MusicUtils.getIntPref(this.getActivity(), "numweeks", 2)
				* (3600 * 24 * 7);
		final String[] ccols = new String[] { MediaStore.Audio.Media._ID };
		String where = MediaStore.MediaColumns.DATE_ADDED + ">"
				+ (System.currentTimeMillis() / 1000 - X);
		Cursor cursor = MusicUtils.query(this.getActivity(),
				MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ccols, where,
				null, MediaStore.Audio.Media.DEFAULT_SORT_ORDER);

		if (cursor == null) {
			// Todo: show a message
			return;
		}
		try {
			int len = cursor.getCount();
			long[] list = new long[len];
			for (int i = 0; i < len; i++) {
				cursor.moveToNext();
				list[i] = cursor.getLong(0);
			}
			MusicUtils.playAll(this.getActivity(), list, 0);
		} catch (SQLiteException ex) {
		} finally {
			cursor.close();
		}
	}

	private void playPodcasts() {
		// do a query for all files that are podcasts
		final String[] ccols = new String[] { MediaStore.Audio.Media._ID };
		Cursor cursor = MusicUtils.query(this.getActivity(),
				MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ccols,
				MediaStore.Audio.Media.IS_PODCAST + "=1", null,
				MediaStore.Audio.Media.DEFAULT_SORT_ORDER);

		if (cursor == null) {
			// Todo: show a message
			return;
		}
		try {
			int len = cursor.getCount();
			long[] list = new long[len];
			for (int i = 0; i < len; i++) {
				cursor.moveToNext();
				list[i] = cursor.getLong(0);
			}
			MusicUtils.playAll(this.getActivity(), list, 0);
		} catch (SQLiteException ex) {
		} finally {
			cursor.close();
		}
	}

	private BroadcastReceiver mScanListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			MusicUtils.setSpinnerState(PlaylistBrowser.this.getActivity());
			mReScanHandler.sendEmptyMessage(0);
		}
	};

	private Handler mReScanHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (mAdapter != null) {
				getPlaylistCursor(mAdapter.getQueryHandler(), null);
			}
		}
	};

	String[] mCols = new String[] { MediaStore.Audio.Playlists._ID,
			MediaStore.Audio.Playlists.NAME };

	private Cursor getPlaylistCursor(AsyncQueryHandler async,
			String filterstring) {

		StringBuilder where = new StringBuilder();
		where.append(MediaStore.Audio.Playlists.NAME + " != ''");

		// Add in the filtering constraints
		String[] keywords = null;
		if (filterstring != null) {
			String[] searchWords = filterstring.split(" ");
			keywords = new String[searchWords.length];
			Collator col = Collator.getInstance();
			col.setStrength(Collator.PRIMARY);
			for (int i = 0; i < searchWords.length; i++) {
				keywords[i] = '%' + searchWords[i] + '%';
			}
			for (int i = 0; i < searchWords.length; i++) {
				where.append(" AND ");
				where.append(MediaStore.Audio.Playlists.NAME + " LIKE ?");
			}
		}

		String whereclause = where.toString();

		if (async != null) {
			async.startQuery(0, null,
					MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, mCols,
					whereclause, keywords, MediaStore.Audio.Playlists.NAME);
			return null;
		}
		Cursor c = null;
		c = MusicUtils.query(this.getActivity(),
				MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, mCols,
				whereclause, keywords, MediaStore.Audio.Playlists.NAME);

		return mergedCursor(c);
	}

	private Cursor mergedCursor(Cursor c) {
		if (c == null) {
			return null;
		}
		if (c instanceof MergeCursor) {
			// this shouldn't happen, but fail gracefully
			Log.d("PlaylistBrowserActivity", "Already wrapped");
			return c;
		}
		MatrixCursor autoplaylistscursor = new MatrixCursor(mCols);
		if (mCreateShortcut) {
			ArrayList<Object> all = new ArrayList<Object>(2);
			all.add(ALL_SONGS_PLAYLIST);
			all.add(getString(R.string.play_all));
			autoplaylistscursor.addRow(all);
		}
		ArrayList<Object> recent = new ArrayList<Object>(2);
		recent.add(RECENTLY_ADDED_PLAYLIST);
		recent.add(getString(R.string.recentlyadded));
		autoplaylistscursor.addRow(recent);

		// check if there are any podcasts
		Cursor counter = MusicUtils.query(this.getActivity(),
				MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
				new String[] { "count(*)" }, "is_podcast=1", null, null);
		if (counter != null) {
			counter.moveToFirst();
			int numpodcasts = counter.getInt(0);
			counter.close();
			if (numpodcasts > 0) {
				ArrayList<Object> podcasts = new ArrayList<Object>(2);
				podcasts.add(PODCASTS_PLAYLIST);
				podcasts.add(getString(R.string.podcasts_listitem));
				autoplaylistscursor.addRow(podcasts);
			}
		}

		Cursor cc = new MergeCursor(new Cursor[] { autoplaylistscursor, c });
		return cc;
	}

	private Cursor mPlaylistCursor;

	public void init(Cursor cursor) {

		if (mAdapter == null) {
			return;
		}
		mAdapter.changeCursor(cursor);

		if (mPlaylistCursor == null) {
			MusicUtils.displayDatabaseError(this.getActivity());
			getActivity().closeContextMenu();
			mReScanHandler.sendEmptyMessageDelayed(0, 1000);
			return;
		}

		// restore previous position
		if (mLastListPosCourse >= 0) {
			getListView().setSelectionFromTop(mLastListPosCourse,
					mLastListPosFine);
			mLastListPosCourse = -1;
		}
		MusicUtils.hideDatabaseError(this.getActivity());
		MusicUtils.updateButtonBar(this.getActivity(), R.id.playlisttab);
		setTitle();
	}

	private void setTitle() {
		getActivity().setTitle(R.string.playlists_title);
	}

	private void setEmptyView() {
		TextView noMedia = null;
		noMedia = (TextView) getView().findViewById(R.id.error_message);
		getListView().setEmptyView(noMedia);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onActivityCreated(savedInstanceState);

		setEmptyView();

		mActivityCommunicator = (NotifyMainLibrary) getActivity();

		final Intent intent = getActivity().getIntent();
		final String action = intent.getAction();
		if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
			mCreateShortcut = true;
		}

		getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
		playSelected(intent);

		IntentFilter f = new IntentFilter();
		f.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
		f.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
		f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
		f.addDataScheme("file");
		getActivity().registerReceiver(mScanListener, f);

		MusicUtils.updateButtonBar(this.getActivity(), R.id.playlisttab);
		ListView lv = getListView();
		lv.setOnCreateContextMenuListener(this);
		lv.setTextFilterEnabled(true);

		mAdapter = (PlaylistListAdapter) getActivity()
				.getLastNonConfigurationInstance();
		if (mAdapter == null) {
			// Log.i("@@@", "starting query");
			mAdapter = new PlaylistListAdapter(getActivity().getApplication(),
					this, R.layout.track_list_item, mPlaylistCursor,
					new String[] { MediaStore.Audio.Playlists.NAME },
					new int[] { android.R.id.text1 });
			setListAdapter(mAdapter);
			getActivity().setTitle(R.string.working_playlists);
			getPlaylistCursor(mAdapter.getQueryHandler(), null);
		} else {
			mAdapter.setActivity(this);
			setListAdapter(mAdapter);
			mPlaylistCursor = mAdapter.getCursor();
			// If mPlaylistCursor is null, this can be because it doesn't have
			// a cursor yet (because the initial query that sets its cursor
			// is still in progress), or because the query failed.
			// In order to not flash the error dialog at the user for the
			// first case, simply retry the query when the cursor is null.
			// Worst case, we end up doing the same query twice.
			if (mPlaylistCursor != null) {
				init(mPlaylistCursor);
			} else {
				getActivity().setTitle(R.string.working_playlists);
				getPlaylistCursor(mAdapter.getQueryHandler(), null);
			}
		}

	}

	private void playSelected(Intent intent) {

		String action = intent.getAction();
		if (Intent.ACTION_VIEW.equals(action)) {
			Bundle b = intent.getExtras();
			if (b == null) {
				Log.w(TAG, "Unexpected:getExtras() returns null.");
			} else {
				try {
					long id = Long.parseLong(b.getString("playlist"));
					if (id == RECENTLY_ADDED_PLAYLIST) {
						playRecentlyAdded();
					} else if (id == PODCASTS_PLAYLIST) {
						playPodcasts();
					} else if (id == ALL_SONGS_PLAYLIST) {
						long[] list = MusicUtils
								.getAllSongs(PlaylistBrowser.this.getActivity());
						if (list != null) {
							MusicUtils
									.playAll(
											PlaylistBrowser.this.getActivity(),
											list, 0);
						}
					} else {
						MusicUtils.playPlaylist(
								PlaylistBrowser.this.getActivity(), id);
					}
				} catch (NumberFormatException e) {
					Log.w(TAG, "Playlist id missing or broken");
				}
			}
			getActivity().finish();
			return;
		}
		MusicUtils.updateNowPlaying(PlaylistBrowser.this.getActivity());

	}

	static class PlaylistListAdapter extends SimpleCursorAdapter {
		int mTitleIdx;
		int mIdIdx;
		private PlaylistBrowser mActivity = null;
		private AsyncQueryHandler mQueryHandler;
		private String mConstraint = null;
		private boolean mConstraintIsValid = false;

		class QueryHandler extends AsyncQueryHandler {
			QueryHandler(ContentResolver res) {
				super(res);
			}

			@Override
			protected void onQueryComplete(int token, Object cookie,
					Cursor cursor) {
				// Log.i("@@@", "query complete: " + cursor.getCount() + "   " +
				// mActivity);
				if (cursor != null) {
					cursor = mActivity.mergedCursor(cursor);
				}
				mActivity.init(cursor);
			}
		}

		PlaylistListAdapter(Context context, PlaylistBrowser currentactivity,
				int layout, Cursor cursor, String[] from, int[] to) {
			super(context, layout, cursor, from, to);
			mActivity = currentactivity;
			getColumnIndices(cursor);
			mQueryHandler = new QueryHandler(context.getContentResolver());
		}

		private void getColumnIndices(Cursor cursor) {
			if (cursor != null) {
				mTitleIdx = cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME);
				mIdIdx = cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID);
			}
		}

		public void setActivity(PlaylistBrowser newactivity) {
			mActivity = newactivity;
		}

		public AsyncQueryHandler getQueryHandler() {
			return mQueryHandler;
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {

			TextView tv = (TextView) view.findViewById(R.id.line1);

			String name = cursor.getString(mTitleIdx);
			tv.setText(name);

			long id = cursor.getLong(mIdIdx);

			ImageView iv = (ImageView) view.findViewById(R.id.icon);
			if (id == RECENTLY_ADDED_PLAYLIST) {
				iv.setImageResource(R.drawable.ic_mp_playlist_recently_added_list);
			} else {
				iv.setImageResource(R.drawable.ic_mp_playlist_list);
			}
			ViewGroup.LayoutParams p = iv.getLayoutParams();
			p.width = ViewGroup.LayoutParams.WRAP_CONTENT;
			p.height = ViewGroup.LayoutParams.WRAP_CONTENT;

			iv = (ImageView) view.findViewById(R.id.play_indicator);
			iv.setVisibility(View.GONE);

			view.findViewById(R.id.line2).setVisibility(View.GONE);
		}

		@Override
		public void changeCursor(Cursor cursor) {
			if (mActivity.getActivity().isFinishing() && cursor != null) {
				cursor.close();
				cursor = null;
			}
			if (cursor != mActivity.mPlaylistCursor) {
				mActivity.mPlaylistCursor = cursor;
				super.changeCursor(cursor);
				getColumnIndices(cursor);
			}
		}

		@Override
		public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
			String s = constraint.toString();
			if (mConstraintIsValid
					&& ((s == null && mConstraint == null) || (s != null && s
							.equals(mConstraint)))) {
				return getCursor();
			}
			Cursor c = mActivity.getPlaylistCursor(null, s);
			mConstraint = s;
			mConstraintIsValid = true;
			return c;
		}
	}

	@Override
	public void onDestroyView() {
		ListView lv = getListView();
		if (lv != null) {
			mLastListPosCourse = lv.getFirstVisiblePosition();
			View cv = lv.getChildAt(0);
			if (cv != null) {
				mLastListPosFine = cv.getTop();
			}
		}
		// If we have an adapter and didn't send it off to another activity yet,
		// we should
		// close its cursor, which we do by assigning a null cursor to it. Doing
		// this
		// instead of closing the cursor directly keeps the framework from
		// accessing
		// the closed cursor later.
		if (!mAdapterSent && mAdapter != null) {
			mAdapter.changeCursor(null);
		}
		// Because we pass the adapter to the next activity, we need to make
		// sure it doesn't keep a reference to this activity. We can do this
		// by clearing its DatasetObservers, which setListAdapter(null) does.
		setListAdapter(null);
		mAdapter = null;
		getActivity().unregisterReceiver(mScanListener);
		super.onDestroyView();
	}

}
