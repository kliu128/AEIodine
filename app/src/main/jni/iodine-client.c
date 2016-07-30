#include <assert.h>
#include <string.h>

#include <android/log.h>
#include <jni.h>
#include <stdlib.h>
#include <time.h>

#include <sys/system_properties.h>
#include <stdio.h>

#include "iodine/src/iodine.h"
#include "iodine/src/client.h"
#include "iodine/src/tun.h"

#define IODINE_CLIENT_CLASS "space/potatofrom/aeiodine/IodineClient"
#define IODINE_CLIENT_CLASS_LOG_CALLBACK "log_callback"
#define IODINE_CLIENT_CLASS_LOG_CALLBACK_SIG "(Ljava/lang/String;)V"

static int dns_fd;

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
	return dns_fd;
}

JNIEXPORT jint JNICALL Java_space_potatofrom_aeiodine_IodineClient_connect(
		JNIEnv *env, jclass klass, jstring j_nameserv_addr, jstring j_topdomain, jboolean j_lazy_mode,
		jstring j_password, jint j_request_hostname_size, jint j_response_fragment_size) {
	char request_hostname_size_str[15];
	snprintf(request_hostname_size_str, 15, "%d", j_request_hostname_size);
	char response_fragment_size_str[15];
	snprintf(response_fragment_size_str, 15, "%d", j_response_fragment_size);
    const char *password = (*env)->GetStringUTFChars(env, j_password, JNI_FALSE);
    const char *nameserv_addr = (*env)->GetStringUTFChars(env, j_nameserv_addr, JNI_FALSE);
    const char *topdomain = (*env)->GetStringUTFChars(env, j_topdomain, JNI_FALSE);

	const char* args[] = {
	        "iodine",
			"-L", j_lazy_mode ? "1" : "0",
			"-P", password,
			"-M", request_hostname_size_str,
			//"-m", response_fragment_size_str,
			"-r",
			topdomain, nameserv_addr
	};

	int retVal = main(10, args);

    (*env)->ReleaseStringUTFChars(env, j_password, password);
    (*env)->ReleaseStringUTFChars(env, j_nameserv_addr, nameserv_addr);
    (*env)->ReleaseStringUTFChars(env, j_topdomain, topdomain);

    return retVal;
}

JNIEXPORT void JNICALL Java_space_potatofrom_aeiodine_IodineClient_tunnelInterrupt(JNIEnv *env,
		jclass klass) {
	tun_config_android.request_disconnect = 1;
	client_stop();
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