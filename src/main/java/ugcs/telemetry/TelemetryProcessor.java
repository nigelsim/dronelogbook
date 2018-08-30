package ugcs.telemetry;

import com.ugcs.ucs.proto.DomainProto.Telemetry;
import com.ugcs.ucs.proto.DomainProto.Value;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.ugcs.common.util.Strings.isNullOrEmpty;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

public class TelemetryProcessor {
    private final List<Telemetry> telemetryList;
    private SortedMap<Long, Map<String, Telemetry>> processedTelemetry = null;
    private Set<String> allFieldCodes = null;
    private List<FlightTelemetry> flightTelemetries = null;

    public TelemetryProcessor(List<Telemetry> telemetryList) {
        this.telemetryList = telemetryList;
    }

    private SortedMap<Long, Map<String, Telemetry>> getProcessedTelemetry() {
        if (processedTelemetry == null) {
            synchronized (this) {
                if (processedTelemetry == null) {
                    processedTelemetry = telemetryList.stream()
                            .sorted(comparing(Telemetry::getTime))
                            .collect(groupingBy(Telemetry::getTime, TreeMap::new,
                                    toMap(t -> t.getTelemetryField().getCode(), t -> t, (t1, t2) -> {
                                        System.err.println("*** Merge fail:");
                                        System.err.println(t1);
                                        System.err.println(t2);
                                        return t1;
                                    })));
                }
            }
        }
        return processedTelemetry;
    }

    private Set<String> getAllFieldCodes() {
        if (allFieldCodes == null) {
            synchronized (this) {
                if (allFieldCodes == null) {
                    allFieldCodes = getProcessedTelemetry().values().stream()
                            .flatMap(m -> m.values().stream())
                            .map(t -> t.getTelemetryField().getCode())
                            .collect(Collectors.toSet());
                }
            }
        }
        return allFieldCodes;
    }

    private List<FlightTelemetry> getFlightTelemetries() {
        if (flightTelemetries == null) {
            synchronized (this) {
                if (flightTelemetries == null) {
                    flightTelemetries = new ArrayList<>();

                    final SortedMap<Long, Map<String, Telemetry>> allTelemetry = getProcessedTelemetry();

                    long lastTelemetryTime = 0;
                    final List<Triple<Long, Long, Map<String, Telemetry>>> telemetryByTimeDiff = new LinkedList<>();
                    for (Map.Entry<Long, Map<String, Telemetry>> entry : allTelemetry.entrySet()) {
                        if (lastTelemetryTime == 0) {
                            telemetryByTimeDiff.add(Triple.of(Long.MAX_VALUE, entry.getKey(), entry.getValue()));
                        } else {
                            telemetryByTimeDiff.add(Triple.of(entry.getKey() - lastTelemetryTime, entry.getKey(), entry.getValue()));
                        }
                        lastTelemetryTime = entry.getKey();
                    }

                    List<Pair<Long, Map<String, Telemetry>>> currentFlightTelemetry = new LinkedList<>();
                    for (Triple<Long, Long, Map<String, Telemetry>> telemetryWithTimeDiff : telemetryByTimeDiff) {
                        final Pair<Long, Map<String, Telemetry>> telemetryRecord =
                                Pair.of(telemetryWithTimeDiff.getMiddle(), telemetryWithTimeDiff.getRight());
                        if (currentFlightTelemetry.isEmpty()) {
                            currentFlightTelemetry.add(telemetryRecord);
                        } else {
                            if (telemetryWithTimeDiff.getLeft() < 10000) {
                                currentFlightTelemetry.add(telemetryRecord);
                            } else {
                                if (currentFlightTelemetry.size() > 1) {
                                    flightTelemetries.add(new FlightTelemetry(currentFlightTelemetry));
                                }
                                currentFlightTelemetry = new LinkedList<>();
                            }
                        }
                    }
                }
            }
        }
        return flightTelemetries;
    }

    public void printAsCsv(OutputStream out, Charset charset) {
        final PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(out, charset)));
        printCsvHeader(writer);

        final Map<String, Value> currentRecord = new HashMap<>();
        getAllFieldCodes().forEach(fieldCode -> currentRecord.put(fieldCode, null));
        getProcessedTelemetry().forEach((epochMilli, telemetryMap) -> {
            telemetryMap.forEach((fieldCode, telemetry) -> {
                final Value value = telemetry.getValue();
                if (!isNullOrEmpty(value.toString())) {
                    currentRecord.put(fieldCode, value);
                }
            });
            final String fieldCodeValues = getAllFieldCodes().stream()
                    .map(fieldCode -> {
                        final Value value = currentRecord.get(fieldCode);
                        if (value == null) {
                            return "";
                        }
                        return valueToString(value);
                    })
                    .collect(joining(","));
            writer.println(convertDateTime(epochMilli) + "," + fieldCodeValues);
        });
        writer.flush();
    }

    private void printCsvHeader(PrintWriter writer) {
        writer.println("Time," + getAllFieldCodes().stream()
                .map(fieldCode -> CsvFieldMapper.mapper().convertTypeName(fieldCode))
                .collect(joining(",")));
    }

    private static String convertDateTime(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault()).toString();
    }

    private static String valueToString(Value value) {
        if (value.hasFloatValue()) {
            return String.valueOf(value.getFloatValue());
        }
        if (value.hasDoubleValue()) {
            return String.valueOf(value.getDoubleValue());
        }
        if (value.hasIntValue()) {
            return String.valueOf(value.getIntValue());
        }
        if (value.hasLongValue()) {
            return String.valueOf(value.getLongValue());
        }
        if (value.hasBoolValue()) {
            return String.valueOf(value.getBoolValue());
        }
        return value.getStringValue();
    }
}
