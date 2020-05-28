package com.android.systemui.statusbar.auto;

import android.content.Context;
import android.content.Intent;

public class TtsHelper {
	public static void playTTS(Context context, String text) {
		Intent intent = new Intent("aios.intent.action.SPEAK");
		intent.putExtra("aios.intent.extra.TEXT", text);
		intent.putExtra("aios.intent.extra.PRIORITY", 1);
		context.sendBroadcast(intent);
	}
}
