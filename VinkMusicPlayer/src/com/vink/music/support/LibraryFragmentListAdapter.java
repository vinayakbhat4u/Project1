package com.vink.music.support;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import com.vink.music.R;
import com.vink.music.ui.fragments.AlbumBrowser;
import com.vink.music.ui.fragments.ArtistBrowser;
import com.vink.music.ui.fragments.PlaylistBrowser;
import com.vink.music.ui.fragments.TrackBrowser;
import com.vink.music.util.Constants;

public class LibraryFragmentListAdapter extends FragmentPagerAdapter implements
		Constants {

	private int mCount = 0;
	private Bundle mBundle = null;
	private Context mContext = null;

	public LibraryFragmentListAdapter(FragmentManager fm) {
		super(fm);
	}

	@Override
	public int getCount() {
		return mCount;
	}

	public void setCount(int count) {
		mCount = count;
	}

	private void setArguments(Bundle bundle) {
		mBundle = bundle;
	}

	@Override
	public Fragment getItem(int position) {
		switch (position) {
		case ALBUM_BROWSER:
			return new AlbumBrowser();
			
		case TRACK_BROWSER:
			return new TrackBrowser();
			
		case ARTIST_BROWSER:
			return new ArtistBrowser();

		case PLAYLIST_BROWSER:
			return new PlaylistBrowser();

		default:
			return null;
		}
	}

	@Override
	public CharSequence getPageTitle(int position) {
		return setPageTitle(position);
	}
	
	private String setPageTitle(int position){
		
		if(mContext == null){
			return null;
		}
		switch (position) {
		case ALBUM_BROWSER:
			return mContext.getString(R.string.albums_title);
			
		case TRACK_BROWSER:
			return mContext.getString(R.string.tracks_title);
			
		case ARTIST_BROWSER:
			return mContext.getString(R.string.artists_title);

		case PLAYLIST_BROWSER:
			return mContext.getString(R.string.playlists_title);

		default:
			return null;
		}
	}

	public void setContext(Context context) {
		 mContext = context;
		
	}
}