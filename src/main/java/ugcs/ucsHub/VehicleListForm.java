package ugcs.ucsHub;

import com.github.lgooddatepicker.components.DateTimePicker;
import com.ugcs.ucs.proto.DomainProto.Vehicle;
import ugcs.upload.MultipartUtility;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.toMap;
import static javax.swing.JOptionPane.INFORMATION_MESSAGE;
import static ugcs.ucsHub.Settings.settings;

public class VehicleListForm extends JPanel {
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");

    private final Map<String, Vehicle> vehicleMap;
    private final JList<String> vehicleJList;

    public VehicleListForm(SessionController controller) {
        super(new BorderLayout());

        final DateTimePicker startDateTimePicker = new DateTimePicker();
        final DateTimePicker endDateTimePicker = new DateTimePicker();

        vehicleMap = controller.getVehicles().stream()
                .collect(toMap(Vehicle::getName, v -> v));

        final String[] vehicleNames = vehicleMap.keySet().toArray(new String[0]);
        vehicleJList = new JList<>(vehicleNames);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createBevelBorder(0));
        leftPanel.add(BorderLayout.NORTH, new JLabel("List of all vehicles:"));
        vehicleJList.setBorder(BorderFactory.createTitledBorder(""));
        leftPanel.add(BorderLayout.CENTER, new JScrollPane(vehicleJList));
        this.add(BorderLayout.WEST, leftPanel);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createTitledBorder("Information about vehicle:"));
        JTextPane infoPane = new JTextPane();
        ((DefaultCaret) infoPane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        infoPane.setEditable(false);
        final JScrollPane infoScrollPane = new JScrollPane(infoPane);
        centerPanel.add(BorderLayout.CENTER, infoScrollPane);
        this.add(BorderLayout.CENTER, centerPanel);

        JPanel bottomPanel = new JPanel(new BorderLayout());

        final JPanel bottomRightPanel = new JPanel(new GridLayout(2, 1));

        final JCheckBox uploadFlightCheckBox = new JCheckBox("Upload flight", true);
        bottomRightPanel.add(new JPanel().add(uploadFlightCheckBox).getParent());

        final JButton getTelemetryButton = new JButton("Get Telemetry");
        getTelemetryButton.setEnabled(false);
        bottomRightPanel.add(new JPanel().add(getTelemetryButton).getParent());
        bottomPanel.add(BorderLayout.EAST, bottomRightPanel);
        getTelemetryButton.addActionListener(event -> getSelectedVehicle().ifPresent(vehicle -> {
            final long startTimeEpochMilli = getTimeAsEpochMilli(startDateTimePicker);
            final long endTimeEpochMilli = getTimeAsEpochMilli(endDateTimePicker);

            final TelemetryProcessor telemetryProcessor = new TelemetryProcessor(controller
                    .getTelemetry(vehicle, startTimeEpochMilli, endTimeEpochMilli)
                    .getTelemetryList());

            final JFileChooser directoryChooser = new JFileChooser(".");
            directoryChooser.setDialogType(JFileChooser.SAVE_DIALOG);
            directoryChooser.setSelectedFile(new File(generateFileName(vehicle.getName(), startTimeEpochMilli)));
            if (directoryChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                final File selectedFile = directoryChooser.getSelectedFile();

                saveTelemetryToCsvFile(selectedFile, telemetryProcessor);

                if (uploadFlightCheckBox.isSelected()) {
                    final List<String> response = uploadCsvFile(selectedFile);
                    moveUploadedFile(selectedFile);
                    JOptionPane.showMessageDialog(this, response.toString(), "Server Upload Successful", INFORMATION_MESSAGE);
                }
            }
        }));
        this.add(BorderLayout.SOUTH, bottomPanel);

        final JPanel timePickersPanel = new JPanel();
        timePickersPanel.setLayout(new BoxLayout(timePickersPanel, BoxLayout.Y_AXIS));
        startDateTimePicker.setDateTimePermissive(LocalDateTime.now().minusHours(24));
        endDateTimePicker.setDateTimePermissive(LocalDateTime.now());

        final JPanel startTimePickerPanel = new JPanel();
        startTimePickerPanel.setBorder(BorderFactory.createTitledBorder("Start Date/Time"));
        startTimePickerPanel.add(startDateTimePicker);
        timePickersPanel.add(startTimePickerPanel);

        final JPanel endTimePickerPanel = new JPanel();
        endTimePickerPanel.setBorder(BorderFactory.createTitledBorder("End Date/Time"));
        endTimePickerPanel.add(endDateTimePicker);
        timePickersPanel.add(endTimePickerPanel);

        bottomPanel.add(BorderLayout.CENTER, timePickersPanel);

        vehicleJList.addListSelectionListener(event ->
                getSelectedVehicle().ifPresent(vehicle -> {
                    infoPane.setText(vehicle.toString());
                    getTelemetryButton.setEnabled(true);
                }));
    }

    private Optional<Vehicle> getSelectedVehicle() {
        return Optional.ofNullable(vehicleMap.get(vehicleJList.getSelectedValue()));
    }

    private static long getTimeAsEpochMilli(DateTimePicker dateTimePicker) {
        return dateTimePicker.getDateTimePermissive().atZone(ZoneId.systemDefault()).toInstant().getEpochSecond() * 1000L;
    }

    private void saveTelemetryToCsvFile(File fileToSave, TelemetryProcessor telemetryProcessor) {
        try (final OutputStream out = new FileOutputStream(fileToSave)) {
            telemetryProcessor.printAsCsv(out);
        } catch (Exception toRethrow) {
            throw new RuntimeException(toRethrow);
        }
    }

    private String generateFileName(String vehicleName, long startTimeEpochMilli) {
        return (vehicleName + "-" + FILE_DATE_FORMAT.format(new Date(startTimeEpochMilli)) + ".csv")
                .replaceAll("[\\*/\\\\!\\|:?<>]", "_")
                .replaceAll("(%22)", "_");
    }

    private List<String> uploadCsvFile(File fileToUpload) {
        try {
            MultipartUtility multipart = new MultipartUtility(settings().getUploadServerUrl(), "UTF-8");
            multipart.addFormField("login", settings().getUploadServerLogin());
            multipart.addFormField("password", settings().getUploadServerPassword());
            multipart.addFilePart("data", fileToUpload);

            return multipart.finish();
        } catch (Exception toRethrow) {
            throw new RuntimeException(toRethrow);
        }
    }

    private void moveUploadedFile(File fileToMove) {
        try {
            final Path uploadedFilesFolder = fileToMove.toPath().getParent().resolve(settings().getUploadedFileFolder());
            if (!Files.isDirectory(uploadedFilesFolder)) {
                Files.createDirectory(uploadedFilesFolder);
            }

            final Path targetFileName = uploadedFilesFolder.resolve(fileToMove.getName());
            Files.move(fileToMove.toPath(), targetFileName);
        } catch (Exception toRethrow) {
            throw new RuntimeException(toRethrow);
        }
    }
}
