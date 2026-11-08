#define _DEFAULT_SOURCE 1

/*
 * SSH Client PTY bridge.
 *
 * Runs as the child process of the local terminal emulator. Its stdin/stdout
 * are connected to a PTY; it bridges those bytes to a loopback TCP socket
 * accepted by the Kotlin SSH transport manager.
 */
#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <termios.h>
#include <unistd.h>

static int write_all(int fd, const uint8_t *buffer, size_t length) {
    size_t offset = 0;
    while (offset < length) {
        ssize_t n = write(fd, buffer + offset, length - offset);
        if (n < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        if (n == 0) return -1;
        offset += (size_t)n;
    }
    return 0;
}

static void copy_loop(int source, int destination) {
    uint8_t buffer[8192];
    for (;;) {
        ssize_t n = read(source, buffer, sizeof(buffer));
        if (n == 0) break;
        if (n < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (write_all(destination, buffer, (size_t)n) != 0) break;
    }
}

int main(int argc, char **argv) {
    if (argc != 3) {
        dprintf(STDERR_FILENO, "usage: sshbridge <loopback-host> <port>\n");
        return 2;
    }

    char *end = NULL;
    long port = strtol(argv[2], &end, 10);
    if (end == argv[2] || *end != '\0' || port < 1 || port > 65535) {
        dprintf(STDERR_FILENO, "invalid port\n");
        return 2;
    }

    signal(SIGPIPE, SIG_IGN);

    int socket_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (socket_fd < 0) {
        dprintf(STDERR_FILENO, "socket failed: %s\n", strerror(errno));
        return 1;
    }

    struct sockaddr_in address;
    memset(&address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_port = htons((uint16_t)port);
    if (inet_pton(AF_INET, argv[1], &address.sin_addr) != 1) {
        dprintf(STDERR_FILENO, "invalid IPv4 address\n");
        close(socket_fd);
        return 2;
    }

    int connected = 0;
    for (int attempt = 0; attempt < 30; ++attempt) {
        if (connect(socket_fd, (struct sockaddr *)&address, sizeof(address)) == 0) {
            connected = 1;
            break;
        }
        if (errno != ECONNREFUSED && errno != EINTR) break;
        usleep(100000);
    }
    if (!connected) {
        dprintf(STDERR_FILENO, "loopback SSH bridge connection failed: %s\n", strerror(errno));
        close(socket_fd);
        return 1;
    }

    /* Set raw mode only after the loopback socket is ready, so setup failures
       do not leave the terminal in raw mode. */
    struct termios original_termios;
    int have_original_termios = (tcgetattr(STDIN_FILENO, &original_termios) == 0);
    if (have_original_termios) {
        struct termios raw_termios = original_termios;
        cfmakeraw(&raw_termios);
        if (tcsetattr(STDIN_FILENO, TCSANOW, &raw_termios) != 0) {
            dprintf(STDERR_FILENO, "could not set PTY raw mode: %s\n", strerror(errno));
            close(socket_fd);
            return 1;
        }
    }

    pid_t upstream = fork();
    if (upstream < 0) {
        dprintf(STDERR_FILENO, "fork failed: %s\n", strerror(errno));
        if (have_original_termios) tcsetattr(STDIN_FILENO, TCSANOW, &original_termios);
        close(socket_fd);
        return 1;
    }

    if (upstream == 0) {
        /* Terminal keystrokes: PTY stdin -> SSH transport socket. */
        copy_loop(STDIN_FILENO, socket_fd);
        shutdown(socket_fd, SHUT_WR);
        _exit(0);
    }

    /* Remote terminal output: SSH transport socket -> PTY stdout. */
    copy_loop(socket_fd, STDOUT_FILENO);
    shutdown(socket_fd, SHUT_RD);
    close(socket_fd);
    kill(upstream, SIGTERM);
    if (have_original_termios) tcsetattr(STDIN_FILENO, TCSANOW, &original_termios);
    return 0;
}
