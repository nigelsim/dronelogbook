package ugcs.processing.telemetry.tracks;

import com.ugcs.ucs.proto.DomainProto;
import com.ugcs.ucs.proto.DomainProto.Vehicle;
import org.junit.jupiter.api.Test;

import static com.ugcs.ucs.proto.DomainProto.Track;
import static com.ugcs.ucs.proto.DomainProto.TrackPoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VehicleTrackTest {
    @Test
    void testVehicleTrack() {
        final Vehicle vehicle = Vehicle.newBuilder()
                .setSerialNumber("test_serial")
                .buildPartial();

        final TrackPoint firstTrackPoint = TrackPoint.newBuilder().setTime(12345L).buildPartial();
        final TrackPoint secondTrackPoint = TrackPoint.newBuilder().setTime(54321L).buildPartial();

        final Track track = Track.newBuilder()
                .addPoints(firstTrackPoint)
                .addPoints(secondTrackPoint)
                .buildPartial();

        final DomainProto.VehicleTrack domainVehicleTrack = DomainProto.VehicleTrack.newBuilder()
                .setVehicle(vehicle)
                .setTrack(track)
                .buildPartial();

        final VehicleTrack testedVehicleTrack = new VehicleTrack(vehicle, domainVehicleTrack);

        assertEquals(12345L, testedVehicleTrack.getStartEpochMilli());
        assertEquals(54321L, testedVehicleTrack.getEndEpochMilli());

        assertThat(testedVehicleTrack.getId().toString())
                .startsWith("Flight at 1970-01-01")
                .endsWith(":12");

        assertEquals(testedVehicleTrack.getId().getId().toString(), "12345_54321_test_serial");
    }
}