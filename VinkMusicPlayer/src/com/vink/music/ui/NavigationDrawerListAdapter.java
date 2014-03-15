package com.vink.music.ui;

import com.vink.music.R;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class NavigationDrawerListAdapter extends ArrayAdapter<String> {

	private String[] mNavigationDrawerListItems;
	private Context mContext;
	private int mLayoutResourceId;

	public NavigationDrawerListAdapter(Context context, int resource,
			String[] objects) {
		super(context, resource, objects);
		// TODO Auto-generated constructor stub
		mContext = context;
		mLayoutResourceId = resource;
		mNavigationDrawerListItems = objects;

	}

	@Override
	public int getCount() {
		// TODO Auto-generated method stub
		return mNavigationDrawerListItems.length;
	}

	@Override
	public String getItem(int position) {
		// TODO Auto-generated method stub
		return mNavigationDrawerListItems[position];
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		// TODO Auto-generated method stub
		if (convertView == null) {
			convertView = ((Activity) mContext).getLayoutInflater().inflate(
					mLayoutResourceId, parent, false);
			convertView.setTag(mNavigationDrawerListItems[position]);
		}

		TextView text = (TextView) convertView
				.findViewById(R.id.drawer_list_text);
		text.setText(mNavigationDrawerListItems[position]);

		return convertView;
	}

}
