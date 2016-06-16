#include "mp4decoder.h"

AVPacket dmx_pkt;
AVPacket dec_pkt;
AVFrame *vdo_frame = NULL;
AVFrame *ado_frame = NULL;
AVFormatContext *fmt_ctx = NULL;
AVCodecContext *ado_codec_ctx = NULL;
AVCodecContext *vdo_codec_ctx = NULL;
SwrContext *swr_ctx = NULL;

int ado_stream_idx = -1;
int vdo_stream_idx = -1;

int ado_frame_cnt = 0;
int vdo_frame_cnt = 0;

double ado_time_base = 0.0;
double vdo_time_base = 0.0;

/*****************************************************************************/
// YUV -> RGB
int *colortab = NULL;
int *u_b_tab = NULL;
int *u_g_tab = NULL;
int *v_g_tab = NULL;
int *v_r_tab = NULL;

unsigned int *rgb_2_pix = NULL;
unsigned int *r_2_pix = NULL;
unsigned int *g_2_pix = NULL;
unsigned int *b_2_pix = NULL;

void CreateYUVTab_16() {
	int i;
	int u, v;

	colortab = (int *) av_malloc(4 * 256 * sizeof(int));
	u_b_tab = &colortab[0 * 256];
	u_g_tab = &colortab[1 * 256];
	v_g_tab = &colortab[2 * 256];
	v_r_tab = &colortab[3 * 256];

	for (i = 0; i < 256; i++) {
		u = v = (i - 128);

		u_b_tab[i] = (int) (1.772 * u);
		u_g_tab[i] = (int) (0.34414 * u);
		v_g_tab[i] = (int) (0.71414 * v);
		v_r_tab[i] = (int) (1.402 * v);
	}

	rgb_2_pix = (unsigned int *) av_malloc(3 * 768 * sizeof(unsigned int));
	r_2_pix = &rgb_2_pix[0 * 768];
	g_2_pix = &rgb_2_pix[1 * 768];
	b_2_pix = &rgb_2_pix[2 * 768];

	for (i = 0; i < 256; i++) {
		r_2_pix[i] = 0;
		g_2_pix[i] = 0;
		b_2_pix[i] = 0;
	}

	for (i = 0; i < 256; i++) {
		r_2_pix[i + 256] = (i & 0xF8) << 8;
		g_2_pix[i + 256] = (i & 0xFC) << 3;
		b_2_pix[i + 256] = (i) >> 3;
	}

	for (i = 0; i < 256; i++) {
		r_2_pix[i + 512] = 0xF8 << 8;
		g_2_pix[i + 512] = 0xFC << 3;
		b_2_pix[i + 512] = 0x1F;
	}

	r_2_pix += 256;
	g_2_pix += 256;
	b_2_pix += 256;
}

void DeleteYUVTab() {
	if (colortab != NULL) {
		av_free(colortab);
		colortab = NULL;
	}
	if (rgb_2_pix != NULL) {
		av_free(rgb_2_pix);
		rgb_2_pix = NULL;
	}
}

void DisplayYUV_16(unsigned int *pdst1, int format, unsigned char *y,
	unsigned char *u, unsigned char *v, int width, int height,
	int src_ystride, int src_uvstride, int dst_ystride) {

	int i, j;
	int r, g, b, rgb;

	int yy, ub, ug, vg, vr;

	unsigned char* yoff;
	unsigned char* uoff;
	unsigned char* voff;

	unsigned int* pdst = pdst1;

	int width2 = width / 2;
	int height2 = height / 2;

	if (AV_PIX_FMT_YUV420P == format) {  // YUV420P planar YUV 4:2:0, 12bpp, (1 Cr & Cb sample per 2x2 Y samples)
		for (j = 0; j < height2; j++) {
			yoff = y + (j << 1) * src_ystride;
			uoff = u + j * src_uvstride;
			voff = v + j * src_uvstride;

			for (i = 0; i < width2; i++) {

				ub = u_b_tab[*(uoff + i)];
				ug = u_g_tab[*(uoff + i)];
				vg = v_g_tab[*(voff + i)];
				vr = v_r_tab[*(voff + i)];

				yy = *(yoff + (i << 1));

				b = yy + ub;
				g = yy - ug - vg;
				r = yy + vr;

				rgb = r_2_pix[r] + g_2_pix[g] + b_2_pix[b];

				yy = *(yoff + (i << 1) + 1);
				b = yy + ub;
				g = yy - ug - vg;
				r = yy + vr;

				pdst[(j * dst_ystride + i)] = (rgb)
						+ ((r_2_pix[r] + g_2_pix[g] + b_2_pix[b]) << 16);

				yy = *(yoff + (i << 1) + src_ystride);
				b = yy + ub;
				g = yy - ug - vg;
				r = yy + vr;

				rgb = r_2_pix[r] + g_2_pix[g] + b_2_pix[b];

				yy = *(yoff + (i << 1) + src_ystride + 1);
				b = yy + ub;
				g = yy - ug - vg;
				r = yy + vr;

				pdst[((2 * j + 1) * dst_ystride + i * 2) >> 1] = (rgb)
						+ ((r_2_pix[r] + g_2_pix[g] + b_2_pix[b]) << 16);
			}
		}
	} else {  // YUVJ422P planar YUV 4:2:2, 16bpp, full scale (JPEG)
		for (j = 0; j < height2; j++) {
			yoff = y + (j << 1) * src_ystride;
			uoff = u + (j << 1) * src_uvstride;
			voff = v + (j << 1) * src_uvstride;

			for (i = 0; i < width2; i++) {
				ub = u_b_tab[*(uoff + i)];
				ug = u_g_tab[*(uoff + i)];
				vg = v_g_tab[*(voff + i)];
				vr = v_r_tab[*(voff + i)];

				yy = *(yoff + (i << 1));

				b = yy + ub;
				g = yy - ug - vg;
				r = yy + vr;

				rgb = r_2_pix[r] + g_2_pix[g] + b_2_pix[b];

				yy = *(yoff + (i << 1) + 1);
				b = yy + ub;
				g = yy - ug - vg;
				r = yy + vr;

				pdst[(j * dst_ystride + i)] = (rgb)
						+ ((r_2_pix[r] + g_2_pix[g] + b_2_pix[b]) << 16);

				yy = *(yoff + (i << 1) + src_ystride);
				b = yy + ub;
				g = yy - ug - vg;
				r = yy + vr;

				rgb = r_2_pix[r] + g_2_pix[g] + b_2_pix[b];

				yy = *(yoff + (i << 1) + src_ystride + 1);
				b = yy + ub;
				g = yy - ug - vg;
				r = yy + vr;

				pdst[((2 * j + 1) * dst_ystride + i * 2) >> 1] = (rgb)
						+ ((r_2_pix[r] + g_2_pix[g] + b_2_pix[b]) << 16);
			}
		}
	}
}
/*****************************************************************************/

JNIEXPORT jint JNICALL Java_com_mp4player_jni_MP4Decoder_create(JNIEnv *env,
	jobject this, jstring file_path) {

	const char *_file_path = (*env)->GetStringUTFChars(env, file_path, 0);
	int ret, i, nb;
	struct AVCodec *codec = NULL;
	
	CreateYUVTab_16();
	av_register_all();
	avcodec_register_all();
	avformat_network_init();

	if ((ret = avformat_open_input(&fmt_ctx, _file_path, NULL, NULL)) < 0) {
		char buf[MAX_ERROR_INFO_SIZE];
		av_strerror(ret, buf, MAX_ERROR_INFO_SIZE);
		LOGE("Failed to open input format: %s", buf);
		(*env)->ReleaseStringUTFChars(env, file_path, _file_path);
		return AV_CODEC_ERROR;
	}
	(*env)->ReleaseStringUTFChars(env, file_path, _file_path);

	if (avformat_find_stream_info(fmt_ctx, NULL) < 0) {
		LOGE("Failed to find stream info.\n");
		return AV_CODEC_ERROR;
	}

	nb = fmt_ctx->nb_streams;
	for (i = 0; i < nb; i++) {
		if (AVMEDIA_TYPE_VIDEO == fmt_ctx->streams[i]->codec->codec_type) {
			vdo_stream_idx = i;
		}
		if (AVMEDIA_TYPE_AUDIO == fmt_ctx->streams[i]->codec->codec_type) {
			ado_stream_idx = i;
		}
		if (vdo_stream_idx >= 0 && ado_stream_idx >= 0) {
			break;
		}
	}

	if (vdo_stream_idx >= 0) {
		vdo_codec_ctx = fmt_ctx->streams[vdo_stream_idx]->codec;
		codec = avcodec_find_decoder(vdo_codec_ctx->codec_id);
		if (NULL == codec) {
			LOGE("Failed to find %s codec.\n", 
				av_get_media_type_string(vdo_codec_ctx->codec_id));
			return AV_CODEC_ERROR;
		}
		if (avcodec_open2(vdo_codec_ctx, codec, NULL) < 0) {
			LOGE("Failed to open %s codec.\n", 
				av_get_media_type_string(vdo_codec_ctx->codec_id));
			return AV_CODEC_ERROR;
		}
	}

	if (ado_stream_idx >= 0) {
		ado_codec_ctx = fmt_ctx->streams[ado_stream_idx]->codec;
		codec = avcodec_find_decoder(ado_codec_ctx->codec_id);
		if (NULL == codec) {
			LOGE("Failed to find %s codec.\n", 
				av_get_media_type_string(ado_codec_ctx->codec_id));
			return AV_CODEC_ERROR;
		}
		if (avcodec_open2(ado_codec_ctx, codec, NULL) < 0) {
			LOGE("Failed to open %s codec.\n", 
				av_get_media_type_string(ado_codec_ctx->codec_id));
			return AV_CODEC_ERROR;
		}
	}

	ado_frame = av_frame_alloc();
	vdo_frame = av_frame_alloc();
	if (NULL == ado_frame || NULL == vdo_frame) {
		LOGE("Failed to alloc frame.\n");
		return AV_CODEC_ERROR;
	}

	ado_time_base = av_q2d(fmt_ctx->streams[ado_stream_idx]->time_base);
	vdo_time_base = av_q2d(fmt_ctx->streams[vdo_stream_idx]->time_base);

	return fmt_ctx->duration / AV_TIME_BASE;
}

JNIEXPORT jint JNICALL Java_com_mp4player_jni_MP4Decoder_demux(JNIEnv *env,
	jobject this, jbyteArray out_bytes, jlongArray out_params) {

	int ret = AV_CODEC_SUCCESS;
	int length = 0;
	int decoded_size = 0;
	int got_frame = 0;

	jbyte *_out_bytes = (jbyte *) (*env)->GetByteArrayElements(env, out_bytes, 0);
	if (NULL == _out_bytes) {
		LOGE("Failed to get output byte array.\n");
		return AV_CODEC_ERROR;
	}
	jlong *_out_params = (jlong *) (*env)->GetLongArrayElements(env, out_params, 0);
	if (NULL == _out_params) {
		LOGE("Failed to get output params.\n");
		return AV_CODEC_ERROR;
	}

	// Read frame from file if dmx_pkt is empty, otherwise read audio frame from
	// current dmx_pkt again
	av_init_packet(&dmx_pkt);
	if (dmx_pkt.size <= 0) {
		if (av_read_frame(fmt_ctx, &dmx_pkt) < 0) {
			av_free_packet(&dmx_pkt);
			(*env)->ReleaseByteArrayElements(env, out_bytes, _out_bytes, 0);
			(*env)->ReleaseLongArrayElements(env, out_params, _out_params, 0);
			return AV_CODEC_FILE_END;
		}
	}

	// Get original(non-decoded) data from dmx_pkt
	decoded_size = dmx_pkt.size;
	if (vdo_stream_idx == dmx_pkt.stream_index) {  // video data, not decode
		_out_params[0] = dmx_pkt.size;
		_out_params[1] = (long) (dmx_pkt.pts * vdo_time_base * 1000);
		_out_params[2] = AV_CODEC_ID_MJPEG == vdo_codec_ctx->codec_id ? 
			AV_TYPE_VIDEO_MJPEG : AV_TYPE_VIDEO_H264;
		memcpy(_out_bytes, dmx_pkt.data, dmx_pkt.size);

	} else if (ado_stream_idx == dmx_pkt.stream_index) {  // audio data, decode
		int res = avcodec_decode_audio4(ado_codec_ctx, ado_frame, &got_frame, &dmx_pkt);
		if (res < 0) {
//			char buf[MAX_ERROR_INFO_SIZE];
//			av_strerror(res, buf, MAX_ERROR_INFO_SIZE);
//			LOGE("Failed to decode audio, FFMPEG errcode: %s", buf);
			_out_params[0] = 0;
			_out_params[1] = (long) (dmx_pkt.pts * ado_time_base * 1000);
			_out_params[2] = AV_CODEC_ID_MP2 == ado_codec_ctx->codec_id ?
				AV_TYPE_AUDIO_MP2 : AV_TYPE_AUDIO_AAC;
			decoded_size = dmx_pkt.size;
			ret = AV_CODEC_AUDIO_ERROR;

		} else {
			if (got_frame) {
				if (AV_CODEC_ID_MP2 == ado_codec_ctx->codec_id) {  // audio, MP2
					decoded_size = FFMIN(res, dmx_pkt.size);
					length = ado_frame->nb_samples * 
						av_get_bytes_per_sample(ado_frame->format);
					_out_params[0] = length;
					_out_params[1] = (long) (dmx_pkt.pts * ado_time_base * 1000);
					_out_params[2] = AV_TYPE_AUDIO_MP2;
					memcpy(_out_bytes, ado_frame->extended_data[0], length);
					
				} else {  // audio, AAC
					uint8_t **out_buf = NULL;
					int out_cnt = 0;
					if (NULL == swr_ctx) {
						swr_ctx = swr_alloc_set_opts(swr_ctx, ado_frame->channel_layout,
							AV_SAMPLE_FMT_S16, ado_frame->sample_rate,
							ado_frame->channel_layout, ado_codec_ctx->sample_fmt,
							ado_frame->sample_rate, 0, NULL);
						swr_init(swr_ctx);
					}
					out_cnt = av_rescale_rnd(ado_frame->nb_samples, 
						ado_frame->sample_rate, ado_frame->sample_rate, AV_ROUND_UP);
					// Alloc output buffer
					av_samples_alloc_array_and_samples(&out_buf, NULL,
						ado_codec_ctx->channels, out_cnt, AV_SAMPLE_FMT_S16, 0);
					if (swr_convert(swr_ctx, out_buf, out_cnt,
					    (const uint8_t **)ado_frame->data, ado_frame->nb_samples) < 0) {
						LOGE("Failed to convert an AAC frame.\n");
						ret = AV_CODEC_ERROR;
					}
					length = av_samples_get_buffer_size(NULL, ado_codec_ctx->channels,
						ado_frame->nb_samples, AV_SAMPLE_FMT_S16, 1);
					_out_params[0] = length;
					_out_params[1] = (long) (dmx_pkt.dts * ado_time_base * 1000);
					_out_params[2] = AV_TYPE_AUDIO_AAC;
					memcpy(_out_bytes, out_buf[0], length);

					if (NULL != out_buf) {
						av_freep(&out_buf[0]);
					}
					av_freep(&out_buf);
					out_buf = NULL;
				}
			}
		}
	}

	dmx_pkt.data += decoded_size;
	dmx_pkt.size -= decoded_size;

	if (dmx_pkt.size <= 0) {
		dmx_pkt.data = NULL;
		dmx_pkt.size = 0;
		av_free_packet(&dmx_pkt);
	}

	(*env)->ReleaseByteArrayElements(env, out_bytes, _out_bytes, 0);
	(*env)->ReleaseLongArrayElements(env, out_params, _out_params, 0);

	return ret;
}

JNIEXPORT jint JNICALL Java_com_mp4player_jni_MP4Decoder_decode(JNIEnv *env,
	jobject this, jbyteArray in_bytes, jint in_len, jbyteArray out_bytes) {
	
	int ret = AV_CODEC_ERROR;
	int finished = 0;

	jbyte *_in_bytes = (jbyte *) (*env)->GetByteArrayElements(env, in_bytes, 0);
	if (NULL == _in_bytes) {
		LOGE("Failed to get input byte array.\n");
		return AV_CODEC_ERROR;
	}
	jbyte *_out_bytes = (jbyte *) (*env)->GetLongArrayElements(env, out_bytes, 0);
	if (NULL == _out_bytes) {
		LOGE("Failed to get output byte array.\n");
		return AV_CODEC_ERROR;
	}

	av_init_packet(&dec_pkt);
	dec_pkt.data = _in_bytes;
	dec_pkt.size = in_len;
	int res = avcodec_decode_video2(vdo_codec_ctx, vdo_frame, &finished, &dec_pkt);
	if (res > 0 && 1 == finished) {
		ret = vdo_codec_ctx->width;
		DisplayYUV_16((int *) _out_bytes, vdo_frame->format, vdo_frame->data[0],
			vdo_frame->data[1], vdo_frame->data[2], vdo_codec_ctx->width,
			vdo_codec_ctx->height, vdo_frame->linesize[0], vdo_frame->linesize[1],
			vdo_codec_ctx->width);
	}

	dec_pkt.data = NULL;
	dec_pkt.size = 0;
	av_free_packet(&dec_pkt);

	(*env)->ReleaseByteArrayElements(env, in_bytes, _in_bytes, 0);
	(*env)->ReleaseByteArrayElements(env, out_bytes, _out_bytes, 0);

	return ret;
}

JNIEXPORT jint JNICALL Java_com_mp4player_jni_MP4Decoder_seekto(JNIEnv *env,
	jclass this, jint start_time) {

	if (start_time < 0 || start_time > fmt_ctx->duration / AV_TIME_BASE) {
		LOGE("Failed seekto caused by wrong start time.\n");
		return AV_CODEC_ERROR;
	}

	int64_t real_time = (int64_t) (start_time * AV_TIME_BASE);
	if (av_seek_frame(fmt_ctx, -1, real_time, AVSEEK_FLAG_ANY) < 0) {
		LOGE("Failed seekto caused by seek frame error.\n");
		return AV_CODEC_ERROR;
	}

	return AV_CODEC_SUCCESS;
}

JNIEXPORT jint JNICALL Java_com_mp4player_jni_MP4Decoder_snapshot(JNIEnv *env,
	jobject this, jint start_time, jbyteArray out_bytes, jlongArray out_params) {

	int ret = AV_CODEC_ERROR;
	int loop = 1;
	int length = 0;
	int finished = 0;
	int64_t real_time = 0;

	jbyte *_out_bytes = (jbyte *) (*env)->GetByteArrayElements(env, out_bytes, 0);
	if (NULL == _out_bytes) {
		LOGE("Failed to get output byte array.\n");
		return AV_CODEC_ERROR;
	}
	jlong *_out_params = (jlong *) (*env)->GetLongArrayElements(env, out_params, 0);
	if (NULL == _out_params) {
		LOGE("Failed to get output params.\n");
		return AV_CODEC_ERROR;
	}
	if (start_time < 0 || start_time > fmt_ctx->duration / AV_TIME_BASE) {
		LOGE("Failed snapshot caused by wrong start time.\n");
		return AV_CODEC_ERROR;
	}

	AVPacket pkt;
	av_init_packet(&pkt);
	pkt.data = NULL;
	pkt.size = 0;
	real_time = (int64_t) (start_time * AV_TIME_BASE);
	if (av_seek_frame(fmt_ctx, -1, real_time,
		AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_ANY) < 0) {
		av_free_packet(&pkt);
		(*env)->ReleaseByteArrayElements(env, out_bytes, _out_bytes, 0);
		(*env)->ReleaseLongArrayElements(env, out_params, _out_params, 0);
		return AV_CODEC_ERROR;
	}

	while (loop) {
		if (av_read_frame(fmt_ctx, &pkt) < 0) {
			break;
		} else {
			if (vdo_stream_idx == pkt.stream_index) {
				_out_params[0] = pkt.size;
				_out_params[1] = AV_CODEC_ID_MJPEG == vdo_codec_ctx->codec_id ? 
					AV_TYPE_VIDEO_MJPEG : AV_TYPE_VIDEO_H264;
				
				if (AV_CODEC_ID_MJPEG == vdo_codec_ctx->codec_id) {
					memcpy(_out_bytes, pkt.data, pkt.size);
					ret = AV_CODEC_SUCCESS;
					break;
				} else {
					int res = avcodec_decode_video2(vdo_codec_ctx, vdo_frame,
						&finished, &pkt);
					if (res > 0 && 1 == finished) {
						ret = vdo_codec_ctx->width;
						DisplayYUV_16((int *) _out_bytes, vdo_frame->format,
							vdo_frame->data[0], vdo_frame->data[1],
							vdo_frame->data[2], vdo_codec_ctx->width,
							vdo_codec_ctx->height, vdo_frame->linesize[0],
							vdo_frame->linesize[1], vdo_codec_ctx->width);
						break;
					}
				}
			}
		}
	}

	pkt.data = NULL;
	pkt.size = 0;
	av_free_packet(&pkt);

	(*env)->ReleaseByteArrayElements(env, out_bytes, _out_bytes, 0);
	(*env)->ReleaseLongArrayElements(env, out_params, _out_params, 0);

	return ret;
}

JNIEXPORT jint JNICALL Java_com_mp4player_jni_MP4Decoder_release(JNIEnv *env,
	jobject this) {

	if (NULL != ado_codec_ctx) {
		avcodec_close(ado_codec_ctx);
		ado_codec_ctx = NULL;
	}
	if (NULL != vdo_codec_ctx) {
		avcodec_close(vdo_codec_ctx);
		vdo_codec_ctx = NULL;
	}
	if (NULL != fmt_ctx) {
		avformat_close_input(&fmt_ctx);
		fmt_ctx = NULL;		
	}
	if (NULL != ado_frame) {
		av_free(ado_frame);
		ado_frame = NULL;
	}
	if (NULL != vdo_frame) {
		av_free(vdo_frame);
		vdo_frame = NULL;
	}
	DeleteYUVTab();

	return AV_CODEC_SUCCESS;
}
