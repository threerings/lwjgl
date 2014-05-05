#include "Windows.h"
#include <malloc.h>
#include "org_lwjgl_opengl_WindowsIME.h"

#define STRINGRETURNER_SIG "org/lwjgl/opengl/WindowsIME$StringReturner"

jobject getStringReturner (JNIEnv *env, jint result, jstring buf)
{
    jclass clazz = (*env)->FindClass(env, STRINGRETURNER_SIG);
    jmethodID constructor = (*env)->GetMethodID(env, clazz, "<init>", "()V");
    jobject object = (*env)->NewObject(env, clazz, constructor);

    (*env)->SetIntField(env, object, (*env)->GetFieldID(env, clazz, "result", "I"), result);
    (*env)->SetObjectField(env, object, (*env)->GetFieldID(env, clazz, "buf", "Ljava/lang/String;"), buf);

    return object;
}

JNIEXPORT jlong JNICALL Java_org_lwjgl_opengl_WindowsIME_CreateContext (
        JNIEnv *env, jclass unused)
{
    return (intptr_t)ImmCreateContext();
}

JNIEXPORT jboolean JNICALL Java_org_lwjgl_opengl_WindowsIME_DestroyContext (
        JNIEnv *env, jclass unused, jlong himc_int)
{
    HIMC himc = (HIMC)(INT_PTR)himc_int;
    return ImmDestroyContext(himc);
}

JNIEXPORT jlong JNICALL Java_org_lwjgl_opengl_WindowsIME_AssociateContext (
        JNIEnv *env, jclass unused, jlong hwnd_int, jlong himc_int)
{
    HWND hwnd = (HWND)(INT_PTR)hwnd_int;
    HIMC himc = (HIMC)(INT_PTR)himc_int;
    return (intptr_t)ImmAssociateContext(hwnd, himc);
}

JNIEXPORT jobject JNICALL Java_org_lwjgl_opengl_WindowsIME_ImmGetCompositionString (
        JNIEnv *env, jclass unused, jlong himc_int, jlong dw_index_int)
{
    HIMC himc = (HIMC)(INT_PTR)himc_int;
    DWORD dwIndex = (DWORD)dw_index_int;
    DWORD result = ImmGetCompositionStringW(himc, dwIndex, (LPVOID)NULL, 0l);
    LPWSTR lpstr;
    jstring str;

    if (dwIndex == GCS_CURSORPOS || dwIndex == GCS_DELTASTART ||
            result == IMM_ERROR_NODATA || result == IMM_ERROR_GENERAL) {
        return getStringReturner(env, (jint)result, (jstring)NULL);
    }

    lpstr = (LPWSTR)malloc(result);
    result = ImmGetCompositionStringW(himc, dwIndex, lpstr, result);
    str = (*env)->NewString(env, lpstr, result/sizeof(WCHAR));
    free(lpstr);
    return getStringReturner(env, (jint)result, str);
}
