#ifndef _MP4_DECODER_
#define _MP4_DECODER_

#ifdef __cplusplus
extern "C"
#endif

#include <jni.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <android/log.h>

#include "include/libavutil/imgutils.h"
#include "include/libavutil/samplefmt.h"
#include "include/libavutil/timestamp.h"
#include "include/libavutil/common.h"
#include "include/libavformat/avformat.h"
#include "include/libavcodec/avcodec.h"
#include "include/libswresample/swresample.h"

enum AV_TYPE {
	AV_TYPE_BASE,
	AV_TYPE_AUDIO_MP2,
	AV_TYPE_AUDIO_AAC,
	AV_TYPE_VIDEO_MJPEG,
	AV_TYPE_VIDEO_H264
};

enum AV_CODEC_FLAG {
	AV_CODEC_SUCCESS 	 =  0,
	AV_CODEC_ERROR 	 	 = -1,
	AV_CODEC_AUDIO_ERROR = -2,
	AV_CODEC_FILE_END 	 = -3
};

#define MAX_ERROR_INFO_SIZE		(1024)

#define  LOG_TAG    "libmp4decoder"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/******************************************************************************
 *Function:    Java_com_mp4decoder_jni_MP4Decoder_create
 *Description: create and init mp4 decoder
 *Input: 	   file_path: mp4 file path
 *Output: 	   none
 *Return: 	   AV_CODEC_ERROR when error happens, otherwise duration of file,
			   time unit is second
******************************************************************************/
JNIEXPORT jint JNICALL Java_com_mp4player_jni_MP4Decoder_create(JNIEnv *env,
	jobject this, jstring file_path);

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
JNIEXPORT jint JNICALL Java_com_mp4player_jni_MP4Decoder_demux(JNIEnv *env,
	jobject this, jbyteArray out_bytes, jlongArray out_params);

/******************************************************************************
 *Function:    Java_com_mp4decoder_jni_MP4Decoder_decode
 *Description: decode original video stream data
 *Input: 	   in_bytes: a frame of original video stream data which type is
			   AV_TYPE_VIDEO_H264
			   in_len: length of the stream data in this frame
 *Output: 	   out_bytes: a frame of decoded a/v stream data correspond to in_bytes
 *Return: 	   AV_CODEC_ERROR when error happens, otherwise width of video frame
******************************************************************************/
JNIEXPORT jint JNICALL Java_com_mp4player_jni_MP4Decoder_decode(JNIEnv *env,
	jobject this, jbyteArray in_bytes, jint in_len, jbyteArray out_bytes);

/******************************************************************************
 *Function:    Java_com_mp4decoder_jni_MP4Decoder_seekto
 *Description: seek to the appointed position
 *Input:	   start_time: the start time to seek to, time unit is second
 *Output:	   none
 *Return: 	   AV_CODEC_ERROR when error happens, otherwise AV_CODEC_SUCCESS
******************************************************************************/
JNIEXPORT jint JNICALL Java_com_mp4player_jni_MP4Decoder_seekto(JNIEnv *env,
	jclass this, jint start_time);

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
JNIEXPORT jint JNICALL Java_com_mp4player_jni_MP4Decoder_snapshot(JNIEnv *env,
	jobject this, jint start_time, jbyteArray out_bytes, jlongArray out_params);

/******************************************************************************
 *Function:    Java_com_mp4decoder_jni_MP4Decoder_release
 *Description: release mp4 decoder
 *Input:	   none
 *Output:	   none
 *Return: 	   always return AV_CODEC_SUCCESS
******************************************************************************/
JNIEXPORT jint JNICALL Java_com_mp4player_jni_MP4Decoder_release(JNIEnv *env,
	jobject this);

#ifdef __cplusplus
}
#endif

#endif
