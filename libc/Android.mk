# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


# Use the prebuilt ffmpeg shared library, ndk will copy .so to libs/local/armeabi/...
LOCAL_PATH 					:= $(call my-dir)
include $(CLEAR_VARS)	
LOCAL_MODULE				:= ffmpeg
LOCAL_SRC_FILES				:= libs/libffmpeg.so
include $(PREBUILT_SHARED_LIBRARY)

# Build MP4Decoder lib that contains mp4 decoders.
include $(CLEAR_VARS)
PATH_TO_FFMPEG_SOURCE		:= $(LOCAL_PATH)/include
LOCAL_C_INCLUDES 			+= $(PATH_TO_FFMPEG_SOURCE)
LOCAL_SHARED_LIBRARIES		:= ffmpeg
LOCAL_LDLIBS				:= -lm -llog
LOCAL_MODULE    			:= mp4decoder
LOCAL_SRC_FILES 			:= mp4decoder.c
include $(BUILD_SHARED_LIBRARY)
