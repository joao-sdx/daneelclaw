package org.daneel.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class PromptResolver {

    private static final DateTimeFormatter LOCAL_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private final String tasksDir;

    public PromptResolver(@Value("${daneel.scheduler.tasks-dir:./tasks}") String tasksDir) {
        this.tasksDir = tasksDir;
    }

    public String resolve(PlannedTask task, Instant triggerTime, Instant currentTime) {
        var file = Path.of(tasksDir, task.promptFile());
        try {
            var content = Files.readString(file);
            return content
                    .replace("{trigger_time_gmt}", DateTimeFormatter.ISO_INSTANT.format(triggerTime))
                    .replace("{current_time_gmt}", DateTimeFormatter.ISO_INSTANT.format(currentTime))
                    .replace("{trigger_time_local}", formatLocal(triggerTime))
                    .replace("{current_time_local}", formatLocal(currentTime));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read prompt file: " + file, e);
        }
    }

    private String formatLocal(Instant instant) {
        return LOCAL_FORMAT.format(ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()));
    }
}
