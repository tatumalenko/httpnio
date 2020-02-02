package httpsocketclient.cli.model;

import httpsocketclient.cli.Const;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@Data
@Accessors(fluent = true)
public class Help {
    private final String name;
    private final String summary;
    private final String description;
    private final String note;
    private final List<Option> options;
    private final Argument argument;

    private String usage() {
        return Const.CLI_NAME + " "
            + (options == null && argument == null ? "" : name) + " "
            + (options == null ? "command" : options.stream().map(option -> "[" + option.format + "]").collect(Collectors.joining(" "))) + " "
            + (argument == null ? "[arguments]" : argument);
    }

    private String details() {
        if (options == null && argument == null) {
            return "The commands are:\n"
                + Arrays.stream(Command.values()).map(cmd -> "\t" + String.format("%-15s", cmd.value()) + cmd.help().summary()).collect(Collectors.joining("\n"));
        } else if (options == null) {
            return "";
        }

        return "The options are:\n"
            + options.stream().map(option -> "\t" + String.format("%-15s", option.format) + option.info).collect(Collectors.joining("\n"));
    }

    public String text() {
        return description + "\n"
            + "Usage:\n"
            + "\t" + usage() + "\n"
            + details() + "\n";
    }

    public void print() {
        System.out.println(text());
    }
}