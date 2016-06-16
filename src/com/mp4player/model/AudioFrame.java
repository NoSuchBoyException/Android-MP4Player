package com.mp4player.model;

public class AudioFrame {

	private static final int INCREMENT_SIZE = 512;
	private static final int DEFAULT_SIZE   = 1024;
	
	private int mCapacity;
	private int mLength;
	private byte[] mData;
	
	public AudioFrame() {
		mLength = 0;
		mCapacity = DEFAULT_SIZE;
		mData = new byte[mCapacity];
	}
	
	public void setData(byte[] src, int len) {
		if (null == src || len <= 0) {
			return;
		}
		
		boolean isExtended = false;
		while (mCapacity < len) {
			mCapacity += INCREMENT_SIZE;
			isExtended = true;
		}
		if (isExtended) {
			mData = new byte[mCapacity];
		}
		
		mLength = len;
		System.arraycopy(src, 0, mData, 0, len);
	}
	
	public byte[] getData() {
		return mData;
	}
	
	public int getLength() {
		return mLength;
	}

}
