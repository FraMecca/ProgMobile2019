#include <stdio.h>
#include <string.h>
#include "transcoding.h"

long get_file_size(FILE *fp) {
    long size;
    fseek(fp, 0, SEEK_END);
    size = ftell(fp);
    fseek(fp, 0, SEEK_SET);

    return size;
}

size_t trans(uint8_t *inbuf, size_t file_size, uint8_t **outbuf, size_t start_time, size_t end_time)
{
        BufferData src_buf;
        src_buf.size = file_size;
        src_buf.buf = inbuf;

        BufferData dst_buf;
        TranscodingArgs args;
        args.sample_rate = 48000;
        args.bit_rate = 32000;

        int i = 0;
        int dot = 0;
        char format_name[32] = "mp3";
        args.format_name = format_name;

        int out_bit_rate; 
        float out_duration;
        transcoding(&dst_buf, &out_bit_rate, &out_duration, args, src_buf, start_time, end_time);

        printf("out bit rate: %d\n", out_bit_rate);
        printf("out duration: %f\n", out_duration);

        size_t sz = dst_buf.size;
        *outbuf = (uint8_t *) calloc(sz, sizeof(uint8_t));
        memcpy(*outbuf, dst_buf.buf, sz*sizeof(uint8_t));
        return dst_buf.size;
}

#ifdef MAIN
int main(int argc, char **argv)
{
    if (argc < 3) {
        fprintf(stderr, "Usage: %s <input file> <output file>\n", argv[0]);
        return 1;
    }
    else {
        FILE *src_file = fopen(argv[1], "rb");
        long file_size = get_file_size(src_file);
        uint8_t *inbuf = (uint8_t *)malloc(file_size);
        fread(inbuf, file_size, 1, src_file);
        fclose(src_file);

        uint8_t *outbuf;
        size_t sz = trans(inbuf, file_size, &outbuf, 0, 10000000);

        FILE *dst_file = fopen(argv[2], "wb");
        fwrite(outbuf, sz, 1, dst_file);
        fclose(dst_file);

        free(inbuf);
        free(outbuf);

        return 0;
    }
}
#endif
