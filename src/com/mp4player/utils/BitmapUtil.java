package com.mp4player.utils;

import java.nio.Buffer;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;

public class BitmapUtil {
	
	public static Bitmap decodeByteArray(byte[] src, int offset, int len) {
		if (null == src || len <= 0 || offset < 0) {
			return null;
		}
		
		return BitmapFactory.decodeByteArray(src, offset, len);
	}
	
	public static Bitmap getBitmapFromBuffer(Buffer src, int width) {
		if (null == src) {
			return null;
		}
		
		int height = getHeightFromWidth(width);
		if (-1 == height) {
			return null;
		}
		
		Bitmap bitmap = null;
		try {
			bitmap = Bitmap.createBitmap(width, height, Config.RGB_565);
			bitmap.copyPixelsFromBuffer(src);
		} catch (Exception e) {
			e.printStackTrace();
			bitmap = null;
		}
		
		return bitmap;
	}
	
	public static int getHeightFromWidth(int width) {
		int h = -1;
		
		switch (width) {
		case 1280:
			h = 720;
			break;
		case 640:
			h = 480;
			break;
		case 320:
			h = 240;
			break;
		default:
			break;
		}
		
		return h;
	}
	
}
