package com.vink.music.ui.fragments;

import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView.FindListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AlphabetIndexer;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.vink.music.R;
import com.vink.music.interfaces.NotifyMainLibrary;
import com.vink.music.support.MediaPlaybackService;
import com.vink.music.ui.CreatePlaylist;
import com.vink.music.ui.DeleteItems;
import com.vink.music.util.Constants;
import com.vink.music.util.MusicAlphabetIndexer;
import com.vink.music.util.MusicUtils;

public class AlbumBrowser extends Fragment implements MusicUtils.Defs,OnItemClickListener{

	private String mCurrentAlbumId;
	private String mCurrentAlbumName;
	private String mCurrentArtistNameForAlbum;
	boolean mIsUnknownArtist;
	boolean mIsUnknownAlbum;
	private AlbumListAdapter mAdapter;
	private boolean mAdapterSent;
	private final static int SEARCH = CHILD_MENU_BASE;
	private static final String TAG = "AlbumBrowser";
	private static int mLastListPosCourse = -1;
	private static int mLastListPosFine = -1;
	private Cursor mAlbumCursor;
	private String mArtistId;
	private NotifyMainLibrary mActivityCommunicator;
	private View vg;
	private GridView mGridView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {

		if (savedInstanceState != null) {
			mCurrentAlbumId = savedInstanceState.getString("selectedalbum");
			mArtistId = savedInstanceState.getString("artist");
		} else {
			mArtistId = getActivity().getIntent().getStringExtra("artist");
		}

		mActivityCommunicator = (NotifyMainLibrary) getActivity();

		super.onActivityCreated(savedInstanceState);
		getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);

		MusicUtils.updateNowPlaying(this.getActivity());

		IntentFilter f = new IntentFilter();
		f.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
		f.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
		f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
		f.addDataScheme("file");
		getActivity().registerReceiver(mScanListener, f);

		// MusicUtils.updateButtonBar(this.getActivity(), R.id.albumtab);
		//ListView lv = getListView();
		mGridView = (GridView) vg.findViewById(R.id.gridView1);
		mGridView.setOnCreateContextMenuListener(this);
		mGridView.setOnItemClickListener(this);
		mGridView.setTextFilterEnabled(true);
		setEmptyView();

		mAdapter = (AlbumListAdapter) getActivity()
				.getLastNonConfigurationInstance();
		if (mAdapter == null) {
			// Log.i("@@@", "starting query");
			mAdapter = new AlbumListAdapter(getActivity().getApplication(),
					this, R.layout.album_grid_view_layout, mAlbumCursor,
					new String[] {}, new int[] {});
		//	setListAdapter(mAdapter);
			mGridView.setAdapter(mAdapter);
			getActivity().setTitle(R.string.working_albums);
			getAlbumCursor(mAdapter.getQueryHandler(), null);
		} else {
			mAdapter.setActivity(this);
			mGridView.setAdapter(mAdapter);
			mAlbumCursor = mAdapter.getCursor();
			if (mAlbumCursor != null) {
				init(mAlbumCursor);
			} else {
				getAlbumCursor(mAdapter.getQueryHandler(), null);
			}
		}

	}

	private void setEmptyView() {
		TextView noMedia = null;
		noMedia = (TextView) getView().findViewById(R.id.error_message);
		mGridView.setEmptyView(noMedia);
		
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.album_grid_view, container,
				false);
		vg = view;
		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

	}

	@Override
	public void onStart() {
		super.onStart();
		IntentFilter f = new IntentFilter();
		f.addAction(MediaPlaybackService.META_CHANGED);
		f.addAction(MediaPlaybackService.QUEUE_CHANGED);
		getActivity().registerReceiver(mTrackListListener, f);
		mTrackListListener.onReceive(null, null);

		MusicUtils.setSpinnerState(this.getActivity());
	}

	@Override
	public void onStop() {
		getActivity().unregisterReceiver(mTrackListListener);
		mReScanHandler.removeCallbacksAndMessages(null);
		super.onStop();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		menu.add(0, PLAY_SELECTION, 0, R.string.play_selection);
		SubMenu sub = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0,
				R.string.add_to_playlist);
		MusicUtils.makePlaylistMenu(this.getActivity(), sub);
		menu.add(0, DELETE_ITEM, 0, R.string.delete_item);

		AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfo;
		mAlbumCursor.moveToPosition(mi.position);
		mCurrentAlbumId = mAlbumCursor.getString(mAlbumCursor
				.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID));
		mCurrentAlbumName = mAlbumCursor.getString(mAlbumCursor
				.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM));
		mCurrentArtistNameForAlbum = mAlbumCursor.getString(mAlbumCursor
				.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST));
		mIsUnknownArtist = mCurrentArtistNameForAlbum == null
				|| mCurrentArtistNameForAlbum.equals(MediaStore.UNKNOWN_STRING);
		mIsUnknownAlbum = mCurrentAlbumName == null
				|| mCurrentAlbumName.equals(MediaStore.UNKNOWN_STRING);
		if (mIsUnknownAlbum) {
			menu.setHeaderTitle(getString(R.string.unknown_album_name));
		} else {
			menu.setHeaderTitle(mCurrentAlbumName);
		}
//		super.onCreateContextMenu(menu, v, menuInfo);

	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case PLAY_SELECTION: {
			// play the selected album
			long[] list = MusicUtils.getSongListForAlbum(this.getActivity(),
					Long.parseLong(mCurrentAlbumId));
			MusicUtils.playAll(this.getActivity(), list, 0);
			return true;
		}

		case QUEUE: {
			long[] list = MusicUtils.getSongListForAlbum(this.getActivity(),
					Long.parseLong(mCurrentAlbumId));
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
			long[] list = MusicUtils.getSongListForAlbum(this.getActivity(),
					Long.parseLong(mCurrentAlbumId));
			long playlist = item.getIntent().getLongExtra("playlist", 0);
			MusicUtils.addToPlaylist(this.getActivity(), list, playlist);
			return true;
		}
		case DELETE_ITEM: {
			Log.i(TAG, "mCurrentAlbumId="+mCurrentAlbumId);
			long[] list = MusicUtils.getSongListForAlbum(this.getActivity(),
					Long.parseLong(mCurrentAlbumId.toString()));
			String f;
//			if (android.os.Environment.isExternalStorageRemovable()) {
				f = getString(R.string.delete_album_desc);
//			} else {
//				f = getString(R.string.delete_album_desc_nosdcard);
//			}
			String desc = String.format(f, mCurrentAlbumName);
			Bundle b = new Bundle();
			b.putString("description", desc);
			b.putLongArray("items", list);
			Intent intent = new Intent();
			intent.setClass(this.getActivity(), DeleteItems.class);
			intent.putExtras(b);
			startActivityForResult(intent, -1);
			return true;
		}

		}
		return super.onContextItemSelected(item);
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View view, int poition, long id) {
		// TODO Auto-generated method stub
		Bundle bundle = new Bundle();
		bundle.putInt(Constants.LAUNCH_TYPE, Constants.TRACK_BROWSER);
		bundle.putString(Constants.ALBUM_EXTRA, Long.valueOf(id).toString());
		bundle.putString(Constants.ARTIST_EXTRA, mArtistId);

		mActivityCommunicator.passDataToActivity(bundle);
	}

	private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (mGridView != null)
				mGridView.invalidateViews();
			MusicUtils.updateNowPlaying(AlbumBrowser.this.getActivity());
		}
	};
	private BroadcastReceiver mScanListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			MusicUtils.setSpinnerState(AlbumBrowser.this.getActivity());
			mReScanHandler.sendEmptyMessage(0);
			if (intent.getAction().equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
				MusicUtils.clearAlbumArtCache();
			}
		}
	};

	private Handler mReScanHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (mAdapter != null) {
				getAlbumCursor(mAdapter.getQueryHandler(), null);
			}
		}
	};

	private Cursor getAlbumCursor(AsyncQueryHandler async, String filter) {
		String[] cols = new String[] { MediaStore.Audio.Albums._ID,
				MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.ALBUM,
				MediaStore.Audio.Albums.ALBUM_ART };

		Cursor ret = null;
		if (mArtistId != null) {
			Uri uri = MediaStore.Audio.Artists.Albums.getContentUri("external",
					Long.valueOf(mArtistId));
			if (!TextUtils.isEmpty(filter)) {
				uri = uri.buildUpon()
						.appendQueryParameter("filter", Uri.encode(filter))
						.build();
			}
			if (async != null) {
				async.startQuery(0, null, uri, cols, null, null,
						MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);
			} else {
				ret = MusicUtils.query(this.getActivity(), uri, cols, null,
						null, MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);
			}
		} else {
			Uri uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
			if (!TextUtils.isEmpty(filter)) {
				uri = uri.buildUpon()
						.appendQueryParameter("filter", Uri.encode(filter))
						.build();
			}
			if (async != null) {
				async.startQuery(0, null, uri, cols, null, null,
						MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);
			} else {
				ret = MusicUtils.query(this.getActivity(), uri, cols, null,
						null, MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);
			}
		}
		return ret;
	}

	public void init(Cursor c) {

		if (mAdapter == null) {
			return;
		}
		mAdapter.changeCursor(c); // also sets mAlbumCursor

		if (mAlbumCursor == null) {
			MusicUtils.displayDatabaseError(this.getActivity());
			getActivity().closeContextMenu();
			mReScanHandler.sendEmptyMessageDelayed(0, 1000);
			return;
		}

		// restore previous position
		if (mLastListPosCourse >= 0) {
			/*lv.setSelectionFromTop(mLastListPosCourse,
					mLastListPosFine);*/
			mLastListPosCourse = -1;
		}

		MusicUtils.hideDatabaseError(this.getActivity());
		// MusicUtils.updateButtonBar(this.getActivity(), R.id.albumtab);
		setTitle();
	}

	private void setTitle() {
		CharSequence fancyName = "";
		if (mAlbumCursor != null && mAlbumCursor.getCount() > 0) {
			mAlbumCursor.moveToFirst();
			fancyName = mAlbumCursor.getString(mAlbumCursor
					.getColumnIndex(MediaStore.Audio.Albums.ARTIST));
			if (fancyName == null
					|| fancyName.equals(MediaStore.UNKNOWN_STRING))
				fancyName = getText(R.string.unknown_artist_name);
		}

		if (mArtistId != null && fancyName != null)
			getActivity().setTitle(fancyName);
		else
			getActivity().setTitle(R.string.albums_title);
	}

	static class AlbumListAdapter extends SimpleCursorAdapter implements
			SectionIndexer {

		private final Drawable mNowPlayingOverlay;
		private final BitmapDrawable mDefaultAlbumIcon;
		private int mAlbumIdx;
		private int mArtistIdx;
		private int mAlbumArtIndex;
		private final Resources mResources;
		private final StringBuilder mStringBuilder = new StringBuilder();
		private final String mUnknownAlbum;
		private final String mUnknownArtist;
		private final String mAlbumSongSeparator;
		private final Object[] mFormatArgs = new Object[1];
		private AlphabetIndexer mIndexer;
		private AlbumBrowser mActivity;
		private AsyncQueryHandler mQueryHandler;
		private String mConstraint = null;
		private boolean mConstraintIsValid = false;

		static class ViewHolder {
			TextView line1;
			TextView line2;
			ImageView play_indicator;
			SquareImageView icon;
		}

		class QueryHandler extends AsyncQueryHandler {
			QueryHandler(ContentResolver res) {
				super(res);
			}

			@Override
			protected void onQueryComplete(int token, Object cookie,
					Cursor cursor) {
				// Log.i("@@@", "query complete");
				mActivity.init(cursor);
			}
		}

		AlbumListAdapter(Context context, AlbumBrowser currentactivity,
				int layout, Cursor cursor, String[] from, int[] to) {
			super(context, layout, cursor, from, to);

			mActivity = currentactivity;
			mQueryHandler = new QueryHandler(context.getContentResolver());

			mUnknownAlbum = context.getString(R.string.unknown_album_name);
			mUnknownArtist = context.getString(R.string.unknown_artist_name);
			mAlbumSongSeparator = context
					.getString(R.string.albumsongseparator);

			Resources r = context.getResources();
			mNowPlayingOverlay = r
					.getDrawable(R.drawable.indicator_ic_mp_playing_list);

			Bitmap b = BitmapFactory.decodeResource(r,
					R.drawable.albumart_mp_unknown_list);
			mDefaultAlbumIcon = new BitmapDrawable(context.getResources(), b);
			// no filter or dither, it's a lot faster and we can't tell the
			// difference
			mDefaultAlbumIcon.setFilterBitmap(false);
			mDefaultAlbumIcon.setDither(false);
			getColumnIndices(cursor);
			mResources = context.getResources();
		}

		private void getColumnIndices(Cursor cursor) {
			if (cursor != null) {
				mAlbumIdx = cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM);
				mArtistIdx = cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST);
				mAlbumArtIndex = cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART);

				if (mIndexer != null) {
					mIndexer.setCursor(cursor);
				} else {
					 mIndexer = new MusicAlphabetIndexer(cursor, mAlbumIdx,
					 mResources.getString(
					 R.string.fast_scroll_alphabet));
				}
			}
		}

		public void setActivity(AlbumBrowser newactivity) {
			mActivity = newactivity;
		}

		public AsyncQueryHandler getQueryHandler() {
			return mQueryHandler;
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			View v = super.newView(context, cursor, parent);
			ViewHolder vh = new ViewHolder();
			vh.line1 = (TextView) v.findViewById(R.id.line1);
			vh.line2 = (TextView) v.findViewById(R.id.line2);
			vh.play_indicator = (ImageView) v.findViewById(R.id.play_indicator);
			vh.icon = (SquareImageView) v.findViewById(R.id.icon);
			vh.icon.setBackgroundDrawable(mDefaultAlbumIcon);
			vh.icon.setPadding(0, 0, 1, 0);
			v.setTag(vh);
			return v;
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {

			ViewHolder vh = (ViewHolder) view.getTag();

			String name = cursor.getString(mAlbumIdx);
			String displayname = name;
			boolean unknown = name == null
					|| name.equals(MediaStore.UNKNOWN_STRING);
			if (unknown) {
				displayname = mUnknownAlbum;
			}
			vh.line1.setText(displayname);

			name = cursor.getString(mArtistIdx);
			displayname = name;
			if (name == null || name.equals(MediaStore.UNKNOWN_STRING)) {
				displayname = mUnknownArtist;
			}
			vh.line2.setText(displayname);

			SquareImageView iv = vh.icon;
			// We don't actually need the path to the thumbnail file,
			// we just use it to see if there is album art or not
			String art = cursor.getString(mAlbumArtIndex);
			long aid = cursor.getLong(0);
			if (unknown || art == null || art.length() == 0) {
				iv.setImageDrawable(null);
			} else {
				Drawable d = MusicUtils.getCachedArtwork(context, aid,
						mDefaultAlbumIcon);
				iv.setImageDrawable(d);
			}
            ImageView iv1;
			long currentalbumid = MusicUtils.getCurrentAlbumId();
			iv1 = vh.play_indicator;
			if (currentalbumid == aid) {
				iv1.setImageDrawable(mNowPlayingOverlay);
			} else {
				iv1.setImageDrawable(null);
			}
		}

		@Override
		public void changeCursor(Cursor cursor) {
			if (mActivity.getActivity().isFinishing() && cursor != null) {
				cursor.close();
				cursor = null;
			}

			// if (cursor == null || cursor.getCount() == 0) {
			// Log.i(TAG, "List is Empty");
			// noMedia.setVisibility(View.VISIBLE);
			// return;
			// } else {
			// noMedia.setVisibility(View.GONE);
			//
			// }
			if (cursor != mActivity.mAlbumCursor) {
				mActivity.mAlbumCursor = cursor;
				getColumnIndices(cursor);
				super.changeCursor(cursor);
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
			Cursor c = mActivity.getAlbumCursor(null, s);
			mConstraint = s;
			mConstraintIsValid = true;
			return c;
		}

		public Object[] getSections() {
			return mIndexer.getSections();
		}

		public int getPositionForSection(int section) {
			return mIndexer.getPositionForSection(section);
		}

		public int getSectionForPosition(int position) {
			return 0;
		}
	}

	@Override
	public void onDestroyView() {
		if (mGridView != null) {
			mLastListPosCourse = mGridView.getFirstVisiblePosition();
			View cv = mGridView.getChildAt(0);
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
		mGridView.setAdapter(null);
		mAdapter = null;
		getActivity().unregisterReceiver(mScanListener);
		super.onDestroyView();
	}

}