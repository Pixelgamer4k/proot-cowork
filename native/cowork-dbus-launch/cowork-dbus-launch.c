/*
 * Minimal dbus-launch replacement for proot guests.
 * GLib cannot posix_spawn bash scripts under proot ("Function not implemented").
 */
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

static const char *runtime_dir(void)
{
    const char *runtime = getenv("XDG_RUNTIME_DIR");
    return runtime && runtime[0] ? runtime : "/tmp/cowork-runtime";
}

static int bus_socket_exists(const char *runtime)
{
    char path[512];
    struct stat st;

    snprintf(path, sizeof(path), "%s/bus", runtime);
    return stat(path, &st) == 0 && S_ISSOCK(st.st_mode);
}

static int start_bus(char *addr_out, size_t addr_len)
{
    const char *runtime = runtime_dir();
    char address[512];

    snprintf(address, sizeof(address), "unix:path=%s/bus", runtime);
    if (getenv("DBUS_SESSION_BUS_ADDRESS")) {
        snprintf(addr_out, addr_len, "%s", getenv("DBUS_SESSION_BUS_ADDRESS"));
        return 0;
    }
    if (bus_socket_exists(runtime)) {
        setenv("DBUS_SESSION_BUS_ADDRESS", address, 1);
        snprintf(addr_out, addr_len, "%s", address);
        return 0;
    }

    mkdir(runtime, 0700);
    chmod(runtime, 0700);

    pid_t pid = fork();
    if (pid < 0) {
        return -1;
    }
    if (pid == 0) {
        char addr_arg[512];
        snprintf(addr_arg, sizeof(addr_arg), "--address=%s", address);
        char *argv[] = {
            "dbus-daemon",
            "--session",
            "--nopidfile",
            addr_arg,
            NULL,
        };
        execvp("dbus-daemon", argv);
        _exit(127);
    }

    for (int i = 0; i < 50; i++) {
        if (bus_socket_exists(runtime)) {
            setenv("DBUS_SESSION_BUS_ADDRESS", address, 1);
            snprintf(addr_out, addr_len, "%s", address);
            return 0;
        }
        usleep(100000);
    }
    return -1;
}

static int is_autolaunch_flag(const char *arg)
{
    return arg && (
        strcmp(arg, "--autolaunch") == 0 ||
        strncmp(arg, "--autolaunch=", 13) == 0 ||
        strcmp(arg, "--binary-syntax") == 0 ||
        strcmp(arg, "--csh-syntax") == 0);
}

int main(int argc, char **argv)
{
    char address[512];
    int argi = 1;

    if (argc > 1 && strcmp(argv[1], "--sh-syntax") == 0) {
        if (start_bus(address, sizeof(address)) != 0) {
            return 1;
        }
        printf("DBUS_SESSION_BUS_ADDRESS='%s'; export DBUS_SESSION_BUS_ADDRESS;\n", address);
        return 0;
    }

    if (argc > 1 && is_autolaunch_flag(argv[1])) {
        return start_bus(address, sizeof(address)) == 0 ? 0 : 1;
    }

    if (start_bus(address, sizeof(address)) != 0) {
        return 1;
    }

    if (argc > argi && strcmp(argv[argi], "--exit-with-session") == 0) {
        argi++;
    }
    if (argc > argi) {
        execvp(argv[argi], argv + argi);
        return 127;
    }
    return 0;
}
