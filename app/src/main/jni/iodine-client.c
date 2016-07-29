#include <assert.h>
#include <string.h>
#include <errno.h>
#include <stdlib.h>
#include <time.h>
#include <unistd.h>
#include <netdb.h>

#include <android/log.h>
#include <jni.h>

#include <sys/system_properties.h>
#include <stdio.h>
#include <pthread.h>

#include "iodine/src/common.h"
#include "iodine/src/tun.h"
#include "iodine/src/client.h"
#include "iodine/src/util.h"

#define IODINE_CLIENT_CLASS "space/potatofrom/aeiodine/IodineClient"
#define IODINE_CLIENT_CLASS_LOG_CALLBACK "log_callback"
#define IODINE_CLIENT_CLASS_LOG_CALLBACK_SIG "(Ljava/lang/String;)V"

static int dns_fd;

static JavaVM *javaVM = 0;
static void* env = 0;
static jclass iodineClass = 0;

int start_logger();

JNIEXPORT jint JNI_OnLoad(JavaVM* jvm, void* reserved) {
    javaVM = jvm;

    JNIEnv *env;
    jint rs = (*javaVM)->AttachCurrentThread(javaVM, &env, NULL);
    assert (rs == JNI_OK);

    jclass localRefCls = (*env)->FindClass(env, IODINE_CLIENT_CLASS);
    if (localRefCls == NULL) {
        printf("Can't find class %s", IODINE_CLIENT_CLASS);
    }

    //cache the EyeSightCore ref as global
    /* Create a global reference */
    iodineClass = (jclass*)(*env)->NewGlobalRef(env, localRefCls);

    /* The local reference is no longer useful */
    (*env)->DeleteLocalRef(env, localRefCls);

    /* Is the global reference created successfully? */
    if (iodineClass == NULL) {
        printf("Error - class is still null when it is suppose to be global");
        /* out of memory exception thrown */
    }

    start_logger();

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

    //if ((*javaVM)->AttachCurrentThread(javaVM, &env, 0) < 0) {
    //    __android_log_print(ANDROID_LOG_ERROR, "iodine", "Failed to get the environment using AttachCurrentThread()");
    //    return;
    //}

    //jclass clazz = (*env)->FindClass(env, IODINE_CLIENT_CLASS);
    //if (!clazz) {
    //    __android_log_print(ANDROID_LOG_ERROR, "iodine", "Native Debug: clazz == null");
    //    return;
    //}

    jmethodID log_callback = (*env)->GetStaticMethodID(env, iodineClass,
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
    (*env)->CallStaticVoidMethod(env, iodineClass, log_callback, message);

    (*env)->DeleteLocalRef(env,message);
    free(msg);
}

static int pfd[2];
static pthread_t thr;

static void *thread_func(void* x)
{
    ssize_t rdsz;
    char buf[128];
    while((rdsz = read(pfd[0], buf, sizeof buf - 1)) > 0) {
        buf[rdsz] = 0;  /* add null-terminator */
        android_log_callback(buf);
    }
    return 0;
}

int start_logger()
{
    /* make stdout line-buffered and stderr unbuffered */
    setvbuf(stdout, 0, _IOLBF, 0);
    setvbuf(stderr, 0, _IONBF, 0);

    /* create the pipe and redirect stdout and stderr */
    pipe(pfd);
    dup2(pfd[1], STDOUT_FILENO);
    dup2(pfd[1], STDERR_FILENO);

    /* spawn the logging thread */
    if(pthread_create(&thr, 0, thread_func, 0) == -1)
        return -1;
    pthread_detach(thr);
    return 0;
}

JNIEXPORT jint JNICALL Java_space_potatofrom_aeiodine_IodineClient_getDnsFd(
		JNIEnv *env, jclass klass) {
	return dns_fd;
}

JNIEXPORT jint JNICALL Java_space_potatofrom_aeiodine_IodineClient_connect(
		JNIEnv *env, jclass klass, jstring j_nameserv_addr, jstring j_topdomain, jboolean j_raw_mode, jboolean j_lazy_mode,
		jstring j_password, jint j_request_hostname_size, jint j_response_fragment_size) {
	const char *__p_nameserv_addr = (*env)->GetStringUTFChars(env,
			j_nameserv_addr, NULL);
	char *p_nameserv_addr = strdup(__p_nameserv_addr);
    struct socket *nameserv_addrs = malloc(sizeof(struct socket) * 1);
    struct sockaddr_storage p_nameserv;
	int p_nameserv_len = get_addr(p_nameserv_addr, 53, AF_INET, 0, &p_nameserv);
	(*env)->ReleaseStringUTFChars(env, j_nameserv_addr, __p_nameserv_addr);
    nameserv_addrs[0].length = p_nameserv_len;
    memcpy(&nameserv_addrs[0].addr, &p_nameserv, sizeof(struct sockaddr_storage));

	const char *__p_topdomain = (*env)->GetStringUTFChars(env, j_topdomain,
			NULL);
	const char *p_topdomain = strdup(__p_topdomain);
	__android_log_print(ANDROID_LOG_ERROR, "iodine", "Topdomain from vm: %s", p_topdomain);

	(*env)->ReleaseStringUTFChars(env, j_topdomain, __p_topdomain);
	__android_log_print(ANDROID_LOG_ERROR, "iodine", "Topdomain from vm: %s", p_topdomain);

	const char *p_password = (*env)->GetStringUTFChars(env, j_password, NULL);
	char passwordField[33];
	memset(passwordField, 0, 33);
	strncpy(passwordField, p_password, 32);
	(*env)->ReleaseStringUTFChars(env, j_password, p_password);

	tun_config_android.request_disconnect = 0;

	int selecttimeout = 2; // original: 4
	int lazy_mode;
	int hostname_maxlen = j_request_hostname_size;
	int raw_mode;
	int autodetect_frag_size = j_response_fragment_size == 0 ? 1 : 0;
	int max_downstream_frag_size = j_response_fragment_size;

	if (j_raw_mode) {
		raw_mode = 1;
	} else {
		raw_mode = 0;
	}

	if (j_lazy_mode) {
		lazy_mode = 1;
	} else {
		lazy_mode = 0;
	}

	srand((unsigned) time(NULL));
	client_init();
	client_set_nameservers(nameserv_addrs, 1);
	//client_set_dnstimeout(selecttimeout);
	client_set_lazymode(lazy_mode);
	client_set_topdomain(p_topdomain);
	client_set_hostname_maxlen(hostname_maxlen);
	client_set_password(passwordField);

	if ((dns_fd = open_dns_from_host(NULL, 0, AF_INET, AI_PASSIVE)) == -1) {
		printf("Could not open dns socket: %s", strerror(errno));
		return 1;
	}

	if (client_handshake(dns_fd, raw_mode, autodetect_frag_size,
			max_downstream_frag_size)) {
		printf("Handshake unsuccessful: %s", strerror(errno));
		close(dns_fd);
		return 2;
	}

	if (client_get_conn() == CONN_RAW_UDP) {
		printf("Sending raw traffic directly to %s\n", client_get_raw_addr());
	}

	printf("Handshake successful, leave native code");
	return 0;
}


static int tunnel_continue_cb() {
	return ! tun_config_android.request_disconnect;
}

JNIEXPORT void JNICALL Java_space_potatofrom_aeiodine_IodineClient_tunnelInterrupt(JNIEnv *env,
		jclass klass) {
	tun_config_android.request_disconnect = 1;
	client_stop();
}

JNIEXPORT jint JNICALL Java_space_potatofrom_aeiodine_IodineClient_tunnel(JNIEnv *env,
		jclass klass, jint tun_fd) {

    printf("Run client_tunnel_cb");
	int retval = client_tunnel(tun_fd, dns_fd);

	close(dns_fd);
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