package com.vink.music.ui.fragments;

import java.util.Arrays;

import android.app.SearchManager;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.AbstractCursor;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.Playlists;
import android.support.v4.app.ListFragment;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AlphabetIndexer;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.vink.music.R;
import com.vink.music.support.MediaPlaybackService;
import com.vink.music.support.TouchInterceptor;
import com.vink.music.ui.CreatePlaylist;
import com.vink.music.ui.DeleteItems;
import com.vink.music.util.Constants;
import com.vink.music.util.IMediaPlaybackService;
import com.vink.music.util.MusicAlphabetIndexer;
import com.vink.music.util.MusicUtils;
import com.vink.music.util.MusicUtils.ServiceToken;

public class TrackBrowser extends ListFragment implements MusicUtils.Defs {

	int mCurrentPage;
	private static final int Q_SELECTED = CHILD_MENU_BASE;
	private static final int Q_ALL = CHILD_MENU_BASE + 1;
	private static final int SAVE_AS_PLAYLIST = CHILD_MENU_BASE + 2;
	private static final int PLAY_ALL = CHILD_MENU_BASE + 3;
	private static final int CLEAR_PLAYLIST = CHILD_MENU_BASE + 4;
	private static final int REMOVE = CHILD_MENU_BASE + 5;
	private static final int SEARCH = CHILD_MENU_BASE + 6;

	private static final String TAG = "TrackBrowser";

	private String[] mCursorCols;
	private String[] mPlaylistMemberCols;
	private boolean mDeletedOneRow = false;
	private boolean mEditMode = false;
	private String mCurrentTrackName;
	private String mCurrentAlbumName;
	private String mCurrentArtistNameForAlbum;
	private ListView mTrackList;
	private Cursor mTrackCursor;
	private TrackListAdapter mAdapter;
	private boolean mAdapterSent = false;
	private String mAlbumId;
	private String mArtistId;
	private String mPlaylist;
	private String mGenre;
	private String mSortOrder;
	private int mSelectedPosition;
	private long mSelectedId;
	private static int mLastListPosCourse = -1;
	private static int mLastListPosFine = -1;
	private boolean mUseLastListPos = false;
	private ServiceToken mToken;

	// private static final ProgressDialog mProgressDialog = null;

	public TrackBrowser() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.media_picker_activity, container,
				false);
		return v;
	}

	@Override
	public void onActivityCreated(Bundle icicle) {
		super.onActivityCreated(icicle);

		// setEmptyView();
		Bundle bundle = getArguments();
		getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
		if (icicle != null) {
			mSelectedId = icicle.getLong("selectedtrack");
			mAlbumId = icicle.getString("album");
			mArtistId = icicle.getString("artist");
			mPlaylist = icicle.getString("playlist");
			mGenre = icicle.getString("genre");
			mEditMode = icicle.getBoolean("editmode", false);
		} else if (bundle != null) {
			mAlbumId = bundle.getString("album");
			// If we have an album, show everything on the album, not just stuff
			// by a particular artist.
			mArtistId = bundle.getString("artist");
			mPlaylist = bundle.getString("playlist");
			mGenre = bundle.getString("genre");
			mEditMode = bundle.getBoolean(Constants.EDIT_PLAYLIST);
		}

		mCursorCols = new String[] { MediaStore.Audio.Media._ID,
				MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA,
				MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST,
				MediaStore.Audio.Media.ARTIST_ID,
				MediaStore.Audio.Media.DURATION };
		mPlaylistMemberCols = new String[] {
				MediaStore.Audio.Playlists.Members._ID,
				MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA,
				MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST,
				MediaStore.Audio.Media.ARTIST_ID,
				MediaStore.Audio.Media.DURATION,
				MediaStore.Audio.Playlists.Members.PLAY_ORDER,
				MediaStore.Audio.Playlists.Members.AUDIO_ID,
				MediaStore.Audio.Media.IS_MUSIC };

		mUseLastListPos = MusicUtils.updateButtonBar(this.getActivity(),
				R.id.songtab);
		mTrackList = getListView();
		mTrackList.setOnCreateContextMenuListener(this);
		mTrackList.setCacheColorHint(0);
		if (mEditMode) {
			((TouchInterceptor) mTrackList).setDropListener(mDropListener);
			((TouchInterceptor) mTrackList).setRemoveListener(mRemoveListener);
			mTrackList.setDivider(null);
			// mTrackList.setSelector(R.drawable.list_selector_background);
		} else {
			mTrackList.setTextFilterEnabled(true);
		}
		mAdapter = (TrackListAdapter) getActivity()
				.getLastNonConfigurationInstance();

		if (mAdapter != null) {
			mAdapter.setActivity(this);
			setListAdapter(mAdapter);
		}

		setTrackBrowserAdapter();

		// don't set the album art until after the view has been layed out
		// mTrackList.post(new Runnable() {
		//
		// public void run() {
		// setAlbumArtBackground();
		// }
		// });

	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		if (mTrackCursor.getCount() == 0) {
			return;
		}
		// When selecting a track from the queue, just jump there instead of
		// reloading the queue. This is both faster, and prevents accidentally
		// dropping out of party shuffle.
		if (mTrackCursor instanceof NowPlayingCursor) {
			if (MusicUtils.sService != null) {
				try {
					MusicUtils.sService.setQueuePosition(position);
					return;
				} catch (RemoteException ex) {
				}
			}
		}
		MusicUtils.playAll(this.getActivity(), mTrackCursor, position);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (mTrackCursor != null) {
			getListView().invalidateViews();
		}
		MusicUtils.setSpinnerState(this.getActivity());
	}

	@Override
	public void onPause() {
		mReScanHandler.removeCallbacksAndMessages(null);
		super.onPause();
	}

	/*
	 * This listener gets called when the media scanner starts up or finishes,
	 * and when the sd card is unmounted.
	 */
	private BroadcastReceiver mScanListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (Intent.ACTION_MEDIA_SCANNER_STARTED.equals(action)
					|| Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)) {
				MusicUtils.setSpinnerState(TrackBrowser.this.getActivity());
			}
			mReScanHandler.sendEmptyMessage(0);
		}
	};

	private void setTitle() {

		CharSequence fancyName = null;
		if (mAlbumId != null) {
			int numresults = mTrackCursor != null ? mTrackCursor.getCount() : 0;
			if (numresults > 0) {
				mTrackCursor.moveToFirst();
				int idx = mTrackCursor
						.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
				fancyName = mTrackCursor.getString(idx);
				// For compilation albums show only the album title,
				// but for regular albums show "artist - album".
				// To determine whether something is a compilation
				// album, do a query for the artist + album of the
				// first item, and see if it returns the same number
				// of results as the album query.
				String where = MediaStore.Audio.Media.ALBUM_ID
						+ "='"
						+ mAlbumId
						+ "' AND "
						+ MediaStore.Audio.Media.ARTIST_ID
						+ "="
						+ mTrackCursor
								.getLong(mTrackCursor
										.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID));
				Cursor cursor = MusicUtils.query(this.getActivity(),
						MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
						new String[] { MediaStore.Audio.Media.ALBUM }, where,
						null, null);
				if (cursor != null) {
					if (cursor.getCount() != numresults) {
						// compilation album
						fancyName = mTrackCursor.getString(idx);
					}
					cursor.close();
				}
				if (fancyName == null
						|| fancyName.equals(MediaStore.UNKNOWN_STRING)) {
					fancyName = getString(R.string.unknown_album_name);
				}
			}
		} else if (mPlaylist != null) {
			if (mPlaylist.equals("nowplaying")) {
				if (MusicUtils.getCurrentShuffleMode() == MediaPlaybackService.SHUFFLE_AUTO) {
					fancyName = getText(R.string.partyshuffle_title);
				} else {
					fancyName = getText(R.string.nowplaying_title);
				}
			} else if (mPlaylist.equals("podcasts")) {
				fancyName = getText(R.string.podcasts_title);
			} else if (mPlaylist.equals("recentlyadded")) {
				fancyName = getText(R.string.recentlyadded_title);
			} else {
				String[] cols = new String[] { MediaStore.Audio.Playlists.NAME };
				Cursor cursor = MusicUtils.query(this.getActivity(),
						ContentUris.withAppendedId(
								Playlists.EXTERNAL_CONTENT_URI,
								Long.valueOf(mPlaylist)), cols, null, null,
						null);
				if (cursor != null) {
					if (cursor.getCount() != 0) {
						cursor.moveToFirst();
						fancyName = cursor.getString(0);
					}
					cursor.close();
				}
			}
		} else if (mGenre != null) {
			String[] cols = new String[] { MediaStore.Audio.Genres.NAME };
			Cursor cursor = MusicUtils.query(this.getActivity(), ContentUris
					.withAppendedId(
							MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
							Long.valueOf(mGenre)), cols, null, null, null);
			if (cursor != null) {
				if (cursor.getCount() != 0) {
					cursor.moveToFirst();
					fancyName = cursor.getString(0);
				}
				cursor.close();
			}
		}

		if (fancyName != null) {
			getActivity().setTitle(fancyName);
		} else {
			getActivity().setTitle(R.string.tracks_title);
		}
	}

	private Handler mReScanHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (mAdapter != null) {
				getTrackCursor(mAdapter.getQueryHandler(), null, true);
			}
			// if the query results in a null cursor, onQueryComplete() will
			// call init(), which will post a delayed message to this handler
			// in order to try again.
		}
	};

	private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			getListView().invalidateViews();
			if (!mEditMode) {
				MusicUtils.updateNowPlaying(TrackBrowser.this.getActivity());
			}
		}
	};

	private BroadcastReceiver mNowPlayingListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(MediaPlaybackService.META_CHANGED)) {
				getListView().invalidateViews();
			} else if (intent.getAction().equals(
					MediaPlaybackService.QUEUE_CHANGED)) {
				if (mDeletedOneRow) {
					// This is the notification for a single row that was
					// deleted previously, which is already reflected in
					// the UI.
					mDeletedOneRow = false;
					return;
				}
				// The service could disappear while the broadcast was in
				// flight,
				// so check to see if it's still valid
				if (MusicUtils.sService == null) {
					getActivity().finish();
					return;
				}
				if (mAdapter != null) {
					Cursor c = new NowPlayingCursor(MusicUtils.sService,
							mCursorCols);
					if (c.getCount() == 0) {
						getActivity().finish();
						return;
					}
					mAdapter.changeCursor(c);
				}
			}
		}
	};

	public void init(Cursor newCursor, boolean isLimited) {

		if (mAdapter == null) {
			return;
		}
		mAdapter.changeCursor(newCursor); // also sets mTrackCursor

		if (mTrackCursor == null) {
			MusicUtils.displayDatabaseError(this.getActivity());
			getActivity().closeContextMenu();
			mReScanHandler.sendEmptyMessageDelayed(0, 1000);
			return;
		}

		MusicUtils.hideDatabaseError(this.getActivity());
		mUseLastListPos = MusicUtils.updateButtonBar(this.getActivity(),
				R.id.songtab);
		setTitle();

		// Restore previous position
		if (mLastListPosCourse >= 0 && mUseLastListPos) {
			ListView lv = getListView();
			// this hack is needed because otherwise the position doesn't change
			// for the 2nd (non-limited) cursor
			lv.setAdapter(lv.getAdapter());
			lv.setSelectionFromTop(mLastListPosCourse, mLastListPosFine);
			if (!isLimited) {
				mLastListPosCourse = -1;
			}
		}

		// When showing the queue, position the selection on the currently
		// playing track
		// Otherwise, position the selection on the first matching artist, if
		// any
		IntentFilter f = new IntentFilter();
		f.addAction(MediaPlaybackService.META_CHANGED);
		f.addAction(MediaPlaybackService.QUEUE_CHANGED);
		if ("nowplaying".equals(mPlaylist)) {
			try {
				int cur = MusicUtils.sService.getQueuePosition();
				setSelection(cur);
				getActivity().registerReceiver(mNowPlayingListener,
						new IntentFilter(f));
				mNowPlayingListener.onReceive(this.getActivity(), new Intent(
						MediaPlaybackService.META_CHANGED));
			} catch (RemoteException ex) {
			}
		} else {
			String key = getActivity().getIntent().getStringExtra("artist");
			if (key != null) {
				int keyidx = mTrackCursor
						.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID);
				mTrackCursor.moveToFirst();
				while (!mTrackCursor.isAfterLast()) {
					String artist = mTrackCursor.getString(keyidx);
					if (artist.equals(key)) {
						setSelection(mTrackCursor.getPosition());
						break;
					}
					mTrackCursor.moveToNext();
				}
			}
			getActivity().registerReceiver(mTrackListListener,
					new IntentFilter(f));
			mTrackListListener.onReceive(this.getActivity(), new Intent(
					MediaPlaybackService.META_CHANGED));
		}
	}

	private void setAlbumArtBackground() {
		if (!mEditMode) {
			try {
				long albumid = Long.valueOf(mAlbumId);
				Bitmap bm = MusicUtils.getArtwork(
						TrackBrowser.this.getActivity(), -1, albumid, false);
				if (bm != null) {
					// MusicUtils.setBackground(mTrackList, bm);
					mTrackList.setCacheColorHint(0);
					return;
				}
			} catch (Exception ex) {
			}
		}
		mTrackList.setBackgroundColor(0xff000000);
		mTrackList.setCacheColorHint(0);
	}

	private void setEmptyView() {
		TextView noMedia = null;
		noMedia = (TextView) getView().findViewById(R.id.error_message);
		getListView().setEmptyView(noMedia);
	}

	private Cursor getTrackCursor(
			TrackListAdapter.TrackQueryHandler queryhandler, String filter,
			boolean async) {

		if (queryhandler == null) {
			throw new IllegalArgumentException();
		}

		Cursor ret = null;
		mSortOrder = MediaStore.Audio.Media.TITLE_KEY;
		StringBuilder where = new StringBuilder();
		where.append(MediaStore.Audio.Media.TITLE + " != ''");

		if (mGenre != null) {
			Uri uri = MediaStore.Audio.Genres.Members.getContentUri("external",
					Integer.valueOf(mGenre));
			if (!TextUtils.isEmpty(filter)) {
				uri = uri.buildUpon()
						.appendQueryParameter("filter", Uri.encode(filter))
						.build();
			}
			mSortOrder = MediaStore.Audio.Genres.Members.DEFAULT_SORT_ORDER;
			ret = queryhandler.doQuery(uri, mCursorCols, where.toString(),
					null, mSortOrder, async);
		} else if (mPlaylist != null) {
			getListView().setFastScrollEnabled(false);
			if (mPlaylist.equals("nowplaying")) {
				if (MusicUtils.sService != null) {
					ret = new NowPlayingCursor(MusicUtils.sService, mCursorCols);
					if (ret.getCount() == 0) {
						getActivity().finish();
					}
				} else {
					// Nothing is playing.
				}
			} else if (mPlaylist.equals("podcasts")) {
				where.append(" AND " + MediaStore.Audio.Media.IS_PODCAST + "=1");
				Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				if (!TextUtils.isEmpty(filter)) {
					uri = uri.buildUpon()
							.appendQueryParameter("filter", Uri.encode(filter))
							.build();
				}
				ret = queryhandler.doQuery(uri, mCursorCols, where.toString(),
						null, MediaStore.Audio.Media.DEFAULT_SORT_ORDER, async);
			} else if (mPlaylist.equals("recentlyadded")) {
				// do a query for all songs added in the last X weeks
				Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				if (!TextUtils.isEmpty(filter)) {
					uri = uri.buildUpon()
							.appendQueryParameter("filter", Uri.encode(filter))
							.build();
				}
				int X = MusicUtils
						.getIntPref(this.getActivity(), "numweeks", 2)
						* (3600 * 24 * 7);
				where.append(" AND " + MediaStore.MediaColumns.DATE_ADDED + ">");
				where.append(System.currentTimeMillis() / 1000 - X);
				ret = queryhandler.doQuery(uri, mCursorCols, where.toString(),
						null, MediaStore.Audio.Media.DEFAULT_SORT_ORDER, async);
			} else {
				Uri uri = MediaStore.Audio.Playlists.Members.getContentUri(
						"external", Long.valueOf(mPlaylist));
				if (!TextUtils.isEmpty(filter)) {
					uri = uri.buildUpon()
							.appendQueryParameter("filter", Uri.encode(filter))
							.build();
				}
				mSortOrder = MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER;
				ret = queryhandler.doQuery(uri, mPlaylistMemberCols,
						where.toString(), null, mSortOrder, async);
			}
		} else {
			if (mAlbumId != null) {
				where.append(" AND " + MediaStore.Audio.Media.ALBUM_ID + "="
						+ mAlbumId);
				mSortOrder = MediaStore.Audio.Media.TRACK + ", " + mSortOrder;
			}
			if (mArtistId != null) {
				where.append(" AND " + MediaStore.Audio.Media.ARTIST_ID + "="
						+ mArtistId);
			}
			
			where.append(" AND " + MediaStore.Audio.Media.IS_MUSIC + "=1");
			Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
			if (!TextUtils.isEmpty(filter)) {
				uri = uri.buildUpon()
						.appendQueryParameter("filter", Uri.encode(filter))
						.build();
			}
			ret = queryhandler.doQuery(uri, mCursorCols, where.toString(),
					null, mSortOrder, async);
		}

		// This special case is for the "nowplaying" cursor, which cannot be
		// handled
		// asynchronously using AsyncQueryHandler, so we do some extra
		// initialization here.
		if (ret != null && async) {
			init(ret, false);
			setTitle();
		}
		return ret;
	}

	class TrackListAdapter extends SimpleCursorAdapter implements
			SectionIndexer {
		boolean mIsNowPlaying;
		boolean mDisableNowPlayingIndicator;

		int mTitleIdx;
		int mArtistIdx;
		int mDurationIdx;
		int mAudioIdIdx;

		private final StringBuilder mBuilder = new StringBuilder();
		private final String mUnknownArtist;
		private final String mUnknownAlbum;

		private AlphabetIndexer mIndexer;

		private TrackBrowser mFragment = null;
		private TrackQueryHandler mQueryHandler;
		private String mConstraint = null;
		private boolean mConstraintIsValid = false;

		class ViewHolder {
			TextView line1;
			TextView line2;
			TextView duration;
			ImageView play_indicator;
			CharArrayBuffer buffer1;
			char[] buffer2;
		}

		class TrackQueryHandler extends AsyncQueryHandler {

			class QueryArgs {
				public Uri uri;
				public String[] projection;
				public String selection;
				public String[] selectionArgs;
				public String orderBy;
			}

			TrackQueryHandler(ContentResolver res) {
				super(res);
			}

			public Cursor doQuery(Uri uri, String[] projection,
					String selection, String[] selectionArgs, String orderBy,
					boolean async) {
				if (async) {
					// Get 100 results first, which is enough to allow the user
					// to start scrolling,
					// while still being very fast.
					Uri limituri = uri.buildUpon()
							.appendQueryParameter("limit", "100").build();
					QueryArgs args = new QueryArgs();
					args.uri = uri;
					args.projection = projection;
					args.selection = selection;
					args.selectionArgs = selectionArgs;
					args.orderBy = orderBy;

					startQuery(0, args, limituri, projection, selection,
							selectionArgs, orderBy);
					return null;
				}
				return MusicUtils.query(mFragment.getActivity(), uri,
						projection, selection, selectionArgs, orderBy);
			}

			@Override
			protected void onQueryComplete(int token, Object cookie,
					Cursor cursor) {
				// Log.i("@@@", "query complete: " + cursor.getCount() + "   " +
				// mActivity);
				mFragment.init(cursor, cookie != null);
				if (token == 0 && cookie != null && cursor != null
						&& cursor.getCount() >= 100) {
					QueryArgs args = (QueryArgs) cookie;
					startQuery(1, null, args.uri, args.projection,
							args.selection, args.selectionArgs, args.orderBy);
				}
			}
		}

		TrackListAdapter(Context context, TrackBrowser currentactivity,
				int layout, Cursor cursor, String[] from, int[] to,
				boolean isnowplaying, boolean disablenowplayingindicator) {
			super(context, layout, cursor, from, to);
			mFragment = currentactivity;
			getColumnIndices(cursor);
			mIsNowPlaying = isnowplaying;
			mDisableNowPlayingIndicator = disablenowplayingindicator;
			mUnknownArtist = context.getString(R.string.unknown_artist_name);
			mUnknownAlbum = context.getString(R.string.unknown_album_name);

			mQueryHandler = new TrackQueryHandler(context.getContentResolver());
		}

		public void setActivity(TrackBrowser newactivity) {
			mFragment = newactivity;
		}

		public TrackQueryHandler getQueryHandler() {
			return mQueryHandler;
		}

		private void getColumnIndices(Cursor cursor) {
			if (cursor != null) {
				mTitleIdx = cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
				mArtistIdx = cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
				mDurationIdx = cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
				try {
					mAudioIdIdx = cursor
							.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
				} catch (IllegalArgumentException ex) {
					mAudioIdIdx = cursor
							.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
				}

				if (mIndexer != null) {
					mIndexer.setCursor(cursor);
				} else if (!mFragment.mEditMode && mFragment.mAlbumId == null) {
					String alpha = mFragment
							.getString(R.string.fast_scroll_alphabet);

					mIndexer = new MusicAlphabetIndexer(cursor, mTitleIdx,
							alpha);
				}
			}
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			View v = super.newView(context, cursor, parent);
			ImageView iv = (ImageView) v.findViewById(R.id.icon);
			iv.setVisibility(View.GONE);

			ViewHolder vh = new ViewHolder();
			vh.line1 = (TextView) v.findViewById(R.id.line1);
			vh.line2 = (TextView) v.findViewById(R.id.line2);
			vh.duration = (TextView) v.findViewById(R.id.duration);
			vh.play_indicator = (ImageView) v.findViewById(R.id.play_indicator);
			vh.buffer1 = new CharArrayBuffer(100);
			vh.buffer2 = new char[200];
			v.setTag(vh);
			return v;
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {

			ViewHolder vh = (ViewHolder) view.getTag();

			cursor.copyStringToBuffer(mTitleIdx, vh.buffer1);
			vh.line1.setText(vh.buffer1.data, 0, vh.buffer1.sizeCopied);

			int secs = cursor.getInt(mDurationIdx) / 1000;
			if (secs == 0) {
				vh.duration.setText("");
			} else {
				vh.duration.setText(MusicUtils.makeTimeString(context, secs));
			}

			final StringBuilder builder = mBuilder;
			builder.delete(0, builder.length());

			String name = cursor.getString(mArtistIdx);
			if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
				builder.append(mUnknownArtist);
			} else {
				builder.append(name);
			}
			int len = builder.length();
			if (vh.buffer2.length < len) {
				vh.buffer2 = new char[len];
			}
			builder.getChars(0, len, vh.buffer2, 0);
			vh.line2.setText(vh.buffer2, 0, len);

			ImageView iv = vh.play_indicator;
			long id = -1;
			if (MusicUtils.sService != null) {
				// TODO: IPC call on each bind??
				try {
					if (mIsNowPlaying) {
						id = MusicUtils.sService.getQueuePosition();
					} else {
						id = MusicUtils.sService.getAudioId();
					}
				} catch (RemoteException ex) {
				}
			}

			// Determining whether and where to show the "now playing indicator
			// is tricky, because we don't actually keep track of where the
			// songs
			// in the current playlist came from after they've started playing.
			//
			// If the "current playlists" is shown, then we can simply match by
			// position,
			// otherwise, we need to match by id. Match-by-id gets a little
			// weird if
			// a song appears in a playlist more than once, and you're in
			// edit-playlist
			// mode. In that case, both items will have the "now playing"
			// indicator.
			// For this reason, we don't show the play indicator at all when in
			// edit
			// playlist mode (except when you're viewing the "current playlist",
			// which is not really a playlist)
			if ((mIsNowPlaying && cursor.getPosition() == id)
					|| (!mIsNowPlaying && !mDisableNowPlayingIndicator && cursor
							.getLong(mAudioIdIdx) == id)) {
				iv.setImageResource(R.drawable.indicator_ic_mp_playing_list);
				iv.setVisibility(View.VISIBLE);
			} else {
				iv.setVisibility(View.GONE);
			}
		}

		@Override
		public void changeCursor(Cursor cursor) {
			if (mFragment.getActivity().isFinishing() && cursor != null) {
				cursor.close();
				cursor = null;
			}
			// mProgressDialog.dismiss();
			// if (cursor == null || cursor.getCount() == 0) {
			// setEmptyView();
			// }
			if (cursor != mFragment.mTrackCursor) {
				mFragment.mTrackCursor = cursor;
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
			Cursor c = mFragment.getTrackCursor(mQueryHandler, s, false);
			mConstraint = s;
			mConstraintIsValid = true;
			return c;
		}

		// SectionIndexer methods

		public Object[] getSections() {
			if (mIndexer != null) {
				return mIndexer.getSections();
			} else {
				return new String[] { " " };
			}
		}

		public int getPositionForSection(int section) {
			if (mIndexer != null) {
				return mIndexer.getPositionForSection(section);
			}
			return 0;
		}

		public int getSectionForPosition(int position) {
			return 0;
		}
	}


	private class NowPlayingCursor extends AbstractCursor {
		public NowPlayingCursor(IMediaPlaybackService service, String[] cols) {
			mCols = cols;
			mService = service;
			makeNowPlayingCursor();
		}

		private void makeNowPlayingCursor() {
			mCurrentPlaylistCursor = null;
			try {
				mNowPlaying = mService.getQueue();
			} catch (RemoteException ex) {
				mNowPlaying = new long[0];
			}
			mSize = mNowPlaying.length;
			if (mSize == 0) {
				return;
			}

			StringBuilder where = new StringBuilder();
			where.append(MediaStore.Audio.Media._ID + " IN (");
			for (int i = 0; i < mSize; i++) {
				where.append(mNowPlaying[i]);
				if (i < mSize - 1) {
					where.append(",");
				}
			}
			where.append(")");

			mCurrentPlaylistCursor = MusicUtils.query(
					TrackBrowser.this.getActivity(),
					MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mCols,
					where.toString(), null, MediaStore.Audio.Media._ID);

			if (mCurrentPlaylistCursor == null) {
				mSize = 0;
				return;
			}

			int size = mCurrentPlaylistCursor.getCount();
			mCursorIdxs = new long[size];
			mCurrentPlaylistCursor.moveToFirst();
			int colidx = mCurrentPlaylistCursor
					.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
			for (int i = 0; i < size; i++) {
				mCursorIdxs[i] = mCurrentPlaylistCursor.getLong(colidx);
				mCurrentPlaylistCursor.moveToNext();
			}
			mCurrentPlaylistCursor.moveToFirst();
			mCurPos = -1;

			// At this point we can verify the 'now playing' list we got
			// earlier to make sure that all the items in there still exist
			// in the database, and remove those that aren't. This way we
			// don't get any blank items in the list.
			try {
				int removed = 0;
				for (int i = mNowPlaying.length - 1; i >= 0; i--) {
					long trackid = mNowPlaying[i];
					int crsridx = Arrays.binarySearch(mCursorIdxs, trackid);
					if (crsridx < 0) {
						// Log.i("@@@@@", "item no longer exists in db: " +
						// trackid);
						removed += mService.removeTrack(trackid);
					}
				}
				if (removed > 0) {
					mNowPlaying = mService.getQueue();
					mSize = mNowPlaying.length;
					if (mSize == 0) {
						mCursorIdxs = null;
						return;
					}
				}
			} catch (RemoteException ex) {
				mNowPlaying = new long[0];
			}
		}

		@Override
		public int getCount() {
			return mSize;
		}

		@Override
		public boolean onMove(int oldPosition, int newPosition) {
			if (oldPosition == newPosition)
				return true;

			if (mNowPlaying == null || mCursorIdxs == null
					|| newPosition >= mNowPlaying.length) {
				return false;
			}

			// The cursor doesn't have any duplicates in it, and is not ordered
			// in queue-order, so we need to figure out where in the cursor we
			// should be.

			long newid = mNowPlaying[newPosition];
			int crsridx = Arrays.binarySearch(mCursorIdxs, newid);
			mCurrentPlaylistCursor.moveToPosition(crsridx);
			mCurPos = newPosition;

			return true;
		}

		public boolean removeItem(int which) {
			try {
				if (mService.removeTracks(which, which) == 0) {
					return false; // delete failed
				}
				int i = (int) which;
				mSize--;
				while (i < mSize) {
					mNowPlaying[i] = mNowPlaying[i + 1];
					i++;
				}
				onMove(-1, (int) mCurPos);
			} catch (RemoteException ex) {
			}
			return true;
		}

		public void moveItem(int from, int to) {
			try {
				mService.moveQueueItem(from, to);
				mNowPlaying = mService.getQueue();
				onMove(-1, mCurPos); // update the underlying cursor
			} catch (RemoteException ex) {
			}
		}

		private void dump() {
			String where = "(";
			for (int i = 0; i < mSize; i++) {
				where += mNowPlaying[i];
				if (i < mSize - 1) {
					where += ",";
				}
			}
			where += ")";
		}

		@Override
		public String getString(int column) {
			try {
				return mCurrentPlaylistCursor.getString(column);
			} catch (Exception ex) {
				onChange(true);
				return "";
			}
		}

		@Override
		public short getShort(int column) {
			return mCurrentPlaylistCursor.getShort(column);
		}

		@Override
		public int getInt(int column) {
			try {
				return mCurrentPlaylistCursor.getInt(column);
			} catch (Exception ex) {
				onChange(true);
				return 0;
			}
		}

		@Override
		public long getLong(int column) {
			try {
				return mCurrentPlaylistCursor.getLong(column);
			} catch (Exception ex) {
				onChange(true);
				return 0;
			}
		}

		@Override
		public float getFloat(int column) {
			return mCurrentPlaylistCursor.getFloat(column);
		}

		@Override
		public double getDouble(int column) {
			return mCurrentPlaylistCursor.getDouble(column);
		}

		@Override
		public int getType(int column) {
			return mCurrentPlaylistCursor.getType(column);
		}

		@Override
		public boolean isNull(int column) {
			return mCurrentPlaylistCursor.isNull(column);
		}

		@Override
		public String[] getColumnNames() {
			return mCols;
		}

		@Override
		public void deactivate() {
			if (mCurrentPlaylistCursor != null)
				mCurrentPlaylistCursor.close();
		}

		@Override
		public boolean requery() {
			makeNowPlayingCursor();
			return true;
		}

		private String[] mCols;
		private Cursor mCurrentPlaylistCursor; // updated in onMove
		private int mSize; // size of the queue
		private long[] mNowPlaying;
		private long[] mCursorIdxs;
		private int mCurPos;
		private IMediaPlaybackService mService;
	}

	@Override
	public void onDestroyView() {
		ListView lv = getListView();
		if (lv != null) {
			if (mUseLastListPos) {
				mLastListPosCourse = lv.getFirstVisiblePosition();
				View cv = lv.getChildAt(0);
				if (cv != null) {
					mLastListPosFine = cv.getTop();
				}
			}
			if (mEditMode) {
				// clear the listeners so we won't get any more callbacks
				// ((TouchInterceptor) lv).setDropListener(null);
				// ((TouchInterceptor) lv).setRemoveListener(null);
			}
		}

		MusicUtils.unbindFromService(mToken);
		try {
			if ("nowplaying".equals(mPlaylist)) {
				unregisterReceiverSafe(mNowPlayingListener);
			} else {
				unregisterReceiverSafe(mTrackListListener);
			}
		} catch (IllegalArgumentException ex) {
			// we end up here in case we never registered the listeners
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
		unregisterReceiverSafe(mScanListener);
		super.onDestroyView();
	}

	/**
	 * Unregister a receiver, but eat the exception that is thrown if the
	 * receiver was never registered to begin with. This is a little easier than
	 * keeping track of whether the receivers have actually been registered by
	 * the time onDestroy() is called.
	 */
	private void unregisterReceiverSafe(BroadcastReceiver receiver) {
		try {
			getActivity().unregisterReceiver(receiver);
		} catch (IllegalArgumentException e) {
			// ignore
		}
	}

	public void setTrackBrowserAdapter() {

		IntentFilter f = new IntentFilter();
		f.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
		f.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
		f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
		f.addDataScheme("file");
		getActivity().registerReceiver(mScanListener, f);

		if (mAdapter == null) {
			// Log.i("@@@", "starting query");
			mAdapter = new TrackListAdapter(
					getActivity().getApplication(), // need to use application
													// context to avoid leaks
					this,
					mEditMode ? R.layout.edit_track_list_item
							: R.layout.track_list_item,
					null, // cursor
					new String[] {}, new int[] {},
					"nowplaying".equals(mPlaylist),
					mPlaylist != null
							&& !(mPlaylist.equals("podcasts") || mPlaylist
									.equals("recentlyadded")));
			setListAdapter(mAdapter);
			getActivity().setTitle(R.string.working_songs);
			getTrackCursor(mAdapter.getQueryHandler(), null, true);
		} else {
			mTrackCursor = mAdapter.getCursor();
			// If mTrackCursor is null, this can be because it doesn't have
			// a cursor yet (because the initial query that sets its cursor
			// is still in progress), or because the query failed.
			// In order to not flash the error dialog at the user for the
			// first case, simply retry the query when the cursor is null.
			// Worst case, we end up doing the same query twice.
			if (mTrackCursor != null) {
				init(mTrackCursor, false);
			} else {
				getActivity().setTitle(R.string.working_songs);
				getTrackCursor(mAdapter.getQueryHandler(), null, true);
			}
		}
		if (!mEditMode) {
			MusicUtils.updateNowPlaying(this.getActivity());
		}

	}

	private TouchInterceptor.DropListener mDropListener = new TouchInterceptor.DropListener() {
		public void drop(int from, int to) {
			if (mTrackCursor instanceof NowPlayingCursor) {
				// update the currently playing list
				NowPlayingCursor c = (NowPlayingCursor) mTrackCursor;
				c.moveItem(from, to);
				((TrackListAdapter) getListAdapter()).notifyDataSetChanged();
				getListView().invalidateViews();
				mDeletedOneRow = true;
			} else {
				// update a saved playlist
				MediaStore.Audio.Playlists.Members.moveItem(getActivity()
						.getContentResolver(), Long.valueOf(mPlaylist), from,
						to);
			}
		}
	};

	private TouchInterceptor.RemoveListener mRemoveListener = new TouchInterceptor.RemoveListener() {
		public void remove(int which) {
			removePlaylistItem(which);
		}
	};

	private void removePlaylistItem(int which) {
		View v = mTrackList.getChildAt(which
				- mTrackList.getFirstVisiblePosition());
		if (v == null) {
			return;
		}
		try {
			if (MusicUtils.sService != null
					&& which != MusicUtils.sService.getQueuePosition()) {
				mDeletedOneRow = true;
			}
		} catch (RemoteException e) {
			// Service died, so nothing playing.
			mDeletedOneRow = true;
		}
		v.setVisibility(View.GONE);
		mTrackList.invalidateViews();
		if (mTrackCursor instanceof NowPlayingCursor) {
			((NowPlayingCursor) mTrackCursor).removeItem(which);
		} else {
			int colidx = mTrackCursor
					.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members._ID);
			mTrackCursor.moveToPosition(which);
			long id = mTrackCursor.getLong(colidx);
			Uri uri = MediaStore.Audio.Playlists.Members.getContentUri(
					"external", Long.valueOf(mPlaylist));
			getActivity().getContentResolver().delete(
					ContentUris.withAppendedId(uri, id), null, null);
		}
		v.setVisibility(View.VISIBLE);
		mTrackList.invalidateViews();
	}
	
	@Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) {
        menu.add(0, PLAY_SELECTION, 0, R.string.play_selection);
        SubMenu sub = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0, R.string.add_to_playlist);
        MusicUtils.makePlaylistMenu(this.getActivity(), sub);
        if (mEditMode) {
            menu.add(0, REMOVE, 0, R.string.remove_from_playlist);
        }
        menu.add(0, USE_AS_RINGTONE, 0, R.string.ringtone_menu);
        menu.add(0, DELETE_ITEM, 0, R.string.delete_item);
        AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfoIn;
        mSelectedPosition =  mi.position;
        mTrackCursor.moveToPosition(mSelectedPosition);
        try {
            int id_idx = mTrackCursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Playlists.Members.AUDIO_ID);
            mSelectedId = mTrackCursor.getLong(id_idx);
        } catch (IllegalArgumentException ex) {
            mSelectedId = mi.id;
        }
        // only add the 'search' menu if the selected item is music
        if (isMusic(mTrackCursor)) {
            menu.add(0, SEARCH, 0, R.string.search_title);
        }
        mCurrentAlbumName = mTrackCursor.getString(mTrackCursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ALBUM));
        mCurrentArtistNameForAlbum = mTrackCursor.getString(mTrackCursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ARTIST));
        mCurrentTrackName = mTrackCursor.getString(mTrackCursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.TITLE));
        menu.setHeaderTitle(mCurrentTrackName);
//        super.onCreateContextMenu(menu, view, menuInfoIn);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case PLAY_SELECTION: {
                // play the track
                int position = mSelectedPosition;
                MusicUtils.playAll(this.getActivity(), mTrackCursor, position);
                return true;
            }

            case QUEUE: {
                long [] list = new long[] { mSelectedId };
                MusicUtils.addToCurrentPlaylist(this.getActivity(), list);
                return true;
            }

            case NEW_PLAYLIST: {
                Intent intent = new Intent();
                intent.setClass(this.getActivity(), CreatePlaylist.class);
                startActivityForResult(intent, NEW_PLAYLIST);
                return true;
            }

            case PLAYLIST_SELECTED: {
                long [] list = new long[] { mSelectedId };
                long playlist = item.getIntent().getLongExtra("playlist", 0);
                MusicUtils.addToPlaylist(this.getActivity(), list, playlist);
                return true;
            }

            case USE_AS_RINGTONE:
                // Set the system setting to make this the current ringtone
                MusicUtils.setRingtone(this.getActivity(), mSelectedId);
                return true;

            case DELETE_ITEM: {
                long [] list = new long[1];
                list[0] = (int) mSelectedId;
                Bundle b = new Bundle();
                String f;
//                if (android.os.Environment.isExternalStorageRemovable()) {
                    f = getString(R.string.delete_song_desc); 
//                } else {
//                    f = getString(R.string.delete_song_desc_nosdcard); 
//                }
                String desc = String.format(f, mCurrentTrackName);
                b.putString("description", desc);
                b.putLongArray("items", list);
                Intent intent = new Intent();
                intent.setClass(this.getActivity(), DeleteItems.class);
                intent.putExtras(b);
                startActivityForResult(intent, -1);
                return true;
            }
            
            case REMOVE:
                removePlaylistItem(mSelectedPosition);
                return true;
                
            case SEARCH:
                doSearch();
                return true;
        }
        return super.onContextItemSelected(item);
    }

    void doSearch() {
        CharSequence title = null;
        String query = null;
        
        Intent i = new Intent();
        i.setAction(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        title = mCurrentTrackName;
        if (MediaStore.UNKNOWN_STRING.equals(mCurrentArtistNameForAlbum)) {
            query = mCurrentTrackName;
        } else {
            query = mCurrentArtistNameForAlbum + " " + mCurrentTrackName;
            i.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, mCurrentArtistNameForAlbum);
        }
        if (MediaStore.UNKNOWN_STRING.equals(mCurrentAlbumName)) {
            i.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, mCurrentAlbumName);
        }
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "audio/*");
        title = getString(R.string.mediasearch, title);
        i.putExtra(SearchManager.QUERY, query);

        startActivity(Intent.createChooser(i, title));
    }
    
    // Cursor should be positioned on the entry to be checked
    // Returns false if the entry matches the naming pattern used for recordings,
    // or if it is marked as not music in the database.
    private boolean isMusic(Cursor c) {
        int titleidx = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
        int albumidx = c.getColumnIndex(MediaStore.Audio.Media.ALBUM);
        int artistidx = c.getColumnIndex(MediaStore.Audio.Media.ARTIST);

        String title = c.getString(titleidx);
        String album = c.getString(albumidx);
        String artist = c.getString(artistidx);
        if (MediaStore.UNKNOWN_STRING.equals(album) &&
                MediaStore.UNKNOWN_STRING.equals(artist) &&
                title != null &&
                title.startsWith("recording")) {
            // not music
            return false;
        }

        int ismusic_idx = c.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC);
        boolean ismusic = true;
        if (ismusic_idx >= 0) {
            ismusic = mTrackCursor.getInt(ismusic_idx) != 0;
        }
        return ismusic;
    }
}
