/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vink.music.ui;

import com.vink.music.R;
import com.vink.music.util.Constants;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;

public class Settings extends PreferenceActivity implements
		OnSharedPreferenceChangeListener {
	private static final String TAG = "Settings";
	public static String mTheme = "0";
	private String theme = "0";
	private PreferenceScreen mPref;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings);
		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);
		 mPref = getPreferenceScreen();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences arg0, String key) {

		if ((Constants.THEME_TYPE).equals(key)) {
			theme = arg0.getString(Constants.THEME_TYPE, "0");
			mPref.findPreference(Constants.THEME_TYPE).setSummary(theme);
		}
	}

	@Override
	public void onBackPressed() {
		if (mTheme.equals(theme)) {
			setResult(RESULT_CANCELED);
		} else {
			mTheme = theme;
			Intent newIntent = new Intent();
			newIntent.putExtra(Constants.THEME_TYPE, mTheme);
			setResult(Constants.THEME_CHANGED, newIntent);
		}
		super.onBackPressed();
	}
}
