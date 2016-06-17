## Android-MP4Player 项目说明

#### 项目描述
  基于 ffmpeg 开源音视频库的安卓 MP4 播放器，MP4 文件须由H.264（或MJPEG）视频帧和PCM（或AAC）音频帧组成。
  
#### 项目结构
   文件和路径 | 功能
   -----------|-----------
   src\ | java 源码文件
   res\ | resources 文件
   libc\ | 编解码所需的 c 源码文件
   libs | 依赖库
   ... | ...
  
#### 详细描述
  1. 播放器包含播放，点播和截图功能。
  2. native层包含create，demux，decode，seekto，snapshot，release核心方法。
  3. java层包含分流、解码和播放三个核心线程。
  4. 解码后的 Bitmap 较大，播放器允许的最大解码数为 10
  
#### 缺陷
  1. 属于软解播放，内存开销较大，但已经过音视频内存块复用等内存优化。
  2. 每次调用snapshot函数截图时都对整个播放器进行了初始化和销毁，仅适合于播放前对首帧截图。
