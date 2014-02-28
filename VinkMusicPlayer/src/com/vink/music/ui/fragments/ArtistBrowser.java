package com.vink.music.ui.fragments;

import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ImageView;
import android.widget.SimpleCursorTreeAdapter;
import android.widget.TextView;

import com.vink.music.R;
import com.vink.music.interfaces.NotifyMainLibrary;
import com.vink.music.support.ExpandableListFragment;
import com.vink.music.util.Constants;
import com.vink.music.util.MusicAlphabetIndexer;
import com.vink.music.util.MusicUtils;

public class ArtistBrowser extends ExpandableListFragment implements
		MusicUtils.Defs, OnChildClickListener {

	private String mCurrentArtistId;
	private String mCurrentArtistName;
	private String mCurrentAlbumId;
	private String mCurrentAlbumName;
	private String mCurrentArtistNameForAlbum;
	boolean mIsUnknownArtist;
	boolean mIsUnknownAlbum;
	private ArtistAlbumListAdapter mAdapter;
	private boolean mAdapterSent;
	private final static int SEARCH = CHILD_MENU_BASE;
	private static final String TAG = "ArtistBrowser";
	private static int mLastListPosCourse = -1;
	private static int mLastListPosFine = -1;
	private NotifyMainLibrary mActivityCommunicator;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.media_picker_activity_expanding,
				container, false);
		return v;
	}

	private void setEmptyView() {
		TextView noMedia = null;
		noMedia = (TextView) getView().findViewById(R.id.error_message);
		getExpandableListView().setEmptyView(noMedia);
	}

	@Override
	public void onActivityCreated(Bundle icicle) {
		super.onActivityCreated(icicle);

		setEmptyView();
		mActivityCommunicator = (NotifyMainLibrary) getActivity();

		getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
		if (icicle != null) {
			mCurrentAlbumId = icicle.getString("selectedalbum");
			mCurrentAlbumName = icicle.getString("selectedalbumname");
			mCurrentArtistId = icicle.getString("selectedartist");
			mCurrentArtistName = icicle.getString("selectedartistname");
		}
		// mToken = MusicUtils.bindToService(this.getActivity(), this);
		MusicUtils.updateNowPlaying(this.getActivity());

		IntentFilter f = new IntentFilter();
		f.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
		f.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
		f.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
		f.addDataScheme("file");
		getActivity().registerReceiver(mScanListener, f);

		// MusicUtils.updateButtonBar(this.getActivity(), R.id.artisttab);
		ExpandableListView expListView = getExpandableListView();
		expListView.setOnCreateContextMenuListener(this);
		expListView.setTextFilterEnabled(true);
		expListView.setOnChildClickListener(this);

		mAdapter = (ArtistAlbumListAdapter) getActivity()
				.getLastNonConfigurationInstance();
		if (mAdapter == null) {
			// Log.i("@@@", "starting query");
			mAdapter = new ArtistAlbumListAdapter(getActivity()
					.getApplication(),
					this,
					null, // cursor
					R.layout.track_list_item_group, new String[] {},
					new int[] {}, R.layout.track_list_item_child,
					new String[] {}, new int[] {});
			setListAdapter(mAdapter);
			getActivity().setTitle(R.string.working_artists);
			getArtistCursor(mAdapter.getQueryHandler(), null);
		} else {
			mAdapter.setActivity(this);
			setListAdapter(mAdapter);
			mArtistCursor = mAdapter.getCursor();
			if (mArtistCursor != null) {
				init(mArtistCursor);
			} else {
				getArtistCursor(mAdapter.getQueryHandler(), null);
			}
		}
	}

	private Cursor mArtistCursor;

	private Handler mReScanHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (mAdapter != null) {
				getArtistCursor(mAdapter.getQueryHandler(), null);
			}
		}
	};

	private BroadcastReceiver mScanListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			MusicUtils.setSpinnerState(ArtistBrowser.this.getActivity());
			mReScanHandler.sendEmptyMessage(0);
			if (intent.getAction().equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
				MusicUtils.clearAlbumArtCache();
			}
		}
	};

	public void init(Cursor c) {

		if (mAdapter == null) {
			return;
		}
		mAdapter.changeCursor(c); // also sets mArtistCursor

		if (mArtistCursor == null) {
			MusicUtils.displayDatabaseError(this.getActivity());
			getActivity().closeContextMenu();
			mReScanHandler.sendEmptyMessageDelayed(0, 1000);
			return;
		}

		// restore previous position
		// if (mLastListPosCourse >= 0) {
		// ExpandableListView elv = getExpandableListView();
		// elv.setSelectionFromTop(mLastListPosCourse, mLastListPosFine);
		// mLastListPosCourse = -1;
		// }

		MusicUtils.hideDatabaseError(this.getActivity());
		// MusicUtils.updateButtonBar(this.getActivity(), R.id.artisttab);
		setTitle();
	}

	private void setTitle() {
		getActivity().setTitle(R.string.artists_title);
	}

	private Cursor getArtistCursor(AsyncQueryHandler async, String filter) {

		String[] cols = new String[] { MediaStore.Audio.Artists._ID,
				MediaStore.Audio.Artists.ARTIST,
				MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
				MediaStore.Audio.Artists.NUMBER_OF_TRACKS };

		Uri uri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
		if (!TextUtils.isEmpty(filter)) {
			uri = uri.buildUpon()
					.appendQueryParameter("filter", Uri.encode(filter)).build();
		}

		Cursor ret = null;
		if (async != null) {
			async.startQuery(0, null, uri, cols, null, null,
					MediaStore.Audio.Artists.ARTIST_KEY);
		} else {
			ret = MusicUtils.query(this.getActivity(), uri, cols, null, null,
					MediaStore.Audio.Artists.ARTIST_KEY);
		}
		return ret;
	}

	static class ArtistAlbumListAdapter extends SimpleCursorTreeAdapter
	/* implements SectionIndexer */{

		private final Drawable mNowPlayingOverlay;
		private final BitmapDrawable mDefaultAlbumIcon;
		private int mGroupArtistIdIdx;
		private int mGroupArtistIdx;
		private int mGroupAlbumIdx;
		private int mGroupSongIdx;
		private final Context mContext;
		private final Resources mResources;
		private final String mAlbumSongSeparator;
		private final String mUnknownAlbum;
		private final String mUnknownArtist;
		private final StringBuilder mBuffer = new StringBuilder();
		private final Object[] mFormatArgs = new Object[1];
		private final Object[] mFormatArgs3 = new Object[3];
		private MusicAlphabetIndexer mIndexer;
		private ArtistBrowser mActivity;
		private AsyncQueryHandler mQueryHandler;
		private String mConstraint = null;
		private boolean mConstraintIsValid = false;

		static class ViewHolder {
			TextView line1;
			TextView line2;
			ImageView play_indicator;
			ImageView icon;
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

		ArtistAlbumListAdapter(Context context, ArtistBrowser currentactivity,
				Cursor cursor, int glayout, String[] gfrom, int[] gto,
				int clayout, String[] cfrom, int[] cto) {
			super(context, cursor, glayout, gfrom, gto, clayout, cfrom, cto);

			mActivity = currentactivity;
			mQueryHandler = new QueryHandler(context.getContentResolver());

			Resources r = context.getResources();
			mNowPlayingOverlay = r
					.getDrawable(R.drawable.indicator_ic_mp_playing_list);
			mDefaultAlbumIcon = (BitmapDrawable) r
					.getDrawable(R.drawable.albumart_mp_unknown_list);
			// no filter or dither, it's a lot faster and we can't tell the
			// difference
			mDefaultAlbumIcon.setFilterBitmap(false);
			mDefaultAlbumIcon.setDither(false);

			mContext = context;
			getColumnIndices(cursor);
			mResources = context.getResources();
			mAlbumSongSeparator = context
					.getString(R.string.albumsongseparator);
			mUnknownAlbum = context.getString(R.string.unknown_album_name);
			mUnknownArtist = context.getString(R.string.unknown_artist_name);
		}

		private void getColumnIndices(Cursor cursor) {
			if (cursor != null) {
				mGroupArtistIdIdx = cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID);
				mGroupArtistIdx = cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST);
				mGroupAlbumIdx = cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS);
				mGroupSongIdx = cursor
						.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS);
				if (mIndexer != null) {
					mIndexer.setCursor(cursor);
				} else {
					mIndexer = new MusicAlphabetIndexer(cursor,
							mGroupArtistIdx,
							mResources.getString(R.string.fast_scroll_alphabet));
				}
			}
		}

		public void setActivity(ArtistBrowser newactivity) {
			mActivity = newactivity;
		}

		public AsyncQueryHandler getQueryHandler() {
			return mQueryHandler;
		}

		@Override
		public View newGroupView(Context context, Cursor cursor,
				boolean isExpanded, ViewGroup parent) {
			View v = super.newGroupView(context, cursor, isExpanded, parent);
			ImageView iv = (ImageView) v.findViewById(R.id.icon);
			ViewGroup.LayoutParams p = iv.getLayoutParams();
			p.width = ViewGroup.LayoutParams.WRAP_CONTENT;
			p.height = ViewGroup.LayoutParams.WRAP_CONTENT;
			ViewHolder vh = new ViewHolder();
			vh.line1 = (TextView) v.findViewById(R.id.line1);
			vh.line2 = (TextView) v.findViewById(R.id.line2);
			vh.play_indicator = (ImageView) v.findViewById(R.id.play_indicator);
			vh.icon = (ImageView) v.findViewById(R.id.icon);
			vh.icon.setPadding(0, 0, 1, 0);
			v.setTag(vh);
			return v;
		}

		//
		@Override
		public View newChildView(Context context, Cursor cursor,
				boolean isLastChild, ViewGroup parent) {
			View v = super.newChildView(context, cursor, isLastChild, parent);
			ViewHolder vh = new ViewHolder();
			vh.line1 = (TextView) v.findViewById(R.id.line1);
			vh.line2 = (TextView) v.findViewById(R.id.line2);
			vh.play_indicator = (ImageView) v.findViewById(R.id.play_indicator);
			vh.icon = (ImageView) v.findViewById(R.id.icon);
			vh.icon.setBackgroundDrawable(mDefaultAlbumIcon);
			vh.icon.setPadding(0, 0, 1, 0);
			v.setTag(vh);
			return v;
		}

		@Override
		public void bindGroupView(View view, Context context, Cursor cursor,
				boolean isexpanded) {

			// public void bindView(View view, Context context, Cursor cursor){
			ViewHolder vh = (ViewHolder) view.getTag();

			String artist = cursor.getString(mGroupArtistIdx);
			String displayartist = artist;
			boolean unknown = artist == null
					|| artist.equals(MediaStore.UNKNOWN_STRING);
			if (unknown) {
				displayartist = mUnknownArtist;
			}
			vh.line1.setText(displayartist);

			int numalbums = cursor.getInt(mGroupAlbumIdx);
			int numsongs = cursor.getInt(mGroupSongIdx);

			String songs_albums = MusicUtils.makeAlbumsLabel(context,
					numalbums, numsongs, unknown);

			vh.line2.setText(songs_albums);

			long currentartistid = MusicUtils.getCurrentArtistId();
			long artistid = cursor.getLong(mGroupArtistIdIdx);
			if (currentartistid == artistid /* && !isexpanded */) {
				vh.play_indicator.setImageDrawable(mNowPlayingOverlay);
			} else {
				vh.play_indicator.setImageDrawable(null);
			}
		}

		@Override
		public void bindChildView(View view, Context context, Cursor cursor,
				boolean islast) {

			ViewHolder vh = (ViewHolder) view.getTag();

			String name = cursor.getString(cursor
					.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM));
			String displayname = name;
			boolean unknown = name == null
					|| name.equals(MediaStore.UNKNOWN_STRING);
			if (unknown) {
				displayname = mUnknownAlbum;
			}
			vh.line1.setText(displayname);

			int numsongs = cursor
					.getInt(cursor
							.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS));
			int numartistsongs = cursor
					.getInt(cursor
							.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS_FOR_ARTIST));

			final StringBuilder builder = mBuffer;
			builder.delete(0, builder.length());
			if (unknown) {
				numsongs = numartistsongs;
			}

			if (numsongs == 1) {
				builder.append(context.getString(R.string.onesong));
			} else {
				if (numsongs == numartistsongs) {
					final Object[] args = mFormatArgs;
					args[0] = numsongs;
					builder.append(mResources.getQuantityString(
							R.plurals.Nsongs, numsongs, args));
				} else {
					final Object[] args = mFormatArgs3;
					args[0] = numsongs;
					args[1] = numartistsongs;
					args[2] = cursor
							.getString(cursor
									.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST));
					builder.append(mResources.getQuantityString(
							R.plurals.Nsongscomp, numsongs, args));
				}
			}
			vh.line2.setText(builder.toString());

			ImageView iv = vh.icon;
			// We don't actually need the path to the thumbnail file,
			// we just use it to see if there is album art or not
			String art = cursor.getString(cursor
					.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART));
			if (unknown || art == null || art.length() == 0) {
				iv.setBackgroundDrawable(mDefaultAlbumIcon);
				iv.setImageDrawable(null);
			} else {
				long artIndex = cursor.getLong(0);
				Drawable d = MusicUtils.getCachedArtwork(context, artIndex,
						mDefaultAlbumIcon);
				iv.setImageDrawable(d);
			}

			long currentalbumid = MusicUtils.getCurrentAlbumId();
			long aid = cursor.getLong(0);
			iv = vh.play_indicator;
			if (currentalbumid == aid) {
				iv.setImageDrawable(mNowPlayingOverlay);
			} else {
				iv.setImageDrawable(null);
			}
		}

		@Override
		public void changeCursor(Cursor cursor) {
			if (mActivity.getActivity().isFinishing() && cursor != null) {
				cursor.close();
				cursor = null;
			}
			if (cursor != mActivity.mArtistCursor) {
				mActivity.mArtistCursor = cursor;
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
			Cursor c = mActivity.getArtistCursor(null, s);
			mConstraint = s;
			mConstraintIsValid = true;
			return c;
		}

		public Object[] getSections() {
			return mIndexer.getSections();
		}

		public int getPositionForSection(int sectionIndex) {
			return mIndexer.getPositionForSection(sectionIndex);
		}

		public int getSectionForPosition(int position) {
			return 0;
		}

		@Override
		protected Cursor getChildrenCursor(Cursor groupCursor) {

			long id = groupCursor.getLong(groupCursor
					.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID));

			String[] cols = new String[] { MediaStore.Audio.Albums._ID,
					MediaStore.Audio.Albums.ALBUM,
					MediaStore.Audio.Albums.NUMBER_OF_SONGS,
					MediaStore.Audio.Albums.NUMBER_OF_SONGS_FOR_ARTIST,
					MediaStore.Audio.Albums.ALBUM_ART };
			Cursor c = MusicUtils.query(mActivity.getActivity(),
					MediaStore.Audio.Artists.Albums.getContentUri("external",
							id), cols, null, null,
					MediaStore.Audio.Albums.DEFAULT_SORT_ORDER);

			class MyCursorWrapper extends CursorWrapper {
				String mArtistName;
				int mMagicColumnIdx;

				MyCursorWrapper(Cursor c, String artist) {
					super(c);
					mArtistName = artist;
					if (mArtistName == null
							|| mArtistName.equals(MediaStore.UNKNOWN_STRING)) {
						mArtistName = mUnknownArtist;
					}
					mMagicColumnIdx = c.getColumnCount();
				}

				@Override
				public String getString(int columnIndex) {
					if (columnIndex != mMagicColumnIdx) {
						return super.getString(columnIndex);
					}
					return mArtistName;
				}

				@Override
				public int getColumnIndexOrThrow(String name) {
					if (MediaStore.Audio.Albums.ARTIST.equals(name)) {
						return mMagicColumnIdx;
					}
					return super.getColumnIndexOrThrow(name);
				}

				@Override
				public String getColumnName(int idx) {
					if (idx != mMagicColumnIdx) {
						return super.getColumnName(idx);
					}
					return MediaStore.Audio.Albums.ARTIST;
				}

				@Override
				public int getColumnCount() {
					return super.getColumnCount() + 1;
				}
			}
			return new MyCursorWrapper(c,
					groupCursor.getString(mGroupArtistIdx));

		}
	}

	@Override
	public boolean onChildClick(ExpandableListView parent, View v,
			int groupPosition, int childPosition, long id) {

		mCurrentAlbumId = Long.valueOf(id).toString();

		Bundle bundle = new Bundle();
		bundle.putInt(Constants.LAUNCH_TYPE, Constants.TRACK_BROWSER);
		bundle.putString(Constants.ALBUM_EXTRA, mCurrentAlbumId);
		mArtistCursor.moveToPosition(groupPosition);
		mCurrentArtistId = mArtistCursor.getString(mArtistCursor
				.getColumnIndex(MediaStore.Audio.Artists._ID));
		bundle.putString(Constants.ARTIST_EXTRA, mCurrentArtistId);

		mActivityCommunicator.passDataToActivity(bundle);

		return true;
	}

	@Override
	public void onDestroyView() {
		ExpandableListView lv = getExpandableListView();
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
		setListAdapter(null);
		super.onDestroyView();
	}

}
