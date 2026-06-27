import java.util.ArrayList;

/** Network host node */
public class Host {

    /** Host identifier */
    String id;

    /** Clearance level */
    int clearanceLevel;

    /** Index assigned by Core */
    int index;

    /** Adjacent backdoors */
    ArrayList<Backdoor> adj;

    /** Neighbor backdoor lookup */
    MyHashMap<String, Backdoor> neighborMap;

    /** Creates a host */
    public Host(String id, int cLevel) {
        this.id = id;
        this.clearanceLevel = cLevel;
        this.adj = new ArrayList<>();
        this.neighborMap = new MyHashMap<>();
    }
}

