package com.dincer552.hattrickssh

object Constants {
    const val TAG = "HattrickSSH"

    const val DEFAULT_HOST = "ssh.dinceryuk.com"
    const val DEFAULT_PORT = 22
    const val DEFAULT_USERNAME = "azureuser"
    const val VM_NAME = "hattrick-vm-uks"

    const val SSH_KEY_DIR = "ssh_keys"
    const val SSH_KEY_FILENAME = "id_rsa.enc"
    const val KNOWN_HOSTS_FILENAME = "known_hosts.enc"

    const val KEYSTORE_ALIAS = "hattrick_ssh_aes_key_v1"
    const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    const val CLOUDFLARED_NATIVE_LIB_NAME = "libcloudflared.so"
    const val SSHBRIDGE_NATIVE_LIB_NAME = "libsshbridge.so"

    const val CONNECT_TIMEOUT_MS = 180_000
    const val DEFAULT_ROWS = 28
    const val DEFAULT_COLS = 90
    const val DEFAULT_PX_PER_ROW = 24
    const val DEFAULT_PX_PER_COL = 12

    const val STATUS_DISCONNECTED = "Bağlı değil"
    const val STATUS_CONNECTING = "Bağlanıyor…"
    const val STATUS_CONNECTED = "Bağlı"
}
