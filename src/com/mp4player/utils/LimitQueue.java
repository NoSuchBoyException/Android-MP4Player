package com.mp4player.utils;

import java.nio.Buffer;
import java.util.ArrayList;
import java.util.List;

import com.mp4player.model.AudioFrame;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;

public class LimitQueue<E> {
	
	private Object syncObj = new Object();
	
	private int mMax;   // 最大容量
	private int mSize;  // 当前数目
	private int mHead;  // 队列头部
	private int mTail;  // 队列尾部
	
	private List<E> mElems;
	
	public LimitQueue(int max) {
		mMax = max;
		mSize = 0;
		mHead = -1;
		mTail = -1;
		mElems = new ArrayList<E>(max);
	}
	
	public void clear() {
		mHead = -1;
		mTail = -1;
		mSize = 0;
		mElems.clear();
	}
	
	public boolean isFull() {
		return mMax == mSize;
	}
	
	public boolean isEmpty() {
		return 0 == mSize;
	}
	
	public int size() {
		return mSize;
	}
	
	public void offer(E e) {
		if (null == e) {
			return;
		}
		
		synchronized (syncObj) {
			mTail = (mTail + 1) % mMax;
			if (mElems.size() < mMax) {
				mElems.add(e);
			} else {
				mElems.set(mTail, e);
			}

			if (++mSize > mMax) {
				mSize = mMax;
			}
		}
	}
	
	/**
	 * 入队MPJPG解码出的Bitmap
	 */
	@SuppressWarnings("unchecked")
	public void offerVideoFrame(Bitmap bitmap) {
		if (null == bitmap) {
			return;
		}
		
		synchronized (syncObj) {
			mTail = (mTail + 1) % mMax;
			if (mElems.size() < mMax) {
				mElems.add((E)bitmap);
			} else {
				Bitmap b = (Bitmap)mElems.get(mTail);
				if (!b.isRecycled()) {
					b.recycle();
					b = null;
				}
				
				mElems.add((E)bitmap);
			}
		}
	}
	
	/**
	 * 入队H.264解码出的Bitmap，可复用队列中已存在的Bitmap内存空间
	 */
	@SuppressWarnings("unchecked")
	public void offerVideoFrame(Buffer src, int w, int h) {
		if (null == src || w <= 0 || h <= 0) {
			return;
		}
		
		synchronized (syncObj) {
			mTail = (mTail + 1) % mMax;
			src.position(0);
			if (mElems.size() < mMax) {
				Bitmap bitmap = Bitmap.createBitmap(w, h, Config.RGB_565);
				bitmap.copyPixelsFromBuffer(src);
				mElems.add((E)bitmap);
			} else {
				Bitmap bitmap = (Bitmap)mElems.get(mTail);
				
				// Bitmap宽和高至少一个不同时才重新创建，否则复用原内存空间
				if (w != bitmap.getWidth() || h != bitmap.getHeight()) {
					bitmap = Bitmap.createBitmap(w, h, Config.RGB_565);
					mElems.set(mTail, (E)bitmap);
				}
				bitmap.copyPixelsFromBuffer(src);
			}
			
			if (++mSize > mMax) {
				mSize = mMax;
			}
		}
	}
	
	/**
	 * 当E为AudioData时使用，可复用队列中已存在的AudioData内存空间
	 */
	@SuppressWarnings("unchecked")
	public void offerAudioFrame(byte[] src, int len) {
		if (null == src || len <= 0) {
			return;
		}
		
		synchronized (syncObj) {
			mTail = (mTail + 1) % mMax;
			if (mElems.size() < mMax) {
				AudioFrame frame = new AudioFrame();
				frame.setData(src, len);
				mElems.add((E)frame);
			} else {
				AudioFrame frame = (AudioFrame)mElems.get(mTail);
				frame.setData(src, len);
			}
			
			if (++mSize > mMax) {
				mSize = mMax;
			}
		}
	}
	
	public E poll() {
		E e = null;
		synchronized (syncObj) {
			if (mSize > 0) {
				mHead = (mHead + 1) % mMax;
				e = mElems.get(mHead);
				mSize--;
			} else {
				mSize = 0;
			}
		}
		
		return e;
	}
	
}
