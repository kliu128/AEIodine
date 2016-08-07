#include <assert.h>
#include <string.h>

#include <android/log.h>
#include <jni.h>
#include <stdlib.h>
#include <time.h>

#include <sys/system_properties.h>
#include <stdio.h>
#include <netdb.h>
#include <errno.h>
#include <unistd.h>

#include "iodine/src/client.h"
#include "iodine/src/tun.h"
#include "iodine/src/common.h"
#include "iodine/src/base32.h"

#define IODINE_CLIENT_CLASS "space/potatofrom/aeiodine/IodineClient"
#define IODINE_CLIENT_CLASS_LOG_CALLBACK "log_callback"
#define IODINE_CLIENT_CLASS_LOG_CALLBACK_SIG "(Ljava/lang/String;)V"

static JavaVM *javaVM = 0;

JNIEXPORT jint JNI_OnLoad(JavaVM* jvm, void* reserved) {
    javaVM = jvm;

    JNIEnv *env;
    jint rs = (*javaVM)->AttachCurrentThread(javaVM, &env, NULL);
    assert (rs == JNI_OK);

    return JNI_VERSION_1_6;
}

void android_log_callback(const char *msg_) {
    int i;
    char *msg = strdup(msg_);

    if (!msg) {
        return;
    }

    JNIEnv *env;
    jint rs = (*javaVM)->AttachCurrentThread(javaVM, &env, NULL);
    assert (rs == JNI_OK);
    if (!env) {
        __android_log_print(ANDROID_LOG_ERROR, "iodine", "Native Debug: env == null");
        return;
    }

    if ((*javaVM)->AttachCurrentThread(javaVM, &env, 0) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "iodine", "Failed to get the environment using AttachCurrentThread()");
        return;
    }

    jclass clazz = (*env)->FindClass(env, IODINE_CLIENT_CLASS);
    if (!clazz) {
        __android_log_print(ANDROID_LOG_ERROR, "iodine", "Native Debug: clazz == null");
        return;
    }

    jmethodID log_callback = (*env)->GetStaticMethodID(env, clazz,
        IODINE_CLIENT_CLASS_LOG_CALLBACK, IODINE_CLIENT_CLASS_LOG_CALLBACK_SIG);
    if (!log_callback) {
        __android_log_print(ANDROID_LOG_ERROR, "iodine", "Native Debug: log_callback == null");
        return;
    }

    jstring message = (*env)->NewStringUTF(env, msg);
    if (!message) {
        __android_log_print(ANDROID_LOG_ERROR, "iodine", "Native Debug: message == null");
        return;
    }
    (*env)->CallStaticVoidMethod(env, clazz, log_callback, message);

    (*env)->DeleteLocalRef(env,message);
    free(msg);
}

JNIEXPORT jint JNICALL Java_space_potatofrom_aeiodine_IodineClient_getDnsFd(
		JNIEnv *env, jclass klass) {
	return this.dns_fd;
}

JNIEXPORT jint JNICALL Java_space_potatofrom_aeiodine_IodineClient_connect(
		JNIEnv *env, jclass klass, jstring j_nameserv_addr, jstring j_topdomain, jboolean j_lazy_mode,
		jstring j_password, jint j_request_hostname_size, jint j_response_fragment_size) {
	char request_hostname_size_str[15];
	snprintf(request_hostname_size_str, 15, "%d", j_request_hostname_size);
	char response_fragment_size_str[15];
	snprintf(response_fragment_size_str, 15, "%d", j_response_fragment_size);
    const char *password = (*env)->GetStringUTFChars(env, j_password, JNI_FALSE);
    const char *nameserv_addr_str = (*env)->GetStringUTFChars(env, j_nameserv_addr, JNI_FALSE);
    const char *topdomain = (*env)->GetStringUTFChars(env, j_topdomain, JNI_FALSE);
    struct sockaddr_storage nameservaddr_sockaddr;
    int nameservaddr_len = get_addr(nameserv_addr_str, DNS_PORT, AF_UNSPEC, 0, &nameservaddr_sockaddr);
    if (nameservaddr_len < 0) {
        errx(1, "Cannot lookup nameserver '%s': %s ",
             nameserv_addr_str, gai_strerror(nameservaddr_len));
    }
    this.max_timeout_ms = 5000;
    this.send_interval_ms = 0;
    this.server_timeout_ms = 4000;
    this.downstream_timeout_ms = 2000;
    this.autodetect_server_timeout = 1;
    this.dataenc = &base32_encoder;
    this.autodetect_frag_size = 1;
    this.max_downstream_frag_size = MAX_FRAGSIZE;
    this.compression_down = 1;
    this.compression_up = 1;
    this.windowsize_up = 8;
    this.windowsize_down = 8;
    this.hostname_maxlen = (int)j_request_hostname_size;
    this.downenc = ' ';
    this.do_qtype = T_UNSET;
    this.conn = CONN_DNS_NULL;
	this.send_ping_soon = 1;
	this.maxfragsize_up = 100;
	this.next_downstream_ack = -1;
	this.num_immediate = 1;
	this.rtt_total_ms = 200;
	this.remote_forward_addr.ss_family = AF_UNSPEC;
    this.lazymode = j_lazy_mode;
    this.topdomain = (char *) topdomain;
    strncpy(this.password, password, sizeof(this.password));
    const char *a[1];
    a[0] = nameserv_addr_str;
    this.nameserv_hosts = (char **) a;
    this.nameserv_hosts_len = 1;
    this.nameserv_addrs = malloc(sizeof(struct sockaddr_storage) * this.nameserv_hosts_len);
    memcpy(&this.nameserv_addrs[0], &nameservaddr_sockaddr, sizeof(struct sockaddr_storage));
    this.nameserv_addrs_len = 1;

    this.rand_seed = (uint16_t) rand();
    this.chunkid = (uint16_t) rand();
    this.running = 1;

    if ((this.dns_fd = open_dns_from_host(NULL, 0, nameservaddr_sockaddr.ss_family, AI_PASSIVE)) < 0) {
        printf("Could not open DNS socket: %s\n", strerror(errno));
        return 1;
    }
    if (client_handshake()) {
        printf("Handshake unsuccessful: %s\n", strerror(errno));
        close(this.dns_fd);
        return 2;
    }

    (*env)->ReleaseStringUTFChars(env, j_password, password);
    (*env)->ReleaseStringUTFChars(env, j_nameserv_addr, nameserv_addr_str);
    (*env)->ReleaseStringUTFChars(env, j_topdomain, topdomain);

    printf("Success?");

    return 0;
}

JNIEXPORT void JNICALL Java_space_potatofrom_aeiodine_IodineClient_tunnelInterrupt(JNIEnv *env,
		jclass klass) {
	tun_config_android.request_disconnect = 1;
}

JNIEXPORT jint JNICALL Java_space_potatofrom_aeiodine_IodineClient_tunnel(
        JNIEnv *env, jclass klass, jint tun_fd) {
    printf("Run client_tunnel");
    int retval = client_tunnel();

    close(this.dns_fd);
    close(tun_fd);
    return retval;
}

// String IodineClient.getIp()
JNIEXPORT jstring JNICALL Java_space_potatofrom_aeiodine_IodineClient_getIp(
		JNIEnv *env, jclass klass) {
	return (*env)->NewStringUTF(env, tun_config_android.ip);
}

// String IodineClient.getRemoteIp()
JNIEXPORT jstring JNICALL Java_space_potatofrom_aeiodine_IodineClient_getRemoteIp(
		JNIEnv *env, jclass klass) {
	return (*env)->NewStringUTF(env, tun_config_android.remoteip);
}

// int IodineClient.getNetbits()
JNIEXPORT jint JNICALL Java_space_potatofrom_aeiodine_IodineClient_getNetbits(
		JNIEnv *env, jclass klass) {
	return tun_config_android.netbits;
}

// int IodineClient.getMtu()
JNIEXPORT jint JNICALL Java_space_potatofrom_aeiodine_IodineClient_getMtu(JNIEnv *env,
		jclass klass) {
	return tun_config_android.mtu;
}

// String IodineClient.getPropertyNetDns1
JNIEXPORT jstring JNICALL Java_space_potatofrom_aeiodine_IodineClient_getPropertyNetDns1(
		JNIEnv *env, jclass klass) {
	char value[PROP_VALUE_MAX];
	__system_property_get("net.dns1", value);
	return (*env)->NewStringUTF(env, value);
}