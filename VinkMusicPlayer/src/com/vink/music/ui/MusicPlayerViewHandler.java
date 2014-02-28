package com.vink.music.ui;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;

import com.vink.music.R;
import com.vink.music.support.LibraryFragmentListAdapter;
import com.vink.music.util.Constants;

public class MusicPlayerViewHandler extends FragmentActivity implements Constants {

	private LibraryFragmentListAdapter mAdapter;
	private ViewPager mPager;

	private final static int TAB_COUNT = 1;
	private static final String TAG = "MainLibrary";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.player_main_view);
		mAdapter = new LibraryFragmentListAdapter(getSupportFragmentManager());
		mAdapter.setCount(TAB_COUNT);
		//
		mAdapter.setContext(this);
		mPager = (ViewPager) findViewById(R.id.pager);
		mPager.setAdapter(mAdapter);
	}

}