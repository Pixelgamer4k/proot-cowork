/*
 * Intercept bwrap spawns from GTK/glycin under proot and exec loaders directly.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <spawn.h>
#include <string.h>
#include <unistd.h>

static int (*real_execve)(const char *, char *const[], char *const[]);
static int (*real_posix_spawn)(pid_t *, const char *,
    const posix_spawn_file_actions_t *, const posix_spawnattr_t *,
    char *const[], char *const[]);
static int (*real_posix_spawnp)(pid_t *, const char *,
    const posix_spawn_file_actions_t *, const posix_spawnattr_t *,
    char *const[], char *const[]);

static int is_bwrap_path(const char *path)
{
    if (!path) {
        return 0;
    }
    return strstr(path, "/bwrap") != NULL || strcmp(path, "bwrap") == 0;
}

static int is_dbus_launch_path(const char *path)
{
    if (!path) {
        return 0;
    }
    return strstr(path, "dbus-launch") != NULL;
}

static const char *find_glycin_loader(char *const argv[])
{
    for (int i = 0; argv && argv[i]; i++) {
        if (strncmp(argv[i], "/usr/libexec/glycin-loaders/", 28) == 0) {
            return argv[i];
        }
    }
    return NULL;
}

static int forward_bwrap(char *const argv[], char *const envp[], const char **out_path)
{
    const char *loader = find_glycin_loader(argv);
    if (loader) {
        for (int i = 0; argv && argv[i]; i++) {
            if (strcmp(argv[i], loader) == 0) {
                *out_path = loader;
                return i;
            }
        }
    }
    for (int i = 0; argv && argv[i]; i++) {
        if (strcmp(argv[i], "--") == 0 && argv[i + 1]) {
            *out_path = argv[i + 1];
            return i + 1;
        }
    }
    for (int i = 0; argv && argv[i]; i++) {
        if (argv[i][0] == '/' && access(argv[i], X_OK) == 0) {
            *out_path = argv[i];
            return i;
        }
    }
    return -1;
}

static int spawn_bwrap(pid_t *pid, const char *path,
    const posix_spawn_file_actions_t *file_actions,
    const posix_spawnattr_t *attrp,
    char *const argv[], char *const envp[],
    int (*spawn_fn)(pid_t *, const char *,
        const posix_spawn_file_actions_t *, const posix_spawnattr_t *,
        char *const[], char *const[]))
{
    const char *target = NULL;
    int idx = forward_bwrap(argv, envp, &target);
    if (idx >= 0 && target) {
        return spawn_fn(pid, target, NULL, NULL, argv + idx, envp);
    }
    if (access("/usr/bin/cowork-bwrap", X_OK) == 0) {
        return spawn_fn(pid, "/usr/bin/cowork-bwrap", NULL, NULL, argv, envp);
    }
    return -1;
}

int execve(const char *pathname, char *const argv[], char *const envp[])
{
    if (!real_execve) {
        real_execve = dlsym(RTLD_NEXT, "execve");
    }
    if (is_dbus_launch_path(pathname)) {
        pathname = "/usr/bin/cowork-dbus-launch";
    }
    if (is_bwrap_path(pathname)) {
        const char *target = NULL;
        int idx = forward_bwrap(argv, envp, &target);
        if (idx >= 0 && target) {
            return real_execve(target, argv + idx, envp);
        }
        if (access("/usr/bin/cowork-bwrap", X_OK) == 0) {
            return real_execve("/usr/bin/cowork-bwrap", argv, envp);
        }
        errno = ENOEXEC;
        return -1;
    }
    return real_execve(pathname, argv, envp);
}

int posix_spawn(pid_t *pid, const char *path,
    const posix_spawn_file_actions_t *file_actions,
    const posix_spawnattr_t *attrp,
    char *const argv[], char *const envp[])
{
    if (!real_posix_spawn) {
        real_posix_spawn = dlsym(RTLD_NEXT, "posix_spawn");
    }
    if (is_dbus_launch_path(path)) {
        path = "/usr/bin/cowork-dbus-launch";
    }
    if (is_bwrap_path(path)) {
        return spawn_bwrap(pid, path, file_actions, attrp, argv, envp, real_posix_spawn);
    }
    return real_posix_spawn(pid, path, file_actions, attrp, argv, envp);
}

int posix_spawnp(pid_t *pid, const char *file,
    const posix_spawn_file_actions_t *file_actions,
    const posix_spawnattr_t *attrp,
    char *const argv[], char *const envp[])
{
    if (!real_posix_spawnp) {
        real_posix_spawnp = dlsym(RTLD_NEXT, "posix_spawnp");
    }
    if (is_dbus_launch_path(file)) {
        file = "/usr/bin/cowork-dbus-launch";
    }
    if (is_bwrap_path(file)) {
        return spawn_bwrap(pid, file, file_actions, attrp, argv, envp, real_posix_spawnp);
    }
    return real_posix_spawnp(pid, file, file_actions, attrp, argv, envp);
}
