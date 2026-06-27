/** Network connection between two hosts */
public class Backdoor {

    /** Latency of the connection */
    int latency;

    /** Bandwidth (positive) */
    int bandwidth;

    /** Required clearance level */
    int firewallLevel;

    /** Connected hosts */
    Host point1, point2;

    /** Connection state */
    boolean unsealed = true;

    /** Creates a backdoor between two hosts */
    public Backdoor(Host host1, Host host2,
                    int latency, int bandwidth, int firewallLevel) {

        this.point1 = host1;
        this.point2 = host2;
        this.latency = latency;
        this.bandwidth = bandwidth;
        this.firewallLevel = firewallLevel;
    }
}
