package space.potatofrom.aeiodine;

/**
 * Created by kevin on 7/25/16.
 */
public enum IodineFlags {
    DNS_TYPE("-T"),
    DOWNSTREAM_ENCODING("-O"),
    TARGET_OR_PING_INTERVAL("-I"),
    MINIMUM_QUERY_INTERVAL("-s"),
    LAZY_MODE("-L"),
    MAX_SIZE_DOWNSTREAM_FRAGMENTS("-m"),
    MAX_SIZE_UPSTREAM_HOSTNAMES("-M"),
    SKIP_RAW_UDP_MODE_ATTEMPT("-r"),
    AUTHENTICATION_PASSWORD("-P"),

    WINDOW_SIZE_DOWNSTREAM_FRAGMENT("-w"),
    WINDOW_SIZE_UPSTREAM_FRAGMENT("-W"),
    LAZY_MODE_SERVER_SIDE_REQUEST_TIMEOUT("-i"),
    DOWNSTREAM_FRAGMENT_ACK_TIMEOUT("-j"),
    USE_DOWNSTREAM_COMPRESSION("-c"),
    USE_UPSTREAM_COMPRESSION("-C"),

    PRINT_VERSION_INFO("-v"),
    PRINT_HELP("-h"),
    PRINT_CONNECTION_STATISTICS_INTERVAL_SEC("-V"),
    KEEP_RUNNING_IN_FOREGROUND("-F"),
    ENABLE_DEBUG_MODE("-D"),
    RUN_AS_USER("-u"),
    CHROOT_TO("-d"),
    SELINUX_CONTEXT("-z"),
    ROUTING_DOMAIN_OPENBSD_ONLY("-R"),
    PIDFILE("-F");

    private final String name;

    IodineFlags(String s) {
        name = s;
    }

    public boolean equalsName(String otherName) {
        return otherName != null && name.equals(otherName);
    }

    public String toString() {
        return this.name;
    }
}