/*
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package org.yardstick.probes;

import org.yardstick.*;
import org.yardstick.util.*;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

/**
 * Probe that gathers statistics generated by Linux 'vmstat' command.
 */
public class VmStatProbe implements BenchmarkProbe {
    /** */
    private static final String INVERVAL = "benchmark.probe.vmstat.interval";

    /** */
    private static final String PATH = "benchmark.probe.vmstat.path";

    /** */
    private static final String OPTS = "benchmark.probe.vmstat.opts";

    /** */
    private static final int DEFAULT_INVERVAL_IN_SECS = 1;

    /** */
    private static final String DEFAULT_PATH = "vmstat";

    /** */
    private static final String DEFAULT_OPTS = "-n";

    /** */
    private static final String FIRST_LINE_RE = "^\\s*procs -*memory-* -*swap-* -*io-* -*system-* -*cpu-*\\s*$";

    /** */
    private static final Pattern FIRST_LINE = Pattern.compile(FIRST_LINE_RE);

    /** */
    private static final String HEADER_LINE_RE = "^\\s*r\\s+b\\s+swpd\\s+free\\s+buff\\s+cache\\s+si\\s+so\\s+bi" +
        "\\s+bo\\s+in\\s+cs\\s+us\\s+sy\\s+id\\s+wa\\s*(st\\s*)?$";

    /** */
    private static final Pattern HEADER_LINE = Pattern.compile(HEADER_LINE_RE);

    /** */
    private static final Pattern VMSTAT_PAT;

    static {
        int numFields = 16;

        StringBuilder sb = new StringBuilder("^\\s*");

        for (int i = 0; i < numFields; i++) {
            sb.append("(\\d+)");
            if (i < numFields - 1)
                sb.append("\\s+");
            else
                sb.append("\\s*");
        }

        sb.append("(\\d+)?\\s*");
        sb.append("$");

        VMSTAT_PAT = Pattern.compile(sb.toString());
    }

    /** */
    private BenchmarkConfiguration cfg;

    /** */
    private BenchmarkProcessLauncher proc;

    /** Collected points. */
    private Collection<BenchmarkProbePoint> collected = new ArrayList<>();

    /** {@inheritDoc} */
    @Override public void start(BenchmarkConfiguration cfg) throws Exception {
        this.cfg = cfg;

        BenchmarkClosure<String> c = new BenchmarkClosure<String>() {
            private final AtomicInteger lineNum = new AtomicInteger(0);

            @Override public void apply(String s) {
                parseLine(lineNum.getAndIncrement(), s);
            }
        };

        proc = new BenchmarkProcessLauncher();

        Collection<String> cmdParams = new ArrayList<>();

        cmdParams.add(vmstatPath(cfg));
        cmdParams.addAll(vmstatOpts(cfg));
        cmdParams.add(Integer.toString(interval(cfg)));

        proc.exec(cmdParams, Collections.<String, String>emptyMap(), c);

        cfg.output().println(VmStatProbe.class.getSimpleName() + " is started.");
    }

    /** {@inheritDoc} */
    @Override public void stop() throws Exception {
        if (proc != null) {
            proc.shutdown(false);

            cfg.output().println(VmStatProbe.class.getSimpleName() + " is stopped.");
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<String> metaInfo() {
        return Arrays.asList("Time, ms", "procs r", "procs b", "memory swpd", "memory free",
            "memory buff", "memory cache", "swap si", "swap so", "io bi", "io bo", "system in",
            "system cs", "cpu us", "cpu sy", "cpu id", "cpu wa");
    }

    /** {@inheritDoc} */
    @Override public synchronized Collection<BenchmarkProbePoint> points() {
        Collection<BenchmarkProbePoint> ret = collected;

        collected = new ArrayList<>(ret.size() + 5);

        return ret;
    }

    /**
     * @param pnt Probe point.
     */
    private synchronized void collectPoint(BenchmarkProbePoint pnt) {
        collected.add(pnt);
    }

    /**
     * @param lineNum Line number.
     * @param line Line to parse.
     */
    private void parseLine(int lineNum, String line) {
        if (lineNum == 0) {
            Matcher m = FIRST_LINE.matcher(line);

            if (!m.matches())
                cfg.output().println("WARNING: vmstat returned unexpected first line: " + line);
        }
        else if (lineNum == 1) {
            Matcher m = HEADER_LINE.matcher(line);

            if (!m.matches())
                cfg.output().println("ERROR: Header line does match expected header " +
                    "[exp=" + HEADER_LINE + ", act=" + line + "].");
        }
        else {
            Matcher m = VMSTAT_PAT.matcher(line);

            if (m.matches()) {
                try {
                    BenchmarkProbePoint pnt = new BenchmarkProbePoint(System.currentTimeMillis(),
                        new float[] {
                            Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                            Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
                            Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)),
                            Integer.parseInt(m.group(7)), Integer.parseInt(m.group(8)),
                            Integer.parseInt(m.group(9)), Integer.parseInt(m.group(10)),
                            Integer.parseInt(m.group(11)), Integer.parseInt(m.group(12)),
                            Integer.parseInt(m.group(13)), Integer.parseInt(m.group(14)),
                            Integer.parseInt(m.group(15)), Integer.parseInt(m.group(16)),
                        });

                    collectPoint(pnt);
                }
                catch (NumberFormatException e) {
                    cfg.output().println("ERROR: Can't parse line: " + line + ".");
                }
            }
            else
                cfg.output().println("ERROR: Can't parse line: " + line + ".");
        }
    }

    /**
     * @param cfg Config.
     * @return Interval.
     */
    private static int interval(BenchmarkConfiguration cfg) {
        try {
            return Integer.parseInt(cfg.customProperties().get(INVERVAL));
        }
        catch (NumberFormatException | NullPointerException ignored) {
            return DEFAULT_INVERVAL_IN_SECS;
        }
    }

    /**
     * @param cfg Config.
     * @return Path to vmstat executable.
     */
    private static String vmstatPath(BenchmarkConfiguration cfg) {
        String res = cfg.customProperties().get(PATH);

        return res == null || res.isEmpty() ? DEFAULT_PATH : res;
    }

    /**
     * @param cfg Config.
     * @return Path to vmstat executable.
     */
    private static Collection<String> vmstatOpts(BenchmarkConfiguration cfg) {
        String res = cfg.customProperties().get(OPTS);

        res = res == null || res.isEmpty() ? DEFAULT_OPTS : res;

        return Arrays.asList(res.split("\\s+"));
    }
}
