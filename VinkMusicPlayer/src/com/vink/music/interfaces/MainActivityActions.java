package com.vink.music.interfaces;

import android.content.Intent;

public interface MainActivityActions {

	void passDataToFragment(Intent bundle);

	void toggleShuffle();

	void cycleRepeat();
	
	void updateTrackInfo();

	void isPlaying(boolean isPlaying);
	
}
