#include <unistd.h>
#include <string.h>

int main(int argc, char **argv) {
    int i;
    for (i = 1; i < argc; i++) {
        if (strncmp(argv[i], "/usr/libexec/glycin-loaders/", 28) == 0)
            execv(argv[i], argv + i);
    }
    for (i = argc - 1; i >= 1; i--) {
        if (argv[i][0] == '/' && access(argv[i], X_OK) == 0)
            execv(argv[i], argv + i);
    }
    for (i = 1; i < argc; i++) {
        if (!strcmp(argv[i], "--") && i + 1 < argc)
            execv(argv[i + 1], argv + i + 1);
    }
    return 127;
}
