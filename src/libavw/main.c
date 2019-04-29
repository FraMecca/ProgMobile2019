#include <stdio.h>
#include <stdlib.h>
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>

int main(int argc, char **argv)
{
    // Register all formats and codecs
    av_register_all();
    AVFormatContext *pFormatCtx = avformat_alloc_context();

    // Open video file
    if(av_open_input_file(&pFormatCtx, argv[1], NULL, 0, NULL)!=0)
        return 2; // Couldn't open file

    // Retrieve stream information
    if(av_find_stream_info(pFormatCtx) > 0)
        return 2; // Couldn't find stream information

    // Dump information about file onto standard error
    dump_format(pFormatCtx, 0, argv[1], 0);

    return 0;
}
