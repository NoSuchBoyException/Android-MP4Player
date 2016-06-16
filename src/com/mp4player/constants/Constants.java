package com.mp4player.constants;

import java.io.File;

import android.os.Environment;

public class Constants {

	public static final String MP4_FILE_NAME = "sample1.mp4";
	public static final String MP4_FILE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath()
			+ File.separator + MP4_FILE_NAME;

}
