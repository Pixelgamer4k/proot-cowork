/*
 * Android + proot link2symlink often leaves st_nlink >= 2 while link(2)
 * still returns ENOSYS. Xvfb treats that as fatal when creating .X*-lock.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdio.h>
#include <sys/stat.h>
#include <unistd.h>

static int (*real_link)(const char *, const char *);
static int (*real_linkat)(int, const char *, int, const char *, int);

static int link_succeeded(const char *path)
{
    struct stat st;
    if (stat(path, &st) != 0) {
        return 0;
    }
    return st.st_nlink >= 2;
}

static int emulate_link(const char *oldpath, const char *newpath)
{
    if (!real_link) {
        real_link = dlsym(RTLD_NEXT, "link");
    }
    if (!real_link) {
        errno = ENOSYS;
        return -1;
    }

    int r = real_link(oldpath, newpath);
    if (r == 0) {
        return 0;
    }

    if (errno == ENOSYS || errno == EPERM || errno == EACCES) {
        if (link_succeeded(oldpath)) {
            return 0;
        }
        if (errno != EEXIST) {
            unlink(newpath);
        }
        if (symlink(oldpath, newpath) == 0) {
            return 0;
        }
    }

    return r;
}

int link(const char *oldpath, const char *newpath)
{
    return emulate_link(oldpath, newpath);
}

int linkat(int olddirfd, const char *oldpath, int newdirfd, const char *newpath, int flags)
{
    if (!real_linkat) {
        real_linkat = dlsym(RTLD_NEXT, "linkat");
    }
    if (!real_linkat) {
        errno = ENOSYS;
        return -1;
    }

    int r = real_linkat(olddirfd, oldpath, newdirfd, newpath, flags);
    if (r == 0) {
        return 0;
    }

    if (errno == ENOSYS || errno == EPERM || errno == EACCES) {
        char oldbuf[PATH_MAX];
        char newbuf[PATH_MAX];

        if (oldpath && oldpath[0] == '/') {
            if (link_succeeded(oldpath)) {
                return 0;
            }
            if (symlink(oldpath, newpath) == 0) {
                return 0;
            }
        } else if (olddirfd == AT_FDCWD && newdirfd == AT_FDCWD) {
            snprintf(oldbuf, sizeof(oldbuf), "%s", oldpath ? oldpath : "");
            snprintf(newbuf, sizeof(newbuf), "%s", newpath ? newpath : "");
            return emulate_link(oldbuf, newbuf);
        }
    }

    return r;
}
