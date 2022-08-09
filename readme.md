Where to get binary files
=========================== 
1) Libwebp
    - [Webp Project](https://developers.google.com/speed/webp/docs/precompiled "Webp Project")


2) FFMpeg
    - [FFMpeg from GyanD](https://github.com/GyanD/codexffmpeg/releases "FFMpeg from GyanD")
    - [FFMpeg from BtbN](https://github.com/BtbN/FFmpeg-Builds/releases "FFMpeg from BtbN")

webpmux, anim_dump, ffmpeg binaries are required.

(Not inclueded this project.)

How to use
=========================== 
    java kr.shar.webp2mp4.Converter -s webp/*.webp -p pattern -r repeat -b bin/ -d false
        1) essential
            -s : Source file(s). (e.g. webp/001.webp, webp/*.webp)

        2) optional
            -p, -r : If patterned target file exists, increase repeat count.
                 (p is matched string or regular expression)
                 (r is to be repeat count)
            -b : ffmpeg and webp binaries' path. (e.g. bin/ /var/usr/bin/)
            -d : Delete temporary files after job finished.
                 (true / false, default is true)

This project referred to [https://github.com/kippler/webp2mp4](https://github.com/kippler/webp2mp4)