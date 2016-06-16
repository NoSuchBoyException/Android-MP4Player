package com.mp4player.player;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import com.mp4player.jni.MP4Decoder;
import com.mp4player.jni.MP4Decoder.AV_CODEC_FLAG;
import com.mp4player.jni.MP4Decoder.AV_TYPE;
import com.mp4player.model.AVFrameContext;
import com.mp4player.model.AudioFrame;
import com.mp4player.model.OriginAVFrame;
import com.mp4player.utils.AsyncTask;
import com.mp4player.utils.BitmapUtil;
import com.mp4player.utils.LimitQueue;

public class MP4Player {

	private static final int MAX_DEMUX_FRAME_SIZE	 = 1280 * 720;
	private static final int MAX_DECODE_FRAME_SIZE	 = 1280 * 720 * 2;
	private static final int MAX_DEMUX_FRAME_COUNT	 = 100;
	private static final int MINOR_DEMUX_FRAME_COUNT = 30;
	private static final int MAX_DECODE_FRAME_COUNT	 = 10;
	private static final int MIN_AUDIO_BUFFER_SIZE   = 2304;
	
	private List<OriginAVFrame> mOriginAVFrameList;
	private LimitQueue<Bitmap> mVideoFrameQueue;
	private LimitQueue<AudioFrame> mAudioFrameQueue;
	private LimitQueue<AVFrameContext> mAVFrameCtxQueue;
	
	private final Object mDecoderUseSyncObj		= new Object();
	private final Object mDecoderDestroySyncObj = new Object();
	private final Object mAVFrameListSyncObj 	= new Object();
	private final Object mAVFrameQueueSyncObj	= new Object();
	
	private AudioTrack mAudioTrack;
	
	private Thread mDemuxThread;
	private Thread mDecodeThread;
	
	private byte[] mDemuxFrameBuf;
	private byte[] mDecodeFrameBuf;
	private ByteBuffer mDecodeFrameBufWrapper;

	private AtomicBoolean mIsPlaying;
	private AtomicBoolean mIsPausing;
	private AtomicBoolean mDemuxComplete;
	private AtomicBoolean mDecodeComplete;
	private AtomicBoolean mPlayComplete;
	
	private String mFilePath;
	private int mStartTime;
	private int mDuration;
	
	private IMP4PlayCb mPlayCb;
	
	public static interface IMP4PlayCb {
		
		// 视频播放回调函数
		public void onDisplay(Bitmap bitmap);
		// 进度更新回调函数
		public void onProcess(double percent);
	}
	
	public MP4Player(IMP4PlayCb playCb) {
		mPlayCb = playCb;
	}
	
	public synchronized Bitmap snapshot(String filePath, int startTime) {
		if (null == filePath || startTime < 0) {
			return null;
		}
		
		Bitmap bitmap = null;
		long[] params = new long[2];
		params[0] = 0;
		params[1] = 0;
		
		synchronized (mDecoderUseSyncObj) {
			if (MP4Decoder.create(filePath) > 0) {
				byte[] frameBuf = new byte[MAX_DEMUX_FRAME_SIZE];
				ByteBuffer frameBufWrapper = ByteBuffer.wrap(frameBuf);
				
				int ret = MP4Decoder.snapshot(startTime, frameBuf, params);
				if (AV_CODEC_FLAG.AV_CODEC_ERROR != ret) {
					int len = (int)params[0];
					int type = (int)params[1];
					if (AV_TYPE.AV_TYPE_VIDEO_MJPEG == type && len > 0) {
						bitmap = BitmapUtil.decodeByteArray(frameBuf, 0, len);
					} else if (AV_TYPE.AV_TYPE_VIDEO_H264 == type && len > 0) {
						bitmap = BitmapUtil.getBitmapFromBuffer(frameBufWrapper, ret);
					}
				}

				MP4Decoder.release();
			}
		}
		
		return bitmap;
	}
	
	public synchronized int prepare(String filePath, int startTime) {
		if (null == filePath || startTime < 0) {
			return -1;
		} 
		
		synchronized (mDecoderUseSyncObj) {
			mDuration = MP4Decoder.create(filePath);
			MP4Decoder.release();
			if (AV_CODEC_FLAG.AV_CODEC_ERROR == mDuration) {
				return -1;
			}
		}
		
		mFilePath = filePath;
		mStartTime = startTime;
		
		if (null == mIsPausing) {
			mIsPausing = new AtomicBoolean(true);
		}
		if (null == mIsPlaying) {
			mIsPlaying = new AtomicBoolean(false);
		}
		if (null == mDemuxComplete) {
			mDemuxComplete = new AtomicBoolean(false);
		}
		if (null == mDecodeComplete) {
			mDecodeComplete = new AtomicBoolean(false);
		}
		if (null == mPlayComplete) {
			mPlayComplete = new AtomicBoolean(false);
		}
		
		if (null == mDemuxFrameBuf) {
			mDemuxFrameBuf = new byte[MAX_DEMUX_FRAME_SIZE];
		}
		if (null == mDecodeFrameBuf) {
			mDecodeFrameBuf = new byte[MAX_DECODE_FRAME_SIZE];
			mDecodeFrameBufWrapper = ByteBuffer.wrap(mDecodeFrameBuf);
		}
			
		if (null == mOriginAVFrameList) {
			mOriginAVFrameList = new LinkedList<OriginAVFrame>();
		} else {
			mOriginAVFrameList.clear();
		}
		if (null == mVideoFrameQueue) {
			mVideoFrameQueue = new LimitQueue<Bitmap>(MAX_DECODE_FRAME_COUNT);
		} else {
			mVideoFrameQueue.clear();
		}
		if (null == mAudioFrameQueue) {
			mAudioFrameQueue = new LimitQueue<AudioFrame>(MAX_DECODE_FRAME_COUNT);
		} else {
			mAudioFrameQueue.clear();
		}
		if (null == mAVFrameCtxQueue) {
			mAVFrameCtxQueue = new LimitQueue<AVFrameContext>(MAX_DECODE_FRAME_COUNT);
		} else {
			mAVFrameCtxQueue.clear();
		}
		
		createAudioTrack();
		
		return mDuration;
	}
	
	public synchronized void play() {
		if (null == mPlayCb || mDuration < 0) {
			return;
		}
		
		mIsPlaying.set(true);
		mIsPausing.set(false);
		mDemuxComplete.set(false);
		mDecodeComplete.set(false);
		mPlayComplete.set(false);
		
		mDemuxThread = new Thread(new DemuxRunnable());
		mDecodeThread = new Thread(new DecodeRunnable());
		mDecodeThread.setPriority(Thread.MAX_PRIORITY);
		
		AsyncTask.execute(mDemuxThread);
		AsyncTask.execute(mDecodeThread);
		sleep(100);
		AsyncTask.execute(new Thread(new PlayRunnable()));
	}
	
	public synchronized boolean isPlaying() {
		return mIsPlaying.get();
	}
	
	public synchronized boolean isPlayComplete() {
		return mDecodeComplete.get() && mPlayComplete.get();
	}
	
	public synchronized void pause() {
		mIsPausing.set(true);
	}
	
	public synchronized boolean isPausing() {
		return mIsPausing.get();
	}
	
	public synchronized void resume() {
		mIsPausing.set(false);
	}
	
	public synchronized void stop() {
		if (null != mIsPausing) {
			mIsPausing.set(true);
		}
		if (null != mIsPlaying) {
			mIsPlaying.set(false);
		}
	}
	
	public void release() {
		releaseAudioTrack();
		
		mDemuxFrameBuf = null;
		mDecodeFrameBuf = null;
		
		mOriginAVFrameList = null;
		mAudioFrameQueue = null;
		mVideoFrameQueue = null;
		mAVFrameCtxQueue = null;
		
		System.gc();
	}
	
	public synchronized void setVolumn(float volumn) {
		if (null != mAudioTrack) {
			mAudioTrack.setStereoVolume(volumn, volumn);
		}
	}
	
	private void createAudioTrack() {
		int minBufSize = AudioTrack.getMinBufferSize(16000 * 21 / 20,
				AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
		int size = MIN_AUDIO_BUFFER_SIZE * 2;
		size = size > minBufSize ? size : minBufSize;

		mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
				16000 * 21 / 20, AudioFormat.CHANNEL_OUT_DEFAULT,
				AudioFormat.ENCODING_PCM_16BIT, size, AudioTrack.MODE_STREAM);
		
		mAudioTrack.play();
	}
	
	private void releaseAudioTrack() {
		if (null != mAudioTrack 
				&& AudioTrack.PLAYSTATE_STOPPED != mAudioTrack.getPlayState()) {
			mAudioTrack.stop();
			mAudioTrack.release();
		}
	}

	private void sleep(long time) {
		try {
			Thread.sleep(time);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private long[] createLongArray(int size) {
		long[] array = new long[size];
		for (int i = 0; i < size; i++) {
			array[i] = 0;
		}

		return array;
	}
	
	private class DemuxRunnable implements Runnable {

		@Override
		public void run() {
			synchronized (mDecoderUseSyncObj) {
				if (AV_CODEC_FLAG.AV_CODEC_ERROR == MP4Decoder.create(mFilePath)) {
					stop();
				} else {
					if (0 != mStartTime 
							&& AV_CODEC_FLAG.AV_CODEC_ERROR == MP4Decoder.seekto(mStartTime)) {
						stop();
					} else {
						long[] params = createLongArray(3);
						int len = -1;
						int timestamp = -1;
						int type = AV_CODEC_FLAG.AV_CODEC_ERROR;
						int ret = AV_CODEC_FLAG.AV_CODEC_ERROR;
						byte[] data = null;
						OriginAVFrame frame = null;
						int frameCount = 0;
						boolean isAdded = false;

						while (mIsPlaying.get()) {
							if (mOriginAVFrameList.size() > MAX_DEMUX_FRAME_COUNT) {
								sleep(50);
								continue;
							}

							ret = MP4Decoder.demux(mDemuxFrameBuf, params);
							if (!mIsPlaying.get() || AV_CODEC_FLAG.AV_CODEC_FILE_END == ret) {
								break;
							} else if (AV_CODEC_FLAG.AV_CODEC_ERROR == ret) {
								stop();
								break;
							} else if (AV_CODEC_FLAG.AV_CODEC_AUDIO_ERROR == ret
									|| params[0] <= 0 || params[1] < 0) {
								continue;
							}

							len = (int)params[0];
							timestamp = (int)params[1];
							type = (int)params[2];
							data = new byte[len];

							System.arraycopy(mDemuxFrameBuf, 0, data, 0, len);
							
							if (AV_TYPE.AV_TYPE_VIDEO_H264 == type 
									|| AV_TYPE.AV_TYPE_VIDEO_MJPEG == type) {
								frame = new OriginAVFrame();
								frame.timestamp = timestamp;
								frame.type = type;
								frame.data = data;

								synchronized (mAVFrameListSyncObj) {
									mOriginAVFrameList.add(frame);
								}
							} else if (AV_TYPE.AV_TYPE_AUDIO_AAC == type 
									|| AV_TYPE.AV_TYPE_AUDIO_MP2 == type) {
								frame = new OriginAVFrame();
								frame.timestamp = timestamp;
								frame.type = type;
								frame.data = data;
								
								synchronized (mAVFrameListSyncObj) {
									isAdded = false;
									frameCount = mOriginAVFrameList.size();
									for (int i = 0; i < frameCount; i++) {
										if (mOriginAVFrameList.get(i).timestamp > frame.timestamp) {
											mOriginAVFrameList.add(i, frame);
											isAdded = true;
											break;
										}
									}
									if (!isAdded) {
										mOriginAVFrameList.add(frame);
									}
								}
							}
						}
					}
				}
			}

			mDemuxComplete.set(true);
			if (!mDecodeComplete.get()) {
				synchronized (mDecoderDestroySyncObj) {
					try {
						mDecoderDestroySyncObj.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

			MP4Decoder.release();
			release();
		}
	}

	private class DecodeRunnable implements Runnable {

		@Override
		public void run() {
			OriginAVFrame frame = null;
			AVFrameContext frameCtx = null;
			int width = 0;
			int height = 0;
			
			while (mIsPlaying.get()) {
				if (mAVFrameCtxQueue.isFull()) {
					sleep(50);
					continue;
				}
				
				if (mOriginAVFrameList.size() < MINOR_DEMUX_FRAME_COUNT && !mDemuxComplete.get()) {
					continue;
				}
				
				synchronized (mAVFrameListSyncObj) {
					if (0 != mOriginAVFrameList.size()) {
						frame = mOriginAVFrameList.remove(0);
					}
				}

				frameCtx = new AVFrameContext();
				if (AV_TYPE.AV_TYPE_AUDIO_AAC == frame.type
						|| AV_TYPE.AV_TYPE_AUDIO_MP2 == frame.type) {
					frameCtx.type = frame.type;
					frameCtx.timestamp = frame.timestamp;
					synchronized (mAVFrameQueueSyncObj) {
						mAVFrameCtxQueue.offer(frameCtx);
						mAudioFrameQueue.offerAudioFrame(frame.data, frame.data.length);
					}
				} else if (AV_TYPE.AV_TYPE_VIDEO_H264 == frame.type) {
					width = MP4Decoder.decode(frame.data, frame.data.length, mDecodeFrameBuf);
					if (width > 0) {
						height = BitmapUtil.getHeightFromWidth(width);
						frameCtx.type = frame.type;
						frameCtx.timestamp = frame.timestamp;
						synchronized (mAVFrameQueueSyncObj) {
							mAVFrameCtxQueue.offer(frameCtx);
							mVideoFrameQueue.offerVideoFrame(mDecodeFrameBufWrapper, width, height);
						}
					}
				} else if (AV_TYPE.AV_TYPE_VIDEO_MJPEG == frame.type) {
					frameCtx.type = frame.type;
					frameCtx.timestamp = frame.timestamp;
					
					synchronized (mAVFrameQueueSyncObj) {
						mAVFrameCtxQueue.offer(frameCtx);
						Bitmap bitmap = BitmapFactory.decodeByteArray(frame.data, 0, frame.data.length);
						mVideoFrameQueue.offerVideoFrame(bitmap);
					}
				}
			}
			
			mDecodeComplete.set(true);
			if (mDemuxComplete.get()) {
				synchronized (mDecoderDestroySyncObj) {
					mDecoderDestroySyncObj.notifyAll();
				}
			}
		}
	}

	private class PlayRunnable implements Runnable {

		@Override
		public void run() {
			double startTimeMillis = mStartTime * 1000;
			double totalTimeMillis = mDuration * 1000;
			long t1 = 0;
			long t2 = 0;
			double process = 0.0;
			AVFrameContext frameCtx = null;
			AudioFrame audioFrame = null;
			Bitmap videoFrame = null;
			
			mPlayCb.onProcess(startTimeMillis / totalTimeMillis);
			
			while (mIsPlaying.get()) {
				t1 = System.currentTimeMillis();
				
				if (mIsPausing.get()) {
					sleep(1);
					continue;
				}

				synchronized (mAVFrameQueueSyncObj) {
					if (mAVFrameCtxQueue.isEmpty()) {
						if (!mDecodeComplete.get()) {
							continue;
						} else {
							mPlayCb.onProcess(1.0);
							break;
						}
					} else {
						if (null == frameCtx) {
							frameCtx = mAVFrameCtxQueue.poll();
							if (AV_TYPE.AV_TYPE_VIDEO_H264 == frameCtx.type
									|| AV_TYPE.AV_TYPE_VIDEO_MJPEG == frameCtx.type) {
								videoFrame = mVideoFrameQueue.poll();
							} else if (AV_TYPE.AV_TYPE_AUDIO_AAC == frameCtx.type
									|| AV_TYPE.AV_TYPE_AUDIO_MP2 == frameCtx.type) {
								audioFrame = mAudioFrameQueue.poll();
							}
						}
					}
				}
				
				if (startTimeMillis + 50 < frameCtx.timestamp) {
					sleep(5);
					if ((process = startTimeMillis / totalTimeMillis) >= 1.0) {
						mPlayCb.onProcess(1.0);
						break;
					} else {
						mPlayCb.onProcess(process);
					}
					
					t2 = System.currentTimeMillis();
					startTimeMillis += t2 - t1;
					
				} else {
					startTimeMillis = frameCtx.timestamp;
					if (AV_TYPE.AV_TYPE_VIDEO_H264 == frameCtx.type
							|| AV_TYPE.AV_TYPE_VIDEO_MJPEG == frameCtx.type) {
						mPlayCb.onDisplay(videoFrame);
						mPlayCb.onProcess(startTimeMillis / totalTimeMillis);
						mDecodeFrameBufWrapper.clear();
					} else {
						mAudioTrack.write(audioFrame.getData(), 0, audioFrame.getLength());
					}
					frameCtx = null;
				}
			}
			
			mPlayComplete.set(true);
		}
	}

}
