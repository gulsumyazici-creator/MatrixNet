import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

/**
 * Core class that manages the network structure and operations.
 */

public class Core {

    /** Host storage */
    private final MyHashMap<String, Host> hosts = new MyHashMap<>(65536);
    private final ArrayList<Host> hostList = new ArrayList<>(50005);

    /** Network statistics */
    private long totalBandwidthSum = 0;
    private int unsealedCount = 0;
    private long totalClearanceSum = 0;

    /** Host indexing */
    private int hostIdxCounter = 0;

    /** BFS helper */
    private final Host[] bfsQueue = new Host[50005];

    /** Query stamp for lazy resets */
    private int currentSearchId = 0;

    /** traceRoute label structures */
    private int[] labelHead = new int[50005];
    private int[] labelHeadStamp = new int[50005];

    /** Label pool */
    private int[] labNode = new int[200000];
    private int[] labHops = new int[200000];
    private long[] labBase = new long[200000];
    private int[] labParent = new int[200000];
    private int[] labNext = new int[200000];
    private int labelCnt = 0;

    /**
     * =============================
     * HOST OPERATIONS
     * =============================
     */

    /**
     * Creates a new host in the network.
     *
     * @param id unique host identifier
     * @param cLevel clearance level of the host
     * @return status message
     */
    public String spawnHost(String id, int cLevel) {
        if (!isValidHostId(id) || hosts.containsKey(id)) {
            return "Some error occurred in spawn_host.";
        }

        Host h = new Host(id, cLevel);
        h.index = hostIdxCounter++;
        hosts.put(id, h);
        hostList.add(h);
        totalClearanceSum += cLevel;

        return "Spawned host " + id + " with clearance level " + cLevel + ".";
    }

    /**
     * Checks whether a host ID is valid.
     * Valid IDs contain only uppercase letters, digits, and underscore.
     */
    private boolean isValidHostId(String id) {
        if (id == null || id.length() == 0) return false;

        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);

            if (!(c >= 'A' && c <= 'Z') &&
                    !(c >= '0' && c <= '9') &&
                    c != '_') {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates an undirected backdoor between two hosts.
     *
     * @param id1 first host ID
     * @param id2 second host ID
     * @param lat latency of the backdoor
     * @param bw bandwidth of the backdoor
     * @param fw required firewall clearance level
     * @return status message
     */
    public String linkBackdoor(String id1, String id2, int lat, int bw, int fw) {
        Host h1 = hosts.get(id1), h2 = hosts.get(id2);
        if (h1 == null || h2 == null || id1.equals(id2) || h1.neighborMap.containsKey(id2)) {
            return "Some error occurred in link_backdoor.";
        }

        Backdoor b = new Backdoor(h1, h2, lat, bw, fw);
        h1.adj.add(b);
        h2.adj.add(b);
        h1.neighborMap.put(id2, b);
        h2.neighborMap.put(id1, b);

        unsealedCount++;
        totalBandwidthSum += bw;

        return "Linked " + id1 + " <-> " + id2
                + " with latency " + lat + "ms, bandwidth "
                + bw + "Mbps, firewall " + fw + ".";
    }

    /**
     * Toggles the sealed state of an existing backdoor.
     *
     * @param id1 first host ID
     * @param id2 second host ID
     * @return status message
     */
    public String sealBackdoor(String id1, String id2) {
        Host h1 = hosts.get(id1);
        Backdoor b = (h1 != null) ? h1.neighborMap.get(id2) : null;
        if (b == null) return "Some error occurred in seal_backdoor.";

        if (b.unsealed) {
            b.unsealed = false;
            unsealedCount--;
            totalBandwidthSum -= b.bandwidth;
            return "Backdoor " + id1 + " <-> " + id2 + " sealed.";
        } else {
            b.unsealed = true;
            unsealedCount++;
            totalBandwidthSum += b.bandwidth;
            return "Backdoor " + id1 + " <-> " + id2 + " unsealed.";
        }
    }

    /**
     * ============================================================
     * TRACE_ROUTE
     * ============================================================
     *
     * Finds the optimal route between two hosts using a label-setting
     * Dijkstra-like algorithm with dominance pruning.
     *
     * Optimization criteria (in order):
     * 1) Minimum total latency
     * 2) Minimum number of segments
     * 3) Lexicographically smallest path
     *
     * Dynamic latency formula:
     * totalLatency = baseLatency + lambda * (m * (m - 1) / 2)
     *
     * @param src source host ID
     * @param dst destination host ID
     * @param minBW minimum required bandwidth
     * @param lambda dynamic latency coefficient
     * @return formatted optimal route or failure message
     */
    public String traceRoute(String src, String dst, int minBW, int lambda) {
        Host sH = hosts.get(src), dH = hosts.get(dst);
        if (sH == null || dH == null) return "Some error occurred in trace_route.";
        if (src.equals(dst)) {
            return "Optimal route " + src + " -> " + dst + ": " + src + " (Latency = 0ms)";
        }

        // Start a new search instance
        currentSearchId++;
        int stamp = currentSearchId;

        // Reset label pool for this query
        labelCnt = 0;

        // Priority queue ordered by (total latency, hop count)
        LabelHeap pq = new LabelHeap(32768);

        // Initialize starting label at source node
        ensureNodeHeadInit(sH.index, stamp);
        int startLabel = newLabelEnsureCapacity();
        labNode[startLabel] = sH.index;
        labHops[startLabel] = 0;
        labBase[startLabel] = 0L;
        labParent[startLabel] = -1;
        labNext[startLabel] = labelHead[sH.index];
        labelHead[sH.index] = startLabel;

        pq.push(startLabel, 0L, 0);

        boolean foundAny = false;
        long bestDyn = Long.MAX_VALUE;
        int bestHops = Integer.MAX_VALUE;
        int bestLabel = -1;

        // Main label-setting loop
        while (!pq.isEmpty()) {
            int li = pq.popMinLabel();

            int u = labNode[li];
            int hops = labHops[li];
            long base = labBase[li];

            long dyn = base + dynExtra(lambda, hops);

            // Prune states worse than the current best
            if (foundAny) {
                if (dyn > bestDyn) break;
                if (dyn == bestDyn && hops > bestHops) break;
            }

            // Destination reached
            if (u == dH.index) {
                if (!foundAny) {
                    foundAny = true;
                    bestDyn = dyn;
                    bestHops = hops;
                    bestLabel = li;
                } else if (dyn == bestDyn && hops == bestHops) {
                    if (lexLess(li, bestLabel)) bestLabel = li;
                }
                continue;
            }

            // Relax outgoing backdoors
            Host uHost = hostList.get(u);
            ArrayList<Backdoor> adjs = uHost.adj;

            for (int i = 0; i < adjs.size(); i++) {
                Backdoor b = adjs.get(i);

                if (!b.unsealed) continue;
                if (b.bandwidth < minBW) continue;
                if (uHost.clearanceLevel < b.firewallLevel) continue;

                Host vHost = (b.point1 == uHost) ? b.point2 : b.point1;
                int v = vHost.index;

                int nh = hops + 1;
                long nb = base + b.latency;

                ensureNodeHeadInit(v, stamp);

                int kept = tryInsertLabel(v, nh, nb, li);
                if (kept >= 0) {
                    long ndyn = nb + dynExtra(lambda, nh);
                    if (!foundAny || ndyn < bestDyn || (ndyn == bestDyn && nh <= bestHops)) {
                        pq.push(kept, ndyn, nh);
                    }
                }
            }
        }

        if (!foundAny) return "No route found from " + src + " to " + dst;
        return buildPathFromLabel(bestLabel, sH.index, dH.index, bestDyn);
    }

    /**
     * =============================
     * TRACE_ROUTE HELPERS
     * =============================
     */

    /**
     * Lazily initializes the label list of a node for the current query.
     *
     * @param nodeIdx index of the node
     * @param stamp current query stamp
     */
    private void ensureNodeHeadInit(int nodeIdx, int stamp) {
        if (labelHeadStamp[nodeIdx] != stamp) {
            labelHeadStamp[nodeIdx] = stamp;
            labelHead[nodeIdx] = -1;
        }
    }

    /**
     * Computes the dynamic latency penalty caused by lambda.
     *
     * Formula: lambda * (m * (m - 1) / 2)
     *
     * @param lambda dynamic latency coefficient
     * @param m number of segments
     * @return additional latency
     */
    private static long dynExtra(int lambda, int m) {
        if (lambda == 0 || m <= 1) return 0L;
        return (long) lambda * (long) m * (long) (m - 1) / 2L;
    }

    /**
     * Allocates a new label index, growing the label pool if needed.
     *
     * @return new label index
     */
    private int newLabelEnsureCapacity() {
        if (labelCnt >= labNode.length) growLabelPool();
        return labelCnt++;
    }

    /**
     * Doubles the capacity of the label pool.
     */
    private void growLabelPool() {
        int oldCap = labNode.length;
        int newCap = oldCap * 2;

        int[] nNode = new int[newCap];
        int[] nHops = new int[newCap];
        long[] nBase = new long[newCap];
        int[] nPar = new int[newCap];
        int[] nNext = new int[newCap];

        for (int i = 0; i < oldCap; i++) {
            nNode[i] = labNode[i];
            nHops[i] = labHops[i];
            nBase[i] = labBase[i];
            nPar[i] = labParent[i];
            nNext[i] = labNext[i];
        }

        labNode = nNode;
        labHops = nHops;
        labBase = nBase;
        labParent = nPar;
        labNext = nNext;
    }

    /**
     * Inserts a new label into a node with dominance pruning.
     *
     * Dominance rules:
     * - If an existing label dominates the new one, the new label is rejected.
     * - Labels dominated by the new one are removed.
     * - If two labels have the same (hops, baseLatency),
     *   the lexicographically smaller path is kept.
     *
     * @param v target node index
     * @param nh number of hops
     * @param nb base latency sum
     * @param parentLabel parent label index
     * @return label index to be used, or -1 if rejected
     */
    private int tryInsertLabel(int v, int nh, long nb, int parentLabel) {
        int cur = labelHead[v];

        // Check if dominated by an existing label
        while (cur != -1) {
            int ch = labHops[cur];
            long cb = labBase[cur];

            if (ch <= nh && cb <= nb) {
                if (ch == nh && cb == nb) {
                    if (lexLessByParents(parentLabel, labParent[cur])) {
                        labParent[cur] = parentLabel;
                        return cur;
                    }
                }
                return -1;
            }
            cur = labNext[cur];
        }

        // Remove labels dominated by the new one
        int prev = -1;
        cur = labelHead[v];
        while (cur != -1) {
            int ch = labHops[cur];
            long cb = labBase[cur];

            if (nh <= ch && nb <= cb) {
                int nxt = labNext[cur];
                if (prev == -1) labelHead[v] = nxt;
                else labNext[prev] = nxt;
                cur = nxt;
                continue;
            }

            prev = cur;
            cur = labNext[cur];
        }

        // Insert new label
        int nl = newLabelEnsureCapacity();
        labNode[nl] = v;
        labHops[nl] = nh;
        labBase[nl] = nb;
        labParent[nl] = parentLabel;
        labNext[nl] = labelHead[v];
        labelHead[v] = nl;

        return nl;
    }

    /**
     * Compares two complete paths lexicographically by host IDs.
     *
     * @param a first label
     * @param b second label
     * @return true if path(a) is lexicographically smaller than path(b)
     */
    private boolean lexLess(int a, int b) {
        if (a == b) return false;

        int[] sa = new int[128];
        int[] sb = new int[128];
        int la = 0, lb = 0;

        int x = a;
        while (x != -1) {
            if (la == sa.length) sa = growIntArray(sa);
            sa[la++] = labNode[x];
            x = labParent[x];
        }

        int y = b;
        while (y != -1) {
            if (lb == sb.length) sb = growIntArray(sb);
            sb[lb++] = labNode[y];
            y = labParent[y];
        }

        int i = la - 1, j = lb - 1;
        while (i >= 0 && j >= 0) {
            int cmp = hostList.get(sa[i]).id.compareTo(hostList.get(sb[j]).id);
            if (cmp != 0) return cmp < 0;
            i--;
            j--;
        }

        return la < lb;
    }

    /**
     * Compares two parent paths lexicographically.
     *
     * @param parentA first parent label
     * @param parentB second parent label
     * @return true if parentA path is lexicographically smaller
     */
    private boolean lexLessByParents(int parentA, int parentB) {
        if (parentA == parentB) return false;
        if (parentB == -1) return false;
        if (parentA == -1) return true;

        int[] sa = new int[128];
        int[] sb = new int[128];
        int la = 0, lb = 0;

        int x = parentA;
        while (x != -1) {
            if (la == sa.length) sa = growIntArray(sa);
            sa[la++] = labNode[x];
            x = labParent[x];
        }

        int y = parentB;
        while (y != -1) {
            if (lb == sb.length) sb = growIntArray(sb);
            sb[lb++] = labNode[y];
            y = labParent[y];
        }

        int i = la - 1, j = lb - 1;
        while (i >= 0 && j >= 0) {
            int cmp = hostList.get(sa[i]).id.compareTo(hostList.get(sb[j]).id);
            if (cmp != 0) return cmp < 0;
            i--;
            j--;
        }

        return la < lb;
    }

    /**
     * Grows an integer array by a factor of two.
     *
     * @param arr original array
     * @return expanded array
     */
    private int[] growIntArray(int[] arr) {
        int[] n = new int[arr.length * 2];
        for (int i = 0; i < arr.length; i++) n[i] = arr[i];
        return n;
    }

    /**
     * Reconstructs the path from the destination label and formats the output.
     *
     * @param dstLabel destination label index
     * @param sIdx source node index
     * @param dIdx destination node index
     * @param bestDyn total latency of the optimal path
     * @return formatted route string
     */
    private String buildPathFromLabel(int dstLabel, int sIdx, int dIdx, long bestDyn) {
        int[] nodes = new int[128];
        int len = 0;

        int cur = dstLabel;
        while (cur != -1) {
            if (len == nodes.length) nodes = growIntArray(nodes);
            nodes[len++] = labNode[cur];
            cur = labParent[cur];
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Optimal route ")
                .append(hostList.get(sIdx).id)
                .append(" -> ")
                .append(hostList.get(dIdx).id)
                .append(": ");

        for (int i = len - 1; i >= 0; i--) {
            sb.append(hostList.get(nodes[i]).id);
            if (i != 0) sb.append(" -> ");
        }

        sb.append(" (Latency = ").append(bestDyn).append("ms)");
        return sb.toString();
    }

    /**
     * Min-heap used by traceRoute, ordered by (dynamic latency, hop count).
     */
    private static final class LabelHeap {

        /** Number of elements in the heap */
        private int size = 0;

        /** Stored label indices */
        private int[] lab;

        /** Dynamic latency keys */
        private long[] keyDyn;

        /** Hop count keys */
        private int[] keyHops;

        /**
         * Creates a heap with given initial capacity.
         *
         * @param cap initial capacity
         */
        LabelHeap(int cap) {
            lab = new int[cap];
            keyDyn = new long[cap];
            keyHops = new int[cap];
        }

        /** @return true if heap is empty */
        boolean isEmpty() {
            return size == 0;
        }

        /**
         * Inserts a new label into the heap.
         *
         * @param labelIdx label index
         * @param dyn dynamic latency
         * @param hops hop count
         */
        void push(int labelIdx, long dyn, int hops) {
            if (size == lab.length) grow();
            int i = size++;
            lab[i] = labelIdx;
            keyDyn[i] = dyn;
            keyHops[i] = hops;
            siftUp(i);
        }

        /**
         * Removes and returns the minimum label.
         *
         * @return label index with minimum key
         */
        int popMinLabel() {
            int res = lab[0];
            size--;
            if (size > 0) {
                lab[0] = lab[size];
                keyDyn[0] = keyDyn[size];
                keyHops[0] = keyHops[size];
                siftDown(0);
            }
            return res;
        }

        /** Doubles heap capacity */
        private void grow() {
            int old = lab.length;
            int ncap = old * 2;

            int[] nLab = new int[ncap];
            long[] nDyn = new long[ncap];
            int[] nHop = new int[ncap];

            for (int i = 0; i < old; i++) {
                nLab[i] = lab[i];
                nDyn[i] = keyDyn[i];
                nHop[i] = keyHops[i];
            }

            lab = nLab;
            keyDyn = nDyn;
            keyHops = nHop;
        }

        /** Restores heap order upwards */
        private void siftUp(int i) {
            while (i > 0) {
                int p = (i - 1) >>> 1;
                if (less(i, p)) {
                    swap(i, p);
                    i = p;
                } else break;
            }
        }

        /** Restores heap order downwards */
        private void siftDown(int i) {
            while (true) {
                int l = (i << 1) + 1;
                if (l >= size) break;
                int r = l + 1;
                int m = (r < size && less(r, l)) ? r : l;

                if (less(m, i)) {
                    swap(m, i);
                    i = m;
                } else break;
            }
        }

        /** Compares two heap elements */
        private boolean less(int i, int j) {
            if (keyDyn[i] != keyDyn[j]) return keyDyn[i] < keyDyn[j];
            return keyHops[i] < keyHops[j];
        }

        /** Swaps two heap elements */
        private void swap(int i, int j) {
            int tl = lab[i]; lab[i] = lab[j]; lab[j] = tl;
            long td = keyDyn[i]; keyDyn[i] = keyDyn[j]; keyDyn[j] = td;
            int th = keyHops[i]; keyHops[i] = keyHops[j]; keyHops[j] = th;
        }
    }

    /**
     * =============================
     * REPORT / ANALYSIS
     * =============================
     */

    /**
     * Generates a summary report of the current network state.
     *
     * @return formatted network report
     */
    public String oracleReport() {
        int v = hostList.size();
        int k = countComponents(null, null);

        BigDecimal bw = (unsealedCount == 0)
                ? BigDecimal.ZERO.setScale(1)
                : BigDecimal.valueOf(totalBandwidthSum)
                .divide(BigDecimal.valueOf(unsealedCount), 1, RoundingMode.HALF_UP);

        BigDecimal cl = (v == 0)
                ? BigDecimal.ZERO.setScale(1)
                : BigDecimal.valueOf(totalClearanceSum)
                .divide(BigDecimal.valueOf(v), 1, RoundingMode.HALF_UP);

        StringBuilder sb = new StringBuilder("--- Resistance Network Report ---\n");
        sb.append("Total Hosts: ").append(v).append("\n");
        sb.append("Total Unsealed Backdoors: ").append(unsealedCount).append("\n");
        sb.append("Network Connectivity: ")
                .append(k <= 1 ? "Connected" : "Disconnected").append("\n");
        sb.append("Connected Components: ").append(k).append("\n");
        sb.append("Contains Cycles: ").append(containsCycle() ? "Yes" : "No").append("\n");
        sb.append("Average Bandwidth: ").append(bw).append("Mbps\n");
        sb.append("Average Clearance Level: ").append(cl);

        return sb.toString();
    }

    /**
     * Counts the number of connected components in the network.
     * Optionally simulates removal of a host or a backdoor.
     *
     * @param sId host ID to be excluded (null if none)
     * @param sB backdoor to be excluded (null if none)
     * @return number of connected components
     */
    private int countComponents(String sId, Backdoor sB) {
        boolean[] vis = new boolean[hostIdxCounter];
        int c = 0;

        for (Host h : hostList) {
            if (sId != null && h.id.equals(sId)) continue;
            if (vis[h.index]) continue;

            c++;
            int head = 0, tail = 0;
            bfsQueue[tail++] = h;
            vis[h.index] = true;

            while (head < tail) {
                Host cur = bfsQueue[head++];
                ArrayList<Backdoor> adjs = cur.adj;

                for (int i = 0; i < adjs.size(); i++) {
                    Backdoor b = adjs.get(i);
                    if (!b.unsealed) continue;
                    if (b == sB) continue;

                    Host n = (b.point1 == cur) ? b.point2 : b.point1;
                    if (sId != null && n.id.equals(sId)) continue;

                    if (!vis[n.index]) {
                        vis[n.index] = true;
                        bfsQueue[tail++] = n;
                    }
                }
            }
        }

        return c;
    }

    /**
     * Checks whether the network contains any cycle.
     *
     * @return true if a cycle exists, false otherwise
     */
    private boolean containsCycle() {
        int[] vArr = new int[hostIdxCounter];
        for (Host h : hostList) {
            if (vArr[h.index] == 0) {
                if (dfsCycle(h, null, 1, vArr)) return true;
            }
        }
        return false;
    }

    /**
     * Depth-first search helper for cycle detection.
     *
     * @param c current host
     * @param p parent host
     * @param d depth in DFS tree
     * @param v visitation array
     * @return true if a cycle is found
     */
    private boolean dfsCycle(Host c, Host p, int d, int[] v) {
        v[c.index] = d;

        ArrayList<Backdoor> adjs = c.adj;

        for (int i = 0; i < adjs.size(); i++) {
            Backdoor b = adjs.get(i);
            if (!b.unsealed) continue;

            Host n = (b.point1 == c) ? b.point2 : b.point1;
            if (n == p) continue;

            if (v[n.index] > 0) {
                // cycle length >= 3
                if (d - v[n.index] + 1 >= 3) return true;
            } else {
                if (dfsCycle(n, c, d + 1, v)) return true;
            }
        }

        return false;
    }

    /**
     * Checks if the network is fully connected.
     *
     * @return connectivity status message
     */
    public String scanConnectivity() {
        int k = countComponents(null, null);
        return (k <= 1)
                ? "Network is fully connected."
                : "Network has " + k + " disconnected components.";
    }

    /**
     * Simulates the failure of a host and checks if it is an articulation point.
     *
     * @param id host ID to be removed
     * @return analysis result
     */
    public String simulateBreachHost(String id) {
        Host h = hosts.get(id);
        if (h == null) return "Some error occurred in simulate_breach.";

        int before = countComponents(null, null);
        int after = countComponents(id, null);

        if (after <= before)
            return "Host " + id + " is NOT an articulation point. Network remains the same.";

        return "Host " + id + " IS an articulation point.\n"
                + "Failure results in " + after + " disconnected components.";
    }

    /**
     * Simulates the failure of a backdoor and checks if it is a bridge.
     *
     * @param id1 first host ID
     * @param id2 second host ID
     * @return analysis result
     */
    public String simulateBreachBackdoor(String id1, String id2) {
        Host h1 = hosts.get(id1);
        Backdoor b = (h1 != null) ? h1.neighborMap.get(id2) : null;

        if (b == null || !b.unsealed)
            return "Some error occurred in simulate_breach.";

        int before = countComponents(null, null);
        int after = countComponents(null, b);

        if (after <= before)
            return "Backdoor " + id1 + " <-> " + id2
                    + " is NOT a bridge. Network remains the same.";

        return "Backdoor " + id1 + " <-> " + id2
                + " IS a bridge.\nFailure results in "
                + after + " disconnected components.";
    }
}

