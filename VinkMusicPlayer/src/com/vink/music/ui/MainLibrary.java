package com.vink.music.ui;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Switch;

import com.vink.music.R;
import com.vink.music.interfaces.NotifyMainLibrary;
import com.vink.music.interfaces.MainActivityActions;
import com.vink.music.support.LibraryFragmentListAdapter;
import com.vink.music.support.PlaybackControlAdapter;
import com.vink.music.ui.fragments.TrackBrowser;
import com.vink.music.util.Constants;
import com.vink.music.util.MusicUtils;
import com.vink.music.util.MusicUtils.ServiceToken;

public class MainLibrary extends FragmentActivity implements NotifyMainLibrary,
		Constants, ServiceConnection {

	private LibraryFragmentListAdapter mLibraryAdapter;
	private ViewPager mPager;

	private PlaybackControlAdapter mPlaybackControlAdapter;
	private ViewPager mPlaybackControlPager;

	private FragmentManager mFragmentManager;
	private FragmentTransaction mFragmentTransaction;
	private TrackBrowser mTrackbrowserFragment;
	private ServiceToken mToken;

	private MainActivityActions mListener;

	private SharedPreferences sharedPref;
	private int mPrevPageNum;
	private String mTheme = "0";
	private int mPrevControllerPage = 0;
	private ProgressDialog mProgressDialog;

	private final static int TAB_COUNT = 4;
	private final static int PLAYBACK_CONTROL_PAGES = 3;
	private static final String TAG = "MainLibrary";

	private final static String CUR_LIB_PAGE = "current_page";
	private static final String MUSIC_PLAYER_STATUS = "Music_player_status";
	private static final int SETTINGS = 0;
	private static final String CUR_CONTROL_PAGE = "current_controller_page_number";
	private static final int DEFAULT_PAGE = 0;
	
	private String[] mNavigationDrawerListItems;
	private DrawerLayout mDrawerLayout;
	private ListView mDrawerListView;
	private ActionBarDrawerToggle mDrawerToggle;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Figure out alternative for this
		onThemeChanged(Integer.valueOf(Settings.mTheme));
		setContentView(R.layout.main_library);
		showProgressDialog(getString(R.string.loading));

		Thread thread = new Thread() {
			public void run() {
				getPrevState();
				mToken = MusicUtils.bindToService(MainLibrary.this,
						MainLibrary.this);
			};
		};
		thread.start();

	}

	private void showProgressDialog(String msg) {
		if (mProgressDialog == null) {
			mProgressDialog = new ProgressDialog(this);
		}
		mProgressDialog.setMessage(msg);
		mProgressDialog.setInverseBackgroundForced(true);
		mProgressDialog.show();
	}

	private void getPrevState() {
		mPrevPageNum = getPrevPageNum(CUR_LIB_PAGE);
		mPrevControllerPage = getPrevPageNum(CUR_CONTROL_PAGE);

	}

	private void onThemeChanged(int theme) {
		switch (theme) {
		case BLUE:
			setTheme(R.style.blue);
			break;
		case RED:
			setTheme(R.style.red);
			break;
		case YELLOW:
			setTheme(R.style.yellow);
			break;

		default:
			setTheme(R.style.blue);
			break;
		}

	}

	private void setPlaybackControlPage() {
		mPlaybackControlAdapter = new PlaybackControlAdapter(
				getSupportFragmentManager());
		mPlaybackControlAdapter.setCount(PLAYBACK_CONTROL_PAGES);
		mPlaybackControlAdapter.setContext(this);
		mPlaybackControlPager = (ViewPager) findViewById(R.id.playback_control_pager);
		mPlaybackControlPager.setAdapter(mPlaybackControlAdapter);
		mPlaybackControlPager.setOffscreenPageLimit(PLAYBACK_CONTROL_PAGES - 1);
		mPlaybackControlPager.setCurrentItem(mPrevControllerPage, true);
	}

	private int getPrevPageNum(String pageType) {
		if (sharedPref == null) {
			sharedPref = getApplication().getSharedPreferences(
					MUSIC_PLAYER_STATUS, MODE_PRIVATE);
		}
		return sharedPref.getInt(pageType, DEFAULT_PAGE);
	}

	private void savePlayerDetails() {
		if (sharedPref == null) {
			sharedPref = getApplication().getSharedPreferences(
					MUSIC_PLAYER_STATUS, MODE_PRIVATE);
		}
		if (mPager == null) {
			return;
		}
		Editor sharedPrefEditor = sharedPref.edit();
		sharedPrefEditor.putInt(CUR_LIB_PAGE, mPager.getCurrentItem());
		sharedPrefEditor.putInt(CUR_CONTROL_PAGE,
				mPlaybackControlPager.getCurrentItem());
		sharedPrefEditor.commit();
	}

	private void setMusicLibraryPage() {
		mLibraryAdapter = new LibraryFragmentListAdapter(
				getSupportFragmentManager());
		mLibraryAdapter.setCount(TAB_COUNT);
		mLibraryAdapter.setContext(this);
		mPager = (ViewPager) findViewById(R.id.pager);
		mPager.setAdapter(mLibraryAdapter);
		mPager.setOffscreenPageLimit(TAB_COUNT - 1);
		mPager.setCurrentItem(mPrevPageNum, true);
	}

	public Uri getIntentDataUri() {
		return getIntent().getData();
	}

	@Override
	public void passDataToActivity(Bundle bundle) {
		int type = bundle.getInt(LAUNCH_TYPE, TRACK_BROWSER);
		launchChildLibrary(type, bundle);
	}

	private void launchChildLibrary(int type, Bundle bundle) {
		switch (type) {
		case TRACK_BROWSER:
			editFragmentStack(bundle);
			break;

		default:
			break;
		}

	}

	private void editFragmentStack(Bundle bundle) {
		if (mFragmentManager == null) {
			mFragmentManager = getSupportFragmentManager();
		}
		mFragmentTransaction = mFragmentManager.beginTransaction();

		if (bundle != null) {
			if (mTrackbrowserFragment.isAdded()) {
				// Work around for replacing already added fragment
				mFragmentTransaction.remove(mTrackbrowserFragment);
				mFragmentTransaction.commit();
				mTrackbrowserFragment = new TrackBrowser();
				mFragmentTransaction = mFragmentManager.beginTransaction();
			}
			mTrackbrowserFragment.setArguments(bundle);
			mFragmentTransaction.add(R.id.list_fragment, mTrackbrowserFragment);
			mFragmentTransaction.commit();

			mPager.setVisibility(View.GONE);
			return;
		}

		// when back key is hit
		mFragmentTransaction.remove(mTrackbrowserFragment);
		mFragmentTransaction.commit();

	}

	@Override
	public void onBackPressed() {
		if (mPager != null && mPager.getVisibility() == View.GONE) {
			editFragmentStack(null);
			mPager.setVisibility(View.VISIBLE);

		} else {
			super.onBackPressed();
		}
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {

		if (MusicUtils.sService == null) {
			return;// TODO: show some message
		}
		initViewPager();
		initNavigationDrawer();
		
		mTrackbrowserFragment = new TrackBrowser();
	}

	private void initViewPager() {
		setPlaybackControlPage();
		setMusicLibraryPage();
		mProgressDialog.dismiss();
	}
	
	private void initNavigationDrawer(){
		mNavigationDrawerListItems = getResources().getStringArray(R.array.navigation_drawer_list);
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
		mDrawerListView = (ListView) findViewById(R.id.left_drawer);
		mDrawerListView.setAdapter(new NavigationDrawerListAdapter(this, R.layout.navigation_drawer_list_item_layout, mNavigationDrawerListItems));
		mDrawerListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View view, int position,
					long arg3) {
				// TODO Auto-generated method stub
				String tag = (String) view.getTag();
				if(tag.equals("Settings")){
					Intent intent = new Intent(MainLibrary.this, Settings.class);
					startActivityForResult(intent, THEME_CHANGED);
				}
				mDrawerLayout.closeDrawers();
			}
		});
		
		mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                R.drawable.ic_drawer, R.string.player_name, R.string.player_name) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                getActionBar().setTitle(R.string.player_name);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                getActionBar().setTitle(R.string.player_name);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);
	}
	

	@Override
	public void onServiceDisconnected(ComponentName name) {
		// TODO show some message
		finish();

	}

	@Override
	protected void onDestroy() {
		savePlayerDetails();
		MusicUtils.unbindFromService(mToken);
		super.onDestroy();
	}

	@Override
	public void updateTrackInfo() {
		if (mListener == null) {
			return;
		}
		mListener.updateTrackInfo();

	}

	@Override
	public void setMainActivityActionsListener(MainActivityActions listener) {
		mListener = listener;
	}

	@Override
	public void isPlaying(boolean isPlaying) {
		if (mListener == null) {
			return;
		}
		mListener.isPlaying(isPlaying);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_library, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		if (mDrawerToggle.onOptionsItemSelected(item)) {
	          return true;
	        }
		switch (item.getItemId()) {
		case R.id.menu_settings:
			Intent intent = new Intent(this, Settings.class);
			startActivityForResult(intent, THEME_CHANGED);
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void setMusicTheme(int theme) {

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent arg2) {
		super.onActivityResult(requestCode, resultCode, arg2);
		if (requestCode == THEME_CHANGED && resultCode == THEME_CHANGED) {
			// restart activity to get theme change working
			finish();
			startActivity(new Intent(this, MainLibrary.class));
		}
	}

	private class MusicAsyncHandler extends AsyncTask<Object, Object, Object> {

		@Override
		protected Object doInBackground(Object... arg0) {
			return null;
		}

	}

}