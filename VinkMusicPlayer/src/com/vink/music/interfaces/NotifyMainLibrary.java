package com.vink.music.interfaces;

import android.os.Bundle;

public interface NotifyMainLibrary {

	void passDataToActivity(Bundle bundle);

	void updateTrackInfo();

	void setMainActivityActionsListener(MainActivityActions albumArtView);

	void isPlaying(boolean isPlaying);
	
	void setMusicTheme(int theme);

}