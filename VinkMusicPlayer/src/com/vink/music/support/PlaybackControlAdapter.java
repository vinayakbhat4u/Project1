package com.vink.music.support;

import android.content.Context;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import com.vink.music.R;
import com.vink.music.ui.fragments.AlbumArtView;
import com.vink.music.ui.fragments.BasicControlView;
import com.vink.music.ui.fragments.ExtraControlView;
import com.vink.music.util.Constants;
import com.vink.music.util.MusicUtils;

public class PlaybackControlAdapter extends FragmentPagerAdapter implements
		Constants {

	private int mCount = 0;
	private Context mContext = null;

	public PlaybackControlAdapter(FragmentManager fm) {
		super(fm);
	}

	@Override
	public Fragment getItem(int position) {
		switch (position) {
		case BASIC_CONTROL_VIEW:
			return new BasicControlView();

		case EXTRA_CONTROL_VIEW:
			return new ExtraControlView();

		case ALBUM_ART_VIEW:
			return new AlbumArtView();

		default:
			return null;
		}
	}

	@Override
	public int getCount() {
		return mCount;
	}

	public void setCount(int count) {
		mCount = count;
	}

	@Override
	public CharSequence getPageTitle(int position) {
		try {
			return setPageTitle(position);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private String setPageTitle(int position) throws RemoteException {

		if (mContext == null) {
			return null;
		}
		switch (position) {
		case BASIC_CONTROL_VIEW:
			return mContext.getString(R.string.basic_control_title);

		case EXTRA_CONTROL_VIEW:
			return mContext.getString(R.string.extra_control_title);

		case ALBUM_ART_VIEW:
			return mContext.getString(R.string.nowplaying_title);

		default:
			return null;
		}
	}

	public void setContext(Context context) {
		mContext = context;

	}
}
