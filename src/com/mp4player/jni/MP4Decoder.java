package com.mp4player.jni;

public class MP4Decoder {
	
	public static class AV_TYPE {
		public static final int AV_TYPE_BASE 		 =  0;
		public static final int AV_TYPE_AUDIO_MP2    =  1;
		public static final int AV_TYPE_AUDIO_AAC	 =  2;
		public static final int AV_TYPE_VIDEO_MJPEG  =  3;
		public static final int AV_TYPE_VIDEO_H264   =  4;
	}
	
	public static class AV_CODEC_FLAG {
		public static final int AV_CODEC_SUCCESS     =  0;
		public static final int AV_CODEC_ERROR       = -1;
		public static final int AV_CODEC_AUDIO_ERROR = -2;
		public static final int AV_CODEC_FILE_END    = -3;
	}
	
	static {
		System.loadLibrary("ffmpeg");
		System.loadLibrary("mp4decoder");
	}

	/******************************************************************************
	 *Function:    Java_com_mp4decoder_jni_MP4Decoder_create
	 *Description: create and init mp4 decoder
	 *Input: 	   path: mp4 file path
	 *Output: 	   none
	 *Return: 	   AV_CODEC_ERROR when error happens, otherwise duration of file,
	 			   time unit is second
	******************************************************************************/
	public native static int create(String path);

	/******************************************************************************
	 *Function:    demux
	 *Description: demux a/v stream data in the file
	 *Input: 	   none
	 *Output: 	   out_bytes: a frame of original(non-decoded) video stream data or
				   decoded audio stream data. If stream type is AV_TYPE_VIDEO_H264,
				   send it into decode function in this file, while use android API
				   to decode it if type is AV_TYPE_VIDEO_MJPEG
				   out_params: params of this a/v frame data, out_params[0] is length,
				   out_params[1] is timestamp, out_params[2] is stream type
	 *Return: 	   AV_CODEC_ERROR when error happens, AV_CODEC_AUDIO_ERROR when decode
			   	   audio frame failed, AV_CODEC_FILE_END when file read end, otherwise
			   	   AV_CODEC_SUCCESS
	******************************************************************************/
	public native static int demux(byte[] out_bytes, long[] out_params);

	/******************************************************************************
	 *Function:    Java_com_mp4decoder_jni_MP4Decoder_decode
	 *Description: decode original video stream data
	 *Input: 	   in_bytes: a frame of original video stream data which type is
				   AV_TYPE_VIDEO_H264
				   in_len: length of the stream data in this frame
	 *Output: 	   out_bytes: a frame of decoded a/v stream data correspond to in_bytes
	 *Return: 	   AV_CODEC_ERROR when error happens, otherwise width of video frame
	******************************************************************************/
	public native static int decode(byte[] in_bytes, int in_len, byte[] out_bytes);

	/******************************************************************************
	 *Function:    Java_com_mp4decoder_jni_MP4Decoder_seekto
	 *Description: seek to the appointed position
	 *Input:	   start_time: the start time to seek to, time unit is second
	 *Output:	   none
	 *Return: 	   AV_CODEC_ERROR when error happens, otherwise AV_CODEC_SUCCESS
	******************************************************************************/
	public native static int seekto(int start_time);

	/******************************************************************************
	 *Function:    Java_com_mp4decoder_jni_MP4Decoder_snapshot
	 *Description: snapshot at the appointed start time
	 *Input:	   start_time: the start time to snapshot, time unit is second
	 *Output:	   out_bytes: a frame of decoded video stream data at the start time
				   out_params: params of this video frame, out_params[0] is length,
				   out_params[1] is stream data type
	 *Return: 	   AV_CODEC_ERROR when error happens, AV_CODEC_SUCCESS when stream
	 	 	 	   data type is AV_TYPE_VIDEO_MJPEG, otherwise width of video frame
	******************************************************************************/
	public native static int snapshot(int start_time, byte[] out_bytes, long[] out_params);

	/******************************************************************************
	 *Function:    Java_com_mp4decoder_jni_MP4Decoder_release
	 *Description: release mp4 decoder
	 *Input:	   none
	 *Output:	   none
	 *Return: 	   always return AV_CODEC_SUCCESS
	******************************************************************************/
	public native static int release();

}