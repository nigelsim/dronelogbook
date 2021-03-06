package ugcs.ucsHub.ui;

import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;

import static java.awt.Color.LIGHT_GRAY;
import static java.text.MessageFormat.format;
import static java.time.LocalDate.now;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.SwingUtilities.invokeLater;
import static ugcs.time.TimeUtils.time;

/**
 * Part of the {@link VehicleListForm} for date filtering controls
 */
class DatePickerPanel extends JPanel {
    private final static int DEFAULT_FLIGHTS_COUNT = 50;

    private final DatePicker datePicker;

    private final Color NORMAL_FOREGROUND_COLOR;

    private final ButtonGroup datePickerButtonGroup = new ButtonGroup();
    private final JToggleButton last24HoursButton = new JToggleButton("Last 24h");
    private final JToggleButton last7DaysButton = new JToggleButton("Last 7 days");
    private final JToggleButton lastXFlightsButton =
            new JToggleButton(format("Last {0} flights", DEFAULT_FLIGHTS_COUNT));

    private final List<DateChangeListener> dateChangeListeners = new LinkedList<>();

    DatePickerPanel(TelemetryDatesHighlighter datesHighlighter) {
        setBorder(createTitledBorder("Pick the date"));

        final DatePickerSettings datePickerSettings = new DatePickerSettings();
        datePickerSettings.setAllowEmptyDates(false);
        datePickerSettings.setHighlightPolicy(datesHighlighter);

        final JPanel datePickerPanel = new JPanel();
        datePicker = new DatePicker(datePickerSettings);
        datePickerPanel.add(datePicker);
        datePicker.addDateChangeListener(event -> onDatePickerTimeChanged());
        NORMAL_FOREGROUND_COLOR = datePicker.getComponentDateTextField().getForeground();

        datePickerPanel.add(last24HoursButton);
        last24HoursButton.getModel().setGroup(datePickerButtonGroup);
        last24HoursButton.addActionListener(event -> onToggleButtonChanged());

        datePickerPanel.add(last7DaysButton);
        last7DaysButton.getModel().setGroup(datePickerButtonGroup);
        last7DaysButton.addActionListener(event -> onToggleButtonChanged());

        datePickerPanel.add(lastXFlightsButton);
        lastXFlightsButton.getModel().setGroup(datePickerButtonGroup);
        lastXFlightsButton.addActionListener(event -> onToggleButtonChanged());

        add(datePickerPanel);
    }

    @FunctionalInterface
    interface DateChangeListener {
        void onDateChanged();
    }

    void addDateChangeListener(DateChangeListener listener) {
        dateChangeListeners.add(listener);
    }

    ZonedDateTime getSelectedStartTime() {
        if (isLast24hSelected()) {
            return ZonedDateTime.now().minusDays(1);
        }

        if (isLast7DaysSelected()) {
            return atStartOfDay(now().minusWeeks(1));
        }

        if (isLastXFlightsSelected()) {
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneId.systemDefault());
        }

        return atStartOfDay(datePicker.getDate());
    }

    ZonedDateTime getSelectedEndTime() {
        if (isLast24hSelected() || isLast7DaysSelected()) {
            return ZonedDateTime.now();
        }

        if (isLastXFlightsSelected()) {
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.MAX_VALUE), ZoneId.systemDefault());
        }

        return atEndOfDay(datePicker.getDate());
    }

    int getSelectedFlightsLimit() {
        return DEFAULT_FLIGHTS_COUNT;
    }

    private static ZonedDateTime atStartOfDay(LocalDate date) {
        return ZonedDateTime.of(date, LocalTime.of(0, 0), time().defaultZoneId());
    }

    private static ZonedDateTime atEndOfDay(LocalDate date) {
        return ZonedDateTime.of(date.plusDays(1), LocalTime.of(0, 0), time().defaultZoneId());
    }

    private boolean isLast24hSelected() {
        return datePickerButtonGroup.getSelection() == last24HoursButton.getModel();
    }

    private boolean isLast7DaysSelected() {
        return datePickerButtonGroup.getSelection() == last7DaysButton.getModel();
    }

    private boolean isLastXFlightsSelected() {
        return datePickerButtonGroup.getSelection() == lastXFlightsButton.getModel();
    }

    private void fadeDatePicker() {
        invokeLater(() -> datePicker.getComponentDateTextField().setForeground(LIGHT_GRAY));
    }

    private void activateDatePicker() {
        invokeLater(() -> datePicker.getComponentDateTextField().setForeground(NORMAL_FOREGROUND_COLOR));
    }

    private void onToggleButtonChanged() {
        fadeDatePicker();
        notifyDateChangeListeners();
    }

    private void onDatePickerTimeChanged() {
        datePickerButtonGroup.clearSelection();
        activateDatePicker();
        notifyDateChangeListeners();
    }

    private void notifyDateChangeListeners() {
        dateChangeListeners.forEach(DateChangeListener::onDateChanged);
    }
}
