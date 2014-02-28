package com.vink.music.ui.fragments;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.vink.music.R;


public class MusicSettings extends PreferenceFragment{
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings);
	}

}
