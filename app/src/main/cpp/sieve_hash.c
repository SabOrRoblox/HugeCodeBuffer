#include <jni.h>
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include <stdlib.h>

#define rotl64(x, k) __builtin_rotateleft64(x, k)
#define write64(p, v) __builtin_memcpy((p), &(v), 8)

static const uint64_t RC[48] = {
    0x807E12EBD0C1C722ULL, 0x2ffED75D38D5985DULL, 0x098EBAEBCEB90079ULL, 0x34572C6540f7D98AULL,
    0x3EBAAfEB8631D5fEULL, 0xB6851E4A94A555B8ULL, 0x6228AC853E28C9E0ULL, 0xf852C525D14544D6ULL,
    0xE1DA288C443243ffULL, 0x532CD91C59EfDBA4ULL, 0x3AAf1B296B6EB19CULL, 0x519537E05083A87EULL,
    0x534f98E1D915CE54ULL, 0x1809D40101B43DECULL, 0xBEC259C2fA6CC4DBULL, 0xD1C07C8821fD24f3ULL,
    0x906A0B3516EBDfEDULL, 0x527EC181B6f4262EULL, 0x14C61BEDB6E73DA8ULL, 0x8Df39307f30932C9ULL,
    0x4f20CCE565fABD22ULL, 0x13B3330A1D03A36DULL, 0x72D65D923f65157CULL, 0x03f3fCf84CB9DE88ULL,
    0x65656C7DfD6B40DfULL, 0x557AC5B9583AfB9BULL, 0x8CAAfCC286856873ULL, 0xC851E7B00525A417ULL,
    0x525E4152288C06B3ULL, 0xC07801DAA9C8BBCDULL, 0x6A0D49BD71CEEEA0ULL, 0x9f6D2EEBA8083D70ULL,
    0x74DEf30217D4AE6CULL, 0xA2574A46BA336ff3ULL, 0xCD3C5112DABC63D7ULL, 0x9654fA88147B6543ULL,
    0xCB9A0EC34E508C9AULL, 0x7fA7054A277D4384ULL, 0xE51381CD798C89D1ULL, 0x553A44EBA529Cf01ULL,
    0xD3A7612DAE23E5EEULL, 0x021E90EB05C12564ULL, 0x4BfE008458D5109EULL, 0xEE8352fDE10A5fB1ULL,
    0x12f9B04f39CD3191ULL, 0x856B17BAB29B16ACULL, 0x7AC1B72C1902E4A4ULL, 0x0EA5092901AAC84BULL
};

static const uint8_t ROT[32] = {
     1,  3,  6, 10, 15, 21, 28, 36,
    45, 55,  2, 14, 27, 41, 56,  8,
    25, 43, 62, 18, 39, 61, 20, 44,
     5, 31, 58, 22, 51, 17, 48, 16
};

static const int TAU[36] = {
     0,  8, 16, 18, 26, 34,
    19, 27, 35,  1,  9, 17,
     2, 10, 12, 20, 28, 30,
    21, 29, 31,  3, 11, 13,
     4,  6, 14, 22, 24, 32,
    23, 25, 33,  5,  7, 15
};

static const uint64_t IV[6] = {
    0x513E9B51ECE3ff6DULL, 0x7f3AE42C6499533BULL, 0xC4355174182950f0ULL,
    0xABACf55fA5814D80ULL, 0x9324994B32D94A10ULL, 0x639E18C984569CB8ULL
};

#define RATE      1024
#define LANES     36

typedef struct {
    uint64_t A[6][6];
    uint8_t  buf[RATE];
    size_t   len;
} sieve_state;

static void sieve_init(sieve_state *st) {
    memset(st, 0, sizeof(sieve_state));
    for (int x = 0; x < 6; x++)
        for (int y = 0; y < 6; y++)
            st->A[x][y] = IV[(x*6 + y) % 6] ^ (x*6 + y);
}

static void sieve_round(uint64_t A[6][6], uint64_t rc, uint64_t dom) {
    int x, y;
    uint64_t C[6];
    uint64_t G[6][6];
    uint64_t T[6][6];
    
    for (y = 0; y < 6; y++)
        A[0][y] ^= rc ^ (dom ^ y);
    
    for (x = 0; x < 6; x++)
        C[x] = A[x][0] ^ A[x][1] ^ A[x][2] ^ A[x][3] ^ A[x][4] ^ A[x][5];
    
    for (x = 0; x < 6; x++)
        for (y = 0; y < 6; y++)
            A[x][y] ^= rotl64(C[(x+5)%6], ROT[(dom + x + y) % 32])
                    ^ rotl64(C[(x+1)%6], ROT[(dom + x + y + 17) % 32]);
    
    memcpy(G, A, sizeof(G));
    for (y = 0; y < 6; y++)
        for (x = 0; x < 6; x++) {
            uint64_t a = G[x][y], b = G[(x+1)%6][y], c = G[(x+2)%6][y];
            G[x][y] = A[x][y] ^ ((a + b) ^ (a & c));
        }
    
    for (x = 0; x < 6; x++)
        for (y = 0; y < 6; y++) {
            uint64_t a = G[x][y], b = G[(x+1)%6][y], c = G[x][(y+1)%6];
            uint64_t amt = (((a ^ c) & 0x3F) ^ (((a ^ c) >> 16) & 0x3F)
                         ^ (((a ^ c) >> 32) & 0x3F) ^ (((a ^ c) >> 48) & 0x3F)) + 1;
            A[x][y] = rotl64(a + b, amt) ^ c;
        }
    
    for (x = 0; x < 6; x++)
        for (y = 0; y < 6; y++) {
            int dst = TAU[x*6 + y];
            T[dst/6][dst%6] = A[x][y] ^ ((A[(x+1)%6][y] & A[x][(y-1)%6])+1);
        }
    memcpy(A, T, sizeof(T));
}

static void sieve_absorb(sieve_state *st, const uint8_t *block) {
    const uint64_t *p = (const uint64_t*)block;
    for (int i = 0; i < 16; i++)
        ((uint64_t*)st->A)[i] ^= p[i];
    for (int i = 0; i < 48; i++)
        sieve_round(st->A, RC[i], i);
}

static void sieve_output(sieve_state *st, uint8_t *out, size_t out_len) {
    size_t pos = 0;
    size_t lane = 0;
    size_t off = 0;
    while (pos < out_len) {
        if (lane >= LANES) {
            for (int i = 0; i < 48; i++)
                sieve_round(st->A, RC[i], i + 48);
            lane = 0;
            off = 0;
        }
        size_t avail = 8 - off;
        size_t copy = (out_len - pos < avail) ? out_len - pos : avail;
        memcpy(out + pos, (uint8_t*)&((uint64_t*)st->A)[lane] + off, copy);
        pos += copy;
        off += copy;
        if (off >= 8) { lane++; off = 0; }
    }
}

static void sieve_update(sieve_state *st, const uint8_t *data, size_t len) {
    if (st->len > 0) {
        size_t fill = RATE - st->len, take = (len < fill) ? len : fill;
        memcpy(st->buf + st->len, data, take);
        st->len += take; data += take; len -= take;
        if (st->len == RATE) { sieve_absorb(st, st->buf); st->len = 0; }
    }
    while (len >= RATE) { sieve_absorb(st, data); data += RATE; len -= RATE; }
    if (len > 0) { memcpy(st->buf, data, len); st->len = len; }
}

static void sieve_final(sieve_state *st, uint8_t *out, size_t out_len, uint64_t bits_lo, uint64_t bits_hi) {
    st->buf[st->len++] = 0x80;
    if (st->len > RATE - 18) {
        memset(st->buf + st->len, 0, RATE - st->len);
        sieve_absorb(st, st->buf);
        st->len = 0;
    }
    memset(st->buf + st->len, 0, RATE - 18 - st->len);
    st->buf[RATE - 18] = 0x01;
    st->buf[RATE - 17] = 0x00;
    write64(st->buf + RATE - 16, bits_lo);
    write64(st->buf + RATE - 8,  bits_hi);
    sieve_absorb(st, st->buf);
    sieve_output(st, out, out_len);
    memset(st, 0, sizeof(sieve_state));
}

void sieve_hash_256(const uint8_t *data, size_t len, uint8_t out[32]) {
    sieve_state st;
    uint64_t bits = (uint64_t)len * 8;
    sieve_init(&st);
    sieve_update(&st, data, len);
    sieve_final(&st, out, 32, bits, 0);
}

JNIEXPORT jbyteArray JNICALL
Java_com_hugecode_buffer_CryptoManager_sieveHash256(JNIEnv *env, jobject thiz, jbyteArray data) {
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    
    uint8_t out[32];
    sieve_hash_256((uint8_t*)bytes, len, out);
    
    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    
    jbyteArray result = (*env)->NewByteArray(env, 32);
    (*env)->SetByteArrayRegion(env, result, 0, 32, (jbyte*)out);
    
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_hugecode_buffer_CryptoManager_sieveHash512(JNIEnv *env, jobject thiz, jbyteArray data) {
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    
    uint8_t out[64];
    sieve_state st;
    uint64_t bits = (uint64_t)len * 8;
    sieve_init(&st);
    sieve_update(&st, (uint8_t*)bytes, len);
    sieve_final(&st, out, 64, bits, 0);
    
    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    
    jbyteArray result = (*env)->NewByteArray(env, 64);
    (*env)->SetByteArrayRegion(env, result, 0, 64, (jbyte*)out);
    
    return result;
}
